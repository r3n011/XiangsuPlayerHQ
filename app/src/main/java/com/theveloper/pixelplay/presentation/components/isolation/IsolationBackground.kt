package com.theveloper.pixelplay.presentation.components.isolation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.skydoves.cloudy.cloudy
import com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 流动速度，AMLL Playground 的默认值。 */
internal const val ISOLATION_FLOW_SPEED = 0.2f

/** 低频能量驱动的流速增益：鼓点上流动时间最多加快 150%（液态跟随的主表现）。 */
private const val BASS_FLOW_GAIN = 1.5f

/** AGSL 路径帧率上限。 */
private const val AGSL_FPS_CAP = 60

/** 省电模式 / 没有音频在播（暂停、切歌间隙）时的降档帧率。 */
private const val AGSL_LOW_POWER_FPS_CAP = 30

/** 纯色兜底档的调色板过渡帧率（只重画一个矩形，够用且省电）。 */
private const val SOLID_FPS_CAP = 8
private const val SOLID_LOW_POWER_FPS_CAP = 4

/**
 * 兜底档封面模糊半径（Cloudy）。内容是静态封面位图，只在换封面时算一次，
 * 低版本（Cloudy 的 CPU 实现）也不会持续占电。
 */
private const val FALLBACK_BLUR_RADIUS = 24

/** 音频电平多久没更新就视为"没有在播放"（可视化约 60Hz 发布）。 */
private const val PLAYBACK_IDLE_NANOS = 700_000_000L

/* ========================= 渲染档位与优雅降级 ========================= */

/** 档位：正在探测能力（此间只画不透明兜底纯色）。 */
private const val RENDERER_PROBING = 0

/** 档位：AGSL 着色器（API 33+，首选）。 */
private const val RENDERER_AGSL = 1

/**
 * 档位：**兜底（封面模糊背景）**。
 *
 * 低版本设备（无 AGSL）、以及 AGSL 着色器探测不过 / 画不出来的设备都落到这一档：
 * 底层是封面调色板主色的不透明纯色，其上叠加 Cloudy 模糊的封面图（见
 * [FallbackIsolationBackground]）。不做任何逐像素的 CPU 求值，
 * 换歌时颜色仍按 1s 的 OkLab 过渡平滑跟上。
 */
private const val RENDERER_SOLID = 2

/**
 * 档位：**取色背景（静态封面主色）**。
 *
 * 「取色背景」样式专用：直接把封面调色板主色铺成不透明纯色（复用 [SolidIsolationCanvas]），
 * 不跑流体着色器、不做能力探测 —— 有封面色调的氛围，但不流动。
 */
private const val RENDERER_STATIC_PALETTE = 3

/**
 * 「绚丽背景」渲染能力缓存（进程级）。
 *
 * 个别机型 / 驱动上 AGSL 着色器会编译失败或**画不出任何像素**（表现为播放器背景透明、
 * 一帧有一帧无地闪）。这里只探测一次，失败即永久降档到纯色兜底，
 * 不支持的设备优雅落地，而不是每帧重试一直闪。
 */
private object IsolationCapability {
    private var agslProbed = false
    private var agslOk = false

    fun initialLevel(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslUsable() -> RENDERER_AGSL
        else -> RENDERER_SOLID
    }

    /**
     * 已经探测过就直接给出档位（同步、零开销）；还没探测过返回 [RENDERER_PROBING]。
     *
     * 用它做初始值：进程内第二次及以后构造背景（换歌、打开歌词页）不用再等一帧探测，
     * 也就不会出现「先闪一帧纯色再变成渐变」。
     */
    fun cachedLevel(): Int = when {
        !agslProbed -> RENDERER_PROBING
        agslOk -> RENDERER_AGSL
        else -> RENDERER_SOLID
    }

    @Synchronized
    fun agslUsable(): Boolean {
        if (!agslProbed) {
            agslProbed = true
            agslOk = probeAgsShader()
        }
        return agslOk
    }

    @Synchronized
    fun markAgsUnusable() {
        agslProbed = true
        agslOk = false
    }

