package com.theveloper.pixelplay.presentation.components.isolation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 流动速度，AMLL Playground 的默认值。 */
internal const val ISOLATION_FLOW_SPEED = 0.2f

/** 低频能量驱动的流速增益：鼓点上流动时间最多加快 150%（液态跟随的主表现）。 */
private const val BASS_FLOW_GAIN = 1.5f

/** AGSL 路径帧率上限；CPU 路径减半以省电。 */
private const val AGSL_FPS_CAP = 60
private const val CPU_FPS_CAP = 30

/** 省电模式 / 没有音频在播（暂停、切歌间隙）时的降档帧率。 */
private const val AGSL_LOW_POWER_FPS_CAP = 30
private const val CPU_LOW_POWER_FPS_CAP = 15

/** 音频电平多久没更新就视为"没有在播放"（可视化约 60Hz 发布）。 */
private const val PLAYBACK_IDLE_NANOS = 700_000_000L

/**
 * AMLL Isolation 流体渐变背景。
 *
 * 封面在 CPU 一次性完成 直方图 → K-Means/八叉树择优 → 4 主色 → OkLab，
 * 之后每帧只是把少量 uniform 喂给单 pass 着色器 —— 不采样封面纹理、无几何网格。
 * 换封面 = 重掷随机布局 + 1s 的 OkLab 调色板过渡。
 *
 * API 33+ 走 AGSL RuntimeShader；更低版本走 CPU 低分辨率求值 + 双线性放大，
 * 观感与 GPU 路径几乎一致（Isolation 是极低频渐变，双线性放大的损失可忽略）。
 */
@Composable
internal fun IsolationBackground(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    lightWave: Boolean = true,
    dithering: Boolean = true,
) {
    val state = remember { IsolationBackgroundState(lightWave = lightWave, dithering = dithering) }

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    LaunchedEffect(albumArtUri) {
        if (albumArtUri.isNullOrBlank()) {
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
            state.applyDefaultColors()
            return@LaunchedEffect
        }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AgslIsolationCanvas(state, modifier, isLowPower, lastActivity)
    } else {
        CpuIsolationCanvas(state, modifier, isLowPower, lastActivity)
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
                //    画面依旧在流动，但 GPU（AGSL）/ CPU（低版本路径）占用直接减半。
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
    isLowPower: () -> Boolean,
    lastActivityNanos: () -> Long,
) {
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

    val shader = remember { RuntimeShader(ISOLATION_SHADER_SRC) }
    val paint = remember(shader) { Paint().apply { this.shader = shader } }

    Canvas(modifier) {
        driver.frameTick
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
    }
}

/**
 * CPU 路径（API < 33）：低分辨率逐帧求值 + 双线性放大到画布尺寸。
 * Isolation 是极低频的平滑渐变，双线性放大的视觉损失可以忽略。
 */
@Composable
private fun CpuIsolationCanvas(
    state: IsolationBackgroundState,
    modifier: Modifier,
    isLowPower: () -> Boolean,
    lastActivityNanos: () -> Long,
) {
    val driver = rememberIsolationFrameDriver(
        baseFpsCap = CPU_FPS_CAP,
        lowPowerFpsCap = CPU_LOW_POWER_FPS_CAP,
        isLowPower = isLowPower,
        lastActivityNanos = lastActivityNanos,
    ) { delta ->
        state.updateColorBuffer(delta)
        state.tickBass(delta)
        state.bassLevel
    }

    Spacer(
        modifier.drawWithCache {
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            val scale = IsolationCpuRenderer.LOW_RES_LONG_SIDE / max(w, h).toFloat()
            val lowW = max(8, (w * scale).toInt())
            val lowH = max(8, (h * scale).toInt())
            val bitmap = Bitmap.createBitmap(lowW, lowH, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(lowW * lowH)
            val image = bitmap.asImageBitmap()
            onDrawBehind {
                driver.frameTick
                IsolationCpuRenderer.render(
                    colorBuffer = state.colorBuffer,
                    randomValues = state.randomValues,
                    flowParams = state.flowParams,
                    angleJitter = state.angleJitter,
                    lightWave = state.lightWave,
                    dithering = state.dithering,
                    bass = state.bassLevel,
                    timeSec = driver.timeSec,
                    width = lowW,
                    height = lowH,
                    outPixels = pixels,
                )
                bitmap.setPixels(pixels, 0, lowW, 0, 0, lowW, lowH)
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w, h),
                    filterQuality = FilterQuality.High,
                )
            }
        },
    )
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
