package com.theveloper.pixelplay.data.service.http

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.model.Song
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
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val neteaseRepository: javax.inject.Provider<NeteaseRepository>,
    private val qqMusicRepository: javax.inject.Provider<QqMusicRepository>,
    private val navidromeRepository: javax.inject.Provider<NavidromeRepository>,
    private val okHttpClient: OkHttpClient
) {

    data class DownloadInfo(
        val songId: String,
        val title: String,
        val artist: String,
        val progress: Float,
        val isComplete: Boolean,
        val isFailed: Boolean,
        val filePath: String?
    )

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    fun isOnlineSong(song: Song): Boolean {
        return song.neteaseId != null || song.qqMusicMid != null || song.navidromeId != null || 
               song.gdriveFileId != null || song.telegramFileId != null
    }

    suspend fun downloadSong(song: Song): String? {
        return try {
            val songId = song.id
            val existing = _downloads.value.find { it.songId == songId }
            if (existing != null && existing.isComplete) {
                return existing.filePath
            }

            updateDownloadStatus(songId, song.title, song.displayArtist, 0f, false, false, null)

            val streamUrl = getStreamUrl(song)
            if (streamUrl.isNullOrEmpty()) {
                Timber.w("MusicDownloadService: No stream URL available for songId=$songId")
                updateDownloadStatus(songId, song.title, song.displayArtist, 0f, false, true, null)
                return null
            }

            val fileName = sanitizeFileName("${song.displayArtist} - ${song.title}.mp3")
            val outputPath = getOutputFilePath(fileName)

            if (outputPath == null) {
                Timber.w("MusicDownloadService: Cannot determine output path")
                updateDownloadStatus(songId, song.title, song.displayArtist, 0f, false, true, null)
                return null
            }

            val result = downloadFileWithProgress(streamUrl, outputPath) { progress ->
                updateDownloadStatus(songId, song.title, song.displayArtist, progress, false, false, null)
            }

            if (result) {
                updateDownloadStatus(songId, song.title, song.displayArtist, 100f, true, false, outputPath)
                outputPath
            } else {
                updateDownloadStatus(songId, song.title, song.displayArtist, 0f, false, true, null)
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "MusicDownloadService: Failed to download songId=${song.id}")
            updateDownloadStatus(song.id, song.title, song.displayArtist, 0f, false, true, null)
            null
        }
    }

    private suspend fun getStreamUrl(song: Song): String? {
        return when {
            song.neteaseId != null -> {
                neteaseRepository.get().getSongUrl(song.neteaseId).getOrNull()
            }
            song.qqMusicMid != null -> {
                qqMusicRepository.get().getSongUrl(song.qqMusicMid).getOrNull()
            }
            song.navidromeId != null -> {
                navidromeRepository.get().getStreamUrl(song.navidromeId)
            }
            else -> null
        }
    }

    private suspend fun getOutputFilePath(fileName: String): String? {
        val downloadPathPref = userPreferencesRepository.getDownloadPath()
        
        return if (downloadPathPref.startsWith("content://")) {
            val uri = Uri.parse(downloadPathPref)
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.createFile("audio/mpeg", fileName)?.uri?.toString()
            } catch (e: Exception) {
                Timber.e(e, "Failed to create file in SAF directory")
                fallbackToPublicDirectory(fileName)
            }
        } else {
            fallbackToPublicDirectory(fileName)
        }
    }

    private fun fallbackToPublicDirectory(fileName: String): String {
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return File(downloadDir, fileName).absolutePath
    }

    private suspend fun downloadFileWithProgress(url: String, outputPath: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
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

            if (outputPath.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(outputPath))?.use { outputStream ->
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
            } else {
                FileOutputStream(File(outputPath)).use { outputStream ->
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
        }
        true
    }

    private fun updateDownloadStatus(songId: String, title: String, artist: String, progress: Float, isComplete: Boolean, isFailed: Boolean, filePath: String?) {
        _downloads.value = _downloads.value.map {
            if (it.songId == songId) {
                it.copy(progress = progress, isComplete = isComplete, isFailed = isFailed, filePath = filePath)
            } else {
                it
            }
        }.ifEmpty {
            listOf(DownloadInfo(songId, title, artist, progress, isComplete, isFailed, filePath))
        }
    }

    fun getDownloadInfo(songId: String): DownloadInfo? {
        return _downloads.value.find { it.songId == songId }
    }

    fun removeDownload(songId: String) {
        _downloads.value = _downloads.value.filterNot { it.songId == songId }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }
}