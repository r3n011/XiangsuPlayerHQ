package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.ShapeCache
import kotlin.math.*

/**
 * Nothing Phone Glyph Matrix 圆形 LED 预览组件（单色）
 *
 * 所有 LED 均为白色，仅亮度不同。
 * Phone (3) 25×25 和 Phone (4a) Pro 13×13 合并在一个卡片中。
 */
@Composable
fun GlyphMatrixPreview(
    displayMode: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glyph_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val ledOnColor = Color(0xFFE8E8E8)   // 单色 LED 亮起色
    val ledOffColor = Color(0xFF121216)   // 未点亮

    Surface(
        shape = ShapeCache.smooth24,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 两颗矩阵并排
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 25×25 高密度
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMatrixCanvas(
                        mask = GLYPH_MASK_25,
                        gridSize = 25,
                        displayMode = displayMode,
                        animPhase = animPhase,
                        ledOnColor = ledOnColor,
                        ledOffColor = ledOffColor,
                        canvasSizeDp = 148
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "25×25",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 13×13 低密度
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMatrixCanvas(
                        mask = GLYPH_MASK_13,
                        gridSize = 13,
                        displayMode = displayMode,
                        animPhase = animPhase,
                        ledOnColor = ledOnColor,
                        ledOffColor = ledOffColor,
                        canvasSizeDp = 100
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "13×13",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GlyphMatrixCanvas(
    mask: BooleanArray,
    gridSize: Int,
    displayMode: String,
    animPhase: Float,
    ledOnColor: Color,
    ledOffColor: Color,
    canvasSizeDp: Int
) {
    Surface(
        modifier = Modifier.size(canvasSizeDp.dp),
        shape = ShapeCache.smooth16,
        color = Color(0xFF050508),
        shadowElevation = 1.dp
    ) {
        Canvas(modifier = Modifier.padding(5.dp)) {
            val canvasW = this.size.width
            val cellSize = canvasW / gridSize
            val gap = cellSize * 0.18f
            val ledSize = cellSize - gap
            val cornerR = ledSize * 0.12f

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val idx = row * gridSize + col
                    if (!mask[idx]) continue

                    val x = col * cellSize + gap / 2f
                    val y = row * cellSize + gap / 2f

                    val brightness = calculateBrightness(
                        mode = displayMode,
                        row = row,
                        col = col,
                        gridSize = gridSize,
                        animPhase = animPhase
                    )

                    if (brightness > 0.05f) {
                        // 辉光
                        drawRoundRect(
                            color = ledOnColor.copy(alpha = brightness * 0.1f),
                            topLeft = Offset(x - gap * 0.3f, y - gap * 0.3f),
                            size = Size(ledSize + gap * 0.6f, ledSize + gap * 0.6f),
                            cornerRadius = CornerRadius(cornerR * 1.5f)
                        )
                        // LED 本体
                        drawRoundRect(
                            color = ledOnColor.copy(alpha = brightness.coerceIn(0f, 1f)),
                            topLeft = Offset(x, y),
                            size = Size(ledSize, ledSize),
                            cornerRadius = CornerRadius(cornerR)
                        )
                    } else {
                        drawRoundRect(
                            color = ledOffColor,
                            topLeft = Offset(x, y),
                            size = Size(ledSize, ledSize),
                            cornerRadius = CornerRadius(cornerR)
                        )
                    }
                }
            }
        }
    }
}

// ─── 单色亮度计算 ──────────────────────────────────────────────

private fun calculateBrightness(
    mode: String,
    row: Int,
    col: Int,
    gridSize: Int,
    animPhase: Float
): Float {
    val half = gridSize / 2f
    val dx = col - half + 0.5f
    val dy = row - half + 0.5f
    val dist = sqrt(dx * dx + dy * dy)
    val normDist = (dist / half).coerceIn(0f, 1f)
    val cy = row - half.toInt()

    return when (mode) {
        "NOW_PLAYING" -> {
            val ringPhase = (animPhase * 0.5f) % (2f * PI.toFloat())
            val ringPulse = abs(sin(normDist * 3.5f - ringPhase))
            val brightness = (ringPulse * (1.0 - normDist * 0.4)).coerceIn(0.0, 1.0).toFloat()
            if (brightness > 0.08f) brightness else 0f
        }

        "VISUALIZER" -> {
            val barHeight = ((sin(animPhase + col * 0.45f) + 1f) / 2f * gridSize * 0.8f)
            val threshold = gridSize - barHeight.toInt()
            if (row >= threshold) {
                val t = ((gridSize - 1 - row).toFloat() / gridSize).coerceIn(0f, 1f)
                (0.3f + t * 0.7f).coerceIn(0f, 1f)
            } else 0f
        }

        "WAVEFORM" -> {
            val waveY = sin(animPhase + col * 0.3f) * (gridSize * 0.12f)
            val distFromWave = abs(cy.toFloat() - waveY)
            if (distFromWave < 2.5f) {
                (1f - distFromWave / 2.5f).coerceIn(0f, 1f)
            } else {
                val ambient = distFromWave - 2.5f
                if (ambient < 2f) (1f - ambient / 2f) * 0.04f else 0f
            }
        }

        else -> 0f
    }
}

// ─── 真实硬件 LED 圆形遮罩 ──────────────────────────────────

private val GLYPH_MASK_25: BooleanArray = buildMask(25, intArrayOf(
    7, 11, 15, 17, 19, 21, 21, 23, 23,
    25, 25, 25, 25, 25, 25, 25,
    23, 23, 21, 21, 19, 17, 15, 11, 7
))

private val GLYPH_MASK_13: BooleanArray = buildMask(13, intArrayOf(
    5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5
))

private fun buildMask(gridSize: Int, rowWidths: IntArray): BooleanArray {
    val mask = BooleanArray(gridSize * gridSize)
    for (row in 0 until gridSize) {
        val width = if (row < rowWidths.size) rowWidths[row] else 0
        val startCol = (gridSize - width) / 2
        for (col in startCol until startCol + width) {
            if (col in 0 until gridSize) {
                mask[row * gridSize + col] = true
            }
        }
    }
    return mask
}
