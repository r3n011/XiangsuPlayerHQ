@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalAppFontFamily

internal val LocalMaterialTheme = compositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp

/**
 * Expressive 迷你条槽位高度（对齐 Rhythm ExpressiveMiniPlayer）：
 * 外层槽位 84dp，内部上下各 6dp 内缩 → 可见胶囊 72dp。
 */
val ExpressiveMiniPlayerHeight = 84.dp
const val ANIMATION_DURATION_MS = 255
val MiniPlayerBottomSpacer = 8.dp

/**
 * 当前迷你条样式对应的槽位高度。由宿主在根部按样式提供；
 * 各页面的底部预留、导航栏偏移都读取这个值，切样式时不会出现遮挡/空隙。
 */
val LocalMiniPlayerSlotHeight = compositionLocalOf { MiniPlayerHeight }

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
    totalDurationProvider: () -> Long = { 0L },
    expansionFractionProvider: () -> Float = { 0f },
    /** 迷你条样式（CLASSIC / MATERIAL / EXPRESSIVE），见 MiniPlayerStyle */
    miniPlayerStyle: String = com.theveloper.pixelplay.data.preferences.MiniPlayerStyle.CLASSIC.name
) {
    // MATERIAL = Rhythm 的 MaterialMiniPlayer（三键 + 卡片底）
    val isMaterialStyle = miniPlayerStyle ==
        com.theveloper.pixelplay.data.preferences.MiniPlayerStyle.MATERIAL.name
    // EXPRESSIVE = Rhythm 的 ExpressiveMiniPlayer（胶囊 + 不透明进度填充 + 单个大播放键）
    val isExpressiveStyle = miniPlayerStyle ==
        com.theveloper.pixelplay.data.preferences.MiniPlayerStyle.EXPRESSIVE.name

    if (isExpressiveStyle) {
        // ⚡ 1:1 对齐 Rhythm ExpressiveMiniPlayer（84dp 槽位 → 72dp 全胶囊，独立实现）
        ExpressiveMiniPlayerContent(
            song = song,
            isPlaying = isPlaying,
            isCastConnecting = isCastConnecting,
            isPreparingPlayback = isPreparingPlayback,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            canScroll = canScroll,
            currentPositionProvider = currentPositionProvider,
            totalDurationProvider = totalDurationProvider,
            expansionFractionProvider = expansionFractionProvider,
            modifier = modifier
        )
        return
    }

    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }

    // ⚡ Optimization: Animate colors at the component level instead of the top level.
    // This allows smooth transitions while the top-level LocalMaterialTheme only
    // triggers a single recomposition when the target changes.
    val onPrimaryContainer by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onPrimaryContainer,
        animationSpec = tween(durationMillis = 400),
        label = "MiniOnPrimaryContainer"
    )
    val primaryContainer by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.primaryContainer,
        animationSpec = tween(durationMillis = 400),
        label = "MiniPrimaryContainer"
    )
    val primary by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.primary,
        animationSpec = tween(durationMillis = 400),
        label = "MiniPrimary"
    )
    val onPrimary by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onPrimary,
        animationSpec = tween(durationMillis = 400),
        label = "MiniOnPrimary"
    )

    // 进度条颜色：
    // - Expressive（Rhythm ExpressiveMiniPlayer）：不透明 primaryContainer，整条从左往右填充
    // - 其余样式：primaryContainer 中略深一点的半透明变体，保证文字对比度
    val progressColor = if (isExpressiveStyle) {
        primaryContainer
    } else {
        onPrimaryContainer.copy(alpha = 0.30f)
    }

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
                    // 正在获取播放链接、或播放广播实时流时不绘制歌曲进度条
                    if (isPreparingPlayback || song.isRadioStation) return@onDrawBehind
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
        Column(modifier = Modifier.fillMaxSize()) {
            // 主要内容：专辑封面 + 标题 + 艺术家 + 控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight - 3.dp)
                    .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
                Box(contentAlignment = Alignment.Center) {
                    // ⚡ 低性能优化：不再用 key(song.id) 强制卸载/重建封面。
                    // 直接替换 model 会让 Coil 在旧图基础上 crossfade 平滑过渡，
                    // 避免切歌/打开 mini player 时封面因整棵重建而闪动。
                    SmartImage(
                        model = albumArtModel,
                        contentDescription = "Carátula de ${song.title}",
                        // Material / Expressive（对齐 Rhythm）使用圆角方形封面，经典样式保持圆形
                        shape = if (isMaterialStyle || isExpressiveStyle) {
                            RoundedCornerShape(10.dp)
                        } else {
                            CircleShape
                        },
                        targetSize = Size(150, 150),
                        modifier = Modifier
                            .size(if (isExpressiveStyle) 48.dp else 44.dp)
                            .graphicsLayer {
                                val f = expansionFractionProvider().coerceIn(0f, 1f)
                                // 封面移动动画已移除：展开时封面原地淡出，不再缩放/平移到全屏播放器位置
                                alpha = (1f - f * 0.4f).coerceIn(0f, 1f)
                            },
                        placeholderModel = if (albumArtModel?.startsWith("telegram_art") == true) {
                            "$albumArtModel?quality=thumb"
                        } else null
                    )
                    if (isCastConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = onPrimaryContainer
                        )
                    } else {
                        // Smoothly transition the preparing playback indicator
                        val preparingAlpha by animateFloatAsState(
                            targetValue = if (isPreparingPlayback) 1f else 0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "PreparingPlaybackAlpha"
                        )
                        if (preparingAlpha > 0f) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(preparingAlpha)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val f = expansionFractionProvider().coerceIn(0f, 1f)
                            // 歌名/歌手移动动画已移除：展开时原地淡出，不再平移到全屏播放器 metadata 位置
                            alpha = (1f - f * 1.8f).coerceIn(0f, 1f)
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    // Expressive 对齐 Rhythm：歌名 16sp Bold、歌手 13sp
                    val titleStyle = MaterialTheme.typography.titleSmall.copy(
                        fontSize = if (isExpressiveStyle) 16.sp else 15.sp,
                        fontWeight = if (isExpressiveStyle) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        fontFamily = LocalAppFontFamily.current,
                        color = onPrimaryContainer
                    )
                    val artistStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        letterSpacing = 0.sp,
                        fontFamily = LocalAppFontFamily.current,
                        color = onPrimaryContainer.copy(alpha = 0.7f)
                    )

                    AutoScrollingText(
                        text = when {
                            isCastConnecting -> "Connecting to device…"
                            isPreparingPlayback -> AppLocaleManager.getString(R.string.ui_getting_playback_url)
                            else -> song.title
                        },
                        style = titleStyle,
                        gradientEdgeColor = primaryContainer,
                        canScroll = canScroll
                    )
                    AutoScrollingText(
                        text = if (isPreparingPlayback) AppLocaleManager.getString(R.string.ui_please_wait_a_moment) else song.displayArtist,
                        style = artistStyle,
                        gradientEdgeColor = primaryContainer,
                        canScroll = canScroll
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // 控制按钮：展开时快速淡出（移动动画已移除）
                Row(
                    modifier = Modifier.graphicsLayer {
                        val f = expansionFractionProvider().coerceIn(0f, 1f)
                        alpha = (1f - f * 3f).coerceIn(0f, 1f)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ⚡ Expressive（Rhythm ExpressiveMiniPlayer 手机版）只有单个大播放键，无上下曲
                    if (!isExpressiveStyle) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(onPrimary)
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
                                tint = primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(if (isExpressiveStyle) 44.dp else 36.dp)
                            .clip(CircleShape)
                            .background(primary)
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
                            tint = onPrimary,
                            modifier = Modifier.size(if (isExpressiveStyle) 26.dp else 22.dp)
                        )
                    }

                    if (!isExpressiveStyle) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(onPrimary)
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
                                tint = primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 底部细加载条（仅在 preparing 时显示，占满宽度）
            if (isPreparingPlayback) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = onPrimaryContainer,
                    trackColor = onPrimaryContainer.copy(alpha = 0.15f)
                )
            }
        }
    }
}

