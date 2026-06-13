package com.theveloper.pixelplay.data.lx

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
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

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val req = when (method.uppercase()) {
                "POST" -> {
                    val rb = (body ?: "{}").toRequestBody(mediaType)
                    builder.post(rb).build()
                }
                "PUT" -> builder.put((body ?: "{}").toRequestBody(mediaType)).build()
                "DELETE" -> if (body != null) builder.delete(body.toRequestBody(mediaType)).build() else builder.delete().build()
                else -> builder.get().build()
            }

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                val headerMap = resp.headers.toMap()
                LxHttpResponse(
                    statusCode = resp.code,
                    headers = headerMap,
                    body = bodyStr
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
    val headers: Map<String, String>,
    val body: String,
    val error: String? = null
)
