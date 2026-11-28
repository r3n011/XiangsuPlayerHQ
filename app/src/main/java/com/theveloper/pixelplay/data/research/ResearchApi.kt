package com.theveloper.pixelplay.data.research

import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.di.ResearchOkHttpClient
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「听歌数据研究计划」API 客户端（规范 03 §2 接口总览）。
 *
 * - 注册与公告/协议：无需签名
 * - 其余接口：密钥 + HMAC 请求签名（[ResearchSigner]，body 哈希对实际发送字节计算）
 * - 统一错误体 { error: { code, message, retryAfter } } → [ResearchApiException]
 * - 上游密钥只在服务端；客户端只连自家域名，不接触上游地址
 */
@Singleton
class ResearchApi @Inject constructor(
    @ResearchOkHttpClient private val client: OkHttpClient,
    private val authStore: ResearchAuthStore
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = false
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /* ── 端点路径（canonical 签名用原样 path+query，不含域名） ── */

    private fun legalPath(key: String, version: String?): String {
        val base = "/activity/v1/legal/$key"
        return if (version.isNullOrBlank()) base else "$base?version=$version"
    }

    /* ── 注册（无需签名） ── */

    fun enroll(request: ResearchEnrollRequest): ResearchEnrollResponse {
        val body = json.encodeToString(ResearchEnrollRequest.serializer(), request)
        val response = post(P_ENROLL, body, signed = false)
        return parseBody(response, ResearchEnrollResponse.serializer())
    }

    /* ── 听歌事件上传（签名，幂等） ── */

    fun listen(request: ResearchListenRequest): ResearchListenResponse {
        val body = json.encodeToString(ResearchListenRequest.serializer(), request)
        val response = post(P_LISTEN, body, signed = true)
        return parseBody(response, ResearchListenResponse.serializer())
    }

    /* ── 状态查询（签名，GET 空 body） ── */

    fun me(): ResearchMeResponse {
        val response = get(P_ME, signed = true)
        return parseBody(response, ResearchMeResponse.serializer())
    }

    /** 导出本设备上传记录（原始 JSON 字符串，由仓库层落盘） */
    fun exportRaw(): String {
        val response = get(P_EXPORT, signed = true)
        return parseRaw(response)
    }

    /* ── 设置 / 退出（签名） ── */

    fun setAutoDaily(autoDaily: Boolean) {
        val body = json.encodeToString(
            ResearchSettingsRequest.serializer(),
            ResearchSettingsRequest(autoDaily)
        )
        post(P_SETTINGS, body, signed = true).use { /* 204 或空体即成功 */ }
    }

    fun deleteMe() {
        val request = signedRequestBuilder("DELETE", P_ME, ByteArray(0))
            .delete(ByteArray(0).toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            ensureSuccess(response)
        }
    }

    /* ── 公告与协议（无需签名） ── */

    fun notice(): ResearchNoticeFeed {
        val response = get(P_NOTICE, signed = false)
        return parseBody(response, ResearchNoticeFeed.serializer())
    }

    fun notices(limit: Int = 20, cursor: String?): ResearchNoticePage {
        val path = buildString {
            append(P_NOTICES)
            append("?limit=").append(limit)
            if (!cursor.isNullOrBlank()) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        val response = get(path, signed = false)
        return parseBody(response, ResearchNoticePage.serializer())
    }

    fun legalSummary(): ResearchLegalSummary {
        val response = get("/activity/v1/legal", signed = false)
        return parseBody(response, ResearchLegalSummary.serializer())
    }

    fun legalDocument(key: String, version: String? = null): ResearchLegalDocument {
        val response = get(legalPath(key, version), signed = false)
        val wrapped = parseBody(response, ResearchLegalDocumentResponse.serializer())
        return wrapped.document
    }

    /* ── AI 转发（签名；流式由调用方按 SSE 读取） ── */

    fun aiModels(): ResearchAiModelsResponse {
        val response = get(P_AI_MODELS, signed = true)
        return parseBody(response, ResearchAiModelsResponse.serializer())
    }

    /** 发起 ai/chat，返回未关闭的 [Response]；非流式由调用方解析，流式按 SSE 逐帧读 */
    fun aiChatRaw(request: ResearchAiChatRequest): Response {
        val body = json.encodeToString(ResearchAiChatRequest.serializer(), request)
        return post(P_AI_CHAT, body, signed = true, longReadTimeout = true)
    }

    /** 就地解析非流式 AI 响应体（usage 透传上游；quota 从响应头取，见 [quotaFromHeaders]） */
    fun parseAiChatBody(response: Response): String = parseRaw(response)

    /* ── 内部工具 ── */

    private fun signedRequestBuilder(
        method: String,
        pathWithQuery: String,
        rawBody: ByteArray
    ): Request.Builder {
        val licenseKey = authStore.licenseKey
        val signKeyId = authStore.signKeyId
        val signKey = authStore.signKey
        if (licenseKey.isNullOrBlank() || signKeyId.isNullOrBlank() || signKey.isNullOrBlank()) {
            throw ResearchApiException(
                code = "INVALID_KEY",
                message = "research session missing",
                httpStatus = -1
            )
        }
        val headers = ResearchSigner.sign(
            method = method,
            pathWithQuery = pathWithQuery,
            rawBody = rawBody,
            licenseKey = licenseKey,
            signKeyId = signKeyId,
            signKeyRaw = signKey,
            appVersion = BuildConfig.VERSION_NAME,
            serverTimeOffsetSec = authStore.serverTimeOffsetSec
        )
        return Request.Builder()
            .url(ResearchEndpoints.BASE_URL + pathWithQuery)
            .apply { headers.toHeaderMap(BuildConfig.VERSION_NAME).forEach { (k, v) -> header(k, v) } }
    }

    private fun get(path: String, signed: Boolean): Response {
        val builder = if (signed) {
            signedRequestBuilder("GET", path, ByteArray(0)).get()
        } else {
            Request.Builder().url(ResearchEndpoints.BASE_URL + path).get()
        }
        return client.newCall(builder.build()).execute()
    }

    private fun post(path: String, jsonBody: String, signed: Boolean, longReadTimeout: Boolean = false): Response {
        val rawBody = jsonBody.toByteArray(Charsets.UTF_8)
        val requestBody: RequestBody = rawBody.toRequestBody(jsonMedia)
        val builder = if (signed) {
            signedRequestBuilder("POST", path, rawBody).post(requestBody)
        } else {
            Request.Builder().url(ResearchEndpoints.BASE_URL + path).post(requestBody)
        }
        val effectiveClient = if (longReadTimeout) {
            client.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        } else {
            client
        }
        return effectiveClient.newCall(builder.build()).execute()
    }

    private fun ensureSuccess(response: Response) {
        if (response.isSuccessful) return
        val raw = runCatching { response.body?.string() }.getOrNull()
        val errorBody = raw?.let {
            runCatching { json.decodeFromString(ResearchErrorBody.serializer(), it) }.getOrNull()
        }?.error
        throw ResearchApiException(
            code = errorBody?.code ?: "HTTP_${response.code}",
            message = errorBody?.message ?: "HTTP ${response.code}",
            httpStatus = response.code,
            retryAfter = errorBody?.retryAfter ?: 0L
        )
    }

    private fun <T> parseBody(response: Response, serializer: kotlinx.serialization.KSerializer<T>): T {
        response.use {
            ensureSuccess(it)
            val raw = it.body?.string() ?: throw ResearchNetworkException()
            return runCatching {
                json.decodeFromString(serializer, raw)
            }.getOrElse { error ->
                throw ResearchNetworkException(error)
            }
        }
    }

    private fun parseRaw(response: Response): String {
        response.use {
            ensureSuccess(it)
            return it.body?.string() ?: throw ResearchNetworkException()
        }
    }

    /** 从响应头提取额度（每次 AI 调用后就地刷新，规范 03 §7 服务端行为 4） */
    fun quotaFromHeaders(response: Response): ResearchAiQuotaFrame? {
        val limit = response.header("X-Quota-Tokens-Limit")?.toLongOrNull() ?: return null
        val used = response.header("X-Quota-Tokens-Used")?.toLongOrNull() ?: 0L
        val remaining = response.header("X-Quota-Tokens-Remaining")?.toLongOrNull()
            ?: (limit - used).coerceAtLeast(0)
        val resetAt = response.header("X-Quota-Reset-At")?.toLongOrNull() ?: 0L
        return ResearchAiQuotaFrame(limit, used, remaining, resetAt)
    }

    companion object {
        /* ── 端点路径（canonical 签名用原样 path+query，不含域名） ── */
        private const val P_ENROLL = "/activity/v1/enroll"
        private const val P_LISTEN = "/activity/v1/listen"
        private const val P_ME = "/activity/v1/me"
        private const val P_EXPORT = "/activity/v1/export"
        private const val P_SETTINGS = "/activity/v1/settings"
        private const val P_AI_CHAT = "/activity/v1/ai/chat"
        private const val P_AI_MODELS = "/activity/v1/ai/models"
        private const val P_NOTICE = "/activity/v1/notice"
        private const val P_NOTICES = "/activity/v1/notices"

        /** SSE 数据帧前缀（流式转发解析用） */
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
    }
}

/** 活动服务端地址（全部接口 HTTPS，规范 §0） */
object ResearchEndpoints {
    const val BASE_URL = "https://xiangsudev.cn"

    // TODO(听歌数据研究计划): 服务端下发主/备 SPKI pin 后，在 AppModule 的
    //  @ResearchOkHttpClient 里填入 CertificatePinner（当前 pins 列表为空 = 未启用固定）。
    const val HOST = "xiangsudev.cn"
}
