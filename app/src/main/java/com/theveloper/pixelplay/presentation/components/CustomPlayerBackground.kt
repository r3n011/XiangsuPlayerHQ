package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.preferences.PlayerBackgroundMode

/**
 * 自定义播放器背景层。
 * 开关关闭或未选择图片时不绘制任何内容；
 * 打开并已选择图片时，按 [mode] 渲染所选图片（[blurRadius] > 0 时按该半径（dp）模糊），
 * 并叠加可选深色遮罩保证前景可读。
 *  - [PlayerBackgroundMode.Cover]：铺满（裁剪填满，保持比例）
 *  - [PlayerBackgroundMode.Stretch]：拉伸（完全拉伸到区域尺寸）
 */
@Composable
fun CustomPlayerBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    uri: String?,
    mode: PlayerBackgroundMode = PlayerBackgroundMode.Cover,
    blurRadius: Int = 0,
    scrimAlpha: Float = 0f
) {
    if (!enabled || uri.isNullOrBlank()) return
    val context = LocalContext.current
    Box(modifier = modifier) {
        // ⚡ Android 12（API 31）以下没有 RenderEffect，Modifier.blur 是空操作。
        //    低版本交给 SoftBlur：「极小尺寸解码 + 双线性放大」实现软件模糊，全版本可用。
        val needBlur = blurRadius > 0
        val softwareBlur = SoftBlur.needsSoftwareBlur(needBlur)
        val imageRequest = remember(uri, needBlur, softwareBlur) {
            ImageRequest.Builder(context)
                .data(uri)
                .size(SoftBlur.decodeSize(needBlur = needBlur, softBlurSize = SoftBlur.DECODE_SIZE_SOFT))
                .crossfade(true)
                .build()
        }
        val painter = rememberAsyncImagePainter(model = imageRequest)
        val imageModifier =
            if (needBlur && !softwareBlur) {
                Modifier.fillMaxSize().blur(blurRadius.dp)
            } else {
                Modifier.fillMaxSize()
            }
        when (mode) {
            PlayerBackgroundMode.Cover -> Image(
                painter = painter,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
            PlayerBackgroundMode.Stretch -> Image(
                painter = painter,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.FillBounds
            )
        }
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
    }
}
