package com.theveloper.pixelplay.presentation.components.isolation

import android.graphics.Bitmap
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * AMLL Isolation 渲染器的取色管线，移植自
 * applemusic-like-lyrics/packages/core/src/bg-render/palette（其取色部分移植自
 * Storyteller-Studios/Impressionist, MIT）。
 *
 * 流程：封面缩到 64×64 建颜色直方图 → K-Means 与八叉树两种聚类各自取色 →
 * 自动择优（dominant 取向下偏好更分散的一支）→ 输出指定数量的主色。
 */
internal class ColorCount(val color: FloatArray, var count: Int)

internal class ThemeColorResult(val color: FloatArray, val colorIsDark: Boolean)

internal class PaletteResult(
    val palette: List<FloatArray>,
    val paletteIsDark: Boolean,
    /** auto 模式下记录实际胜出的算法，便于调试。 */
    val algo: String,
)

/* ============================== 色彩空间 ============================== */

private fun channelToLinear(value: Float): Float =
    if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

/** sRGB（分量 [0,1]）→ OkLab，感知均匀，混色与过渡都在这个空间做。 */
internal fun srgbToOkLab(rgb: FloatArray): FloatArray {
    val lr = channelToLinear(rgb[0])
    val lg = channelToLinear(rgb[1])
    val lb = channelToLinear(rgb[2])
    val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
    val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
    val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
    return floatArrayOf(
        (0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s).toFloat(),
        (1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s).toFloat(),
        (0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s).toFloat(),
    )
}

private const val D65_X = 0.95047
private const val D65_Y = 1.0
private const val D65_Z = 1.0883

private fun fxyz(t: Double): Double =
    if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0

internal fun rgbToLab(rgb: FloatArray): FloatArray {
    val r = rgb[0] / 255.0
    val g = rgb[1] / 255.0
    val b = rgb[2] / 255.0
    val x = r * 0.4124 + g * 0.3576 + b * 0.1805
    val y = r * 0.2126 + g * 0.7152 + b * 0.0722
    val z = r * 0.0193 + g * 0.1192 + b * 0.9505
    val fx = fxyz(x / D65_X)
    val fy = fxyz(y / D65_Y)
    val fz = fxyz(z / D65_Z)
    return floatArrayOf(
        (116 * fy - 16).toFloat(),
        (500 * (fx - fy)).toFloat(),
        (200 * (fy - fz)).toFloat(),
    )
}

internal fun labToRgb(lab: FloatArray): FloatArray {
    val delta = 6.0 / 29.0
    val fy = (lab[0] + 16.0) / 116.0
    val fx = fy + lab[1] / 500.0
    val fz = fy - lab[2] / 200.0
    val x = if (fx > delta) D65_X * fx * fx * fx else (fx - 16.0 / 116.0) * 3 * delta * delta * D65_X
    val y = if (fy > delta) D65_Y * fy * fy * fy else (fy - 16.0 / 116.0) * 3 * delta * delta * D65_Y
    val z = if (fz > delta) D65_Z * fz * fz * fz else (fz - 16.0 / 116.0) * 3 * delta * delta * D65_Z
    return floatArrayOf(
        ((x * 3.2406 - y * 1.5372 - z * 0.4986) * 255).toFloat().coerceIn(0f, 255f),
        ((-x * 0.9689 + y * 1.8758 + z * 0.0415) * 255).toFloat().coerceIn(0f, 255f),
        ((x * 0.0557 - y * 0.204 + z * 1.057) * 255).toFloat().coerceIn(0f, 255f),
    )
}

private fun yToLStar(y: Double): Float =
    (if (y <= 216.0 / 24389.0) y * (24389.0 / 27.0) else cbrt(y) * 116.0 - 16.0).toFloat()

private fun lStarOf(rgb: FloatArray): Float {
    val y = 0.2126 * channelToLinear(rgb[0] / 255f) +
        0.7152 * channelToLinear(rgb[1] / 255f) +
        0.0722 * channelToLinear(rgb[2] / 255f)
    return yToLStar(y)
}

