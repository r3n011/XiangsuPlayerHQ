package com.theveloper.pixelplay.data.service.audioengine

interface AudioProcessor {

    fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int)

    fun process(buffer: FloatArray, offset: Int, length: Int)

    fun flush()

    fun reset()

    fun isActive(): Boolean = true

    fun release() {}
}