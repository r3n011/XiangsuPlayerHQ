package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 分享格式选择（省略号 → 分享）。
 *
 * 提供三种方式：
 *  - 像素播放器格式：歌名 / 歌手 + 应用署名 + `xiangsuplayer://share?d=...` 分享链接
 *    （对方用像素播放器打开即可定位/在线补全这首歌），任何歌曲都能用；
 *  - 网易云格式：《歌名》+ 网易云单曲链接 + @网易云音乐，仅网易云来源歌曲可选；
 *  - 直接分享文件：把本地音频文件本体发给对方，仅本地歌曲可选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerShareSheet(
    neteaseAvailable: Boolean,
    localFileAvailable: Boolean,
    onDismiss: () -> Unit,
    onSharePixelStyle: () -> Unit,
    onShareNeteaseStyle: () -> Unit,
    onShareLocalFile: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.action_share),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ShareOptionRow(
                icon = Icons.Rounded.MusicNote,
                label = stringResource(R.string.player_share_format_pixel),
                desc = stringResource(R.string.player_share_format_pixel_desc),
                enabled = true,
                onClick = onSharePixelStyle
            )
            ShareOptionRow(
                icon = Icons.Rounded.Cloud,
                label = stringResource(R.string.player_share_format_netease),
                desc = stringResource(R.string.player_share_format_netease_desc),
                enabled = neteaseAvailable,
                onClick = onShareNeteaseStyle
            )
            ShareOptionRow(
                icon = Icons.Rounded.Folder,
                label = stringResource(R.string.player_share_format_file),
                desc = stringResource(R.string.player_share_format_file_desc),
                enabled = localFileAvailable,
                onClick = onShareLocalFile
            )
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    label: String,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
    }
}
