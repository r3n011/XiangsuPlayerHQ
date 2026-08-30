package com.theveloper.pixelplay.data.service.visualizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频频谱采集器。
 *
 * 作为一个 Media3 [AudioProcessor] 插入 DefaultAudioProcessorChain，对播放中的 PCM
 * 做实时 FFT，把频谱压成 [BANDS]（25）段对数频率强度，暴露给 Glyph Matrix 等可视化端驱动画面。
 * 处理器是纯透传（不改任何采样数据），只“偷看”内容，不参与 DSP。
 *
 * 为避免造成音频卡顿，解析过程复用内部缓冲、不分配额外数组。
 */
@Singleton
@UnstableApi
class AudioVisualizer @Inject constructor() {

    companion object {
        const val BANDS = 25
        private const val FFT_SIZE = 512
        private const val FRAME_RATE = 60 // 发布频率：约 60Hz
        private const val SMOOTH = 0.45f // 平滑系数：新帧占比
        private const val minDb = -55f
        private const val maxDb = -10f
    }

    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    // ── 内部解析状态 ──
    private var sampleRate = 44100
    private var channelCount = 2
    private var isFloat = false
    private var bytesPerSample = 2
    private var configured = false

    // ── FFT 状态：全部分量可复用 ──
    private val window = FloatArray(FFT_SIZE)
    private var windowPos = 0
    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)
    private val lastLevels = FloatArray(BANDS)
    private var framesUntilPublish = 0

    /** 自适应峰值：跟随歌曲整体响度，避免低频带持续饱和到 1 */
    private var runningPeak = 0f

    /** 生成一个绑定到本采集器的透传处理器（每次重建播放器时调用）。 */
    fun createProcessor(): AudioProcessor = SpectrumCaptureProcessor()

    // ── 频谱帧更新 ──
    private fun pushSample(v: Float) {
        window[windowPos] = v
        windowPos++
        if (windowPos >= FFT_SIZE) {
            runFft()
            windowPos = 0
        }
    }

    private fun runFft() {
        // Hann 窗 + 复制到实部
        val n = FFT_SIZE
        for (k in 0 until n) {
            val w = 0.5f * (1f - cos(2f * PI.toFloat() * k / (n - 1)))
            real[k] = window[k] * w
            imag[k] = 0f
        }
        fftRadix2(real, imag, false)

        // 计算幅度并做对数分带映射
        val magnitudes = FloatArray(n / 2)
        for (k in 0 until n / 2) {
            val re = real[k]
            val im = imag[k]
            magnitudes[k] = sqrt(re * re + im * im)
        }

        val bandEnergy = FloatArray(BANDS)
        val bandCount = IntArray(BANDS)
        // 对数分带：bin 1..(N/2-1)，低频挤在低 bin，log 分布贴合听感
        val maxBin = n / 2 - 1
        for (k in 1..maxBin) {
            val logNorm = ln(k.toDouble()) / ln(maxBin.toDouble())
            val band = (logNorm * BANDS).toInt().coerceIn(0, BANDS - 1)
            bandEnergy[band] += magnitudes[k]
            bandCount[band]++
        }

        val newLevels = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            val avg = if (bandCount[b] > 0) bandEnergy[b] / bandCount[b] else 0f
            val db = 20f * kotlin.math.log10(avg + 1e-4f)
            val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            val shaped = norm.pow(0.9f)
            newLevels[b] = shaped
        }

        // 自适应归一：相对最近的运行峰值缩放，保证各频带有动态变化、不持续饱和
        runningPeak = runningPeak * 0.985f + (newLevels.max() * 0.015f)
        if (runningPeak > 0.001f) {
            for (b in 0 until BANDS) {
                newLevels[b] = (newLevels[b] / runningPeak).coerceIn(0f, 1f)
            }
        }

        // 平滑 + 发布
        for (b in 0 until BANDS) {
            lastLevels[b] = lastLevels[b] * SMOOTH + newLevels[b] * (1f - SMOOTH)
        }
        framesUntilPublish++
        if (framesUntilPublish * FFT_SIZE >= sampleRate / FRAME_RATE) {
            framesUntilPublish = 0
            _levels.value = lastLevels.clone()
        }
    }

    /** 原位 radix-2 FFT，n 必须为 2 的幂。 */
    private fun fftRadix2(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var bits = 0
        var t = n
        while (t > 1) { t = t shr 1; bits++ }
        for (i in 1 until n) {
            var j = 0
            var m = i
            for (bit in 0 until bits) {
                j = (j shl 1) or (m and 1)
                m = m shr 1
            }
            if (j > i) {
                var swap = re[i]; re[i] = re[j]; re[j] = swap
                swap = im[i]; im[i] = im[j]; im[j] = swap
            }
        }
        var len = 2
        while (len <= n) {
            val ang = if (inverse) 2 * PI / len else -2 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = kotlin.math.sin(ang).toFloat()
            for (i in 0 until n step len) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]; val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe; im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe; im[i + k + len / 2] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
            }
            len = len shl 1
        }
    }

    private fun resetLevels() {
        val z = FloatArray(BANDS) { 0f }
        z.copyInto(lastLevels)
        _levels.value = z
    }

    // ── 透传处理器：偷看 PCM 填 window ──
    private inner class SpectrumCaptureProcessor : AudioProcessor {
        private var inputEnded = false
        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            sampleRate = inputAudioFormat.sampleRate
            channelCount = inputAudioFormat.channelCount
            isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            bytesPerSample = if (isFloat) Float.SIZE_BYTES else Short.SIZE_BYTES
            configured = true
            return inputAudioFormat
        }

        override fun isActive(): Boolean = configured

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) {
                inputBuffer.position(inputBuffer.limit())
                outputBuffer = inputBuffer.duplicate()
                return
            }
            // 透传：原 bufer 原样输出
            outputBuffer = inputBuffer.duplicate()
            // 用另一份读 source 偷看，不动 outputBuffer 的 position
            val src = inputBuffer.duplicate()
            inputBuffer.position(inputBuffer.limit())

            val bytesPerFrame = channelCount * bytesPerSample
            if (bytesPerFrame <= 0) return
            val frames = src.remaining() / bytesPerFrame
            if (frames == 0) return
            if (channelCount <= 0) return

            if (isFloat) {
                val bb = src.order(ByteOrder.nativeOrder())
                repeat(frames) {
                    var acc = 0f
                    repeat(channelCount) { acc += bb.float }
                    pushSample(acc / channelCount)
                }
            } else {
                val bb = src.order(ByteOrder.nativeOrder())
                repeat(frames) {
                    var acc = 0f
                    repeat(channelCount) {
                        acc += bb.short.toFloat() / Short.MAX_VALUE.toFloat()
                    }
                    pushSample(acc / channelCount)
                }
            }
        }

        private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
        override fun getOutput(): ByteBuffer {
            val out = outputBuffer
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return out
        }

        override fun isEnded(): Boolean = inputEnded

        override fun queueEndOfStream() {
            inputEnded = true
            outputBuffer = AudioProcessor.EMPTY_BUFFER
        }

        @Suppress("DEPRECATION")
        @Deprecated("Media4/Waveform no longer needed; retain minimal override")
        override fun flush() {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            inputEnded = false
            windowPos = 0
            resetLevels()
        }

        override fun reset() {
            flush()
            configured = false
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun internalLevels(): FloatArray = _levels.value
}