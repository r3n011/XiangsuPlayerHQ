package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.glyph.glyphChar
import com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.ShapeCache
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

/**
 * Nothing Phone Glyph Matrix 圆形 LED 预览组件（频谱类单色，封面类带原色）
 *
 * 所有 LED 在频谱类样式下均为白色、仅亮度不同，方便模拟真实 LED 点阵。
 * Phone (3) 25×25 和 Phone (4a) Pro 13×13 合并在一个卡片中。
 *
 * 频谱类样式（VISUALIZER / WAVEFORM / RING / PULSE）由真实音频频谱 [AudioVisualizer]
 * 驱动；无声时回退为柔和相位动画，保证预览不空。
 * 封面类样式（ARTWORK）用点阵显示当前歌曲专辑封面，播放时旋转、暂停冻结，
 * 无封面时显示应用 Logo；无声回退为 Logo。
 */
@Composable
fun GlyphMatrixPreview(
    displayMode: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glyph_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val appContext = LocalContext.current.applicationContext

    // 通过 Hilt EntryPoint 拿到真实频谱采集器（谐波采样已接在播放引擎里）
    val visualizer = remember(appContext) {
        runCatching {
            EntryPointAccessors.fromApplication(appContext, VisualizerEntry::class.java).audioVisualizer()
        }.getOrNull()
    }
    val emptyLevels = remember { FloatArray(AudioVisualizer.BANDS) }
    val levels: FloatArray = if (visualizer != null) {
        val s by visualizer.levels.collectAsState(initial = emptyLevels)
        s
    } else emptyLevels

    // 通过 EntryPoint 拿到播放状态（当前歌曲 + 是否正在播放），用于封面样式
    val playbackHolder = remember(appContext) {
        runCatching {
            EntryPointAccessors.fromApplication(appContext, VisualizerEntry::class.java).playbackStateHolder()
        }.getOrNull()
    }
    val idleState = remember { StablePlayerState() }
    val stableState: StablePlayerState = if (playbackHolder != null) {
        playbackHolder.stablePlayerState.collectAsState(initial = idleState).value
    } else idleState
    val isPlaying = stableState.isPlaying
    val songTitle = stableState.currentSong?.title?.takeIf { it.isNotBlank() } ?: "PixelPlay"
    // 进度预览：用动画相位模拟 0..1 的播放进度，驱动 PROGRESS 环形进度
    val progressFraction = (animPhase / (2 * PI.toFloat())).coerceIn(0f, 1f)

    // 加载当前歌曲的封面（每次切歌重新加载）
    var artwork by remember(stableState.currentSong?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(stableState.currentSong?.id) {
        artwork = null
        val song = stableState.currentSong ?: return@LaunchedEffect
        artwork = withContext(Dispatchers.IO) {
            loadCover(appContext, song)?.let { downscale(it, 48) }
        }
    }

    // 应用 Logo（无封面时兜底）
    val appLogo = remember(appContext) {
        runCatching {
            val d = appContext.packageManager.getApplicationIcon(appContext.packageName)
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 192
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 192
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(cv)
            bmp
        }.getOrNull()
    }

    val ledOnColor = Color(0xFFE8E8E8)   // 单色 LED 亮起色
    val ledOffColor = Color(0xFF121216)   // 未点亮

    Surface(
        shape = ShapeCache.smooth24,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 两颗矩阵并排
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 25×25 高密度
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMatrixCanvas(
                        mask = GLYPH_MASK_25,
                        gridSize = 25,
                        displayMode = displayMode,
                        animPhase = animPhase,
                        levels = levels,
                        ledOnColor = ledOnColor,
                        ledOffColor = ledOffColor,
                        canvasSizeDp = 148,
                        title = songTitle,
                        progressFraction = progressFraction,
                        artwork = artwork,
                        appLogo = appLogo,
                        isPlaying = isPlaying
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "25×25",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 13×13 低密度
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMatrixCanvas(
                        mask = GLYPH_MASK_13,
                        gridSize = 13,
                        displayMode = displayMode,
                        animPhase = animPhase,
                        levels = levels,
                        ledOnColor = ledOnColor,
                        ledOffColor = ledOffColor,
                        canvasSizeDp = 100,
                        title = songTitle,
                        progressFraction = progressFraction,
                        artwork = artwork,
                        appLogo = appLogo,
                        isPlaying = isPlaying
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "13×13",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VisualizerEntry {
    fun audioVisualizer(): AudioVisualizer
    fun playbackStateHolder(): PlaybackStateHolder
}

@Composable
private fun GlyphMatrixCanvas(
    mask: BooleanArray,
    gridSize: Int,
    displayMode: String,
    animPhase: Float,
    levels: FloatArray,
    ledOnColor: Color,
    ledOffColor: Color,
    canvasSizeDp: Int,
    title: String = "PixelPlay",
    progressFraction: Float = 0f,
    artwork: Bitmap? = null,
    appLogo: Bitmap? = null,
    isPlaying: Boolean = false
) {
    // 封面旋转角：播放时随时间推进，暂停时冻结在最后一帧的角度
    val rotationHolder = remember { FloatArray(1) }

    Surface(
        modifier = Modifier.size(canvasSizeDp.dp),
        shape = ShapeCache.smooth16,
        color = Color(0xFF050508),
        shadowElevation = 1.dp
    ) {
        Canvas(modifier = Modifier.padding(5.dp)) {
            val canvasW = this.size.width
            val cellSize = canvasW / gridSize
            val gap = cellSize * 0.18f
            val ledSize = cellSize - gap
            val cornerR = ledSize * 0.12f

            // ARTWORK 专用旋转角：播放时 = animPhase，暂停时固定为最后一次播放值
            val angle = if (displayMode == "ARTWORK") {
                if (isPlaying) {
                    rotationHolder[0] = animPhase
                    animPhase
                } else {
                    rotationHolder[0]
                }
            } else 0f

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val idx = row * gridSize + col
                    if (!mask[idx]) continue

                    val x = col * cellSize + gap / 2f
                    val y = row * cellSize + gap / 2f

                    val cellColor: Color = if (displayMode == "ARTWORK") {
                        // 封面黑白化：取灰度亮度，映射到单色 LED 的明暗
                        val src = artwork ?: appLogo
                        if (src != null) {
                            val luma = sampleCoverLuma(src, gridSize, row, col, angle)
                            if (luma > 0.05f) ledOnColor.copy(alpha = luma.coerceIn(0f, 1f))
                            else ledOffColor
                        } else ledOffColor
                    } else {
                        val brightness = calculateBrightness(
                            mode = displayMode,
                            row = row,
                            col = col,
                            gridSize = gridSize,
                            animPhase = animPhase,
                            levels = levels,
                            title = title,
                            progressFraction = progressFraction
                        )
                        if (brightness > 0.05f) ledOnColor.copy(alpha = brightness.coerceIn(0f, 1f))
                        else ledOffColor
                    }

                    if (displayMode != "ARTWORK") {
                        val brightness = cellColor.alpha
                        if (brightness > 0.05f) {
                            // 辉光
                            drawRoundRect(
                                color = cellColor.copy(alpha = brightness * 0.1f),
                                topLeft = Offset(x - gap * 0.3f, y - gap * 0.3f),
                                size = Size(ledSize + gap * 0.6f, ledSize + gap * 0.6f),
                                cornerRadius = CornerRadius(cornerR * 1.5f)
                            )
                        }
                    }
                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(x, y),
                        size = Size(ledSize, ledSize),
                        cornerRadius = CornerRadius(cornerR)
                    )
                }
            }
        }
    }
}

// ─── 封面点阵采样 ─────────────────────────────────────────────

private fun sampleCoverLuma(src: Bitmap, gridSize: Int, row: Int, col: Int, angle: Float): Float {
    // 将 LED 位置映射到方形容器的归一化坐标，再旋转取色，返回灰度亮度 0..1
    val u = (col + 0.5f) / gridSize - 0.5f
    val v = (row + 0.5f) / gridSize - 0.5f
    val cosA = cos(angle)
    val sinA = sin(angle)
    val ux = (u * cosA - v * sinA) * 2f
    val uy = (u * sinA + v * cosA) * 2f
    val sx = ((ux + 1f) / 2f * src.width).toInt().coerceIn(0, src.width - 1)
    val sy = ((uy + 1f) / 2f * src.height).toInt().coerceIn(0, src.height - 1)
    val c = runCatching { src.getPixel(sx, sy) }.getOrDefault(0)
    return (0.299f * android.graphics.Color.red(c) +
        0.587f * android.graphics.Color.green(c) +
        0.114f * android.graphics.Color.blue(c)) / 255f
}

private fun loadCover(appContext: android.content.Context, song: Song): Bitmap? {
    // 优先按封面 URI 加载
    song.albumArtUriString?.takeIf { it.isNotBlank() }?.let { uriStr ->
        runCatching {
            AlbumArtUtils.openArtworkInputStream(appContext, Uri.parse(uriStr))?.use { ins ->
                BitmapFactory.decodeStream(ins, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        }.getOrNull()?.let { return it }
    }
    // 回退到内嵌封面
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            val f = File(song.path)
            if (f.exists() && f.canRead()) retriever.setDataSource(song.path)
            else if (song.contentUriString.isNotBlank()) retriever.setDataSource(appContext, Uri.parse(song.contentUriString))
            else return@runCatching null
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}

private fun downscale(src: Bitmap, max: Int): Bitmap {
    if (src.width <= max && src.height <= max) return src
    val scale = max.toFloat() / maxOf(src.width, src.height)
    return Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ─── 单色亮度计算 ──────────────────────────────────────────────

private fun calculateBrightness(
    mode: String,
    row: Int,
    col: Int,
    gridSize: Int,
    animPhase: Float,
    levels: FloatArray,
    title: String = "PixelPlay",
    progressFraction: Float = 0f
): Float {
    val half = gridSize / 2f
    val dx = col - half + 0.5f
    val dy = row - half + 0.5f
    val dist = sqrt(dx * dx + dy * dy)
    val normDist = (dist / half).coerceIn(0f, 1f)

    // 该页取频谱电平：col -> 0..BANDS-1（低频靠左）
    val level = levelAt(col, gridSize, levels)
    val hasSignal = levels.any { it > 0.03f }

    return when (mode) {
        "NOW_PLAYING" -> {
            val ringPhase = (animPhase * 0.5f) % (2f * PI.toFloat())
            val ringPulse = abs(sin(normDist * 3.5f - ringPhase))
            val brightness = (ringPulse * (1.0 - normDist * 0.4)).coerceIn(0.0, 1.0).toFloat()
            if (brightness > 0.08f) brightness else 0f
        }

        "VISUALIZER" -> {
            val barHeight = if (hasSignal) level * (gridSize - 1)
                else ((sin(animPhase + col * 0.45f) + 1f) / 2f) * (gridSize - 1)
            val threshold = (gridSize - 1 - barHeight).toInt()
            if (row >= threshold) {
                val t = ((gridSize - 1 - row).toFloat() / gridSize).coerceIn(0f, 1f)
                (0.3f + t * 0.7f).coerceIn(0f, 1f)
            } else 0f
        }

        "WAVEFORM" -> {
            // 频谱驱动的中心对称镜像柱：随电平伸缩，避免整列常满或整排空
            val centerRow = (gridSize - 1) / 2f
            val colLevel = levelAt(col, gridSize, levels)
            val peak = if (hasSignal) {
                0.35f + colLevel * centerRow * 1.2f
            } else {
                (0.3f + (sin(animPhase + col * 0.5f) + 1f) / 2f) * (centerRow * 0.9f)
            }
            val dist = abs(row - centerRow)
            if (dist <= peak) {
                val t = 1f - (dist / peak.coerceAtLeast(0.5f))
                (0.2f + t * 0.8f).coerceIn(0f, 1f)
            } else 0f
        }

        "RING" -> {
            // 旋转的径向频谱：每段角度映射一段频谱，半径随电平伸到矩阵边缘，中心暗、边缘亮
            val angle = atan2(dy.toDouble(), dx.toDouble())
            var angNorm = ((angle + PI) / (2 * PI)).toFloat() + animPhase * 0.12f
            angNorm -= floor(angNorm)
            val fpos = angNorm * AudioVisualizer.BANDS
            val i0 = fpos.toInt().coerceIn(0, AudioVisualizer.BANDS - 1)
            val i1 = (i0 + 1).coerceIn(0, AudioVisualizer.BANDS - 1)
            val frac = fpos - fpos.toInt()
            val lv = if (hasSignal) {
                lerp(levels.getOrElse(i0) { 0f }, levels.getOrElse(i1) { 0f }, frac)
            } else {
                (sin(animPhase * 2f + angNorm * 6f) + 1f) / 2f * 0.7f
            }
            val maxR = half * 1.15f
            val radius = 0.5f + lv * maxR
            if (dist <= radius) {
                val t = (dist / maxR).coerceIn(0f, 1f)
                (0.08f + t * 0.92f).coerceIn(0f, 1f) * (0.35f + 0.65f * lv).coerceIn(0f, 1f)
            } else 0f
        }

        "PULSE" -> {
            val bassCount = min(4, levels.size.coerceAtMost(6))
            val bass = if (hasSignal) {
                var s = 0f
                for (i in 0 until bassCount) s += levels[i]
                s / bassCount
            } else (sin(animPhase * 6f) + 1f) / 2f * 0.6f
            val radius = 1f + bass * half
            val edge = abs(dist - radius)
            if (edge <= 1f) {
                val w = ((1f - edge) * (0.35f + 0.65f * bass)).coerceIn(0f, 1f)
                if (w > 0.05f) w else 0f
            } else 0f
        }

        "TITLE" -> {
            // 点阵滚动歌名：字符 5×3 + 间距1，垂直居中，随动画相位向左滚动
            val charH = 5
            val charW = 3
            val step = charW + 1
            val top = (gridSize - charH) / 2
            val bmpW = title.length * step
            val total = bmpW + gridSize
            val gx = ((col + animPhase * 12f) % total + total) % total
            if (gx >= bmpW) {
                0f
            } else {
                val ci = (gx / step).toInt()
                val cc = (gx % step).toInt()
                if (cc >= charW) 0f
                else {
                    val ch = title.getOrNull(ci)
                    if (ch == null) 0f else {
                        val rowIdx = row - top
                        if (rowIdx in 0 until charH && glyphChar(ch)[rowIdx][cc]) 1f else 0f
                    }
                }
            }
        }

        "PROGRESS" -> {
            // 大百分比显示：如 "50%"，数字居中，清晰易读
            val percentText = "${(progressFraction * 100).toInt()}%"
            val charW = 3
            val step = charW + 1
            val textWidth = percentText.length * step - 1
            val startX = ((gridSize - textWidth) / 2f).toInt()
            val startY = (gridSize - 5) / 2
            if (col in startX until (startX + textWidth) && row in startY until (startY + 5)) {
                val charIdx = (col - startX) / step
                val colInChar = (col - startX) % step
                if (colInChar < charW) {
                    val ch = percentText.getOrNull(charIdx)
                    if (ch != null) {
                        val glyph = glyphChar(ch)
                        val rowIdx = row - startY
                        if (glyph[rowIdx][colInChar]) 1f else 0f
                    } else 0f
                } else 0f
            } else 0f
        }

        else -> 0f
    }
}

private fun levelAt(col: Int, gridSize: Int, levels: FloatArray): Float {
    if (levels.isEmpty()) return 0f
    val idx = ((col.toFloat() / (gridSize - 1).coerceAtLeast(1)) *
        (AudioVisualizer.BANDS - 1)).toInt().coerceIn(0, AudioVisualizer.BANDS - 1)
    return levels[idx].coerceIn(0f, 1f)
}

// ─── 真实硬件 LED 圆形遮罩 ──────────────────────────────────

private val GLYPH_MASK_25: BooleanArray = buildMask(25, intArrayOf(
    7, 11, 15, 17, 19, 21, 21, 23, 23,
    25, 25, 25, 25, 25, 25, 25,
    23, 23, 21, 21, 19, 17, 15, 11, 7
))

private val GLYPH_MASK_13: BooleanArray = buildMask(13, intArrayOf(
    5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5
))

private fun buildMask(gridSize: Int, rowWidths: IntArray): BooleanArray {
    val mask = BooleanArray(gridSize * gridSize)
    for (row in 0 until gridSize) {
        val width = if (row < rowWidths.size) rowWidths[row] else 0
        val startCol = (gridSize - width) / 2
        for (col in startCol until startCol + width) {
            if (col in 0 until gridSize) {
                mask[row * gridSize + col] = true
            }
        }
    }
    return mask
}