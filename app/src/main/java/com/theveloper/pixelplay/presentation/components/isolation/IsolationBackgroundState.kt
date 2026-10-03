package com.theveloper.pixelplay.presentation.components.isolation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Isolation 流体渐变着色器（AGSL）。逐行移植自
 * applemusic-like-lyrics/packages/core/src/bg-render/isolation/isolation.frag.glsl。
 *
 * 与原版的差异只有两处：`gl_FragCoord` 改为 `main(fragCoord)` 入参；两个布尔
 * uniform 改用 float 表达（RuntimeShader 的布尔传参在部分 ROM 上行为不一致）。
 * 每帧常量（OkLab 四色、随机相位、流向参数）全部由 CPU 算好后以 uniform 下发。
 */
internal const val ISOLATION_SHADER_SRC: String = """
uniform float2 u_resolution;
uniform float u_time;
uniform float3 u_color0;
uniform float3 u_color1;
uniform float3 u_color2;
uniform float3 u_color3;
uniform float3 u_random;
uniform float4 u_flowParams;
uniform float u_angleJitter;
uniform float u_enableLightWave;
uniform float u_enableDithering;
uniform float u_bass;

const float PI = 3.141592653589793;

float2 rotatePoint(float2 point, float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return float2(
        point.x * cosine - point.y * sine,
        point.x * sine + point.y * cosine
    );
}

float2 gradientHash(float2 point) {
    return fract(
        sin(
            float2(
                dot(point, float2(127.1, 311.7)),
                dot(point, float2(269.5, 183.3))
            )
        ) * 43758.5453
    );
}

float gradientNoise(float2 point) {
    float2 cell = floor(point);
    float2 offset = fract(point);
    float2 eased = offset * offset * (3.0 - 2.0 * offset);
    float lower = mix(
        dot(-1.0 + 2.0 * gradientHash(cell), offset),
        dot(-1.0 + 2.0 * gradientHash(cell + float2(1.0, 0.0)), offset - float2(1.0, 0.0)),
        eased.x
    );
    float upper = mix(
        dot(-1.0 + 2.0 * gradientHash(cell + float2(0.0, 1.0)), offset - float2(0.0, 1.0)),
        dot(-1.0 + 2.0 * gradientHash(cell + float2(1.0, 1.0)), offset - float2(1.0, 1.0)),
        eased.x
    );
    return 0.5 + 0.5 * mix(lower, upper, eased.y);
}

float encodeSrgb(float channel) {
    return channel <= 0.0031308
        ? 12.92 * channel
        : 1.055 * pow(max(channel, 0.0), 1.0 / 2.4) - 0.055;
}

float3 okLabToSrgb(float3 color) {
    float lRoot = color.x + 0.3963377774 * color.y + 0.2158037573 * color.z;
    float mRoot = color.x - 0.1055613458 * color.y - 0.0638541728 * color.z;
    float sRoot = color.x - 0.0894841775 * color.y - 1.2914855480 * color.z;
    float l = lRoot * lRoot * lRoot;
    float m = mRoot * mRoot * mRoot;
    float s = sRoot * sRoot * sRoot;
    float3 linearColor = float3(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    );
    return float3(
        encodeSrgb(linearColor.r),
        encodeSrgb(linearColor.g),
        encodeSrgb(linearColor.b)
    );
}

float3 applyLightWave(float3 okLabColor, float2 uv) {
    float2 point = -1.0 + 1.5 * uv;
    float x = point.x;
    float y = point.y;
    float time = u_time * 0.2;
    float yPhase = y / 0.3;
    float xPhase = x / 0.2;
    float timeWarp = cos(sin(time) * 2.0) * 0.1;
    float movement = (x + y) * 0.001 + timeWarp + sin(x * 0.01);
    float wave1 =
        sin(yPhase + 2.0 * time + u_random.x) * 0.5 -
        yPhase -
        xPhase * 0.5;
    float wave2 = cos(
        wave1 +
            sin(movement + time) +
            sin(y * 0.025 + time) +
            sin((x + y) * 0.01) * 3.0 +
            u_random.y
    );
    float wave3 = abs(
        sin(
            wave2 +
                cos(yPhase + time + xPhase + wave2) +
                cos(xPhase) +
                sin(x * 0.001) +
                u_random.z
        )
    );
    // ⚡ 低频能量同时增强光波幅度：鼓点上明度波纹更明显（液态跟随的一部分）
    //    基线从 1.1 收到 0.97：光波不再整体提亮，而是围绕取色后的暗底做明暗起伏，
    //    保证压在背景上的歌词 / 控制按钮始终有对比度。
    okLabColor.x = clamp(
        okLabColor.x * (0.97 + clamp(u_bass, 0.0, 1.0) * 0.06 - 0.08 * wave3),
        0.0,
        1.0
    );
    return okLabToSrgb(okLabColor);
}

float interleavedGradientNoise(float2 position) {
    return fract(
        52.9829189 * fract(dot(position, float2(0.06711056, 0.00583715)))
    );
}

float3 screenSpaceDither(float2 screenPosition) {
    float2 position = screenPosition + u_random.xy * 97.0;
    float3 noise = float3(
        interleavedGradientNoise(position),
        interleavedGradientNoise(position + float2(17.0, 59.0)),
        interleavedGradientNoise(position + float2(71.0, 23.0))
    );
    return (noise - 0.5) / 255.0;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;
    vec2 basePoint = uv - 0.5;
    float degree = gradientNoise(
        float2(
            u_time * 0.1 + u_random.x * 0.07,
            basePoint.x * basePoint.y + u_random.y * 0.07
        )
    );
    float noiseAngle = ((degree - 0.5) * 720.0 + 180.0) * PI / 180.0;
    // ⚡ 律动：低频能量让形变场轻微扩张（鼓点脉动）。噪声输入使用未缩放的
    //    basePoint，缩放时布局/角度保持稳定，不会每帧抖动。
    float bass = clamp(u_bass, 0.0, 1.0);
    vec2 gradientPoint = rotatePoint(basePoint * (1.0 + bass * 0.10), noiseAngle + u_angleJitter);

    float frequency = u_flowParams.x;
    float amplitude = u_flowParams.y;
    float speed = u_time * u_flowParams.z;
    // ⚡ 液态跟随：低频能量直接推进波纹相位 —— 鼓点上液体"涌动"扭动，
    //    而不是整体缩放（流速本身的提升在 CPU 的时间推进里做，见 IsolationFrameDriver）。
    float bassPhase = bass * 2.6;
    gradientPoint.x += sin(gradientPoint.y * frequency + speed + bassPhase) / amplitude;
    gradientPoint.y +=
        sin(gradientPoint.x * frequency * 1.5 + speed + bassPhase) / (amplitude * 0.5);

    float rotatedX = rotatePoint(gradientPoint, u_flowParams.w).x;
    float horizontal = smoothstep(-0.3, 0.2, rotatedX);
    float3 okLabColor = mix(
        mix(u_color0, u_color1, horizontal),
        mix(u_color2, u_color3, horizontal),
        1.0 - smoothstep(-0.3, 0.5, gradientPoint.y)
    );
    float3 color = u_enableLightWave > 0.5
        ? applyLightWave(okLabColor, uv)
        : okLabToSrgb(okLabColor);

    if (u_enableDithering > 0.5) {
        color += screenSpaceDither(fragCoord);
    }

    // ⚡ 律动亮度脉冲：鼓点上画面轻微提亮（clamp 统一收在最后；辅助项，主表现是
    //    波纹相位推进与流速提升）。幅度从 0.12 收到 0.06，避免鼓点把背景顶得发白。
    color *= (1.0 + bass * 0.06);

    float3 clamped = clamp(color, 0.0, 1.0);
    return half4(clamped.r, clamped.g, clamped.b, 1.0);
}
"""

