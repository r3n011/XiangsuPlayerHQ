package com.theveloper.pixelplay.presentation.components.blur

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.theveloper.pixelplay.utils.LowVersionBlur
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * 低版本 Android（API < 31，没有 RenderEffect 硬件模糊）专用的「背景内容模糊」采集器。
 *
 * 用法：
 * ```
 * val backdrop = rememberLowVersionBlurBackdrop(enabled = isLowVersion && blurOn)
 * Box(Modifier.fillMaxSize()) {
 *     // 需要被模糊的内容（整屏内容层）
 *     Box(Modifier.fillMaxSize().then(backdrop.contentModifier)) { Content() }
 *     // 需要"毛玻璃"的浮层（导航栏 / 歌词底衬）：把采集结果画在自己背后
 *     NavBar(Modifier.then(backdrop.blurModifier))
 * }
 * ```
 *
 * 实现要点（对齐 md3Music 低版本那套）：
 * - 内容层用 [rememberGraphicsLayer] 离屏录制，[withFrameNanos] 驱动、**限频**（默认 120ms）
 *   重新抓帧 —— 逐帧抓整屏位图在低端机上太贵，限频后观感几乎无差；
 * - 抓到的位图交给 [LowVersionBlur]（降采样 + 盒式模糊）得到模糊结果，缓存复用；
 * - 浮层用 [blurModifier] 把这张模糊图按 cover 铺在自己身后（绘制阶段读 State，零重组）。
 *
 * ⚠️ 只在 `Build.VERSION.SDK_INT < Build.VERSION_CODES.S` 时构造（[enabled] = false 时
 *    返回的 modifier 全是 no-op，高版本继续走原来的 RenderEffect/haze，完全不受影响）。
 */
class LowVersionBlurBackdrop internal constructor(
    private val blurredState: State<ImageBitmap?>,
    private val contentModifierState: Modifier,
    private val blurModifierState: Modifier,
) {
    /** 挂到「要被模糊的内容」上（高版本/关闭时是 no-op）。 */
    val contentModifier: Modifier get() = contentModifierState

    /** 挂到「毛玻璃浮层」上：它会把采集到的模糊图铺在自己身后。 */
    val blurModifier: Modifier get() = blurModifierState
}

/** 重新抓帧的最小间隔：低端机上 4fps 的模糊底衬已经足够跟手（每次抓帧要回读整屏位图，不能太频繁）。 */
private const val CAPTURE_INTERVAL_MS = 250L

@Composable
fun rememberLowVersionBlurBackdrop(enabled: Boolean): LowVersionBlurBackdrop {
    if (!enabled) {
        // 高版本 / 关闭模糊：全部 no-op，不额外分配任何资源
        val noop = remember { mutableStateOf<ImageBitmap?>(null) }
        return remember {
            LowVersionBlurBackdrop(
                blurredState = noop,
                contentModifierState = Modifier,
                blurModifierState = Modifier,
            )
        }
    }

    val graphicsLayer = rememberGraphicsLayer()
    val blurredState = remember { mutableStateOf<ImageBitmap?>(null) }
    // 内容层 / 浮层在窗口中的位置：绘制阶段按比例裁图用（声明必须在 modifier 之前）
    var contentBounds by remember { mutableStateOf(Rect.Zero) }
    var overlayBounds by remember { mutableStateOf(Rect.Zero) }

    // 限频抓帧 → 降采样 → 盒式模糊
    LaunchedEffect(graphicsLayer) {
        Timber.d("LowVersionBlur: 低版本软件模糊已启用，开始抓帧")
        var loggedFailure = false
        var loggedSuccess = false
        while (isActive) {
            withFrameNanos { }
            delay(CAPTURE_INTERVAL_MS)
            val captured = runCatching { graphicsLayer.toImageBitmap() }.getOrElse { error ->
                if (!loggedFailure) {
                    loggedFailure = true
                    Timber.w(error, "LowVersionBlur: 抓帧失败，低版本模糊不会显示")
                }
                null
            } ?: continue
            // 图层还没绘制过时快照是空尺寸，跳过
            if (captured.width <= 1 || captured.height <= 1) continue
            val source: Bitmap = runCatching { captured.asAndroidBitmap() }.getOrNull() ?: continue
            // ⚡ 半径按抓帧尺寸取比例（5%），保证不同分辨率下模糊强度一致；
            //    之前写死 24px，在 1080p 抓帧降采样后半径只剩 1~2px，画面几乎没变化。
            val radiusPx = maxOf(captured.width, captured.height) * 0.05f
            val result = runCatching { LowVersionBlur.blur(source, radiusPx) }.getOrNull() ?: continue
            if (!loggedSuccess) {
                loggedSuccess = true
                Timber.d(
                    "LowVersionBlur: 首帧模糊完成 %dx%d -> %dx%d (radius=%.1f)",
                    captured.width, captured.height, result.width, result.height, radiusPx
                )
            }
            blurredState.value = result.asImageBitmap()
        }
    }

    val contentModifier = remember(graphicsLayer) {
        Modifier
            .onGloballyPositioned { coords -> contentBounds = coords.boundsInWindow() }
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            }
    }

    // ⚡ 浮层只画「它自己背后那一块」：记录内容层与浮层在窗口里的位置，
    //    按比例从整屏模糊图里裁出对应区域贴回去（真正的 backdrop 行为）。
    val blurModifier = remember {
        Modifier
            .onGloballyPositioned { coords -> overlayBounds = coords.boundsInWindow() }
            .drawWithContent {
                val image = blurredState.value
                if (image != null && contentBounds.width > 0f && overlayBounds.width > 0f) {
                    val scaleX = image.width / contentBounds.width
                    val scaleY = image.height / contentBounds.height
                    val srcLeft = ((overlayBounds.left - contentBounds.left) * scaleX)
                        .coerceIn(0f, image.width.toFloat() - 1f)
                    val srcTop = ((overlayBounds.top - contentBounds.top) * scaleY)
                        .coerceIn(0f, image.height.toFloat() - 1f)
                    val srcW = (overlayBounds.width * scaleX)
                        .coerceIn(1f, image.width - srcLeft)
                    val srcH = (overlayBounds.height * scaleY)
                        .coerceIn(1f, image.height - srcTop)
                    // DrawScope 里带 src/dst 矩形的是 drawImage 重载（drawImageRect 只存在于 Canvas）
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                        srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }
                drawContent()
            }
    }

    return remember(graphicsLayer) {
        LowVersionBlurBackdrop(
            blurredState = blurredState,
            contentModifierState = contentModifier,
            blurModifierState = blurModifier,
        )
    }
}

/** 采集器 + 内容 + 浮层的便捷组合（内容整屏、浮层自己叠在上面）。 */
@Composable
fun LowVersionBlurBox(
    enabled: Boolean,
    backdrop: @Composable BoxScope.(LowVersionBlurBackdrop) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val capture = rememberLowVersionBlurBackdrop(enabled)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(capture.contentModifier)) {
            content()
        }
        backdrop(capture)
    }
}
