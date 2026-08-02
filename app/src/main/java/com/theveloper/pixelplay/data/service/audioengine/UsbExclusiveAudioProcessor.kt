package com.theveloper.pixelplay.data.service.audioengine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.service.usb.UsbAudioOutput
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB 独占输出 AudioProcessor
 *
 * 作为 DefaultAudioProcessorChain 的最后一个处理器（HiFiEngine 之后），
 * 将最终输出的 PCM 数据原样透传给 AudioSink（系统 AudioTrack），
 * 同时在 USB DAC 独占模式激活时，把同样的音频数据镜像写入 USB DAC
 * （转换为 16bit 立体声 little-endian，等时 OUT 端点）。
 *
 * 数据原样透传：getOutput 返回与输入完全一致的字节，不影响系统播放链路；
 * 仅当 UsbAudioOutput.isActive() 时才做 USB 镜像。
 */
@UnstableApi
class UsbExclusiveAudioProcessor : AudioProcessor {

    companion object {
        private const val TAG = "UsbExclusiveProcessor"
    }

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var configured = false

    private var sampleRate = 44100
    private var channelCount = 2
    private var isFloat = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        configured = true
        Timber.d(TAG, "Configured: %dHz, %dch, %s",
            sampleRate, channelCount, if (isFloat) "FLOAT" else "PCM16")
        // 输出格式与输入格式一致（透传）
        return inputAudioFormat
    }

    override fun isActive(): Boolean = configured

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!configured) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        // 透传：原样拷贝到输出缓冲区（消费输入）
        val out = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        out.put(inputBuffer)
        out.flip()
        outputBuffer = out

        // USB 独占模式激活时，镜像到 USB DAC
        if (UsbAudioOutput.isActive()) {
            val pcm = ByteArray(remaining)
            out.duplicate().get(pcm)
            UsbAudioOutput.writePcm(convertForUsb(pcm))
        }
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata)")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        configured = false
    }

    // ── 格式转换（USB DAC 统一按 16bit 立体声输出） ──

    private fun convertForUsb(data: ByteArray): ByteArray {
        return if (isFloat) {
            // Float32 → PCM16（little-endian）
            val sampleCount = data.size / Float.SIZE_BYTES
            val fb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val out = ByteBuffer.allocate(sampleCount * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount) {
                val f = fb.get().coerceIn(-1f, 1f)
                out.putShort((f * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            out.array()
        } else {
            // PCM16 直接透传（USB 音频为 little-endian，与 nativeOrder 一致）
            data
        }
    }
}
