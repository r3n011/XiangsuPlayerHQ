package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // ⚡ 必须跳过「半展开」状态：默认的半展开会让 sheet 自身的展开手势与内容滚动
    //    争抢竖直拖动，上滑一次之后所有条目都点不动了。直接全展开 + 内容自己滚动
    //    （与 LyricsMoreBottomSheet 同款做法）。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp
    ) {
        // ⚡ 禁用内容列表的 stretch overscroll：material3（1.5.0-alpha）弹窗里，列表滚到顶部后
        //    继续下拉的位移会先被系统拉伸效果吃掉（列表先"拉丝"），剩下的才交给 sheet 移动，
        //    两个动画互相争抢就是「下拉关闭时抽搐」的来源。关掉 overscroll 后下拉位移
        //    全部直达 sheet 的嵌套滚动，跟手不抖。
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // ⚡ 可上下滑动：选项最多有 7 条（歌曲信息/评论/下载/分享/一起听/艺术家/专辑），
                    //    小屏或横屏下总高度会超过弹窗上限，最后几条会被挤出屏幕点不到。
                    //    内容整体滚动（ModalBottomSheet 自身已处理系统栏 insets，这里不再重复留白）。
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
            // ── 头部：与 SongInfoBottomSheet 同款「封面 + 标题/歌手」布局 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
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

            Spacer(Modifier.height(8.dp))

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
}

/**
 * 与媒体库歌单详情「右上角更多」弹出菜单（PlaylistActionItem）同一设计语言：
 * surfaceContainerHigh 圆角卡片行 + 圆形图标底 + titleMedium 文案，
 * 保证两个入口的观感完全一致。
 */
@Composable
private fun MoreActionRow(
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            val tint = MaterialTheme.colorScheme.primary
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
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
