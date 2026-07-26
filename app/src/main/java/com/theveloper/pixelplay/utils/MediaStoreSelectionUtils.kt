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

// WAV, DFF, DSF files may have incomplete duration metadata on some devices
// Explicitly include them to ensure they are scanned
private val HIGH_RES_MIME_SELECTION_ARGS = arrayOf(
    "audio/wav",
    "audio/x-wav",
    "audio/x-wave",
    "audio/dff",
    "audio/x-dff",
    "audio/dsf",
    "audio/x-dsf",
    "application/octet-stream"
)
private val HIGH_RES_EXTENSION_SELECTION_ARGS = arrayOf(
    "%.wav",
    "%.wave",
    "%.dff",
    "%.dsf"
)

/**
 * Builds the baseline MediaStore selection for user-facing local audio.
 *
 * We intentionally do not rely on [MediaStore.Audio.Media.IS_MUSIC] here because some devices
 * and scanners leave valid songs flagged as non-music, which makes library sync and folder
 * browsing appear to "cap out" below the real file count for specific users.
 *
 * MIDI files may be indexed with incomplete duration metadata, so explicit MIDI MIME/path
 * matches bypass the duration floor and are left to playback capability checks.
 */
fun buildLocalAudioSelection(minDurationMs: Int): Pair<String, Array<String>> {
    val clampedMinDurationMs = minDurationMs.coerceAtLeast(0)
    val midiMimePlaceholders = MIDI_MIME_SELECTION_ARGS.joinToString(",") { "?" }
    val midiExtensionSelection = MIDI_EXTENSION_SELECTION_ARGS.joinToString(" OR ") {
        "LOWER(${MediaStore.Audio.Media.DATA}) LIKE ?"
    }
    val highResMimePlaceholders = HIGH_RES_MIME_SELECTION_ARGS.joinToString(",") { "?" }
    val highResExtensionSelection = HIGH_RES_EXTENSION_SELECTION_ARGS.joinToString(" OR ") {
        "LOWER(${MediaStore.Audio.Media.DATA}) LIKE ?"
    }
    val selection = buildString {
        append("(")
        append("${MediaStore.Audio.Media.DURATION} >= ?")
        append(" OR LOWER(COALESCE(${MediaStore.Audio.Media.MIME_TYPE}, '')) IN ($midiMimePlaceholders)")
        append(" OR $midiExtensionSelection")
        append(" OR LOWER(COALESCE(${MediaStore.Audio.Media.MIME_TYPE}, '')) IN ($highResMimePlaceholders)")
        append(" OR $highResExtensionSelection")
        append(")")
        append(" AND COALESCE(${MediaStore.Audio.Media.TITLE}, '') != ''")
        append(" AND ${MediaStore.Audio.Media.DATA} IS NOT NULL")
    }
    return selection to arrayOf(clampedMinDurationMs.toString()) +
        MIDI_MIME_SELECTION_ARGS +
        MIDI_EXTENSION_SELECTION_ARGS +
        HIGH_RES_MIME_SELECTION_ARGS +
        HIGH_RES_EXTENSION_SELECTION_ARGS
}
