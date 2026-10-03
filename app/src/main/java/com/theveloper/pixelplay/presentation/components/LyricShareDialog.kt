package com.theveloper.pixelplay.presentation.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.isolation.extractIsolationPalette
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌词长按分享（参考 MeloX 的 `MeloXLyricShareDialog`）：
 * 全屏面板里逐行多选（默认选中长按的那一行），支持「分享文本」与「生成图片」。
 *
 * 图片是 Canvas 直接绘制的一张 1080 宽卡片（封面 + 选中的歌词 + 歌名歌手），
 * 写到 cacheDir 后经 FileProvider 分享（`file_paths.xml` 已包含 cache-path）。
 */
@Composable
internal fun LyricShareDialog(
    title: String,
    artist: String,
    artworkUrl: String?,
    lines: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(lines, initialIndex) {
        mutableStateOf(setOf(initialIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))))
    }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ⚡ 连贯的进出动画：进入时淡入 + 轻微上浮（带一点回弹），退出时反向播完再真正关闭。
    //    之前 Dialog 直接硬切，观感很生硬。
    val appear = remember { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { 26.dp.toPx() }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    // 关闭统一走这里：先播退出动画，播完再通知上层销毁
    val dismissAnimated: () -> Unit = {
        scope.launch {
            appear.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * slidePx
                }
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏：取消 / 标题 / 全选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier
                        .clickable(onClick = dismissAnimated)
                        .padding(8.dp)
                )
                Text(
                    text = stringResource(R.string.lyric_share_title),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.lyric_share_select_all),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier
                        .clickable { selected = lines.indices.toSet() }
                        .padding(8.dp)
                )
            }

            // 歌曲信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                        fontFamily = GoogleSansRounded,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 选中行数变化时用淡入淡出过渡（AnimatedContent 会把旧值淡出、新值淡入）
                    AnimatedContent(
                        targetState = selected.size,
                        label = "shareSelectedCount"
                    ) { count ->
                        Text(
                            text = stringResource(R.string.lyric_share_selected_count, count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // 歌词多选
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(lines, key = { index, line -> "share-$index-$line" }) { index, line ->
                    val chosen = index in selected
                    // 选中态用颜色过渡而不是硬切，勾选 / 取消更顺滑
                    val rowColor by animateColorAsState(
                        targetValue = if (chosen) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "shareRowColor"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(rowColor)
                            .clickable { selected = if (chosen) selected - index else selected + index }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = line,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = GoogleSansRounded,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val chosenLines = selected.sorted().mapNotNull(lines::getOrNull)
                Button(
                    onClick = {
                        runCatching { shareLyricText(context, title, artist, chosenLines) }
                            .onFailure { error = it.message }
                    },
                    enabled = chosenLines.isNotEmpty() && !generating,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyric_share_text),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = {
                        generating = true
                        error = null
                        scope.launch {
                            runCatching {
                                shareLyricImage(context, title, artist, artworkUrl, chosenLines)
                            }.onFailure { error = it.message ?: "歌词图片生成失败" }
                            generating = false
                        }
                    },
                    enabled = chosenLines.isNotEmpty() && !generating,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // 「生成图片 / 生成中」文案切换用淡入淡出，而不是硬切
                    AnimatedContent(targetState = generating, label = "shareGenerating") { gen ->
                        Text(
                            text = stringResource(
                                if (gen) R.string.lyric_share_generating else R.string.lyric_share_image
                            ),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** 分享歌词文本：选中行 + `——《歌名》 · 歌手` */
internal fun shareLyricText(context: Context, title: String, artist: String, lines: List<String>) {
    require(lines.isNotEmpty())
    val text = buildString {
        append(lines.joinToString("\n"))
        append("\n——《$title》")
        if (artist.isNotBlank()) append(" · $artist")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/**
 * 生成歌词图片并分享：1080 宽卡片 = 深色渐变底 + 封面圆角方块 + 选中歌词 + 歌名/歌手。
 * 图片写到 cacheDir，经 FileProvider 分享。
 */
internal suspend fun shareLyricImage(
    context: Context,
    title: String,
    artist: String,
    artworkUrl: String?,
    lines: List<String>,
) {
    require(lines.isNotEmpty()) { "请至少选择一行歌词" }
    val uri = withContext(Dispatchers.IO) {
        val width = 1080
        val margin = 72f
        val headerHeight = 380f
        val bodyTextSize = 46f
        val lineSpacing = 22f
        val maxBodyWidth = width - margin * 2

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bodyTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // 先按宽度折行，算出总高度
        val wrapped = lines.flatMap { line -> wrapText(line, bodyPaint, maxBodyWidth) }
        val bodyHeight = wrapped.size * (bodyTextSize + lineSpacing)
        val height = (headerHeight + bodyHeight + 220f).toInt().coerceIn(720, 4096)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 封面（Coil 缓存解码，失败则跳过）
        val cover = loadArtworkBitmap(context, artworkUrl, 320)

        // ⚡ 跟随封面取色：复用 Isolation 背景那套调色板（同一模块 internal 可直接调用），
        //    取到的颜色先整体压暗再画，所以任何封面色（包括浅色封面）下歌词都保持可读。
        val palette = cover?.let { bmp ->
            runCatching { extractIsolationPalette(bmp).palette }.getOrNull()
        }.orEmpty()
        val baseColor = palette.firstOrNull()?.let { darkenRgb(it, 0.30f) }
            ?: Color.parseColor("#141419")
        canvas.drawColor(baseColor)
        // 压暗后的大色斑，营造跟封面同源的氛围渐变
        palette.drop(1).take(3).forEachIndexed { index, rgb ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkenRgb(rgb, 0.50f)
                alpha = 150
            }
            val cx = width * ((index % 2) + 0.5f) / 2f
            val cy = height * ((index / 2) + 0.5f) / 2f
            canvas.drawCircle(cx, cy, width * 0.62f, paint)
        }
        // 再叠一层黑色垂直渐变，保证文字对比度（取色只影响氛围，不影响可读性）
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#5C000000"), Color.parseColor("#99000000"),
                    Shader.TileMode.CLAMP
                )
            }
        )

        var textTop = margin + 40f
        if (cover != null) {
            val coverSize = 240f
            val src = RectF(0f, 0f, cover.width.toFloat(), cover.height.toFloat())
            val dst = RectF(margin, textTop, margin + coverSize, textTop + coverSize)
            canvas.save()
            canvas.clipPath(
                android.graphics.Path().apply {
                    addRoundRect(
                        android.graphics.RectF(dst),
                        36f, 36f, android.graphics.Path.Direction.CW
                    )
                }
            )
            canvas.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            textTop += coverSize + 56f
        } else {
            textTop += 24f
        }

        // 歌名 / 歌手
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, margin, textTop, titlePaint)
        textTop += 62f
        if (artist.isNotBlank()) {
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B0FFFFFF")
                textSize = 38f
            }
            canvas.drawText(artist, margin, textTop, artistPaint)
            textTop += 56f
        }

        // 歌词
        textTop += 40f
        wrapped.forEach { line ->
            textTop += bodyTextSize
            canvas.drawText(line, margin, textTop, bodyPaint)
            textTop += lineSpacing
        }

        val dir = File(context.cacheDir, "lyric_share").apply { mkdirs() }
        val file = File(dir, "lyric_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()

        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/** 把调色板里的 sRGB（0..255）压暗成绘制用色：取色只负责氛围，可读性靠压暗 + 黑色渐变兜底 */
private fun darkenRgb(rgb: FloatArray, factor: Float): Int {
    val r = (rgb.getOrElse(0) { 0f } * factor).coerceIn(0f, 255f).toInt()
    val g = (rgb.getOrElse(1) { 0f } * factor).coerceIn(0f, 255f).toInt()
    val b = (rgb.getOrElse(2) { 0f } * factor).coerceIn(0f, 255f).toInt()
    return Color.rgb(r, g, b)
}

/** 按像素宽度折行（中英文都按字符宽度累计，够用且不依赖排版引擎） */
private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {    if (text.isBlank()) return listOf("")
    val result = mutableListOf<String>()
    val builder = StringBuilder()
    for (char in text) {
        val candidate = builder.toString() + char
        if (paint.measureText(candidate) > maxWidth && builder.isNotEmpty()) {
            result += builder.toString()
            builder.clear()
        }
        builder.append(char)
    }
    if (builder.isNotEmpty()) result += builder.toString()
    return result
}

/** 从 Coil 缓存解码封面位图（失败返回 null，不影响分享） */
private suspend fun loadArtworkBitmap(context: Context, url: String?, sizePx: Int): Bitmap? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(sizePx, sizePx)
            .build()
        (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }.getOrNull()
}
