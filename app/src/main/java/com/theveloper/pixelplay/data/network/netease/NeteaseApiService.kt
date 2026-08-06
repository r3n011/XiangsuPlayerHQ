package com.theveloper.pixelplay.data.network.netease

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct Netease Cloud Music API client.
 *
 * Modeled after NeriPlayer's NeteaseClient — uses OkHttp with cookie management,
 * supports all 4 crypto modes, and calls music.163.com/interface.music.163.com directly.
 *
 * EAPI 请求对齐原版 NeteaseCloudMusicApi（util/request.js）：
 *  - 请求体加密数据内嵌入 `header` 对象（osver/deviceId/os/appver/versioncode/.../requestId/MUSIC_U/MUSIC_A）
 *  - Cookie 头使用 createHeaderCookie(header)（encodeURIComponent 编码）
 *  - User-Agent 使用 iPhone 客户端 UA（NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)）
 *  - 未登录时自动获取匿名 MUSIC_A token（weapi /register/anonimous）
 */
@Singleton
class NeteaseApiService @Inject constructor() {

    companion object {
        private const val TAG = "NeteaseApi"

        /** EAPI 客户端 UA（对齐原版 util/request.js chooseUserAgent('api', 'iphone')） */
        private const val EAPI_USER_AGENT = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"

        /** WEAPI 客户端 UA（桌面 Chrome，对齐原版 chooseUserAgent('weapi')） */
        private const val WEAPI_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"

        private const val ID_XOR_KEY = "3go8&$8*3*3h0k(2)2"
    }

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    @Volatile
    private var persistedCookies: Map<String, String> = emptyMap()

    /** 匿名 MUSIC_A token（未登录时 eapi 播放/歌词需要），获取一次后复用 */
    @Volatile
    private var anonymousToken: String? = null

    private val anonymousTokenLock = AtomicBoolean(false)

    /** 稳定的随机 deviceId（对齐原版 generateDeviceId，52 位十六进制） */
    @Volatile
    private var deviceId: String? = null

    private val secureRandom = SecureRandom()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                list.removeAll { c -> cookies.any { it.name == c.name } }
                list.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ─── Cookie Management ─────────────────────────────────────────────

    /** Check if user is logged in (has MUSIC_U cookie) */
    fun hasLogin(): Boolean = !persistedCookies["MUSIC_U"].isNullOrBlank()

