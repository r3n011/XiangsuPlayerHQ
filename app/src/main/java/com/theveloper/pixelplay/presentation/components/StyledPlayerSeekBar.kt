@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import androidx.graphics.shapes.toPath
import com.theveloper.pixelplay.R
import kotlin.math.min
import kotlin.math.sin

/**
 * 播放器进度条轨道样式（对齐 Rhythm 的 ProgressStyle）。
 */
enum class PlayerProgressStyle(
    val storageKey: String,
    @StringRes val labelResId: Int,
    @StringRes val descriptionResId: Int
) {
    NORMAL(
        "NORMAL",
        R.string.presentation_batch_f_progress_style_normal,
        R.string.presentation_batch_f_progress_style_normal_desc
    ),
    WAVY(
        "WAVY",
        R.string.presentation_batch_f_progress_style_wavy,
        R.string.presentation_batch_f_progress_style_wavy_desc
    ),
    ROUNDED(
        "ROUNDED",
        R.string.presentation_batch_f_progress_style_rounded,
        R.string.presentation_batch_f_progress_style_rounded_desc
    ),
    THIN(
        "THIN",
        R.string.presentation_batch_f_progress_style_thin,
        R.string.presentation_batch_f_progress_style_thin_desc
    ),
    THICK(
        "THICK",
        R.string.presentation_batch_f_progress_style_thick,
        R.string.presentation_batch_f_progress_style_thick_desc
    ),
    GRADIENT(
        "GRADIENT",
        R.string.presentation_batch_f_progress_style_gradient,
        R.string.presentation_batch_f_progress_style_gradient_desc
    ),
    SEGMENTED(
        "SEGMENTED",
        R.string.presentation_batch_f_progress_style_segmented,
        R.string.presentation_batch_f_progress_style_segmented_desc
    ),
    DOTS(
        "DOTS",
        R.string.presentation_batch_f_progress_style_dots,
        R.string.presentation_batch_f_progress_style_dots_desc
    ),
    WAVEFORM(
        "WAVEFORM",
        R.string.presentation_batch_f_progress_style_waveform,
        R.string.presentation_batch_f_progress_style_waveform_desc
    );

    companion object {
        val default: PlayerProgressStyle = WAVY

        fun fromStorage(value: String?): PlayerProgressStyle =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: default
    }
}

/**
 * 进度条滑块（thumb）样式（对齐 Rhythm 的 ThumbStyle，使用 MaterialShapes 表现力形状）。
 */
enum class PlayerThumbStyle(
    val storageKey: String,
    @StringRes val labelResId: Int,
    val sizeScale: Float = 1f
) {
    NONE("NONE", R.string.presentation_batch_f_progress_thumb_none),
    DEFAULT("DEFAULT", R.string.presentation_batch_f_progress_thumb_default),
    CIRCLE("CIRCLE", R.string.presentation_batch_f_progress_thumb_circle),
    SQUARE("SQUARE", R.string.presentation_batch_f_progress_thumb_square),
    PILL("PILL", R.string.presentation_batch_f_progress_thumb_pill, 1.25f),
    DIAMOND("DIAMOND", R.string.presentation_batch_f_progress_thumb_diamond, 1.25f),
    FLOWER("FLOWER", R.string.presentation_batch_f_progress_thumb_flower, 1.25f),
    HEART("HEART", R.string.presentation_batch_f_progress_thumb_heart, 1.25f),
    COOKIE("COOKIE", R.string.presentation_batch_f_progress_thumb_cookie, 1.25f),
    PUFFY("PUFFY", R.string.presentation_batch_f_progress_thumb_puffy, 1.25f);

    companion object {
        val default: PlayerThumbStyle = DEFAULT

        /** 兼容历史存储值（来自 Rhythm 的旧命名）。 */
        fun fromStorage(value: String?): PlayerThumbStyle = when (value) {
            "NONE" -> NONE
            "DEFAULT", "GLOW", "ARROW" -> DEFAULT
            "OUTLINE", "DOT", "RING", "CIRCLE" -> CIRCLE
            "SQUARE" -> SQUARE
            "PILL", "LINE" -> PILL
            "DIAMOND" -> DIAMOND
            "FLOWER" -> FLOWER
            "HEART" -> HEART
            "COOKIE", "COOKIE_6" -> COOKIE
            "PUFFY" -> PUFFY
            else -> default
        }
    }
}

