package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.lx.LxFileStore
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LxUiState(
    val engineReady: Boolean = false,
    val version: String = "unknown",
    val sources: Map<String, LxSourceInfo> = emptyMap(),
    val keyword: String = "",
    val selectedSource: String = "all",
    val searching: Boolean = false,
    val results: List<LxSongInfo> = emptyList(),
    val error: String? = null,
    val importError: String? = null,
    val initing: Boolean = false,
    val progress: Float? = null,
    val progressLabel: String? = null,
    // ⚡ 分页相关字段
    val isEnd: Boolean = true,
    val isLoadingMore: Boolean = false,
)

@HiltViewModel
class LxMusicViewModel @Inject constructor(
    app: Application,
    private val engine: LxJsEngine,
    private val store: LxFileStore,
    private val searchApi: LxSearchApi,
    private val musicRepository: MusicRepository,
    private val neteaseRepository: NeteaseRepository,
    private val userPreferencesRepository: com.theveloper.pixelplay.data.preferences.UserPreferencesRepository,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(LxUiState())
    val uiState: StateFlow<LxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            autoInitIfPresent()
        }
    }

    var showImportUrl: Boolean = false
    var showInfo: Boolean = false

    var keyword: String
        get() = _uiState.value.keyword
        set(v) { _uiState.value = _uiState.value.copy(keyword = v) }

    var selectedSource: String
        get() = _uiState.value.selectedSource
        set(v) { _uiState.value = _uiState.value.copy(selectedSource = v) }

    fun refreshDisplayOnly() {
        val hasJs = store.exists()
        _uiState.value = if (hasJs) {
            _uiState.value.copy(engineReady = false, sources = emptyMap(), version = "custom")
        } else {
            _uiState.value.copy(engineReady = false, sources = emptyMap(), version = "none", importError = null)
        }
    }

    fun ensureEngineStarted() {
        viewModelScope.launch(Dispatchers.IO) { 
            // 如果引擎已就绪，直接更新 UI 状态反映引擎当前状态
            if (engine.isReady()) {
                _uiState.value = _uiState.value.copy(
                    engineReady = true,
                    sources = runCatching { engine.getSources() }.getOrDefault(emptyMap()),
                    version = runCatching { engine.versionName() }.getOrDefault("custom"),
                    initing = false,
                    importError = null
                )
                return@launch
            }
            val hasJs = store.exists()
            if (!hasJs) {
                _uiState.value = _uiState.value.copy(
                    initing = false, engineReady = false,
                    importError = "请先导入一个 JS 音源文件（点右上角 +）"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(initing = true, importError = null)
            val ok = runCatching { engine.ready() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ok,
                sources = if (ok) runCatching { engine.getSources() }.getOrDefault(emptyMap()) else emptyMap(),
                version = runCatching { engine.versionName() }.getOrDefault("custom"),
                importError = if (!ok) engine.lastError ?: "JS 执行时报错" else null
            )
        }
    }

    fun autoInitIfPresent() {
        ensureEngineStarted()
    }

    fun importFromUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(initing = true, importError = null, engineReady = false)
        viewModelScope.launch(Dispatchers.IO) { 
            val ok = store.writeFromUri(uri)    
            if (!ok) {
                _uiState.value = _uiState.value.copy(initing = false, importError = "读取文件失 败")
                return@launch
            }
            val ready = engine.reload()
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ready,
                sources = engine.getSources(),  
                version = engine.versionName(), 
                importError = if (!ready) engine.lastError ?: "JS 执行时报错" else null
            )
        }
    }

    fun importFromUrl(url: String) {
        _uiState.value = _uiState.value.copy(initing = true, importError = null, engineReady = false)
        viewModelScope.launch(Dispatchers.IO) { 
            val ok = store.writeFromUrl(url)    
            if (!ok) {
                _uiState.value = _uiState.value.copy(initing = false, importError = "下载失败 ( 超时或非 JS)")
                return@launch
            }
            val ready = engine.reload()
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ready,
                sources = engine.getSources(),  
                version = engine.versionName(), 
                importError = if (!ready) engine.lastError ?: "JS 执行时报错" else null
            )
        }
    }

    fun removeJs() {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete()
            engine.close()
            _uiState.value = LxUiState(version = "none")
        }
    }

    fun reloadEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(initing = true)
            val ok = engine.reload()
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ok,
                sources = engine.getSources(),
                version = engine.versionName(),
                importError = if (!ok) "JS 重新加载失败" else null
            )
        }
    }

    // ⚡ 分页状态
    private val _pageSize = 20
    private var _currentPage = 1
    private var _lastKeyword: String? = null

    fun search() {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        // ⚡ 新搜索重置分页状态
        _currentPage = 1
        _lastKeyword = kw
        _uiState.value = _uiState.value.copy(
            searching = true,
            error = null,
            isEnd = false,
            isLoadingMore = false,
            results = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!engine.isReady()) {
                    if (!store.exists()) {
                        _uiState.value = _uiState.value.copy(
                            searching = false,
                            error = "请先在设置中导入 JS 音源"
                        )
                        return@launch
                    }
                    engine.ready()
                }
                // vkeys 搜索接口一次返回 id / 歌名 / 歌手 / 专辑 / 封面，
                // 不再单独获取封面，显著提速。
                val result = searchApi.search(kw, page = 1, pageSize = _pageSize)
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = result.list,
                    isEnd = result.isEnd,
                    error = if (result.list.isEmpty()) "无结果（请换关键词）" else null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = emptyList(),
                    isEnd = true,
                    error = "搜索失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * ⚡ 加载下一页搜索结果（无限滚动）
     * - 追加到现有结果列表
     * - 仅在非搜索中、非最后一页时生效
     * - 自动切换 isLoadingMore 状态
     */
    fun loadMore() {
        val kw = _lastKeyword?.trim() ?: keyword.trim()
        if (kw.isBlank()) return
        // 防重复：正在搜索/加载更多时不触发；已到最后一页时不触发
        if (_uiState.value.searching || _uiState.value.isLoadingMore || _uiState.value.isEnd) return
        // 无现有结果时，交给普通 search()
        if (_uiState.value.results.isEmpty()) return

        val nextPage = _currentPage + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchApi.search(kw, page = nextPage, pageSize = _pageSize)
                // ⚡ 追加到现有结果列表，使用 LinkedHashSet 去重（避免重复歌曲）
                val existingIds = _uiState.value.results.mapTo(LinkedHashSet()) { it.id }
                val newItems = result.list.filterNot { it.id in existingIds }
                val merged = _uiState.value.results + newItems

                _currentPage = nextPage
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    results = merged,
                    isEnd = result.isEnd
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "加载更多失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    fun playSong(
        song: LxSongInfo,
        onOpenPlayer: (url: String, title: String, artist: String, cover: String, songId: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("LxPlaySong", "=== playSong called ===")
                android.util.Log.d("LxPlaySong", "Song name: ${song.name}, singer: ${song.singer}, id: ${song.id}, cover: ${song.pic}")
                _uiState.value = _uiState.value.copy(
                    progress = 0.2f,
                    progressLabel = "获取播放链接…"
                )
                val songMap = song.toInfoMap()
                val availableSources = engine.getSources().keys.filter { it in listOf("wy", "tx", "kw", "kg", "mg", "qsvip") }
                val targetSource = if (selectedSource != "all" && availableSources.contains(selectedSource)) {
                    selectedSource
                } else {
                    availableSources.firstOrNull() ?: "wy"
                }
                android.util.Log.d("LxPlaySong", "Target source: $targetSource")

                // 如果 song.pic 为空，在播放前尝试获取封面
                val coverToUse = if (song.pic.isBlank()) {
                    android.util.Log.d("LxPlaySong", "封面为空，尝试从 vkeys 获取...")
                    searchApi.getSongCoverFromVkeys(song.id) ?: ""
                } else song.pic

                val url = if (targetSource == "wy" && song.id.all { it.isDigit() }) {
                    val neteaseId = song.id.toLong()
                    val preferredQuality = try {
                        userPreferencesRepository.musicQualityFlow.first()
                    } catch (_: Exception) {
                        com.theveloper.pixelplay.data.preferences.MusicQuality.HIGH
                    }
                    val officialUrl = try {
                        val res = neteaseRepository.getSongUrl(neteaseId, preferredQuality.neteaseLevel)
                        res.getOrNull()
                    } catch (t: Throwable) {
                        Timber.w(t, "LxPlaySong: Netease official API failed for songId=$neteaseId")
                        null
                    }
                    if (!officialUrl.isNullOrBlank()) {
                        Timber.d("LxPlaySong: Using official Netease URL for '${song.name}' (id=$neteaseId)")
                        android.util.Log.d("LxPlaySong", "Using official Netease URL for '${song.name}'")
                        officialUrl
                    } else {
                        Timber.d("LxPlaySong: Official API failed, falling back to LxJsEngine for '${song.name}'")
                        engine.getPlayUrl("wy", songMap, preferredQuality.lxValue)
                            ?: engine.getPlayUrl("wy", songMap, "320k")
                            ?: engine.getPlayUrl("wy", songMap, "128k")
                    }
                } else {
                    val preferredQuality = try {
                        userPreferencesRepository.musicQualityFlow.first()
                    } catch (_: Exception) {
                        com.theveloper.pixelplay.data.preferences.MusicQuality.HIGH
                    }
                    engine.getPlayUrl(targetSource, songMap, preferredQuality.lxValue)
                        ?: engine.getPlayUrl(targetSource, songMap, "320k")
                        ?: engine.getPlayUrl(targetSource, songMap, "128k")
                }
                android.util.Log.d("LxPlaySong", "Resolved URL: $url, cover: $coverToUse")

                // ── 将歌曲保存到数据库，使用返回的真实 song id
                // 同时将成功获取 URL 的音源保存到 songInfo.source，
                // 这样从媒体库播放时可以用正确的音源重新获取播放链接
                val songWithCover = if (coverToUse.isNotBlank() && song.pic.isBlank()) {
                    song.copy(pic = coverToUse, source = targetSource)
                } else {
                    song.copy(source = targetSource)
                }
                val savedSongId = try {
                    musicRepository.saveCloudSong(songWithCover).toString()
                } catch (t: Throwable) {
                    android.util.Log.w("LxPlaySong", "saveCloudSong 失败: ${t.message}")
                    "cloud_${song.id}"
                }
                android.util.Log.d("LxPlaySong", "Saved song ID: $savedSongId, source: $targetSource")

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(progress = null, progressLabel = null)
                    if (url == null) {
                        android.util.Log.w("LxPlaySong", "URL is null, showing error")
                        _uiState.value = _uiState.value.copy(error = "无法获取播放链接，请换一首或换音源")
                        return@withContext
                    }
                    android.util.Log.d("LxPlaySong", "Calling onOpenPlayer with URL length: ${url.length}, songId: $savedSongId")
                    android.util.Log.d("LxPlaySong", "URL scheme: ${android.net.Uri.parse(url).scheme}, host: ${android.net.Uri.parse(url).host}")
                    onOpenPlayer(url, song.name, song.singer, coverToUse, savedSongId)
                }
            } catch (t: Throwable) {
                android.util.Log.e("LxPlaySong", "Error: ${t.message}", t)
                _uiState.value = _uiState.value.copy(
                    progress = null, progressLabel = null,
                    error = "播放失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    // ── Favorite support for cloud songs ─────────────────────────────────────

    /** Flow of all favorited song IDs from the main database. */
    val favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>
        get() = musicRepository.getFavoriteSongIdsFlow()

    /** Saves a cloud song to the database and toggles its favorite status. */
    fun toggleFavoriteForSong(song: LxSongInfo, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: ensure the song is saved in the main database
                // 记录收藏时的默认音源，方便从媒体库播放时重新获取播放链接
                val songWithSource = if (song.source.isNotBlank()) {
                    song
                } else {
                    val availableSources = runCatching {
                        engine.getSources().keys.filter { it in listOf("wy", "tx", "kw", "kg", "mg", "qsvip") }
                    }.getOrDefault(emptyList())
                    val targetSource = if (selectedSource != "all" && availableSources.contains(selectedSource)) {
                        selectedSource
                    } else {
                        availableSources.firstOrNull() ?: "wy"
                    }
                    song.copy(source = targetSource)
                }
                val songId = musicRepository.saveCloudSong(songWithSource)
                // Step 2: toggle favorite status
                val newFav = musicRepository.toggleFavoriteStatus(songId.toString())
                withContext(Dispatchers.Main) {
                    onResult(newFav)
                }
            } catch (t: Throwable) {
                android.util.Log.e("LxFavorite", "toggleFavoriteForSong failed: ${t.message}", t)
            }
        }
    }

    /** Checks whether a cloud song is currently favorited. */
    fun isSongFavorited(song: LxSongInfo, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songId = computeStableId(song)
                val favIds = musicRepository.getFavoriteSongIdsOnce()
                val isFav = favIds.contains(songId.toString())
                withContext(Dispatchers.Main) {
                    onResult(isFav)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun getStableSongId(song: LxSongInfo): String {
        var hash = 1125899906842597L
        val input = "lx_song_" + song.id + "|" + song.name + "|" + song.singer
        for (c in input) {
            hash = (hash * 31 + c.code.toLong())
        }
        val result = hash and Long.MAX_VALUE
        return (if (result == 0L) 1L else result).toString()
    }

    private fun computeStableId(song: LxSongInfo): Long {
        return getStableSongId(song).toLong()
    }

    private fun LxSongInfo.toInfoMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "vid" to id,
        "songmid" to (songmid.ifBlank { id }),
        "hash" to (hash.ifBlank { id }),
        "name" to name,
        "singer" to singer,
        "artists" to singer,
        "album" to albumName,
        "albumName" to albumName,
        "duration" to duration,
        "cover" to pic,
        "pic" to pic,
    )
}
