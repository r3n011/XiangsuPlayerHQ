package com.theveloper.pixelplay.utils

import androidx.media3.common.MediaItem
import org.json.JSONObject

/**
 * 从 MediaItem 提取网易云歌曲 ID 的统一工具。
 *
 * 覆盖三种来源：
 * - 搜索/收藏等直接网易云歌曲：mediaId 为 `netease_{id}`，或 extras 携带 [MediaItemBuilder.EXTERNAL_EXTRA_NETEASE_ID]
 * - 统一媒体库里的网易云歌曲：entity id = neteaseId + [UNIFIED_SONG_ID_OFFSET]
 * - 落雪/在线歌曲（`cloud://lx/{json}`）：仅 `source` 为 `wy`（或空）且 `id` 为纯数字时视为网易云歌曲
 *   （酷我 rid 也是纯数字，必须校验 source，避免把酷我歌曲记进网易云听歌记录）
 */
object NeteaseMediaIds {

    /** 与 NeteaseRepository.NETEASE_SONG_ID_OFFSET 一致 */
    const val UNIFIED_SONG_ID_OFFSET = 3_000_000_000_000L

    fun neteaseIdOf(item: MediaItem?): Long? {
        item ?: return null

        item.mediaMetadata.extras
            ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_NETEASE_ID, 0L)
            ?.takeIf { it > 0L }
            ?.let { return it }

        val mediaId = item.mediaId
        if (mediaId.startsWith("netease_")) {
            return mediaId.removePrefix("netease_").toLongOrNull()?.takeIf { it > 0L }
        }
        mediaId.toLongOrNull()?.let { raw ->
            if (raw >= UNIFIED_SONG_ID_OFFSET) {
                val neteaseId = raw - UNIFIED_SONG_ID_OFFSET
                if (neteaseId > 0L) return neteaseId
            }
        }

        val contentUri = item.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
        if (contentUri != null && contentUri.startsWith("netease://", ignoreCase = true)) {
            return contentUri.removePrefix("netease://").substringBefore('?')
                .toLongOrNull()?.takeIf { it > 0L }
        }
        if (contentUri != null && contentUri.startsWith("cloud://lx/", ignoreCase = true)) {
            return runCatching {
                val json = JSONObject(
                    java.net.URLDecoder.decode(contentUri.substringAfter("cloud://lx/"), "UTF-8")
                )
                val source = json.optString("source", "").trim()
                val id = json.optString("id", "").trim()
                if ((source.isBlank() || source.equals("wy", true)) && id.isNotEmpty()) {
                    id.toLongOrNull()?.takeIf { it > 0L }
                } else {
                    null
                }
            }.getOrNull()
        }

        return null
    }
}
