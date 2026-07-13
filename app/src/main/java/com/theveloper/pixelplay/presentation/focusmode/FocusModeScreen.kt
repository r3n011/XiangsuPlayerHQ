package com.theveloper.pixelplay.presentation.focusmode

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    timerState: FocusTimerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    // Material You: Use dynamic color scheme based on phase
    val containerColor by animateColorAsState(
        targetValue = when (timerState.currentPhase) {
            FocusPhase.STUDY -> MaterialTheme.colorScheme.primaryContainer
            FocusPhase.BREAK -> MaterialTheme.colorScheme.tertiaryContainer
            FocusPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(500),
        label = "container_color"
    )

    val indicatorColor by animateColorAsState(
        targetValue = when (timerState.currentPhase) {
            FocusPhase.STUDY -> MaterialTheme.colorScheme.primary
            FocusPhase.BREAK -> MaterialTheme.colorScheme.tertiary
            FocusPhase.IDLE -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "indicator_color"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = containerColor
    ) {
        if (isLandscape || isTablet) {
            FocusModeLandscapeContent(
                currentSong = currentSong,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                isPlaying = isPlaying,
                timerState = timerState,
                indicatorColor = indicatorColor,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onExit = onExit,
                onStopTimer = onStopTimer
            )
        } else {
            FocusModePortraitContent(
                currentSong = currentSong,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                isPlaying = isPlaying,
                timerState = timerState,
                indicatorColor = indicatorColor,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onExit = onExit,
                onStopTimer = onStopTimer
            )
        }
    }
}

@Composable
private fun FocusTopBar(
    indicatorColor: androidx.compose.ui.graphics.Color,
    completedCycles: Int,
    timerPhase: FocusPhase,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：退出按钮 - Material 3 FilledTonalIconButton
        FilledTonalIconButton(
            onClick = onExit,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "退出",
                modifier = Modifier.size(24.dp)
            )
        }

        // 中间：完成轮数 - Material 3 Suggestion Chip
        if (completedCycles > 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "已完成 $completedCycles 轮",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "专注模式",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 右侧：结束计时按钮 - Material 3 OutlinedButton
        if (timerPhase != FocusPhase.IDLE) {
            FilledTonalButton(
                onClick = onStopTimer,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "结束",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun FocusPhaseIndicator(
    timerState: FocusTimerState,
    indicatorColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = timerState.currentPhase to timerState.isRunning,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it / 3 })) togetherWith
                    (fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it / 3 }))
        },
        label = "phase_text",
        modifier = modifier
    ) { (phase, running) ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (phase) {
                    FocusPhase.STUDY -> if (running) "专注学习中" else "已暂停"
                    FocusPhase.BREAK -> if (running) "休息放松中" else "已暂停"
                    FocusPhase.IDLE -> "准备开始"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = indicatorColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Material 3 LinearProgressIndicator 替代自定义圆点
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when (phase) {
                    FocusPhase.STUDY -> MaterialTheme.colorScheme.primary
                    FocusPhase.BREAK -> MaterialTheme.colorScheme.tertiary
                    FocusPhase.IDLE -> MaterialTheme.colorScheme.outline
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun FocusModePortraitContent(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    timerState: FocusTimerState,
    indicatorColor: androidx.compose.ui.graphics.Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusTopBar(
            indicatorColor = indicatorColor,
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        Spacer(modifier = Modifier.height(24.dp))

        FocusPhaseIndicator(
            timerState = timerState,
            indicatorColor = indicatorColor,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Material 3 CircularProgressIndicator
        MaterialTimerCircle(
            timerState = timerState,
            indicatorColor = indicatorColor,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Material 3 播放控制
        MaterialFocusPlaybackControls(
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            indicatorColor = indicatorColor
        )

        Spacer(modifier = Modifier.weight(1f))

        // Material 3 Card 歌曲信息
        MaterialSongInfoCard(
            currentSong = currentSong,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Material 3 播放进度
        MaterialProgressBar(
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            indicatorColor = indicatorColor
        )
    }
}

@Composable
private fun FocusModeLandscapeContent(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    timerState: FocusTimerState,
    indicatorColor: androidx.compose.ui.graphics.Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        FocusTopBar(
            indicatorColor = indicatorColor,
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // 左侧：Material 3 时钟
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FocusPhaseIndicator(
                    timerState = timerState,
                    indicatorColor = indicatorColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                MaterialTimerCircle(
                    timerState = timerState,
                    indicatorColor = indicatorColor,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                MaterialFocusPlaybackControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    indicatorColor = indicatorColor
                )
            }

            // 右侧：歌曲信息 + 播放进度
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSongInfoCard(
                    currentSong = currentSong,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                MaterialProgressBar(
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    indicatorColor = indicatorColor,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }
    }
}

/**
 * Material 3 风格的大时钟 - 使用 CircularProgressIndicator
 */
@Composable
private fun MaterialTimerCircle(
    timerState: FocusTimerState,
    indicatorColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    timerFontSize: Int = 56
) {
    val progress by animateFloatAsState(
        targetValue = timerState.getProgress(),
        animationSpec = tween(durationMillis = 500),
        label = "progress_animation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Material 3 CircularProgressIndicator
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = indicatorColor,
            strokeWidth = 12.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        // 内部：倒计时时间 + 阶段信息
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState = timerState.formatTime(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "timer_text"
            ) { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = timerFontSize.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 阶段标签
            Text(
                text = when (timerState.currentPhase) {
                    FocusPhase.STUDY -> "${timerState.studyDurationMinutes}分钟专注"
                    FocusPhase.BREAK -> "${timerState.breakDurationMinutes}分钟休息"
                    FocusPhase.IDLE -> "点击开始"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Material 3 风格的播放控制按钮组
 */
@Composable
private fun MaterialFocusPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    indicatorColor: androidx.compose.ui.graphics.Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一曲 - Material 3 FilledTonalIconButton
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "上一曲",
                modifier = Modifier.size(28.dp)
            )
        }

        // 主播放/暂停按钮 - Material 3 FilledIconButton
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = indicatorColor,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp)
            )
        }

        // 下一曲 - Material 3 FilledTonalIconButton
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "下一曲",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Material 3 Card 风格的歌曲信息卡片
 */
@Composable
private fun MaterialSongInfoCard(
    currentSong: Song?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentSong?.title ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentSong?.displayArtist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Material 3 风格的播放进度条
 */
@Composable
private fun MaterialProgressBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    indicatorColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    if (totalDurationMs <= 0) return

    val progress = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Material 3 LinearProgressIndicator
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = indicatorColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
