package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Response sent from the phone to the watch with browse results.
 * Contains a list of library items matching the browse request.
 */
@Serializable
data class WearBrowseResponse(
    /** Matches the requestId from WearBrowseRequest for correlation */
    val requestId: String,
    /** List of library items (songs, albums, artists, playlists, or categories) */
    val items: List<WearLibraryItem> = emptyList(),
    /** Error message if the request failed, null on success */
    val error: String? = null,
)
