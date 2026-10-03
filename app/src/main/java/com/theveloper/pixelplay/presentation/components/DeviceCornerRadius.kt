package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设备屏幕圆角半径（dp）。
 *
 * Android 12（API 31）起才有 `Display.getRoundedCorner`，读不到或更低版本一律返回 0 ——
 * 也就是「方角」。
 *
 * 全屏覆盖层（歌词页 / 评论页等）应该用它来裁根布局：
 * - 设备本身是圆角屏 → 内容正好贴合屏幕圆角；
 * - 设备四角是直角（老机型 / 非全面屏）→ 0dp，背景铺满到边角，
 *   不会出现「写死 32dp 圆角把四角切掉、露出窗口底色」的四个白角。
 */
@Composable
internal fun rememberDeviceCornerRadius(): Dp {
    val context = LocalContext.current
    return remember(context) {
        var radiusDp = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            radiusDp = runCatching {
                val radiusPx = context.display
                    ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
                radiusPx / context.resources.displayMetrics.density
            }.getOrDefault(0f)
        }
        radiusDp.dp
    }
}
