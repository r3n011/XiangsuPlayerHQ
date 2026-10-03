package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil.size.Size
import jp.wasabeef.blurry.blurBitmapWithBlurry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * 低版本「软件模糊」统一支持。
 *
 * Compose 的 `Modifier.blur` 底层依赖 `RenderEffect`，而 `RenderEffect` 是
 * **Android 12（API 31）** 才加入的系统能力。在 API 31 以下 `Modifier.blur` 是「空操作」——
 * 不报错、也不模糊，画面完全清晰。历史代码多处直接写 `.blur(40.dp)`，
 * 导致 Android 11 及以下**完全没有模糊效果**（这正是「低版本模糊没了」的原因）。
 *
 * 降级方案按内容分三档，都不依赖 RenderEffect、全版本可用：
 * - 图片背景（封面 / 自定义背景图）→ `BlurryBackdrop`（Blurry 真高斯）；
 * - 任意 Compose 内容（歌词行「远处发虚」）→ `Modifier.softwareBlur`（快照降采样 + Blurry）；
 * - 导航栏毛玻璃 → `rememberLowVersionBlurBackdrop`（整屏采集 + 盒式模糊）。
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
 * 原理：把内容录进 [GraphicsLayer] → 快照成位图 → **降采样**到 1/[factor] 尺寸 →
 * 交给 **Blurry**（jp.wasabeef）做真高斯模糊（RenderScript 优先，失败自动降级
 * 纯 Java StackBlur）→ 绘制时用双线性插值放大回原尺寸。降采样既省算力，
 * 又让模糊半径落在 RenderScript 的 25px 限制内；放大时的重采样再补一层平滑。
 * 全程不依赖 `RenderEffect`，Android 6.0 起可用（低版本 Compose 用 LayerSnapshot 完成快照）。
 *
 * 结果按 [contentKey]、[factor] 和节点尺寸缓存：只有内容或强度档位变化时才重新计算。
 * 因此**逐帧动画（alpha / 缩放）必须放在本修饰符的外层**，否则每帧都会重新栅格化。
 *
 * 快照就绪前按原样绘制（不会闪空）；模糊失败则退化为降采样图，不影响内容显示。
 */
@Composable
fun Modifier.softwareBlur(
    radius: Dp,
    contentKey: Any?,
): Modifier {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var nodeSize by remember { mutableStateOf(IntSize.Zero) }
    var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
    /**
     * 是否有一次「重新录制 layer → 重新模糊」在等绘制阶段完成。
     *
     * ⚡ **绝不再把 blurred 置空**：旧实现每次重算都先 `blurred = null`，重算窗口内这一行
     * 会退回绘制实时（清晰）内容 —— 只要重算被频繁触发，就表现为「清晰 / 模糊」逐帧来回闪
     * （低版本歌词一直闪烁的根因）。现在始终保留上一张模糊图，新图算好后再原子替换。
     */
    var snapshotPending by remember { mutableStateOf(true) }
    // 与旧实现一致的降采样档位：模糊越强、降得越狠（2~6 倍）
    val factor = (2f + radius.value * 0.6f).roundToInt().coerceIn(2, 6)
    // Blurry 作用在降采样图上，半径等比缩小；ScriptIntrinsicBlur 只接受 (0, 25]
    val blurRadius = (with(density) { radius.toPx() } / factor).roundToInt().coerceIn(1, 25)

    LaunchedEffect(contentKey, factor, nodeSize) {
        if (nodeSize.width <= 0 || nodeSize.height <= 0) return@LaunchedEffect
        // 请求重新录制（不清缓存），等两帧确保录制已落到绘制阶段
        snapshotPending = true
        withFrameNanos { }
        withFrameNanos { }
        val full = runCatching { layer.toImageBitmap() }.getOrNull()
        if (full == null || full.width <= 0 || full.height <= 0) {
            snapshotPending = false
            return@LaunchedEffect
        }
        val smallWidth = (full.width / factor).coerceAtLeast(1)
        val smallHeight = (full.height / factor).coerceAtLeast(1)
        // 降采样 + 纯 Kotlin 盒式模糊都在后台线程做
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val small = Bitmap.createScaledBitmap(
                    full.asAndroidBitmap(), smallWidth, smallHeight, true
                )
                val out = blurBitmapWithBlurry(context, small, blurRadius) ?: small
                // ⚡ 部分设备上 layer.toImageBitmap() 会拿到**全透明**的空图：用它绘制会让
                //    整行「消失」（歌词行一会儿有一会儿没有）。空图直接丢弃，继续用上一张；
                //    一直没有可用结果就退回绘制实时内容（清晰但不闪、不丢行）。
                if (out.isFullyTransparent()) null else out.asImageBitmap()
            }.onFailure { Timber.w(it, "softwareBlur: 模糊失败，保留上一张结果") }
                .getOrNull()
        }
        if (result != null) blurred = result
        snapshotPending = false
    }

    return this
        .onSizeChanged { nodeSize = it }
        .drawWithContent {
            val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
            if (target.width <= 0 || target.height <= 0) return@drawWithContent
            val cached = blurred
            if (snapshotPending || cached == null) {
                // 需要新内容：录进 layer 供快照；屏幕上仍画上一张模糊图（有的话），避免闪
                layer.record(density, layoutDirection, target) {
                    this@drawWithContent.drawContent()
                }
                if (cached != null) {
                    drawImage(image = cached, dstSize = target, filterQuality = FilterQuality.High)
                } else {
                    drawLayer(layer)
                }
            } else {
                drawImage(image = cached, dstSize = target, filterQuality = FilterQuality.High)
            }
        }
}

/** 位图是否全透明（识别「快照拿到空图」的异常，避免整行消失）。 */
private fun Bitmap.isFullyTransparent(): Boolean {
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return true
    val step = 8
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            if (((getPixel(x, y) ushr 24) and 0xFF) > 8) return false
            x += step
        }
        y += step
    }
    return true
}
