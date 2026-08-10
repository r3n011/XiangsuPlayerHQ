package com.theveloper.pixelplay.data.service.audioengine

import android.media.AudioDeviceInfo
import android.os.SystemClock
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

        /** 判定流"停滞"的阈值：流已启动但 framesRead 长时间不前进 */
        private const val STALL_DETECT_MS = 3_000L

        /** 看门狗轮询间隔：避免每次 write 都做 JNI 调用 */
        private const val PROGRESS_CHECK_INTERVAL_MS = 500L

        /** 内部重启的最大次数，超过后抛错交给上层（DefaultAudioSink/DualPlayerEngine）重建 */
        private const val MAX_INTERNAL_RESTARTS = 2

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
                } else {
                    // ⚡ 计算启动水位阈值：流缓冲容量的一半（帧→字节）。
                    //    启动前先把缓冲填到该水位，避免"首帧即启动"在缓冲不足时
                    //    underrun 反复爆音（切歌瞬间持续数秒噪音的根因）。
                    val capFrames = AaudioNativeOutput.nativeGetBufferCapacityInFrames(instance.handle)
                    val bytesPerSample = if (config.encoding == C.ENCODING_PCM_FLOAT) 4L else 2L
                    val bytesPerFrame = bytesPerSample * channels
                    instance.startThresholdBytes =
                        (capFrames * bytesPerFrame / 2).coerceAtLeast(bytesPerFrame)
                    instance
                }
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
    /** 是否已请求播放（play() 已调用）但 native 流尚未真正启动 */
    private var playRequested = false
    /** 是否处于暂停态（DefaultAudioSink.pause() 已调用）。
     *  与 playRequested 不同：暂停后 playRequested 也是 false，但输出并未被重建，
     *  暂停期间渲染线程仍会约 1s 一次重试 write()，auto-start 必须被禁止； */
    private var paused = false
    /** native 流是否已处于运行态（STARTED） */
    private var streamStarted = false
    /** 启动水位阈值（字节）：启动前需已写入的缓冲量，防止 underrun 噪音 */
    private var startThresholdBytes = 0L
    /** 启动前已写入的字节数累计 */
    private var bytesAccumulatedBeforeStart = 0L
    /** 累计写入 AAudio 的字节总数（相对 flush 基准）。用于区分"真停滞"（有数据未消费）与"数据已消费完"（等在 EOS/新数据） */
    private var totalBytesWritten = 0L
    /** 每帧字节数（帧↔字节换算用） */
    private val bytesPerFrame: Int =
        (if (config.encoding == C.ENCODING_PCM_FLOAT) 4 else 2) * Integer.bitCount(config.channelMask)
    /** 上次观察到 framesRead 前进的时刻 */
    private var lastProgressElapsedMs = SystemClock.elapsedRealtime()
    /** 上次观察到的 framesRead */
    private var lastProgressFramesRead = 0L
    /** 看门狗轮询节流 */
    private var lastProgressCheckElapsedMs = 0L
    /** 连续内部重启次数（流停滞自愈用） */
    private var restartCount = 0
    /** 数据耗尽（DRAINED）日志节流时间戳 */
    private var lastDrainedLogMs = 0L

    override fun play() {
        if (handle == 0L) return
        if (!hasBeenStopped) {
            playRequested = true
            paused = false
            // ⚡ 启动修复：不立即 requestStart，等缓冲填到水位（startThresholdBytes）后再启动，
            //    避免空/浅缓冲启动 → underrun → 切歌瞬间出现约 3s 的杂音/爆音。
            //    若暂停恢复时缓冲已足够（累计量达标），立即启动（失败不抛错，
            //    write() 中的兜底逻辑会重试并把真正的错误抛给上层）。
            if (!streamStarted && bytesAccumulatedBeforeStart >= startThresholdBytes) {
                tryStartStream()
            }
        }
    }

    override fun pause() {
        if (handle == 0L) return
        paused = true
        AaudioNativeOutput.nativePause(handle)
        streamStarted = false
        // ⚡ 暂停必须清掉 playRequested：否则渲染线程在暂停期间约 1s 一次的重试 write()
        //    会在 write() 里命中「首帧写入后 nativeStart」逻辑，把已暂停的流重新启动，
        //    导致暂停后每隔一秒冒一声。清掉后暂停期间的写入只会静默缓冲、永不重启流。
        playRequested = false
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
        if (written > 0) totalBytesWritten += written

        // ⚡ 启动逻辑不再依赖 playRequested：DefaultAudioSink 在部分场景
        // （isStalled→flush 重建输出、缓冲水位达标前的 play()）不会重新调用 play()。
        // 若只凭 playRequested 启动，流会永远停在 UNSTARTED → 数据只进不出 →
        // framesRead 冻结 → 10s 后被 StuckPlayerDetector 抛出 StuckPlayerException。
        // 只要正在写入数据、未被显式 stop 且未暂停，达到启动水位就启动
        // （暂停期间渲染线程的重试 write() 会被 paused 挡下，不会偷偷出声）。
        if (written > 0 && !streamStarted && !hasBeenStopped && !paused) {
            bytesAccumulatedBeforeStart += written
            if (bytesAccumulatedBeforeStart >= startThresholdBytes) {
                startStreamOrThrow()
            }
        } else if (written == 0 && !streamStarted && !hasBeenStopped && !paused) {
            // 缓冲已写满但仍未启动（缓冲容量小于水位阈值时的极端情况）：
            // 数据只进不出，必须强制启动，否则位置永远不前进。
            startStreamOrThrow()
        }

        // ⚡ 停滞看门狗：流已启动但 framesRead 长时间不前进 → AAudio HAL 未消费数据
        // （underrun 未恢复 / 缓冲卡死 / 音频路由变更等）。先内部重启流；连续重启
        // 无效则抛错，让上层错误恢复重建输出/播放器（不再干等 10s 被 StuckPlayerDetector 判死）。
        if (streamStarted) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastProgressCheckElapsedMs >= PROGRESS_CHECK_INTERVAL_MS) {
                lastProgressCheckElapsedMs = now
                val framesRead = AaudioNativeOutput.nativeGetFramesRead(handle)
                if (framesRead != lastProgressFramesRead) {
                    lastProgressFramesRead = framesRead
                    lastProgressElapsedMs = now
                    restartCount = 0
                } else if (now - lastProgressElapsedMs >= STALL_DETECT_MS) {
                    // ⚡ 数据已全部消费完（等在 EOS / 新数据）不算停滞：
                    // 此时重启/重建会破坏 DefaultAudioSink 的 writtenFrames ↔ 位置
                    // 对应关系 → hasPendingData() 永远 true → EOS 永远不达 →
                    // 歌曲播完卡死（"回到歌曲开头 / 不继续播放"的根因）。
                    // 仅当还有未消费数据却停滞（真正 HAL 卡死）时才内部重启。
                    val dataPendingFrames = totalBytesWritten / bytesPerFrame - framesRead
                    if (dataPendingFrames > 0) {
                        if (restartCount >= MAX_INTERNAL_RESTARTS) {
                            Timber.e(
                                TAG,
                                "AAudio stream stalled beyond $MAX_INTERNAL_RESTARTS restarts — surfacing error to force rebuild"
                            )
                            restartCount = 0
                            throw AudioOutput.WriteException(-1, false)
                        }
                        restartCount++
                        restartStream()
                    } else {
                        // 数据已消费完毕：静默更新基准，避免每次 write 重复判定
                        lastProgressElapsedMs = now
                        // 🔍 切歌定位日志：数据耗尽后 framesRead 是否追上 writtenFrames？
                        // 若 framesRead < writtenFrames（差值>0），说明尾部数据没被 AAudio 消费，
                        // DefaultAudioSink 的 hasPendingData() 永远 true → EOS 永远不达 → 无法自动下一首！
                        val drainedAt = SystemClock.elapsedRealtime()
                        if (drainedAt - lastDrainedLogMs >= 1000L) {
                            lastDrainedLogMs = drainedAt
                            val writtenFrames = totalBytesWritten / bytesPerFrame
                            Timber.d(
                                TAG,
                                "DRAINED framesRead=$framesRead writtenFrames=$writtenFrames gap=${writtenFrames - framesRead} posUs=${getPositionUs()}"
                            )
                        }
                    }
                }
            }
        }

        if (pendingOffset >= data.size) {
            pendingData = null
            pendingOffset = 0
            return true
        }
        return false
    }

    /**
     * 启动 native 流。失败时仅记录日志并保持未启动状态，
     * 后续 write() 的兜底逻辑会再次尝试并把错误抛给上层。
     */
    private fun tryStartStream(): Boolean {
        val rc = AaudioNativeOutput.nativeStart(handle)
        if (rc == 0) {
            streamStarted = true
            lastProgressElapsedMs = SystemClock.elapsedRealtime()
            return true
        }
        Timber.e(TAG, "nativeStart failed rc=%d", rc)
        streamStarted = false
        return false
    }

    /** 启动 native 流；失败立即抛错（比静默停滞 10s 更快触发上层恢复） */
    private fun startStreamOrThrow() {
        if (!tryStartStream()) {
            throw AudioOutput.WriteException(-1, false)
        }
    }

    /** 停滞自愈：仅重启同一 AAudio 流，保持位置基准连续 */
    private fun restartStream() {
        Timber.w(
            TAG,
            "AAudio stream stalled (data pending but no framesRead progress for ${STALL_DETECT_MS}ms), restarting attempt #$restartCount"
        )
        // ⚡ 不能调用 nativeStop：它会把 flush_base_frames 重置到当前 read，
        // 而 DefaultAudioSink 的 writtenFrames 不会因底层重启清零 → 位置突然相对
        // 变小 → hasPendingData() 永远 true → EOS 永远不达 → 歌曲播完卡死 10s。
        // nativeRestart 仅 requestStop → requestStart、不动基准：framesRead 连续累计，
        // 尾部缓冲消费完后 framesRead == writtenFrames，EOS 正常送达，自动切下一首。
        val rc = runCatching { AaudioNativeOutput.nativeRestart(handle) }.getOrDefault(-1)
        if (rc == 0) {
            streamStarted = true
        } else {
            streamStarted = false
            bytesAccumulatedBeforeStart = 0L
        }
        lastProgressElapsedMs = SystemClock.elapsedRealtime()
        lastProgressFramesRead = AaudioNativeOutput.nativeGetFramesRead(handle)
        lastProgressCheckElapsedMs = SystemClock.elapsedRealtime()
    }

    override fun flush() {
        pendingData = null
        pendingOffset = 0
        bytesAccumulatedBeforeStart = 0L
        totalBytesWritten = 0L
        hasBeenStopped = false
        playRequested = false
        paused = false
        streamStarted = false
        restartCount = 0
        lastProgressElapsedMs = SystemClock.elapsedRealtime()
        lastProgressFramesRead = 0L
        lastProgressCheckElapsedMs = SystemClock.elapsedRealtime()
        if (handle != 0L) {
            AaudioNativeOutput.nativeFlush(handle)
        }
    }

    override fun stop() {
        if (hasBeenStopped) return
        hasBeenStopped = true
        pendingData = null
        pendingOffset = 0
        bytesAccumulatedBeforeStart = 0L
        totalBytesWritten = 0L
        playRequested = false
        paused = true
        streamStarted = false
        restartCount = 0
        lastProgressElapsedMs = SystemClock.elapsedRealtime()
        lastProgressFramesRead = 0L
        lastProgressCheckElapsedMs = SystemClock.elapsedRealtime()
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

    override fun isStalled(): Boolean {
        if (handle == 0L || !streamStarted || hasBeenStopped) return false
        // ⚡ 数据已全部消费完（等在 EOS / 更多数据）不算停滞：
        // 返回 true 会让 DefaultAudioSink 执行 flush → 释放并重建 AudioOutput，
        // 重建后位置从 0 起算 → 播完被"拉回开头"，且 EOS 状态被破坏。
        // 只有还有未消费数据却长时间不前进（真正 HAL 卡死）才算停滞。
        val framesRead = AaudioNativeOutput.nativeGetFramesRead(handle)
        if (totalBytesWritten / bytesPerFrame - framesRead <= 0) return false
        if (SystemClock.elapsedRealtime() - lastProgressElapsedMs >= STALL_DETECT_MS) {
            // 🔍 停滞定位日志：真·HAL 卡死（有数据未消费但 framesRead 不前进）
            Timber.w(
                TAG,
                "isStalled()=true framesRead=$framesRead writtenFrames=${totalBytesWritten / bytesPerFrame} " +
                    "gap=${totalBytesWritten / bytesPerFrame - framesRead}"
            )
            return true
        }
        return false
    }

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
