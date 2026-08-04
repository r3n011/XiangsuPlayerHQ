package com.theveloper.pixelplay.data.service.audioengine

import com.theveloper.pixelplay.data.service.usb.UsbDacManager
import com.theveloper.pixelplay.data.service.usb.UsbDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class UsbOutputBitDepth(val bits: Int, val bytesPerSample: Int) {
    BITS_16(16, 2),
    BITS_24(24, 3),
    BITS_32(32, 4);

    companion object {
        fun fromBits(bits: Int): UsbOutputBitDepth = when (bits) {
            24 -> BITS_24
            32 -> BITS_32
            else -> BITS_16
        }
    }
}

@Singleton
class AudioEngineSettings @Inject constructor(
    private val usbDacManager: UsbDacManager
) {
    private val _replayGainEnabled = MutableStateFlow(false)
    private val _replayGainUseAlbumGain = MutableStateFlow(false)
    private val _replayGainPreamp = MutableStateFlow(0.0f)

    private val _eqEnabled = MutableStateFlow(false)
    private val _eqBands = MutableStateFlow<List<EQBand>>(emptyList())

    private val _crossfeedEnabled = MutableStateFlow(false)
    private val _crossfeedMode = MutableStateFlow(CrossfeedMode.BS2B)
    private val _crossfeedLevel = MutableStateFlow(0.3f)
    private val _crossfeedDelay = MutableStateFlow(2.0f)
    private val _crossfeedLowpassFreq = MutableStateFlow(700.0f)

    private val _limiterEnabled = MutableStateFlow(true)
    private val _limiterThreshold = MutableStateFlow(-0.1f)
    private val _limiterReleaseTime = MutableStateFlow(0.05f)
    private val _limiterLookahead = MutableStateFlow(5.0f)

    private val _convolverEnabled = MutableStateFlow(false)

    private val _usbExclusiveModeEnabled = MutableStateFlow(false)
    private val _currentUsbDeviceName = MutableStateFlow<String?>(null)
    private val _selectedUsbDevice = MutableStateFlow<UsbDeviceInfo?>(null)

    // AAudio 低延迟后端：Android O 及以上 Media3 DefaultAudioSink 默认优先使用 AAudio，
    // 此开关用于向 UI 暴露当前状态，并在必要时退回到 AudioTrack 兼容路径。
    private val _aaudioEnabled = MutableStateFlow(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)

    // USB 独占输出位深：16/24/32-bit
    private val _usbOutputBitDepth = MutableStateFlow(UsbOutputBitDepth.BITS_32)

    val replayGainEnabled: StateFlow<Boolean> = _replayGainEnabled.asStateFlow()
    val replayGainUseAlbumGain: StateFlow<Boolean> = _replayGainUseAlbumGain.asStateFlow()
    val replayGainPreamp: StateFlow<Float> = _replayGainPreamp.asStateFlow()

    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    val eqBands: StateFlow<List<EQBand>> = _eqBands.asStateFlow()

    val crossfeedEnabled: StateFlow<Boolean> = _crossfeedEnabled.asStateFlow()
    val crossfeedMode: StateFlow<CrossfeedMode> = _crossfeedMode.asStateFlow()
    val crossfeedLevel: StateFlow<Float> = _crossfeedLevel.asStateFlow()
    val crossfeedDelay: StateFlow<Float> = _crossfeedDelay.asStateFlow()
    val crossfeedLowpassFreq: StateFlow<Float> = _crossfeedLowpassFreq.asStateFlow()

    val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()
    val limiterThreshold: StateFlow<Float> = _limiterThreshold.asStateFlow()
    val limiterReleaseTime: StateFlow<Float> = _limiterReleaseTime.asStateFlow()
    val limiterLookahead: StateFlow<Float> = _limiterLookahead.asStateFlow()

    val convolverEnabled: StateFlow<Boolean> = _convolverEnabled.asStateFlow()

    val usbExclusiveModeEnabled: StateFlow<Boolean> = _usbExclusiveModeEnabled.asStateFlow()
    val currentUsbDeviceName: StateFlow<String?> = _currentUsbDeviceName.asStateFlow()
    val aaudioEnabled: StateFlow<Boolean> = _aaudioEnabled.asStateFlow()
    val usbOutputBitDepth: StateFlow<UsbOutputBitDepth> = _usbOutputBitDepth.asStateFlow()

    fun setReplayGainEnabled(enabled: Boolean) {
        _replayGainEnabled.value = enabled
    }

    fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        _replayGainUseAlbumGain.value = useAlbumGain
    }

    fun setReplayGainPreamp(preamp: Float) {
        _replayGainPreamp.value = preamp
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
    }

    fun setEqBands(bands: List<EQBand>) {
        _eqBands.value = bands
    }

    fun setCrossfeedEnabled(enabled: Boolean) {
        _crossfeedEnabled.value = enabled
    }

    fun setCrossfeedMode(mode: CrossfeedMode) {
        _crossfeedMode.value = mode
    }

    fun setCrossfeedLevel(level: Float) {
        _crossfeedLevel.value = level.coerceIn(0f, 1f)
    }

    fun setCrossfeedDelay(delay: Float) {
        _crossfeedDelay.value = delay
    }

    fun setCrossfeedLowpassFreq(freq: Float) {
        _crossfeedLowpassFreq.value = freq
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
    }

    fun setLimiterThreshold(threshold: Float) {
        _limiterThreshold.value = threshold
    }

    fun setLimiterReleaseTime(releaseTime: Float) {
        _limiterReleaseTime.value = releaseTime
    }

    fun setLimiterLookahead(lookahead: Float) {
        _limiterLookahead.value = lookahead
    }

    fun setConvolverEnabled(enabled: Boolean) {
        _convolverEnabled.value = enabled
    }

    fun setUsbExclusiveModeEnabled(enabled: Boolean) {
        _usbExclusiveModeEnabled.value = enabled
        if (enabled) {
            val device = _selectedUsbDevice.value
            if (device != null) {
                // 先请求 USB 权限（如未授权），授权成功后自动激活独占模式
                usbDacManager.requestAndActivateExclusiveMode(device) { success ->
                    if (!success) {
                        _usbExclusiveModeEnabled.value = false
                    }
                }
            } else {
                _usbExclusiveModeEnabled.value = false
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                usbDacManager.deactivateExclusiveMode()
            }
        }
    }

    fun setCurrentUsbDeviceName(name: String?) {
        _currentUsbDeviceName.value = name
    }

    fun selectUsbDevice(device: UsbDeviceInfo) {
        _selectedUsbDevice.value = device
        _currentUsbDeviceName.value = device.displayName
    }

    fun getSelectedUsbDevice(): UsbDeviceInfo? {
        return _selectedUsbDevice.value
    }

    fun setAaudioEnabled(enabled: Boolean) {
        _aaudioEnabled.value = if (enabled) {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
        } else {
            false
        }
    }

    fun setUsbOutputBitDepth(bits: Int) {
        _usbOutputBitDepth.value = UsbOutputBitDepth.fromBits(bits)
    }

    fun setUsbOutputBitDepthEnum(depth: UsbOutputBitDepth) {
        _usbOutputBitDepth.value = depth
    }
}