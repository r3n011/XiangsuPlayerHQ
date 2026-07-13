package com.theveloper.pixelplay.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Xiaomi MiMo API 客户端
 *
 * 遵循官方文档：https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call
 * - 兼容 OpenAI API 格式
 * - BASE_URL: https://api.xiaomimimo.com/v1
 * - 默认模型: mimo-v2.5-pro
 * - 认证: Authorization: Bearer {apiKey} 或 api-key: {apiKey}
 */
class MiMoAiClient(
    private val apiKey: String
) : AiClient {

    companion object {
        private const val BASE_URL = "https://api.xiaomimimo.com/v1"
        private const val DEFAULT_MODEL = "mimo-v2.5-pro"
        private val PREDEFINED_MODELS = listOf(
            "mimo-v2.5-pro",
            "mimo-v2.5-turbo",
            "mimo-v2.5-lite"
        )
    }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7,
        val max_completion_tokens: Int? = null,
        val top_p: Double? = null,
        val stream: Boolean = false
    )

    @Serializable
    private data class ChatChoice(val message: ChatMessage)

    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)

    @Serializable
    private data class ModelItem(val id: String)

    @Serializable
    private data class ModelsResponse(val data: List<ModelItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val resolvedModel = model.ifBlank { DEFAULT_MODEL }
            val messagesList = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messagesList.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messagesList.add(ChatMessage(role = "user", content = prompt))

            val requestBody = ChatRequest(
                model = resolvedModel,
                messages = messagesList,
                temperature = temperature.toDouble(),
                max_completion_tokens = 4096,
                top_p = 0.95
            )

            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()

                    if (!response.isSuccessful) {
                        throw AiProviderSupport.createException(
                            providerName = "Xiaomi MiMo",
                            statusCode = response.code,
                            transportMessage = response.message,
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                    }

                    val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                    chatResponse.choices.firstOrNull()?.message?.content
                        ?: throw AiProviderSupport.createException(
                            providerName = "Xiaomi MiMo",
                            statusCode = response.code,
                            transportMessage = "Response had no content",
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                }
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable("Xiaomi MiMo", e, resolvedModel)
            }
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return (systemPrompt.length + prompt.length) / 4
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("api-key", apiKey)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext PREDEFINED_MODELS
                }

                val responseBody = response.body.string()
                val modelsResponse = json.decodeFromString<ModelsResponse>(responseBody)
                modelsResponse.data.map { it.id }.filter {
                    !it.contains("whisper") && !it.contains("embed") && !it.contains("tts")
                }.ifEmpty { PREDEFINED_MODELS }
            } catch (e: Exception) {
                PREDEFINED_MODELS
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("api-key", apiKey)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun getDefaultModel(): String = DEFAULT_MODEL
}
