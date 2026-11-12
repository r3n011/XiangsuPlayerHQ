package com.theveloper.pixelplay.data.service.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.mcp.MCP_PROTOCOL_VERSION
import com.theveloper.pixelplay.data.mcp.McpJson
import com.theveloper.pixelplay.data.mcp.McpPairingManager
import com.theveloper.pixelplay.data.mcp.McpServer
import com.theveloper.pixelplay.data.mcp.McpServerState
import com.theveloper.pixelplay.data.mcp.McpSessionManager
import com.theveloper.pixelplay.data.mcp.mcpRoutes
import com.theveloper.pixelplay.data.preferences.McpPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.ServerSocket
import javax.inject.Inject

/**
 * Standalone MCP bridge.
 *
 * Deliberately independent from WebRemoteServerService: own port (8787+), own notification
 * channel, own network locks, own auth (Bearer tokens). Neither service starts or stops the
 * other.
 */
@AndroidEntryPoint
class McpServerService : LifecycleService() {

    @Inject
    lateinit var mcpServer: McpServer

    @Inject
    lateinit var pairing: McpPairingManager

    @Inject
    lateinit var sessions: McpSessionManager

    @Inject
    lateinit var prefs: McpPreferencesRepository

    private var engine: EmbeddedServer<*, *>? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在启动…"))
        // Deep-link invites arrive while the app may not have the settings screen open.
        lifecycleScope.launch {
            com.theveloper.pixelplay.data.mcp.McpPairingIntentBus.invites.collect { payload ->
                pairing.receiveInvite(payload)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch { stopServer() }
                return START_NOT_STICKY
            }
        }
        val preferred = intent?.getIntExtra(EXTRA_PORT, McpPreferencesRepository.DEFAULT_PORT)
            ?: McpPreferencesRepository.DEFAULT_PORT
        lifecycleScope.launch { startServer(preferred) }
        return START_STICKY
    }

    private suspend fun startServer(preferredPort: Int) {
        if (McpServerState.running.value) return
        val port = resolvePort(preferredPort)
        if (port == null) {
            McpServerState.markFailed("没有可用端口")
            stopSelfSafely()
            return
        }
        val host = localIpAddress()
        McpServerState.markStarting(port, host)
        startForeground(NOTIFICATION_ID, buildNotification("http://$host:$port/mcp"))
        acquireLocks()

        try {
            val module: suspend Application.() -> Unit = {
                install(DefaultHeaders)
                install(ContentNegotiation) { json(McpJson) }
                install(CORS) {
                    anyHost()
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Delete)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader("Mcp-Session-Id")
                    allowHeader("MCP-Protocol-Version")
                }
                routing {
                    mcpRoutes(mcpServer, pairing, sessions)
                }
            }

            engine = embeddedServer(
                factory = CIO,
                rootConfig = serverConfig { module(module) },
                configure = {
                    connector {
                        this.port = port
                        this.host = "0.0.0.0"
                    }
                    connectionGroupSize = 8
                    workerGroupSize = 16
                    callGroupSize = 16
                },
            ).start(wait = false)

            Timber.i("MCP server started at http://$host:$port/mcp ($MCP_PROTOCOL_VERSION)")
        } catch (t: Throwable) {
            Timber.e(t, "MCP server failed to start")
            McpServerState.markFailed(t.message ?: "启动失败")
            stopSelfSafely()
        }
    }

    private suspend fun stopServer() {
        runCatching { engine?.stop(1_000, 3_000) }
        engine = null
        sessions.closeAll()
        releaseLocks()
        McpServerState.markStopped()
        withContext(Dispatchers.Main.immediate) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun stopSelfSafely() {
        lifecycleScope.launch {
            releaseLocks()
            runCatching { engine?.stop(0, 0) }
            engine = null
            McpServerState.markStopped()
            withContext(Dispatchers.Main.immediate) {
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        releaseLocks()
        runCatching { engine?.stop(0, 0) }
        engine = null
        McpServerState.markStopped()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI 外接（MCP）",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "像素播放器的 MCP 服务"
            },
        )
    }

    private fun buildNotification(address: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI 外接")
            .setContentText(address)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun resolvePort(preferred: Int): Int? {
        if (preferred in 1024..65535 && isPortAvailable(preferred)) return preferred
        for (port in McpPreferencesRepository.PORT_RANGE_START..McpPreferencesRepository.PORT_RANGE_END) {
            if (isPortAvailable(port)) return port
        }
        return runCatching { ServerSocket(0).use { it.localPort } }.getOrNull()
    }

    private fun isPortAvailable(port: Int): Boolean =
        runCatching { ServerSocket(port).use { true } }.getOrDefault(false)

    private fun localIpAddress(): String {
        var ipv6: String? = null
        return runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address.isLoopbackAddress || !address.isSiteLocalAddress) continue
                    // Prefer IPv4: most AI clients and desktop browsers handle it more reliably.
                    if (address is java.net.Inet4Address) {
                        return@runCatching address.hostAddress ?: "127.0.0.1"
                    }
                    if (ipv6 == null) ipv6 = address.hostAddress
                }
            }
            ipv6?.let { host ->
                val clean = host.substringBefore('%')
                if (clean.contains(':')) "[$clean]" else clean
            } ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    private fun acquireLocks() {
        runCatching {
            if (wifiLock == null) {
                val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pixelplay:mcp")
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
            }
        }.onFailure { Timber.w(it, "MCP: WifiLock failed") }

        runCatching {
            if (wakeLock == null) {
                val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelplay:mcp")
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.setReferenceCounted(false)
                wakeLock?.acquire()
            }
        }.onFailure { Timber.w(it, "MCP: WakeLock failed") }
    }

    private fun releaseLocks() {
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wifiLock = null
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.r3n011.pixelplay.mcp.START"
        const val ACTION_STOP = "com.r3n011.pixelplay.mcp.STOP"
        const val EXTRA_PORT = "port"

        private const val NOTIFICATION_ID = 0x4D43
        private const val CHANNEL_ID = "mcp_server"

        fun start(context: Context, port: Int) {
            val intent = Intent(context, McpServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, McpServerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
