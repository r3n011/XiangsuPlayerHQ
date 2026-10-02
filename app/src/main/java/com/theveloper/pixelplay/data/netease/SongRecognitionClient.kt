package com.theveloper.pixelplay.data.netease

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/** 一次识曲命中的结果：网易云歌曲 id + 命中的歌曲内偏移（毫秒）。 */
data class RecognizedSongMatch(
    val neteaseId: Long,
    val startTimeMs: Long,
)

/**
 * 听歌识曲（音频指纹匹配）。
 *
 * 流程与 MeloX 的 Android 实现一致：
 * 1. [AudioRecord] 以 8 kHz / 单声道 / PCM16 录制一段外放声音（默认 6 秒）；
 * 2. 用 assets 里的 `AudioFingerprint`（WebView + WASM，来自 neteaseapireborn，MIT）
 *    生成 shazam_v2 音频指纹 —— 纯 Kotlin 无法生成，必须借助这份 WASM；
 * 3. `GET /api/music/audio/match` 上传指纹（Base64，明文 GET，无需登录），
 *    返回 `data.result[].song.id` 与 `startTime`。
 *
 * 返回的只是 id，调用方再用 [NeteaseRepository.getNeteaseSongsByIds] 补全成 Song。
 */
@Singleton
class SongRecognitionClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : AutoCloseable {

    private val fingerprintRuntime = FingerprintRuntime(context.applicationContext)

    /** 录 [durationSeconds] 秒并识别。duration 限制在 3..15 秒（太长会让 GET query 过大被重置）。 */
    suspend fun recognize(durationSeconds: Int = DEFAULT_SECONDS): List<RecognizedSongMatch> {
        require(durationSeconds in 3..15) { "识曲时长需在 3~15 秒之间" }
        val samples = capture(durationSeconds)
        val fingerprint = fingerprintRuntime.generate(samples)
        return match(fingerprint, durationSeconds)
    }

    override fun close() {
        fingerprintRuntime.close()
    }

