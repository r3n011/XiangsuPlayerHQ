package com.theveloper.pixelplay.data.preferences

/**
 * 平板/横屏左侧导航栏的形态。
 *
 * 参考 Rhythm 的两套样式：
 * - [FLOATING]：悬浮胶囊（80dp 宽，28dp 圆角，带高度与投影，四周留白）
 * - [DOCKED]：贴边停靠（84dp 宽，直角，贴满整个高度）
 */
object NavRailStyle {
    const val FLOATING = "floating"
    const val DOCKED = "docked"
}
