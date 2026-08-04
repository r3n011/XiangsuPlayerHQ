package com.theveloper.pixelplay.data.service.http

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val okHttpClient: OkHttpClient,
    private val songMetadataEditor: SongMetadataEditor,
    private val lyricsRepository: LyricsRepository
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
                // 下载完成后自动补全元数据：写音频标签（标题/歌手/专辑/封面/歌词）、
                // 生成同目录 .lrc 歌词文件并刷新 MediaStore
                enrichDownloadedFile(song, outputPath)
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
                // 使用用户设置的首选音质（无损耗 FLAC 等），API 内部失败会自动回退
                val quality = try {
                    userPreferencesRepository.musicQualityFlow.first().neteaseLevel
                } catch (_: Exception) {
                    "exhigh"
                }
                neteaseRepository.get().getSongUrl(song.neteaseId, quality).getOrNull()
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

    /**
     * 下载完成后自动补全元数据：
     * 1. 从在线封面 URL 下载封面图
     * 2. 从 LyricsRepository 获取歌词（网易云/LRCLIB/AMLLDB）
     * 3. 把标题/歌手/专辑/歌词/封面写入音频文件标签
     * 4. 在音频文件同目录生成 .lrc 歌词文件
     * 5. 刷新 MediaStore 让系统收录下载的文件
     * 任何一步失败都不会影响下载本身，仅记录日志。
     */
    private suspend fun enrichDownloadedFile(song: Song, outputPath: String) {
        try {
            // 1. 下载封面（仅远程 URL）
            val coverArt = song.albumArtUriString
                ?.takeIf {
                    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                }
                ?.let { downloadCoverArt(it) }

            // 2. 获取歌词
            var lrcText: String? = null
            try {
                val lyrics = lyricsRepository.getLyrics(
                    song,
                    sourcePreference = LyricsSourcePreference.API_FIRST,
                    forceRefresh = true
                )
                if (lyrics != null) {
                    val text = LyricsUtils.toLrcString(lyrics)
                    if (text.isNotBlank()) lrcText = text
                }
            } catch (e: Exception) {
                Timber.w(e, "MusicDownloadService: 歌词获取失败 songId=${song.id}")
            }

            // 3. 写音频标签（SAF content:// 路径无法直接改文件，跳过）
            if (!outputPath.startsWith("content://")) {
                val ok = songMetadataEditor.writeDownloadedFileTags(
                    filePath = outputPath,
                    title = song.title,
                    artist = song.displayArtist,
                    album = song.album,
                    albumArtist = song.albumArtist,
                    lyrics = lrcText,
                    coverArtUpdate = coverArt
                )
                Timber.d("MusicDownloadService: 元数据补全 ${if (ok) "成功" else "失败"} songId=${song.id}")
            } else {
                Timber.d("MusicDownloadService: SAF 路径跳过标签写入 songId=${song.id}")
            }

            // 4. 写 .lrc 歌词文件
            if (lrcText != null) {
                writeLrcFile(outputPath, lrcText)
            }

            // 5. 刷新 MediaStore 让系统识别新下载的文件
            if (!outputPath.startsWith("content://")) {
                MediaScannerConnection.scanFile(context, arrayOf(outputPath), null, null)
            }
        } catch (e: Exception) {
            Timber.e(e, "MusicDownloadService: enrichDownloadedFile failed songId=${song.id}")
        }
    }

    private suspend fun downloadCoverArt(url: String): CoverArtUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("MusicDownloadService: 封面下载 HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body ?: return@use null
                val mime = body.contentType()?.toString() ?: "image/jpeg"
                val bytes = body.bytes()
                if (bytes.isEmpty()) null else CoverArtUpdate(bytes = bytes, mimeType = mime)
            }
        } catch (e: Exception) {
            Timber.w(e, "MusicDownloadService: 封面下载失败 $url")
            null
        }
    }

    private suspend fun writeLrcFile(audioPath: String, lrcContent: String) {
        try {
            if (audioPath.startsWith("content://")) {
                // SAF 目录：在父目录创建同名 .lrc
                val parentDoc = DocumentFile.fromSingleUri(context, Uri.parse(audioPath))?.parentFile
                val lrcName = audioPath.substringAfterLast('/').substringBeforeLast('.') + ".lrc"
                if (parentDoc != null) {
                    parentDoc.createFile("text/x-lrc", lrcName)?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                            out.write(lrcContent.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            } else {
                val lrcFile = File(audioPath.substringBeforeLast('.') + ".lrc")
                lrcFile.writeText(lrcContent, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Timber.w(e, "MusicDownloadService: .lrc 写入失败 $audioPath")
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }
}