package com.theveloper.pixelplay.data.research

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 「听歌数据研究计划」服务端 DTO 集合。
 *
 * 对应《03 数据格式与接口规范 v1》：全部接口 HTTPS + JSON UTF-8，时间为 Unix 秒（int64）。
 * 服务端 v1 之后新增字段必须可选，因此所有响应模型都配 `ignoreUnknownKeys`；
 * 请求侧用 `explicitNulls = false` 省略未启用字段（如明文三字段、attestation）。
 */

/* ── 注册 ─────────────────────────────────────────────────────────────── */

@Serializable
data class ResearchConsent(
    @SerialName("agreementVersion") val agreementVersion: String,
    @SerialName("privacyVersion") val privacyVersion: String? = null,
    @SerialName("acceptedAt") val acceptedAt: Long
)

@Serializable
data class ResearchAttestation(
    // playintegrity（Play 渠道）/ appattest（iOS）/ none（GitHub 分发，服务端按策略放行）
    @SerialName("type") val type: String,
    @SerialName("keyId") val keyId: String? = null,
    @SerialName("assertion") val assertion: String? = null
)

@Serializable
data class ResearchEnrollRequest(
    @SerialName("deviceCode") val deviceCode: String,
    @SerialName("platform") val platform: String = "android",
    @SerialName("appVersion") val appVersion: String,
    @SerialName("osVersion") val osVersion: String,
    @SerialName("locale") val locale: String,
    @SerialName("plaintextEnabled") val plaintextEnabled: Boolean,
    @SerialName("consent") val consent: ResearchConsent,
    @SerialName("attestation") val attestation: ResearchAttestation? = null
)

@Serializable
data class ResearchKeyStatus(
    @SerialName("status") val status: String, // pending / active / expired / disabled
    @SerialName("expiresAt") val expiresAt: Long,
    @SerialName("autoDaily") val autoDaily: Boolean = false,
    @SerialName("validDays") val validDays: Int = 5,
    @SerialName("graceHours") val graceHours: Int = 24
)

@Serializable
data class ResearchQuota(
    @SerialName("period") val period: String = "upload",
    @SerialName("limitTokens") val limitTokens: Long = 0,
    @SerialName("usedTokens") val usedTokens: Long = 0,
    @SerialName("remainingTokens") val remainingTokens: Long? = null,
    @SerialName("issuedAt") val issuedAt: Long = 0,
    @SerialName("resetAt") val resetAt: Long = 0,
    @SerialName("expired") val expired: Boolean = false
) {
    val effectiveRemaining: Long
        get() = remainingTokens ?: (limitTokens - usedTokens).coerceAtLeast(0)
}

@Serializable
data class ResearchUploadPolicy(
    @SerialName("required") val required: Boolean = true,
    @SerialName("minEvents") val minEvents: Int = 20,
    @SerialName("nextUploadAfter") val nextUploadAfter: Long = 0,
    @SerialName("intervalDays") val intervalDays: Int = 5
)

@Serializable
data class ResearchAuthSecrets(
    @SerialName("signKeyId") val signKeyId: String,
    // signKey 原串即 HMAC 密钥（服务端 createHmac 直用），仅注册响应出现一次；勿做 Base64 解码
    @SerialName("signKey") val signKey: String,
    @SerialName("algorithm") val algorithm: String = "HMAC-SHA256"
)

@Serializable
data class ResearchActivityInfo(
    @SerialName("deadline") val deadline: Long = 0,
    @SerialName("enrollOpen") val enrollOpen: Boolean = true,
    @SerialName("noticeVersion") val noticeVersion: String? = null
)

@Serializable
data class ResearchEnrollResponse(
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("key") val key: ResearchKeyStatus,
    @SerialName("quota") val quota: ResearchQuota,
    @SerialName("upload") val upload: ResearchUploadPolicy,
    @SerialName("auth") val auth: ResearchAuthSecrets,
    @SerialName("activity") val activity: ResearchActivityInfo? = null,
    @SerialName("serverTime") val serverTime: Long = 0,
    @SerialName("hashSaltId") val hashSaltId: Int? = null
)

/* ── 听歌事件上传 ─────────────────────────────────────────────────────── */

@Serializable
data class ResearchListenEventPayload(
    @SerialName("songKey") val songKey: String,
    @SerialName("titleHash") val titleHash: String,
    @SerialName("artistHash") val artistHash: String,
    @SerialName("albumHash") val albumHash: String,
    @SerialName("source") val source: String,
    @SerialName("playedAt") val playedAt: Long,
    @SerialName("durationSec") val durationSec: Int,
    @SerialName("playedSec") val playedSec: Int,
    @SerialName("completed") val completed: Boolean,
    @SerialName("skipped") val skipped: Boolean,
    @SerialName("playMode") val playMode: String,
    @SerialName("quality") val quality: String,
    // 仅 plaintextEnabled=true 时携带（规范：未开启时直接省略）
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null
)

@Serializable
data class ResearchListenRequest(
    @SerialName("deviceCode") val deviceCode: String,
    @SerialName("batchId") val batchId: String,
    @SerialName("uploadedAt") val uploadedAt: Long,
    @SerialName("events") val events: List<ResearchListenEventPayload>
)

