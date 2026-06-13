@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

internal val LocalMaterialTheme = compositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return sanitizeNavigationBarBottomInset(insets.calculateBottomPadding())
}

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true,
    currentPositionProvider: () -> Long = { 0L },
    totalDurationProvider: () -> Long = { 0L }
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    // 进度条颜色：使用 primaryContainer 中略深一点的变体，保证对比度
    val progressColor = LocalMaterialTheme.current.onPrimaryContainer
        .copy(alpha = 0.30f)

    // 进度条右侧圆角（左侧为直角，右侧为小圆角，避免"半圆"感）
    val progressRightCornerRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }

    // ⚡ 关键优化：读取播放器位置数据发生在绘制阶段，不触发重新组合/布局。
    // 同时避免使用 fillMaxWidth(fraction = ...) 这种每帧都会触发重新测量的写法，
    // 改为使用图形层变换（graphicsLayer.scaleX），进度条宽度在布局时就已经确定了。
    // 我们把进度放在最外层 Box 的 graphicsLayer 里绘制，避免子元素重新布局。
    val progressFractionProvider = remember(currentPositionProvider, totalDurationProvider) {
        {
            val total = totalDurationProvider()
            if (total <= 0L) 0f
            else (currentPositionProvider().toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // ⚡ 只在绘制阶段读取进度，不会触发子树重新组合或重新布局。
            .drawWithCache {
                onDrawBehind {
                    val fraction = progressFractionProvider()
                    if (fraction <= 0f) return@onDrawBehind
                    val width = this.size.width
                    val height = this.size.height
                    val progressWidth = width * fraction
                    // 画一个左侧直角、右侧小圆角的矩形作为进度条
                    val top = 0f
                    val bottom = height
                    val left = 0f
                    val right = progressWidth.coerceAtLeast(0f)
                    val r = progressRightCornerRadiusPx.coerceAtMost((right - left) * 0.5f)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left, top)
                        lineTo(right - r, top)
                        quadraticTo(right, top, right, top + r)
                        lineTo(right, bottom - r)
                        quadraticTo(right, bottom, right - r, bottom)
                        lineTo(left, bottom)
                        close()
                    }
                    drawPath(path, progressColor)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
            Box(contentAlignment = Alignment.Center) {
                key(song.id) {
                    SmartImage(
                        model = albumArtModel,
                        contentDescription = "Carátula de ${song.title}",
                        shape = CircleShape,
                        targetSize = Size(150, 150),
                        modifier = Modifier.size(44.dp),
                        placeholderModel = if (albumArtModel?.startsWith("telegram_art") == true) {
                            "$albumArtModel?quality=thumb"
                        } else null
                    )
                }
                if (isCastConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = LocalMaterialTheme.current.onPrimaryContainer
                    )
                } else if (isPreparingPlayback) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val titleStyle = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    fontFamily = GoogleSansRounded,
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
                val artistStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    fontFamily = GoogleSansRounded,
                    color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
                )

                AutoScrollingText(
                    text = when {
                        isCastConnecting -> "Connecting to device…"
                        isPreparingPlayback -> "Preparing playback…"
                        else -> song.title
                    },
                    style = titleStyle,
                    gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                    canScroll = canScroll
                )
                AutoScrollingText(
                    text = if (isPreparingPlayback) "Loading audio…" else song.displayArtist,
                    style = artistStyle,
                    gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                    canScroll = canScroll
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LocalMaterialTheme.current.onPrimary)
                    .clickable(
                        interactionSource = previousInteraction,
                        indication = miniPlayerIndication,
                        enabled = controlsEnabled
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = LocalMaterialTheme.current.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LocalMaterialTheme.current.primary)
                    .clickable(
                        interactionSource = playPauseInteraction,
                        indication = miniPlayerIndication,
                        enabled = controlsEnabled
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = LocalMaterialTheme.current.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LocalMaterialTheme.current.onPrimary)
                    .clickable(
                        interactionSource = nextInteraction,
                        indication = miniPlayerIndication,
                        enabled = controlsEnabled
                    ) { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = LocalMaterialTheme.current.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
