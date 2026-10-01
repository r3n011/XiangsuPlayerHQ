package com.theveloper.pixelplay.presentation.netease.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.listentogether.ListenTogetherCoordinator
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.netease.chat.ChatMessage
import com.theveloper.pixelplay.data.netease.chat.ChatUserDetail
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.netease.chat.NeteaseChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 单会话聊天状态：分页消息 + 发送 + 对方资料与关注态。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: NeteaseChatRepository,
    private val neteaseRepository: NeteaseRepository,
    private val listenTogetherCoordinator: ListenTogetherCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        private const val PAGE_SIZE = 30
    }

    val peerUserId: Long = savedStateHandle.get<String>("userId")?.toLongOrNull() ?: 0L
    private val routeNickname: String = savedStateHandle.get<String>("name").orEmpty()
    private val routeAvatar: String = savedStateHandle.get<String>("avatar").orEmpty()

    /** 消息按时间升序（最早在前），UI 用 reverseLayout 呈现 */
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _selfUserId = MutableStateFlow<Long?>(null)
    val selfUserId: StateFlow<Long?> = _selfUserId.asStateFlow()

    private val _peerDetail = MutableStateFlow<ChatUserDetail?>(null)
    val peerDetail: StateFlow<ChatUserDetail?> = _peerDetail.asStateFlow()

    private val _peerNickname = MutableStateFlow(routeNickname)
    val peerNickname: StateFlow<String> = _peerNickname.asStateFlow()

    private val _peerAvatar = MutableStateFlow(routeAvatar.ifBlank { null })
    val peerAvatar: StateFlow<String?> = _peerAvatar.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isTogglingFollow = MutableStateFlow(false)
    val isTogglingFollow: StateFlow<Boolean> = _isTogglingFollow.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    /** 发送失败时把文本还给输入框 */
    private val _draftRestore = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val draftRestore: SharedFlow<String> = _draftRestore.asSharedFlow()

    init {
        viewModelScope.launch {
            _selfUserId.value = neteaseRepository.getNeteaseUserId()
        }
        loadInitial()
        loadPeerDetail()
    }

    fun loadInitial() {
        if (peerUserId <= 0L || _isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            repository.getHistory(peerUserId, beforeTime = 0L, limit = PAGE_SIZE)
                .onSuccess { list ->
                    _messages.value = list.sortedBy { it.timeMs }
                    _hasMore.value = list.size >= PAGE_SIZE
                }
                .onFailure { emitError(it.message ?: "加载聊天记录失败") }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (peerUserId <= 0L || _isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        val earliest = _messages.value.firstOrNull()?.timeMs ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            repository.getHistory(peerUserId, beforeTime = earliest, limit = PAGE_SIZE)
                .onSuccess { list ->
                    _messages.update { current ->
                        (list + current)
                            .distinctBy { msg -> msg.id }
                            .sortedBy { msg -> msg.timeMs }
                    }
                    _hasMore.value = list.size >= PAGE_SIZE
                }
                .onFailure { emitError(it.message ?: "加载更多失败") }
            _isLoadingMore.value = false
        }
    }

    fun sendText(text: String) {
        val content = text.trim()
        if (content.isEmpty() || peerUserId <= 0L || _isSending.value) return
        val selfId = _selfUserId.value
        val optimisticId = -System.currentTimeMillis()
        val optimistic = ChatMessage(
            id = optimisticId,
            fromUserId = selfId ?: 0L,
            toUserId = peerUserId,
            timeMs = System.currentTimeMillis(),
            text = content,
            pending = true
        )
        _messages.update { it + optimistic }

        viewModelScope.launch {
            _isSending.value = true
            repository.sendText(peerUserId, content)
                .onSuccess {
                    _messages.update { list ->
                        list.map { if (it.id == optimisticId) it.copy(pending = false) else it }
                    }
                }
                .onFailure {
                    _messages.update { list -> list.filterNot { it.id == optimisticId } }
                    _draftRestore.tryEmit(content)
                    emitError(it.message ?: "发送失败")
                }
            _isSending.value = false
        }
    }

    fun loadPeerDetail() {
        if (peerUserId <= 0L) return
        viewModelScope.launch {
            repository.getUserDetail(peerUserId)
                .onSuccess { detail ->
                    _peerDetail.value = detail
                    if (detail.nickname.isNotBlank()) _peerNickname.value = detail.nickname
                    detail.avatarUrl?.let { _peerAvatar.value = it }
                }
        }
    }

    fun toggleFollow() {
        val detail = _peerDetail.value ?: return
        if (_isTogglingFollow.value) return
        val target = !detail.followed
        _peerDetail.value = detail.copy(followed = target)
        viewModelScope.launch {
            _isTogglingFollow.value = true
            repository.setFollowed(peerUserId, target)
                .onFailure {
                    _peerDetail.value = detail
                    emitError(it.message ?: "操作失败，请稍后重试")
                }
            _isTogglingFollow.value = false
        }
    }

    /** 一起听邀请链接消息一键加入 */
    fun joinTogether(inviterId: String, roomId: String) {
        listenTogetherCoordinator.joinRoom(roomId, inviterId)
    }

    /**
     * 拉取网易云歌单的歌曲，转成可直接交给播放器的 [LxSongInfo]。
     *
     * 走 [NeteaseRepository.getPlaylistSongsOnce]：本地库优先，聊天里收到的陌生歌单
     * （从未同步过、本地没有记录）会自动回退到网络歌单详情，不需要用户先手动同步。
     */
    suspend fun loadPlaylistSongs(playlistId: Long): List<LxSongInfo> {
        if (playlistId <= 0L) return emptyList()
        return neteaseRepository.getPlaylistSongsOnce(playlistId).mapNotNull { song ->
            // id 优先取 neteaseId；个别来源没带上时从 contentUriString 的 netease:// 里解析
            val songId = song.neteaseId?.takeIf { it > 0L }
                ?: song.contentUriString.substringAfter("netease://", "")
                    .toLongOrNull()
                    ?.takeIf { it > 0L }
                ?: return@mapNotNull null
            LxSongInfo(
                id = songId.toString(),
                songmid = songId.toString(),
                hash = songId.toString(),
                name = song.title,
                singer = song.artist,
                albumName = song.album,
                duration = song.duration,
                pic = song.albumArtUriString.orEmpty(),
                source = "wy"
            )
        }
    }

    /** 一起听面板消息提示（如"已在一间一起听房间中"） */
    val listenTogetherMessages: SharedFlow<String> = listenTogetherCoordinator.messageEvents

    private fun emitError(message: String) {
        _errorEvents.tryEmit(message)
    }
}