/** 换封面时调色板过渡的时长。 */
internal const val PALETTE_TRANSITION_MS = 1000f

/** 还没拿到封面时用的中性深色配色（sRGB [0,1]），与 AMLL 一致。 */
private val DEFAULT_COLORS = listOf(
    floatArrayOf(0.09f, 0.09f, 0.11f),
    floatArrayOf(0.13f, 0.13f, 0.16f),
    floatArrayOf(0.07f, 0.07f, 0.09f),
    floatArrayOf(0.11f, 0.11f, 0.13f),
)

private val DEFAULT_OKLAB_COLORS: FloatArray = FloatArray(12).also { out ->
    DEFAULT_COLORS.forEachIndexed { i, c -> srgbToOkLab(c).copyInto(out, i * 3) }
}

/**
 * Isolation 背景的渲染状态：随机布局参数、OkLab 四色与调色板过渡。
 * 与具体绘制后端（AGSL / CPU 低分辨率）无关，两者共用这一份状态。
 */
internal class IsolationBackgroundState(
    var lightWave: Boolean = true,
    var dithering: Boolean = true,
) {
    /** 当前帧四色的 OkLab 分量，四色首尾相接，直接作为 u_colors uniform。 */
    val colorBuffer = FloatArray(12)
    private val fromColors = FloatArray(12)
    private val toColors = FloatArray(12)
    private var transitionElapsedMs = PALETTE_TRANSITION_MS

    /** 随机相位（光波与渐变噪声用），每张封面重掷一次。 */
    val randomValues = FloatArray(3)

    /** x=波纹频率 y=波纹幅度 z=流速（带方向） w=渐变轴倾角（弧度）。 */
    val flowParams = FloatArray(4)
    var angleJitter = 0f
        private set

    /**
     * 低频能量（0..1，快攻慢放平滑后），驱动画面随鼓点轻微脉动。
     * 数据来自 AudioVisualizer 的频段电平，由 [IsolationBackground] 每帧喂入。
     */
    var bassEnergy = 0f
        private set
    private var lastBassUpdateMs = 0L

    /** 频段数据更新（约 60Hz）：上升快跟、下降缓慢释放，避免画面抖跳。 */
    fun updateBassEnergy(rawLevel: Float) {
        val target = rawLevel.coerceIn(0f, 1f)
        bassEnergy = if (target > bassEnergy) {
            bassEnergy + (target - bassEnergy) * 0.5f
        } else {
            bassEnergy + (target - bassEnergy) * 0.12f
        }
        lastBassUpdateMs = android.os.SystemClock.uptimeMillis()
    }

    /**
     * 用于渲染的律动强度：对平滑值开方提升中低能量 —— 频段电平自适应归一后
     * 鼓点值常在中低区间（0.3~0.6），直接使用观感不明显；开方后 0.3→0.55、
     * 0.9→0.95，弱拍也能看到律动、强拍不饱和。
     */
    val bassLevel: Float
        get() = kotlin.math.sqrt(bassEnergy)

    /**
     * 帧循环每帧调用：超过 250ms 没有新的频段数据（暂停 / 静音 / 采集停止）时
     * 缓慢释放到 0，避免画面停在"鼓点高亮"状态。
     */
    fun tickBass(frameDeltaMs: Float) {
        if (android.os.SystemClock.uptimeMillis() - lastBassUpdateMs <= 250L) return
        bassEnergy += (0f - bassEnergy) * (frameDeltaMs / 400f).coerceIn(0f, 1f)
        if (bassEnergy < 0.001f) bassEnergy = 0f
    }

    private var paletteOrder = intArrayOf(0, 1, 2, 3)

    init {
        rollRandomParameters()
        DEFAULT_OKLAB_COLORS.copyInto(fromColors)
        DEFAULT_OKLAB_COLORS.copyInto(toColors)
        DEFAULT_OKLAB_COLORS.copyInto(colorBuffer)
    }

    /** 重掷整张封面期间保持不变的随机参数，避免画面逐帧跳变。 */
    fun rollRandomParameters() {
        for (i in 0 until 3) randomValues[i] = kotlin.random.Random.nextFloat() * (2.0 * PI).toFloat()
        val direction = if (kotlin.random.Random.nextBoolean()) -1f else 1f
        flowParams[0] = (4.5f + kotlin.random.Random.nextFloat()) // lerp(4.5, 5.5)
        flowParams[1] = 22f + kotlin.random.Random.nextFloat() * 7f // lerp(22, 29)
        flowParams[2] = (0.65f + kotlin.random.Random.nextFloat() * 0.2f) * direction
        flowParams[3] = (-5f + (kotlin.random.Random.nextFloat() - 0.5f) * 12f) * (PI.toFloat() / 180f)
        angleJitter = (kotlin.random.Random.nextFloat() - 0.5f) * 0.3f
        paletteOrder = intArrayOf(0, 1, 2, 3)
        for (i in paletteOrder.indices.reversed()) {
            val j = kotlin.random.Random.nextInt(i + 1)
            val tmp = paletteOrder[i]
            paletteOrder[i] = paletteOrder[j]
            paletteOrder[j] = tmp
        }
    }

    /** 按当前过渡进度就地更新 [colorBuffer]，不产生任何中间数组。 */
    fun updateColorBuffer(frameDeltaMs: Float) {
        if (transitionElapsedMs < PALETTE_TRANSITION_MS) {
            transitionElapsedMs = min(PALETTE_TRANSITION_MS, transitionElapsedMs + frameDeltaMs)
        }
        if (transitionElapsedMs >= PALETTE_TRANSITION_MS) {
            toColors.copyInto(colorBuffer)
            return
        }
        val progress = transitionElapsedMs / PALETTE_TRANSITION_MS
        val eased = progress * progress * (3f - 2f * progress)
        for (i in colorBuffer.indices) {
            colorBuffer[i] = fromColors[i] + (toColors[i] - fromColors[i]) * eased
        }
    }

    /** 从当前这一帧的实际颜色接着往下过渡，连续换封面时颜色不会回跳。 */
    fun transitionTo(next: FloatArray) {
        updateColorBuffer(0f)
        colorBuffer.copyInto(fromColors)
        next.copyInto(toColors)
        transitionElapsedMs = 0f
    }

    /** 提取封面调色板（阻塞，建议在后台线程调用）并开始 1s 的 OkLab 过渡。 */
    fun applyPalette(palette: List<FloatArray>) {
        val next = FloatArray(12)
        for (i in 0 until 4) {
            val rgb = palette[paletteOrder[i]]
            val lab = srgbToOkLab(floatArrayOf(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f))
            // ⚡ 取色统一压暗：AMLL 原版把封面主色直接铺满全屏，亮部太刺眼，
            //    歌词 / 按钮压在上面时对比度不够。这里把 OkLab 的 L 压到深色区间，
            //    彩度略收一点（避免整屏糊成高饱和色块），并给暗部兜一个下限免得死黑。
            lab[0] = (lab[0] * 0.70f).coerceIn(0.045f, 0.56f)
            lab[1] *= 0.90f
            lab[2] *= 0.90f
            lab.copyInto(next, i * 3)
        }
        transitionTo(next)
    }

    fun applyDefaultColors() {
        transitionTo(DEFAULT_OKLAB_COLORS.copyOf())
    }
}

