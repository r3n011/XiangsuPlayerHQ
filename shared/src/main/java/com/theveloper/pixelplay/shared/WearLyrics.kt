package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

@Serializable
data class WearLyrics(
    val plain: List<String> = emptyList(),
    val synced: List<WearSyncedLyricLine> = emptyList(),
) {
    val hasLyrics: Boolean
        get() = plain.isNotEmpty() || synced.isNotEmpty()
}

@Serializable
data class WearSyncedLyricLine(
    val timeMs: Int,
    val line: String,
    val translation: String? = null,
    val romanization: String? = null,
)
