package com.theveloper.pixelplay.data.service.audioengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngineSettings @Inject constructor() {
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
    }

    fun setCurrentUsbDeviceName(name: String?) {
        _currentUsbDeviceName.value = name
    }
}