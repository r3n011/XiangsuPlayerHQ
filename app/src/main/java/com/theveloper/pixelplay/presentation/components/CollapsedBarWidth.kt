package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 折叠态浮层（mini player / 悬浮底栏）的宽度与对齐规则 —— **两处必须共用这一份**，
 * 否则底栏会比 mini player 宽出一截、左右边缘也对不齐（用户反馈过）。
 *
 * 规则（与 mini player 原有实现一致）：
 * - 「宽屏（≥600dp）+ 横屏」时：最大宽度 [CollapsedBarWidth.maxWidth] = 520dp，并**右对齐**
 *   （多余空间留在左侧导航栏那一侧）；
 * - 竖屏（含平板竖屏）不限宽、居中铺满。
 *
 * ⚠️ 宽度取自 [LocalWindowInfo] 的真实窗口尺寸，而不是 LocalConfiguration：
 * 分屏 / 小窗 / 旋转后 Configuration 可能不更新。
 */
@Immutable
data class CollapsedBarWidth(
    val limitWidth: Boolean,
    val maxWidth: Dp = 520.dp,
    val alignEnd: Boolean = false,
)

@Composable
fun rememberCollapsedBarWidth(): CollapsedBarWidth {
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val isWideScreen = with(density) { windowSize.width.toDp() } >= 600.dp
    val isLandscape = windowSize.width > windowSize.height
    val limit = isWideScreen && isLandscape
    return remember(limit) {
        CollapsedBarWidth(limitWidth = limit, alignEnd = limit)
    }
}
