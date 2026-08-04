package com.theveloper.pixelplay.presentation.viewmodel.exts

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.theveloper.pixelplay.data.service.player.HiResSampleRateCapAudioProcessor
import com.theveloper.pixelplay.data.service.player.SurroundDownmixProcessor
import com.theveloper.pixelplay.presentation.components.UsbDacWithAudioEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors

@OptIn(UnstableApi::class)
class DeckController(
    private val context: Context
) {
    var player: ExoPlayer? = null
        private set

    fun loadSong(songUri: Uri) {
        release()
        player = buildSafePlayer().apply {
            setMediaItem(MediaItem.fromUri(songUri))
            prepare()
        }
    }

    private fun buildSafePlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                // ⚡ AAudio 后端控制：尝试从 Hilt EntryPoint 获取设置，失败则默认 Android O+ 启用
                val useAaudio = try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        UsbDacWithAudioEngineEntryPoint::class.java
                    )
                    entryPoint.audioEngineSettings.aaudioEnabled.value &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                } catch (t: Throwable) {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                }
                try {
                    if (!useAaudio) {
                        System.setProperty("androidx.media3.exoplayer.audio.DefaultAudioSink.disableAAudio", "true")
                    } else {
                        System.clearProperty("androidx.media3.exoplayer.audio.DefaultAudioSink.disableAAudio")
                    }
                } catch (t: Throwable) {
                    // ignore
                }

                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            HiResSampleRateCapAudioProcessor(),
                            SurroundDownmixProcessor()
                        )
                    )
                    .build()
            }

            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }

            override fun buildTextRenderers(
                context: Context,
                eventListener: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Audio-only player
            }
        }.setEnableAudioFloatOutput(false)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, false)
                val offloadDisabledPrefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .build()
                setTrackSelectionParameters(
                    trackSelectionParameters
                        .buildUpon()
                        .setAudioOffloadPreferences(offloadDisabledPrefs)
                        .build()
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
            }
    }

    fun playPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seek(progress: Float) {
        val duration = player?.duration?.takeIf { it > 0 } ?: return
        val position = (duration * progress).toLong()
        player?.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    fun nudge(amountMs: Long) {
        val duration = player?.duration ?: return
        val currentPosition = player?.currentPosition ?: return
        val newPosition = (currentPosition + amountMs).coerceIn(0, duration)
        player?.seekTo(newPosition)
    }

    fun setDeckVolume(deckVolume: Float) {
        player?.volume = deckVolume
    }

    fun getProgress(): Float {
        val duration = player?.duration?.takeIf { it > 0 } ?: return 0f
        val position = player?.currentPosition ?: return 0f
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        player?.release()
        player = null
    }
}
