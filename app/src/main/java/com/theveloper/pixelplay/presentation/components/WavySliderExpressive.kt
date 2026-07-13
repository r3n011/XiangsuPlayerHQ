package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,

    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f, // Slower wave as requested

    waveAmplitudeWhenPlaying: Dp = 4.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val normalizedValueState = remember(valueRange) {
        derivedStateOf {
            val v = value()
            if (valueRange.endInclusive == valueRange.start) 0f
            else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValueState = remember(safeSemanticsStep) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember(valueRange) {
        derivedStateOf {
            valueRange.start + semanticNormalizedValueState.value * (valueRange.endInclusive - valueRange.start)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    // 用 rememberUpdatedState 追踪运行时变化的值，避免 pointerInput 频繁重启
    val currentEnabled by rememberUpdatedState(enabled)
    val currentValueRange by rememberUpdatedState(valueRange)
    val currentTrackEdgePaddingPx by rememberUpdatedState(trackEdgePaddingPx)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    // 安全网：如果 isPointerSeeking 卡在 true 超过3秒，强制重置
    LaunchedEffect(isPointerSeeking) {
        if (isPointerSeeking) {
            kotlinx.coroutines.delay(3000L)
            isPointerSeeking = false
        }
    }

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    // Keep visual progress interpolation out of composition:
    // update this state on frame clock, then consume it only inside draw lambdas.
    // This preserves smooth visuals while avoiding high-frequency recompositions.
    val renderedNormalizedProgress = remember {
        val initialVal = value()
        val initialNorm = if (valueRange.endInclusive == valueRange.start) 0f
            else ((initialVal - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        mutableFloatStateOf(initialNorm)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isInteracting, enabled) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            if (!enabled || isInteracting) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val start = renderedNormalizedProgress.floatValue
            // Snap on discontinuities (song change, big catch-up after a seek, resume after
            // backgrounding). Per-tick natural progress is well under 10% even for short
            // clips, so a bigger jump can't be normal playback — tweening it produces the
            // "slowly slides to 0" effect on track switch.
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val nowNanos = System.nanoTime()
            // Cap the perceived interval so a long pause (paused playback, sheet hidden,
            // backgrounded app) can't translate into a multi-second tween once progress
            // resumes with a tiny delta.
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collect
            }

            val durationNanos = (intervalMs * 700_000L).coerceAtLeast(1_000_000L)
                var startFrameNanos = 0L
                var lastRenderFrameNanos = 0L
                val renderIntervalNanos = 16_666_667L / 2
                while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    if (startFrameNanos == 0L) {
                        startFrameNanos = frameNanos
                        lastRenderFrameNanos = frameNanos
                    }
                    if (frameNanos - lastRenderFrameNanos < renderIntervalNanos) {
                        continue
                    }
                    lastRenderFrameNanos = frameNanos
                    val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                    val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                    renderedNormalizedProgress.floatValue = start + (target - start) * fraction
                    if (fraction >= 1f) break
                }
            renderedNormalizedProgress.floatValue = target
        }
    }

    val containerHeight = max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValueState.value,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit?.invoke(coerced)
                            ?: latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isVisible) {
            LinearWavyProgressIndicator(
                progress = { renderedNormalizedProgress.floatValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                    // Decorative layer: avoid duplicate semantics updates from the visual track.
                    .clearAndSetSemantics { },
                color = activeTrackColor,
                trackColor = inactiveTrackColor,
                stroke = stroke,
                trackStroke = stroke,
                gapSize = 2f * dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
                stopSize = 3.dp,
                amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
                wavelength = wavelength,
                waveSpeed = waveSpeed
            )
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(containerHeight))
        }


        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isVisible) return@Canvas
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2
            val renderedProgress = renderedNormalizedProgress.floatValue

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)
            
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 使用 Unit 作为 key：避免 enabled/valueRange/trackEdgePaddingPx 变化
                // 导致 pointerInput 协程重启；
                // 外层 while(true) + awaitEachGesture 保证每一轮手势处理后自动重启监听，
                // 单个 enabled=false 或异常都不会让整个手势系统"永久死掉"。
                // 组件卸载时 Compose 会取消 pointerInput 协程，内部挂起函数会抛出
                // CancellationException，我们在 catch 中通过 ensureActive 重新让它传播。
                .pointerInput(Unit) {
                    while (true) {
                        try {
                            awaitEachGesture {
                                fun valueForX(rawX: Float): Float {
                                    val edgePadding = currentTrackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                                    val trackStart = edgePadding
                                    val trackEnd = size.width - edgePadding
                                    val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                                    val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                                    return currentValueRange.start +
                                        normalized * (currentValueRange.endInclusive - currentValueRange.start)
                                }

                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 运行时检查 enabled：若当前不可交互则静默跳过当前手势，
                                // 外层 while 继续监听下一次。
                                if (!currentEnabled) {
                                    return@awaitEachGesture
                                }
                                isPointerSeeking = true
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

                                latestOnValueCommit?.invoke(latestGestureValue)
                                    ?: latestOnValueChangeFinished?.invoke()
                            }
                        } catch (ex: Throwable) {
                            // 异常可能是"组件卸载导致取消"，也可能是单次手势处理失败。
                            // 通过检查 Job.isActive 区分：若协程已被取消则退出循环；
                            // 否则忽略异常，继续监听下一次手势。
                            val active = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive ?: true
                            if (!active) throw ex
                            // 重置状态后继续
                        } finally {
                            isPointerSeeking = false
                        }
                    }
                }
        )
    }
}
