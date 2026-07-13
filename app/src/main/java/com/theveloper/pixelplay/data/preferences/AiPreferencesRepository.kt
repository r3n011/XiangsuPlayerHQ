package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

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

        val DEFAULT_MIMO_SYSTEM_PROMPT = DEFAULT_SYSTEM_PROMPT
    }

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val SAFE_TOKEN_LIMIT = booleanPreferencesKey("safe_token_limit")

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
    }

    fun getApiKey(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getApiKey(provider)]?.trim() ?: "" }

    fun getModel(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.getModel(provider)] ?: "" }

    fun getSystemPrompt(provider: AiProvider): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[Keys.getSystemPrompt(provider)] ?: DEFAULT_SYSTEM_PROMPT
        }

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

    val mimoApiKey: Flow<String> = getApiKey(AiProvider.MIMO)
    val mimoModel: Flow<String> = getModel(AiProvider.MIMO)
    val mimoSystemPrompt: Flow<String> = getSystemPrompt(AiProvider.MIMO)

    val aiProvider: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.AI_PROVIDER] ?: "MIMO" }

    val isSafeTokenLimitEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SAFE_TOKEN_LIMIT] ?: true }

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
