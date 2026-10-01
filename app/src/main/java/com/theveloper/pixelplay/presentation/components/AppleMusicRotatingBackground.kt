package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.theveloper.pixelplay.data.netease.normalizeRemoteImageUrl
import com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 从任意「被包裹」的字符串里抠出 http(s) 地址。
 *
 * 在线音源的封面地址经常被引号类字符包着（半角/全角反引号、直引号、弯引号…），
 * 用 trim + replace 穷举字符既不可靠也永远追不上新变体；按前缀直接提取最稳，
 * 前后裹着什么垃圾字符都不影响。
 */
private val HTTP_URL_REGEX = Regex("""https?://[^\s"'`<>]+""", RegexOption.IGNORE_CASE)

/**
 * Apple Music / AMLL 风格「网格渐变（Mesh Gradient）」氛围背景。
 *
 * 1:1 对齐 applemusic-like-lyrics 的 `bg-render/mesh-renderer`：
 *  1. 封面缩到 32×32，做「对比度 0.4 → 饱和度 3.0 → 对比度 1.7 → 亮度 0.75」的像素处理，再做一次盒式模糊；
 *  2. 控制点取自 `cp-presets.ts` 的 [CONTROL_POINT_PRESETS]（6 组手工调优预设，按横竖屏挑，
 *     20% 概率走 `cp-generate.ts` 的随机控制点），用双三次 Hermite 网格把低清图「揉」成流动渐变；
 *  3. 纹理在网格内部按 `mesh.frag.glsl` 的 `finalUV = R(angle)·(uv-0.2)*scale + 0.5` 缓慢旋转，
 *     角度与缩放由低频音量驱动（对应 AMLL 的 `setLowFreqVolume`），形成跟鼓点呼吸的律动；
 *  4. 暗角按 `mesh.frag.glsl` 的 `mask = 0.6 + smoothstep(0.8, 0.3, dist) * 0.4` 精确复刻。
 *
 * 由于 minSdk = 23（无法使用 AGSL RuntimeShader / WebGL），这里用 `drawVertices` + `BitmapShader`
 * （MIRRORED_REPEAT 平铺）在 CPU 侧求值网格几何、逐帧旋转纹理 UV，达到与 WebGL 版一致的效果。
 */
