package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Request sent from the watch to the phone to browse the music library.
 * Uses a request/response pattern via MessageClient to avoid DataItem size limits.
 */
@Serializable
data class WearBrowseRequest(
    /** Unique ID to correlate request with response */
    val requestId: String,
    /** Type of browse operation to perform */
    val browseType: String,
    /** Optional context ID for sub-navigation (albumId, artistId, playlistId) */
    val contextId: String? = null,
) {
    companion object {
        const val ROOT = "root"
        const val QUEUE = "queue"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
        const val ALL_SONGS = "all_songs"
        const val ALBUM_SONGS = "album_songs"
        const val ARTIST_SONGS = "artist_songs"
        const val PLAYLIST_SONGS = "playlist_songs"
    }
}
