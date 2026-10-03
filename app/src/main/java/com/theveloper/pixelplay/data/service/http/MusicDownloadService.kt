package com.theveloper.pixelplay.data.service.http

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.preferences.DownloadFileNameTemplate
import com.theveloper.pixelplay.data.preferences.MusicQualityCatalog
import com.theveloper.pixelplay.data.preferences.PersistedDownloadEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * 在线歌曲下载服务。
 *
 * 与参考实现（lx-music-desktop 的下载管线）对齐的关键行为：
 * - **队列 + 并发上限**：所有任务先入队（[DownloadStatus.Waiting]），最多同时跑
 *   [MAX_CONCURRENT_DOWNLOADS] 个；支持暂停 / 继续 / 取消；
 * - **失败重试 + 退避**：每首最多 [MAX_RETRY] 次，间隔 [RETRY_DELAY_MS]；
 *   链接过期（401/403/410）或网络错误会**重新解析直链**再试；
 * - **断点续传**：先下到缓存临时文件，服务端支持 Range 时带 `Range` 续传并做
 *   重叠字节校验，416 / 校验失败则删档重来；
 * - **临时文件 + 完成后落盘**：失败/取消只留临时文件（会被清理），目标目录不会
 *   出现半个文件；元数据标签也因此在**临时文件**上写 —— SAF 目录同样能写标签；
 * - **扩展名按真实类型**：从响应 Content-Type（兜底 URL 后缀 / 音质档位）推导，
 *   不再一律 `.mp3`；
 * - **文件名模板 / 跳过已存在**：可在设置里配置。
 */
