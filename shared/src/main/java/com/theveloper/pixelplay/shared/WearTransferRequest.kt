package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Request sent from the watch to the phone to initiate a song transfer.
 * The phone will validate the song, send metadata, and stream the audio file.
 */
@Serializable
data class WearTransferRequest(
    val requestId: String,
    val songId: String,
    /** Whether the transfer should be persisted on watch or used only for transient playback. */
    val transferMode: String = MODE_SAVE_TO_LIBRARY,
    /** Position to restore when the watch starts playback. */
    val startPositionMs: Long = 0L,
    /** Whether playback should start automatically once the watch has the audio. */
    val autoPlay: Boolean = false,
) {
    companion object {
        const val MODE_SAVE_TO_LIBRARY = "save_to_library"
        const val MODE_TEMPORARY_PLAYBACK = "temporary_playback"
    }
}
