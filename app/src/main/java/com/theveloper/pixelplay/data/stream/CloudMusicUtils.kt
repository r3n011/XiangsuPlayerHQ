package com.theveloper.pixelplay.data.stream

import org.json.JSONObject

/**
 * Shared data class for bulk sync operations across cloud music repositories.
 */
data class BulkSyncResult(
    val playlistCount: Int,
    val syncedSongCount: Int,
    val failedPlaylistCount: Int
)

/**
 * Shared utility functions for cloud music repositories.
 */
object CloudMusicUtils {

    /** Parse a JSON string of key-value pairs into a Map (used for cookie persistence). */
    fun jsonToMap(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            result[key] = obj.optString(key, "")
        }
        return result
    }

    /** Split a raw artist string like "A, B & C" into individual names. */
    fun parseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist.split(Regex("\\s*[,/&;+、]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    /**
     * ⚡ 网易云歌曲歌手拆分：直接按网易云返回的顺序拆分（singer 一般用 "、" 或带空格的 " / "
     * 连接，与 artistIds 逗号连接一一对应）。
     *
     * 不做 [parseArtistNames] 的本地暴力分割（[,/&;+、] 会把歌手名里的特殊字符如 "/"、"&"、"+" 误切，
     * 导致歌手名与真实 artistIds 错位 → 多歌手歌曲第二歌手跳转成第一歌手）。
     */
    fun parseNeteaseArtistNames(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return listOf("Unknown Artist")
        val parsed = rawArtist
            .replace(Regex("\\s*/\\s*"), "、")
            .split("、")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }
}
