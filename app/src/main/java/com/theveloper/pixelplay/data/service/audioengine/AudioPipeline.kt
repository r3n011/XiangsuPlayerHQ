package com.theveloper.pixelplay.data.service.audioengine

import timber.log.Timber

class AudioPipeline {
    private val processors = mutableListOf<AudioProcessor>()
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32

    fun addProcessor(processor: AudioProcessor) {
        processors.add(processor)
        processor.configure(sampleRate, channelCount, bitDepth)
    }

    fun removeProcessor(processor: AudioProcessor) {
        processors.remove(processor)
        processor.release()
    }

    fun removeProcessor(index: Int) {
        if (index in processors.indices) {
            processors.removeAt(index).release()
        }
    }

    fun getProcessors(): List<AudioProcessor> = processors.toList()

    fun clearProcessors() {
        processors.forEach { it.release() }
        processors.clear()
    }

    fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        processors.forEach { it.configure(sampleRate, channelCount, bitDepth) }
    }

    fun process(buffer: FloatArray, offset: Int, length: Int) {
        processors.forEach { processor ->
            if (processor.isActive()) {
                try {
                    processor.process(buffer, offset, length)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing audio in ${processor::class.simpleName}")
                }
            }
        }
    }

    fun flush() {
        processors.forEach { it.flush() }
    }

    fun reset() {
        processors.forEach { it.reset() }
    }

    fun release() {
        processors.forEach { it.release() }
        processors.clear()
    }

    fun size(): Int = processors.size
}