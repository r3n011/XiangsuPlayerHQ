package com.theveloper.pixelplay.data.bilibili

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * 为 Bilibili API 与 CDN 请求自动补全必要的 HTTP header（UA、Referer、Origin、Cookie）。
 * 这些 header 对 ExoPlayer 播放 Bilibili 音视频流至关重要，缺少 Referer 会被 CDN 拒绝。
 */
class BilibiliHeaderInterceptor(
    private val repository: BilibiliRepository
) : Interceptor {

    companion object {
        private const val PC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val BILIBILI_REFERER = "https://www.bilibili.com"
        private const val BILIBILI_ORIGIN = "https://www.bilibili.com"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()

        if (!isBilibiliRequest(host)) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()

        // 仅当请求尚未设置对应 header 时才补充，避免覆盖调用方显式指定的值。
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", PC_USER_AGENT)
        }
        if (request.header("Referer") == null) {
            builder.header("Referer", BILIBILI_REFERER)
        }
        if (request.header("Origin") == null) {
            builder.header("Origin", BILIBILI_ORIGIN)
        }

        val cookieHeader = repository.getCookieHeader()
        if (cookieHeader.isNotBlank() && request.header("Cookie") == null) {
            builder.header("Cookie", cookieHeader)
            Timber.d("Added Bilibili cookie header for %s", host)
        }

        return chain.proceed(builder.build())
    }

    private fun isBilibiliRequest(host: String): Boolean {
        return host == "bilibili.com" ||
            host == "api.bilibili.com" ||
            host.endsWith(".bilibili.com") ||
            host.endsWith(".bilivideo.com") ||
            host.endsWith(".mcdn.bilivideo.com") ||
            host.endsWith(".biliapi.com") ||
            host.endsWith(".biliapi.net") ||
            host.endsWith(".hdslb.com") ||
            host.contains("bilivideo") ||
            host.contains("bilibili")
    }
}
