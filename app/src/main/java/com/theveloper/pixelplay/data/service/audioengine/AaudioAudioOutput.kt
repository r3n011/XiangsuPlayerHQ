package com.theveloper.pixelplay.data.service.audioengine

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import timber.log.Timber

/**
 * 基于 NDK AAudio 的 Media3 [AudioOutput] 实现。
 *
 * 与 [AudioTrackAudioOutput](androidx.media3.exoplayer.audio.AudioTrackAudioOutput) 行为对齐：
 * - write 为阻塞写（最多 200ms），未写满时返回 false，由 DefaultAudioSink 背压重试
 * - 位置报告基于 AAudioStream_getFramesRead（已播放帧数）
 * - 不支持 offload / 播放参数变速 / 辅助音效，全部返回默认值
 *
 * 注意：AAudio 仅 Android O（API 26）+ 可用，调用方需做版本判断。
 */
@OptIn(UnstableApi::class)
class AaudioAudioOutput private constructor(
    private val config: AudioOutputProvider.OutputConfig
) : AudioOutput {

    companion object {
        private const val TAG = "AaudioAudioOutput"

        /** 通过 provider 创建；失败返回 null（上层转 InitializationException） */
        fun create(config: AudioOutputProvider.OutputConfig): AaudioAudioOutput? {
            val channels = Integer.bitCount(config.channelMask)
            if (channels < 1 || channels > 8) return null
            val format = if (config.encoding == C.ENCODING_PCM_FLOAT) 1 else 0
            return try {
                val instance = AaudioAudioOutput(config)
                instance.handle = AaudioNativeOutput.nativeCreate(config.sampleRate, channels, format)
                if (instance.handle == 0L) {
                    Timber.e(TAG, "nativeCreate failed: rate=%d ch=%d fmt=%d",
                        config.sampleRate, channels, format)
                    null
                } else instance
            } catch (t: Throwable) {
                Timber.e(TAG, "create failed", t)
                null
            }
        }
    }

    private var handle: Long = 0L
    private val listeners = CopyOnWriteArrayList<AudioOutput.Listener>()

    /** 未消费完的写缓冲（nativeWrite 返回部分字节时保留，等待下次继续写） */
    private var pendingData: ByteArray? = null
    private var pendingOffset = 0
    private var volume = 1f
    private var hasBeenStopped = false

    override fun play() {
        if (handle == 0L) return
        if (!hasBeenStopped) {
            AaudioNativeOutput.nativeStart(handle)
        }
    }

    override fun pause() {
        if (handle == 0L) return
        AaudioNativeOutput.nativePause(handle)
    }

    override fun write(
        buffer: ByteBuffer,
        encodedAccessUnitCount: Int,
        presentationTimeUs: Long
    ): Boolean {
        if (handle == 0L) throw AudioOutput.WriteException(-1, false)
        if (pendingData == null) {
            val bytesRemaining = buffer.remaining()
            if (bytesRemaining == 0) return true
            // 拷贝整个剩余缓冲；即使本次只写入部分，也依赖副本继续写
            val data = ByteArray(bytesRemaining)
            buffer.get(data)
            pendingData = data
            pendingOffset = 0
        }
        val data = pendingData!!
        val remaining = data.size - pendingOffset
        if (remaining <= 0) {
            pendingData = null
            pendingOffset = 0
            return true
        }
        val written = AaudioNativeOutput.nativeWrite(handle, data, pendingOffset, remaining)
        if (written < 0) {
            throw AudioOutput.WriteException(written, false)
        }
        pendingOffset += written
        if (pendingOffset >= data.size) {
            pendingData = null
            pendingOffset = 0
            return true
        }
        return false
    }

    override fun flush() {
        pendingData = null
        pendingOffset = 0
        hasBeenStopped = false
        if (handle != 0L) {
            AaudioNativeOutput.nativeFlush(handle)
        }
    }

    override fun stop() {
        if (hasBeenStopped) return
        hasBeenStopped = true
        pendingData = null
        pendingOffset = 0
        if (handle != 0L) {
            AaudioNativeOutput.nativeStop(handle)
        }
    }

    override fun release() {
        if (handle != 0L) {
            AaudioNativeOutput.nativeRelease(handle)
            handle = 0L
        }
        listeners.clear()
        Timber.d(TAG, "released")
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        if (handle != 0L) {
            AaudioNativeOutput.nativeSetVolume(handle, this.volume)
        }
    }

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = C.AUDIO_SESSION_ID_UNSET

    override fun getSampleRate(): Int =
        if (handle != 0L) AaudioNativeOutput.nativeGetSampleRate(handle) else config.sampleRate

    override fun getBufferSizeInFrames(): Long =
        if (handle != 0L) AaudioNativeOutput.nativeGetBufferCapacityInFrames(handle) else 0L

    override fun getPositionUs(): Long {
        if (handle == 0L) return 0L
        val frames = AaudioNativeOutput.nativeGetFramesRead(handle)
        val rate = getSampleRate()
        return if (rate > 0) frames * 1_000_000L / rate else 0L
    }

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun isStalled(): Boolean = false

    override fun addListener(listener: AudioOutput.Listener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: AudioOutput.Listener) {
        listeners.remove(listener)
    }

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) {
        // AAudio 不支持变速：忽略，速度由 Media3 Sonic 处理器控制
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) {
        // AAudio 走系统默认路由，不支持指定设备
    }
}
