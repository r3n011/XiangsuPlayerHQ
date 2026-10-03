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
import androidx.compose.foundation.clickable
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
import com.theveloper.pixelplay.R
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
 * @param statusTextOverride 直接指定状态文案（多任务聚合等无法用单个 %1$d 表达时用）
 * @param onClick 点击 chip 的回调（如跳转到下载队列）；null 表示不可点
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
    modifier: Modifier = Modifier,
    statusTextOverride: String? = null,
    onClick: (() -> Unit)? = null,
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

        val chipStatusText = statusTextOverride ?: when {
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
                // ⚡ 可点：点击 chip 跳转到下载队列（拖拽关闭仍由下方 pointerInput 处理，
                //    detectDragGestures 只在超过滑动阈值后才 consume，普通点击不会被吞）
                .then(
                    if (onClick != null) Modifier.clickable { onClick?.invoke() } else Modifier
                )
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

/**
 * 「下载队列」常驻进度 chip：批量下载期间常驻在顶部，显示已下载数量，点击进入下载管理页。
 *
 * 复用 [DownloadStatusTopChip] 的视觉与交互（滑入 / 可滑动关闭 / 完成驻留后自动消失）：
 * - 有任务在跑或暂停 → [DownloadStatusPhase.Downloading]，**常驻**显示「已下载 N / M 首」；
 * - 全部完成 → [DownloadStatusPhase.Success]，驻留 2s 后自动消失；
 * - 有失败且无进行中任务 → [DownloadStatusPhase.Failed]。
 */
@Composable
fun DownloadQueueTopChip(
    downloads: List<com.theveloper.pixelplay.data.service.http.MusicDownloadService.DownloadInfo>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (downloads.isEmpty()) return

    val total = downloads.size
    val completed = downloads.count { it.isComplete }
    val failed = downloads.count { it.isFailed }
    val inFlight = downloads.count { it.isActive || it.isPaused }

    val phase: DownloadStatusPhase? = when {
        inFlight > 0 -> {
            // 总体进度：已完成算满，进行中的按其百分比折算
            val inFlightProgress = downloads
                .filter { it.isActive || it.isPaused }
                .sumOf { it.progress.toDouble().coerceIn(0.0, 100.0) }
            val percent = (((completed * 100.0) + inFlightProgress) / total).toInt().coerceIn(0, 100)
            DownloadStatusPhase.Downloading(percent)
        }
        completed > 0 && failed == 0 -> DownloadStatusPhase.Success
        failed > 0 -> DownloadStatusPhase.Failed()
        else -> null
    }
    if (phase == null) return

    val label = stringResource(R.string.download_queue_chip_label)
    val statusText = when (phase) {
        is DownloadStatusPhase.Downloading -> stringResource(R.string.download_queue_chip_progress, completed, total)
        DownloadStatusPhase.Success -> stringResource(R.string.download_queue_chip_done, completed)
        else -> stringResource(R.string.download_queue_chip_failed)
    }

    DownloadStatusTopChip(
        phase = phase,
        label = label,
        progressTextRes = R.string.download_queue_chip_progress,
        indeterminateTextRes = R.string.download_queue_chip_progress,
        successText = stringResource(R.string.download_queue_chip_done, completed),
        failedText = stringResource(R.string.download_queue_chip_failed),
        modifier = modifier,
        statusTextOverride = statusText,
        onClick = onClick,
    )
}
