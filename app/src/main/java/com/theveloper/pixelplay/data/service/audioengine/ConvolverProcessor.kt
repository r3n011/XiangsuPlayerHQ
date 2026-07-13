package com.theveloper.pixelplay.data.service.audioengine

import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConvolverProcessor : AudioProcessor {
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var enabled = false

    private var irData: Array<FloatArray>? = null
    private var fftSize = 0
    private var overlapSize = 0
    private var fftPlan: FFTPlan? = null
    private var irFFT: Array<FloatArray>? = null

    private var inputBuffer: FloatArray? = null
    private var outputBuffer: FloatArray? = null
    private var overlapBuffer: FloatArray? = null
    private var inputPtr = 0

    override fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        if (irData != null) {
            initFFT()
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun loadImpulseResponse(file: File): Boolean {
        return try {
            val ir = readWavFile(file)
            if (ir != null && ir.size >= channelCount) {
                irData = ir
                initFFT()
                Timber.d("Convolver: Loaded impulse response from ${file.name}")
                true
            } else {
                Timber.w("Convolver: Invalid impulse response data")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Convolver: Failed to load impulse response")
            false
        }
    }

    fun setImpulseResponse(data: Array<FloatArray>) {
        irData = data
        initFFT()
    }

    private fun readWavFile(file: File): Array<FloatArray>? {
        FileInputStream(file).use { stream ->
            val header = ByteArray(44)
            if (stream.read(header) != 44) return null

            val riff = String(header, 0, 4)
            if (riff != "RIFF") return null

            val fmt = String(header, 8, 4)
            if (fmt != "WAVE") return null

            val audioFormat = ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            if (audioFormat != 1) return null

            val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val dataSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int

            val bytesPerSample = bitsPerSample / 8
            val totalSamples = dataSize / (channels * bytesPerSample)

            val result = Array(channels) { FloatArray(totalSamples) }
            val buffer = ByteArray(channels * bytesPerSample)

            for (i in 0 until totalSamples) {
                if (stream.read(buffer) != buffer.size) break

                for (ch in 0 until channels) {
                    val offset = ch * bytesPerSample
                    result[ch][i] = when (bytesPerSample) {
                        1 -> (buffer[offset].toInt() and 0xFF - 128) / 128.0f
                        2 -> ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat() / Short.MAX_VALUE
                        4 -> ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat() / Int.MAX_VALUE
                        else -> 0.0f
                    }
                }
            }
            return result
        }
    }

    private fun initFFT() {
        val ir = irData ?: return

        var maxLen = 0
        ir.forEach { maxLen = maxLen.coerceAtLeast(it.size) }

        fftSize = 1
        while (fftSize < maxLen * 2) {
            fftSize *= 2
        }
        overlapSize = fftSize / 2

        fftPlan = FFTPlan(fftSize)
        irFFT = Array(channelCount) { FloatArray(fftSize * 2) }

        for (ch in 0 until channelCount) {
            val irCh = if (ch < ir.size) ir[ch] else FloatArray(1)
            val padded = FloatArray(fftSize)
            irCh.copyInto(padded)
            fftPlan?.fft(padded, irFFT!![ch])
        }

        inputBuffer = FloatArray(fftSize)
        outputBuffer = FloatArray(fftSize)
        overlapBuffer = FloatArray(overlapSize)
        inputPtr = 0
    }

    override fun process(buffer: FloatArray, offset: Int, length: Int) {
        if (!enabled || irData == null || fftPlan == null || irFFT == null) return

        val inBuf = inputBuffer ?: return
        val outBuf = outputBuffer ?: return
        val ovlBuf = overlapBuffer ?: return
        val plan = fftPlan ?: return
        val irfft = irFFT ?: return

        var remaining = length
        var srcPtr = offset

        while (remaining > 0) {
            val toCopy = remaining.coerceAtMost(overlapSize - inputPtr)

            for (ch in 0 until channelCount) {
                for (i in 0 until toCopy) {
                    inBuf[inputPtr + i] = buffer[srcPtr + i * channelCount + ch]
                }
            }

            inputPtr += toCopy
            srcPtr += toCopy * channelCount
            remaining -= toCopy

            if (inputPtr >= overlapSize) {
                for (ch in 0 until channelCount) {
                    val input = FloatArray(fftSize)
                    for (i in 0 until overlapSize) {
                        input[i] = inBuf[i]
                    }

                    plan.fft(input, outBuf)

                    val result = FloatArray(fftSize * 2)
                    for (i in 0 until fftSize) {
                        val re = outBuf[i * 2] * irfft[ch][i * 2] - outBuf[i * 2 + 1] * irfft[ch][i * 2 + 1]
                        val im = outBuf[i * 2] * irfft[ch][i * 2 + 1] + outBuf[i * 2 + 1] * irfft[ch][i * 2]
                        result[i * 2] = re
                        result[i * 2 + 1] = im
                    }

                    plan.ifft(result, input)

                    for (i in 0 until overlapSize) {
                        input[i] += ovlBuf[i]
                        ovlBuf[i] = input[i + overlapSize]
                    }

                    for (i in 0 until overlapSize) {
                        inBuf[i] = inBuf[i + overlapSize]
                    }

                    val outPtr = srcPtr - toCopy * channelCount + ch
                    for (i in 0 until toCopy) {
                        if (outPtr + i * channelCount < buffer.size) {
                            buffer[outPtr + i * channelCount] = input[i]
                        }
                    }
                }

                inputPtr -= overlapSize
            }
        }
    }

    override fun flush() {
        inputBuffer?.fill(0.0f)
        outputBuffer?.fill(0.0f)
        overlapBuffer?.fill(0.0f)
        inputPtr = 0
    }

    override fun reset() {
        flush()
        enabled = false
        irData = null
        fftPlan = null
        irFFT = null
    }

    override fun isActive(): Boolean = enabled && irData != null

    private class FFTPlan(private val size: Int) {
        fun fft(input: FloatArray, output: FloatArray) {
            val n = size
            val logN = (Math.log(n.toDouble()) / Math.log(2.0)).toInt()

            val rev = IntArray(n)
            for (i in 0 until n) {
                rev[i] = 0
                for (j in 0 until logN) {
                    rev[i] = (rev[i] shl 1) or ((i shr j) and 1)
                }
            }

            for (i in 0 until n) {
                val j = rev[i]
                if (i < j) {
                    val temp = input[i]
                    input[i] = input[j]
                    input[j] = temp
                }
            }

            for (s in 1..logN) {
                val m = 1 shl s
                val wmRe = Math.cos(-2.0 * Math.PI / m).toFloat()
                val wmIm = Math.sin(-2.0 * Math.PI / m).toFloat()

                for (k in 0 until n step m) {
                    var wRe = 1.0f
                    var wIm = 0.0f

                    for (j in 0 until m / 2) {
                        val tRe = wRe * input[k + j + m / 2] - wIm * 0.0f
                        val tIm = wIm * input[k + j + m / 2] + wRe * 0.0f
                        val uRe = input[k + j]
                        val uIm = 0.0f

                        input[k + j] = uRe + tRe
                        input[k + j + m / 2] = uRe - tRe

                        val nextWRe = wRe * wmRe - wIm * wmIm
                        val nextWIm = wRe * wmIm + wIm * wmRe
                        wRe = nextWRe
                        wIm = nextWIm
                    }
                }
            }

            for (i in 0 until n) {
                output[i * 2] = input[i]
                output[i * 2 + 1] = 0.0f
            }
        }

        fun ifft(input: FloatArray, output: FloatArray) {
            val n = size

            for (i in 0 until n) {
                output[i] = input[i * 2]
            }

            val logN = (Math.log(n.toDouble()) / Math.log(2.0)).toInt()
            val rev = IntArray(n)
            for (i in 0 until n) {
                rev[i] = 0
                for (j in 0 until logN) {
                    rev[i] = (rev[i] shl 1) or ((i shr j) and 1)
                }
            }

            for (i in 0 until n) {
                val j = rev[i]
                if (i < j) {
                    val temp = output[i]
                    output[i] = output[j]
                    output[j] = temp
                }
            }

            for (s in 1..logN) {
                val m = 1 shl s
                val wmRe = Math.cos(2.0 * Math.PI / m).toFloat()
                val wmIm = Math.sin(2.0 * Math.PI / m).toFloat()

                for (k in 0 until n step m) {
                    var wRe = 1.0f
                    var wIm = 0.0f

                    for (j in 0 until m / 2) {
                        val tRe = wRe * output[k + j + m / 2] - wIm * 0.0f
                        val uRe = output[k + j]

                        output[k + j] = (uRe + tRe) / 2.0f
                        output[k + j + m / 2] = (uRe - tRe) / 2.0f

                        val nextWRe = wRe * wmRe - wIm * wmIm
                        val nextWIm = wRe * wmIm + wIm * wmRe
                        wRe = nextWRe
                        wIm = nextWIm
                    }
                }
            }
        }
    }
}