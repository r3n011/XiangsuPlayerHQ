package com.theveloper.pixelplay.data.service

/**
 * Cast 远端会话同步端口（main 纯 Kotlin，零 GMS）。
 *
 * full（GMS）：由 CastSyncCoordinator 实现（SessionManager 监听 + MediaStatus 投影 +
 * 收听统计镜像）；lite（no-gms）：由 NoOpCastSyncPort 兜底（快照/队列指纹恒 null，
 * 回调附件直接忽略）。Widget 更新回调由 MusicService 在 onCreate 后绑（[attachWidgetUpdateCallback]）。
 */
interface CastSyncPort {
    fun attachWidgetUpdateCallback(callback: (force: Boolean) -> Unit)
    fun start()
    fun stop()
    fun resolveRemoteSnapshot(): RemotePlaybackSnapshot?
    /** 远端队列指纹（songId tokens + currentItemId）；无远端会话时 null。 */
    fun remoteQueueRevision(): Pair<List<String>, Int>?
}
