package com.theveloper.pixelplay.data.service.audioengine

import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AAudio [AudioOutputProvider]，接入 Media3 1.10.1 的
 * [androidx.media3.exoplayer.audio.DefaultAudioSink.Builder.setAudioOutputProvider]。
 *
 * 只支持线性 PCM（PCM16/FLOAT32，1..8 声道），其余格式返回 UNSUPPORTED，
 * 由 DefaultAudioSink/上层转码。不支持 offload / 隧道 / 播放参数变速。
 */
@OptIn(UnstableApi::class)
class AaudioAudioOutputProvider : AudioOutputProvider {

    companion object {
        private const val TAG = "AaudioProvider"
    }

    private val listeners = CopyOnWriteArrayList<AudioOutputProvider.Listener>()

    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        val format = formatConfig.format
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) {
            // 非 PCM 交由上层转码成 PCM 后再输出
            return AudioOutputProvider.FormatSupport.UNSUPPORTED
        }
        val channels = format.channelCount
        val supported = (format.pcmEncoding == C.ENCODING_PCM_FLOAT ||
                format.pcmEncoding == C.ENCODING_PCM_16BIT) &&
                channels in 1..8
        return if (supported) {
            AudioOutputProvider.FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                .build()
        } else {
            AudioOutputProvider.FormatSupport.UNSUPPORTED
        }
    }

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        val format = formatConfig.format
        val channelMask = getAudioOutputChannelConfig(format.channelCount)
        val pcmFrameSize = Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)
        val preferredBufferSize = if (formatConfig.preferredBufferSize != C.LENGTH_UNSET) {
            formatConfig.preferredBufferSize
        } else {
            // 默认约 200ms 缓冲（字节）
            format.sampleRate * format.channelCount * pcmFrameSize / 5
        }
        return AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(format.pcmEncoding)
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .setBufferSize(preferredBufferSize)
            .setAudioAttributes(formatConfig.audioAttributes)
            .setAudioSessionId(formatConfig.audioSessionId)
            .setVirtualDeviceId(formatConfig.virtualDeviceId)
            .build()
    }

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        return AaudioAudioOutput.create(config)
            ?: throw AudioOutputProvider.InitializationException()
    }

    override fun addListener(listener: AudioOutputProvider.Listener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: AudioOutputProvider.Listener) {
        listeners.remove(listener)
    }

    override fun setClock(clock: Clock) = Unit

    override fun release() {
        listeners.clear()
    }

    /** 声道数 → 输出 channel mask（与 AudioTrackAudioOutputProvider 一致） */
    private fun getAudioOutputChannelConfig(channelCount: Int): Int = when (channelCount) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
        4 -> AudioFormat.CHANNEL_OUT_QUAD
        5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
        6 -> AudioFormat.CHANNEL_OUT_5POINT1
        7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
        8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        else -> AudioFormat.CHANNEL_OUT_STEREO
    }
}
