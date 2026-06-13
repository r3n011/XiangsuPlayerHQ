package com.theveloper.pixelplay.data

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class WearDeviceSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
)

@Singleton
class WearDeviceMusicRepository @Inject constructor(
    private val application: Application,
) {
    suspend fun scanDeviceSongs(): List<WearDeviceSong> = withContext(Dispatchers.IO) {
        val resolver = application.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
        }

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            val songs = mutableListOf<WearDeviceSong>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex).orEmpty().ifBlank { "Unknown title" }
                val artist = cursor.getString(artistIndex).orEmpty()
                val album = cursor.getString(albumIndex).orEmpty()
                val duration = cursor.getLong(durationIndex).coerceAtLeast(0L)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
                songs += WearDeviceSong(
                    songId = "device:$id",
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    contentUri = contentUri,
                )
            }
            songs
        } ?: emptyList()
    }
}

