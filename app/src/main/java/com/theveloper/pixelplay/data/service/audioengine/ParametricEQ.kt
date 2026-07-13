package com.theveloper.pixelplay.data.service.audioengine

import timber.log.Timber

data class EQBand(
    var frequency: Float = 1000.0f,
    var gain: Float = 0.0f,
    var q: Float = 1.0f,
    var type: EQType = EQType.BELL
)

enum class EQType {
    BELL,
    LOW_SHELF,
    HIGH_SHELF,
    NOTCH,
    HIGH_PASS,
    LOW_PASS
}

class ParametricEQ : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var enabled = false
    private var bands = mutableListOf<EQBand>()
    private var filters: Array<IIRFilter>? = null

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        updateFilters()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setBands(newBands: List<EQBand>) {
        this.bands = newBands.toMutableList()
        updateFilters()
    }

    fun addBand(band: EQBand) {
        bands.add(band)
        updateFilters()
    }

    fun removeBand(index: Int) {
        if (index in bands.indices) {
            bands.removeAt(index)
            updateFilters()
        }
    }

    fun getBands(): List<EQBand> = bands.toList()

    private fun updateFilters() {
        filters = Array(channelCount) { IIRFilter() }
        filters?.forEach { filter ->
            filter.clearFilters()
            bands.forEach { band ->
                val coefficients = calculateCoefficients(band)
                filter.addFilter(coefficients)
            }
        }
    }

    private fun calculateCoefficients(band: EQBand): IIRFilter.Coefficients {
        val w0 = 2.0 * Math.PI * band.frequency / sampleRate
        val cosW0 = Math.cos(w0)
        val sinW0 = Math.sin(w0)
        val alpha = sinW0 / (2.0 * band.q)

        return when (band.type) {
            EQType.BELL -> calculateBellCoefficients(band.gain, cosW0, sinW0, alpha)
            EQType.LOW_SHELF -> calculateLowShelfCoefficients(band.gain, cosW0, sinW0, alpha)
            EQType.HIGH_SHELF -> calculateHighShelfCoefficients(band.gain, cosW0, sinW0, alpha)
            EQType.NOTCH -> calculateNotchCoefficients(cosW0, alpha)
            EQType.HIGH_PASS -> calculateHighPassCoefficients(cosW0, alpha)
            EQType.LOW_PASS -> calculateLowPassCoefficients(cosW0, alpha)
        }
    }

    private fun calculateBellCoefficients(gain: Float, cosW0: Double, sinW0: Double, alpha: Double): IIRFilter.Coefficients {
        val A = Math.pow(10.0, gain / 40.0)
        val b0 = 1.0 + alpha * A
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * A
        val a0 = 1.0 + alpha / A
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / A
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    private fun calculateLowShelfCoefficients(gain: Float, cosW0: Double, sinW0: Double, alpha: Double): IIRFilter.Coefficients {
        val A = Math.pow(10.0, gain / 40.0)
        val sqrtA = Math.sqrt(A)
        val b0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
        val b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
        val b2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
        val a0 = (A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
        val a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
        val a2 = (A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    private fun calculateHighShelfCoefficients(gain: Float, cosW0: Double, sinW0: Double, alpha: Double): IIRFilter.Coefficients {
        val A = Math.pow(10.0, gain / 40.0)
        val sqrtA = Math.sqrt(A)
        val b0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
        val b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
        val b2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
        val a0 = (A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
        val a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
        val a2 = (A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    private fun calculateNotchCoefficients(cosW0: Double, alpha: Double): IIRFilter.Coefficients {
        val b0 = 1.0
        val b1 = -2.0 * cosW0
        val b2 = 1.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    private fun calculateHighPassCoefficients(cosW0: Double, alpha: Double): IIRFilter.Coefficients {
        val b0 = (1.0 + cosW0) / 2.0
        val b1 = -(1.0 + cosW0)
        val b2 = (1.0 + cosW0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    private fun calculateLowPassCoefficients(cosW0: Double, alpha: Double): IIRFilter.Coefficients {
        val b0 = (1.0 - cosW0) / 2.0
        val b1 = 1.0 - cosW0
        val b2 = (1.0 - cosW0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha
        return IIRFilter.Coefficients(b0, b1, b2, a0, a1, a2)
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        if (!enabled || filters == null) return

        val frameCount = length / channelCount
        for (frame in 0 until frameCount) {
            for (channel in 0 until channelCount) {
                val index = offset + frame * channelCount + channel
                filters?.get(channel)?.process(buffer, index)
            }
        }
    }

    override fun flush() {
        filters?.forEach { it.flush() }
    }

    override fun reset() {
        filters?.forEach { it.reset() }
    }

    override fun isActive(): Boolean = enabled && bands.isNotEmpty()

    private class IIRFilter {
        private var filterChain = mutableListOf<BiQuadFilter>()

        fun clearFilters() {
            filterChain.clear()
        }

        fun addFilter(coefficients: Coefficients) {
            filterChain.add(BiQuadFilter(coefficients))
        }

        fun process(buffer: FloatArray, index: Int) {
            var input = buffer[index]
            filterChain.forEach { input = it.process(input) }
            buffer[index] = input
        }

        fun flush() {
            filterChain.forEach { it.flush() }
        }

        fun reset() {
            filterChain.forEach { it.reset() }
        }

        data class Coefficients(
            val b0: Double,
            val b1: Double,
            val b2: Double,
            val a0: Double,
            val a1: Double,
            val a2: Double
        )

        private class BiQuadFilter(private val coeff: Coefficients) {
            private var x1 = 0.0
            private var x2 = 0.0
            private var y1 = 0.0
            private var y2 = 0.0

            fun process(input: Float): Float {
                val x0 = input.toDouble()
                val y0 = (coeff.b0 * x0 + coeff.b1 * x1 + coeff.b2 * x2 - coeff.a1 * y1 - coeff.a2 * y2) / coeff.a0
                x2 = x1
                x1 = x0
                y2 = y1
                y1 = y0
                return y0.toFloat()
            }

            fun flush() {
                x1 = 0.0
                x2 = 0.0
                y1 = 0.0
                y2 = 0.0
            }

            fun reset() {
                flush()
            }
        }
    }
}