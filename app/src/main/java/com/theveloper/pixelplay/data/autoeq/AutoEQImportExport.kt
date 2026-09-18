package com.theveloper.pixelplay.data.autoeq

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for importing and exporting AutoEQ profiles
 * Supports multiple formats:
 * - FixedBandEQ text format (standard AutoEQ output)
 * - JSON format (for sharing and backup)
 * - Parametric EQ text format
 */
object AutoEQImportExport {

    // Standard 10-band frequencies in Hz
    private val BAND_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /**
     * Parse a FixedBandEQ text format from AutoEQ
     * Format:
     * Preamp: -5.8 dB
     * Filter 1: ON PK Fc 31 Hz Gain -4.3 dB Q 1.41
     * Filter 2: ON PK Fc 62 Hz Gain -1.8 dB Q 1.41
     * ...
     */
    fun parseFixedBandEQ(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            val bands = MutableList(10) { 0f }
            val lines = text.lines()

            for (line in lines) {
                // Match pattern: Filter N: ON PK Fc XXX Hz Gain YYY dB Q ZZZ
                val filterMatch = Regex("""Filter\s+(\d+).*Gain\s+([-\d.]+)\s*dB""").find(line)
                if (filterMatch != null) {
                    val filterNum = filterMatch.groupValues[1].toIntOrNull() ?: continue
                    val gain = filterMatch.groupValues[2].toFloatOrNull() ?: continue
                    if (filterNum in 1..10) {
                        bands[filterNum - 1] = gain
                    }
                }
            }

            // Check if we got any valid data
            if (bands.any { it != 0f }) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bands
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse a parametric EQ text format
     * Format:
     * Filter N: ON PK Fc XXXX Hz Gain YY.Y dB Q Z.ZZ
     */
    fun parseParametricEQ(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            val bandSums = FloatArray(10)
            val bandCounts = IntArray(10)
            val lines = text.lines()

            for (line in lines) {
                // Match any parametric filter and map to nearest fixed band
                val filterMatch = Regex("""Fc\s+(\d+)\s*Hz.*Gain\s+([-\d.]+)\s*dB""").find(line)
                if (filterMatch != null) {
                    val freq = filterMatch.groupValues[1].toIntOrNull() ?: continue
                    val gain = filterMatch.groupValues[2].toFloatOrNull() ?: continue

                    // Find nearest band frequency
                    val bandIndex = findNearestBandIndex(freq)
                    if (bandIndex >= 0) {
                        bandSums[bandIndex] += gain
                        bandCounts[bandIndex] += 1
                    }
                }
            }

            val bands = List(10) { i ->
                if (bandCounts[i] > 0) {
                    bandSums[i] / bandCounts[i]
                } else {
                    0f
                }
            }

            if (bands.any { it != 0f }) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bands.map { it.round(1) }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse JSON format profile
     * Supports both single profile and array of profiles
     */
    fun parseJSON(text: String): List<AutoEQProfile> {
        return try {
            val trimmedText = text.trim()
            val profiles = mutableListOf<AutoEQProfile>()

            when {
                trimmedText.startsWith("[") -> {
                    // Array of profiles
                    val jsonArray = JSONArray(trimmedText)
                    for (i in 0 until jsonArray.length()) {
                        parseJSONObject(jsonArray.getJSONObject(i))?.let { profiles.add(it) }
                    }
                }
                trimmedText.startsWith("{") -> {
                    val jsonObject = JSONObject(trimmedText)

                    // Check if it's a container with "profiles" array
                    if (jsonObject.has("profiles")) {
                        val jsonArray = jsonObject.getJSONArray("profiles")
                        for (i in 0 until jsonArray.length()) {
                            parseJSONObject(jsonArray.getJSONObject(i))?.let { profiles.add(it) }
                        }
                    } else {
                        // Single profile
                        parseJSONObject(jsonObject)?.let { profiles.add(it) }
                    }
                }
            }

            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseJSONObject(json: JSONObject): AutoEQProfile? {
        return try {
            val name = json.optString("name", "Imported Profile")
            val brand = json.optString("brand", extractBrandFromName(name))
            val type = json.optString("type", "Unknown")

            val bandsArray = json.optJSONArray("bands")
            val bands = if (bandsArray != null) {
                List(minOf(bandsArray.length(), 10)) { i ->
                    bandsArray.optDouble(i, 0.0).toFloat()
                }.let { list ->
                    if (list.size < 10) list + List(10 - list.size) { 0f } else list
                }
            } else {
                List(10) { 0f }
            }

            AutoEQProfile(name = name, brand = brand, type = type, bands = bands)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Export profile to FixedBandEQ text format
     */
    fun exportToFixedBandEQ(profile: AutoEQProfile): String {
        val sb = StringBuilder()

        // Calculate preamp to prevent clipping
        val maxGain = profile.bands.maxOrNull() ?: 0f
        val preamp = if (maxGain > 0) -maxGain else 0f

        sb.appendLine("Preamp: ${preamp.round(1)} dB")

        for (i in profile.bands.indices) {
            val freq = BAND_FREQUENCIES.getOrElse(i) { 1000 }
            val gain = profile.bands[i].round(1)
            sb.appendLine("Filter ${i + 1}: ON PK Fc $freq Hz Gain $gain dB Q 1.41")
        }

        return sb.toString()
    }

    /**
     * Export profile to JSON format
     */
    fun exportToJSON(profile: AutoEQProfile): String {
        val json = JSONObject().apply {
            put("name", profile.name)
            put("brand", profile.brand)
            put("type", profile.type)
            put("bands", JSONArray(profile.bands.map { it.round(1) }))
        }
        return json.toString(2)
    }

    /**
     * Export multiple profiles to JSON format
     */
    fun exportToJSON(profiles: List<AutoEQProfile>): String {
        val container = JSONObject().apply {
            put("version", 1)
            put("source", "PixelPlay App Export")
            put("bandFrequencies", JSONArray(BAND_FREQUENCIES))
            put("profiles", JSONArray().apply {
                profiles.forEach { profile ->
                    put(JSONObject().apply {
                        put("name", profile.name)
                        put("brand", profile.brand)
                        put("type", profile.type)
                        put("bands", JSONArray(profile.bands.map { it.round(1) }))
                    })
                }
            })
        }
        return container.toString(2)
    }

    /**
     * Auto-detect format and parse
     */
    fun autoDetectAndParse(text: String, name: String = "Imported Profile"): List<AutoEQProfile> {
        val trimmedText = text.trim()

        return when {
            // JSON format
            trimmedText.startsWith("{") || trimmedText.startsWith("[") -> {
                parseJSON(trimmedText)
            }
            // FixedBandEQ or Parametric EQ format
            trimmedText.contains("Filter") && trimmedText.contains("Gain") -> {
                listOfNotNull(parseFixedBandEQ(trimmedText, name) ?: parseParametricEQ(trimmedText, name))
            }
            // Comma-separated values (simple format: name,brand,type,b1,b2,b3,...,b10)
            trimmedText.contains(",") && !trimmedText.contains("{") -> {
                parseCSV(trimmedText)
            }
            else -> {
                listOfNotNull(parseSpaceSeparated(trimmedText, name))
            }
        }
    }

    /**
     * Parse simple CSV format
     * Format: name,brand,type,b1,b2,b3,b4,b5,b6,b7,b8,b9,b10
     */
    fun parseCSV(text: String): List<AutoEQProfile> {
        return try {
            val profiles = mutableListOf<AutoEQProfile>()
            val lines = text.lines().filter { it.isNotBlank() }

            for (line in lines) {
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 13) {
                    // Full format with name, brand, type
                    val name = parts[0]
                    val brand = parts[1]
                    val type = parts[2]
                    val bands = parts.drop(3).take(10).mapNotNull { it.toFloatOrNull() }

                    if (bands.size == 10) {
                        profiles.add(AutoEQProfile(name, brand, type, bands))
                    }
                } else if (parts.size >= 10) {
                    // Just 10 band values
                    val bands = parts.take(10).mapNotNull { it.toFloatOrNull() }
                    if (bands.size == 10) {
                        profiles.add(AutoEQProfile("Imported Profile", "Unknown", "Unknown", bands))
                    }
                }
            }

            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse space/tab/newline separated list of 10 band gains
     */
    fun parseSpaceSeparated(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            // Split by any whitespace: space, tab, carriage return, newline
            val tokens = text.trim().split(Regex("""\s+""")).map { it.trim() }
            val bands = tokens.mapNotNull { it.toFloatOrNull() }

            if (bands.size >= 10) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bands.take(10)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read file content from URI
     */
    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a shareable URL-like format for the profile
     */
    fun generateShareableText(profile: AutoEQProfile): String {
        val bandsStr = profile.bands.joinToString(",") { it.round(1).toString() }
        return buildString {
            appendLine("# PixelPlay EQ Profile")
            appendLine("Name: ${profile.name}")
            appendLine("Brand: ${profile.brand}")
            appendLine("Type: ${profile.type}")
            appendLine("Bands: $bandsStr")
            appendLine()
            appendLine("# FixedBandEQ Format (for other apps)")
            append(exportToFixedBandEQ(profile))
        }
    }

    // Helper functions
    private fun findNearestBandIndex(freq: Int): Int {
        if (freq <= 0) return -1
        var minDiff = Double.MAX_VALUE
        var nearestIndex = -1

        val logFreq = kotlin.math.log10(freq.toDouble())
        for (i in BAND_FREQUENCIES.indices) {
            val logBand = kotlin.math.log10(BAND_FREQUENCIES[i].toDouble())
            val diff = kotlin.math.abs(logBand - logFreq)
            if (diff < minDiff) {
                minDiff = diff
                nearestIndex = i
            }
        }

        return nearestIndex
    }

    private fun extractBrandFromName(name: String): String {
        val knownBrands = listOf(
            "Sony", "Apple", "Bose", "Sennheiser", "Samsung", "Beats", "Audio-Technica",
            "Jabra", "OnePlus", "Anker", "Soundcore", "Xiaomi", "Marshall", "JBL", "Google",
            "Shure", "Focal", "Beyerdynamic", "AKG", "Nothing", "Oppo", "Realme", "HyperX",
            "SteelSeries", "Razer", "Logitech", "1MORE", "Creative", "HIFIMAN", "Moondrop",
            "FiiO", "Audeze", "Meze", "Dan Clark Audio", "Grado", "Koss", "Etymotic",
            "Final Audio", "Campfire Audio", "ThieAudio", "KZ", "CCA", "TRN", "BLON",
            "Truthear", "Tripowin", "Tanchjim", "Dunu", "iBasso", "Fostex", "Philips"
        )

        for (brand in knownBrands) {
            if (name.startsWith(brand, ignoreCase = true)) {
                return brand
            }
        }

        return name.split(" ").firstOrNull() ?: "Unknown"
    }

    private fun Float.round(decimals: Int): Float {
        var multiplier = 1f
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
