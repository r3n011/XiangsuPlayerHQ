@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Rhythm 同款 Material 3 Expressive 形状（对齐 Rhythm `ExpressiveShapeProvider`）。
 *
 * 默认值：播放器封面 BUN、上一/下一曲 COOKIE_12、迷你播放器封面 COOKIE_4。
 * 用户可在「设置 → 外观 → 播放器外观 → 图形库」中逐项切换，持久化在 DataStore。
 */
object ExpressiveShapes {

    const val DEFAULT_PLAYER_ART = "BUN"
    const val DEFAULT_PLAYER_CONTROLS = "COOKIE_12"
    const val DEFAULT_MINI_PLAYER = "COOKIE_4"

    // ⚠️ 注意属性初始化顺序：下面三个默认 Shape 会调用 shapeFor()，
    //    而 shapeFor() 依赖 polygons；因此 polygons 必须先声明（Kotlin 按声明顺序初始化，
    //    否则会触发 ExceptionInInitializerError）。
    private val polygons: Map<String, RoundedPolygon> = mapOf(
        "CIRCLE" to MaterialShapes.Circle,
        "SQUARE" to MaterialShapes.Square,
        "PENTAGON" to MaterialShapes.Pentagon,
        "DIAMOND" to MaterialShapes.Diamond,
        "FLOWER" to MaterialShapes.Flower,
        "CLOVER_4" to MaterialShapes.Clover4Leaf,
        "CLOVER_8" to MaterialShapes.Clover8Leaf,
        "HEART" to MaterialShapes.Heart,
        "BOOM" to MaterialShapes.Boom,
        "SOFT_BOOM" to MaterialShapes.SoftBoom,
        "COOKIE_4" to MaterialShapes.Cookie4Sided,
        "COOKIE_6" to MaterialShapes.Cookie6Sided,
        "COOKIE_9" to MaterialShapes.Cookie9Sided,
        "COOKIE_12" to MaterialShapes.Cookie12Sided,
        "BUN" to MaterialShapes.Bun,
        "GEM" to MaterialShapes.Gem,
        "PIXEL_CIRCLE" to MaterialShapes.PixelCircle
    )

    /** 播放器大封面（Rhythm：PLAYER_ART = BUN） */
    val PlayerArt: Shape = shapeFor(DEFAULT_PLAYER_ART)

    /** 播放器上一曲/下一曲圆钮（Rhythm：PLAYER_CONTROLS = COOKIE_12） */
    val PlayerControls: Shape = shapeFor(DEFAULT_PLAYER_CONTROLS)

    /** 迷你播放器封面（Rhythm：MINI_PLAYER = COOKIE_4） */
    val MiniPlayerArt: Shape = shapeFor(DEFAULT_MINI_PLAYER)

    /**
     * 图形库预设：预设键 → (播放器封面, 控制按钮, 迷你播放器封面)。
     * 与设置页的「预设」入口一一对应。
     */
    const val PRESET_RHYTHM = "rhythm"
    const val PRESET_SOFT = "soft"
    const val PRESET_GEOMETRIC = "geometric"
    const val PRESET_FLORAL = "floral"

    private val presetMap: Map<String, Triple<String, String, String>> = mapOf(
        PRESET_RHYTHM to Triple(DEFAULT_PLAYER_ART, DEFAULT_PLAYER_CONTROLS, DEFAULT_MINI_PLAYER),
        PRESET_SOFT to Triple("SOFT_BOOM", "COOKIE_9", "CLOVER_4"),
        PRESET_GEOMETRIC to Triple("DIAMOND", "GEM", "PENTAGON"),
        PRESET_FLORAL to Triple("FLOWER", "CLOVER_4", "CLOVER_8")
    )

    val presetKeys: List<String> = listOf(PRESET_RHYTHM, PRESET_SOFT, PRESET_GEOMETRIC, PRESET_FLORAL)

    fun presetShapes(presetKey: String): Triple<String, String, String> =
        presetMap[presetKey] ?: presetMap.getValue(PRESET_RHYTHM)

    /** 图形库展示顺序（与文案资源 shape_name_* 对应）。 */
    val availableShapeIds: List<String> = listOf(
        "CIRCLE", "SQUARE", "PENTAGON", "DIAMOND",
        "FLOWER", "CLOVER_4", "CLOVER_8", "HEART",
        "BOOM", "SOFT_BOOM",
        "COOKIE_4", "COOKIE_6", "COOKIE_9", "COOKIE_12",
        "BUN", "GEM", "PIXEL_CIRCLE"
    )

    fun isValidShapeId(shapeId: String?): Boolean = shapeId != null && polygons.containsKey(shapeId)

    /** 把存储的图形 ID 解析成 Shape；非法/旧 ID 自动回落到目标默认形状。 */
    fun shapeFor(shapeId: String?, fallbackId: String = DEFAULT_PLAYER_ART): Shape {
        val polygon = polygons[shapeId] ?: polygons[fallbackId] ?: polygons.getValue(DEFAULT_PLAYER_ART)
        return materialShape(polygon)
    }

    /** 设置页预览用：按 ID 取形状（组合内 remember 缓存）。 */
    @Composable
    fun rememberShape(shapeId: String?): Shape =
        remember(shapeId) { shapeFor(shapeId) }

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
