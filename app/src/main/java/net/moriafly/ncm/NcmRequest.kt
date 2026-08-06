@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import java.security.MessageDigest
import kotlin.random.Random
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * 请求引擎：对应 util/request.js 的 100 行主流程
 *
 * 支持：
 *  - 3 种加密路由: weapi / eapi / linuxapi（调用 NcmCrypto）
 *  - os=pc / ios / android 的不同 UA、appver、Host
 *  - MUSIC_U / __csrf / MUSIC_A / os 自动拼 cookie
 *  - realIp 注入（X-Real-IP / X-Forwarded-For，对应 option.js realIp）
 *  - proxy (http://127.0.0.1:7890) 简易 HTTP 代理
 *  - 301/302 手动跟随，且把 Set-Cookie 全部合并到 NcmSession
 *  - /api/song/url 返回 302 Location 时直接作为 data[0].url 返回
 *
 * 依赖：JDK HttpURLConnection（不需要 OkHttp / Retrofit）
 */
object NcmRequest {

    // ============================================================
    // 常量（对应 util/request.js 顶部 UA / appver / Host）
    // ============================================================

    enum class OS(val ua: String, val appver: String) {
        PC(
            // 对齐原版 userAgentMap.weapi.pc（Chrome 124 Edg）
            ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0",
            appver = "3.1.17.204416",
        ),
        ANDROID(
            // 对齐原版 userAgentMap.api.android（网易云 Android 客户端）
            ua = "NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14; 23013RK75C Build/UKQ1.230804.001)",
            appver = "8.20.20.231215173437",
        ),
        IOS(
            ua = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)",
            appver = "9.0.90",
        ),
    }

    // ─── 设备指纹（对齐原版 util/request.js processCookieObject，风控关键） ───
    // 进程内稳定的 deviceId（一次生成复用，防止每次变化触发风控）
    private val deviceId: String by lazy { randomHex(32) }

    // 进程内稳定的 _ntes_nuid / WNMCID：
    // 原版 _ntes_nuid 首次随机后由 Set-Cookie 保持、WNMCID 进程级只计算一次。
    // 每次请求都重新随机会导致设备指纹频繁变化，反而被判定为异常流量（405 操作频繁）。
    private val stableNuid: String by lazy { randomHex(32) }
    private val stableWnmcid: String by lazy { "${randomAlpha(6)}.${System.currentTimeMillis()}.01.0" }

    private fun osMeta(os: OS): Triple<String, String, String> = when (os) {
        OS.PC -> Triple("Microsoft-Windows-10-Professional-build-19045-64bit", os.appver, "netease")
        OS.ANDROID -> Triple("14", os.appver, "xiaomi")
        OS.IOS -> Triple("16.2", os.appver, "distribution")
    }

    private val HEX = "0123456789abcdef"
    private val ALPHA = "abcdefghijklmnopqrstuvwxyz"

    private fun randomHex(len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(HEX[Random.nextInt(HEX.length)]) }
        return sb.toString()
    }

    private fun randomAlpha(len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(ALPHA[Random.nextInt(ALPHA.length)]) }
        return sb.toString()
    }

    // ─── 匿名 token（对齐原版 generateConfig.js + util/request.js） ─────────
    // 未登录时通过 /api/register/anonimous 获取 MUSIC_A，模拟游客设备，
    // 可显著降低 405「操作频繁」风控概率。注册只需进程内一次。
    @Volatile
    private var anonymousReady = false
    @Volatile
    private var anonymousRegistering = false
    private val anonymousLock = Any()

    /** 匿名 MUSIC_A token（对齐原版 anonymous_token 文件：进程内注册一次并全局复用） */
    @Volatile
    private var anonymousToken: String? = null

    private fun cloudmusicDllEncodeId(id: String): String {
        // 对齐原版 register_anonimous.js：XOR 循环 + MD5 + Base64
        val key = "3go8&$8*3*3h0k(2)2"
        val xored = StringBuilder(id.length)
        for (i in id.indices) {
            xored.append((id[i].code xor key[i % key.length].code).toChar())
        }
        val digest = MessageDigest.getInstance("MD5")
            .digest(xored.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** 惰性获取匿名 token：未登录时注册一次，Set-Cookie 中的 MUSIC_A 自动并入 session */
    private suspend fun ensureAnonymousToken(sess: NcmSession?) {
        if (anonymousReady) return
        if (sess?.isLogin == true) { anonymousReady = true; return }              // 已登录无需匿名
        if (sess?.cookies?.containsKey("MUSIC_A") == true) { anonymousReady = true; return }
        synchronized(anonymousLock) {
            if (anonymousReady || anonymousRegistering) return
            anonymousRegistering = true
        }
        try {
            val deviceId = randomHex(52).uppercase()
            val username = Base64.encodeToString(
                "$deviceId ${cloudmusicDllEncodeId(deviceId)}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            val encrypted = NcmCrypto.weapi(mapOf("username" to username))
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val target = buildWeapiUrl("/api/register/anonimous")
            val headers = buildHeaders(
                host = URL(target).host,
                referer = WY_YX_BASE_URL + "/",
                osEnum = OS.PC,
                session = sess,
                realIp = sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", target, headers, body, sess?.proxy)
            // 从 Set-Cookie 提取 MUSIC_A 保存到进程变量（对齐原版 anonymous_token 文件）
            raw.setCookies.forEach { rawCookie ->
                val head = rawCookie.split(';').firstOrNull()?.trim() ?: return@forEach
                val idx = head.indexOf('=')
                if (idx > 0 && head.substring(0, idx) == "MUSIC_A") {
                    head.substring(idx + 1).takeIf { it.isNotBlank() }?.let { anonymousToken = it }
                }
            }
            if (raw.setCookies.isNotEmpty()) sess?.merge(raw.setCookies)
            Timber.d("NCM anonymous register -> http=$raw.code setCookies=${raw.setCookies.size} musicA=${anonymousToken != null}")
        } catch (t: Throwable) {
            Timber.w(t, "NCM anonymous register 失败")
        } finally {
            anonymousRegistering = false
            // 无论成败本次进程只尝试一次（失败则下次启动再试）
            anonymousReady = true
        }
    }

    // ─── 随机中国 IP（对齐原版 generateRandomChineseIP，弱化地理风控） ─────
    private val CHINA_IP_RANGES = listOf(
        "36.56.", "36.57.", "39.128.", "39.129.", "42.48.", "42.49.",
        "49.64.", "49.65.", "58.17.", "58.18.", "60.13.", "60.14.",
        "61.49.", "61.52.", "101.37.", "101.38.", "106.12.", "106.13.",
        "110.32.", "110.33.", "111.55.", "111.56.", "112.64.", "112.65.",
        "114.80.", "114.81.", "115.56.", "115.57.", "116.25.", "116.26.",
        "117.20.", "117.21.", "118.122.", "118.123.", "120.48.", "120.49.",
        "121.60.", "121.61.", "122.49.", "122.50.", "123.56.", "123.57.",
        "124.71.", "124.72.", "125.36.", "125.37.", "171.8.", "171.9.",
        "180.120.", "180.121.", "183.44.", "183.45.", "202.105.", "202.106.",
        "210.28.", "210.29.", "218.24.", "218.25.", "219.128.", "219.129.",
        "220.200.", "220.201.", "222.32.", "222.33.", "223.64.", "223.65.",
    )

    /** 进程内固定的随机中国 IP（一次生成复用，避免频繁变动反触发风控） */
    private val randomChinaIp: String? by lazy {
        CHINA_IP_RANGES.randomOrNull()?.let { seg ->
            "$seg${Random.nextInt(0, 256)}.${Random.nextInt(0, 256)}"
        }
    }

    /**
     * 生成设备指纹 cookie 串（对齐原版 processCookieObject）：
     * _ntes_nuid / _ntes_nnid / WNMCID / WEVNSM / NMTID / __remember_me / ntes_kaola_ad / osver / deviceId / channel
     *
     * 与 session cookie 合并时只补缺失键：session 里已有的（如服务器 Set-Cookie 下发的
     * _ntes_nuid / WNMCID）不再追加，避免 Cookie 头出现重复键导致风控异常。
     */
    private fun deviceFingerprintCookies(os: OS, session: NcmSession?, includeNmtid: Boolean): String {
        val now = System.currentTimeMillis()
        val (osver, appver, channel) = osMeta(os)
        val existing = session?.cookies ?: emptyMap()
        val sb = StringBuilder()
        fun appendIfMissing(key: String, value: String) {
            if (key in existing) return
            if (sb.isNotEmpty()) sb.append("; ")
            sb.append("$key=$value")
        }
        appendIfMissing("__remember_me", "true")
        appendIfMissing("ntes_kaola_ad", "1")
        appendIfMissing("_ntes_nuid", stableNuid)
        appendIfMissing("_ntes_nnid", "$stableNuid,$now")
        appendIfMissing("WNMCID", stableWnmcid)
        appendIfMissing("WEVNSM", "1.0.0")
        // NMTID：非 login 请求每次新生成（对齐原版），session 通常不含此键
        if (includeNmtid) appendIfMissing("NMTID", randomHex(16))
        appendIfMissing("osver", osver)
        appendIfMissing("deviceId", deviceId)
        appendIfMissing("os", os.name.lowercase())
        appendIfMissing("channel", channel)
        appendIfMissing("appver", appver)
        return sb.toString()
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** weapi 接口基础地址（官方 PC Host） */
    const val WY_YX_BASE_URL = "https://music.163.com"
    const val WY_INTERFACE_BASE_URL = "https://interface3.music.163.com"
    const val WY_INTERFACE_EAPI_BASE_URL = "https://interface3.music.163.com"
    const val WY_LINUXAPI_BASE_URL = "https://music.163.com"

    // ============================================================
    // 公开 API —— 3 种加密路由
    // ============================================================

    /**
     * weapi POST（对应 `crypto: weapi` 的所有 module）。
     * 结果返回统一格式 { code, message?, data } 的 Map。
     */
    suspend fun weapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val osValue = sess?.os?.takeIf { it.isNotBlank() } ?: "pc"
            val osEnum = when (osValue.lowercase()) {
                "android" -> OS.ANDROID
                "ios" -> OS.IOS
                else -> OS.PC
            }

            // 1. 拼入 __csrf / e_r / eparams（对应 util/request.js 开头 if(option.cookie) 逻辑）
            val csrf = sess?.cookies?.get("__csrf").orEmpty()
            val enriched = LinkedHashMap(params)
            if (csrf.isNotBlank()) enriched["csrf_token"] = csrf

            // 2. 加密
            val encrypted = NcmCrypto.weapi(enriched)

            // 3. 选择目标 Host（官方 weapi 走 music.163.com，可选自定义 url 参数）
            //    对齐原版 util/request.js：'/api/xxx' → 'https://music.163.com/weapi/xxx'
            val target = url ?: buildWeapiUrl(path)

            // 4. POST
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(target).host,
                referer = WY_YX_BASE_URL + "/",
                osEnum = osEnum,
                session = sess,
                extraCookie = "os=$osValue",
                realIp = realIp ?: sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )

            val raw = http("POST", target, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = false)
        }
    }

    /**
     * eapi POST（对应 `crypto: eapi` 的 module）
     */
    suspend fun eapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val osEnum = OS.ANDROID
            // 对齐原版 util/request.js：'/api/xxx' → 'https://interface3.music.163.com/eapi/xxx'
            // 注意：加密摘要仍使用原始 '/api/xxx' 路径（NcmCrypto.eapi 的第一个参数）
            val fullUrl = url ?: buildEapiUrl(path)

            val encrypted = NcmCrypto.eapi(path, params)
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(fullUrl).host,
                referer = null,
                osEnum = osEnum,
                session = sess,
                realIp = realIp ?: sess?.realIp,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", fullUrl, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = true)
        }
    }

    /**
     * linuxapi POST（对应 `crypto: linuxapi` 的 module）
     */
    suspend fun linuxapi(
        path: String,
        params: Map<String, Any?>,
        url: String? = null,
    ): Result<NcmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val sess = NcmSession.INSTANCE
            ensureAnonymousToken(sess)
            val target = url ?: (WY_LINUXAPI_BASE_URL + path)
            val encrypted = NcmCrypto.linuxapi(params)
            val body = encrypted.toFormBody().toByteArray(Charsets.UTF_8)
            val headers = buildHeaders(
                host = URL(target).host,
                referer = null,
                osEnum = OS.PC,
                session = sess,
                contentType = "application/x-www-form-urlencoded",
                accept = "*/*",
            )
            val raw = http("POST", target, headers, body, sess?.proxy)
            handleRawResponse(raw, path, sess, isEapi = false)
        }
    }

    // ============================================================
    // 内部：响应处理
    // ============================================================

    internal data class Raw(
        val code: Int,
        val bodyBytes: ByteArray,
        val location: String?,
        val setCookies: List<String>,
    )

    private fun handleRawResponse(
        raw: Raw,
        path: String,
        sess: NcmSession?,
        isEapi: Boolean,
    ): NcmResponse {
        // 1) Set-Cookie 合并进 session
        if (raw.setCookies.isNotEmpty()) sess?.merge(raw.setCookies)

        // 2) 301/302 — 两种处理：
        //    - /api/song/enhance/player/url 系列 → 把 Location 当 data[0].url
        //    - 其他情况 → 手动跟随
        if (raw.code in 300..399) {
            val loc = raw.location
                ?: return NcmResponse(raw.code, emptyMap(), "3xx 无 Location")

            // 特殊：/song/url / /song/enhance/player/url 系列，返回 302 -> data[0].url = loc
            if (path.contains("/song/url") || path.contains("player/url")) {
                val fake = mapOf(
                    "code" to raw.code,
                    "data" to listOf(mapOf("url" to loc))
                )
                return NcmResponse(raw.code, fake, null)
            }

            // 跟随一次
            Timber.d("3xx follow: $path -> $loc")
            val followHeaders = buildHeaders(
                host = URL(loc).host,
                referer = null,
                osEnum = OS.PC,
                session = sess,
                accept = "*/*",
            )
            val raw2 = http("GET", loc, followHeaders, null, sess?.proxy)
            if (raw2.setCookies.isNotEmpty()) sess?.merge(raw2.setCookies)
            return parseBody(raw2.code, raw2.bodyBytes, path, isEapi = false)
        }

        return parseBody(raw.code, raw.bodyBytes, path, isEapi)
    }

    private fun parseBody(code: Int, bodyBytes: ByteArray, path: String, isEapi: Boolean): NcmResponse {
        val body = try {
            String(decompressGzipIfNeeded(bodyBytes), Charsets.UTF_8)
        } catch (t: Throwable) {
            String(bodyBytes, Charsets.ISO_8859_1)
        }
        val parsed: Any? = when {
            body.isBlank() -> null
            isEapi -> runCatching { NcmCrypto.eapiResDecrypt(body) }.getOrNull()
                ?: runCatching { NcmJson.parseAny(body) }.getOrNull()
            else -> runCatching { NcmJson.parseAny(body) }.getOrNull()
        }
        val map = parsed as? Map<String, Any?> ?: emptyMap()
        val respCode = map["code"] as? Int ?: code
        val msg = when {
            map.contains("message") -> map["message"]?.toString()
            map.contains("msg") -> map["msg"]?.toString()
            else -> null
        }
        Timber.d("NCM $path -> code=$respCode bodyLen=${body.length}")
        Timber.d("NCM $path -> body=${body.take(1200)}")
        return NcmResponse(respCode, map, msg)
    }

    private fun decompressGzipIfNeeded(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        // gzip (1F 8B)
        if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            return GZIPInputStream(data.inputStream()).use { it.readBytesCompat() }
        }
        // zlib/deflate (0x78 xx)
        if (data[0] == 0x78.toByte()) {
            return try {
                InflaterInputStream(data.inputStream()).use { it.readBytesCompat() }
            } catch (t: Throwable) {
                data
            }
        }
        return data
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (true) {
            n = this.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ============================================================
    // URL 构建（对齐原版 util/request.js 的路径转换）
    //   weapi: '/api/xxx' → 'https://music.163.com/weapi/xxx'
    //   eapi : '/api/xxx' → 'https://interface3.music.163.com/eapi/xxx'
    // ============================================================

    private fun buildWeapiUrl(path: String): String = when {
        path.startsWith("/api/") -> WY_YX_BASE_URL + "/weapi/" + path.removePrefix("/api/")
        path.startsWith("/weapi/") || path.startsWith("/eapi/") -> WY_YX_BASE_URL + path
        else -> WY_YX_BASE_URL + path
    }

    private fun buildEapiUrl(path: String): String = when {
        path.startsWith("/api/") -> WY_INTERFACE_EAPI_BASE_URL + "/eapi/" + path.removePrefix("/api/")
        path.startsWith("/eapi/") -> WY_INTERFACE_EAPI_BASE_URL + path
        else -> WY_INTERFACE_EAPI_BASE_URL + path
    }

    // ============================================================
    // Header 构建
    // ============================================================

    @Suppress("LongParameterList")
    private fun buildHeaders(
        host: String,
        referer: String?,
        osEnum: OS,
        session: NcmSession?,
        extraCookie: String? = null,
        realIp: String? = null,
        contentType: String? = null,
        accept: String? = null,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["Host"] = host
        headers["User-Agent"] = osEnum.ua
        if (accept != null) headers["Accept"] = accept
        if (contentType != null) headers["Content-Type"] = contentType
        headers["Accept-Encoding"] = "gzip"
        headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
        if (referer != null) headers["Referer"] = referer
        headers["Connection"] = "keep-alive"

        // X-Real-IP / X-Forwarded-For（未设置 realIp 时用进程内固定的随机中国 IP，弱化地理风控）
        val effRealIp = realIp ?: randomChinaIp
        if (effRealIp != null) {
            headers["X-Real-IP"] = effRealIp
            headers["X-Forwarded-For"] = effRealIp
        }

        // Cookie
        val cookieSb = StringBuilder()
        session?.toCookieHeader()?.takeIf { it.isNotBlank() }?.let {
            cookieSb.append(it)
        }
        // 未登录时注入匿名 MUSIC_A（对齐原版 processCookieObject：无 MUSIC_U 则带 MUSIC_A）
        val hasMusicU = session?.cookies?.containsKey("MUSIC_U") == true
        val musicA = anonymousToken ?: session?.cookies?.get("MUSIC_A")
        if (!hasMusicU && musicA != null) {
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append("MUSIC_A=$musicA")
        }
        if (extraCookie != null) {
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(extraCookie)
        }
        if (osEnum == OS.ANDROID) {
            // eapi：对齐原版 createHeaderCookie(header)——header 字段完整拼入 Cookie
            val now = System.currentTimeMillis()
            val (osver, appver, channel) = osMeta(OS.ANDROID)
            val csrf = session?.cookies?.get("__csrf") ?: ""
            val headerCookie = buildString {
                append("osver=$osver; deviceId=$deviceId; os=android; appver=$appver; ")
                append("versioncode=${appver.replace(".", "")}; mobilename=; ")
                append("buildver=${now.toString().substring(0, 10)}; resolution=1920x1080; ")
                append("__csrf=$csrf; channel=$channel; ")
                append("requestId=${now}_${Random.nextInt(1000).toString().padStart(4, '0')}")
                // 对齐原版 createHeaderCookie：header 里带 MUSIC_U / MUSIC_A
                val cu = session?.cookies?.get("MUSIC_U")
                if (!cu.isNullOrBlank()) append("; MUSIC_U=$cu")
                val ca = anonymousToken ?: session?.cookies?.get("MUSIC_A")
                if (!ca.isNullOrBlank()) append("; MUSIC_A=$ca")
            }
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(headerCookie)
        } else {
            // weapi：对齐原版 processCookieObject（完整设备指纹 cookie）
            if (cookieSb.isNotEmpty()) cookieSb.append("; ")
            cookieSb.append(deviceFingerprintCookies(osEnum, session, includeNmtid = true))
        }
        if (cookieSb.isNotBlank()) headers["Cookie"] = cookieSb.toString()

        return headers
    }

    // ============================================================
    // 底层 HTTP
    // ============================================================

    private fun http(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        proxy: String?,
    ): Raw {
        val u = URL(url)
        val conn = if (proxy.isNullOrBlank()) u.openConnection() else u.openConnection(parseProxy(proxy))

        conn as HttpURLConnection
        if (conn is HttpsURLConnection) {
            // 保持默认系统 TLS，不走自定义
        }
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false    // 手动处理 3xx
        conn.doInput = true
        conn.doOutput = body != null
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        if (body != null) {
            conn.outputStream.use { it.write(body) }
        }

        return try {
            val code = conn.responseCode
            val setCookies = extractSetCookies(conn)
            val loc = conn.getHeaderField("Location")
            val stream = runCatching {
                if (code in 200..299) conn.inputStream else conn.errorStream
            }.getOrNull()
            val bytes = stream?.use { it.readBytesCompat() } ?: ByteArray(0)
            Raw(code, bytes, loc, setCookies)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun extractSetCookies(conn: HttpURLConnection): List<String> {
        val out = ArrayList<String>(2)
        var i = 0
        while (true) {
            val k = conn.getHeaderFieldKey(i) ?: break
            if (k.equals("Set-Cookie", ignoreCase = true)) {
                conn.getHeaderField(i)?.let(out::add)
            }
            i++
        }
        return out
    }

    private fun parseProxy(s: String): Proxy {
        val noScheme = s.removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val parts = noScheme.split(':', limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        return Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress.createUnresolved(host, port))
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

/**
 * NCM 响应通用结构
 *
 * @property code HTTP 层 / body code 合并后的最终 code（200 一般为成功）
 * @property body 解析后的 JSON Map；当 /api/song/url 302 时返回合成对象 { code, data:[{url}] }
 * @property message 错误信息（如果有）
 */
data class NcmResponse(
    val code: Int,
    val body: Map<String, Any?>,
    val message: String?,
) {
    val isSuccess: Boolean get() = code == 200

    inline fun <T> map(block: (Map<String, Any?>) -> T): Result<T> =
        if (isSuccess) runCatching { block(body) }
        else Result.failure(IllegalStateException("NCM code=$code msg=$message"))
}