/** MaterialShapes → 以原点为中心的 Compose Path（等比缩放到 size） */
private fun thumbShapePath(style: PlayerThumbStyle, size: Float): Path? {
    val polygon = when (style) {
        PlayerThumbStyle.CIRCLE, PlayerThumbStyle.DEFAULT -> MaterialShapes.Circle
        PlayerThumbStyle.SQUARE -> MaterialShapes.Square
        PlayerThumbStyle.PILL -> MaterialShapes.Pill
        PlayerThumbStyle.DIAMOND -> MaterialShapes.Diamond
        PlayerThumbStyle.FLOWER -> MaterialShapes.Flower
        PlayerThumbStyle.HEART -> MaterialShapes.Heart
        PlayerThumbStyle.COOKIE -> MaterialShapes.Cookie6Sided
        PlayerThumbStyle.PUFFY -> MaterialShapes.Puffy
        PlayerThumbStyle.NONE -> return null
    }
    val raw = polygon.toPath().asComposePath()
    val bounds = raw.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return raw
    val scale = min(size / bounds.width, size / bounds.height)
    val centerX = bounds.left + bounds.width / 2f
    val centerY = bounds.top + bounds.height / 2f
    val matrix = Matrix().apply {
        translate(-centerX * scale, -centerY * scale)
        scale(scale, scale)
    }
    return raw.apply { transform(matrix) }
}

/**
 * 可交互的播放器进度条，支持 9 种轨道样式 + 10 种滑块样式 + 播放时滑块旋转。
 * 值域固定为 0f..1f 的归一化进度，实际时间换算由调用方负责。
 */
