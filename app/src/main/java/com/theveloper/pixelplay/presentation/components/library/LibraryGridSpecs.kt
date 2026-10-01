package com.theveloper.pixelplay.presentation.components.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 媒体库的自适应布局规格。
 *
 * 背景：此前媒体库所有网格都写死 `GridCells.Fixed(2)`，宽屏 / 平板上横向利用率很低。
 * 这里统一按屏幕宽度决定列数：手机保持原观感，宽屏横向显示更多内容。
 *
 * 说明：屏幕宽度沿用项目既有做法 `LocalConfiguration.screenWidthDp`
 * （与 LibraryScreen 中 Tab 切换面板的判定保持一致）。
 */

/** 行式列表在宽屏下拆成的列数（1 = 单列，保持手机观感） */
@Composable
@ReadOnlyComposable
fun rememberLibraryListColumnCount(
    minColumnWidth: Dp = LibraryListMinColumnWidth,
    maxColumns: Int = 3
): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return (screenWidthDp / minColumnWidth.value).toInt().coerceIn(1, maxColumns)
}

/** 行式列表的分栏断点：低于该宽度不拆分，保持单列 */
val LibraryListMinColumnWidth: Dp = 400.dp

/**
 * 封面类内容（专辑 / 艺术家 / 歌单 / 文件夹）的网格列数。
 *
 * 说明：这里用屏幕宽度换算列数并**保证手机竖屏至少 2 列**（与原本写死的 `Fixed(2)` 观感一致），
 * 宽屏则按 [minItemWidth] 依次增加列数，最多 [maxColumns] 列，避免平板上封面过小。
 */
@Composable
@ReadOnlyComposable
fun rememberLibraryCoverGridColumns(
    minItemWidth: Dp = 170.dp,
    minColumns: Int = 2,
    maxColumns: Int = 6
): GridCells {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fit = (screenWidthDp / minItemWidth.value).toInt()
    return GridCells.Fixed(fit.coerceIn(minColumns, maxColumns))
}

/**
 * 行式列表（歌曲 / 收藏 / 历史 / 艺术家）在宽屏下拆成多列用的网格列。
 * 手机宽度下返回 `GridCells.Fixed(1)`，观感与原来的单列 LazyColumn 一致。
 */
@Composable
@ReadOnlyComposable
fun rememberLibraryListGridCells(
    minColumnWidth: Dp = LibraryListMinColumnWidth,
    maxColumns: Int = 3
): GridCells = GridCells.Fixed(
    rememberLibraryListColumnCount(minColumnWidth = minColumnWidth, maxColumns = maxColumns)
)