    /** 录制 8 kHz 单声道 PCM（VOICE_RECOGNITION 音源，带降噪），返回 [-1,1] 浮点采样。 */
    @SuppressLint("MissingPermission")
    private suspend fun capture(durationSeconds: Int): FloatArray = withContext(Dispatchers.IO) {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, encoding)
        if (minimum <= 0) throw IOException("当前设备不支持听歌识曲所需的 8 kHz 录音")

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channel)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimum * 2, 4_096))
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("麦克风初始化失败")
        }

        val target = durationSeconds * SAMPLE_RATE
        val pcm = ShortArray(target)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < target) {
                val count = recorder.read(pcm, offset, minOf(2_048, target - offset), AudioRecord.READ_BLOCKING)
                if (count < 0) throw IOException("录音读取失败：$count")
                if (count == 0) continue
                offset += count
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        if (offset < SAMPLE_RATE) throw IOException("没有收到足够的麦克风音频")
        FloatArray(offset) { pcm[it] / 32768f }
    }

    /** 指纹匹配：网易云明文 GET，不需要 Cookie / 登录。 */
    private suspend fun match(fingerprint: String, durationSeconds: Int): List<RecognizedSongMatch> =
        withContext(Dispatchers.IO) {
            if (fingerprint.isBlank()) throw IOException("音频指纹为空")
            val url = MATCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("sessionId", SESSION_ID)
                .addQueryParameter("algorithmCode", "shazam_v2")
                .addQueryParameter("duration", durationSeconds.toString())
                .addQueryParameter("rawdata", fingerprint)
                .addQueryParameter("times", "1")
                .addQueryParameter("decrypt", "1")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) PixelPlay/1.0")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("听歌识曲请求失败：HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(body) }.getOrNull()
                    ?: throw IOException("听歌识曲响应解析失败")
                if (root.optInt("code", 200) != 200) {
                    throw IOException(root.optString("message").ifBlank { "网易云听歌识曲服务返回错误" })
                }
                val candidates = root.optJSONObject("data")?.optJSONArray("result")
                    ?: return@withContext emptyList()
                val seen = HashSet<Long>()
                buildList {
                    for (index in 0 until candidates.length()) {
                        val candidate = candidates.optJSONObject(index) ?: continue
                        val songId = candidate.optJSONObject("song")?.optLong("id", -1L) ?: -1L
                        if (songId <= 0L || !seen.add(songId)) continue
                        add(
                            RecognizedSongMatch(
                                neteaseId = songId,
                                startTimeMs = candidate.optLong("startTime", 0L).coerceAtLeast(0L),
                            )
                        )
                    }
                }
            }
        }

    /**
     * 指纹生成运行时：WebView 加载 assets 里的 runtime.html，
     * 通过 [JavascriptInterface] 把结果/错误回传（与 MeloX 同款实现）。
     */
    private class FingerprintRuntime(private val context: Context) {
        private val generationMutex = Mutex()
        private var webView: WebView? = null

        @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
        suspend fun generate(samples: FloatArray): String = generationMutex.withLock {
            withContext(Dispatchers.Main) {
                val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                samples.forEach(bytes::putFloat)
                val pcm = Base64.encodeToString(bytes.array(), Base64.NO_WRAP)
                suspendCancellableCoroutine { continuation ->
                    val completed = AtomicBoolean(false)
                    val runtimeView = webView ?: WebView(context).also {
                        it.settings.javaScriptEnabled = true
                        it.settings.allowFileAccess = true
                        webView = it
                    }
                    fun finish(result: Result<String>) {
                        if (!completed.compareAndSet(false, true)) return
                        runtimeView.post {
                            runtimeView.stopLoading()
                            runtimeView.removeJavascriptInterface(JS_BRIDGE)
                        }
                        result.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
                    }
                    continuation.invokeOnCancellation {
                        if (completed.compareAndSet(false, true)) {
                            runtimeView.post {
                                runtimeView.stopLoading()
                                runtimeView.removeJavascriptInterface(JS_BRIDGE)
                            }
                        }
                    }
                    runtimeView.addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onFingerprint(value: String) = finish(
                                if (value.isBlank()) {
                                    Result.failure(IOException("音频指纹生成失败"))
                                } else {
                                    Result.success(value)
                                }
                            )

                            @JavascriptInterface
                            fun onError(message: String) = finish(
                                Result.failure(IOException("音频指纹生成失败：$message"))
                            )
                        },
                        JS_BRIDGE
                    )
                    runtimeView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val script = """
                            (async function() {
                              try {
                                const bytes = Uint8Array.from(atob('$pcm'), c => c.charCodeAt(0));
                                const input = new Float32Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 4));
                                const result = await GenerateFP(input);
                                $JS_BRIDGE.onFingerprint(String(result || ''));
                              } catch (error) {
                                $JS_BRIDGE.onError(String(error && (error.stack || error.message) || error));
                              }
                            })();
                            """.trimIndent()
                            view.evaluateJavascript(script, null)
                        }
                    }
                    runtimeView.loadUrl(RUNTIME_URL)
                }
            }
        }

        fun close() {
            val runtimeView = webView ?: return
            webView = null
            runtimeView.post {
                runCatching {
                    runtimeView.stopLoading()
                    runtimeView.removeJavascriptInterface(JS_BRIDGE)
                    runtimeView.destroy()
                }
                Timber.tag(TAG).d("FingerprintRuntime closed")
            }
        }
    }

    companion object {
        private const val TAG = "SongRecognition"

        /** 采样率：与指纹算法（以及网易云服务端）约定一致。 */
        const val SAMPLE_RATE = 8_000

        /** 默认录制时长（秒）；3~15 之间。 */
        const val DEFAULT_SECONDS = 6

        private const val MATCH_ENDPOINT = "https://interface.music.163.com/api/music/audio/match"
        private const val SESSION_ID = "0123456789abcdef"
        private const val JS_BRIDGE = "PixelPlayNative"
        private const val RUNTIME_URL = "file:///android_asset/AudioFingerprint/runtime.html"
    }
}