/* ============================== CPU 求值器 ============================== */

private fun fractF(x: Float) = x - kotlin.math.floor(x)

private fun gradientHashX(px: Float, py: Float): Float =
    fractF(sin(px * 127.1f + py * 311.7f) * 43758.5453f)

private fun gradientHashY(px: Float, py: Float): Float =
    fractF(sin(px * 269.5f + py * 183.3f) * 43758.5453f)

private fun gradientNoise(pointX: Float, pointY: Float): Float {
    val cellX = kotlin.math.floor(pointX)
    val cellY = kotlin.math.floor(pointY)
    val offsetX = pointX - cellX
    val offsetY = pointY - cellY
    val easedX = offsetX * offsetX * (3f - 2f * offsetX)
    val easedY = offsetY * offsetY * (3f - 2f * offsetY)
    val lower = mix(
        dot2(-1f + 2f * gradientHashX(cellX, cellY), -1f + 2f * gradientHashY(cellX, cellY), offsetX, offsetY),
        dot2(
            -1f + 2f * gradientHashX(cellX + 1f, cellY),
            -1f + 2f * gradientHashY(cellX + 1f, cellY),
            offsetX - 1f,
            offsetY,
        ),
        easedX,
    )
    val upper = mix(
        dot2(
            -1f + 2f * gradientHashX(cellX, cellY + 1f),
            -1f + 2f * gradientHashY(cellX, cellY + 1f),
            offsetX,
            offsetY - 1f,
        ),
        dot2(
            -1f + 2f * gradientHashX(cellX + 1f, cellY + 1f),
            -1f + 2f * gradientHashY(cellX + 1f, cellY + 1f),
            offsetX - 1f,
            offsetY - 1f,
        ),
        easedX,
    )
    return 0.5f + 0.5f * mix(lower, upper, easedY)
}

