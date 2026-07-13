package com.theveloper.pixelplay.data.musicbrainz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.model.Song
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class MetadataAutoCompleter @Inject constructor(
    private val musicBrainzRepository: MusicBrainzRepository,
    private val musicDao: MusicDao,
    @param:ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "musicbrainz_covers")

    init {
        cacheDir.mkdirs()
    }

    suspend fun completeMetadataIfNeeded(song: Song) {
        if (!isLocalSong(song)) {
            return
        }

        if (!needsCompletion(song)) {
            return
        }

        try {
            val songId = song.id.toLongOrNull() ?: return

            val searchResults = if (song.title.isNotBlank()) {
                musicBrainzRepository.searchRecordingByTitleAndArtist(song.title, song.artist)
            } else {
                return
            }

            if (searchResults.isEmpty()) {
                Timber.d("MusicBrainz: No results found for ${song.title} - ${song.artist}")
                return
            }

            val bestMatch = findBestMatch(searchResults, song)
            if (bestMatch == null) {
                Timber.d("MusicBrainz: No good match found")
                return
            }

            Timber.d("MusicBrainz: Found match ${bestMatch.title}")

            val recording = musicBrainzRepository.lookupRecording(bestMatch.id)
            if (recording == null) {
                Timber.w("MusicBrainz: Failed to lookup recording ${bestMatch.id}")
                return
            }

            updateSongMetadata(songId, recording)

            val releaseMbid = recording.releases.firstOrNull()?.id
            if (releaseMbid != null && song.albumArtUriString.isNullOrBlank()) {
                downloadAndSaveCoverArt(songId, releaseMbid)
            }
        } catch (e: Exception) {
            Timber.e(e, "MetadataAutoCompleter failed for ${song.title}")
        }
    }

    private suspend fun isLocalSong(song: Song): Boolean {
        return try {
            val entity = musicDao.getSongByIdOnce(song.id.toLong())
            entity?.sourceType == SourceType.LOCAL
        } catch (_: Exception) {
            !song.contentUriString.startsWith("telegram://") &&
                    !song.contentUriString.startsWith("netease://") &&
                    !song.contentUriString.startsWith("gdrive://") &&
                    !song.contentUriString.startsWith("qqmusic://") &&
                    !song.contentUriString.startsWith("navidrome://") &&
                    !song.contentUriString.startsWith("jellyfin://") &&
                    !song.contentUriString.startsWith("cloud://")
        }
    }

    private fun needsCompletion(song: Song): Boolean {
        return song.title.isBlank() ||
                song.artist.isBlank() ||
                song.album.isBlank() ||
                song.albumArtUriString.isNullOrBlank() ||
                song.genre.isNullOrBlank()
    }

    private fun findBestMatch(results: List<MusicBrainzRecording>, song: Song): MusicBrainzRecording? {
        if (results.isEmpty()) return null

        val durationMs = song.duration

        return results.firstOrNull { result ->
            val titleMatch = result.title.equals(song.title, ignoreCase = true)
            val artistMatch = result.artist_credit.any {
                it.name.equals(song.artist, ignoreCase = true)
            }

            val durationMatch = if (durationMs > 0 && result.length > 0) {
                val diff = Math.abs(durationMs - result.length)
                diff < 10000
            } else {
                true
            }

            titleMatch && artistMatch && durationMatch
        } ?: results.firstOrNull { result ->
            result.title.contains(song.title, ignoreCase = true)
        } ?: results.firstOrNull()
    }

    private suspend fun updateSongMetadata(songId: Long, recording: MusicBrainzRecording) {
        val artistName = recording.artist_credit.joinToString(", ") { it.name }
        val albumName = recording.releases.firstOrNull()?.title ?: ""
        val genre = recording.genres.firstOrNull()?.name

        val entity = musicDao.getSongByIdOnce(songId)
        if (entity == null) {
            Timber.w("Song entity not found for id $songId")
            return
        }

        var updated = false

        if (entity.title.isBlank() && recording.title.isNotBlank()) {
            musicDao.updateSongTitle(songId, recording.title)
            updated = true
        }

        if (entity.artistName.isBlank() && artistName.isNotBlank()) {
            musicDao.updateSongArtist(songId, artistName)
            updated = true
        }

        if (entity.albumName.isBlank() && albumName.isNotBlank()) {
            musicDao.updateSongAlbum(songId, albumName)
            updated = true
        }

        if ((entity.genre == null || entity.genre.isBlank()) && genre != null) {
            musicDao.updateSongGenre(songId, genre)
            updated = true
        }

        if (updated) {
            Timber.d("MetadataAutoCompleter: Updated metadata for song $songId")
        }
    }

    private suspend fun downloadAndSaveCoverArt(songId: Long, releaseMbid: String) {
        val coverUrl = musicBrainzRepository.getCoverArt(releaseMbid)
        if (coverUrl.isNullOrBlank()) {
            Timber.d("MetadataAutoCompleter: No cover art found for release $releaseMbid")
            return
        }

        try {
            val bitmap = downloadBitmap(coverUrl)
            if (bitmap == null) {
                Timber.w("MetadataAutoCompleter: Failed to download cover from $coverUrl")
                return
            }

            val coverFile = saveBitmapToCache(bitmap, songId)
            val coverUri = Uri.fromFile(coverFile).toString()

            musicDao.updateSongAlbumArt(songId, coverUri)
            Timber.d("MetadataAutoCompleter: Saved cover art to $coverUri")
        } catch (e: Exception) {
            Timber.e(e, "MetadataAutoCompleter: Failed to save cover art")
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .build()

            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                result.drawable.toBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download image from $url")
            null
        }
    }

    private fun Drawable.toBitmap(): Bitmap {
        return android.graphics.Bitmap.createBitmap(
            intrinsicWidth,
            intrinsicHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        ).apply {
            val canvas = android.graphics.Canvas(this)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, songId: Long): File {
        val file = File(cacheDir, "cover_$songId.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }
}