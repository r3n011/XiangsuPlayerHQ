package com.theveloper.pixelplay.data.service.wear

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.theveloper.pixelplay.shared.WearThemePalette
import kotlin.math.max
import kotlin.math.min

internal fun buildWearThemePalette(darkScheme: ColorScheme): WearThemePalette {
    val surfaceContainer = darkScheme.surfaceContainer.toArgb()
    val surfaceContainerLowest = darkScheme.surfaceContainerLowest.toArgb()
    val surfaceContainerLow = darkScheme.surfaceContainerLow.toArgb()
    val surfaceContainerHigh = darkScheme.surfaceContainerHigh.toArgb()
    val surfaceContainerHighest = darkScheme.surfaceContainerHighest.toArgb()

    val playContainer = darkScheme.onPrimaryContainer.toArgb()
    val playContent = darkScheme.primaryContainer.toArgb()
    val transportContainer = darkScheme.primary.toArgb()
    val transportContent = darkScheme.onPrimary.toArgb()
    val chipContainer = ColorUtils.blendARGB(
        darkScheme.secondaryContainer.toArgb(),
        surfaceContainerLow,
        0.28f,
    )

    val gradientTop = ColorUtils.blendARGB(surfaceContainerHigh, playContainer, 0.34f)
    val gradientMiddle = ColorUtils.blendARGB(
        ColorUtils.blendARGB(surfaceContainer, transportContainer, 0.12f),
        AndroidColor.BLACK,
        0.34f,
    )
    val gradientBottom = ColorUtils.blendARGB(
        ColorUtils.blendARGB(surfaceContainerLowest, transportContainer, 0.08f),
        AndroidColor.BLACK,
        0.68f,
    )

    return WearThemePalette(
        gradientTopArgb = gradientTop,
        gradientMiddleArgb = gradientMiddle,
        gradientBottomArgb = gradientBottom,
        surfaceContainerLowestArgb = surfaceContainerLowest,
        surfaceContainerLowArgb = surfaceContainerLow,
        surfaceContainerArgb = surfaceContainer,
        surfaceContainerHighArgb = surfaceContainerHigh,
        surfaceContainerHighestArgb = surfaceContainerHighest,
        textPrimaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurface.toArgb(),
            backgroundColor = gradientMiddle,
        ),
        textSecondaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = gradientBottom,
        ),
        textErrorArgb = 0xFFFFB8C7.toInt(),
        controlContainerArgb = playContainer,
        controlContentArgb = ensureWearReadable(
            preferredColor = playContent,
            backgroundColor = playContainer,
        ),
        controlDisabledContainerArgb = surfaceContainerHighest,
        controlDisabledContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = surfaceContainerHighest,
        ),
        transportContainerArgb = transportContainer,
        transportContentArgb = ensureWearReadable(
            preferredColor = transportContent,
            backgroundColor = transportContainer,
        ),
        chipContainerArgb = chipContainer,
        chipContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSecondaryContainer.toArgb(),
            backgroundColor = chipContainer,
        ),
        favoriteActiveArgb = shiftWearHue(transportContainer, 34f),
        shuffleActiveArgb = shiftWearHue(transportContainer, -72f),
        repeatActiveArgb = shiftWearHue(transportContainer, -22f),
    )
}

internal fun buildWearThemePalette(bitmap: Bitmap): WearThemePalette {
    return buildWearThemePaletteFromSeed(extractWearSeedColor(bitmap))
}

