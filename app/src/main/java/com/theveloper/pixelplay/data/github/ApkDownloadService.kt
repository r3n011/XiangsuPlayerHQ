package com.theveloper.pixelplay.data.github

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 更新 APK 后台下载前台服务。
 *
 * 由「更新弹窗 → 后台更新」触发：在系统通知栏显示下载进度（`dataSync` 前台服务类型），
 * 即使用户关闭弹窗/切到后台，下载仍会继续；下载完成后自动拉起系统安装器。
 * 复用 [ApkDownloadInstaller] 的多源（蓝奏云直链 + GitHub 镜像）下载管线。
 */
class ApkDownloadService : Service() {

    private val apkDownloadInstaller = ApkDownloadInstaller()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private var hasStartedForeground = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 已有下载在跑时忽略重复的启动指令
        if (downloadJob?.isActive == true) {
            startInForegroundIfNeeded(0f, indeterminate = true)
            return START_NOT_STICKY
        }

        val candidates = intent?.let(::parseCandidates).orEmpty()
        if (candidates.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForegroundIfNeeded(0f, indeterminate = true)
        downloadJob = serviceScope.launch {
            try {
                apkDownloadInstaller.downloadApk(this@ApkDownloadService, candidates).collect { state ->
                    when (state) {
                        is ApkDownloadInstaller.DownloadState.Downloading -> {
                            notifyDownloading(state.progress)
                        }
                        is ApkDownloadInstaller.DownloadState.Downloaded -> {
                            notifyDownloading(1f)
                            val installed = apkDownloadInstaller.installApk(this@ApkDownloadService, state.file)
                            if (installed) {
                                notifyDone(success = true, message = "下载完成，正在安装…")
                            } else {
                                notifyDone(success = true, message = "下载完成，但缺少安装权限，请在系统设置开启「安装未知应用」")
                            }
                        }
                        ApkDownloadInstaller.DownloadState.Installing -> {
                            notifyDone(success = true, message = "正在安装更新…")
                        }
                        is ApkDownloadInstaller.DownloadState.Error -> {
                            notifyDone(success = false, message = state.message)
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "后台更新下载失败")
                notifyDone(success = false, message = t.message ?: "下载失败")
            } finally {
                stopForegroundCompat()
                stopSelf()
                hasStartedForeground = false
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun parseCandidates(intent: Intent): List<ApkDownloadInstaller.DownloadCandidate> {
        val urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: return emptyList()
        val cookies = intent.getStringArrayListExtra(EXTRA_COOKIES) ?: ArrayList()
        val referers = intent.getStringArrayListExtra(EXTRA_REFERERS) ?: ArrayList()
        return urls.mapIndexed { index, url ->
            ApkDownloadInstaller.DownloadCandidate(
                url = url,
                cookie = cookies.getOrNull(index)?.takeIf { it.isNotBlank() },
                referer = referers.getOrNull(index)?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun startInForegroundIfNeeded(progress: Float, indeterminate: Boolean) {
        if (hasStartedForeground) {
            notificationManager().notify(NOTIFICATION_ID, buildNotification(progress, indeterminate, null))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(progress, indeterminate, null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(progress, indeterminate, null))
        }
        hasStartedForeground = true
    }

    private fun notifyDownloading(progress: Float) {
        try {
            if (!hasStartedForeground) {
                startInForegroundIfNeeded(progress, indeterminate = progress <= 0f)
            } else {
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification(progress, indeterminate = progress <= 0f, null)
                )
            }
        } catch (t: Throwable) {
            Timber.w(t, "更新进度通知失败")
        }
    }

    private fun notifyDone(success: Boolean, message: String) {
        try {
            if (notificationsAllowed()) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification(1f, false, message to success))
            }
        } catch (t: Throwable) {
            Timber.w(t, "更新结果通知失败")
        }
    }

    private fun buildNotification(progress: Float, indeterminate: Boolean, result: Pair<String, Boolean>?): Notification {
        val title = "应用更新"
        val contentText = result?.first
            ?: if ((progress >= 0f && !indeterminate)) "正在下载… ${(progress * 100).toInt()}%" else "正在准备下载…"

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(createOpenAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(result == null)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (result != null) {
            builder.setProgress(0, 0, false).setAutoCancel(true)
        } else {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), indeterminate)
        }
        return builder.build()
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "后台下载更新进度"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun stopForegroundCompat() {
        if (!hasStartedForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        hasStartedForeground = false
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val TAG = "ApkDownloadSvc"
        private const val NOTIFICATION_CHANNEL_ID = "pixelplay_update_channel"
        private const val NOTIFICATION_ID = 1004

        private const val EXTRA_URLS = "extra_urls"
        private const val EXTRA_COOKIES = "extra_cookies"
        private const val EXTRA_REFERERS = "extra_referers"

        /** 后台启动更新下载。 */
        fun start(context: Context, candidates: List<ApkDownloadInstaller.DownloadCandidate>) {
            if (candidates.isEmpty()) return
            val intent = Intent(context, ApkDownloadService::class.java).apply {
                putStringArrayListExtra(EXTRA_URLS, ArrayList(candidates.map { it.url }))
                putStringArrayListExtra(
                    EXTRA_COOKIES,
                    ArrayList(candidates.map { it.cookie ?: "" })
                )
                putStringArrayListExtra(
                    EXTRA_REFERERS,
                    ArrayList(candidates.map { it.referer ?: "" })
                )
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to start APK download foreground service")
            }
        }
    }
}