@Composable
fun AppleMusicRotatingBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    /** 目标帧率：对齐 AMLL 的 `setFPS`，默认 30，避免高刷屏上白算网格。 */
    targetFps: Int = DEFAULT_BACKGROUND_FPS,
    /**
     * 静态模式：对齐 AMLL 的 `setStaticMode`。
     * 开启后时间不再推进、也不再跟低频音量呼吸，画面停在最后一帧（省电）。
     */
    staticMode: Boolean = false
) {
    val context = LocalContext.current

    // ⚡ Android 12（API 31）以下没有 RenderEffect。AMLL 的网格本身不依赖 GPU 模糊，
    //    但低版本放大 32×32 纹理时插值更硬，这里降一档到 16 让网格过渡更细、观感更接近。
    val nativeBlurSupported = SoftBlur.isNativeBlurSupported
    val texSize = if (nativeBlurSupported) TEX_SIZE.toInt() else TEX_SIZE_LOW.toInt()

    // ⚡ 背景要的是「像素」（32×32 纹理），而 SmartImage 产出的是 Painter，所以这里必须
    //    自己向 Coil 要一次 Drawable。也正因为绕开了 SmartImage 的统一清洗入口，
    //    地址得在本地处理干净：在线封面地址常带反引号/首尾空白，或是 http 明文，
    //    地址不干净 → Coil 解析失败 → 网格拿不到纹理 → 表现就是「绚丽背景只有纯色」。
    val normalizedArtUri = remember(albumArtUri) {
        albumArtUri?.let { raw ->
            // ⚠️ 这里不能只做 trim + replace("`")：在线音源的封面地址可能被各种引号类字符包裹
            //    （半角/全角反引号、直引号、弯引号、零宽空格…），只要有一个字符对不上，
            //    前缀判断就会走「原样保留」分支，Coil 拿到带垃圾字符的地址必然解析失败
            //    → 网格拿不到纹理就什么都不画 → 表现就是「绚丽背景打开了只有纯色」。
            //    改为直接从字符串里「抠」出第一个 http(s) 地址，前面裹着什么字符都不影响。
            val httpUrl = HTTP_URL_REGEX.find(raw)?.value
            when {
                httpUrl != null -> normalizeRemoteImageUrl(httpUrl) ?: httpUrl
                // 协议相对地址：//host/path
                raw.trim().startsWith("//") -> normalizeRemoteImageUrl(raw.trim()) ?: raw.trim()
                // 本地/自定义 scheme（content://、file://、local://、pixelplay_local_art:// 等）原样保留
                else -> raw.trim()
            }
        }
    }

    // 是否已确认「这张封面确实拿不到」。用来区分「加载失败」与「还没加载完」：
    // 前者要立刻切到主题色兜底纹理，后者应该继续等 —— 切歌途中不该闪一下兜底色。
    var loadFailed by remember(normalizedArtUri) { mutableStateOf(false) }

    // 封面加载 + 像素处理。切歌时 produceState 重新求值，旧图在新图就绪前保留。
    val processed by produceState<Bitmap?>(null, normalizedArtUri, texSize) {
        var failure: String? = null
        val result: Bitmap? = normalizedArtUri?.let { uri ->
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .size(Size(512, 512))
                    .build()
                when (val response = context.imageLoader.execute(request)) {
                    is SuccessResult ->
                        response.drawable.toBitmap().let { processCover(it, texSize) }

                    else -> {
                        failure = "coil:${response::class.simpleName}"
                        null
                    }
                }
            }.onFailure {
                // runCatching 连协程取消也一起吞：切歌 / 退出播放器时 Coil 抛的
                // LeftCompositionCancellationException 会被记成一次「加载失败」，
                // 必须原样抛回去，取消语义才不会被破坏。
                if (it is CancellationException) throw it
                failure = "throw:${it::class.simpleName}:${it.message}"
            }.getOrNull()
        }
        // 诊断：绚丽背景看不到时，用 `[MeshBG]` 过滤日志确认封面是否处理成功。
        // failure 会区分「Coil 请求失败」与「抛异常」，避免只知道 processed=false 却不知为何。
        android.util.Log.w(
            "PixelPlay_Debug",
            "[MeshBG] cover processed=${result != null} size=${result?.width}x${result?.height} " +
                "texSize=$texSize failure=$failure raw=$albumArtUri normalized=$normalizedArtUri"
        )
        loadFailed = result == null
        value = result
    }

    // 控制点网格：按专辑 uri 做种子，保证「每首歌形状固定但各不相同」；
    // 横竖屏各缓存一份（预设按 cp-presets.ts 的「竖屏推荐 / 横屏推荐」分组挑选）。
    val gridProvider = remember(albumArtUri) {
        ControlGridProvider(albumArtUri?.hashCode() ?: 0)
    }

    // 兜底纹理：封面拿不到时（在线源地址异常 / 断网 / 这首歌本来就没封面）原来是什么都不画，
    // 用户看到的就是「背景只有纯色」。这里用主题色合成一张同尺寸纹理顶上，
    // 后面完全走同一套网格 + 旋转 + 呼吸逻辑，所以仍然是有颜色在流动的动态背景。
    val colorScheme = MaterialTheme.colorScheme
    val fallbackCover = remember(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        texSize
    ) {
        buildFallbackCover(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            texSize
        )
    }

    val shader = remember(processed, fallbackCover) {
        BitmapShader(processed ?: fallbackCover, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }

    val buffers = remember { MeshBuffers() }
    val paint = remember {
        Paint().apply {
            isFilterBitmap = true
            // ⚠️ 关键：drawVertices 的 colors 数组传 null 时，它会拿 paint 的 color 当「顶点色」，
            //    再与 shader 的采样结果相乘（默认 SkBlendMode.kModulate）。Paint 默认是黑色，
            //    相乘后整片归零 —— 表现就是「纹理处理成功、网格也建出来了，但画面什么都没有」。
            //    必须给白色，shader 的结果才能原样透出。
            color = 0xFFFFFFFF.toInt()
        }
    }

    // 绘制诊断计数（普通数组而非 State，避免每帧触发重组）
    val drawLogCount = remember { intArrayOf(0) }

    // 低频音量：对应 AMLL 的 setLowFreqVolume，接在播放引擎上的 [AudioVisualizer] 直接取用
    val appContext = context.applicationContext
    val visualizer = remember(appContext) {
        runCatching {
            EntryPointAccessors.fromApplication(appContext, VisualizerEntry::class.java).audioVisualizer()
        }.getOrNull()
    }
    val volumeSmoother = remember { LowFreqVolumeSmoother() }
    val vignetteCache = remember { VignetteCache() }
    val vignettePaint = remember { Paint() }

    // 时间轴（秒），对齐 AMLL 的 `frameTime += frameDelta * flowSpeed`。
    //
    // 不用 rememberInfiniteTransition：它跟着屏幕刷新率走，120Hz 屏就是每秒算 120 次网格，
    // 而 AMLL 默认只有 30 FPS。这里用 withFrameNanos 手动推进——只有累计够一个帧间隔才写
    // state，不够就什么都不做，因此不会触发重组/重绘，高刷屏下白白多算的开销被抹掉。
    // 静态模式下循环压根不启动，纹理停在最后一帧。
    var frameTimeSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(targetFps, staticMode) {
        if (staticMode) return@LaunchedEffect
        val intervalNanos = 1_000_000_000L / targetFps.coerceIn(1, 120)
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                } else if (now - lastNanos >= intervalNanos) {
                    frameTimeSeconds += (now - lastNanos) / 1_000_000_000f
                    lastNanos = now
                }
            }
        }
    }

    // 淡入：AMLL 的 state.alpha（delta/500 递增）+ easeInOutSine。
    // 封面就绪、或已确认失败（此时切兜底纹理）→ 1；
    // 切歌途中（还没失败）保持 0，等新封面到了再淡入，避免闪一下兜底色。
    val rawFade by animateFloatAsState(
        targetValue = if (processed != null || loadFailed) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "amllFade"
    )
    val fade = easeInOutSine(rawFade.coerceIn(0f, 1f))

    Box(modifier) {
        // ⚡ 这里**刻意不叠** Modifier.blur：AMLL 的 mesh-renderer 只在像素处理阶段做一次
        //    `blurImage(imageData, 2, 4)`，渲染完就直接输出，没有任何后置模糊。
        //    之前额外套的 64dp 高斯会把网格的流动层次糊平，观感反而和 AMLL 不一致。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // shader 永远非空：封面拿不到时由 fallbackCover 顶上
                val shdr = shader

                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // 横竖屏各挑一套预设（cp-presets.ts：5×5 为竖屏推荐、4×4 为横屏推荐）
                val grid = gridProvider.grid(forPortrait = h > w)
                buffers.ensureBuilt(grid, w, h)

                // AMLL：u_volume = lowFreqVolume / 10（低频音量取值 [0,1]）
                // 静态模式下不读频谱、固定为 0，画面就完全静止（对齐 AMLL 的 setStaticMode）
                val lowFreq = if (staticMode) {
                    0f
                } else {
                    visualizer?.let { lowFreqLevel(it.levels.value) } ?: 0f
                }
                val uVolume = volumeSmoother.update(lowFreq) / 10f

                // AMLL：angle = (uTime + u_volume) * 2（uTime 每秒 0.1 → 每秒 0.2 弧度，31.415s 一圈）
                val angle = frameTimeSeconds * 0.2f + uVolume * 2f
                // AMLL：finalUV = rotatedUV * max(0.001, 1 - u_volume * 2) + vec2(0.5)
                val uvScale = max(0.001f, 1f - uVolume * 2f)

                val texs = buffers.texsFor(angle, uvScale, texSize.toFloat())
                if (drawLogCount[0] < DRAW_LOG_LIMIT) {
                    drawLogCount[0]++
                    android.util.Log.w(
                        "PixelPlay_Debug",
                        "[MeshBG] draw fade=$fade angle=$angle uvScale=$uvScale " +
                            "tex0=${texs[0]},${texs[1]} paintColor=${paint.color} " +
                            "alpha=${paint.alpha} verts=${buffers.vertexCount}"
                    )
                }

                paint.shader = shdr
                drawContext.canvas.nativeCanvas.drawVertices(
                    AndroidCanvas.VertexMode.TRIANGLES,
                    buffers.vertexCount,
                    buffers.verts,
                    0,
                    texs,
                    0,
                    null,
                    0,
                    buffers.indices,
                    0,
                    buffers.indexCount,
                    paint
                )

                // 暗角：1:1 复刻 mesh.frag.glsl 的 mask
                //   dist = distance(v_uv, vec2(0.5))（UV 单位），mask = 0.6 + smoothstep(0.8, 0.3, dist) * 0.4
                //   叠加的黑色 alpha = 1 - mask，等值线在屏幕上是半轴 (0.8w, 0.8h) 的椭圆。
                val vignette = vignetteCache.obtain(w, h)
                if (vignette != null) {
                    vignettePaint.shader = vignette
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, w, h, vignettePaint)
                }
            }
        }
    }
}

