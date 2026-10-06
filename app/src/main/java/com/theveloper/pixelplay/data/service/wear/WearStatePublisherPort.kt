package com.theveloper.pixelplay.data.service.wear

import com.theveloper.pixelplay.data.model.PlayerInfo

/**
 * 手机 → 手表播放状态发布的端口（main 纯 Kotlin，零 GMS）。
 *
 * full（GMS）：由 WearStatePublisher 实现（play-services-wearable DataClient 推送）；
 * lite（no-gms）：由 NoOpWearStatePublisher 兜底（直接丢弃，无任何副作用）。
 * Hilt 按 flavor 各自绑定（见 full/lite 源集的 WearBindings）。
 */
interface WearStatePublisherPort {
    fun clearCache()
    fun publishState(songId: String?, playerInfo: PlayerInfo)
    fun clearState()
}
