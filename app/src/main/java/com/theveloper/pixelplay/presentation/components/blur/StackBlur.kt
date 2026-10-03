package com.theveloper.pixelplay.presentation.components.blur

import android.graphics.Bitmap

/**
 * 纯 Kotlin 的位图模糊（三次分离式盒式模糊 ≈ 高斯），**不依赖 RenderScript**。
 *
 * 为什么需要它：低版本（API < 31）没有 `RenderEffect`，Compose 的 `Modifier.blur` 是空操作；
 * 而 Blurry（jp.wasabeef）内部走 RenderScript —— RS 在 Android 12+ 已废弃、部分 ROM / 设备
 * 的 RS 驱动不可用会直接失败。此前失败后只回退成「降采样图」（等于没有模糊），
 * 表现就是「低版本歌词模糊出不来」。这里用纯 CPU 实现兜底，任何版本 / 任何 ROM 都可用。
 *
 * 成本很低：调用方（歌词行 / 封面背景）都会先降采样到 24~256px 再模糊。
 */
internal object StackBlur {

    /**
     * 对 [source] 做 [radius] 像素的模糊并返回新位图。
     * [sampling] > 1 时先降采样、模糊后再放大回原尺寸（更省算力、观感更柔）。
     */
    fun blur(source: Bitmap, radius: Int, sampling: Int = 1): Bitmap {
        val r = radius.coerceAtLeast(1)
        val sample = sampling.coerceAtLeast(1)
        var working = source
        if (sample > 1) {
            val sw = (source.width / sample).coerceAtLeast(1)
            val sh = (source.height / sample).coerceAtLeast(1)
            working = Bitmap.createScaledBitmap(source, sw, sh, true)
        }

        val w = working.width
        val h = working.height
        if (w <= 0 || h <= 0) return source

        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        // 三次盒式模糊逼近高斯（与 StackBlur 观感接近，实现更短、无浮点误差累积）
        repeat(3) {
            boxBlurHorizontal(pixels, tmp, w, h, r)
            boxBlurVertical(tmp, pixels, w, h, r)
        }

        val blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        blurred.setPixels(pixels, 0, w, 0, 0, w, h)

        if (sample > 1) {
            val scaled = Bitmap.createScaledBitmap(blurred, source.width, source.height, true)
            if (scaled != blurred) blurred.recycle()
            return scaled
        }
        return blurred
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var red = 0; var green = 0; var blue = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                a += (p ushr 24) and 0xFF
                red += (p ushr 16) and 0xFF
                green += (p ushr 8) and 0xFF
                blue += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((a / div) shl 24) or ((red / div) shl 16) or
                    ((green / div) shl 8) or (blue / div)
                val outP = src[row + (x - r).coerceIn(0, w - 1)]
                val inP = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                red += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                green += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                blue += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var red = 0; var green = 0; var blue = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                a += (p ushr 24) and 0xFF
                red += (p ushr 16) and 0xFF
                green += (p ushr 8) and 0xFF
                blue += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((a / div) shl 24) or ((red / div) shl 16) or
                    ((green / div) shl 8) or (blue / div)
                val outP = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inP = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                red += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                green += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                blue += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }
}
