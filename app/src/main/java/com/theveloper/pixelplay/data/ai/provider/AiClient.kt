package com.theveloper.pixelplay.data.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 流式输出中的一个增量片段。
 * [isThinking] 为 true 表示这是模型的思考/推理内容（Gemini 的 thought part、
 * OpenAI 兼容接口的 reasoning_content），不应混入正文；false 表示正式回答文本。
 */
data class AiStreamChunk(
    val text: String,
    val isThinking: Boolean = false
)

/**
 * Abstract interface for AI providers
 * Defines common operations for text generation and metadata completion
 */
interface AiClient {

    suspend fun generateContent(
        model: String, 
        systemPrompt: String, 
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        topK: Int = 64,
        maxTokens: Int = 4096,
        presencePenalty: Float = 0.0f,
        frequencyPenalty: Float = 0.0f
    ): String

    /**
     * Stream text generation as a cold [Flow] of incremental chunks.
     * Providers that support SSE streaming should override this; otherwise a
     * non-stream fallback emits the whole result at once.
     */
    fun generateContentStream(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        topK: Int = 64,
        maxTokens: Int = 4096,
        presencePenalty: Float = 0.0f,
        frequencyPenalty: Float = 0.0f
    ): Flow<String>
    
    /**
     * 带思考/推理内容区分的流式生成。返回的每个 [AiStreamChunk] 标注该增量是
     * 模型思考（[AiStreamChunk.isThinking] = true）还是正式回答文本。
     *
     * 默认实现直接把 [generateContentStream] 的文本流映射为正文 chunk，
     * 不支持暴露思考内容的 provider 可沿用默认实现。
     */
    fun generateContentStreamWithReasoning(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        topK: Int = 64,
        maxTokens: Int = 4096,
        presencePenalty: Float = 0.0f,
        frequencyPenalty: Float = 0.0f
    ): Flow<AiStreamChunk> = generateContentStream(
        model = model,
        systemPrompt = systemPrompt,
        prompt = prompt,
        temperature = temperature,
        topP = topP,
        topK = topK,
        maxTokens = maxTokens,
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty
    ).map { AiStreamChunk(it) }
    
    /**
     * Estimate or count tokens for a given prompt
     */
    suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int
    
    /**
     * Get list of available models for this provider
     */
    suspend fun getAvailableModels(apiKey: String): List<String>
    
    /**
     * Validate the API key
     */
    suspend fun validateApiKey(apiKey: String): Boolean
    
    /**
     * Get the default model for this provider
     */
    fun getDefaultModel(): String
}