@Serializable
data class ResearchListenResponse(
    @SerialName("accepted") val accepted: Int,
    @SerialName("rejected") val rejected: Int = 0,
    @SerialName("nextUploadAfter") val nextUploadAfter: Long = 0,
    @SerialName("active") val active: Boolean? = null,
    @SerialName("need") val need: Int? = null
)

/* ── 状态查询 / 设置 / 退出 ───────────────────────────────────────────── */

@Serializable
data class ResearchMeResponse(
    @SerialName("key") val key: ResearchKeyStatus,
    @SerialName("quota") val quota: ResearchQuota,
    @SerialName("upload") val upload: ResearchUploadPolicy,
    @SerialName("activity") val activity: ResearchActivityInfo? = null,
    @SerialName("serverTime") val serverTime: Long = 0,
    @SerialName("hashSaltId") val hashSaltId: Int? = null
)

@Serializable
data class ResearchSettingsRequest(
    @SerialName("autoDaily") val autoDaily: Boolean
)

/* ── 公告（独立模块） ─────────────────────────────────────────────────── */

@Serializable
data class ResearchNoticeSummary(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String = "system", // rule / deadline / system
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String = "",
    @SerialName("content") val content: String? = null,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("version") val version: String? = null,
    @SerialName("publishedAt") val publishedAt: Long = 0,
    @SerialName("status") val status: String = "active",
    @SerialName("withdrawnAt") val withdrawnAt: Long = 0,
    @SerialName("withdrawReason") val withdrawReason: String = ""
)

@Serializable
data class ResearchNoticeFeed(
    @SerialName("activity") val activity: ResearchActivityInfo? = null,
    @SerialName("noticeVersion") val noticeVersion: String? = null,
    @SerialName("unread") val unread: Int = 0,
    @SerialName("latest") val latest: ResearchNoticeSummary? = null,
    @SerialName("notices") val notices: List<ResearchNoticeSummary> = emptyList()
)

@Serializable
data class ResearchNoticePage(
    @SerialName("notices") val notices: List<ResearchNoticeSummary> = emptyList(),
    @SerialName("nextCursor") val nextCursor: String? = null,
    @SerialName("total") val total: Int = 0
)

/* ── 协议与隐私条款（服务端下发） ─────────────────────────────────────── */

@Serializable
data class ResearchLegalDocumentMeta(
    @SerialName("key") val key: String, // agreement / privacy
    @SerialName("title") val title: String,
    @SerialName("currentVersion") val currentVersion: String,
    @SerialName("published") val published: Boolean = true,
    @SerialName("effectiveAt") val effectiveAt: Long = 0,
    @SerialName("publishedAt") val publishedAt: Long = 0,
    @SerialName("hash") val hash: String? = null
)

@Serializable
data class ResearchLegalSummary(
    @SerialName("documents") val documents: List<ResearchLegalDocumentMeta> = emptyList(),
    @SerialName("versions") val versions: Map<String, String> = emptyMap()
)

@Serializable
data class ResearchLegalDocument(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("version") val version: String,
    @SerialName("content") val content: String,
    @SerialName("effectiveAt") val effectiveAt: Long = 0,
    @SerialName("publishedAt") val publishedAt: Long = 0,
    @SerialName("hash") val hash: String? = null,
    @SerialName("status") val status: String = "published"
)

@Serializable
data class ResearchLegalDocumentResponse(
    @SerialName("document") val document: ResearchLegalDocument
)

/* ── AI 转发（OpenAI 兼容） ───────────────────────────────────────────── */

@Serializable
data class ResearchAiMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

@Serializable
data class ResearchAiChatRequest(
    @SerialName("model") val model: String = "auto",
    @SerialName("messages") val messages: List<ResearchAiMessage>,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("stream") val stream: Boolean = false
)

@Serializable
data class ResearchAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ResearchAiQuotaFrame(
    @SerialName("limitTokens") val limitTokens: Long = 0,
    @SerialName("usedTokens") val usedTokens: Long = 0,
    @SerialName("remainingTokens") val remainingTokens: Long = 0,
    @SerialName("resetAt") val resetAt: Long = 0
)

@Serializable
data class ResearchAiModelsResponse(
    @SerialName("models") val models: List<ResearchAiModel> = emptyList(),
    @SerialName("quota") val quota: ResearchQuota? = null
)

@Serializable
data class ResearchAiModel(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("default") val default: Boolean = false
)

/* ── 错误体 ───────────────────────────────────────────────────────────── */

@Serializable
data class ResearchErrorBody(
    @SerialName("error") val error: ResearchError?
)

@Serializable
data class ResearchError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String = "",
    @SerialName("retryAfter") val retryAfter: Long = 0
)

/**
 * 统一的接口异常：HTTP 非 2xx 或错误体。上层按 [code] 映射界面表现
 * （规范 05 §8 错误码与界面映射表）。
 */
class ResearchApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
    val retryAfter: Long = 0
) : Exception(message)

/** 网络层异常（超时 / 断网 / 解析失败），区别于服务端业务错误。 */
class ResearchNetworkException(override val cause: Throwable? = null) : Exception(cause)
