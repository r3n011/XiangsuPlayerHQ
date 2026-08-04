package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 偏好设置仓库（多 Provider）
 *
 * 以原版（XiangsuPlayer-masteryuanban）的多 Provider 实现为基础，
 * 同时保留当前项目附加的自动化开关（AI 推荐卡片 / 自动歌单 / Web Remote 等）。
 */
@Singleton
class AiPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are 'Vibe-Engine', a professional music curator.
            Analyze the user's request and listening profile to provide perfect music recommendations.
            Always prioritize flow, emotional resonance, and discovery.
        """.trimIndent()

        val DEFAULT_DEEPSEEK_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GROQ_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_MISTRAL_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_NVIDIA_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_KIMI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_GLM_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENAI_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
        val DEFAULT_OPENROUTER_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
    }

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val AI_TOP_P = floatPreferencesKey("ai_top_p")
        val AI_TOP_K = intPreferencesKey("ai_top_k")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_PRESENCE_PENALTY = floatPreferencesKey("ai_presence_penalty")
        val AI_FREQUENCY_PENALTY = floatPreferencesKey("ai_frequency_penalty")
        val AI_SAMPLE_SIZE = intPreferencesKey("ai_sample_size")
        val AI_DIGEST_MODE = stringPreferencesKey("ai_digest_mode")
        val AI_INCLUDE_EXTENDED_FIELDS = booleanPreferencesKey("ai_include_extended_fields")

        // 当前项目附加字段
        val AI_AUTO_PLAYLIST = booleanPreferencesKey("ai_auto_playlist")
        val AI_AUTO_METADATA = booleanPreferencesKey("ai_auto_metadata")
        val AI_AUTO_DAILY_MIX = booleanPreferencesKey("ai_auto_daily_mix")
        val AI_AUTO_PLAYLIST_EVALUATION = booleanPreferencesKey("ai_auto_playlist_evaluation")
        val AI_RECOMMENDATION_CARD_ENABLED = booleanPreferencesKey("ai_recommendation_card_enabled")
        val AI_RECOMMENDATION_MANUAL_ONLY = booleanPreferencesKey("ai_recommendation_manual_only")

        val WEB_REMOTE_ENABLED = booleanPreferencesKey("web_remote_enabled")
        val WEB_REMOTE_SYNC_MODE = booleanPreferencesKey("web_remote_sync_mode")
        val WEB_REMOTE_PORT = intPreferencesKey("web_remote_port")
        val WEB_REMOTE_AUDIO_ON_DEVICE = booleanPreferencesKey("web_remote_audio_on_device")
        val WEB_REMOTE_THEME_COLOR = stringPreferencesKey("web_remote_theme_color")

        fun getApiKey(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_api_key")
        fun getModel(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_model")
        fun getSystemPrompt(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_system_prompt")
        fun getBaseUrl(provider: AiProvider) = stringPreferencesKey("${provider.name.lowercase()}_base_url")
    }

    // Generic accessors for AiHandler
    fun getApiKey(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getApiKey(provider)]?.trim() ?: "" }

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

    fun getBaseUrl(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getBaseUrl(provider)] ?: "" }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        dataStore.edit { preferences -> preferences[Keys.getApiKey(provider)] = apiKey.trim() }
    }

    suspend fun setModel(provider: AiProvider, model: String) {
        dataStore.edit { preferences -> preferences[Keys.getModel(provider)] = model }
    }

    suspend fun setSystemPrompt(provider: AiProvider, prompt: String) {
        dataStore.edit { preferences -> preferences[Keys.getSystemPrompt(provider)] = prompt }
    }

    suspend fun resetSystemPrompt(provider: AiProvider) {
        dataStore.edit { preferences ->
            preferences[Keys.getSystemPrompt(provider)] = DEFAULT_SYSTEM_PROMPT
        }
    }

    suspend fun setBaseUrl(provider: AiProvider, url: String) {
        dataStore.edit { preferences -> preferences[Keys.getBaseUrl(provider)] = url.trim() }
    }

    // Convenience properties for legacy compatibility (e.g. PlayerViewModel)
    val geminiApiKey: Flow<String> = getApiKey(AiProvider.GEMINI)
    val geminiModel: Flow<String> = getModel(AiProvider.GEMINI)
    val geminiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GEMINI)

    val deepseekApiKey: Flow<String> = getApiKey(AiProvider.DEEPSEEK)
    val deepseekModel: Flow<String> = getModel(AiProvider.DEEPSEEK)
    val deepseekSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.DEEPSEEK)

    val groqApiKey: Flow<String> = getApiKey(AiProvider.GROQ)
    val groqModel: Flow<String> = getModel(AiProvider.GROQ)
    val groqSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GROQ)

    val mistralApiKey: Flow<String> = getApiKey(AiProvider.MISTRAL)
    val mistralModel: Flow<String> = getModel(AiProvider.MISTRAL)
    val mistralSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.MISTRAL)

    val nvidiaApiKey: Flow<String> = getApiKey(AiProvider.NVIDIA)
    val nvidiaModel: Flow<String> = getModel(AiProvider.NVIDIA)
    val nvidiaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.NVIDIA)

    val kimiApiKey: Flow<String> = getApiKey(AiProvider.KIMI)
    val kimiModel: Flow<String> = getModel(AiProvider.KIMI)
    val kimiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.KIMI)

    val glmApiKey: Flow<String> = getApiKey(AiProvider.GLM)
    val glmModel: Flow<String> = getModel(AiProvider.GLM)
    val glmSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.GLM)

    val openaiApiKey: Flow<String> = getApiKey(AiProvider.OPENAI)
    val openaiModel: Flow<String> = getModel(AiProvider.OPENAI)
    val openaiSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENAI)

    val openrouterApiKey: Flow<String> = getApiKey(AiProvider.OPENROUTER)
    val openrouterModel: Flow<String> = getModel(AiProvider.OPENROUTER)
    val openrouterSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OPENROUTER)

    val ollamaApiKey: Flow<String> = getApiKey(AiProvider.OLLAMA)
    val ollamaModel: Flow<String> = getModel(AiProvider.OLLAMA)
    val ollamaSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.OLLAMA)

    val customApiKey: Flow<String> = getApiKey(AiProvider.CUSTOM)
    val customModel: Flow<String> = getModel(AiProvider.CUSTOM)
    val customSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.CUSTOM)
    val customBaseUrl: Flow<String> = getBaseUrl(AiProvider.CUSTOM)

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "GEMINI" }

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

    val aiTemperature: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TEMPERATURE] ?: 0.7f }

    val aiTopP: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_P] ?: 0.95f }

    val aiTopK: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_TOP_K] ?: 64 }

    val aiMaxTokens: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_MAX_TOKENS] ?: 4096 }

    val aiPresencePenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] ?: 0.0f }

    val aiFrequencyPenalty: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] ?: 0.0f }

    val aiSampleSize: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AI_SAMPLE_SIZE] ?: 40 }

    val aiDigestMode: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_DIGEST_MODE] ?: "safe" }

    val aiIncludeExtendedFields: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] ?: false }

    // 当前项目附加字段
    val isAutoPlaylistEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_AUTO_PLAYLIST] ?: true }

    val isAutoMetadataEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_AUTO_METADATA] ?: true }

    val isAutoDailyMixEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_AUTO_DAILY_MIX] ?: true }

    val isAutoPlaylistEvaluationEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_AUTO_PLAYLIST_EVALUATION] ?: false }

    val isAiRecommendationCardEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_RECOMMENDATION_CARD_ENABLED] ?: false }

    val isAiRecommendationManualOnly: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.AI_RECOMMENDATION_MANUAL_ONLY] ?: true }

    val isWebRemoteEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.WEB_REMOTE_ENABLED] ?: false }

    val isWebRemoteSyncMode: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.WEB_REMOTE_SYNC_MODE] ?: true }

    val isWebRemoteAudioOnDevice: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.WEB_REMOTE_AUDIO_ON_DEVICE] ?: true }

    val webRemotePort: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.WEB_REMOTE_PORT] ?: 8081 }

    val webRemoteThemeColor: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.WEB_REMOTE_THEME_COLOR] ?: "#6750A4" }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_PROVIDER] = provider }
    }

    suspend fun setSafeTokenLimitEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] = enabled }
    }

    suspend fun setAiTemperature(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TEMPERATURE] = value }
    }

    suspend fun setAiTopP(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_P] = value }
    }

    suspend fun setAiTopK(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_TOP_K] = value }
    }

    suspend fun setAiMaxTokens(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_MAX_TOKENS] = value }
    }

    suspend fun setAiPresencePenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_PRESENCE_PENALTY] = value }
    }

    suspend fun setAiFrequencyPenalty(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.AI_FREQUENCY_PENALTY] = value }
    }

    suspend fun setAiSampleSize(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AI_SAMPLE_SIZE] = value }
    }

    suspend fun setAiDigestMode(mode: String) {
        dataStore.edit { preferences -> preferences[Keys.AI_DIGEST_MODE] = mode }
    }

    suspend fun setAiIncludeExtendedFields(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_INCLUDE_EXTENDED_FIELDS] = enabled }
    }

    // 当前项目附加字段 setters
    suspend fun setAutoPlaylistEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_AUTO_PLAYLIST] = enabled }
    }

    suspend fun setAutoMetadataEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_AUTO_METADATA] = enabled }
    }

    suspend fun setAutoDailyMixEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_AUTO_DAILY_MIX] = enabled }
    }

    suspend fun setAutoPlaylistEvaluationEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_AUTO_PLAYLIST_EVALUATION] = enabled }
    }

    suspend fun setAiRecommendationCardEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_RECOMMENDATION_CARD_ENABLED] = enabled }
    }

    suspend fun setAiRecommendationManualOnly(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AI_RECOMMENDATION_MANUAL_ONLY] = enabled }
    }

    suspend fun setWebRemoteEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.WEB_REMOTE_ENABLED] = enabled }
    }

    suspend fun setWebRemoteSyncMode(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.WEB_REMOTE_SYNC_MODE] = enabled }
    }

    suspend fun setWebRemoteAudioOnDevice(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.WEB_REMOTE_AUDIO_ON_DEVICE] = enabled }
    }

    suspend fun setWebRemotePort(port: Int) {
        dataStore.edit { preferences -> preferences[Keys.WEB_REMOTE_PORT] = port }
    }

    suspend fun setWebRemoteThemeColor(color: String) {
        dataStore.edit { preferences -> preferences[Keys.WEB_REMOTE_THEME_COLOR] = color }
    }
}