/** 背景默认帧率，对齐 AMLL `setFPS` 的默认值 30。 */
private const val DEFAULT_BACKGROUND_FPS = 30

/** 绘制阶段的诊断日志最多打几条（避免 30fps 刷屏）。 */
private const val DRAW_LOG_LIMIT = 3

/**
 * 构建标记：只为了在日志里一眼确认「跑的是不是最新编译的包」。
 *
 * 改一次代码就换一个值，看到日志里的标记没变，就说明装上去的还是旧 APK，
 * 不用再浪费时间分析效果。
 */
private const val MESH_BG_BUILD_TAG = "20261002-0040"

/**
 * 封面拿不到时的兜底纹理：用主题色画一条对角渐变。
 *
 * 尺寸与封面纹理一致，交给同一套网格 / 旋转 / 呼吸逻辑，因此观感上
 * 仍然是「有颜色在流动的动态背景」，而不是一片纯色。
 */
private fun buildFallbackCover(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    size: Int,
): Bitmap {
    val safeSize = size.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        isFilterBitmap = true
        shader = LinearGradient(
            0f,
            0f,
            safeSize.toFloat(),
            safeSize.toFloat(),
            intArrayOf(primary.toArgb(), secondary.toArgb(), tertiary.toArgb()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    AndroidCanvas(bitmap).drawRect(0f, 0f, safeSize.toFloat(), safeSize.toFloat(), paint)
    return bitmap
}

/** ARGB 黑色，仅给出 alpha。 */
private fun argb(alpha: Float): Int =
    (alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24

/**
 * 暗角着色器缓存。等值线是屏幕上的椭圆（半轴 0.8w × 0.8h），
 * 用 `RadialGradient` + 局部矩阵把圆拉成椭圆，尺寸不变时复用同一个 SkShader。
 */
private class VignetteCache {
    private var shader: RadialGradient? = null
    private var cachedW = -1f
    private var cachedH = -1f

    fun obtain(w: Float, h: Float): RadialGradient? {
        if (w <= 0f || h <= 0f) return null
        val hit = shader
        if (hit != null && cachedW == w && cachedH == h) return hit
        cachedW = w
        cachedH = h
        val cx = w / 2f
        val cy = h / 2f
        val built = RadialGradient(
            cx,
            cy,
            0.8f * w,
            intArrayOf(
                argb(0f),      // dist <= 0.3 → 不压暗
                argb(0.042f),  // dist = 0.4
                argb(0.141f),  // dist = 0.5
                argb(0.200f),  // dist = 0.55
                argb(0.314f),  // dist = 0.65
                argb(0.400f)   // dist >= 0.8 → 压到 0.6
            ),
            floatArrayOf(0.375f, 0.5f, 0.625f, 0.6875f, 0.8125f, 1f),
            Shader.TileMode.CLAMP
        ).apply {
            // 局部矩阵把「着色器坐标」映射到「画布坐标」：画布点 q 取的是 L⁻¹(q) 处的颜色。
            // 目标等值线是 (x-cx)²/(0.8w)² + (y-cy)²/(0.8h)² = 1 的椭圆，
            // 因此 y 方向要按 h/w 拉伸（竖屏 h>w → 上下方向被拉长），
            // 写成 w/h 会把椭圆压扁：竖屏下屏幕上下各约 1/3 区域直接被 40% 黑盖死，
            // 观感就是「绚丽背景怎么调都没效果」。
            setLocalMatrix(AndroidMatrix().apply { setScale(1f, h / w, cx, cy) })
        }
        shader = built
        return built
    }
}

/**
 * 网格细分级别。
 *
 * AMLL 用的是 50（201×201 = 40401 顶点），但那是 GPU 求值、基本免费；
 * 我们在 CPU 侧逐帧算双三次 Hermite，只能取一个折中值。
 * 16 → 6×6 预设下 81×81 = 6561 顶点、索引 38400，仍在 `Short` 上限（65535）内；
 * 再高就会溢出，且每帧求值开销开始明显。
 */
private const val SUB_DIV = 16
private const val TEX_SIZE = 32f

/** 低版本（无原生模糊）用的更低分辨率纹理：放大后更柔和，作为软件模糊降级。 */
private const val TEX_SIZE_LOW = 16f

private fun easeInOutSine(x: Float): Float = (-(cos(PI.toFloat() * x) - 1f) / 2f)

/**
 * 低频音量平滑（AMLL：`smoothedVolume += (volume - smoothedVolume) * min(1, delta / 100)`）。
 *
 * 取值来自 [AudioVisualizer] 的最低几个频带，直接读 `StateFlow.value`、在绘制阶段采样，
 * 不订阅状态，因此不会带来任何重组。
 */
private class LowFreqVolumeSmoother {
    private var smoothed = 0f
    private var lastNanos = 0L

    fun update(target: Float): Float {
        val now = System.nanoTime()
        val deltaMs = if (lastNanos == 0L) 16f
        else ((now - lastNanos) / 1_000_000f).coerceIn(0f, 100f)
        lastNanos = now
        val lerp = min(1f, deltaMs / 100f)
        smoothed += (target.coerceIn(0f, 1f) - smoothed) * lerp
        return smoothed
    }
}

/** 低频音量 = 最低 3 个频带的加权平均（AMLL 建议取 50-120Hz 区间）。 */
private fun lowFreqLevel(bands: FloatArray): Float {
    if (bands.isEmpty()) return 0f
    val n = min(3, bands.size)
    var acc = 0f
    var weight = 0f
    for (i in 0 until n) {
        val w = 1f - i * 0.3f
        acc += bands[i].coerceIn(0f, 1f) * w
        weight += w
    }
    return if (weight > 0f) acc / weight else 0f
}

// ------------------------------------------------------------------------------------------
// 封面像素处理：对比度 0.4 → 饱和度 3.0 → 对比度 1.7 → 亮度 0.75 → 盒式模糊(半径2, 4次)
// ------------------------------------------------------------------------------------------

private fun processCover(src: Bitmap, size: Int): Bitmap {
    val scaled = Bitmap.createScaledBitmap(src, size, size, true)
    val px = IntArray(size * size)
    scaled.getPixels(px, 0, size, 0, 0, size, size)

    for (i in px.indices) {
        val c = px[i]
        var r = ((c ushr 16) and 0xFF).toFloat()
        var g = ((c ushr 8) and 0xFF).toFloat()
        var b = (c and 0xFF).toFloat()

        // contrast 0.4
        r = (r - 128f) * 0.4f + 128f
        g = (g - 128f) * 0.4f + 128f
        b = (b - 128f) * 0.4f + 128f

        // saturate 3.0
        val gray = r * 0.3f + g * 0.59f + b * 0.11f
        r = gray * -2.0f + r * 3.0f
        g = gray * -2.0f + g * 3.0f
        b = gray * -2.0f + b * 3.0f

        // contrast 1.7
        r = (r - 128f) * 1.7f + 128f
        g = (g - 128f) * 1.7f + 128f
        b = (b - 128f) * 1.7f + 128f

        // brightness 0.75 + clamp（对应 Uint8ClampedArray 写入）
        val rr = (r * 0.75f).roundToInt().coerceIn(0, 255)
        val gg = (g * 0.75f).roundToInt().coerceIn(0, 255)
        val bb = (b * 0.75f).roundToInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    // 模糊半径按分辨率等比缩放（32px → 2，16px → 1），保证降分辨率后观感一致偏柔
    blurBox(px, size, size, radius = (size / 16).coerceAtLeast(1), quality = 4)

    // ⚠️ 重载是 (colors, offset, stride, width, height, config)：第 3 个参数是**每行像素数**。
    //    这里原来写的 0，Bitmap 会直接抛 "abs(stride) must be >= width"。
    //    封面其实早就从网络下好了，却卡在这一步被 catch 掉 → 网格拿不到纹理
    //    → 绚丽背景永远只剩兜底色。日志里的 failure 已经把这条异常打出来了。
    return Bitmap.createBitmap(px, 0, size, size, size, Bitmap.Config.ARGB_8888)
}

/** 可分离盒式模糊，边缘 clamp，重复 quality 次。 */
private fun blurBox(px: IntArray, w: Int, h: Int, radius: Int, quality: Int) {
    val n = w * h
    val R = IntArray(n)
    val G = IntArray(n)
    val B = IntArray(n)
    for (i in 0 until n) {
        val c = px[i]
        R[i] = (c ushr 16) and 0xFF
        G[i] = (c ushr 8) and 0xFF
        B[i] = c and 0xFF
    }
    val tR = IntArray(n)
    val tG = IntArray(n)
    val tB = IntArray(n)
    val win = radius * 2 + 1

    repeat(quality) {
        // 水平
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (k in -radius..radius) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    val idx = y * w + xx
                    sr += R[idx]
                    sg += G[idx]
                    sb += B[idx]
                }
                val idx = y * w + x
                tR[idx] = (sr + win / 2) / win
                tG[idx] = (sg + win / 2) / win
                tB[idx] = (sb + win / 2) / win
            }
        }
        // 垂直
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sr = 0
                var sg = 0
                var sb = 0
                for (k in -radius..radius) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    val idx = yy * w + x
                    sr += tR[idx]
                    sg += tG[idx]
                    sb += tB[idx]
                }
                val idx = y * w + x
                R[idx] = (sr + win / 2) / win
                G[idx] = (sg + win / 2) / win
                B[idx] = (sb + win / 2) / win
            }
        }
    }

    for (i in 0 until n) {
        px[i] = (0xFF shl 24) or (R[i] shl 16) or (G[i] shl 8) or B[i]
    }
}

