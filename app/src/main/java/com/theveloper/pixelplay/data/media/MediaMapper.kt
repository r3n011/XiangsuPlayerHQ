package com.theveloper.pixelplay.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to map MediaItem to Song.
 * Note: This does NOT have access to the full song library master list,
 * so it should be used for strictly metadata-based mapping or fallback.
 * The ViewModel should try lookup by ID first.
 */
@Singleton
class MediaMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveSongFromMediaItem(mediaItem: MediaItem): Song? {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val mediaId = mediaItem.mediaId

        // 优先从 extras 或 localConfiguration 获取 contentUri。两者都缺失时，
        // 尝试根据 mediaId 推断来源（例如纯数字字符串 → netease://{id}），
        // 避免因持久化 bundle 丢失而彻底无法恢复曲目信息。
        val contentUri = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?.takeIf { it.isNotBlank() }
            ?: mediaItem.localConfiguration?.uri?.toString()
                ?.takeIf { it.isNotBlank() }
            ?: inferContentUriFromMediaId(mediaId)
            ?: return null

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_artist)
        val album = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM)?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_album)
        val albumId = -1L
        val duration = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION) ?: 0L
        val dateAdded = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DATE_ADDED) ?: System.currentTimeMillis()
        val filePath = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: mediaItem.localConfiguration?.uri
                ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
                ?.path
                .orEmpty()
        val id = mediaId

        val neteaseId = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_NETEASE_ID, -1L)
            ?.takeIf { it > 0L }
            ?: if (contentUri.startsWith("netease://")) {
                contentUri.substringAfter("netease://").substringBefore("/")
                    .toLongOrNull()?.takeIf { it > 0L }
            } else null
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = -1L,
            album = album,
            albumId = albumId,
            path = filePath,
            contentUriString = contentUri,
            albumArtUriString = metadata.artworkUri?.toString(),
            duration = duration,
            dateAdded = dateAdded,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
            neteaseId = neteaseId,
        )
    }

    private fun inferContentUriFromMediaId(mediaId: String): String? {
        if (mediaId.isBlank()) return null
        // 纯数字 ID → 假设为网易云曲目
        return if (mediaId.all { it.isDigit() }) {
            "netease://$mediaId"
        } else {
            // 其他情况使用 mediaId 作为标识，避免 null
            "unknown://$mediaId"
        }
    }
}
