package com.theveloper.pixelplay.data.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线播放直链的持久化缓存（对齐 lx-music-mobile 的 saveMusicUrl / getMusicUrl）。
 *
 * - key = `songKey|source|quality`，其中 songKey 优先用媒体库稳定 id；
 * - 只缓存 http/https 直链；
 * - 默认 15 分钟过期（签名直链一般 10~30 分钟失效）；
 * - 进程重启后仍然有效，但 `forceRefresh` / 手动换源会绕过或清除。
 */
@Singleton
class PlaybackUrlCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private companion object {
        const val TAG = "PlaybackUrlCache"
        const val PREFS = "playback_url_cache_v1"
        const val KEY_ENTRIES = "entries"
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        const val MAX_ENTRIES = 300
    }

    private data class Entry(
        val url: String,
        val source: String,
        val quality: String,
        val savedAtMs: Long,
        val ttlMs: Long,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
            now - savedAtMs > ttlMs
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private var entries: MutableMap<String, Entry> = load()

    fun get(songKey: String, source: String?, quality: String): String? = synchronized(lock) {
        val key = cacheKey(songKey, source, quality)
        val entry = entries[key] ?: return@synchronized null
        if (entry.isExpired()) {
            entries.remove(key)
            persist()
            return@synchronized null
        }
        val host = runCatching { java.net.URI(entry.url).host }.getOrNull()?.lowercase()
        if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
            entries.remove(key)
            persist()
            return@synchronized null
        }
        entry.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    fun put(
        songKey: String,
        source: String?,
        quality: String,
        url: String,
        ttlMs: Long = DEFAULT_TTL_MS,
    ) = synchronized(lock) {
        val trimmed = url.trim()
        if (trimmed.isBlank() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            return@synchronized
        }
        // 本地代理端口每次进程重启都会变，持久化缓存里绝不能存 127.0.0.1/localhost。
        val host = runCatching { java.net.URI(trimmed).host }.getOrNull()?.lowercase()
        if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
            return@synchronized
        }
        entries[cacheKey(songKey, source, quality)] = Entry(
            url = trimmed,
            source = source.orEmpty(),
            quality = quality,
            savedAtMs = System.currentTimeMillis(),
            ttlMs = ttlMs.coerceAtLeast(60_000L),
        )
        pruneIfNeeded()
        persist()
    }

    fun invalidate(songKey: String) = synchronized(lock) {
        val prefix = "$songKey|"
        entries.keys.removeAll { it.startsWith(prefix) }
        persist()
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun cacheKey(songKey: String, source: String?, quality: String): String =
        "$songKey|${source.orEmpty()}|$quality"

    private fun pruneIfNeeded() {
        if (entries.size <= MAX_ENTRIES) return
        val sorted = entries.entries.sortedBy { it.value.savedAtMs }
        sorted.take((entries.size - MAX_ENTRIES).coerceAtLeast(0)).forEach { entries.remove(it.key) }
    }

    private fun load(): MutableMap<String, Entry> = runCatching {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return@runCatching mutableMapOf()
        val root = JSONObject(raw)
        val now = System.currentTimeMillis()
        val map = mutableMapOf<String, Entry>()
        root.keys().forEach { key ->
            val obj = root.optJSONObject(key) ?: return@forEach
            val entry = Entry(
                url = obj.optString("url"),
                source = obj.optString("source"),
                quality = obj.optString("quality"),
                savedAtMs = obj.optLong("savedAtMs"),
                ttlMs = obj.optLong("ttlMs", DEFAULT_TTL_MS),
            )
            if (entry.url.isNotBlank() && !entry.isExpired(now)) map[key] = entry
        }
        map
    }.getOrElse {
        Timber.w(it, "$TAG: load cache failed")
        mutableMapOf()
    }

    private fun persist() {
        runCatching {
            val root = JSONObject()
            entries.forEach { (key, entry) ->
                root.put(key, JSONObject().apply {
                    put("url", entry.url)
                    put("source", entry.source)
                    put("quality", entry.quality)
                    put("savedAtMs", entry.savedAtMs)
                    put("ttlMs", entry.ttlMs)
                })
            }
            prefs.edit().putString(KEY_ENTRIES, root.toString()).apply()
        }.onFailure { Timber.w(it, "$TAG: persist cache failed") }
    }
}
