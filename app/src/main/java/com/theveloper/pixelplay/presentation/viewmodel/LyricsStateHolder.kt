package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import java.io.File
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback interface for lyrics loading results.
 * Used to update StablePlayerState in PlayerViewModel.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
    fun onLyricsLoadFinished(songId: String, lyrics: Lyrics?)
}

/**
 * Callbacks supplied by [PlayerViewModel] so the AI-translation flow can reach the AI layer and
 * resolve localized strings without [LyricsStateHolder] depending on AiStateHolder or a Context.
 * Mirrors the callback-lambda pattern used elsewhere (e.g. [LyricsStateHolder.fetchLyricsForSong]).
 *
 * @param translate Delegates the raw lyrics to the AI translator (AiStateHolder.translateLyrics).
 * @param getString Resolves a no-arg string resource.
 * @param getErrorString Resolves the generic AI error string (R.string.ai_error_generic) with a detail.
 */
class LyricsTranslationCallbacks(
    val translate: suspend (String) -> Result<String>,
    val getString: (Int) -> String,
    val getErrorString: (String) -> String
)

/**
 * Manages lyrics loading, search state, and sync offset.
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val songMetadataEditor: SongMetadataEditor
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var loadCallback: LyricsLoadCallback? = null

    // ⚡ 当前正在加载歌词的目标歌曲 ID。用于防止歌曲快速切换时，
    // 已取消的请求仍返回歌词覆盖了正确歌曲的歌词状态。
    @Volatile
    private var currentTargetSongId: String? = null

    // ⚡ 歌词获取为空时的自动重试（仅针对在线/网易云歌曲，避免歌词界面空白无提示）
    private var lyricsRetryCount = 0
    private var lyricsRetrySongId: String? = null
    private var lyricsRetryJob: Job? = null

    // Sync offset per song in milliseconds
    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    // Lyrics search UI state
    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    // Event to notify ViewModel of song updates (e.g. lyrics added)
    private val _songUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Song, Lyrics?>>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val songUpdates = _songUpdates.asSharedFlow()

    // Event for Toasts
    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    /**
     * Initialize with coroutine scope and callback from ViewModel.
     */
    fun initialize(
        coroutineScope: CoroutineScope,
        callback: LyricsLoadCallback,
        stablePlayerState: StateFlow<com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState>
    ) {
        scope = coroutineScope
        loadCallback = callback

        coroutineScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != null) {
                        updateSyncOffsetForSong(songId)
                    }
                }
        }
    }

    /**
     * Load lyrics for a song. Uses [LyricsLoadCallback.onLyricsLoadFinished] so
     * that a failed fetch never overwrites already-loaded lyrics.
     * If lyrics fail to load for a non-Netease song, automatically triggers a remote search.
     * @param song The song to load lyrics for
     * @param sourcePreference The preferred source for lyrics
     */
    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        val targetSongId = song.id
        // ⚡ 同一首歌已有加载在跑时不重复启动：切歌同步 / repository hydration 会先后触发
        //   多次加载，旧逻辑直接 cancel+restart，会把进行中的请求白白打断（网络抖动下
        //   重启后的请求更容易失败，最终表现为"实际有歌词却显示暂无歌词"）。
        if (currentTargetSongId == targetSongId && loadingJob?.isActive == true) {
            return
        }
        // ⚡ 被顶替的旧目标要显式收口：cancel() 之后旧协程不会执行自身的收尾分支，
        //   它的 isLoadingLyrics 会永停在 true —— 歌词页 / 播放器一直显示「加载中」。
        val previousTargetSongId = currentTargetSongId
        cancelLoadingJob()
        lyricsRetryJob?.cancel()
        currentTargetSongId = targetSongId
        if (previousTargetSongId != null && previousTargetSongId != targetSongId) {
            loadCallback?.onLyricsLoadFinished(previousTargetSongId, null)
        }
        // 切歌时重置重试计数
        if (lyricsRetrySongId != targetSongId) {
            lyricsRetrySongId = targetSongId
            lyricsRetryCount = 0
        }

        if (scope == null) {
            android.util.Log.w("LyricsStateHolder", "scope is null, cannot load lyrics for: ${song.title}")
            return
        }

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            val startedAtMs = android.os.SystemClock.elapsedRealtime()
            var fetchedLyrics: Lyrics? = null
            var attempt = 0
            // ⚡ 首次加载 + 空结果自动重试放在同一个 Job 内完成。此前 retry 通过重新调用
            //   loadLyricsForSong 实现，会把正在跑的 Job 取消再重启，重试期间任何新的
            //   触发（hydration/sync）都可能打断整条重试链。
            while (currentTargetSongId == targetSongId) {
                try {
                    fetchedLyrics = kotlinx.coroutines.withTimeout(LYRICS_LOAD_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            musicRepository.getLyrics(
                                song = song,
                                sourcePreference = sourcePreference,
                                forceRefresh = false
                            )
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.w("LyricsStateHolder", "歌词加载超时(${LYRICS_LOAD_TIMEOUT_MS}ms): ${song.title}")
                    fetchedLyrics = null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Job 被取消（切歌/cancelLoading）：直接结束，不得吞掉取消信号
                    throw e
                } catch (_: Throwable) {
                    fetchedLyrics = null
                }

                if (fetchedLyrics != null) break
                // ⚡ 重试不再限定网易云：本地歌 / 其它在线源一次失败就直接显示「暂无歌词」
                //    太脆弱（网络抖动、单源限流都会命中）。统一按次数 + 耗时上限重试。
                if (attempt >= MAX_LYRICS_FETCH_RETRIES) break
                // ⚡ 只有"快速返回空结果"才值得重试。若本次是超时/长时间等待（单次上限 30s，
                //   3 次最坏 ≈ 90s），再重试只会让歌词页一直停在"正在加载歌词" ——
                //   用户感知就是"歌词永远加载不出来"。超时后直接收口为"暂无歌词"。
                if (android.os.SystemClock.elapsedRealtime() - startedAtMs >
                    LYRICS_RETRY_ALLOWED_ELAPSED_MS
                ) {
                    break
                }

                attempt++
                lyricsRetryCount = attempt
                android.util.Log.d(
                    "LyricsStateHolder",
                    "歌词获取为空，第 $lyricsRetryCount 次自动重试: ${song.title}"
                )
                loadCallback?.onLoadingStarted(targetSongId)
                try {
                    kotlinx.coroutines.delay(LYRICS_RETRY_DELAY_MS)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // 歌曲已切换/取消加载：由新歌曲的加载流程接管
                    loadCallback?.onLyricsLoadFinished(targetSongId, null)
                    return@launch
                }
            }

            if (currentTargetSongId != targetSongId) return@launch
            loadCallback?.onLyricsLoadFinished(targetSongId, fetchedLyrics)

            if (fetchedLyrics == null) {
                // ⚡ 任何歌曲拿不到歌词都再走一次「本地 → 在线搜索」兜底：
                //    以前只对非网易云歌做，网易云歌一旦拉取失败就直接显示「暂无歌词」。
                android.util.Log.d("LyricsStateHolder", "歌词加载为空，自动触发搜索: ${song.title}")
                triggerAutoLyricsSearch(song, sourcePreference)
            }
        }
    }

    /** 是否正在为指定歌曲加载歌词（用于调用方避免取消/重启进行中的加载） */
    fun isLoadingFor(songId: String): Boolean =
        currentTargetSongId == songId && loadingJob?.isActive == true

    /**
     * 取消进行中的加载任务，并把可能被「取消」卡住的搜索态一并收口。
     *
     * 被 cancel 的协程不会执行自身的收尾分支，`_searchUiState` 会永久停在 [LyricsSearchUiState.Loading]；
     * 而歌词页只要看到 Loading 就强制渲染「加载中」（即使歌词其实已经取到）。
     * 所有取消入口都必须走这里，不能再直接调用 `loadingJob?.cancel()`。
     */
    private fun cancelLoadingJob() {
        loadingJob?.cancel()
        if (_searchUiState.value is LyricsSearchUiState.Loading) {
            _searchUiState.value = LyricsSearchUiState.Idle
        }
    }

    private companion object {
        // 歌词获取为空的自动重试上限与间隔
        const val MAX_LYRICS_FETCH_RETRIES = 2
        const val LYRICS_RETRY_DELAY_MS = 3_000L

        // ⚡ 单次加载总预算：repository 内部（fetchLyricsFromAPI）自带 20s 超时，
        //   外层预算必须大于内层，否则内层请求永远跑不满就被整体取消
        const val LYRICS_LOAD_TIMEOUT_MS = 30_000L

        // ⚡ 允许继续重试的时间窗：超过它说明上一次是超时而非"快速空结果"，
        //   再重试只会把"正在加载歌词"继续挂住（重试 2 次最坏 ≈ 90s）
        const val LYRICS_RETRY_ALLOWED_ELAPSED_MS = 12_000L
    }
    
    /**
     * 判断是否为网易云歌曲
     */
    private fun isNeteaseSong(song: Song): Boolean =
        song.neteaseId != null ||
        song.contentUriString.startsWith("netease://", ignoreCase = true) ||
        song.contentUriString.startsWith("cloud://lx/", ignoreCase = true)
    
    /**
     * 自动触发歌词搜索，用于非网易云歌曲歌词加载失败时
     */
    private fun triggerAutoLyricsSearch(song: Song, sourcePreference: LyricsSourcePreference) {
        if (scope == null) {
            android.util.Log.w("LyricsStateHolder", "scope is null, cannot trigger auto lyrics search for: ${song.title}")
            return
        }

        val targetSongId = song.id
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    val localLyrics = readLocalLyrics(song)
                    if (localLyrics != null) {
                        val parsed = LyricsUtils.parseLyrics(localLyrics)
                        if (hasValidLyrics(parsed)) {
                            val finalLyrics = parsed.copy(areFromRemote = false)
                            if (currentTargetSongId == targetSongId) {
                                _searchUiState.value = LyricsSearchUiState.Success(finalLyrics)
                                loadCallback?.onLyricsLoaded(targetSongId, finalLyrics)
                            }
                            return@withTimeout
                        }
                    }

                    musicRepository.getLyricsFromRemote(song)
                        .onSuccess { (lyrics, rawLyrics) ->
                            if (currentTargetSongId != targetSongId) return@onSuccess
                            _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                            loadCallback?.onLyricsLoaded(targetSongId, lyrics)
                            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                            val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                            _songUpdates.emit(updatedSong to lyrics)
                        }
                        .onFailure { error ->
                            if (currentTargetSongId != targetSongId) return@onFailure
                            if (error is NoLyricsFoundException) {
                                musicRepository.searchRemoteLyrics(song)
                                    .onSuccess { (query, results) ->
                                        if (currentTargetSongId != targetSongId) return@onSuccess
                                        _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                    }
                                    .onFailure { searchError ->
                                        if (currentTargetSongId != targetSongId) return@onFailure
                                        handleError(searchError)
                                        _searchUiState.value = LyricsSearchUiState.Idle
                                    }
                            } else {
                                handleError(error)
                                _searchUiState.value = LyricsSearchUiState.Idle
                            }
                        }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (currentTargetSongId == targetSongId) {
                    handleError(TimeoutException("Lyrics search timeout"))
                    _searchUiState.value = LyricsSearchUiState.Idle
                }
            } catch (e: Exception) {
                if (currentTargetSongId == targetSongId) {
                    handleError(e)
                    _searchUiState.value = LyricsSearchUiState.Idle
                }
            }
        }
    }
    
    /**
     * 读取本地歌词（嵌入式或本地文件）
     */
    private suspend fun readLocalLyrics(song: Song): String? {
        // 检查嵌入式歌词
        val embeddedLyrics = readEmbeddedLyricsFromFile(song)
        if (!embeddedLyrics.isNullOrBlank()) {
            val parsed = LyricsUtils.parseLyrics(embeddedLyrics)
            if (hasValidLyrics(parsed)) return embeddedLyrics
        }
        
        // 检查本地 .lrc 文件
        val localLyricsFile = readLocalLyricsFile(song)
        if (!localLyricsFile.isNullOrBlank()) {
            val parsed = LyricsUtils.parseLyrics(localLyricsFile)
            if (hasValidLyrics(parsed)) return localLyricsFile
        }
        
        return null
    }

    /**
     * Cancel any ongoing lyrics loading.
     */
    fun cancelLoading() {
        val targetSongId = currentTargetSongId
        cancelLoadingJob()
        targetSongId?.let { loadCallback?.onLyricsLoadFinished(it, null) }
    }

    /**
     * Set sync offset for a song.
     */
    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    /**
     * Update sync offset from song ID (called when song changes).
     */
    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    /**
     * Set the lyrics search UI state.
     */
    fun setSearchState(state: LyricsSearchUiState) {
        _searchUiState.value = state
    }

    /**
     * Reset the lyrics search state to idle.
     */
    fun resetSearchState() {
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    /**
     * Fetch lyrics for the given song, respecting the user's source preference.
     */
    fun fetchLyricsForSong(
        song: Song,
        forcePickResults: Boolean,
        sourcePreference: LyricsSourcePreference,
        contextHelper: (Int) -> String
    ) {
        // ⚡ 被取消的默认加载不会回调 onLyricsLoadFinished：这里主动收口一次，
        //   否则 isLoadingLyrics 会永远停在 true，歌词页停在"加载中"看起来是全空
        val inFlightSongId = currentTargetSongId
        cancelLoadingJob()
        inFlightSongId?.let { loadCallback?.onLyricsLoadFinished(it, null) }
        val targetSongId = song.id
        currentTargetSongId = targetSongId
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    if (!forcePickResults) {
                        val storedLyrics = withContext(Dispatchers.IO) {
                            musicRepository.getStoredLyrics(song)
                        }
                        if (storedLyrics != null && currentTargetSongId == targetSongId) {
                            val (lyrics, rawLyrics) = storedLyrics
                            _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                            _songUpdates.emit(song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri = null) to lyrics)
                            _messageEvents.emit(contextHelper(R.string.lyrics_already_available))
                            return@withTimeout
                        }
                    }

                    val localSourceChecks: List<suspend () -> Pair<String, Int>?> = when (sourcePreference) {
                        LyricsSourcePreference.API_FIRST -> emptyList()
                        LyricsSourcePreference.EMBEDDED_FIRST -> listOf(
                            { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } },
                            { readLocalLyricsFile(song)?.let { it to R.string.local_lrc_already_available } }
                        )
                        LyricsSourcePreference.LOCAL_FIRST -> listOf(
                            { readLocalLyricsFile(song)?.let { it to R.string.local_lrc_already_available } },
                            { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } }
                        )
                    }

                    for (sourceCheck in localSourceChecks) {
                        val result = withContext(Dispatchers.IO) { sourceCheck() }
                        if (result != null && currentTargetSongId == targetSongId) {
                            val (rawLyrics, messageResId) = result
                            val parsed = LyricsUtils.parseLyrics(rawLyrics)
                            if (hasValidLyrics(parsed)) {
                                val lyrics = parsed.copy(areFromRemote = false)
                                _searchUiState.value = LyricsSearchUiState.Success(lyrics)

                                val songId = song.id.toLongOrNull()
                                if (songId != null) {
                                    musicRepository.updateLyrics(songId, rawLyrics)
                                }

                                _songUpdates.emit(song.copy(lyrics = rawLyrics) to lyrics)
                                _messageEvents.emit(contextHelper(messageResId))
                                return@withTimeout
                            }
                        }
                    }

                    if (forcePickResults) {
                        musicRepository.searchRemoteLyrics(song)
                            .onSuccess { (query, results) ->
                                if (currentTargetSongId != targetSongId) return@onSuccess
                                _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                            }
                            .onFailure { error ->
                                if (currentTargetSongId != targetSongId) return@onFailure
                                handleError(error)
                            }
                    } else {
                        musicRepository.getLyricsFromRemote(song)
                            .onSuccess { (lyrics, rawLyrics) ->
                                if (currentTargetSongId != targetSongId) return@onSuccess
                                _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                                val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                                _songUpdates.emit(updatedSong to lyrics)
                            }
                            .onFailure { error ->
                                if (currentTargetSongId != targetSongId) return@onFailure
                                if (error is NoLyricsFoundException) {
                                    musicRepository.searchRemoteLyrics(song)
                                        .onSuccess { (query, results) ->
                                            if (currentTargetSongId != targetSongId) return@onSuccess
                                            _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                        }
                                        .onFailure { searchError ->
                                            if (currentTargetSongId != targetSongId) return@onFailure
                                            handleError(searchError)
                                        }
                                } else {
                                    handleError(error)
                                }
                            }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (currentTargetSongId == targetSongId) {
                    handleError(TimeoutException("Lyrics fetch timeout"))
                }
            } catch (e: Exception) {
                if (currentTargetSongId == targetSongId) {
                    handleError(e)
                }
            }
        }
    }

    /**
     * Manual search by query.
     */
    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        // 同上：取消进行中的加载时主动收口，避免 isLoadingLyrics 卡在 true
        val inFlightSongId = currentTargetSongId
        cancelLoadingJob()
        inFlightSongId?.let { loadCallback?.onLyricsLoadFinished(it, null) }
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    _searchUiState.value = LyricsSearchUiState.PickResult(q, results)
                }
                .onFailure { error -> handleError(error) }
        }
    }

    /**
     * Accept a search result.
     */
    fun acceptLyricsSearchResult(result: LyricsSearchResult, currentSong: Song) {
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Success(result.lyrics)

            // 1. Update DB cache
            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, result.rawLyrics)
            }

            // 2. Attempt metadata write-back to the audio file
            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, result.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(result.rawLyrics, refreshedAlbumArtUri)

            // 3. Notify
            _songUpdates.emit(updatedSong to result.lyrics)
        }
    }

    /**
     * Import from file.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport, currentSong: Song?) {
        scope?.launch {
            val sanitizedContent = validatedImport.sanitizedContent
            val parsedLyrics = validatedImport.parsedLyrics

            musicRepository.updateLyrics(songId, sanitizedContent)

            if (currentSong != null && currentSong.id.toLongOrNull() == songId) {
                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, sanitizedContent)
                val updatedSong = currentSong.withPersistedLyrics(sanitizedContent, refreshedAlbumArtUri)
                _songUpdates.emit(updatedSong to parsedLyrics.takeIf(::hasValidLyrics))
            }

            _messageEvents.emit("Lyrics imported successfully!")
        }
    }

    /**
     * Translate the current song's lyrics via AI and import the result.
     * The actual inference is delegated through [LyricsTranslationCallbacks.translate] so this holder
     * stays decoupled from the AI layer. Toasts are surfaced through [messageEvents] as usual.
     */
    fun translateLyricsViaAi(currentSong: Song, lyricsObj: Lyrics?, cb: LyricsTranslationCallbacks) {
        val songId = currentSong.id.toLongOrNull() ?: return
        val rawLyrics = currentSong.lyrics

        if (rawLyrics.isNullOrBlank()) {
            _messageEvents.tryEmit(cb.getString(R.string.lyrics_not_found))
            return
        }

        if (lyricsObj?.synced != null) {
            val hasValidTranslation = lyricsObj.synced.any { !it.translation.isNullOrBlank() }
            if (hasValidTranslation) {
                _messageEvents.tryEmit(cb.getString(R.string.ai_lyrics_already_translated))
                return
            }
        }

        scope?.launch {
            _messageEvents.emit(cb.getString(R.string.ai_lyrics_translating))
            val result = cb.translate(rawLyrics)
            result.onSuccess { translatedText ->
                if (translatedText.trim() == "ALREADY_IN_TARGET_LANGUAGE") {
                    _messageEvents.emit(cb.getString(R.string.ai_lyrics_already_in_target_language))
                    return@onSuccess
                }

                if (translatedText.isNotBlank()) {
                    val validation = LyricsImportSecurity.validateImportedLrcContent(translatedText)
                    if (validation is LyricsImportValidationResult.Valid) {
                        importLyricsFromFile(songId, validation.value, currentSong)
                        _messageEvents.emit(cb.getString(R.string.ai_lyrics_translation_success))
                    } else {
                        val reason = (validation as LyricsImportValidationResult.Invalid).reason
                        val errorMsg = LyricsImportSecurity.messageFor(reason)
                        _messageEvents.emit(cb.getErrorString(errorMsg))
                    }
                } else {
                    _messageEvents.emit(cb.getErrorString("Empty response"))
                }
            }.onFailure {
                if (it.message?.contains("key", ignoreCase = true) == true ||
                    it.message?.contains("config", ignoreCase = true) == true
                ) {
                    _messageEvents.emit(cb.getString(R.string.ai_error_api_key))
                } else {
                    _messageEvents.emit(cb.getErrorString(it.message ?: ""))
                }
            }
        }
    }

    fun resetLyrics(songId: Long) {
        resetSearchState()
        scope?.launch {
            musicRepository.resetLyrics(songId)
            _songUpdates.emit(Song.emptySong().copy(id = songId.toString()) to null)
        }
    }

    fun resetAllLyrics() {
        resetSearchState()
        scope?.launch {
            musicRepository.resetAllLyrics()
        }
    }

    private fun handleError(error: Throwable) {
        _searchUiState.value = if (error is NoLyricsFoundException) {
            LyricsSearchUiState.NotFound("Lyrics not found")
        } else {
            LyricsSearchUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun hasValidLyrics(lyrics: Lyrics?): Boolean {
        if (lyrics == null) return false
        return !lyrics.synced.isNullOrEmpty() || !lyrics.plain.isNullOrEmpty()
    }

    private fun readEmbeddedLyricsFromFile(song: Song): String? {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return runCatching {
            AudioMetadataReader.read(File(song.path))
                ?.lyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readLocalLyricsFile(song: Song): String? {
        return runCatching {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@runCatching null
            for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                val lyricsFile = File(directory, "${songFile.nameWithoutExtension}.$extension")
                if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                when (val validation = LyricsImportSecurity.validateLocalLyricsFile(lyricsFile)) {
                    is LyricsImportValidationResult.Valid -> return@runCatching validation.value.sanitizedContent
                    is LyricsImportValidationResult.Invalid -> continue
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun persistLyricsToFileMetadataIfPossible(song: Song, rawLyrics: String): String? {
        val songId = song.id.toLongOrNull() ?: return null
        val normalizedLyrics = rawLyrics.trim()
        if (normalizedLyrics.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val existingArtwork = runCatching {
                AudioMetadataReader.read(File(song.path))?.artwork
            }.getOrNull()

            val coverArtUpdate = existingArtwork?.let { artwork ->
                CoverArtUpdate(
                    bytes = artwork.bytes,
                    mimeType = artwork.mimeType ?: "image/jpeg"
                )
            }

            runCatching {
                songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre ?: "",
                    newLyrics = normalizedLyrics,
                    newTrackNumber = song.trackNumber,
                    newDiscNumber = song.discNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }.getOrNull()?.updatedAlbumArtUri
        }
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}

internal fun Song.withPersistedLyrics(rawLyrics: String, refreshedAlbumArtUri: String?): Song {
    return copy(
        lyrics = rawLyrics,
        // Lyrics writes can refresh the cached cover-art file path. Carry it forward immediately
        // so the full player doesn't keep rendering a deleted image URI until the next app reload.
        albumArtUriString = refreshedAlbumArtUri ?: albumArtUriString
    )
}