private fun paletteIsDark(rgb: FloatArray) = lStarOf(rgb) <= 40f
private fun paletteIsLight(rgb: FloatArray) = lStarOf(rgb) >= 60f
private fun themeIsDark(rgb: FloatArray) = lStarOf(rgb) <= 50f

private fun distanceSquared(a: FloatArray, b: FloatArray): Float {
    val dx = a[0] - b[0]
    val dy = a[1] - b[1]
    val dz = a[2] - b[2]
    return dx * dx + dy * dy + dz * dz
}

/* ============================== 直方图 ============================== */

/**
 * 缩到最长边 [sampleSize] 后逐像素统计。取色只关心整体色彩分布，
 * 64×64 已足够，且能把 K-Means 压到几毫秒内。
 */
internal fun buildColorHistogram(source: Bitmap, sampleSize: Int = 64): List<ColorCount> {
    val scale = min(1f, sampleSize.toFloat() / max(source.width, source.height))
    val w = max(1, (source.width * scale).toInt())
    val h = max(1, (source.height * scale).toInt())
    val scaled = Bitmap.createScaledBitmap(source, w, h, true)
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    val counts = HashMap<Int, Int>(w * h)
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xff
        if (alpha == 0) continue
        counts[pixel and 0xffffff] = (counts[pixel and 0xffffff] ?: 0) + 1
    }
    return counts.map { (key, count) ->
        ColorCount(
            floatArrayOf(
                ((key shr 16) and 0xff).toFloat(),
                ((key shr 8) and 0xff).toFloat(),
                (key and 0xff).toFloat(),
            ),
            count,
        )
    }
}

/* ============================== 随机源 ============================== */

/** mulberry32，按直方图内容播种，同一张封面每次得到完全相同的调色板。 */
internal fun createRandom(seed: Int): () -> Float {
    var state = seed
    return fun(): Float {
        state += 0x6d2b79f5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor ((t xor (t ushr 7)) * (t or 61) + t)
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0f
    }
}

internal fun seedFromHistogram(entries: List<ColorCount>): Int {
    // 0x811c9dc5 超出 Int 正数范围，Kotlin 里会推断成 Long，必须显式收成 Int
    var hash = 0x811c9dc5.toInt()
    for (entry in entries) {
        for (value in entry.color) {
            hash = hash xor (value.toInt() and 0xffff)
            hash *= 0x01000193
        }
        hash = hash xor (entry.count and 0xffff)
        hash *= 0x01000193
    }
    return hash
}

/* ============================== K-Means ============================== */

private fun isNotNearWhite(color: FloatArray) = color.any { it <= 250f }

private fun filterOrFallback(entries: List<ColorCount>, predicate: (ColorCount) -> Boolean): List<ColorCount> {
    val filtered = entries.filter(predicate)
    return if (filtered.isNotEmpty()) filtered else entries.toList()
}

private fun groupColors(entries: List<ColorCount>): List<ColorCount> {
    val grouped = LinkedHashMap<String, ColorCount>()
    for (entry in entries) {
        val key = "${entry.color[0]},${entry.color[1]},${entry.color[2]}"
        val existing = grouped[key]
        if (existing != null) existing.count += entry.count
        else grouped[key] = ColorCount(entry.color.copyOf(), entry.count)
    }
    return grouped.values.toList()
}

private fun findNearestCenterIndex(color: FloatArray, centers: List<FloatArray>): Int {
    var nearest = 0
    var minDist = Float.POSITIVE_INFINITY
    for (i in centers.indices) {
        val dist = distanceSquared(color, centers[i])
        if (dist < minDist) {
            nearest = i
            minDist = dist
        }
    }
    return nearest
}

private fun findFarthestColor(entries: List<ColorCount>, centers: List<FloatArray>): FloatArray {
    var farthest = floatArrayOf(0f, 0f, 0f)
    var maxDistance = Float.NEGATIVE_INFINITY
    for (entry in entries) {
        var nearest = Float.POSITIVE_INFINITY
        for (center in centers) nearest = min(nearest, distanceSquared(entry.color, center))
        if (nearest > maxDistance) {
            maxDistance = nearest
            farthest = entry.color
        }
    }
    return farthest
}

