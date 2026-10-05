package com.theveloper.pixelplay.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Tests for [normalizeMetadataText]'s mojibake repair: tags whose bytes are UTF-8
 * (or legacy Shift_JIS) but whose ID3 header declares ISO-8859-1/Windows-1252.
 *
 * The desktop JDK decodes the five undefined Windows-1252 bytes (0x81/0x8D/0x8F/0x90/0x9D)
 * to U+FFFD, unlike Android's ICU which maps them to C1 controls — the ICU-style helper
 * below reproduces the on-device behavior for the Japanese cases that contain those bytes.
 */
class MetadataMojibakeRepairTest {

    private val cp1252: Charset = Charset.forName("windows-1252")
    private val shiftJis: Charset = Charset.forName("Shift_JIS")

    private fun asLatin1Mojibake(real: String, actual: Charset): String {
        val sb = StringBuilder()
        for (b in real.toByteArray(actual)) sb.append((b.toInt() and 0xFF).toChar())
        return sb.toString()
    }

    /** Android/ICU Windows-1252 decoding: undefined bytes become C1 controls, recoverable. */
    private fun asIcuCp1252Mojibake(real: String, actual: Charset): String {
        val decoder = cp1252.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val sb = StringBuilder()
        for (b in real.toByteArray(actual)) {
            val decoded = try { decoder.decode(ByteBuffer.wrap(byteArrayOf(b))).toString() } catch (_: CharacterCodingException) { null }
            if (decoded != null && decoded.length == 1 && decoded[0] != '\uFFFD') sb.append(decoded)
            else sb.append((0x80 + (b.toInt() and 0x7F)).toChar())
        }
        return sb.toString()
    }

    @Test
    fun `repairs Japanese UTF-8 tags misread as Latin-1`() {
        assertEquals("揺れる想い", asLatin1Mojibake("揺れる想い", StandardCharsets.UTF_8).normalizeMetadataText())
        assertEquals("愛してる", asLatin1Mojibake("愛してる", StandardCharsets.UTF_8).normalizeMetadataText())
    }

    @Test
    fun `repairs Japanese UTF-8 tags misread as Windows-1252 (ICU)`() {
        assertEquals("揺れる想い", asIcuCp1252Mojibake("揺れる想い", StandardCharsets.UTF_8).normalizeMetadataText())
        assertEquals("大問題", asIcuCp1252Mojibake("大問題", StandardCharsets.UTF_8).normalizeMetadataText())
    }

    @Test
    fun `repairs Chinese and Korean UTF-8 tags`() {
        assertEquals("周杰伦", asLatin1Mojibake("周杰伦", StandardCharsets.UTF_8).normalizeMetadataText())
        assertEquals("아이유", asLatin1Mojibake("아이유", StandardCharsets.UTF_8).normalizeMetadataText())
    }

    @Test
    fun `repairs Western UTF-8 tags and double-encoded text`() {
        assertEquals("Café", String("Café".toByteArray(StandardCharsets.UTF_8), cp1252).normalizeMetadataText())
        assertEquals("œuvre", String("œuvre".toByteArray(StandardCharsets.UTF_8), cp1252).normalizeMetadataText())
        val once = String("Café".toByteArray(StandardCharsets.UTF_8), cp1252)
        val twice = String(once.toByteArray(StandardCharsets.UTF_8), cp1252)
        assertEquals("Café", twice.normalizeMetadataText())
    }

    @Test
    fun `repairs legacy Shift_JIS Japanese tags`() {
        assertEquals("本日", asLatin1Mojibake("本日", shiftJis).normalizeMetadataText())
        assertEquals("揺れる想い", asLatin1Mojibake("揺れる想い", shiftJis).normalizeMetadataText())
        assertEquals("ZARD 揺れる想い", asLatin1Mojibake("ZARD 揺れる想い", shiftJis).normalizeMetadataText())
    }

    @Test
    fun `leaves legitimate text untouched`() {
        assertEquals("Café", "Café".normalizeMetadataText())
        assertEquals("ZARD - 揺れる想い", "ZARD - 揺れる想い".normalizeMetadataText())
        assertEquals("Don’t Stop", "Don’t Stop".normalizeMetadataText())
        // C1 curly-quote byte from a Latin-1 tag must not trigger the Shift_JIS fallback
        assertEquals("Dont Stop", "Dont Stop".normalizeMetadataText())
        assertEquals("°º¤ø,¸¸,ø¤º°`°º¤ø,¸¸", "°º¤ø,¸¸,ø¤º°`°º¤ø,¸¸".normalizeMetadataText())
        assertEquals("Track 🎵", "Track 🎵".normalizeMetadataText())
        assertEquals("bad\ufffdtitle", "bad\ufffdtitle".normalizeMetadataText())
    }

    @Test
    fun `mixed real CJK and mojibake is not partially rewritten`() {
        val mixed = "愛" + asLatin1Mojibake("あ", StandardCharsets.UTF_8)
        assertEquals(mixed, mixed.normalizeMetadataText())
    }

    @Test
    fun `trims and strips null bytes as before`() {
        assertEquals("Title", "  Title  ".normalizeMetadataText())
        assertEquals("Title", "Ti\u0000tle".normalizeMetadataText())
    }
}
