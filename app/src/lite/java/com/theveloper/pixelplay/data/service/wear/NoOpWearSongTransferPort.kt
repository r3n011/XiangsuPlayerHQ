package com.theveloper.pixelplay.data.service.wear

/**
 * lite（no-gms）兜底：手表传输端口恒不可用（lite 不打包 play-services-wearable）。
 */
internal class NoOpWearSongTransferPort @javax.inject.Inject constructor() : WearSongTransferPort {
    override suspend fun isPixelPlayWatchAvailable(): Boolean = false

    override suspend fun refreshWatchLibraryState(): Result<Unit> =
        Result.failure(IllegalStateException("GMS 未启用（lite 变体）"))

    override suspend fun requestSongTransfer(songId: String, songTitle: String): Result<Int> =
        Result.failure(IllegalStateException("GMS 未启用（lite 变体）"))

    override suspend fun cancelTransfer(requestId: String) = Unit
}