private fun kMeansPlusPlusCenters(
    entries: List<ColorCount>,
    clusterCount: Int,
    random: () -> Float,
): List<FloatArray> {
    val firstIndex = (random() * entries.size).toInt().coerceAtMost(entries.size - 1)
    val selected = hashSetOf(firstIndex)
    val centers = mutableListOf(entries[firstIndex].color)
    for (i in 1 until clusterCount) {
        var accumulated = 0.0
        val accDistances = DoubleArray(entries.size)
        for (v in entries.indices) {
            val target = entries[v].color
            var minDist = distanceSquared(centers[0], target)
            for (k in 1 until i) minDist = min(minDist, distanceSquared(centers[k], target))
            accumulated += minDist * entries[v].count
            accDistances[v] = accumulated
        }
        if (accumulated <= Double.MIN_VALUE) {
            val next = entries.indices.firstOrNull { it !in selected } ?: break
            selected.add(next)
            centers.add(entries[next].color)
            continue
        }
        val targetPoint = random() * accumulated
        for (v in entries.indices) {
            if (v !in selected && accDistances[v] >= targetPoint) {
                selected.add(v)
                centers.add(entries[v].color)
                break
            }
        }
        if (centers.size == i) {
            val next = entries.indices.firstOrNull { it !in selected } ?: break
            selected.add(next)
            centers.add(entries[next].color)
        }
    }
    return centers
}

