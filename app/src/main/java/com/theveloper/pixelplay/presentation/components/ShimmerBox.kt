package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    // Use MaterialTheme colors for proper dark/light mode support
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, delayMillis = 200),
        ),
        label = "shimmerTranslate"
    )

    // ⚡ 在绘制阶段读取动画值：避免每帧重组本组件，也避免每帧在组合期新建 Brush。
    Box(
        modifier = modifier.drawBehind {
            val translate = translateAnim.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset.Zero,
                    end = Offset(x = translate, y = translate)
                )
            )
        }
    )
}