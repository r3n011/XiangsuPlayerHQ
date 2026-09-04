package com.theveloper.pixelplay.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.ApkDownloadInstaller
import com.theveloper.pixelplay.data.github.ApkDownloadService
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * 应用启动时的自动更新检查提示。
 *
 * 软件每次打开都会尝试检查，但每天最多真正联网检查一次（依据
 * [UserPreferencesRepository.lastUpdateCheckTimestampFlow] 与
 * [UserPreferencesRepository.autoUpdateCheckEnabledFlow]）。
 * 仅当发现新版本时才弹出 [UpdateAvailableDialog]；支持前台安装下载与
 * 「后台更新」（[ApkDownloadService] 通知栏进度）。
 */
@Composable
fun AutoUpdatePrompt(
    userPreferencesRepository: UserPreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    val apkInstaller = remember { ApkDownloadInstaller() }

    var showDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<ApkDownloadInstaller.DownloadState?>(null) }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }

    // 本地版本信息：versionName 用于版本号比较（主判断），lastUpdateTime 用于时间戳兜底
    val (currentVersionName, lastUpdateTime) = remember {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty() to packageInfo.lastUpdateTime
        }.getOrDefault("" to 0L)
    }

    // 安装未知应用权限：跳转到本应用设置页，用户返回后自动重试安装
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstallFile
        pendingInstallFile = null
        if (file != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                apkInstaller.installApk(context, file)
            } else {
                downloadState = ApkDownloadInstaller.DownloadState.Error(
                    context.getString(R.string.update_install_permission_required)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (!userPreferencesRepository.autoUpdateCheckEnabledFlow.first()) return@LaunchedEffect
            val lastCheck = userPreferencesRepository.lastUpdateCheckTimestampFlow.first()
            val now = System.currentTimeMillis()
            if (now - lastCheck < DAY_IN_MILLIS) return@LaunchedEffect

            // 联网检查；失败时不记录时间戳，下次启动自动重试
            val info = runCatching { updateChecker.checkForUpdates() }.getOrNull()?.getOrNull() ?: run {
                Timber.w("Auto update check failed, will retry next launch")
                return@LaunchedEffect
            }
            val synced = runCatching { updateChecker.syncLanzouVersions(info) }
                .getOrDefault(info.copy(isLanzouSynced = false))

            // 已成功完成一次检查 → 记录时间戳，保证每天最多检查一次
            runCatching { userPreferencesRepository.setLastUpdateCheckTimestamp(now) }

            if (synced.hasUpdate(currentVersionName, lastUpdateTime)) {
                updateInfo = synced
                showDialog = true
            }
        } catch (t: Throwable) {
            Timber.w(t, "Auto update check unexpected error")
        }
    }

    fun startInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = file
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            installPermissionLauncher.launch(intent)
        } else {
            apkInstaller.installApk(context, file)
        }
    }

    if (showDialog && updateInfo != null) {
        UpdateAvailableDialog(
            updateInfo = updateInfo!!,
            downloadState = downloadState,
            onDismiss = {
                showDialog = false
                downloadState = null
            },
            onDownload = { candidates ->
                scope.launch {
                    apkInstaller.downloadApk(context, candidates).collect { state ->
                        downloadState = state
                        if (state is ApkDownloadInstaller.DownloadState.Downloaded) {
                            downloadState = ApkDownloadInstaller.DownloadState.Installing
                            startInstall(state.file)
                        }
                    }
                }
            },
            onBackgroundDownload = { candidates ->
                ApkDownloadService.start(context, candidates)
            }
        )
    }
}

private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L