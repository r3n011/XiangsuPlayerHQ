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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一首歌的波形峰值快照。
 *
 * [peaks] 长度固定为 [AudioVisualizer.WAVE_BUCKETS]，把整首歌等分成若干个时间桶，
 * 每个桶存放该时间段内的最大振幅（0f..1f）。值为负数表示该时间段尚未分析过，
 * UI 遇到这种桶时回退到合成波形，从而实现在线播放「听过一段亮一段」的效果。
 */
class WaveformPeaks(
    val songId: String,
    val peaks: FloatArray
)

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

        /** 波形进度条的时间桶数量（实时累积与本地整轨分析共用，保证两边的分辨率一致）。 */
        const val WAVE_BUCKETS = 480

        /** 波形快照的发布间隔：约 6Hz，足够跟上进度条长条，又不会频繁分配数组。 */
        private const val WAVE_PUBLISH_INTERVAL_MS = 160L

        /** 波形缓存上限：超过后淘汰最早写入的一首，避免长期播放吃掉内存。 */
        private const val WAVE_CACHE_LIMIT = 8

        /** 未分析桶的哨兵值，UI 见到它时回退合成波形。 */
        const val WAVE_UNKNOWN = -1f
    }

    private val _levels = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    // ── 波形峰值（真实来自 PCM）──
    // 在线音源只能边播边分析：位置轮询会周期性调用 syncWaveform 校正帧游标，
    // 于是「播放过的部分」被逐桶填成真实峰值，未播放的桶保持 WAVE_UNKNOWN。
    private val _waveform = MutableStateFlow<WaveformPeaks?>(null)
    val waveform: StateFlow<WaveformPeaks?> = _waveform.asStateFlow()

    @Volatile private var waveSongId: String? = null
    @Volatile private var waveDurationMs: Long = 0L
    @Volatile private var waveTotalFrames: Long = 0L
    @Volatile private var waveFrameCursor: Long = 0L
    @Volatile private var wavePeaks: FloatArray? = null
    @Volatile private var waveLastPublishMs: Long = 0L

    /** 已完成分析的波形缓存（LRU，仅进程内）。本地整轨分析结果与在线已听片段都存这里。 */
    private val waveCache = LinkedHashMap<String, FloatArray>()

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
    // ⚡ FFT 频谱分带复用工作数组：runFft 每 ~23ms 跑一次，消除每帧 4 次临时分配
    //    （magnitudes/bandEnergy/bandCount/newLevels），减少渲染线程 GC 压力避免帧堆积
    private val magnitudes = FloatArray(FFT_SIZE / 2)
    private val bandEnergy = FloatArray(BANDS)
    private val bandCount = IntArray(BANDS)
    private val newLevels = FloatArray(BANDS)
    private var framesUntilPublish = 0

    /** 自适应峰值：跟随歌曲整体响度，避免低频带持续饱和到 1 */
    private var runningPeak = 0f

    /** 生成一个绑定到本采集器的透传处理器（每次重建播放器时调用）。 */
    fun createProcessor(): AudioProcessor = SpectrumCaptureProcessor()

    // ── 频谱帧更新 ──
    private fun pushSample(v: Float) {
        accumulateWaveform(v)
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

        // 计算幅度并做对数分带映射（复用工作数组，零临时分配）
        for (k in 0 until n / 2) {
            val re = real[k]
            val im = imag[k]
            magnitudes[k] = sqrt(re * re + im * im)
        }

        bandEnergy.fill(0f)
        bandCount.fill(0)
        // 对数分带：bin 1..(N/2-1)，低频挤在低 bin，log 分布贴合听感
        val maxBin = n / 2 - 1
        for (k in 1..maxBin) {
            val logNorm = ln(k.toDouble()) / ln(maxBin.toDouble())
            val band = (logNorm * BANDS).toInt().coerceIn(0, BANDS - 1)
            bandEnergy[band] += magnitudes[k]
            bandCount[band]++
        }

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

    // ── 波形：与播放位置对齐 ──

    /**
     * 由位置轮询周期性调用，把帧游标重新钉到当前播放位置。
     *
     * 每次同步都重置游标，于是 seek / 变速 / 解码时钟漂移都会被自然吸收；
     * 换歌时把上一首的峰值存入缓存，并优先取用缓存（可能已被本地分析填满）。
     */
    fun syncWaveform(songId: String?, positionMs: Long, durationMs: Long) {
        if (songId.isNullOrBlank()) {
            clearWaveform()
            return
        }
        val rate = if (sampleRate > 0) sampleRate else 44100
        val duration = durationMs.coerceAtLeast(0L)
        val positionFrames = positionMs.coerceAtLeast(0L) * rate / 1000L

        if (songId != waveSongId) {
            stashWaveform()
            wavePeaks = cachedWaveform(songId) ?: FloatArray(WAVE_BUCKETS) { WAVE_UNKNOWN }
            waveSongId = songId
            waveDurationMs = duration
            waveTotalFrames = if (duration > 0L) duration * rate / 1000L else 0L
            waveFrameCursor = positionFrames
            publishWaveform(force = true)
            return
        }

        // 时长未知时（如 seek 后 controller.duration 暂为 TIME_UNSET）保留已有映射，
        // 否则整首歌的桶索引会被瞬间打乱。
        if (duration > 0L) {
            waveDurationMs = duration
            waveTotalFrames = duration * rate / 1000L
        }
        waveFrameCursor = positionFrames
    }

    /**
     * 本地音源整轨分析完成后提交结果：整首波形一次性填满，
     * 若正在播放同一首则立即推送到 UI。
     */
    fun submitLocalWaveform(songId: String, peaks: FloatArray) {
        if (songId.isBlank() || peaks.size != WAVE_BUCKETS) return
        cacheWaveform(songId, peaks)
        if (songId == waveSongId) {
            wavePeaks = peaks
            publishWaveform(force = true)
        }
    }

    /** 每帧调用一次，把当前采样并入所属时间桶的峰值。 */
    private fun accumulateWaveform(sample: Float) {
        val peaks = wavePeaks ?: return
        val total = waveTotalFrames
        if (total <= 0L) return
        val frame = waveFrameCursor
        waveFrameCursor = frame + 1
        if (frame < 0L) return
        val bucket = (frame * WAVE_BUCKETS / total).toInt()
        if (bucket < 0 || bucket >= WAVE_BUCKETS) return
        // 用平方根压一下动态范围：人耳对响度的感知更接近振幅的平方根
        val amplitude = sqrt(abs(sample)).coerceIn(0f, 1f)
        if (amplitude > peaks[bucket]) peaks[bucket] = amplitude
        publishWaveform(force = false)
    }

    private fun publishWaveform(force: Boolean) {
        val peaks = wavePeaks ?: return
        val songId = waveSongId ?: return
        val now = System.currentTimeMillis()
        if (!force && now - waveLastPublishMs < WAVE_PUBLISH_INTERVAL_MS) return
        waveLastPublishMs = now
        _waveform.value = WaveformPeaks(songId, peaks.clone())
    }

    private fun clearWaveform() {
        stashWaveform()
        waveSongId = null
        wavePeaks = null
        waveDurationMs = 0L
        waveTotalFrames = 0L
        waveFrameCursor = 0L
        if (_waveform.value != null) _waveform.value = null
    }

    private fun stashWaveform() {
        val songId = waveSongId ?: return
        val peaks = wavePeaks ?: return
        cacheWaveform(songId, peaks)
    }

    private fun cacheWaveform(songId: String, peaks: FloatArray) {
        synchronized(waveCache) {
            waveCache.remove(songId)
            waveCache[songId] = peaks
            while (waveCache.size > WAVE_CACHE_LIMIT) {
                val oldest = waveCache.keys.firstOrNull() ?: break
                waveCache.remove(oldest)
            }
        }
    }

    /** 命中缓存时刷新 LRU 顺序后返回。 */
    private fun cachedWaveform(songId: String): FloatArray? = synchronized(waveCache) {
        val peaks = waveCache.remove(songId)
        if (peaks != null) waveCache[songId] = peaks
        peaks
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun internalLevels(): FloatArray = _levels.value
}