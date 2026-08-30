package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Apple Music 风格歌词背景：将封面裁成多块碎片，各自以随机方向/速度旋转 + 随机漂移，
 * 碎片之间重叠不留底色，最后在整体上方叠加一层较重的模糊，形成柔和的环境光氛围。
 */
@Composable
fun AppleMusicRotatingBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    blurRadius: androidx.compose.ui.unit.Dp = 64.dp
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val bitmap by produceState<ImageBitmap?>(null, albumArtUri) {
        value = albumArtUri?.let { uri ->
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .size(Size(512, 512))
                    .build()
                (imageLoader.execute(request) as? SuccessResult)
                    ?.drawable
                    ?.toBitmap()
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }

    // 3x3 = 9 块碎片，切歌时重新生成随机参数
    val cols = 3
    val rows = 3
    val blockCount = cols * rows
    val blocks = remember(albumArtUri) {
        List(blockCount) { index ->
            val col = index % cols
            val row = index / cols
            // 网格中心点略微内收让碎片之间重叠
            val overlap = 0.06f
            val stepX = 1f / cols
            val stepY = 1f / rows
            val cx = stepX * (col + 0.5f) + (Random.nextFloat() - 0.5f) * overlap
            val cy = stepY * (row + 0.5f) + (Random.nextFloat() - 0.5f) * overlap

            RotatingBlock(
                centerFractionX = cx.coerceIn(0f, 1f),
                centerFractionY = cy.coerceIn(0f, 1f),
                srcCol = col,
                srcRow = row,
                initialAngle = Random.nextFloat() * 360f,
                // 随机旋转方向：正转或反转
                direction = if (Random.nextBoolean()) 1f else -1f,
                // 旋转速度差异更大：15~80 秒一圈
                durationMs = Random.nextInt(15_000, 80_000),
                // 旋转圆心随机偏移更大
                pivotOffsetX = (Random.nextFloat() - 0.5f) * 0.8f,
                pivotOffsetY = (Random.nextFloat() - 0.5f) * 0.8f,
                // 漂移参数：每个碎片有独立的漂移轨迹
                driftAmplitudeX = Random.nextFloat() * 0.08f + 0.02f,
                driftAmplitudeY = Random.nextFloat() * 0.06f + 0.02f,
                driftSpeedX = Random.nextFloat() * 0.7f + 0.3f,
                driftSpeedY = Random.nextFloat() * 0.5f + 0.3f,
                driftPhaseX = Random.nextFloat() * 360f,
                driftPhaseY = Random.nextFloat() * 360f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "appleMusicBg")

    // 旋转动画
    val rotations = blocks.map { block ->
        transition.animateFloat(
            initialValue = block.initialAngle,
            targetValue = block.initialAngle + 360f * block.direction,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = block.durationMs, easing = LinearEasing)
            ),
            label = "blockRotation"
        )
    }

    // 漂移动画（用时间驱动的正弦波）
    val driftTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 60_000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "driftTime"
    )

    Box(modifier) {
        val bmp = bitmap
        if (bmp != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
                    .graphicsLayer {
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix().apply {
                                setToSaturation(0.6f)
                                timesAssign(
                                    ColorMatrix().apply { setToScale(0.9f, 0.9f, 0.9f, 1f) }
                                )
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                // 碎片边长取屏幕较大边的 70%（9块时可以小一些，但要确保覆盖）
                val blockSize = max(w, h) * 0.75f
                val srcW = bmp.width / cols
                val srcH = bmp.height / rows

                for (i in 0 until blockCount) {
                    val block = blocks[i]
                    val rotation = rotations[i].value

                    // 漂移偏移：正弦波驱动，每个碎片独立的相位和频率
                    val time = driftTime * 360f
                    val driftX = sin(Math.toRadians((time * block.driftSpeedX + block.driftPhaseX).toDouble())).toFloat() * block.driftAmplitudeX * w
                    val driftY = sin(Math.toRadians((time * block.driftSpeedY + block.driftPhaseY).toDouble())).toFloat() * block.driftAmplitudeY * h

                    val center = Offset(
                        w * block.centerFractionX + driftX,
                        h * block.centerFractionY + driftY
                    )
                    val pivot = Offset(
                        center.x + block.pivotOffsetX * blockSize,
                        center.y + block.pivotOffsetY * blockSize
                    )

                    rotate(degrees = rotation, pivot = pivot) {
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset(block.srcCol * srcW, block.srcRow * srcH),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset(
                                (center.x - blockSize / 2f).toInt(),
                                (center.y - blockSize / 2f).toInt()
                            ),
                            dstSize = IntSize(blockSize.toInt(), blockSize.toInt())
                        )
                    }
                }
            }
        }
    }
}

private data class RotatingBlock(
    val centerFractionX: Float,
    val centerFractionY: Float,
    val srcCol: Int,
    val srcRow: Int,
    val initialAngle: Float,
    val direction: Float,
    val durationMs: Int,
    val pivotOffsetX: Float,
    val pivotOffsetY: Float,
    val driftAmplitudeX: Float,
    val driftAmplitudeY: Float,
    val driftSpeedX: Float,
    val driftSpeedY: Float,
    val driftPhaseX: Float,
    val driftPhaseY: Float
)
