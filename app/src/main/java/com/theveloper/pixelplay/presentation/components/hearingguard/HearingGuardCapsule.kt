package com.theveloper.pixelplay.presentation.components.hearingguard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState

/**
 * 听力保护胶囊组件
 *
 * 未配置时显示 "像素卫士"
 * 已配置时显示盾牌图标 + 听音进度条 + 剩余分钟
 *
 * 盾牌颜色：
 * - < 75%: 绿色 + 对勾
 * - 75% ~ 90%: 黄色 + 感叹号
 * - >= 90%: 红色 + ×
 */
@Composable
fun HearingGuardCapsule(
    state: HearingGuardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(surfaceHigh)
            .alpha(if (state.isConfigured && !state.enabled) 0.5f else 1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!state.isConfigured) {
            // 未配置：显示 "像素卫士"
            UnconfiguredContent(onSurface = onSurface)
        } else {
            // 已配置：盾牌 + 进度条 + 剩余时间
            ConfiguredContent(state = state, onSurface = onSurface, onSurfaceVariant = onSurfaceVariant)
        }
    }
}

@Composable
private fun UnconfiguredContent(onSurface: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ShieldIcon(
            progress = 0f,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "像素卫士",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = onSurface,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ConfiguredContent(
    state: HearingGuardState,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    val progress = state.dailyProgress.coerceIn(0f, 1f)
    val remaining = state.remainingMinutes

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 盾牌图标
        ShieldIcon(
            progress = progress,
            modifier = Modifier.size(18.dp)
        )

        // 进度条（模仿 now playing 进度条样式）
        ListeningProgressBar(
            progress = progress,
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
        )

        // 剩余时间
        Text(
            text = "${remaining}min",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * 盾牌图标 + 状态指示
 *
 * - < 75%: 绿色盾牌 + 对勾
 * - 75% ~ 90%: 黄色盾牌 + 感叹号
 * - >= 90%: 红色盾牌 + ×
 */
@Composable
private fun ShieldIcon(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val shieldColor by animateColorAsState(
        targetValue = when {
            progress >= 0.9f -> Color(0xFFEF4444)   // 红色
            progress >= 0.75f -> Color(0xFFFBBF24)  // 黄色
            else -> Color(0xFF22C55E)               // 绿色
        },
        animationSpec = tween(400),
        label = "shield_color"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            progress >= 0.9f -> Color.White
            progress >= 0.75f -> Color(0xFF422006)
            else -> Color.White
        },
        animationSpec = tween(400),
        label = "icon_tint"
    )

    val icon: ImageVector = when {
        progress >= 0.9f -> Icons.Rounded.Close
        progress >= 0.75f -> Icons.Rounded.Warning
        else -> Icons.Rounded.Check
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 盾牌形状
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                // 盾牌轮廓
                moveTo(w * 0.5f, 0f)
                lineTo(w, h * 0.15f)
                lineTo(w, h * 0.55f)
                cubicTo(w, h * 0.75f, w * 0.75f, h * 0.9f, w * 0.5f, h)
                cubicTo(w * 0.25f, h * 0.9f, 0f, h * 0.75f, 0f, h * 0.55f)
                lineTo(0f, h * 0.15f)
                close()
            }
            drawPath(path, color = shieldColor)
        }

        // 中心图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(10.dp)
        )
    }
}

/**
 * 听音进度条（模仿 now playing 进度条样式）
 */
@Composable
private fun ListeningProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress_anim"
    )

    val trackColor = Color.White.copy(alpha = 0.15f)
    val progressColor by animateColorAsState(
        targetValue = when {
            progress >= 0.9f -> Color(0xFFEF4444)
            progress >= 0.75f -> Color(0xFFFBBF24)
            else -> Color(0xFF22C55E)
        },
        animationSpec = tween(400),
        label = "progress_color"
    )

    Canvas(modifier = modifier) {
        val barHeight = size.height
        val barWidth = size.width
        val cornerRadius = barHeight / 2f

        // 背景轨道
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(cornerRadius),
            size = Size(barWidth, barHeight)
        )

        // 进度条
        val progressWidth = (barWidth * animatedProgress).coerceIn(0f, barWidth)
        if (progressWidth > 0f) {
            drawRoundRect(
                color = progressColor,
                cornerRadius = CornerRadius(cornerRadius),
                size = Size(progressWidth, barHeight)
            )
        }
    }
}
