package com.theveloper.pixelplay.data.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载安装管理器
 *
 * 负责从 GitHub Release 下载 APK 文件，并触发系统安装界面。
 *
 * 下载采用「加速镜像优先 + 官方原地址兜底」策略：依次尝试多个 GitHub
 * Release 下载加速镜像，任一成功后即停止；全部失败时再回退到官方原地址。
 * 以此解决国内网络直连 GitHub Release 经常下载失败 / 超时的问题。
 */
class ApkDownloadInstaller {

    /**
     * GitHub Release 下载加速镜像（代理前缀型）。
     * 用法：将原始 `https://github.com/.../releases/download/...` 链接整体拼在
     * 镜像域名之后即可，例如 `https://ghproxy.net/https://github.com/...`。
     *
     * 顺序即为尝试顺序（靠前的优先）。可随时按可用性增删，
     * 列表末尾会额外追加官方原地址作为兜底。
     */
    private val mirrorPrefixes = listOf(
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://gh-proxy.com/",
        "https://ghproxy.homeboyc.cn/",
        "https://github.akams.cn/"
    )

    /**
     * 下载 APK 文件，返回下载进度 Flow。
     *
     * 自动按 [mirrorPrefixes] + 原地址的顺序尝试，任一成功即止；
     * 全部失败后才 emit [DownloadState.Error]。
     */
    fun downloadApk(context: Context, downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        val file = File(context.cacheDir, "pixelplay_update.apk")
        val candidates = mirrorPrefixes.map { prefix -> prefix + downloadUrl } + downloadUrl

        var lastError: String? = null
        for ((index, url) in candidates.withIndex()) {
            var connection: HttpURLConnection? = null
            try {
                Timber.d("APK 下载源 [${index + 1}/${candidates.size}]: $url")
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 60000
                    addRequestProperty("Accept", "application/octet-stream")
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw RuntimeException("下载失败: HTTP $responseCode")
                }

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L
                var lastEmitTime = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 200 || downloadedBytes == totalBytes) {
                                val progress = if (totalBytes > 0) {
                                    downloadedBytes.toFloat() / totalBytes
                                } else {
                                    -1f
                                }
                                emit(DownloadState.Downloading(progress))
                                lastEmitTime = now
                            }
                        }
                        output.flush()
                    }
                }

                emit(DownloadState.Downloaded(file))
                return@flow
            } catch (e: Exception) {
                Timber.w(e, "APK 下载源失败 [${index + 1}/${candidates.size}]")
                lastError = e.message ?: "下载失败"
                file.delete()
            } finally {
                connection?.disconnect()
            }
        }

        emit(DownloadState.Error(lastError ?: "下载失败"))
    }.flowOn(Dispatchers.IO)

    /**
     * 触发系统安装界面。
     *
     * 调用前需确保已授予「安装未知应用」权限（Android 8.0+, API 26+）。
     * 权限不足时**不会**自动跳转设置，而是返回 false，由调用方（UI 层）引导用户去开启，
     * 以便能在用户返回后自动重试安装。
     *
     * @return true=已拉起系统安装器；false=未拉起（权限不足或发生异常）。
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Timber.w("未授予「安装未知应用」权限，无法拉起安装器")
            return false
        }

        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start APK install intent")
            false
        }
    }

    sealed class DownloadState {
        data class Downloading(val progress: Float) : DownloadState()  // progress: 0~1, -1=未知大小
        data class Downloaded(val file: File) : DownloadState()
        object Installing : DownloadState()
        data class Error(val message: String) : DownloadState()
    }
}
