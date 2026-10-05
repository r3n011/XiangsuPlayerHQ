package com.theveloper.pixelplay.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

private val SHIFT_JIS: Charset? = runCatching { Charset.forName("Shift_JIS") }.getOrNull()

/**
 * Reverse mapping for the Windows-1252 punctuation that occupies the 0x80-0x9F
 * byte range (curly quotes, dashes, ellipsis...). When UTF-8 bytes are misdecoded
 * through Windows-1252 those bytes surface as these characters and must be mapped
 * back before the text can be re-decoded. Built by round-tripping each byte
 * through the charset so it always matches the platform's decoding table.
 */
private val CP1252_HIGH_BYTE_REVERSE: Map<Char, Byte> = buildMap {
    val decoder = WINDOWS_1252.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    for (b in 0x80..0x9F) {
        val decoded = runCatching { decoder.decode(ByteBuffer.wrap(byteArrayOf(b.toByte()))) }
            .getOrNull() ?: continue
        if (decoded.length == 1) put(decoded[0], b.toByte())
    }
}

fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}

/**
 * Attempts to fix incorrectly encoded metadata strings — most commonly tags whose
 * bytes are UTF-8 (Japanese/Chinese/European text) but whose ID3 header declares
 * ISO-8859-1/Windows-1252. Every misdecoded character then lives in the 0x80-0xFF
 * range (e.g. "く" shows up as "ã□□"), which is exactly what the structural
 * detection below looks for: map the characters back to bytes, re-decode strictly
 * as UTF-8, and keep the result only when the bytes form valid multibyte UTF-8.
 * Genuinely Latin text ("Café", "Don't") never survives that strict validation,
 * so it is returned untouched. Falls back to a Shift_JIS re-decode for legacy
 * Japanese tags, accepted only when the result contains kana/kanji.
 */
fun String?.normalizeMetadataText(): String? {
    if (this == null) return null
    var candidate = this.trim()
    if (candidate.isEmpty()) return candidate

    // UTF-8-as-Latin-1 mojibake may be nested (encoded twice); repair until stable.
    repeat(2) {
        val repaired = repairUtf8Mojibake(candidate) ?: return@repeat
        candidate = repaired
    }
    if (candidate == this.trim()) {
        repairShiftJisMojibake(candidate)?.let { candidate = it }
    }

    val cleaned = candidate.replace("\u0000", "")

    return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
}

/**
 * Maps a suspicious string back to its original bytes. Chars in 0x00-0xFF map 1:1;
 * Windows-1252 punctuation maps through [CP1252_HIGH_BYTE_REVERSE]. Any other char
 * (real CJK text, emoji, U+FFFD...) proves the string is not Latin-1 mojibake.
 */
private fun String.toSuspectedLatin1Bytes(): ByteArray? {
    val bytes = ByteArray(length)
    for (i in indices) {
        val code = this[i].code
        bytes[i] = when {
            code <= 0xFF -> code.toByte()
            else -> CP1252_HIGH_BYTE_REVERSE[this[i]] ?: return null
        }
    }
    return bytes
}

private fun decodeStrictly(charset: Charset, bytes: ByteArray): String? = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

private fun repairUtf8Mojibake(text: String): String? {
    if (text.none { it.code > 0x7F }) return null

    val bytes = text.toSuspectedLatin1Bytes() ?: return null

    // A genuine mojibake must contain at least one UTF-8 lead byte (0xC2-0xF4);
    // continuation/control bytes alone cannot form text.
    if (bytes.none { val u = it.toInt() and 0xFF; u in 0xC2..0xF4 }) return null

    val decoded = decodeStrictly(Charsets.UTF_8, bytes) ?: return null
    if (decoded == text) return null
    // The repair must not leave C1 control junk (the visible boxes) behind.
    if (decoded.any { it.code in 0x80..0x9F }) return null
    return decoded
}

/**
 * Legacy Japanese tags written in Shift_JIS misdecoded as Latin-1. Shift_JIS decodes
 * almost anything, so the result is only accepted when the input is overwhelmingly
 * high-byte (real misdecoded Japanese is ~100% high bytes) and the decoded text
 * contains full-width kana or kanji — decorative Latin-1 strings ("°º¤ø,¸¸") and
 * Latin text with a stray curly quote decode to half-width kana or garbage and are
 * left untouched.
 */
