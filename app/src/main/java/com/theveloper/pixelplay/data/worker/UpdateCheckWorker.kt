package com.theveloper.pixelplay.data.worker

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.github.ApkDownloadInstaller
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 后台自动更新检查 Worker。
 *
 * 按用户设置的频率（daily / weekly）周期调度，静默检查 GitHub Release；
 * 发现新版本且「后台下载」开启时，自动下载当前 flavor（full / lite）匹配的
 * APK 到缓存目录，由启动时的 AutoUpdatePrompt 弹窗提示用户安装。
 * 「仅 WiFi + 充电」开启时通过 WorkManager 约束保证只在充电且非计费网络下运行。
 */
class UpdateCheckWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // 总开关（自动检查更新）关闭时不执行
            val autoCheckEnabled = userPreferencesRepository.autoUpdateCheckEnabledFlow.first()
            if (!autoCheckEnabled) return Result.success()

            // 后台下载关闭时不执行（仅保留弹窗检查逻辑由 AutoUpdatePrompt 负责）
            val backgroundDownload = userPreferencesRepository.backgroundUpdateDownloadFlow.first()
            if (!backgroundDownload) return Result.success()

            val updateChecker = UpdateChecker()
            val apkDownloadInstaller = ApkDownloadInstaller()

            // ⚡ 下载前先校验缓存里已下载的 APK（检测包名与版本）：
            //   仍是有效的新版本（用户还没装）→ 跳过重复下载省流量；
            //   已过期（用户已更新过 / 旧版 / 损坏 / 包名不匹配）→ 校验会顺手删除缓存。
            val cachedApk = java.io.File(applicationContext.cacheDir, "pixelplay_update.apk")
                .takeIf { it.exists() && it.length() > 1_000_000L }
            if (cachedApk != null &&
                apkDownloadInstaller.validatePendingUpdateApk(applicationContext, cachedApk) != null
            ) {
                Timber.tag(TAG).i("后台更新：缓存中已有有效的新版本 APK，跳过重复下载")
                return Result.success()
            }

            val info = updateChecker.checkForUpdates().getOrNull() ?: return Result.success()
            val currentVersionName = runCatching {
                applicationContext.packageManager
                    .getPackageInfo(applicationContext.packageName, 0)
                    .versionName.orEmpty()
            }.getOrDefault("")

            if (!info.hasUpdate(currentVersionName)) return Result.success()

            // 识别当前 flavor：lite（no-telegram）→ 下载精简版资产；full → 下载完整版资产
            val isLite = !com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED
            val abiMap = info.abiMapFor(isLite)
            val deviceAbis = Build.SUPPORTED_ABIS.toList()
            val url = info.preferredArchKey(deviceAbis, isLite)
                ?.let { abiMap[it] }
                ?: info.apkUrl
                ?: return Result.success()

            Timber.tag(TAG).i(
                "后台更新：发现新版本 ${info.version}（flavor=${if (isLite) "lite" else "full"}），开始下载"
            )
            apkDownloadInstaller.downloadApk(applicationContext, listOf(ApkDownloadInstaller.DownloadCandidate(url = url)))
                .collect { state ->
                    when (state) {
                        is ApkDownloadInstaller.DownloadState.Downloaded -> {
                            Timber.tag(TAG).i("后台更新：APK 下载完成，等待用户打开应用后安装")
                        }
                        is ApkDownloadInstaller.DownloadState.Error -> {
                            Timber.tag(TAG).w("后台更新：下载失败 ${state.message}")
                        }
                        else -> {}
                    }
                }
            return Result.success()
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "后台更新检查失败")
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.UpdateCheckWorker"
        private const val TAG = "UpdateCheckWorker"

        /**
         * 按用户频率构建周期任务。daily → 24h；weekly → 7d。
         * 若开启「仅 WiFi + 充电」，附加约束：充电中 + 非计费网络（WiFi）。
         */
        fun periodicWork(
            frequency: String,
            wifiChargingOnly: Boolean
        ): androidx.work.PeriodicWorkRequest {
            val constraintsBuilder = Constraints.Builder()
            if (wifiChargingOnly) {
                constraintsBuilder
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
            }
            return PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                if (frequency == WORK_FREQUENCY_WEEKLY) 7L else 24L,
                TimeUnit.HOURS
            )
                .setConstraints(constraintsBuilder.build())
                .build()
        }

        /** 调度（或更新）后台更新周期任务。UPDATE 使频率/约束变更即时生效。 */
        fun schedule(
            context: Context,
            userPreferencesRepository: UserPreferencesRepository,
            scope: kotlinx.coroutines.CoroutineScope
        ) {
            scope.launch {
                val frequency = userPreferencesRepository.updateCheckFrequencyFlow.first()
                val wifiChargingOnly =
                    userPreferencesRepository.updateWifiChargingOnlyFlow.first()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWork(frequency, wifiChargingOnly)
                )
            }
        }

        const val WORK_FREQUENCY_DAILY = "daily"
        const val WORK_FREQUENCY_WEEKLY = "weekly"
    }
}
