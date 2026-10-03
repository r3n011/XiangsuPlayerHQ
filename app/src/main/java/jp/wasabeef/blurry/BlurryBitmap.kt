package jp.wasabeef.blurry

import android.content.Context
import android.graphics.Bitmap
import com.theveloper.pixelplay.presentation.components.blur.StackBlur
import timber.log.Timber

/**
 * 低版本（API < 31）软件模糊的「位图进 → 位图出」同步入口。
 *
 * ⚡ 引擎已从 Blurry（RenderScript）换成**纯 Kotlin 的盒式模糊**（[StackBlur]）：
 * RenderScript 在 Android 12+ 已废弃，且部分 ROM / 设备的 RS 驱动不可用 —— Blurry 会直接
 * 失败（它只兜底 RSRuntimeException，很多设备抛的是别的异常），而旧代码失败后只回退成
 * 「降采样图」，等于**根本没有模糊**。这正是「低版本歌词模糊出不来」的原因。
 * 现在换成纯 CPU 实现，任何版本 / 任何 ROM 都可用；调用方本来就先降采样到 24~256px，
 * 计算量很小，所以放在后台线程即可。
 *
 * 保留原函数名（调用方 [com.theveloper.pixelplay.presentation.components.SoftBlur] 与
 * `BlurryBackdrop` 都用它），签名不变。
 */
internal fun blurBitmapWithBlurry(
    context: Context,
    source: Bitmap,
    radius: Int,
    /** 采样倍率：>1 时先降采样、模糊后再放大回原尺寸 */
    sampling: Int = 1,
): Bitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    return runCatching { StackBlur.blur(source, radius.coerceIn(1, 25), sampling) }
        .onFailure { Timber.w(it, "blurBitmapWithBlurry: 纯 Kotlin 模糊失败") }
        .getOrNull()
}