private fun kMeansCluster(
    entries: List<ColorCount>,
    numClusters: Int,
    useKMeansPlusPlus: Boolean,
    random: () -> Float,
): List<FloatArray> {
    val clusterCount = min(numClusters, entries.size)
    if (clusterCount <= 0) return emptyList()

    val centers: MutableList<FloatArray> = if (useKMeansPlusPlus) {
        kMeansPlusPlusCenters(entries, clusterCount, random).toMutableList()
    } else {
        val shuffled = entries.map { it.color }.toMutableList()
        for (i in shuffled.indices.reversed()) {
            val j = (random() * (i + 1)).toInt()
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        shuffled.take(clusterCount).toMutableList()
    }

    val assignments = IntArray(entries.size)
    var changed = true
    var iterations = 0
    while (changed && iterations < 250) {
        changed = false
        iterations++
        for (i in entries.indices) assignments[i] = findNearestCenterIndex(entries[i].color, centers)
        for (i in 0 until clusterCount) {
            var sumX = 0.0
            var sumY = 0.0
            var sumZ = 0.0
            var weight = 0.0
            for (e in entries.indices) {
                if (assignments[e] != i) continue
                val entry = entries[e]
                sumX += entry.color[0] * entry.count
                sumY += entry.color[1] * entry.count
                sumZ += entry.color[2] * entry.count
                weight += entry.count
            }
            if (weight == 0.0) {
                centers[i] = findFarthestColor(entries, centers)
                changed = true
                continue
            }
            val newCenter = floatArrayOf(
                (sumX / weight).toFloat(),
                (sumY / weight).toFloat(),
                (sumZ / weight).toFloat(),
            )
            if (!newCenter.contentEquals(centers[i])) {
                centers[i] = newCenter
                changed = true
            }
        }
    }
    return centers
}

internal fun createThemeColor(
    sourceColors: List<ColorCount>,
    ignoreWhite: Boolean = false,
    toLab: Boolean = false,
    random: (() -> Float)? = null,
): ThemeColorResult {
    val rng = random ?: createRandom(seedFromHistogram(sourceColors))
    var entries = sourceColors.toList()
    if (ignoreWhite && entries.size > 1) entries = filterOrFallback(entries) { isNotNearWhite(it.color) }
    if (toLab) entries = entries.map { ColorCount(rgbToLab(it.color), it.count) }
    entries = groupColors(entries)
    val centers = kMeansCluster(entries, 1, false, rng)
    val first = centers.firstOrNull() ?: floatArrayOf(0f, 0f, 0f)
    val color = if (toLab) labToRgb(first) else first
    return ThemeColorResult(color, themeIsDark(color))
}

internal fun createKMeansPalette(
    sourceColors: List<ColorCount>,
    clusterCount: Int,
    themeColor: ThemeColorResult,
    ignoreWhite: Boolean = false,
    toLab: Boolean = false,
    useKMeansPlusPlus: Boolean = false,
    intent: String = "accent",
    random: (() -> Float)? = null,
): PaletteResult {
    val rng = random ?: createRandom(seedFromHistogram(sourceColors))
    var effectiveIgnoreWhite = ignoreWhite
    var effectiveKmpp = useKMeansPlusPlus
    if (sourceColors.size == 1) {
        effectiveIgnoreWhite = false
        effectiveKmpp = false
    }
    val colorIsDark = themeColor.colorIsDark
    val entries = filterOrFallback(sourceColors) { entry ->
        if (intent == "dominant") {
            !effectiveIgnoreWhite || isNotNearWhite(entry.color)
        } else if (colorIsDark) {
            paletteIsDark(entry.color)
        } else {
            paletteIsLight(entry.color) && (!effectiveIgnoreWhite || isNotNearWhite(entry.color))
        }
    }
        .let { if (toLab) it.map { e -> ColorCount(rgbToLab(e.color), e.count) } else it }
        .let(::groupColors)

    val centers = kMeansCluster(entries, clusterCount, effectiveKmpp, rng)
    val dominant = centers.map { if (toLab) labToRgb(it) else it }
    val palette = List(clusterCount) { i ->
        if (dominant.isNotEmpty()) dominant[i % dominant.size].copyOf() else floatArrayOf(0f, 0f, 0f)
    }
    return PaletteResult(palette, colorIsDark, "K-Means")
}

/* ============================== 八叉树 ============================== */

private const val MAX_COLOR_DEPTH = 8

private class OctreeNode(
    private val owner: OctreeQuantizer,
    val parentNode: OctreeNode?,
    val indexInParent: Int,
) {
    val children = arrayOfNulls<OctreeNode>(8)
    var childCount = 0
    var leafNodeCount = 0
    var sampleCount = 0
    private var redSum = 0.0
    private var greenSum = 0.0
    private var blueSum = 0.0

    val averageColor: FloatArray
        get() = if (sampleCount == 0) floatArrayOf(0f, 0f, 0f)
        else floatArrayOf(
            (redSum / sampleCount).toFloat(),
            (greenSum / sampleCount).toFloat(),
            (blueSum / sampleCount).toFloat(),
        )

    fun addColor(red: Int, green: Int, blue: Int, depth: Int, sampleCount: Int) {
        this.sampleCount += sampleCount
        redSum += red.toDouble() * sampleCount
        greenSum += green.toDouble() * sampleCount
        blueSum += blue.toDouble() * sampleCount
        if (depth == MAX_COLOR_DEPTH) {
            if (leafNodeCount == 0) leafNodeCount = 1
            return
        }
        val bitShift = 7 - depth
        val childIndex =
            (((red shr bitShift) and 1) shl 2) or (((green shr bitShift) and 1) shl 1) or ((blue shr bitShift) and 1)
        var child = children[childIndex]
        if (child == null) {
            child = OctreeNode(owner, this, childIndex)
            children[childIndex] = child
            childCount++
            owner.registerNodeAtDepth(child, depth)
        }
        val previousLeaf = child.leafNodeCount
        child.addColor(red, green, blue, depth + 1, sampleCount)
        leafNodeCount += child.leafNodeCount - previousLeaf
    }

    fun collectPaletteColors(result: MutableList<Pair<FloatArray, Int>>) {
        if (leafNodeCount == 0) return
        if (childCount == 0) {
            result.add(averageColor to sampleCount)
            return
        }
        for (child in children) child?.collectPaletteColors(result)
    }

    fun mergeChildrenIntoThisNode() {
        if (childCount == 0 || leafNodeCount <= 1) return
        val previousLeaf = leafNodeCount
        children.fill(null)
        childCount = 0
        leafNodeCount = if (sampleCount > 0) 1 else 0
        val reduction = previousLeaf - leafNodeCount
        var parent = parentNode
        while (parent != null) {
            parent.leafNodeCount -= reduction
            parent = parent.parentNode
        }
    }

    fun isAttachedToRoot(): Boolean {
        var current: OctreeNode = this
        while (true) {
            val parent = current.parentNode ?: return true
            if (parent.children[current.indexInParent] !== current) return false
            current = parent
        }
    }
}

private class OctreeQuantizer {
    private val rootNode = OctreeNode(this, null, -1)
    private val nodesByDepth: Array<MutableList<OctreeNode>> =
        Array(MAX_COLOR_DEPTH) { mutableListOf() }

    fun registerNodeAtDepth(node: OctreeNode, depth: Int) {
        nodesByDepth[depth].add(node)
    }

    fun addColor(color: FloatArray, sampleCount: Int) {
        if (sampleCount <= 0) return
        rootNode.addColor(
            color[0].toInt() and 0xff,
            color[1].toInt() and 0xff,
            color[2].toInt() and 0xff,
            0,
            sampleCount,
        )
    }

    fun getPalette(maxColorCount: Int): List<FloatArray> {
        if (maxColorCount <= 0 || rootNode.leafNodeCount == 0) return emptyList()
        val entries = mutableListOf<Pair<FloatArray, Int>>()
        rootNode.collectPaletteColors(entries)
        if (entries.size <= maxColorCount) return entries.map { it.first }
        entries.sortWith(
            compareByDescending<Pair<FloatArray, Int>> { it.second }
                .thenBy { it.first[0] }
                .thenBy { it.first[1] }
                .thenBy { it.first[2] },
        )
        return entries.take(min(maxColorCount, entries.size)).map { it.first }
    }

    fun reduceToColorCount(targetColorCount: Int) {
        if (targetColorCount <= 0) return
        var remaining = rootNode.leafNodeCount - targetColorCount
        if (remaining <= 0) return

        var depth = MAX_COLOR_DEPTH - 2
        while (depth >= 0 && remaining > 0) {
            val nodes = nodesByDepth[depth]
            nodes.sortWith(
                compareBy<OctreeNode> { it.leafNodeCount }.thenBy { it.sampleCount },
            )
            for (candidate in nodes) {
                if (remaining <= 0) break
                if (candidate.childCount == 0) continue
                val reduction = candidate.leafNodeCount - 1
                if (reduction <= 0 || reduction > remaining) continue
                remaining -= reduction
                candidate.mergeChildrenIntoThisNode()
            }
            depth--
        }

        while (rootNode.leafNodeCount > targetColorCount) {
            val candidate = findBestMergeCandidate() ?: break
            candidate.mergeChildrenIntoThisNode()
        }
    }

    private fun findBestMergeCandidate(): OctreeNode? {
        var best: OctreeNode? = null
        var bestReduction = Int.MAX_VALUE
        var bestSamples = Int.MAX_VALUE
        for (depth in MAX_COLOR_DEPTH - 2 downTo 0) {
            for (candidate in nodesByDepth[depth]) {
                if (!candidate.isAttachedToRoot()) continue
                if (candidate.childCount == 0) continue
                val reduction = candidate.leafNodeCount - 1
                if (reduction <= 0) continue
                if (reduction < bestReduction ||
                    (reduction == bestReduction && candidate.sampleCount < bestSamples)
                ) {
                    best = candidate
                    bestReduction = reduction
                    bestSamples = candidate.sampleCount
                }
            }
        }
        return best
    }
}

internal fun createOctTreePalette(
    sourceColors: List<ColorCount>,
    clusterCount: Int,
    themeColor: ThemeColorResult,
    ignoreWhite: Boolean = false,
    intent: String = "accent",
): PaletteResult {
    val quantizer = OctreeQuantizer()
    val effectiveIgnoreWhite = if (sourceColors.size == 1) false else ignoreWhite
    val filtered = sourceColors.filter { entry ->
        val r = entry.color[0].toInt()
        val g = entry.color[1].toInt()
        val b = entry.color[2].toInt()
        if (effectiveIgnoreWhite && r > 250 && g > 250 && b > 250) return@filter false
        if (intent == "dominant") return@filter true
        if (themeColor.colorIsDark) paletteIsDark(entry.color) else paletteIsLight(entry.color)
    }
    val entries = if (filtered.isNotEmpty()) filtered else sourceColors
    for (entry in entries) quantizer.addColor(entry.color, entry.count)
    quantizer.reduceToColorCount(clusterCount)
    val quantized = quantizer.getPalette(clusterCount)

    val palette = if (quantized.size < clusterCount) {
        List(clusterCount) { i ->
            if (quantized.isNotEmpty()) quantized[i % quantized.size].copyOf() else floatArrayOf(0f, 0f, 0f)
        }
    } else {
        quantized
    }
    return PaletteResult(palette, themeColor.colorIsDark, "八叉树")
}

/* ============================== 自动择优 ============================== */

/** 调色板在 LAB 空间到自身质心的平均平方距离，衡量色彩分散程度。 */
private fun calculateSpatialDiversity(palette: List<FloatArray>): Double {
    if (palette.isEmpty()) return 0.0
    val labs = palette.map(::rgbToLab)
    var cl = 0.0
    var ca = 0.0
    var cb = 0.0
    for (lab in labs) {
        cl += lab[0]
        ca += lab[1]
        cb += lab[2]
    }
    cl /= labs.size
    ca /= labs.size
    cb /= labs.size
    var sum = 0.0
    for (lab in labs) {
        val dl = lab[0] - cl
        val da = lab[1] - ca
        val db = lab[2] - cb
        sum += dl * dl + da * da + db * db
    }
    return sum / labs.size
}

private fun countDistinctColors(palette: List<FloatArray>): Int =
    palette.mapTo(HashSet()) { "${it[0]},${it[1]},${it[2]}" }.size

private fun createAutoPalette(
    sourceColors: List<ColorCount>,
    clusterCount: Int,
    ignoreWhite: Boolean = false,
    toLab: Boolean = false,
    useKMeansPlusPlus: Boolean = false,
    intent: String = "accent",
): PaletteResult {
    val random = createRandom(seedFromHistogram(sourceColors))
    val themeColor = createThemeColor(sourceColors, ignoreWhite, toLab, random)
    val kmeans = createKMeansPalette(
        sourceColors, clusterCount, themeColor, ignoreWhite, toLab, useKMeansPlusPlus, intent, random,
    )
    val octtree = createOctTreePalette(sourceColors, clusterCount, themeColor, ignoreWhite, intent)

    val kMeansDistinct = countDistinctColors(kmeans.palette)
    val octTreeDistinct = countDistinctColors(octtree.palette)
    if (kMeansDistinct != octTreeDistinct) {
        return if (kMeansDistinct > octTreeDistinct) kmeans else octtree
    }

    val kMeansDiversity = calculateSpatialDiversity(kmeans.palette)
    val octTreeDiversity = calculateSpatialDiversity(octtree.palette)

    if (intent == "dominant" || kmeans.paletteIsDark) {
        return if (kMeansDiversity >= octTreeDiversity) kmeans else octtree
    }
    return if (kMeansDiversity <= octTreeDiversity || octTreeDiversity == 0.0) kmeans else octtree
}

/**
 * 从封面提取 [clusterCount] 个主色。同步执行：直方图按 64×64 统计，
 * 整个流程在主线程上也只有几毫秒，调用方仍建议放到后台线程。
 */
internal fun extractIsolationPalette(
    source: Bitmap,
    clusterCount: Int = 4,
    algorithm: String = "auto",
): PaletteResult {
    val entries = buildColorHistogram(source)
    if (entries.isEmpty()) {
        return PaletteResult(
            List(clusterCount) { floatArrayOf(0f, 0f, 0f) },
            paletteIsDark = true,
            algo = "空直方图",
        )
    }
    return when (algorithm) {
        "kmeans" -> createKMeansPalette(
            entries, clusterCount, createThemeColor(entries), intent = "dominant",
        )
        "octtree" -> createOctTreePalette(
            entries, clusterCount, createThemeColor(entries, toLab = true), intent = "dominant",
        )
        else -> createAutoPalette(entries, clusterCount, intent = "dominant")
    }
}
