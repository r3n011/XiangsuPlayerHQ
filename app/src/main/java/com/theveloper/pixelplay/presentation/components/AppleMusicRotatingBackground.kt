package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.preferences.BackgroundStyle
import com.theveloper.pixelplay.presentation.components.isolation.IsolationBackground

/**
 * 歌词页 / 播放页共用的「背景」入口（历史上的 Apple Music 风格碎片旋转实现
 * 已整体替换为 AMLL 的 Isolation 流体渐变，见 isolation/IsolationBackground.kt）。
 *
 * 按 [style] 三选一渲染：
 * - [BackgroundStyle.VIBRANT] 绚丽背景：AGSL 流体渐变（低版本走 Cloudy 模糊封面兜底）；
 * - [BackgroundStyle.PALETTE] 取色背景：静态的封面主色纯色（不流动）；
 * - [BackgroundStyle.SOLID] 纯色背景：不绘制任何背景层，由调用方底下的主题表面色呈现。
 */
@Composable
fun AppleMusicRotatingBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    style: BackgroundStyle = BackgroundStyle.VIBRANT,
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = 64.dp,
) {
    when (style) {
        BackgroundStyle.VIBRANT -> IsolationBackground(
            albumArtUri = albumArtUri,
            modifier = modifier,
        )
        BackgroundStyle.PALETTE -> IsolationBackground(
            albumArtUri = albumArtUri,
            modifier = modifier,
            staticPalette = true,
        )
        // 纯色背景：什么都不画（调用方不渲染此组件也算同一观感）
        BackgroundStyle.SOLID -> Unit
    }
}
