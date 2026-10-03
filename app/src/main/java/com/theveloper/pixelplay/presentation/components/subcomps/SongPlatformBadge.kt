package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song

/**
 * 歌曲平台标识（在线歌曲显示短文字徽标，本地歌曲不显示）。
 *
 * ⚡ 抽成公共组件：此前只有 [EnhancedSongListItem]（媒体库 / 专辑 / 歌手等列表）内置了这一套，
 * 队列与歌单详情用的 `QueuePlaylistSongItem` 没有，导致那些列表看不到「来自哪个平台」。
 */
data class PlatformBadge(
    val label: String,
    val color: Color,
)

/** 从歌曲的播放 URI 反推来源平台（本地文件返回 null，不显示徽标）。 */
fun resolvePlatformBadge(song: Song): PlatformBadge? {
    if (song.telegramFileId != null || song.telegramChatId != null) {
        return PlatformBadge("tg", Color(0xFF229ED9))
    }
    val uri = song.contentUriString.orEmpty()
    return when {
        uri.startsWith("netease://") -> PlatformBadge("网易", Color(0xFFE0242B))
        uri.startsWith("qqmusic://") -> PlatformBadge("qq", Color(0xFF31C27C))
        uri.startsWith("bilibili://") -> PlatformBadge("B站", Color(0xFFFB7299))
        uri.startsWith("navidrome://") -> PlatformBadge("nav", Color(0xFF2E7CF6))
        uri.startsWith("jellyfin://") -> PlatformBadge("jf", Color(0xFFFB7299))
        uri.startsWith("gdrive://") -> PlatformBadge("gd", Color(0xFF229ED9))
        uri.startsWith("cloud://lx/") -> resolveCloudLxSourceBadge(uri)
        else -> null
    }
}

private fun resolveCloudLxSourceBadge(uri: String): PlatformBadge {
    return try {
        val jsonStr = java.net.URLDecoder.decode(uri.removePrefix("cloud://lx/"), "UTF-8")
        when (org.json.JSONObject(jsonStr).optString("source")) {
            "tx" -> PlatformBadge("qq", Color(0xFF31C27C))
            "kg" -> PlatformBadge("酷狗", Color(0xFF2E7CF6))
            "mg" -> PlatformBadge("咪咕", Color(0xFFE4393C))
            "kw" -> PlatformBadge("酷我", Color(0xFFFF8F00))
            "bilibili" -> PlatformBadge("B站", Color(0xFFFB7299))
            "wy" -> PlatformBadge("网易", Color(0xFFE0242B))
            else -> PlatformBadge("云", Color(0xFF8E8E93))
        }
    } catch (_: Exception) {
        PlatformBadge("云", Color(0xFF8E8E93))
    }
}

/** 徽标胶囊；[badge] 为 null（本地歌曲）时不渲染任何内容。 */
@Composable
fun PlatformBadgeChip(badge: PlatformBadge?, modifier: Modifier = Modifier) {
    if (badge == null) return
    Surface(
        modifier = modifier,
        color = badge.color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall,
            color = badge.color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/** 便捷入口：直接按歌曲渲染平台徽标（本地歌曲自动不显示）。 */
@Composable
fun SongPlatformBadge(song: Song, modifier: Modifier = Modifier) {
    PlatformBadgeChip(badge = resolvePlatformBadge(song), modifier = modifier)
}
