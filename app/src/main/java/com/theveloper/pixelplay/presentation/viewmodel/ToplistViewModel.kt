package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.toplist.ToplistCatalog
import com.theveloper.pixelplay.data.toplist.ToplistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 排行榜 ViewModel：多平台（网易云/QQ音乐/酷狗/酷我/咪咕）榜单。
 *
 * 数据流对齐落雪音乐 core/leaderboard.ts：
 * - 平台一次拉全量 → 内存缓存（ToplistRepository，key=entryId）
 * - UI 按 30 条/页切片，滚动到底触发 loadMore() 纯本地切片（无网络等待）
 * - 切换平台/榜单：缓存命中秒开，未命中后台拉取
 */
@HiltViewModel
class ToplistViewModel @Inject constructor(
    private val toplistRepository: ToplistRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        /** 首页区块 + 详情页统一按 30 条/页切片 */
        const val PAGE_SIZE = 30
    }

    data class UiState(
        val enabled: Boolean = true,
        val selectedPlatform: ToplistCatalog.Platform = ToplistCatalog.Platform.WY,
        val selectedToplistId: String = ToplistCatalog.DEFAULT_TOPLIST_ID,
        val allSongs: List<Song> = emptyList(),
        val displaySongs: List<Song> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val hasMore: Boolean = false
    ) {
        val selectedEntry: ToplistCatalog.Entry? = ToplistCatalog.findEntry(selectedToplistId)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferencesRepository.homeTopListEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(enabled = enabled) }
                // ⚡ 性能：开关关闭时不拉取默认榜单（避免白费网络请求）；
                // 仅当榜单歌曲尚未加载时触发（后续开关变化不再重复拉取）
                if (enabled && _uiState.value.allSongs.isEmpty()) {
                    ensureLoaded(ToplistCatalog.DEFAULT_TOPLIST_ID)
                }
            }
        }
    }

    /** 切换平台：自动选中该平台第一个榜单 */
    fun selectPlatform(platform: ToplistCatalog.Platform) {
        val current = _uiState.value
        if (current.selectedPlatform == platform) return
        val firstEntry = ToplistCatalog.entriesFor(platform).firstOrNull()
            ?: return
        _uiState.update { it.copy(selectedPlatform = platform) }
        selectToplist(firstEntry.id)
    }

    /** 切换榜单（带内存缓存秒开） */
    fun selectToplist(toplistId: String) {
        val current = _uiState.value
        if (current.selectedToplistId == toplistId && current.allSongs.isNotEmpty()) return
        // 缓存命中：立即展示，不阻塞 UI
        toplistRepository.cached(toplistId)?.let { cached ->
            _uiState.update {
                it.copy(
                    selectedToplistId = toplistId,
                    selectedPlatform = ToplistCatalog.fromPlatform(toplistId),
                    allSongs = cached,
                    displaySongs = cached.take(PAGE_SIZE),
                    isLoading = false,
                    error = null,
                    hasMore = cached.size > PAGE_SIZE
                )
            }
            return
        }
        ensureLoaded(toplistId)
    }

    /** 无限下滑：本地切片追加一页（纯内存操作，无网络等待） */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        if (state.displaySongs.size >= state.allSongs.size) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }
        _uiState.update {
            val next = it.allSongs.take(it.displaySongs.size + PAGE_SIZE)
            it.copy(displaySongs = next, hasMore = next.size < it.allSongs.size)
        }
    }

    /** 下拉刷新：强制重拉当前榜单 */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val toplistId = _uiState.value.selectedToplistId
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            toplistRepository.getToplistSongs(toplistId, refresh = true)
                .onSuccess { songs ->
                    // 丢弃陈旧响应：刷新期间用户已切到其他榜单
                    if (_uiState.value.selectedToplistId != toplistId) return@launch
                    _uiState.update {
                        it.copy(
                            allSongs = songs,
                            displaySongs = songs.take(PAGE_SIZE),
                            isRefreshing = false,
                            isLoading = false,
                            error = null,
                            hasMore = songs.size > PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    if (_uiState.value.selectedToplistId != toplistId) return@launch
                    _uiState.update { it.copy(isRefreshing = false, isLoading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    private fun ensureLoaded(toplistId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 切换榜单时立即清空上一平台残留歌曲（否则加载中/失败时会展示旧内容，
            // 造成「咪咕里显示酷我、酷我里显示网易云」这类平台内容错位）
            val clearing = _uiState.value.selectedToplistId != toplistId
            _uiState.update {
                it.copy(
                    selectedToplistId = toplistId,
                    selectedPlatform = ToplistCatalog.fromPlatform(toplistId),
                    isLoading = true,
                    error = null,
                    hasMore = false,
                    allSongs = if (clearing) emptyList() else it.allSongs,
                    displaySongs = if (clearing) emptyList() else it.displaySongs
                )
            }
            val result = toplistRepository.getToplistSongs(toplistId)
            // 丢弃陈旧响应：加载期间用户已切到其他榜单
            if (_uiState.value.selectedToplistId != toplistId) return@launch
            result
                .onSuccess { songs ->
                    _uiState.update {
                        it.copy(
                            allSongs = songs,
                            displaySongs = songs.take(PAGE_SIZE),
                            isLoading = false,
                            error = null,
                            hasMore = songs.size > PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                }
        }
    }
}
