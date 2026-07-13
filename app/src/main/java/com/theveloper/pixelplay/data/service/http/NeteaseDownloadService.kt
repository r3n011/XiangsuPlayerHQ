package com.theveloper.pixelplay.data.service.http

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeteaseDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val neteaseRepository: NeteaseRepository,
    private val okHttpClient: OkHttpClient
) {

    data class DownloadInfo(
        val neteaseId: Long,
        val title: String,
        val artist: String,
        val progress: Float,
        val isComplete: Boolean,
        val isFailed: Boolean,
        val filePath: String?
    )

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    suspend fun downloadSong(neteaseId: Long, title: String, artist: String): String? {
        return try {
            val existing = _downloads.value.find { it.neteaseId == neteaseId }
            if (existing != null && existing.isComplete) {
                return existing.filePath
            }

            updateDownloadStatus(neteaseId, title, artist, 0f, false, false, null)

            val streamUrl = neteaseRepository.getSongUrl(neteaseId).getOrNull()
            if (streamUrl.isNullOrEmpty()) {
                Timber.w("NeteaseDownloadService: No stream URL available for songId=$neteaseId")
                updateDownloadStatus(neteaseId, title, artist, 0f, false, true, null)
                return null
            }

            val downloadPath = userPreferencesRepository.getDownloadPath()
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(downloadPath).absolutePath)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val fileName = sanitizeFileName("$artist - $title.mp3")
            val outputFile = File(downloadDir, fileName)

            if (outputFile.exists()) {
                outputFile.delete()
            }

            val result = downloadFileWithProgress(streamUrl, outputFile) { progress ->
                updateDownloadStatus(neteaseId, title, artist, progress, false, false, null)
            }

            if (result) {
                updateDownloadStatus(neteaseId, title, artist, 100f, true, false, outputFile.absolutePath)
                outputFile.absolutePath
            } else {
                updateDownloadStatus(neteaseId, title, artist, 0f, false, true, null)
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "NeteaseDownloadService: Failed to download songId=$neteaseId")
            updateDownloadStatus(neteaseId, title, artist, 0f, false, true, null)
            null
        }
    }

    private suspend fun downloadFileWithProgress(url: String, outputFile: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                Timber.w("Download failed: ${resp.code}")
                return@withContext false
            }

            val body = resp.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()

            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                body.byteStream().use { inputStream ->
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toFloat() / contentLength.toFloat()) * 100f
                            onProgress(progress)
                        }
                    }
                }
            }
        }
        true
    }

    private fun updateDownloadStatus(neteaseId: Long, title: String, artist: String, progress: Float, isComplete: Boolean, isFailed: Boolean, filePath: String?) {
        _downloads.value = _downloads.value.map {
            if (it.neteaseId == neteaseId) {
                it.copy(progress = progress, isComplete = isComplete, isFailed = isFailed, filePath = filePath)
            } else {
                it
            }
        }.ifEmpty {
            listOf(DownloadInfo(neteaseId, title, artist, progress, isComplete, isFailed, filePath))
        }
    }

    fun getDownloadInfo(neteaseId: Long): DownloadInfo? {
        return _downloads.value.find { it.neteaseId == neteaseId }
    }

    fun removeDownload(neteaseId: Long) {
        _downloads.value = _downloads.value.filterNot { it.neteaseId == neteaseId }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }
}
