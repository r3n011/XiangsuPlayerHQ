package com.theveloper.pixelplay.data.service.wear

/**
 * 歌单 → 手表歌曲传输的端口（main 纯 Kotlin，零 GMS）。
 *
 * full（GMS）：由 WearPhoneTransferSender 实现（play-services-wearable 的
 * MessageClient/ChannelClient 传输链路）；lite（no-gms）：由 NoOpWearSongTransferPort
 * 兜底（恒不可用）。Hilt 按 flavor 各自绑定（见 full/lite 源集的 WearBindings）。
 */
interface WearSongTransferPort {
    suspend fun isPixelPlayWatchAvailable(): Boolean
    suspend fun refreshWatchLibraryState(): Result<Unit>
    suspend fun requestSongTransfer(songId: String, songTitle: String): Result<Int>
    suspend fun cancelTransfer(requestId: String)
}
