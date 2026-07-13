package com.theveloper.pixelplay.data.service.audioengine

class LimiterProcessor : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var enabled = false
    private var threshold = -0.1f
    private var releaseTime = 0.05f
    private var lookahead = 5.0f

    private var gain = 1.0f
    private var maxAmp = 0.0f
    private var envelope = 0.0f
    private var releaseCoef = 0.0f
    private var lookaheadBuffer: FloatArray? = null
    private var lookaheadIndex = 0

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        updateReleaseCoef()
        initLookaheadBuffer()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setThreshold(threshold: Float) {
        this.threshold = threshold
    }

    fun setReleaseTime(releaseTime: Float) {
        this.releaseTime = releaseTime
        updateReleaseCoef()
    }

    fun setLookahead(lookahead: Float) {
        this.lookahead = lookahead
        initLookaheadBuffer()
    }

    private fun updateReleaseCoef() {
        releaseCoef = Math.exp(-1.0 / (sampleRate * releaseTime)).toFloat()
    }

    private fun initLookaheadBuffer() {
        val lookaheadSamples = (sampleRate * lookahead / 1000.0).toInt()
        lookaheadBuffer = FloatArray(lookaheadSamples * channelCount)
        lookaheadIndex = 0
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        if (!enabled) return

        val buf = lookaheadBuffer ?: return
        val frameCount = length / channelCount

        for (frame in 0 until frameCount) {
            val index = offset + frame * channelCount

            var currentMax = 0.0f
            for (ch in 0 until channelCount) {
                val sample = buffer[index + ch]
                currentMax = currentMax.coerceAtLeast(kotlin.math.abs(sample))
            }

            maxAmp = maxAmp.coerceAtLeast(currentMax)

            val targetGain = if (maxAmp > threshold) {
                threshold / maxAmp
            } else {
                1.0f
            }

            envelope = envelope * releaseCoef + targetGain * (1.0f - releaseCoef)
            gain = envelope

            for (ch in 0 until channelCount) {
                buffer[index + ch] *= gain
            }

            if (maxAmp > 0) {
                maxAmp *= 0.9999f
            }
        }
    }

    override fun flush() {
        gain = 1.0f
        maxAmp = 0.0f
        envelope = 1.0f
        lookaheadIndex = 0
        lookaheadBuffer?.fill(0.0f)
    }

    override fun reset() {
        flush()
        enabled = false
        threshold = -0.1f
        releaseTime = 0.05f
        lookahead = 5.0f
    }

    override fun isActive(): Boolean = enabled
}