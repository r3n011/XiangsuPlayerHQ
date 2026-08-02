package com.theveloper.pixelplay.data.radio

import org.json.JSONObject

/**
 * radio-browser.info 电台数据结构
 *
 * 对应 API 的 Station 结构体。字段遵循新版 API 规范：
 * - 使用 stationuuid 而非废弃的 id 字段
 * - 使用 countrycode 而非废弃的 country 字段（country 仍保留用于展示）
 */
data class RadioStation(
    val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val clickCount: Int,
    val lastCheckOk: Int,
    val hls: Int
) {
    /** 优先使用已解析（M3U/PLS/301 重定向）的流地址 */
    val streamUrl: String
        get() = urlResolved.ifBlank { url }

    companion object {
        fun fromJson(o: JSONObject): RadioStation? {
            val name = o.optString("name").trim()
            val url = o.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) return null
            return RadioStation(
                stationUuid = o.optString("stationuuid").trim(),
                name = name,
                url = url,
                urlResolved = o.optString("url_resolved").trim(),
                favicon = o.optString("favicon").trim(),
                country = o.optString("country").trim(),
                countryCode = o.optString("countrycode").trim(),
                language = o.optString("language").trim(),
                tags = o.optString("tags").trim(),
                codec = o.optString("codec").trim(),
                bitrate = o.optInt("bitrate", 0),
                votes = o.optInt("votes", 0),
                clickCount = o.optInt("clickcount", 0),
                lastCheckOk = o.optInt("lastcheckok", 0),
                hls = o.optInt("hls", 0)
            )
        }
    }
}
