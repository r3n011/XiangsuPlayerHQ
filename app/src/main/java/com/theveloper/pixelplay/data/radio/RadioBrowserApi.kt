package com.theveloper.pixelplay.data.radio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
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
        private const val USER_AGENT = "XiangsuPlayer/8.9"
        // 对 radio-browser 使用更短的超时：所有镜像并行请求，整体耗时≈最快镜像
        private const val CONNECT_TIMEOUT_SECONDS = 6L
        private const val READ_TIMEOUT_SECONDS = 8L
        // 列表缓存有效期：弱网/接口抖动时直接展示上次成功的数据，避免空白
        private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
        // 国家接口不可用（网络失败/解析异常）时的兜底常用国家列表，保证国家筛选始终可用
        val FALLBACK_COUNTRIES: List<RadioCountry> = listOf(
            RadioCountry("CN", "China"),
            RadioCountry("US", "United States"),
            RadioCountry("GB", "United Kingdom"),
            RadioCountry("JP", "Japan"),
            RadioCountry("DE", "Germany"),
            RadioCountry("FR", "France"),
            RadioCountry("AU", "Australia"),
            RadioCountry("CA", "Canada"),
            RadioCountry("RU", "Russia"),
            RadioCountry("KR", "South Korea"),
            RadioCountry("IN", "India"),
            RadioCountry("IT", "Italy"),
            RadioCountry("ES", "Spain"),
            RadioCountry("BR", "Brazil")
        )
    }
    // 短超时客户端：复用主客户端的连接池与拦截器，仅缩短超时
    private val radioClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(READ_TIMEOUT_SECONDS + 3, TimeUnit.SECONDS)
            .build()
    }

    // 内存缓存（单例，列表数据量小），用于弱网/接口抖动时回退展示
    @Volatile private var topStationsCache: List<RadioStation>? = null
    @Volatile private var topStationsCacheTime = 0L
    @Volatile private var countriesCache: List<RadioCountry>? = null
    @Volatile private var countriesCacheTime = 0L

    private fun endpoints(path: String): List<String> =
        SERVERS.map { "https://$it$path" }

    /** 单个镜像的 GET 请求；失败返回 null（由调用方继续尝试其它镜像） */
    private fun fetchJsonArray(url: String): JSONArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        radioClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("$TAG: HTTP ${response.code} @ $url")
                return null
            }
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Timber.w("$TAG: 空响应 @ $url")
                return null
            }
            return JSONArray(body)
        }
    }

    /**
     * GET 请求：并行访问所有镜像服务器，竞速取「第一个成功」的结果。
     *
     * 任一镜像失败（HTTP 错误 / 超时 / 解析异常）不会抛错，而是继续等待其余镜像；
     * 全部镜像失败时返回 null，由调用方回退到缓存/空列表。
     */
    private suspend fun getJsonArray(path: String): JSONArray? = withContext(Dispatchers.IO) {
        val deferreds = endpoints(path).map { url ->
            async {
                try {
                    fetchJsonArray(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: 请求异常 @ $url")
                    null
                }
            }
        }
        var remaining = deferreds
        while (remaining.isNotEmpty()) {
            // 谁先完成取谁：成功则立即返回；失败（null）则继续等待尚未返回的镜像
            val result = select<JSONArray?> { remaining.forEach { it.onAwait { value -> value } } }
            if (result != null) return@withContext result
            remaining = remaining.filterNot { it.isCompleted }
        }
        Timber.w("$TAG: 所有镜像请求均失败 @ $path")
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
        val now = System.currentTimeMillis()
        val cached = topStationsCache
        if (cached != null && now - topStationsCacheTime < CACHE_TTL_MILLIS) return cached
        val result = getJsonArray("/json/stations/topvote/$limit?hidebroken=true")
            ?.toStations()
            ?: cached
            ?: emptyList()
        if (result.isNotEmpty()) {
            topStationsCache = result
            topStationsCacheTime = now
        }
        return result
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

    private fun JSONArray.toCountries(): List<RadioCountry> {
        val list = ArrayList<RadioCountry>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val code = obj.optString("iso_3166_1").trim().uppercase()
            val name = obj.optString("name").trim()
            if (code.isNotBlank() && name.isNotBlank()) {
                list += RadioCountry(code = code, name = name)
            }
        }
        return list
    }

    /**
     * 国家列表（按电台数量排序）
     * /json/countries?order=stationcount&reverse=true&limit={limit}
     */
    suspend fun getCountries(limit: Int = 30): List<RadioCountry> {
        val now = System.currentTimeMillis()
        val cached = countriesCache
        if (cached != null && now - countriesCacheTime < CACHE_TTL_MILLIS) return cached
        val result = getJsonArray("/json/countries?order=stationcount&reverse=true&limit=$limit")
            ?.toCountries()
            ?: cached
            ?: FALLBACK_COUNTRIES
        if (result.isNotEmpty()) {
            countriesCache = result
            countriesCacheTime = now
        }
        return result
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
                    radioClient.newCall(request).execute().use { response ->
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
