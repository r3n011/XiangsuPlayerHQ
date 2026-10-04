package com.theveloper.pixelplay.presentation.netease.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.data.listentogether.ListenTogetherCoordinator
import com.theveloper.pixelplay.data.listentogether.ListenTogetherState
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 网易云一起听面板的状态与动作（状态由进程级 [ListenTogetherCoordinator] 持有） */
@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    private val coordinator: ListenTogetherCoordinator,
    private val neteaseRepository: NeteaseRepository,
    private val personalFmApi: com.theveloper.pixelplay.data.netease.PersonalFmApi
) : ViewModel() {

    val state: StateFlow<ListenTogetherState> = coordinator.state

    val playlists: StateFlow<List<NeteasePlaylistEntity>> = neteaseRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isLoggedIn: StateFlow<Boolean> = neteaseRepository.isLoggedInFlow

    init {
        // 打开网易云面板时确保协调器在跑（含"已在房间"状态恢复）
        coordinator.ensureStarted()
    }

    /** 房主开房：以指定网易云歌单作为队列 */
    fun createRoom(playlistId: Long) = coordinator.startHostRoom(playlistId)

    /**
     * ⚡ 播放器入口开房：直接用当前播放队列（只保留网易云歌曲），并从正在播放的那首开始。
     *    对齐 MeloX：创建房间不需要先选歌单，房间队列就是本地当前队列。
     */
    fun createRoomFromCurrentQueue(
        songs: List<com.theveloper.pixelplay.data.model.Song>,
        startSongId: String?,
    ) {
        coordinator.startHostRoomWithSongs(
            songs = songs,
            failureMessage = "当前播放队列没有可一起听的网易云歌曲",
            startSongId = startSongId,
        )
    }

    /**
     * ⚡ 一起听漫游：以私人 FM/漫游推荐歌曲开房（全网易云歌曲，天然满足门禁）。
     * 结果通过 [state] 的 phase/lastError 反映。
     */
    fun startRoamingRoom() {
        viewModelScope.launch {
            val cookie = neteaseRepository.getCookieString()
            if (cookie.isBlank()) {
                coordinator.reportError("请先登录网易云账号")
                return@launch
            }
            val songIds = personalFmApi.fetchPersonalFmRecommendations(cookie)
                .getOrElse { error ->
                    // 漫游推荐失败：直接给出原因（过一遍友好映射，避免服务端原文直出）
                    coordinator.reportError(
                        "获取漫游推荐失败：" +
                            com.theveloper.pixelplay.data.listentogether
                                .friendlyListenTogetherMessage(error.message.orEmpty())
                    )
                    return@launch
                }
            val songs = neteaseRepository.getNeteaseSongsByIds(songIds)
            coordinator.startHostRoomWithSongs(
                songs = songs,
                failureMessage = "漫游推荐暂无可一起听的网易云歌曲"
            )
        }
    }

    private val _resolvingInvite = MutableStateFlow(false)

    /** 短链还原中（UI 用来显示"正在解析链接…"） */
    val resolvingInvite: StateFlow<Boolean> = _resolvingInvite.asStateFlow()

    /**
     * 解析并加入邀请。
     *
     * - 长链 / 含 roomId + inviterId 的文本：直接加入，返回 true；
     * - 网易短链（官方分享的一起听短网址）：返回 true 并**异步还原**后加入，还原失败会提示；
     * - 完全无法识别：返回 false（UI 显示格式错误）。
     */
    fun join(inviteText: String): Boolean {
        com.theveloper.pixelplay.data.listentogether
            .parseListenTogetherInvitation(inviteText)
            ?.let { invitation ->
                coordinator.joinRoom(invitation.roomId, invitation.inviterId)
                return true
            }
        if (!com.theveloper.pixelplay.data.listentogether.looksLikeListenTogetherLink(inviteText)) {
            return false
        }
        viewModelScope.launch {
            _resolvingInvite.value = true
            val resolved = com.theveloper.pixelplay.data.listentogether
                .resolveListenTogetherInvitation(inviteText)
            _resolvingInvite.value = false
            if (resolved != null) {
                coordinator.joinRoom(resolved.roomId, resolved.inviterId)
            } else {
                coordinator.reportError("邀请链接解析失败，请确认是网易云一起听分享链接")
            }
        }
        return true
    }

    fun leave() = coordinator.leaveRoom()

    fun inviteText(): String? = coordinator.inviteText()
}
