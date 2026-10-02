package com.theveloper.pixelplay.presentation.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.data.kugou.KugouAudiobookApi
import com.theveloper.pixelplay.data.kugou.KugouAudiobookChapter
import com.theveloper.pixelplay.data.kugou.KugouAudiobookTag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 书架推荐分区。 */
enum class AudiobookSectionKind { DAILY, RANK, WEEK, VIP }

/** 听书书架（推荐分区）状态。 */
data class AudiobookHomeUiState(
    val loading: Boolean = true,
    val daily: List<KugouAudiobookAlbum> = emptyList(),
    val rank: List<KugouAudiobookAlbum> = emptyList(),
    val week: List<KugouAudiobookAlbum> = emptyList(),
    val vip: List<KugouAudiobookAlbum> = emptyList(),
    val error: Boolean = false,
) {
    val hasAnyData: Boolean
        get() = daily.isNotEmpty() || rank.isNotEmpty() || week.isNotEmpty() || vip.isNotEmpty()

    fun albumsOf(kind: AudiobookSectionKind): List<KugouAudiobookAlbum> = when (kind) {
        AudiobookSectionKind.DAILY -> daily
        AudiobookSectionKind.RANK -> rank
        AudiobookSectionKind.WEEK -> week
        AudiobookSectionKind.VIP -> vip
    }
}

/** 免费书库（筛选 + 分页）状态。 */
data class AudiobookLibraryUiState(
    val tags: List<KugouAudiobookTag> = emptyList(),
    val tagId: Int = DEFAULT_TAG_ID,
    val sort: Int = 0,
    val gender: Int = 0,
    val status: Int = 0,
    val albums: List<KugouAudiobookAlbum> = emptyList(),
    /** 初始即视为加载中：进入书库立刻显示进度圈，避免先闪一帧「空」。 */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
) {
    companion object {
        /** 906 = 有声小说（全部）；其余分类由标签接口动态补。 */
        const val DEFAULT_TAG_ID = 906
    }
}

/** 专辑详情（章节列表）状态。 */
data class AudiobookDetailUiState(
    val albumId: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val author: String? = null,
    val chapterCount: Int = 0,
    val intro: String? = null,
    val chapters: List<KugouAudiobookChapter> = emptyList(),
    /** 首屏章节加载中 */
    val loading: Boolean = true,
    /** 后台自动翻页拉全部章节中 */
    val autoLoading: Boolean = false,
    val error: Boolean = false,
)

/** 搜索页状态。 */
data class AudiobookSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<KugouAudiobookAlbum> = emptyList(),
)

