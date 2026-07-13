package com.theveloper.pixelplay.data.service.audioengine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioProcessorProvider @Inject constructor() {
    private var hifiEngineProcessor: HiFiEngineAudioProcessor? = null

    fun registerProcessor(processor: HiFiEngineAudioProcessor) {
        hifiEngineProcessor = processor
    }

    fun getProcessor(): HiFiEngineAudioProcessor? {
        return hifiEngineProcessor
    }
}