@Composable
fun StyledPlayerSeekBar(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    style: PlayerProgressStyle,
    thumbStyle: PlayerThumbStyle,
    rotateThumbWhenPlaying: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    // GRADIENT 样式的渐变色：由调用方传入，默认回退应用主题色。
    // 播放器内应传入 LocalMaterialTheme（封面取色）的 primary/secondary/tertiary，
    // 使渐变进度条跟随播放器取色而非整个应用的取色。
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    ),
    // WAVEFORM 样式的真实波形：从歌曲音频提取的桶峰值（<0f 表示该段尚未分析）。
    // 传 null 或某段为哨兵值时，该段回退到合成的确定性波形。
    waveformPeaks: FloatArray? = null,
    trackHeight: Dp = 5.dp,
    thumbSize: Dp = 16.dp,
    trackEdgePadding: Dp = 0.dp,
    semanticsLabel: String? = null
) {
    val density = LocalDensity.current
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val stroke = remember(trackHeightPx) { Stroke(width = trackHeightPx, cap = StrokeCap.Round) }

    val showThumb = thumbStyle != PlayerThumbStyle.NONE &&
        style != PlayerProgressStyle.SEGMENTED &&
        style != PlayerProgressStyle.DOTS
    val effectiveThumbSize = if (showThumb) thumbSize * thumbStyle.sizeScale else 0.dp
    val defaultThumbSlotHeight = if (showThumb && thumbStyle == PlayerThumbStyle.DEFAULT) 24.dp else 0.dp

    // 各样式实际绘制高度（与 drawTrack 内的取值保持一致，避免粗条被容器裁切）
    val styleMinHeight = when (style) {
        // 与旧 WavySliderExpressive 一致：波浪样式始终保留 M3 波浪容器高度
        PlayerProgressStyle.WAVY -> WavyProgressIndicatorDefaults.LinearContainerHeight
        PlayerProgressStyle.THIN -> 4.dp
        PlayerProgressStyle.THICK -> 8.dp
        PlayerProgressStyle.ROUNDED -> max(trackHeight, 6.dp)
        PlayerProgressStyle.DOTS -> 6.dp
        // 波形样式需要纵向空间容纳振幅，高度固定在 26dp
        PlayerProgressStyle.WAVEFORM -> WaveformStyleMinHeight
        else -> trackHeight
    }
    val containerHeight = max(styleMinHeight, max(effectiveThumbSize, defaultThumbSlotHeight))

    val thumbPath = remember(thumbStyle, effectiveThumbSize, showThumb) {
        if (!showThumb || thumbStyle == PlayerThumbStyle.DEFAULT) null
        else with(density) { thumbShapePath(thumbStyle, effectiveThumbSize.toPx()) }
    }

    var isDragging by remember { mutableStateOf(false) }
    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "thumbInteraction"
    )

    val animatedAmplitude by animateFloatAsState(
        // 只与播放状态/拖拽相关（不绑定 enabled），使预览中的波浪同样可以动起来
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "waveAmplitude"
    )

    // DEFAULT 滑块与旧实现保持一致：静止为圆点，拖拽时收成竖条；
    // 波浪在滑块处留出的空隙（gapSize 为间隙全宽）随之变化。
    val defaultIdleGapHalf = 6.dp
    val defaultDragGapHalf = trackHeight * 0.6f + 1.2.dp
    val thumbGapHalf = when {
        !showThumb -> 0.dp
        thumbStyle == PlayerThumbStyle.DEFAULT ->
            defaultIdleGapHalf + (defaultDragGapHalf - defaultIdleGapHalf) * thumbInteractionFraction
        else -> effectiveThumbSize / 2f
    }

    // 拖拽中滑块宽度：DEFAULT 从圆点收到竖条，其余样式保持自身尺寸
    val defaultThumbWidth = effectiveThumbSize +
        (trackHeight * 1.2f - effectiveThumbSize) * thumbInteractionFraction
    val defaultThumbHeight = effectiveThumbSize +
        (24.dp - effectiveThumbSize) * thumbInteractionFraction
    val thumbFootprint = if (thumbStyle == PlayerThumbStyle.DEFAULT) defaultThumbWidth else effectiveThumbSize

    val rotationState: State<Float> = if (showThumb && rotateThumbWhenPlaying && isPlaying) {
        rememberInfiniteTransition(label = "thumbRotate").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "thumbRotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value().coerceIn(0f, 1f),
                    range = 0f..1f,
                    steps = 0
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(0f, 1f)
                        latestOnValueChange(coerced)
                        latestOnValueCommit(coerced)
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isVisible) {
            if (style == PlayerProgressStyle.WAVY) {
                LinearWavyProgressIndicator(
                    progress = { value().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                        // 装饰层：避免与父级语义重复
                        .clearAndSetSemantics { },
                    color = activeTrackColor,
                    trackColor = inactiveTrackColor,
                    stroke = stroke,
                    trackStroke = stroke,
                    gapSize = 2f * thumbGapHalf * (1f + 0.1573f * animatedAmplitude * animatedAmplitude),
                    stopSize = 3.dp,
                    amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
                    wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                    waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val startX = edgePadding
                val endX = size.width - edgePadding
                val trackWidth = (endX - startX).coerceAtLeast(0f)
                val centerY = size.height / 2f
                val progress = value().coerceIn(0f, 1f)

                if (style != PlayerProgressStyle.WAVY) {
                    drawTrack(
                        style = style,
                        progress = progress,
                        activeColor = activeTrackColor,
                        inactiveColor = inactiveTrackColor,
                        gradientColors = gradientColors,
                        waveformPeaks = waveformPeaks,
                        startX = startX,
                        endX = endX,
                        centerY = centerY,
                        trackHeightPx = trackHeightPx
                    )
                }

                if (showThumb) {
                    val halfThumb = thumbFootprint.toPx() / 2f
                    val desired = startX + trackWidth * progress
                    val minCenter = startX + halfThumb
                    val maxCenter = endX - halfThumb
                    val thumbCenterX = if (maxCenter >= minCenter) {
                        desired.coerceIn(minCenter, maxCenter)
                    } else {
                        (startX + endX) / 2f
                    }
                    val rotation = rotationState.value

                    if (thumbStyle == PlayerThumbStyle.DEFAULT) {
                        val barWidth = defaultThumbWidth.toPx()
                        val barHeight = defaultThumbHeight.toPx()
                        rotate(rotation, pivot = Offset(thumbCenterX, centerY)) {
                            drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(thumbCenterX - barWidth / 2f, centerY - barHeight / 2f),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f)
                            )
                        }
                    } else {
                        thumbPath?.let { path ->
                            rotate(rotation, pivot = Offset(thumbCenterX, centerY)) {
                                translate(left = thumbCenterX, top = centerY) {
                                    drawPath(path = path, color = thumbColor)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(containerHeight))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, trackEdgePaddingPx) {
                    if (!enabled) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        return ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                    }

                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            down.consume()
                            var latestGestureValue = valueForX(down.position.x)
                            latestOnValueChange(latestGestureValue)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                if (change.position != change.previousPosition) {
                                    change.consume()
                                    latestGestureValue = valueForX(change.position.x)
                                    latestOnValueChange(latestGestureValue)
                                }
                            }

                            latestOnValueCommit(latestGestureValue)
                        } finally {
                            isDragging = false
                        }
                    }
                }
        )
    }
}

