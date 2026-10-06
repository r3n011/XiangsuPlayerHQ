package com.theveloper.pixelplay.data.service.wear

import com.theveloper.pixelplay.data.model.PlayerInfo

/**
 * lite（no-gms）兜底：手表状态发布端口无操作（lite 不打包 play-services-wearable）。
 */
internal class NoOpWearStatePublisher @javax.inject.Inject constructor() : WearStatePublisherPort {
    override fun clearCache() = Unit
    override fun publishState(songId: String?, playerInfo: PlayerInfo) = Unit
    override fun clearState() = Unit
}
