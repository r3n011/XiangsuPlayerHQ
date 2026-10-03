@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An [AudioProcessor] that downmixes 5.1 (6-channel) and 7.1 (8-channel) surround audio
 * to stereo (2-channel) PCM using the standard Dolby downmix matrix coefficients.
 *
 * Downmix matrix:
 * ```
 *   L = FL + 0.707·FC + 0.707·SL [+ 0.707·SBL] + 0.707·LFE
 *   R = FR + 0.707·FC + 0.707·SR [+ 0.707·SBR] + 0.707·LFE
 * ```
 *
 * ⚡ 每个输出声道再除以**系数绝对值之和**（5.1 ≈ 3.12，7.1 ≈ 3.83）做归一化。
 * 没有这一步时，六个声道同时接近满幅会叠加到 ~3 倍满幅，写回 16bit / float 时被硬钳到
 * ±满幅 —— 听感就是持续破音（5.1 音源尤其明显）。归一化保证任意输入组合都不削顶，
 * 代价是 5.1 音源整体比立体声音源低约 10dB。
 *
 * FFmpeg output channel order assumed:
 * - 5.1: FL, FR, FC, LFE, SL, SR
 * - 7.1: FL, FR, FC, LFE, SL, SR, SBL, SBR
 *
 * This processor is only active for 6-channel or 8-channel 16-bit PCM / float input.
 * All other formats are passed through without modification.
 */
@UnstableApi
class SurroundDownmixProcessor : AudioProcessor {

    companion object {
        /** Dolby standard surround downmix coefficient: 1/√2 ≈ 0.707 */
        private const val COEFF_SURROUND = 0.707f

        /** LFE (subwoofer) mix coefficient */
        private const val COEFF_LFE = 0.707f

        /** Largest supported surround layout (7.1). */
        private const val MAX_SUPPORTED_CHANNELS = 8

        /** 5.1 单侧输出系数之和：1 + 0.707(FC) + 0.707(SL) + 0.707(LFE)。 */
        private const val NORM_51 = 1f / (1f + COEFF_SURROUND + COEFF_SURROUND + COEFF_LFE)

        /** 7.1 单侧输出系数之和：1 + 0.707(FC) + 0.707(SL) + 0.707(SBL) + 0.707(LFE)。 */
        private const val NORM_71 = 1f / (1f + COEFF_SURROUND * 3f + COEFF_LFE)

        // 5.1 channel indices (FFmpeg order)
        private const val FL_51  = 0
        private const val FR_51  = 1
        private const val FC_51  = 2
        private const val LFE_51 = 3
        private const val SL_51  = 4
        private const val SR_51  = 5

        // 7.1 channel indices (FFmpeg order)
        private const val FL_71  = 0
        private const val FR_71  = 1
        private const val FC_71  = 2
        private const val LFE_71 = 3
        private const val SL_71  = 4
        private const val SR_71  = 5
        private const val SBL_71 = 6
        private const val SBR_71 = 7
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private val floatScratch = FloatArray(MAX_SUPPORTED_CHANNELS)
    private val shortScratch = ShortArray(MAX_SUPPORTED_CHANNELS)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val isSupported = (inputAudioFormat.channelCount == 6 || inputAudioFormat.channelCount == 8)
                && (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)

        return if (isSupported) {
            inputFormat = inputAudioFormat
            outputFormat = AudioFormat(
                inputAudioFormat.sampleRate,
                /* channelCount = */ 2,
                inputAudioFormat.encoding
            )
            outputFormat
        } else {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            inputAudioFormat // pass-through
        }
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        val channelCount = inputFormat.channelCount
        val isFloat = inputFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) Float.SIZE_BYTES else Short.SIZE_BYTES
        val bytesPerFrame = channelCount * bytesPerSample
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        outputBuffer = ensureOutputBuffer(frameCount * 2 * bytesPerSample)
        val source = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val is51 = channelCount == 6

        if (isFloat) {
            val floatInput = source.asFloatBuffer()
            repeat(frameCount) {
                floatInput.get(floatScratch, 0, channelCount)
                val left = if (is51) downmix51Left(floatScratch) else downmix71Left(floatScratch)
                val right = if (is51) downmix51Right(floatScratch) else downmix71Right(floatScratch)
                outputBuffer.putFloat(left.coerceIn(-1f, 1f))
                outputBuffer.putFloat(right.coerceIn(-1f, 1f))
            }
        } else {
            val shortInput = source.asShortBuffer()
            repeat(frameCount) {
                shortInput.get(shortScratch, 0, channelCount)
                for (ch in 0 until channelCount) {
                    floatScratch[ch] = shortScratch[ch] / 32768f
                }
                val left = if (is51) downmix51Left(floatScratch) else downmix71Left(floatScratch)
                val right = if (is51) downmix51Right(floatScratch) else downmix71Right(floatScratch)
                outputBuffer.putShort(toPcm16(left))
                outputBuffer.putShort(toPcm16(right))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    private fun ensureOutputBuffer(requiredCapacity: Int): ByteBuffer {
        return if (outputBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder()).also {
                outputBuffer = it
            }
        } else {
            outputBuffer.clear()
            outputBuffer
        }
    }

    override fun getOutput(): ByteBuffer {
        val pending = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pending
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() { inputEnded = true }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    @Suppress("DEPRECATION")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }

    /** Float(-1..1) → 16bit，带钳位兜底（归一化后正常不会触发）。 */
    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    /** Left channel for a 5.1 surround frame (FL, FR, FC, LFE, SL, SR)，已归一化。 */
    private fun downmix51Left(s: FloatArray): Float =
        (s[FL_51] + COEFF_SURROUND * s[FC_51] + COEFF_SURROUND * s[SL_51] + COEFF_LFE * s[LFE_51]) * NORM_51

    /** Right channel for a 5.1 surround frame (FL, FR, FC, LFE, SL, SR)，已归一化。 */
    private fun downmix51Right(s: FloatArray): Float =
        (s[FR_51] + COEFF_SURROUND * s[FC_51] + COEFF_SURROUND * s[SR_51] + COEFF_LFE * s[LFE_51]) * NORM_51

    /** Left channel for a 7.1 surround frame (FL, FR, FC, LFE, SL, SR, SBL, SBR)，已归一化。 */
    private fun downmix71Left(s: FloatArray): Float =
        (s[FL_71] + COEFF_SURROUND * s[FC_71] + COEFF_SURROUND * s[SL_71] + COEFF_SURROUND * s[SBL_71] + COEFF_LFE * s[LFE_71]) * NORM_71

    /** Right channel for a 7.1 surround frame (FL, FR, FC, LFE, SL, SR, SBL, SBR)，已归一化。 */
    private fun downmix71Right(s: FloatArray): Float =
        (s[FR_71] + COEFF_SURROUND * s[FC_71] + COEFF_SURROUND * s[SR_71] + COEFF_SURROUND * s[SBR_71] + COEFF_LFE * s[LFE_71]) * NORM_71
}
