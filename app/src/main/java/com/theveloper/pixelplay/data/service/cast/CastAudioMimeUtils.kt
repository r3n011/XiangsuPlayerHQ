package com.theveloper.pixelplay.data.service.cast

import java.util.Locale

internal object CastAudioMimeUtils {
    const val AUDIO_OGG = "audio/ogg"
    const val AUDIO_OGG_OPUS = "audio/ogg; codecs=\"opus\""
    const val AUDIO_OGG_VORBIS = "audio/ogg; codecs=\"vorbis\""

    fun baseMimeType(mimeType: String?): String? {
        return mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
    }

    fun toCastSupportedMimeTypeOrNull(rawMimeType: String): String? {
        val raw = rawMimeType.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedRaw = raw.lowercase(Locale.ROOT)
        val normalizedBase = baseMimeType(normalizedRaw) ?: return null

        if (normalizedBase == AUDIO_OGG ||
            normalizedBase == "audio/oga" ||
            normalizedBase == "application/ogg"
        ) {
            detectOggCodecFromMimeParameter(normalizedRaw)?.let { return it }
        }

        return when (normalizedBase) {
            "audio/mpeg",
            "audio/mp3",
            "audio/mpeg3",
            "audio/x-mpeg" -> "audio/mpeg"

            "audio/flac",
            "audio/x-flac" -> "audio/flac"

            "audio/aac",
            "audio/aacp",
            "audio/adts",
            "audio/vnd.dlna.adts",
            "audio/mp4a-latm",
            "audio/aac-latm",
            "audio/x-aac",
            "audio/x-hx-aac-adts",
            "audio/alac" -> "audio/aac"

            "audio/mp4",
            "audio/x-m4a",
            "audio/m4a",
            "audio/3gpp",
            "audio/3gp" -> "audio/mp4"

            "audio/wav",
            "audio/x-wav",
            "audio/wave" -> "audio/wav"

            AUDIO_OGG,
            "audio/oga",
            "application/ogg" -> AUDIO_OGG

            "audio/opus" -> AUDIO_OGG_OPUS
            "audio/vorbis" -> AUDIO_OGG_VORBIS

            "audio/webm" -> "audio/webm"

            "audio/amr",
            "audio/amr-wb",
            "audio/l16",
            "audio/l24" -> normalizedBase

            "audio/aiff",
            "audio/x-aiff",
            "audio/aif" -> "audio/mpeg"

            else -> null
        }
    }

    fun resolveOggContentType(
        rawMimeCandidates: List<String?>,
        extension: String?,
        headerBytes: ByteArray?
    ): String? {
        rawMimeCandidates.asSequence()
            .filterNotNull()
            .mapNotNull { toCastSupportedMimeTypeOrNull(it) }
            .firstOrNull { isExactOggContentType(it) }
            ?.let { return it }

        if (extension.equals("opus", ignoreCase = true)) {
            return AUDIO_OGG_OPUS
        }

        detectOggCodecContentType(headerBytes)?.let { return it }

        val hasOggCandidate = rawMimeCandidates.any { raw ->
            val base = baseMimeType(raw)
            base == AUDIO_OGG || base == "audio/oga" || base == "application/ogg" || base == "audio/opus"
        } || extension.equals("ogg", ignoreCase = true) ||
            extension.equals("oga", ignoreCase = true) ||
            extension.equals("opus", ignoreCase = true)

        return if (hasOggCandidate) AUDIO_OGG else null
    }

    fun isExactOggContentType(contentType: String): Boolean {
        return contentType == AUDIO_OGG_OPUS || contentType == AUDIO_OGG_VORBIS
    }

    fun isCastSeekUnstableContentType(contentType: String?): Boolean {
        return when (baseMimeType(contentType)) {
            AUDIO_OGG,
            "audio/oga",
            "audio/opus",
            "audio/vorbis",
            "application/ogg" -> true
            else -> false
        }
    }

    private fun detectOggCodecFromMimeParameter(normalizedRawMimeType: String): String? {
        val codecsParameter = normalizedRawMimeType
            .substringAfter("codecs=", missingDelimiterValue = "")
            .trim()
            .trim('"', '\'')

        return when {
            codecsParameter.contains("opus") -> AUDIO_OGG_OPUS
            codecsParameter.contains("vorbis") -> AUDIO_OGG_VORBIS
            else -> null
        }
    }

    private fun detectOggCodecContentType(headerBytes: ByteArray?): String? {
        val bytes = headerBytes ?: return null
        if (!containsAscii(bytes, "OggS")) return null
        return when {
            containsAscii(bytes, "OpusHead") -> AUDIO_OGG_OPUS
            containsAscii(bytes, "vorbis") -> AUDIO_OGG_VORBIS
            else -> null
        }
    }

    private fun containsAscii(bytes: ByteArray, token: String): Boolean {
        if (token.isEmpty() || bytes.size < token.length) return false
        val tokenBytes = token.encodeToByteArray()
        val lastStart = bytes.size - tokenBytes.size
        for (start in 0..lastStart) {
            var matched = true
            for (offset in tokenBytes.indices) {
                if (bytes[start + offset] != tokenBytes[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }
}
