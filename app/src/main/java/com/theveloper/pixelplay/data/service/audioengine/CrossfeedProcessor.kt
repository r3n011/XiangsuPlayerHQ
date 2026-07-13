package com.theveloper.pixelplay.data.service.audioengine

enum class CrossfeedMode {
    BS2B,
    MEIER
}

class CrossfeedProcessor : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var enabled = false
    private var mode = CrossfeedMode.BS2B
    private var level = 0.3f
    private var delay = 2.0f
    private var lowpassFreq = 700.0f

    private var delayBuffer: FloatArray? = null
    private var delayIndex = 0
    private var lowpassL = BiQuadLowpass()
    private var lowpassR = BiQuadLowpass()

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        updateDelayBuffer()
        updateLowpass()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setMode(mode: CrossfeedMode) {
        this.mode = mode
    }

    fun setLevel(level: Float) {
        this.level = level.coerceIn(0f, 1f)
    }

    fun setDelay(delay: Float) {
        this.delay = delay
        updateDelayBuffer()
    }

    fun setLowpassFreq(freq: Float) {
        this.lowpassFreq = freq
        updateLowpass()
    }

    private fun updateDelayBuffer() {
        val delaySamples = (sampleRate * delay / 1000.0).toInt()
        delayBuffer = FloatArray(delaySamples)
        delayIndex = 0
    }

    private fun updateLowpass() {
        lowpassL.setParams(sampleRate, lowpassFreq)
        lowpassR.setParams(sampleRate, lowpassFreq)
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        if (!enabled || channelCount < 2) return

        val buf = delayBuffer ?: return
        val frameCount = length / channelCount

        when (mode) {
            CrossfeedMode.BS2B -> processBS2B(buffer, offset, frameCount, buf)
            CrossfeedMode.MEIER -> processMeier(buffer, offset, frameCount, buf)
        }
    }

    private fun processBS2B(buffer: FloatArray, offset: Int, frameCount: Int, delayBuf: FloatArray) {
        for (frame in 0 until frameCount) {
            val index = offset + frame * channelCount
            val left = buffer[index]
            val right = buffer[index + 1]

            val delayedLeft = delayBuf[delayIndex]
            val delayedRight = delayBuf[(delayIndex + delayBuf.size / 2) % delayBuf.size]

            val filteredLeft = lowpassL.process(delayedLeft)
            val filteredRight = lowpassR.process(delayedRight)

            buffer[index] = left + filteredRight * level
            buffer[index + 1] = right + filteredLeft * level

            delayBuf[delayIndex] = left
            delayBuf[(delayIndex + delayBuf.size / 2) % delayBuf.size] = right

            delayIndex = (delayIndex + 1) % delayBuf.size
        }
    }

    private fun processMeier(buffer: FloatArray, offset: Int, frameCount: Int, delayBuf: FloatArray) {
        val crossfeedGain = level * 0.5f
        val directGain = 1.0f - crossfeedGain * 0.5f

        for (frame in 0 until frameCount) {
            val index = offset + frame * channelCount
            val left = buffer[index]
            val right = buffer[index + 1]

            val delayedLeft = delayBuf[delayIndex]
            val delayedRight = delayBuf[(delayIndex + delayBuf.size / 2) % delayBuf.size]

            val filteredLeft = lowpassL.process(delayedLeft)
            val filteredRight = lowpassR.process(delayedRight)

            buffer[index] = left * directGain + filteredRight * crossfeedGain
            buffer[index + 1] = right * directGain + filteredLeft * crossfeedGain

            delayBuf[delayIndex] = left
            delayBuf[(delayIndex + delayBuf.size / 2) % delayBuf.size] = right

            delayIndex = (delayIndex + 1) % delayBuf.size
        }
    }

    override fun flush() {
        delayBuffer?.fill(0.0f)
        delayIndex = 0
        lowpassL.flush()
        lowpassR.flush()
    }

    override fun reset() {
        flush()
        enabled = false
        mode = CrossfeedMode.BS2B
        level = 0.3f
        delay = 2.0f
        lowpassFreq = 700.0f
    }

    override fun isActive(): Boolean = enabled

    private class BiQuadLowpass {
        private var b0 = 0.0f
        private var b1 = 0.0f
        private var b2 = 0.0f
        private var a0 = 1.0f
        private var a1 = 0.0f
        private var a2 = 0.0f
        private var x1 = 0.0f
        private var x2 = 0.0f
        private var y1 = 0.0f
        private var y2 = 0.0f

        fun setParams(sampleRate: Int, cutoffFreq: Float) {
            val w0 = 2.0f * Math.PI.toFloat() * cutoffFreq / sampleRate
            val cosW0 = Math.cos(w0.toDouble()).toFloat()
            val sinW0 = Math.sin(w0.toDouble()).toFloat()
            val alpha = sinW0 / 2.0f

            b0 = (1.0f - cosW0) / 2.0f
            b1 = 1.0f - cosW0
            b2 = (1.0f - cosW0) / 2.0f
            a0 = 1.0f + alpha
            a1 = -2.0f * cosW0
            a2 = 1.0f - alpha
        }

        fun process(input: Float): Float {
            val x0 = input
            val y0 = (b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0
        }

        fun flush() {
            x1 = 0.0f
            x2 = 0.0f
            y1 = 0.0f
            y2 = 0.0f
        }
    }
}