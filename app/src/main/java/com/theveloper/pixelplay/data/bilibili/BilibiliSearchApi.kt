package com.theveloper.pixelplay.data.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BilibiliSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val SEARCH_API_BASE = "https://api.bilibili.com/x/web-interface/wbi/search/type"
    private val VIDEO_DETAIL_API = "https://api.bilibili.com/x/web-interface/view"
    private val PLAY_URL_API = "https://api.bilibili.com/x/player/wbi/playurl"
    private val NAV_API = "https://api.bilibili.com/x/web-interface/nav"
    private val PAGELIST_API = "https://api.bilibili.com/x/player/pagelist"
    private val RCMD_API = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13
    )

    private var mixinKey: String? = null
    private var mixinKeyTimestamp: Long = 0
    private val MAX_RETRY_COUNT = 3
    private val RETRY_DELAY_MS = 1000L

    private fun urlEncode(input: String): String {
        return URLEncoder.encode(input, "UTF-8").replace("+", "%20")
    }

    private fun getMixinKey(orig: String): String {
        val codeUnits = orig.toCharArray()
        val result = CharArray(MIXIN_KEY_ENC_TAB.size)
        for (i in MIXIN_KEY_ENC_TAB.indices) {
            result[i] = codeUnits[MIXIN_KEY_ENC_TAB[i]]
        }
        return String(result)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        val bigInt = BigInteger(1, digest)
        return bigInt.toString(16).padStart(32, '0')
    }

    private fun encodeWbi(params: MutableMap<String, String>): Map<String, String> {
        val wts = (System.currentTimeMillis() / 1000).toString()
        params["wts"] = wts

        val keys = params.keys.toList().sorted()
        val queryBuilder = StringBuilder()
        val chrFilter = Regex("[!'()*]")
        for (key in keys) {
            val value = params[key]?.replace(chrFilter, "") ?: ""
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.append("&")
            }
            queryBuilder.append("${urlEncode(key)}=${urlEncode(value)}")
        }

        val mixinKey = mixinKey ?: run {
            Timber.e("WBI mixinKey is null, signing will fail!")
            return params
        }
        val wRid = md5(queryBuilder.toString() + mixinKey)
        params["w_rid"] = wRid

        return params
    }

    private suspend fun fetchMixinKey(): Boolean {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            while (attempts < MAX_RETRY_COUNT) {
                try {
                    val request = Request.Builder()
                        .url(NAV_API)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Origin", "https://www.bilibili.com")
                        .header("Referer", "https://www.bilibili.com/")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()

                    val body = response.body?.string() ?: run {
                        Timber.e("Bilibili nav API body is null")
                        attempts++
                        if (attempts < MAX_RETRY_COUNT) delay(RETRY_DELAY_MS * attempts)
                        continue
                    }
                    Timber.d("Bilibili nav response: ${body.take(500)}")

                    val obj = JSONObject(body)
                    val code = obj.optInt("code", -1)

                    val data = obj.optJSONObject("data")

                    if (data == null) {
                        Timber.e("Bilibili nav API data is null, code=$code")
                        attempts++
                        if (attempts < MAX_RETRY_COUNT) delay(RETRY_DELAY_MS * attempts)
                        continue
                    }

                    val wbiImg = data.optJSONObject("wbi_img")

                    if (wbiImg != null) {
                        val imgUrl = wbiImg.optString("img_url", "")
                        val subUrl = wbiImg.optString("sub_url", "")

                        Timber.d("Bilibili wbi_img: img_url=$imgUrl, sub_url=$subUrl")

                        val imgKey = getFileName(imgUrl, false)
                        val subKey = getFileName(subUrl, false)

                        if (imgKey.isNotBlank() && subKey.isNotBlank()) {
                            mixinKey = getMixinKey(imgKey + subKey)
                            mixinKeyTimestamp = System.currentTimeMillis()
                            Timber.d("Successfully fetched mixinKey from wbi_img: ${mixinKey?.take(8)}...")
                            return@withContext true
                        }
                    }

                    Timber.w("Bilibili nav API wbi_img not found or invalid, code=$code, trying alternative method")

                    val wbiImgUrl = data.optString("wbi_img_url", "")
                    val wbiSubUrl = data.optString("wbi_sub_url", "")

                    if (wbiImgUrl.isNotBlank() && wbiSubUrl.isNotBlank()) {
                        val imgKey = getFileName(wbiImgUrl, false)
                        val subKey = getFileName(wbiSubUrl, false)
                        if (imgKey.isNotBlank() && subKey.isNotBlank()) {
                            mixinKey = getMixinKey(imgKey + subKey)
                            mixinKeyTimestamp = System.currentTimeMillis()
                            Timber.d("Successfully fetched mixinKey from alt fields: ${mixinKey?.take(8)}...")
                            return@withContext true
                        }
                    }

                    attempts++
                    if (attempts < MAX_RETRY_COUNT) delay(RETRY_DELAY_MS * attempts)
                } catch (e: Exception) {
                    Timber.e(e, "Fetch mixin key exception, attempt ${attempts + 1}")
                    attempts++
                    if (attempts < MAX_RETRY_COUNT) delay(RETRY_DELAY_MS * attempts)
                }
            }

            Timber.w("nav API failed, trying alternative API")
            return@withContext tryFetchMixinKeyFromRcmd()
        }
    }

    private suspend fun tryFetchMixinKeyFromRcmd(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RCMD_API)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", "https://www.bilibili.com/")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("Bilibili rcmd API failed: ${response.code}")
                    return@withContext false
                }

                val body = response.body?.string() ?: return@withContext false
                Timber.d("Bilibili rcmd response: ${body.take(500)}")

                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    Timber.e("Bilibili rcmd API returned error: ${obj.optString("message")}")
                    return@withContext false
                }

                val data = obj.optJSONObject("data") ?: return@withContext false

                val wbiImg = data.optJSONObject("wbi_img")
                if (wbiImg != null) {
                    val imgUrl = wbiImg.optString("img_url", "")
                    val subUrl = wbiImg.optString("sub_url", "")

                    val imgKey = getFileName(imgUrl, false)
                    val subKey = getFileName(subUrl, false)

                    if (imgKey.isNotBlank() && subKey.isNotBlank()) {
                        mixinKey = getMixinKey(imgKey + subKey)
                        mixinKeyTimestamp = System.currentTimeMillis()
                        Timber.d("Successfully fetched mixinKey from rcmd API: ${mixinKey?.take(8)}...")
                        return@withContext true
                    }
                }

                Timber.e("Failed to get wbi_img from rcmd API")
                false
            } catch (e: Exception) {
                Timber.e(e, "Fetch mixin key from rcmd exception")
                false
            }
        }
    }

    private fun getFileName(url: String, includeExt: Boolean): String {
        if (url.isBlank()) return ""
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash == -1) return ""
        var fileName = url.substring(lastSlash + 1)
        if (!includeExt) {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot != -1) {
                fileName = fileName.substring(0, lastDot)
            }
        }
        return fileName
    }

    private suspend fun ensureMixinKey(): Boolean {
        val now = System.currentTimeMillis()
        if (mixinKey.isNullOrBlank() || now - mixinKeyTimestamp > 3600000) {
            return fetchMixinKey()
        }
        return true
    }

    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): BilibiliSearchResult {
        if (keyword.isBlank()) {
            return BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0)
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!ensureMixinKey()) {
                    Timber.e("Failed to get mixin key, search will likely fail")
                }

                val params = mutableMapOf(
                    "search_type" to "video",
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "page_size" to pageSize.toString(),
                    "platform" to "pc",
                    "web_location" to "1430654",
                    "order" to "click"
                )

                val signedParams = encodeWbi(params)

                val urlBuilder = StringBuilder(SEARCH_API_BASE).append("?")
                var first = true
                for ((key, value) in signedParams) {
                    if (!first) {
                        urlBuilder.append("&")
                    }
                    urlBuilder.append("${urlEncode(key)}=${urlEncode(value)}")
                    first = false
                }

                val fullUrl = urlBuilder.toString()
                Timber.d("Bilibili search URL: $fullUrl")

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Origin", "https://search.bilibili.com")
                    .header("Referer", "https://search.bilibili.com/video?keyword=${urlEncode(keyword)}")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("Bilibili search API failed: ${response.code}")
                    return@withContext BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = "网络请求失败: ${response.code}")
                }

                val body = response.body?.string()
                    ?: return@withContext BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = "响应为空")
                Timber.d("Bilibili search response: ${body.take(1000)}")

                parseSearchResponse(body, pageSize)
            } catch (e: Exception) {
                Timber.e(e, "Bilibili search API exception")
                BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = e.message ?: "搜索异常")
            }
        }
    }

    private fun parseSearchResponse(body: String, pageSize: Int): BilibiliSearchResult {
        return try {
            val obj = JSONObject(body)
            val code = obj.optInt("code", -1)
            if (code != 0) {
                val msg = obj.optString("message", "")
                val ttl = obj.optInt("ttl", 0)
                Timber.w("Bilibili search returned non-success code: $code, message: $msg, ttl: $ttl")

                if (code == -403 || code == 100016) {
                    mixinKey = null
                    Timber.e("WBI signature invalid, clearing mixinKey")
                }
                return BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = "搜索失败: $msg")
            }
            val data = obj.optJSONObject("data") ?: return BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = "数据为空")

            val total = data.optInt("numResults", 0)
            val videoArr = data.optJSONArray("result") ?: data.optJSONArray("items")
            if (videoArr == null) {
                Timber.w("Bilibili search result array not found")
                return BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = "无搜索结果")
            }

            Timber.d("Bilibili search result count: ${videoArr.length()}, total: $total")

            val list = ArrayList<BilibiliSongInfo>(videoArr.length())
            for (i in 0 until videoArr.length()) {
                val item = videoArr.optJSONObject(i) ?: continue
                val songInfo = parseVideoItem(item)
                if (songInfo.name.isNotBlank()) {
                    list += songInfo
                }
            }

            val isEnd = list.size < pageSize || data.optBoolean("is_end", false)
            BilibiliSearchResult(list = list, isEnd = isEnd, total = total)
        } catch (t: Throwable) {
            Timber.e(t, "Parse Bilibili search response exception")
            BilibiliSearchResult(list = emptyList(), isEnd = true, total = 0, error = t.message ?: "解析失败")
        }
    }

    private fun parseVideoItem(item: JSONObject): BilibiliSongInfo {
        val bvid = item.optString("bvid", "")
        val aid = item.optLong("aid", 0L)
        val cid = item.optLong("cid", 0L)

        var title = item.optString("title", "")
            .replace("<em class=\"keyword\">", "")
            .replace("</em>", "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        val duration = item.optLong("duration", 0L) * 1000L

        var pic = item.optString("pic", "")
        if (pic.startsWith("//")) {
            pic = "https:$pic"
        }

        val author = item.optString("author", "")
            .replace("<em class=\"keyword\">", "")
            .replace("</em>", "")

        val pubdate = item.optLong("pubdate", 0L)

        return BilibiliSongInfo(
            id = if (bvid.isNotBlank()) bvid else aid.toString(),
            bvid = bvid,
            aid = aid,
            cid = cid,
            name = title,
            singer = author,
            albumName = "Bilibili Video",
            duration = duration,
            pic = pic,
            playUrl = ""
        )
    }

    suspend fun getVideoDetail(aid: Long, bvid: String): BilibiliVideoDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val url = if (bvid.isNotBlank()) {
                    "$VIDEO_DETAIL_API?bvid=${urlEncode(bvid)}"
                } else {
                    "$VIDEO_DETAIL_API?aid=$aid"
                }
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", "https://www.bilibili.com/video/$bvid")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("Bilibili video detail API failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    Timber.e("Bilibili video detail API returned error: ${obj.optString("message")}")
                    return@withContext null
                }
                val data = obj.optJSONObject("data") ?: return@withContext null

                var cid = data.optLong("cid", 0L)
                if (cid == 0L) {
                    val pages = data.optJSONArray("pages")
                    if (pages != null && pages.length() > 0) {
                        val firstPage = pages.optJSONObject(0)
                        if (firstPage != null) {
                            cid = firstPage.optLong("cid", 0L)
                        }
                    }
                }

                BilibiliVideoDetail(
                    aid = data.optLong("aid", 0L),
                    bvid = data.optString("bvid", ""),
                    title = data.optString("title", ""),
                    duration = data.optLong("duration", 0L) * 1000L,
                    pic = data.optString("pic", ""),
                    cid = cid
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili video detail exception")
                null
            }
        }
    }

    suspend fun getPageList(aid: Long, bvid: String): List<BilibiliVideoDetail> {
        return withContext(Dispatchers.IO) {
            try {
                val url = if (bvid.isNotBlank()) {
                    "$PAGELIST_API?bvid=${urlEncode(bvid)}"
                } else {
                    "$PAGELIST_API?aid=$aid"
                }
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("Bilibili pagelist API failed: ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    return@withContext emptyList()
                }
                val data = obj.optJSONArray("data") ?: return@withContext emptyList()

                val list = ArrayList<BilibiliVideoDetail>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    list.add(
                        BilibiliVideoDetail(
                            aid = aid,
                            bvid = bvid,
                            title = item.optString("part", ""),
                            duration = item.optLong("duration", 0L) * 1000L,
                            pic = "",
                            cid = item.optLong("cid", 0L)
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Timber.e(e, "Bilibili pagelist exception")
                emptyList()
            }
        }
    }

    suspend fun getPlayUrl(aid: Long, cid: Long, bvid: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!ensureMixinKey()) {
                    Timber.e("Failed to get mixin key for play URL")
                    return@withContext null
                }

                val params = mutableMapOf(
                    "cid" to cid.toString(),
                    "qn" to "80",
                    "otype" to "json",
                    "fnver" to "0",
                    "fnval" to "4048",
                    "fourk" to "1"
                )
                if (bvid.isNotBlank()) {
                    params["bvid"] = bvid
                } else {
                    params["aid"] = aid.toString()
                }

                val signedParams = encodeWbi(params)

                val urlBuilder = StringBuilder(PLAY_URL_API).append("?")
                var first = true
                for ((key, value) in signedParams) {
                    if (!first) {
                        urlBuilder.append("&")
                    }
                    urlBuilder.append("${urlEncode(key)}=${urlEncode(value)}")
                    first = false
                }

                val fullUrl = urlBuilder.toString()
                Timber.d("Bilibili play URL request: $fullUrl")

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", "https://www.bilibili.com/video/$bvid")
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("Bilibili play URL API failed: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                Timber.d("Bilibili play URL response: ${body.take(1000)}")

                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    val msg = obj.optString("message", "")
                    val ttl = obj.optInt("ttl", 0)
                    Timber.e("Bilibili play URL API returned error: code=${obj.optInt("code")}, message=$msg, ttl=$ttl")
                    if (obj.optInt("code") == -403 || obj.optInt("code") == 100016) {
                        mixinKey = null
                        Timber.e("WBI signature invalid, clearing mixinKey")
                    }
                    return@withContext null
                }
                val data = obj.optJSONObject("data") ?: return@withContext null

                val dash = data.optJSONObject("dash")
                if (dash != null) {
                    val audioArr = dash.optJSONArray("audio")
                    if (audioArr != null && audioArr.length() > 0) {
                        Timber.d("Found ${audioArr.length()} audio tracks")
                        for (i in 0 until audioArr.length()) {
                            val audioObj = audioArr.optJSONObject(i)
                            if (audioObj != null) {
                                val baseUrl = audioObj.optString("base_url", "")
                                val backupUrl = audioObj.optString("backup_url", "")
                                var url = if (baseUrl.isNotBlank()) baseUrl else backupUrl
                                if (url.isBlank()) {
                                    val backupUrls = audioObj.optJSONArray("backup_url")
                                    if (backupUrls != null && backupUrls.length() > 0) {
                                        url = backupUrls.optString(0, "")
                                    }
                                }
                                if (url.isNotBlank()) {
                                    if (url.startsWith("//")) {
                                        url = "https:$url"
                                    }
                                    Timber.d("Found audio URL: ${url.take(80)}...")
                                    return@withContext url
                                }
                            }
                        }
                    } else {
                        Timber.w("No audio tracks found in DASH")
                    }
                } else {
                    Timber.w("No DASH data found")
                }

                val durl = data.optJSONArray("durl")
                if (durl != null && durl.length() > 0) {
                    Timber.d("Found ${durl.length()} durl items")
                    val durlObj = durl.optJSONObject(0)
                    if (durlObj != null) {
                        var url = durlObj.optString("url", "")
                        if (url.isBlank()) {
                            val backupUrls = durlObj.optJSONArray("backup_url")
                            if (backupUrls != null && backupUrls.length() > 0) {
                                url = backupUrls.optString(0, "")
                            }
                        }
                        if (url.isNotBlank()) {
                            if (url.startsWith("//")) {
                                url = "https:$url"
                            }
                            Timber.d("Found durl URL: ${url.take(80)}...")
                            return@withContext url
                        }
                    }
                } else {
                    Timber.w("No durl data found")
                }

                Timber.w("No playable URL found in response")
                null
            } catch (e: Exception) {
                Timber.e(e, "Bilibili play URL exception")
                null
            }
        }
    }
}