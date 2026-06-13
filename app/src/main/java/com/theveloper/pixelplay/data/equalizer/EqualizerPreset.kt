package com.theveloper.pixelplay.data.equalizer

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class EqualizerPreset(
    val name: String,
    val displayName: String,
    val bandLevels: List<Int>,
    val isCustom: Boolean = false,
    @kotlinx.serialization.Transient
    @StringRes val displayNameRes: Int? = null
) {
    companion object {
        val BAND_FREQUENCIES = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

        val FLAT = EqualizerPreset(
            name = "flat",
            displayName = "FLAT",
            bandLevels = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_flat
        )

        val ROCK = EqualizerPreset(
            name = "rock",
            displayName = "ROCK",
            bandLevels = listOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_rock
        )

        val POP = EqualizerPreset(
            name = "pop",
            displayName = "POP",
            bandLevels = listOf(-1, 2, 4, 5, 5, 4, 2, 1, 2, 2),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_pop
        )

        val HIP_HOP = EqualizerPreset(
            name = "hip_hop",
            displayName = "HIP HOP",
            bandLevels = listOf(6, 8, 4, 1, -1, -1, 1, 1, 3, 4),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_hip_hop
        )

        val JAZZ = EqualizerPreset(
            name = "jazz",
            displayName = "JAZZ",
            bandLevels = listOf(3, 2, 1, 2, -1, -1, 0, 2, 3, 4),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_jazz
        )

        val CLASSICAL = EqualizerPreset(
            name = "classical",
            displayName = "CLASSICAL",
            bandLevels = listOf(4, 3, 2, 1, -1, -1, 0, 2, 4, 4),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_classical
        )

        val ELECTRONIC = EqualizerPreset(
            name = "electronic",
            displayName = "ELECTRONIC",
            bandLevels = listOf(5, 6, 2, 0, -1, 1, 0, 2, 6, 7),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_electronic
        )

        val BASS_BOOST = EqualizerPreset(
            name = "bass_boost",
            displayName = "BASS BOOST",
            bandLevels = listOf(7, 9, 6, 3, 0, 0, 0, 0, 0, 0),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_bass_boost
        )

        val TREBLE_BOOST = EqualizerPreset(
            name = "treble_boost",
            displayName = "TREBLE BOOST",
            bandLevels = listOf(0, 0, 0, 0, 0, 1, 3, 6, 8, 9),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_treble_boost
        )

        val VOCAL = EqualizerPreset(
            name = "vocal",
            displayName = "VOCAL",
            bandLevels = listOf(-3, -2, -1, 2, 5, 6, 5, 3, 1, 0),
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_vocal
        )

        fun custom(bandLevels: List<Int>) = EqualizerPreset(
            name = "custom",
            displayName = "CUSTOM",
            bandLevels = bandLevels,
            isCustom = true,
            displayNameRes = com.theveloper.pixelplay.R.string.equalizer_preset_custom
        )

        val ALL_PRESETS = listOf(
            FLAT, ROCK, POP, HIP_HOP, JAZZ, CLASSICAL, ELECTRONIC, BASS_BOOST, TREBLE_BOOST, VOCAL
        )

        fun fromName(name: String): EqualizerPreset {
            return ALL_PRESETS.find { it.name == name } ?: FLAT
        }
    }
}
