package com.theveloper.pixelplay.data.service.player

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.flac.FlacExtractor
import com.theveloper.pixelplay.data.diagnostics.PerformanceMetrics
import com.theveloper.pixelplay.data.preferences.MusicQualityCatalog
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import com.theveloper.pixelplay.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

import com.theveloper.pixelplay.data.netease.NeteaseStreamProxy
import com.theveloper.pixelplay.data.service.audioengine.AudioProcessorProvider
import com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy
import com.theveloper.pixelplay.data.qqmusic.QqMusicStreamProxy
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics

data class ActiveDecoderInfo(
    val name: String,
    val isHardware: Boolean
)

internal fun shouldResumeAfterTransientAudioFocusLoss(
    masterPlayWhenReady: Boolean,
    masterIsPlaying: Boolean,
    transitionRunning: Boolean,
    auxiliaryPlayWhenReady: Boolean,
    auxiliaryIsPlaying: Boolean
): Boolean {
    return masterPlayWhenReady ||
        masterIsPlaying ||
        (transitionRunning && (auxiliaryPlayWhenReady || auxiliaryIsPlaying))
}

internal fun shouldDisableAudioOffloadByDefaultForDevice(
    manufacturer: String,
    brand: String,
    model: String,
    hardware: String,
    sdkInt: Int
): Boolean {
    val manufacturerName = manufacturer.trim().lowercase()
    val brandName = brand.trim().lowercase()
    val modelName = model.trim().lowercase()
    val hardwareName = hardware.trim().lowercase()

    val isXiaomiFamilyDevice = manufacturerName == "xiaomi" ||
        brandName == "xiaomi" ||
        brandName == "redmi" ||
        brandName == "poco"
    if (isXiaomiFamilyDevice && sdkInt >= 36) return true

    // Google Pixel devices on SDK 37+ (Android 16 QPR / 17 preview) exhibit an audio
    // offload HAL bug where the Opus position counter jumps ~49 seconds at a time,
    // causing audible skips and incorrect position restoration on player rebuild.
    val isGooglePixelDevice = manufacturerName == "google" || brandName == "google"
    if (isGooglePixelDevice && sdkInt >= 37) return true

    val isLavaDevice =
        manufacturerName == "lava" ||
            brandName == "lava"
    val looksLikeMtkHardware =
        hardwareName.startsWith("mt") ||
            hardwareName.contains("mediatek") ||
            hardwareName.contains("mtk")
    val isReportedLxxFamily = modelName.startsWith("lxx") && isLavaDevice
    val isMtkLavaVariant = isLavaDevice && looksLikeMtkHardware

    return sdkInt >= 35 && (isReportedLxxFamily || isMtkLavaVariant)
}

internal fun shouldTriggerAudioOffloadStallFallback(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    isCurrentMasterPlayer: Boolean,
    mediaIdMatches: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackSuppressionReason: Int
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        isCurrentMasterPlayer &&
        mediaIdMatches &&
        playWhenReady &&
        !isPlaying &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED
}

/**
 * Decides whether an early STATE_BUFFERING (within ~500ms of audio playing) should be read
 * as a HAL offload reset and trigger disabling offload for the session.
 *
 * The buffering is NOT treated as a HAL reset when it is explained by a recent user seek
 * ([isPostSeekBuffering]) or by a just-finished crossfade ([isPostTransitionBuffering]) —
 * in those cases the buffering is expected, and disabling offload would needlessly drop the
 * battery saving and rebuild the player (an audible glitch).
 */
internal fun shouldDisableAudioOffloadOnEarlyBuffering(
    audioOffloadEnabled: Boolean,
    transitionRunning: Boolean,
    lastPlayingAtMs: Long,
    timeSincePlayingMs: Long,
    isPostSeekBuffering: Boolean,
    isPostTransitionBuffering: Boolean,
    isPostMediaItemTransition: Boolean
): Boolean {
    return audioOffloadEnabled &&
        !transitionRunning &&
        lastPlayingAtMs > 0L &&
        timeSincePlayingMs < 500L &&
        !isPostSeekBuffering &&
        !isPostTransitionBuffering &&
        !isPostMediaItemTransition
}

/** ExoPlayer [DefaultLoadControl] buffer durations (ms) for a build of the player. */
internal data class LoadControlBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

/**
 * Picks the buffer profile for the player. On memory-constrained devices the maximum
 * prefetch depth is reduced to cap peak RAM — with time-based buffering the buffered RAM is
 * bitrate × seconds, so a 60 s window on a hi-res lossless track (plus a second buffered
 * player during a crossfade) can be tens of MB. Shrinking the *time* window (not switching
 * to a byte threshold) keeps start latency and cross-format uniformity identical to the
 * normal profile; only how far ahead we prefetch changes, which is free for local files and
 * still ample for remote streams. Normal-RAM devices are unchanged.
 */
