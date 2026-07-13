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
        loadingJob?.cancel()
        val targetSongId = song.id
        currentTargetSongId = targetSongId

        if (scope == null) {
            android.util.Log.w("LyricsStateHolder", "scope is null, cannot load lyrics for: ${song.title}")
            return
        }

        loadingJob = scope?.launch {
            var fetchedLyrics: Lyrics? = null
            try {
                loadCallback?.onLoadingStarted(targetSongId)

                kotlinx.coroutines.withTimeout(20000L) {
                    fetchedLyrics = try {
                        withContext(Dispatchers.IO) {
                            musicRepository.getLyrics(
                                song = song,
                                sourcePreference = sourcePreference,
                                forceRefresh = false
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.w("LyricsStateHolder", "歌词加载超时: ${song.title}")
                fetchedLyrics = null
            } catch (_: Throwable) {
                fetchedLyrics = null
            } finally {
                if (currentTargetSongId == targetSongId) {
                    loadCallback?.onLyricsLoadFinished(targetSongId, fetchedLyrics)
                    
                    if (fetchedLyrics == null) {
                        if (isNeteaseSong(song)) {
                            android.util.Log.d("LyricsStateHolder", "网易云歌曲歌词加载失败，保留加载状态等待重试: ${song.title}")
                        } else {
                            android.util.Log.d("LyricsStateHolder", "非网易云歌曲歌词加载失败，自动触发搜索: ${song.title}")
                            triggerAutoLyricsSearch(song, sourcePreference)
                        }
                    }
                }
            }
        }
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
        
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            
            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    val localLyrics = readLocalLyrics(song)
                    if (localLyrics != null) {
                        val parsed = LyricsUtils.parseLyrics(localLyrics)
                        if (hasValidLyrics(parsed)) {
                            val finalLyrics = parsed.copy(areFromRemote = false)
                            _searchUiState.value = LyricsSearchUiState.Success(finalLyrics)
                            loadCallback?.onLyricsLoaded(song.id, finalLyrics)
                            return@withTimeout
                        }
                    }
                    
                    musicRepository.getLyricsFromRemote(song)
                        .onSuccess { (lyrics, rawLyrics) ->
                            _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                            loadCallback?.onLyricsLoaded(song.id, lyrics)
                            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                            val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                            _songUpdates.emit(updatedSong to lyrics)
                        }
                        .onFailure { error ->
                            if (error is NoLyricsFoundException) {
                                musicRepository.searchRemoteLyrics(song)
                                    .onSuccess { (query, results) ->
                                        _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                    }
                                    .onFailure { searchError -> 
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
                handleError(TimeoutException("Lyrics search timeout"))
                _searchUiState.value = LyricsSearchUiState.Idle
            } catch (e: Exception) {
                handleError(e)
                _searchUiState.value = LyricsSearchUiState.Idle
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
        loadingJob?.cancel()
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
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    if (!forcePickResults) {
                        val storedLyrics = withContext(Dispatchers.IO) {
                            musicRepository.getStoredLyrics(song)
                        }
                        if (storedLyrics != null) {
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
                        if (result != null) {
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
                                _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                            }
                            .onFailure { error ->
                                handleError(error)
                            }
                    } else {
                        musicRepository.getLyricsFromRemote(song)
                            .onSuccess { (lyrics, rawLyrics) ->
                                _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                                val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                                _songUpdates.emit(updatedSong to lyrics)
                            }
                            .onFailure { error ->
                                if (error is NoLyricsFoundException) {
                                    musicRepository.searchRemoteLyrics(song)
                                        .onSuccess { (query, results) ->
                                            _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                        }
                                        .onFailure { searchError -> handleError(searchError) }
                                } else {
                                    handleError(error)
                                }
                            }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                handleError(TimeoutException("Lyrics fetch timeout"))
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * Manual search by query.
     */
    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        loadingJob?.cancel()
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
