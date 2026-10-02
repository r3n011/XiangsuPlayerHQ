package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.isolation.IsolationBackground

/**
 * 歌词页 / 播放页共用的「绚丽背景」入口（历史上的 Apple Music 风格碎片旋转实现
 * 已整体替换为 AMLL 的 Isolation 流体渐变，见 isolation/IsolationBackground.kt）。
 *
 * API 33+ 走 AGSL 单 pass 着色器，更低版本走 CPU 低分辨率求值 + 双线性放大，
 * 两档共用同一套取色管线与随机布局参数，观感一致。
 */
@Composable
fun AppleMusicRotatingBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = 64.dp,
) {
    IsolationBackground(
        albumArtUri = albumArtUri,
        modifier = modifier,
    )
}
