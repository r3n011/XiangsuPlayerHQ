package com.theveloper.pixelplay.presentation.components.hearingguard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState

/**
 * 状态颜色：跟随动态取色（Material ColorScheme），使盾牌与全局配色协调
 * - < 75%: primary（健康）
 * - 75% ~ 90%: tertiary（提醒）
 * - >= 90%: error（告警）
 */
internal fun statusColor(colors: ColorScheme, progress: Float): Color = when {
    progress >= 0.9f -> colors.error
    progress >= 0.75f -> colors.tertiary
    else -> colors.primary
}

/**
 * 听力保护按钮（首页顶栏）
 *
 * 与顶栏其它按钮（FilledIconButton：40dp 圆形 + surfaceContainerHigh 底）样式一致，
 * 纯图标（无文字）；听力进度以背景填充呈现（保留状态色）。
 */
@Composable
fun HearingGuardCapsule(
    state: HearingGuardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface

    val progress = state.dailyProgress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "capsule_progress"
    )
    val shieldColor by animateColorAsState(
        targetValue = statusColor(MaterialTheme.colorScheme, progress),
        animationSpec = tween(400),
        label = "capsule_fill_color"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(surfaceHigh)
            .alpha(if (state.isConfigured && !state.enabled) 0.5f else 1f)
            .clickable(onClick = onClick)
            .drawBehind {
                // 背景填充进度：端部半径 = 高度一半，贴合 CircleShape 轮廓
                if (state.isConfigured && animatedProgress > 0f) {
                    val w = size.width
                    val h = size.height
                    val pw = w * animatedProgress
                    drawRoundRect(
                        color = shieldColor.copy(alpha = 0.22f),
                        topLeft = Offset.Zero,
                        size = Size(pw, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}