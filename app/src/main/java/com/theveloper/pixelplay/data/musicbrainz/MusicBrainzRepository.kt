package com.theveloper.pixelplay.data.musicbrainz

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzRepository @Inject constructor(
    private val api: MusicBrainzApi
) {
    suspend fun searchRecording(query: String, limit: Int = 10): List<MusicBrainzRecording> {
        return try {
            val result = api.searchRecording(query, limit = limit)
            result.recordings
        } catch (e: Exception) {
            Timber.e(e, "MusicBrainz search failed")
            emptyList()
        }
    }

    suspend fun searchRecordingByTitleAndArtist(title: String, artist: String): List<MusicBrainzRecording> {
        val query = if (artist.isNotBlank()) {
            "artist:\"${artist.replace("\"", "\\\"")}\" AND recording:\"${title.replace("\"", "\\\"")}\""
        } else {
            "recording:\"${title.replace("\"", "\\\"")}\""
        }
        return searchRecording(query)
    }

    suspend fun lookupRecording(mbid: String): MusicBrainzRecording? {
        return try {
            api.lookupRecording(mbid)
        } catch (e: Exception) {
            Timber.e(e, "MusicBrainz lookup recording failed: $mbid")
            null
        }
    }

    suspend fun lookupRelease(mbid: String): MusicBrainzRelease? {
        return try {
            api.lookupRelease(mbid)
        } catch (e: Exception) {
            Timber.e(e, "MusicBrainz lookup release failed: $mbid")
            null
        }
    }

    suspend fun getCoverArt(releaseMbid: String): String? {
        return try {
            val result = api.getCoverArt(releaseMbid)
            result.images.firstOrNull { it.front }?.image
                ?: result.images.firstOrNull()?.image
        } catch (e: Exception) {
            Timber.e(e, "Cover art fetch failed: $releaseMbid")
            null
        }
    }

    suspend fun getCoverArtThumbnail(releaseMbid: String, size: CoverArtSize = CoverArtSize.MEDIUM): String? {
        return try {
            val result = api.getCoverArt(releaseMbid)
            val image = result.images.firstOrNull { it.front } ?: result.images.firstOrNull()
            image?.thumbnails?.let { thumbs ->
                when (size) {
                    CoverArtSize.SMALL -> thumbs.`250`.takeIf { it.isNotBlank() } ?: image.image
                    CoverArtSize.MEDIUM -> thumbs.`500`.takeIf { it.isNotBlank() } ?: thumbs.`250`.takeIf { it.isNotBlank() } ?: image.image
                    CoverArtSize.LARGE -> thumbs.`1200`.takeIf { it.isNotBlank() } ?: thumbs.`500`.takeIf { it.isNotBlank() } ?: image.image
                }
            } ?: image?.image
        } catch (e: Exception) {
            Timber.e(e, "Cover art thumbnail fetch failed: $releaseMbid")
            null
        }
    }

    enum class CoverArtSize {
        SMALL, MEDIUM, LARGE
    }
}