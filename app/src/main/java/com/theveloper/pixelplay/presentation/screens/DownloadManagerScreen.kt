package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.service.http.MusicDownloadService
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 下载管理：队列/进行中/已暂停/失败/已完成的下载任务列表，支持暂停、继续、取消与清理已完成记录。
 *
 * 数据直接来自 [MusicDownloadService.downloads]（进程内队列 + 已完成索引），
 * 所以这里看到的就是真实任务状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
) {
    val downloads by playerViewModel.downloads.collectAsStateWithLifecycle()

    // 排序：进行中/排队 → 已暂停/失败 → 已完成
    val ordered = remember(downloads) {
        downloads.sortedBy { info ->
            when (info.status) {
                MusicDownloadService.DownloadStatus.Running -> 0
                MusicDownloadService.DownloadStatus.Waiting -> 1
                MusicDownloadService.DownloadStatus.Paused -> 2
                MusicDownloadService.DownloadStatus.Error -> 3
                MusicDownloadService.DownloadStatus.Completed -> 4
            }
        }
    }
    val hasCompleted = downloads.any { it.isComplete }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.download_manager_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onBackClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                actions = {
                    if (hasCompleted) {
                        FilledTonalIconButton(
                            modifier = Modifier.padding(end = 6.dp),
                            onClick = {
                                downloads.filter { it.isComplete }.forEach { playerViewModel.removeDownload(it.songId) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.download_manager_clear_completed)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (ordered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.download_manager_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ordered, key = { it.songId }) { info ->
                DownloadTaskCard(
                    info = info,
                    onPause = { playerViewModel.pauseDownload(info.songId) },
                    onResume = { playerViewModel.resumeDownload(info.songId) },
                    onCancel = { playerViewModel.cancelDownload(info.songId) },
                    onRetry = { playerViewModel.resumeDownload(info.songId) }
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    info: MusicDownloadService.DownloadInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = info.artist,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                when (info.status) {
                    MusicDownloadService.DownloadStatus.Running -> {
                        FilledTonalIconButton(onClick = onPause) {
                            Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.download_manager_pause))
                        }
                    }
                    MusicDownloadService.DownloadStatus.Paused -> {
                        FilledTonalIconButton(onClick = onResume) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.download_manager_resume))
                        }
                    }
                    MusicDownloadService.DownloadStatus.Error -> {
                        FilledTonalIconButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.download_manager_retry))
                        }
                    }
                    MusicDownloadService.DownloadStatus.Completed -> {
                        FilledTonalIconButton(
                            onClick = {},
                            enabled = false,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                        }
                    }
                    MusicDownloadService.DownloadStatus.Waiting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (info.status != MusicDownloadService.DownloadStatus.Completed) {
                    FilledIconButton(
                        onClick = onCancel,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_manager_cancel))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val statusText = when (info.status) {
                MusicDownloadService.DownloadStatus.Running ->
                    stringResource(R.string.song_info_downloading_progress, info.progress.toInt().coerceIn(0, 100))
                MusicDownloadService.DownloadStatus.Waiting -> stringResource(R.string.download_manager_waiting)
                MusicDownloadService.DownloadStatus.Paused -> stringResource(R.string.song_info_download_paused)
                MusicDownloadService.DownloadStatus.Error -> stringResource(R.string.song_info_download_failed)
                MusicDownloadService.DownloadStatus.Completed -> stringResource(R.string.song_info_downloaded)
            }
            Text(
                text = info.fileName?.let { "$statusText · $it" } ?: statusText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (info.status == MusicDownloadService.DownloadStatus.Running) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { (info.progress / 100f).coerceIn(0f, 1f) }
                )
            }
        }
    }
}
