package com.theveloper.pixelplay.presentation.components

import android.os.Build
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
    // ⚡ 蓝奏云分享里同时有多个文件（arm64 / arm32 / x86_64，lite / full 变体）：
    //    浏览器下载只能带一个链接，这里**完全跟随用户在界面上做的选择** ——
    //    变体用 useLite（精简版/完整版开关）、架构用 selectedArchKey（64/32 位选择），
    //    与 GitHub 侧的选择保持联动；都没命中时才退回设备 ABI 推荐顺序。
    //    否则会下载到装不上的架构。排序权重见 [lanzouFileRank]。
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
    val hasAnySource = lanzouCandidates.isNotEmpty() || !githubUrl.isNullOrBlank()

    val isDownloading = downloadState is ApkDownloadInstaller.DownloadState.Downloading
    val isDownloaded = downloadState is ApkDownloadInstaller.DownloadState.Downloaded
    val isInstalling = downloadState is ApkDownloadInstaller.DownloadState.Installing
    val isError = downloadState is ApkDownloadInstaller.DownloadState.Error
    val errorMessage = (downloadState as? ApkDownloadInstaller.DownloadState.Error)?.message
    // ⚡ 蓝奏云直链被 CDN 人机验证拦截 → 提示用浏览器打开
    val isLanzouError = (downloadState as? ApkDownloadInstaller.DownloadState.Error)?.isLanzou == true

    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
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
                        
                        // 蓝奏云同步状态
                        if (updateInfo.isLanzouSynced) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(8.dp, 60),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "✓ 蓝奏云已同步（国内高速下载）",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (updateInfo.lanzouFiles.isNotEmpty()) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(8.dp, 60),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "⚠ 蓝奏云版本与 GitHub 不一致，仅使用 GitHub 下载",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )
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
                    val canDownload = !isDownloading && !isInstalling && hasAnySource
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
                        // 双下载源：蓝奏云（主，国内高速）+ GitHub（备选），独立展示互不掺和
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (lanzouCandidates.isNotEmpty()) {
                                Button(
                                    // ⚡ 蓝奏云新版直链后面还有 CDN 挑战 + 中转页（ajax.php）两道机关，
                                    //    应用内下载过不去；把**解析出的直链**直接交给浏览器 —— 挑战、
                                    //    中转跳转、最终下载全部由浏览器完成（与网页打开行为一致）。
                                    //    直链已按「当前安装变体 + 设备 ABI」排好序，第一个即最优文件。
                                    onClick = { onOpenInBrowser(lanzouCandidates.first().url) },
                                    shape = actionShape,
                                    enabled = canDownload,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "蓝奏云下载（国内高速）",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                // ⚡ 明示将下载哪个文件（架构 / 变体），避免用户怀疑"下错架构"
                                lanzouFilesOrdered.firstOrNull()?.let { selected ->
                                    Text(
                                        text = "将下载：${selected.fileName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                    )
                                }
                            }
                            // 版本选择（完整版 / 精简版 no-telegram）：仅在 GitHub 同时发布了
                            // 精简版资产时显示；切换后架构选择与下载链接联动。
                            if (hasLiteVariant && !isDownloading && !isInstalling) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val liteSelected = useLite
                                    val fullSelected = !useLite
                                    Surface(
                                        onClick = { useLite = false },
                                        shape = MaterialTheme.shapes.small,
                                        color = if (fullSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.update_version_full),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (fullSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                            if (fullSelected && com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED) {
                                                Text(
                                                    text = stringResource(R.string.update_arch_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                    Surface(
                                        onClick = { useLite = true },
                                        shape = MaterialTheme.shapes.small,
                                        color = if (liteSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.update_version_lite),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (liteSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                            if (liteSelected && !com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED) {
                                                Text(
                                                    text = stringResource(R.string.update_arch_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 架构选择（64/32 位）：仅当存在多个架构的 APK 且未下载中时显示
                            if (archKeys.size > 1 && !githubUrl.isNullOrBlank() && !isDownloading && !isInstalling) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    archKeys.forEach { key ->
                                        val isSelected = selectedArchKey == key
                                        val isRecommended = recommendedArchKey == key
                                        Surface(
                                            onClick = { selectedArchKey = key },
                                            shape = MaterialTheme.shapes.small,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(archLabelRes(key)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                                if (isRecommended) {
                                                    Text(
                                                        text = stringResource(R.string.update_arch_recommended),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (!githubUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        onDownload(listOf(ApkDownloadInstaller.DownloadCandidate(url = githubUrl)))
                                    },
                                    shape = actionShape,
                                    enabled = canDownload,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "GitHub 下载",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            // ⚡ 蓝奏云直链被 CDN 人机验证拦截：提供「浏览器打开」兜底，而不是换 GitHub 源
                            if (isLanzouError) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenInBrowser(
                                            lanzouCandidates.firstOrNull()?.url
                                                ?: UpdateChecker.LANZOU_SHARE_URL
                                        )
                                    },
                                    shape = actionShape,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "在浏览器中打开蓝奏云",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            // 后台更新：关闭弹窗后用前台服务下载，通知栏实时显示进度
                            // ⚡ 仅 GitHub 源支持应用内下载；蓝奏云直链需浏览器过挑战，不走此路径
                            if (!githubUrl.isNullOrBlank() && !isDownloading && !isInstalling) {
                                TextButton(
                                    onClick = {
                                        onBackgroundDownload(
                                            listOfNotNull(
                                                githubUrl?.let {
                                                    ApkDownloadInstaller.DownloadCandidate(url = it)
                                                }
                                            )
                                        )
                                        onDismiss()
                                    },
                                    shape = actionShape,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "后台更新（通知栏显示进度）",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (lanzouCandidates.isEmpty() && githubUrl.isNullOrBlank() && !isDownloaded && !isInstalling) {
                                Text(
                                    text = stringResource(R.string.update_no_download_source),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
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