private fun repairShiftJisMojibake(text: String): String? {
    if (text.contains('\uFFFD')) return null
    val bytes = text.toSuspectedLatin1Bytes() ?: return null
    val highBytes = bytes.count { (it.toInt() and 0xFF) >= 0x80 }
    if (highBytes * 100 < bytes.size * 35) return null
    val decoded = SHIFT_JIS?.let { decodeStrictly(it, bytes) } ?: return null
    if (decoded == text) return null
    if (decoded.any { it.code in 0x80..0x9F || it == '\uFFFD' }) return null
    val hasJapanese = decoded.any {
        it.code in 0x3040..0x30FF || // kana
            it.code in 0x3400..0x4DBF || // CJK ext A
            it.code in 0x4E00..0x9FFF    // CJK ideographs
    }
    return if (hasJapanese) decoded else null
}

fun String?.normalizeMetadataTextOrEmpty(): String {
    return normalizeMetadataText() ?: ""
}

/**
 * Escape sequence for delimiters in artist names.
 * Use double backslash (\\) before a delimiter to prevent splitting at that position.
 * Example: "AC\\\\DC" with delimiter "/" won't split, but "Artist1/Artist2" will.
 */
private const val ESCAPE_SEQUENCE = "\\\\"

/**
 * Placeholder used internally during parsing to preserve escaped delimiters.
 */
private const val ESCAPE_PLACEHOLDER = "\u0000ESCAPED\u0000"

/**
 * Placeholder used internally during parsing to preserve whitelisted artist names
 * (names that must never be split, e.g. "AC/DC").
 */
private const val PROTECTED_PLACEHOLDER = "\u0000PROTECTED\u0000"

/**
 * Default word-based delimiters for splitting multi-artist strings.
 * These are matched case-insensitively with word boundaries.
 */
val DEFAULT_WORD_DELIMITERS = listOf("featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod")

/**
 * Splits an artist string by the given character delimiters and word delimiters,
 * respecting escaped delimiters and whitelisted names.
 *
 * @param delimiters List of character delimiter strings to split by (e.g., ["/", ";", ","])
 * @param wordDelimiters List of word-based delimiters to split by (e.g., ["feat.", "ft.", "vs."])
 *        These are matched case-insensitively with surrounding whitespace.
 *        The single-letter "x" is handled specially — only matched when surrounded by spaces.
 * @param protectedNames Whitelisted artist names that must NEVER be split, even if they
 *        contain delimiter characters (e.g. "AC/DC", "Tones & I"). Matched case-insensitively
 *        and the original casing is preserved after splitting.
 * @return List of individual artist names, trimmed and with escaped delimiters restored.
 *         Returns a single-element list with the original string if no splitting occurs.
 *
 * Examples:
 * - "Artist1/Artist2".splitArtistsByDelimiters(listOf("/")) -> ["Artist1", "Artist2"]
 * - "AC\\DC".splitArtistsByDelimiters(listOf("/")) -> ["AC/DC"] (escaped)
 * - "AC/DC".splitArtistsByDelimiters(listOf("/"), protectedNames = ["AC/DC"]) -> ["AC/DC"]
 * - "Drake feat. Rihanna".splitArtistsByDelimiters(listOf(), listOf("feat.")) -> ["Drake", "Rihanna"]
 * - "Marshmello x Bastille".splitArtistsByDelimiters(listOf(), listOf("x")) -> ["Marshmello", "Bastille"]
 */