// ------------------------------------------------------------------------------------------
// 控制点：cp-presets.ts 的预设 + cp-generate.ts 的随机生成
// ------------------------------------------------------------------------------------------

private class ControlGrid(
    val w: Int,
    val h: Int,
    val locX: FloatArray,
    val locY: FloatArray,
    val uTanX: FloatArray,
    val uTanY: FloatArray,
    val vTanX: FloatArray,
    val vTanY: FloatArray
)

/** 按专辑种子挑选控制点网格，并缓存横竖屏两份。 */
private class ControlGridProvider(private val seed: Int) {
    private var cachedPortrait: Boolean? = null
    private var cached: ControlGrid? = null

    fun grid(forPortrait: Boolean): ControlGrid {
        val hit = cached
        if (hit != null && cachedPortrait == forPortrait) return hit
        val built = build(forPortrait)
        cached = built
        cachedPortrait = forPortrait
        return built
    }

    private fun build(forPortrait: Boolean): ControlGrid {
        val rnd = Random(seed)
        // AMLL：20% 概率使用随机控制点（cp-generate.ts），其余从预设里随机挑一个
        if (rnd.nextInt(5) == 0) return generateControlGrid(6, 6, Random(seed))
        // cp-presets.ts：5×5 为竖屏推荐，4×4 为横屏推荐
        val target = if (forPortrait) 5 else 4
        val candidates = CONTROL_POINT_PRESETS.filter { it.width == target }
        val preset = candidates.getOrNull(rnd.nextInt(candidates.size.coerceAtLeast(1)))
            ?: CONTROL_POINT_PRESETS[0]
        return preset.toControlGrid()
    }
}

private fun Random.range(min: Float, max: Float): Float = min + nextFloat() * (max - min)

private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp01((x - edge0) / (edge1 - edge0))
    return t * t * (3f - 2f * t)
}

private fun fract(x: Float): Float = x - floor(x)

private fun noise(x: Float, y: Float): Float =
    fract(sin(x * 12.9898f + y * 78.233f) * 43758.5453f)

