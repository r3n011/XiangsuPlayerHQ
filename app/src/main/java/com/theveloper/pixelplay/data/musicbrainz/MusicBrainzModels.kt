package com.theveloper.pixelplay.data.musicbrainz

import kotlinx.serialization.Serializable

@Serializable
data class ArtistCredit(
    val name: String = "",
    val artist: Artist? = null
)

@Serializable
data class Artist(
    val id: String = "",
    val name: String = "",
    val sort_name: String = ""
)

@Serializable
data class MusicBrainzRecording(
    val id: String = "",
    val title: String = "",
    val length: Long = 0L,
    val `first-release-date`: String = "",
    val artist_credit: List<ArtistCredit> = emptyList(),
    val releases: List<MusicBrainzRelease> = emptyList(),
    val genres: List<MusicBrainzGenre> = emptyList(),
    val release_groups: List<ReleaseGroup> = emptyList()
)

@Serializable
data class MusicBrainzRelease(
    val id: String = "",
    val title: String = "",
    val status: String = "",
    val date: String = "",
    val country: String = "",
    val barcode: String = "",
    val artist_credit: List<ArtistCredit> = emptyList(),
    val release_group: ReleaseGroup? = null
)

@Serializable
data class ReleaseGroup(
    val id: String = "",
    val title: String = "",
    val `type`: String = ""
)

@Serializable
data class MusicBrainzGenre(
    val name: String = "",
    val count: Int = 0,
    val disambiguation: String = ""
)

@Serializable
data class MusicBrainzSearchResult(
    val created: String = "",
    val count: Int = 0,
    val offset: Int = 0,
    val recordings: List<MusicBrainzRecording> = emptyList()
)

@Serializable
data class CoverArtImage(
    val id: Int = 0,
    val front: Boolean = false,
    val back: Boolean = false,
    val image: String = "",
    val thumbnails: CoverArtThumbnails? = null
)

@Serializable
data class CoverArtThumbnails(
    val `250`: String = "",
    val `500`: String = "",
    val `1200`: String = ""
)

@Serializable
data class CoverArtResult(
    val images: List<CoverArtImage> = emptyList()
)

@Serializable
data class AcoustIdResult(
    val status: String = "",
    val results: List<AcoustIdMatch> = emptyList()
)

@Serializable
data class AcoustIdMatch(
    val score: Double = 0.0,
    val recordings: List<AcoustIdRecording> = emptyList()
)

@Serializable
data class AcoustIdRecording(
    val id: String = "",
    val title: String = "",
    val artists: List<AcoustIdArtist> = emptyList(),
    val releases: List<AcoustIdRelease> = emptyList()
)

@Serializable
data class AcoustIdArtist(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class AcoustIdRelease(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val track_count: Int = 0
)