/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Rhythm (https://github.com/chromahub/rhythm-app) — Apple Music Canvas dynamic artwork.
 *
 * 动态封面提供者：从 Apple Music 公开网页抓取开发者 token → amp-api 检索歌曲/专辑 →
 * 读取 editorialVideo（motion detail 视频，HLS）→ 供播放器交叉淡入播放。
 * 与 Rhythm 一致：不硬编码任何配色/内容，检索结果按 歌名/歌手/专辑 评分匹配；
 * 未开启功能或检索失败时上层直接回退到本地静态封面（本地封面方案始终优先）。
 */

package com.theveloper.pixelplay.data.canvas

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private object AppleCanvasLogger {
    private const val TAG = "AppleMusicCanvas"
    fun d(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (e: RuntimeException) {
            println("[$TAG] D: $msg")
        }
    }

    fun w(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (e: RuntimeException) {
            println("[$TAG] W: $msg")
        }
    }

    fun e(msg: String, t: Throwable) {
        try {
            Log.e(TAG, msg, t)
        } catch (e: RuntimeException) {
            System.err.println("[$TAG] E: $msg")
            t.printStackTrace()
        }
    }
}

object AppleMusicCanvasProvider {

    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    private val client by lazy {
        OkHttpClient.Builder()
            // ⚡ 强制 HTTP/1.1：Apple amp-api 在部分网络下走 HTTP/2 会「takeHeaders 卡住直到超时」
            //（日志里的 Http2Stream\$StreamTimeout就是这个），降级到 1.1 后稳定很多。
            .protocols(listOf(Protocol.HTTP_1_1))
            // ⚡ 短超时：上一版 15/25s，一旦被卡住要白等半分钟（每首歌都试）。
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** ⚡ 网络失败的负缓存时长（区分于「确实没有动态封面」的 24h）。 */
    private const val FAILURE_CACHE_TTL_MS = 3L * 60 * 1000

    /** ⚡ token 拉取失败后的冷却：避免每首歌都重试拉 token（每次都要数秒）。 */
    @Volatile
    private var tokenFailureUntilMs = 0L

    private val gson = Gson()

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 hours

    /**
     * 动态抓取 Apple Music 网页版的公开开发者 JWT（解析页面脚本 + 校验 exp）。
     * 抓取失败时返回 null（不内置回退 token —— 拿不到 token 就让功能静默回退静态封面）。
     */
    private suspend fun getOrFetchToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryMs - 60_000) {
            return@withContext cachedToken!!
        }
        if (now < tokenFailureUntilMs) {
            AppleCanvasLogger.d("Token fetch in cooldown, skip for now")
            return@withContext null
        }

        AppleCanvasLogger.d("Fetching fresh developer token dynamically...")
        try {
            val request = Request.Builder()
                .url("https://music.apple.com/us/browse")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppleCanvasLogger.w("Failed to get Apple Music browse page: ${response.code}")
                    return@use null
                }
                val html = response.body.string()

                val scriptRegex = Pattern.compile("/assets/index(?:-legacy)?[~-][a-zA-Z0-9_-]+\\.js")
                val matcher = scriptRegex.matcher(html)
                val scripts = mutableListOf<String>()
                while (matcher.find()) {
                    val script = matcher.group()
                    if (!scripts.contains(script)) {
                        scripts.add(script)
                    }
                }
                AppleCanvasLogger.d("Found script candidates in HTML: $scripts")

                var fetchedToken: String? = null
                for (scriptPath in scripts) {
                    val scriptUrl = "https://music.apple.com$scriptPath"
                    AppleCanvasLogger.d("Fetching script: $scriptUrl")

                    val scriptReq = Request.Builder()
                        .url(scriptUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    client.newCall(scriptReq).execute().use { scriptRes ->
                        if (!scriptRes.isSuccessful) return@use

                        val scriptText = scriptRes.body.string()
                        val tokenRegex = Pattern.compile("ey[a-zA-Z0-9_-]+\\.ey[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+")
                        val tokenMatcher = tokenRegex.matcher(scriptText)

                        while (tokenMatcher.find()) {
                            val token = tokenMatcher.group()
                            try {
                                val parts = token.split(".")
                                if (parts.size >= 2) {
                                    val body = parts[1]
                                    val decodedBytes = Base64.getUrlDecoder().decode(body)
                                    val decoded = String(decodedBytes, Charsets.UTF_8)

                                    if (decoded.contains("iss") && decoded.contains("exp")) {
                                        val expIndex = decoded.indexOf("\"exp\":")
                                        if (expIndex != -1) {
                                            val endIdx = decoded.indexOf("}", expIndex)
                                            val segment = if (endIdx != -1) decoded.substring(expIndex + 6, endIdx) else decoded.substring(expIndex + 6)
                                            val expValStr = segment.takeWhile { it.isDigit() }
                                            val expSeconds = expValStr.toLongOrNull() ?: 0L

                                            if (expSeconds * 1000 > now) {
                                                fetchedToken = token
                                                tokenExpiryMs = expSeconds * 1000
                                                AppleCanvasLogger.d("Successfully decoded and verified new token. Expires at: ${Date(tokenExpiryMs)}")
                                                break
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore decoding exceptions for invalid matches
                            }
                        }
                    }
                    if (fetchedToken != null) break
                }

                if (fetchedToken != null) {
                    cachedToken = fetchedToken
                    fetchedToken
                } else {
                    AppleCanvasLogger.w("Could not locate any valid fresh token in script files.")
                    null
                }
            }
        } catch (e: Exception) {
            AppleCanvasLogger.e("Error occurred during dynamic token fetch.", e)
            // ⚡ 拉 token 失败：冷却 60s 再试，避免切歌就拉一次 token
            tokenFailureUntilMs = System.currentTimeMillis() + 60_000L
            null
        }
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        val result = try {
            val songResult = searchAndFetchMotion(song, artist, album, storefront, "songs")
            if (songResult != null) {
                songResult
            } else if (!album.isNullOrBlank()) {
                // Fallback: if song search returned null, try searching for the album of the song
                AppleCanvasLogger.d("Song search returned null for '$song' by '$artist'. Falling back to album search for '$album'...")
                searchAndFetchMotion(term = album, artist = artist, album = null, storefront = storefront, type = "albums")
                    ?.copy(name = song)
            } else {
                null
            }
        } catch (e: IOException) {
            // ⚡ 网络失败（超时/断网/HTTP2 卡住）：只短期负缓存，
            //    且不再继续第二段专辑搜索（避免又白等一次超时）
            AppleCanvasLogger.w("Network failure for '$song' (${e.message}); short negative cache")
            cache[key] = CacheEntry(null, System.currentTimeMillis() + FAILURE_CACHE_TTL_MS)
            return@withContext null
        }

        // 命中：缓存 24h；确实没有：同样缓存 24h（避免重复打接口）
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return@withContext result
    }

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val key = cacheKey("album", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        val result = searchAndFetchMotion(term = album, artist = artist, album = null, storefront = storefront, type = "albums")
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return@withContext result
    }

    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        try {
            AppleCanvasLogger.d("searching for $type: $term (album: $album) in $storefront")
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) {
                query = "$query $album"
            }

            val token = getOrFetchToken() ?: return@withContext null
            val urlBuilder = "https://amp-api.music.apple.com/v1/catalog/$storefront/search".toHttpUrl().newBuilder()

            urlBuilder.addQueryParameter("term", query)
            urlBuilder.addQueryParameter("types", type)
            urlBuilder.addQueryParameter("limit", "10")
            urlBuilder.addQueryParameter("extend", "editorialVideo")
            urlBuilder.addQueryParameter("include", "albums")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    AppleCanvasLogger.w("Search failed with status ${response.code}")
                    return@use null
                }

                val bodyStr = response.body.string()
                val root = JsonParser.parseString(bodyStr).asJsonObject
                val resultsObj = root.getAsJsonObject("results") ?: return@use null
                val typeObj = resultsObj.getAsJsonObject(type) ?: return@use null
                val dataArray = typeObj.getAsJsonArray("data") ?: return@use null

                val scoredResults = mutableListOf<Pair<Int, JsonObject>>()

                for (itemElement in dataArray) {
                    val obj = itemElement.asJsonObject
                    val attributes = obj.getAsJsonObject("attributes") ?: continue
                    val resultArtistName = attributes.get("artistName")?.asString ?: ""
                    val resultName = attributes.get("name")?.asString ?: ""
                    val resultCollectionName = attributes.get("albumName")?.asString
                        ?: attributes.get("collectionName")?.asString
                        ?: ""

                    val nameLower = resultName.lowercase(Locale.ROOT)
                    val collectionLower = resultCollectionName.lowercase(Locale.ROOT)

                    val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                            collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                            nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                            collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                            collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                            nameLower.contains("session") || collectionLower.contains("session")

                    if (isBlacklisted) {
                        AppleCanvasLogger.d("Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
                        continue
                    }

                    val artistMatch = artistMatches(artist, resultArtistName)
                    if (!artistMatch) continue

                    var score = 10 // Base matching score since artist matched

                    val nameMatch = resultName.equals(term, ignoreCase = true)
                    val nameFuzzy = resultName.contains(term, ignoreCase = true) || term.contains(resultName, ignoreCase = true)

                    if (nameMatch) {
                        score += 15
                    } else if (nameFuzzy) {
                        score += 7
                    } else {
                        score -= 10
                    }

                    val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
                    for (word in editionWords) {
                        val inTerm = term.contains(word, ignoreCase = true)
                        val inResult = resultName.contains(word, ignoreCase = true)
                        if (inTerm && inResult) score += 5
                        else if (inTerm != inResult && inResult) score -= 3
                    }

                    if (!album.isNullOrBlank() && resultCollectionName.isNotEmpty()) {
                        val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
                        val albumFuzzy = resultCollectionName.contains(album, ignoreCase = true) || album.contains(resultCollectionName, ignoreCase = true)

                        if (albumMatch) score += 20
                        else if (albumFuzzy) score += 10
                    }

                    AppleCanvasLogger.d("Result candidate: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName') -> Score: $score")
                    scoredResults.add(score to obj)
                }

                scoredResults.sortByDescending { it.first }

                for ((score, obj) in scoredResults) {
                    if (score < 12) {
                        AppleCanvasLogger.d("skipping result with low score: $score")
                        continue
                    }
                    val attributes = obj.getAsJsonObject("attributes") ?: continue
                    val resultName = attributes.get("name")?.asString ?: ""
                    val resultArtistName = attributes.get("artistName")?.asString ?: ""

                    var targetAlbumId: String? = null
                    val objType = obj.get("type")?.asString

                    if (objType == "songs") {
                        val relationships = obj.getAsJsonObject("relationships")
                        if (relationships != null) {
                            val albumsRel = relationships.getAsJsonObject("albums")
                            if (albumsRel != null) {
                                val albumsData = albumsRel.getAsJsonArray("data")
                                if (albumsData != null && albumsData.size() > 0) {
                                    targetAlbumId = albumsData.get(0).asJsonObject.get("id")?.asString
                                }
                            }
                        }
                        if (targetAlbumId == null) {
                            targetAlbumId = attributes.get("collectionId")?.asString
                        }
                        if (targetAlbumId == null) {
                            val url = attributes.get("url")?.asString
                            if (url != null) {
                                val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                                val id = albumPart.substringAfterLast("/", "")
                                if (id.isNotEmpty() && id.all { it.isDigit() }) {
                                    targetAlbumId = id
                                }
                            }
                        }
                    } else if (objType == "albums") {
                        targetAlbumId = obj.get("id")?.asString
                    }

                    if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) {
                        AppleCanvasLogger.d("skipping null or playlist albumId ($targetAlbumId) for $resultName")
                        continue
                    }

                    AppleCanvasLogger.d("trying resolve for $targetAlbumId (from $objType)")

                    // Check editorialVideo directly in the search result
                    val ev = attributes.getAsJsonObject("editorialVideo")
                    if (ev != null) {
                        val hlsUrl = extractEditorialVideoUrl(ev)
                        if (!hlsUrl.isNullOrBlank()) {
                            val collName = attributes.get("collectionName")?.asString
                            val resolvedAlbumName = if (objType == "songs") collName else resultName
                            AppleCanvasLogger.d("Found direct editorialVideo for $resultName (ID: $targetAlbumId)")
                            return@use CanvasArtwork(
                                name = resultName,
                                artist = resultArtistName,
                                albumId = targetAlbumId,
                                albumName = resolvedAlbumName,
                                animated = hlsUrl
                            )
                        }
                    }

                    // Fallback to full lookup
                    val fetched = fetchMotionArtwork(
                        albumId = targetAlbumId,
                        storefront = storefront,
                        fallbackArtist = resultArtistName,
                        titleOverride = if (objType == "songs") resultName else null,
                        artistOverride = if (objType == "songs") resultArtistName else null
                    )
                    if (fetched != null) return@use fetched
                }
                null
            }
        } catch (e: IOException) {
            // ⚡ 网络类失败上抛：让上层区分「网络失败」与「确实无动态封面」，
            //    避免一次超时就把这首歌封死 24 小时
            throw e
        } catch (e: Exception) {
            AppleCanvasLogger.e("Error searching and fetching motion", e)
            null
        }
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (albumId.startsWith("pl.")) return@withContext null
        try {
            AppleCanvasLogger.d("fetching album $albumId")
            val token = getOrFetchToken() ?: return@withContext null
            val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/albums/$albumId?extend=editorialVideo&include=tracks"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    AppleCanvasLogger.w("album fetch failed for $albumId: ${response.code}")
                    return@use null
                }

                val bodyStr = response.body.string()
                val root = JsonParser.parseString(bodyStr).asJsonObject
                val dataArray = root.getAsJsonArray("data") ?: return@use null
                if (dataArray.size() == 0) return@use null

                val albumObj = dataArray.get(0).asJsonObject
                val attributes = albumObj.getAsJsonObject("attributes") ?: return@use null
                val albumName = attributes.get("name")?.asString ?: ""
                val artistName = attributes.get("artistName")?.asString ?: fallbackArtist

                val nameLower = albumName.lowercase(Locale.ROOT)
                val isBlacklisted = nameLower.contains("playlist") || nameLower.contains("set list") ||
                        nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                        nameLower.contains("mixed") || nameLower.contains("apple music") ||
                        nameLower.contains("today's hits") || nameLower.contains("session")

                if (isBlacklisted) {
                    AppleCanvasLogger.d("fetchMotionArtwork: ignoring blacklisted album '$albumName' ($albumId)")
                    return@use null
                }

                val finalTitle = titleOverride ?: albumName
                val finalArtist = artistOverride ?: artistName

                val ev = attributes.getAsJsonObject("editorialVideo")
                if (ev != null) {
                    val videoUrl = extractEditorialVideoUrl(ev)
                    if (!videoUrl.isNullOrBlank()) {
                        AppleCanvasLogger.d("found editorialVideo for $finalTitle (album: $albumName, id: $albumId)")
                        return@use CanvasArtwork(
                            name = finalTitle,
                            artist = finalArtist,
                            albumId = albumId,
                            albumName = albumName,
                            animated = videoUrl
                        )
                    }
                }
                AppleCanvasLogger.d("no editorialVideo for $albumId")
                null
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            AppleCanvasLogger.e("error in fetchMotionArtwork for $albumId", e)
            null
        }
    }

    private fun extractEditorialVideoUrl(ev: JsonObject): String? {
        val candidates = listOf("motionDetailRaw", "motionDetailSquare", "motionDetailTall", "motionDetailStatic")
        for (key in candidates) {
            val asset = ev.getAsJsonObject(key) ?: continue
            val video = asset.get("video")?.asString
                ?: asset.get("videoUrl")?.asString
                ?: asset.get("hlsUrl")?.asString
                ?: asset.get("url")?.asString

            if (!video.isNullOrBlank()) return video
        }
        return null
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }

    private fun artistMatches(requested: String, returned: String): Boolean {
        val delimiters = "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)"
        val requestedList = requested.split(Regex(delimiters, RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val returnedList = returned.split(Regex(delimiters, RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        if (requestedList.isEmpty() || returnedList.isEmpty()) return false
        return requestedList.all { req -> returnedList.any { res -> res == req } }
    }
}
