package com.theveloper.pixelplay.data.lx

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Headers.Companion.toHeaders
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LxHttpFetcher {

    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 发起 HTTP 请求。
     * @param body 请求体：String 原样发送；Map 会被 JSON 序列化（对齐参考项目 request.js 的行为）
     * @param form 表单参数：以 x-www-form-urlencoded 编码后作为请求体
     */
    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        form: Map<String, String>? = null,
        timeoutMs: Long = 15000
    ): LxHttpResponse = withContext(Dispatchers.IO) {
        try {
            val realHeaders = LinkedHashMap<String, String>()
            realHeaders["User-Agent"] = UA
            realHeaders["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
            realHeaders["Accept"] = "*/*"
            headers.forEach { (k, v) -> realHeaders[k] = v }

            val builder = Request.Builder()
                .url(url)
                .headers(realHeaders.toHeaders())

            val req = when (method.uppercase()) {
                "POST", "PUT", "DELETE" -> {
                    val bodyStr: String = when {
                        form != null -> form.entries.joinToString("&") { (k, v) ->
                            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
                        }
                        body is String -> body
                        body != null -> body.toString()
                        else -> ""
                    }
                    val mediaType = (realHeaders["Content-Type"] ?: "application/json; charset=utf-8")
                        .toMediaTypeOrNull()
                    val rb = if (bodyStr.isEmpty() && method.equals("DELETE", ignoreCase = true)) null
                    else bodyStr.toRequestBody(mediaType)
                    when (method.uppercase()) {
                        "POST" -> builder.post(rb ?: "".toRequestBody(null)).build()
                        "PUT" -> builder.put(rb ?: "".toRequestBody(null)).build()
                        else -> if (rb != null) builder.delete(rb).build() else builder.delete().build()
                    }
                }
                else -> builder.get().build()
            }

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                val headerMap = resp.headers.toMap()
                LxHttpResponse(
                    statusCode = resp.code,
                    statusMessage = resp.message,
                    headers = headerMap,
                    body = bodyStr,
                    url = resp.request.url.toString(),
                    ok = resp.isSuccessful
                )
            }
        } catch (t: Throwable) {
            LxHttpResponse(
                statusCode = 0,
                headers = emptyMap(),
                body = "",
                error = t.message ?: t.javaClass.simpleName
            )
        }
    }
}

data class LxHttpResponse(
    val statusCode: Int,
    val statusMessage: String = "",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val url: String = "",
    val ok: Boolean = false,
    val error: String? = null
)