    /** Get all persisted cookies as a "key=value; key2=value2" formatted string */
    fun getCookieString(): String {
        val map = persistedCookies.toMap()
        if (map.isEmpty()) return ""
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    /** Set persisted cookies from saved state and inject into CookieJar */
    fun setPersistedCookies(cookies: Map<String, String>) {
        val m = cookies.toMutableMap()
        m.putIfAbsent("os", "pc")
        m.putIfAbsent("appver", "8.10.35")
        persistedCookies = m.toMap()

        seedCookieJarFromPersisted("music.163.com")
        seedCookieJarFromPersisted("interface.music.163.com")
        syncNcmSessionCookies()
    }

    /** Get all cookies currently in memory */
    fun getCookies(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        cookieStore.values.forEach { list ->
            list.forEach { cookie -> result[cookie.name] = cookie.value }
        }
        return result
    }

    fun logout() {
        cookieStore.clear()
        persistedCookies = emptyMap()
        try {
            net.moriafly.ncm.NcmSession.INSTANCE?.logout()
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: NcmSession logout failed")
        }
    }

    /** 将当前登录态同步到本地 NcmApi SDK（App 进程内直连官方接口，无需外部代理） */
    private fun syncNcmSessionCookies() {
        try {
            val session = net.moriafly.ncm.NcmSession.INSTANCE
            if (session == null) return
            session.merge(persistedCookies)
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: syncNcmSessionCookies failed")
        }
    }

    private fun seedCookieJarFromPersisted(host: String) {
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        persistedCookies.forEach { (name, value) ->
            val c = Cookie.Builder()
                .name(name).value(value)
                .domain(host).path("/")
                .build()
            list.removeAll { it.name == name }
            list.add(c)
        }
    }

    private fun getCookie(name: String): String? = cookieStore.values
        .asSequence()
        .flatMap { it.asSequence() }
        .firstOrNull { it.name == name }
        ?.value

    private fun buildPersistedCookieHeader(): String? {
        val map = persistedCookies.toMutableMap()
        map.putIfAbsent("os", "pc")
        map.putIfAbsent("appver", "8.10.35")
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    // ─── Core Request Method ───────────────────────────────────────────

    /** Visit music.163.com homepage to obtain __csrf cookie */
    @Throws(IOException::class)
    fun ensureWeapiSession() {
        request(
            url = "https://music.163.com/",
            params = emptyMap(), mode = CryptoMode.API,
            method = "GET", usePersistedCookies = true
        )
    }

    @Throws(IOException::class)
    fun request(
        url: String,
        params: Map<String, Any>,
        mode: CryptoMode = CryptoMode.WEAPI,
        method: String = "POST",
        usePersistedCookies: Boolean = true
    ): String {
        val requestUrl = url.toHttpUrl()
        Timber.d("$TAG: >>> $method $url [mode=$mode, persistedCookies=${usePersistedCookies}]")
        Timber.d("$TAG: >>> params keys=${params.keys}")
        Timber.d("$TAG: >>> hasLogin=${hasLogin()}, MUSIC_U=${persistedCookies["MUSIC_U"]?.take(20)}...")

        // eapi 播放/歌词等接口在未登录时需要匿名 MUSIC_A token（对齐原版 processCookieObject）
        if (mode == CryptoMode.EAPI && !hasLogin() && persistedCookies["MUSIC_A"].isNullOrBlank()) {
            ensureAnonymousToken()
        }

        val builder = Request.Builder()
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            .header("Connection", "keep-alive")
            .header("Host", requestUrl.host)

        val bodyParams: Map<String, String> = when (mode) {
            CryptoMode.WEAPI -> {
                builder.header("Referer", "https://music.163.com")
                builder.header("User-Agent", WEAPI_USER_AGENT)
                // WEAPI 需要 csrf_token（对齐原版 data.csrf_token 放入请求体）
                val p = params.toMutableMap()
                val csrf = persistedCookies["__csrf"] ?: getCookie("__csrf") ?: ""
                p["csrf_token"] = csrf
                if (usePersistedCookies) {
                    buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
                }
                NeteaseEncryption.weApiEncrypt(p)
            }
            CryptoMode.EAPI -> {
                // eapi 请求头：对齐原版 util/request.js —— header 嵌入加密数据 + Cookie 头 + iPhone UA（不设 Referer）
                builder.header("User-Agent", EAPI_USER_AGENT)
                val header = buildEapiHeader()
                if (usePersistedCookies) {
                    builder.header("Cookie", createHeaderCookie(header))
                }
                val p = params.toMutableMap()
                p["header"] = header
                p["e_r"] = false // ENCRYPT_RESPONSE 默认关闭，响应为明文 JSON
                NeteaseEncryption.eApiEncrypt(requestUrl.encodedPath, p)
            }
            CryptoMode.LINUX -> {
                builder.header("Referer", "https://music.163.com")
                builder.header("User-Agent", WEAPI_USER_AGENT)
                if (usePersistedCookies) {
                    buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
                }
                NeteaseEncryption.linuxApiEncrypt(params)
            }
            CryptoMode.API -> {
                builder.header("Referer", "https://music.163.com")
                builder.header("User-Agent", WEAPI_USER_AGENT)
                if (usePersistedCookies) {
                    buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
                }
                params.mapValues { it.value.toString() }
            }
        }

        var reqUrl = requestUrl
        builder.url(reqUrl)

        when (method.uppercase(Locale.getDefault())) {
            "POST" -> {
                val formBodyBuilder = FormBody.Builder(StandardCharsets.UTF_8)
                bodyParams.forEach { (k, v) -> formBodyBuilder.add(k, v) }
                builder.post(formBodyBuilder.build())
            }
            "GET" -> {
                val urlBuilder = reqUrl.newBuilder()
                bodyParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                builder.url(urlBuilder.build())
            }
            else -> throw IllegalArgumentException("Unsupported method: $method")
        }

        try {
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val code = resp.code
                Timber.d("$TAG: <<< HTTP $code for $url")
                val bytes = resp.body.bytes()
                val body = String(bytes, StandardCharsets.UTF_8)
                Timber.d("$TAG: <<< body[${body.length}]: ${body.take(500)}")
                return body
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: !!! FAILED $method $url")
            throw e
        }
    }

    // ─── EAPI header（对齐原版 NeteaseCloudMusicApi） ──────────────────

    /**
     * 构建 eapi 请求头对象（对齐原版 util/request.js 的 header 构建逻辑）。
     * 该对象会同时：1) 嵌入到加密请求体的 `header` 字段；2) 编码为 Cookie 头发送。
     */
    private fun buildEapiHeader(): Map<String, String> {
        val os = persistedCookies["os"] ?: "pc"
        val now = System.currentTimeMillis()
        val header = LinkedHashMap<String, String>()
        header["osver"] = persistedCookies["osver"]
            ?: if (os == "android") "14" else "Microsoft-Windows-10-Professional-build-19045-64bit"
        header["deviceId"] = persistedCookies["deviceId"] ?: generateOrGetDeviceId()
        header["os"] = os
        header["appver"] = persistedCookies["appver"] ?: "8.10.35"
        header["versioncode"] = persistedCookies["versioncode"] ?: "140"
        header["mobilename"] = persistedCookies["mobilename"] ?: ""
        header["buildver"] = persistedCookies["buildver"] ?: now.toString().substring(0, 10)
        header["resolution"] = persistedCookies["resolution"] ?: "1920x1080"
        header["__csrf"] = persistedCookies["__csrf"] ?: getCookie("__csrf") ?: ""
        header["channel"] = persistedCookies["channel"] ?: "netease"
        header["requestId"] = "${now}_${(secureRandom.nextInt(1000)).toString().padStart(4, '0')}"
        persistedCookies["MUSIC_U"]?.let { header["MUSIC_U"] = it }
        persistedCookies["MUSIC_A"]?.let { header["MUSIC_A"] = it }
        return header
    }

    /** 生成并缓存稳定的 52 位十六进制 deviceId（对齐原版 generateDeviceId） */
    private fun generateOrGetDeviceId(): String {
        deviceId?.let { return it }
        val hexChars = "0123456789ABCDEF"
        val sb = StringBuilder(52)
        repeat(52) { sb.append(hexChars[secureRandom.nextInt(hexChars.length)]) }
        val id = sb.toString()
        deviceId = id
        persistedCookies = persistedCookies + ("deviceId" to id)
        return id
    }

    /** 将 header 对象编码为 Cookie 头（对齐原版 createHeaderCookie，encodeURIComponent 编码） */
    private fun createHeaderCookie(header: Map<String, String>): String =
        header.entries.joinToString("; ") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    /**
     * 未登录时获取匿名 MUSIC_A token（对齐原版 module/register_anonimous.js）。
     * username 为 base64("$deviceId $md5Base64(xor(deviceId))")，weapi POST /register/anonimous。
     */
    private fun ensureAnonymousToken() {
        if (hasLogin()) return
        if (!persistedCookies["MUSIC_A"].isNullOrBlank() || anonymousToken != null) return
        if (!anonymousTokenLock.compareAndSet(false, true)) return
        try {
            val id = generateOrGetDeviceId()
            val username = Base64.encodeToString(
                "$id ${cloudmusicEncodeId(id)}".toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            val resp = request(
                url = "https://music.163.com/weapi/register/anonimous",
                params = mapOf("username" to username),
                mode = CryptoMode.WEAPI,
                method = "POST",
                usePersistedCookies = false
            )
            val root = runCatching { JSONObject(resp) }.getOrNull()
            val code = root?.optInt("code", -1) ?: -1
            if (code == 200) {
                val fromCookies = getCookies()["MUSIC_A"]
                val fromBody = root?.optString("MUSIC_A")?.takeIf { it.isNotBlank() }
                    ?: root?.optString("cookie")?.takeIf { it.isNotBlank() }
                    ?: root?.optString("anonymous_token")?.takeIf { it.isNotBlank() }
                val musicA = fromCookies ?: fromBody
                if (!musicA.isNullOrBlank()) {
                    anonymousToken = musicA
                    persistedCookies = persistedCookies + ("MUSIC_A" to musicA)
                    seedCookieJarFromPersisted("interface.music.163.com")
                    seedCookieJarFromPersisted("music.163.com")
                    Timber.d("$TAG: anonymous token acquired (${musicA.take(20)}...)")
                }
            } else {
                Timber.w("$TAG: register anonimous failed code=$code")
            }
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: ensureAnonymousToken failed")
        } finally {
            anonymousTokenLock.set(false)
        }
    }

    /** 对齐原版 register_anonimous.js 的 cloudmusic_dll_encode_id（异或 + MD5 + Base64） */
    private fun cloudmusicEncodeId(id: String): String {
        val sb = StringBuilder(id.length)
        for (i in id.indices) {
            sb.append((id[i].code xor ID_XOR_KEY[i % ID_XOR_KEY.length].code).toChar())
        }
        val digest = MessageDigest.getInstance("MD5")
            .digest(sb.toString().toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    // ─── Convenience Methods ───────────────────────────────────────────

    fun callWeApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://music.163.com/weapi$p", params, CryptoMode.WEAPI, "POST", usePersistedCookies)
    }

    fun callEApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return request("https://interface.music.163.com/eapi$p", params, CryptoMode.EAPI, "POST", usePersistedCookies)
    }

    // ─── Authentication ────────────────────────────────────────────────

    fun sendCaptcha(phone: String, ctcode: Int = 86): String {
        val params = mapOf("cellphone" to phone, "ctcode" to ctcode.toString())
        return request("https://interface.music.163.com/weapi/sms/captcha/sent", params, CryptoMode.WEAPI, "POST", usePersistedCookies = false)
    }

    fun loginByCaptcha(phone: String, captcha: String, ctcode: Int = 86): String {
        val params = mutableMapOf<String, Any>(
            "phone" to phone,
            "countrycode" to ctcode.toString(),
            "remember" to "true",
            "type" to "1",
            "captcha" to captcha
        )
        return callEApi("/w/login/cellphone", params, usePersistedCookies = false)
    }

    // ─── User Info ─────────────────────────────────────────────────────

    fun getCurrentUserAccount(): String {
        return callWeApi("/w/nuser/account/get", emptyMap(), usePersistedCookies = true)
    }

    fun getCurrentUserId(): Long {
        val raw = getCurrentUserAccount()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) {
            throw IllegalStateException("Failed to get user info: $raw")
        }
        return root.optJSONObject("profile")?.optLong("userId")
            ?: throw IllegalStateException("userId not found")
    }

    // ─── Content ───────────────────────────────────────────────────────

    fun getUserPlaylists(userId: Long, offset: Int = 0, limit: Int = 50): String {
        val params = mutableMapOf<String, Any>(
            "uid" to userId.toString(),
            "offset" to offset.toString(),
            "limit" to limit.toString(),
            "includeVideo" to "true"
        )
        return request("https://music.163.com/weapi/user/playlist", params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    fun getPlaylistDetail(playlistId: Long): String {
        val params = mutableMapOf<String, Any>(
            "id" to playlistId.toString(),
            "n" to "100000",
            "s" to "8"
        )
        return request("https://music.163.com/api/v6/playlist/detail", params, CryptoMode.API, "POST", usePersistedCookies = true)
    }

    /**
     * Fetch full track metadata for a list of song IDs.
     * This is used to complete playlist sync when playlist/detail embeds only a subset of tracks.
     */
    fun getSongDetails(songIds: List<Long>): String {
        if (songIds.isEmpty()) {
            return """{"code":200,"songs":[]}"""
        }

        val ids = JSONArray()
        val c = JSONArray()
        songIds.forEach { id ->
            ids.put(id)
            c.put(JSONObject().put("id", id))
        }

        val params = mutableMapOf<String, Any>(
            "ids" to ids.toString(),
            "c" to c.toString()
        )
        return request("https://music.163.com/api/v3/song/detail", params, CryptoMode.API, "POST", usePersistedCookies = true)
    }

    // ─── Song Data ─────────────────────────────────────────────────────

    /**
     * Get song download/streaming URL.
     * Uses EAPI encryption (like NeriPlayer).
     * Will retry with session warm-up if needed.
     */
    fun getSongDownloadUrl(songId: Long, level: String = "exhigh"): String {
        fun call(): String {
            // 高解析度音质必须使用 flac 编码类型，否则服务端会忽略 level 返回低音质
            val encodeType = if (level == "lossless" || level == "hires" || level == "jyeffect") "flac" else "mp3"
            val params = mutableMapOf<String, Any>(
                "ids" to "[$songId]",
                "level" to level,
                "encodeType" to encodeType
            )
            return callEApi("/song/enhance/player/url/v1", params, usePersistedCookies = true)
        }

        var resp = call()
        return try {
            val code = JSONObject(resp).optInt("code", -1)
            if (code == 301 && hasLogin()) {
                try {
                    ensureWeapiSession()
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: session warm-up failed, continuing with original response")
                }
                resp = call()
            }
            resp
        } catch (_: Exception) {
            resp
        }
    }

    // ─── Search ────────────────────────────────────────────────────────

    fun searchSongs(keyword: String, limit: Int = 30, offset: Int = 0): String {
        val params = mutableMapOf<String, Any>(
            "s" to keyword,
            "type" to "1",
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "total" to "true"
        )
        return request("https://music.163.com/weapi/cloudsearch/get/web", params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    // ─── Lyrics ────────────────────────────────────────────────────────

    fun getLyrics(songId: Long): String {
        val params = mutableMapOf<String, Any>(
            "id" to songId.toString(),
            "cp" to "false",
            "lv" to -1,
            "tv" to -1,
            "kv" to -1,
            "rv" to -1,
            "yv" to 1,
            "ytv" to 1,
            "yrv" to 1
        )
        return callEApi("/song/lyric/v1", params, usePersistedCookies = true)
    }
}
