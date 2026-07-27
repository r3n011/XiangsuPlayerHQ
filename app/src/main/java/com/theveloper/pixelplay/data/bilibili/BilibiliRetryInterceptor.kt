package com.theveloper.pixelplay.data.bilibili

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Bilibili API专用重试拦截器
 * 参考PiliPlus的RetryInterceptor实现，针对Bilibili API进行优化
 */
class BilibiliRetryInterceptor(
    private val maxRetryCount: Int = 3,
    private val initialDelayMs: Long = 1000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0 until maxRetryCount) {
            try {
                if (response != null) {
                    response.close()
                }

                response = chain.proceed(request)

                // 检查是否需要重试
                if (shouldRetry(response, attempt)) {
                    response.close()
                    delayForRetry(attempt)
                    continue
                }

                return response

            } catch (e: IOException) {
                lastException = e
                Timber.w(e, "Bilibili request failed, attempt ${attempt + 1}/$maxRetryCount")

                if (isRetryableException(e) && attempt < maxRetryCount - 1) {
                    delayForRetry(attempt)
                    continue
                }

                throw e
            }
        }

        // 所有重试都失败了，重新抛出最后一个异常
        lastException?.let { throw it }
        throw IOException("Bilibili request failed after $maxRetryCount attempts")
    }

    private fun shouldRetry(response: Response, attempt: Int): Boolean {
        val code = response.code
        if (attempt >= maxRetryCount - 1) {
            return false
        }

        // Bilibili特定的重试状态码
        return when (code) {
            403, 401 -> {
                // WBI签名失效，需要刷新mixinKey
                Timber.w("Bilibili API returned $code, WBI signature may be invalid")
                BilibiliSearchApi.invalidateMixinKey()
                true
            }
            500, 502, 503, 504 -> {
                // 服务器错误，重试
                Timber.w("Bilibili API returned server error $code")
                true
            }
            429 -> {
                // 请求频率限制，等待更长时间后重试
                Timber.w("Bilibili API rate limited (429)")
                try {
                    Thread.sleep(initialDelayMs * (attempt + 1) * 2)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                true
            }
            else -> false
        }
    }

    private fun isRetryableException(e: IOException): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.UnknownHostException -> true
            is java.net.SocketException -> true
            else -> e.message?.contains("timeout", ignoreCase = true) == true
        }
    }

    private fun delayForRetry(attempt: Int) {
        val delay = initialDelayMs * (attempt + 1)
        Timber.d("Retrying Bilibili request after $delay ms (attempt ${attempt + 1})")
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
