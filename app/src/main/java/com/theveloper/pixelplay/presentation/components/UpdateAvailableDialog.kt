package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.utils.localizedStringResource
import com.theveloper.pixelplay.utils.AppLocaleManager

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.ApkDownloadInstaller
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.ui.theme.LocalAppFontFamily
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    downloadState: ApkDownloadInstaller.DownloadState?,
    onDismiss: () -> Unit,
    onDownload: (List<ApkDownloadInstaller.DownloadCandidate>) -> Unit,
    onBackgroundDownload: (List<ApkDownloadInstaller.DownloadCandidate>) -> Unit = {},
    /** 在浏览器中打开链接（蓝奏云直链的下载由浏览器完成：CDN 挑战 / 中转页交给浏览器处理） */
    onOpenInBrowser: (String) -> Unit = {}
) {
    // GitHub 下载：按设备 ABI 自动推荐架构，用户可手动切换 64/32 位
    val deviceAbis = remember { Build.SUPPORTED_ABIS.toList() }
    // 版本选择：完整版（含 Telegram）/ 精简版（no-telegram）。当前安装的精简版用户默认推荐精简版。
    val hasLiteVariant = remember(updateInfo) { updateInfo.liteApkUrlsByAbi.isNotEmpty() }
    var useLite by remember(updateInfo) {
        mutableStateOf(hasLiteVariant && !com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED)
    }

    val archKeys = remember(updateInfo, useLite) { updateInfo.availableArchKeys(useLite) }
    val recommendedArchKey = remember(updateInfo, useLite) { updateInfo.preferredArchKey(deviceAbis, useLite) }
    var selectedArchKey by remember(updateInfo, useLite) {
        mutableStateOf(updateInfo.preferredArchKey(deviceAbis, useLite))
    }
    val githubUrl = selectedArchKey?.let { updateInfo.abiMapFor(useLite)[it] } ?: updateInfo.apkUrl

    // 蓝奏云直链（已同步时可用）+ GitHub 兜底，两个下载源独立展示、互不掺和。
    // 排序完全跟随界面上的变体 / 架构选择；都没命中时才退回设备 ABI 推荐顺序。
    val lanzouFilesOrdered = remember(updateInfo, useLite, deviceAbis, selectedArchKey) {
        if (updateInfo.isLanzouSynced) {
            updateInfo.lanzouFiles.sortedBy {
                lanzouFileRank(it.fileName, deviceAbis, useLite, selectedArchKey)
            }
        } else {
            emptyList()
        }
    }
    val lanzouCandidates = remember(lanzouFilesOrdered) {
        lanzouFilesOrdered.map {
            ApkDownloadInstaller.DownloadCandidate(
                url = it.downloadUrl,
                cookie = it.cookie.ifBlank { null },
                referer = it.referer.ifBlank { null }
            )
        }
    }

    val isDownloading = downloadState is ApkDownloadInstaller.DownloadState.Downloading
    val isDownloaded = downloadState is ApkDownloadInstaller.DownloadState.Downloaded
    val isInstalling = downloadState is ApkDownloadInstaller.DownloadState.Installing
    val isError = downloadState is ApkDownloadInstaller.DownloadState.Error
    val errorMessage = (downloadState as? ApkDownloadInstaller.DownloadState.Error)?.message
    val isLanzouError = (downloadState as? ApkDownloadInstaller.DownloadState.Error)?.isLanzou == true
    val downloadProgress = (downloadState as? ApkDownloadInstaller.DownloadState.Downloading)?.progress ?: -1f
    val canDownload = !isDownloading && !isInstalling

    BasicAlertDialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = AbsoluteSmoothCornerShape(28.dp, 60),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── 精简标题：图标 + 版本号，不再用多层卡片嵌套 ────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(18.dp, 60),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(11.dp).size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_available_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "v${updateInfo.version}",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = LocalAppFontFamily.current,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.update_available_body, updateInfo.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 同步状态只保留一条轻量提示，不再用整块彩色容器
                if (updateInfo.isLanzouSynced) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = localizedStringResource(R.string.ui_lanzou_cloud_is_up_to_date_fast_downloads_in_china),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (updateInfo.lanzouFiles.isNotEmpty()) {
                    Text(
                        text = localizedStringResource(R.string.ui_lanzou_cloud_and_github_versions_differ_only_github_downloads_are),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // ── 下载进度 / 状态：只在这里显示一次 ──────────────────────────
                when {
                    isDownloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (downloadProgress >= 0) {
                                    AppLocaleManager.getString(
                                        R.string.ui_downloading_69e1d89,
                                        (downloadProgress * 100).toInt().toString()
                                    )
                                } else {
                                    AppLocaleManager.getString(R.string.ui_downloading_644d4f7)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (downloadProgress >= 0) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    isDownloaded -> Text(
                        text = localizedStringResource(R.string.ui_download_complete_starting_installation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    isInstalling -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = localizedStringResource(R.string.ui_starting_installation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    isError -> Text(
                        text = errorMessage ?: localizedStringResource(R.string.ui_residue_setcat_download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // ── 选项：版本 / 架构合成一行紧凑胶囊，横向滚动 ────────────────
                val showVariantChips = hasLiteVariant && !isDownloading && !isInstalling
                val showArchChips = archKeys.size > 1 && !githubUrl.isNullOrBlank() && !isDownloading && !isInstalling
                if (showVariantChips || showArchChips) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showVariantChips) {
                            UpdateOptionChip(
                                label = stringResource(R.string.update_version_full),
                                selected = !useLite,
                                badge = if (!useLite && com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED) {
                                    stringResource(R.string.update_arch_recommended)
                                } else null,
                                onClick = { useLite = false }
                            )
                            UpdateOptionChip(
                                label = stringResource(R.string.update_version_lite),
                                selected = useLite,
                                badge = if (useLite && !com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED) {
                                    stringResource(R.string.update_arch_recommended)
                                } else null,
                                onClick = { useLite = true }
                            )
                        }
                        if (showVariantChips && showArchChips) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                        }
                        if (showArchChips) {
                            archKeys.forEach { key ->
                                UpdateOptionChip(
                                    label = stringResource(archLabelRes(key)),
                                    selected = selectedArchKey == key,
                                    badge = if (recommendedArchKey == key) {
                                        stringResource(R.string.update_arch_recommended)
                                    } else null,
                                    onClick = { selectedArchKey = key },
                                )
                            }
                        }
                    }
                }

                // ── 操作区：主按钮 + 次按钮，下载中只保留一根进度按钮 ──────────
                if (isDownloading) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(18.dp, 60),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (downloadProgress >= 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(downloadProgress)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                            Text(
                                text = if (downloadProgress >= 0) {
                                    "${(downloadProgress * 100).toInt()}%"
                                } else {
                                    AppLocaleManager.getString(R.string.ui_downloading_644d4f7)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                } else if (!isDownloaded && !isInstalling) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (lanzouCandidates.isNotEmpty()) {
                            Button(
                                // 蓝奏云新版直链后面还有 CDN 挑战 + 中转页，应用内下载过不去；
                                // 把解析出的直链交给浏览器完成挑战、跳转与下载。
                                onClick = { onOpenInBrowser(lanzouCandidates.first().url) },
                                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                                enabled = canDownload,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Text(
                                    text = localizedStringResource(R.string.ui_download_from_lanzou_cloud_fast_in_china),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (!githubUrl.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    onDownload(listOf(ApkDownloadInstaller.DownloadCandidate(url = githubUrl)))
                                },
                                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                                enabled = canDownload,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Text(
                                    text = localizedStringResource(R.string.ui_download_from_github),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLanzouError) {
                                TextButton(
                                    onClick = {
                                        onOpenInBrowser(
                                            lanzouCandidates.firstOrNull()?.url
                                                ?: UpdateChecker.LANZOU_SHARE_URL
                                        )
                                    },
                                ) {
                                    Text(
                                        text = localizedStringResource(R.string.ui_open_lanzou_cloud_in_browser),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            if (!githubUrl.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        onBackgroundDownload(
                                            listOfNotNull(
                                                ApkDownloadInstaller.DownloadCandidate(url = githubUrl)
                                            )
                                        )
                                        onDismiss()
                                    },
                                ) {
                                    Text(
                                        text = localizedStringResource(R.string.ui_update_in_background_progress_shown_in_notifications),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                        if (lanzouCandidates.isEmpty() && githubUrl.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.update_no_download_source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 版本 / 架构选项胶囊：选中态用 primaryContainer，附「推荐」小字。 */
@Composable
private fun UpdateOptionChip(
    label: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 架构键 → 显示文案资源 */
private fun archLabelRes(key: String): Int = when (key) {
    "x86_64" -> R.string.update_arch_x86_64
    "x86" -> R.string.update_arch_x86
    "arm" -> R.string.update_arch_arm
    else -> R.string.update_arch_arm64
}

/**
 * 蓝奏云文件排序权重（越小越优先）：
 * 1) 变体与用户选择一致（精简版 / 完整版开关 [preferLite]）优先（权重 0），
 *    变动最低；
 * 2) 架构**优先跟随用户在界面上的 64/32 位选择**（[selectedArchKey]，与 GitHub 侧联动），
 *    命中记 0；未命中才按设备 ABI 推荐顺序（arm64 设备 → arm64 最优），
 *    universal / 无架构标识次之，与设备不匹配的架构最后。
 * 变体权重乘以 100，保证「变体正确」比「架构正确」更优先（用户跑的是 lite 版时，
 * 宁可下另一个架构的 lite 也不该悄悄换成 full）。
 */
private fun lanzouFileRank(
    fileName: String,
    deviceAbis: List<String>,
    preferLite: Boolean,
    selectedArchKey: String?,
): Int {
    val name = fileName.lowercase()
    val variantRank = when {
        preferLite && name.contains("lite") -> 0
        !preferLite && name.contains("full") -> 0
        !name.contains("lite") && !name.contains("full") -> 20
        else -> 60
    }
    val selectedToken = archTokenFor(selectedArchKey)
    val archRank = if (selectedToken != null && name.contains(selectedToken)) {
        0
    } else {
        val deviceRank = deviceAbis.withIndex().firstOrNull { (_, abi) ->
            name.contains(abiTokenFor(abi))
        }?.index?.times(5) ?: when {
            name.contains("universal") -> 8
            else -> 30
        }
        // +2：让「用户选择的架构」严格优于任何设备默认顺序
        deviceRank + 2
    }
    return variantRank * 100 + archRank
}

/** GitHub 侧架构键 → 蓝奏云文件名里的架构标识（arm32 文件命名） */
private fun archTokenFor(archKey: String?): String? = when (archKey?.lowercase()) {
    null, "" -> null
    "arm" -> "arm32"
    else -> archKey.lowercase()
}

/** 设备 ABI → 蓝奏云文件名里的架构标识 */
private fun abiTokenFor(abi: String): String = when {
    abi.startsWith("arm64") -> "arm64"
    abi.startsWith("armeabi") -> "arm32"
    abi.startsWith("x86_64") -> "x86_64"
    abi.startsWith("x86") -> "x86"
    else -> abi.lowercase()
}
