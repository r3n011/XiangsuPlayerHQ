package com.theveloper.pixelplay.utils

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 低版本 Android 的位图模糊引擎（API < 31，没有 RenderEffect 硬件模糊时使用）。
 *
 * 思路照搬 md3Music 的低版本实现，核心是「**先降采样、再模糊、交给 GPU 放大**」：
 * 1. 位图降采样到最长边 [MAX_LONG_SIDE] —— 模糊本来就会抹掉细节，降采样既省 CPU，
 *    又让结果天然更平滑（放大时的双线性插值再补一层平滑）；
 * 2. 在降采样图上做多轮**盒式模糊**（box blur，滑动窗口，每像素 O(1)）近似高斯；
 * 3. 调用方按原尺寸放大绘制即可。
 *
 * 全程纯 CPU、不依赖 RenderEffect / AGSL / RenderScript，API 23+ 都能用；
 * 单帧成本与 [MAX_LONG_SIDE]² 成正比（160 时约 2.5 万像素，几毫秒级）。
 *
 * ⚠️ 只在 `Build.VERSION.SDK_INT < Build.VERSION_CODES.S` 时使用 ——
 *    高版本一律走现成的 RenderEffect（硬件、更准更快）。
 */
object LowVersionBlur {

    /** 模糊前的最长边。越小越快、越糊；160 是观感与耗时的折中。 */
    const val MAX_LONG_SIDE = 160

    /** 模糊半径上限（降采样后尺度），避免把画面糊成一团。 */
    private const val MAX_RADIUS = 25

    /** 盒式模糊迭代次数：3 次已非常接近高斯分布。 */
    private const val ITERATIONS = 3

    /**
     * 把 [source] 降采样并模糊，返回**新的**位图（不改动入参）。
     * [radiusPx] 按**原图**尺度给出，内部会随降采样等比缩小。
     * 半径过小时直接返回原图（省一次拷贝）。
     */
    fun blur(source: Bitmap, radiusPx: Float): Bitmap {
        if (radiusPx <= 0.5f || source.isRecycled) return source
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        val scale = (MAX_LONG_SIDE.toFloat() / maxSide).coerceAtMost(1f)
        val targetW = max(1, (source.width * scale).roundToInt())
        val targetH = max(1, (source.height * scale).roundToInt())

        val working = if (targetW == source.width && targetH == source.height) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }

        val pixels = IntArray(targetW * targetH)
        working.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        val radius = (radiusPx * scale).roundToInt().coerceIn(1, MAX_RADIUS)
        boxBlur(pixels, targetW, targetH, radius)
        working.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        return working
    }

    /**
     * 纯 Kotlin 盒式模糊（可分离：先横后竖），就地修改 [pixels]。
     * 滑动窗口累加，每像素 O(1)，[iterations] 次迭代后接近高斯模糊。
     */
    fun boxBlur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        iterations: Int = ITERATIONS,
    ) {
        if (radius < 1 || width <= 0 || height <= 0) return
        if (pixels.size < width * height) return
        val temp = IntArray(width * height)
        repeat(iterations.coerceAtLeast(1)) {
            horizontalPass(pixels, temp, width, height, radius)
            verticalPass(temp, pixels, width, height, radius)
        }
    }

    private fun horizontalPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            // 初始窗口：越界部分用边缘像素补齐
            for (i in -radius..radius) {
                val color = src[row + i.coerceIn(0, width - 1)]
                sumA += (color ushr 24) and 0xFF
                sumR += (color ushr 16) and 0xFF
                sumG += (color ushr 8) and 0xFF
                sumB += color and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val outgoing = src[row + (x - radius).coerceIn(0, width - 1)]
                val incoming = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                sumA += ((incoming ushr 24) and 0xFF) - ((outgoing ushr 24) and 0xFF)
                sumR += ((incoming ushr 16) and 0xFF) - ((outgoing ushr 16) and 0xFF)
                sumG += ((incoming ushr 8) and 0xFF) - ((outgoing ushr 8) and 0xFF)
                sumB += (incoming and 0xFF) - (outgoing and 0xFF)
            }
        }
    }

    private fun verticalPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -radius..radius) {
                val color = src[i.coerceIn(0, height - 1) * width + x]
                sumA += (color ushr 24) and 0xFF
                sumR += (color ushr 16) and 0xFF
                sumG += (color ushr 8) and 0xFF
                sumB += color and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = ((sumA / window) shl 24) or
                    ((sumR / window) shl 16) or
                    ((sumG / window) shl 8) or
                    (sumB / window)
                val outgoing = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val incoming = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumA += ((incoming ushr 24) and 0xFF) - ((outgoing ushr 24) and 0xFF)
                sumR += ((incoming ushr 16) and 0xFF) - ((outgoing ushr 16) and 0xFF)
                sumG += ((incoming ushr 8) and 0xFF) - ((outgoing ushr 8) and 0xFF)
                sumB += (incoming and 0xFF) - (outgoing and 0xFF)
            }
        }
    }
}
