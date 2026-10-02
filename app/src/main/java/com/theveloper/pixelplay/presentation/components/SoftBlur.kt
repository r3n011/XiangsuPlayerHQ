package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import coil.size.Size
import kotlin.math.roundToInt

/**
 * 低版本「软件模糊」统一支持。
 *
 * Compose 的 `Modifier.blur` 底层依赖 `RenderEffect`，而 `RenderEffect` 是
 * **Android 12（API 31）** 才加入的系统能力。在 API 31 以下 `Modifier.blur` 是「空操作」——
 * 不报错、也不模糊，画面完全清晰。历史代码多处直接写 `.blur(40.dp)`，
 * 导致 Android 11 及以下**完全没有模糊效果**（这正是「低版本模糊没了」的原因）。
 *
 * 降级方案：把图片**解码到极小尺寸**再交给 Compose 放大铺满。放大时的双线性插值
 * 本身就是柔和的颜色过渡，观感接近高斯模糊；不依赖 RenderEffect、全版本可用，
 * 而且解码开销比常规尺寸更小。
 */
object SoftBlur {

    /** API 31（Android 12）及以上才有原生 GPU 模糊（RenderEffect / Modifier.blur）。 */
    val isNativeBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 软件模糊解码边长（px）：越小的模糊越重。用于封面背景这类「重模糊」。 */
    const val DECODE_SIZE_STRONG = 24

    /** 软件模糊解码边长（px）：较轻的模糊。 */
    const val DECODE_SIZE_SOFT = 48

    /** 调用方需要模糊、但系统不支持原生模糊时返回 true（此时应改用本对象的降级方案）。 */
    /**
     * ⚡ 开发者选项：强制走「低版本软件模糊」（在高版本设备上验证低版本模糊是否正常）。
     * 由偏好 `force_software_blur` 驱动，见 SettingsViewModel。
     *
     * ⚠️ 必须用 snapshot state 存：这个值在多个 composable 里是**组合期读取**的
     * （`needsSoftwareBlur` / `decodeSize` / `rememberLowVersionBlurBackdrop(enabled)`），
     * 以前用 `@Volatile var` 时改了开关不会触发重组 —— 表现就是「开发者选项里打开
     * 强制低版本模糊，画面毫无变化」，要等下一次其它原因的重组（甚至重启）才生效。
     */
    private val forceSoftwareBlurState = mutableStateOf(false)

    var forceSoftwareBlur: Boolean
        get() = forceSoftwareBlurState.value
        set(value) {
            forceSoftwareBlurState.value = value
        }

    fun needsSoftwareBlur(needBlur: Boolean): Boolean =
        needBlur && (!isNativeBlurSupported || forceSoftwareBlur)

    /**
     * 计算图片解码尺寸：
     * - 需要软件模糊（见 [needsSoftwareBlur]）→ 返回极小尺寸，靠放大插值得到模糊；
     * - 否则返回 [normalSize] 常规尺寸，模糊交给原生 `Modifier.blur`。
     */
    fun decodeSize(
        needBlur: Boolean,
        normalSize: Int = 1024,
        softBlurSize: Int = DECODE_SIZE_STRONG
    ): Size = if (needsSoftwareBlur(needBlur)) {
        Size(softBlurSize, softBlurSize)
    } else {
        Size(normalSize, normalSize)
    }
}

/**
 * API 31 以下的「真模糊」修饰符：对内容整体做像素级模糊，
 * 而不是在清晰内容后面叠一层同色虚影（后者字形仍然是锐利的）。
 *
 * 原理：把内容录进 [GraphicsLayer] → 快照成位图 → **降采样**到 1/[downscale] 尺寸 →
 * 绘制时用双线性插值放大回原尺寸。降采样丢掉了高频细节，放大时的重采样就是模糊本身。
 * 全程不依赖 `RenderEffect`，Android 6.0 起可用（低版本 Compose 用 LayerSnapshot 完成快照）。
 *
 * 结果按 [contentKey]、[downscale] 和节点尺寸缓存：只有内容或强度变化时才重新快照。
 * 因此**逐帧动画（alpha / 缩放）必须放在本修饰符的外层**，否则每帧都会重新栅格化。
 *
 * 快照就绪前按原样绘制（不会闪空）；快照失败则退化为不模糊，不影响内容显示。
 */
@Composable
fun Modifier.softwareBlur(
    downscale: Int,
    contentKey: Any?,
): Modifier {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var nodeSize by remember { mutableStateOf(IntSize.Zero) }
    var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
    val factor = downscale.coerceIn(1, 12)

    LaunchedEffect(contentKey, factor, nodeSize) {
        if (nodeSize.width <= 0 || nodeSize.height <= 0) return@LaunchedEffect
        // 内容变化时先丢掉旧结果，让接下来这帧重新录制 layer
        blurred = null
        // 等两帧：第一帧把 blurred = null 的重组落到绘制阶段（重新录制 layer），
        // 第二帧确保录制已经完成，快照拿到的才是新内容。
        withFrameNanos { }
        withFrameNanos { }
        val full = runCatching { layer.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        if (full.width <= 0 || full.height <= 0) return@LaunchedEffect
        val smallWidth = (full.width / factor).coerceAtLeast(1)
        val smallHeight = (full.height / factor).coerceAtLeast(1)
        blurred = runCatching {
            ImageBitmap(smallWidth, smallHeight).also { target ->
                // 用位置参数：CanvasDrawScope.draw(Density, LayoutDirection, Canvas, Size, block)
                CanvasDrawScope().draw(
                    density,
                    layoutDirection,
                    Canvas(target),
                    androidx.compose.ui.geometry.Size(smallWidth.toFloat(), smallHeight.toFloat())
                ) {
                    scale(1f / factor, 1f / factor, Offset.Zero) { drawImage(full) }
                }
            }
        }.getOrNull()
    }

    return this
        .onSizeChanged { nodeSize = it }
        .drawWithContent {
            val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
            val cached = blurred
            if (cached != null && target.width > 0 && target.height > 0) {
                drawImage(
                    image = cached,
                    dstSize = target,
                    filterQuality = FilterQuality.High
                )
            } else {
                // 用位置参数：GraphicsLayer.record(Density, LayoutDirection, IntSize, block)
                layer.record(density, layoutDirection, target) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
            }
        }
}