private fun DrawScope.drawTrack(
    style: PlayerProgressStyle,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    gradientColors: List<Color>,
    waveformPeaks: FloatArray?,
    startX: Float,
    endX: Float,
    centerY: Float,
    trackHeightPx: Float
) {
    val trackWidth = (endX - startX).coerceAtLeast(0f)
    val progressWidth = trackWidth * progress

    fun bar(width: Float, height: Float, x: Float, color: Color) {
        if (width <= 0f || height <= 0f) return
        drawRoundRect(
            color = color,
            topLeft = Offset(x, centerY - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f)
        )
    }

    when (style) {
        PlayerProgressStyle.NORMAL -> {
            bar(trackWidth, trackHeightPx, startX, inactiveColor)
            bar(progressWidth, trackHeightPx, startX, activeColor)
        }

        PlayerProgressStyle.ROUNDED -> {
            val height = trackHeightPx.coerceAtLeast(6.dp.toPx())
            bar(trackWidth, height, startX, inactiveColor)
            bar(progressWidth, height, startX, activeColor)
        }

        PlayerProgressStyle.THIN -> {
            val height = 2.dp.toPx()
            drawLine(inactiveColor, Offset(startX, centerY), Offset(endX, centerY), height, StrokeCap.Round)
            if (progressWidth > 0f) {
                drawLine(
                    color = activeColor,
                    start = Offset(startX, centerY),
                    end = Offset(startX + progressWidth, centerY),
                    strokeWidth = height,
                    cap = StrokeCap.Round
                )
            }
        }

        PlayerProgressStyle.THICK -> {
            val height = 8.dp.toPx()
            val corner = CornerRadius(4.dp.toPx())
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(startX, centerY - height / 2f),
                size = Size(trackWidth, height),
                cornerRadius = corner
            )
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(startX, centerY - height / 2f),
                    size = Size(progressWidth, height),
                    cornerRadius = corner
                )
            }
        }

        PlayerProgressStyle.GRADIENT -> {
            bar(trackWidth, trackHeightPx, startX, inactiveColor)
            if (progressWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(gradientColors),
                    topLeft = Offset(startX, centerY - trackHeightPx / 2f),
                    size = Size(progressWidth, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2f)
                )
            }
        }

        PlayerProgressStyle.SEGMENTED -> {
            val segments = 20
            val gap = 3.dp.toPx()
            val segmentWidth = ((trackWidth - (segments - 1) * gap) / segments).coerceAtLeast(1f)
            val filledSegments = (progress * segments).toInt()
            for (i in 0 until segments) {
                val x = startX + i * (segmentWidth + gap)
                bar(segmentWidth, trackHeightPx, x, if (i < filledSegments) activeColor else inactiveColor)
            }
        }

        PlayerProgressStyle.DOTS -> {
            val dotCount = 12
            val radius = 3.dp.toPx()
            val activeDots = (progress * dotCount).toInt()
            for (i in 0 until dotCount) {
                val x = startX + trackWidth * (i + 0.5f) / dotCount
                drawCircle(
                    color = if (i < activeDots) activeColor else inactiveColor,
                    radius = radius,
                    center = Offset(x, centerY)
                )
            }
        }

        PlayerProgressStyle.WAVEFORM -> drawWaveformTrack(
            progress = progress,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            peaks = waveformPeaks,
            startX = startX,
            endX = endX,
            centerY = centerY
        )

        PlayerProgressStyle.WAVY -> Unit
    }
}

/** 波形样式的最小绘制高度（容纳振幅起伏）。 */
private val WaveformStyleMinHeight = 26.dp

/** 波形竖条的宽度与间距（刻意比参考图疏，约 4.5dp 一根）。 */
private val WaveformBarWidth = 2.dp
private val WaveformBarGap = 2.5.dp

/**
 * 由竖条序号生成稳定的 0f..1f 振幅：整数哈希提供细碎起伏，低频正弦提供整体包络，
 * 完全确定性 => 重组、拖动进度条时波形不会抖动。
 */
