package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.DailyRecommendUiState
import kotlin.math.roundToInt

/**
 * 网易云「每日推荐」高亮卡片（媒体库播放列表 tab 顶部）。
 * 仅登录网易云后展示；支持加载中 / 就绪 / 失败三种状态，点击播放每日推荐。
 */
@Composable
fun DailyRecommendCard(
    state: DailyRecommendUiState,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(R.string.daily_recommend_title)
    val subtitle = stringResource(R.string.daily_recommend_subtitle)
    val errorText = stringResource(R.string.daily_recommend_error)
    val retryText = stringResource(R.string.daily_recommend_retry)
    val playCd = stringResource(R.string.daily_recommend_play_cd)

    if (state is DailyRecommendUiState.Hidden) return

    val contentColor = Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .then(
                if (state is DailyRecommendUiState.Ready) {
                    Modifier.clickable(onClick = onPlay)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        when (state) {
            is DailyRecommendUiState.Loading -> {
                // 加载中的流动高光动画：一道柔光从左到右循环扫过卡片，
                // 与右侧圆形进度指示器组合，营造"加载中"的呼吸动效
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val barWidthPx = with(density) { 160.dp.toPx() }
                    val sweepDistancePx = with(density) { maxWidth.toPx() } + barWidthPx
                    val sweepTransition = rememberInfiniteTransition(label = "dailyRecommendSweep")
                    val sweepProgress by sweepTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "dailyRecommendSweepProgress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(160.dp)
                            .offset { IntOffset((sweepProgress * sweepDistancePx - barWidthPx).roundToInt(), 0) }
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        CircularProgressIndicator(
                            color = contentColor.copy(alpha = 0.9f),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            is DailyRecommendUiState.Error -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .clickable(onClick = onRetry)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = retryText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor.copy(alpha = 0.95f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = retryText,
                        tint = contentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            is DailyRecommendUiState.Ready -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor
                            )
                            Spacer(Modifier.width(6.dp))
                            // 网易云商标（与设置-账户管理中的网易云 logo 一致）
                            Icon(
                                painter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(contentColor.copy(alpha = 0.25f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.daily_recommend_songs_count,
                                        state.songs.size
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = playCd,
                            tint = contentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            DailyRecommendUiState.Hidden -> Unit
        }
    }
}
