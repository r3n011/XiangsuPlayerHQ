package com.theveloper.pixelplay.data.service.audioengine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 AudioProcessor 桥接器
 *
 * 将自定义 AudioPipeline（Float32 DSP 处理链）集成到 Media3 的音频处理管线中。
 * 作为 DefaultAudioProcessorChain 的最后一个处理器，接收上游的 PCM 数据，
 * 转换为 Float32 后依次通过 ReplayGain → EQ → Crossfeed → Limiter 等 DSP，
 * 再转换回原始格式输出给 AudioSink。
 *
 * 设计原则：
 * - 不修改 Media3 播放架构
 * - 不修改播放器生命周期
 * - 仅在 PCM 输出阶段插入 DSP 处理
 */
@UnstableApi
class HiFiEngineAudioProcessor : AudioProcessor {

    companion object {
        private const val TAG = "HiFiEngineProcessor"
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // ⚡ 可复用工作缓冲（渲染线程串行调用，单线程安全）。
    //    消除 DSP 启用时每块 4-5 次临时分配（FloatArray/ByteArray/allocateDirect），
    //    避免持续 GC 压力导致音频渲染线程周期性停顿 → 解码器输入堆积（queueInputBuffer
    //    间隔拉大）与 AudioFlinger pipeline 帧堆积（"too many frames in pipeline"）。
    private var cachedFloatWork: FloatArray? = null
    private var cachedByteWork: ByteArray? = null
    private var cachedDirectOutput: ByteBuffer? = null

    // 自定义 DSP 处理器实例
    private val replayGainProcessor = ReplayGainProcessor()
    private val parametricEQ = ParametricEQ()
    private val crossfeedProcessor = CrossfeedProcessor()
    private val limiterProcessor = LimiterProcessor()

    // DSP Pipeline（纯 Float32 处理链）
    private val pipeline = AudioPipeline()

    // 当前音频格式参数
    private var sampleRate = 44100
    private var channelCount = 2
    private var isFloat = false
    private var bytesPerSample = 2

    private var configured = false

    init {
        // 按照架构设计书的处理器顺序添加
        pipeline.addProcessor(replayGainProcessor)
        pipeline.addProcessor(parametricEQ)
        pipeline.addProcessor(crossfeedProcessor)
        pipeline.addProcessor(limiterProcessor)
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        bytesPerSample = if (isFloat) Float.SIZE_BYTES else Short.SIZE_BYTES

        pipeline.configure(sampleRate, channelCount, if (isFloat) 32 else 16)
        configured = true

        Timber.d(TAG, "Configured: %dHz, %dch, %s",
            sampleRate, channelCount, if (isFloat) "FLOAT" else "PCM16")

        // 输出格式与输入格式一致（DSP 就地处理，不改变格式）
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        if (!configured) return false
        // 只要有一个处理器激活，整个桥接器就激活
        return pipeline.hasActiveProcessor()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!configured || inputFormat == AudioFormat.NOT_SET) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        // 所有 DSP 处理器均未启用时直接透传：避免每次分块都做 ByteArray/FloatArray
        // 分配与 PCM↔Float 转换，产生持续 GC 压力导致周期性音频卡顿（打嗝/电流声）。
        if (!pipeline.hasActiveProcessor()) {
            outputBuffer = inputBuffer.duplicate()
            inputBuffer.position(inputBuffer.limit())
            return
        }

        // 将输入数据复制到待处理缓冲区
        ensurePendingCapacity(remaining)
        pendingBuffer.put(inputBuffer)
        pendingBuffer.flip()
        inputBuffer.position(inputBuffer.limit())

        // 处理完整帧
        val bytesPerFrame = channelCount * bytesPerSample
        val pendingBytes = pendingBuffer.remaining()
        val completeFrames = pendingBytes / bytesPerFrame

        if (completeFrames == 0) return

        val processBytes = completeFrames * bytesPerFrame
        val processData = ensureByteWork(processBytes)
        pendingBuffer.get(processData)

        // 保留未使用的字节
        val leftover = pendingBuffer.remaining()
        if (leftover > 0) {
            val leftoverData = ByteArray(leftover)
            pendingBuffer.get(leftoverData)
            pendingBuffer.clear()
            pendingBuffer.put(leftoverData)
        } else {
            pendingBuffer.clear()
        }

        // 转换为 Float32 并处理（复用工作缓冲，零临时分配）
        val sampleCount = processBytes / bytesPerSample
        val floatData = ensureFloatWork(sampleCount)
        toFloatArray(processData, floatData)

        // 通过 DSP 处理链（length = 样本数）
        pipeline.process(floatData, 0, sampleCount)

        // 转换回原始格式（复用同一工作数组，processData 已消费完可覆盖）
        val outputData = ensureByteWork(sampleCount * bytesPerSample)
        toByteArray(floatData, outputData)

        // 存入输出缓冲区（复用 direct buffer，容量不足才重新分配）
        setOutputBuffer(outputData)
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && pendingBuffer.remaining() == 0 && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun queueEndOfStream() {
        // 透传模式下无残留数据，直接标记结束
        if (!pipeline.hasActiveProcessor()) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            inputEnded = true
            return
        }

        // 处理 pending buffer 中剩余的数据
        val remaining = pendingBuffer.remaining()
        if (remaining > 0) {
            val processData = ensureByteWork(remaining)
            pendingBuffer.get(processData)
            pendingBuffer.clear()

            val sampleCount = remaining / bytesPerSample
            val floatData = ensureFloatWork(sampleCount)
            toFloatArray(processData, floatData)

            pipeline.process(floatData, 0, sampleCount)

            val outputData = ensureByteWork(sampleCount * bytesPerSample)
            toByteArray(floatData, outputData)

            setOutputBuffer(outputData)
        } else {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
        }
        inputEnded = true
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata)")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingBuffer.clear()
        inputEnded = false
        pipeline.flush()
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingBuffer.clear()
        inputEnded = false
        inputFormat = AudioFormat.NOT_SET
        configured = false
        pipeline.reset()
    }

    // ── DSP 处理器访问器（供 ViewModel / Settings 层调用） ──

    fun getReplayGainProcessor(): ReplayGainProcessor = replayGainProcessor
    fun getParametricEQ(): ParametricEQ = parametricEQ
    fun getCrossfeedProcessor(): CrossfeedProcessor = crossfeedProcessor
    fun getLimiterProcessor(): LimiterProcessor = limiterProcessor

    // ── ByteBuffer 管理 ──

    private fun ensurePendingCapacity(required: Int) {
        if (pendingBuffer.remaining() + required > pendingBuffer.capacity()) {
            val newBuffer = ByteBuffer.allocateDirect(
                (pendingBuffer.capacity() + required).coerceAtLeast(pendingBuffer.capacity() * 2)
            ).order(ByteOrder.nativeOrder())
            pendingBuffer.flip()
            newBuffer.put(pendingBuffer)
            pendingBuffer = newBuffer
        }
    }

    // ── 格式转换（复用工作缓冲，避免热路径临时分配） ──

    private fun ensureFloatWork(sampleCount: Int): FloatArray {
        val existing = cachedFloatWork
        if (existing == null || existing.size < sampleCount) {
            val work = FloatArray(sampleCount)
            cachedFloatWork = work
            return work
        }
        return existing
    }

    private fun ensureByteWork(byteCount: Int): ByteArray {
        val existing = cachedByteWork
        if (existing == null || existing.size < byteCount) {
            val work = ByteArray(byteCount)
            cachedByteWork = work
            return work
        }
        return existing
    }

    /** 写入复用输出缓冲：容量不足才重新分配（大多数情况零分配） */
    private fun setOutputBuffer(outputData: ByteArray) {
        var direct = cachedDirectOutput
        if (direct == null || direct.capacity() < outputData.size) {
            direct = ByteBuffer.allocateDirect(outputData.size).order(ByteOrder.nativeOrder())
            cachedDirectOutput = direct
        }
        direct.clear()
        direct.put(outputData)
        direct.flip()
        outputBuffer = direct
    }

    /** PCM 字节 → Float32，写入 target（复用） */
    private fun toFloatArray(data: ByteArray, target: FloatArray) {
        if (isFloat) {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
            for (i in target.indices) {
                target[i] = bb.float
            }
        } else {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
            for (i in target.indices) {
                target[i] = bb.short.toFloat() / Short.MAX_VALUE.toFloat()
            }
        }
    }

    /** Float32 → PCM 字节，写入 target（复用） */
    private fun toByteArray(data: FloatArray, target: ByteArray) {
        if (isFloat) {
            val bb = ByteBuffer.wrap(target).order(ByteOrder.nativeOrder())
            for (f in data) {
                bb.putFloat(f)
            }
        } else {
            val bb = ByteBuffer.wrap(target).order(ByteOrder.nativeOrder())
            for (f in data) {
                val clamped = f.coerceIn(-1f, 1f)
                bb.putShort(
                    (clamped * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                )
            }
        }
    }
}