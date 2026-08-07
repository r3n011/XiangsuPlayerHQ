package com.theveloper.pixelplay.data.preferences

import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

enum class CollagePattern(
    val storageKey: String,
    val label: String,
    @StringRes val labelResId: Int
) {
    COSMIC_SWIRL("cosmic_swirl", "Cosmic Swirl", R.string.setcat_collage_pattern_cosmic_swirl),
    HONEYCOMB_GROOVE("honeycomb_groove", "Honeycomb Groove", R.string.setcat_collage_pattern_honeycomb_groove),
    VINYL_STACK("vinyl_stack", "Vinyl Stack", R.string.setcat_collage_pattern_vinyl_stack),
    PIXEL_MOSAIC("pixel_mosaic", "Pixel Mosaic", R.string.setcat_collage_pattern_pixel_mosaic),
    STARDUST_SCATTER("stardust_scatter", "Stardust Scatter", R.string.setcat_collage_pattern_stardust_scatter);

    companion object {
        val default: CollagePattern = COSMIC_SWIRL

        fun fromStorageKey(value: String?): CollagePattern {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
