package com.theveloper.pixelplay.data.service.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackStateHolder
import com.theveloper.pixelplay.utils.MediaItemBuilder
import com.theveloper.pixelplay.utils.AlbumArtUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.ServerSocket
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class WebRemoteServerService : LifecycleService() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var lyricsRepository: LyricsRepository

    @Inject
    lateinit var dualPlayerEngine: DualPlayerEngine

    @Inject
    lateinit var playbackStateHolder: PlaybackStateHolder

    @Inject
    lateinit var neteaseStreamProxy: com.theveloper.pixelplay.data.netease.NeteaseStreamProxy

    @Inject
    lateinit var qqMusicStreamProxy: com.theveloper.pixelplay.data.qqmusic.QqMusicStreamProxy

    private var server: Any? = null
    private var port: Int = 8080
    private var isSyncMode: Boolean = false
    private var isAudioOnDevice: Boolean = true
    private var themeColor: String = "#6750A4"

    private val activeConnections = java.util.concurrent.CopyOnWriteArrayList<WsClient>()

    /**
     * /api/stream 复用的 OkHttpClient。此前每个请求都 new 一个，连接池与线程池永不回收，
     * 长时间串流会不断累积线程/连接，最终拖垮 Ktor CIO 的 worker → WebSocket 被掐成 code=1006。
     * readTimeout 必须为 0（不超时）：音频是"边下边播"，读间隔可能远超常规 30s。
     */
    private val streamHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // 在线音源 CDN 普遍校验 UA：默认的 "okhttp/x.y.z" 会被 403，
            // 网页端 <audio> 收到错误响应直接 MediaError → 无声。
            // 与 DualPlayerEngine 播放器使用的 UA 保持一致。
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(
                        request.newBuilder().header("User-Agent", STREAM_USER_AGENT).build()
                    )
                }
            }
            .build()
    }

    /**
     * 单连接发送保护：Ktor 的 WebSocketSession 不允许并发 send（会抛
     * "Another send is already in progress"），且僵死连接的 send 会永久挂起、
     * 卡死整个广播循环。因此每个连接独享 Mutex 串行化 + withTimeout 兜底，
     * 发送失败立即剔除该客户端，绝不让一个坏连接拖垮所有人。
     */
    private inner class WsClient(val session: DefaultWebSocketSession) {
        val sendMutex = Mutex()
        var firstFrameLogged = false
        suspend fun sendText(json: String): Boolean = try {
            sendMutex.withLock {
                withTimeout(3000L) { session.send(Frame.Text(json)) }
            }
            if (!firstFrameLogged) {
                firstFrameLogged = true
                Timber.i("WS first frame written to client session (active=${activeConnections.size})")
            }
            true
        } catch (e: Exception) {
            Timber.w(e, "WS send failed, dropping client")
            runCatching { session.close() }
            activeConnections.remove(this)
            false
        }
    }

    val activeConnectionsCount: Int
        get() = activeConnections.size

    private var previousVolume: Int = -1

    /**
     * 熄屏保活：Web 远控是"局域网长连接"场景，手机一旦熄屏进入 Wi-Fi 省电 /
     * Doze，射频会被降频甚至挂起，浏览器侧看到的正是异常断开 code=1006。
     * 服务运行期间持有 WifiLock(HIGH_PERF) + PARTIAL_WAKE_LOCK，保证熄屏后
     * TCP 连接与心跳仍能正常收发。
     */
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    private fun acquireKeepAliveLocks() {
        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pixelplay:webremote")
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Timber.i("WifiLock acquired (web remote keep-alive)")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to acquire WifiLock")
        }
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelplay:webremote")
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.setReferenceCounted(false)
                wakeLock?.acquire()
                Timber.i("PARTIAL_WAKE_LOCK acquired (web remote keep-alive)")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to acquire WakeLock")
        }
    }

    private fun releaseKeepAliveLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Timber.w(e, "Failed to release WifiLock")
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Timber.w(e, "Failed to release WakeLock")
        }
        wifiLock = null
        wakeLock = null
    }

    /**
     * 是否由手机本机出声。这里只认用户的显式选择，不再回头看 activeConnections：
     * WS 偶发瞬断（code=1006）时 activeConnections 会瞬间为空，若此时据此恢复手机出声，
     * 会出现"手机突然外放 1s → 重连后又静音"的来回横跳，听感就是声音时有时无。
     * 全部客户端断开后的恢复改由 scheduleResumePhonePlaybackIfIdle() 延迟兜底。
     */
    private fun shouldPlayOnDevice(): Boolean = isAudioOnDevice

    /**
     * 用 DualPlayerEngine 的统一在线源解析器，把 Song.contentUriString 解析成
     * 可直接播放的 http(s) 直链或本地文件路径。解析不出来返回 null（调用方回退原有逻辑）。
     */
    private suspend fun resolveCloudPlayablePath(contentUriString: String): String? {
        if (contentUriString.isBlank()) return null
        val contentUri = runCatching { Uri.parse(contentUriString) }.getOrNull() ?: return null
        if (contentUri.scheme !in CLOUD_PLAYABLE_SCHEMES) return null
        val resolved = withTimeoutOrNull(12_000L) {
            runCatching { dualPlayerEngine.resolveCloudUri(contentUri) }.getOrNull()
        } ?: return null
        val resolvedString = resolved.toString()
        if (resolvedString.isBlank() || resolvedString == contentUriString) return null
        return if (resolved.scheme == "file") resolved.path else resolvedString
    }

    private val mainThreadHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun syncPlayerVolume() {
        // ⚡ Media3 的 Player 方法（含 setVolume）强制要求主线程调用，否则抛
        //   IllegalStateException("Player is accessed on the wrong thread")。
        //   HTTP 处理器与 WebSocket 回调都跑在 Ktor 的工作线程上，直接摸 masterPlayer 会让
        //   /api/player/toggleAudioOnDevice 返回 500，body 还是裸文本 "Error: Player is ..."；
        //   网页端再按 JSON 解析 → 抛 SyntaxError，表现就是"切手机播放失败"。
        //   统一在这里兜底切主线程，避免每个调用点都要自己记得包一层 lifecycleScope.launch。
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainThreadHandler.post { syncPlayerVolume() }
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val shouldPlay = shouldPlayOnDevice()
        
        if (shouldPlay) {
            if (previousVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                Timber.i("syncPlayerVolume: Restoring system volume to $previousVolume")
                previousVolume = -1
            }
        } else {
            if (previousVolume < 0) {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                Timber.i("syncPlayerVolume: Saving current volume $previousVolume before muting")
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            Timber.i("syncPlayerVolume: Setting system volume to 0 (muted)")
        }
        
        if (::dualPlayerEngine.isInitialized) {
            dualPlayerEngine.masterPlayer.volume = if (shouldPlay) 1f else 0f
        }
    }

    /** 全部客户端离线后的延迟恢复任务，见 scheduleResumePhonePlaybackIfIdle() */
    private var idleResumeJob: Job? = null

    /**
     * 所有 Web 客户端断开后，延迟 20s 再恢复手机出声。
     * 以前是"一断就切回手机播放"，而 WebSocket 局域网上瞬断(code=1006)极常见，
     * 客户端 1~2s 内就会自动重连 —— 结果每闪断一次就被踢回手机模式，
     * 网页端 <audio> 也随之被 stopStreaming()，用户听到的就是"声音时有时无 + 远控不稳定"。
     *
     * 阈值从 8s 放宽到 20s：Web 客户端重连退避最长 5s，页面被切到后台时浏览器还会
     * 进一步节流定时器（可达分钟级），8s 窗口内没重连上就会被误判为"已离线"，
     * 于是把网页正在播放的模式静默翻回手机播放 → 网页无声。
     */
    private fun scheduleResumePhonePlaybackIfIdle() {
        if (activeConnections.isNotEmpty()) {
            idleResumeJob?.cancel()
            idleResumeJob = null
            return
        }
        idleResumeJob?.cancel()
        idleResumeJob = lifecycleScope.launch {
            delay(20_000)
            if (activeConnections.isEmpty() && !isAudioOnDevice) {
                isAudioOnDevice = true
                Timber.i("No web remote clients for 20s, resuming phone playback")
                syncPlayerVolume()
                broadcastCurrentState()
            }
        }
    }

    companion object {
        @Volatile
        var serverAddress: String? = null

        @Volatile
        var currentPin: String? = null

        @Volatile
        var isServerRunning: Boolean = false

        const val CHANNEL_ID = "web_remote_channel"
        const val NOTIFICATION_ID = 12345

        const val ACTION_START_SERVER = "com.theveloper.pixelplay.action.START_WEB_REMOTE"
        const val ACTION_STOP_SERVER = "com.theveloper.pixelplay.action.STOP_WEB_REMOTE"
        const val ACTION_UPDATE_THEME = "com.theveloper.pixelplay.action.UPDATE_WEB_REMOTE_THEME"

        /** contentUriString 中可由 DualPlayerEngine 统一解析的在线音源 scheme。 */
        val CLOUD_PLAYABLE_SCHEMES = setOf(
            "cloud", "telegram", "gdrive", "navidrome", "jellyfin", "bilibili", "netease", "qqmusic"
        )

        /** 代理在线音频时的 UA，与 DualPlayerEngine 播放器保持一致（绕开 CDN 的 UA 校验）。 */
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): WebRemoteServerService = this@WebRemoteServerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("WebRemoteServerService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP_SERVER -> {
                lifecycleScope.launch {
                    stopServer()
                }
                return START_NOT_STICKY
            }
            ACTION_UPDATE_THEME -> {
                val newColor = intent.getStringExtra("themeColor") ?: return START_STICKY
                themeColor = newColor
                // Broadcast theme color to all connected WebSocket clients
                lifecycleScope.launch {
                    val broadcast = """{"action":"setThemeColor","color":"$newColor"}"""
                    activeConnections.forEach { conn ->
                        launch { conn.sendText(broadcast) }
                    }
                }
                return START_STICKY
            }
        }

        val preferredPort = intent?.getIntExtra("port", 8080) ?: 8080
        isSyncMode = intent?.getBooleanExtra("syncMode", false) ?: false
        // ⚡ 只在 intent 显式携带该 extra 时才覆盖：START_STICKY 被系统以 null intent 重建时，
        //   无脑回落 true 会把用户已选的"网页播放"静默翻成"手机播放"并广播出去，
        //   网页收到 audioOnDevice=true 后会 stopStreaming() → 永久无声。
        if (intent?.hasExtra("audioOnDevice") == true) {
            isAudioOnDevice = intent.getBooleanExtra("audioOnDevice", isAudioOnDevice)
        }
        themeColor = intent?.getStringExtra("themeColor") ?: "#6750A4"

        lifecycleScope.launch {
            startServer(preferredPort)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // 注意：lifecycleScope 在 onDestroy 后会被取消，协程里的 stopServer 可能来不及执行，
        // 保活锁必须同步释放，否则会残留持有（耗电 / 影响系统 Wi-Fi 省电）。
        releaseKeepAliveLocks()
        super.onDestroy()
        lifecycleScope.launch {
            stopServer()
        }
    }

    private fun createNotificationChannel() {
        // NotificationChannel 是 API 26 才有的类，低版本机型（如 Android 7.1）直接跳过，避免崩溃
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Web Remote",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Web Remote Server"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Web Remote")
            .setContentText("Server running at $serverAddress")
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setOngoing(true)
            .build()
    }

    private suspend fun startServer(preferredPort: Int) {
        if (isServerRunning) {
            Timber.w("Server is already running")
            return
        }

        val resolvedPort = resolveServerPort(preferredPort)
        this.port = resolvedPort
        currentPin = generatePin()
        serverAddress = "${getIpAddress()}:$resolvedPort"

        Timber.i("Starting web remote server at http://$serverAddress with PIN $currentPin")

        try {
            // ⚡ WS 稳定性：曾尝试 Netty 引擎但 netty 4.2.x 需要 minSdk 26+（D8 拒绝 MethodHandle 指令，
            //   项目 minSdk=23 无法编译）。回退 CIO 引擎，稳定性靠：pingPeriod 心跳 + WsClient 串行化
            //   发送 + 客户端 2s 应用层心跳。
            // ⚡ Ktor 3.5 移除了 embeddedServer(factory, port, configure) 重载：端口必须通过
            //   configure 里的 connector { } 指定，module 则通过 rootConfig = serverConfig { module { } } 注册。
            val webRemoteModule: suspend Application.() -> Unit = {
                install(DefaultHeaders)
                install(ContentNegotiation) {
                    json()
                }
                install(CORS) {
                    anyHost()
                }
                install(WebSockets) {
                    // 心跳保活：裸装无 ping 时，NAT/路由器会把"看似空闲"的连接掐掉（表现为连上几秒即断）
                    pingPeriod = 15.seconds
                    timeout = 30.seconds
                }

                routing {
                    get("/") {
                        call.respondRedirect("/index.html", permanent = false)
                    }

                    get("/index.html") {
                        try {
                            val inputStream = assets.open("web-remote/index.html")
                            val bytes = inputStream.readBytes()
                            inputStream.close()
                            call.response.header("Content-Type", "text/html; charset=UTF-8")
                            call.response.header("Cache-Control", "no-cache, no-store, must-revalidate")
                            call.response.header("Pragma", "no-cache")
                            call.respond(bytes)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to serve index.html")
                            call.respondText("File not found", status = io.ktor.http.HttpStatusCode.NotFound)
                        }
                    }

                    post("/api/auth") {
                        val pin = call.parameters["pin"] ?: ""
                        if (pin == currentPin) {
                            call.respond(AuthResponse(success = true, message = "Authenticated", syncMode = isSyncMode, audioOnDevice = isAudioOnDevice, themeColor = themeColor))
                        } else {
                            call.respond(AuthResponse(success = false, message = "Invalid PIN"))
                        }
                    }

                    get("/api/player/state") {
                        try {
                            val state = playbackStateHolder.stablePlayerState.first()
                            val position = playbackStateHolder.currentPosition.first()
                            call.respond(PlayerState(
                                isPlaying = state.isPlaying,
                                song = state.currentSong?.toSongDto(),
                                position = position,
                                duration = state.totalDuration,
                                volume = 1.0f,
                                syncMode = isSyncMode,
                                audioOnDevice = isAudioOnDevice,
                                themeColor = themeColor
                            ))
                        } catch (e: Exception) {
                            Timber.e(e, "Error getting player state")
                            call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                        }
                    }

                    post("/api/player/play") {
                        val songId = call.parameters["songId"]
                        lifecycleScope.launch {
                            songId?.let {
                                musicRepository.getSongsByIds(listOf(it)).first().firstOrNull()?.let { song ->
                                    if (::dualPlayerEngine.isInitialized) {
                                        val mediaItem = MediaItemBuilder.build(song)
                                        dualPlayerEngine.masterPlayer.setMediaItem(mediaItem)
                                        dualPlayerEngine.masterPlayer.prepare()
                                        dualPlayerEngine.masterPlayer.play()
                                        syncPlayerVolume()
                                    }
                                }
                            } ?: run {
                                if (::dualPlayerEngine.isInitialized) {
                                    dualPlayerEngine.masterPlayer.play()
                                    syncPlayerVolume()
                                }
                            }
                        }
                        call.respond(OperationResponse(success = true))
                    }

                    post("/api/player/pause") {
                        lifecycleScope.launch {
                            if (::dualPlayerEngine.isInitialized) {
                                dualPlayerEngine.masterPlayer.pause()
                            }
                        }
                        call.respond(OperationResponse(success = true))
                    }

                    post("/api/player/skip") {
                        val next = call.parameters["next"]?.toBoolean() ?: true
                        lifecycleScope.launch {
                            if (::playbackStateHolder.isInitialized) {
                                if (next) playbackStateHolder.nextSong() else playbackStateHolder.previousSong()
                            }
                        }
                        call.respond(OperationResponse(success = true))
                    }

                    post("/api/player/seek") {
                        val position = call.parameters["position"]?.toLongOrNull()
                        position?.let {
                            lifecycleScope.launch {
                                if (::playbackStateHolder.isInitialized) {
                                    playbackStateHolder.seekTo(it)
                                }
                                delay(200)
                                broadcastCurrentState()
                            }
                        }
                        call.respond(OperationResponse(success = true))
                    }

                    post("/api/player/playPause") {
                        lifecycleScope.launch {
                            if (::playbackStateHolder.isInitialized) {
                                playbackStateHolder.playPause()
                                syncPlayerVolume()
                            }
                            delay(200)
                            broadcastCurrentState()
                        }
                        call.respond(OperationResponse(success = true))
                    }

                    post("/api/player/toggleAudioOnDevice") {
                        try {
                            isAudioOnDevice = !isAudioOnDevice
                            syncPlayerVolume()
                            lifecycleScope.launch {
                                delay(200)
                                broadcastCurrentState()
                            }
                            val state = playbackStateHolder.stablePlayerState.first()
                            val position = playbackStateHolder.currentPosition.first()
                            val playerState = PlayerState(
                                isPlaying = state.isPlaying,
                                song = state.currentSong?.toSongDto(),
                                position = position,
                                duration = state.totalDuration,
                                volume = 1.0f,
                                syncMode = isSyncMode,
                                audioOnDevice = isAudioOnDevice,
                                themeColor = themeColor
                            )
                            call.respond(playerState)
                        } catch (e: Exception) {
                            Timber.e(e, "Error toggling audio device")
                            call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                        }
                    }

                    post("/api/player/toggleLike") {
                        try {
                            val songId = playbackStateHolder.stablePlayerState.first().currentSong?.id
                            if (songId != null) {
                                musicRepository.toggleFavoriteStatus(songId)
                                lifecycleScope.launch {
                                    delay(200)
                                    broadcastCurrentState()
                                }
                            }
                            call.respond(OperationResponse(success = true))
                        } catch (e: Exception) {
                            Timber.e(e, "Error toggling like")
                            call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                        }
                    }

                    get("/api/search") {
                        val query = call.parameters["q"] ?: ""
                        val songs = musicRepository.searchSongs(query).first()
                        call.respond(SearchResponse(results = songs.map { it.toSongDto() }))
                    }

                    get("/api/lyrics/{songId}") {
                        val encodedSongId = call.parameters["songId"] ?: ""
                        val songId = java.net.URLDecoder.decode(encodedSongId, "UTF-8")
                        Timber.i("GET /api/lyrics songId=$songId (encoded=$encodedSongId)")
                        
                        var song: com.theveloper.pixelplay.data.model.Song? = null
                        
                        if (songId.startsWith("roaming_")) {
                            val neteaseId = songId.removePrefix("roaming_").toLongOrNull()
                            if (neteaseId != null) {
                                val state = playbackStateHolder.stablePlayerState.first()
                                state.currentSong?.let { currentSong ->
                                    if (currentSong.id == songId) {
                                        song = currentSong
                                        Timber.i("  Found roaming song from current state: ${song?.title}")
                                    }
                                }
                                if (song == null) {
                                    song = com.theveloper.pixelplay.data.model.Song(
                                        id = songId,
                                        title = "Unknown",
                                        artist = "Unknown",
                                        artistId = 0L,
                                        artists = emptyList(),
                                        album = "Unknown",
                                        albumId = 0L,
                                        path = "",
                                        contentUriString = "",
                                        albumArtUriString = null,
                                        duration = 0L,
                                        mimeType = null,
                                        neteaseId = neteaseId,
                                        bitrate = null,
                                        sampleRate = null
                                    )
                                }
                            }
                        } else {
                            val songs = musicRepository.getSongsByIds(listOf(songId)).first()
                            Timber.i("  Found ${songs.size} songs for ID: $songId")
                            song = songs.firstOrNull()
                            // ⚡ 在线歌曲 id 同 albumArt：getSongsByIds 查不到 → 回退当前播放歌曲
                            if (song == null) {
                                val state = playbackStateHolder.stablePlayerState.first()
                                state.currentSong?.takeIf { it.id == songId }?.let { currentSong ->
                                    Timber.i("  Falling back to current playing song for lyrics: ${currentSong.title}")
                                    song = currentSong
                                }
                            }
                        }
                        
                        if (song != null) {
                            Timber.i("  Getting lyrics for: ${song?.title} - ${song?.artist}")
                            val lyrics = lyricsRepository.getLyrics(song!!)
                            val lyricsText = if (lyrics?.synced != null && lyrics.synced.isNotEmpty()) {
                                lyrics.synced.joinToString("\n") { line ->
                                    val totalMs = line.time
                                    val minutes = totalMs / 60000
                                    val seconds = (totalMs % 60000) / 1000
                                    val millis = totalMs % 1000
                                    val timestamp = String.format("[%02d:%02d.%02d]", minutes, seconds, millis / 10)
                                    val text = line.line.replace("\n", "\\n")
                                    "$timestamp$text"
                                }
                            } else {
                                lyrics?.plain?.joinToString("\n") ?: ""
                            }
                            Timber.i("  Lyrics length: ${lyricsText.length}")
                            call.respond(LyricsResponse(lyrics = lyricsText))
                        } else {
                            Timber.w("  Song not found for ID: $songId")
                            call.respond(LyricsResponse(lyrics = ""))
                        }
                    }

                    get("/api/albumArt/{songId}") {
                        val encodedSongId = call.parameters["songId"] ?: ""
                        val songId = java.net.URLDecoder.decode(encodedSongId, "UTF-8")
                        Timber.i("GET /api/albumArt songId=$songId (encoded=$encodedSongId)")
                        
                        var song: com.theveloper.pixelplay.data.model.Song? = null
                        
                        if (songId.startsWith("roaming_")) {
                            val state = playbackStateHolder.stablePlayerState.first()
                            state.currentSong?.let { currentSong ->
                                if (currentSong.id == songId) {
                                    song = currentSong
                                    Timber.i("  Found roaming song from current state: ${song?.title}")
                                }
                            }
                        } else {
                            val songs = musicRepository.getSongsByIds(listOf(songId)).first()
                            Timber.i("  Found ${songs.size} songs for ID: $songId")
                            song = songs.firstOrNull()
                            // ⚡ 在线歌曲 id（netease_xxx / cloud://lx/...）getSongsByIds 只按纯数字 id 查，
                            //    永远查不到 → 一律 404。回退到当前播放歌曲（coverUrl 正是用它的 id 构造的）。
                            if (song == null || song!!.albumArtUriString == null) {
                                val state = playbackStateHolder.stablePlayerState.first()
                                state.currentSong?.takeIf { it.id == songId }?.let { currentSong ->
                                    Timber.i("  Falling back to current playing song: ${currentSong.title}, albumArt=${currentSong.albumArtUriString}")
                                    song = currentSong
                                }
                            }
                        }
                        
                        if (song != null && song!!.albumArtUriString != null) {
                            Timber.i("  Found song: ${song!!.title}, albumArtUriString=${song!!.albumArtUriString}")
                            try {
                                val artUri = song!!.albumArtUriString!!
                                val bytes: ByteArray? = if (artUri.startsWith("http://") || artUri.startsWith("https://")) {
                                    val url = java.net.URL(artUri)
                                    val connection = url.openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 10000
                                    connection.readTimeout = 10000
                                    connection.connect()
                                    connection.inputStream.use { it.readBytes() }
                                } else {
                                    val uri = Uri.parse(artUri)
                                    val inputStream = AlbumArtUtils.openArtworkInputStream(applicationContext, uri)
                                    inputStream?.use { it.readBytes() }
                                }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val contentType = if (bytes.size >= 4 &&
                                        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                                        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
                                    ) "image/png" else "image/jpeg"
                                    call.response.header("Content-Type", contentType)
                                    call.respond(bytes)
                                } else {
                                    call.respondText("Album art not found", status = io.ktor.http.HttpStatusCode.NotFound)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to serve album art")
                                call.respondText("Error", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        } else {
                            call.respondText("Album art not found", status = io.ktor.http.HttpStatusCode.NotFound)
                        }
                    }

                    get("/api/stream/{songId}") {
                        val encodedSongId = call.parameters["songId"] ?: ""
                        val songId = java.net.URLDecoder.decode(encodedSongId, "UTF-8")
                        Timber.i("GET /api/stream songId=$songId (encoded=$encodedSongId)")
                        
                        var song: com.theveloper.pixelplay.data.model.Song? = null
                        
                        if (songId.startsWith("roaming_")) {
                            val state = playbackStateHolder.stablePlayerState.first()
                            state.currentSong?.let { currentSong ->
                                if (currentSong.id == songId) {
                                    song = currentSong
                                    Timber.i("  Found roaming song from current state: ${song?.title}")
                                }
                            }
                            if (song == null) {
                                val neteaseId = songId.removePrefix("roaming_").toLongOrNull()
                                if (neteaseId != null) {
                                    song = com.theveloper.pixelplay.data.model.Song(
                                        id = songId,
                                        title = "Unknown",
                                        artist = "Unknown",
                                        artistId = 0L,
                                        artists = emptyList(),
                                        album = "Unknown",
                                        albumId = 0L,
                                        path = "",
                                        contentUriString = "",
                                        albumArtUriString = null,
                                        duration = 0L,
                                        mimeType = "audio/mpeg",
                                        neteaseId = neteaseId,
                                        bitrate = null,
                                        sampleRate = null
                                    )
                                }
                            }
                        } else {
                            val songs = musicRepository.getSongsByIds(listOf(songId)).first()
                            Timber.i("  Found ${songs.size} songs for ID: $songId")
                            song = songs.firstOrNull()
                            // ⚡ 在线歌曲 id 同 albumArt：getSongsByIds 查不到 → 回退当前播放歌曲
                            if (song == null) {
                                val state = playbackStateHolder.stablePlayerState.first()
                                state.currentSong?.takeIf { it.id == songId }?.let { currentSong ->
                                    Timber.i("  Falling back to current playing song: ${currentSong.title}")
                                    song = currentSong
                                }
                            }
                        }
                        
                        if (song != null) {
                            Timber.i("  Found song: ${song!!.title}, path=${song!!.path}, neteaseId=${song!!.neteaseId}, qqMusicMid=${song!!.qqMusicMid}")
                            try {
                                // ⚡ 云端/在线歌曲的 path 通常为空，真实来源在 contentUriString
                                //    （cloud://lx/...、telegram://、gdrive://、navidrome://、jellyfin:// 等）。
                                //    统一交给 DualPlayerEngine 的解析器换成可播放的 http(s) 直链或本地文件路径，
                                //    否则会一路走到 "Stream not available" 404 → 浏览器 <audio> 报 MediaError → 无声。
                                val rawPath = song!!.path
                                val songPath = if (
                                    rawPath.startsWith("http://") ||
                                    rawPath.startsWith("https://") ||
                                    java.io.File(rawPath).isFile
                                ) {
                                    rawPath
                                } else {
                                    resolveCloudPlayablePath(song!!.contentUriString) ?: rawPath
                                }
                                Timber.i("  Resolved songPath=$songPath")
                                
                                if (songPath.startsWith("http://") || songPath.startsWith("https://")) {
                                    Timber.i("  Proxying HTTP stream URL: $songPath")
                                    val requestBuilder = okhttp3.Request.Builder().url(songPath)
                                    call.request.headers["Range"]?.let { requestBuilder.header("Range", it) }

                                    val response = withContext(Dispatchers.IO) {
                                        streamHttpClient.newCall(requestBuilder.build()).execute()
                                    }
                                    response.use { upstream ->
                                        if (upstream.code != 200 && upstream.code != 206) {
                                            Timber.e("  Upstream error: ${upstream.code}")
                                            call.respondText("Upstream error", status = io.ktor.http.HttpStatusCode.BadGateway)
                                            return@get
                                        }

                                        val body = upstream.body ?: run {
                                            call.respondText("No content", status = io.ktor.http.HttpStatusCode.BadGateway)
                                            return@get
                                        }

                                        val contentType = upstream.header("Content-Type") ?: "audio/mpeg"
                                        upstream.header("Content-Length")?.let { call.response.header("Content-Length", it) }
                                        upstream.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                                        upstream.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

                                        if (upstream.code == 206) {
                                            call.response.status(io.ktor.http.HttpStatusCode.PartialContent)
                                        }
                                        call.response.header("Content-Type", contentType)
                                        call.response.header("Access-Control-Allow-Origin", "*")

                                        call.respondOutputStream {
                                            val out = this
                                            withContext(Dispatchers.IO) {
                                                body.byteStream().use { it.copyTo(out) }
                                            }
                                        }
                                    }
                                    return@get
                                }
                                
                                val file = java.io.File(songPath)
                                if (file.exists() && file.canRead()) {
                                    val fileSize = file.length()
                                    val mimeType = song!!.mimeType ?: "audio/mpeg"

                                    val rangeHeader = call.request.headers["Range"]
                                    if (rangeHeader != null) {
                                        val rangeMatch = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
                                        if (rangeMatch != null) {
                                            val start = rangeMatch.groupValues[1].toLong()
                                                .coerceIn(0L, (fileSize - 1).coerceAtLeast(0L))
                                            val end = if (rangeMatch.groupValues[2].isNotEmpty()) {
                                                rangeMatch.groupValues[2].toLong()
                                            } else {
                                                fileSize - 1
                                            }.coerceAtMost(fileSize - 1)

                                            java.io.FileInputStream(file).use { inputStream ->
                                                // FileInputStream.skip() 允许"部分跳过"，用它做 Range 定位
                                                // 会导致起点偏移、音频数据错位（浏览器解码失败 → 串流卡死）。
                                                // 改用 FileChannel.position 精确定位。
                                                inputStream.channel.position(start)
                                                val contentLength = end - start + 1

                                                call.response.status(io.ktor.http.HttpStatusCode.PartialContent)
                                                call.response.header("Content-Type", mimeType)
                                                call.response.header("Accept-Ranges", "bytes")
                                                call.response.header("Content-Range", "bytes $start-$end/$fileSize")
                                                call.response.header("Content-Length", contentLength.toString())
                                                call.response.header("Access-Control-Allow-Origin", "*")
                                                call.respondOutputStream {
                                                    val out = this
                                                    withContext(Dispatchers.IO) {
                                                        var remaining = contentLength
                                                        val buffer = ByteArray(8192)
                                                        while (remaining > 0) {
                                                            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                                                            val read = inputStream.read(buffer, 0, toRead)
                                                            if (read == -1) break
                                                            out.write(buffer, 0, read)
                                                            remaining -= read
                                                        }
                                                    }
                                                }
                                            }
                                            return@get
                                        }
                                    }

                                    call.response.header("Content-Type", mimeType)
                                    call.response.header("Content-Length", fileSize.toString())
                                    call.response.header("Accept-Ranges", "bytes")
                                    call.response.header("Access-Control-Allow-Origin", "*")
                                    call.respondOutputStream {
                                        val out = this
                                        withContext(Dispatchers.IO) {
                                            file.inputStream().use { it.copyTo(out) }
                                        }
                                    }
                                } else {
                                    // ⚡ song 是 var 且被兜底 lambda 捕获修改过，无法对其属性 smart cast，
                                    //    先快照到局部 val，让 neteaseId/qqMusicMid 的 smart cast 成立
                                    val resolved = song
                                    val streamUrl: String? = if (resolved?.neteaseId != null) {
                                        neteaseStreamProxy.resolveAndCacheStreamUrl(resolved!!.neteaseId)
                                    } else if (!resolved?.qqMusicMid.isNullOrBlank()) {
                                        qqMusicStreamProxy.resolveAndCacheStreamUrl(resolved!!.qqMusicMid)
                                    } else {
                                        null
                                    }

                                    if (streamUrl != null) {
                                        val requestBuilder = okhttp3.Request.Builder().url(streamUrl)
                                        call.request.headers["Range"]?.let { requestBuilder.header("Range", it) }

                                        val response = withContext(Dispatchers.IO) {
                                            streamHttpClient.newCall(requestBuilder.build()).execute()
                                        }
                                        response.use { upstream ->
                                            if (upstream.code != 200 && upstream.code != 206) {
                                                call.respondText("Upstream error", status = io.ktor.http.HttpStatusCode.BadGateway)
                                                return@get
                                            }

                                            val body = upstream.body ?: run {
                                                call.respondText("No content", status = io.ktor.http.HttpStatusCode.BadGateway)
                                                return@get
                                            }

                                            val contentType = upstream.header("Content-Type") ?: "audio/mpeg"
                                            upstream.header("Content-Length")?.let { call.response.header("Content-Length", it) }
                                            upstream.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                                            upstream.header("Accept-Ranges")?.let { call.response.header("Accept-Ranges", it) }

                                            if (upstream.code == 206) {
                                                call.response.status(io.ktor.http.HttpStatusCode.PartialContent)
                                            }
                                            call.response.header("Content-Type", contentType)
                                            call.response.header("Access-Control-Allow-Origin", "*")

                                            call.respondOutputStream {
                                                val out = this
                                                withContext(Dispatchers.IO) {
                                                    body.byteStream().use { it.copyTo(out) }
                                                }
                                            }
                                        }
                                    } else {
                                        call.respondText("Stream not available", status = io.ktor.http.HttpStatusCode.NotFound)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to serve stream")
                                call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        } else {
                            call.respondText("Song not found", status = io.ktor.http.HttpStatusCode.NotFound)
                        }
                    }

                    

                    webSocket("/ws") {
                        val clientIp = call.request.local.remoteHost
                        Timber.i("WebSocket session opened from IP: $clientIp")
                        val client = WsClient(this)
                        activeConnections.add(client)
                        // 有新客户端接入 → 取消"离线恢复手机播放"的延迟任务
                        idleResumeJob?.cancel()
                        idleResumeJob = null
                        Timber.i("WS active connections: ${activeConnections.size}")
                        syncPlayerVolume()
                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        if (text == "__ping__") {
                                            // 客户端 2s 心跳：直接回 pong，不走消息解码（防未知 action 噪音）
                                            client.sendText("""{"action":"__pong__"}""")
                                        } else {
                                            handleWebSocketMessage(text)
                                        }
                                    }
                                    is Frame.Binary, is Frame.Close, is Frame.Ping, is Frame.Pong -> {}
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "WebSocket incoming loop error (this names the killer)")
                        } finally {
                            // closeReason 在异常断开（1006）时可能永不完成，必须带超时，否则 handler 泄漏
                            val closeReasonInfo = withTimeoutOrNull(1000L) { closeReason.await() }
                            Timber.i(
                                "WS client $clientIp left (code=${closeReasonInfo?.code?.toString() ?: "?"}, " +
                                    "reason=${closeReasonInfo?.message ?: "n/a"}" +
                                    "${if (closeReasonInfo == null) ", abnormal: no close frame" else ""})"
                            )
                            activeConnections.remove(client)
                            scheduleResumePhonePlaybackIfIdle()
                            syncPlayerVolume()
                        }
                    }
                }
            }

            server = embeddedServer(
                factory = CIO,
                rootConfig = serverConfig { module(webRemoteModule) },
                configure = {
                    // ⚡ /api/stream 是"整首歌时长的长连接"，一旦占用 Ktor 的 worker 线程，
                    //   /ws 心跳与 1s 轮询就排不上队 → 客户端表现为 WebSocket 反复 code=1006。
                    //   这里放大各线程池，并配合下方 withContext(Dispatchers.IO) 让阻塞 IO 不占 worker。
                    connector {
                        port = resolvedPort
                        host = "0.0.0.0"
                    }
                    connectionGroupSize = 8
                    workerGroupSize = 24
                    callGroupSize = 24
                }
            ).start()

            isServerRunning = true
            Timber.i("Web remote server started successfully at http://$serverAddress")

            startForeground(NOTIFICATION_ID, buildNotification())

            // 熄屏保活：避免屏幕关闭后 Wi-Fi 省电/Doze 掐断长连接（code=1006）
            acquireKeepAliveLocks()

            playbackStateHolder.setWebRemoteActive(true)
            playbackStateHolder.startProgressUpdates()

            lifecycleScope.launch {
                sendPlayerStateUpdates()
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to start web remote server")
            isServerRunning = false
            serverAddress = null
            currentPin = null
        }
    }

    private suspend fun stopServer() {
        playbackStateHolder.setWebRemoteActive(false)
        releaseKeepAliveLocks()
        server?.let { s ->
            try {
                val stopMethod = s::class.java.getMethod("stop", Long::class.java, Long::class.java)
                stopMethod.invoke(s, 1000L, 2000L)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop server")
            }
        }
        server = null
        isServerRunning = false
        serverAddress = null
        currentPin = null
        activeConnections.clear()
        Timber.i("Web remote server stopped")
    }

    private fun resolveServerPort(preferredPort: Int): Int {
        if (preferredPort in 1024..65535) {
            if (isPortAvailable(preferredPort)) {
                return preferredPort
            }
        }
        for (port in 8080..8100) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        return runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(8081)
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun generatePin(): String {
        return Random.nextInt(1000, 9999).toString()
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == "wlan0" || iface.name == "eth0") {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get IP address")
        }
        return "127.0.0.1"
    }

    private suspend fun sendPlayerStateUpdates() {
        val scope = this.lifecycleScope
        scope.launch {
            playbackStateHolder.currentPosition.collect { position ->
                if (!isServerRunning) return@collect
                try {
                    val state = playbackStateHolder.stablePlayerState.first()
                    broadcastPlayerState(state, position)
                } catch (e: Exception) {
                    Timber.e(e, "Error sending player state")
                }
            }
        }
        scope.launch {
            playbackStateHolder.stablePlayerState.collect { state ->
                if (!isServerRunning) return@collect
                try {
                    val position = playbackStateHolder.currentPosition.first()
                    broadcastPlayerState(state, position)
                } catch (e: Exception) {
                    Timber.e(e, "Error sending player state on state change")
                }
            }
        }
    }

    private suspend fun broadcastCurrentState() {
        try {
            val state = playbackStateHolder.stablePlayerState.first()
            val position = playbackStateHolder.currentPosition.first()
            broadcastPlayerState(state, position)
        } catch (e: Exception) {
            Timber.e(e, "Error broadcasting current state")
        }
    }

    private suspend fun broadcastPlayerState(state: com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState, position: Long) {
        val playerState = PlayerState(
            isPlaying = state.isPlaying,
            song = state.currentSong?.toSongDto(),
            position = position,
            duration = state.totalDuration,
            volume = 1.0f,
            syncMode = isSyncMode,
            audioOnDevice = isAudioOnDevice,
            themeColor = themeColor
        )
        val json = Json.encodeToString(playerState)
        // 并发发送：每个 WsClient 内部有 Mutex+3s 超时，坏连接会被剔除。
        // 若串行发送，一个僵死客户端最多拖慢 3s，会导致其它客户端收到过期位置（表现为"卡顿/跳变"）。
        coroutineScope {
            activeConnections.forEach { client ->
                launch { client.sendText(json) }
            }
        }
    }

    private fun handleWebSocketMessage(message: String) {
        try {
            Timber.i("WebSocket message received: $message")
            val request = Json.decodeFromString<WebSocketRequest>(message)
            when (request.action) {
                "play" -> lifecycleScope.launch {
                    if (::dualPlayerEngine.isInitialized) {
                        dualPlayerEngine.masterPlayer.play()
                        syncPlayerVolume()
                    }
                    delay(200)
                    broadcastCurrentState()
                }
                "pause" -> lifecycleScope.launch {
                    if (::dualPlayerEngine.isInitialized) dualPlayerEngine.masterPlayer.pause()
                    delay(200)
                    broadcastCurrentState()
                }
                "playPause" -> lifecycleScope.launch {
                    if (::playbackStateHolder.isInitialized) {
                        playbackStateHolder.playPause()
                        syncPlayerVolume()
                    }
                    delay(200)
                    broadcastCurrentState()
                }
                "skipNext" -> lifecycleScope.launch {
                    if (::playbackStateHolder.isInitialized) playbackStateHolder.nextSong()
                    delay(200)
                    broadcastCurrentState()
                }
                "skipPrevious" -> lifecycleScope.launch {
                    if (::playbackStateHolder.isInitialized) playbackStateHolder.previousSong()
                    delay(200)
                    broadcastCurrentState()
                }
                "seek" -> lifecycleScope.launch {
                    request.data?.get("position")?.toLongOrNull()?.let { pos ->
                        if (::playbackStateHolder.isInitialized) {
                            playbackStateHolder.seekTo(pos)
                        }
                    }
                    delay(200)
                    broadcastCurrentState()
                }
                "volume" -> lifecycleScope.launch {
                    request.data?.get("volume")?.toFloatOrNull()?.let { vol ->
                        if (::dualPlayerEngine.isInitialized) {
                            dualPlayerEngine.masterPlayer.volume = vol.coerceIn(0f, 1f)
                        }
                    }
                    delay(200)
                    broadcastCurrentState()
                }
                "playSong" -> lifecycleScope.launch {
                    request.data?.get("songId")?.let { songId ->
                        musicRepository.getSongsByIds(listOf(songId)).first().firstOrNull()?.let { song ->
                            if (::dualPlayerEngine.isInitialized) {
                                val mediaItem = MediaItemBuilder.build(song)
                                dualPlayerEngine.masterPlayer.setMediaItem(mediaItem)
                                dualPlayerEngine.masterPlayer.prepare()
                                dualPlayerEngine.masterPlayer.play()
                                syncPlayerVolume()
                            }
                        }
                    }
                    delay(200)
                    broadcastCurrentState()
                }
                "setThemeColor" -> {
                    request.data?.get("color")?.let { color ->
                        themeColor = color
                        lifecycleScope.launch {
                            val broadcast = """{"action":"setThemeColor","color":"$color"}"""
                            activeConnections.forEach { conn ->
                                launch { conn.sendText(broadcast) }
                            }
                            delay(200)
                            broadcastCurrentState()
                        }
                    }
                }
                "toggleAudioOnDevice" -> {
                    isAudioOnDevice = !isAudioOnDevice
                    syncPlayerVolume()
                    lifecycleScope.launch {
                        delay(200)
                        broadcastCurrentState()
                    }
                }
                "toggleLike" -> {
                    lifecycleScope.launch {
                        val songId = playbackStateHolder.stablePlayerState.first().currentSong?.id
                        if (songId != null) {
                            musicRepository.toggleFavoriteStatus(songId)
                            delay(200)
                            broadcastCurrentState()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling WebSocket message")
        }
    }

    private fun com.theveloper.pixelplay.data.model.Song.toSongDto(): SongDto {
        return SongDto(
            id = this.id,
            title = this.title,
            artist = this.displayArtist,
            album = this.album,
            coverUrl = this.albumArtUriString?.let { "/api/albumArt/${java.net.URLEncoder.encode(id, "UTF-8")}" },
            streamUrl = "/api/stream/${java.net.URLEncoder.encode(id, "UTF-8")}",
            duration = this.duration,
            isFavorite = this.isFavorite
        )
    }

    @Serializable
    data class ServerStatus(
        val running: Boolean,
        val address: String?,
        val pin: String?,
        val syncMode: Boolean
    )

    @Serializable
    data class AuthRequest(val pin: String)

    @Serializable
    data class AuthResponse(val success: Boolean, val message: String, val syncMode: Boolean = false, val audioOnDevice: Boolean = true, val themeColor: String = "#6750A4")

    @Serializable
    data class PlayerState(
        val isPlaying: Boolean,
        val song: SongDto?,
        val position: Long,
        val duration: Long,
        val volume: Float,
        val syncMode: Boolean = false,
        val audioOnDevice: Boolean = true,
        val themeColor: String = "#6750A4"
    )

    @Serializable
    data class SongDto(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val coverUrl: String?,
        val streamUrl: String?,
        val duration: Long,
        val isFavorite: Boolean
    )

    @Serializable
    data class SearchResponse(val results: List<SongDto>)

    @Serializable
    data class SeekRequest(val position: Long)

    @Serializable
    data class VolumeRequest(val volume: Float)

    @Serializable
    data class PlaySongRequest(val songId: String)

    @Serializable
    data class OperationResponse(val success: Boolean)

    @Serializable
    data class LyricsResponse(val lyrics: String)

    @Serializable
    data class WebSocketRequest(
        val action: String,
        val data: Map<String, String>? = null
    )
}