internal fun loadControlBufferProfileFor(isLowRamDevice: Boolean): LoadControlBufferProfile {
    return if (isLowRamDevice) {
        LoadControlBufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
    } else {
        LoadControlBufferProfile(
            minBufferMs = 30_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 5_000
        )
    }
}

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player. During a crossfade the MediaSession can
 * expose Player B early for UI continuity, while Player A remains alive to fade out.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
    private val telegramStreamProxy: com.theveloper.pixelplay.data.telegram.TelegramStreamProxy,
    private val neteaseStreamProxy: NeteaseStreamProxy,
    private val qqMusicStreamProxy: QqMusicStreamProxy,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: com.theveloper.pixelplay.data.jellyfin.JellyfinStreamProxy,
    private val gdriveStreamProxy: com.theveloper.pixelplay.data.gdrive.GDriveStreamProxy,
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager,
    private val connectivityStateHolder: com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val musicRepository: MusicRepository,
    private val lxJsEngine: com.theveloper.pixelplay.data.lx.LxJsEngine,
    private val builtInSourceSearchApi: com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi,
    private val bilibiliSearchApi: com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi,
    private val audioEngineSettings: com.theveloper.pixelplay.data.service.audioengine.AudioEngineSettings,
    private val audioProcessorProvider: AudioProcessorProvider,
    private val audioVisualizer: com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer,
) {
    private companion object {
        private const val AUDIO_OFFLOAD_STALL_FALLBACK_MS = 4_000L
        // Grace window after a crossfade/transition during which the STATE_BUFFERING
        // "HAL offload reset" heuristic is suppressed. Right after the player swap the new
        // master (the former auxiliary) has just started, so a brief buffering blip there
        // must NOT be mistaken for a HAL underflow — doing so would disable audio offload
        // for the whole session (losing the battery saving) and rebuild the player (an
        // audible glitch right after the fade). This keeps offload enabled across crossfades.
        private const val POST_TRANSITION_OFFLOAD_GUARD_MS = 2_000L
        private const val MAX_AUXILIARY_TIMELINE_ITEMS = 200
        private val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "cloud", "bilibili")
        // Subset of REMOTE_MEDIA_SCHEMES: schemes that need proxy resolution.
        // http/https resolve directly and must NOT enter the resolvedUriCache lookup path.
        private val CLOUD_PROXY_SCHEMES = setOf("telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "cloud", "bilibili")
    }

    data class TransitionTarget(
        val mediaItem: MediaItem,
        val absoluteIndex: Int,
        val queueSize: Int
    )

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var hiFiModeEnabled: Boolean = false
        private set
    private var hiFiEngineProcessor: com.theveloper.pixelplay.data.service.audioengine.HiFiEngineAudioProcessor? = null
    private var audioOffloadEnabled = !shouldDisableAudioOffloadByDefault()
    // 广播电台等实时流媒体模式：禁用 audio offload，规避部分设备 HAL 对直播流的周期性杂音
    private var streamingModeEnabled = false
    private var transitionJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private var transitionRunning = false
    private var preResolutionJob: Job? = null
    private var queueSnapshot: List<MediaItem> = emptyList()
    private var activeWindowStartIndex = 0
    private var activePlayerUsesWindowedQueue = false
    private var preparedWindowStartIndex = 0
    private var preparedPlayerUsesWindowedQueue = false

    // Proxy port tracking: detects when a proxy restarts with a new port
    // so we can invalidate stale resolved URI cache entries.
    private var lastKnownNeteasePort: Int = 0
    private var lastKnownQqMusicPort: Int = 0
    private var lastKnownNavidromePort: Int = 0
    private var lastKnownJellyfinPort: Int = 0
    private var lastKnownGDrivePort: Int = 0
    private var lastKnownTelegramPort: Int = 0
    private val mediaItemRetryCount = ConcurrentHashMap<String, Int>()
    private val MAX_RETRIES_PER_ITEM = 3

    private lateinit var playerA: ExoPlayer
    private var preferredAudioDevice: android.media.AudioDeviceInfo? = null
    private var playerB: ExoPlayer? = null

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionDisplayPlayerListeners = mutableListOf<(Player) -> Unit>()
    private val onTransitionFinishedListeners = mutableListOf<() -> Unit>()

    private var onPlayerAboutToBeReleasedListener: ((Player) -> Unit)? = null

    fun setOnPlayerAboutToBeReleasedListener(listener: (Player) -> Unit) {
        onPlayerAboutToBeReleasedListener = listener
    }
    
    // Active Audio Session ID Flow
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _activeDecoderInfo = MutableStateFlow<ActiveDecoderInfo?>(null)
    val activeDecoderInfo: StateFlow<ActiveDecoderInfo?> = _activeDecoderInfo.asStateFlow()

    // Audio Focus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null // AudioFocusRequest on API 26+, or null on API 24-25
    private var isFocusLossPause = false
    private var lastPlayWhenReadyAtMs: Long = 0L
    private var lastPlayingAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a user seek from a real HAL offload
    // reset (where audio underflows mid-playback). Without this, seeking shortly after
    // playback starts re-enters BUFFERING within the HAL-reset window and triggers a full
    // player rebuild, which leaves the MediaSession briefly pointing at the released player
    // and silently drops any subsequent seeks.
    private var lastSeekAtMs: Long = 0L
    // Used to distinguish a STATE_BUFFERING caused by a song transition from a real HAL offload reset.
    private var lastMediaItemTransitionAtMs: Long = 0L
    // Diagnostics: timestamp when the master player entered STATE_BUFFERING, used to
    // measure buffering->ready (playback prepare) durations for the performance report.
    private var bufferingStartedAtMs: Long = 0L
    // Diagnostics: timestamp when the most recent crossfade/transition started.
    private var transitionStartedAtMs: Long = 0L
    // Timestamp when the most recent crossfade/transition finished. Used to give the new
    // master a grace window before the HAL-offload-reset heuristic can fire, so a crossfade
    // can never spuriously disable audio offload (battery) or trigger a player rebuild.
    private var lastTransitionFinishedAtMs: Long = 0L

    /**
     * Whether ExoPlayer audio offload is currently enabled for this session. Exposed
     * read-only for the diagnostic performance report. Offload is disabled at runtime
     * when a HAL stall/reset is detected (see [disableAudioOffloadForSession]).
     */
    val isAudioOffloadEnabled: Boolean
        get() = audioOffloadEnabled

    /** Lightweight, allocation-cheap snapshot of the live audio format, for diagnostics. */
    data class AudioFormatSnapshot(
        val sampleMimeType: String?,
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int,
        val bitrate: Int
    )

    /** Returns the current master-player audio format, or null when nothing is decoding. */
    fun currentAudioFormatSnapshot(): AudioFormatSnapshot? {
        if (!::playerA.isInitialized) return null
        val format = playerA.audioFormat ?: return null
        fun Int.orZero() = if (this == Format.NO_VALUE) 0 else this
        val bitrate = when {
            format.averageBitrate != Format.NO_VALUE -> format.averageBitrate
            format.peakBitrate != Format.NO_VALUE -> format.peakBitrate
            else -> 0
        }
        return AudioFormatSnapshot(
            sampleMimeType = format.sampleMimeType,
            sampleRate = format.sampleRate.orZero(),
            channelCount = format.channelCount.orZero(),
            pcmEncoding = format.pcmEncoding.orZero(),
            bitrate = bitrate
        )
    }

    /**
     * Set by MusicService once ReplayGain for the incoming track is known.
     * The crossfade loop reads this at the end instead of hard-coding 1f,
     * so the incoming track reaches its correct RG volume without a jump.
     * Reset to null after each transition.
     */
    var incomingTrackReplayGainVolume: Float? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB?.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                val auxiliaryPlayer = playerB
                isFocusLossPause = shouldResumeAfterTransientAudioFocusLoss(
                    masterPlayWhenReady = playerA.playWhenReady,
                    masterIsPlaying = playerA.isPlaying,
                    transitionRunning = transitionRunning,
                    auxiliaryPlayWhenReady = auxiliaryPlayer?.playWhenReady == true,
                    auxiliaryIsPlaying = auxiliaryPlayer?.isPlaying == true
                )
                playerA.playWhenReady = false
                auxiliaryPlayer?.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB?.playWhenReady = true
                }
            }
        }
    }

    // Listener to attach to the active master player (playerA)
    private val masterPlayerListener = object : Player.Listener, AnalyticsListener, ExoPlayer.AudioOffloadListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                lastPlayWhenReadyAtMs = SystemClock.elapsedRealtime()
                requestAudioFocus()
                scheduleAudioOffloadFallbackIfNeeded(playerA)
                // ⚡ 恢复播放：若交叉淡入淡出仍在进行，同步恢复辅助播放器，
                //    否则 incoming 曲目停在暂停态，切歌后会无声。
                if (transitionRunning) {
                    playerB?.let { auxiliaryPlayer ->
                        if (auxiliaryPlayer.playbackState != Player.STATE_IDLE && !auxiliaryPlayer.playWhenReady) {
                            auxiliaryPlayer.playWhenReady = true
                            if (!auxiliaryPlayer.isPlaying) auxiliaryPlayer.play()
                        }
                    }
                }
            } else {
                cancelAudioOffloadFallback()
                // ⚡ 用户暂停：过渡中的 incoming 播放器必须同步暂停。
                //    交叉淡入淡出期间点击暂停，若只停 playerA（对外暴露的 master），
                //    playerB 里的 incoming 曲目仍会继续以淡入音量出声 → 「暂停后还在出声」。
                //
                //    ⚡ 但必须排除 pauseAtEndOfMediaItems 触发的「曲尾自动暂停」
                //   （reason = END_OF_MEDIA_ITEM）：淡出窗口内旧曲到达 EOS 时 ExoPlayer
                //   会置 playWhenReady=false，这不是用户暂停——若此时把正在淡入的
                //   incoming 一并暂停，fade 结束 swap 后新曲就停在暂停态，表现为
                //   「单曲/列表循环 + 淡入淡出时，淡入到下一曲歌曲会暂停」。
                if (transitionRunning &&
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    playerB?.let { auxiliaryPlayer ->
                        if (auxiliaryPlayer.playWhenReady || auxiliaryPlayer.isPlaying) {
                            auxiliaryPlayer.playWhenReady = false
                            auxiliaryPlayer.pause()
                        }
                    }
                }
                // Keep focus across user pauses so a quick resume doesn't have to re-acquire it.
                // Focus is abandoned explicitly on AUDIOFOCUS_LOSS and on release(); anything in
                // between (user pause/play) keeps the request alive to avoid contention races
                // that occasionally caused press-play to auto-pause after a short wait.
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastPlayingAtMs = SystemClock.elapsedRealtime()
                cancelAudioOffloadFallback()
            }
        }

        /**
         * Fires when ExoPlayer believes the audio HAL is producing output via
         * offload and the renderer thread can stop polling — at that point the
         * CPU genuinely doesn't need a wake lock to keep playing audio. When
         * [sleepingForOffload] flips back to false (track change, format
         * mismatch, fallback path), restore [C.WAKE_MODE_LOCAL] so the
         * non-offload PCM path keeps the CPU awake correctly.
         *
         * Battery: this is what actually lets the SoC race-to-sleep during
         * music playback. The static [C.WAKE_MODE_LOCAL] we set at build time
         * is the safe default; this callback is the dynamic optimisation.
         */
        @Suppress("UnsafeOptInUsageError")
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            if (!::playerA.isInitialized) return
            // Only override the wake mode for local media. Remote schemes need
            // C.WAKE_MODE_NETWORK to keep the wifi lock; we never want to drop
            // that to NONE.
            val baseMode = wakeModeFor(playerA.currentMediaItem)
            val desiredMode = if (sleepingForOffload && baseMode == C.WAKE_MODE_LOCAL) {
                C.WAKE_MODE_NONE
            } else {
                baseMode
            }
            if (currentWakeMode == desiredMode) return

            try {
                playerA.setWakeMode(desiredMode)
                playerB?.setWakeMode(desiredMode)
                currentWakeMode = desiredMode
                Timber.tag("DualPlayerEngine").d(
                    "Wake mode -> %d (sleepingForOffload=%b)",
                    desiredMode,
                    sleepingForOffload
                )
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").w(e, "Failed to apply offload-aware wake mode")
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHardware = AudioDecoderPolicy.isLikelyHardwareDecoder(decoderName)
            _activeDecoderInfo.value = ActiveDecoderInfo(decoderName, isHardware)
            PerformanceMetrics.recordTiming(
                PerformanceMetrics.Timings.AUDIO_DECODER_INIT,
                initializationDurationMs
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_decoder_initialized"
            ) {
                mapOf(
                    "decoderName" to decoderName,
                    "isHardware" to isHardware.toString(),
                    "initializationDurationMs" to initializationDurationMs.toString()
                )
            }
            Timber.tag("DualPlayerEngine").d("Audio decoder initialized: %s (Hardware: %b)", decoderName, isHardware)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            // Record the live format (channels, sample rate, bit depth) as the report's
            // source of multichannel / bit-depth data — these aren't stored in the library DB.
            PerformanceMetrics.recordPlaybackFormat(
                channelCount = if (format.channelCount == Format.NO_VALUE) 0 else format.channelCount,
                sampleRate = if (format.sampleRate == Format.NO_VALUE) 0 else format.sampleRate,
                pcmEncoding = if (format.pcmEncoding == Format.NO_VALUE) 0 else format.pcmEncoding
            )
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "audio_format_changed"
            ) {
                mapOf(
                    "mime" to (format.sampleMimeType ?: "unknown"),
                    "sampleRate" to format.sampleRate.toString(),
                    "channels" to format.channelCount.toString(),
                    "pcmEncoding" to format.pcmEncoding.toString(),
                    "bitrate" to format.bitrate.toString()
                )
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != 0 && _activeAudioSessionId.value != audioSessionId) {
                _activeAudioSessionId.value = audioSessionId
                AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                    type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                    name = "audio_session_changed"
                ) {
                    mapOf("audioSessionId" to audioSessionId.toString())
                }
                Timber.tag("TransitionDebug").d("Master audio session changed: %d", audioSessionId)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 🔍 切歌定位日志。reason: 0=UNKNOWN 1=AUTO(自动切歌) 2=SEEK(手动) 3=PLAYLIST_CHANGED(列表变化)
            // repeat: 0=OFF 1=ONE 2=ALL ; state: 1=IDLE 2=BUFFERING 3=READY 4=ENDED
            android.util.Log.d(
                "LxPlayer",
                "=== 切歌 onMediaItemTransition idx=${playerA.currentMediaItemIndex}/${playerA.mediaItemCount} " +
                    "reason=$reason title=${mediaItem?.mediaMetadata?.title} " +
                    "uri=${mediaItem?.localConfiguration?.uri} " +
                    "pos=${playerA.currentPosition}ms dur=${playerA.duration}ms " +
                    "state=${playerA.playbackState} repeat=${playerA.repeatMode} " +
                    "playing=${playerA.isPlaying} pwr=${playerA.playWhenReady} ==="
            )
            lastMediaItemTransitionAtMs = SystemClock.elapsedRealtime()
            cancelAudioOffloadFallback()
            // 根据当前媒体是否为广播电台流切换流式模式（禁用 audio offload）
            updateStreamingModeFor(mediaItem?.mediaId)
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                name = "media_item_transition",
                elapsedRealtimeMs = lastMediaItemTransitionAtMs
            ) {
                mapOf(
                    "reason" to reason.toString(),
                    "scheme" to (mediaItem?.localConfiguration?.uri?.scheme ?: "unknown")
                )
            }
            
            // If the transition was not automatic (e.g. user skip or playlist change),
            // immediately cancel any background crossfade logic to ensure responsiveness.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                cancelNext()
            }

            val uri = mediaItem?.localConfiguration?.uri
            if (uri?.scheme == "telegram") {
                scope.launch {
                    val result = telegramRepository.resolveTelegramUri(uri.toString())
                    val fileId = result?.first
                    telegramCacheManager.setActivePlayback(fileId)
                    Timber.tag("DualPlayerEngine").d("Telegram playback active: fileId=$fileId")
                }
            } else {
                telegramCacheManager.setActivePlayback(null)
            }
            applyWakeModeForCurrentItem()

            // --- Pre-Resolve Next/Prev Tracks with Debounce to prevent flooding ---
            preResolutionJob?.cancel()
            preResolutionJob = scope.launch {
                delay(600) // Wait for user to stop skipping/navigating
                try {
                    val currentIndex = playerA.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        // Resolve each neighbour directly — no intermediate list allocation.
                        if (currentIndex + 1 < playerA.mediaItemCount) {
                            playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                        if (currentIndex - 1 >= 0) {
                            playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri
                                ?.takeIf { it.scheme in CLOUD_PROXY_SCHEMES }
                                ?.let { resolveCloudUri(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Pre-resolution error")
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (transitionRunning) return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || queueSnapshot.isEmpty()) {
                refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // 🔍 播放状态定位日志。state: 1=IDLE 2=BUFFERING 3=READY 4=ENDED
            // repeat: 0=OFF 1=ONE 2=ALL。ENDED 说明 EOS 已送达；READY 停滞且 pos≈dur 说明 EOS 不达。
            android.util.Log.d(
                "LxPlayer",
                "=== 播放状态 onPlaybackStateChanged state=$playbackState " +
                    "idx=${playerA.currentMediaItemIndex} pos=${playerA.currentPosition}ms dur=${playerA.duration}ms " +
                    "repeat=${playerA.repeatMode} playing=${playerA.isPlaying} pwr=${playerA.playWhenReady} ==="
            )
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = now
                    AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                        type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                        name = "playback_buffering",
                        elapsedRealtimeMs = now
                    )
                    val timeSincePlayingMs = now - lastPlayingAtMs
                    val timeSinceSeekMs = now - lastSeekAtMs
                    val timeSinceTransitionMs = now - lastTransitionFinishedAtMs
                    val timeSinceMediaItemTransitionMs = now - lastMediaItemTransitionAtMs
                    val isPostSeekBuffering = lastSeekAtMs > 0L && timeSinceSeekMs < 1_500L
                    val isPostTransitionBuffering = lastTransitionFinishedAtMs > 0L &&
                        timeSinceTransitionMs < POST_TRANSITION_OFFLOAD_GUARD_MS
                    val isPostMediaItemTransition = lastMediaItemTransitionAtMs > 0L &&
                        timeSinceMediaItemTransitionMs < 2_000L
                    if (shouldDisableAudioOffloadOnEarlyBuffering(
                            audioOffloadEnabled = audioOffloadEnabled,
                            transitionRunning = transitionRunning,
                            lastPlayingAtMs = lastPlayingAtMs,
                            timeSincePlayingMs = timeSincePlayingMs,
                            isPostSeekBuffering = isPostSeekBuffering,
                            isPostTransitionBuffering = isPostTransitionBuffering,
                            isPostMediaItemTransition = isPostMediaItemTransition
                        )
                    ) {
                        disableAudioOffloadForSession(
                            reason = "HAL offload reset detected: STATE_BUFFERING after ${timeSincePlayingMs}ms of playback"
                        )
                    } else {
                        scheduleAudioOffloadFallbackIfNeeded(playerA)
                    }
                }
                Player.STATE_READY -> {
                    if (bufferingStartedAtMs > 0L) {
                        val prepareDurationMs = SystemClock.elapsedRealtime() - bufferingStartedAtMs
                        PerformanceMetrics.recordTiming(
                            PerformanceMetrics.Timings.PLAYBACK_PREPARE,
                            prepareDurationMs
                        )
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
                            name = "playback_ready_after_buffering"
                        ) {
                            mapOf("prepareDurationMs" to prepareDurationMs.toString())
                        }
                        bufferingStartedAtMs = 0L
                    }
                    // 倍速恢复：在线流加载就绪后确保倍速（含变调音高）不丢失
                    if (desiredPlaybackSpeed != 1f && playerA.playbackParameters.speed != desiredPlaybackSpeed) {
                        playerA.playbackParameters = PlaybackParameters(desiredPlaybackSpeed, desiredPlaybackPitch)
                    }
                    scheduleAudioOffloadFallbackIfNeeded(playerA)
                }
                Player.STATE_IDLE -> {
                    bufferingStartedAtMs = 0L
                    cancelAudioOffloadFallback()
                }
                Player.STATE_ENDED -> {
                    bufferingStartedAtMs = 0L
                    cancelAudioOffloadFallback()
                    // 🔍 EOS 已送达（播放器自然播完）。若这里没日志，说明 EOS 从未到达 → 无法自动切歌
                    android.util.Log.d(
                        "LxPlayer",
                        "STATE_ENDED reached: idx=${playerA.currentMediaItemIndex} " +
                            "pos=${playerA.currentPosition}ms dur=${playerA.duration}ms " +
                            "repeat=${playerA.repeatMode} next=${playerA.hasNextMediaItem()}"
                    )
                    // 广播电台直播流可能因服务端主动断开/超时而无错误地进入 ENDED，
                    // 此时自动重新连接，避免播放静默停止。
                    if (streamingModeEnabled &&
                        playerA.currentMediaItem?.mediaId?.startsWith("radio://") == true
                    ) {
                        Timber.tag("DualPlayerEngine").w("Radio stream ended — reconnecting")
                        scope.launch {
                            delay(800)
                            runCatching {
                                if (!::playerA.isInitialized) return@runCatching
                                val mediaId = playerA.currentMediaItem?.mediaId.orEmpty()
                                fadeInVolume(playerA, mediaId)
                                playerA.seekToDefaultPosition()
                                playerA.prepare()
                                playerA.play()
                            }
                        }
                    }
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // 🔍 位置跳变日志。reason: 0=UNKNOWN 1=SEEK 2=SEEK_ADJUSTMENT 3=INTERNAL 4=AUTO_TRANSITION 5=REMOVE 6=REPEAT 7=AD_INSERTION
            android.util.Log.d(
                "LxPlayer",
                "位置跳变 onPositionDiscontinuity: ${oldPosition.positionMs}ms->${newPosition.positionMs}ms reason=$reason " +
                    "idx=${playerA.currentMediaItemIndex}/${playerA.mediaItemCount} dur=${playerA.duration}ms"
            )
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                lastSeekAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun addMasterPlayerListeners(player: ExoPlayer) {
        player.addListener(masterPlayerListener)
        player.addAnalyticsListener(masterPlayerListener)
        player.addAudioOffloadListener(masterPlayerListener)
    }

    private fun removeMasterPlayerListeners(player: ExoPlayer) {
        player.removeListener(masterPlayerListener)
        player.removeAnalyticsListener(masterPlayerListener)
        player.removeAudioOffloadListener(masterPlayerListener)
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    fun addTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.add(listener)
    }

    fun removeTransitionDisplayPlayerListener(listener: (Player) -> Unit) {
        onTransitionDisplayPlayerListeners.remove(listener)
    }

    fun addTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.add(listener)
    }

    /**
     * Notifies the engine that an external caller (UI seek, etc.) is about to issue a
     * seek through the MediaController. Used to mark the upcoming STATE_BUFFERING as
     * seek-driven so the HAL-reset heuristic does not trigger a player rebuild that
     * would race with the in-flight seek command.
     *
     * Setting this here (synchronously, before the seek dispatches) is more reliable
     * than waiting for onPositionDiscontinuity, which is delivered on the next event
     * batch and can race with onPlaybackStateChanged on some Media3 versions.
     */
    fun notifyExternalSeekInitiated() {
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    fun removeTransitionFinishedListener(listener: () -> Unit) {
        onTransitionFinishedListeners.remove(listener)
    }

    val masterPlayer: Player
        get() {
            initialize()
            return playerA
        }

    fun isTransitionRunning(): Boolean = transitionRunning

    fun isUsingWindowedQueue(): Boolean = activePlayerUsesWindowedQueue

    fun getFullQueue(): List<MediaItem> = ensureQueueSnapshot()

    fun getCurrentAbsoluteIndex(): Int {
        if (!::playerA.isInitialized) return 0
        val mediaItem = playerA.currentMediaItem ?: return playerA.currentMediaItemIndex.coerceAtLeast(0)
        val snapshot = ensureQueueSnapshot()
        val index = resolveCurrentAbsoluteIndex(mediaItem, snapshot)
        return if (index == C.INDEX_UNSET) {
            if (activePlayerUsesWindowedQueue) {
                (activeWindowStartIndex + playerA.currentMediaItemIndex).coerceIn(0, (snapshot.size - 1).coerceAtLeast(0))
            } else {
                playerA.currentMediaItemIndex.coerceAtLeast(0)
            }
        } else {
            index
        }
    }

    fun triggerAdjacentPreResolution() {
        if (!::playerA.isInitialized) return
        preResolutionJob?.cancel()
        val currentIndex = playerA.currentMediaItemIndex
        if (currentIndex != C.INDEX_UNSET) {
            val adjacentCloudUris = mutableListOf<Uri>()
            if (currentIndex + 1 < playerA.mediaItemCount) {
                playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }
            if (currentIndex - 1 >= 0) {
                playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let { uri ->
                    if (uri.scheme in REMOTE_MEDIA_SCHEMES) adjacentCloudUris.add(uri)
                }
            }

            if (adjacentCloudUris.isNotEmpty()) {
                preResolutionJob = scope.launch {
                    delay(600) // Wait for user to stop skipping/navigating
                    try {
                        for (uriToResolve in adjacentCloudUris) {
                            resolveCloudUri(uriToResolve)
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution triggered manually")
                    }
                }
            }
        }
    }

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    /**
     * 用户设定的基础音量。AI 电台闪避结束后始终恢复到该值，
     * 避免闪避把 playerA.volume 拉到低电平后无法自行恢复（声音变小回不来）。
     */
    private var companionBaseVolume = 1f

    fun setVolume(volume: Float) {
        companionBaseVolume = volume.coerceIn(0f, 1f)
        if (::playerA.isInitialized) {
            playerA.volume = companionBaseVolume
        }
    }

    // ─── AI 电台音量闪避（ducking）───────────────────────────────
    // AI 陪伴播报时把当前播放音量平滑压到低电平（音乐渐出），播报结束平滑恢复
    // （音乐渐入）。与 crossfade 叠加后呈现"音乐压低 → AI 评说 → 音乐回到下一曲"的电台感。
    private var companionDuckJob: Job? = null
    private var companionDuckRestoreVolume = 1f

    /** AI 说话期间调用：true 压音乐，false 恢复 */
    fun setCompanionDucked(
        ducked: Boolean,
        fadeOutMs: Long = 450L,
        fadeInMs: Long = 800L
    ) {
        companionDuckJob?.cancel()
        val duckLevel = 0.05f

        // scope 基于 Dispatchers.Main，这里整个动画连同 ExoPlayer 的读写都放到主线程，
        // 避免在 IO 线程访问 ExoPlayer（Player is accessed on the wrong thread）。
        companionDuckJob = scope.launch {
            if (ducked) {
                // 以用户设定的基础音量为恢复目标，而非当前已（可能被上次闪避拉低）的 playerA.volume
                companionDuckRestoreVolume = companionBaseVolume.coerceIn(0f, 1f)
                val restoreVol = companionDuckRestoreVolume
                val startMs = SystemClock.uptimeMillis()
                val durationMs = fadeOutMs.coerceIn(60L, 6000L)
                while (true) {
                    val p = ((SystemClock.uptimeMillis() - startMs) / durationMs.toFloat()).coerceIn(0f, 1f)
                    val eased = p * p * (3f - 2f * p) // smoothstep
                    val vol = restoreVol + (duckLevel - restoreVol) * eased
                    setCompanionDuckRawVolume(vol)
                    if (p >= 1f) break
                    delay(16)
                }
                setCompanionDuckRawVolume(duckLevel)
            } else {
                val restored = companionDuckRestoreVolume.coerceIn(0f, 1f)
                val from = if (::playerA.isInitialized) playerA.volume else companionBaseVolume
                val startMs = SystemClock.uptimeMillis()
                val durationMs = fadeInMs.coerceIn(60L, 6000L)
                while (true) {
                    val p = ((SystemClock.uptimeMillis() - startMs) / durationMs.toFloat()).coerceIn(0f, 1f)
                    val eased = p * p * (3f - 2f * p) // smoothstep
                    val vol = from + (restored - from) * eased
                    setCompanionDuckRawVolume(vol)
                    if (p >= 1f) break
                    delay(16)
                }
                companionDuckRestoreVolume = restored
                setCompanionDuckRawVolume(restored)
            }
        }
    }

    private fun setCompanionDuckRawVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1f)
        if (::playerA.isInitialized) {
            playerA.volume = v
        }
        // 过渡/淡入下一曲时 playerB 也可能是当前出声的一侧，同步闪避与恢复
        playerB?.volume = v
    }

    /**
     * Clears the resolved URI cache. This should be called when restoring playback
     * to ensure fresh play URLs are fetched for cloud songs (e.g., Netease, QQ Music).
     */
    fun clearResolvedUriCache() {
        resolvedUriCache.evictAll()
    }

    private var isReleased = false

    /**
     * 解析后的真实直链缓存（占位 URI → 直链）。
     * ⚡ 带 TTL：网易云/QQ/酷我等直链有时效（通常 10-30 分钟），过期后返回 403/410。
     *   此前无 TTL——歌曲播放一半退出后重播，无条件命中旧直链导致播放失败，
     *   且部分错误恢复路径（非代理类错误）不清缓存，失败会反复复现。
     *   现在 TTL 过期的条目视为未解析，强制走完整解析链拿新鲜直链。
     */
    private val resolvedUriCache = LruCache<String, ResolvedUriEntry>(100)

    /** 直链缓存有效期：与各流媒体代理内部的 15 分钟 TTL 对齐 */
    private val resolvedUriTtlMs: Long = 15L * 60_000L

    private class ResolvedUriEntry(val uri: Uri, val atMs: Long)

    private fun getFreshResolvedUri(key: String): Uri? {
        val entry = resolvedUriCache.get(key) ?: return null
        if (SystemClock.elapsedRealtime() - entry.atMs > resolvedUriTtlMs) {
            resolvedUriCache.remove(key)
            return null
        }
        return entry.uri
    }

    private fun putResolvedUri(key: String, uri: Uri) {
        resolvedUriCache.put(key, ResolvedUriEntry(uri, SystemClock.elapsedRealtime()))
    }

    /** 用户期望的倍速，引擎在每次就绪时自动恢复 */
    var desiredPlaybackSpeed: Float = 1f
        private set

    /** 用户期望的音高（变调开关开启时 == 倍速），引擎在每次就绪时自动恢复 */
    var desiredPlaybackPitch: Float = 1f
        private set

    /** 设置倍速，立即应用到当前活跃播放器，并记住设置以便后续恢复 */
    fun setPlaybackSpeed(speed: Float, pitch: Float = 1f) {
        val clamped = speed.coerceIn(0.5f, 2f)
        desiredPlaybackSpeed = clamped
        desiredPlaybackPitch = pitch.coerceIn(0.5f, 2f)
        val params = PlaybackParameters(clamped, desiredPlaybackPitch)
        if (::playerA.isInitialized) playerA.playbackParameters = params
        playerB?.playbackParameters = params
    }

    // Whether the OS classifies this as a low-RAM device. Used to cap the player's max
    // prefetch depth so hi-res/lossless buffering (and the second player during a crossfade)
    // can't balloon peak memory on constrained hardware. Cached: it never changes at runtime.
    private val isLowRamDevice: Boolean by lazy {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            try { playerA.release() } catch (e: Exception) { /* Ignore */ }
        }
        playerB?.let { try { it.release() } catch (e: Exception) { /* Ignore */ } }
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)

        _activeAudioSessionId.value = playerA.audioSessionId
        isReleased = false
        queueSnapshot = emptyList()
        activeWindowStartIndex = 0
        activePlayerUsesWindowedQueue = false
        resetPreparedWindowState()
        // Clear stale resolved URI cache to avoid using proxy URLs from old ports
        checkAndUpdateProxyPorts()
        clearAllResolvedCache()
        mediaItemRetryCount.clear()

        // ⚡ 预启动 netease / qqmusic 代理（非阻塞）
        //   - 这确保在播放恢复前代理端口已分配，
        //     避免 ResolvingDataSource 的 5s 等待触发
        neteaseStreamProxy.startIfNeeded()
        qqMusicStreamProxy.startIfNeeded()
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: use AudioFocusRequest (modern API)
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                // Let the system queue our request behind a transient holder instead of failing.
                // Pairs with the AUDIOFOCUS_GAIN handler below: on DELAYED we pause and mark the
                // pause as focus-driven so the eventual GAIN callback resumes playback.
                .setAcceptsDelayedFocusGain(true)
                .build()

            val result = audioManager.requestAudioFocus(request)
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    audioFocusRequest = request
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    audioFocusRequest = request
                    isFocusLossPause = true
                    playerA.playWhenReady = false
                    if (transitionRunning) playerB?.playWhenReady = false
                }
                else -> {
                    Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
                    playerA.playWhenReady = false
                }
            }
        } else {
            // API 24-25: use the legacy audio focus API
            val result = audioManager.requestAudioFocus(
                focusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    audioFocusRequest = focusChangeListener
                }
                else -> {
                    Timber.tag("TransitionDebug").w("AudioFocus (legacy) Request Failed: $result")
                    playerA.playWhenReady = false
                }
            }
        }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request is AudioFocusRequest) {
            audioManager.abandonAudioFocusRequest(request)
        } else {
            // Legacy API: abandon using the focus change listener
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        audioFocusRequest = null
    }

    private fun scheduleAudioOffloadFallbackIfNeeded(player: ExoPlayer) {
        cancelAudioOffloadFallback()
        if (!audioOffloadEnabled || transitionRunning || !player.playWhenReady || player.isPlaying) return
        if (!isLikelyLocalMedia(player.currentMediaItem)) return

        val watchedMediaId = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return
        bufferingFallbackJob = scope.launch {
            delay(AUDIO_OFFLOAD_STALL_FALLBACK_MS)

            val currentMediaId = player.currentMediaItem?.mediaId
            val shouldFallback = shouldTriggerAudioOffloadStallFallback(
                audioOffloadEnabled = audioOffloadEnabled,
                transitionRunning = transitionRunning,
                isCurrentMasterPlayer = player === playerA,
                mediaIdMatches = currentMediaId == watchedMediaId,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackSuppressionReason = player.playbackSuppressionReason
            )
            if (!shouldFallback) return@launch

            disableAudioOffloadForSession(
                reason = "Local media did not produce audio for " +
                    "${AUDIO_OFFLOAD_STALL_FALLBACK_MS}ms (state=${player.playbackState})"
            )
        }
    }

    private fun cancelAudioOffloadFallback() {
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
    }

    private fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
    }

    private fun wakeModeFor(mediaItem: MediaItem?): Int {
        val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
            C.WAKE_MODE_NETWORK
        } else {
            C.WAKE_MODE_LOCAL
        }
    }

    private var currentWakeMode: Int = C.WAKE_MODE_LOCAL

    private fun applyWakeModeForCurrentItem() {
        if (!::playerA.isInitialized) return
        val mode = wakeModeFor(playerA.currentMediaItem)
        if (currentWakeMode == mode) return
        
        try {
            playerA.setWakeMode(mode)
            playerB?.setWakeMode(mode)
            currentWakeMode = mode
            Timber.tag("DualPlayerEngine").d("Wake mode updated to %d", mode)
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Failed to update wake mode")
        }
    }

    private fun shouldDisableAudioOffloadByDefault(): Boolean {
        return shouldDisableAudioOffloadByDefaultForDevice(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    private fun disableAudioOffloadForSession(reason: String) {
        if (!audioOffloadEnabled) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload fallback during active transition. %s", reason)
            return
        }

        audioOffloadEnabled = false
        PerformanceMetrics.recordOffloadFallback(reason, SystemClock.elapsedRealtime())
        rebuildPlayersPreservingMasterState(
            logMessage = "Audio offload disabled for current session. $reason"
        )
    }

    /**
     * 禁用 audio offload（不重建播放器）。
     * 供"播完卡死看门狗"使用：歌曲已在末尾停滞（offload HAL 未发 EOS），
     * 直接停用 offload 并应用到现有播放器，避免重播时再次卡死；
     * 不重建播放器，防止时间线被清空导致 mini player 消失。
     */
    fun disableAudioOffloadWithoutRebuild() {
        if (!audioOffloadEnabled) return
        if (transitionRunning) {
            Timber.tag("DualPlayerEngine").w("Skipping offload disable during active transition.")
            return
        }
        audioOffloadEnabled = false
        val offloadPrefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()
        if (::playerA.isInitialized) {
            runCatching {
                playerA.trackSelectionParameters = playerA.trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(offloadPrefs)
                    .build()
            }
        }
        playerB?.let { aux ->
            runCatching {
                aux.trackSelectionParameters = aux.trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(offloadPrefs)
                    .build()
            }
        }
        PerformanceMetrics.recordOffloadFallback(
            "TrackEndWatchdog disabled offload (no rebuild)",
            SystemClock.elapsedRealtime()
        )
    }

    private fun rebuildPlayersPreservingMasterState(logMessage: String) {
        cancelAudioOffloadFallback()
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_start"
        ) {
            mapOf("reason" to logMessage)
        }

        val desiredPlayWhenReady = playerA.playWhenReady
        // Guard against snapshotting a position that landed during a bad early-startup seek
        // (e.g. an offload stall rebuild firing while the player is at a spurious offset).
        // Positions under 5s on first playback are more likely noise than intent.
        val positionMs = if (playerA.currentPosition > 5_000L) playerA.currentPosition else 0L
        val currentIndex = playerA.currentMediaItemIndex.coerceAtLeast(0)
        // Pre-sized ArrayList avoids the IntRange object and the extra copy produced by .map.
        val mediaItemCount = playerA.mediaItemCount
        val mediaItems = ArrayList<MediaItem>(mediaItemCount)
        for (i in 0 until mediaItemCount) mediaItems.add(playerA.getMediaItemAt(i))
        val repeatMode = playerA.repeatMode
        val shuffleMode = playerA.shuffleModeEnabled
        val volume = playerA.volume
        val pauseAtEnd = playerA.pauseAtEndOfMediaItems
        val playbackParameters: PlaybackParameters = playerA.playbackParameters

        removeMasterPlayerListeners(playerA)
        onPlayerAboutToBeReleasedListener?.invoke(playerA)
        playerA.release()
        playerB?.release()
        playerB = null

        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters

        if (mediaItems.isNotEmpty()) {
            playerA.setMediaItems(mediaItems, currentIndex, positionMs)
            playerA.repeatMode = repeatMode
            playerA.shuffleModeEnabled = shuffleMode
            playerA.prepare()
            playerA.playWhenReady = desiredPlayWhenReady
            applyWakeModeForCurrentItem()
        }

        _activeAudioSessionId.value = playerA.audioSessionId
        onPlayerSwappedListeners.forEach { it(playerA) }

        // After rebuild: clear stale resolved URI cache to avoid using proxy URLs
        // from a previous server instance that might have a different port
        checkAndUpdateProxyPorts()
        clearAllResolvedCache()

        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "player_rebuild_end"
        ) {
            mapOf("audioSessionId" to playerA.audioSessionId.toString())
        }
        Timber.tag("DualPlayerEngine").d(logMessage)
    }

    /**
     * Checks whether any local proxy has restarted with a different port.
     * If so, returns true to signal that the resolved URI cache must be invalidated
     * for the affected scheme.
     */
    private fun checkAndUpdateProxyPorts(): Boolean {
        var anyChanged = false
        val currentNeteasePort = neteaseStreamProxy.getCurrentPort()
        if (lastKnownNeteasePort != 0 && currentNeteasePort != lastKnownNeteasePort) {
            Timber.tag("DualPlayerEngine").w("Netease proxy port changed: $lastKnownNeteasePort -> $currentNeteasePort. Clearing cached resolved URIs for netease://")
            anyChanged = true
        }
        lastKnownNeteasePort = currentNeteasePort

        val currentQqMusicPort = qqMusicStreamProxy.getCurrentPort()
        if (lastKnownQqMusicPort != 0 && currentQqMusicPort != lastKnownQqMusicPort) {
            Timber.tag("DualPlayerEngine").w("QQ Music proxy port changed: $lastKnownQqMusicPort -> $currentQqMusicPort. Clearing cached resolved URIs for qqmusic://")
            anyChanged = true
        }
        lastKnownQqMusicPort = currentQqMusicPort

        val currentNavidromePort = navidromeStreamProxy.getCurrentPort()
        if (lastKnownNavidromePort != 0 && currentNavidromePort != lastKnownNavidromePort) {
            anyChanged = true
        }
        lastKnownNavidromePort = currentNavidromePort

        val currentJellyfinPort = jellyfinStreamProxy.getCurrentPort()
        if (lastKnownJellyfinPort != 0 && currentJellyfinPort != lastKnownJellyfinPort) {
            anyChanged = true
        }
        lastKnownJellyfinPort = currentJellyfinPort

        return anyChanged
    }

    /**
     * Returns true if the given URI points to 127.0.0.1 but the port doesn't
     * match the current proxy port, or if any proxy has restarted.
     */
    private fun isLocalhostProxyUriStale(uri: Uri): Boolean {
        if (uri.host != "127.0.0.1") return false
        val port = uri.port
        if (port <= 0) return false
        return (lastKnownNeteasePort != 0 && port != lastKnownNeteasePort) ||
                (lastKnownQqMusicPort != 0 && port != lastKnownQqMusicPort) ||
                (lastKnownNavidromePort != 0 && port != lastKnownNavidromePort) ||
                (lastKnownJellyfinPort != 0 && port != lastKnownJellyfinPort)
    }

    private fun invalidateResolvedCacheForScheme(originalUriString: String) {
        try {
            val scheme = android.net.Uri.parse(originalUriString).scheme
            if (scheme != null) {
                val snapshot = resolvedUriCache.snapshot()
                val keysToRemove = mutableListOf<String>()
                for (key in snapshot.keys) {
                    if (key.startsWith(scheme)) {
                        keysToRemove.add(key)
                    }
                }
                for (key in keysToRemove) {
                    resolvedUriCache.remove(key)
                }
                if (keysToRemove.isNotEmpty()) {
                    Timber.tag("DualPlayerEngine").d("Invalidated ${keysToRemove.size} cached URIs for scheme $scheme")
                }
            }
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Error invalidating resolved cache")
        }
    }

    private fun clearAllResolvedCache() {
        try {
            resolvedUriCache.evictAll()
            Timber.tag("DualPlayerEngine").d("Cleared all resolved URI cache")
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 清空各流媒体代理内部的 URL 缓存（15 分钟 TTL），防止失效直链被反复复用 */
    private fun invalidateProxyStreamCaches() {
        runCatching { neteaseStreamProxy.invalidateAll() }
        runCatching { qqMusicStreamProxy.invalidateAll() }
        runCatching { navidromeStreamProxy.invalidateAll() }
        runCatching { jellyfinStreamProxy.invalidateAll() }
    }

    private fun isLocalhostProxyConnectionError(error: androidx.media3.common.PlaybackException): Boolean {
        if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
            var cause: Throwable? = error.cause
            while (cause != null) {
                val msg = cause.message ?: ""
                if (msg.contains("127.0.0.1") ||
                    msg.contains("localhost") ||
                    cause is java.net.ConnectException ||
                    cause is java.net.SocketException) {
                    return true
                }
                cause = cause.cause
            }
        }
        return false
    }

    /**
     * Error recovery handler: called from the player's onPlayerError.
     * Invalidates stale cache entries and attempts to re-prepare the same
     * media item with fresh URL resolution.
     */
    private fun tryRecoverFromError(
        player: ExoPlayer,
        error: androidx.media3.common.PlaybackException,
        wasPlaying: Boolean
    ) {
        val failingItem = player.currentMediaItem ?: return
        val failingUri = failingItem.localConfiguration?.uri ?: return
        val failingUriString = failingUri.toString()

        // 🔍 错误恢复定位日志
        android.util.Log.d(
            "LxPlayer",
            "=== 播放错误恢复 tryRecoverFromError: errorCode=${error.errorCode} " +
                "uri=$failingUriString pos=${player.currentPosition}ms dur=${player.duration}ms " +
                "state=${player.playbackState} repeat=${player.repeatMode} " +
                "next=${player.hasNextMediaItem()} wasPlaying=$wasPlaying ==="
        )

        // Track retries per item to prevent infinite loops
        val mediaId = failingItem.mediaId
        val retries = mediaItemRetryCount.getOrDefault(mediaId, 0)
        if (retries >= MAX_RETRIES_PER_ITEM) {
            Timber.tag("DualPlayerEngine").e("Max retries ($MAX_RETRIES_PER_ITEM) reached for $mediaId. Skipping.")
            mediaItemRetryCount.remove(mediaId)
            // Auto-advance to next track
            if (player.hasNextMediaItem()) {
                try {
                    player.seekToNextMediaItem()
                    player.prepare()
                    if (wasPlaying) player.playWhenReady = true
                } catch (e: Exception) {
                    Timber.tag("DualPlayerEngine").w(e, "Failed to seek to next track")
                }
            }
            return
        }

        // Handle StuckPlayerException (ERROR_CODE_TIMEOUT): the audio offload HAL
        // reported STATE_READY + isPlaying=true but the position never advanced for
        // 10 s, so Media3's built-in StuckPlayerDetector killed the player. The
        // custom offload fallback (scheduleAudioOffloadFallbackIfNeeded) only
        // catches BUFFERING stalls (!isPlaying) and misses this case. Recovery:
        // disable offload for the session and rebuild — the rebuilt player resumes
        // from the saved position on the non-offload PCM path.
        if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
            mediaItemRetryCount[mediaId] = retries + 1
            // ⚡ 修复"单曲播完卡死"：歌曲实际已播完，但 offload HAL 未向 ExoPlayer
            // 发 EOS，播放器停留在 READY + playWhenReady=true、位置不再前进，10s 后
            // 被 StuckPlayerDetector 抛出 StuckPlayerException。此前恢复逻辑会重建/
            // 清空播放器时间线 → onMediaItemTransition(null) → PlayerViewModel 清空
            // currentSong → mini player 消失。队尾且已到末尾时直接视为"自然播完"：
            // 暂停并保留时间线（不重建播放器），避免 mini player 消失。
            val durationMs = player.duration
            val positionMs = player.currentPosition
            val nearTrackEnd = durationMs > 0 && positionMs >= durationMs - 2000L
            // ⚡ 修复"无法自动下一首"（治本）：代理/边下边播流时长未知（duration = -1，
            // 上游无 Content-Length 走 chunked），nearTrackEnd 永远 false → 播完卡死 10s
            // 后只会走 rebuild → 位置被重置 → "回到歌曲开头"，永远切不了歌。
            // StuckPlayerException 的本质是位置 10s 无进展 = 播放已结束/彻底卡死，
            // 此时无论时长是否已知都应按 repeatMode 模拟"自然播完"切歌。
            val stuckAtUnknownDuration = durationMs <= 0L
            // ⚡ 修复"开启列表循环仍播完就暂停 / 回到歌曲开头"：只要位置已到曲尾，
            // 就说明歌曲实际已播完但 EOS 未送达（HAL 未发）→ 直接按 repeatMode
            // 模拟"自然播完"。此前还要求 atEndOfQueue（!hasNextMediaItem()），但
            // REPEAT_MODE_ALL 下队尾 hasNextMediaItem() 恒为 true（自动回绕到第一首），
            // 导致循环模式永远进不了此分支 → 落入 rebuild → 位置被重置 → "回到开头"。
            if (nearTrackEnd || stuckAtUnknownDuration) {
                // ⚡ 修复"开启列表循环仍播完就暂停"：歌曲在队尾实际已播完，但 ExoPlayer
                // 未收到 EOS（HAL 未发）→ 位置冻结 → 10s 后被 StuckPlayerDetector 判死。
                // 此前的恢复逻辑一律暂停，无视用户开启的列表循环/单曲循环 →
                // 必须按 repeatMode 模拟"自然播完"后的行为（见 simulateNaturalTrackEnd）：
                //   REPEAT_MODE_ALL → 跳下一首（队尾自动回到第一首）并继续播放
                //   REPEAT_MODE_ONE → 从头重播同一首并继续播放
                //   REPEAT_MODE_OFF → 列表播完，暂停（保留时间线，避免 mini player 消失）
                Timber.tag("DualPlayerEngine").w(
                    "StuckPlayerException at track end (mediaId=$mediaId, repeatMode=${player.repeatMode}) — simulating natural completion"
                )
                // 确保不因 pauseAtEnd 残留而"播完暂停不切歌"
                setPauseAtEndOfMediaItems(false)
                simulateNaturalTrackEnd()
                return
            }
            if (audioOffloadEnabled) {
                Timber.tag("DualPlayerEngine").w(
                    "StuckPlayerException for %s — disabling audio offload and rebuilding player",
                    mediaId
                )
                disableAudioOffloadForSession(
                    reason = "StuckPlayerException: player stuck with no progress (offload HAL stall)"
                )
            } else {
                // Offload already disabled (e.g. AAudio backend) but still stuck.
                // ⚡ 此前的 re-prepare 会复用同一条 native 管线（AAudio 流/解析后的 URL），
                // 对"流已停滞/断连/URL 失效"的场景无法真正恢复 → 改为整体重建播放器：
                // 全新的 AudioSink（全新 AAudio 流）+ 重新解析 URL + 重放同一队列。
                Timber.tag("DualPlayerEngine").w(
                    "StuckPlayerException for %s with offload already disabled — full player rebuild",
                    mediaId
                )
                if (transitionRunning) {
                    // 过渡中不重建，退化为普通 re-prepare，避免干扰 playerB
                    try {
                        val currentIndex = player.currentMediaItemIndex
                        val currentPosition = player.currentPosition
                        player.stop()
                        player.clearMediaItems()
                        val snapshot = ensureQueueSnapshot()
                        if (snapshot.isNotEmpty()) {
                            player.setMediaItems(snapshot, currentIndex, currentPosition)
                            player.prepare()
                            if (wasPlaying) player.playWhenReady = true
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Re-prepare failed for %s after stuck player", mediaId)
                    }
                } else {
                    runCatching {
                        rebuildPlayersPreservingMasterState(
                            logMessage = "StuckPlayerException: player stuck with no progress (offload disabled) — rebuilt player for $mediaId"
                        )
                    }.onFailure { e ->
                        Timber.tag("DualPlayerEngine").w(e, "Full rebuild failed for %s after stuck player", mediaId)
                    }
                }
            }
            return
        }

        // Check if this is a localhost proxy connection error (most common failure)
        val isProxyError = isLocalhostProxyConnectionError(error) || failingUri.host == "127.0.0.1"

        if (isProxyError) {
            Timber.tag("DualPlayerEngine").w("Proxy connection error for $mediaId. Attempting recovery (retry ${retries + 1}).")

            // Invalidate cache for the failing URI and its scheme
            resolvedUriCache.remove(failingUriString)
            // Also clear cache for the original scheme (e.g., "netease://")
            // We need to find the original URI - stored in mediaId or extras
            // But we don't have the original netease:// URI here; the mediaItem
            // already has the resolved proxy URL. So invalidate the entire
            // proxy cache and force re-resolution.
            clearAllResolvedCache()
            invalidateProxyStreamCaches()

            // Also reset proxy state so ports are re-detected
            lastKnownNeteasePort = 0
            lastKnownQqMusicPort = 0
            lastKnownNavidromePort = 0
            lastKnownJellyfinPort = 0
            lastKnownGDrivePort = 0
            lastKnownTelegramPort = 0

            mediaItemRetryCount[mediaId] = retries + 1

            // Re-prepare the same item - the resolver will be invoked again
            // and will get fresh proxy URLs.
            val currentIndex = player.currentMediaItemIndex
            val currentPosition = player.currentPosition
            try {
                // ⚡ 必须先取快照再 stop/clear：clearMediaItems 后时间线为空，
                //    ensureQueueSnapshot() 会刷新成空列表导致重试落空。
                val snapshot = ensureQueueSnapshot()
                player.stop()
                player.clearMediaItems()
                // Rebuild the media items from the queue snapshot, so URL
                // resolution runs again
                if (snapshot.isNotEmpty()) {
                    player.setMediaItems(snapshot, currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) player.playWhenReady = true
                    Timber.tag("DualPlayerEngine").d("Recovery: re-prepared queue at index $currentIndex")
                }
            } catch (e: Exception) {
                Timber.tag("DualPlayerEngine").w(e, "Recovery failed for $mediaId")
                // Final fallback: try next track
                if (player.hasNextMediaItem()) {
                    try {
                        player.seekToNextMediaItem()
                        player.prepare()
                        if (wasPlaying) player.playWhenReady = true
                    } catch (ex: Exception) {
                        Timber.tag("DualPlayerEngine").w(ex, "Fallback also failed")
                    }
                }
            }
        } else {
            // Non-proxy error: still try next track if retries exhausted
            mediaItemRetryCount[mediaId] = retries + 1
            Timber.tag("DualPlayerEngine").w("Playback error for $mediaId (non-proxy). errorCode=${error.errorCode}")

            // 漫游歌曲（roaming_ 前缀）播放失败时重新解析 URL 并原地重放。
            // 走完整解析链：lxJsEngine → neteaseStreamProxy 兜底（与 ResolvingDataSource
            // 路径一致），避免 lxJsEngine 单点失败就直接跳歌。
            if (mediaId.startsWith("roaming_") && retries < MAX_RETRIES_PER_ITEM - 1) {
                val numericId = mediaId.removePrefix("roaming_").toLongOrNull()
                if (numericId != null) {
                    val wasPlayingBefore = wasPlaying
                    // 清除过期的缓存条目和代理缓存，确保拿到新鲜直链
                    val neteaseUri = "netease://$numericId"
                    resolvedUriCache.remove(neteaseUri)
                    invalidateProxyStreamCaches()
                    scope.launch(Dispatchers.Main) {
                        val freshUri = withContext(Dispatchers.IO) {
                            try {
                                resolveNeteaseUriAsync(neteaseUri)
                            } catch (t: Throwable) {
                                Timber.tag("DualPlayerEngine").w(t, "Roaming re-resolve failed for $mediaId")
                                null
                            }
                        }
                        if (freshUri == null || freshUri.toString() == neteaseUri || !::playerA.isInitialized) {
                            // 全链路解析失败 → 跳到下一首
                            runCatching {
                                if (player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem()
                                    player.prepare()
                                    if (wasPlayingBefore) player.playWhenReady = true
                                }
                            }
                            return@launch
                        }
                        if (playerA.currentMediaItem?.mediaId != mediaId) return@launch
                        try {
                            val currentIndex = player.currentMediaItemIndex
                            val currentPosition = player.currentPosition
                            val newItem = player.currentMediaItem
                                ?.buildUpon()?.setUri(freshUri)?.build()
                            if (newItem != null) {
                                // ⚡ 必须先取快照再 stop/clear：clearMediaItems 后时间线为空，
                                //    ensureQueueSnapshot() 会刷新成空列表导致重试落空。
                                val snapshot = ensureQueueSnapshot().map { item ->
                                    if (item.mediaId == mediaId) newItem else item
                                }
                                player.stop()
                                player.clearMediaItems()
                                if (snapshot.isNotEmpty()) {
                                    player.setMediaItems(snapshot, currentIndex, currentPosition)
                                    player.prepare()
                                    if (wasPlayingBefore) player.playWhenReady = true
                                    Timber.tag("DualPlayerEngine").d(
                                        "Roaming: re-resolved fresh URL and re-prepared $mediaId"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("DualPlayerEngine").w(e, "Roaming re-resolve re-prepare failed for $mediaId")
                        }
                    }
                    return
                }
            }

            // 广播电台重试机制：http(s) 直播流断流/失败时按退避策略自动重连
            if (mediaId.startsWith("radio://")) {
                val backoffMs = listOf(1_000L, 3_000L, 6_000L)[retries.coerceAtMost(2)]
                Timber.tag("DualPlayerEngine").w(
                    "Radio stream error, retrying in ${backoffMs}ms (attempt ${retries + 1}/$MAX_RETRIES_PER_ITEM)"
                )
                scope.launch(Dispatchers.Main) {
                    delay(backoffMs)
                    runCatching {
                        if (!::playerA.isInitialized) return@runCatching
                        if (playerA.currentMediaItem?.mediaId != mediaId) return@runCatching
                        val currentIndex = player.currentMediaItemIndex
                        player.stop()
                        player.clearMediaItems()
                        val snapshot = ensureQueueSnapshot()
                        if (snapshot.isNotEmpty()) {
                            player.setMediaItems(snapshot, currentIndex, 0L)
                            player.prepare()
                            player.playWhenReady = true
                            Timber.tag("DualPlayerEngine").d("Radio retry: re-connected to $mediaId")
                        }
                    }
                }
            }

            // ⚡ 在线歌曲（落雪搜索/收藏等已保存到数据库的云端歌曲）：
            //    MediaItem 里是一次性的 http(s) 直链（带签名会过期 / CDN 限流间歇失败），
            //    对同一条直链重试必然再次失败。从数据库找回可重新解析的
            //    netease:// / cloud://lx/ URI，重新解析出新鲜直链后原地重放，
            //    解析不出再交给 MAX_RETRIES 逻辑跳歌。修复"Source error 后歌曲直接停止"。
            val dbSongId = mediaId.toLongOrNull()
            if (dbSongId != null && retries < MAX_RETRIES_PER_ITEM - 1) {
                val wasPlayingBefore = wasPlaying
                scope.launch(Dispatchers.Main) {
                    // 重新解析前先清掉代理 15 分钟缓存，避免拿到同一条失效直链
                    invalidateProxyStreamCaches()
                    val freshItem = withContext(Dispatchers.IO) {
                        runCatching {
                            val dbSong = musicRepository.getSong(dbSongId.toString()).first()
                            val cloudUri = dbSong?.contentUriString?.takeIf {
                                it.startsWith("netease://") || it.startsWith("cloud://")
                            } ?: return@runCatching null
                            val candidate = failingItem.buildUpon().setUri(Uri.parse(cloudUri)).build()
                            // resolveMediaItem -> resolveCloudUri：落雪引擎/内置源重新解析一条新直链
                            resolveMediaItem(candidate)
                        }.getOrNull()
                    }
                    if (freshItem == null) return@launch
                    if (player.currentMediaItem?.mediaId != mediaId) return@launch
                    try {
                        // ⚡ 必须先取快照再 stop/clear：clearMediaItems 后 player 时间线
                        //    变为空，此时 ensureQueueSnapshot() 会刷新成空列表导致重试落空。
                        val currentIndex = player.currentMediaItemIndex
                        val currentPosition = player.currentPosition
                        val snapshot = ensureQueueSnapshot().map { item ->
                            if (item.mediaId == mediaId) freshItem else item
                        }
                        player.stop()
                        player.clearMediaItems()
                        if (snapshot.isNotEmpty()) {
                            player.setMediaItems(snapshot, currentIndex, currentPosition)
                            player.prepare()
                            if (wasPlayingBefore) player.playWhenReady = true
                            Timber.tag("DualPlayerEngine").d(
                                "Cloud song: re-resolved fresh URL and re-prepared $mediaId"
                            )
                        }
                    } catch (e: Exception) {
                        Timber.tag("DualPlayerEngine").w(e, "Cloud song re-resolve re-prepare failed for $mediaId")
                    }
                }
                return
            }
        }
    }

    /**
     * Returns a [DefaultLoadControl] tuned to the device's RAM tier.
     *
     * Low-RAM devices ([ActivityManager.isLowRamDevice]) receive halved buffer ceilings
     * to prevent memory pressure when both players co-exist during a crossfade.
     * [bufferForPlaybackMs] is set to ExoPlayer's documented default of 2 500 ms on both
     * tiers — the previous value of 5 000 ms doubled first-audio latency with no benefit.
     * [bufferForPlaybackAfterRebufferMs] is raised to 7 000 ms so that after a buffer
     * underrun (common with network radio streams under jitter) playback resumes only
     * once a comfortable margin is re-buffered, reducing recovery-time clicks/pops.
     */
    private fun buildAdaptiveLoadControl(): DefaultLoadControl {
        val isLowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isLowRamDevice
        // setPrioritizeTimeOverSizeThresholds(true): instructs ExoPlayer to use buffered
        // *duration* (not buffered *bytes*) as the criterion for deciding when to start
        // playback and when to stop buffering. This is required for correct behaviour with
        // high-bitrate and lossless formats (FLAC, hi-res ALAC, WAV) where a short byte
        // window would be exhausted almost immediately, causing repeated rebuffering.
        // Without this flag ExoPlayer falls back to a default byte threshold that was
        // designed for typical compressed audio (~128–320 kbps) and will underperform on
        // files with bitrates above ~1 Mbps.
        return if (isLowRam) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 15_000,
                    /* maxBufferMs                      */ 30_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  7_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 30_000,
                    /* maxBufferMs                      */ 60_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  7_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos)
        }
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                // ⚡ 恢复原版播放输出：始终使用系统 AudioTrack（Media3 默认后端），
                // 不再注入自定义 AAudio AudioOutputProvider —— AAudio 后端在播放到
                // 曲尾时 framesRead 位置基准抖动会导致 hasPendingData() 永远 true、
                // EOS 永远不达，歌曲播完无法自动切歌（"回跳 3 秒反复 / 不自动下一首"）。
                // USB 独占在 AudioProcessor 层镜像输出到 DAC，不受输出后端影响。
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiFiModeEnabled)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor(),
                            com.theveloper.pixelplay.data.service.audioengine.HiFiEngineAudioProcessor().also {
                                hiFiEngineProcessor = it
                                audioProcessorProvider.registerProcessor(it)
                            },
                            // ⚡ 频谱采集：透传但偷看 PCM，供 Glyph Matrix 可视化驱动（不改声音）
                            audioVisualizer.createProcessor(),
                            // ⚡ USB 独占输出：镜像最终 PCM 到 USB DAC（激活时）
                            com.theveloper.pixelplay.data.service.audioengine.UsbExclusiveAudioProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip video renderers to save memory and "renderers" count.
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip text renderers.
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip camera motion renderers.
            }
        }.setEnableAudioFloatOutput(hiFiModeEnabled)
         .setMediaCodecSelector(mediaCodecSelector)
         .setEnableDecoderFallback(true)
         .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val scheme = uri.scheme
                if (scheme in CLOUD_PROXY_SCHEMES) {
                    val originalUri = uri.toString()
                    val cached = getFreshResolvedUri(originalUri)
                    if (cached != null) {
                        // Validate: cached URI pointing to 127.0.0.1 must match current proxy port
                        if (cached.host == "127.0.0.1" && isLocalhostProxyUriStale(cached)) {
                            Timber.tag("DualPlayerEngine").w(
                                "Stale cached proxy URI detected (port ${cached.port} != current). Forcing re-resolution."
                            )
                            resolvedUriCache.remove(originalUri)
                        } else {
                            return dataSpec.buildUpon().setUri(cached).build()
                        }
                    }
                    val resolvedNow = runBlocking(Dispatchers.IO) {
                        try {
                            when (scheme) {
                                "netease" -> {
                                    if (neteaseStreamProxy.ensureReady(5_000L)) {
                                        neteaseStreamProxy.resolveNeteaseUri(originalUri)
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { Uri.parse(it) }
                                    } else null
                                }
                                "qqmusic" -> {
                                    // 与 resolveCloudUri 走同一条三级解析链（官方代理+vkey /
                                    // 落雪 tx 源 / 内置源官方接口），避免数据源层只走代理
                                    // 导致上游不可用时必然 404。
                                    resolveQqMusicUriAsync(originalUri)
                                }
                                "telegram" -> {
                                    telegramRepository.resolveTelegramUri(originalUri)?.first
                                        ?.let { telegramStreamProxy -> Uri.parse(telegramStreamProxy.toString()) }
                                }
                                "bilibili" -> {
                                    // 兜底：历史/通知等路径若未预解析，数据源层重新解析最新播放地址
                                    resolveBilibiliUri(Uri.parse(originalUri))
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            Timber.tag("DualPlayerEngine").w(e, "Failed to resolve cloud URI: $originalUri")
                            null
                        }
                    }
                    if (resolvedNow != null && resolvedNow.toString().isNotBlank()) {
                        putResolvedUri(originalUri, resolvedNow)
                        return dataSpec.buildUpon().setUri(resolvedNow).build()
                    }
                    Timber.tag("DualPlayerEngine").w(
                        "Cloud URI $scheme:$originalUri could not be resolved — playback may fail"
                    )
                }
                return dataSpec
            }
        }

        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .apply {
                setTransferListener(object : androidx.media3.datasource.TransferListener {
                    override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                        android.util.Log.d("LxTransfer", "onTransferInitializing: ${dataSpec.uri.host}, isNetwork=$isNetwork")
                    }
                    override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                        android.util.Log.d("LxTransfer", "onTransferStart: ${dataSpec.uri.host}, isNetwork=$isNetwork")
                    }
                    override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                        // too verbose, skip
                    }
                    override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                        android.util.Log.d("LxTransfer", "onTransferEnd: ${dataSpec.uri.host}")
                    }
                })
            }
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory, resolver)
        val wavConversionFactory = com.theveloper.pixelplay.utils.WavConversionDataSource.Factory(context, resolvingFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            // FLAG_WORKAROUND_IGNORE_EDIT_LISTS intentionally removed: it breaks Opus files
            // by discarding the edit list that encodes the pre-skip (encoder delay), causing
            // ExoPlayer to seek ~44-52s into the track on first playback.
            // FLAG_ENABLE_CONSTANT_BITRATE_SEEKING (not _ALWAYS): fallback-only CBR seeking
            // so VBR MP3s with proper Xing/VBRI headers still use their seek table and land
            // on the exact frame instead of jumping ±30 s on a VBR file.
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setFlacExtractorFlags(FlacExtractor.FLAG_DISABLE_ID3_METADATA)

        val loadControl = buildAdaptiveLoadControl()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(wavConversionFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            setAudioAttributes(audioAttributes, false)
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabled && !streamingModeEnabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(offloadPreferences)
                .build()
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            preferredAudioDevice?.let { setPreferredAudioDevice(it) }
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "STATE_IDLE"
                        Player.STATE_BUFFERING -> "STATE_BUFFERING"
                        Player.STATE_READY -> "STATE_READY"
                        Player.STATE_ENDED -> "STATE_ENDED"
                        else -> "STATE_$playbackState"
                    }
                    android.util.Log.d("LxPlayer", "=== onPlaybackStateChanged: $stateName ===")
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    android.util.Log.d("LxPlayer", "onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reason")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("LxPlayer", "=== PLAYER ERROR ===")
                    android.util.Log.e("LxPlayer", "errorCode: ${error.errorCode}")
                    android.util.Log.e("LxPlayer", "errorCodeName: ${error.errorCodeName}")
                    android.util.Log.e("LxPlayer", "message: ${error.message}")
                    android.util.Log.e("LxPlayer", "cause: ${error.cause}", error.cause)
                    val underlyingException = android.util.Log.getStackTraceString(error.cause ?: error)
                    android.util.Log.e("LxPlayer", "stacktrace: $underlyingException")
                    val failingMediaItem = currentMediaItem
                    if (failingMediaItem != null) {
                        android.util.Log.e("LxPlayer", "currentMediaItem uri: ${failingMediaItem.localConfiguration?.uri}")
                        android.util.Log.e("LxPlayer", "currentMediaItem mediaId: ${failingMediaItem.mediaId}")
                        android.util.Log.e("LxPlayer", "currentMediaItem title: ${failingMediaItem.mediaMetadata.title}")
                    }
                    // Trigger recovery: clear stale proxy URL cache and re-prepare
                    val wasPlaying = playWhenReady
                    this@DualPlayerEngine.scope.launch(Dispatchers.Main) {
                        this@DualPlayerEngine.tryRecoverFromError(
                            player = playerA,
                            error = error,
                            wasPlaying = wasPlaying
                        )
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    android.util.Log.d("LxPlayer", "=== onMediaItemTransition: ${mediaItem?.localConfiguration?.uri} (reason=$reason) ===")
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    android.util.Log.d("LxPlayer", "onIsPlayingChanged: isPlaying=$isPlaying")
                }
            })
        }
    }

    private fun getOrCreateAuxiliaryPlayer(): ExoPlayer {
        playerB?.let { return it }
        return buildPlayer().also { player ->
            player.setWakeMode(currentWakeMode)
            if (desiredPlaybackSpeed != 1f) {
                player.playbackParameters = PlaybackParameters(desiredPlaybackSpeed, desiredPlaybackPitch)
            }
            playerB = player
        }
    }

    fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
        preferredAudioDevice = device
        if (::playerA.isInitialized) {
            playerA.setPreferredAudioDevice(device)
        }
        playerB?.setPreferredAudioDevice(device)
    }

    /**
     * USB 独占模式切换时重建播放器（保持当前曲目与播放状态）。
     *
     * ⚡ 独占开关在播放中切换时必须重建播放器，否则无声：
     *   1. buildAudioSink 的 useAaudio 只在构建时评估。AAudio 后端的
     *      setPreferredDevice 是 no-op（无法跟随路由到 USB），且 libusb
     *      forceClaim 会踢掉占用 USB 接口的 AAudio 流（AAUDIO_ERROR_DISCONNECTED）
     *      → 播放中开启独占 = 系统与 USB 都无声。
     *   2. AudioTrack 的优选设备路由只在 track 创建时生效；重建后 buildPlayer
     *      会重新应用 preferredAudioDevice（见 build 尾部），独占激活才有声。
     */
    fun rebuildForUsbExclusiveModeChange() {
        if (!::playerA.isInitialized) {
            // 播放器尚未构建：无需重建，下次 buildPlayer 会读取最新的
            // usbExclusiveModeEnabled/aaudioEnabled/preferredAudioDevice
            Timber.tag("DualPlayerEngine").d("USB exclusive mode changed, player not built yet; skip rebuild")
            return
        }
        rebuildPlayersPreservingMasterState("USB exclusive mode changed")
    }

    /**
     * 更新流式（广播电台）播放模式。
     *
     * 实时流（如 radio:// 直播电台）在部分设备上启用 audio offload 后，
     * HAL 会对流式内容产生周期性的打嗝/电流杂音。进入流式模式时动态关闭
     * audio offload（走 PCM 路径），切回普通歌曲时自动恢复，无需重建播放器。
     */
    fun updateStreamingModeFor(mediaId: String?) {
        val isStreaming = mediaId?.startsWith("radio://") == true
        if (streamingModeEnabled == isStreaming) return
        streamingModeEnabled = isStreaming
        applyStreamingOffloadPreference()
        Timber.tag("DualPlayerEngine").i(
            "Streaming mode: %s (audio offload enabled=%s)",
            isStreaming, audioOffloadEnabled && !isStreaming
        )
    }

    /**
     * 播放网络直播流/在线媒体（playUrl 统一入口）。
     * 提前进入流式模式（禁用 audio offload，规避 HAL 杂音）；
     * 播放前先 stop 立即释放旧流，避免切台瞬间新旧流叠加的爆音；
     * 启动时音量淡入，消除 AudioTrack 创建/切换瞬间的爆音（咔哒声）。
     */
    fun playStreaming(mediaItem: MediaItem, mediaId: String) {
        updateStreamingModeFor(mediaId)
        val player = masterPlayer
        if (player.playbackState != Player.STATE_IDLE) {
            player.stop()
        }
        player.setMediaItem(mediaItem, 0L)
        player.prepare()
        if (mediaId.startsWith("radio://")) {
            fadeInVolume(player, mediaId)
        }
        player.play()
    }

    /** 音量淡入：从 0 渐变到 1，约 160ms，掩盖 AudioTrack 启动/重连时的瞬时爆音 */
    private fun fadeInVolume(player: Player, mediaId: String) {
        player.volume = 0f
        scope.launch(Dispatchers.Main) {
            val startMs = SystemClock.uptimeMillis()
            while (true) {
                // 播放期间切台：放弃本次淡入，不干扰新流的淡入
                if (player.currentMediaItem?.mediaId != mediaId) return@launch
                if (player.volume >= 1f) break
                val progress = ((SystemClock.uptimeMillis() - startMs) / 160f).coerceIn(0f, 1f)
                player.volume = progress
                delay(16)
            }
            player.volume = 1f
        }
    }

    private fun applyStreamingOffloadPreference() {
        val offloadEnabled = audioOffloadEnabled && !streamingModeEnabled
        val prefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (offloadEnabled) {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                } else {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                }
            )
            .build()
        fun apply(player: ExoPlayer?) {
            if (player == null) return
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(prefs)
                    .build()
            }
        }
        apply(if (::playerA.isInitialized) playerA else null)
        apply(playerB)
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = shouldPause
        }
    }

    /**
     * 按当前 repeatMode 模拟"歌曲自然播完"后的行为，用于跨 fade 未及时触发、
     * EOS 未送达等"播完不自动切歌"场景的兜底：
     *   REPEAT_MODE_ALL → 跳下一首（队尾自动回绕第一首）并继续播放
     *   REPEAT_MODE_ONE → 从头重播同一首并继续播放
     *   REPEAT_MODE_OFF → 有下一首顺播；列表播完则回到开头暂停（保留时间线）
     */
    fun simulateNaturalTrackEnd() {
        if (!::playerA.isInitialized) return
        val player = playerA
        Timber.tag("DualPlayerEngine").w(
            "simulateNaturalTrackEnd: repeatMode=${player.repeatMode} " +
                "idx=${player.currentMediaItemIndex}/${player.mediaItemCount} next=${player.hasNextMediaItem()}"
        )
        runCatching {
            // 禁用 offload（不重建播放器），避免重播/下一首时再次卡死
            disableAudioOffloadWithoutRebuild()
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> {
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                    } else {
                        player.seekToDefaultPosition(0)
                    }
                    player.prepare()
                    player.playWhenReady = true
                }
                Player.REPEAT_MODE_ONE -> {
                    player.seekTo(0L)
                    player.prepare()
                    player.playWhenReady = true
                }
                else -> {
                    // 单次播放：有下一首就顺播；列表播完则回到开头暂停（保留时间线）
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.playWhenReady = true
                    } else {
                        player.seekTo(0L)
                        player.prepare()
                        player.playWhenReady = false
                    }
                }
            }
        }
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        // ⚡ 列表循环（REPEAT_MODE_ALL）必须 wrap：播完最后一首时 crossfade 预加载第一首，
        //    否则队尾无过渡目标，列表循环会"不循环"（依赖 ExoPlayer 自身 wrap 但 crossfade 断裂）。
        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
            Player.REPEAT_MODE_ALL -> (currentAbsoluteIndex + 1) % snapshot.size
            else -> currentAbsoluteIndex + 1
        }

        val targetItem = snapshot.getOrNull(targetIndex) ?: return null
        return TransitionTarget(
            mediaItem = targetItem,
            absoluteIndex = targetIndex,
            queueSize = snapshot.size
        )
    }

    fun setHiFiMode(enabled: Boolean) {
        if (hiFiModeEnabled == enabled) return
        if (enabled && !HiFiCapabilityChecker.isSupported()) {
            Timber.tag("DualPlayerEngine").w("Hi-Fi mode requested but device does not support PCM_FLOAT")
            return
        }
        hiFiModeEnabled = enabled
        rebuildPlayersPreservingMasterState("Hi-Fi mode set to $enabled")
    }

    fun getHiFiEngineProcessor(): com.theveloper.pixelplay.data.service.audioengine.HiFiEngineAudioProcessor? =
        hiFiEngineProcessor

    @Volatile
    private var musicQualityLxValue: String = "320k"

    fun setMusicQuality(qualityValue: String) {
        musicQualityLxValue = qualityValue
    }

    /** 音源脚本注册的可用音质（qualitys）；未注册/未就绪时返回空列表。 */
    private fun availableQualitiesFor(source: String): List<String> =
        lxJsEngine.getSources()[source]?.qualitys.orEmpty()

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        getFreshResolvedUri(uriString)?.let { return@withContext it }

        val resolved: Uri? = when (uri.scheme) {
            "telegram" -> resolveTelegramUriAsync(uri, uriString)
            "netease" -> resolveNeteaseUriAsync(uriString)
            "qqmusic" -> resolveQqMusicUriAsync(uriString)
            "navidrome" -> resolveNavidromeUriAsync(uriString)
            "jellyfin" -> resolveJellyfinUriAsync(uriString)
            "gdrive" -> resolveGDriveUriAsync(uriString)
            "cloud" -> resolveCloudLxUriAsync(uriString)
            "bilibili" -> resolveBilibiliUri(uri)
            else -> null
        }

        if (resolved != null) {
            putResolvedUri(uriString, resolved)
            return@withContext resolved
        }
        uri
    }

    private suspend fun resolveTelegramUriAsync(uri: Uri, uriString: String): Uri? = withContext(Dispatchers.IO) {
        val pathSegments = uri.pathSegments
        val fileId = if (pathSegments.isNotEmpty()) {
            telegramRepository.resolveTelegramUri(uriString)?.first
        } else {
            uri.host?.toIntOrNull()
        } ?: return@withContext null

        val fileInfo = telegramRepository.getFile(fileId)
        if (fileInfo?.local?.isDownloadingCompleted == true && fileInfo.local.path.isNotEmpty()) {
            return@withContext Uri.fromFile(File(fileInfo.local.path))
        }

        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }

        if (!telegramStreamProxy.ensureReady(5_000L)) return@withContext null
        val proxyUrl = telegramStreamProxy.getProxyUrl(fileId, 0L)
        if (proxyUrl.isNotEmpty()) Uri.parse(proxyUrl) else null
    }

    private suspend fun resolveNeteaseUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        // ★: 媒体库/播放列表中的网易云歌曲（netease://{id}）播放时同样走落雪引擎优先，
        // 且遵守用户音质设置；官方 neteaseStreamProxy 仅在落雪失败后兜底。
        val songId = uriString.removePrefix("netease://")
        val numericId = songId.toLongOrNull()
        if (numericId != null && numericId > 0) {
            try {
                // 等待落雪引擎就绪（首次播放引擎可能还在初始化，多等几秒避免直接回退官方 128k）
                if (lxJsEngine.awaitReady(15_000)) {
                    // 落雪播放只需平台 + 歌曲 ID（songmid/id），无需歌名/歌手
                    val songMap = mapOf<String, Any?>(
                        "id" to songId,
                        "vid" to songId,
                        "songmid" to songId,
                        "hash" to songId,
                        "source" to "wy"
                    )
                    // 按用户音质向下递减尝试（动态识别音源脚本注册的 qualitys）
                    val chain = MusicQualityCatalog.resolveChain(
                        target = musicQualityLxValue,
                        available = availableQualitiesFor("wy")
                    )
                    var lxUrl: String? = null
                    for (q in chain) {
                        if (lxUrl != null) break
                        lxUrl = runCatching { lxJsEngine.getPlayUrl("wy", songMap, q) }.getOrNull()
                    }
                    if (lxUrl != null) {
                        android.util.Log.d(
                            "DualPlayerEngine",
                            "resolveNeteaseUri: lx engine hit for $songId (quality=$musicQualityLxValue)"
                        )
                        return@withContext Uri.parse(lxUrl)
                    }
                    android.util.Log.w("DualPlayerEngine", "resolveNeteaseUri: lx engine FAILED for $songId -> 官方兜底")
                } else {
                    android.util.Log.w("DualPlayerEngine", "resolveNeteaseUri: 等待落雪引擎就绪超时(15s) for $songId -> 官方兜底")
                }
            } catch (t: Throwable) {
                android.util.Log.w("DualPlayerEngine", "resolveNeteaseUri: lx engine failed for $songId: ${t.message}")
            }
        }
        // 落雪失败 → 官方 neteaseStreamProxy 兜底
        if (!neteaseStreamProxy.ensureReady(5_000L)) return@withContext null
        neteaseStreamProxy.resolveNeteaseUri(uriString)?.let { Uri.parse(it) }
    }

    /**
     * 解析 `qqmusic://{songMid}`（QQ 音乐歌单同步进媒体库后的歌曲）。
     *
     * 解析链（与网易云对齐，避免"QQ 歌单里的音乐播放不了"）：
     * 1. 官方链路：本地代理 + QQ vkey（需已登录 QQ 音乐；VIP/失效曲目 purl 为空）。
     *    ⚠️ 代理只负责生成 127.0.0.1 地址、并不校验上游，因此必须先探测上游能否
     *    解析出真实直链；否则会先返回一个必然 404 的代理地址，后面的兜底永远不可达。
     * 2. 落雪 JS 引擎 `tx` 源（已安装音源插件时可用，音质更高）。
     * 3. 内置源官方接口（溯音酷我，按"歌名 歌手"精确搜索，无需 QQ 登录）。
     */
    private suspend fun resolveQqMusicUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        val midUri = Uri.parse(uriString)
        val songMid = (midUri.host ?: midUri.path?.removePrefix("/"))?.trim().orEmpty()
        if (songMid.isBlank()) return@withContext null

        // ① 官方链路（代理 + vkey）
        if (qqMusicStreamProxy.ensureReady(5_000L) &&
            !qqMusicStreamProxy.resolveAndCacheStreamUrl(songMid).isNullOrBlank()
        ) {
            qqMusicStreamProxy.resolveQqMusicUri(uriString)
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext Uri.parse(it) }
        }
        android.util.Log.w(
            "DualPlayerEngine",
            "resolveQqMusicUri: 官方 vkey 不可用 (songMid=$songMid) -> 走落雪/内置源兜底"
        )

        // ② 落雪 JS 引擎 tx 源（无须歌名/歌手，只需 songmid）。
        //    超时给 5s：只为兜住"首次播放时引擎还在初始化"，未安装插件时不至于
        //    在兜底前白等太久（awaitReady 未就绪会一直等到超时）。
        if (lxJsEngine.awaitReady(5_000)) {
            val songMap = mapOf<String, Any?>(
                "id" to songMid,
                "vid" to songMid,
                "songmid" to songMid,
                "hash" to songMid,
                "source" to "tx"
            )
            val chain = MusicQualityCatalog.resolveChain(
                target = musicQualityLxValue,
                available = availableQualitiesFor("tx")
            )
            for (q in chain) {
                val lxUrl = runCatching { lxJsEngine.getPlayUrl("tx", songMap, q) }.getOrNull()
                if (!lxUrl.isNullOrBlank()) return@withContext Uri.parse(lxUrl)
            }
        } else {
            android.util.Log.w("DualPlayerEngine", "resolveQqMusicUri: 落雪引擎未就绪，跳过 lx tx 源")
        }

        // ③ 内置源官方接口（溯音酷我）：需要"歌名 + 歌手"，从媒体库按 contentUri 反查
        val song = loadSongByContentUri("qqmusic://$songMid")
        if (song != null && song.title.isNotBlank()) {
            val lxSongInfo = com.theveloper.pixelplay.data.lx.LxSongInfo(
                id = songMid,
                songmid = songMid,
                hash = songMid,
                name = song.title,
                singer = song.artist,
                duration = song.duration,
                pic = song.albumArtUriString.orEmpty(),
                source = "tx"
            )
            val chain = MusicQualityCatalog.resolveChain(
                target = musicQualityLxValue,
                available = availableQualitiesFor("tx")
            )
            for (q in chain) {
                val url = builtInSourceSearchApi.resolvePlayUrl("tx", lxSongInfo, q)
                if (!url.isNullOrBlank()) {
                    android.util.Log.d(
                        "DualPlayerEngine",
                        "resolveQqMusicUri: 内置源兜底命中 songMid=$songMid quality=$q"
                    )
                    return@withContext Uri.parse(url)
                }
            }
        }
        android.util.Log.w("DualPlayerEngine", "resolveQqMusicUri: 所有链路均失败 songMid=$songMid")
        null
    }

    /** 按 contentUriString 反查媒体库中的歌曲（用于给 QQ 歌曲做"歌名+歌手"兜底解析） */
    private suspend fun loadSongByContentUri(contentUri: String): Song? = runCatching {
        musicRepository.getSongIdByContentUri(contentUri)?.let { songId ->
            musicRepository.getSong(songId.toString()).first()
        }
    }.getOrNull()


    private suspend fun resolveNavidromeUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!navidromeStreamProxy.ensureReady(5_000L)) return@withContext null
        navidromeStreamProxy.warmUpStreamUrl(uriString)
        navidromeStreamProxy.resolveNavidromeUri(uriString)?.toUri()
    }

    private suspend fun resolveJellyfinUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!jellyfinStreamProxy.ensureReady(5_000L)) return@withContext null
        jellyfinStreamProxy.warmUpStreamUrl(uriString)
        jellyfinStreamProxy.resolveJellyfinUri(uriString)?.toUri()
    }

    private suspend fun resolveGDriveUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        if (!gdriveStreamProxy.ensureReady(5_000L)) return@withContext null
        gdriveStreamProxy.resolveGDriveUri(uriString)?.toUri()
    }

    private suspend fun resolveCloudLxUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        // Parse URI format: cloud://lx/{urlEncodedJson}
        val jsonPart = uriString.removePrefix("cloud://lx/")
        if (jsonPart.isEmpty()) return@withContext null

        try {
            val decoded = java.net.URLDecoder.decode(jsonPart, "UTF-8")
            val json = org.json.JSONObject(decoded)
            val songMap = mutableMapOf<String, Any?>()
            songMap["id"] = json.optString("id", "")
            songMap["vid"] = json.optString("id", "")
            // ⚡ 空串也要兜底：optString 的默认值仅在 key 缺失时生效，
            //   占位 JSON 里 songmid/hash 可能是 ""（如酷我歌单歌曲无 hash），
            //   必须像 LxMusicViewModel.toInfoMap 一样用 ifBlank 回落到 id
            songMap["songmid"] = json.optString("songmid", "").ifBlank { songMap["id"] as String }
            songMap["hash"] = json.optString("hash", "").ifBlank { songMap["id"] as String }
            songMap["name"] = json.optString("name", "")
            val singerValue = json.optString("singer", "")
            songMap["singer"] = singerValue
            // 多歌手支持：artists 必须传数组（{id,name} 对象），
            // 否则脚本读取 musicInfo.artists 时拿到字符串会解析失败
            val idList = json.optString("artistIds", "").split(",")
                .map { it.trim() }.filter { it.isNotBlank() }
            val nameList = com.theveloper.pixelplay.data.stream.CloudMusicUtils.parseArtistNames(singerValue)
            songMap["artists"] = nameList.mapIndexed { index, name ->
                mapOf("id" to idList.getOrNull(index).orEmpty(), "name" to name)
            }
            songMap["artistIds"] = idList
            val albumValue = json.optString("album", "")
            songMap["album"] = albumValue
            songMap["albumName"] = albumValue
            val picValue = json.optString("pic", "")
            songMap["pic"] = picValue
            songMap["cover"] = picValue
            if (json.has("duration")) {
                songMap["duration"] = json.getLong("duration")
            }
            // 从收藏时记录的信息中获取音源；
            // 如果没有记录，则优先用 JS 引擎注册过的音源。
            val savedSource = json.optString("source", "").trim()
            val availableSources = runCatching {
                lxJsEngine.getSources().keys.filter { it in listOf("wy", "tx", "kw", "kg", "mg", "qsvip") }
            }.getOrDefault(emptyList())
            val targetSources = if (savedSource.isNotBlank()) {
                // 优先用收藏时成功的音源，失败后再尝试其他注册过的音源
                listOf(savedSource) + availableSources.filter { it != savedSource }
            } else {
                availableSources.ifEmpty { listOf("wy", "tx") }
            }
            android.util.Log.d(
                "DualPlayerEngine",
                "resolveCloudLxUri: savedSource=$savedSource targetSources=$targetSources song=${songMap["name"]}"
            )

            // 等待落雪引擎就绪（首次播放引擎可能还在初始化，多等几秒避免直接失败）。
            // ★ 引擎未就绪时不能直接返回 null：内置源（tx/kg/mg/kw）由
            //   builtInSourceSearchApi 官方接口兜底，无需安装 JS 插件即可播放
            //   （排行榜歌曲在未安装音源插件时正是靠这条链路）。
            // ⚡ 插件优先：只要用户导入的 JS 音源注册了目标音源，就等待引擎就绪。
            //   否则冷启动瞬间 isReady()=false，会退化成内置源 → 仍可能出现 410。
            val pluginSources = lxJsEngine.getSources().keys
            val needsLxEngine = targetSources.any { it in pluginSources } ||
                targetSources.any { !builtInSourceSearchApi.isSupported(it) }
            val lxReady = if (needsLxEngine) lxJsEngine.awaitReady(15_000) else lxJsEngine.isReady()
            if (!lxReady) {
                android.util.Log.w(
                    "DualPlayerEngine",
                    "resolveCloudLxUri: 落雪引擎未就绪，改走内置源官方接口兜底 (targetSources=$targetSources)"
                )
            }

            // ★: 网易云歌曲（纯数字 id + source=wy）落雪引擎优先（音质更高），
            // 官方 neteaseStreamProxy 仅在落雪全部失败后兜底。
            val rawId = songMap["id"]?.toString() ?: ""
            val neteaseSongId = rawId.toLongOrNull()
            val isNeteaseSource = neteaseSongId != null && neteaseSongId > 0 &&
                    (savedSource == "" || savedSource == "wy")

            // 按音源优先级 + 音质优先级 尝试获取播放链接（落雪优先）
            // 内置源（tx/kg/mg/kw）走官方播放接口并遵守音质设置；
            // 其余源（含 wy）走 Lx JS 引擎，同样优先使用用户选择的音质。
            var url: String? = null
            for (source in targetSources) {
                if (url != null) break
                // 按该音源脚本注册的 qualitys 动态生成降级链（不再硬编码 320k/128k）
                val chain = MusicQualityCatalog.resolveChain(
                    target = musicQualityLxValue,
                    available = availableQualitiesFor(source)
                )
                url = if (builtInSourceSearchApi.isSupported(source)) {
                    val lxSongInfo = com.theveloper.pixelplay.data.lx.LxSongInfo(
                        id = songMap["id"]?.toString() ?: "",
                        songmid = songMap["songmid"]?.toString() ?: "",
                        hash = songMap["hash"]?.toString() ?: "",
                        name = songMap["name"]?.toString() ?: "",
                        singer = songMap["singer"]?.toString() ?: "",
                        albumName = songMap["albumName"]?.toString() ?: "",
                        duration = (songMap["duration"] as? Number)?.toLong() ?: 0L,
                        pic = songMap["pic"]?.toString() ?: "",
                        source = source
                    )
                    var resolved: String? = null
                    for (q in chain) {
                        if (resolved != null) break
                        // ★ 用户导入的 JS 音源插件优先：全豆要 v9.x 等插件内部自带
                        //   多源 fallback 链（星海/Huibq/溯音/聆川/长青/念心 SVIP）并对
                        //   结果做校验，比内置第三方聚合稳得多。
                        // 说明：此前把内置源排在插件前面，插件形同虚设；而内置第三方
                        //   聚合返回的酷我签名直链部分已失效 → HTTP 410 Gone →
                        //   ExoPlayer errorCode 2004。仅当插件未安装/未就绪或全部失败时，
                        //   才回落到内置官方接口（tx/mg/kw 走酷我官方直链兜底）。
                        // ⚡ 插件优先、内置官方接口兜底（与 LxMusicViewModel.resolvePlayableSong 一致）。
                        //   注意 Kotlin 优先级：`if (a) x else null ?: y` 会解析成
                        //   `if (a) x else (null ?: y)`——插件就绪但解析失败时内置兜底
                        //   永远不执行，队列占位歌曲（整单入队/搜索排队）会全部解析失败，
                        //   必须给 if 表达式加括号后再走 elvis。
                        resolved = (if (lxReady) lxJsEngine.getPlayUrl(source, songMap, q) else null)
                            // 引擎未就绪时不再调用 lx：getPlayUrl 内部 awaitReady 会阻塞
                            // 15s/次，未装插件时整条音质链会被卡住数十秒。
                            ?: builtInSourceSearchApi.resolvePlayUrl(source, lxSongInfo, q)
                    }
                    resolved
                } else if (lxReady) {
                    var resolved: String? = null
                    for (q in chain) {
                        if (resolved != null) break
                        resolved = lxJsEngine.getPlayUrl(source, songMap, q)
                    }
                    resolved
                } else null
                if (url != null) {
                    android.util.Log.d("DualPlayerEngine", "resolveCloudLxUri: got url from source=$source")
                    break
                }
            }

            // 落雪全失败且为网易云歌曲 → 官方 neteaseStreamProxy 兜底
            if (url == null && isNeteaseSource && neteaseSongId != null) {
                if (neteaseStreamProxy.ensureReady(5_000L)) {
                    val proxyUrl = neteaseStreamProxy.resolveNeteaseUri("netease://$neteaseSongId")
                    if (!proxyUrl.isNullOrBlank()) {
                        android.util.Log.d(
                            "DualPlayerEngine",
                            "resolveCloudLxUri: resolved via neteaseStreamProxy fallback for song $neteaseSongId"
                        )
                        url = proxyUrl
                    }
                } else {
                    android.util.Log.w(
                        "DualPlayerEngine",
                        "resolveCloudLxUri: neteaseStreamProxy unavailable for fallback"
                    )
                }
            }

            if (url != null) Uri.parse(url) else null
        } catch (e: Exception) {
            android.util.Log.e("DualPlayerEngine", "Failed to resolve cloud URI: ${e.message}", e)
            null
        }
    }

    /**
     * Bilibili 真实播放 URL 会过期，因此数据库中只保存 bilibili://{bvid}/{cid}/{aid}。
     * 实际播放前通过此函数重新请求 playurl API 获取最新可用地址。
     */
    private suspend fun resolveBilibiliUri(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }

        val bvid = uri.host?.takeIf { it.isNotBlank() } ?: return@withContext null
        val segments = uri.pathSegments ?: return@withContext null
        val cid = segments.getOrNull(0)?.toLongOrNull() ?: run {
            Timber.w("Bilibili URI missing cid: $uri")
            return@withContext null
        }
        val aidFromPath = segments.getOrNull(1)?.toLongOrNull() ?: 0L

        val realBvid = if (bvid.startsWith("BV", ignoreCase = true)) bvid else ""
        val realAid = if (realBvid.isBlank()) bvid.toLongOrNull() ?: aidFromPath else aidFromPath

        Timber.d("Resolving Bilibili URI: bvid=$realBvid, aid=$realAid, cid=$cid")
        // B 站网络接口偶发慢/风控（OkHttp 读超时 8s），给播放解析加总超时，
        // 避免媒体库点 B 站歌时长时间"卡住"（网络抖动时一次解析最坏等 8s+）。
        val url = try {
            withTimeout(4000) { bilibiliSearchApi.getPlayUrl(realAid, cid, realBvid) }
        } catch (e: TimeoutCancellationException) {
            Timber.w("Bilibili URI resolution timed out: $uri")
            null
        }
        if (url.isNullOrBlank()) {
            Timber.w("Failed to resolve Bilibili playable URL for $uri")
            return@withContext null
        }
        Uri.parse(url)
    }

    suspend fun resolveMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = uri.scheme
        // Use CLOUD_PROXY_SCHEMES: http/https resolve directly via ExoPlayer and never
        // reach resolveCloudUri, so checking them wastes an IO dispatch.
        if (scheme !in CLOUD_PROXY_SCHEMES) return mediaItem
        val resolvedUri = resolveCloudUri(uri)
        return if (resolvedUri == uri) mediaItem else mediaItem.buildUpon().setUri(resolvedUri).build()
    }

    /**
     * 读取当前播放已解析出的真实流 URL（数据源层/播放链路上已写入 resolvedUriCache）。
     * 供音质元数据探测（采样率/码率）使用：mediaItem 的 URI 是 netease:// 等自定义 scheme，
     * 直接用它对网络流探测必然失败；只有这里缓存的真实 http(s) URL 才能抓到流头。
     * 未解析或缓存值不是网络流时返回 null。
     */
    fun getResolvedStreamUri(uri: Uri): Uri? {
        val cached = getFreshResolvedUri(uri.toString()) ?: return null
        val scheme = cached.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") cached else null
    }

    suspend fun prepareNext(target: TransitionTarget, startPositionMs: Long = 0L) {
        prepareNext(target.mediaItem, target.absoluteIndex, startPositionMs)
    }

    suspend fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        val preferredIndex = findMediaItemIndex(
            items = ensureQueueSnapshot(),
            mediaId = mediaItem.mediaId,
            preferAfterExclusive = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, queueSnapshot)
        )
        prepareNext(mediaItem, preferredIndex, startPositionMs)
    }

    private suspend fun prepareNext(mediaItem: MediaItem, preferredAbsoluteIndex: Int, startPositionMs: Long = 0L) {
        try {
            val snapshot = ensureQueueSnapshot()
            val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(playerA.currentMediaItem ?: mediaItem, snapshot)
            val targetIndex = when {
                preferredAbsoluteIndex in snapshot.indices &&
                    snapshot[preferredAbsoluteIndex].mediaId == mediaItem.mediaId -> preferredAbsoluteIndex
                else -> findMediaItemIndex(snapshot, mediaItem.mediaId, currentAbsoluteIndex)
            }
            val resolvedItem = resolveMediaItem(mediaItem)
            val auxiliaryPlayer = getOrCreateAuxiliaryPlayer()

            auxiliaryPlayer.stop()
            auxiliaryPlayer.clearMediaItems()

            if (targetIndex != C.INDEX_UNSET && snapshot.isNotEmpty()) {
                val count = snapshot.size
                val (start, end) = auxiliaryWindowBounds(targetIndex, count)
                val windowItems = ArrayList<MediaItem>(end - start)
                for (i in start until end) {
                    val item = snapshot[i]
                    windowItems.add(if (i == targetIndex) resolvedItem else item)
                }
                preparedWindowStartIndex = start
                preparedPlayerUsesWindowedQueue = count > MAX_AUXILIARY_TIMELINE_ITEMS
                auxiliaryPlayer.setMediaItems(windowItems, targetIndex - start, startPositionMs)
            } else {
                // Fallback for single item if not found in current timeline
                resetPreparedWindowState()
                auxiliaryPlayer.setMediaItem(resolvedItem)
                auxiliaryPlayer.seekTo(startPositionMs)
            }

            auxiliaryPlayer.prepare()
            auxiliaryPlayer.volume = 0f
            auxiliaryPlayer.pause()
        } catch (e: Exception) {
            resetPreparedWindowState()
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    fun cancelNext() {
        val shouldPublishMasterPlayer = transitionRunning
        transitionJob?.cancel()
        transitionRunning = false
        resetPreparedWindowState()
        playerB?.takeIf { it.mediaItemCount > 0 }?.let { auxiliaryPlayer ->
            try {
                auxiliaryPlayer.stop()
                auxiliaryPlayer.clearMediaItems()
            } catch (e: Exception) { /* Ignore */ }
        }
        if (::playerA.isInitialized) {
            playerA.volume = 1f
            if (shouldPublishMasterPlayer) {
                onPlayerSwappedListeners.forEach { it(playerA) }
            }
        }
        incomingTrackReplayGainVolume = null
        setPauseAtEndOfMediaItems(false)
    }

    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionRunning = true
        transitionStartedAtMs = SystemClock.elapsedRealtime()
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.tag("TransitionDebug").e(e, "Error performing transition")
                }
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                playerB?.stop()
            } finally {
                transitionRunning = false
                lastTransitionFinishedAtMs = SystemClock.elapsedRealtime()
                if (transitionStartedAtMs > 0L) {
                    PerformanceMetrics.recordTiming(
                        PerformanceMetrics.Timings.TRANSITION,
                        SystemClock.elapsedRealtime() - transitionStartedAtMs
                    )
                    transitionStartedAtMs = 0L
                }
                onTransitionFinishedListeners.forEach { it() }
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        val auxiliaryPlayer = playerB
        if (auxiliaryPlayer == null || auxiliaryPlayer.mediaItemCount == 0) {
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        // ⚡ 切换前捕获完整队列：过渡完成后 master 会换成 auxiliary 的（窗口）队列，
        //    队列 > MAX_AUXILIARY_TIMELINE_ITEMS 时窗口会被截断（后面的歌"消失"），
        //    且窗口边界的列表循环会错乱。这里先保存完整快照，切换后回填到新 master。
        val fullSnapshotBeforeSwap = ensureQueueSnapshot()

        if (auxiliaryPlayer.playbackState == Player.STATE_IDLE) auxiliaryPlayer.prepare()
        if (auxiliaryPlayer.playbackState == Player.STATE_BUFFERING) {
            if (!awaitPlayerReady(auxiliaryPlayer, 3000L)) {
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                return
            }
        }

        val outgoingStartVolume = playerA.volume.coerceIn(0f, 1f)
        auxiliaryPlayer.volume = 0f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) playerA.play()
        auxiliaryPlayer.playWhenReady = true
        auxiliaryPlayer.play()

        val outgoingPlayer = playerA
        val incomingPlayer = auxiliaryPlayer

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        outgoingPlayer.pauseAtEndOfMediaItems = true
        incomingPlayer.pauseAtEndOfMediaItems = false
        onTransitionDisplayPlayerListeners.forEach { it(incomingPlayer) }

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 32L
        val startedAtMs = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtMost(duration)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volIn = envelope(progress, settings.curveIn)
            val volOut = 1f - envelope(progress, settings.curveOut)
            val incomingTarget = incomingTrackReplayGainVolume ?: 1f
            incomingPlayer.volume = (volIn * incomingTarget).coerceIn(0f, 1f)
            outgoingPlayer.volume = (volOut * outgoingStartVolume).coerceIn(0f, 1f)

            if (elapsed >= duration) break
            delay(stepMs)
        }

        outgoingPlayer.volume = 0f
        incomingPlayer.volume = incomingTrackReplayGainVolume ?: 1f
        incomingTrackReplayGainVolume = null

        removeMasterPlayerListeners(outgoingPlayer)

        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB?.pauseAtEndOfMediaItems = false
        addMasterPlayerListeners(playerA)
        if (playerA.playWhenReady) requestAudioFocus()

        onPlayerSwappedListeners.forEach { it(playerA) }
        _activeAudioSessionId.value = playerA.audioSessionId

        // ⚡ 完整队列回填：auxiliary 预加载的是窗口队列（队列 > 200 首时），直接作为新
        //    master 会导致列表消失 / 列表循环错乱。用切换前保存的完整快照重建新 master
        //    队列。注意不要 stop()：当前曲目正在播放，stop 会打断输出引发爆音；
        //    就绪状态下 setMediaItems 会无缝替换队列（当前曲目与进度保持不变）。
        if (activePlayerUsesWindowedQueue || playerA.mediaItemCount != fullSnapshotBeforeSwap.size) {
            try {
                val resolvedTarget = playerA.currentMediaItem
                val fullItems = fullSnapshotBeforeSwap.map { item ->
                    if (resolvedTarget != null && item.mediaId == resolvedTarget.mediaId) resolvedTarget else item
                }
                val targetAbsIndex = fullItems.indexOfFirst { it.mediaId == resolvedTarget?.mediaId }
                    .takeIf { it >= 0 } ?: 0
                val currentPos = playerA.currentPosition
                playerA.setMediaItems(fullItems, targetAbsIndex, currentPos)
                if (playerA.playbackState == Player.STATE_IDLE) playerA.prepare()
                if (playerA.playWhenReady && !playerA.isPlaying) playerA.play()
                activeWindowStartIndex = 0
                activePlayerUsesWindowedQueue = false
                queueSnapshot = fullItems
                Timber.tag("TransitionDebug").d(
                    "Backfilled full queue into master (size=%d, index=%d) after windowed transition",
                    fullItems.size, targetAbsIndex
                )
            } catch (e: Exception) {
                Timber.tag("TransitionDebug").w(e, "Failed to backfill full queue after transition")
            }
        }

        playerB?.pause()
        playerB?.stop()
        playerB?.clearMediaItems()

        setPauseAtEndOfMediaItems(false)
    }

    private fun ensureQueueSnapshot(): List<MediaItem> {
        // Single guard: isEmpty() short-circuits the windowed-queue size check, so
        // refreshQueueSnapshotFromMaster() is called at most once per invocation.
        if (queueSnapshot.isEmpty() ||
            (!activePlayerUsesWindowedQueue && queueSnapshot.size != playerA.mediaItemCount)
        ) {
            refreshQueueSnapshotFromMaster(windowStartIndex = 0, usesWindowedQueue = false)
        }
        return queueSnapshot
    }

    private fun refreshQueueSnapshotFromMaster(windowStartIndex: Int, usesWindowedQueue: Boolean) {
        if (!::playerA.isInitialized) return

        val count = playerA.mediaItemCount
        if (count <= 0) {
            queueSnapshot = emptyList()
            activeWindowStartIndex = 0
            activePlayerUsesWindowedQueue = false
            return
        }

        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(playerA.getMediaItemAt(i))
        }

        queueSnapshot = items
        activeWindowStartIndex = windowStartIndex
        activePlayerUsesWindowedQueue = usesWindowedQueue
    }

    private fun resolveCurrentAbsoluteIndex(mediaItem: MediaItem, snapshot: List<MediaItem>): Int {
        if (snapshot.isEmpty()) return C.INDEX_UNSET

        val playerIndex = playerA.currentMediaItemIndex
        if (activePlayerUsesWindowedQueue) {
            val absoluteIndex = activeWindowStartIndex + playerIndex
            if (absoluteIndex in snapshot.indices &&
                snapshot[absoluteIndex].mediaId == mediaItem.mediaId
            ) {
                return absoluteIndex
            }
        } else if (playerIndex in snapshot.indices &&
            snapshot[playerIndex].mediaId == mediaItem.mediaId
        ) {
            return playerIndex
        }

        return findMediaItemIndex(snapshot, mediaItem.mediaId, preferAfterExclusive = C.INDEX_UNSET)
    }

    private fun findMediaItemIndex(
        items: List<MediaItem>,
        mediaId: String,
        preferAfterExclusive: Int
    ): Int {
        var fallback = C.INDEX_UNSET
        for (i in items.indices) {
            if (items[i].mediaId == mediaId) {
                if (preferAfterExclusive != C.INDEX_UNSET && i > preferAfterExclusive) return i
                if (fallback == C.INDEX_UNSET) fallback = i
            }
        }
        return fallback
    }

    private fun auxiliaryWindowBounds(targetIndex: Int, count: Int): Pair<Int, Int> {
        if (count <= MAX_AUXILIARY_TIMELINE_ITEMS) return 0 to count

        val halfWindow = MAX_AUXILIARY_TIMELINE_ITEMS / 2
        var start = (targetIndex - halfWindow).coerceAtLeast(0)
        var end = (start + MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtMost(count)
        start = (end - MAX_AUXILIARY_TIMELINE_ITEMS).coerceAtLeast(0)
        return start to end
    }

    private fun resetPreparedWindowState() {
        preparedWindowStartIndex = 0
        preparedPlayerUsesWindowedQueue = false
    }

    private suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
        if (player.playbackState == Player.STATE_READY) return true
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_BUFFERING) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(playbackState == Player.STATE_READY)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        } ?: false
    }


    fun release() {
        transitionJob?.cancel()
        preResolutionJob?.cancel()
        cancelAudioOffloadFallback()
        scope.coroutineContext[Job]?.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            removeMasterPlayerListeners(playerA)
            onPlayerAboutToBeReleasedListener?.invoke(playerA)
            playerA.release()
        }
        playerB?.release()
        playerB = null
        isReleased = true
    }
}
