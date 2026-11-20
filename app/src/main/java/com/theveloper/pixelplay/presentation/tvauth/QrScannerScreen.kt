package com.theveloper.pixelplay.presentation.tvauth

import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val DECODE_HINTS = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
)

/**
 * CameraX 预览 + ZXing 解码。识别到二维码后回调原始字符串，由上层校验是否为
 * 电视授权地址；只解码二维码格式，避免误扫其它码。
 */
@Composable
internal fun QrScannerScreen(
    modifier: Modifier = Modifier,
    onPayload: (String) -> Unit,
    onCameraError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val lastAttempt = remember { AtomicLong(0L) }
    val reader = remember { MultiFormatReader().apply { setHints(DECODE_HINTS) } }

    DisposableEffect(Unit) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        if (delivered.get()) return@setAnalyzer
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAttempt.get() < 250L) return@setAnalyzer
                        lastAttempt.set(now)
                        decodeQr(proxy, reader)?.let { value ->
                            if (delivered.compareAndSet(false, true)) {
                                mainExecutor.execute { onPayload(value) }
                            }
                        }
                    } finally {
                        proxy.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure { error ->
                mainExecutor.execute { onCameraError(error.message ?: "相机启动失败") }
            }
        }
        future.addListener(listener, mainExecutor)
        onDispose {
            delivered.set(true)
            runCatching { if (future.isDone) future.get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

private fun decodeQr(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val image = proxy.image ?: return null
    val yPlane = image.planes.firstOrNull() ?: return null
    val buffer = yPlane.buffer
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride
    val data = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width) {
        buffer.rewind()
        buffer.get(data, 0, minOf(buffer.remaining(), data.size))
    } else {
        val row = ByteArray(rowStride.coerceAtLeast(width))
        var offset = 0
        for (y in 0 until height) {
            val rowStart = y * rowStride
            if (rowStart >= buffer.limit()) break
            buffer.position(rowStart)
            val count = minOf(rowStride, buffer.limit() - rowStart)
            buffer.get(row, 0, count)
            for (x in 0 until width) {
                if (offset >= data.size) break
                data[offset++] = row[x * pixelStride]
            }
        }
    }
    val rotated = rotateLuminance(data, width, height, proxy.imageInfo.rotationDegrees)
    val source = PlanarYUVLuminanceSource(
        rotated.data,
        rotated.width,
        rotated.height,
        0,
        0,
        rotated.width,
        rotated.height,
        false,
    )
    return runCatching {
        reader.reset()
        reader.decode(BinaryBitmap(HybridBinarizer(source)), DECODE_HINTS).text
    }.getOrNull()
}

private data class RotatedImage(val data: ByteArray, val width: Int, val height: Int)

private fun rotateLuminance(data: ByteArray, width: Int, height: Int, degrees: Int): RotatedImage {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return RotatedImage(data, width, height)
    val out = ByteArray(data.size)
    when (normalized) {
        90 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[x * height + (height - 1 - y)] = data[y * width + x]
                }
            }
            return RotatedImage(out, height, width)
        }
        180 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[(height - 1 - y) * width + (width - 1 - x)] = data[y * width + x]
                }
            }
            return RotatedImage(out, width, height)
        }
        270 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[(width - 1 - x) * height + y] = data[y * width + x]
                }
            }
            return RotatedImage(out, height, width)
        }
        else -> return RotatedImage(data, width, height)
    }
}