@Singleton
class MusicDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val neteaseRepository: Provider<NeteaseRepository>,
    private val qqMusicRepository: Provider<QqMusicRepository>,
    private val navidromeRepository: Provider<NavidromeRepository>,
    /** 在线源按需解析直链（cloud://lx、kgaudio://、bilibili:// 等，不再依赖播放缓存） */
    private val playerEngine: Provider<DualPlayerEngine>,
    private val okHttpClient: OkHttpClient,
    private val songMetadataEditor: SongMetadataEditor,
    private val lyricsRepository: LyricsRepository
) {

    companion object {
        private const val DOWNLOAD_CHANNEL_ID = "pixelplay_download_channel"
        private const val DOWNLOAD_NOTIFICATION_ID_BASE = 3000

        /** 同时进行的下载数上限（对齐参考实现默认 3） */
        private const val MAX_CONCURRENT_DOWNLOADS = 3

        /** 单首最大重试次数 / 重试间隔 */
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 1_000L

        /** 续传时重发的重叠字节数：用于校验服务端返回的是同一份数据 */
        private const val RESUME_OVERLAP_BYTES = 10

        /** 文件名（含扩展名）最大长度，避免超出文件系统限制 */
        private const val MAX_FILE_NAME_LENGTH = 150

        /** 判定「文件已存在」的最小体积（小于它的多半是失败残留） */
        private const val EXISTING_FILE_MIN_BYTES = 100L

        private const val TEMP_DIR_NAME = "downloads"

        /** 在线歌曲的自定义播放 scheme（由播放引擎懒解析真实直链，非本地文件） */
        private val ONLINE_CUSTOM_SCHEMES = listOf(
            "cloud://", "netease://", "qqmusic://", "qq://", "kw://", "kg://", "mg://",
            "kgaudio://", "bilibili://", "navidrome://", "jellyfin://", "gdrive://", "telegram://"
        )
    }

    enum class DownloadStatus { Waiting, Running, Paused, Error, Completed }

    data class DownloadInfo(
        val songId: String,
        val title: String,
        val artist: String,
        val progress: Float,
        val isComplete: Boolean,
        val isFailed: Boolean,
        val filePath: String?,
        val status: DownloadStatus = when {
            isComplete -> DownloadStatus.Completed
            isFailed -> DownloadStatus.Error
            else -> DownloadStatus.Running
        },
        /** 最终文件名（含扩展名），仅用于展示 */
        val fileName: String? = null,
    ) {
        val isActive: Boolean
            get() = status == DownloadStatus.Waiting || status == DownloadStatus.Running
        val isPaused: Boolean get() = status == DownloadStatus.Paused
    }

    /** 内部任务：比 [DownloadInfo] 多持有原始 [Song]、解析出的直链与协程句柄 */
    private class Task(
        val song: Song,
        var preferredUrl: String?,
        var onFinished: ((Boolean) -> Unit)?,
        var status: DownloadStatus = DownloadStatus.Waiting,
        var job: Job? = null,
        var progress: Float = 0f,
        var fileName: String? = null,
        var filePath: String? = null,
    )

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    /** 任务表（含进行中）；[taskLock] 保护。已完成的任务保留在表里以便跳过重复下载。 */
    private val tasks = LinkedHashMap<String, Task>()
    private val taskLock = Any()

    // ⚡ 下载索引缓存：songId → DownloadInfo。getDownloadInfo 原先对下载列表线性扫描
    // （O(M)），媒体库放歌构建大队列时每首歌都要查一次（O(N×M)），低性能设备上会明显
    // 拖慢"放歌前几秒"。改为在下载列表变更时重建不可变 Map，查询降为 O(1)。
    @Volatile
    private var downloadIndex: Map<String, DownloadInfo> = emptyMap()

    private fun rebuildDownloadIndex() {
        downloadIndex = _downloads.value.associateBy { it.songId }
    }

    // 应用级作用域：下载在后台独立协程中执行，不占主线程、
    // 不随播放页 ViewModel 销毁而中断（关闭播放页/切到后台仍能继续下载）。
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    private val tempDir: File
        get() = File(context.cacheDir, TEMP_DIR_NAME).apply { if (!exists()) mkdirs() }

    init {
        createDownloadChannel()
        // ⚡ 进程重启后恢复已下载索引：_downloads 从纯内存态升级为
        // DataStore 持久化索引，重启后仍能命中"已下载 → 本地播放"。
        loadPersistedDownloads()
    }

    /** 从 DataStore 恢复已完成的下载记录（标题/进度仅为展示用，路径最关键）。 */
    private fun loadPersistedDownloads() {
        appScope.launch {
            val persisted = userPreferencesRepository.getDownloadsIndexOnce()
            if (persisted.isEmpty()) return@launch
            _downloads.value = persisted
                .filter { it.songId.isNotBlank() && it.filePath.isNotBlank() }
                .map {
                    DownloadInfo(
                        songId = it.songId,
                        title = it.title,
                        artist = it.artist,
                        progress = 100f,
                        isComplete = true,
                        isFailed = false,
                        filePath = it.filePath,
                        status = DownloadStatus.Completed
                    )
                }
            rebuildDownloadIndex()
            Timber.d("MusicDownloadService: restored ${persisted.size} downloaded songs from index")
        }
    }

    /** 把已完成的下载记录持久化到 DataStore，保证进程重启后仍可本地播放。 */
    private fun persistDownloads() {
        appScope.launch {
            userPreferencesRepository.setDownloadsIndex(
                _downloads.value
                    .filter { it.isComplete && !it.songId.isBlank() && !it.filePath.isNullOrBlank() }
                    .map {
                        PersistedDownloadEntry(
                            songId = it.songId,
                            title = it.title,
                            artist = it.artist,
                            filePath = it.filePath!!
                        )
                    }
            )
        }
    }

    private fun createDownloadChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "歌曲下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun isDownloading(songId: String): Boolean = synchronized(taskLock) {
        tasks[songId]?.status.let { it == DownloadStatus.Waiting || it == DownloadStatus.Running }
    }

    fun isPaused(songId: String): Boolean =
        synchronized(taskLock) { tasks[songId]?.status == DownloadStatus.Paused }

    /**
     * 加入下载队列（非挂起，立即返回）。
     *
     * - 已完成 → 直接回调成功；
     * - 已暂停 / 失败 → 重新排队（继续下载）；
     * - 排队中 / 进行中 → 忽略（避免重复任务）。
     *
     * [preferredUrl] 为播放链路已解析好的真实流 URL，可绕过官方 API 限速与落雪引擎，
     * 避免与播放争抢资源；为 null 时由 [resolveStreamUrl] 现解析。
     */
    fun startDownload(song: Song, preferredUrl: String? = null, onFinished: ((Boolean) -> Unit)? = null) {
        val songId = song.id
        val existingInfo = getDownloadInfo(songId)
        if (existingInfo?.isComplete == true) {
            mainHandler.post { onFinished?.invoke(true) }
            return
        }

        synchronized(taskLock) {
            val task = tasks[songId]
            when (task?.status) {
                DownloadStatus.Waiting, DownloadStatus.Running -> {
                    task.onFinished = onFinished ?: task.onFinished
                    return
                }
                else -> {
                    if (task != null) {
                        task.preferredUrl = preferredUrl ?: task.preferredUrl
                        task.onFinished = onFinished
                        task.status = DownloadStatus.Waiting
                        task.progress = 0f
                    } else {
                        tasks[songId] = Task(
                            song = song,
                            preferredUrl = preferredUrl,
                            onFinished = onFinished
                        )
                    }
                }
            }
        }
        updateDownloadStatus(
            songId = songId,
            title = song.title,
            artist = song.displayArtist,
            progress = 0f,
            status = DownloadStatus.Waiting,
            filePath = null,
            fileName = null
        )
        pump()
    }

    /** 暂停：进行中的任务取消协程（已下的部分留在临时文件里，可继续续传） */
    fun pauseDownload(songId: String) {
        val task = synchronized(taskLock) {
            val t = tasks[songId] ?: return
            if (t.status == DownloadStatus.Completed || t.status == DownloadStatus.Error) return
            t.status = DownloadStatus.Paused
            t.job?.cancel()
            t.job = null
            t
        }
        updateDownloadStatus(
            songId = songId,
            title = task.song.title,
            artist = task.song.displayArtist,
            progress = task.progress,
            status = DownloadStatus.Paused,
            filePath = null,
            fileName = task.fileName
        )
        showDownloadNotification(notificationIdFor(songId), task.song.title, task.progress, isDone = true, success = true, text = "已暂停")
        pump()
    }

    /** 继续（暂停后重新排队） */
    fun resumeDownload(songId: String) {
        val task = synchronized(taskLock) {
            val t = tasks[songId] ?: return
            if (t.status != DownloadStatus.Paused && t.status != DownloadStatus.Error) return
            t.status = DownloadStatus.Waiting
            t
        }
        updateDownloadStatus(
            songId = songId,
            title = task.song.title,
            artist = task.song.displayArtist,
            progress = task.progress,
            status = DownloadStatus.Waiting,
            filePath = null,
            fileName = task.fileName
        )
        pump()
    }

    /** 取消：终止任务、删除临时文件与状态记录 */
    fun cancelDownload(songId: String) {
        val task = synchronized(taskLock) {
            val t = tasks.remove(songId)
            t?.job?.cancel()
            t
        }
        deleteTempFile(songId)
        _downloads.value = _downloads.value.filterNot { it.songId == songId }
        rebuildDownloadIndex()
        runCatching { NotificationManagerCompat.from(context).cancel(notificationIdFor(songId)) }
        task?.onFinished?.let { cb -> mainHandler.post { cb(false) } }
        pump()
    }

    /** 仅移除「已下载」记录（不删文件，也不影响正在跑的任务） */
    fun removeDownload(songId: String) {
        if (isDownloading(songId)) return
        _downloads.value = _downloads.value.filterNot { it.songId == songId }
        rebuildDownloadIndex()
        persistDownloads()
    }

    fun isOnlineSong(song: Song): Boolean {
        // 只要是在线源（有网易云/QQ/自建库等 ID，或播放地址是 http(s) URL）都算在线，
        // 下载按钮对所有在线源歌曲显示
        if (song.neteaseId != null || song.qqMusicMid != null || song.navidromeId != null ||
            song.gdriveFileId != null || song.telegramFileId != null ||
            song.path?.startsWith("http", ignoreCase = true) == true ||
            song.contentUriString?.startsWith("http", ignoreCase = true) == true
        ) {
            return true
        }
        // ⚡ cloud://lx/{json} 占位（在线搜索/歌单入列）与 netease://、bilibili:// 等
        //    自定义 scheme 同样是在线歌曲：由播放引擎在实际播放时懒解析真实直链。
        //    此前不识别导致这类歌曲在播放器里不显示下载按钮。
        val uri = song.contentUriString ?: return false
        return ONLINE_CUSTOM_SCHEMES.any { uri.startsWith(it, ignoreCase = true) }
    }

    // ─── 队列调度 ─────────────────────────────────────────────────────────

    /** 按并发上限启动等待中的任务（每次状态变化后调用） */
    private fun pump() {
        val toStart = synchronized(taskLock) {
            val running = tasks.values.count { it.status == DownloadStatus.Running }
            val slots = (MAX_CONCURRENT_DOWNLOADS - running).coerceAtLeast(0)
            if (slots == 0) return
            tasks.values
                .filter { it.status == DownloadStatus.Waiting }
                .take(slots)
                .onEach { it.status = DownloadStatus.Running }
        }
        // 任务句柄在同一把锁里赋值：否则「刚排队就被暂停」会拿不到 job 而取消不掉
        synchronized(taskLock) {
            toStart.forEach { task ->
                task.job = appScope.launch {
                    var success = false
                    try {
                        success = runTask(task)
                    } catch (cancelled: CancellationException) {
                        // 暂停 / 取消：状态已由 pause/cancel 设置好，这里不覆盖
                        throw cancelled
                    } finally {
                        synchronized(taskLock) {
                            if (task.status == DownloadStatus.Running) {
                                task.status = if (success) DownloadStatus.Completed else DownloadStatus.Error
                            }
                            task.job = null
                        }
                    }
                    mainHandler.post { task.onFinished?.invoke(success) }
                    if (success) persistDownloads()
                    pump()
                }
            }
        }
    }

    // ─── 单个任务 ─────────────────────────────────────────────────────────

    private suspend fun runTask(task: Task): Boolean {
        val song = task.song
        val songId = song.id
        val notificationId = notificationIdFor(songId)
        val quality = readQualityPreference()

        try {
            // 1. 解析直链（播放缓存 → 在线源按需解析 → 各平台 API）
            val streamUrl = resolveStreamUrl(song, task.preferredUrl)
            if (streamUrl.isNullOrBlank()) {
                Timber.w("MusicDownloadService: 无法解析直链 songId=$songId")
                failTask(task, notificationId, "无法获取下载地址")
                return false
            }

            // 2. 下载到缓存临时文件（Range 续传 + 重试）
            val tempFile = tempFileFor(songId)
            val outcome = downloadWithRetry(task, streamUrl, tempFile, notificationId)
            if (outcome == null || !tempFile.exists() || tempFile.length() <= 0L) {
                deleteTempFile(songId)
                failTask(task, notificationId, "下载失败")
                return false
            }

            // 3. 扩展名按真实类型（Content-Type → URL 后缀 → 音质档位）
            val ext = decideExtension(outcome.mimeType, streamUrl, quality)
            val fileName = buildFileName(song, ext)
            task.fileName = fileName

            // 4. 目标位置：已存在同名文件（且体积正常）→ 直接视为完成，跳过重复下载
            val destination = resolveDestination(fileName)
            if (destination == null) {
                deleteTempFile(songId)
                failTask(task, notificationId, "无法确定保存位置")
                return false
            }
            val skipExisting = runCatching {
                userPreferencesRepository.downloadSkipExistingFlow.first()
            }.getOrDefault(true)
            val existingPath = destination.existingPathIfUsable(fileName)
            if (skipExisting && existingPath != null) {
                deleteTempFile(songId)
                task.filePath = existingPath
                updateDownloadStatus(songId, song.title, song.displayArtist, 100f, DownloadStatus.Completed, existingPath, fileName)
                showDownloadNotification(notificationId, song.title, 100f, isDone = true, success = true, text = "已存在，跳过")
                return true
            }

            // 5. 元数据标签写在**临时文件**上：SAF 目录同样能带上标签
            val embed = readEmbedOptions()
            val lrcText = writeTags(song, tempFile.absolutePath, embed)

            // 6. 落到目标位置（SAF 拷贝 / 公共目录移动）
            updateDownloadStatus(songId, song.title, song.displayArtist, 100f, DownloadStatus.Running, null, fileName)
            showDownloadNotification(notificationId, song.title, 100f, isDone = false, text = "正在写入文件…")
            val finalPath = destination.commit(tempFile, fileName)
            if (finalPath == null) {
                deleteTempFile(songId)
                failTask(task, notificationId, "写入目标目录失败")
                return false
            }

            // 7. 同目录 .lrc + MediaStore 收录
            if (embed.writeLrc && !lrcText.isNullOrBlank()) writeLrcFile(finalPath, lrcText)
            scanFile(finalPath)

            task.filePath = finalPath
            updateDownloadStatus(songId, song.title, song.displayArtist, 100f, DownloadStatus.Completed, finalPath, fileName)
            showDownloadNotification(notificationId, song.title, 100f, isDone = true, success = true, text = "下载完成")
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.e(e, "MusicDownloadService: 下载异常 songId=$songId")
            deleteTempFile(songId)
            failTask(task, notificationId, "下载失败")
            return false
        }
    }

    private fun failTask(task: Task, notificationId: Int, text: String) {
        updateDownloadStatus(
            songId = task.song.id,
            title = task.song.title,
            artist = task.song.displayArtist,
            progress = task.progress,
            status = DownloadStatus.Error,
            filePath = null,
            fileName = task.fileName
        )
        showDownloadNotification(notificationId, task.song.title, task.progress, isDone = true, success = false, text = text)
    }

    /**
     * 带重试与断点续传的下载：写 [tempFile]。
     *
     * - 服务端支持 Range 且临时文件已有内容 → 带 `Range` 续传（重发 [RESUME_OVERLAP_BYTES]
     *   字节并校验，不一致 / 416 则删档重来）；
     * - 401 / 403 / 410（直链过期）或网络异常 → 清掉 preferredUrl 重新解析后重试；
     * - 最多 [MAX_RETRY] 次，间隔 [RETRY_DELAY_MS]。
     */
    private suspend fun downloadWithRetry(
        task: Task,
        initialUrl: String,
        tempFile: File,
        notificationId: Int,
    ): DownloadOutcome? {
        var url = initialUrl
        var lastError: Throwable? = null
        for (attempt in 1..MAX_RETRY) {
            try {
                val outcome = downloadOnce(url, tempFile, notificationId, task)
                if (outcome != null) return outcome
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                lastError = e
                Timber.w(e, "MusicDownloadService: 第 $attempt 次下载失败 songId=${task.song.id}")
                // ⚡ 保留已下载的临时文件：网络抖动 / 直链过期后下一次尝试可以续传。
                //    只有 416 或续传校验失败才需要删档重来（那两种情况已在 downloadOnce 里删过）。
                if (attempt < MAX_RETRY) {
                    val refreshed = resolveStreamUrl(task.song, preferredUrl = null)
                    if (!refreshed.isNullOrBlank()) url = refreshed
                }
            }
            if (attempt < MAX_RETRY) delay(RETRY_DELAY_MS * attempt)
        }
        Timber.w(lastError, "MusicDownloadService: 重试耗尽 songId=${task.song.id}")
        return null
    }

    private data class DownloadOutcome(val mimeType: String)

    /** 单次下载：支持 Range 续传与重叠校验；返回内容类型 */
    private suspend fun downloadOnce(
        url: String,
        tempFile: File,
        notificationId: Int,
        task: Task,
    ): DownloadOutcome? = withContext(Dispatchers.IO) {
        var resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        if (resumeFrom < RESUME_OVERLAP_BYTES) {
            tempFile.delete()
            resumeFrom = 0L
        }
        val overlap = if (resumeFrom > 0) readTail(tempFile, RESUME_OVERLAP_BYTES) else null

        val request = Request.Builder()
            .url(url)
            .apply { if (resumeFrom > 0) header("Range", "bytes=${resumeFrom - RESUME_OVERLAP_BYTES}-") }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // 范围不合法（多半是临时文件比服务端文件还长）→ 删档重来
                tempFile.delete()
                throw IllegalStateException("HTTP 416：Range 不被接受，已重置临时文件")
            }
            if (response.code == 401 || response.code == 403 || response.code == 410) {
                throw IllegalStateException("HTTP ${response.code}：直链已失效")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("响应体为空")
            val mimeType = body.contentType()?.toString().orEmpty()
            val totalLength = body.contentLength()
            val appending = resumeFrom > 0 && response.code == 206

            var written = if (appending) resumeFrom - RESUME_OVERLAP_BYTES else 0L
            val fullLength = if (appending && totalLength > 0) written + totalLength else totalLength
            if (!appending) tempFile.delete()

            body.byteStream().use { input ->
                // 续传：先校验重发的前 N 字节与临时文件尾部一致，否则删档重来
                if (appending && overlap != null) {
                    val head = ByteArray(RESUME_OVERLAP_BYTES)
                    var read = 0
                    while (read < RESUME_OVERLAP_BYTES) {
                        val n = input.read(head, read, RESUME_OVERLAP_BYTES - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < RESUME_OVERLAP_BYTES || !head.contentEquals(overlap)) {
                        tempFile.delete()
                        throw IllegalStateException("续传校验失败，已重置临时文件")
                    }
                }

                FileOutputStream(tempFile, appending).use { output ->
                    copyStream(input, output) { chunk ->
                        written += chunk
                        reportProgress(task, notificationId, written, fullLength)
                    }
                }
            }

            if (fullLength > 0 && written < fullLength) {
                throw IllegalStateException("下载不完整：$written/$fullLength")
            }
            DownloadOutcome(mimeType)
        }
    }

    private inline fun copyStream(input: InputStream, output: OutputStream, onChunk: (Int) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            onChunk(read)
        }
        runCatching { output.flush() }
    }

    private fun reportProgress(task: Task, notificationId: Int, written: Long, total: Long) {
        val progress = if (total > 0) (written.toFloat() / total.toFloat() * 100f).coerceIn(0f, 99f) else 0f
        if (progress - task.progress < 1f && progress < 99f) return
        task.progress = progress
        updateDownloadStatus(
            songId = task.song.id,
            title = task.song.title,
            artist = task.song.displayArtist,
            progress = progress,
            status = DownloadStatus.Running,
            filePath = null,
            fileName = task.fileName
        )
        showDownloadNotification(notificationId, task.song.title, progress, isDone = false)
    }

    private fun readTail(file: File, count: Int): ByteArray? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length < count) return null
            raf.seek(length - count)
            ByteArray(count).also { raf.readFully(it) }
        }
    }.getOrNull()

    // ─── 直链解析 ─────────────────────────────────────────────────────────

    /**
     * 解析可下载的直链：
     * 1. 播放链路已解析的 URL（[preferredUrl]，最省事且避免与播放争抢）；
     * 2. 在线源自定义 scheme（`cloud://lx`、`kgaudio://`、`bilibili://` 等）→ 播放引擎按需解析；
     * 3. 各平台官方 API（网易云 / QQ / Navidrome），按用户音质档位并各自向下回退。
     */
    private suspend fun resolveStreamUrl(song: Song, preferredUrl: String?): String? {
        preferredUrl?.takeIf { it.startsWith("http", ignoreCase = true) }?.let { return it }

        val contentUri = song.contentUriString
        if (!contentUri.isNullOrBlank() &&
            ONLINE_CUSTOM_SCHEMES.any { contentUri.startsWith(it, ignoreCase = true) }
        ) {
            runCatching {
                val resolved = playerEngine.get().resolveCloudUri(Uri.parse(contentUri))
                resolved.toString().takeIf { it.startsWith("http", ignoreCase = true) }
            }.onSuccess { if (it != null) return it }
                .onFailure { Timber.w(it, "MusicDownloadService: 自定义 scheme 解析失败 $contentUri") }
        }

        return getStreamUrl(song)
    }

    private suspend fun getStreamUrl(song: Song): String? {
        return when {
            song.neteaseId != null -> {
                // 使用用户设置的首选音质（无损耗 FLAC 等），API 内部失败会自动回退
                val quality = MusicQualityCatalog.neteaseLevelFor(readQualityPreference())
                neteaseRepository.get().getSongUrl(song.neteaseId, quality).getOrNull()
            }
            song.qqMusicMid != null -> {
                // 使用用户设置的首选音质（无损/320k 等），无对应权限时按档位向下回退
                qqMusicRepository.get().getSongUrl(song.qqMusicMid, readQualityPreference()).getOrNull()
            }
            song.navidromeId != null -> navidromeRepository.get().getStreamUrl(song.navidromeId)
            else -> null
        }
    }

    /**
     * 下载使用的音质：设置里选了具体档位就用它，选「跟随播放音质」则用播放音质。
     * 各平台解析层内部仍会按档位向下回退。
     */
    private suspend fun readQualityPreference(): String {
        val downloadQuality = runCatching {
            userPreferencesRepository.downloadQualityValueFlow.first()
        }.getOrDefault(MusicQualityCatalog.FOLLOW_PLAYBACK)
        if (downloadQuality.isNotBlank() && downloadQuality != MusicQualityCatalog.FOLLOW_PLAYBACK) {
            return downloadQuality
        }
        return runCatching {
            userPreferencesRepository.musicQualityValueFlow.first()
        }.getOrDefault("320k")
    }

    // ─── 文件名 / 扩展名 / 目标位置 ─────────────────────────────────────────

    /** 从 Content-Type（兜底 URL 后缀、音质档位）推导扩展名 */
    private fun decideExtension(mimeType: String, url: String, quality: String): String {
        val mime = mimeType.substringBefore(';').trim().lowercase()
        when {
            mime.contains("flac") -> return "flac"
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> return "m4a"
            mime.contains("opus") -> return "opus"
            mime.contains("ogg") || mime.contains("vorbis") -> return "ogg"
            mime.contains("wav") || mime.contains("x-wav") -> return "wav"
            mime.contains("ape") -> return "ape"
            mime.contains("mpeg") || mime.contains("mp3") -> return "mp3"
        }
        val fromUrl = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        if (fromUrl != null) return fromUrl
        // 兜底：按音质档位猜（无损档给 flac，其余 mp3）
        return when (quality.lowercase()) {
            "24bit", "hires", "flac", "lossless", "ape", "wav" -> "flac"
            else -> "mp3"
        }
    }

    /** 按设置的文件名模板生成文件名（非法字符替换、长度裁剪） */
    private suspend fun buildFileName(song: Song, extension: String): String {
        val template = runCatching {
            userPreferencesRepository.downloadFileNameTemplateFlow.first()
        }.getOrDefault(DownloadFileNameTemplate.ARTIST_TITLE)
        val artist = song.displayArtist.ifBlank { "未知歌手" }
        val title = song.title.ifBlank { "未知歌曲" }
        val base = when (template) {
            DownloadFileNameTemplate.TITLE_ARTIST -> "$title - $artist"
            DownloadFileNameTemplate.TITLE -> title
            else -> "$artist - $title"
        }
        val sanitized = sanitizeFileName(base)
        val clipped = if (sanitized.length > MAX_FILE_NAME_LENGTH - extension.length - 1) {
            sanitized.take((MAX_FILE_NAME_LENGTH - extension.length - 1).coerceAtLeast(1))
        } else {
            sanitized
        }
        return "$clipped.$extension"
    }

    private fun sanitizeFileName(name: String): String =
        name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim().ifBlank { "audio" }

    /** 目标位置抽象：SAF 目录 / 公共 Music 目录 */
    private sealed interface Destination {
        /** 已存在可用文件时返回其路径（跳过重复下载） */
        fun existingPathIfUsable(fileName: String): String?

        /** 把临时文件落到目标位置，返回最终路径（失败返回 null） */
        fun commit(tempFile: File, fileName: String): String?
    }

    private class SafDestination(
        private val context: Context,
        private val treeUri: Uri,
    ) : Destination {
        override fun existingPathIfUsable(fileName: String): String? = runCatching {
            DocumentFile.fromTreeUri(context, treeUri)
                ?.findFile(fileName)
                ?.takeIf { it.isFile && it.length() >= EXISTING_FILE_MIN_BYTES }
                ?.uri?.toString()
        }.getOrNull()

        override fun commit(tempFile: File, fileName: String): String? = runCatching {
            val parent = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            parent.findFile(fileName)?.takeIf { it.isFile }?.delete()
            val target = parent.createFile(mimeTypeForName(fileName), fileName) ?: return null
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: return null
            tempFile.delete()
            target.uri.toString()
        }.getOrNull()
    }

    private class FileDestination(private val target: File) : Destination {
        override fun existingPathIfUsable(fileName: String): String? =
            target.takeIf { it.isFile && it.length() >= EXISTING_FILE_MIN_BYTES }?.absolutePath

        override fun commit(tempFile: File, fileName: String): String? = runCatching {
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            if (tempFile.renameTo(target)) return target.absolutePath
            // 跨文件系统（缓存 → 外置存储）时退化为拷贝
            tempFile.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            tempFile.delete()
            target.absolutePath
        }.getOrNull()
    }

    private suspend fun resolveDestination(fileName: String): Destination? {
        val downloadPathPref = runCatching { userPreferencesRepository.getDownloadPath() }.getOrDefault("")
        if (downloadPathPref.startsWith("content://")) {
            val uri = Uri.parse(downloadPathPref)
            if (DocumentFile.fromTreeUri(context, uri) != null) {
                return SafDestination(context, uri)
            }
            Timber.w("MusicDownloadService: SAF 目录不可用，回退公共 Music 目录")
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)
        if (!dir.exists() && !dir.mkdirs()) {
            Timber.w("MusicDownloadService: 无法创建公共 Music 目录")
            return null
        }
        return FileDestination(File(dir, fileName))
    }

    // ─── 元数据 / 歌词 ─────────────────────────────────────────────────────

    /**
     * 下载完成后自动补全元数据（在临时文件上做，因此 SAF 目录也能带标签）：
     * 封面获取 → 歌词获取 → 写音频标签（标题/歌手/专辑/封面/歌词）。
     * 返回歌词文本，供落盘后写同目录 .lrc 使用。任何一步失败都不影响下载本身。
     */
    private suspend fun writeTags(song: Song, tempFilePath: String, options: EmbedOptions): String? {
        try {
            val needLyrics = options.embedLyrics || options.writeLrc
            val lrcText = if (needLyrics) fetchLyricsText(song) else null
            if (!options.embedCover && !options.embedLyrics) return lrcText

            val coverArt = if (options.embedCover) resolveCoverArt(song) else null
            val embeddedLyrics = if (options.embedLyrics) lrcText else null

            // ⚡ 失败重试一次：写标签偶发失败多半是临时 IO 抖动，第二次通常能成。
            var ok = songMetadataEditor.writeDownloadedFileTags(
                filePath = tempFilePath,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                albumArtist = song.albumArtist,
                lyrics = embeddedLyrics,
                coverArtUpdate = coverArt
            )
            if (!ok) {
                Timber.w("MusicDownloadService: 标签写入失败，重试一次 songId=${song.id}")
                ok = songMetadataEditor.writeDownloadedFileTags(
                    filePath = tempFilePath,
                    title = song.title,
                    artist = song.displayArtist,
                    album = song.album,
                    albumArtist = song.albumArtist,
                    lyrics = embeddedLyrics,
                    coverArtUpdate = coverArt
                )
            }
            Timber.d("MusicDownloadService: 元数据补全 ${if (ok) "成功" else "失败"} songId=${song.id}")
            return lrcText
        } catch (e: Exception) {
            Timber.e(e, "MusicDownloadService: writeTags failed songId=${song.id}")
            return null
        }
    }

    /** 下载相关的内嵌开关（设置 → 下载设置） */
    private data class EmbedOptions(
        val embedCover: Boolean,
        val embedLyrics: Boolean,
        val writeLrc: Boolean,
    )

    private suspend fun readEmbedOptions(): EmbedOptions = EmbedOptions(
        embedCover = runCatching { userPreferencesRepository.downloadEmbedCoverFlow.first() }.getOrDefault(true),
        embedLyrics = runCatching { userPreferencesRepository.downloadEmbedLyricsFlow.first() }.getOrDefault(true),
        writeLrc = runCatching { userPreferencesRepository.downloadWriteLrcFlow.first() }.getOrDefault(true),
    )

    /** 取歌词文本（在线源 → 本地 .lrc），失败返回 null，不影响下载 */
    private suspend fun fetchLyricsText(song: Song): String? = try {
        val lyrics = lyricsRepository.getLyrics(
            song,
            sourcePreference = LyricsSourcePreference.API_FIRST,
            forceRefresh = false
        )
        lyrics?.let { LyricsUtils.toLrcString(it) }?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Timber.w(e, "MusicDownloadService: 歌词获取失败 songId=${song.id}")
        null
    }

    /**
     * 封面：远程 URL 直接下载；本地歌曲的封面（content:// / file://）也读出来内嵌，
     * 否则本地库歌曲下载后永远没有封面（之前只认 http(s)，表现就是"内嵌封面时有时无"）。
     */
    private suspend fun resolveCoverArt(song: Song): CoverArtUpdate? {
        val art = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return null
        return when {
            art.startsWith("http://", ignoreCase = true) || art.startsWith("https://", ignoreCase = true) ->
                downloadCoverArt(art)
            art.startsWith("content://", ignoreCase = true) || art.startsWith("file://", ignoreCase = true) ->
                readLocalCoverArt(art)
            else -> null
        }
    }

    private suspend fun readLocalCoverArt(uriString: String): CoverArtUpdate? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val bytes = if (uriString.startsWith("file://", ignoreCase = true)) {
                File(uri.path ?: return@withContext null).takeIf { it.isFile }?.readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // 封面不可能太大，限制 8MB 防止读到异常数据
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    var total = 0L
                    while (total <= 8L * 1024 * 1024) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        buffer.write(chunk, 0, read)
                        total += read
                    }
                    buffer.toByteArray()
                }
            } ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            CoverArtUpdate(bytes = bytes, mimeType = mime)
        } catch (e: Exception) {
            Timber.w(e, "MusicDownloadService: 本地封面读取失败 $uriString")
            null
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
                    parentDoc.findFile(lrcName)?.delete()
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

    private fun scanFile(path: String) {
        if (path.startsWith("content://")) return
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) }
    }

    // ─── 状态 / 通知 / 临时文件 ─────────────────────────────────────────────

    private fun tempFileFor(songId: String): File =
        File(tempDir, "${songId.hashCode().toUInt().toString(16)}_${songId.length}.part")

    private fun deleteTempFile(songId: String) {
        runCatching { tempFileFor(songId).takeIf { it.exists() }?.delete() }
    }

    private fun notificationIdFor(songId: String): Int =
        DOWNLOAD_NOTIFICATION_ID_BASE + (songId.hashCode() and 0x7FFFFFFF) % 1000

    private fun updateDownloadStatus(
        songId: String,
        title: String,
        artist: String,
        progress: Float,
        status: DownloadStatus,
        filePath: String?,
        fileName: String?,
    ) {
        // ⚡ 必须做 upsert：之前用 `.map{...}.ifEmpty{ 新增 }`，只有列表整体为空时才会插入新条目。
        //    第一次下载把列表变成非空后，之后任何新歌曲的状态更新都会被静默丢弃，
        //    导致 downloads.find { it.songId == 新歌 } 恒为 null —— 表现就是"只有第一次下载能显示进度"。
        val current = _downloads.value
        val index = current.indexOfFirst { it.songId == songId }
        val isComplete = status == DownloadStatus.Completed
        val isFailed = status == DownloadStatus.Error
        _downloads.value = if (index >= 0) {
            current.toMutableList().apply {
                val old = this[index]
                this[index] = old.copy(
                    title = title,
                    artist = artist,
                    progress = progress,
                    isComplete = isComplete,
                    isFailed = isFailed,
                    filePath = filePath ?: old.filePath.takeIf { isComplete },
                    status = status,
                    fileName = fileName ?: old.fileName
                )
            }
        } else {
            current + DownloadInfo(
                songId = songId,
                title = title,
                artist = artist,
                progress = progress,
                isComplete = isComplete,
                isFailed = isFailed,
                filePath = filePath,
                status = status,
                fileName = fileName
            )
        }
        rebuildDownloadIndex()
    }

    fun getDownloadInfo(songId: String): DownloadInfo? {
        val index = downloadIndex
        return if (index.isNotEmpty()) index[songId] else _downloads.value.find { it.songId == songId }
    }

    /** 在通知栏展示下载进度（进行中/暂停/完成/失败），完成后几秒自动清除 */
    private fun showDownloadNotification(
        notificationId: Int,
        title: String,
        progress: Float,
        isDone: Boolean,
        success: Boolean = true,
        text: String? = null,
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(com.theveloper.pixelplay.R.drawable.monochrome_player)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setOngoing(!isDone)

            if (isDone) {
                builder
                    .setContentText(text ?: if (success) "下载完成" else "下载失败")
                    .setAutoCancel(true)
            } else {
                val pct = progress.toInt().coerceIn(0, 100)
                builder
                    .setContentText(text ?: "下载中 $pct%")
                    .setProgress(100, pct, progress <= 0f)
                // 点击通知回到播放页
                val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (openAppIntent != null) {
                    builder.setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            notificationId,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            if (isDone) {
                mainHandler.postDelayed({
                    runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
                }, 4000)
            }
        } catch (t: Throwable) {
            Timber.w(t, "MusicDownloadService: 通知展示失败")
        }
    }
}

/** 由扩展名推断 MIME（SAF createFile 需要） */
private fun mimeTypeForName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "flac" -> "audio/flac"
    "m4a" -> "audio/mp4"
    "opus" -> "audio/opus"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/x-wav"
    "ape" -> "audio/ape"
    else -> "audio/mpeg"
}
