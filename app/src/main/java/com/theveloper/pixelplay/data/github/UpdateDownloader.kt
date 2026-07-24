package com.theveloper.pixelplay.data.github

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

/**
 * Downloads an update APK via DownloadManager and triggers the system package
 * installer through a FileProvider-backed content URI.
 */
class UpdateDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var currentDownloadId: Long = -1L
    private var completionReceiver: BroadcastReceiver? = null

    fun enqueueDownload(fileName: String, downloadUrl: String) {
        cleanupPreviousDownload()

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("PixelPlay $fileName")
            setDescription("Downloading update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        currentDownloadId = downloadManager.enqueue(request)
        _downloadState.value = DownloadState.Downloading(currentDownloadId)
        registerCompletionReceiver()
    }

    fun retryDownload(fileName: String, downloadUrl: String) {
        enqueueDownload(fileName, downloadUrl)
    }

    private fun registerCompletionReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != currentDownloadId) return

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                        val localUri = cursor.getString(uriIndex)
                        _downloadState.value = DownloadState.Completed(localUri)
                    } else {
                        val reasonIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonIndex)
                        _downloadState.value = DownloadState.Failed("Download failed: $reason")
                    }
                }
                cursor.close()
                unregisterCompletionReceiver()
            }
        }

        completionReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    fun cleanup() {
        unregisterCompletionReceiver()
    }

    private fun unregisterCompletionReceiver() {
        completionReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Completion receiver was not registered")
            }
        }
        completionReceiver = null
    }

    private fun cleanupPreviousDownload() {
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove previous download")
            }
        }
        unregisterCompletionReceiver()
        _downloadState.value = DownloadState.Idle
    }

    fun installDownloadedApk(localUri: String) {
        val file = File(Uri.parse(localUri).path ?: return)
        if (!file.exists()) {
            _downloadState.value = DownloadState.Failed("Downloaded APK not found")
            return
        }

        val authority = "${BuildConfig.APPLICATION_ID}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val downloadId: Long) : DownloadState()
        data class Completed(val localUri: String) : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }
}