private fun smoothNoise(x: Float, y: Float): Float {
    val x0 = floor(x)
    val y0 = floor(y)
    val x1 = x0 + 1f
    val y1 = y0 + 1f
    val xf = x - x0
    val yf = y - y0
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)
    val n00 = noise(x0, y0)
    val n10 = noise(x1, y0)
    val n01 = noise(x0, y1)
    val n11 = noise(x1, y1)
    val nx0 = n00 * (1 - u) + n10 * u
    val nx1 = n01 * (1 - u) + n11 * u
    return nx0 * (1 - v) + nx1 * v
}

private fun noiseGradient(x: Float, y: Float): FloatArray {
    val e = 0.001f
    val dx = (smoothNoise(x + e, y) - smoothNoise(x - e, y)) / (2 * e)
    val dy = (smoothNoise(x, y + e) - smoothNoise(x, y - e)) / (2 * e)
    var len = sqrt(dx * dx + dy * dy)
    if (len == 0f) len = 1f
    return floatArrayOf(dx / len, dy / len)
}

private val KERNEL = arrayOf(
    intArrayOf(1, 2, 1),
    intArrayOf(2, 4, 2),
    intArrayOf(1, 2, 1)
)

private fun smoothChannel(
    src: FloatArray,
    w: Int,
    h: Int,
    iters: Int,
    factorStart: Float,
    factorMod: Float
): FloatArray {
    var cur = src
    var f = factorStart
    repeat(iters) {
        val next = cur.copyOf()
        for (j in 1 until h - 1) {
            for (i in 1 until w - 1) {
                var sum = 0f
                for (dj in -1..1) {
                    for (di in -1..1) {
                        sum += cur[(j + dj) * w + (i + di)] * KERNEL[dj + 1][di + 1]
                    }
                }
                val avg = sum / 16f
                val c = cur[j * w + i]
                next[j * w + i] = c * (1 - f) + avg * f
            }
        }
        cur = next
        f = clamp01(f + factorMod)
    }
    return cur
}

private fun generateControlGrid(w: Int, h: Int, rnd: Random): ControlGrid {
    val n = w * h
    val locX = FloatArray(n)
    val locY = FloatArray(n)
    val ur = FloatArray(n)
    val vr = FloatArray(n)
    val up = FloatArray(n)
    val vp = FloatArray(n)

    val variationFraction = rnd.range(0.4f, 0.6f)
    val normalOffset = rnd.range(0.3f, 0.6f)
    val blendFactor = 0.8f
    val smoothIters = floor(rnd.range(3f, 5f)).toInt()
    val smoothFactor = rnd.range(0.2f, 0.3f)
    val smoothModifier = rnd.range(-0.1f, -0.05f)
    val dx = 2f / (w - 1)
    val dy = 2f / (h - 1)

    for (j in 0 until h) {
        for (i in 0 until w) {
            val idx = j * w + i
            val baseX = (i.toFloat() / (w - 1)) * 2f - 1f
            val baseY = (j.toFloat() / (h - 1)) * 2f - 1f
            val border = i == 0 || i == w - 1 || j == 0 || j == h - 1

            val pertX = if (border) 0f else rnd.range(-variationFraction * dx, variationFraction * dx)
            val pertY = if (border) 0f else rnd.range(-variationFraction * dy, variationFraction * dy)
            var x = baseX + pertX
            var y = baseY + pertY

            val urr = if (border) 0f else rnd.range(-60f, 60f)
            val vrr = if (border) 0f else rnd.range(-60f, 60f)
            val upp = if (border) 1f else rnd.range(0.8f, 1.2f)
            val vpp = if (border) 1f else rnd.range(0.8f, 1.2f)

            if (!border) {
                val uNorm = (baseX + 1f) / 2f
                val vNorm = (baseY + 1f) / 2f
                val grad = noiseGradient(uNorm, vNorm)
                var offsetX = grad[0] * normalOffset
                var offsetY = grad[1] * normalOffset
                val distToBorder = min(min(uNorm, 1 - uNorm), min(vNorm, 1 - vNorm))
                val weight = smoothstep(0f, 1f, distToBorder)
                offsetX *= weight
                offsetY *= weight
                x = x * (1 - blendFactor) + (x + offsetX) * blendFactor
                y = y * (1 - blendFactor) + (y + offsetY) * blendFactor
            }

            locX[idx] = x
            locY[idx] = y
            ur[idx] = urr
            vr[idx] = vrr
            up[idx] = upp
            vp[idx] = vpp
        }
    }

    val sx = smoothChannel(locX, w, h, smoothIters, smoothFactor, smoothModifier)
    val sy = smoothChannel(locY, w, h, smoothIters, smoothFactor, smoothModifier)
    val su = smoothChannel(ur, w, h, smoothIters, smoothFactor, smoothModifier)
    val sv = smoothChannel(vr, w, h, smoothIters, smoothFactor, smoothModifier)
    val sp = smoothChannel(up, w, h, smoothIters, smoothFactor, smoothModifier)
    val sq = smoothChannel(vp, w, h, smoothIters, smoothFactor, smoothModifier)

    return controlGrid(w, h, sx, sy, su, sv, sp, sq)
}

/** 控制点数据 → 切线（参照 mesh-renderer/index.ts 的 ControlPoint）。 */
private fun controlGrid(
    w: Int,
    h: Int,
    locX: FloatArray,
    locY: FloatArray,
    ur: FloatArray,
    vr: FloatArray,
    up: FloatArray,
    vp: FloatArray
): ControlGrid {
    val n = w * h
    val uPower = 2f / (w - 1)
    val vPower = 2f / (h - 1)
    val uTanX = FloatArray(n)
    val uTanY = FloatArray(n)
    val vTanX = FloatArray(n)
    val vTanY = FloatArray(n)
    for (idx in 0 until n) {
        val uRot = ur[idx] * (PI.toFloat() / 180f)
        val vRot = vr[idx] * (PI.toFloat() / 180f)
        val uScale = uPower * up[idx]
        val vScale = vPower * vp[idx]
        uTanX[idx] = cos(uRot) * uScale
        uTanY[idx] = sin(uRot) * uScale
        vTanX[idx] = -sin(vRot) * vScale
        vTanY[idx] = cos(vRot) * vScale
    }
    return ControlGrid(w, h, locX, locY, uTanX, uTanY, vTanX, vTanY)
}

// ------------------------------------------------------------------------------------------
// cp-presets.ts 的 CONTROL_POINT_PRESETS（原样移植）
// ------------------------------------------------------------------------------------------