private fun buildWearThemePaletteFromSeed(seedColor: Int): WearThemePalette {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor, hsl)
    hsl[1] = (hsl[1] * 1.18f).coerceIn(0.30f, 0.82f)
    hsl[2] = hsl[2].coerceIn(0.32f, 0.56f)
    val tunedSeed = Color(ColorUtils.HSLToColor(hsl))

    val top = lerp(tunedSeed, Color.Black, 0.30f)
    val middle = lerp(tunedSeed, Color.Black, 0.57f)
    val bottom = lerp(tunedSeed, Color.Black, 0.84f)
    val surfaceBackground = lerp(middle, bottom, 0.58f)
    val surfaceContainerLowest = lerp(surfaceBackground, Color.Black, 0.18f).copy(alpha = 0.96f)
    val surfaceContainerLow = lerp(surfaceBackground, Color.White, 0.06f).copy(alpha = 0.95f)
    val surfaceContainer = lerp(surfaceBackground, Color.White, 0.10f).copy(alpha = 0.95f)
    val surfaceContainerHigh = lerp(surfaceBackground, Color.White, 0.14f).copy(alpha = 0.97f)
    val surfaceContainerHighest = lerp(surfaceBackground, Color.White, 0.20f).copy(alpha = 0.98f)
    val chipContainer = surfaceContainer
    val controlContainer = lerp(surfaceBackground, Color.White, 0.26f).copy(alpha = 0.96f)
    val transportContainer = lerp(surfaceBackground, Color.White, 0.20f).copy(alpha = 0.98f)
    val controlDisabledContainer = surfaceContainerHighest

    return WearThemePalette(
        gradientTopArgb = top.toArgb(),
        gradientMiddleArgb = middle.toArgb(),
        gradientBottomArgb = bottom.toArgb(),
        surfaceContainerLowestArgb = surfaceContainerLowest.toArgb(),
        surfaceContainerLowArgb = surfaceContainerLow.toArgb(),
        surfaceContainerArgb = surfaceContainer.toArgb(),
        surfaceContainerHighArgb = surfaceContainerHigh.toArgb(),
        surfaceContainerHighestArgb = surfaceContainerHighest.toArgb(),
        textPrimaryArgb = Color(0xFFF7F2FF).toArgb(),
        textSecondaryArgb = Color(0xFFE8DEF8).toArgb(),
        textErrorArgb = 0xFFFFB8C7.toInt(),
        controlContainerArgb = controlContainer.toArgb(),
        controlContentArgb = bestContrastWearContent(controlContainer),
        controlDisabledContainerArgb = controlDisabledContainer.toArgb(),
        controlDisabledContentArgb = bestContrastWearContent(controlDisabledContainer),
        transportContainerArgb = transportContainer.toArgb(),
        transportContentArgb = bestContrastWearContent(transportContainer),
        chipContainerArgb = chipContainer.toArgb(),
        chipContentArgb = Color(0xFFF2EBFF).toArgb(),
        favoriteActiveArgb = buildWearSeedAccent(seedColor, 34f),
        shuffleActiveArgb = buildWearSeedAccent(seedColor, -72f),
        repeatActiveArgb = buildWearSeedAccent(seedColor, -22f),
    )
}

private fun shiftWearHue(color: Int, hueShift: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * 1.18f).coerceIn(0.42f, 0.92f)
    hsl[2] = (hsl[2] + 0.08f).coerceIn(0.34f, 0.78f)
    return ColorUtils.HSLToColor(hsl)
}

private fun ensureWearReadable(preferredColor: Int, backgroundColor: Int): Int {
    val opaqueBackground = if (AndroidColor.alpha(backgroundColor) >= 255) {
        backgroundColor
    } else {
        ColorUtils.compositeColors(backgroundColor, AndroidColor.BLACK)
    }
    val preferredContrast = ColorUtils.calculateContrast(preferredColor, opaqueBackground)
    if (preferredContrast >= 3.0) return preferredColor

    val light = 0xFFF6F2FF.toInt()
    val dark = 0xFF17141E.toInt()
    val lightContrast = ColorUtils.calculateContrast(light, opaqueBackground)
    val darkContrast = ColorUtils.calculateContrast(dark, opaqueBackground)
    return if (lightContrast >= darkContrast) light else dark
}

private fun buildWearSeedAccent(seedColor: Int, hueShift: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor, hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * 1.22f).coerceIn(0.46f, 0.92f)
    hsl[2] = (hsl[2] + 0.08f).coerceIn(0.40f, 0.72f)
    return ColorUtils.HSLToColor(hsl)
}

private fun bestContrastWearContent(background: Color): Int {
    val light = Color(0xFFF6F2FF)
    val dark = Color(0xFF17141E)
    val opaqueBackgroundArgb = if (background.alpha >= 0.999f) {
        background.toArgb()
    } else {
        ColorUtils.compositeColors(background.toArgb(), Color.Black.toArgb())
    }
    val lightContrast = ColorUtils.calculateContrast(light.toArgb(), opaqueBackgroundArgb)
    val darkContrast = ColorUtils.calculateContrast(dark.toArgb(), opaqueBackgroundArgb)
    return if (lightContrast >= darkContrast) light.toArgb() else dark.toArgb()
}

private fun extractWearSeedColor(bitmap: Bitmap): Int {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return Color(0xFF6C3AD8).toArgb()

    val step = max(1, min(width, height) / 24)
    var redSum = 0L
    var greenSum = 0L
    var blueSum = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha >= 28) {
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                if (red + green + blue > 36) {
                    redSum += red
                    greenSum += green
                    blueSum += blue
                    count++
                }
            }
            x += step
        }
        y += step
    }

    if (count == 0L) return Color(0xFF6C3AD8).toArgb()
    return AndroidColor.rgb(
        (redSum / count).toInt(),
        (greenSum / count).toInt(),
        (blueSum / count).toInt(),
    )
}