    /**
     * 真机探测 AGSL：在**硬件加速画布**（RenderNode → HardwareRenderer → ImageReader）
     * 上用最小尺寸画一次，再读回像素判断**是不是真的画出了东西**。
     *
     * 探测后端必须与真实渲染后端一致 —— Compose 在屏渲染走的是硬件加速画布：
     * - Android 16 起，RuntimeShader 在软件画布（Bitmap 创建的 Canvas）上会直接抛
     *   `IllegalArgumentException: Software rendering doesn't support RuntimeShader`。
     *   旧实现用软件位图探测，导致高版本设备必然探测失败、被误降级成纯色；
     * - 即便在允许软件渲染的旧版本上，Skia CPU 路径也验证不了 GPU 驱动问题，
     *   两方面都指向：只能在硬件画布上探测。
     */
    private fun probeAgsShader(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@runCatching false
        val shader = RuntimeShader(ISOLATION_SHADER_SRC)
        shader.setFloatUniform("u_resolution", 16f, 16f)
        shader.setFloatUniform("u_time", 0f)
        shader.setFloatUniform("u_color0", 0.20f, 0.00f, 0.00f)
        shader.setFloatUniform("u_color1", 0.22f, 0.02f, 0.00f)
        shader.setFloatUniform("u_color2", 0.26f, -0.02f, 0.02f)
        shader.setFloatUniform("u_color3", 0.30f, 0.00f, -0.02f)
        shader.setFloatUniform("u_random", 0.5f, 0.5f, 0.5f)
        shader.setFloatUniform("u_flowParams", 1f, 1f, 1f, 1f)
        shader.setFloatUniform("u_angleJitter", 0f)
        shader.setFloatUniform("u_enableLightWave", 1f)
        shader.setFloatUniform("u_enableDithering", 1f)
        shader.setFloatUniform("u_bass", 0f)
        val paint = Paint().apply { this.shader = shader }
        // HardwareRenderer 要求使用方线程带 Looper：丢到专用 HandlerThread 上同步执行
        val probeThread = HandlerThread("IsolationAgsProbe").apply { start() }
        val done = CountDownLatch(1)
        var painted = false
        Handler(probeThread.looper).post {
            try {
                painted = runCatching { drawProbeRectOnHardwareCanvas(paint) }.getOrDefault(false)
            } finally {
                done.countDown()
            }
        }
        try {
            done.await(4, TimeUnit.SECONDS) && painted
        } finally {
            probeThread.quitSafely()
        }
    }.getOrDefault(false)

    /** 把探测矩形画进硬件画布并读回像素，判断 GPU 是否真的画出了不透明内容。 */
    private fun drawProbeRectOnHardwareCanvas(paint: Paint): Boolean {
        val size = 16
        val renderer = HardwareRenderer()
        renderer.setLightSourceAlpha(0f, 0f)
        renderer.setLightSourceGeometry(0f, 0f, 0f, 0f)
        @Suppress("WrongConstant") // usage 是按位或组合的 int flag，Lint 误报
        val reader = ImageReader.newInstance(
            size, size, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                HardwareBuffer.USAGE_CPU_READ_RARELY,
        )
        var image: android.media.Image? = null
        try {
            renderer.setSurface(reader.surface)
            val node = RenderNode("IsolationAgsProbe")
            node.setPosition(0, 0, size, size)
            val canvas = node.beginRecording(size, size)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()
            // GPU 出帧异步落进 ImageReader：慢驱动上多等几轮
            repeat(10) {
                if (image != null) return@repeat
                image = reader.acquireLatestImage()
                if (image == null) SystemClock.sleep(50)
            }
            val img = image ?: return false
            try {
                val plane = img.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val alpha = buffer.get(y * rowStride + x * 4 + 3).toInt() and 0xff
                        if (alpha > 0) return true
                    }
                }
                return false
            } finally {
                img.close()
            }
        } finally {
            reader.close()
            renderer.setSurface(null)
        }
    }
}

/**
 * 兜底底色：封面调色板主色，**永远不透明**。
 *
 * 铺在着色器渲染结果下面：任何一帧着色器画不出来，看到的也是这块底色，
 * 而不是「透明 → 直接看到后面的界面」。
 */