/**
 * 听书（酷狗长音频）ViewModel：
 * 书架四个推荐分区 / 免费书库（分类 + 排序 + 男频女频 + 连载状态分页）/ 专辑章节 / 搜索。
 *
 * 数据源 [KugouAudiobookApi]（匿名可用）；付费章节由接口 `fail_process` 标识，
 * 统一在 [AudiobookDetailUiState.chapters] 里过滤掉（不展示、不参与播放）。
 */
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val api: KugouAudiobookApi,
) : ViewModel() {

    private val _home = MutableStateFlow(AudiobookHomeUiState())
    val home: StateFlow<AudiobookHomeUiState> = _home.asStateFlow()

    private val _library = MutableStateFlow(AudiobookLibraryUiState())
    val library: StateFlow<AudiobookLibraryUiState> = _library.asStateFlow()

    private val _detail = MutableStateFlow(AudiobookDetailUiState())
    val detail: StateFlow<AudiobookDetailUiState> = _detail.asStateFlow()

    private val _search = MutableStateFlow(AudiobookSearchUiState())
    val search: StateFlow<AudiobookSearchUiState> = _search.asStateFlow()

    private var homeJob: Job? = null
    private var libraryJob: Job? = null
    private var detailJob: Job? = null
    private var searchJob: Job? = null
    private var tagsRequested = false

    // ─── 书架 ────────────────────────────────────────────────────────────

    /** 拉四个推荐分区（并行）；[force] 下拉刷新时重拉。 */
    fun loadHome(force: Boolean = false) {
        if (homeJob?.isActive == true) return
        if (!force && !_home.value.loading && _home.value.hasAnyData) return
        homeJob = viewModelScope.launch {
            _home.update { it.copy(loading = true, error = false) }
            val daily = async { api.fetchDailyRecommend().getOrDefault(emptyList()) }
            val rank = async { api.fetchRankRecommend().getOrDefault(emptyList()) }
            val week = async { api.fetchWeekRecommend().getOrDefault(emptyList()) }
            val vip = async { api.fetchVipRecommend().getOrDefault(emptyList()) }
            val d = daily.await()
            val r = rank.await()
            val w = week.await()
            val v = vip.await()
            _home.value = AudiobookHomeUiState(
                loading = false,
                daily = d,
                rank = r,
                week = w,
                vip = v,
                // 四个分区全空 → 视为加载失败 / 无数据（UI 给重试）
                error = d.isEmpty() && r.isEmpty() && w.isEmpty() && v.isEmpty(),
            )
        }
    }

    // ─── 免费书库 ────────────────────────────────────────────────────────

    /** 首次进入书库：拉分类标签 + 第一页。 */
    fun ensureLibraryLoaded() {
        if (!tagsRequested) {
            tagsRequested = true
            viewModelScope.launch {
                api.fetchTags().onSuccess { tags ->
                    if (tags.isNotEmpty()) {
                        _library.update { state -> state.copy(tags = tags) }
                    }
                }
            }
            reloadLibrary()
        }
    }

    fun setLibraryTag(tagId: Int) {
        if (_library.value.tagId == tagId) return
        _library.update { it.copy(tagId = tagId) }
        reloadLibrary()
    }

    fun setLibrarySort(sort: Int) {
        if (_library.value.sort == sort) return
        _library.update { it.copy(sort = sort) }
        reloadLibrary()
    }

    fun setLibraryGender(gender: Int) {
        if (_library.value.gender == gender) return
        _library.update { it.copy(gender = gender) }
        reloadLibrary()
    }

    fun setLibraryStatus(status: Int) {
        if (_library.value.status == status) return
        _library.update { it.copy(status = status) }
        reloadLibrary()
    }

    /** 筛选变化：清空列表重置回第一页（列表整体进入加载态）。 */
    fun reloadLibrary() = loadLibraryFirstPage(clearExisting = true)

    /** 下拉刷新：保留现有列表，只转顶部指示器；失败也不清空。 */
    fun refreshLibrary() = loadLibraryFirstPage(clearExisting = false)

    private fun loadLibraryFirstPage(clearExisting: Boolean) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            val state = _library.value
            _library.update {
                it.copy(
                    loading = true,
                    albums = if (clearExisting) emptyList() else it.albums,
                    loadingMore = false,
                )
            }
            api.fetchFreeAlbums(
                tagId = state.tagId,
                sort = state.sort,
                gender = state.gender,
                status = state.status,
                page = 1,
                pageSize = LIBRARY_PAGE_SIZE,
            ).onSuccess { result ->
                _library.update {
                    it.copy(loading = false, albums = result.albums, page = 1, hasMore = result.hasMore)
                }
            }.onFailure {
                _library.update { it.copy(loading = false, hasMore = if (clearExisting) false else it.hasMore) }
            }
        }
    }

    /** 滚动到底加载下一页。 */
    fun loadMoreLibrary() {
        val state = _library.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        libraryJob = viewModelScope.launch {
            _library.update { it.copy(loadingMore = true) }
            api.fetchFreeAlbums(
                tagId = state.tagId,
                sort = state.sort,
                gender = state.gender,
                status = state.status,
                page = state.page + 1,
                pageSize = LIBRARY_PAGE_SIZE,
            ).onSuccess { result ->
                _library.update {
                    it.copy(
                        loadingMore = false,
                        albums = it.albums + result.albums,
                        page = it.page + 1,
                        hasMore = result.hasMore,
                    )
                }
            }.onFailure {
                _library.update { it.copy(loadingMore = false, hasMore = false) }
            }
        }
    }

    // ─── 专辑详情 ────────────────────────────────────────────────────────

    /**
     * 打开专辑详情：拉简介 + 章节列表（第一页 50 条），随后后台自动翻页拉全
     * （参考项目同款行为：整本书一次性入列，「播放全部」才是整本）。
     */
    fun loadAlbum(
        albumId: String,
        title: String,
        coverUrl: String?,
        author: String?,
        chapterCount: Int = 0,
    ) {
        if (albumId.isBlank()) return
        if (detailJob?.isActive == true && _detail.value.albumId == albumId) return
        if (_detail.value.albumId == albumId && _detail.value.chapters.isNotEmpty()) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detail.value = AudiobookDetailUiState(
                albumId = albumId,
                title = title,
                coverUrl = coverUrl,
                author = author,
                chapterCount = chapterCount,
                loading = true,
            )
            // 简介单独拉，失败不影响章节浏览
            launch {
                api.fetchAlbumDetail(albumId).onSuccess { album ->
                    if (album == null || _detail.value.albumId != albumId) return@onSuccess
                    _detail.update { state ->
                        state.copy(
                            title = state.title.ifBlank { album.name },
                            coverUrl = state.coverUrl?.takeIf { it.isNotBlank() } ?: album.coverUrl,
                            author = state.author?.takeIf { !it.isNullOrBlank() } ?: album.author,
                            chapterCount = if (state.chapterCount > 0) state.chapterCount else album.chapterCount,
                            intro = album.intro,
                        )
                    }
                }
            }
            val firstPage = api.fetchAlbumChapters(albumId, page = 1, pageSize = CHAPTER_PAGE_SIZE)
            firstPage.onSuccess { chapters ->
                val playable = chapters.filter { it.canPlay }
                _detail.update { it.copy(loading = false, chapters = playable, error = false) }
                if (chapters.size >= CHAPTER_PAGE_SIZE) autoLoadRemainingChapters(albumId)
            }.onFailure {
                _detail.update { it.copy(loading = false, error = it.chapters.isEmpty()) }
            }
        }
    }

    fun retryLoadAlbum() {
        val state = _detail.value
        if (state.albumId.isBlank()) return
        _detail.value = AudiobookDetailUiState()
        loadAlbum(state.albumId, state.title, state.coverUrl, state.author, state.chapterCount)
    }

    /** 后台把所有分页拉完（上限 [MAX_CHAPTER_PAGES] 页，防止异常接口死循环）。 */
    private fun autoLoadRemainingChapters(albumId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(autoLoading = true) }
            var page = 2
            while (page <= MAX_CHAPTER_PAGES) {
                if (_detail.value.albumId != albumId) break
                val items = api.fetchAlbumChapters(albumId, page = page, pageSize = CHAPTER_PAGE_SIZE)
                    .getOrNull()
                    ?: break
                if (_detail.value.albumId != albumId) break
                val playable = items.filter { it.canPlay }
                _detail.update { it.copy(chapters = it.chapters + playable) }
                if (items.size < CHAPTER_PAGE_SIZE) break
                page++
            }
            // 只清当前专辑的标记：翻页期间用户可能已经切到别的专辑了
            if (_detail.value.albumId == albumId) {
                _detail.update { it.copy(autoLoading = false) }
            }
        }
    }

    // ─── 搜索 ────────────────────────────────────────────────────────────

    fun search(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _search.update { it.copy(query = kw, searching = true, searched = true, results = emptyList()) }
            val results = api.searchAlbums(kw).getOrDefault(emptyList())
            _search.update { it.copy(searching = false, results = results) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = AudiobookSearchUiState()
    }

    private companion object {
        const val LIBRARY_PAGE_SIZE = 20
        const val CHAPTER_PAGE_SIZE = 50
        const val MAX_CHAPTER_PAGES = 40
    }
}