private class CpConf(
    val cx: Int,
    val cy: Int,
    val x: Float,
    val y: Float,
    val ur: Float = 0f,
    val vr: Float = 0f,
    val up: Float = 1f,
    val vp: Float = 1f
)

private class MeshPreset(val width: Int, val height: Int, val conf: List<CpConf>)

private fun MeshPreset.toControlGrid(): ControlGrid {
    val w = width
    val h = height
    val n = w * h
    val locX = FloatArray(n)
    val locY = FloatArray(n)
    val ur = FloatArray(n)
    val vr = FloatArray(n)
    val up = FloatArray(n)
    val vp = FloatArray(n)
    for (cp in conf) {
        val idx = cp.cy * w + cp.cx
        if (idx < 0 || idx >= n) continue
        locX[idx] = cp.x
        locY[idx] = cp.y
        ur[idx] = cp.ur
        vr[idx] = cp.vr
        up[idx] = cp.up
        vp[idx] = cp.vp
    }
    return controlGrid(w, h, locX, locY, ur, vr, up, vp)
}

private val CONTROL_POINT_PRESETS: List<MeshPreset> = listOf(
    // 竖屏推荐
    MeshPreset(
        5, 5, listOf(
            CpConf(0, 0, -1f, -1f),
            CpConf(1, 0, -0.5f, -1f),
            CpConf(2, 0, 0f, -1f),
            CpConf(3, 0, 0.5f, -1f),
            CpConf(4, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.5f),
            CpConf(1, 1, -0.5f, -0.5f),
            CpConf(2, 1, -0.0052029684413368305f, -0.6131420587090777f),
            CpConf(3, 1, 0.5884227308309977f, -0.3990805107556692f),
            CpConf(4, 1, 1f, -0.5f),
            CpConf(0, 2, -1f, 0f),
            CpConf(1, 2, -0.4210024670505933f, -0.11895058380429502f),
            CpConf(2, 2, -0.1019613423315412f, -0.023812118047224606f, 0f, -47f, 0.629f, 0.849f),
            CpConf(3, 2, 0.40275125660925437f, -0.06345314544600389f),
            CpConf(4, 2, 1f, 0f),
            CpConf(0, 3, -1f, 0.5f),
            CpConf(1, 3, 0.06801958477287173f, 0.5205913248960121f, -31f, -45f, 1f, 1f),
            CpConf(2, 3, 0.21446469120128908f, 0.29331610114301043f, 6f, -56f, 0.566f, 1.321f),
            CpConf(3, 3, 0.5f, 0.5f),
            CpConf(4, 3, 1f, 0.5f),
            CpConf(0, 4, -1f, 1f),
            CpConf(1, 4, -0.31378372841550195f, 1f),
            CpConf(2, 4, 0.26153633255328046f, 1f),
            CpConf(3, 4, 0.5f, 1f),
            CpConf(4, 4, 1f, 1f)
        )
    ),
    // 横屏推荐
    MeshPreset(
        4, 4, listOf(
            CpConf(0, 0, -1f, -1f),
            CpConf(1, 0, -0.33333333333333337f, -1f),
            CpConf(2, 0, 0.33333333333333326f, -1f),
            CpConf(3, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.04495399932657351f),
            CpConf(1, 1, -0.24056117520129328f, -0.22465999020104f),
            CpConf(2, 1, 0.334758885767489f, -0.00531297192779423f),
            CpConf(3, 1, 0.9989920470678106f, -0.3382976020775408f, 8f, 0f, 0.566f, 1.792f),
            CpConf(0, 2, -1f, 0.33333333333333326f),
            CpConf(1, 2, -0.3425497314639411f, -0.000027501607956947893f),
            CpConf(2, 2, 0.3321437945812673f, 0.1981776353859399f),
            CpConf(3, 2, 1f, 0.0766118180296832f),
            CpConf(0, 3, -1f, 1f),
            CpConf(1, 3, -0.33333333333333337f, 1f),
            CpConf(2, 3, 0.33333333333333326f, 1f),
            CpConf(3, 3, 1f, 1f)
        )
    ),
    MeshPreset(
        4, 4, listOf(
            CpConf(0, 0, -1f, -1f, 0f, 0f, 1f, 2.075f),
            CpConf(1, 0, -0.33333333333333337f, -1f),
            CpConf(2, 0, 0.33333333333333326f, -1f),
            CpConf(3, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.4545779491139603f),
            CpConf(1, 1, -0.33333333333333337f, -0.33333333333333337f),
            CpConf(2, 1, 0.0889403142626457f, -0.6025711180694033f, -32f, 45f, 1f, 1f),
            CpConf(3, 1, 1f, -0.33333333333333337f),
            CpConf(0, 2, -1f, -0.07402408608567845f, 1f, 0f, 1f, 0.094f),
            CpConf(1, 2, -0.2719422694359541f, 0.09775369930903222f, 25f, -18f, 1.321f, 0f),
            CpConf(2, 2, 0.19877414408395877f, 0.4307383294587789f, 48f, -40f, 0.755f, 0.975f),
            CpConf(3, 2, 1f, 0.33333333333333326f, -37f, 0f, 1f, 1f),
            CpConf(0, 3, -1f, 1f),
            CpConf(1, 3, -0.33333333333333337f, 1f),
            CpConf(2, 3, 0.5125850864305672f, 1f, -20f, -18f, 0f, 1.604f),
            CpConf(3, 3, 1f, 1f)
        )
    ),
    MeshPreset(
        5, 5, listOf(
            CpConf(0, 0, -1f, -1f),
            CpConf(1, 0, -0.4501953125f, -1f, 0f, 55f, 1f, 2.075f),
            CpConf(2, 0, 0.1953125f, -1f),
            CpConf(3, 0, 0.4580078125f, -1f, 0f, -25f, 1f, 1f),
            CpConf(4, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.2514475377525607f, -16f, 0f, 2.327f, 0.943f),
            CpConf(1, 1, -0.55859375f, -0.6609325945787148f, 47f, 0f, 2.358f, 0.377f),
            CpConf(2, 1, 0.232421875f, -0.5244375756366635f, -66f, -25f, 1.855f, 1.164f),
            CpConf(3, 1, 0.685546875f, -0.3753706470552125f),
            CpConf(4, 1, 1f, -0.6699125300354287f),
            CpConf(0, 2, -1f, 0.035910396862284255f),
            CpConf(1, 2, -0.4921875f, 0.005378616309457018f, 90f, 23f, 1f, 1.981f),
            CpConf(2, 2, 0.021484375f, -0.1365043639066228f, 0f, 42f, 1f, 1f),
            CpConf(3, 2, 0.4765625f, 0.05925822904974043f, -30f, 0f, 1.95f, 0.44f),
            CpConf(4, 2, 1f, 0.251428847823418f),
            CpConf(0, 3, -1f, 0.6968336464764276f, -68f, 0f, 1f, 0.786f),
            CpConf(1, 3, -0.6904296875f, 0.5890744209958608f, -68f, 0f, 1f, 1f),
            CpConf(2, 3, 0.1845703125f, 0.3879238667654693f, 61f, 0f, 1f, 1f),
            CpConf(3, 3, 0.60546875f, 0.4633553246018661f, -47f, -59f, 0.849f, 1.73f),
            CpConf(4, 3, 1f, 0.6214021886400309f, -33f, 0f, 0.377f, 1.604f),
            CpConf(0, 4, -1f, 1f),
            CpConf(1, 4, -0.5f, 1f, 0f, -73f, 1f, 1f),
            CpConf(2, 4, -0.3271484375f, 1f, 0f, -24f, 0.314f, 2.704f),
            CpConf(3, 4, 0.5f, 1f),
            CpConf(4, 4, 1f, 1f)
        )
    ),
    MeshPreset(
        5, 5, listOf(
            CpConf(0, 0, -1f, -1f),
            CpConf(1, 0, -0.6393f, -1f, 0f, 0f, 1f, 2.3884f),
            CpConf(2, 0, 0f, -1f),
            CpConf(3, 0, 0.5f, -1f),
            CpConf(4, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.2301f),
            CpConf(1, 1, -0.6934f, -0.331f, 0f, -0.7188f, 1f, 1.063f),
            CpConf(2, 1, -0.0082f, -0.6814f, -0.2583f, 0f, 1.0964f, 1f),
            CpConf(3, 1, 0.5836f, -0.531f, 0.7029f, 0f, 1.5466f, 1f),
            CpConf(4, 1, 1f, -0.6407f),
            CpConf(0, 2, -1f, 0.2973f, 0f, 0f, 1.8352f, 1f),
            CpConf(1, 2, -0.4082f, 0.0602f),
            CpConf(2, 2, -0.1803f, -0.3646f, -0.2998f, 0f, 1.1513f, 1f),
            CpConf(3, 2, 0.477f, -0.1027f, 0.8903f, -0.1882f, 1.0807f, 0.8551f),
            CpConf(4, 2, 1f, -0.2973f),
            CpConf(0, 3, -1f, 0.7628f, 0f, 0f, 2.3868f, 1f),
            CpConf(1, 3, -0.2525f, 0.4814f, -0.8406f, -1.6199f, 1.4093f, 1.2215f),
            CpConf(2, 3, 0.3607f, 0.2814f, -1.0713f, -0.0529f, 1.0025f, 0.7611f),
            CpConf(3, 3, 0.4885f, 0.623f, 0f, 0.8184f, 1f, 1.2876f),
            CpConf(4, 3, 1f, 0.5f),
            CpConf(0, 4, -1f, 1f),
            CpConf(1, 4, -0.4033f, 1f),
            CpConf(2, 4, 0.2672f, 1f),
            CpConf(3, 4, 0.5967f, 1f),
            CpConf(4, 4, 1f, 1f)
        )
    ),
    MeshPreset(
        5, 5, listOf(
            CpConf(0, 0, -1f, -1f),
            CpConf(1, 0, -0.2197f, -1f),
            CpConf(2, 0, 0.0197f, -1f),
            CpConf(3, 0, 0.8033f, -1f),
            CpConf(4, 0, 1f, -1f),
            CpConf(0, 1, -1f, -0.5451f),
            CpConf(1, 1, -0.4885f, -0.4035f, -1.0246f, -0.2268f, 1.1936f, 0.8005f),
            CpConf(2, 1, -0.1213f, -0.2867f, 0f, -0.6981f, 1f, 0.809f),
            CpConf(3, 1, 0.3246f, -0.5628f, 0f, -1.2188f, 1f, 1.044f),
            CpConf(4, 1, 1f, -0.3292f),
            CpConf(0, 2, -1f, 0.1416f),
            CpConf(1, 2, -0.341f, -0.0142f, 0f, -0.4004f, 1f, 1.1293f),
            CpConf(2, 2, -0.0393f, -0.023f, 0.2915f, -0.373f, 1.044f, 0.9879f),
            CpConf(3, 2, 0.3148f, -0.0673f, -0.7853f, -0.8962f, 1.4709f, 1.0247f),
            CpConf(4, 2, 1f, 0.1912f),
            CpConf(0, 3, -1f, 0.5f),
            CpConf(1, 3, -0.2689f, 0.2743f, 0.3404f, -0.5248f, 1.0184f, 0.4391f),
            CpConf(2, 3, 0.0721f, 0.269f, 0.5302f, 0.1244f, 0.6723f, 0.3225f),
            CpConf(3, 3, 0.4148f, 0.3894f, -0.6977f, -0.6783f, 0.8094f, 0.9247f),
            CpConf(4, 3, 1f, 0.446f),
            CpConf(0, 4, -1f, 1f),
            CpConf(1, 4, -0.7311f, 1f),
            CpConf(2, 4, 0.323f, 1f),
            CpConf(3, 4, 0.6393f, 1f),
            CpConf(4, 4, 1f, 1f)
        )
    )
)