private fun DrawScope.drawIsolationFallbackBase(colorBuffer: FloatArray) {
    val srgb = FloatArray(3)
    isolationPaletteSrgb(colorBuffer, 0, srgb)
    drawRect(Color(srgb[0], srgb[1], srgb[2], 1f))
}

/**
 * AMLL Isolation 流体渐变背景。
 *
 * 封面在 CPU 一次性完成 直方图 → K-Means/八叉树择优 → 4 主色 → OkLab，
 * 之后每帧只是把少量 uniform 喂给单 pass 着色器 —— 不采样封面纹理、无几何网格。
 * 换封面 = 重掷随机布局 + 1s 的 OkLab 调色板过渡。
 *
 * **渲染档位（优雅降级）**：
 * - API 33+ 且 AGSL 探测通过（硬件画布上真的画出像素）→ AGSL 单 pass 着色器；
 * - 其余情况（低版本设备 / 着色器不可用）→ 封面模糊背景（Cloudy）+ 调色板主色底，
 *   不做任何逐像素的 CPU 求值，既不闪也不费电。
 * 两档都保证背景**不透明**。
 */
@Composable
internal fun IsolationBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    lightWave: Boolean = true,
    dithering: Boolean = true,
    /**
     * true = 取色背景：直接铺静态的封面主色纯色（不走 AGSL / 能力探测，不流动）；
     * false = 绚丽背景：流体渐变（低版本 / 着色器不可用时自动降级为 Cloudy 模糊封面）。
     */
    staticPalette: Boolean = false,
) {
    val state = remember { IsolationBackgroundState(lightWave = lightWave, dithering = dithering) }

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // 兜底档的封面模糊背景直接复用取色管线解码好的位图（Coil 缓存实例，勿 recycle）
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri.isNullOrBlank()) {
            coverBitmap = null
            state.applyDefaultColors()
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .allowHardware(false)
                    .size(Size(256, 256))
                    .build()
                (imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            }.getOrNull()
        }
        if (bitmap == null) {
            coverBitmap = null
            state.applyDefaultColors()
            return@LaunchedEffect
        }
        coverBitmap = bitmap
        val palette = withContext(Dispatchers.Default) {
            extractIsolationPalette(bitmap)
        }
        // ⚠️ 绝不能 recycle 这张位图：Coil 2.x 的 SuccessResult.drawable 在无滤镜、
        //    尺寸一致时 toBitmap() 直接返回**内存缓存中的同一个 Bitmap 实例**，
        //    recycle 会把缓存对象一并回收，其它任何用同一缓存位图的 AsyncImagePainter
        //    （迷你播放器/播放器封面等）再绘制时就会抛
        //    "Canvas: trying to use a recycled bitmap"。生命周期交给 Coil 管理。
        state.rollRandomParameters()
        state.applyPalette(palette.palette)
    }

    // ⚡ 跟随歌曲律动：读取全局 AudioVisualizer 的低频段电平（播放时约 60Hz 发布），
    //    平滑后喂给着色器 —— 鼓点上形变场轻微扩张 + 画面提亮。
    //    采集由 DualPlayerEngine 挂载的音频处理器提供，暂停/静音时无新数据，
    //    由帧循环里的 tickBass 缓慢释放，不会停在高亮状态。
    // 最近一次收到音频电平的时间：帧循环用它判断"当前有没有音频在播"，没有就降帧
    val lastLevelsNanos = remember { mutableLongStateOf(0L) }
    val powerSaveMode = rememberPowerSaveMode(context)

    LaunchedEffect(Unit) {
        val visualizer = EntryPointAccessors.fromApplication(
            context.applicationContext,
            IsolationAudioEntryPoint::class.java
        ).audioVisualizer()
        visualizer.levels.collect { levels ->
            lastLevelsNanos.longValue = System.nanoTime()
            // 取前 5 个低频段的最大值：对鼓点峰值最敏感（加权平均会被安静的频段拉低，
            // 律动看起来就不明显）
            var bass = 0f
            for (i in 0 until 5) {
                val v = levels.getOrElse(i) { 0f }
                if (v > bass) bass = v
            }
            state.updateBassEnergy(bass)
        }
    }

    val isLowPower: () -> Boolean = { powerSaveMode.value }
    val lastActivity: () -> Long = { lastLevelsNanos.longValue }

    // ⚡ 渲染档位：先在后台探测一次设备能力（AGSL 着色器在硬件画布上能否真的画出像素），
    //    探测期间只画不透明兜底纯色 —— 保证任何时刻都不会出现「背景透明」。
    //    探测不过或渲染失败即永久降档到兜底（封面模糊背景）。
    //    取色背景（staticPalette=true）直接进静态档：不探测、不跑着色器。
    var rendererLevel by remember(staticPalette) {
        mutableIntStateOf(if (staticPalette) RENDERER_STATIC_PALETTE else IsolationCapability.cachedLevel())
    }
    LaunchedEffect(staticPalette) {
        if (staticPalette) return@LaunchedEffect
        // 只有「还没探测过」才需要异步探测；已探测过的直接沿用缓存档位，不会闪一帧纯色
        if (rendererLevel == RENDERER_PROBING) {
            rendererLevel = withContext(Dispatchers.Default) { IsolationCapability.initialLevel() }
        }
    }
    val onAgsFailed: () -> Unit = remember {
        {
            IsolationCapability.markAgsUnusable()
            rendererLevel = RENDERER_SOLID
        }
    }

    when (rendererLevel) {
        RENDERER_AGSL -> AgslIsolationCanvas(
            state = state,
            modifier = modifier,
            albumArtUri = albumArtUri,
            coverBitmap = coverBitmap,
            isLowPower = isLowPower,
            lastActivityNanos = lastActivity,
            onRendererFailed = onAgsFailed,
        )
        RENDERER_STATIC_PALETTE -> SolidIsolationCanvas(state, modifier)
        else -> FallbackIsolationBackground(
            state = state,
            albumArtUri = albumArtUri,
            coverBitmap = coverBitmap,
            modifier = modifier,
        )
    }
}

