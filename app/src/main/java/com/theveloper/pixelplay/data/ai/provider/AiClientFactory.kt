package com.theveloper.pixelplay.data.ai.provider

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiClientFactory @Inject constructor() {
    
    fun createClient(provider: AiProvider, apiKey: String): AiClient {
        return createClient(provider, apiKey, "", "")
    }
    
    fun createClient(
        provider: AiProvider,
        apiKey: String,
        customBaseUrl: String = "",
        customModel: String = ""
    ): AiClient {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key cannot be blank for ${provider.displayName}")
        }
        
        return when (provider) {
            AiProvider.MIMO -> MiMoAiClient(apiKey = apiKey)
        }
    }
}
