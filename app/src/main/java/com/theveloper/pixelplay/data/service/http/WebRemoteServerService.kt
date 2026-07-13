package com.theveloper.pixelplay.data.service.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val activeConnections = java.util.concurrent.CopyOnWriteArrayList<DefaultWebSocketSession>()

    val activeConnectionsCount: Int
        get() = activeConnections.size

    private var previousVolume: Int = -1

    private fun shouldPlayOnDevice(): Boolean {
        if (activeConnections.isEmpty()) {
            return true
        }
        return isAudioOnDevice
    }

    private fun syncPlayerVolume() {
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
                        runCatching { conn.send(Frame.Text(broadcast)) }
                    }
                }
                return START_STICKY
            }
        }

        val preferredPort = intent?.getIntExtra("port", 8080) ?: 8080
        isSyncMode = intent?.getBooleanExtra("syncMode", false) ?: false
        isAudioOnDevice = intent?.getBooleanExtra("audioOnDevice", true) ?: true
        themeColor = intent?.getStringExtra("themeColor") ?: "#6750A4"

        lifecycleScope.launch {
            startServer(preferredPort)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            stopServer()
        }
    }

    private fun createNotificationChannel() {
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
            server = embeddedServer(CIO, port = resolvedPort) {
                install(DefaultHeaders)
                install(ContentNegotiation) {
                    json()
                }
                install(CORS) {
                    anyHost()
                }
                install(WebSockets)

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
                        }
                        
                        if (song != null) {
                            Timber.i("  Found song: ${song!!.title}, path=${song!!.path}, neteaseId=${song!!.neteaseId}, qqMusicMid=${song!!.qqMusicMid}")
                            try {
                                val songPath = song!!.path
                                
                                if (songPath.startsWith("http://") || songPath.startsWith("https://")) {
                                    Timber.i("  Proxying HTTP stream URL: $songPath")
                                    val httpClient = okhttp3.OkHttpClient.Builder()
                                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()

                                    val requestBuilder = okhttp3.Request.Builder().url(songPath)
                                    call.request.headers["Range"]?.let { requestBuilder.header("Range", it) }

                                    val response = httpClient.newCall(requestBuilder.build()).execute()
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
                                            body.byteStream().use { it.copyTo(this) }
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
                                            val end = if (rangeMatch.groupValues[2].isNotEmpty()) {
                                                rangeMatch.groupValues[2].toLong()
                                            } else {
                                                fileSize - 1
                                            }.coerceAtMost(fileSize - 1)

                                            file.inputStream().use { inputStream ->
                                                inputStream.skip(start)
                                                val contentLength = end - start + 1

                                                call.response.status(io.ktor.http.HttpStatusCode.PartialContent)
                                                call.response.header("Content-Type", mimeType)
                                                call.response.header("Accept-Ranges", "bytes")
                                                call.response.header("Content-Range", "bytes $start-$end/$fileSize")
                                                call.response.header("Content-Length", contentLength.toString())
                                                call.response.header("Access-Control-Allow-Origin", "*")
                                                call.respondOutputStream {
                                                    var remaining = contentLength
                                                    val buffer = ByteArray(8192)
                                                    while (remaining > 0) {
                                                        val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                                                        val read = inputStream.read(buffer, 0, toRead)
                                                        if (read == -1) break
                                                        write(buffer, 0, read)
                                                        remaining -= read
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
                                        file.inputStream().use { it.copyTo(this) }
                                    }
                                } else {
                                    val streamUrl: String? = if (song!!.neteaseId != null) {
                                        neteaseStreamProxy.resolveAndCacheStreamUrl(song!!.neteaseId)
                                    } else if (!song!!.qqMusicMid.isNullOrBlank()) {
                                        qqMusicStreamProxy.resolveAndCacheStreamUrl(song!!.qqMusicMid)
                                    } else {
                                        null
                                    }

                                    if (streamUrl != null) {
                                        val httpClient = okhttp3.OkHttpClient.Builder()
                                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                            .build()

                                        val requestBuilder = okhttp3.Request.Builder().url(streamUrl)
                                        call.request.headers["Range"]?.let { requestBuilder.header("Range", it) }

                                        val response = httpClient.newCall(requestBuilder.build()).execute()
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
                                                body.byteStream().use { it.copyTo(this) }
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
                        Timber.i("WebSocket client connected from IP: $clientIp")
                        activeConnections.add(this)
                        syncPlayerVolume()
                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        handleWebSocketMessage(text)
                                    }
                                    is Frame.Binary, is Frame.Close, is Frame.Ping, is Frame.Pong -> {}
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "WebSocket error")
                        } finally {
                            Timber.i("WebSocket client disconnected from IP: $clientIp")
                            activeConnections.remove(this)
                            if (activeConnections.isEmpty()) {
                                isAudioOnDevice = true
                                Timber.i("All web remote clients disconnected, switching back to phone playback")
                            }
                            syncPlayerVolume()
                        }
                    }
                }
            }.start()

            isServerRunning = true
            Timber.i("Web remote server started successfully at http://$serverAddress")

            startForeground(NOTIFICATION_ID, buildNotification())

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
        activeConnections.forEach { connection ->
            runCatching { connection.send(Frame.Text(json)) }
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
                                runCatching { conn.send(Frame.Text(broadcast)) }
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
