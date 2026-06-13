package com.theveloper.pixelplay.presentation.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Shape describing star with rounded corners.
 *
 * @param sides number of sides.
 * @param curve value between 0.0 and 1.0 to modify star curve.
 * @param rotation value between 0 and 360 for rotation.
 * @param iterations quality value between 0 and 360.
 */
class RoundedStarShape(
    private val sides: Int,
    private val curve: Double = 0.09,
    private val rotation: Float = 0f,
    iterations: Int = 360,
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val steps = TWO_PI / min(iterations, 360)
    private val rotationDegree = (PI / 180) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            val radius = min(size.height, size.width) * 0.4 * mapRange(1.0, 0.0, 0.5, 1.0, curve)
            val xCenter = size.width * 0.5f
            val yCenter = size.height * 0.5f

            var t = 0.0
            val startX = radius * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
            val startY = radius * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
            moveTo((startX + xCenter).toFloat(), (startY + yCenter).toFloat())
            t += steps

            while (t <= TWO_PI) {
                val x = radius * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
                val y = radius * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
                lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
                t += steps
            }

            val x = radius * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
            val y = radius * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
            lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
            close()
        }
    )

    private fun mapRange(a: Double, b: Double, c: Double, d: Double, x: Double): Double {
        return (x - a) / (b - a) * (d - c) + c
    }
}
