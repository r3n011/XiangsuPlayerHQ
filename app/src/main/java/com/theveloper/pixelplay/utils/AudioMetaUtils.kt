package com.theveloper.pixelplay.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.theveloper.pixelplay.data.database.MusicDao
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class AudioMeta(
    val mimeType: String?,
    val bitrate: Int?,      // bits per second
    val sampleRate: Int?,  // Hz
    /** 声道数（MediaExtractor 提供） */
    val channels: Int? = null,
    /** 位深（无标签来源时按格式/码率推断，仅作展示） */
    val bitDepth: Int? = null,
    /** 文件字节数（本地文件才有） */
    val fileSize: Long? = null
)

object AudioMetaUtils {

    private const val TAG = "AudioMetaUtils"
    private const val METADATA_READ_TIMEOUT_MS = 10_000L

    /**
     * Dedicated thread pool for native MediaMetadataRetriever / MediaExtractor calls.
     *
     * These APIs perform blocking JNI I/O that cannot be interrupted by coroutine
     * cancellation. Previously, [getAudioMetadata] used `withTimeout { ... }`, but
     * since `MediaMetadataRetriever.setDataSource()` is a regular blocking call,
     * the timeout could never fire while the native call was in progress — the
     * calling Dispatchers.IO thread was blocked indefinitely, eventually exhausting
     * the pool and freezing the library scan (see debug-media-scan-350-freeze.md).
     *
     * By offloading to this dedicated pool and using Future.get(timeout), the
     * caller is released on timeout even if the native call continues running
     * in the background. Leaked threads are confined to this pool and never
     * starve the shared IO dispatcher.
     */
    private val threadCounter = AtomicInteger(0)
    private val metadataExecutor = Executors.newFixedThreadPool(
        4,
        ThreadFactory { r ->
            Thread(r, "AudioMetaUtils-${threadCounter.incrementAndGet()}").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    )

    /**
     * Returns audio metadata for a given file path.
     * Tries MediaMetadataRetriever first, then falls back to MediaExtractor.
     */
    suspend fun getAudioMetadata(musicDao: MusicDao, id: Long, filePath: String, deepScan: Boolean): AudioMeta {
        val cached = musicDao.getAudioMetadataById(id)
        if (!deepScan && cached != null &&
            cached.mimeType != null &&
            cached.bitrate != null &&
            cached.sampleRate != null
        )
            return cached

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return AudioMeta(null, null, null)

        val future = metadataExecutor.submit(Callable { readMetadataInternal(filePath) })
        return try {
            future.get(METADATA_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            Log.w(TAG, "Metadata read timed out for file: ${file.name}")
            AudioMeta(null, null, null)
        } catch (e: java.util.concurrent.ExecutionException) {
            Log.w(TAG, "Metadata read failed for ${file.name}: ${e.cause?.message}")
            AudioMeta(null, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Metadata read failed for ${file.name}: ${e.message}")
            AudioMeta(null, null, null)
        }
    }

    /**
     * Blocking implementation that reads mimeType/bitrate/sampleRate from the file.
     * Runs on [metadataExecutor] so that a stuck native call cannot exhaust the
     * shared Dispatchers.IO pool.
     */
    private fun readMetadataInternal(filePath: String): AudioMeta {
        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var channels: Int? = null

        MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } catch (e: Exception) {
                Log.w(TAG, "Retriever failed for $filePath: ${e.message}")
            }
        }

        // ⚡ 无条件走一次 MediaExtractor：声道数只有它能给，采样率/码率/格式也能补上
        //    MediaMetadataRetriever 拿不到的值。此前只在 retriever 拿不到 mimeType 时才执行，
        //    导致「文件信息」里的声道、以及部分文件的采样率/码率长期为空（显示不全）。
        MediaExtractor().apply {
            try {
                setDataSource(filePath)
                for (i in 0 until trackCount) {
                    val format: MediaFormat = getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME)
                    if (trackMime?.startsWith("audio/") == true) {
                        mimeType = mimeType ?: trackMime
                        sampleRate = sampleRate ?: if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else null
                        bitrate = bitrate ?: if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else null
                        channels = channels ?: if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else null
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Extractor failed for $filePath: ${e.message}")
            } finally {
                try {
                    release()
                } catch (_: Exception) {
                }
            }
        }

        val fileSize = runCatching { File(filePath).length().takeIf { it > 0L } }.getOrNull()
        return AudioMeta(
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = inferBitDepth(mimeType, sampleRate, bitrate),
            fileSize = fileSize
        )
    }

    /**
     * 位深推断：无损格式优先看码率密度（bitrate / (sampleRate × channels)），
     * 无码率信息时按常见格式给出代表值。属于估算值，仅用于展示。
     */
    private fun inferBitDepth(mimeType: String?, sampleRate: Int?, bitrate: Int?): Int? {
        val mime = mimeType?.lowercase(Locale.US) ?: return null
        val isLossless = mime.contains("flac") || mime.contains("alac") ||
            mime.contains("wav") || mime.contains("x-wav") || mime.contains("aiff") ||
            mime.contains("dsd") || mime.contains("dsf") || mime.contains("ape")
        if (!isLossless) return 16
        if (mime.contains("dsd") || mime.contains("dsf")) return 1
        if (sampleRate != null && sampleRate > 48_000) return 24
        // 有码率时按密度推断（单声道视为 2 声道计算上限）
        val br = bitrate
        val sr = sampleRate
        if (br != null && br > 0 && sr != null && sr > 0) {
            val perSample = br / sr
            return when {
                perSample >= 24 -> 24
                perSample >= 16 -> 16
                else -> 16
            }
        }
        return 16
    }

    fun mimeTypeToFormat(mimeType: String?): String {
        val normalized = mimeType
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.substringBefore(';')
            ?: return "-"

        if (normalized.isBlank()) return "-"

        return when {
            normalized == "audio/mpeg" ||
                normalized == "audio/mp3" ||
                normalized == "audio/x-mp3" ||
                normalized == "audio/mpeg3" -> "mp3"

            normalized == "audio/flac" ||
                normalized == "audio/x-flac" -> "flac"

            normalized == "audio/wav" ||
                normalized == "audio/x-wav" ||
                normalized == "audio/wave" ||
                normalized == "audio/vnd.wave" -> "wav"

            normalized == "audio/ogg" ||
                normalized == "application/ogg" ||
                normalized == "audio/vorbis" ||
                normalized == "audio/x-vorbis" -> "ogg"

            normalized == "audio/opus" ||
                normalized == "audio/x-opus" -> "opus"

            normalized == "audio/mp4" ||
                normalized == "audio/m4a" ||
                normalized == "audio/x-m4a" ||
                normalized == "audio/mp4a-latm" -> "m4a"

            normalized == "audio/aac" ||
                normalized == "audio/aacp" -> "aac"

            normalized == "audio/amr" ||
                normalized == "audio/amr-wb" ||
                normalized == "audio/3gpp" -> "amr"

            normalized == "audio/evrc" ||
                normalized == "audio/x-evrc" -> "evrc"

            normalized == "audio/qcelp" ||
                normalized == "audio/x-qcelp" -> "qcelp"

            normalized == "audio/x-ima-adpcm" ||
                normalized == "audio/ima-adpcm" -> "ima"

            normalized == "audio/alac" ||
                normalized == "audio/x-alac" -> "alac"

            normalized == "audio/aiff" ||
                normalized == "audio/x-aiff" ||
                normalized == "audio/aif" ||
                normalized == "audio/x-aifc" -> "aiff"

            normalized == "audio/x-ms-wma" ||
                normalized == "audio/wma" -> "wma"

            normalized == "audio/ac3" ||
                normalized == "audio/eac3" ||
                normalized == "audio/eac3-joc" -> "ac3"

            normalized == "audio/vnd.dts" ||
                normalized == "audio/vnd.dts.hd" -> "dts"

            normalized == "audio/midi" ||
                normalized == "audio/x-midi" ||
                normalized == "audio/sp-midi" ||
                normalized == "audio/x-mid" -> "midi"

            normalized.contains("mp4a") -> "m4a"
            normalized.contains("flac") -> "flac"
            normalized.contains("opus") -> "opus"
            normalized.contains("vorbis") || normalized.contains("ogg") -> "ogg"
            normalized.contains("wav") || normalized.contains("wave") -> "wav"
            normalized.contains("aac") -> "aac"
            normalized.contains("mpeg") || normalized.contains("mp3") -> "mp3"
            normalized.contains("amr") -> "amr"
            normalized.contains("alac") -> "alac"
            normalized.contains("aiff") || normalized.contains("aif") -> "aiff"
            normalized.contains("wma") -> "wma"
            normalized.contains("dts") -> "dts"
            normalized.contains("eac3") || normalized.contains("ac3") -> "ac3"
            normalized.contains("midi") || normalized.contains("x-mid") -> "midi"
            normalized.startsWith("audio/") -> normalized.substringAfter("audio/").ifBlank { "-" }
            else -> "-"
        }
    }
}
