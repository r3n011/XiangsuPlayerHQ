package com.theveloper.pixelplay.data.service

/**
 * lite（no-gms）兜底：Cast 同步端口无操作（lite 不打包 play-services-cast-framework）。
 */
internal class NoOpCastSyncPort @javax.inject.Inject constructor() : CastSyncPort {
    override fun attachWidgetUpdateCallback(callback: (force: Boolean) -> Unit) = Unit
    override fun start() = Unit
    override fun stop() = Unit
    override fun resolveRemoteSnapshot(): RemotePlaybackSnapshot? = null
    override fun remoteQueueRevision(): Pair<List<String>, Int>? = null
}