fun String.splitArtistsByDelimiters(
    delimiters: List<String>,
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS,
    protectedNames: Collection<String> = emptyList()
): List<String> {
    if ((delimiters.isEmpty() && wordDelimiters.isEmpty()) || this.isBlank()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    // Sort delimiters by length descending to handle longer delimiters first
    val sortedDelimiters = delimiters.sortedByDescending { it.length }

    var working = this

    // Replace escaped delimiters with placeholders
    val escapedMappings = mutableMapOf<String, String>()
    sortedDelimiters.forEachIndexed { index, delimiter ->
        val escapedDelimiter = ESCAPE_SEQUENCE + delimiter
        val placeholder = "${ESCAPE_PLACEHOLDER}${index}${ESCAPE_PLACEHOLDER}"
        escapedMappings[placeholder] = delimiter
        working = working.replace(escapedDelimiter, placeholder)
    }

    // ⚡ Protect whitelisted artist names: replace every occurrence with a unique
    //    placeholder BEFORE splitting, then restore the original (case-preserved)
    //    text afterwards. This way "AC/DC" never gets split by "/".
    val protectedMappings = mutableMapOf<String, String>()
    if (protectedNames.isNotEmpty()) {
        val sortedProtected = protectedNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
        var protectedIndex = 0
        for (name in sortedProtected) {
            val regex = Regex(Regex.escape(name), RegexOption.IGNORE_CASE)
            working = regex.replace(working) { match ->
                val placeholder = "${PROTECTED_PLACEHOLDER}${protectedIndex++}${PROTECTED_PLACEHOLDER}"
                protectedMappings[placeholder] = match.value
                placeholder
            }
        }
    }

    // Build combined regex pattern:
    // 1. Word delimiters: matched case-insensitively with whitespace boundaries
    //    Single-char word delimiters (like "x") require spaces on both sides
    // 2. Character delimiters: matched literally (with optional surrounding whitespace)
    val patternParts = mutableListOf<String>()

    // Word delimiters first (longer ones first to avoid partial matches)
    val sortedWordDelimiters = wordDelimiters.sortedByDescending { it.length }
    for (wd in sortedWordDelimiters) {
        val escaped = Regex.escape(wd)
        if (wd.length == 1) {
            // Single-char word delimiters (e.g., "x") — require spaces on both sides
            patternParts.add("\\s+$escaped\\s+")
        } else {
            // Multi-char word delimiters — require word boundary or whitespace
            patternParts.add("\\s+$escaped\\s+|\\s+$escaped$|^$escaped\\s+")
        }
    }

    // Character delimiters
    if (sortedDelimiters.isNotEmpty()) {
        val charPattern = sortedDelimiters.joinToString("|") { Regex.escape(it) }
        patternParts.add(charPattern)
    }

    if (patternParts.isEmpty()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val combinedPattern = patternParts.joinToString("|")
    val regex = Regex(combinedPattern, RegexOption.IGNORE_CASE)

    // Split by combined pattern
    val parts = working.split(regex)

    // Restore escaped delimiters and whitelisted names, then trim each part
    return parts
        .map { part ->
            var restored = part
            escapedMappings.forEach { (placeholder, delimiter) ->
                restored = restored.replace(placeholder, delimiter)
            }
            protectedMappings.forEach { (placeholder, originalName) ->
                restored = restored.replace(placeholder, originalName)
            }
            restored.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .ifEmpty { if (this.trim().isNotEmpty()) listOf(this.trim()) else emptyList() }
}

/**
 * Extracts featured artists from a song title and returns the cleaned title + extracted artists.
 *
 * Detects patterns like:
 * - "Song (feat. Artist)" / "Song [feat. Artist]"
 * - "Song (ft. Artist1 & Artist2)" / "Song [with Artist]"
 *
 * @param delimiters Character delimiters to further split extracted artist strings
 * @param wordDelimiters Word delimiters to further split extracted artist strings
 * @param protectedNames Whitelisted artist names that must never be split
 * @return Pair of (cleaned title, list of extracted artist names). Empty list if no artists found.
 */
fun String.extractArtistsFromTitle(
    delimiters: List<String> = emptyList(),
    wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS,
    protectedNames: Collection<String> = emptyList()
): Pair<String, List<String>> {
    if (this.isBlank()) return this to emptyList()

    // Match patterns like (feat. ...), [ft. ...], (with ...), etc.
    val featureKeywords = listOf("featuring", "feat\\.", "feat", "ft\\.", "ft", "with", "prod\\.", "prod")
    val keywordPattern = featureKeywords.joinToString("|")
    val bracketPattern = Regex(
        """[\(\[]\\?\s*(?:$keywordPattern)\s+(.+?)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    val extractedArtists = mutableListOf<String>()
    var cleanedTitle = this

    bracketPattern.findAll(this).forEach { match ->
        val artistString = match.groupValues[1]
        // Split the extracted artist string by delimiters (handles "Artist1 & Artist2" inside parens)
        val artists = artistString.splitArtistsByDelimiters(delimiters, wordDelimiters, protectedNames)
        extractedArtists.addAll(artists)
        cleanedTitle = cleanedTitle.replace(match.value, "")
    }

    return cleanedTitle.trim() to extractedArtists.distinct()
}

/**
 * Joins a list of artist names into a display string.
 * 
 * @param separator The separator to use between artist names (default: ", ")
 * @return A formatted string with all artist names joined.
 */
fun List<String>.joinArtistsForDisplay(separator: String = ", "): String {
    return this.joinToString(separator)
}
