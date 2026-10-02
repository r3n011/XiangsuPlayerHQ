package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 播放器右下角「省略号」打开的歌曲操作菜单（对齐 Rhythm 的更多操作入口）。
 *
 * 视觉语言与全局的 SongInfoBottomSheet 保持一致：同款「封面 + 标题/歌手」头部、
 * surfaceContainerHigh 圆角卡片行 + ListItem；「歌曲信息」入口直接落到
 * SongInfoBottomSheet 的歌曲信息详情卡片页（initialPage = 1）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheet(
    song: Song,
    /** 网易云 / B 站来源歌曲才有评论 */
    canShowComments: Boolean,
    /** 在线歌曲才有下载 */
    canDownload: Boolean,
    onDismiss: () -> Unit,
    onShowSongInfo: () -> Unit,
    onShowComments: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onStartListenTogether: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── 头部：与 SongInfoBottomSheet 同款「封面 + 标题/歌手」布局 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = stringResource(R.string.widget_album_art),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            MoreActionRow(
                iconPainter = null,
                label = stringResource(R.string.player_cd_song_info),
                onClick = onShowSongInfo
            )
            if (canShowComments) {
                MoreActionRow(
                    iconPainter = painterResource(R.drawable.rounded_mode_comment_24),
                    label = stringResource(R.string.player_more_comments),
                    onClick = onShowComments
                )
            }
            if (canDownload) {
                MoreActionRow(
                    iconPainter = painterResource(R.drawable.rounded_download_24),
                    label = stringResource(R.string.player_more_download),
                    onClick = onDownload
                )
            }
            MoreActionRow(
                iconVector = Icons.Rounded.Share,
                label = stringResource(R.string.action_share),
                onClick = onShare
            )
            MoreActionRow(
                iconVector = Icons.Rounded.Podcasts,
                // ⚡ 菜单项直接叫「一起听」（点进去就用当前播放队列开房，不用选歌）
                label = stringResource(R.string.listen_together_title),
                onClick = onStartListenTogether
            )
            MoreActionRow(
                iconVector = Icons.Rounded.Person,
                label = stringResource(R.string.player_more_view_artist),
                onClick = onViewArtist
            )
            MoreActionRow(
                iconVector = Icons.Rounded.Album,
                label = stringResource(R.string.player_more_view_album),
                onClick = onViewAlbum
            )
        }
    }
}

/**
 * 与 SongInfoBottomSheet 操作行同一设计语言：surfaceContainerHigh 圆角卡片 +
 * ListItem（图标 + 标题），保证全应用各菜单观感一致。
 */
@Composable
private fun MoreActionRow(
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    label: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                when {
                    iconVector != null -> Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = tint
                    )

                    iconPainter != null -> Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = tint
                    )

                    else -> Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = tint
                    )
                }
            },
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }
}
