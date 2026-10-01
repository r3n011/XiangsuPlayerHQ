package com.theveloper.pixelplay.presentation.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 顶部下载提示 chip 的阶段。
 * [Downloading] 的 progressPercent 为 -1 表示总长度未知（显示不确定进度环）。
 */
sealed interface DownloadStatusPhase {
    data class Downloading(val progressPercent: Int) : DownloadStatusPhase
    data object Success : DownloadStatusPhase
    data class Failed(val message: String? = null) : DownloadStatusPhase
}

/**
 * 通用"顶部悬浮下载提示 chip"（模仿 Rhythm 的滑入样式）。
 *
 * 日语注音引擎（kuromoji）与歌词字体下载共用同一套视觉与交互：
 * 从顶部滑入 → 显示进度环 + 百分比 → 完成显示对勾驻留 2s / 失败显示警告驻留 3s
 * → 自动滑出；期间可上滑或左右滑手动关闭。
 *
 * @param phase null 表示没有需要提示的下载
 * @param label chip 顶部的小标题（如"日语注音引擎"）
 * @param progressTextRes 带 %1$d 的进度文案
 * @param indeterminateTextRes 总长度未知时的文案
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadStatusTopChip(
    phase: DownloadStatusPhase?,
    label: String,
    @StringRes progressTextRes: Int,
    @StringRes indeterminateTextRes: Int,
    successText: String,
    failedText: String,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    var exitTransition by remember {
        mutableStateOf(fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it }))
    }
    var manuallyDismissed by remember { mutableStateOf(false) }

    // 完成/失败为驻留态：显示一段时间后自动消失。按"阶段类别"重启（而非每次进度刷新都重启），
    // 否则用户下滑关闭后会被下一条进度更新重新唤起。
    val phaseKind = when (phase) {
        null -> "none"
        is DownloadStatusPhase.Downloading -> "downloading"
        DownloadStatusPhase.Success -> "success"
        is DownloadStatusPhase.Failed -> "failed"
    }
    var settledPhase by remember { mutableStateOf<DownloadStatusPhase?>(null) }
    LaunchedEffect(phaseKind) {
        manuallyDismissed = false
        settledPhase = null
        when (phase) {
            DownloadStatusPhase.Success -> {
                settledPhase = DownloadStatusPhase.Success
                delay(2000)
                settledPhase = null
            }
            is DownloadStatusPhase.Failed -> {
                settledPhase = phase
                delay(3000)
                settledPhase = null
            }
            else -> {}
        }
    }

    val isDownloading = phase is DownloadStatusPhase.Downloading
    val visible = !manuallyDismissed && (isDownloading || settledPhase != null)

    LaunchedEffect(visible) {
        if (visible) {
            swipeOffsetX.snapTo(0f)
            swipeOffsetY.snapTo(0f)
            exitTransition = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it })
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -it }),
        exit = exitTransition,
        modifier = modifier
    ) {
        val settled = settledPhase

        val chipStatusText = when {
            isDownloading -> {
                val progressPercent = (phase as DownloadStatusPhase.Downloading).progressPercent
                if (progressPercent >= 0) {
                    stringResource(progressTextRes, progressPercent)
                } else {
                    stringResource(indeterminateTextRes)
                }
            }
            settled is DownloadStatusPhase.Success -> successText
            else -> failedText
        }

        val swipeFraction = remember(swipeOffsetX.value, swipeOffsetY.value) {
            val maxDist = swipeThresholdPx * 1.5f
            val dist = maxOf(kotlin.math.abs(swipeOffsetX.value), kotlin.math.abs(swipeOffsetY.value))
            (dist / maxDist).coerceIn(0f, 1f)
        }
        val chipAlpha = (1f - swipeFraction).coerceIn(0f, 1f)
        val chipScale = (1f - swipeFraction * 0.1f).coerceIn(0.9f, 1f)

        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    translationX = swipeOffsetX.value
                    translationY = swipeOffsetY.value
                    alpha = chipAlpha
                    scaleX = chipScale
                    scaleY = chipScale
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val x = swipeOffsetX.value
                            val y = swipeOffsetY.value
                            if (y < -swipeThresholdPx) {
                                coroutineScope.launch {
                                    exitTransition = fadeOut(animationSpec = tween(200)) +
                                        slideOutVertically(targetOffsetY = { -it })
                                    swipeOffsetY.animateTo(-500f, tween(200))
                                    manuallyDismissed = true
                                }
                            } else if (kotlin.math.abs(x) > swipeThresholdPx) {
                                coroutineScope.launch {
                                    if (x > 0) {
                                        exitTransition = fadeOut(animationSpec = tween(200)) +
                                            slideOutHorizontally(targetOffsetX = { it })
                                    } else {
                                        exitTransition = fadeOut(animationSpec = tween(200)) +
                                            slideOutHorizontally(targetOffsetX = { -it })
                                    }
                                    val targetX = if (x > 0) 1000f else -1000f
                                    swipeOffsetX.animateTo(targetX, tween(200))
                                    manuallyDismissed = true
                                }
                            } else {
                                coroutineScope.launch {
                                    launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                            swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount.y).coerceAtMost(50f))
                        }
                    }
                },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDownloading -> {
                            val progressPercent = (phase as DownloadStatusPhase.Downloading).progressPercent
                            if (progressPercent >= 0) {
                                CircularWavyProgressIndicator(
                                    progress = { progressPercent / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            } else {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            }
                        }
                        settled is DownloadStatusPhase.Success -> Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = chipStatusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