/** 迷你条时长胶囊文案：mm:ss（超过一小时显示 h:mm:ss） */
private fun formatMiniPlayerTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Expressive 迷你播放器（1:1 对齐 Rhythm ExpressiveMiniPlayer）：
 * - 槽位 84dp 内缩（水平 14dp / 垂直 6dp）→ 可见 72dp 全胶囊（CircleShape），底色 surfaceContainer；
 * - 进度不是进度条，而是**胶囊背景填充**（手机 primaryContainer 不透明 / 平板 45%）；
 * - 手机：60dp 封面（MaterialShapes COOKIE_4）+ 单个 56dp 播放键（无上下曲，左右滑切歌）；
 *   平板：46dp 封面 + 上一曲 36 / 播放 44 / 下一曲 36；
 * - 文本：标题 16sp Bold onSurface、歌手 13sp onSurfaceVariant，两者无间距；
 * - 动画：按下 0.98、首次出现 1.05、换曲 1.02（同 Rhythm 的 spring 节奏）。
 */
@Composable
private fun ExpressiveMiniPlayerContent(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canScroll: Boolean,
    currentPositionProvider: () -> Long,
    totalDurationProvider: () -> Long,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalMaterialTheme.current
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    // Rhythm：首次出现 1.05 弹跳 150ms，换曲 1.02 弹跳 100ms
    var bounceScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(song.id) {
        bounceScale = 1.05f
        kotlinx.coroutines.delay(150)
        bounceScale = 1.02f
        kotlinx.coroutines.delay(100)
        bounceScale = 1f
    }

    // Rhythm：按下 0.98（tween 100）
    val pressInteraction = remember { MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ExpressiveMiniPress"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val f = expansionFractionProvider().coerceIn(0f, 1f)
                alpha = (1f - f * 2f).coerceIn(0f, 1f)
                scaleX = pressScale * bounceScale
                scaleY = pressScale * bounceScale
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = scheme.surfaceContainer
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 进度背景填充（Rhythm：整条从左往右，被胶囊裁剪成同形）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val total = totalDurationProvider()
                            val fraction = if (total <= 0L) 0f
                            else (currentPositionProvider().toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            scaleX = fraction.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(
                            if (isTablet) scheme.primaryContainer.copy(alpha = 0.45f)
                            else scheme.primaryContainer
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面：手机 72dp 盒内缩 6dp → 60dp；平板 56dp 盒内 46dp
                    Box(
                        modifier = if (isTablet) {
                            Modifier.size(56.dp).padding(5.dp)
                        } else {
                            Modifier.fillMaxHeight().aspectRatio(1f).padding(6.dp)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        val artModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
                        SmartImage(
                            model = artModel,
                            contentDescription = song.title,
                            shape = ExpressiveShapes.MiniPlayerArt,
                            targetSize = Size(150, 150),
                            modifier = Modifier.fillMaxSize(),
                            placeholderModel = if (artModel?.startsWith("telegram_art") == true) {
                                "$artModel?quality=thumb"
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        AutoScrollingText(
                            text = when {
                                isCastConnecting -> "Connecting to device…"
                                isPreparingPlayback -> AppLocaleManager.getString(R.string.ui_getting_playback_url)
                                else -> song.title
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isTablet) 14.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = LocalAppFontFamily.current,
                                color = scheme.onSurface
                            ),
                            gradientEdgeColor = scheme.surfaceContainer,
                            canScroll = canScroll
                        )
                        if (isTablet) Spacer(modifier = Modifier.height(2.dp))
                        AutoScrollingText(
                            text = if (isPreparingPlayback) {
                                AppLocaleManager.getString(R.string.ui_please_wait_a_moment)
                            } else {
                                song.displayArtist
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isTablet) 12.sp else 13.sp,
                                fontFamily = LocalAppFontFamily.current,
                                color = scheme.onSurfaceVariant
                            ),
                            gradientEdgeColor = scheme.surfaceContainer,
                            canScroll = canScroll
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isTablet) 10.dp else 12.dp))

                    if (isTablet) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExpressiveMiniIconButton(
                                icon = Icons.Rounded.SkipPrevious,
                                size = 36.dp,
                                iconSize = 20.dp,
                                tint = scheme.onSurface,
                                description = "Previous"
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevious()
                            }
                            ExpressiveMiniPlayButton(
                                size = 44.dp,
                                iconSize = 22.dp,
                                isPlaying = isPlaying,
                                isLoading = isCastConnecting || isPreparingPlayback,
                                container = scheme.primary,
                                content = scheme.onPrimary
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            }
                            ExpressiveMiniIconButton(
                                icon = Icons.Rounded.SkipNext,
                                size = 36.dp,
                                iconSize = 20.dp,
                                tint = scheme.onSurface,
                                description = "Next"
                            ) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNext()
                            }
                        }
                    } else {
                        ExpressiveMiniPlayButton(
                            size = 56.dp,
                            iconSize = 28.dp,
                            isPlaying = isPlaying,
                            isLoading = isCastConnecting || isPreparingPlayback,
                            container = scheme.primary,
                            content = scheme.onPrimary
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        }
                    }
                }
            }
        }
    }
}

/** Expressive 迷你条播放键（Rhythm：圆形 primary 底 + onPrimary 图标，按压 0.92） */
@Composable
private fun ExpressiveMiniPlayButton(
    size: Dp,
    iconSize: Dp,
    isPlaying: Boolean,
    isLoading: Boolean,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "ExpressiveMiniPlayScale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.42f),
                strokeWidth = 2.5.dp,
                color = content,
                trackColor = content.copy(alpha = 0.24f)
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = content,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Expressive 迷你条平板上一曲/下一曲图标按钮（36dp） */
@Composable
private fun ExpressiveMiniIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Dp,
    iconSize: Dp,
    tint: androidx.compose.ui.graphics.Color,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
