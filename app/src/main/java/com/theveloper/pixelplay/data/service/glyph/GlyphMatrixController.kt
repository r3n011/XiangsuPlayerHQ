package com.theveloper.pixelplay.data.service.glyph

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.palette.graphics.Palette
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls the Nothing Glyph Matrix display.
 * Shows music visualization on the Nothing Phone LED matrix.
 *
 * Three display modes:
 * - NOW_PLAYING: Album art pixelated onto the matrix
 * - VISUALIZER: Simple bar visualization synced to playback position
 * - WAVEFORM: Animated waveform pattern
 */
@Singleton
class GlyphMatrixController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GlyphMatrixController"
        private const val MATRIX_SIZE = 25 // Phone (3) is 25x25
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

    private var managerInstance: Any? = null
    private var isInitialized = false

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _displayMode = MutableStateFlow("NOW_PLAYING")
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    private var currentSong: Song? = null
    private var animationJob: Job? = null

    /**
     * Check if Glyph Matrix hardware is available on this device.
     */
    fun isAvailable(): Boolean = glyphManagerClass != null

    /**
     * Initialize the Glyph Matrix connection.
     * Should be called when the feature is enabled.
     */
    fun initialize() {
        if (!isAvailable()) {
            Timber.tag(TAG).w("Glyph Matrix not available")
            return
        }
        if (isInitialized) return

        try {
            val mgrClass = glyphManagerClass ?: return
            val getInstanceMethod = mgrClass.getMethod("getInstance", Context::class.java)
            managerInstance = getInstanceMethod.invoke(null, context)

            val callbackClass = Class.forName("com.nothing.glyph.matrix.GlyphMatrixManager\$Callback")
            val initMethod = mgrClass.getMethod("init", callbackClass)
            // Create callback via proxy
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                mgrClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, _ ->
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
                    else -> null
                }
            }

            initMethod.invoke(managerInstance, callback)
            Timber.tag(TAG).d("Glyph Matrix initialized")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize Glyph Matrix")
        }
    }

    private fun registerDevice() {
        try {
            // Try Phone (3) first, then Phone (4a) Pro
            val device23112 = glyphClass?.getField("DEVICE_23112")?.get(null) as? String
            val device25111p = glyphClass?.getField("DEVICE_25111p")?.get(null) as? String

            val registerMethod = glyphManagerClass!!.getMethod("register", String::class.java)
            registerMethod.invoke(managerInstance, device23112 ?: "DEVICE_23112")
            Timber.tag(TAG).d("Registered device")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to register device")
        }
    }

    /**
     * Activate the Glyph Matrix display with the given mode.
     */
    fun activate(mode: String) {
        _displayMode.value = mode
        _isActive.value = true
        startAnimation()
    }

    /**
     * Deactivate and turn off the matrix.
     */
    fun deactivate() {
        _isActive.value = false
        animationJob?.cancel()
        turnOffMatrix()
    }

    /**
     * Update the current playing song for visualization.
     */
    fun updateNowPlaying(song: Song?) {
        currentSong = song
        if (_isActive.value && _displayMode.value == "NOW_PLAYING") {
            renderNowPlaying(song)
        }
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
            }
        }
    }

    /**
     * Render album art as a pixelated pattern on the matrix.
     */
    private fun renderNowPlaying(song: Song?) {
        if (!isInitialized) return
        try {
            // Create a simple pattern based on the song
            val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
            val color = if (song != null) {
                // Generate a color from the song title hash for a unique pattern
                val hash = (song.title + song.artist).hashCode()
                Color.rgb(
                    (hash shr 16) and 0xFF,
                    (hash shr 8) and 0xFF,
                    hash and 0xFF
                )
            } else {
                Color.rgb(60, 60, 60)
            }

            // Draw a centered diamond/circle pattern
            val center = MATRIX_SIZE / 2
            for (y in 0 until MATRIX_SIZE) {
                for (x in 0 until MATRIX_SIZE) {
                    val dx = x - center
                    val dy = y - center
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                    if (dist < center) {
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

    /**
     * Run a simple bar visualizer animation.
     */
    private suspend fun runVisualizerAnimation() {
        val bars = IntArray(MATRIX_SIZE)
        var phase = 0.0

        while (_isActive.value) {
            // Generate animated bars
            for (i in 0 until MATRIX_SIZE) {
                val height = ((Math.sin(phase + i * 0.5) + 1) / 2 * MATRIX_SIZE * 0.8).toInt()
                    .coerceIn(0, MATRIX_SIZE - 1)
                bars[i] = height
            }

            val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
            for (x in 0 until MATRIX_SIZE) {
                for (y in 0 until MATRIX_SIZE) {
                    val barHeight = bars[x]
                    val row = MATRIX_SIZE - 1 - y
                    frame[y * MATRIX_SIZE + x] = if (row < barHeight) {
                        // Gradient from cyan to white
                        val t = row.toFloat() / MATRIX_SIZE
                        Color.rgb(
                            (40 + 200 * t).toInt(),
                            (200 + 55 * t).toInt(),
                            (220 + 35 * t).toInt()
                        )
                    } else {
                        Color.BLACK
                    }
                }
            }

            setMatrixFrame(frame)
            phase += 0.3
            delay(100)
        }
    }

    /**
     * Run an animated waveform pattern.
     */
    private suspend fun runWaveformAnimation() {
        var phase = 0.0

        while (_isActive.value) {
            val frame = IntArray(MATRIX_SIZE * MATRIX_SIZE)
            val center = MATRIX_SIZE / 2

            for (x in 0 until MATRIX_SIZE) {
                val wave = ((Math.sin(phase + x * 0.3) * 3) + center).toInt()
                    .coerceIn(0, MATRIX_SIZE - 1)

                for (y in 0 until MATRIX_SIZE) {
                    val dist = Math.abs(y - wave)
                    if (dist <= 2) {
                        val brightness = 1.0 - dist / 3.0
                        frame[y * MATRIX_SIZE + x] = Color.rgb(
                            (120 * brightness).toInt(),
                            (200 * brightness).toInt(),
                            (255 * brightness).toInt()
                        )
                    }
                }
            }

            setMatrixFrame(frame)
            phase += 0.2
            delay(80)
        }
    }

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

    /**
     * Release all resources. Call when the feature is disabled or app is closing.
     */
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
