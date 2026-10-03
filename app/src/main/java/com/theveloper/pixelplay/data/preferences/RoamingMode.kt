package com.theveloper.pixelplay.data.preferences

import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

/**
 * 网易云漫游（私人 FM）模式 —— 对应 `/api/v1/radio/get` 的 `mode` 参数：
 * 熟悉 = `F`（偏常听口味），探索 = `E`（偏没听过的新歌）。
 *
 * [apiMode] 是传给 NcmApi `personalFm(mode)` 的整型取值（见 NcmModulesFull）。
 */
enum class RoamingMode(
    val storageKey: String,
    val apiMode: Int,
    @StringRes val labelResId: Int,
    @StringRes val descResId: Int,
) {
    FAMILIAR("familiar", 1, R.string.roaming_mode_familiar, R.string.roaming_mode_familiar_desc),
    EXPLORE("explore", 4, R.string.roaming_mode_explore, R.string.roaming_mode_explore_desc);

    companion object {
        val default: RoamingMode = FAMILIAR

        fun fromStorageKey(value: String?): RoamingMode =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}