// ------------------------------------------------------------------------------------------
// 双三次 Hermite 网格求值 + 顶点缓冲
// ------------------------------------------------------------------------------------------

private class MeshBuffers {
    var verts = FloatArray(0)
    var texs = FloatArray(0)
    var texsRaw = FloatArray(0)
    var indices = ShortArray(0)
    var vertexCount = 0
    var indexCount = 0

    private var builtGrid: ControlGrid? = null
    private var builtW = -1f
    private var builtH = -1f

    fun ensureBuilt(grid: ControlGrid, w: Float, h: Float) {
        if (builtGrid === grid && builtW == w && builtH == h) return
        builtGrid = grid
        builtW = w
        builtH = h

        val gridW = (grid.w - 1) * SUB_DIV + 1
        val gridH = (grid.h - 1) * SUB_DIV + 1
        vertexCount = gridW * gridH
        verts = FloatArray(vertexCount * 2)
        texsRaw = FloatArray(vertexCount * 2)
        texs = FloatArray(vertexCount * 2)

        val aspect = if (h > 0f) w / h else 1f
        val pos = FloatArray(2)
        for (gy in 0 until gridH) {
            val gv = gy.toFloat() / (gridH - 1)
            for (gx in 0 until gridW) {
                val gu = gx.toFloat() / (gridW - 1)
                evalMesh(grid, gu, gv, pos)

                // 顶点着色器的 aspect 覆盖裁切：pos.y *= aspect（横屏）或 pos.x /= aspect（竖屏）
                val sx: Float
                val sy: Float
                if (aspect > 1f) {
                    sx = (pos[0] + 1f) / 2f * w
                    sy = (pos[1] * aspect + 1f) / 2f * h
                } else {
                    sx = (pos[0] / aspect + 1f) / 2f * w
                    sy = (pos[1] + 1f) / 2f * h
                }

                val idx = gy * gridW + gx
                verts[idx * 2] = sx
                verts[idx * 2 + 1] = sy
                texsRaw[idx * 2] = gu
                texsRaw[idx * 2 + 1] = gv
            }
        }

        val quadW = gridW - 1
        val quadH = gridH - 1
        indexCount = quadW * quadH * 6
        indices = ShortArray(indexCount)
        var p = 0
        for (gy in 0 until quadH) {
            for (gx in 0 until quadW) {
                val i0 = (gy * gridW + gx).toShort()
                val i1 = (gy * gridW + gx + 1).toShort()
                val i2 = ((gy + 1) * gridW + gx).toShort()
                val i3 = ((gy + 1) * gridW + gx + 1).toShort()
                indices[p++] = i0
                indices[p++] = i1
                indices[p++] = i2
                indices[p++] = i1
                indices[p++] = i3
                indices[p++] = i2
            }
        }

        // 诊断：绚丽背景看不到时，用 `[MeshBG]` 过滤日志确认网格是否真的建出来了
        // （v0 / vLast 应为屏幕上/下的不同坐标；若全是 0 或 NaN，说明网格退化）
        android.util.Log.w(
            "PixelPlay_Debug",
            "[MeshBG] mesh built build=$MESH_BG_BUILD_TAG grid=${grid.w}x${grid.h} subdiv=$SUB_DIV " +
                "verts=$vertexCount indices=$indexCount canvas=${w.toInt()}x${h.toInt()} " +
                "v0=(${verts[0]},${verts[1]}) vLast=(${verts[(vertexCount - 1) * 2]},${verts[(vertexCount - 1) * 2 + 1]})"
        )
    }

