package com.theveloper.pixelplay.data.service.wear

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

/**
 * lite（no-gms）兜底：手表传输端口恒不可用（lite 不打包 play-services-wearable）。
 */
internal class NoOpWearSongTransferPort @javax.inject.Inject constructor() : WearSongTransferPort {
    override suspend fun isPixelPlayWatchAvailable(): Boolean = false

    override suspend fun refreshWatchLibraryState(): Result<Unit> =
        Result.failure(IllegalStateException(AppLocaleManager.getString(R.string.ui_google_mobile_services_are_disabled_in_the_lite_version)))

    override suspend fun requestSongTransfer(songId: String, songTitle: String): Result<Int> =
        Result.failure(IllegalStateException(AppLocaleManager.getString(R.string.ui_google_mobile_services_are_disabled_in_the_lite_version)))

    override suspend fun cancelTransfer(requestId: String) = Unit
}
