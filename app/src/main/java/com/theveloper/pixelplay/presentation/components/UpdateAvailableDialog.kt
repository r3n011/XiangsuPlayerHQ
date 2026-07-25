package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.ApkDownloadInstaller
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    downloadState: ApkDownloadInstaller.DownloadState?,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    onDownload: (apkUrl: String) -> Unit
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    val availableApks = remember(updateInfo) { updateInfo.availableApks() }
    var selectedArch by rememberSaveable {
        mutableStateOf(
            when {
                availableApks.containsKey("64位 (arm64)") -> "64位 (arm64)"
                availableApks.containsKey("通用版 (universal)") -> "通用版 (universal)"
                availableApks.isNotEmpty() -> availableApks.keys.first()
                else -> ""
            }
        )
    }

    val isDownloading = downloadState is ApkDownloadInstaller.DownloadState.Downloading
    val isDownloaded = downloadState is ApkDownloadInstaller.DownloadState.Downloaded
    val isInstalling = downloadState is ApkDownloadInstaller.DownloadState.Installing
    val isError = downloadState is ApkDownloadInstaller.DownloadState.Error
    val errorMessage = (downloadState as? ApkDownloadInstaller.DownloadState.Error)?.message

    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = { if (!isDownloading) onDismiss(dontShowAgain) }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 标题区
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.update_available_label),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Surface(
                                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp).size(18.dp),
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.update_available_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.update_available_body, updateInfo.version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 架构选择区
                if (availableApks.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "选择安装包架构",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableApks.keys.forEach { archLabel ->
                                    FilterChip(
                                        selected = selectedArch == archLabel,
                                        onClick = { selectedArch = archLabel },
                                        label = { Text(archLabel, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 下载进度区
                AnimatedVisibility(
                    visible = isDownloading || isDownloaded || isInstalling || isError,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    when (downloadState) {
                        is ApkDownloadInstaller.DownloadState.Downloading -> {
                            val progress = downloadState.progress
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = if (progress >= 0) {
                                        "下载中… ${(progress * 100).toInt()}%"
                                    } else {
                                        "下载中…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        is ApkDownloadInstaller.DownloadState.Downloaded -> {
                            Text(
                                text = "下载完成，正在启动安装…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ApkDownloadInstaller.DownloadState.Installing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "正在启动安装…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is ApkDownloadInstaller.DownloadState.Error -> {
                            Text(
                                text = errorMessage ?: "下载失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> {}
                    }
                }

                // 底部操作区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                        )
                        Text(
                            text = stringResource(R.string.update_dont_show_again),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    val canDownload = !isDownloading && !isInstalling && availableApks.isNotEmpty()
                    val downloadProgress = (downloadState as? ApkDownloadInstaller.DownloadState.Downloading)?.progress ?: 0f
                    
                    if (isDownloading) {
                        // 下载中显示进度条按钮
                        Surface(
                            shape = actionShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // 进度背景填充
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(downloadProgress)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                                // 进度文字
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                availableApks[selectedArch]?.let { onDownload(it) }
                            },
                            shape = actionShape,
                            enabled = canDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isDownloaded || isInstalling) "安装中" else stringResource(R.string.update_go_download),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
