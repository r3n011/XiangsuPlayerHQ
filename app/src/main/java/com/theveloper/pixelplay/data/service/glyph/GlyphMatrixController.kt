package com.theveloper.pixelplay.data.service.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.visualizer.AudioVisualizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls the Nothing Glyph Matrix display.
 * Shows music visualization on the Nothing Phone LED matrix.
 *
 * Display modes:
 * - NOW_PLAYING: Album-art-style pixelated pattern (static, from song title)
 * - VISUALIZER: Real-FTT spectrum bars (low→high, left→right)
 * - WAVEFORM: Spectrum-driven mirrored waveform
 * - RING: Spectrum-driven radiating ring around the center
 * - PULSE: Beat-driving pulse rings
 *
 * VISUALIZER / WAVEFORM / RING / PULSE are all driven by the real audio spectrum
 * captured in [AudioVisualizer]; they fall back to a gentle idle breathing pattern
 * when no playback is present.
 */
@Singleton
class GlyphMatrixController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioVisualizer: AudioVisualizer
) {
    companion object {
        private const val TAG = "GlyphMatrixController"
        private const val MATRIX_SIZE = 25 // Phone (3) is 25x25
        private const val FRAME_DELAY_MS = 33L
        // Default faint idle color (grey) so the matrix is never fully dark.
        private val IDLE_BASE = Color.rgb(70, 70, 70)
        /** 点阵歌名白字 */
        private val WHITE = Color.rgb(240, 240, 240)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Try to load Glyph Matrix SDK classes dynamically
    private val glyphManagerClass: Class<*>? by lazy {
        try {
            Class.forName("com.nothing.glyph.matrix.GlyphMatrixManager")
        } catch (_: ClassNotFoundException) {
            Timber.tag(TAG).d("Glyph Matrix SDK not available on this device")
            null
        }
    }

    private val glyphClass: Class<*>? by lazy {
        try {
            Class.forName("com.nothing.glyph.Glyph")
        } catch (_: ClassNotFoundException) { null }
    }

    /** 从 GlyphToy 反射读取背部按钮长按事件常量（EVENT_CHANGE） */
    private fun glyphToyEventChange(): String? = runCatching {
        Class.forName("com.nothing.glyph.GlyphToy")
            .getField("EVENT_CHANGE")
            .get(null)?.toString()
    }.getOrNull()

    private var managerInstance: Any? = null
    private var isInitialized = false

    /** 背部按钮长按回调：由外部（PlayerViewModel）注入，实现暂停/继续 */
    @Volatile
    var onTogglePlayback: (() -> Unit)? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _displayMode = MutableStateFlow("NOW_PLAYING")
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    private var currentSong: Song? = null
    private var animationJob: Job? = null

    /** 采样累积，供 idle 呼吸动画使用 */
    private var phase = 0.0
    private var lastLevels = FloatArray(AudioVisualizer.BANDS) { 0f }

    // ── ARTWORK（封面）状态 ──
    private var isPlaying = false
    private var artworkBitmap: Bitmap? = null
    private var artworkSongKey: String? = null
    private var logoBitmap: Bitmap? = null
    private var artworkAngle = 0.0

    // ── 播放进度（用于 PROGRESS 模式） ──
    private var positionMs = 0L
    private var durationMs = 0L

    // ── 滚动歌名（用于 TITLE 模式） ──
    private var titleScrollX = 0f

    fun isAvailable(): Boolean = glyphManagerClass != null

    fun initialize() {
        if (!isAvailable()) return
        if (isInitialized) return
        try {
            val mgrClass = glyphManagerClass ?: return
            val getInstanceMethod = mgrClass.getMethod("getInstance", Context::class.java)
            managerInstance = getInstanceMethod.invoke(null, context)

            val callbackClass = Class.forName("com.nothing.glyph.matrix.GlyphMatrixManager\$Callback")
            val initMethod = mgrClass.getMethod("init", callbackClass)
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                mgrClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onServiceConnected" -> {
                        Timber.tag(TAG).d("Glyph service connected")
                        registerDevice()
                        isInitialized = true
                        true
                    }
                    "onServiceDisconnected" -> {
                        Timber.tag(TAG).d("Glyph service disconnected")
                        isInitialized = false
                        true
                    }
                    "onGlyphEvent" -> {
                        val event = args?.getOrNull(0)?.toString() ?: ""
                        // GlyphToy.EVENT_CHANGE：背部按钮长按 → 暂停/继续
                        val backEvent = glyphToyEventChange()
                        if (event == backEvent || event == "EVENT_CHANGE" || event == "CHANGE") {
                            Timber.tag(TAG).d("Glyph back button long-press: toggling playback")
                            runCatching { onTogglePlayback?.invoke() }
                        } else {
                            Timber.tag(TAG).d("Glyph event: $event")
                        }
                        true
                    }
                    else -> null
                }
            }
            initMethod.invoke(managerInstance, callback)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize Glyph Matrix")
        }
    }

    private fun registerDevice() {
        try {
            val device23112 = glyphClass?.getField("DEVICE_23112")?.get(null) as? String
            val device25111p = glyphClass?.getField("DEVICE_25111p")?.get(null) as? String
            val registerMethod = glyphManagerClass!!.getMethod("register", String::class.java)
            registerMethod.invoke(managerInstance, device23112 ?: "DEVICE_23112")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to register device")
        }
    }

    fun activate(mode: String) {
        _displayMode.value = mode
        _isActive.value = true
        startAnimation()
    }

    fun deactivate() {
        _isActive.value = false
        animationJob?.cancel()
        turnOffMatrix()
    }

    fun updateNowPlaying(song: Song?) {
        if (currentSong?.id != song?.id) {
            // 切歌后清掉封面缓存，确保下一帧重新加载
            artworkBitmap = null
            artworkSongKey = null
        }
        currentSong = song
        if (_isActive.value && _displayMode.value == "NOW_PLAYING") {
            renderNowPlaying(song)
        }
    }

    /** 同步播放/暂停状态，用于 ARTWORK 封面旋转与冻结 */
    fun updatePlayState(playing: Boolean) {
        isPlaying = playing
    }

    /** 同步播放进度，用于 PROGRESS 模式 */
    fun updateProgress(position: Long, duration: Long) {
        positionMs = position
        durationMs = duration
    }

    fun setDisplayMode(mode: String) {
        _displayMode.value = mode
        if (_isActive.value) {
            animationJob?.cancel()
            startAnimation()
        }
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            when (_displayMode.value) {
                "NOW_PLAYING" -> renderNowPlaying(currentSong)
                "VISUALIZER" -> runVisualizerAnimation()
                "WAVEFORM" -> runWaveformAnimation()
                "RING" -> runRingAnimation()
                "PULSE" -> runPulseAnimation()
                "ARTWORK" -> runArtworkAnimation()
                "TITLE" -> runTitleAnimation()
                "PROGRESS" -> runProgressAnimation()
            }
        }
    }

    // ── NOW_PLAYING：由歌曲标题 hash 生成静态图案 ──
    private fun renderNowPlaying(song: Song?) {
        if (!isInitialized && !isPreviewEnabled()) return
        try {
            val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
            val color = if (song != null) {
                val hash = (song.title + song.artist).hashCode()
                Color.rgb(
                    (hash shr 16) and 0xFF,
                    (hash shr 8) and 0xFF,
                    hash and 0xFF
                )
            } else IDLE_BASE

            val center = MATRIX_SIZE / 2
            for (y in 0 until MATRIX_SIZE) {
                for (x in 0 until MATRIX_SIZE) {
                    val dx = x - center
                    val dy = y - center
                    val dist = sqrt((dx * dx + dy * dy).toDouble())
                    if (dist < center.toDouble()) {
                        val brightness = (1.0 - dist / center).coerceIn(0.2, 1.0)
                        frame[y * MATRIX_SIZE + x] = Color.rgb(
                            (Color.red(color) * brightness).toInt(),
                            (Color.green(color) * brightness).toInt(),
                            (Color.blue(color) * brightness).toInt()
                        )
                    }
                }
            }
            setMatrixFrame(frame)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to render now playing")
        }
    }

    // ── 频谱读取 ──
    private fun snapshotLevels(): FloatArray {
        val raw = audioVisualizer.levels.value
        val out = FloatArray(raw.size)
        for (i in raw.indices) {
            val smoothed = raw[i]
            // 慢退化：信号消失时电平缓慢归零，避免闪烁
            val prev = lastLevels[i]
            val decayed = prev * 0.6f
            val v = if (smoothed > decayed) smoothed else decayed
            out[i] = (v * 1.6f).coerceIn(0f, 1f)
            lastLevels[i] = out[i]
        }
        return out
    }

    private fun hasSignal(lv: FloatArray): Boolean {
        if (currentSong == null) return false
        var sum = 0f
        for (v in lv) sum += v
        return sum / lv.size > 0.03f
    }

    // 简化索引：对 25 段频谱取谁作为第 x 列（低频靠左）
    private fun levelAt(lv: FloatArray, col: Int): Float {
        val idx = ((col.toFloat() / (MATRIX_SIZE - 1)) * (AudioVisualizer.BANDS - 1)).toInt()
            .coerceIn(0, AudioVisualizer.BANDS - 1)
        return lv[idx]
    }

    // ── idle 呼吸：无声时柔和明暗脉动 ──
    private fun idleFrame(): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val center = MATRIX_SIZE / 2
        phase += 0.04
        val breathe = (sin(phase).toFloat() + 1f) / 2f
        val radius = 3 + breathe * 4f
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val dx = x - center
                val dy = y - center
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val v = (1f - (dist - radius).coerceIn(0f, 3f) / 3f).coerceIn(0f, 1f)
                val b = (v * (0.25f + 0.75f * breathe)).toInt().coerceIn(0, 255)
                frame[y * MATRIX_SIZE + x] = Color.rgb(b, b, b * 3 / 4)
            }
        }
        return frame
    }

    // ── VISUALIZER：真实频谱柱 ──
    private fun renderBars(lv: FloatArray): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (x in 0 until MATRIX_SIZE) {
            val level = levelAt(lv, x)
            val height = (level * (MATRIX_SIZE - 4)).toInt().coerceIn(0, MATRIX_SIZE - 4)
            for (y in 0 until MATRIX_SIZE) {
                if (y < (MATRIX_SIZE - 1) - height) continue
                val t = y.toFloat() / MATRIX_SIZE
                frame[y * MATRIX_SIZE + x] = spectrumColor(t)
            }
        }
        return frame
    }

    private fun runVisualizerAnimation() {
        while (_isActive.value) {
            val lv = snapshotLevels()
            setMatrixFrame(if (hasSignal(lv)) renderBars(lv) else idleFrame())
            delaySafely()
        }
    }

    // ── WAVEFORM：频谱驱动镜像波形 ──
    private fun renderWaveform(lv: FloatArray): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val center = (MATRIX_SIZE - 1) / 2
        // 对每一列取频谱电平，映射为波峰位置，上下对称
        val peaks = IntArray(MATRIX_SIZE)
        for (x in 0 until MATRIX_SIZE) {
            val level = levelAt(lv, x)
            peaks[x] = (level * (MATRIX_SIZE / 2 - 1)).toInt().coerceIn(0, MATRIX_SIZE / 2 - 1)
        }
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val dist = abs(y - center)
                if (dist <= peaks[x]) {
                    val t = dist.toFloat() / (MATRIX_SIZE / 2)
                    frame[y * MATRIX_SIZE + x] = spectrumColor(t)
                }
            }
        }
        return frame
    }

    private fun runWaveformAnimation() {
        while (_isActive.value) {
            val lv = snapshotLevels()
            setMatrixFrame(if (hasSignal(lv)) renderWaveform(lv) else idleFrame())
            delaySafely()
        }
    }

    // ── RING：旋转的频谱辐射圆（满屏大圆环） ──
    private fun renderRing(lv: FloatArray): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val maxR = (MATRIX_SIZE - 1) / 2f
        val cols = AudioVisualizer.BANDS
        val rot = phase
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val dx = x - maxR
                val dy = y - maxR
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < 1.0 || dist > maxR) continue
                var ang = (atan2(dy, dx) + PI) / (2 * PI)
                ang += rot
                ang -= floor(ang)
                val fpos = ang * cols
                val i0 = fpos.toInt().coerceIn(0, cols - 1)
                val i1 = (i0 + 1).coerceIn(0, cols - 1)
                val frac = (fpos - fpos.toInt()).toFloat()
                val l = (lv[i0] * (1f - frac) + lv[i1] * frac).coerceIn(0f, 1f)
                val radius = 0.5 + l * maxR
                if (dist <= radius) {
                    // 中心暗、边缘亮，形成向外辐射的大圆环
                    val t = (dist / maxR).toFloat()
                    val b = (0.12f + t * 0.88f) * (0.4f + 0.6f * l)
                    frame[y * MATRIX_SIZE + x] = spectrumColor(b)
                }
            }
        }
        phase += 0.05
        return frame
    }

    private fun runRingAnimation() {
        while (_isActive.value) {
            val lv = snapshotLevels()
            setMatrixFrame(if (hasSignal(lv)) renderRing(lv) else idleFrame())
            delaySafely()
        }
    }

    // ── PULSE：低频鼓点驱动的脉冲扩散 ──
    private fun renderPulse(lv: FloatArray): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val center = (MATRIX_SIZE - 1) / 2
        // 取低频能量作为鼓点脉冲
        var bass = 0f
        val bassCount = min(6, AudioVisualizer.BANDS)
        for (i in 0 until bassCount) bass += lv[i]
        bass /= bassCount
        val radius = 1 + (bass * 10f).toInt()

        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val dx = x - center
                val dy = y - center
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                // 多层同心圆：半径与亮度随低频起伏
                val ringPhase = floor(dist) - radius.toFloat()
                val edge = abs(ringPhase)
                val v = (1f - edge).coerceIn(0f, 1f) * (0.35f + 0.65f * bass)
                if (v > 0f) {
                    frame[y * MATRIX_SIZE + x] = spectrumColor(v)
                }
            }
        }
        return frame
    }

    private fun runPulseAnimation() {
        while (_isActive.value) {
            val lv = snapshotLevels()
            setMatrixFrame(if (hasSignal(lv)) renderPulse(lv) else idleFrame())
            delaySafely()
        }
    }

    // ── ARTWORK：点阵显示专辑封面（播放旋转 / 暂停冻结 / 无封面用 Logo） ──
    private fun renderArtwork(): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val src = ensureArtwork() ?: return idleFrame()
        if (isPlaying) artworkAngle = (artworkAngle + 0.06) % (2 * PI)
        val rot = artworkAngle
        val cell = MATRIX_SIZE
        val cosR = cos(rot)
        val sinR = sin(rot)
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val u = (x + 0.5f) / cell - 0.5f
                val v = (y + 0.5f) / cell - 0.5f
                val ux = (u * cosR - v * sinR) * 2f
                val uy = (u * sinR + v * cosR) * 2f
                val sx = ((ux + 1f) / 2f * src.width).toInt().coerceIn(0, src.width - 1)
                val sy = ((uy + 1f) / 2f * src.height).toInt().coerceIn(0, src.height - 1)
                // 封面转黑白：取灰度亮度，在点阵上以黑白呈现
                val c = runCatching { src.getPixel(sx, sy) }.getOrDefault(Color.BLACK)
                val g = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
                    .coerceIn(0, 255)
                frame[y * MATRIX_SIZE + x] = Color.rgb(g, g, g)
            }
        }
        return frame
    }

    private fun runArtworkAnimation() {
        while (_isActive.value) {
            setMatrixFrame(renderArtwork())
            delaySafely()
        }
    }

    // ── TITLE：点阵滚动显示歌名 ──
    private fun renderTitle(): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val title = currentSong?.title?.takeIf { it.isNotBlank() } ?: "PixelPlay"
        titleScrollX = (titleScrollX - 0.25f)
        // 文本总宽度：字符宽3 + 间距1
        val textWidth = title.length * 4
        // 取模滚动，保证无缝循环
        val total = textWidth + MATRIX_SIZE
        val scroll = ((titleScrollX % total) + total) % total
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val idx = y * MATRIX_SIZE + x
                val charRow = (y - 9) / 5
                // 字符垂直居中（25 行，行高 5 → 上下各留 10/2=5）
                if (y < (MATRIX_SIZE - 5) / 2 || y >= (MATRIX_SIZE + 5) / 2) continue
                val localY = y - (MATRIX_SIZE - 5) / 2
                val px = ((x + scroll) % total).toInt()
                if (px >= textWidth) continue
                val charIdx = px / 4
                val colInChar = px % 4
                if (colInChar == 3) continue // 间距列
                val c = title.getOrNull(charIdx) ?: continue
                val lit = glyphChar(c)[localY][colInChar]
                if (lit) frame[idx] = WHITE
            }
        }
        return frame
    }

    private fun runTitleAnimation() {
        while (_isActive.value) {
            setMatrixFrame(renderTitle())
            delaySafely()
        }
    }

    // ── PROGRESS：点阵大百分比显示（如 "50%"） ──
    private fun renderProgress(): IntArray {
        val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        val percent = if (durationMs > 0) (positionMs * 100 / durationMs).toInt() else 0
        val percentText = "${percent.coerceIn(0, 100)}%"
        val charW = 3
        val step = charW + 1
        val textWidth = percentText.length * step - 1
        val startX = (MATRIX_SIZE - textWidth) / 2
        val startY = (MATRIX_SIZE - 5) / 2
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val idx = y * MATRIX_SIZE + x
                if (x in startX until (startX + textWidth) && y in startY until (startY + 5)) {
                    val charIdx = (x - startX) / step
                    val colInChar = (x - startX) % step
                    if (colInChar < charW) {
                        val ch = percentText.getOrNull(charIdx)
                        if (ch != null) {
                            val glyph = glyphChar(ch)
                            val rowIdx = y - startY
                            if (glyph[rowIdx][colInChar]) {
                                frame[idx] = WHITE
                            }
                        }
                    }
                }
            }
        }
        return frame
    }

    private fun runProgressAnimation() {
        while (_isActive.value) {
            setMatrixFrame(renderProgress())
            delaySafely()
        }
    }

    // ── 封面位图加载 ──
    private fun ensureArtwork(): Bitmap? {
        val song = currentSong ?: return appLogoBitmap()
        val key = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: song.id
        if (artworkBitmap != null && artworkSongKey == key) return artworkBitmap
        val loaded = loadSongArtwork(song) ?: appLogoBitmap()
        if (loaded != null) {
            artworkBitmap = loaded
            artworkSongKey = key
        }
        return loaded
    }

    private fun loadSongArtwork(song: Song): Bitmap? {
        song.albumArtUriString?.takeIf { it.isNotBlank() }?.let { uriString ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { ins ->
                    BitmapFactory.decodeStream(ins, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                val f = File(song.path)
                if (f.exists() && f.canRead()) retriever.setDataSource(song.path)
                else retriever.setDataSource(context, Uri.parse(song.contentUriString))
                retriever.embeddedPicture?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    private fun appLogoBitmap(): Bitmap? {
        if (logoBitmap != null) return logoBitmap
        logoBitmap = runCatching {
            val d = context.packageManager.getApplicationIcon(context.packageName)
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 96
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 96
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            b
        }.getOrNull()
        return logoBitmap
    }

    // ── 色阶：白→青→蓝，随高度渐变 ──
    private fun spectrumColor(tNorm: Float): Int {
        val t = tNorm.coerceIn(0f, 1f)
        val r = (60 + 150 * t).toInt()
        val g = (180 + 70 * t).toInt()
        val b = (255 - 90 * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun delaySafely() {
        Thread.sleep(FRAME_DELAY_MS)
    }

    // 预览模式下（未初始化硬件）也允许本地渲染（供设置页预览使用，下方 setMatrixFrame 会显示落地）
    private fun isPreviewEnabled(): Boolean = true

    private fun setMatrixFrame(colors: IntArray) {
        if (!isInitialized || managerInstance == null) return
        try {
            val setAppFrameMethod = glyphManagerClass!!.getMethod("setAppMatrixFrame", IntArray::class.java)
            setAppFrameMethod.invoke(managerInstance, colors)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set matrix frame")
        }
    }

    private fun turnOffMatrix() {
        if (!isInitialized || managerInstance == null) return
        try {
            val closeMethod = glyphManagerClass!!.getMethod("closeAppMatrix")
            closeMethod.invoke(managerInstance)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to turn off matrix")
        }
    }

    fun release() {
        deactivate()
        try {
            if (isInitialized && managerInstance != null) {
                val unInitMethod = glyphManagerClass!!.getMethod("unInit")
                unInitMethod.invoke(managerInstance)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to release Glyph Matrix")
        }
        managerInstance = null
        isInitialized = false
    }
}

// ── 5×3 点阵字体（供 TITLE 模式滚动显示歌名用） ──

internal val GLYPH_FONT: Map<Char, Array<BooleanArray>> = run {
    val raw: Map<Char, List<String>> = mapOf(
        'A' to listOf("010", "101", "111", "101", "101"),
        'B' to listOf("110", "101", "111", "101", "110"),
        'C' to listOf("011", "100", "100", "100", "011"),
        'D' to listOf("110", "101", "101", "101", "110"),
        'E' to listOf("111", "100", "110", "100", "111"),
        'F' to listOf("111", "100", "110", "100", "100"),
        'G' to listOf("011", "100", "101", "101", "011"),
        'H' to listOf("101", "101", "111", "101", "101"),
        'I' to listOf("111", "010", "010", "010", "111"),
        'J' to listOf("001", "001", "001", "101", "010"),
        'K' to listOf("110", "101", "100", "101", "110"),
        'L' to listOf("100", "100", "100", "100", "111"),
        'M' to listOf("101", "111", "111", "101", "101"),
        'N' to listOf("101", "111", "111", "111", "101"),
        'O' to listOf("010", "101", "101", "101", "010"),
        'P' to listOf("110", "101", "110", "100", "100"),
        'Q' to listOf("010", "101", "101", "110", "011"),
        'R' to listOf("110", "101", "110", "101", "101"),
        'S' to listOf("011", "100", "010", "001", "110"),
        'T' to listOf("111", "010", "010", "010", "010"),
        'U' to listOf("101", "101", "101", "101", "010"),
        'V' to listOf("101", "101", "101", "010", "010"),
        'W' to listOf("101", "101", "111", "111", "101"),
        'X' to listOf("101", "010", "010", "010", "101"),
        'Y' to listOf("101", "101", "010", "010", "010"),
        'Z' to listOf("111", "001", "010", "100", "111"),
        '0' to listOf("010", "101", "101", "101", "010"),
        '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("110", "001", "010", "100", "111"),
        '3' to listOf("110", "001", "010", "001", "110"),
        '4' to listOf("101", "101", "111", "001", "001"),
        '5' to listOf("111", "100", "110", "001", "110"),
        '6' to listOf("010", "100", "110", "101", "010"),
        '7' to listOf("111", "001", "010", "100", "100"),
        '8' to listOf("010", "101", "010", "101", "010"),
        '9' to listOf("010", "101", "011", "001", "010"),
        ' ' to listOf("000", "000", "000", "000", "000"),
        '-' to listOf("000", "000", "111", "000", "000"),
        '_' to listOf("000", "000", "000", "000", "111"),
        '·' to listOf("000", "000", "011", "011", "000"),
        '.' to listOf("000", "000", "000", "010", "010"),
        ',' to listOf("000", "000", "000", "010", "100"),
        '!' to listOf("010", "010", "010", "000", "010"),
        '?' to listOf("110", "001", "010", "000", "010"),
        '%' to listOf("101", "001", "010", "100", "101")
    )
    val font = HashMap<Char, Array<BooleanArray>>()
    for ((ch, rows) in raw) {
        font[ch] = Array(5) { r -> BooleanArray(3) { c -> rows[r][c] == '1' } }
    }
    font
}

internal fun glyphChar(c: Char): Array<BooleanArray> {
    val key = if (c.isLowerCase()) c.uppercaseChar() else c
    return GLYPH_FONT[key] ?: Array(5) { BooleanArray(3) { true } }
}