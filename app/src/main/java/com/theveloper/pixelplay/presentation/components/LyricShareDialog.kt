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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.isolation.IsolationBackgroundState
import com.theveloper.pixelplay.presentation.components.isolation.IsolationCpuRenderer
import com.theveloper.pixelplay.presentation.components.isolation.extractIsolationPalette
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 歌词长按分享（参考 MeloX 的 `MeloXLyricShareDialog`）：
 * 全屏面板里逐行多选（默认选中长按的那一行），支持「分享文本」与「生成图片」。
 *
 * 图片是 Canvas 直接绘制的一张 1080 宽卡片（封面 + 选中的歌词 + 歌名歌手），
 * 背景用与播放器「绚丽背景」相同的 Isolation 流体渐变算法生成。
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
    /** 长按那一行在**窗口坐标**里的位置：用于把它「飞」到面板里对应行的位置（共享元素式衔接） */
    originBounds: androidx.compose.ui.geometry.Rect? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(lines, initialIndex) {
        mutableStateOf(setOf(initialIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))))
    }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ⚡ 面板从**下往上**滑入（起始位置 = 面板自身高度，即屏幕下方），而不是原地淡入上浮。
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }
    // 关闭统一走这里：先播退出动画，播完再通知上层销毁
    val dismissAnimated: () -> Unit = {
        scope.launch {
            appear.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            onDismiss()
        }
    }

    // ⚡ 共享元素式的「落位」：长按的那一行先按它在歌词列表里的位置与大小出现，
    //    再飞到它在本面板里的行位置 —— 与面板上滑同时进行，接上「从歌词里拽出来」的连续感。
    var landedBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val landing = remember { Animatable(1f) }
    LaunchedEffect(landedBounds, originBounds) {
        val ob = originBounds
        val tb = landedBounds
        if (ob == null || tb == null || tb.height <= 0f) {
            landing.snapTo(1f)
            return@LaunchedEffect
        }
        landing.snapTo(0f)
        landing.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    // ⚡ 面板高度（用于「从下往上滑入」与落位时的位移补偿）
    val panelHeightPx = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toFloat()
    }
    // 面板当前的滑动位移：面板内的行在窗口里的位置 = 静止位置 + 这个位移。
    // 落位动画要把「行在窗口里的目标位置」减去它，才能得到与面板滑动无关的静止位置。
    val panelShift = (1f - appear.value) * panelHeightPx

    Dialog(
        onDismissRequest = dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    // 起始位置 = 面板高度（屏幕下方），即整块面板从下往上推入
                    translationY = (1f - appear.value) * size.height
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

            // 歌词多选。列表直接定位到长按那一行 —— 这样「落位」的目标位置就在可视区里，
            // 那句歌词才会准确地落到面板中它自己的位置上。
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(lines, key = { index, line -> "share-$index-$line" }) { index, line ->
                    val chosen = index in selected
                    val isLandingRow = index == initialIndex
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
                            // 先量出本行在窗口里的位置（未做位移前的真实位置），供落位动画用
                            .then(
                                if (isLandingRow) {
                                    Modifier.onGloballyPositioned { coords ->
                                        val rect = coords.boundsInWindow()
                                        if (landedBounds != rect) landedBounds = rect
                                    }
                                } else Modifier
                            )
                            // 落位期间本行淡入（「英雄元素」飞到这里后接管）
                            .then(
                                if (isLandingRow && originBounds != null && landedBounds != null) {
                                    Modifier.graphicsLayer { alpha = landing.value }
                                } else Modifier
                            )
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

        // ⚡ 落位动画的「英雄元素」：把长按的那句歌词画在**面板之上**（不受 LazyColumn 裁剪），
        //    从它在歌词列表里的位置/大小，连续地飞到面板中对应行的位置；面板同时从下往上滑入。
        //    于是视觉上就是「那句歌词被压下去、被拽出来，正好落进面板里的那一行」。
        val heroOrigin = originBounds
        val heroTarget = landedBounds
        val heroP = landing.value
        if (heroOrigin != null && heroTarget != null && heroTarget.height > 0f && heroP < 1f) {
            // 行的「静止位置」= 当前窗口位置 - 面板滑动位移（与面板滑动无关）
            val targetRestTop = heroTarget.top - panelShift
            val heroTop = heroOrigin.top + (targetRestTop - heroOrigin.top) * heroP
            val heroLeft = heroOrigin.left + (heroTarget.left - heroOrigin.left) * heroP
            val heroScale0 = (heroOrigin.height / heroTarget.height).coerceIn(0.5f, 2.5f)
            val heroScale = heroScale0 + (1f - heroScale0) * heroP
            Text(
                text = lines.getOrNull(initialIndex).orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(with(LocalDensity.current) { heroTarget.width.toDp() })
                    .offset { IntOffset(heroLeft.roundToInt(), heroTop.roundToInt()) }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = heroScale
                        scaleY = heroScale
                        // 末段淡出，交回面板里真实的那一行
                        alpha = 1f - heroP
                    }
            )
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

        // ⚡ 背景用与播放器「绚丽背景」**完全相同的生成算法**：AMLL Isolation 流体渐变。
        //    直接复用 IsolationBackgroundState（随机布局 + OkLab 四色过渡）与
        //    IsolationCpuRenderer（AGSL 路径的 CPU 等价实现，App 内低版本设备走的就是它），
        //    而不是自己拿调色板画几个圆。与 App 内做法一致：低分辨率求值 + 双线性放大
        //    （Isolation 是极低频渐变，放大几乎无损），避免 1080×N 逐像素求值过慢。
        val palette = cover?.let { bmp ->
            runCatching { extractIsolationPalette(bmp).palette }.getOrNull()
        }.orEmpty()
        run {
            val isoState = IsolationBackgroundState(lightWave = true, dithering = true)
            // applyPalette 需要 4 个主色（内部按 paletteOrder 索引 0..3），不足时回退默认配色
            if (palette.size >= 4) {
                isoState.rollRandomParameters()
                isoState.applyPalette(palette)
            } else {
                isoState.rollRandomParameters()
                isoState.applyDefaultColors()
            }
            // 调色板过渡是逐帧插值的：导出静态图时先把它推到终点
            repeat(90) { isoState.updateColorBuffer(16f) }

            val longSide = 256
            val scale = longSide / maxOf(width, height).toFloat()
            val lowW = maxOf(8, (width * scale).toInt())
            val lowH = maxOf(8, (height * scale).toInt())
            val pixels = IntArray(lowW * lowH)
            IsolationCpuRenderer.render(
                colorBuffer = isoState.colorBuffer,
                randomValues = isoState.randomValues,
                flowParams = isoState.flowParams,
                angleJitter = isoState.angleJitter,
                lightWave = true,
                dithering = true,
                bass = 0f,
                timeSec = SHARE_BACKGROUND_TIME_SEC,
                width = lowW,
                height = lowH,
                outPixels = pixels,
            )
            val lowBitmap = Bitmap.createBitmap(lowW, lowH, Bitmap.Config.ARGB_8888)
            lowBitmap.setPixels(pixels, 0, lowW, 0, 0, lowW, lowH)
            canvas.drawBitmap(
                lowBitmap,
                null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            lowBitmap.recycle()
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

/**
 * 导出分享图时 Isolation 背景的采样时刻（秒）。
 * Isolation 是持续流动的，静态图取一个固定的、形态舒展的时间点即可。
 */
private const val SHARE_BACKGROUND_TIME_SEC = 12f

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