private fun waveformAmplitude(index: Int): Float {
    var h = index * 374761393
    h = (h xor (h ushr 13)) * 1274126177
    val noise = ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    val x = index.toFloat()
    val envelope = (0.52f + 0.28f * sin(x * 0.33f) + 0.20f * sin(x * 0.097f + 1.7f))
        .coerceIn(0.15f, 1f)
    return (envelope * (0.45f + 0.55f * noise)).coerceIn(0f, 1f)
}

/**
 * 波形轨道：以居中的竖条模拟音频振幅，已播放部分用 activeColor，未播放部分用 inactiveColor。
 *
 * [peaks] 为歌曲真实波形（从音频数据提取的桶均值）；该竖条对应的桶全部为哨兵值时，
 * 说明这段还没被分析过，回退到合成的 [waveformAmplitude]。
 */
private fun DrawScope.drawWaveformTrack(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    peaks: FloatArray?,
    startX: Float,
    endX: Float,
    centerY: Float
) {
    val trackWidth = (endX - startX).coerceAtLeast(0f)
    if (trackWidth <= 0f) return

    val barWidth = WaveformBarWidth.toPx()
    val pitch = barWidth + WaveformBarGap.toPx()
    val barCount = (trackWidth / pitch).toInt().coerceAtLeast(1)
    val usedWidth = barCount * pitch - WaveformBarGap.toPx()
    val originX = startX + (trackWidth - usedWidth) / 2f

    // 上下各留一点留白，避免波形贴满容器显得像色块
    val maxBarHeight = (size.height * 0.92f).coerceAtLeast(barWidth)
    val minBarHeight = (maxBarHeight * 0.08f).coerceAtLeast(barWidth * 0.5f)
    val progressX = startX + trackWidth * progress.coerceIn(0f, 1f)

    // 真实桶值来自 sqrt(|sample|)，普遍挤在 0.7~1.0 的高位；直接当高度用会让每根竖条
    // 都接近满高、看不出起伏。这里按本曲已分析桶的 [lo, hi] 拉伸到 0..1 恢复对比度。
    val range = peaks?.let { waveformRangeOf(it) }
    val lo = range?.first ?: 0f
    val span = if (range != null) range.second - range.first else 0f
    val realPeaks = if (peaks != null && range != null && span > 0.02f) peaks else null

    for (i in 0 until barCount) {
        val x = originX + i * pitch
        val amplitude = if (realPeaks == null) {
            waveformAmplitude(i)
        } else {
            val raw = realAmplitudeForBar(realPeaks, i, barCount)
            if (raw == null) waveformAmplitude(i) else ((raw - lo) / span).coerceIn(0f, 1f)
        }
        val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * amplitude
        drawRoundRect(
            color = if (x + barWidth / 2f <= progressX) activeColor else inactiveColor,
            topLeft = Offset(x, centerY - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f)
        )
    }
}

/**
 * 取第 [barIndex] 根竖条覆盖的桶区间内、已分析桶的**平均**幅度；
 * 该区间全是未分析桶（<0f）时返回 null，交由调用方回退合成波形。
 *
 * 用平均而非最大值：单个桶取 max 会把每段的最强瞬态都保留下来，安静段落也会顶到高位。
 */
private fun realAmplitudeForBar(peaks: FloatArray, barIndex: Int, barCount: Int): Float? {
    val bucketCount = peaks.size
    if (bucketCount <= 0 || barCount <= 0) return null
    val start = (barIndex.toLong() * bucketCount / barCount).toInt()
    val end = (((barIndex + 1).toLong() * bucketCount / barCount).toInt())
        .coerceAtLeast(start + 1)
        .coerceAtMost(bucketCount)
    var sum = 0f
    var known = 0
    for (b in start until end) {
        val v = peaks[b]
        if (v < 0f) continue
        sum += v
        known++
    }
    return if (known == 0) null else sum / known
}

/**
 * 统计本曲已分析桶的幅度范围 [lo, hi]；整首都还是哨兵值时返回 null。
 * 供 [drawWaveformTrack] 做满量程归一化用。
 */
private fun waveformRangeOf(peaks: FloatArray): Pair<Float, Float>? {
    var lo = Float.MAX_VALUE
    var hi = -1f
    for (v in peaks) {
        if (v < 0f) continue
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    return if (hi < 0f) null else lo to hi
}
