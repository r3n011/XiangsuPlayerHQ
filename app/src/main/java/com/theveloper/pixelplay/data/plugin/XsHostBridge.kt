package com.theveloper.pixelplay.data.plugin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackStateHolder
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `__xsHost__.invoke` 的宿主实现（Host API v0.6）。
 *
 * 规则：所有能力调用切主线程；失败返回 `{"error":"<code>"}`，不抛异常。
 * 未实现的方法返回 `not_implemented`，未声明权限返回 `permission_denied`。
 */
@Singleton
class XsHostBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateHolder: PlaybackStateHolder,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val themeOverlay: XsThemeOverlay,
) {
    companion object {
        private const val CHANNEL_ID = "xs_plugin_notifications"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 回执（system.toast / notify / share / error）。 */
    val events = MutableSharedFlow<Pair<String, XSPluginEvent>>(extraBufferCapacity = 128)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun invoke(record: XSPluginRecord, module: String, method: String, argsJson: String): String {
        val args = runCatching { JSONArray(argsJson.ifBlank { "[]" }) }.getOrDefault(JSONArray())
        val started = System.currentTimeMillis()
        return try {
            val result = withContext(Dispatchers.Main) {
                dispatch(record, module, method, args)
            }
            val elapsed = System.currentTimeMillis() - started
            if (result.contains("\"error\"")) {
                XsLog.w(XsLog.BRIDGE, "%s -> %s.%s = %s (%dms)", record.id, module, method, result.take(120), elapsed)
            } else {
                XsLog.d(XsLog.BRIDGE, "%s -> %s.%s ok (%dms)", record.id, module, method, elapsed)
            }
            result
        } catch (e: Exception) {
            XsLog.e(XsLog.BRIDGE, e, "%s -> %s.%s threw (%dms)", record.id, module, method, System.currentTimeMillis() - started)
            err(e.message ?: "host_error")
        }
    }

    // ── 分发 ────────────────────────────────────────────────────────────

    private suspend fun dispatch(
        record: XSPluginRecord,
        module: String,
        method: String,
        args: JSONArray,
    ): String {
        val perms = record.permissions()
        fun need(permission: String): String? =
            if (permission in perms) null else err(XsErrors.PERMISSION_DENIED)

        return when (module) {
            "system" -> when (method) {
                "toast" -> {
                    val message = if (args.opt(0) is JSONObject) {
                        args.optJSONObject(0)?.optString("message").orEmpty()
                    } else {
                        args.optString(0)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    emit(record, "toast", message)
                    ok()
                }
                "haptics" -> {
                    vibrate()
                    ok()
                }
                "share" -> need("share.invoke") ?: shareText(args.optString(0), record)
                "setTitle" -> ok()
                "info" -> systemInfo()
                else -> err(XsErrors.NO_SUCH_METHOD)
            }

            "storage" -> storage(record, method, args, perms)

            "media", "playback" -> media(record, method, args, perms)

            "library" -> library(method, args, perms)

            "lyrics" -> lyrics(method, args, perms)

            "notify", "push" -> notify(record, method, args, perms)

            "share" -> when (method) {
                "shareText" -> need("share.invoke") ?: shareText(args.optString(0), record)
                "shareSong" -> need("share.invoke") ?: shareSong(args.optJSONObject(0), record)
                "sharePoster" -> need("share.invoke") ?: err("not_implemented")
                else -> err(XsErrors.NO_SUCH_METHOD)
            }

            "network" -> network(method, args, perms, record)

            "ui", "theme", "context" -> ui(record, module, method, args, perms)

            "clipboard" -> clipboard(method, args, perms)

            "deepLink", "navigation" -> deepLink(method, args, perms)

            "ai" -> when (method) {
                "status" -> """{"enabled":false,"provider":"none"}"""
                "chat" -> need("ai.invoke") ?: err("not_implemented")
                else -> err(XsErrors.NO_SUCH_METHOD)
            }

            "cast", "device", "musicSource" -> err("not_implemented")

            else -> err(XsErrors.NO_SUCH_MODULE)
        }
    }

    // ── storage ─────────────────────────────────────────────────────────

    private fun storage(record: XSPluginRecord, method: String, args: JSONArray, perms: Set<String>): String {
        val prefs = context.getSharedPreferences("xs_plugin_kv", Context.MODE_PRIVATE)
        val prefix = "xs.plugin.${record.id}."
        fun need(permission: String) = if (permission in perms) null else err(XsErrors.PERMISSION_DENIED)
        return when (method) {
            "get" -> {
                need("storage.read") ?: run {
                    val key = args.optString(0)
                    if (key.isBlank()) return err(XsErrors.INVALID_ARGS)
                    val raw = prefs.getString(prefix + key, null) ?: return "null"
                    raw
                }
            }
            "set" -> {
                need("storage.write") ?: run {
                    val key = args.optString(0)
                    if (key.isBlank()) return err(XsErrors.INVALID_ARGS)
                    val value = args.opt(1)
                    val raw = when (value) {
                        null, JSONObject.NULL -> "null"
                        is String -> JSONObject.quote(value)
                        else -> value.toString()
                    }
                    // 统一存为 JSON 文本，get 原样返回
                    prefs.edit().putString(prefix + key, raw).apply()
                    ok()
                }
            }
            "remove" -> {
                need("storage.write") ?: run {
                    prefs.edit().remove(prefix + args.optString(0)).apply()
                    ok()
                }
            }
            "getKeys" -> {
                need("storage.read") ?: run {
                    val keys = prefs.all.keys
                        .filter { it.startsWith(prefix) }
                        .map { it.removePrefix(prefix) }
                    JSONArray(keys).toString()
                }
            }
            "clear" -> {
                need("storage.write") ?: run {
                    prefs.edit().apply {
                        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
                    }.apply()
                    ok()
                }
            }
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── media ───────────────────────────────────────────────────────────

    private suspend fun media(record: XSPluginRecord, method: String, args: JSONArray, perms: Set<String>): String {
        fun need(permission: String) = if (permission in perms) null else err(XsErrors.PERMISSION_DENIED)
        val state = playbackStateHolder.stablePlayerState.value
        val song = state.currentSong
        return when (method) {
            "getCurrentSong" -> need("media.info") ?: (song?.let { json.encodeToString(XSTrack.serializer(), it.toTrack()) } ?: "null")
            "getPlaybackState" -> need("media.info") ?: run {
                val duration = if (state.totalDuration > 0) state.totalDuration else song?.duration ?: 0L
                val position = playbackStateHolder.currentPosition.value.coerceAtLeast(0L)
                val playback = when {
                    song == null && !state.isPlaying -> "stopped"
                    state.isPlaying -> "playing"
                    else -> "paused"
                }
                json.encodeToString(
                    XSPlaybackState.serializer(),
                    XSPlaybackState(playback, position, duration),
                )
            }
            "getQueue" -> need("media.info") ?: run {
                val items = runCatching {
                    val player = dualPlayerEngine.masterPlayer
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
                }.getOrDefault(emptyList())
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XSTrack.serializer()), items)
            }
            "play" -> need("media.control") ?: run {
                if (!state.isPlaying) playbackStateHolder.playPause()
                ok()
            }
            "pause" -> need("media.control") ?: run {
                if (state.isPlaying) playbackStateHolder.playPause()
                ok()
            }
            "toggle" -> need("media.control") ?: run { playbackStateHolder.playPause(); ok() }
            "next" -> need("media.control") ?: run { playbackStateHolder.nextSong(); ok() }
            "prev" -> need("media.control") ?: run { playbackStateHolder.previousSong(); ok() }
            "seekTo" -> need("media.control") ?: run {
                val position = args.optLong(0, -1L)
                if (position < 0) return err(XsErrors.INVALID_ARGS)
                playbackStateHolder.seekTo(position)
                ok()
            }
            "setVolume" -> need("media.control") ?: run {
                val volume = args.optDouble(0, -1.0)
                if (volume < 0.0) return err(XsErrors.INVALID_ARGS)
                dualPlayerEngine.masterPlayer.volume = volume.coerceIn(0.0, 1.0).toFloat()
                ok()
            }
            "addToQueue" -> need("queue.manage") ?: run {
                val track = args.optJSONObject(0) ?: return err(XsErrors.INVALID_SONG)
                val trackId = track.optString("id")
                if (trackId.isBlank()) return err(XsErrors.INVALID_SONG)
                // 本宿主队列以 MediaItem 为准：按曲库查到 Song 后入队
                val song = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(listOf(trackId)).first().firstOrNull()
                } ?: return err("song_not_found")
                dualPlayerEngine.masterPlayer.addMediaItem(MediaItemBuilder.build(song))
                ok()
            }
            "removeFromQueue" -> need("queue.manage") ?: run {
                val index = args.optInt(0, -1)
                val player = dualPlayerEngine.masterPlayer
                if (index < 0 || index >= player.mediaItemCount) return err(XsErrors.INVALID_INDEX)
                player.removeMediaItem(index)
                ok()
            }
            "onPlaybackStateChange" -> need("media.info") ?: ok()
            "setQueue" -> need("queue.manage") ?: ok()
            "getState" -> err(XsErrors.NO_SUCH_METHOD)
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── library ─────────────────────────────────────────────────────────

    private suspend fun library(method: String, args: JSONArray, perms: Set<String>): String {
        fun need(permission: String) = if (permission in perms) null else err(XsErrors.PERMISSION_DENIED)
        return when (method) {
            "search" -> need("library.read") ?: withContext(Dispatchers.IO) {
                val keyword = args.optString(0)
                val songs = if (keyword.isBlank()) emptyList()
                else musicRepository.searchSongs(keyword).first()
                songsJson(songs)
            }
            "getSongs" -> need("library.read") ?: withContext(Dispatchers.IO) {
                val keyword = args.optString(0)
                val songs = if (keyword.isBlank()) musicRepository.getAllSongsOnce()
                else musicRepository.searchSongs(keyword).first()
                songsJson(songs)
            }
            "getAlbums" -> need("library.read") ?: withContext(Dispatchers.IO) {
                runCatching {
                    musicRepository.getAlbums().first().map { album ->
                        XSAlbum(
                            id = album.id.toString(),
                            title = album.title,
                            artist = album.artist,
                            songCount = album.songCount,
                            year = album.year,
                            artworkUrl = album.albumArtUriString.orEmpty(),
                        )
                    }
                }.getOrDefault(emptyList()).let {
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XSAlbum.serializer()), it)
                }
            }
            "getAlbum" -> need("library.read") ?: withContext(Dispatchers.IO) {
                runCatching {
                    val name = args.optString(0)
                    val album = musicRepository.getAlbums().first().firstOrNull { it.title == name }
                        ?: return@runCatching "null"
                    val songs = musicRepository.getSongsForAlbum(album.id).first()
                    JSONObject()
                        .put("album", album.title)
                        .put("songs", JSONArray(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XSTrack.serializer()), songs.map { it.toTrack() })))
                        .toString()
                }.getOrDefault("null")
            }
            // v1 安卓宿主未接歌单表，先返回空数组，避免插件崩溃
            "getPlaylists" -> need("library.read") ?: "[]"
            "getPlaylist" -> need("library.read") ?: "null"
            "addSong" -> need("library.write") ?: err("not_implemented")
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── lyrics ──────────────────────────────────────────────────────────

    private suspend fun lyrics(method: String, args: JSONArray, perms: Set<String>): String {
        fun need() = if ("lyrics.readwrite" in perms) null else err(XsErrors.PERMISSION_DENIED)
        return when (method) {
            "getLines" -> need() ?: withContext(Dispatchers.IO) {
                val state = playbackStateHolder.stablePlayerState.value
                var lyrics: Lyrics? = state.lyrics
                if (lyrics == null && state.currentSong != null) {
                    lyrics = runCatching { lyricsRepository.getLyrics(state.currentSong) }.getOrNull()
                }
                val arr = JSONArray()
                lyrics?.synced?.forEach { line ->
                    arr.put(JSONObject().put("time", line.time).put("text", line.line))
                }
                if (arr.length() == 0 && !lyrics?.plain.isNullOrEmpty()) {
                    lyrics?.plain?.forEachIndexed { index, text ->
                        arr.put(JSONObject().put("time", index * 1000).put("text", text))
                    }
                }
                arr.toString()
            }
            "seekToLine" -> need() ?: run {
                val index = args.optInt(0, -1)
                val synced = playbackStateHolder.stablePlayerState.value.lyrics?.synced.orEmpty()
                if (index < 0 || index >= synced.size) err(XsErrors.INVALID_INDEX)
                else {
                    playbackStateHolder.seekTo(synced[index].time.toLong())
                    ok()
                }
            }
            "setOverride" -> need() ?: err("not_implemented")
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── notify ──────────────────────────────────────────────────────────

    private fun notify(record: XSPluginRecord, method: String, args: JSONArray, perms: Set<String>): String {
        fun need() = if ("notification.send" in perms) null else err(XsErrors.PERMISSION_DENIED)
        need()?.let { return it }
        val manager = context.getSystemService(NotificationManager::class.java)
        return when (method) {
            "send" -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return err(XsErrors.NOTIFICATION_DENIED)
                }
                val payload = args.optJSONObject(0) ?: JSONObject()
                val title = payload.optString("title", record.manifest.name)
                val body = payload.optString("body").ifBlank { payload.optString("message") }
                val id = payload.optString("id").ifBlank { (record.id.hashCode() and 0x7fffffff).toString() }
                ensureChannel(manager)
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(com.theveloper.pixelplay.R.drawable.rounded_music_note_24)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
                runCatching { manager.notify(idStringToInt(id), notification) }
                emit(record, "notify", "$title $body".trim())
                """{"ok":true,"id":${JSONObject.quote(id)}}"""
            }
            "cancel" -> {
                val id = args.optString(0)
                if (id.isBlank()) err(XsErrors.INVALID_ARGS)
                else {
                    runCatching { manager.cancel(idStringToInt(id)) }
                    ok()
                }
            }
            "setEnabled" -> ok()
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── share ───────────────────────────────────────────────────────────

    private fun shareText(text: String, record: XSPluginRecord): String {
        if (text.isBlank()) return err(XsErrors.INVALID_ARGS)
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            emit(record, "share", "text")
            ok()
        }.getOrElse { err(it.message ?: "share_failed") }
    }

    private fun shareSong(payload: JSONObject?, record: XSPluginRecord): String {
        if (payload == null) return err(XsErrors.INVALID_ARGS)
        val title = payload.optString("title")
        val artist = payload.optString("artist")
        if (title.isBlank()) return err(XsErrors.INVALID_ARGS)
        return shareText("$title${if (artist.isBlank()) "" else " - $artist"} · 像素播放器", record)
    }

    // ── network ─────────────────────────────────────────────────────────

    private suspend fun network(
        method: String,
        args: JSONArray,
        perms: Set<String>,
        record: XSPluginRecord,
    ): String {
        if (method != "request" && method != "fetch") return err(XsErrors.NO_SUCH_METHOD)
        if ("network.request" !in perms) return err(XsErrors.PERMISSION_DENIED)
        val raw = args.optString(0)
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return err(XsErrors.INVALID_URL)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return err(XsErrors.INVALID_SCHEME)
        val host = uri.host?.lowercase() ?: return err(XsErrors.INVALID_URL)
        val domains = record.manifest.networkDomains.orEmpty()
            .map { it.substringBefore(':').lowercase() }
        if (domains.isEmpty()) return err("not_implemented")
        if (host !in domains) return err(XsErrors.PERMISSION_DENIED)

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = http.newCall(Request.Builder().url(raw).get().build()).execute()
                response.use {
                    val body = it.body?.string().orEmpty().take(1_000_000)
                    val headers = JSONObject()
                    it.headers.names().forEach { name -> headers.put(name, it.headers[name].orEmpty()) }
                    JSONObject()
                        .put("status", it.code)
                        .put("headers", headers)
                        .put("body", body)
                        .toString()
                }
            }.getOrElse { err(it.message ?: "network_error") }
        }
    }

    // ── ui / theme / context ────────────────────────────────────────────

    private fun ui(
        record: XSPluginRecord,
        module: String,
        method: String,
        args: JSONArray,
        perms: Set<String>,
    ): String {
        fun need() = if ("theme.apply" in perms) null else err(XsErrors.PERMISSION_DENIED)
        return when (method) {
            "getTheme", "get" -> themeSnapshot()            "applySkin" -> need() ?: run {
                val payload = args.optJSONObject(0) ?: JSONObject()
                val accent = payload.optString("accent")
                if (accent.isNotBlank()) {
                    if (!accent.matches(Regex("^#[0-9a-fA-F]{6}$"))) return err(XsErrors.INVALID_ARGS)
                    themeOverlay.setAccent(accent)
                }
                ok()
            }
            "applyCoverStyle", "applyLyricStyle", "setNowPlaying", "onChange" -> need() ?: ok()
            "reset" -> {
                themeOverlay.reset()
                ok()
            }
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    private fun themeSnapshot(): String {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val accent = themeOverlay.accent.value ?: "#2DB95A"
        return JSONObject()
            .put("platform", "android")
            .put("version", appVersionName())
            .put("isDark", dark)
            .put("accent", accent)
            .toString()
    }

    /** `xs.context.get()` / WebView 容器的 Context 快照（与 getTheme 同构）。 */
    fun contextSnapshot(): String = themeSnapshot()

    private fun systemInfo(): String = JSONObject()
        .put("version", appVersionName())
        .put("build", appVersionCode())
        .put("platform", "android")
        .put("system", "Android ${Build.VERSION.RELEASE}")
        .put("device", Build.MODEL)
        .toString()

    // ── clipboard / deepLink ────────────────────────────────────────────

    private fun clipboard(method: String, args: JSONArray, perms: Set<String>): String {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return when (method) {
            "readText" -> {
                if ("clipboard.read" !in perms) err(XsErrors.PERMISSION_DENIED)
                else {
                    val text = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    JSONObject.quote(text)
                }
            }
            "writeText" -> {
                if ("clipboard.write" !in perms) err(XsErrors.PERMISSION_DENIED)
                else {
                    val text = args.optString(0)
                    if (text.isEmpty()) err(XsErrors.INVALID_ARGS)
                    else {
                        manager.setPrimaryClip(ClipData.newPlainText("xs-plugin", text))
                        ok()
                    }
                }
            }
            "clear" -> {
                if ("clipboard.write" !in perms) err(XsErrors.PERMISSION_DENIED)
                else {
                    if (Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                    else manager.setPrimaryClip(ClipData.newPlainText("", ""))
                    ok()
                }
            }
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    private fun deepLink(method: String, args: JSONArray, perms: Set<String>): String {
        if ("navigation.open" !in perms) return err(XsErrors.PERMISSION_DENIED)
        return when (method) {
            "openUrl" -> {
                val url = args.optString(0)
                val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return err(XsErrors.INVALID_URL)
                if (uri.scheme !in setOf("http", "https", "xiangsuplayer")) return err(XsErrors.INVALID_SCHEME)
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    ok()
                }.getOrElse { err(it.message ?: "open_failed") }
            }
            "canOpen" -> {
                val url = args.optString(0)
                val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return err(XsErrors.INVALID_URL)
                if (uri.scheme !in setOf("http", "https", "xiangsuplayer")) return err(XsErrors.INVALID_SCHEME)
                val can = runCatching {
                    context.packageManager.resolveActivity(
                        Intent(Intent.ACTION_VIEW, uri),
                        PackageManager.MATCH_DEFAULT_ONLY,
                    ) != null
                }.getOrDefault(false)
                """{"ok":$can}"""
            }
            else -> err(XsErrors.NO_SUCH_METHOD)
        }
    }

    // ── 工具 ────────────────────────────────────────────────────────────

    private fun vibrate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(24)
                }
            }
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "插件通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "来自插件的本地通知"
            }
        )
    }

    private fun idStringToInt(id: String): Int = id.hashCode() and 0x7fffffff

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun appVersionCode(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString()
        else @Suppress("DEPRECATION") info.versionCode.toString()
    }.getOrDefault("")

    private fun songsJson(songs: List<Song>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XSTrack.serializer()), songs.map { it.toTrack() })

    private fun Song.toTrack(): XSTrack = XSTrack(
        id = id,
        title = title,
        artist = displayArtist,
        album = album,
        duration = duration,
        artworkUrl = albumArtUriString.orEmpty(),
        source = when {
            neteaseId != null -> "netease"
            navidromeId != null -> "navidrome"
            jellyfinId != null -> "jellyfin"
            gdriveFileId != null -> "nas"
            else -> "local"
        },
    )

    private fun MediaItem.toTrack(): XSTrack {
        val metadata = mediaMetadata
        return XSTrack(
            id = mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = metadata.durationMs ?: 0L,
            artworkUrl = metadata.artworkUri?.toString().orEmpty(),
            source = "local",
        )
    }

    private fun ok(): String = """{"ok":true}"""

    private fun err(code: String): String = JSONObject().put("error", code).toString()

    private fun emit(record: XSPluginRecord, kind: String, message: String) {
        events.tryEmit(
            record.id to XSPluginEvent(System.currentTimeMillis(), kind, message.take(500))
        )
    }
}
