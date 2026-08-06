package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.lx.LxFileStore
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.lx.LxArtistInfo
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.preferences.MusicQuality
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
    /** 已导入的所有 JS 脚本简介列表（多 JS 管理用） */
    val scriptInfos: List<LxScriptInfo> = emptyList(),
    /** 在线音源播放音质（24bit / FLAC / 320k / 128k） */
    val musicQuality: MusicQuality = MusicQuality.HIGH,
    val keyword: String = "",
    val selectedSource: String = "wy",
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
    // ⚡ 歌手搜索相关字段
    val searchingArtists: Boolean = false,
    val artistResults: List<LxArtistInfo> = emptyList(),
    val artistIsEnd: Boolean = true,
    val artistError: String? = null,
)

@HiltViewModel
class LxMusicViewModel @Inject constructor(
    app: Application,
    private val engine: LxJsEngine,
    private val store: LxFileStore,
    private val searchApi: LxSearchApi,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: com.theveloper.pixelplay.data.preferences.UserPreferencesRepository,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(LxUiState())
    val uiState: StateFlow<LxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 首次启动把 assets 内置音源导入用户目录，再初始化引擎
            store.ensureBundledSources()
            autoInitIfPresent()
        }
        // 同步在线音源播放音质
        viewModelScope.launch {
            userPreferencesRepository.musicQualityFlow.collect { quality ->
                _uiState.value = _uiState.value.copy(musicQuality = quality)
            }
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
        val hasJs = store.hasAnyJs()
        _uiState.value = if (hasJs) {
            _uiState.value.copy(engineReady = false, sources = emptyMap(), version = "custom")
        } else {
            _uiState.value.copy(engineReady = false, sources = emptyMap(), version = "none", importError = null)
        }
    }

    /** 设置在线音源播放音质（与设置页数据源一致） */
    fun setMusicQuality(quality: MusicQuality) {
        // 立即同步 UI（不等待 DataStore flow 回环，避免点击后无响应）
        _uiState.value = _uiState.value.copy(musicQuality = quality)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                userPreferencesRepository.setMusicQuality(quality)
            }.onFailure {
                Timber.w(it, "setMusicQuality 持久化失败: $quality")
            }
        }
    }

    private suspend fun loadScriptInfos(): List<LxScriptInfo> =
        runCatching { engine.scriptInfos() }.getOrDefault(emptyList())

    fun ensureEngineStarted() {
        viewModelScope.launch(Dispatchers.IO) { 
            // 如果引擎已就绪，直接更新 UI 状态反映引擎当前状态
            if (engine.isReady()) {
                _uiState.value = _uiState.value.copy(
                    engineReady = true,
                    sources = runCatching { engine.getSources() }.getOrDefault(emptyMap()),
                    version = runCatching { engine.versionName() }.getOrDefault("custom"),
                    scriptInfos = loadScriptInfos(),
                    initing = false,
                    importError = null
                )
                return@launch
            }
            val hasJs = store.hasAnyJs()
            if (!hasJs) {
                _uiState.value = _uiState.value.copy(
                    initing = false, engineReady = false,
                    scriptInfos = loadScriptInfos(),
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
                scriptInfos = loadScriptInfos(),
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
            val fileName = store.writeFromUri(uri)
            if (fileName == null) {
                _uiState.value = _uiState.value.copy(initing = false, importError = "读取文件失败")
                return@launch
            }
            val ready = engine.reload()
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ready,
                sources = engine.getSources(),  
                version = engine.versionName(), 
                scriptInfos = loadScriptInfos(),
                importError = if (!ready) engine.lastError ?: "JS 执行时报错" else null
            )
        }
    }

    fun importFromUrl(url: String) {
        _uiState.value = _uiState.value.copy(initing = true, importError = null, engineReady = false)
        viewModelScope.launch(Dispatchers.IO) { 
            val fileName = store.writeFromUrl(url)
            if (fileName == null) {
                _uiState.value = _uiState.value.copy(initing = false, importError = "下载失败 (超时或非 JS)")
                return@launch
            }
            val ready = engine.reload()
            _uiState.value = _uiState.value.copy(
                initing = false,
                engineReady = ready,
                sources = engine.getSources(),  
                version = engine.versionName(), 
                scriptInfos = loadScriptInfos(),
                importError = if (!ready) engine.lastError ?: "JS 执行时报错" else null
            )
        }
    }

    /** 删除单个 JS 脚本文件并重新加载引擎 */
    fun removeJs(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteByName(fileName)
            val remaining = store.hasAnyJs()
            if (remaining) {
                val ok = engine.reload()
                _uiState.value = _uiState.value.copy(
                    initing = false,
                    engineReady = ok,
                    sources = engine.getSources(),
                    version = engine.versionName(),
                    scriptInfos = loadScriptInfos(),
                    importError = if (!ok) engine.lastError ?: "JS 重新加载失败" else null
                )
            } else {
                engine.close()
                _uiState.value = LxUiState(version = "none")
            }
        }
    }

    /** 删除全部 JS 脚本并关闭引擎 */
    fun removeAllJs() {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteAll()
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
                scriptInfos = loadScriptInfos(),
                importError = if (!ok) "JS 重新加载失败" else null
            )
        }
    }

    // ⚡ 分页状态
    private val _pageSize = 20
    private var _currentPage = 1
    private var _lastKeyword: String? = null
    /** 最近一次搜索实际使用的音源（供分页加载更多时保持同一音源） */
    private var _lastSource: String = "wy"

    fun search(source: String? = null) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val effectiveSource = source ?: selectedSource
        // ⚡ 新搜索重置分页状态
        _currentPage = 1
        _lastKeyword = kw
        _lastSource = effectiveSource
        _uiState.value = _uiState.value.copy(
            searching = true,
            error = null,
            isEnd = false,
            isLoadingMore = false,
            results = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 网易云/内置音源不依赖 JS 引擎；仅落雪 JS 音源需要先就绪引擎
                if (effectiveSource != "wy" && !builtInSourceSearchApi.isSupported(effectiveSource)) {
                    if (!engine.isReady()) {
                        if (!store.hasAnyJs()) {
                            _uiState.value = _uiState.value.copy(
                                searching = false,
                                error = "请先在设置中导入 JS 音源"
                            )
                            return@launch
                        }
                        engine.ready()
                    }
                }
                // wy / all（默认）走网易云官方搜索（原版不动）；
                // 选中落雪音源时按落雪方案走 JS 引擎搜索。
                val result = searchBySource(effectiveSource, kw, page = 1, pageSize = _pageSize)
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = result.list,
                    isEnd = result.isEnd,
                    error = if (result.list.isEmpty()) "无结果（请换关键词）" else null
                )
                // 搜索 API 不返回 picUrl，批量补充封面
                fillMissingCovers(result.list)
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
                val result = searchBySource(_lastSource, kw, page = nextPage, pageSize = _pageSize)
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
                // 批量补充新加载项的封面
                fillMissingCovers(newItems)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "加载更多失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * ⚡ 歌手搜索（网易云 type=100）。
     * - 独立的分页状态（_artistCurrentPage / _lastArtistKeyword）
     * - 不影响歌曲搜索的 results / isEnd 状态
     */
    private var _artistCurrentPage = 1
    private var _lastArtistKeyword: String? = null

    fun searchArtists() {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        // ⚡ 新搜索重置分页状态
        _artistCurrentPage = 1
        _lastArtistKeyword = kw
        _uiState.value = _uiState.value.copy(
            searchingArtists = true,
            artistError = null,
            artistIsEnd = false,
            artistResults = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchApi.searchArtists(kw, page = 1, pageSize = _pageSize)
                _uiState.value = _uiState.value.copy(
                    searchingArtists = false,
                    artistResults = result.list,
                    artistIsEnd = result.isEnd,
                    artistError = if (result.list.isEmpty()) "无相关歌手（请换关键词）" else null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    searchingArtists = false,
                    artistResults = emptyList(),
                    artistIsEnd = true,
                    artistError = "歌手搜索失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * ⚡ 加载下一页歌手搜索结果（无限滚动）
     */
    fun loadMoreArtists() {
        val kw = _lastArtistKeyword?.trim() ?: keyword.trim()
        if (kw.isBlank()) return
        // 防重复：正在搜索/加载更多时不触发；已到最后一页时不触发
        if (_uiState.value.searchingArtists || _uiState.value.isLoadingMore || _uiState.value.artistIsEnd) return
        // 无现有结果时，交给普通 searchArtists()
        if (_uiState.value.artistResults.isEmpty()) return

        val nextPage = _artistCurrentPage + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true, artistError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchApi.searchArtists(kw, page = nextPage, pageSize = _pageSize)
                // ⚡ 追加到现有结果列表，使用 LinkedHashSet 去重
                val existingIds = _uiState.value.artistResults.mapTo(LinkedHashSet()) { it.id }
                val newItems = result.list.filterNot { it.id in existingIds }
                val merged = _uiState.value.artistResults + newItems

                _artistCurrentPage = nextPage
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    artistResults = merged,
                    artistIsEnd = result.isEnd
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    artistError = "加载更多失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * 按所选音源搜索（模仿落雪原版单源搜索方案）：
     * - wy / all（默认）：网易云官方搜索 API（原版，保持不动）
     * - tx / kg / mg（内置源）：落雪同款官方搜索（不依赖 JS，QQ音乐/酷狗/咪咕）
     * - 其他落雪音源：走 JS 引擎 engine.search 搜索该源，不支持搜索时返回空结果
     */
    private suspend fun searchBySource(source: String, kw: String, page: Int, pageSize: Int): LxSearchResult {
        val result = when {
            // 网易云：保持原版官方搜索，不经过 JS 引擎
            source == "wy" || source == "all" -> searchApi.search(kw, page = page, pageSize = pageSize)
            // 内置源：落雪同款官方搜索
            builtInSourceSearchApi.isSupported(source) -> {
                val builtIn = builtInSourceSearchApi.search(source, kw, page, pageSize)
                // 内置源连续失败返回空时（如 QQ 间歇限流），fallback 到 JS 引擎的该源实现，双保险
                if (builtIn.list.isEmpty() && builtIn.total == 0 && page == 1) {
                    runCatching { engine.search(kw, source, page, pageSize) }
                        .getOrNull()
                        ?.takeIf { it.list.isNotEmpty() } ?: builtIn
                } else {
                    builtIn
                }
            }
            // 落雪源：直接走 JS 引擎搜索（不再回退网易云，点击哪个源就搜哪个源）
            else -> engine.search(kw, source, page, pageSize)
        }
        // 统一为每条结果标记音源，供播放时选对音源、UI 提示用
        return result.copy(list = result.list.map { it.copy(source = source) })
    }

    /**
     * 批量补充搜索结果中缺失封面的歌曲。
     * 搜索 API 不返回 picUrl，需通过歌曲详情 API 补全。
     */
    private fun fillMissingCovers(songs: List<LxSongInfo>) {
        val missing = songs.filter { it.pic.isBlank() && it.id.all { c -> c.isDigit() } }
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 每批最多 50 首，避免 URL 过长
            missing.chunked(50).forEach { batch ->
                val covers = runCatching {
                    searchApi.batchGetSongCovers(batch.map { it.id })
                }.getOrDefault(emptyMap())
                if (covers.isEmpty()) return@forEach
                // 更新 UI 状态中的结果列表
                val currentResults = _uiState.value.results
                val updated = currentResults.map { existing ->
                    if (existing.pic.isBlank() && covers.containsKey(existing.id)) {
                        existing.copy(pic = covers[existing.id]!!)
                    } else existing
                }
                if (updated != currentResults) {
                    _uiState.value = _uiState.value.copy(results = updated)
                }
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
                val availableSources = runCatching {
                    engine.getSources().keys.filter { it in listOf("wy", "tx", "kw", "kg", "mg", "qsvip") }
                }.getOrDefault(emptyList())
                // 优先用歌曲自己携带的音源（搜索结果逐条标记），避免切了音源后点播其他源的结果仍走错音源
                val targetSource = when {
                    song.source == "wy" -> "wy"
                    song.source.isNotBlank() &&
                        (availableSources.contains(song.source) || builtInSourceSearchApi.isSupported(song.source)) -> song.source
                    selectedSource != "all" &&
                        (availableSources.contains(selectedSource) || builtInSourceSearchApi.isSupported(selectedSource)) -> selectedSource
                    else -> availableSources.firstOrNull() ?: "wy"
                }
                android.util.Log.d("LxPlaySong", "Target source: $targetSource")

                // 如果 song.pic 为空，在播放前尝试获取封面
                val coverToUse = if (song.pic.isBlank()) {
                    android.util.Log.d("LxPlaySong", "封面为空，尝试从歌曲详情 API 获取...")
                    searchApi.getSongCoverFromDetail(song.id)
                        ?: run {
                            android.util.Log.d("LxPlaySong", "详情 API 无封面，尝试 vkeys...")
                            searchApi.getSongCoverFromVkeys(song.id)
                        } ?: ""
                } else song.pic

                val url = if (targetSource == "wy" && song.id.all { it.isDigit() }) {
                    // 网易云直接走落雪 JS 引擎播放，不经网易云 API 取试听 URL
                    val preferredQuality = try {
                        userPreferencesRepository.musicQualityFlow.first()
                    } catch (_: Exception) {
                        com.theveloper.pixelplay.data.preferences.MusicQuality.HIGH
                    }
                    android.util.Log.d("LxPlaySong", "wy: playing directly via LxJsEngine (quality=${preferredQuality.lxValue})")
                    engine.getPlayUrl("wy", songMap, preferredQuality.lxValue)
                        ?: engine.getPlayUrl("wy", songMap, "24bit")
                        ?: engine.getPlayUrl("wy", songMap, "flac")
                        ?: engine.getPlayUrl("wy", songMap, "320k")
                        ?: engine.getPlayUrl("wy", songMap, "128k")
                } else if (builtInSourceSearchApi.isSupported(targetSource)) {
                    // 内置源（QQ音乐/酷狗/咪咕）：官方播放接口 + 溯音酷我兜底
                    val preferredQuality = try {
                        userPreferencesRepository.musicQualityFlow.first()
                    } catch (_: Exception) {
                        com.theveloper.pixelplay.data.preferences.MusicQuality.HIGH
                    }
                    android.util.Log.d("LxPlaySong", "Built-in source $targetSource, preferredQuality=${preferredQuality.name} (${preferredQuality.lxValue})")
                    builtInSourceSearchApi.resolvePlayUrl(targetSource, song, preferredQuality.lxValue)
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
