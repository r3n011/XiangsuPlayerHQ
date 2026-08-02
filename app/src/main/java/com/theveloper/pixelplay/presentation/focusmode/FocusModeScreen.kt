package com.theveloper.pixelplay.presentation.focusmode

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Brush
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
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

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

    // 与播放器一致的动态主题（封面取色）
    val colors = LocalMaterialTheme.current

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

    val phaseContainer by animateColorAsState(
        targetValue = when (timerState.currentPhase) {
            FocusPhase.STUDY -> colors.primaryContainer
            FocusPhase.BREAK -> colors.tertiaryContainer
            FocusPhase.IDLE -> colors.surfaceContainerHighest
        },
        animationSpec = tween(600),
        label = "phase_container"
    )

    // 柔和渐变背景（顶部阶段色 → 底部 surface），与全屏播放器一致
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            phaseContainer.copy(alpha = 0.55f),
            colors.surfaceContainer.copy(alpha = 0.85f),
            colors.surface
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // 中央阶段色光晕
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(460.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(indicatorColor.copy(alpha = 0.10f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

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
    indicatorColor: Color,
    completedCycles: Int,
    timerPhase: FocusPhase,
    onExit: () -> Unit,
    onStopTimer: () -> Unit
) {
    val colors = LocalMaterialTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：退出按钮 - 与播放器一致的胶囊圆角
        FilledTonalIconButton(
            onClick = onExit,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer.copy(alpha = 0.9f),
                contentColor = colors.onSecondaryContainer
            ),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.content_description_exit),
                modifier = Modifier.size(24.dp)
            )
        }

        // 中间：完成轮数 / 标题 - 胶囊徽章
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 50.dp, smoothnessAsPercentTR = 60,
                cornerRadiusBR = 50.dp, smoothnessAsPercentTL = 60,
                cornerRadiusBL = 50.dp, smoothnessAsPercentBR = 60,
                cornerRadiusTR = 50.dp, smoothnessAsPercentBL = 60
            ),
            color = if (completedCycles > 0) {
                colors.secondaryContainer.copy(alpha = 0.9f)
            } else {
                colors.surfaceContainer.copy(alpha = 0.9f)
            }
        ) {
            Text(
                text = if (completedCycles > 0) {
                    stringResource(R.string.focus_mode_completed_cycles_format, completedCycles)
                } else {
                    stringResource(R.string.focus_mode_title)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (completedCycles > 0) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 右侧：结束计时按钮 - 胶囊按钮
        if (timerPhase != FocusPhase.IDLE) {
            FilledTonalButton(
                onClick = onStopTimer,
                modifier = Modifier.height(40.dp),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 50.dp, smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 50.dp, smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 50.dp, smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 50.dp, smoothnessAsPercentBL = 60
                ),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.surfaceContainer.copy(alpha = 0.9f),
                    contentColor = colors.onSurface
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

@Composable
private fun FocusPhaseIndicator(
    timerState: FocusTimerState,
    indicatorColor: Color,
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
        val phaseTextResId = when (phase) {
            FocusPhase.STUDY -> if (running) R.string.focus_mode_studying else R.string.focus_mode_paused
            FocusPhase.BREAK -> if (running) R.string.focus_mode_break else R.string.focus_mode_paused
            FocusPhase.IDLE -> R.string.focus_mode_ready
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(phaseTextResId),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = indicatorColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .width(64.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (phase) {
                    FocusPhase.STUDY -> LocalMaterialTheme.current.primary
                    FocusPhase.BREAK -> LocalMaterialTheme.current.tertiary
                    FocusPhase.IDLE -> LocalMaterialTheme.current.outline.copy(alpha = 0.5f)
                },
                trackColor = LocalMaterialTheme.current.surfaceContainerHighest,
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
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusTopBar(
            indicatorColor = indicatorColor,
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        Spacer(modifier = Modifier.height(20.dp))

        FocusPhaseIndicator(
            timerState = timerState,
            indicatorColor = indicatorColor,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 玻璃质感时钟
        MaterialTimerCircle(
            timerState = timerState,
            indicatorColor = indicatorColor,
            modifier = Modifier
                .fillMaxWidth()
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

        Spacer(modifier = Modifier.weight(1f))

        MaterialSongInfoCard(
            currentSong = currentSong,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

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
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        FocusTopBar(
            indicatorColor = indicatorColor,
            completedCycles = timerState.completedCycles,
            timerPhase = timerState.currentPhase,
            onExit = onExit,
            onStopTimer = onStopTimer
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // 左侧：时钟
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

                Spacer(modifier = Modifier.height(16.dp))

                MaterialTimerCircle(
                    timerState = timerState,
                    indicatorColor = indicatorColor,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(20.dp))

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
 * 玻璃质感大时钟：圆角进度环 + 毛玻璃圆盘 + GoogleSansRounded 数字
 */
@Composable
private fun MaterialTimerCircle(
    timerState: FocusTimerState,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    timerFontSize: Int = 64
) {
    val colors = LocalMaterialTheme.current
    val progress by animateFloatAsState(
        targetValue = timerState.getProgress(),
        animationSpec = tween(durationMillis = 500),
        label = "progress_animation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 毛玻璃圆盘
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceContainerLowest.copy(alpha = 0.40f), CircleShape)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.30f), CircleShape)
        )

        // 圆角进度环
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = indicatorColor,
            trackColor = colors.surfaceContainerHighest.copy(alpha = 0.55f),
            strokeWidth = 10.dp,
            strokeCap = StrokeCap.Round,
        )

        // 内部：倒计时 + 阶段徽章
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    fontFamily = GoogleSansRounded,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 50.dp, smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 50.dp, smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 50.dp, smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 50.dp, smoothnessAsPercentBL = 60
                ),
                color = indicatorColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = when (timerState.currentPhase) {
                        FocusPhase.STUDY -> stringResource(R.string.focus_mode_study_duration_format, timerState.studyDurationMinutes)
                        FocusPhase.BREAK -> stringResource(R.string.focus_mode_break_duration_format, timerState.breakDurationMinutes)
                        FocusPhase.IDLE -> stringResource(R.string.focus_mode_tap_to_start)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = indicatorColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 胶囊圆角播放控制按钮组（与播放器风格一致）
 */
@Composable
private fun MaterialFocusPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    indicatorColor: Color
) {
    val colors = LocalMaterialTheme.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一曲 - 胶囊圆角
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(56.dp),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 26.dp, smoothnessAsPercentTR = 60,
                cornerRadiusBR = 26.dp, smoothnessAsPercentTL = 60,
                cornerRadiusBL = 26.dp, smoothnessAsPercentBR = 60,
                cornerRadiusTR = 26.dp, smoothnessAsPercentBL = 60
            ),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer.copy(alpha = 0.9f),
                contentColor = colors.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.content_description_previous),
                modifier = Modifier.size(28.dp)
            )
        }

        // 主播放/暂停按钮
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 60.dp, smoothnessAsPercentTR = 60,
                cornerRadiusBR = 60.dp, smoothnessAsPercentTL = 60,
                cornerRadiusBL = 60.dp, smoothnessAsPercentBR = 60,
                cornerRadiusTR = 60.dp, smoothnessAsPercentBL = 60
            ),
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

        // 下一曲 - 胶囊圆角
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(56.dp),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 26.dp, smoothnessAsPercentTR = 60,
                cornerRadiusBR = 26.dp, smoothnessAsPercentTL = 60,
                cornerRadiusBL = 26.dp, smoothnessAsPercentBR = 60,
                cornerRadiusTR = 26.dp, smoothnessAsPercentBL = 60
            ),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.secondaryContainer.copy(alpha = 0.9f),
                contentColor = colors.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.content_description_next),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * 玻璃质感歌曲信息卡片：封面缩略图 + 歌名/歌手
 */
@Composable
private fun MaterialSongInfoCard(
    currentSong: Song?,
    modifier: Modifier = Modifier
) {
    val colors = LocalMaterialTheme.current

    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp, smoothnessAsPercentTR = 60,
            cornerRadiusBR = 26.dp, smoothnessAsPercentTL = 60,
            cornerRadiusBL = 26.dp, smoothnessAsPercentBR = 60,
            cornerRadiusTR = 26.dp, smoothnessAsPercentBL = 60
        ),
        color = colors.surfaceContainer.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.25f)
        )
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(colors.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.primary,
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
                    fontFamily = GoogleSansRounded,
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
 * 圆角播放进度条
 */
@Composable
private fun MaterialProgressBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    if (totalDurationMs <= 0) return

    val colors = LocalMaterialTheme.current
    val progress = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = indicatorColor,
            trackColor = colors.surfaceContainerHighest.copy(alpha = 0.6f),
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
