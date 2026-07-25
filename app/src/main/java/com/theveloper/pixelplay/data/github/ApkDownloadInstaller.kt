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
 * 负责从 GitHub Release 下载 APK 文件并触发系统安装界面
 */
class ApkDownloadInstaller {

    /**
     * 下载 APK 文件，返回下载进度 Flow
     * @param context 上下文
     * @param downloadUrl APK 下载链接
     * @return Flow<DownloadState> 下载状态流
     */
    fun downloadApk(context: Context, downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        var connection: HttpURLConnection? = null
        var tempFile: File? = null

        try {
            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                addRequestProperty("Accept", "application/octet-stream")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                emit(DownloadState.Error("下载失败: HTTP $responseCode"))
                return@flow
            }

            val totalBytes = connection.contentLengthLong
            val file = File(context.cacheDir, "pixelplay_update.apk")
            tempFile = file

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

            // 自动触发安装
            installApk(context, file)
            emit(DownloadState.Installing)

        } catch (e: Exception) {
            Timber.e(e, "APK download failed")
            emit(DownloadState.Error(e.message ?: "下载失败"))
            tempFile?.delete()
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 触发系统安装界面
     */
    fun installApk(context: Context, apkFile: File) {
        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Failed to start APK install intent")
        }
    }

    sealed class DownloadState {
        data class Downloading(val progress: Float) : DownloadState()  // progress: 0~1, -1=未知大小
        data class Downloaded(val file: File) : DownloadState()
        object Installing : DownloadState()
        data class Error(val message: String) : DownloadState()
    }
}
