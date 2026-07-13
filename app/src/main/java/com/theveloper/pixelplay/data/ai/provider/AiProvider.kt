package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(val displayName: String, val requiresApiKey: Boolean) {
    MIMO("Xiaomi MiMo", requiresApiKey = true);

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: MIMO
        }
    }
}