    /**
     * 逐帧旋转 + 缩放纹理 UV：
     * `finalUV = R(angle) * (uv - 0.2) * uvScale + 0.5`，再映射到纹理像素空间。
     */
    fun texsFor(angle: Float, uvScale: Float, texSize: Float): FloatArray {
        val cosA = cos(angle)
        val sinA = sin(angle)
        for (i in 0 until vertexCount) {
            val cx = texsRaw[i * 2] - 0.2f
            val cy = texsRaw[i * 2 + 1] - 0.2f
            val rx = (cosA * cx - sinA * cy) * uvScale
            val ry = (sinA * cx + cosA * cy) * uvScale
            texs[i * 2] = (rx + 0.5f) * texSize
            texs[i * 2 + 1] = (ry + 0.5f) * texSize
        }
        return texs
    }
}

/** 双三次 Hermite（零扭转向量），输出网格空间坐标 [-1,1]。 */
private fun evalMesh(grid: ControlGrid, gu: Float, gv: Float, out: FloatArray) {
    val w = grid.w
    val h = grid.h

    val fx = gu * (w - 1)
    val fy = gv * (h - 1)
    var cx = floor(fx).toInt()
    var cy = floor(fy).toInt()
    var u = fx - cx
    var v = fy - cy
    if (cx >= w - 1) {
        cx = w - 2
        u = 1f
    }
    if (cy >= h - 1) {
        cy = h - 2
        v = 1f
    }

    val i00 = cy * w + cx
    val i10 = cy * w + cx + 1
    val i01 = (cy + 1) * w + cx
    val i11 = (cy + 1) * w + cx + 1

    val u2 = u * u
    val u3 = u2 * u
    val h00u = 2f * u3 - 3f * u2 + 1f
    val h01u = -2f * u3 + 3f * u2
    val h10u = u3 - 2f * u2 + u
    val h11u = u3 - u2

    val v2 = v * v
    val v3 = v2 * v
    val h00v = 2f * v3 - 3f * v2 + 1f
    val h01v = -2f * v3 + 3f * v2
    val h10v = v3 - 2f * v2 + v
    val h11v = v3 - v2

    val px = h00u * h00v * grid.locX[i00] + h01u * h00v * grid.locX[i10] +
        h00u * h01v * grid.locX[i01] + h01u * h01v * grid.locX[i11] +
        h10u * h00v * grid.uTanX[i00] + h11u * h00v * grid.uTanX[i10] +
        h10u * h01v * grid.uTanX[i01] + h11u * h01v * grid.uTanX[i11] +
        h00u * h10v * grid.vTanX[i00] + h01u * h10v * grid.vTanX[i10] +
        h00u * h11v * grid.vTanX[i01] + h01u * h11v * grid.vTanX[i11]

    val py = h00u * h00v * grid.locY[i00] + h01u * h00v * grid.locY[i10] +
        h00u * h01v * grid.locY[i01] + h01u * h01v * grid.locY[i11] +
        h10u * h00v * grid.uTanY[i00] + h11u * h00v * grid.uTanY[i10] +
        h10u * h01v * grid.uTanY[i01] + h11u * h01v * grid.uTanY[i11] +
        h00u * h10v * grid.vTanY[i00] + h01u * h10v * grid.vTanY[i10] +
        h00u * h11v * grid.vTanY[i01] + h01u * h11v * grid.vTanY[i11]

    out[0] = px
    out[1] = py
}
