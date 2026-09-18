package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.autoeq.AutoEQManager
import com.theveloper.pixelplay.data.autoeq.AutoEQProfile
import com.theveloper.pixelplay.data.autoeq.UserAudioDevice
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel for the AutoEQ feature (ported from Rhythm).
 * Loads AutoEQ profiles from assets, applies them to the system equalizer
 * and manages user audio devices with their bound AutoEQ profiles.
 */
@HiltViewModel
class AutoEqViewModel @Inject constructor(
    private val autoEQManager: AutoEQManager,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val equalizerManager: EqualizerManager,
    private val dualPlayerEngine: DualPlayerEngine,
    private val connectivityStateHolder: ConnectivityStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AutoEqViewModel"
    }

    private val _autoEQProfiles = MutableStateFlow<List<AutoEQProfile>>(emptyList())
    val autoEQProfiles: StateFlow<List<AutoEQProfile>> = _autoEQProfiles.asStateFlow()

    private val _autoEQLoading = MutableStateFlow(false)
    val autoEQLoading: StateFlow<Boolean> = _autoEQLoading.asStateFlow()

    private val _autoEQProfile = MutableStateFlow("")
    val autoEQProfile: StateFlow<String> = _autoEQProfile.asStateFlow()

    private val _userAudioDevices = MutableStateFlow<List<UserAudioDevice>>(emptyList())
    val userAudioDevices: StateFlow<List<UserAudioDevice>> = _userAudioDevices.asStateFlow()

    private val _activeAudioDeviceId = MutableStateFlow<String?>(null)
    val activeAudioDeviceId: StateFlow<String?> = _activeAudioDeviceId.asStateFlow()

    private val _dismissedAutoEQSuggestions = MutableStateFlow<String?>(null)
    val dismissedAutoEQSuggestions: StateFlow<String?> = _dismissedAutoEQSuggestions.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _currentBandLevels = MutableStateFlow<List<Float>>(emptyList())
    val currentBandLevels: StateFlow<List<Float>> = _currentBandLevels.asStateFlow()

    /** Currently connected Bluetooth audio device name (if any) */
    val connectedDeviceName: StateFlow<String?> = connectivityStateHolder.bluetoothName

    init {
        viewModelScope.launch {
            equalizerPreferencesRepository.autoEQProfileFlow.collectLatest { _autoEQProfile.value = it }
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.userAudioDevicesFlow.collectLatest { json ->
                _userAudioDevices.value = UserAudioDevice.fromJson(json)
            }
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.activeAudioDeviceIdFlow.collectLatest { _activeAudioDeviceId.value = it }
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.dismissedAutoEQSuggestionsFlow.collectLatest { _dismissedAutoEQSuggestions.value = it }
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.equalizerEnabledFlow.collectLatest { _equalizerEnabled.value = it }
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.equalizerCustomBandsFlow.collectLatest { bands ->
                _currentBandLevels.value = bands.map { it.toFloat() }
            }
        }
        loadAutoEQProfiles()
    }

    fun loadAutoEQProfiles() {
        viewModelScope.launch {
            _autoEQLoading.value = true
            try {
                val result = autoEQManager.loadProfiles()
                if (result.isSuccess) {
                    val profiles = result.getOrNull()?.profiles ?: emptyList()
                    _autoEQProfiles.value = profiles
                    Timber.d("$TAG: Loaded ${profiles.size} AutoEQ profiles")
                } else {
                    Timber.e(result.exceptionOrNull(), "$TAG: Failed to load AutoEQ profiles")
                }
            } finally {
                _autoEQLoading.value = false
            }
        }
    }

    fun searchAutoEQProfiles(query: String): List<AutoEQProfile> {
        return autoEQManager.searchProfiles(query)
    }

    fun getAutoEQRecommendedProfiles(): List<AutoEQProfile> {
        return autoEQManager.getRecommendedProfiles()
    }

    fun applyAutoEQProfile(profile: AutoEQProfile) {
        viewModelScope.launch {
            try {
                if (profile.name.isBlank() || profile.name.equals("None", ignoreCase = true)) {
                    Timber.d("$TAG: Disabling AutoEQ profile")
                    equalizerPreferencesRepository.setAutoEQProfile("")
                    equalizerManager.applyPreset(EqualizerPreset.fromName("flat"))
                    equalizerPreferencesRepository.setEqualizerPreset("flat")
                    return@launch
                }

                Timber.d("$TAG: Applying AutoEQ profile: ${profile.name}")

                // Ensure we have 10 bands
                val levels = profile.bands.take(10)
                if (levels.size != 10) {
                    Timber.w("$TAG: AutoEQ profile has ${levels.size} bands, expected 10")
                    return@launch
                }

                // Save profile name to settings
                equalizerPreferencesRepository.setAutoEQProfile(profile.name)

                // Enable equalizer first if it's not already enabled
                val wasEnabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
                if (!wasEnabled) {
                    Timber.d("$TAG: Enabling equalizer to apply AutoEQ profile")
                    equalizerPreferencesRepository.setEqualizerEnabled(true)
                    equalizerManager.setEnabled(true)
                    // Wait for the equalizer to initialize
                    delay(300)
                }

                val audioSessionId = dualPlayerEngine.getAudioSessionId()
                if (audioSessionId != 0) {
                    equalizerManager.attachToAudioSession(audioSessionId)
                }

                // Apply the profile as a custom preset with the 10-band levels
                val systemBandLevels = levels.map { it.roundToInt().coerceIn(-15, 15) }
                val eqPreset = EqualizerPreset(
                    name = "autoeq_${profile.name}",
                    displayName = "AutoEQ: ${profile.name}",
                    bandLevels = systemBandLevels,
                    isCustom = true
                )
                equalizerManager.applyPreset(eqPreset)
                equalizerPreferencesRepository.saveCustomPresetAndSelect(eqPreset)
                Timber.d("$TAG: AutoEQ profile applied successfully")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to apply AutoEQ profile")
            }
        }
    }

    // User Audio Device Management

    fun saveUserAudioDevice(device: UserAudioDevice) {
        viewModelScope.launch {
            val currentDevices = _userAudioDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.id == device.id }
            if (existingIndex >= 0) {
                currentDevices[existingIndex] = device
                Timber.d("$TAG: Updated audio device: ${device.name}")
            } else {
                currentDevices.add(device)
                Timber.d("$TAG: Added new audio device: ${device.name}")
            }
            equalizerPreferencesRepository.setUserAudioDevices(UserAudioDevice.toJson(currentDevices))
        }
    }

    fun deleteUserAudioDevice(deviceId: String) {
        viewModelScope.launch {
            val currentDevices = _userAudioDevices.value.toMutableList()
            currentDevices.removeAll { it.id == deviceId }
            equalizerPreferencesRepository.setUserAudioDevices(UserAudioDevice.toJson(currentDevices))

            // If deleted device was active, clear active device
            if (_activeAudioDeviceId.value == deviceId) {
                equalizerPreferencesRepository.setActiveAudioDeviceId(null)
            }
            Timber.d("$TAG: Deleted audio device: $deviceId")
        }
    }

    fun setActiveAudioDevice(device: UserAudioDevice) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setActiveAudioDeviceId(device.id)
            Timber.d("$TAG: Set active audio device: ${device.name}")

            // Apply AutoEQ profile if the device has one configured
            if (device.autoEQProfileName != null) {
                val profile = autoEQManager.findProfileByName(device.autoEQProfileName)
                if (profile != null) {
                    applyAutoEQProfile(profile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.autoeq_profile_applied, device.autoEQProfileName),
                        Toast.LENGTH_SHORT
                    ).show()
                    Timber.d("$TAG: Applied AutoEQ profile for device: ${device.autoEQProfileName}")
                }
            }
        }
    }

    fun getActiveAudioDevice(): UserAudioDevice? {
        val activeId = _activeAudioDeviceId.value ?: return null
        return _userAudioDevices.value.find { it.id == activeId }
    }

    /**
     * Try to match a connected audio device (from playback location) with saved UserAudioDevice.
     * Uses fuzzy matching to account for slight name variations.
     */
    fun findMatchingUserDevice(deviceName: String): UserAudioDevice? {
        val devices = _userAudioDevices.value
        if (devices.isEmpty()) return null

        val normalizedSearchName = deviceName.lowercase().trim()

        // First try exact match
        devices.find { it.name.equals(deviceName, ignoreCase = true) }?.let { return it }

        // Try fuzzy matching - device name contains saved name or vice versa
        devices.find { savedDevice ->
            val normalizedSavedName = savedDevice.name.lowercase().trim()
            normalizedSearchName.contains(normalizedSavedName) ||
                normalizedSavedName.contains(normalizedSearchName)
        }?.let { return it }

        // Try matching by brand if available
        devices.find { savedDevice ->
            savedDevice.brand.isNotEmpty() && normalizedSearchName.contains(savedDevice.brand.lowercase())
        }?.let { return it }

        return null
    }

    /**
     * Check if device detection should show AutoEQ suggestion.
     * Returns true if device hasn't been dismissed before.
     */
    fun shouldShowAutoEQSuggestion(deviceId: String): Boolean {
        val dismissedDevices = _dismissedAutoEQSuggestions.value?.split(",") ?: emptyList()
        return !dismissedDevices.contains(deviceId)
    }

    /**
     * Mark a device as "don't ask again" for AutoEQ suggestions.
     */
    fun dismissAutoEQSuggestion(deviceId: String) {
        viewModelScope.launch {
            val current = _dismissedAutoEQSuggestions.value ?: ""
            val dismissedList = if (current.isEmpty()) {
                listOf(deviceId)
            } else {
                current.split(",").toMutableList().apply { add(deviceId) }
            }
            equalizerPreferencesRepository.setDismissedAutoEQSuggestions(dismissedList.joinToString(","))
            Timber.d("$TAG: Dismissed AutoEQ suggestion for device: $deviceId")
        }
    }
}
