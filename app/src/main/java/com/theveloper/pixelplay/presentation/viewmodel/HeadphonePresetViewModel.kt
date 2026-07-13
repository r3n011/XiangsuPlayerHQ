package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.BluetoothPresetBindingEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetWithBands
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.repository.AutoEqDataImporter
import com.theveloper.pixelplay.data.repository.HeadphonePresetRepository
import com.theveloper.pixelplay.data.service.audioengine.AudioProcessorProvider
import com.theveloper.pixelplay.data.service.audioengine.BluetoothPresetAutoSwitcher
import com.theveloper.pixelplay.data.service.audioengine.EQBand
import com.theveloper.pixelplay.data.service.audioengine.EQType
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HeadphonePresetViewModel @Inject constructor(
    private val headphonePresetRepository: HeadphonePresetRepository,
    private val autoEqDataImporter: AutoEqDataImporter,
    private val audioProcessorProvider: AudioProcessorProvider,
    private val bluetoothPresetAutoSwitcher: BluetoothPresetAutoSwitcher,
    private val equalizerManager: com.theveloper.pixelplay.data.equalizer.EqualizerManager,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _presets = MutableStateFlow<List<HeadphonePresetEntity>>(emptyList())
    val presets: StateFlow<List<HeadphonePresetEntity>> = _presets.asStateFlow()

    private val _brands = MutableStateFlow<List<String>>(emptyList())
    val brands: StateFlow<List<String>> = _brands.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedPreset = MutableStateFlow<HeadphonePresetWithBands?>(null)
    val selectedPreset: StateFlow<HeadphonePresetWithBands?> = _selectedPreset.asStateFlow()

    private val _bindings = MutableStateFlow<List<BluetoothPresetBindingEntity>>(emptyList())
    val bindings: StateFlow<List<BluetoothPresetBindingEntity>> = _bindings.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedBrand = MutableStateFlow<String?>(null)
    val selectedBrand: StateFlow<String?> = _selectedBrand.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _activePreset = MutableStateFlow<HeadphonePresetEntity?>(null)
    val activePreset: StateFlow<HeadphonePresetEntity?> = _activePreset.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            autoEqDataImporter.importIfNeeded()
        }

        viewModelScope.launch {
            headphonePresetRepository.getAllBrands().collectLatest { _brands.value = it }
        }

        viewModelScope.launch {
            headphonePresetRepository.getAllCategories().collectLatest { _categories.value = it }
        }

        viewModelScope.launch {
            headphonePresetRepository.getAllBindings().collectLatest { _bindings.value = it }
        }

        updatePresets()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updatePresets()
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
        _selectedBrand.value = null
        updatePresets()
    }

    fun setSelectedBrand(brand: String?) {
        _selectedBrand.value = brand
        _selectedCategory.value = null
        updatePresets()
    }

    fun selectPreset(presetId: Long) {
        viewModelScope.launch {
            headphonePresetRepository.getPresetWithBands(presetId).collectLatest {
                _selectedPreset.value = it
            }
        }
    }

    fun getPresetWithBands(presetId: Long): HeadphonePresetWithBands? {
        return _selectedPreset.value?.takeIf { it.preset.id == presetId }
    }

    fun refreshPresets() {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                autoEqDataImporter.forceImport()
                updatePresets()
                Toast.makeText(context, "耳机预设库已刷新", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "HeadphonePresetViewModel: Failed to refresh presets")
                Toast.makeText(context, "刷新失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun applyPreset(preset: HeadphonePresetWithBands) {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                val bands = preset.bands.map { entity ->
                    val type = when (entity.filterType) {
                        "BELL" -> EQType.BELL
                        "LOW_SHELF" -> EQType.LOW_SHELF
                        "HIGH_SHELF" -> EQType.HIGH_SHELF
                        else -> EQType.BELL
                    }
                    EQBand(
                        frequency = entity.frequency,
                        gain = entity.gain,
                        q = entity.q,
                        type = type
                    )
                }

                audioProcessorProvider.getProcessor()?.apply {
                    getParametricEQ().setEnabled(true)
                    getParametricEQ().setBands(bands)
                    getReplayGainProcessor().setPreamp(preset.preset.preamp)
                }

                val audioSessionId = dualPlayerEngine.getAudioSessionId()
                Timber.d("HeadphonePresetViewModel: audioSessionId = $audioSessionId")

                if (audioSessionId != 0) {
                    equalizerManager.setEnabled(true)
                    equalizerManager.attachToAudioSession(audioSessionId)
                }

                val systemBandLevels = mapPresetToSystemEqualizer(preset)
                Timber.d("HeadphonePresetViewModel: mapped band levels = $systemBandLevels")

                val eqPreset = EqualizerPreset(
                    name = "headphone_preset_${preset.preset.id}",
                    displayName = preset.preset.name,
                    bandLevels = systemBandLevels,
                    isCustom = true
                )

                equalizerManager.applyPreset(eqPreset)

                equalizerPreferencesRepository.saveCustomPreset(eqPreset)

                _activePreset.value = preset.preset

                Toast.makeText(context, "已应用耳机预设: ${preset.preset.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "HeadphonePresetViewModel: Failed to apply preset")
                Toast.makeText(context, "应用预设失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun clearPreset() {
        viewModelScope.launch {
            try {
                audioProcessorProvider.getProcessor()?.apply {
                    getParametricEQ().setEnabled(false)
                    getParametricEQ().setBands(emptyList())
                    getReplayGainProcessor().setPreamp(0.0f)
                }

                equalizerManager.applyPreset(EqualizerPreset.fromName("flat"))
                equalizerManager.setEnabled(false)

                _activePreset.value = null

                Toast.makeText(context, "已取消耳机预设", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "HeadphonePresetViewModel: Failed to clear preset")
                Toast.makeText(context, "取消预设失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mapPresetToSystemEqualizer(preset: HeadphonePresetWithBands): List<Int> {
        val systemFrequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        val systemBandLevels = MutableList(10) { 0 }

        for (band in preset.bands) {
            val bandFreq = band.frequency
            var closestIndex = 0
            var minDiff = Double.MAX_VALUE

            for ((index, freq) in systemFrequencies.withIndex()) {
                val diff = kotlin.math.abs(bandFreq - freq)
                if (diff < minDiff) {
                    minDiff = diff
                    closestIndex = index
                }
            }

            val gain = kotlin.math.round(band.gain).toInt().coerceIn(-15, 15)
            systemBandLevels[closestIndex] = gain
        }

        return systemBandLevels
    }

    fun bindPresetToDevice(deviceName: String, deviceAddress: String?, presetId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            headphonePresetRepository.bindPresetToDevice(deviceName, deviceAddress, presetId)
        }
    }

    fun unbindDevice(bindingId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            headphonePresetRepository.unbindDevice(bindingId)
        }
    }

    fun triggerBluetoothSwitch(deviceName: String, deviceAddress: String?) {
        bluetoothPresetAutoSwitcher.onBluetoothDeviceConnected(deviceName, deviceAddress)
    }

    private fun updatePresets() {
        viewModelScope.launch {
            val query = _searchQuery.value
            val category = _selectedCategory.value
            val brand = _selectedBrand.value

            when {
                query.isNotBlank() -> {
                    headphonePresetRepository.searchPresets(query).collectLatest { _presets.value = it }
                }
                category != null -> {
                    headphonePresetRepository.getPresetsByCategory(category).collectLatest { _presets.value = it }
                }
                brand != null -> {
                    headphonePresetRepository.getPresetsByBrand(brand).collectLatest { _presets.value = it }
                }
                else -> {
                    headphonePresetRepository.getAllPresets().collectLatest { _presets.value = it }
                }
            }
        }
    }
}