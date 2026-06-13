package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Metadata about a song being transferred, sent from phone to watch
 * via MessageClient before the audio stream begins.
 * Contains all info needed to create a local database entry on the watch.
 */
@Serializable
data class WearTransferMetadata(
    val requestId: String,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val mimeType: String,
    val fileSize: Long,
    val bitrate: Int,
    val sampleRate: Int,
    /** Favorite snapshot at transfer time so offline watch playback starts from the right state. */
    val isFavorite: Boolean = false,
    /** Optional seed color extracted from album art on phone, used for offline watch theming. */
    val paletteSeedArgb: Int? = null,
    /** Full watch palette snapshot so local watch playback matches phone playback exactly. */
    val themePalette: WearThemePalette? = null,
    /** Mirrors the request mode so the watch knows whether to persist the received audio. */
    val transferMode: String = WearTransferRequest.MODE_SAVE_TO_LIBRARY,
    /** Position to restore when local playback starts on watch. */
    val startPositionMs: Long = 0L,
    /** Whether playback should start automatically once the transfer finishes. */
    val autoPlay: Boolean = false,
    val error: String? = null,
)
