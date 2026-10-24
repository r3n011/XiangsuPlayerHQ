package com.theveloper.pixelplay.data.playback

/** 手动换源列表中的一项。 */
data class OnlineSourceOption(
    val id: String,
    val displayName: String,
    val available: Boolean,
    val isCurrent: Boolean = false,
)
