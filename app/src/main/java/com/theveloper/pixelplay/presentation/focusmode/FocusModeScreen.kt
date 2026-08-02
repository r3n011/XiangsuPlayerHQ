package com.theveloper.pixelplay.presentation.focusmode

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 专注模式（学习钟）— Material 3 设计。
 *
 * 遵循 Material Design 规范：
 * - 纯色 surface 背景，无渐变/毛玻璃/玻璃卡片；
 * - 标准 Material3 组件（IconButton / Button / Surface / LinearProgressIndicator）；
 * - 计时进度条展示「已流过的时间」：圆角线头 + 末端小蝌蚪圆点（stop indicator）；
 * - 颜色全部来自 MaterialTheme.colorScheme（学习=primary，休息=tertiary）。
 */
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
    val colors = MaterialTheme.colorScheme

    // 阶段主色：学习=primary，休息=tertiary，待机=primary
    val indicatorColor by animateColorAsState(
        targetValue = when (timerState.currentPhase) {
            FocusPhase.STUDY -> colors.primary
            FocusPhase.BREAK -> colors.tertiary
            FocusPhase.IDLE -> colors.primary
        },
        animationSpec = tween(500),
        label = "indicator_color"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.surface
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
private fun FocusModePortraitContent(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    timerState: FocusTimerState,
    indicatorColor: Color,
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
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusTopBar(
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        // 计时区域垂直居中
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FocusTimerDisplay(
                timerState = timerState,
                indicatorColor = indicatorColor,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FocusPlaybackControls(
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            indicatorColor = indicatorColor
        )

        Spacer(modifier = Modifier.height(20.dp))

        FocusSongInfoCard(
            currentSong = currentSong,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        FocusPlaybackProgressBar(
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
    indicatorColor: Color,
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
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        FocusTopBar(
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // 左侧：计时器
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FocusTimerDisplay(
                    timerState = timerState,
                    indicatorColor = indicatorColor,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                FocusPlaybackControls(
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
                FocusSongInfoCard(
                    currentSong = currentSong,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                FocusPlaybackProgressBar(
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
 * 顶部栏：标准 Material 组件（退出 IconButton + 居中标题 + 结束 Button）。
 */
@Composable
private fun FocusTopBar(
    completedCycles: Int,
    timerPhase: FocusPhase,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 退出
        IconButton(
            onClick = onExit,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.content_description_exit),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        // 标题 / 完成轮数
        Text(
            text = if (completedCycles > 0) {
                stringResource(R.string.focus_mode_completed_cycles_format, completedCycles)
            } else {
                stringResource(R.string.focus_mode_title)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )

        // 结束计时
        if (timerPhase != FocusPhase.IDLE) {
            Button(
                onClick = onStopTimer,
                modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.focus_mode_end),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * 计时核心区：阶段文字 + 倒计时大数字 + 时长说明 + 蝌蚪进度条。
 * 进度条展示「已流过的时间」（progress = getProgress()），
 * 圆角线头 + 末端小蝌蚪圆点（stop indicator）符合 Material 设计。
 */
@Composable
private fun FocusTimerDisplay(
    timerState: FocusTimerState,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    val durationCaption = when (timerState.currentPhase) {
        FocusPhase.STUDY -> stringResource(
            R.string.focus_mode_study_duration_format, timerState.studyDurationMinutes
        )
        FocusPhase.BREAK -> stringResource(
            R.string.focus_mode_break_duration_format, timerState.breakDurationMinutes
        )
        FocusPhase.IDLE -> stringResource(R.string.focus_mode_tap_to_start)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 阶段文字
        AnimatedContent(
            targetState = timerState.currentPhase to timerState.isRunning,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it / 3 })) togetherWith
                    (fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it / 3 }))
            },
            label = "phase_text"
        ) { (phase, running) ->
            val phaseTextResId = when (phase) {
                FocusPhase.STUDY -> if (running) R.string.focus_mode_studying else R.string.focus_mode_paused
                FocusPhase.BREAK -> if (running) R.string.focus_mode_break else R.string.focus_mode_paused
                FocusPhase.IDLE -> R.string.focus_mode_ready
            }
            Text(
                text = stringResource(phaseTextResId),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = indicatorColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 倒计时大数字
        AnimatedContent(
            targetState = timerState.formatTime(),
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "timer_text"
        ) { time ->
            Text(
                text = time,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold
                ),
                fontFamily = GoogleSansRounded,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 时长说明
        Text(
            text = durationCaption,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 标准 Material 圆形控制按钮组：上一首 / 播放暂停 / 下一首。
 */
@Composable
private fun FocusPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    indicatorColor: Color
) {
    val colors = MaterialTheme.colorScheme

    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一首
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.content_description_previous),
                modifier = Modifier.size(24.dp)
            )
        }

        // 主播放/暂停
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = indicatorColor,
                contentColor = colors.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.content_description_play_pause),
                modifier = Modifier.size(36.dp)
            )
        }

        // 下一首
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.content_description_next),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 歌曲信息卡片：标准 Material Surface（surfaceContainer 纯色，无玻璃/半透明）。
 */
@Composable
private fun FocusSongInfoCard(
    currentSong: Song?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 封面缩略图
            val albumArt = currentSong?.albumArtUriString
            if (!albumArt.isNullOrBlank()) {
                SmartImage(
                    model = albumArt,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, currentSong.title),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentSong?.title ?: stringResource(R.string.focus_mode_not_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentSong?.displayArtist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/**
 * 歌曲播放进度条：标准 Material LinearProgressIndicator（圆角线头）。
 */
@Composable
private fun FocusPlaybackProgressBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    if (totalDurationMs <= 0) return

    val colors = MaterialTheme.colorScheme
    val progress = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = indicatorColor,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                text = formatDuration(totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
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
