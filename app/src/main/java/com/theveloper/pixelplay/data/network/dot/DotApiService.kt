package com.theveloper.pixelplay.data.network.dot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DotApiService @Inject constructor(
    baseOkHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "DotApi"
        private const val BASE_URL = "https://dot.mindreset.tech"
        private const val API_VERSION = "authV2"
    }

    @Volatile
    private var apiKey: String? = null
    @Volatile
    private var deviceId: String? = null

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun setCredentials(apiKey: String?, deviceId: String?) {
        this.apiKey = apiKey
        this.deviceId = deviceId
        Timber.d("$TAG: Credentials set - apiKey: ${apiKey?.take(10)}..., deviceId: ${deviceId?.take(10)}...")
    }

    fun clearCredentials() {
        this.apiKey = null
        this.deviceId = null
        Timber.d("$TAG: Credentials cleared")
    }

    fun hasCredentials(): Boolean = !apiKey.isNullOrBlank() && !deviceId.isNullOrBlank()

    suspend fun pushImage(
        base64Image: String,
        refreshNow: Boolean = true,
        link: String? = null,
        border: Int = 0,
        ditherType: String = "DIFFUSION",
        ditherKernel: String = "FLOYD_STEINBERG"
    ): Result<DotApiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val key = apiKey ?: throw IllegalStateException("API key not configured")
                val device = deviceId ?: throw IllegalStateException("Device ID not configured")

                val url = "$BASE_URL/api/$API_VERSION/open/device/$device/image"

                val bodyJson = JSONObject().apply {
                    put("refreshNow", refreshNow)
                    put("image", base64Image)
                    put("border", border)
                    put("ditherType", ditherType)
                    put("ditherKernel", ditherKernel)
                    link?.let { put("link", it) }
                }.toString()

                val body = bodyJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                Timber.d("$TAG: >>> POST $url")
                Timber.d("$TAG: >>> image size: ${base64Image.length} chars")

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val responseBody = response.body?.string() ?: ""

                    Timber.d("$TAG: <<< HTTP $code")

                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            JSONObject(responseBody).optString("message", response.message)
                        } catch (_: Exception) {
                            response.message
                        }
                        return@withContext Result.failure(DotApiException(code, errorMsg))
                    }

                    val json = JSONObject(responseBody)
                    val result = DotApiResponse(
                        code = json.optInt("code", 200),
                        message = json.optString("message", ""),
                        resultMessage = json.optJSONObject("result")?.optString("message", "") ?: ""
                    )

                    Timber.d("$TAG: <<< Success - ${result.resultMessage}")
                    Result.success(result)
                }
            } catch (e: DotApiException) {
                Timber.e(e, "$TAG: API error")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Request failed")
                Result.failure(e)
            }
        }
    }

    suspend fun testConnection(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            if (!hasCredentials()) {
                return@withContext Result.failure(IllegalStateException("Credentials not configured"))
            }

            try {
                val key = apiKey ?: return@withContext Result.failure(IllegalStateException("API key not configured"))
                val device = deviceId ?: return@withContext Result.failure(IllegalStateException("Device ID not configured"))

                val url = "$BASE_URL/api/$API_VERSION/open/device/$device/image"

                val body = JSONObject().apply {
                    put("refreshNow", false)
                    put("image", "")
                    put("border", 0)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    return@withContext when {
                        response.isSuccessful -> Result.success(true)
                        response.code == 403 -> Result.failure(DotApiException(403, "Forbidden - Invalid API key or device ID"))
                        response.code == 404 -> Result.failure(DotApiException(404, "Device not found"))
                        else -> Result.failure(DotApiException(response.code, response.message))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

data class DotApiResponse(
    val code: Int,
    val message: String,
    val resultMessage: String
)

class DotApiException(val code: Int, message: String) : Exception(message)