package com.theveloper.pixelplay.presentation.netease.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.netease.chat.ChatContact
import com.theveloper.pixelplay.data.netease.chat.ChatUserSummary
import com.theveloper.pixelplay.data.netease.chat.NeteaseChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 用户搜索页状态 */
data class UserSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<ChatUserSummary> = emptyList(),
    /** 用户手动切换过关注态的结果（覆盖服务端返回的 followed） */
    val followedOverrides: Map<Long, Boolean> = emptyMap(),
    val toggling: Set<Long> = emptySet()
)

/**
 * 消息中心状态：会话列表 + 用户搜索关注（同一个 VM 承载两个分区）。
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: NeteaseChatRepository,
    private val neteaseRepository: NeteaseRepository
) : ViewModel() {

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 320L
    }

    val isLoggedIn: StateFlow<Boolean> = neteaseRepository.isLoggedInFlow

    private val _conversations = MutableStateFlow<List<ChatContact>>(emptyList())
    val conversations: StateFlow<List<ChatContact>> = _conversations.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    private val _searchState = MutableStateFlow(UserSearchState())
    val searchState: StateFlow<UserSearchState> = _searchState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var searchJob: Job? = null

    /** 拉取会话列表（下拉刷新 / 进入主页 / 返回消息页时调用） */
    fun refreshConversations() {
        if (!isLoggedIn.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.getConversations(limit = 50)
                .onSuccess { list ->
                    _conversations.value = list
                    _unreadTotal.value = list.sumOf { it.unreadCount }
                }
                .onFailure { emitError(it.message ?: "获取消息列表失败") }
            _isRefreshing.value = false
        }
    }

    /** 搜索输入（防抖 ≥300ms） */
    fun onSearchQueryChange(query: String) {
        _searchState.update { it.copy(query = query) }
        searchJob?.cancel()
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            _searchState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _searchState.update { it.copy(isSearching = true) }
            repository.searchUsers(keyword, limit = 20)
                .onSuccess { users ->
                    _searchState.update { state ->
                        // 只保留本次查询对应的结果，避免旧请求覆盖新输入
                        if (state.query.trim() != keyword) state
                        else state.copy(results = users, isSearching = false)
                    }
                }
                .onFailure {
                    _searchState.update { state -> state.copy(isSearching = false) }
                    emitError(it.message ?: "搜索用户失败")
                }
        }
    }

    /** 关注 / 取关（乐观切换，失败回滚） */
    fun toggleFollow(userId: Long, targetFollowed: Boolean) {
        if (userId in _searchState.value.toggling) return
        _searchState.update { state ->
            state.copy(
                toggling = state.toggling + userId,
                followedOverrides = state.followedOverrides + (userId to targetFollowed)
            )
        }
        viewModelScope.launch {
            repository.setFollowed(userId, targetFollowed)
                .onFailure {
                    _searchState.update { state ->
                        state.copy(followedOverrides = state.followedOverrides + (userId to !targetFollowed))
                    }
                    emitError(it.message ?: "操作失败，请稍后重试")
                }
            _searchState.update { state -> state.copy(toggling = state.toggling - userId) }
        }
    }

    fun isFollowed(user: ChatUserSummary, state: UserSearchState): Boolean =
        state.followedOverrides[user.userId] ?: user.followed

    private fun emitError(message: String) {
        _errorEvents.tryEmit(message)
    }
}
