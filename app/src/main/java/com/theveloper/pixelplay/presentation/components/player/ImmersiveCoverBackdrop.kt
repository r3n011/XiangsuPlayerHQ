/*
 * 沉浸式封面背景（播放器主题 =「沉浸式封面」）。
 *
 * 参考播放器的 ambient 效果分层（自底向上）：
 *  ① 主题色底：primaryContainer 铺满 —— 下半部分为纯主题色（与播放器取色体系一致）；
 *  ② 模糊延伸层：同一张封面一次性预渲染 StackBlur（256px、每首歌仅一次，运行期零 GPU 模糊）；
 *  ③ 融合遮罩：主题色自上而下渐显 —— 上区让模糊显形、中段模糊过渡、0.72 以下纯主题色；
 *  ④ 清晰封面层：铺满上半部分（边缘到边缘），底部以 DstIn 渐隐融入模糊层 ——
 *     与 Rhythm expressive + ambient 同款手法，清晰区与模糊区之间无硬分割线。
 *
 * [contentFraction] 由播放/暂停动画驱动：1 = 完整展开（清晰层铺满上半屏），
 * 0 = 清晰层完全收起；配合调用方的整体 alpha 淡出，实现「暂停收回普通封面、
 * 播放再次展开」的连续动画。切歌时经 AnimatedContent 交叉淡入淡出。
 */
package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.presentation.components.blur.BlurryBackdrop

@Composable
fun ImmersiveCoverBackdrop(
    albumArtUri: String?,
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
    /** 清晰封面层的展开比例（1 = 铺满上半屏；0 = 收起；由播放/暂停动画驱动） */
    contentFraction: Float = 1f
) {
    Box(modifier = modifier) {
        // ① 主题色底
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.primaryContainer)
        )

        if (albumArtUri.isNullOrBlank()) {
            // 无封面兜底：主题色 → 压暗主题色的纵向渐变
            val dark = Color(
                scheme.primaryContainer.red * 0.35f,
                scheme.primaryContainer.green * 0.35f,
                scheme.primaryContainer.blue * 0.35f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(scheme.primaryContainer, dark)))
            )
            return@Box
        }

        AnimatedContent(
            targetState = albumArtUri,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(450)) },
            label = "immersiveCoverBackdrop"
        ) { uri ->
            Box(modifier = Modifier.fillMaxSize()) {
                // ② 模糊延伸层（一次性预渲染；放大裁切避免露边）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.12f
                            scaleY = 1.12f
                        }
                ) {
                    BlurryBackdrop(
                        model = uri,
                        modifier = Modifier.fillMaxSize(),
                        radius = 25,
                        sampling = 2,
                        decodeSizePx = 256
                    )
                }
                // ③ 融合遮罩：主题色向下渐显（0.72 以下纯主题色 → 「下面是主题色」）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to scheme.primaryContainer.copy(alpha = 0.0f),
                                0.34f to scheme.primaryContainer.copy(alpha = 0.12f),
                                0.55f to scheme.primaryContainer.copy(alpha = 0.62f),
                                0.72f to scheme.primaryContainer
                            )
                        )
                )
                // ④ 清晰封面层：铺满上半部分（随 contentFraction 收放），底部 DstIn 溶解
                val clearHeight = 0.52f * contentFraction.coerceIn(0f, 1f)
                if (clearHeight > 0.01f) {
                    val context = LocalContext.current
                    val request = remember(uri) {
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(1024)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(clearHeight)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.00f to Color.Black,
                                        0.70f to Color.Black,
                                        0.86f to Color.Black.copy(alpha = 0.55f),
                                        1.00f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
