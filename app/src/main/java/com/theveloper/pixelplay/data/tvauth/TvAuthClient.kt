package com.theveloper.pixelplay.data.tvauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 把组装好的账号凭据一次性 POST 给电视。 */
class TvAuthClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun send(target: TvQrPayload, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(target.url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("X-Pair-Version", "1")
                .header("X-Pair-Token", target.nonce)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(errorMessage(response.code))
                }
            }
            Unit
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        403 -> "二维码已失效或已被使用，请在电视上重新生成"
        404 -> "电视授权页已关闭，请重新打开"
        405 -> "电视拒绝了这次请求"
        410 -> "二维码已过期，请在电视上重新生成"
        413 -> "账号数据过大，请联系开发者"
        415 -> "电视端不支持该请求，请更新电视端"
        else -> "电视返回错误（$code）"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