/**
 * 省电模式状态（跟随系统「省电模式」开关）。
 *
 * 用广播接收器缓存：`PowerManager.isPowerSaveMode` 在部分实现里是跨进程调用，
 * 帧循环里逐帧查询不合适。
 */
@Composable
private fun rememberPowerSaveMode(context: Context): State<Boolean> {
    val powerManager = remember(context) {
        runCatching { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
    }
    val state = remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    DisposableEffect(context, powerManager) {
        if (powerManager == null) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                state.value = powerManager.isPowerSaveMode
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return state
}

/**
 * 帧时钟：按帧率上限推进渲染时间。[frameTick] 每渲染一帧递增一次，
 * 绘制阶段读取它以获得逐帧失效。
 */
private class IsolationFrameDriver(
    private val baseFpsCap: Int,
    private val lowPowerFpsCap: Int,
    /** 省电模式 / 其它需要降档的外部条件。 */
    private val isLowPower: () -> Boolean,
    /** 最近一次收到音频电平的时间（System.nanoTime）；没有音频在播时自然变"旧"。 */
    private val lastActivityNanos: () -> Long,
) {
    var timeSec = 0f
        private set
    private val tickState = mutableLongStateOf(0L)

    /** 每渲染一帧递增一次；绘制阶段读取它以获得逐帧失效。 */
    val frameTick: Long
        get() = tickState.longValue

    /**
     * 帧循环。`onFrame` 返回低频能量（0..1），用于**加速流动时间**：
     * 鼓点上液体流得更快（与波纹相位推进一起构成"液态跟随"），
     * 平滑过的能量值连续积分，不会产生跳变。
     */
    suspend fun loop(onFrame: (frameDeltaMs: Float) -> Float) {
        var lastFrameNanos = -1L
        var lastTickNanos = -1L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos < 0) {
                    lastFrameNanos = nowNanos
                    lastTickNanos = nowNanos
                    return@withFrameNanos
                }
                // ⚡ 动态帧率：省电模式、或没有音频在播（暂停 / 切歌间隙 / 静默）时降档。
                //    画面依旧在流动，但 GPU（AGSL）占用直接减半。
                val idle = nowNanos - lastActivityNanos() > PLAYBACK_IDLE_NANOS
                val fpsCap = if (isLowPower() || idle) lowPowerFpsCap else baseFpsCap
                val intervalMs = 1000f / fpsCap
                val tickDeltaMs = (nowNanos - lastTickNanos) / 1_000_000f
                if (tickDeltaMs < intervalMs) return@withFrameNanos
                lastTickNanos = nowNanos - ((tickDeltaMs % intervalMs) * 1_000_000f).toLong()
                // 切后台回来 delta 会很大，封顶，否则调色板过渡会一帧跳完
                val frameDeltaMs = min((nowNanos - lastFrameNanos) / 1_000_000f, 250f)
                lastFrameNanos = nowNanos
                // 先回调（更新调色板过渡与低频能量平滑），再按能量加速流动时间
                val bassBoost = onFrame(frameDeltaMs)
                timeSec += frameDeltaMs / 1000f * ISOLATION_FLOW_SPEED *
                    (1f + bassBoost.coerceIn(0f, 1f) * BASS_FLOW_GAIN)
                tickState.longValue++
            }
        }
    }
}

