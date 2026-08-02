package com.theveloper.pixelplay.data.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * radio-browser.info 电台 API
 *
 * 按照官方文档（https://api.radio-browser.info/）实现：
 * - 多个镜像服务器，请求失败时自动回退到下一个服务器
 * - 发送描述性的 User-Agent（"应用名/版本"）
 * - 用户点击播放时调用 /json/url/{stationuuid} 增加点击计数
 * - 使用 stationuuid（而非废弃的 id）作为唯一标识
 */
@Singleton
class RadioBrowserApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "RadioBrowserApi"
        // 可靠的镜像服务器列表（de1 为文档示例服务器）
        private val SERVERS = listOf(
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info"
        )
        private const val USER_AGENT = "PixelPlayer/8.9"
    }

    private fun endpoints(path: String): List<String> =
        SERVERS.map { "https://$it$path" }

    /** GET 请求并在失败时依次回退到其它镜像服务器，返回 JSON 数组 */
    private suspend fun getJsonArray(path: String): JSONArray? = withContext(Dispatchers.IO) {
        for (url in endpoints(path)) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: HTTP ${response.code} @ $url")
                        return@withContext null
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        Timber.w("$TAG: 空响应 @ $url")
                        return@withContext null
                    }
                    return@withContext JSONArray(body)
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 请求异常 @ $url")
            }
        }
        Timber.w("$TAG: 所有镜像服务器均失败: $path")
        null
    }

    private fun JSONArray.toStations(): List<RadioStation> {
        val list = ArrayList<RadioStation>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            RadioStation.fromJson(obj)?.let { list += it }
        }
        return list
    }

    /**
     * 热门电台（按投票数排序）
     * /json/stations/topvote/{limit}?hidebroken=true
     */
    suspend fun getTopStations(limit: Int = 50): List<RadioStation> {
        val arr = getJsonArray("/json/stations/topvote/$limit?hidebroken=true") ?: return emptyList()
        return arr.toStations()
    }

    /**
     * 按名称搜索电台
     * /json/stations/search?name={query}&hidebroken=true&order=clickcount&reverse=true&limit={limit}
     */
    suspend fun searchStations(query: String, limit: Int = 50): List<RadioStation> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val path = "/json/stations/search?name=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        val arr = getJsonArray(path) ?: return emptyList()
        return arr.toStations()
    }

    /**
     * 按国家代码（ISO 3166-1 alpha-2）获取电台
     * /json/stations/bycountrycodeexact/{code}?hidebroken=true&order=clickcount&reverse=true&limit={limit}
     */
    suspend fun getStationsByCountry(countryCode: String, limit: Int = 50): List<RadioStation> {
        if (countryCode.isBlank()) return emptyList()
        val path = "/json/stations/bycountrycodeexact/${countryCode.trim().uppercase()}?" +
            "hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        val arr = getJsonArray(path) ?: return emptyList()
        return arr.toStations()
    }

    /**
     * 点击计数：每次用户开始播放电台时调用，帮助该电台变得热门
     * /json/url/{stationuuid}
     */
    suspend fun countClick(stationUuid: String) {
        if (stationUuid.isBlank()) return
        withContext(Dispatchers.IO) {
            for (url in endpoints("/json/url/$stationUuid")) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            // 仅用于计数，无需解析 body
                            response.body?.close()
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: 点击计数失败 @ $url")
                }
            }
        }
    }
}
