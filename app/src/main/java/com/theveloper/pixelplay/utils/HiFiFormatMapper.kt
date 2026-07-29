package com.theveloper.pixelplay.utils

import android.net.Uri
import timber.log.Timber
import java.io.File

object HiFiFormatMapper {

    data class FormatInfo(
        val mimeType: String,
        val displayName: String,
        val extensionRenderer: Boolean = true,
        val supportsOffload: Boolean = false
    )

    private val extensionToFormatMap = mapOf(
        "wav" to FormatInfo("audio/wav", "WAV", false, true),
        "wave" to FormatInfo("audio/wav", "WAV", false, true),
        "rf64" to FormatInfo("audio/wav", "RF64", false, true),
        "flac" to FormatInfo("audio/flac", "FLAC", false, true),
        "alac" to FormatInfo("audio/alac", "ALAC", true, false),
        "m4a" to FormatInfo("audio/mp4", "M4A/AAC", false, true),
        "aac" to FormatInfo("audio/aac", "AAC", false, true),
        "ape" to FormatInfo("audio/ape", "APE", true, false),
        "tak" to FormatInfo("audio/tak", "TAK", true, false),
        "tta" to FormatInfo("audio/tta", "TTA", true, false),
        "dts" to FormatInfo("audio/dts", "DTS", true, false),
        "ac3" to FormatInfo("audio/ac3", "AC3", true, false),
        "eac3" to FormatInfo("audio/eac3", "E-AC3", true, false),
        "aiff" to FormatInfo("audio/aiff", "AIFF", true, false),
        "aif" to FormatInfo("audio/aiff", "AIFF", true, false),
        "aifc" to FormatInfo("audio/aiff", "AIFF-C", true, false),
        "caf" to FormatInfo("audio/caf", "CAF", true, false),
        "dsf" to FormatInfo("audio/dsd", "DSF (DSD)", true, false),
        "dff" to FormatInfo("audio/dsd", "DFF (DSDIFF)", true, false),
        "dif" to FormatInfo("audio/dsd", "DIF (DSD)", true, false),
        "opus" to FormatInfo("audio/opus", "Opus", false, true),
        "ogg" to FormatInfo("audio/ogg", "Ogg", false, true),
        "vorbis" to FormatInfo("audio/vorbis", "Vorbis", false, true),
        "wma" to FormatInfo("audio/wma", "WMA", true, false),
        "mp3" to FormatInfo("audio/mpeg", "MP3", false, true),
        "mp4" to FormatInfo("audio/mp4", "MP4", false, true),
        "m4b" to FormatInfo("audio/mp4", "M4B", false, true),
        "aax" to FormatInfo("audio/mp4", "AAX", false, true),
        "aa" to FormatInfo("audio/mp4", "AA", false, true),
        "oggopus" to FormatInfo("audio/opus", "Opus", false, true),
        "raw" to FormatInfo("audio/raw", "RAW", true, false),
        "pcm" to FormatInfo("audio/raw", "PCM", true, false)
    )

    private val hiFiExtensions = setOf(
        "wav", "wave", "rf64", "flac", "alac", "ape", "tak", "tta",
        "dts", "ac3", "eac3", "aiff", "aif", "aifc", "caf",
        "dsf", "dff", "dif", "opus", "ogg", "wma", "raw", "pcm"
    )

    private val ffmpegExtensions = setOf(
        "alac", "ape", "tak", "tta", "dts", "ac3", "eac3",
        "aiff", "aif", "aifc", "caf", "dsf", "dff", "dif", "wma"
    )

    fun getFormatInfo(filePath: String): FormatInfo? {
        val file = File(filePath)
        val ext = file.extension.lowercase()
        return extensionToFormatMap[ext]
    }

    fun getFormatInfo(uri: Uri): FormatInfo? {
        val path = uri.path ?: return null
        return getFormatInfo(path)
    }

    fun getMimeType(filePath: String): String? {
        return getFormatInfo(filePath)?.mimeType
    }

    fun getMimeType(uri: Uri): String? {
        return getFormatInfo(uri)?.mimeType
    }

    fun isHiFiFormat(filePath: String): Boolean {
        val ext = File(filePath).extension.lowercase()
        return ext in hiFiExtensions
    }

    fun isFfmpegFormat(filePath: String): Boolean {
        val ext = File(filePath).extension.lowercase()
        return ext in ffmpegExtensions
    }

    fun isExtensionRendererFormat(filePath: String): Boolean {
        val info = getFormatInfo(filePath) ?: return false
        return info.extensionRenderer
    }

    fun supportsAudioOffload(filePath: String): Boolean {
        val info = getFormatInfo(filePath) ?: return false
        return info.supportsOffload
    }

    fun getDisplayName(filePath: String): String {
        val info = getFormatInfo(filePath)
        return info?.displayName ?: File(filePath).extension.uppercase()
    }

    fun isSupportedFile(filePath: String): Boolean {
        val ext = File(filePath).extension.lowercase()
        return ext in extensionToFormatMap
    }

    fun logFormatDetection(filePath: String) {
        val file = File(filePath)
        val info = getFormatInfo(filePath)
        if (info != null) {
            Timber.tag("HiFiFormatMapper").d(
                "Detected ${file.name}: ${info.displayName} (${info.mimeType}), " +
                    "ffmpeg=${isFfmpegFormat(filePath)}, " +
                    "extensionRenderer=${info.extensionRenderer}, " +
                    "offload=${info.supportsOffload}"
            )
        }
    }
}