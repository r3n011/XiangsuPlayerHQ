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

    /** USB DAC 时钟采样率：native 层解析 UAC FORMAT_TYPE 得到，写入前先重采样到该速率 */
    private var usbTargetRate = 48000

    /** USB DAC 声道数（bNrChannels）：超过等时包带宽的声道数据会被总线丢弃 */
    private var usbDacChannels = 2

    /** USB DAC 子帧字节数（bSubframeSize）：等时包容量按此计算，用户位深超过它须降级 */
    private var usbDacBps = 2

    // ── 重采样状态（跨缓冲区保持，保证帧连续性） ──
    private var resampleAcc = 0.0
    private val resampleLast = floatArrayOf(0f, 0f)

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

    /**
     * USB 输出有效字节数/采样：
     * = min(用户设置的位深, DAC 子帧字节数 bSubframeSize)。
     *
     * ⚡ 等时包容量 wMaxPacketSize 是按 DAC 子帧大小计算的（rate*ch*bps/1000）。
     * 用户位深超过 DAC 支持时，每帧字节数会超出包容量，超出的数据被总线
     * 丢弃 → 完全无声/爆音。故必须降级到 DAC 子帧大小保证有声。
     */
    private fun effectiveBytesPerSample(): Int {
        val userBps = when (getSettings()?.usbOutputBitDepth?.value
            ?: UsbOutputBitDepth.BITS_32) {
            UsbOutputBitDepth.BITS_16 -> 2
            UsbOutputBitDepth.BITS_24 -> 3
            UsbOutputBitDepth.BITS_32 -> 4
        }
        val dacBps = usbDacBps.coerceIn(1, 4)
        if (userBps > dacBps) {
            Timber.w(
                TAG,
                "USB bit depth degraded: user=${userBps}B -> DAC=$dacBps B (超等时包带宽，降级保证有声)"
            )
        }
        // ⚡ 下限 2：若 DAC 子帧解析异常得到 0/1，降级到 2 而不是输出 1 字节/采样
        //    （1 字节时下方打包器无对应分支 → 输出全零静音，导致完全无声）。
        return userBps.coerceAtMost(dacBps).coerceAtLeast(2)
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        configured = true
        // 重采样目标 = DAC 时钟采样率（USB 独占模式下由 native 解析出来）；
        // 播放数据必须先对齐到 DAC 采样率，否则采样率不匹配会产生咔咔杂音/无声
        val dacRate = UsbAudioOutput.getSampleRate()
        usbTargetRate = if (dacRate in 8000..384000) dacRate else 48000
        resetResampler()
        Timber.d(TAG, "Configured: %dHz, %dch, %s (usb target=%dHz)",
            sampleRate, channelCount, if (isFloat) "FLOAT" else "PCM16", usbTargetRate)
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
            // ⚡ 采样率/声道/子帧动态适配：configure() 只在播放开始时执行一次，播放中激活
            //    USB 独占时仍是旧值。若 DAC 时钟/声道/位深与当前不一致 → 无声/杂音。
            //    每次写入前检测一次并重新对齐（JNI 开销极小）。
            val dacRate = UsbAudioOutput.getSampleRate()
            if (dacRate in 8000..384000 && dacRate != usbTargetRate) {
                Timber.i(
                    TAG,
                    "DAC sample rate changed: %d -> %d (re-aligning resampler)",
                    usbTargetRate, dacRate
                )
                usbTargetRate = dacRate
                resetResampler()
            }
            val dacChannels = UsbAudioOutput.getChannels()
            if (dacChannels in 1..2 && dacChannels != usbDacChannels) {
                Timber.i(TAG, "DAC channels changed: %d -> %d", usbDacChannels, dacChannels)
                usbDacChannels = dacChannels
            }
            val dacBps = UsbAudioOutput.getSubframeSize()
            if (dacBps in 1..4 && dacBps != usbDacBps) {
                Timber.i(TAG, "DAC subframe size changed: %d -> %d", usbDacBps, dacBps)
                usbDacBps = dacBps
            }
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
        resetResampler()
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        configured = false
        resetResampler()
    }

    // ── 格式转换（按位深 16/24/32-bit，立体声 little-endian 打包） ──

    /**
     * 将输入 PCM 转换为 USB 输出位深（little-endian，声道数对齐 DAC）。
     *
     * 输入来源：
     *   - HiFi 模式：Float32 系统字节序（通常 little-endian），sample/frame = 4 字节
     *   - 普通模式：PCM16 little-endian，sample/frame = 2 字节
     *
     * 处理链路：
     *   1. 解码到 [-1,1] 交错 Float 序列（保持原始声道数）
     *   2. 归一化到 DAC 声道数（单声道取 L/R 平均，立体声保持，>2ch 取前两声道）
     *   3. ⚡ 线性重采样到 DAC 时钟采样率 usbTargetRate —— 关键！
     *      播放数据采样率必须与 DAC 时钟一致，否则字节流速率不匹配，
     *      DAC 会以自身时钟解读数据 → 咔咔咔杂音或完全无声
     *   4. 按有效位深打包（16/24/32-bit int LE，降级到 DAC 子帧大小防超带宽）
     */
    private fun convertForUsb(data: ByteArray): ByteArray {
        val bytesPerSample = effectiveBytesPerSample()

        // Step 1: 解码到交错 Float 序列
        val samples: FloatArray = if (isFloat) {
            val n = data.size / Float.SIZE_BYTES
            val arr = FloatArray(n)
            ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asFloatBuffer().get(arr)
            arr
        } else {
            // 输入通常是 PCM16 LE
            val n = data.size / 2
            val arr = FloatArray(n)
            val sb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            for (i in 0 until n) {
                arr[i] = sb.get() / Short.MAX_VALUE.toFloat()
            }
            arr
        }

        // Step 2: 归一化到 DAC 声道数（1ch 单声道取 L/R 平均；2ch 立体声；>2 取前两声道）
        // ⚡ 单声道 DAC 的等时包容量按 1ch 计算，送立体声会超带宽 → 必须适配声道数。
        val outCh = if (usbDacChannels in 1..2) usbDacChannels else 2
        val frameCount = if (channelCount >= 1) samples.size / channelCount else samples.size
        val stereo = FloatArray(frameCount * outCh)
        for (i in 0 until frameCount) {
            val l = when (channelCount) {
                1 -> samples[i]
                else -> samples[i * channelCount]
            }
            val r = when (channelCount) {
                1 -> samples[i]
                else -> samples[i * channelCount + 1]
            }
            if (outCh == 1) {
                stereo[i] = (l + r) / 2f
            } else {
                stereo[i * 2] = l
                stereo[i * 2 + 1] = r
            }
        }

        // Step 3: 重采样到 DAC 时钟采样率
        val rateMatched = resampleStereo(stereo, sampleRate, usbTargetRate, outCh)

        // Step 4: 按 bytesPerSample 打包输出
        val outSize = rateMatched.size * bytesPerSample
        val out = ByteArray(outSize)
        var write = 0
        var s = 0
        when (bytesPerSample) {
            2 -> {
                while (s < rateMatched.size) {
                    val v = (rateMatched[s].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out[write++] = (v and 0xFF).toByte()
                    out[write++] = (v ushr 8 and 0xFF).toByte()
                    s++
                }
            }
            3 -> {
                // 24-bit: little-endian 低字节先写，只用 24 位有符号整数（高位符号扩展）
                while (s < rateMatched.size) {
                    val v = (rateMatched[s].coerceIn(-1f, 1f) * 8388607f).toInt()
                        .coerceIn(-8388608, 8388607)
                    out[write++] = (v and 0xFF).toByte()
                    out[write++] = (v ushr 8 and 0xFF).toByte()
                    out[write++] = (v ushr 16 and 0xFF).toByte()
                    s++
                }
            }
            4 -> {
                // 32-bit: int32 little-endian
                while (s < rateMatched.size) {
                    val v = (rateMatched[s].coerceIn(-1f, 1f) * Int.MAX_VALUE).toLong()
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

    /** 复位重采样状态（换曲 / flush / reset 时调用） */
    private fun resetResampler() {
        resampleAcc = 0.0
        resampleLast[0] = 0f
        resampleLast[1] = 0f
    }

    /**
     * 交错 PCM 线性插值重采样（按 [channels] 声道处理）。
     * [inRate] == [outRate] 时原样返回（快速路径）。
     * 通过 [resampleAcc]（跨缓冲区保持的采样相位）与 [resampleLast]（上一缓冲区尾帧）
     * 保证相邻缓冲区的帧连续性，避免拼接处产生咔哒声。
     */
    private fun resampleStereo(input: FloatArray, inRate: Int, outRate: Int, channels: Int): FloatArray {
        val ch = channels.coerceIn(1, 2)
        if (inRate <= 0 || outRate <= 0 || inRate == outRate) return input
        val step = inRate.toDouble() / outRate.toDouble()
        val frameCount = input.size / ch
        if (frameCount <= 0) return input
        // 输出帧数 = (frameCount-1)/step + 1：保证最后一个输出帧正好落在最后一帧输入上，
        // 避免 +1 让每个缓冲块都多出一帧 → 输出速率系统性高于目标 → DAC FIFO 溢出
        // 丢包 → 播放一段时间后咔咔/无声。
        val outLen = ((frameCount - 1) / step).toInt() + 1
        val out = FloatArray(outLen * ch)
        var outIdx = 0
        var inIdx = 0
        var phase = resampleAcc
        while (outIdx < outLen && inIdx < frameCount) {
            for (c in 0 until ch) {
                val prev = if (inIdx == 0) resampleLast[c] else input[(inIdx - 1) * ch + c]
                val cur = input[inIdx * ch + c]
                out[outIdx * ch + c] = prev + (cur - prev) * phase.toFloat()
            }
            outIdx++
            phase += step
            while (phase >= 1.0) {
                phase -= 1.0
                inIdx++
            }
        }
        resampleAcc = phase
        if (frameCount > 0) {
            resampleLast[0] = input[(frameCount - 1) * ch]
            if (ch == 2) {
                resampleLast[1] = input[(frameCount - 1) * ch + 1]
            }
        }
        return out
    }
}
