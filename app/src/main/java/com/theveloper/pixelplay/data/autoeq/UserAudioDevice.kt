package com.theveloper.pixelplay.data.autoeq

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a user's audio device with associated AutoEQ profile
 */
@Immutable
data class UserAudioDevice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: DeviceType = DeviceType.HEADPHONES,
    val brand: String = "",
    val autoEQProfileName: String? = null, // Reference to AutoEQ profile
    val customBandLevels: List<Float>? = null, // Custom EQ if not using AutoEQ
    val monoAudioEnabled: Boolean = false, // Single earpiece mono audio downmix
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class DeviceType(val displayName: String, val icon: String) {
        HEADPHONES("Headphones", "headphones"),
        EARBUDS("Earbuds", "earbuds"),
        IEM("IEMs", "earbuds"),
        SPEAKERS("Speakers", "speaker"),
        BLUETOOTH_SPEAKER("Bluetooth Speaker", "speaker_bluetooth"),
        CAR_AUDIO("Car Audio", "directions_car"),
        STUDIO_MONITORS("Studio Monitors", "speaker"),
        OTHER("Other", "audio")
    }

    companion object {
        private val gson = Gson()

        /**
         * Serialize a list of devices to JSON string
         */
        fun toJson(devices: List<UserAudioDevice>): String {
            return gson.toJson(devices)
        }

        /**
         * Deserialize JSON string to a list of devices
         */
        fun fromJson(json: String?): List<UserAudioDevice> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<UserAudioDevice>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
