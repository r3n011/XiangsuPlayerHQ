package com.theveloper.pixelplay.data.listentogether

import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 一起听邀请链接解析（含**短链还原**）。
 *
 * 网易云官方分享出去的一起听链接是短网址，短链本身不带 `roomId` / `inviterId`，
 * 直接正则扫描（[parseListenTogetherInvitation]）必然失败 —— 表现为「输入没法自动识别」。
 * 这里补上联网还原：
 *   短链 3xx 跳转 → 最终长链 `.../listen-together/share/?songId=..&roomId=..&inviterId=..`；
 *   若跳转目标不是带参数的长链（部分短链走落地页 HTML / 脚本跳转），则扫描落地页正文。
 */

/** 允许跟随跳转的网易域名后缀：避免被任意链接牵着走（防止 SSRF / 钓鱼跳转） */
private val ALLOWED_HOST_SUFFIXES = listOf(
    "163.com",
    "163cn.tv",
    "126.net",
    "127.net",
    "music.163.com"
)

/** 文本中的第一个 http(s) 链接 */
private val URL_IN_TEXT = Regex("(?i)https?://[^\\s\"'<>()\\[\\]（）【】]+")

private const val MAX_REDIRECT_HOPS = 6
private const val CONNECT_TIMEOUT_MS = 6_000
private const val READ_TIMEOUT_MS = 6_000
private const val MAX_BODY_CHARS = 64 * 1024

/**
 * 文本里是否包含「可能是一起听邀请」的网易链接（长链可直接解析，短链需要联网还原）。
 * 用于剪贴板自动读取：只挑网易域名的链接，避免把无关内容塞进输入框。
 */
fun looksLikeListenTogetherLink(text: String): Boolean {
    val url = extractFirstUrl(text) ?: return false
    return isAllowedHost(url)
}

/**
 * 解析一起听邀请文本。
 *
 * - 长链 / 含 `roomId` + `inviterId` 的文本：直接解析，不联网；
 * - 网易短链：跟随 3xx 跳转拿到长链再解析（必要时扫描落地页正文）；
 * - 无法识别返回 null。
 */
suspend fun resolveListenTogetherInvitation(text: String): ListenTogetherInvitation? {
    parseListenTogetherInvitation(text)?.let { return it }
    val startUrl = extractFirstUrl(text) ?: return null
    if (!isAllowedHost(startUrl)) return null
    return withContext(Dispatchers.IO) { followRedirectsAndParse(startUrl) }
}

/** 从任意文本里挑出第一个 http(s) 链接 */
fun extractFirstUrl(text: String): String? =
    URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ';', '，', '。', ')', '）')?.takeIf { it.length > 8 }

private fun isAllowedHost(url: String): Boolean = runCatching {
    val host = URI(url).host?.lowercase() ?: return false
    ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
}.getOrDefault(false)

private data class HttpProbe(val code: Int, val location: String?, val body: String?)

private fun followRedirectsAndParse(startUrl: String): ListenTogetherInvitation? {
    var current = startUrl
    var hops = 0
    while (hops < MAX_REDIRECT_HOPS) {
        hops++
        val probe = httpGet(current) ?: return null

        // 3xx：跳转目标本身可能就带着房间参数
        val location = probe.location
        if (probe.code in 300..399 && !location.isNullOrBlank()) {
            val next = absolutize(current, location)
            parseListenTogetherInvitation(next)?.let { return it }
            if (!isAllowedHost(next)) return null
            current = next
            continue
        }

        // 200：落地页可能是把参数写在 HTML / 内联脚本里的重定向页，全文扫描一次
        if (probe.code == 200) {
            parseListenTogetherInvitation(probe.body.orEmpty())?.let { return it }
        }
        return null
    }
    return null
}

private fun httpGet(url: String): HttpProbe? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        requestMethod = "GET"
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        )
        setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
    }
    try {
        val code = connection.responseCode
        val location = connection.getHeaderField("Location")
        val body = if (code == 200) {
            runCatching { readLimited(connection.inputStream) }.getOrNull()
        } else {
            null
        }
        HttpProbe(code, location, body)
    } finally {
        runCatching { connection.disconnect() }
    }
}.getOrElse {
    Timber.w(it, "listen-together: 短链还原请求失败 url=%s", url)
    null
}

/** 限长读取正文：落地页可能很大，只需要前 64KB 用于扫描参数 */
private fun readLimited(stream: java.io.InputStream): String {
    val builder = StringBuilder()
    val buffer = CharArray(8 * 1024)
    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
        while (builder.length < MAX_BODY_CHARS) {
            val read = reader.read(buffer, 0, minOf(buffer.size, MAX_BODY_CHARS - builder.length))
            if (read <= 0) break
            builder.append(buffer, 0, read)
        }
    }
    return builder.toString()
}

/** 把 Location 解析成绝对地址（兼容 `//host/path` 与相对路径） */
private fun absolutize(baseUrl: String, location: String): String =
    runCatching { URI(baseUrl).resolve(location.trim()).toString() }.getOrDefault(location)
