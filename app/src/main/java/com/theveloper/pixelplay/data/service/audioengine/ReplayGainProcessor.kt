package com.theveloper.pixelplay.data.service.audioengine

class ReplayGainProcessor : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var enabled = false
    private var trackGain = 0.0f
    private var albumGain = 0.0f
    private var preamp = 0.0f
    private var peak = 1.0f
    private var useAlbumGain = false

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setTrackGain(gain: Float) {
        this.trackGain = gain
    }

    fun setAlbumGain(gain: Float) {
        this.albumGain = gain
    }

    fun setPreamp(preamp: Float) {
        this.preamp = preamp
    }

    fun setPeak(peak: Float) {
        this.peak = peak
    }

    fun setUseAlbumGain(useAlbumGain: Boolean) {
        this.useAlbumGain = useAlbumGain
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        if (!enabled) return

        val gain = if (useAlbumGain) albumGain else trackGain
        val multiplier = Math.pow(10.0, (gain + preamp) / 20.0).toFloat()

        for (i in offset until offset + length) {
            buffer[i] *= multiplier
        }

        if (peak > 0) {
            val peakMultiplier = 1.0f / peak
            for (i in offset until offset + length) {
                buffer[i] = buffer[i].coerceIn(-peakMultiplier, peakMultiplier)
            }
        }
    }

    override fun flush() {}

    override fun reset() {
        enabled = false
        trackGain = 0.0f
        albumGain = 0.0f
        preamp = 0.0f
        peak = 1.0f
        useAlbumGain = false
    }

    override fun isActive(): Boolean = enabled
}