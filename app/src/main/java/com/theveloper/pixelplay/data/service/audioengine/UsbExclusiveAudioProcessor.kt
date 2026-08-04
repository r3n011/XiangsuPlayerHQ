package com.theveloper.pixelplay.data.service.audioengine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.service.usb.UsbAudioOutput
import com.theveloper.pixelplay.presentation.components.UsbDacEntryPoint
import com.theveloper.pixelplay.presentation.components.UsbDacWithAudioEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB 独占输出 AudioProcessor
 *
 * 作为 DefaultAudioProcessorChain 的最后一个处理器（HiFiEngine 之后），
 * 将最终输出的 PCM 数据原样透传给 AudioSink（系统 AudioTrack/AAudio），
 * 同时在 USB DAC 独占模式激活时，把同样的音频数据镜像写入 USB DAC。
 *
 * USB 输出位深由 AudioEngineSettings.usbOutputBitDepth 决定，支持 16/24/32-bit
 * 立体声 little-endian，按位深打包到等时 OUT 端点。
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

    /** 应用上下文缓存（用于 Hilt EntryPoint 获取 AudioEngineSettings） */
    private var audioEngineSettings: AudioEngineSettings? = null

    private fun getSettings(): AudioEngineSettings? {
        if (audioEngineSettings != null) return audioEngineSettings
        return try {
            val ctx = com.theveloper.pixelplay.PixelPlayApplication.appContext()
            val entryPoint: UsbDacWithAudioEngineEntryPoint =
                EntryPointAccessors.fromApplication(ctx)
            entryPoint.audioEngineSettings.also {
                audioEngineSettings = it
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun currentBytesPerSample(): Int {
        return when (getSettings()?.usbOutputBitDepth?.value
            ?: UsbOutputBitDepth.BITS_32) {
            UsbOutputBitDepth.BITS_16 -> 2
            UsbOutputBitDepth.BITS_24 -> 3
            UsbOutputBitDepth.BITS_32 -> 4
        }
    }

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

    // ── 格式转换（按位深 16/24/32-bit，立体声 little-endian 打包） ──

    /**
     * 将输入 PCM 转换为 USB 输出位深（little-endian，固定为立体声输出）。
     *
     * 输入来源：
     *   - HiFi 模式：Float32 系统字节序（通常 little-endian），sample/frame = 4 字节
     *   - 普通模式：PCM16 little-endian，sample/frame = 2 字节
     *
     * 输出目标：
     *   - 16-bit：int16 LE
     *   - 24-bit：int24 LE（3 字节/样本，高位对齐）
     *   - 32-bit：int32 LE（4 字节/样本）
     */
    private fun convertForUsb(data: ByteArray): ByteArray {
        val bytesPerSample = currentBytesPerSample()
        val inSamplesPerCh: Int
        val samples: FloatArray

        // Step 1: 把输入 PCM 规整到 [-1, 1] 浮点数样本序列（按交错通道）
        if (isFloat) {
            inSamplesPerCh = data.size / Float.SIZE_BYTES
            samples = FloatArray(inSamplesPerCh)
            val fb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asFloatBuffer()
            fb.get(samples)
        } else {
            // 输入通常是 PCM16
            inSamplesPerCh = data.size / 2
            samples = FloatArray(inSamplesPerCh)
            val sb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            for (i in 0 until inSamplesPerCh) {
                samples[i] = sb.get() / Short.MAX_VALUE.toFloat()
            }
        }

        // Step 2: 按 bytesPerSample 打包输出
        val outSize = inSamplesPerCh * bytesPerSample
        val out = ByteArray(outSize)
        var write = 0
        var s = 0
        when (bytesPerSample) {
            2 -> {
                while (s < inSamplesPerCh) {
                    val v = (samples[s].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out[write++] = (v and 0xFF).toByte()
                    out[write++] = (v ushr 8 and 0xFF).toByte()
                    s++
                }
            }
            3 -> {
                // 24-bit: little-endian 低字节先写，只用 24 位有符号整数（高位符号扩展）
                while (s < inSamplesPerCh) {
                    val v = (samples[s].coerceIn(-1f, 1f) * 8388607f).toInt()
                        .coerceIn(-8388608, 8388607)
                    out[write++] = (v and 0xFF).toByte()
                    out[write++] = (v ushr 8 and 0xFF).toByte()
                    out[write++] = (v ushr 16 and 0xFF).toByte()
                    s++
                }
            }
            4 -> {
                // 32-bit: int32 little-endian
                while (s < inSamplesPerCh) {
                    val v = (samples[s].coerceIn(-1f, 1f) * Int.MAX_VALUE).toLong()
                        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                    out[write++] = (v and 0xFF).toByte()
                    out[write++] = (v ushr 8 and 0xFF).toByte()
                    out[write++] = (v ushr 16 and 0xFF).toByte()
                    out[write++] = (v ushr 24 and 0xFF).toByte()
                    s++
                }
            }
        }
        return out
    }
}