@Composable
private fun rememberIsolationFrameDriver(
    baseFpsCap: Int,
    lowPowerFpsCap: Int,
    isLowPower: () -> Boolean,
    lastActivityNanos: () -> Long,
    onFrame: (frameDeltaMs: Float) -> Float,
): IsolationFrameDriver {
    // ⚠️ 只在基础档位变化时重建 driver：重建会把流动时间归零，画面会跳一下。
    //    动态档位由 driver 每帧自己读，不触发重建。
    val driver = remember(baseFpsCap, lowPowerFpsCap) {
        IsolationFrameDriver(baseFpsCap, lowPowerFpsCap, isLowPower, lastActivityNanos)
    }
    val currentOnFrame by rememberUpdatedState(onFrame)
    LaunchedEffect(driver) {
        driver.loop { delta -> currentOnFrame(delta) }
    }
    return driver
}

/**
 * AGSL 路径（API 33+）：绘制阶段逐帧把 uniform 喂给 [RuntimeShader]，
 * 一次全屏 drawRect，无任何纹理采样。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AgslIsolationCanvas(
    state: IsolationBackgroundState,
    modifier: Modifier,
    albumArtUri: String?,
    coverBitmap: Bitmap?,
    isLowPower: () -> Boolean,
    lastActivityNanos: () -> Long,
    onRendererFailed: () -> Unit,
) {
    // 着色器连创建都失败（驱动不支持 AGSL）：先画兜底，下一帧起永久降档。
    val shader = remember { runCatching { RuntimeShader(ISOLATION_SHADER_SRC) }.getOrNull() }
    if (shader == null) {
        FallbackIsolationBackground(state, albumArtUri, coverBitmap, modifier)
        LaunchedEffect(Unit) { onRendererFailed() }
        return
    }

    val driver = rememberIsolationFrameDriver(
        baseFpsCap = AGSL_FPS_CAP,
        lowPowerFpsCap = AGSL_LOW_POWER_FPS_CAP,
        isLowPower = isLowPower,
        lastActivityNanos = lastActivityNanos,
    ) { delta ->
        state.updateColorBuffer(delta)
        state.tickBass(delta)
        state.bassLevel
    }

    val paint = remember(shader) { Paint().apply { this.shader = shader } }

    Canvas(modifier) {
        // ⚡ 先铺不透明兜底纯色：着色器在个别驱动上可能整帧画不出东西，
        //    没有这层就会「背景透明 → 直接看到后面的界面」，一帧有一帧无就是闪烁。
        drawIsolationFallbackBase(state.colorBuffer)
        driver.frameTick
        runCatching {
            shader.setFloatUniform("u_resolution", size.width, size.height)
            shader.setFloatUniform("u_time", driver.timeSec)
            shader.setFloatUniform("u_color0", state.colorBuffer[0], state.colorBuffer[1], state.colorBuffer[2])
            shader.setFloatUniform("u_color1", state.colorBuffer[3], state.colorBuffer[4], state.colorBuffer[5])
            shader.setFloatUniform("u_color2", state.colorBuffer[6], state.colorBuffer[7], state.colorBuffer[8])
            shader.setFloatUniform("u_color3", state.colorBuffer[9], state.colorBuffer[10], state.colorBuffer[11])
            shader.setFloatUniform("u_random", state.randomValues[0], state.randomValues[1], state.randomValues[2])
            shader.setFloatUniform(
                "u_flowParams",
                state.flowParams[0],
                state.flowParams[1],
                state.flowParams[2],
                state.flowParams[3],
            )
            shader.setFloatUniform("u_angleJitter", state.angleJitter)
            shader.setFloatUniform("u_enableLightWave", if (state.lightWave) 1f else 0f)
            shader.setFloatUniform("u_enableDithering", if (state.dithering) 1f else 0f)
            shader.setFloatUniform("u_bass", state.bassLevel)
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }.onFailure {
            // 画失败一次就永久降档到纯色，避免每帧重试造成持续闪烁
            onRendererFailed()
        }
    }
}

/**
 * 兜底档（低版本设备 / 着色器不可用）：**封面模糊背景（Cloudy）+ 调色板主色底**。
 *
 * - 底层 [SolidIsolationCanvas]：调色板主色的不透明纯色，封面加载前 / 失败时的兜底，
 *   颜色过渡仍以低帧率推进，换歌时不会硬切；
 * - 上层封面模糊图：复用取色管线解码好的位图（256px 放大 + Cloudy 模糊）。内容是静态的，
 *   只在换封面时算一次模糊 —— API 31+ 走硬件 RenderEffect，更低版本由 Cloudy 落到
 *   CPU 实现（https://github.com/skydoves/Cloudy），低版本也有真正意义上的「绚丽背景」。
 * - 轻微压暗与 AGSL 档的取色压暗（OkLab L×0.78）观感对齐，保证歌词 / 控件对比度。
 */
