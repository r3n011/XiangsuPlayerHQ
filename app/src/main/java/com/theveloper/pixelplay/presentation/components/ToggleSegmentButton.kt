package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    // ⚡ 玻璃按钮点击光效（按下水波纹高光）
    pressGlow: Boolean = false,
    glowColor: Color = Color.White,
    onClick: () -> Unit,
    iconId: Int,
    contentDesc: String,
    // ⚡ 背景填充进度（0~1）：>0 时在按钮背景上按比例从左到右填充（模仿 mini player 进度条），
    // 不是改变按钮尺寸，而是绘制填充色
    progressFill: Float = 0f,
    progressFillColor: Color = activeContentColor.copy(alpha = 0.35f)
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        pressGlow = pressGlow,
        glowColor = glowColor,
        onClick = onClick,
        progressFill = progressFill,
        progressFillColor = progressFillColor
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    // ⚡ 玻璃按钮点击光效（按下水波纹高光）
    pressGlow: Boolean = false,
    glowColor: Color = Color.White,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDesc: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        pressGlow = pressGlow,
        glowColor = glowColor,
        onClick = onClick
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.primary,
    activeCornerRadius: Dp = 8.dp,
    // ⚡ 玻璃按钮点击光效（按下水波纹高光）
    pressGlow: Boolean = false,
    glowColor: Color = Color.White,
    onClick: () -> Unit,
    text: String,
    onLongClick: (() -> Unit)? = null
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        pressGlow = pressGlow,
        glowColor = glowColor,
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = if (active) activeContentColor else inactiveContentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            softWrap = true
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    // ⚡ 玻璃按钮点击光效（按下水波纹高光）
    pressGlow: Boolean = false,
    glowColor: Color = Color.White,
    onClick: () -> Unit,
    text: String,
    imageVector: ImageVector
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        pressGlow = pressGlow,
        glowColor = glowColor,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (active) activeContentColor else inactiveContentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (active) activeContentColor else inactiveContentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}


@Composable
private fun ToggleSegmentButtonContainer(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeCornerRadius: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    progressFill: Float = 0f,
    progressFillColor: Color = Color.Transparent,
    // ⚡ 玻璃按钮点击光效：按下时按钮发出「水波纹」式高光（配合玻璃模糊质感）
    pressGlow: Boolean = false,
    glowColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    val targetBgColor = if (active) activeColor else inactiveColor
    val bgColor by animateColorAsState(
        targetValue = if (enabled) targetBgColor else targetBgColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 250),
        label = ""
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = ""
    )

    // ⚡ 按压光效：按下快速亮起、松开缓慢收掉（水波纹观感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowProgress by animateFloatAsState(
        targetValue = if (isPressed && pressGlow) 1f else 0f,
        animationSpec = if (isPressed && pressGlow) tween(durationMillis = 140) else tween(durationMillis = 420),
        label = "segmentPressGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            // ⚡ 背景填充进度：模仿 mini player 进度条，在按钮背景上按比例从左到右填充，
            // 不改变按钮尺寸（旧实现 fillMaxWidth(progress) 会直接拉伸进度区域，显示很怪）
            .drawWithCache {
                onDrawBehind {
                    if (progressFill > 0f) {
                        val fraction = progressFill.coerceIn(0f, 1f)
                        drawRect(
                            color = progressFillColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width * fraction, size.height)
                        )
                    }
                    // ⚡ 按压水波纹高光：径向高光从按钮中心扩散
                    if (glowProgress > 0f) {
                        val glowCenter = Offset(size.width / 2f, size.height / 2f)
                        val glowRadius = maxOf(size.width, size.height) * 0.78f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.45f * glowProgress),
                                    glowColor.copy(alpha = 0.18f * glowProgress),
                                    Color.Transparent
                                ),
                                center = glowCenter,
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = glowCenter
                        )
                    }
                }
            }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (pressGlow) null else LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.graphicsLayer(alpha = if (enabled) 1f else 0.38f)) {
            content()
        }
    }
}
