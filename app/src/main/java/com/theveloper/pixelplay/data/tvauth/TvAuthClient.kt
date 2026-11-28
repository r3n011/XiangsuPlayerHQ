package com.theveloper.pixelplay.data.tvauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** 把组装好的账号凭据一次性 POST 给电视。 */
class TvAuthClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun send(target: TvQrPayload, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(target.url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("X-Pair-Version", "1")
                .header("X-Pair-Token", target.nonce)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // HTTP 状态错误统一转成 IOException，让下面的分支给出去掉堆栈的友好提示。
                    throw IOException(errorMessage(response.code))
                }
            }
            Result.success(Unit)
        } catch (timeout: SocketTimeoutException) {
            // 电视端导入账号需要跑各平台的外网校验，读超时放宽后仍超时多半是网络不通。
            Result.failure(IOException("连接电视超时，请确认手机与电视连的是同一个 Wi-Fi"))
        } catch (io: IOException) {
            Result.failure(IOException(io.message ?: "无法连接电视，请确认手机与电视连的是同一个 Wi-Fi"))
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

        // 电视端导入账号要并行校验各平台 Cookie（单平台最长 12 秒），读超时需覆盖整个导入过程。
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