@Composable
private fun FallbackIsolationBackground(
    state: IsolationBackgroundState,
    albumArtUri: String?,
    coverBitmap: Bitmap?,
    modifier: Modifier,
) {
    Box(modifier) {
        SolidIsolationCanvas(state, Modifier.fillMaxSize())
        if (coverBitmap != null && !albumArtUri.isNullOrBlank()) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 放大一点：模糊（RenderEffect / CPU 采样）在边缘会采到透明，
                        // 不放大四周会有一圈发暗的边
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
                    .cloudy(FALLBACK_BLUR_RADIUS),
            )
            // ⚡ 压暗遮罩随封面明度自适应（亮封面压狠些、暗封面几乎不压），
            //    与取色的 1s OkLab 过渡同步渐变，换歌不会跳。
            val scrimAlpha by animateFloatAsState(
                targetValue = state.fallbackScrimAlpha,
                animationSpec = tween(durationMillis = PALETTE_TRANSITION_MS.toInt()),
                label = "isolationFallbackScrim",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
    }
}

/** 兜底档底色：调色板主色的不透明纯色（也作为模糊封面加载前的占位）。 */
@Composable
private fun SolidIsolationCanvas(
    state: IsolationBackgroundState,
    modifier: Modifier,
) {
    val driver = rememberIsolationFrameDriver(
        baseFpsCap = SOLID_FPS_CAP,
        lowPowerFpsCap = SOLID_LOW_POWER_FPS_CAP,
        isLowPower = { false },
        lastActivityNanos = { System.nanoTime() },
    ) { delta ->
        // 只推进 OkLab 调色板过渡（换歌时的 1s 渐变），不喂低频能量
        state.updateColorBuffer(delta)
        0f
    }

    Canvas(modifier) {
        driver.frameTick
        drawIsolationFallbackBase(state.colorBuffer)
    }
}

/**
 * 从 Compose 侧取 Hilt 单例 [AudioVisualizer] 的入口点（绚丽背景跟随歌曲律动用）。
 * 与项目里 Bilibili 系列组件相同的 EntryPointAccessors 模式。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
private interface IsolationAudioEntryPoint {
    fun audioVisualizer(): AudioVisualizer
}
