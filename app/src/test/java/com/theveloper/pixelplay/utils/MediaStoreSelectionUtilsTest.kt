package com.theveloper.pixelplay.utils

import android.provider.MediaStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaStoreSelectionUtilsTest {

    @Test
    fun `buildLocalAudioSelection uses is music flag as primary predicate`() {
        val (selection, selectionArgs) = buildLocalAudioSelection(10_000)

        assertTrue(selection.contains("${MediaStore.Audio.Media.IS_MUSIC} != 0"))
        assertFalse(selection.contains(MediaStore.Audio.Media.DURATION))
        assertTrue(selection.contains(MediaStore.Audio.Media.TITLE))
        assertTrue(selection.contains(MediaStore.Audio.Media.DATA))
        assertTrue(selection.contains("LIKE"))
        // Args are MIME args, MIDI extension args, then common audio extension fallback args.
        assertTrue(selectionArgs.isNotEmpty())
        assertTrue(selectionArgs.size > 6)
    }

    @Test
    fun `buildLocalAudioSelection ignores minDurationMs parameter`() {
        val (_, selectionArgsSmall) = buildLocalAudioSelection(10_000)
        val (_, selectionArgsNegative) = buildLocalAudioSelection(-250)

        assertArrayEquals(selectionArgsSmall, selectionArgsNegative)
    }

    @Test
    fun `buildLocalAudioSelection includes midi mime and extension bypass`() {
        val (selection, selectionArgs) = buildLocalAudioSelection(10_000)

        assertTrue(selection.contains(MediaStore.Audio.Media.MIME_TYPE))
        assertTrue(selection.contains("audio_media._data") || selection.contains(MediaStore.Audio.Media.DATA))
        assertTrue(selection.contains("LIKE"))
        assertTrue(selectionArgs.any { it == "audio/midi" })
        assertTrue(selectionArgs.any { it == "%.mid" })
    }

    @Test
    fun `buildLocalAudioSelection includes common audio extensions as fallback`() {
        val (_, selectionArgs) = buildLocalAudioSelection(10_000)

        assertTrue(selectionArgs.any { it == "%.mp3" })
        assertTrue(selectionArgs.any { it == "%.flac" })
        assertTrue(selectionArgs.any { it == "%.m4a" })
        assertTrue(selectionArgs.any { it == "%.wav" })
    }
}
