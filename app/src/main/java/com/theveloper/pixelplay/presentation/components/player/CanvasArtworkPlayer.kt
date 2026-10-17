/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Rhythm (https://github.com/chromahub/rhythm-app) — Apple Music Canvas dynamic artwork.
 *
 * 动态封面渲染端：把 Canvas 视频 URL（HLS / MP4）用独立 ExoPlayer 循环静音播放，
 * 渲染到 TextureView（可正确叠进 Compose 图层）。首帧渲染完成前 alpha=0 ——
 * 上层静态封面保持可见，首帧就绪后 500ms 渐入，形成「静态封面 → 动态封面」的渐变切换；
 * primary 播放失败自动切 fallbackUrl。
 */

package com.theveloper.pixelplay.presentation.components.player

import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isEmpty
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    alwaysPlay: Boolean = false
) {
    val context = LocalContext.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return

    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var videoAspectRatio by remember(initial) { mutableFloatStateOf(0f) }

    // Resolve effective play state — alwaysPlay overrides isPlaying
    val effectivePlaying = alwaysPlay || isPlaying

    // Keep effective playing state up to date without rebuilding the player
    val currentIsPlaying by rememberUpdatedState(effectivePlaying)

    val exoPlayer = remember(initial) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 6_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }

        // Use the application context so the player never retains the Activity
        // (media3 holds the builder context in its codec adapter factory).
        ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus= */ false,
                )
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = effectivePlaying
            }
    }

    // Sync effective play state
    LaunchedEffect(effectivePlaying) {
        exoPlayer.playWhenReady = effectivePlaying
    }

    // Error fallback and aspect ratio listener
    DisposableEffect(exoPlayer, primary, fallback) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val next = if (currentUrl == primary) fallback else null
                if (!next.isNullOrBlank()) {
                    currentUrl = next
                    isVideoReady = false
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Load media when URL changes
    LaunchedEffect(currentUrl) {
        val normalized = currentUrl.trim()
        val isM3u8 = normalized.contains(".m3u8", ignoreCase = true)
            || normalized.lowercase(Locale.ROOT).substringBefore("?").endsWith(".m3u8")
        val isApple = normalized.contains("apple.com") || normalized.contains("itunes.apple")
        val mimeType = when {
            isM3u8 || isApple -> MimeTypes.APPLICATION_M3U8
            normalized.lowercase(Locale.ROOT).contains(".mp4") -> MimeTypes.VIDEO_MP4
            else -> MimeTypes.APPLICATION_M3U8
        }

        val mediaItem = MediaItem.Builder()
            .setUri(normalized)
            .setMimeType(mimeType)
            .build()

        isVideoReady = false
        videoAspectRatio = 0f
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = currentIsPlaying
    }

    // Release when leaving composition
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "canvasAlpha"
    )

    AndroidView(
        factory = { ctx ->
            AspectRatioFrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                // Ensure TextureView is added only once to avoid re-creation issues
                if (isEmpty()) {
                    val textureView = TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    addView(textureView)
                    // TextureView is required for proper layering and Compose layout blurs/blends
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        update = { view ->
            if (videoAspectRatio > 0f) {
                view.setAspectRatio(videoAspectRatio)
            }
        },
        modifier = modifier.alpha(alpha),
    )
}