private fun dot2(gx: Float, gy: Float, ox: Float, oy: Float) = gx * ox + gy * oy

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun encodeSrgb(channel: Float): Float =
    if (channel <= 0.0031308f) {
        12.92f * channel
    } else {
        1.055f * max(channel, 0f).pow(1f / 2.4f) - 0.055f
    }

private fun okLabToSrgb(color: FloatArray, out: FloatArray) {
    val lRoot = color[0] + 0.3963377774f * color[1] + 0.2158037573f * color[2]
    val mRoot = color[0] - 0.1055613458f * color[1] - 0.0638541728f * color[2]
    val sRoot = color[0] - 0.0894841775f * color[1] - 1.2914855480f * color[2]
    val l = lRoot * lRoot * lRoot
    val m = mRoot * mRoot * mRoot
    val s = sRoot * sRoot * sRoot
    val lr = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val lg = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val lb = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
    out[0] = encodeSrgb(lr)
    out[1] = encodeSrgb(lg)
    out[2] = encodeSrgb(lb)
}

private fun interleavedGradientNoise(px: Float, py: Float): Float =
    fractF(52.9829189f * fractF(px * 0.06711056f + py * 0.00583715f))

/**
 * 取调色板里第 [index] 个主色的不透明 sRGB 分量（0..1，写入 [out] 的前三位）。
 *
 * 「绚丽背景」渲染失败时的兜底底色用它 —— 走的是与 [IsolationCpuRenderer] 完全相同的
 * OkLab → sRGB 变换，降级后的底色与正常渲染的取色一致，不会跳成另一种颜色。
 */
