package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.data.github.UpdateDownloader
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    selectedAsset: UpdateChecker.AssetInfo?,
    downloadState: UpdateDownloader.DownloadState,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    onSelectAsset: (UpdateChecker.AssetInfo) -> Unit,
    onDownload: () -> Unit,
    onInstall: (localUri: String) -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val canInstallPackages = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 30.dp,
        cornerRadiusTR = 30.dp,
        cornerRadiusBL = 30.dp,
        cornerRadiusBR = 30.dp,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentBR = 60,
    )
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = { onDismiss(dontShowAgain) }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
                .widthIn(max = 420.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(18.dp),
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

                if (updateInfo.assets.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "选择安装包（推荐：${updateInfo.recommendedAsset?.abi?.displayName ?: "无"}）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )

                            updateInfo.assets.forEach { asset ->
                                val isRecommended = asset.id == updateInfo.recommendedAsset?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = asset.id == selectedAsset?.id,
                                            onClick = { onSelectAsset(asset) }
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = asset.id == selectedAsset?.id,
                                        onClick = { onSelectAsset(asset) }
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = buildString {
                                                append(asset.abi.displayName)
                                                if (isRecommended) append("（推荐）")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "${asset.name} · ${formatFileSize(asset.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                when (downloadState) {
                    is UpdateDownloader.DownloadState.Downloading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = "正在下载更新...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    is UpdateDownloader.DownloadState.Failed -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = blockShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "下载失败：${downloadState.message}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    else -> { /* Idle or Completed - handled by action buttons */ }
                }

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

                    when (val state = downloadState) {
                        is UpdateDownloader.DownloadState.Completed -> {
                            Button(
                                onClick = { onInstall(state.localUri) },
                                shape = actionShape,
                            ) {
                                Text("安装")
                            }
                        }
                        is UpdateDownloader.DownloadState.Downloading -> {
                            Button(
                                onClick = { onDismiss(dontShowAgain) },
                                shape = actionShape,
                            ) {
                                Text("后台下载")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    if (canInstallPackages) {
                                        onDownload()
                                    } else {
                                        onOpenInstallSettings()
                                    }
                                },
                                shape = actionShape,
                                enabled = selectedAsset != null,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_arrow_forward_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(text = if (canInstallPackages) "下载" else "开启安装权限")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    val df = DecimalFormat("0.00")
    return when {
        sizeBytes >= 1_000_000 -> "${df.format(sizeBytes / 1_000_000.0)} MB"
        sizeBytes >= 1_000 -> "${df.format(sizeBytes / 1_000.0)} KB"
        else -> "$sizeBytes B"
    }
}
