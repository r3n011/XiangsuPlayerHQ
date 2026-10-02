package jp.wasabeef.blurry

import android.content.Context
import android.graphics.Bitmap

/**
 * Blurry 的「位图进 → 位图出」同步入口（桥接文件）。
 *
 * ⚡ 为什么不用公开 API：Blurry 4.0.1 只能把模糊结果画进 `ImageView`
 * （`Blurry.with(ctx)…from(bmp).into(iv)`），拿不回 Bitmap；而 Compose 里
 * （歌词行模糊等）需要在后台线程同步拿到位图再自己绘制。
 * 引擎类 [Blur] / [BlurFactor] 是**包私有**的 —— 本文件声明在同一个包里，
 * 因此可以直接调用。这就是 `BitmapComposer.into()` 内部走的那条路：
 * RenderScript ScriptIntrinsicBlur 优先，失败自动降级纯 Java StackBlur。
 *
 * ⚠️ 半径必须夹在 1..25：`ScriptIntrinsicBlur.setRadius` 越界会抛
 *    IllegalArgumentException，Blurry 只会捕获 RSRuntimeException，不会兜底。
 * ⚠️ RenderScript 建上下文有开销，调用方应放到后台线程执行。
 * ⚠️ R8 混淆不受影响：这里是直接字节码引用，会被一并重写（不依赖反射）。
 */
internal fun blurBitmapWithBlurry(
    context: Context,
    source: Bitmap,
    radius: Int,
    /** 采样倍率：>1 时 Blurry 内部先降采样、模糊后再放大回原尺寸 */
    sampling: Int = 1,
): Bitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    val factor = BlurFactor()
    factor.width = source.width
    factor.height = source.height
    factor.radius = radius.coerceIn(1, 25)
    factor.sampling = sampling.coerceAtLeast(1)
    return Blur.of(context, source, factor)
}
