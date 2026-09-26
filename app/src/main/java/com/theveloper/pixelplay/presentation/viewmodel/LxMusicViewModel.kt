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
import com.theveloper.pixelplay.data.lx.LxPlaylistInfo
import com.theveloper.pixelplay.data.lx.LxPlaylistSearchResult
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.preferences.MusicQuality
import com.theveloper.pixelplay.data.preferences.MusicQualityCatalog
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LxUiState(
    val engineReady: Boolean = false,
    val version: String = "unknown",
    val sources: Map<String, LxSourceInfo> = emptyMap(),
    /** 已导入的所有 JS 脚本简介列表（多 JS 管理用） */
    val scriptInfos: List<LxScriptInfo> = emptyList(),
    /** 在线音源播放音质值（来自音源脚本 qualitys，如 24bit / FLAC / 320k / 128k） */
    val musicQualityValue: String = MusicQuality.HIGH.lxValue,
    val keyword: String = "",
    val selectedSource: String = "wy",
    val searching: Boolean = false,
    val results: List<LxSongInfo> = emptyList(),
    val error: String? = null,
    val importError: String? = null,
    val initing: Boolean = false,
    val progress: Float? = null,
    val progressLabel: String? = null,
    /** 正在解析播放链接的歌曲 id（搜索页在歌曲名称右侧显示加载提示用） */
    val loadingSongId: String? = null,
    // ⚡ 分页相关字段
    val isEnd: Boolean = true,
    val isLoadingMore: Boolean = false,
    // ⚡ 歌手搜索相关字段
    val searchingArtists: Boolean = false,
    val artistResults: List<LxArtistInfo> = emptyList(),
    val artistIsEnd: Boolean = true,
    val artistError: String? = null,
    // ⚡ 歌单搜索相关字段
    val searchingPlaylists: Boolean = false,
    val playlistResults: List<LxPlaylistInfo> = emptyList(),
    val playlistIsEnd: Boolean = true,
    val playlistError: String? = null,
    /** 正在「保存到本地」的在线歌单 id（其列表项显示加载指示） */
    val savingPlaylistId: String? = null,
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
    private val playlistPreferencesRepository: com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(LxUiState())
    val uiState: StateFlow<LxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 注意：不要再清理"内置音源残留"——内置 JS 早已从 APK 移除，
            // 该清理名单（v4.1 / v9.3 等）与用户自己导入的文件同名，
            // 每次启动会把用户的音源插件删掉 → 直链解析回落到内置源 → 部分歌曲 410。
            autoInitIfPresent()
        }
        // 同步在线音源播放音质
        viewModelScope.launch {
            userPreferencesRepository.musicQualityValueFlow.collect { qualityValue ->
                _uiState.value = _uiState.value.copy(musicQualityValue = qualityValue)
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
    fun setMusicQuality(qualityValue: String) {
        // 立即同步 UI（不等待 DataStore flow 回环，避免点击后无响应）
        _uiState.value = _uiState.value.copy(musicQualityValue = qualityValue)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                userPreferencesRepository.setMusicQualityValue(qualityValue)
            }.onFailure {
                Timber.w(it, "setMusicQuality 持久化失败: $qualityValue")
            }
        }
    }

    // ⚡ 音源临时开关（运行时生效，不持久化）：
    // sourceToggles: source -> enabled；UI 可通过 toggleSource 单独启用/禁用每个音源
    val sourceToggles: StateFlow<Map<String, Boolean>> = engine.sourceToggles

    /** 临时启用/禁用某个音源（禁用后该音源的搜索与播放链接获取都会跳过） */
    fun toggleSource(source: String, enabled: Boolean) {
        engine.setSourceEnabled(source, enabled)
    }

    /** 查询某个 JS 脚本注册了哪些音源（用于音源管理界面展示） */
    fun getInstanceSources(fileName: String): Map<String, LxSourceInfo> =
        engine.instanceSources(fileName)

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

    /** ⚡ 点击搜索结果后的「后台自动补拉后续页入队」任务（新点击/新搜索时取消，避免任务叠加） */
    private var _autoQueueJob: Job? = null

    companion object {
        /** 点击搜索结果后，后台最多自动补拉的页数（防止无限跑飞） */
        private const val AUTO_QUEUE_MAX_PAGES = 10
        /** 后台补拉相邻页之间的让步间隔，避免瞬时爆发网络请求 */
        private const val AUTO_QUEUE_PAGE_DELAY_MS = 250L
        /** 搜索页一次性排入播放队列的歌曲数上限（含点击歌曲自身），避免整页灌入造成卡顿 */
        const val AUTO_QUEUE_MAX_SONGS = 100
    }

    fun search(source: String? = null) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val effectiveSource = source ?: selectedSource
        // ⚡ 新搜索：取消上一次的后台自动补拉任务
        _autoQueueJob?.cancel()
        _autoQueueJob = null
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
     * ⚡ 在线歌单搜索（模仿落雪 songlist 搜索的数据实现）。
     * - wy / all：网易云官方歌单搜索（type=1000）
     * - tx / kg / mg / kw：内置源官方歌单搜索
     * - 其他落雪 JS 源：暂不支持，返回空结果
     * 独立的分页状态（_playlistCurrentPage / _lastPlaylistKeyword），
     * 不影响歌曲搜索与歌手搜索的状态。
     */
    private var _playlistCurrentPage = 1
    private var _lastPlaylistKeyword: String? = null
    /** 最近一次歌单搜索实际使用的音源（供分页加载更多时保持同一音源） */
    private var _lastPlaylistSource: String = "wy"
    private val _playlistPageSize = 15

    fun searchPlaylists(source: String? = null) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val effectiveSource = source ?: selectedSource
        // ⚡ 新搜索重置分页状态
        _playlistCurrentPage = 1
        _lastPlaylistKeyword = kw
        _lastPlaylistSource = effectiveSource
        _uiState.value = _uiState.value.copy(
            searchingPlaylists = true,
            playlistError = null,
            playlistIsEnd = false,
            playlistResults = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchPlaylistsBySource(effectiveSource, kw, page = 1, pageSize = _playlistPageSize)
                _uiState.value = _uiState.value.copy(
                    searchingPlaylists = false,
                    playlistResults = result.list,
                    playlistIsEnd = result.isEnd,
                    playlistError = if (result.list.isEmpty()) "无相关歌单（请换关键词）" else null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    searchingPlaylists = false,
                    playlistResults = emptyList(),
                    playlistIsEnd = true,
                    playlistError = "歌单搜索失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * ⚡ 加载下一页歌单搜索结果（无限滚动）
     */
    fun loadMorePlaylists() {
        val kw = _lastPlaylistKeyword?.trim() ?: keyword.trim()
        if (kw.isBlank()) return
        // 防重复：正在搜索/加载更多时不触发；已到最后一页时不触发
        if (_uiState.value.searchingPlaylists || _uiState.value.isLoadingMore || _uiState.value.playlistIsEnd) return
        if (_uiState.value.playlistResults.isEmpty()) return

        val nextPage = _playlistCurrentPage + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true, playlistError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchPlaylistsBySource(_lastPlaylistSource, kw, page = nextPage, pageSize = _playlistPageSize)
                // ⚡ 追加到现有结果列表，使用 LinkedHashSet 去重
                val existingIds = _uiState.value.playlistResults.mapTo(LinkedHashSet()) { it.id }
                val newItems = result.list.filterNot { it.id in existingIds }

                _playlistCurrentPage = nextPage
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    playlistResults = _uiState.value.playlistResults + newItems,
                    playlistIsEnd = result.isEnd
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    playlistError = "加载更多失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * 拉取在线歌单歌曲的统一入口（供「保存到本地」与「整队入列」共用）：
     * - wy / all：走网易云官方歌单详情（[LxSearchApi.getPlaylistSongs]）
     * - tx / kg / mg / kw 等内置源：走 [BuiltInSourceSearchApi.getPlaylistSongs]
     */
    private suspend fun fetchPlaylistSongs(
        playlist: LxPlaylistInfo,
        page: Int = 1,
        pageSize: Int = 1000
    ): LxSearchResult {
        val source = playlist.source.ifBlank { "wy" }
        val result = if (source != "wy" && source != "all" && builtInSourceSearchApi.isSupported(source)) {
            builtInSourceSearchApi.getPlaylistSongs(source, playlist.id, page, pageSize)
        } else {
            searchApi.getPlaylistSongs(playlist.id, source)
        }
        // ⚡ 统一标记音源：内置源返回的歌曲可能不带 source，落库时会被误判为网易云
        //   （酷狗/QQ 等平台的数字 id 会被存成 netease://{假id} → 播放时永远解析失败）。
        //   以歌单自身来源为准补齐，保证保存到本地后能走对解析链路。
        return result.copy(list = result.list.map { it.copy(source = source) })
    }

    // ── 在线歌单：保存到本地 / 整队入列 ─────────────────────────────────

    /**
     * ⚡ 把在线歌单保存为本地歌单：拉取歌单歌曲 → 逐首写入统一媒体库（生成可播放的 Song）
     * → 创建本地歌单，之后可从「本地歌单」进入 playlist_detail 路由查看/播放。
     */
    fun savePlaylistToLocal(
        playlist: LxPlaylistInfo,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (_uiState.value.savingPlaylistId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(savingPlaylistId = playlist.id)
            var savedCount = 0
            try {
                val result = fetchPlaylistSongs(playlist)
                if (result.list.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "获取歌单歌曲失败，请稍后重试")
                    }
                    return@launch
                }

                val songIds = ArrayList<String>(result.list.size)
                result.list.forEach { song ->
                    val saved = runCatching {
                        // ⚡ 缺失 source 时以歌单自身来源兜底（而非一律 "wy"），防止非网易云
                        //   平台的歌曲被误存成 netease://{id} 导致播放解析失败
                        musicRepository.saveCloudSong(
                            song.copy(source = song.source.ifBlank { playlist.source.ifBlank { "wy" } })
                        )
                    }.getOrNull()
                    if (saved != null) songIds.add(saved.toString())
                }
                savedCount = songIds.size
                if (songIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "保存歌曲失败，请稍后重试")
                    }
                    return@launch
                }

                playlistPreferencesRepository.createPlaylist(
                    name = playlist.name.ifBlank { "在线歌单" },
                    songIds = songIds,
                    coverImageUri = playlist.cover.ifBlank { null },
                    source = "ONLINE"
                )
                withContext(Dispatchers.Main) {
                    onResult(true, "已保存到本地歌单「${playlist.name}」($savedCount 首)")
                }
            } catch (t: Throwable) {
                Timber.e(t, "savePlaylistToLocal failed")
                withContext(Dispatchers.Main) {
                    onResult(false, "保存失败: ${t.message ?: t.javaClass.simpleName}")
                }
            } finally {
                _uiState.value = _uiState.value.copy(savingPlaylistId = null)
            }
        }
    }

    private suspend fun searchPlaylistsBySource(
        source: String,
        kw: String,
        page: Int,
        pageSize: Int
    ): LxPlaylistSearchResult {
        val result = when {
            source == "wy" || source == "all" -> searchApi.searchPlaylists(kw, page = page, pageSize = pageSize)
            builtInSourceSearchApi.isSupported(source) ->
                builtInSourceSearchApi.searchPlaylists(source, kw, page, pageSize)
            else -> LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
        }
        // 网易云结果已自带 source，其余统一标记为所选音源
        return if (source == "wy" || source == "all") result
        else result.copy(list = result.list.map { it.copy(source = source) })
    }

    /**
     * 按所选音源搜索（模仿落雪原版单源搜索方案，支持 wy/tx/kg/mg 等内置源与落雪 JS 源）：
     * - wy / all（默认）：网易云官方搜索 API（原版，保持不动；仅播放走落雪）
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
                    progressLabel = "获取播放链接…",
                    loadingSongId = song.id
                )
                val resolved = resolvePlayableSong(song)
                android.util.Log.d("LxPlaySong", "Resolved URL: ${resolved?.url}, cover: ${resolved?.cover}")

                _uiState.value = _uiState.value.copy(progressLabel = "正在打开播放器…")

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(progress = null, progressLabel = null, loadingSongId = null)
                    if (resolved == null) {
                        android.util.Log.w("LxPlaySong", "URL is null, showing error (song=${song.id})")
                        _uiState.value = _uiState.value.copy(
                            error = "无法获取播放链接（音源: ${resolved?.source ?: "unknown"}），请换一首或换音源"
                        )
                        return@withContext
                    }
                    android.util.Log.d("LxPlaySong", "Calling onOpenPlayer with URL length: ${resolved.url.length}, songId: ${resolved.savedSongId}")
                    onOpenPlayer(resolved.url, song.name, song.singer, resolved.cover, resolved.savedSongId)
                }
            } catch (t: Throwable) {
                android.util.Log.e("LxPlaySong", "Error: ${t.message}", t)
                _uiState.value = _uiState.value.copy(
                    progress = null, progressLabel = null, loadingSongId = null,
                    error = "播放失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    private data class LxResolvedPlayable(
        val url: String,
        val cover: String,
        val savedSongId: String,
        val source: String
    )

    /**
     * 解析一首落雪歌曲的可播放直链（音源选择 → 封面补全 → URL 解析）。
     * 不更新 UI 状态，供 [playSong] 与 [enqueueAllSearchResults] 共用（静默批量解析）。
     *
     * @param persist 是否把歌曲写入统一媒体库。点播时传 true（真正播放的歌曲才入库）；
     *                排队预解析时传 false（仅解析 URL，不落库，避免把整个搜索结果灌进媒体库）。
     */
    private suspend fun resolvePlayableSong(song: LxSongInfo, persist: Boolean = true): LxResolvedPlayable? {
        val songMap = song.toInfoMap()
        // ⚡ 与媒体库播放（DualPlayerEngine.resolveCloudLxUriAsync）对齐：先确保 JS 音源引擎就绪。
        //    冷启动瞬间 getSources() 为空会被误判成"没装插件"而退化到内置源（酷我官方直链，
        //    部分歌曲带"酷我音乐已为您开启免费听歌权限"语音）。未导入任何脚本时立即返回。
        runCatching { engine.awaitReady(15_000) }
        val lxReady = engine.isReady()
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
            searchApi.getSongCoverFromDetail(song.id)
                ?: searchApi.getSongCoverFromVkeys(song.id)
                ?: ""
        } else song.pic

        val preferredQuality = runCatching { userPreferencesRepository.musicQualityValueFlow.first() }
            .getOrDefault(MusicQuality.HIGH.lxValue)
        val url = if (targetSource == "wy" && song.id.all { it.isDigit() }) {
            // 网易云直接走落雪 JS 引擎播放，不经网易云 API 取试听 URL
            android.util.Log.d("LxPlaySong", "wy: playing directly via LxJsEngine (quality=$preferredQuality)")
            resolvePlayUrlWithQualityChain("wy", songMap, preferredQuality)
        } else if (builtInSourceSearchApi.isSupported(targetSource)) {
            // ★ 插件优先、内置源兜底（与媒体库播放 DualPlayerEngine 保持一致）：
            //   内置源 tx/mg/kw 会落到酷我官方直链，部分歌曲的试听流带
            //   "酷我音乐已为您开启免费听歌权限"语音；用户导入的 JS 插件
            //   （全豆要 v9.x 等，内部自带星海/溯音多源 fallback）返回的直链无此问题。
            val pluginUrl = if (lxReady) {
                resolvePlayUrlWithQualityChain(targetSource, songMap, preferredQuality)
            } else null
            if (pluginUrl != null) {
                android.util.Log.d("LxPlaySong", "Built-in source $targetSource resolved via LxJsEngine (plugin first)")
                pluginUrl
            } else {
                android.util.Log.d("LxPlaySong", "Built-in source $targetSource, fallback to builtIn (quality=$preferredQuality)")
                builtInSourceSearchApi.resolvePlayUrl(targetSource, song, preferredQuality)
            }
        } else {
            resolvePlayUrlWithQualityChain(targetSource, songMap, preferredQuality)
        }
        if (url == null) return null

        // ── 是否写入统一媒体库
        // 点播（persist=true）：保存歌曲到数据库，使用返回的真实 song id；
        // 排队预解析（persist=false）：只返回稳定 id，不落库，避免搜索结果整页灌入媒体库
        val savedSongId = if (persist) {
            val songWithCover = if (coverToUse.isNotBlank() && song.pic.isBlank()) {
                song.copy(pic = coverToUse, source = targetSource)
            } else {
                song.copy(source = targetSource)
            }
            try {
                musicRepository.saveCloudSong(songWithCover).toString()
            } catch (t: Throwable) {
                android.util.Log.w("LxPlaySong", "saveCloudSong 失败: ${t.message}")
                getStableSongId(song)
            }
        } else {
            getStableSongId(song)
        }
        android.util.Log.d("LxPlaySong", "Saved song ID: $savedSongId, source: $targetSource")
        return LxResolvedPlayable(url = url, cover = coverToUse, savedSongId = savedSongId, source = targetSource)
    }

    /**
     * 搜索整队播放：点击某首结果后，把当前搜索结果的其余歌曲逐首静默解析并追加到播放队列。
     * 自动切下一曲时即可按搜索结果顺序依次播放。
     */
    fun enqueueAllSearchResults(
        clickedSongId: String,
        onEnqueue: (url: String, title: String, artist: String, cover: String, songId: String) -> Unit
    ) {
        enqueueSongsList(_uiState.value.results, clickedSongId, onEnqueue)
    }

    /**
     * 把给定歌曲列表（除点击的 [excludeId] 外）全部排入播放队列。
     * ⚡ 不立即解析 URL，改为构造 cloud://lx/{json} 占位 URI，
     *    由 DualPlayerEngine 的 ResolvingDataSource 在歌曲实际播放时才解析新鲜直链。
     *    避免搜索结果批量入队时链接过期导致后续歌曲无法播放。
     */
    fun enqueueSongsList(
        songs: List<LxSongInfo>,
        excludeId: String,
        onEnqueue: (url: String, title: String, artist: String, cover: String, songId: String) -> Unit
    ) {
        if (songs.size <= 1) return
        viewModelScope.launch(Dispatchers.IO) {
            songs.filter { getStableSongId(it) != excludeId }.forEach { song ->
                val placeholderUrl = buildLxPlaceholderUri(song, pickSourceForSong(song))
                val stableId = getStableSongId(song)
                withContext(Dispatchers.Main) {
                    onEnqueue(placeholderUrl, song.name, song.singer, song.pic, stableId)
                }
            }
        }
    }

    /** 为歌曲选定解析音源：歌曲自带 → 用户所选 → 引擎已注册源兜底 */
    private fun pickSourceForSong(song: LxSongInfo): String {
        val availableSources = runCatching {
            engine.getSources().keys.filter { it in listOf("wy", "tx", "kw", "kg", "mg", "qsvip") }
        }.getOrDefault(emptyList())
        return when {
            song.source.isNotBlank() && (availableSources.contains(song.source) ||
                builtInSourceSearchApi.isSupported(song.source)) -> song.source
            selectedSource != "all" && (availableSources.contains(selectedSource) ||
                builtInSourceSearchApi.isSupported(selectedSource)) -> selectedSource
            else -> availableSources.firstOrNull() ?: "wy"
        }
    }

    /** 构造 cloud://lx/{json} 占位 URI：由播放引擎在实际播放时才解析新鲜直链 */
    private fun buildLxPlaceholderUri(song: LxSongInfo, targetSource: String): String {
        val songJson = org.json.JSONObject().apply {
            put("id", song.id)
            put("songmid", song.songmid)
            put("hash", song.hash)
            put("name", song.name)
            put("singer", song.singer)
            put("artistIds", song.artistIds)
            put("album", song.albumName)
            put("pic", song.pic)
            put("duration", song.duration)
            put("source", targetSource)
        }
        val encoded = java.net.URLEncoder.encode(songJson.toString(), "UTF-8")
            .replace("+", "%20")
        return "cloud://lx/$encoded"
    }

    /** 队列种子：url 可为真实直链或 cloud://lx 占位 URI，由 PlayerViewModel.buildCloudSong 转为 Song */
    data class CloudQueueSeed(
        val url: String,
        val title: String,
        val artist: String,
        val cover: String,
        val songId: String,
        val bilibiliBvid: String? = null
    )

    /**
     * ⚡ 供「在线歌单详情页」按需拉取歌曲：返回 cloud://lx 占位种子（不落库、不产生媒体库副作用）。
     * 统一封装「选音源 + 占位 URI + 稳定 id」逻辑。
     */
    suspend fun fetchPlaylistSeeds(playlist: LxPlaylistInfo): List<CloudQueueSeed> {
        val songs = runCatching { fetchPlaylistSongs(playlist) }.getOrNull()?.list.orEmpty()
        if (songs.isEmpty()) return emptyList()
        return songs.map { song ->
            val source = pickSourceForSong(song)
            CloudQueueSeed(
                url = buildLxPlaceholderUri(song, source),
                title = song.name,
                artist = song.singer,
                cover = song.pic,
                songId = getStableSongId(song)
            )
        }
    }

    /**
     * ⚡ 搜索结果点击播放 + 整列入队（原子建队，修复竞态）：
     * 解析 [song] 的真实直链后，把「点击歌曲 + 其余搜索结果占位」一次性交给 [onPlayQueue]
     * 构建完整队列。此前 playSong（playUrl 会重置队列）与 enqueueAllSearchResults（逐首
     * 追加）并发执行，先后顺序不定导致播放列表时而只剩单曲、时而丢失部分结果。
     *
     * ⚡ 传入 [onMoreSeeds] 时，还会在后台**按页续拉**尚未加载的搜索结果并增量追加到队列，
     *    使用户点一首歌即可顺序播放"全部搜索结果"，而不止当前已加载的那一页。
     */
    fun playSearchResultWithQueue(
        song: LxSongInfo,
        onPlayQueue: (List<CloudQueueSeed>, Int) -> Unit,
        onMoreSeeds: ((List<CloudQueueSeed>) -> Unit)? = null
    ) {
        val results = _uiState.value.results
        val index = results.indexOfFirst { getStableSongId(it) == getStableSongId(song) }.takeIf { it >= 0 } ?: 0
        playSongListWithQueue(results, index, onPlayQueue)
        if (onMoreSeeds != null) {
            // ⚡ 首次建队已排入的歌曲数（受上限约束），剩余配额再交给后台续拉补齐到上限为止
            val queuedInFirstBatch = results.size.coerceAtMost(AUTO_QUEUE_MAX_SONGS)
            scheduleAutoQueueFill(song, (AUTO_QUEUE_MAX_SONGS - queuedInFirstBatch).coerceAtLeast(0), onMoreSeeds)
        }
    }

    /**
     * ⚡ 后台「自动补拉后续分页并追加进队列」：
     * 点击搜索结果后，从当前已加载的下一页开始，逐页拉取后续搜索结果并增量交给 [onMoreSeeds]，
     * 直到补齐 [remainingSlots] 首或搜索结果耗尽为止。
     *
     * 性能优化：
     * - 独立 [Job]，新点击/新搜索时取消旧任务，避免任务叠加
     * - 逐页串行 + 页间让步延迟，避免瞬时爆发网络请求造成卡顿
     * - 以 stable id 去重（含已加载结果 + 已追加结果），不产生重复歌曲
     * - [remainingSlots] + [AUTO_QUEUE_MAX_PAGES] 双重限制，队列总数不超过 [AUTO_QUEUE_MAX_SONGS]
     */
    private fun scheduleAutoQueueFill(
        clicked: LxSongInfo,
        remainingSlots: Int,
        onMoreSeeds: (List<CloudQueueSeed>) -> Unit
    ) {
        if (remainingSlots <= 0) return
        val kw = _lastKeyword?.trim()?.takeIf { it.isNotBlank() }
            ?: keyword.trim().takeIf { it.isNotBlank() }
        if (kw.isNullOrBlank() || _uiState.value.isEnd) return
        val source = _lastSource
        val startPage = _currentPage + 1
        _autoQueueJob?.cancel()
        _autoQueueJob = viewModelScope.launch(Dispatchers.IO) {
            // 去重集合：已加载结果 + 点击歌曲自身，避免把已入队的歌曲重复追加
            val seen = _uiState.value.results.mapTo(LinkedHashSet()) { getStableSongId(it) }
            seen.add(getStableSongId(clicked))
            var page = startPage
            var fetchedPages = 0
            var appended = 0
            while (fetchedPages < AUTO_QUEUE_MAX_PAGES && appended < remainingSlots) {
                delay(AUTO_QUEUE_PAGE_DELAY_MS)
                val result = runCatching {
                    searchBySource(source, kw, page = page, pageSize = _pageSize)
                }.getOrElse { return@launch }
                val fresh = result.list
                    .filter { seen.add(getStableSongId(it)) }
                    .take(remainingSlots - appended)
                if (fresh.isNotEmpty()) {
                    val seeds = fresh.map { s ->
                        CloudQueueSeed(
                            url = buildLxPlaceholderUri(s, pickSourceForSong(s)),
                            title = s.name,
                            artist = s.singer,
                            cover = s.pic,
                            songId = getStableSongId(s)
                        )
                    }
                    appended += seeds.size
                    withContext(Dispatchers.Main) { onMoreSeeds(seeds) }
                }
                fetchedPages++
                page++
                if (result.isEnd) break
            }
        }
    }

    /**
     * 解析 [songs] 中 [startIndex] 这一首后原子建队：
     * 点击歌曲用真实直链，其余歌曲用 cloud://lx 占位懒解析。
     * 由搜索结果、在线歌单详情页共用。
     *
     * ⚡ 不落库（persist=false）：搜索结果只进播放队列，不写入统一媒体库；
     *    只有用户主动收藏的歌曲才会通过 [toggleFavoriteForSong] 落库。
     * ⚡ 队列长度受 [AUTO_QUEUE_MAX_SONGS] 约束，避免一次性排入过多歌曲造成卡顿。
     */
    fun playSongListWithQueue(
        songs: List<LxSongInfo>,
        startIndex: Int,
        onPlayQueue: (List<CloudQueueSeed>, Int) -> Unit
    ) {
        val clicked = songs.getOrNull(startIndex) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                progress = 0.2f,
                progressLabel = "获取播放链接…",
                loadingSongId = clicked.id
            )
            val resolved = resolvePlayableSong(clicked, persist = false)
            if (resolved == null) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        progress = null, progressLabel = null, loadingSongId = null,
                        error = "无法获取播放链接（音源: ${clicked.source.ifBlank { "unknown" }}），请换一首或换音源"
                    )
                }
                return@launch
            }
            val clickedKey = getStableSongId(clicked)
            val seeds = ArrayList<CloudQueueSeed>(songs.size.coerceAtMost(AUTO_QUEUE_MAX_SONGS))
            // 点击的歌曲：真实直链（不落库，savedSongId 即稳定 id）
            seeds.add(CloudQueueSeed(resolved.url, clicked.name, clicked.singer, resolved.cover, resolved.savedSongId))
            for ((index, s) in songs.withIndex()) {
                if (seeds.size >= AUTO_QUEUE_MAX_SONGS) break
                if (index == startIndex || getStableSongId(s) == clickedKey) continue
                val source = pickSourceForSong(s)
                seeds.add(CloudQueueSeed(buildLxPlaceholderUri(s, source), s.name, s.singer, s.pic, getStableSongId(s)))
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(progress = null, progressLabel = null, loadingSongId = null)
                onPlayQueue(seeds, 0)
            }
        }
    }

    /**
     * 按用户选择的音质**向下递减**尝试落雪播放链接，动态识别音源脚本注册的 qualitys，
     * 严格遵守音质设置（128k 不自动抬音质，避免用户选 128k 却被拉到高音质）。
     */
    private suspend fun resolvePlayUrlWithQualityChain(
        source: String,
        songMap: Map<String, Any?>,
        preferred: String
    ): String? {
        val chain = MusicQualityCatalog.resolveChain(
            target = preferred,
            available = engine.getSources()[source]?.qualitys.orEmpty()
        )
        for (q in chain) {
            val url = engine.getPlayUrl(source, songMap, q)
            if (!url.isNullOrBlank()) {
                android.util.Log.d("LxPlaySong", "quality chain hit: $source/$q")
                return url
            }
        }
        return null
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

    private fun LxSongInfo.toInfoMap(): Map<String, Any?> {
        // 多歌手支持：singer 是 "、" 连接的显示串；artists/artistIds 按 lx-music
        // 协议传给 JS 引擎（数组），避免脚本读取 musicInfo.artists 时拿到字符串导致失败
        val idList = artistIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val nameList = com.theveloper.pixelplay.data.stream.CloudMusicUtils.parseArtistNames(singer)
        val artistsArray = nameList.mapIndexed { index, name ->
            mapOf("id" to idList.getOrNull(index).orEmpty(), "name" to name)
        }
        return mapOf(
            "id" to id,
            "vid" to id,
            "songmid" to (songmid.ifBlank { id }),
            "hash" to (hash.ifBlank { id }),
            "name" to name,
            "singer" to singer,
            "artist" to singer,
            "artists" to artistsArray,
            "artistIds" to idList,
            "album" to albumName,
            "albumName" to albumName,
            "duration" to duration,
            "cover" to pic,
            "pic" to pic,
        )
    }
}
