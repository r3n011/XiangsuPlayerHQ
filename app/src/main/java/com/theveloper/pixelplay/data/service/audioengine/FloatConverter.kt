package com.theveloper.pixelplay.data.service.audioengine

class FloatConverter : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        when (bitDepth) {
            8 -> convertFrom8Bit(buffer, offset, length)
            16 -> {}
            24 -> convertFrom24Bit(buffer, offset, length)
            32 -> {}
        }
    }

    private fun convertFrom8Bit(buffer: FloatArray, offset: Int, length: Int) {
        for (i in offset until offset + length) {
            val raw = (buffer[i] * 255).toInt()
            buffer[i] = (raw - 128) / 128.0f
        }
    }

    private fun convertFrom24Bit(buffer: FloatArray, offset: Int, length: Int) {
        val max24Bit = 8388608.0f
        for (i in offset until offset + length) {
            buffer[i] = buffer[i] / max24Bit
        }
    }

    override fun flush() {}

    override fun reset() {}
}