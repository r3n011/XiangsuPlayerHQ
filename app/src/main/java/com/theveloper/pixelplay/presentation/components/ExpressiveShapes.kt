package com.theveloper.pixelplay.presentation.components

import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.min

/**
 * Rhythm 同款 Material 3 Expressive 形状（对齐 Rhythm `ExpressiveShapeProvider` 的默认值）。
 *
 * Rhythm 默认开启 `expressiveShapesEnabled`，播放器封面用 BUN、上一/下一曲用 COOKIE_12、
 * 迷你播放器封面用 COOKIE_4。此前我们只实现了「圆形/圆角矩形」，这是两套 Expressive
 * 界面观感差异最大的来源之一。
 */
object ExpressiveShapes {

    /** 播放器大封面（Rhythm：PLAYER_ART = BUN） */
    val PlayerArt: Shape = materialShape(MaterialShapes.Bun)

    /** 播放器上一曲/下一曲圆钮（Rhythm：PLAYER_CONTROLS = COOKIE_12） */
    val PlayerControls: Shape = materialShape(MaterialShapes.Cookie12Sided)

    /** 迷你播放器封面（Rhythm：MINI_PLAYER = COOKIE_4） */
    val MiniPlayerArt: Shape = materialShape(MaterialShapes.Cookie4Sided)

    /** 把 MaterialShapes 的 RoundedPolygon 转成 Compose Shape（等比缩放居中到目标尺寸） */
    fun materialShape(polygon: RoundedPolygon): Shape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val target = min(size.width, size.height)
            if (target <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            val raw = polygon.toPath().asComposePath()
            val bounds = raw.getBounds()
            if (bounds.width <= 0f || bounds.height <= 0f) return Outline.Generic(raw)
            val scale = target / maxOf(bounds.width, bounds.height)
            val matrix = Matrix().apply {
                translate(
                    (size.width - bounds.width * scale) / 2f - bounds.left * scale,
                    (size.height - bounds.height * scale) / 2f - bounds.top * scale,
                )
                scale(scale, scale)
            }
            raw.transform(matrix)
            return Outline.Generic(Path().apply { addPath(raw) })
        }
    }
}
