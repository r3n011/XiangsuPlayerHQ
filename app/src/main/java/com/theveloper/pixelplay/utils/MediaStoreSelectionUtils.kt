package com.theveloper.pixelplay.utils

import android.provider.MediaStore

private val MIDI_MIME_SELECTION_ARGS = arrayOf(
    "audio/midi",
    "audio/x-midi",
    "audio/sp-midi",
    "audio/x-mid"
)
private val MIDI_EXTENSION_SELECTION_ARGS = arrayOf(
    "%.mid",
    "%.midi"
)

/**
 * Common audio file extensions used as a fallback when MediaStore's duration / mime_type
 * indexing is incomplete. Some OEMs only correctly index a subset of formats (e.g. OGG),
 * causing MP3/FLAC/M4A/WAV/etc. to be silently dropped from the scan. Matching by path
 * extension ensures these files are still discovered without making the query overly
 * complex.
 */
private val COMMON_AUDIO_EXTENSIONS = arrayOf(
    ".mp3", ".flac", ".m4a", ".wav", ".wma", ".aac",
    ".aiff", ".aif", ".opus", ".oga", ".dff", ".dsf",
    ".amr", ".3gpp", ".3gp", ".awb"
)

/**
 * Builds the baseline MediaStore selection for user-facing local audio.
 *
 * The primary predicate is [MediaStore.Audio.Media.IS_MUSIC] != 0 because that is the
 * most reliable way to discover music files across devices. We also accept any MIME
 * type under the audio namespace, plus a curated list of common audio extensions, so
 * files that are not flagged as music by the OEM scanner are still included.
 *
 * The duration floor from user preferences is intentionally NOT applied here. Some OEM
 * MediaStore providers index MP3/FLAC/WAV/etc. with duration == 0, which would silently
 * drop those songs. Filtering by duration is left to playback-level checks if needed.
 *
 * The selection intentionally avoids SQLite functions such as COALESCE/LOWER because
 * certain OEM MediaStore providers do not support them in selection strings.
 */
fun buildLocalAudioSelection(minDurationMs: Int): Pair<String, Array<String>> {
    val midiMimePlaceholders = MIDI_MIME_SELECTION_ARGS.joinToString(",") { "?" }
    val midiExtensionSelection = MIDI_EXTENSION_SELECTION_ARGS.joinToString(" OR ") {
        "${MediaStore.Audio.Media.DATA} LIKE ?"
    }
    val commonExtensionSelection = COMMON_AUDIO_EXTENSIONS.joinToString(" OR ") {
        "${MediaStore.Audio.Media.DATA} LIKE ?"
    }
    val commonExtensionArgs = COMMON_AUDIO_EXTENSIONS.map { "%$it" }.toTypedArray()

    val selection = buildString {
        append("(")
        append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        append(" OR ${MediaStore.Audio.Media.MIME_TYPE} IN ($midiMimePlaceholders)")
        append(" OR $midiExtensionSelection")
        append(" OR $commonExtensionSelection")
        append(")")
        append(" AND ${MediaStore.Audio.Media.TITLE} IS NOT NULL")
        append(" AND ${MediaStore.Audio.Media.TITLE} != ''")
        append(" AND ${MediaStore.Audio.Media.DATA} IS NOT NULL")
    }
    return selection to MIDI_MIME_SELECTION_ARGS +
        MIDI_EXTENSION_SELECTION_ARGS +
        commonExtensionArgs
}
