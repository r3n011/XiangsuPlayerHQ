package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Lightweight representation of a library item (song, album, artist, playlist, or category)
 * for display on the Wear OS watch. Kept minimal to reduce message payload size.
 */
@Serializable
data class WearLibraryItem(
    /** Unique identifier: songId, albumId, artistId, playlistId, or category key */
    val id: String,
    /** Display title */
    val title: String,
    /** Secondary text: artist name, song count, etc. */
    val subtitle: String = "",
    /** Item type for UI rendering decisions */
    val type: String,
    /** Whether this song can be transferred to watch local storage. */
    val canSaveToWatch: Boolean = false,
) {
    companion object {
        const val TYPE_SONG = "song"
        const val TYPE_ALBUM = "album"
        const val TYPE_ARTIST = "artist"
        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_CATEGORY = "category"
    }
}
