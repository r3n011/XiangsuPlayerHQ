package com.theveloper.pixelplay.data.listentogether

import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.netease.chat.NeteaseChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 一起听邀请消息观察器。
 *
 * 网易云好友发起一起听后，邀请会以私信形式送达。这里在应用前台时周期轮询
 * 会话列表 + 未读会话的最近聊天记录，一旦发现**对方发来的、较新的**一起听邀请，
 * 就通过 [pendingInvite] 通知 UI 弹出「是否加入」对话框。
 *
 * 只做检测与状态持有，不做 UI；加入动作走 [acceptPendingInvite]（内部调用
 * [ListenTogetherCoordinator.joinRoom]，与聊天页里的一键加入是同一条路径）。
 *
 * 轮询行为：
 * - 幂等：同一条邀请消息（按消息 id）只提示一次，忽略/加入过都不会再弹；
 * - 省流：只检查未读且最近活跃的会话，且已在一同听房间 / 已有待处理邀请时跳过；
 * - 名额：一次轮询最多检查 [MAX_CONVERSATIONS_PER_POLL] 个会话，避免请求风暴。
 */
@Singleton
class ListenTogetherInviteWatcher @Inject constructor(
    private val chatRepository: NeteaseChatRepository,
    private val neteaseRepository: NeteaseRepository,
    private val coordinator: ListenTogetherCoordinator,
) {

    /** 待用户处理的邀请（非空时 UI 弹出加入对话框）。 */
    data class PendingInvite(
        val messageId: Long,
        val roomId: String,
        val inviterId: String,
        val inviterName: String,
    )

    private val _pendingInvite = MutableStateFlow<PendingInvite?>(null)
    val pendingInvite: StateFlow<PendingInvite?> = _pendingInvite.asStateFlow()

    /** 已提示过的消息 id（内存级；单协程串行访问，无需加锁）。 */
    private val handledMessageIds = LinkedHashSet<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcherJob: Job? = null

    /** 应用进入前台时启动（幂等）。 */
    fun start() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch {
            delay(FIRST_POLL_DELAY_MS)
            while (true) {
                runCatching { pollOnce() }
                    .onFailure { Timber.w(it, "listen-together: 邀请轮询失败") }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 应用进入后台时停止。 */
    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
    }

    /** 用户选择加入：走与聊天页一致的 joinRoom 路径。 */
    fun acceptPendingInvite() {
        val invite = _pendingInvite.value ?: return
        coordinator.joinRoom(invite.roomId, invite.inviterId)
        _pendingInvite.value = null
    }

    /** 用户忽略（或对话框被关闭）：清除待处理邀请；该消息不会再弹。 */
    fun dismissPendingInvite() {
        _pendingInvite.value = null
    }

    private suspend fun pollOnce() {
        if (!neteaseRepository.isLoggedInFlow.first()) return
        // 已在房间中 / 已有待处理邀请时无需检测
        if (coordinator.state.value.active) return
        if (_pendingInvite.value != null) return

        val conversations = chatRepository.getConversations(limit = 50).getOrNull() ?: return
        val now = System.currentTimeMillis()
        val candidates = conversations
            .asSequence()
            .filter { it.unreadCount > 0 }
            .filter { now - it.lastMessageTimeMs in 0..RECENT_WINDOW_MS }
            .take(MAX_CONVERSATIONS_PER_POLL)
            .toList()

        for (contact in candidates) {
            val history = chatRepository.getHistory(contact.userId, limit = 8).getOrNull() ?: continue
            val inviteMessage = history
                .filter { it.fromUserId == contact.userId && it.invitation != null }
                .filter { now - it.timeMs in 0..RECENT_WINDOW_MS }
                .maxByOrNull { it.timeMs } ?: continue
            if (inviteMessage.id in handledMessageIds) continue

            markHandled(inviteMessage.id)
            val invitation = inviteMessage.invitation ?: continue
            _pendingInvite.value = PendingInvite(
                messageId = inviteMessage.id,
                roomId = invitation.roomId,
                inviterId = invitation.inviterId,
                inviterName = contact.nickname,
            )
            return
        }
    }

    private fun markHandled(messageId: Long) {
        handledMessageIds.add(messageId)
        // 防内存无限增长：超出上限时丢弃最早的一批
        while (handledMessageIds.size > MAX_HANDLED_HISTORY) {
            val oldest = handledMessageIds.firstOrNull() ?: break
            handledMessageIds.remove(oldest)
        }
    }

    private companion object {
        const val FIRST_POLL_DELAY_MS = 5_000L
        const val POLL_INTERVAL_MS = 20_000L
        /** 只提示这个时间窗内收到的邀请（过期邀请没有加入意义） */
        const val RECENT_WINDOW_MS = 30 * 60 * 1000L
        const val MAX_CONVERSATIONS_PER_POLL = 5
        const val MAX_HANDLED_HISTORY = 200
    }
}
