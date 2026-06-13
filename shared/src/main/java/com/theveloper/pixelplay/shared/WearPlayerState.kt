package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Lightweight DTO representing the current player state, synced from phone to watch
 * via the Wear Data Layer API.
 *
 * This is intentionally a subset of the full PlayerInfo. Heavy fields like queue
 * are excluded. Album art is sent separately as an Asset.
 *
 * Optional [themePalette] allows the phone to push a ready-to-use palette so
 * watch and phone can stay visually aligned without recomputing colors on-watch.
 */
@Serializable
data class WearPlayerState(
    val songId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    /** 0 = OFF, 1 = ONE, 2 = ALL */
    val repeatMode: Int = 0,
    /** STREAM_MUSIC current volume level on phone side */
    val volumeLevel: Int = 0,
    /** STREAM_MUSIC max volume level on phone side */
    val volumeMax: Int = 0,
    /** Palette snapshot generated on phone (optional). */
    val themePalette: WearThemePalette? = null,
    /** Changes whenever the active phone queue or current queue position changes. */
    val queueRevision: String = "",
    /** Current song lyrics synced from phone when already available locally. */
    val lyrics: WearLyrics? = null,
    /** Local Wear elapsedRealtime when [currentPositionMs] was anchored. Not sent by phone. */
    val positionUpdatedElapsedRealtimeMs: Long = 0L,
) {
    val isEmpty: Boolean
        get() = songId.isEmpty()
}
