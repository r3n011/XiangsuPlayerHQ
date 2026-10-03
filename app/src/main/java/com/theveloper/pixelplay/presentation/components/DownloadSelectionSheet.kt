package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 批量下载确认面板：把选中的歌曲分成「可下载（在线）」与「本地（跳过）」两组列出来，
 * 用户确认后再真正入队下载。
 *
 * 之所以要先列出来：媒体库/队列里混着本地文件和在线歌曲，本地歌曲本来就在设备上，
 * 直接静默跳过会让人以为「没反应」，所以这里明确标出哪些会下、哪些会跳过。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSelectionSheet(
    songs: List<Song>,
    isOnline: (Song) -> Boolean,
    onConfirm: (List<Song>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val onlineSongs = remember(songs) { songs.filter { isOnline(it) } }
    val localSongs = remember(songs) { songs.filterNot { isOnline(it) } }
    // ⚡ 二次确认：在线歌曲默认全选，用户可以逐首取消/重新勾选，最终只下勾上的
    var selectedIds by remember(onlineSongs) { mutableStateOf(onlineSongs.map { it.id }.toSet()) }
    val chosen = remember(onlineSongs, selectedIds) { onlineSongs.filter { it.id in selectedIds } }
    val allChosen = chosen.size == onlineSongs.size && onlineSongs.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.download_selection_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                text = stringResource(
                    R.string.download_selection_summary,
                    chosen.size,
                    localSongs.size
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(top = 8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onlineSongs.isNotEmpty()) {
                    item(key = "online_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(
                                text = stringResource(R.string.download_selection_online, chosen.size),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    if (allChosen) R.string.download_selection_clear_all
                                    else R.string.lyric_share_select_all
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        selectedIds = if (allChosen) emptySet() else onlineSongs.map { it.id }.toSet()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                    items(onlineSongs, key = { "online_${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            downloadable = true,
                            checked = song.id in selectedIds,
                            onToggle = {
                                selectedIds = if (song.id in selectedIds) {
                                    selectedIds - song.id
                                } else {
                                    selectedIds + song.id
                                }
                            }
                        )
                    }
                }
                if (localSongs.isNotEmpty()) {
                    item(key = "local_header") {
                        SectionHeader(
                            text = stringResource(R.string.download_selection_local, localSongs.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(localSongs, key = { "local_${it.id}" }) { song ->
                        SongRow(song = song, downloadable = false, checked = false, onToggle = null)
                    }
                }
            }

            Spacer(Modifier.padding(top = 8.dp))
            Button(
                onClick = { onConfirm(chosen) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if (chosen.isEmpty()) {
                        stringResource(R.string.download_selection_none)
                    } else {
                        stringResource(R.string.download_selection_confirm, chosen.size)
                    },
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SongRow(
    song: Song,
    downloadable: Boolean,
    checked: Boolean,
    onToggle: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !downloadable -> MaterialTheme.colorScheme.surfaceContainer
                    checked -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .then(
                if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 在线歌曲可逐首勾选/取消（二次确认要下哪些）
        if (onToggle != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (downloadable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(
                    if (downloadable) R.string.download_selection_badge_online
                    else R.string.download_selection_badge_local
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = if (downloadable) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
