package com.theveloper.pixelplay.data.service.audioengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngineProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioEngine: AudioEngine? = null
    private var isInitialized = false

    @Synchronized
    fun getAudioEngine(): AudioEngine {
        if (audioEngine == null) {
            audioEngine = AudioEngine(context).apply {
                addProcessor(FloatConverter())
                addProcessor(ReplayGainProcessor())
                addProcessor(ParametricEQ())
                addProcessor(CrossfeedProcessor())
                addProcessor(LimiterProcessor())
            }
            isInitialized = true
        }
        return audioEngine!!
    }

    @Synchronized
    fun release() {
        audioEngine?.release()
        audioEngine = null
        isInitialized = false
    }

    fun isInitialized(): Boolean = isInitialized
}