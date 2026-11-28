package com.theveloper.pixelplay.data.research

import com.theveloper.pixelplay.data.ai.provider.AiStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 活动额度 AI 代理客户端（规范 03 §7）：POST /activity/v1/ai/chat（OpenAI 兼容）。
 *
 * - 会话生效且剩余额度 > 0 时由 AiOrchestrator 优先走这里，失败自动落回用户自填 Key
 * - 每次调用后用响应头 / 流式末帧 quota 就地刷新额度（不额外请求）
 * - 上游对客户端完全透明：不展示上游名称，全部不可用时上层提示「模型服务暂时不可用」
 */
@Singleton
class ResearchAiClient @Inject constructor(
    private val api: ResearchApi,
    private val repository: ResearchRepository
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** 代理是否可用：会话生效且剩余额度 > 0（与 AI 页额度条同款判据） */
    fun isProxyAvailable(): Boolean = repository.isAiProxyAvailable()

    /** 非流式生成，失败抛异常由调用方回落用户 Key 链 */
    suspend fun generateContent(
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        val request = ResearchAiChatRequest(
            model = "auto",
            messages = listOf(
                ResearchAiMessage(role = "system", content = systemPrompt),
                ResearchAiMessage(role = "user", content = prompt)
            ),
            temperature = temperature.toDouble(),
            stream = false
        )
        val response = api.aiChatRaw(request)
        try {
            if (!response.isSuccessful) throwProxyError(response)
            api.quotaFromHeaders(response)?.let { repository.refreshQuotaFromFrame(it) }
            val raw = api.parseAiChatBody(response)
            val parsed = runCatching {
                json.decodeFromString(ResearchAiChatResponse.serializer(), raw)
            }.getOrElse { error ->
                throw ResearchNetworkException(error)
            }
            parsed.usage?.let { repository.recordProxyUsage(it) }
            val content = parsed.choices.firstOrNull()?.message?.content.orEmpty()
            if (content.isBlank()) throw ResearchNetworkException()
            return content
        } finally {
            response.close()
        }
    }

    /**
     * 流式生成（SSE 逐帧转发）：delta.content 为正式回答增量，
     * delta.reasoning_content 归入思考增量；末帧 usage/quota 刷新额度。
     */
    fun generateContentStream(
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): Flow<AiStreamChunk> = flow {
        val request = ResearchAiChatRequest(
            model = "auto",
            messages = listOf(
                ResearchAiMessage(role = "system", content = systemPrompt),
                ResearchAiMessage(role = "user", content = prompt)
            ),
            temperature = temperature.toDouble(),
            stream = true
        )
        val response = api.aiChatRaw(request)
        if (!response.isSuccessful) throwProxyError(response)
        api.quotaFromHeaders(response)?.let { repository.refreshQuotaFromFrame(it) }

        val source = response.body?.source() ?: throw ResearchNetworkException()
        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith(ResearchApi.SSE_DATA_PREFIX)) continue
                val payload = line.removePrefix(ResearchApi.SSE_DATA_PREFIX).trim()
                if (payload == ResearchApi.SSE_DONE) break
                val chunk = runCatching {
                    json.decodeFromString(ResearchAiStreamChunkDto.serializer(), payload)
                }.getOrNull() ?: continue

                chunk.choices.firstOrNull()?.delta?.let { delta ->
                    delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                        emit(AiStreamChunk(text = it, isThinking = true))
                    }
                    delta.content?.takeIf { it.isNotEmpty() }?.let {
                        emit(AiStreamChunk(text = it, isThinking = false))
                    }
                }
                // 流式末帧：usage + quota（规范 03 §7 服务端行为 5）
                chunk.usage?.let { usage ->
                    repository.recordProxyUsage(usage)
                    chunk.quota?.let { quotaFrame ->
                        repository.refreshQuotaFromFrame(quotaFrame)
                    }
                }
            }
        } finally {
            runCatching { response.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun throwProxyError(response: okhttp3.Response): Nothing {
        val raw = runCatching { response.body?.string() }.getOrNull()
        val error = raw?.let {
            runCatching {
                json.decodeFromString(ResearchErrorBody.serializer(), it).error
            }.getOrNull()
        }
        throw ResearchApiException(
            code = error?.code ?: "UPSTREAM_ERROR",
            message = error?.message ?: "模型服务暂时不可用",
            httpStatus = response.code,
            retryAfter = error?.retryAfter ?: 0L
        )
    }
}

/** 上游 OpenAI 兼容响应（非流式）：只取 choices[0].message.content 与 usage */
@Serializable
data class ResearchAiChatResponse(
    val choices: List<ResearchAiChoice> = emptyList(),
    val usage: ResearchAiUsage? = null
)

@Serializable
data class ResearchAiChoice(
    val message: ResearchAiChatMessage? = null
)

@Serializable
data class ResearchAiChatMessage(
    val role: String = "",
    val content: String = ""
)

/** 流式 SSE 帧：choices[].delta 增量；末帧带 usage + quota（服务端补发） */
@Serializable
data class ResearchAiStreamChunkDto(
    val choices: List<ResearchAiStreamChoice> = emptyList(),
    val usage: ResearchAiUsage? = null,
    val quota: ResearchAiQuotaFrame? = null
)

@Serializable
data class ResearchAiStreamChoice(
    val delta: ResearchAiStreamDelta? = null
)

@Serializable
data class ResearchAiStreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)
