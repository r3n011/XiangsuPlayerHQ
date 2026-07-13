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
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import com.theveloper.pixelplay.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val lxJsEngine: com.theveloper.pixelplay.data.lx.LxJsEngine,
    private val audioEngineSettings: com.theveloper.pixelplay.data.service.audioengine.AudioEngineSettings,
    private val audioProcessorProvider: AudioProcessorProvider,
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
        private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "cloud")
        // Subset of REMOTE_MEDIA_SCHEMES: schemes that need proxy resolution.
        // http/https resolve directly and must NOT enter the resolvedUriCache lookup path.
        private val CLOUD_PROXY_SCHEMES = setOf("telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "cloud")
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
            } else {
                cancelAudioOffloadFallback()
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
            lastMediaItemTransitionAtMs = SystemClock.elapsedRealtime()
            cancelAudioOffloadFallback()
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
                    scheduleAudioOffloadFallbackIfNeeded(playerA)
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    bufferingStartedAtMs = 0L
                    cancelAudioOffloadFallback()
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
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

    fun setVolume(volume: Float) {
        if (::playerA.isInitialized) {
            playerA.volume = volume.coerceIn(0f, 1f)
        }
    }

    private var isReleased = false
    private val resolvedUriCache = LruCache<String, Uri>(100)

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
                player.stop()
                player.clearMediaItems()
                // Rebuild the media items from the queue snapshot, so URL
                // resolution runs again
                val snapshot = ensureQueueSnapshot()
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
        }
    }

    /**
     * Returns a [DefaultLoadControl] tuned to the device's RAM tier.
     *
     * Low-RAM devices ([ActivityManager.isLowRamDevice]) receive halved buffer ceilings
     * to prevent memory pressure when both players co-exist during a crossfade.
     * [bufferForPlaybackMs] is set to ExoPlayer's documented default of 2 500 ms on both
     * tiers — the previous value of 5 000 ms doubled first-audio latency with no benefit.
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
                    /* bufferForPlaybackAfterRebufferMs */  5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 30_000,
                    /* maxBufferMs                      */ 60_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  5_000
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
                            }
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
                    val cached = resolvedUriCache.get(originalUri)
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
                                    if (qqMusicStreamProxy.ensureReady(5_000L)) {
                                        qqMusicStreamProxy.resolveQqMusicUri(originalUri)
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { Uri.parse(it) }
                                    } else null
                                }
                                "telegram" -> {
                                    telegramRepository.resolveTelegramUri(originalUri)?.first
                                        ?.let { telegramStreamProxy -> Uri.parse(telegramStreamProxy.toString()) }
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            Timber.tag("DualPlayerEngine").w(e, "Failed to resolve cloud URI: $originalUri")
                            null
                        }
                    }
                    if (resolvedNow != null && resolvedNow.toString().isNotBlank()) {
                        resolvedUriCache.put(originalUri, resolvedNow)
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build().apply {
            setAudioAttributes(audioAttributes, false)
            val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (audioOffloadEnabled) {
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

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = shouldPause
        }
    }

    fun getNextTransitionTarget(currentMediaItem: MediaItem, repeatMode: Int): TransitionTarget? {
        val snapshot = ensureQueueSnapshot()
        if (snapshot.isEmpty()) return null

        val currentAbsoluteIndex = resolveCurrentAbsoluteIndex(currentMediaItem, snapshot)
        if (currentAbsoluteIndex == C.INDEX_UNSET) return null

        val targetIndex = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> currentAbsoluteIndex
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

    fun setMusicQuality(quality: com.theveloper.pixelplay.data.preferences.MusicQuality) {
        musicQualityLxValue = quality.lxValue
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        resolvedUriCache.get(uriString)?.let { return@withContext it }

        val resolved: Uri? = when (uri.scheme) {
            "telegram" -> resolveTelegramUriAsync(uri, uriString)
            "netease" -> resolveNeteaseUriAsync(uriString)
            "qqmusic" -> resolveQqMusicUriAsync(uriString)
            "navidrome" -> resolveNavidromeUriAsync(uriString)
            "jellyfin" -> resolveJellyfinUriAsync(uriString)
            "gdrive" -> resolveGDriveUriAsync(uriString)
            "cloud" -> resolveCloudLxUriAsync(uriString)
            else -> null
        }

        if (resolved != null) {
            resolvedUriCache.put(uriString, resolved)
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
        if (!neteaseStreamProxy.ensureReady(5_000L)) return@withContext null
        neteaseStreamProxy.resolveNeteaseUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveQqMusicUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!qqMusicStreamProxy.ensureReady(5_000L)) return@withContext null
        qqMusicStreamProxy.warmUpStreamUrl(uriString)
        qqMusicStreamProxy.resolveQqMusicUri(uriString)?.let { Uri.parse(it) }
    }

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
            songMap["songmid"] = json.optString("songmid", songMap["id"] as String)
            songMap["hash"] = json.optString("hash", songMap["id"] as String)
            songMap["name"] = json.optString("name", "")
            val singerValue = json.optString("singer", "")
            songMap["singer"] = singerValue
            songMap["artists"] = singerValue
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

            if (!lxJsEngine.isReady()) {
                val loaded = runCatching { lxJsEngine.ready() }.getOrDefault(false)
                if (!loaded) return@withContext null
            }

            // ★: 如果 ID 是纯数字且音源为 "wy"（网易云），
            // 优先走 neteaseStreamProxy → NeteaseRepository.getSongUrl，
            // 通过官方 API 获取 m701.music.126.net 等可播放链接，
            // 避免 lxJsEngine 返回的 175.27.166.236/wy/wy.php?... 404。
            val rawId = songMap["id"]?.toString() ?: ""
            val neteaseSongId = rawId.toLongOrNull()
            val isNeteaseSource = neteaseSongId != null && neteaseSongId > 0 &&
                    (savedSource == "" || savedSource == "wy")
            if (isNeteaseSource && neteaseSongId != null) {
                if (neteaseStreamProxy.ensureReady(5_000L)) {
                    val proxyUrl = neteaseStreamProxy.resolveNeteaseUri("netease://$neteaseSongId")
                    if (!proxyUrl.isNullOrBlank()) {
                        android.util.Log.d(
                            "DualPlayerEngine",
                            "resolveCloudLxUri: resolved via neteaseStreamProxy for song $neteaseSongId"
                        )
                        return@withContext Uri.parse(proxyUrl)
                    }
                }
                android.util.Log.w(
                    "DualPlayerEngine",
                    "resolveCloudLxUri: neteaseStreamProxy unavailable, falling back to lxJsEngine"
                )
            }

            // 按音源优先级 + 音质优先级 尝试获取播放链接
            var url: String? = null
            for (source in targetSources) {
                if (url != null) break
                url = lxJsEngine.getPlayUrl(source, songMap, musicQualityLxValue)
                    ?: lxJsEngine.getPlayUrl(source, songMap, "320k")
                    ?: lxJsEngine.getPlayUrl(source, songMap, "128k")
                if (url != null) {
                    android.util.Log.d("DualPlayerEngine", "resolveCloudLxUri: got url from source=$source")
                    break
                }
            }

            if (url != null) Uri.parse(url) else null
        } catch (e: Exception) {
            android.util.Log.e("DualPlayerEngine", "Failed to resolve cloud URI: ${e.message}", e)
            null
        }
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
