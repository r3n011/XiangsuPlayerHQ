package com.theveloper.pixelplay.data.musicbrainz

import com.theveloper.pixelplay.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val MUSICBRAINZ_BASE = "https://musicbrainz.org/ws/2"
    private val ACOUSTID_BASE = "https://api.acoustid.org/v2"
    private val COVERART_BASE = "https://coverartarchive.org"

    private val USER_AGENT = "PixelPlayer/${BuildConfig.VERSION_NAME} (pixelplayer@example.com)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val rateLimiter = RateLimiter(1.0, TimeUnit.SECONDS)

    suspend fun searchRecording(query: String, limit: Int = 25, offset: Int = 0): MusicBrainzSearchResult {
        rateLimiter.acquire()
        val url = "$MUSICBRAINZ_BASE/recording?query=${query.encode()}&fmt=json&limit=$limit&offset=$offset"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return executeRequest(request) { json.decodeFromString(it) }
    }

    suspend fun lookupRecording(mbid: String, include: String = "artists+releases+release-groups+genres"): MusicBrainzRecording {
        rateLimiter.acquire()
        val url = "$MUSICBRAINZ_BASE/recording/$mbid?fmt=json&inc=$include"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return executeRequest(request) { json.decodeFromString(it) }
    }

    suspend fun lookupRelease(mbid: String): MusicBrainzRelease {
        rateLimiter.acquire()
        val url = "$MUSICBRAINZ_BASE/release/$mbid?fmt=json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return executeRequest(request) { json.decodeFromString(it) }
    }

    suspend fun getCoverArt(releaseMbid: String): CoverArtResult {
        rateLimiter.acquire()
        val url = "$COVERART_BASE/release/$releaseMbid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        return executeRequest(request) { json.decodeFromString(it) }
    }

    suspend fun lookupFingerprint(clientKey: String, fingerprint: String, duration: Int, meta: String = "recordings+releasegroups+releases"): AcoustIdResult {
        val url = "$ACOUSTID_BASE/lookup"
        val body = FormBody.Builder()
            .add("client", clientKey)
            .add("fingerprint", fingerprint)
            .add("duration", duration.toString())
            .add("meta", meta)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .build()

        return executeRequest(request) { json.decodeFromString(it) }
    }

    private suspend inline fun <reified T> executeRequest(request: Request, crossinline parser: (String) -> T): T {
        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty response")
            parser(body)
        } catch (e: Exception) {
            Timber.e(e, "MusicBrainz API request failed: ${request.url}")
            throw e
        }
    }

    private fun String.encode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }
}

class RateLimiter(private val permitsPerSecond: Double, private val timeUnit: TimeUnit) {
    private val lock = java.util.concurrent.locks.ReentrantLock()
    private var lastRequestTime = 0L
    private val intervalNanos = (timeUnit.toNanos(1) / permitsPerSecond).toLong()

    fun acquire() {
        lock.lock()
        try {
            val now = System.nanoTime()
            val elapsed = now - lastRequestTime
            val waitNanos = intervalNanos - elapsed
            if (waitNanos > 0) {
                Thread.sleep(waitNanos / 1_000_000, (waitNanos % 1_000_000).toInt())
            }
            lastRequestTime = System.nanoTime()
        } finally {
            lock.unlock()
        }
    }
}