internal fun isolationPaletteSrgb(colorBuffer: FloatArray, index: Int, out: FloatArray) {
    val i = index.coerceIn(0, 3) * 3
    val lab = floatArrayOf(colorBuffer[i], colorBuffer[i + 1], colorBuffer[i + 2])
    okLabToSrgb(lab, out)
    out[0] = out[0].coerceIn(0f, 1f)
    out[1] = out[1].coerceIn(0f, 1f)
    out[2] = out[2].coerceIn(0f, 1f)
}

/**
 * Isolation 片元函数的 CPU 实现，与 [ISOLATION_SHADER_SRC] 逐行对应。
 * 输出低分辨率 ARGB 像素，绘制端双线性放大到全屏 —— Isolation 本身是
 * 极低频的平滑渐变，低分辨率求值 + 放大在视觉上与逐像素几乎无差。
 */
internal object IsolationCpuRenderer {
    const val LOW_RES_LONG_SIDE = 108

    fun render(
        colorBuffer: FloatArray,
        randomValues: FloatArray,
        flowParams: FloatArray,
        angleJitter: Float,
        lightWave: Boolean,
        dithering: Boolean,
        bass: Float,
        timeSec: Float,
        width: Int,
        height: Int,
        outPixels: IntArray,
    ) {
        val rndX = randomValues[0]
        val rndY = randomValues[1]
        val rndZ = randomValues[2]
        val frequency = flowParams[0]
        val amplitude = flowParams[1]
        val speed = timeSec * flowParams[2]
        val tilt = flowParams[3]
        val tiltCos = cos(tilt)
        val tiltSin = sin(tilt)
        val lwTime = timeSec * 0.2f
        val lwTimeWarp = cos(sin(lwTime) * 2f) * 0.1f
        // ⚡ 液态跟随：与 AGSL 着色器一致的相位推进 / 形变扩张 / 亮度脉冲系数
        val bassClamped = bass.coerceIn(0f, 1f)
        val bassScale = 1f + bassClamped * 0.10f
        val bassPhase = bassClamped * 2.6f
        val bassBrightness = 1f + bassClamped * 0.06f

        val ditherOffX = (randomValues[0] + randomValues[1] * 0.5f) * 97f
        val ditherOffY = (randomValues[1] + randomValues[2] * 0.5f) * 97f

        var index = 0
        val lab = FloatArray(3)
        val srgb = FloatArray(3)
        for (y in 0 until height) {
            val uvY = (y + 0.5f) / height
            for (x in 0 until width) {
                val uvX = (x + 0.5f) / width
                var gx = uvX - 0.5f
                var gy = uvY - 0.5f

                // 噪声输入使用未缩放的点，缩放时布局/角度保持稳定（与 shader 一致）
                val degree = gradientNoise(
                    timeSec * 0.1f + rndX * 0.07f,
                    gx * gy + rndY * 0.07f,
                )
                val noiseAngle = ((degree - 0.5f) * 720f + 180f) * (PI.toFloat() / 180f)
                val angle = noiseAngle + angleJitter
                val angleSin = sin(angle)
                val angleCos = cos(angle)
                val scaledX = gx * bassScale
                val scaledY = gy * bassScale
                gx = scaledX * angleCos - scaledY * angleSin
                gy = scaledX * angleSin + scaledY * angleCos

                gx += sin(gy * frequency + speed + bassPhase) / amplitude
                gy += sin(gx * frequency * 1.5f + speed + bassPhase) / (amplitude * 0.5f)

                val rot2X = gx * tiltCos - gy * tiltSin
                val horizontal = smoothstep(-0.3f, 0.2f, rot2X)
                val vertical = 1f - smoothstep(-0.3f, 0.5f, gy)

                for (k in 0 until 3) {
                    val top = mix(colorBuffer[k], colorBuffer[3 + k], horizontal)
                    val bottom = mix(colorBuffer[6 + k], colorBuffer[9 + k], horizontal)
                    lab[k] = mix(top, bottom, vertical)
                }

                if (lightWave) {
                    val px = -1f + 1.5f * uvX
                    val py = -1f + 1.5f * uvY
                    val yPhase = py / 0.3f
                    val xPhase = px / 0.2f
                    val movement = (px + py) * 0.001f + lwTimeWarp + sin(px * 0.01f)
                    val wave1 = sin(yPhase + 2f * lwTime + rndX) * 0.5f - yPhase - xPhase * 0.5f
                    val wave2 = cos(
                        wave1 +
                            sin(movement + lwTime) +
                            sin(py * 0.025f + lwTime) +
                            sin((px + py) * 0.01f) * 3f +
                            rndY,
                    )
                    val wave3 = abs(
                        sin(
                            wave2 +
                                cos(yPhase + lwTime + xPhase + wave2) +
                                cos(xPhase) +
                                sin(px * 0.001f) +
                                rndZ,
                        ),
                    )
                    // ⚡ 与 shader 保持一致：光波基线 0.97（不再整体提亮）
                    lab[0] = (lab[0] * (0.97f + bassClamped * 0.06f - 0.08f * wave3)).coerceIn(0f, 1f)
                }
                okLabToSrgb(lab, srgb)

                var r = srgb[0]
                var g = srgb[1]
                var b = srgb[2]
                if (dithering) {
                    val dpx = x + ditherOffX
                    val dpy = y + ditherOffY
                    r += (interleavedGradientNoise(dpx, dpy) - 0.5f) / 255f
                    g += (interleavedGradientNoise(dpx + 17f, dpy + 59f) - 0.5f) / 255f
                    b += (interleavedGradientNoise(dpx + 71f, dpy + 23f) - 0.5f) / 255f
                }

                // ⚡ 律动亮度脉冲（与 shader 一致）
                r *= bassBrightness
                g *= bassBrightness
                b *= bassBrightness

                val ir = (r.coerceIn(0f, 1f) * 255f).toInt()
                val ig = (g.coerceIn(0f, 1f) * 255f).toInt()
                val ib = (b.coerceIn(0f, 1f) * 255f).toInt()
                outPixels[index++] = (0xff shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
    }
}
