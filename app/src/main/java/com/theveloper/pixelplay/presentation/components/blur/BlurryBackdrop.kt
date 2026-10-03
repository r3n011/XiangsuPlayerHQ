package com.theveloper.pixelplay.presentation.components.blur

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import jp.wasabeef.blurry.Blurry
import timber.log.Timber

/**
 * 低版本（API < 31）**真高斯模糊**背景：封面位图 → [Blurry] 模糊 → 铺满。
 *
 * 为什么用 Blurry：Compose 的 `Modifier.blur` 依赖 Android 12 才有的 `RenderEffect`，
 * 低版本是空操作；而 Blurry 自带纯 Java 的 stack-blur 实现（不依赖 RenderEffect / RenderScript），
 * 全版本可用，糊出来的是真正的高斯感，而不是「极小尺寸解码 + 放大」那种马赛克。
 *
 * ⚠️ Blurry 4.0.1 的公开 API 只能把结果画进 `ImageView`（`into()` / `onto()`），
 *    没有同步取 Bitmap 的入口 —— 所以这里用 [AndroidView] 托管一个 ImageView 承载模糊结果。
 *    模糊过程走 Blurry 的 `async()`（后台线程），不会卡主线程。
 *
 * 高版本（API ≥ 31）不要用这个，继续走原生 `Modifier.blur` / RenderEffect。
 */
@Composable
fun BlurryBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
    radius: Int = 25,
    sampling: Int = 2,
    /** 模糊前解码的最长边（px）：模糊会抹掉细节，256 足够且省内存 */
    decodeSizePx: Int = 256,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var bitmap by remember(model) { mutableStateOf<Bitmap?>(null) }

    // ① 把封面解码成一张小位图（Blurry 从 Bitmap 出发，省得它自己去截 View）
    LaunchedEffect(model) {
        bitmap = null
        if (model == null) return@LaunchedEffect
        bitmap = runCatching {
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(decodeSizePx)
                .allowHardware(false) // 模糊要读像素，必须软件位图
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }.onFailure { Timber.w(it, "BlurryBackdrop: 加载封面失败") }.getOrNull()
    }

    val source = bitmap ?: return

    // ② Blurry 模糊后画进托管的 ImageView
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                // 背景层不参与交互
                isClickable = false
            }
        },
        update = { view ->
            view.scaleType = when (contentScale) {
                ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                else -> ImageView.ScaleType.CENTER_CROP
            }
            // ⚡ 模糊引擎改成纯 Kotlin 的盒式模糊（StackBlur）：Blurry 内部走 RenderScript，
            //    RS 在 Android 12+ 已废弃、部分 ROM 驱动不可用会直接失败 —— 低版本歌词背景
            //    因此完全没有模糊。纯 CPU 实现全版本可用，且这里已经先解码成小图，开销很小。
            if (view.tag !== source) {
                view.tag = source
                runCatching {
                    val blurred = StackBlur.blur(source, radius.coerceIn(1, 25), sampling.coerceAtLeast(1))
                    view.setImageBitmap(blurred)
                }.onFailure { Timber.w(it, "BlurryBackdrop: 软件模糊失败") }
            }
        },
    )
}
