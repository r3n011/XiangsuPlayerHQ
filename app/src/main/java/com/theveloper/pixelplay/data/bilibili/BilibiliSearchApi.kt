package com.theveloper.pixelplay.data.bilibili

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BilibiliSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val repository: BilibiliRepository
) {
    private val SEARCH_API_BASE = "https://api.bilibili.com/x/web-interface/wbi/search/type"
    private val VIDEO_DETAIL_API = "https://api.bilibili.com/x/web-interface/view"
    private val PLAY_URL_API = "https://api.bilibili.com/x/player/wbi/playurl"
    private val NAV_API = "https://api.bilibili.com/x/web-interface/nav"
    private val PAGELIST_API = "https://api.bilibili.com/x/player/pagelist"
    private val RCMD_API = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"
    private val REPLY_API = "https://api.bilibili.com/x/v2/reply/main"
    // 楼中楼（子回复）列表（对齐 PiliPlus Api.replyReplyList = /x/v2/reply/reply）
    private val REPLY_REPLY_API = "https://api.bilibili.com/x/v2/reply/reply"
    // 播放器信息接口：返回视频字幕列表（作为 B 站视频歌词使用）。
    // 注意：/x/player/v2 已被 B 站要求 wbi 签名，必须走 /x/player/wbi/v2（对齐 PiliPlus playInfo）
    private val PLAYER_V2_API = "https://api.bilibili.com/x/player/wbi/v2"
    // web 端匿名会话：获取 buvid3/buvid4（评论/详情等 web 接口需要，否则被风控拦截 -412/-352）
    private val SPI_API = "https://api.bilibili.com/x/frontend/finger/spi"

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13
    )

    // BV 号解码算法常量（对齐 PiliPlus IdUtils：data / XOR_CODE / MASK_CODE / BASE=58）
    private val BV_DECODE_DATA = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
    private val BV_XOR_CODE = 23442827791579L
    private val BV_MASK_CODE = 2251799813685247L

    companion object {
        // Static sMixinKey for retry interceptor access
        @Volatile
        private var sMixinKey: String? = null
        @Volatile
        private var sMixinKeyTimestamp: Long = 0

        // Static method to invalidate sMixinKey for retry interceptor
        @JvmStatic
        fun invalidateMixinKey() {
            sMixinKey = null
            sMixinKeyTimestamp = 0
            Timber.d("MixinKey invalidated by retry interceptor")
        }

        // B 站 Android HD 版 app 签名（PiliPlus Constants）
        private const val BILI_APP_KEY = "dfca71928277209b"
        private const val BILI_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"
        private const val BILI_HD_UA =
            "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2"
        private const val BILI_STATISTICS = "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}"
        private const val BILI_TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0"
    }

    private val MAX_RETRY_COUNT = 3
    private val RETRY_DELAY_MS = 1000L

    private fun urlEncode(input: String): String {
        return URLEncoder.encode(input, "UTF-8").replace("+", "%20")
    }

    // 匿名 buvid3/buvid4 会话缓存（web 接口风控兜底）
    private var sAnonymousBuvid: String? = null

    /**
     * 获取匿名 web 会话 cookie：优先使用登录 cookie；未登录时通过
     * /x/frontend/finger/spi 获取 buvid3/buvid4（一次拉取，进程内缓存）。
     * 返回空串表示获取失败（后续请求可能被风控）。
     */
    private suspend fun buildSessionCookie(userCookie: String): String {
        if (userCookie.isNotBlank()) return userCookie
        sAnonymousBuvid?.let { return it }
        return try {
            val request = Request.Builder()
                .url(SPI_API)
                .header("User-Agent", BILI_HD_UA)
                .header("env", "prod")
                .header("app-key", "android64")
                .header("x-bili-aurora-zone", "sh001")
                .header("Referer", "https://www.bilibili.com/")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return ""
            val data = JSONObject(body).optJSONObject("data") ?: return ""
            val b3 = data.optString("b_3", "").takeIf { it.isNotBlank() } ?: return ""
            val b4 = data.optString("b_4", "")
            val cookie = buildString {
                append("buvid3=$b3")
                if (b4.isNotBlank()) append("; buvid4=$b4")
            }
            sAnonymousBuvid = cookie
            cookie
        } catch (e: Exception) {
            Timber.e(e, "Bilibili get anonymous buvid failed")
            ""
        }
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

        val currentMixinKey = sMixinKey ?: run {
            Timber.e("WBI mixinKey is null, signing will fail!")
            return params
        }
        val wRid = md5(queryBuilder.toString() + currentMixinKey)
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
                            sMixinKey = getMixinKey(imgKey + subKey)
                            sMixinKeyTimestamp = System.currentTimeMillis()
                            Timber.d("Successfully fetched sMixinKey from wbi_img: ${sMixinKey?.take(8)}...")
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
                            sMixinKey = getMixinKey(imgKey + subKey)
                            sMixinKeyTimestamp = System.currentTimeMillis()
                            Timber.d("Successfully fetched sMixinKey from alt fields: ${sMixinKey?.take(8)}...")
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
                        sMixinKey = getMixinKey(imgKey + subKey)
                        sMixinKeyTimestamp = System.currentTimeMillis()
                        Timber.d("Successfully fetched sMixinKey from rcmd API: ${sMixinKey?.take(8)}...")
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
        if (sMixinKey.isNullOrBlank() || now - sMixinKeyTimestamp > 3600000) {
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
                    sMixinKey = null
                    Timber.e("WBI signature invalid, clearing sMixinKey")
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

    suspend fun getVideoDetail(aid: Long, bvid: String, cookie: String = ""): BilibiliVideoDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val url = if (bvid.isNotBlank()) {
                    "$VIDEO_DETAIL_API?bvid=${urlEncode(bvid)}"
                } else {
                    "$VIDEO_DETAIL_API?aid=$aid"
                }
                // ⚡ x/web-interface/view 是 web 接口：必须用 web UA 配方（对齐 PiliPlus），
                // 带 app-key: android64 + BiliDroid UA 会被 WAF 直接 400 拒绝。
                val sessionCookie = buildSessionCookie(cookie)
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", BASE_UA)
                    .header("Referer", "https://www.bilibili.com/video/$bvid")
                if (sessionCookie.isNotBlank()) builder.header("Cookie", sessionCookie)
                val request = builder.get().build()

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

    /**
     * BV1 号 → av 号 纯算法解码（精确移植 PiliPlus IdUtils.bv2av，无需网络）。
     * 用于评论/详情等接口解析 aid 失败时的本地兜底（PiliPlus 评论 oid 即来自此算法）。
     */
    fun bvToAid(bv: String): Long {
        if (bv.isBlank() || !bv.startsWith("BV", ignoreCase = true) || bv.length < 12) return 0L
        // sublist(3) 去掉 "BV1" 前缀（对齐 PiliPlus：bvRegex 为 bv1[0-9a-zA-Z]{9}）
        val arr = bv.toCharArray().copyOfRange(3, bv.length)
        swap(arr, 0, 6)
        swap(arr, 1, 4)
        var tmp = 0L
        for (c in arr) {
            val v = BV_DECODE_DATA.indexOf(c)
            if (v < 0) return 0L
            tmp = tmp * 58 + v
        }
        return (tmp and BV_MASK_CODE) xor BV_XOR_CODE
    }

    private fun swap(arr: CharArray, i: Int, j: Int) {
        val t = arr[i]; arr[i] = arr[j]; arr[j] = t
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

    /**
     * 获取 B 站视频字幕并转换为 LRC 歌词（对齐 PiliPlus vttSubtitles 思路）。
     *
     * 链路：/x/player/v2 拿字幕列表（优先中文/ AI 字幕）→ 下载字幕 JSON（补 https:）
     * → 逐句（from/to/content）转成 [mm:ss.xx] 内容 的 LRC 文本。
     *
     * @param bvid  视频 BV 号（必须以 BV 开头）
     * @param cid   分 P 的 cid；传 0 时内部通过 getVideoDetail 兜底解析
     * @param cookie 登录 cookie；为空走匿名 buvid 会话
     * @return LRC 文本；无字幕或失败返回 null
     */
    suspend fun getBilibiliSubtitleLrc(bvid: String, cid: Long = 0L, cookie: String = ""): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (bvid.isBlank() || !bvid.startsWith("BV", ignoreCase = true)) return@withContext null
                var realCid = cid
                if (realCid <= 0L) {
                    realCid = getVideoDetail(aid = 0L, bvid = bvid, cookie = cookie)?.cid ?: 0L
                }
                if (realCid <= 0L) return@withContext null

                val sessionCookie = buildSessionCookie(cookie)
                // /x/player/wbi/v2 需要 wbi 签名，否则被 B 站风控拒绝（-403）
                ensureMixinKey()
                val signed = encodeWbi(mutableMapOf("bvid" to bvid, "cid" to realCid.toString()))
                val signedUrl = buildString {
                    append(PLAYER_V2_API).append("?")
                    signed.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) append("&")
                        append(urlEncode(k)).append("=").append(urlEncode(v))
                    }
                }
                Timber.d("Bilibili subtitle list URL: $signedUrl")
                val builder = Request.Builder()
                    .url(signedUrl)
                    .header("User-Agent", BILI_HD_UA)
                    .header("env", "prod")
                    .header("app-key", "android64")
                    .header("x-bili-aurora-zone", "sh001")
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", "https://www.bilibili.com/video/$bvid")
                // ⚡ 显式设置 Cookie（同 fetchCommentPage），防止 Interceptor 注入错误 cookie
                builder.header("Cookie", sessionCookie)
                val response = okHttpClient.newCall(builder.get().build()).execute()
                val body = response.body?.string() ?: return@withContext null
                val bodyObj = JSONObject(body)
                if (bodyObj.optInt("code", -1) != 0) {
                    Timber.w("Bilibili subtitle list API error: code=${bodyObj.optInt("code", -1)}, msg=${bodyObj.optString("message")}")
                    return@withContext null
                }
                val data = bodyObj.optJSONObject("data") ?: return@withContext null
                val subtitle = data.optJSONObject("subtitle") ?: return@withContext null
                val subtitles = subtitle.optJSONArray("subtitles") ?: return@withContext null
                Timber.d("Bilibili subtitles count: ${subtitles.length()}")
                if (subtitles.length() == 0) return@withContext null

                // 选择主字幕：优先中文字幕（含 AI 字幕 ai-zh），否则取第一个
                var primary: JSONObject? = null
                // 选择翻译字幕：优先英文字幕（en / en-US / ai-en），用于双语显示
                var secondary: JSONObject? = null
                for (i in 0 until subtitles.length()) {
                    val s = subtitles.optJSONObject(i) ?: continue
                    if (primary == null) primary = s
                    val lan = s.optString("lan", "")
                    val lanDoc = s.optString("lan_doc", "")
                    if (lan.startsWith("zh", ignoreCase = true) || lan == "ai-zh" || lanDoc.contains("中文")) {
                        primary = s
                    }
                    if (lan.startsWith("en", ignoreCase = true) || lan == "ai-en" || lanDoc.contains("英文")) {
                        secondary = s
                    }
                }

                val primaryUrl = primary?.optString("subtitle_url", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: primary?.optString("subtitle_url_v2", "")
                    ?: return@withContext null
                if (primaryUrl.isBlank()) return@withContext null

                // 下载主字幕内容
                val primaryLines = downloadSubtitleJson(primaryUrl, bvid) ?: return@withContext null
                if (primaryLines.isEmpty()) return@withContext null

                // 下载翻译字幕内容（如有）
                val secondaryLines = secondary?.let { sec ->
                    val secUrl = sec.optString("subtitle_url", "")
                        .takeIf { it.isNotBlank() }
                        ?: sec.optString("subtitle_url_v2", "")
                    if (secUrl.isNullOrBlank()) return@let null
                    downloadSubtitleJson(secUrl, bvid)
                }

                // 字幕逐句转 LRC（双语时翻译行紧跟主行，用 LRC 翻译标记语法）
                val sb = StringBuilder(primaryLines.size * 48)
                for (i in primaryLines.indices) {
                    val (from, content) = primaryLines[i]
                    val totalMs = (from * 1000.0).toLong()
                    val mm = (totalMs / 60000).toString().padStart(2, '0')
                    val ss = ((totalMs % 60000) / 1000).toString().padStart(2, '0')
                    val ms = ((totalMs % 1000) / 10).toString().padStart(2, '0')
                    // 查找时间最接近的翻译行（±500ms 容差）
                    val translation = secondaryLines?.let { secLines ->
                        secLines.minByOrNull { kotlin.math.abs(it.first - from) }
                            ?.takeIf { kotlin.math.abs(it.first - from) < 0.5 }
                            ?.second
                    }
                    if (translation != null) {
                        // 双语：主行后紧跟翻译行（相同时间戳），pairTranslationLines 会自动配对为 translation
                        sb.append("[$mm:$ss.$ms]$content\n")
                        sb.append("[$mm:$ss.$ms]$translation\n")
                    } else {
                        sb.append("[$mm:$ss.$ms]$content\n")
                    }
                }
                if (sb.isEmpty()) return@withContext null
                sb.toString()
            } catch (e: Exception) {
                Timber.e(e, "Bilibili get subtitle lrc failed")
                null
            }
        }
    }

    /**
     * 下载B站字幕JSON并解析为 (时间秒, 文本) 列表。
     * 字幕URL可能是 // 开头、http:// 或 https://，自动补全协议。
     */
    private fun downloadSubtitleJson(subtitleUrl: String, bvid: String): List<Pair<Double, String>>? {
        return try {
            val fullUrl = when {
                subtitleUrl.startsWith("//") -> "https:$subtitleUrl"
                subtitleUrl.startsWith("http://", ignoreCase = true) ->
                    subtitleUrl.replaceFirst("http://", "https://")
                subtitleUrl.startsWith("https://", ignoreCase = true) -> subtitleUrl
                else -> "https://$subtitleUrl"
            }
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", BILI_HD_UA)
                .header("env", "prod")
                .header("app-key", "android64")
                .header("x-bili-aurora-zone", "sh001")
                .header("Referer", "https://www.bilibili.com/video/$bvid")
                // ⚡ 显式设置空 Cookie，防止 Interceptor 注入残留登录 cookie
                .header("Cookie", "")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val bodyArr = JSONObject(body).optJSONArray("body") ?: return null
            val lines = mutableListOf<Pair<Double, String>>()
            for (i in 0 until bodyArr.length()) {
                val item = bodyArr.optJSONObject(i) ?: continue
                val from = item.optDouble("from", -1.0)
                val content = item.optString("content", "").trim()
                if (from < 0.0 || content.isEmpty()) continue
                lines.add(from to content)
            }
            lines
        } catch (e: Exception) {
            Timber.e(e, "Bilibili download subtitle JSON failed")
            null
        }
    }

    /**
     * 获取视频评论（对齐 PiliPlus 的 web 未登录接口 /x/v2/reply/main）。
     *
     * 自愈回退链（对齐 PiliPlus AccountManager 的会话容错）：
     *  1. 首选客户端配方：已登录走 /x/v2/reply 传统分页；未登录走 /x/v2/reply/main 游标
     *  2. 已登录但会话失效/被风控（-101 未登录 / -352 / -403 / -412）→ 降级匿名重试
     *  3. 匿名仍被风控 → 换 PiliPlus 同款「无 Cookie web 配方」重试（ReplyHttp.options cookie=''）
     *
     * @param aid 视频 av 号（oid）
     * @param offset 游标分页偏移（首次传空串）
     * @param mode 排序：3=按热度，2=按时间
     */
    suspend fun getComments(
        aid: Long,
        offset: String = "",
        mode: Int = 3,
        cookie: String = "",
        isLoggedIn: Boolean = false
    ): BilibiliCommentResult {
        return withContext(Dispatchers.IO) {
            try {
                // 已登录但 cookie 已清空/空：直接按匿名处理，避免走已登录分支被 -101 拦截
                val useLogin = isLoggedIn && cookie.isNotBlank()
                var result = fetchCommentPage(aid, offset, mode, cookie, useLogin)
                // 2. 已登录会话失效/被风控：降级匿名重试
                if (useLogin && (result.isAuthError || result.isRiskBlocked)) {
                    Timber.w("Bilibili comments session invalid, fallback to anonymous: ${result.error}")
                    result = fetchCommentPage(aid, offset, mode, "", isLoggedIn = false)
                }
                // 3. 匿名仍被风控：换 PiliPlus 同款无 Cookie 配方重试
                if (result.isRiskBlocked) {
                    Timber.w("Bilibili comments risk-blocked, retry with PiliPlus-style no-cookie request: ${result.error}")
                    val web = fetchCommentPageWeb(aid, offset, mode)
                    if (web.error.isBlank()) result = web
                }
                result
            } catch (e: Exception) {
                Timber.e(e, "Bilibili comments exception")
                BilibiliCommentResult(error = e.message ?: "网络异常")
            }
        }
    }

    /** 会话失效类错误码（-101 未登录 / -404 / -352 / -403） */
    private val BilibiliCommentResult.isAuthError: Boolean
        get() = error.contains("-101") || error.contains("未登录") ||
            error.contains("-404") || error.contains("-352") || error.contains("-403")

    /** 风控拦截类错误码（-412 / -352 / -403 或 HTTP 412/403） */
    private val BilibiliCommentResult.isRiskBlocked: Boolean
        get() = error.contains("-412") || error.contains("-352") ||
            error.contains("-403") || error.contains("HTTP 412") || error.contains("HTTP 403")

    /** 客户端配方：B站 HD 客户端 UA + app-key 头 + buvid/登录 cookie */
    private suspend fun fetchCommentPage(
        aid: Long,
        offset: String,
        mode: Int,
        cookie: String,
        isLoggedIn: Boolean
    ): BilibiliCommentResult {
        return try {
            val sessionCookie = buildSessionCookie(cookie)
            val builder = Request.Builder()
            // 对齐 PiliPlus reply.dart 双分支：
            //  - 未登录：/x/v2/reply/main 游标接口（无 need_top、无 plat=1，抗风控）
            //  - 已登录：/x/v2/reply?oid&type&sort&pn&ps 传统分页
            val url: String
            var isCursorMode = false
            if (!isLoggedIn) {
                isCursorMode = true
                // pagination_str 含 { " } 等非法 URL 字符，必须百分号编码
                // （PiliPlus 用 dio 自动编码 queryParameters；OkHttp 的 HttpUrl 解析非法字符会抛异常）
                val escaped = offset.replace("\"", "\\\"")
                val paginationStr = urlEncode("{\"offset\":\"$escaped\"}")
                url = "$REPLY_API?oid=$aid&type=1&mode=$mode&pagination_str=$paginationStr"
            } else {
                val pn = offset.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val sort = if (mode == 3) 1 else 0 // 3=热度→sort=1；2=最新→sort=0
                url = "$REPLY_ACTION_API?oid=$aid&type=1&sort=$sort&pn=$pn&ps=20"
            }
            Timber.d("Bilibili comments URL: $url")
            builder.url(url)
            builder
                .header("User-Agent", BILI_HD_UA)
                .header("env", "prod")
                .header("app-key", "android64")
                .header("x-bili-aurora-zone", "sh001")
                .header("Origin", "https://www.bilibili.com")
                .header("Referer", "https://www.bilibili.com/video/av$aid")
            // ⚡ 显式设置 Cookie 头：对齐 PiliPlus 未登录时 options.cookie=''，
            // 防止 BilibiliHeaderInterceptor 自动添加残留的登录 cookie 导致风控拦截 (-352/-412)。
            // 已登录时使用完整 cookie；未登录时使用匿名 buvid 或空串。
            builder.header("Cookie", sessionCookie)
            val response = okHttpClient.newCall(builder.get().build()).execute()
            if (!response.isSuccessful) {
                val err = "评论接口 HTTP ${response.code}"
                Timber.e("Bilibili comments API failed: ${response.code}")
                return BilibiliCommentResult(error = err)
            }
            val body = response.body?.string() ?: return BilibiliCommentResult(error = "评论接口返回空响应")
            val obj = JSONObject(body)
            if (obj.optInt("code", -1) != 0) {
                val msg = obj.optString("message", "未知错误").ifBlank { "未知错误" }
                Timber.w("Bilibili comments API error: code=${obj.optInt("code", -1)}, msg=$msg")
                return BilibiliCommentResult(error = "(${obj.optInt("code", -1)}) $msg")
            }
            val pn = if (!isCursorMode) offset.toIntOrNull()?.coerceAtLeast(1) ?: 1 else 1
            parseCommentResponse(obj, isLoggedIn = isLoggedIn, pn = pn)
        } catch (e: Exception) {
            Timber.e(e, "Bilibili fetchCommentPage exception")
            BilibiliCommentResult(error = e.message ?: "网络异常")
        }
    }

    /** PiliPlus 同款匿名配方：web UA + app-key 头 + 无 Cookie（对齐 ReplyHttp.replyList options: cookie=''） */
    private suspend fun fetchCommentPageWeb(
        aid: Long,
        offset: String,
        mode: Int
    ): BilibiliCommentResult {
        return try {
            val escaped = offset.replace("\"", "\\\"")
            val paginationStr = urlEncode("{\"offset\":\"$escaped\"}")
            val url = "$REPLY_API?oid=$aid&type=1&mode=$mode&pagination_str=$paginationStr"
            Timber.d("Bilibili comments web-style URL: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BASE_UA)
                .header("env", "prod")
                .header("app-key", "android64")
                .header("x-bili-aurora-zone", "sh001")
                // ⚡ 对齐 PiliPlus：显式设置空 Cookie，防止 BilibiliHeaderInterceptor
                // 自动添加残留登录 cookie 导致风控拦截；补充 Referer/Origin 防止 WAF 拒绝。
                .header("Cookie", "")
                .header("Origin", "https://www.bilibili.com")
                .header("Referer", "https://www.bilibili.com/video/av$aid")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return BilibiliCommentResult(error = "评论接口 HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return BilibiliCommentResult(error = "评论接口返回空响应")
            val obj = JSONObject(body)
            if (obj.optInt("code", -1) != 0) {
                val msg = obj.optString("message", "未知错误").ifBlank { "未知错误" }
                return BilibiliCommentResult(error = "(${obj.optInt("code", -1)}) $msg")
            }
            parseCommentResponse(obj, isLoggedIn = false)
        } catch (e: Exception) {
            Timber.e(e, "Bilibili fetchCommentPageWeb exception")
            BilibiliCommentResult(error = e.message ?: "网络异常")
        }
    }

    private fun parseCommentResponse(obj: JSONObject, isLoggedIn: Boolean = false, pn: Int = 1): BilibiliCommentResult {
        val data = obj.optJSONObject("data") ?: return BilibiliCommentResult(error = "评论数据为空")
        // 视频 UP 主 uid（对齐 PiliPlus data.upper.mid），用于识别 UP 本人评论与置顶权限
        val upMid = data.optJSONObject("upper")?.optLong("mid", 0L) ?: 0L
        val comments = mutableListOf<BilibiliComment>()
        val replies = data.optJSONArray("replies")
        if (replies != null) {
            for (i in 0 until replies.length()) {
                parseCommentItem(replies.optJSONObject(i), top = false, upMid = upMid)?.let { comments.add(it) }
            }
        }
        val topComments = mutableListOf<BilibiliComment>()
        // 对齐 PiliPlus Top 模型：置顶评论在 data.top.upper（UP 主置顶）和 data.top.admin（管理员置顶），
        // 而非 data.top_replies（旧接口字段，/x/v2/reply/main 不返回此数组）。
        val topObj = data.optJSONObject("top")
        if (topObj != null) {
            // UP 主置顶评论
            val topUpper = topObj.optJSONObject("upper")
            if (topUpper != null && topUpper.optLong("rpid", 0L) > 0L) {
                parseCommentItem(topUpper, top = true, upMid = upMid)?.let { topComments.add(it) }
            }
            // 管理员置顶评论
            val topAdmin = topObj.optJSONObject("admin")
            if (topAdmin != null && topAdmin.optLong("rpid", 0L) > 0L) {
                parseCommentItem(topAdmin, top = true, upMid = upMid)?.let { topComments.add(it) }
            }
        }
        // 兜底：部分旧接口可能仍返回 top_replies 数组
        if (topComments.isEmpty()) {
            val topReplies = data.optJSONArray("top_replies")
            if (topReplies != null) {
                for (i in 0 until topReplies.length()) {
                    parseCommentItem(topReplies.optJSONObject(i), top = true, upMid = upMid)?.let { topComments.add(it) }
                }
            }
        }
        if (isLoggedIn) {
            // 已登录 /x/v2/reply 传统分页：data.page.count 为评论总数，offset 用 pn 字符串传递
            val page = data.optJSONObject("page")
            val total = page?.optInt("count", 0) ?: 0
            val loaded = pn * 20
            return BilibiliCommentResult(
                comments = comments,
                topComments = topComments,
                hasMore = loaded < total,
                nextOffset = if (loaded < total) (pn + 1).toString() else "",
                upMid = upMid
            )
        }
        // 未登录 /x/v2/reply/main 游标：next_offset 在 cursor.pagination_reply 下（不是 cursor.next）
        val cursor = data.optJSONObject("cursor")
        val pagination = cursor?.optJSONObject("pagination_reply")
        val nextOffset = pagination?.optString("next_offset", "") ?: ""
        val isEnd = cursor?.optBoolean("is_end", true) ?: true
        return BilibiliCommentResult(
            comments = comments,
            topComments = topComments,
            hasMore = !isEnd && nextOffset.isNotBlank(),
            nextOffset = nextOffset,
            upMid = upMid
        )
    }

    /**
     * 解析单条评论（对齐 PiliPlus ReplyItemModel 核心字段）。
     * 命中关键词/广告过滤（见 [isFilteredComment]）时返回 null，由调用方跳过。
     */
    private fun parseCommentItem(item: JSONObject, top: Boolean, upMid: Long): BilibiliComment? {
        if (isFilteredComment(item)) {
            Timber.d("Bilibili comment filtered by user settings")
            return null
        }
        val member = item.optJSONObject("member")
        val content = item.optJSONObject("content")
        val mid = member?.optLong("mid", 0L) ?: 0L
        val avatar = member?.optString("avatar", "")?.let {
            if (it.startsWith("//")) "https:$it" else it
        } ?: ""
        val replyControl = item.optJSONObject("reply_control")
        val isUpTop = replyControl?.optBoolean("is_up_top", false)
            ?: item.optBoolean("is_up_top", false)
        return BilibiliComment(
            rpid = item.optLong("rpid", 0L),
            mid = mid,
            nickname = member?.optString("uname", "") ?: "",
            avatarUrl = avatar,
            level = member?.optJSONObject("level_info")?.optInt("current_level", 0) ?: 0,
            message = content?.optString("message", "") ?: "",
            like = item.optInt("like", 0),
            replyCount = item.optInt("rcount", 0),
            ctime = item.optLong("ctime", 0L),
            isTop = top,
            // —— 对齐 PiliPlus 新增 ——
            root = item.optLong("root", 0L),
            parent = item.optLong("parent", 0L),
            isUp = upMid > 0L && mid == upMid,
            isUpTop = isUpTop,
            liked = replyControl?.optInt("like_state", 0) == 1,
            emotes = parseEmotes(content),
            pictures = parsePictures(content),
            subReplies = parseSubReplies(item, upMid)
        )
    }

    /** 解析 content.emote：表情文本 -> 图片 URL（对齐 PiliPlus content.emotes，url 补全 https:） */
    private fun parseEmotes(content: JSONObject?): Map<String, String> {
        val emoteObj = content?.optJSONObject("emote") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        val keys = emoteObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val url = emoteObj.optJSONObject(k)?.optString("url", "")?.takeIf { it.isNotBlank() } ?: continue
            result[k] = if (url.startsWith("//")) "https:$url" else url
        }
        return result
    }

    /** 解析 content.pictures：评论图片 img_src 列表 */
    private fun parsePictures(content: JSONObject?): List<String> {
        val arr = content?.optJSONArray("pictures") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val src = arr.optJSONObject(i)?.optString("img_src", "")?.takeIf { it.isNotBlank() } ?: continue
            result.add(if (src.startsWith("//")) "https:$src" else src)
        }
        return result
    }

    /** 解析内嵌子回复（/x/v2/reply/main 顶层评论自带 replies 首屏，对齐 PiliPlus ReplyItemModel.replies） */
    private fun parseSubReplies(item: JSONObject, upMid: Long): List<BilibiliComment> {
        val arr = item.optJSONArray("replies") ?: return emptyList()
        val result = mutableListOf<BilibiliComment>()
        for (i in 0 until arr.length()) {
            parseCommentItem(arr.optJSONObject(i), top = false, upMid = upMid)?.let { result.add(it) }
        }
        return result
    }

    /**
     * 关键词/广告评论过滤（对齐 PiliPlus ReplyGrpc.needRemoveGrpc）：
     *  - 关键词过滤：commentBanWords 为正则表达式（默认关闭）
     *  - 广告过滤：高能 B 站商品链接（gaoneng.bilibili.com/tetris）或 jump_url 携带商品标记
     */
    private fun isFilteredComment(item: JSONObject): Boolean {
        if (!repository.commentFilterEnabled && !repository.antiGoodsFilterEnabled) return false
        val content = item.optJSONObject("content") ?: return false
        val message = content.optString("message", "")
        if (repository.commentFilterEnabled) {
            val words = repository.commentBanWords.trim()
            if (words.isNotEmpty()) {
                return try {
                    Regex(words, RegexOption.IGNORE_CASE).containsMatchIn(message)
                } catch (e: Exception) {
                    Timber.w(e, "Invalid comment ban regex")
                    false
                }
            }
        }
        if (repository.antiGoodsFilterEnabled) {
            // 对齐 PiliPlus Constants.goodsUrlPrefix = "https://gaoneng.bilibili.com/tetris"
            if (message.contains("gaoneng.bilibili.com")) return true
            val jumpUrl = content.optJSONObject("jump_url") ?: return false
            val keys = jumpUrl.keys()
            while (keys.hasNext()) {
                val entry = jumpUrl.optJSONObject(keys.next()) ?: continue
                val extra = entry.optJSONObject("extra") ?: continue
                if (extra.optInt("goods_cm_control", 0) == 1 ||
                    extra.has("goods_item_id") ||
                    extra.has("goods_prefetched_cache")
                ) return true
            }
        }
        return false
    }

    /**
     * 楼中楼（子回复）列表，对齐 PiliPlus ReplyHttp.replyReplyList：
     * GET /x/v2/reply/reply?oid&root&pn&type=1&sort=1（登录时附加 csrf）
     *
     * @param root 顶层评论 rpid
     * @param page 页码（从 1 开始）
     */
    suspend fun getReplyReplies(
        oid: Long,
        root: Long,
        page: Int = 1,
        csrf: String? = null,
        cookie: String = "",
        isLoggedIn: Boolean = false
    ): BilibiliReplyRepliesResult {
        return withContext(Dispatchers.IO) {
            try {
                var result = fetchReplyRepliesPage(oid, root, page, csrf, cookie, isLoggedIn)
                // 已登录会话失效（-101 未登录等）：降级匿名重试（对齐 getComments 的自愈逻辑）
                if (isLoggedIn && (result.error.contains("-101") || result.error.contains("未登录") ||
                        result.error.contains("-352") || result.error.contains("-403") || result.error.contains("-412"))
                ) {
                    Timber.w("Bilibili reply-replies session invalid, fallback to anonymous: ${result.error}")
                    result = fetchReplyRepliesPage(oid, root, page, null, "", isLoggedIn = false)
                }
                result
            } catch (e: Exception) {
                Timber.e(e, "Bilibili reply-reply exception")
                BilibiliReplyRepliesResult(error = e.message ?: "网络异常")
            }
        }
    }

    private suspend fun fetchReplyRepliesPage(
        oid: Long,
        root: Long,
        page: Int,
        csrf: String?,
        cookie: String,
        isLoggedIn: Boolean
    ): BilibiliReplyRepliesResult {
        return try {
            val sessionCookie = buildSessionCookie(cookie)
            val pn = page.coerceAtLeast(1)
            val query = StringBuilder()
                .append("oid=$oid&root=$root&pn=$pn&type=1&sort=1")
            if (isLoggedIn && !csrf.isNullOrBlank()) query.append("&csrf=$csrf")
            val url = "$REPLY_REPLY_API?$query"
            Timber.d("Bilibili reply-reply URL: $url")
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", BILI_HD_UA)
                .header("env", "prod")
                .header("app-key", "android64")
                .header("x-bili-aurora-zone", "sh001")
                .header("Origin", "https://www.bilibili.com")
                .header("Referer", "https://www.bilibili.com/video/av$oid")
            // ⚡ 显式设置 Cookie 头（同 fetchCommentPage），防止 BilibiliHeaderInterceptor 注入错误 cookie
            builder.header("Cookie", sessionCookie)
            val response = okHttpClient.newCall(builder.get().build()).execute()
            if (!response.isSuccessful) {
                return BilibiliReplyRepliesResult(error = "子回复接口 HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return BilibiliReplyRepliesResult(error = "子回复接口返回空响应")
            val obj = JSONObject(body)
            if (obj.optInt("code", -1) != 0) {
                val msg = obj.optString("message", "未知错误").ifBlank { "未知错误" }
                return BilibiliReplyRepliesResult(error = "(${obj.optInt("code", -1)}) $msg")
            }
            val data = obj.optJSONObject("data") ?: return BilibiliReplyRepliesResult(error = "子回复数据为空")
            val list = mutableListOf<BilibiliComment>()
            val arr = data.optJSONArray("replies")
            val upMid = data.optJSONObject("upper")?.optLong("mid", 0L) ?: 0L
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseCommentItem(arr.optJSONObject(i), top = false, upMid = upMid)?.let { list.add(it) }
                }
            }
            val pageObj = data.optJSONObject("page")
            val total = pageObj?.optInt("count", 0) ?: 0
            val loaded = pn * 20
            BilibiliReplyRepliesResult(
                replies = list,
                hasMore = loaded < total,
                nextPage = pn + 1,
                error = ""
            )
        } catch (e: Exception) {
            Timber.e(e, "Bilibili fetchReplyRepliesPage exception")
            BilibiliReplyRepliesResult(error = e.message ?: "网络异常")
        }
    }

    // ─────────────────────────────────────────────────────────
    // 扫码登录（TV 端 auth_code，完全对齐 PiliPlus 的
    //   getHDcode(/x/passport-tv-login/qrcode/auth_code) +
    //   codePoll(/x/passport-tv-login/qrcode/poll)，
    //   走 appSign 签名，比 web 端 qrcode 稳定，无需额外会话 cookie）
    // ─────────────────────────────────────────────────────────

    private val PASSPORT_API = "https://passport.bilibili.com"
    private val REPLY_ACTION_API = "https://api.bilibili.com/x/v2/reply"
    private val FAV_API = "https://api.bilibili.com/x/v3/fav"
    private val BASE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 第一步：申请 TV 扫码登录二维码。返回 auth_code 与扫码 url。 */
    suspend fun generateLoginQrCode(): BilibiliQrCodeResult? {
        return withContext(Dispatchers.IO) {
            try {
                val params = linkedMapOf(
                    "local_id" to "0",
                    "platform" to "android",
                    "mobi_app" to "android_hd"
                ).toMutableMap()
                val bodyQuery = appSignAndQuery(params)
                val response = okHttpClient.newCall(
                    appPostRequest("$PASSPORT_API/x/passport-tv-login/qrcode/auth_code", bodyQuery)
                ).execute()
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    Timber.w("Bilibili TV auth_code failed: ${body.take(200)}")
                    return@withContext null
                }
                val data = obj.optJSONObject("data") ?: return@withContext null
                BilibiliQrCodeResult(
                    qrcodeKey = data.optString("auth_code", ""),
                    url = data.optString("url", "")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili generate QrCode failed")
                null
            }
        }
    }

    /**
     * 第二步：轮询 TV 扫码结果。
     * 登录成功（code==0）时从 data.cookie_info.cookies 数组解析会话 cookie。
     */
    suspend fun pollLoginQrCode(qrcodeKey: String, cookies: String = ""): BilibiliLoginPollResult {
        return withContext(Dispatchers.IO) {
            try {
                val params = linkedMapOf(
                    "auth_code" to qrcodeKey,
                    "local_id" to "0"
                ).toMutableMap()
                val bodyQuery = appSignAndQuery(params)
                val response = okHttpClient.newCall(
                    appPostRequest("$PASSPORT_API/x/passport-tv-login/qrcode/poll", bodyQuery)
                ).execute()
                val body = response.body?.string() ?: return@withContext BilibiliLoginPollResult(code = -1, message = "空响应")
                val obj = JSONObject(body)
                val code = obj.optInt("code", -1)
                if (code != 0) {
                    val msg = when (code) {
                        86101 -> "等待扫码"
                        86090 -> "已扫码，请在手机上确认"
                        86038 -> "二维码已过期"
                        else -> obj.optString("message", "登录失败").ifBlank { "登录失败" }
                    }
                    return@withContext BilibiliLoginPollResult(success = false, code = code, message = msg)
                }
                // 从 data.cookie_info.cookies 数组收集会话 cookie
                val cookieMap = mutableMapOf<String, String>()
                val data = obj.optJSONObject("data")
                val cookiesArr = data?.optJSONObject("cookie_info")?.optJSONArray("cookies")
                if (cookiesArr != null) {
                    for (i in 0 until cookiesArr.length()) {
                        val c = cookiesArr.optJSONObject(i)
                        val name = c?.optString("name", "")
                        val value = c?.optString("value", "")
                        if (name.isNullOrBlank() || value == null) continue
                        cookieMap[name] = value
                    }
                }
                BilibiliLoginPollResult(success = true, code = 0, cookies = cookieMap)
            } catch (e: Exception) {
                Timber.e(e, "Bilibili poll login failed")
                BilibiliLoginPollResult(code = -1, message = e.message ?: "网络错误")
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 手机号登录（app 端短信验证码，完全对齐 PiliPlus 的
    //   appSmsCode(/x/passport-login/sms/send) + logInByAppSms(/x/passport-login/login/sms)
    //   方案：appSign 签名 + buvid/deviceId + RSA dt）
    // ─────────────────────────────────────────────────────────

    private val smsBuvid by lazy { generateBuvid() }
    private val smsDeviceId by lazy { generateDeviceId() }

    /** PiliPlus generateBuvid：'XY' + 3 个 md5 字符 + 32 位 hex */
    private fun generateBuvid(): String {
        val rnd = SecureRandom()
        val raw = ByteArray(16).also { rnd.nextBytes(it) }
        val hex = md5Hex(raw)
        return "XY${hex[2]}${hex[12]}${hex[22]}$hex"
    }

    /** PiliPlus genDeviceId：16 随机字节 + BCD 时间(7) + 8 随机字节 + 校验字节，hex 小写 */
    private fun generateDeviceId(): String {
        val rnd = SecureRandom()
        val bytes = ByteArray(31).also { rnd.nextBytes(it) }
        val now = java.util.Calendar.getInstance()
        val y = now.get(java.util.Calendar.YEAR)
        val mo = now.get(java.util.Calendar.MONTH) + 1
        val d = now.get(java.util.Calendar.DAY_OF_MONTH)
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        val mi = now.get(java.util.Calendar.MINUTE)
        val sec = now.get(java.util.Calendar.SECOND)
        bytes[16] = bcdByte(y / 100).toByte()
        bytes[17] = bcdByte(y % 100).toByte()
        bytes[18] = bcdByte(mo).toByte()
        bytes[19] = bcdByte(d).toByte()
        bytes[20] = bcdByte(h).toByte()
        bytes[21] = bcdByte(mi).toByte()
        bytes[22] = bcdByte(sec).toByte()
        var sum = 0
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append("%02x".format(b.toInt() and 0xFF))
            sum += b.toInt() and 0xFF
        }
        sb.append("%02x".format(sum and 0xFF))
        return sb.toString()
    }

    private fun bcdByte(dec: Int): Int = ((dec / 10) shl 4) or (dec % 10)

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b.toInt() and 0xFF))
        return sb.toString()
    }

    /**
     * PiliPlus AppSign.appSign：写入 appkey/ts，排序后拼接 query + appsec 求 md5 得到 sign。
     * 返回最终可直接作为 form body 的 query（所有值已 urlEncode，与服务端校验一致）。
     */
    private fun appSignAndQuery(params: MutableMap<String, String>): String {
        params["appkey"] = BILI_APP_KEY
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        val signQuery = params.entries.sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
        params["sign"] = md5(signQuery + BILI_APP_SEC)
        return params.entries.sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
    }

    /** 获取 RSA 公钥（PiliPlus getWebKey）。 */
    private suspend fun getWebKey(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$PASSPORT_API/x/passport-login/web/key")
                    .header("User-Agent", BILI_HD_UA)
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) return@withContext null
                obj.optJSONObject("data")?.optString("key", "")
            } catch (e: Exception) {
                Timber.e(e, "Bilibili getWebKey failed")
                null
            }
        }
    }

    /** 用 B 站下发的 RSA 公钥加密 16 字节随机数，Base64 输出（PiliPlus dt）。 */
    private fun rsaEncryptBase64(raw: ByteArray, publicKeyPem: String): String? {
        return try {
            val base64Part = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            val keyBytes = Base64.decode(base64Part, Base64.DEFAULT)
            val publicKey: PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.encodeToString(cipher.doFinal(raw), Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Bilibili rsa encrypt failed")
            null
        }
    }

    private val formMediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

    private fun appPostRequest(url: String, bodyQuery: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", BILI_HD_UA)
            .header("buvid", smsBuvid)
            .header("env", "prod")
            .header("app-key", "android_hd")
            .header("x-bili-trace-id", BILI_TRACE_ID)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(bodyQuery.toRequestBody(formMediaType))
            .build()
    }

    /**
     * 发送短信验证码（cid=86 为中国大陆）。
     * 首次调用不需要极验参数；若 B 站要求人机验证（code==0/-105 且 recaptcha_url 非空），
     * 返回 captchaRequired=true 并携带 challenge（gee_gt/gee_challenge/recaptcha_token），
     * 完成极验后携带 gee_validate/gee_seccode/gee_challenge/recaptcha_token 再次调用。
     */
    suspend fun sendSmsCode(
        cid: String,
        tel: String,
        geeChallenge: String? = null,
        geeValidate: String? = null,
        geeSeccode: String? = null,
        recaptchaToken: String? = null
    ): BilibiliSmsSendResult {
        return withContext(Dispatchers.IO) {
            try {
                val millis = System.currentTimeMillis()
                val params = linkedMapOf<String, String>(
                    "build" to "2001100",
                    "buvid" to smsBuvid,
                    "c_locale" to "zh_CN",
                    "channel" to "master",
                    "cid" to cid,
                    "disable_rcmd" to "0",
                    "local_id" to smsBuvid,
                    "login_session_id" to md5(smsBuvid + millis.toString()),
                    "mobi_app" to "android_hd",
                    "platform" to "android",
                    "s_locale" to "zh_CN",
                    "statistics" to BILI_STATISTICS,
                    "tel" to tel
                ).toMutableMap()
                if (!geeChallenge.isNullOrBlank()) params["gee_challenge"] = geeChallenge
                if (!geeSeccode.isNullOrBlank()) params["gee_seccode"] = geeSeccode
                if (!geeValidate.isNullOrBlank()) params["gee_validate"] = geeValidate
                if (!recaptchaToken.isNullOrBlank()) params["recaptcha_token"] = recaptchaToken
                val bodyQuery = appSignAndQuery(params)
                val response = okHttpClient.newCall(appPostRequest("$PASSPORT_API/x/passport-login/sms/send", bodyQuery)).execute()
                val body = response.body?.string() ?: ""
                Timber.d("Bilibili sms send response: ${body.take(400)}")
                val obj = JSONObject(body)
                val code = obj.optInt("code", -1)
                val data = obj.optJSONObject("data")
                val recaptchaUrl = data?.optString("recaptcha_url", "") ?: ""
                when {
                    code == 0 && recaptchaUrl.isBlank() -> {
                        BilibiliSmsSendResult(
                            success = true,
                            message = "验证码已发送",
                            captchaKey = data?.optString("captcha_key", "") ?: ""
                        )
                    }
                    code == 0 || code == -105 -> {
                        // 需要极验人机验证：先从 recaptcha_url 解析 gee 参数，失败则回退 preCapture
                        var challenge = parseCaptchaFromUrl(recaptchaUrl)
                        if (challenge == null) {
                            challenge = preCapture()
                        }
                        if (challenge != null) {
                            BilibiliSmsSendResult(
                                success = false,
                                message = "请完成人机验证",
                                captchaRequired = true,
                                challenge = challenge
                            )
                        } else {
                            BilibiliSmsSendResult(
                                success = false,
                                message = "需要人机验证（极验参数获取失败），请改用扫码登录",
                                captchaRequired = true
                            )
                        }
                    }
                    else -> {
                        val msg = obj.optString("message", "发送失败").ifBlank { "发送失败" }
                        BilibiliSmsSendResult(success = false, message = msg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Bilibili send sms code failed")
                BilibiliSmsSendResult(message = e.message ?: "网络错误")
            }
        }
    }

    /** 从 B 站下发的 recaptcha_url 中解析极验参数（gee_gt/gee_challenge/recaptcha_token）。 */
    private fun parseCaptchaFromUrl(url: String): BilibiliCaptchaChallenge? {
        if (url.isBlank()) return null
        return try {
            val uri = android.net.Uri.parse(url)
            val gt = uri.getQueryParameter("gee_gt")
            val challenge = uri.getQueryParameter("gee_challenge")
            val token = uri.getQueryParameter("recaptcha_token")
            if (gt.isNullOrBlank() || challenge.isNullOrBlank() || token.isNullOrBlank()) {
                null
            } else {
                BilibiliCaptchaChallenge(gt = gt, challenge = challenge, token = token)
            }
        } catch (e: Exception) {
            Timber.w(e, "parseCaptchaFromUrl failed: $url")
            null
        }
    }

    /** 备用极验参数接口（PiliPlus preCapture：/x/safecenter/captcha/pre）。 */
    suspend fun preCapture(): BilibiliCaptchaChallenge? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$PASSPORT_API/x/safecenter/captcha/pre")
                    .header("User-Agent", BILI_HD_UA)
                    .header("buvid", smsBuvid)
                    .header("env", "prod")
                    .header("app-key", "android_hd")
                    .header("x-bili-trace-id", BILI_TRACE_ID)
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) {
                    Timber.w("Bilibili preCapture failed: ${body.take(200)}")
                    return@withContext null
                }
                val data = obj.optJSONObject("data") ?: return@withContext null
                val gt = data.optString("gee_gt", "")
                val challenge = data.optString("gee_challenge", "")
                val token = data.optString("recaptcha_token", "")
                if (gt.isBlank() || challenge.isBlank() || token.isBlank()) null
                else BilibiliCaptchaChallenge(gt = gt, challenge = challenge, token = token)
            } catch (e: Exception) {
                Timber.e(e, "Bilibili preCapture failed")
                null
            }
        }
    }

    /**
     * 获取极验初始化配置（PiliPlus _getConfig：api.geetest.com/gettype.php）。
     * 返回可直接传给前端 Geetest(config) 的 JSON 字符串。
     */
    suspend fun getGeetestConfig(gt: String, challenge: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.geetest.com/gettype.php?gt=$gt")
                    .header("User-Agent", BILI_HD_UA)
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                if (!body.startsWith("(") || !body.endsWith(")")) return@withContext null
                val payload = JSONObject(body.substring(1, body.length - 1))
                if (payload.optString("status") != "success") return@withContext null
                val data = payload.optJSONObject("data") ?: return@withContext null
                val result = JSONObject()
                val keys = data.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    result.put(k, data.get(k))
                }
                result.put("gt", gt)
                result.put("challenge", challenge)
                result.put("offline", false)
                result.put("new_captcha", true)
                result.put("product", "bind")
                result.put("width", "100%")
                result.put("https", true)
                result.put("protocol", "https://")
                result.toString()
            } catch (e: Exception) {
                Timber.e(e, "Bilibili getGeetestConfig failed")
                null
            }
        }
    }

    /** 用短信验证码登录（需 captcha_key，来自 sendSmsCode），成功时从 Set-Cookie 收集会话 cookie。 */
    suspend fun loginWithSms(cid: String, tel: String, code: String, captchaKey: String = ""): BilibiliLoginPollResult {
        return withContext(Dispatchers.IO) {
            try {
                val pubKeyPem = getWebKey()
                val millis = System.currentTimeMillis()
                val rnd = SecureRandom()
                val randomBytes = ByteArray(16).also { rnd.nextBytes(it) }
                val dtRaw = pubKeyPem?.let { rsaEncryptBase64(randomBytes, it) } ?: ""
                // PiliPlus 中 dt/from_url 已先 encode 一次，签名与 body 再 encode 一次（完全一致）
                val params = linkedMapOf<String, String>(
                    "bili_local_id" to smsDeviceId,
                    "build" to "2001100",
                    "buvid" to smsBuvid,
                    "c_locale" to "zh_CN",
                    "captcha_key" to captchaKey,
                    "channel" to "master",
                    "cid" to cid,
                    "code" to code,
                    "device" to "phone",
                    "device_id" to smsDeviceId,
                    "device_name" to "vivo",
                    "device_platform" to "Android14vivo",
                    "disable_rcmd" to "0",
                    "dt" to urlEncode(dtRaw),
                    "from_pv" to "main.my-information.my-login.0.click",
                    "from_url" to urlEncode("bilibili://user_center/mine"),
                    "local_id" to smsBuvid,
                    "mobi_app" to "android_hd",
                    "platform" to "android",
                    "s_locale" to "zh_CN",
                    "statistics" to BILI_STATISTICS,
                    "tel" to tel
                ).toMutableMap()
                val bodyQuery = appSignAndQuery(params)
                val response = okHttpClient.newCall(appPostRequest("$PASSPORT_API/x/passport-login/login/sms", bodyQuery)).execute()
                val body = response.body?.string() ?: ""
                Timber.d("Bilibili sms login response: ${body.take(400)}")
                val obj = JSONObject(body)
                val resultCode = obj.optInt("code", -1)
                if (resultCode != 0) {
                    return@withContext BilibiliLoginPollResult(
                        success = false,
                        code = resultCode,
                        message = obj.optString("message", "验证码错误").ifBlank { "验证码错误" }
                    )
                }
                // app 端登录接口的会话 cookie 在 JSON body 的 data.cookie_info.cookies 数组中（对齐 PiliPlus），
                // 而非 Set-Cookie 响应头；Set-Cookie 仅作兜底
                val cookieMap = mutableMapOf<String, String>()
                val data = obj.optJSONObject("data")
                val cookieInfo = data?.optJSONObject("cookie_info")
                val cookiesArr = cookieInfo?.optJSONArray("cookies")
                if (cookiesArr != null) {
                    for (i in 0 until cookiesArr.length()) {
                        val c = cookiesArr.optJSONObject(i)
                        val name = c?.optString("name", "")
                        val value = c?.optString("value", "")
                        if (name.isNullOrBlank() || value == null) continue
                        cookieMap[name] = value
                    }
                }
                if (cookieMap.isEmpty()) {
                    val setCookies = response.headers("set-cookie")
                    for (header in setCookies) {
                        val pair = header.substringBefore(';').trim()
                        val eq = pair.indexOf('=')
                        if (eq > 0) {
                            cookieMap[pair.substring(0, eq)] = pair.substring(eq + 1)
                        }
                    }
                }
                BilibiliLoginPollResult(
                    success = true,
                    code = 0,
                    message = "ok",
                    cookies = cookieMap
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili sms login failed")
                BilibiliLoginPollResult(code = -1, message = e.message ?: "网络错误")
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 评论交互（需登录，csrf 来自 cookie 的 bili_jct）
    // ─────────────────────────────────────────────────────────

    /**
     * 发布评论或回复评论。
     * @param root 回复的顶层评论 rpid（主评论为 0）
     * @param parent 被直接回复的评论 rpid（主评论为 0）
     */
    suspend fun addReply(
        oid: Long,
        root: Long,
        parent: Long,
        message: String,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("oid", oid.toString())
                    .add("type", "1")
                    .add("message", message)
                    .add("root", root.toString())
                    .add("parent", parent.toString())
                    .add("csrf", csrf)
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/add")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "ok" else "发布失败"),
                    rpid = obj.optJSONObject("data")?.optLong("rpid", 0L) ?: 0L
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili add reply failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /** 点赞 / 取消点赞评论（action=1 点赞，action=0 取消） */
    suspend fun likeReply(
        oid: Long,
        rpid: Long,
        action: Int,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("oid", oid.toString())
                    .add("type", "1")
                    .add("rpid", rpid.toString())
                    .add("action", action.toString())
                    .add("csrf", csrf)
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/action")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "ok" else "操作失败")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili like reply failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /**
     * 举报评论。
     * @param reason 举报理由 code（如 0=其他需填 content，1=违法违禁，2=色情低俗…）
     */
    suspend fun reportReply(
        oid: Long,
        rpid: Long,
        reason: Int,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("add_blacklist", "false")
                    .add("csrf", csrf)
                    .add("gaia_source", "main_h5")
                    .add("oid", oid.toString())
                    .add("platform", "android")
                    .add("reason", reason.toString())
                    .add("rpid", rpid.toString())
                    .add("scene", "main")
                    .add("type", "1")
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/report")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "举报成功" else "举报失败")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili report reply failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /**
     * 踩 / 取消踩评论（对齐 PiliPlus ReplyHttp.hateReply：POST /x/v2/reply/hate）。
     * @param action 1=踩，0=取消踩
     */
    suspend fun hateReply(
        oid: Long,
        rpid: Long,
        action: Int,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("type", "1")
                    .add("oid", oid.toString())
                    .add("rpid", rpid.toString())
                    .add("action", action.toString())
                    .add("csrf", csrf)
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/hate")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "ok" else "操作失败")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili hate reply failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /**
     * 置顶 / 取消置顶评论（对齐 PiliPlus ReplyHttp.replyTop：POST /x/v2/reply/top）。
     * @param isUpTop 当前是否已置顶；true 表示取消置顶（action=0），false 表示置顶（action=1）
     */
    suspend fun toggleTopReply(
        oid: Long,
        rpid: Long,
        isUpTop: Boolean,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("oid", oid.toString())
                    .add("type", "1")
                    .add("rpid", rpid.toString())
                    .add("action", if (isUpTop) "0" else "1")
                    .add("csrf", csrf)
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/top")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "置顶成功" else "操作失败")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili top reply failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /**
     * 评论区互动设置（对齐 PiliPlus ReplyHttp.replySubjectModify：POST /x/v2/reply/subject/modify）。
     * @param action 1=开启评论精选，2=停止评论精选，3=恢复评论，4=关闭评论
     */
    suspend fun setReplySubject(
        oid: Long,
        action: Int,
        csrf: String,
        referer: String,
        cookie: String = ""
    ): BilibiliCommentActionResult {
        return withContext(Dispatchers.IO) {
            try {
                val form = okhttp3.FormBody.Builder()
                    .add("oid", oid.toString())
                    .add("type", "1")
                    .add("action", action.toString())
                    .add("csrf", csrf)
                    .build()
                val builder = Request.Builder()
                    .url("$REPLY_ACTION_API/subject/modify")
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", referer)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                val request = builder.post(form).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val obj = JSONObject(body)
                val success = obj.optInt("code", -1) == 0
                BilibiliCommentActionResult(
                    success = success,
                    message = obj.optString("message", if (success) "设置成功" else "操作失败")
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili set reply subject failed")
                BilibiliCommentActionResult(message = e.message ?: "网络错误")
            }
        }
    }

    /**
     * 评论区互动状态（对齐 PiliPlus ReplyHttp.replyInteraction：
     * GET /x/v2/reply/subject/interaction-status?oid&type&web_location）。
     * 返回评论精选 / 评论开关状态，供 UP 主互动设置面板使用。
     */
    suspend fun getReplyInteraction(
        oid: Long,
        cookie: String = ""
    ): BilibiliReplyInteraction {
        return withContext(Dispatchers.IO) {
            try {
                val sessionCookie = buildSessionCookie(cookie)
                val url = "$REPLY_ACTION_API/subject/interaction-status?oid=$oid&type=1&web_location=333.1369"
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", BASE_UA)
                    .header("Origin", "https://www.bilibili.com")
                    .header("Referer", "https://www.bilibili.com/video/av$oid")
                // ⚡ 显式设置 Cookie（同 fetchCommentPage），保证一致性
                builder.header("Cookie", sessionCookie)
                val response = okHttpClient.newCall(builder.get().build()).execute()
                if (!response.isSuccessful) return@withContext BilibiliReplyInteraction()
                val body = response.body?.string() ?: return@withContext BilibiliReplyInteraction()
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) return@withContext BilibiliReplyInteraction()
                val data = obj.optJSONObject("data") ?: return@withContext BilibiliReplyInteraction()
                val selection = data.optJSONObject("up_reply_selection")
                val reply = data.optJSONObject("up_reply")
                BilibiliReplyInteraction(
                    selectionEnabled = selection?.optInt("status", 0) == 1,
                    selectionCanModify = selection?.optBoolean("can_modify", false) ?: false,
                    replyEnabled = reply?.optInt("status", 0) == 1,
                    replyCanModify = reply?.optBoolean("can_modify", false) ?: false
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili get reply interaction failed")
                BilibiliReplyInteraction()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 收藏同步（需登录）
    // ─────────────────────────────────────────────────────────

    /** 获取用户创建的全部收藏夹（需登录，携带会话 cookie） */
    suspend fun getFavoriteFolders(uid: Long, csrf: String, cookie: String = ""): List<BilibiliFavoriteFolder> {
        return withContext(Dispatchers.IO) {
            try {
                val result = LinkedHashMap<Long, BilibiliFavoriteFolder>()
                // 1) 我创建的收藏夹（created/list-all 一次性返回全部，无分页）
                runCatching {
                    val url = "$FAV_API/folder/created/list-all?up_mid=$uid"
                    val sessionCookie = buildSessionCookie(cookie)
                    val builder = Request.Builder()
                        .url(url)
                        .header("User-Agent", BASE_UA)
                        .header("Referer", "https://space.bilibili.com/$uid/favlist")
                    if (sessionCookie.isNotBlank()) builder.header("Cookie", sessionCookie)
                    val response = okHttpClient.newCall(builder.get().build()).execute()
                    if (!response.isSuccessful) return@runCatching
                    val body = response.body?.string() ?: return@runCatching
                    val obj = JSONObject(body)
                    if (obj.optInt("code", -1) != 0) return@runCatching
                    val data = obj.optJSONObject("data") ?: return@runCatching
                    // ⚡ 实测 created/list-all 响应：data.list 直接是收藏夹数组（非 {created:[...]} 包装）。
                    val listArr = data.optJSONArray("list")
                        ?: data.optJSONObject("list")?.optJSONArray("created")
                        ?: return@runCatching
                    for (i in 0 until listArr.length()) {
                        val item = listArr.optJSONObject(i) ?: continue
                        result[item.optLong("id", 0L)] = BilibiliFavoriteFolder(
                            id = item.optLong("id", 0L),
                            title = item.optString("title", ""),
                            mediaCount = item.optInt("media_count", 0)
                        )
                    }
                }
                // 2) 我收藏的收藏夹（collected/list 分页拉全）
                runCatching {
                    val sessionCookie = buildSessionCookie(cookie)
                    var pn = 1
                    while (true) {
                        val url = "$FAV_API/folder/collected/list?up_mid=$uid&pn=$pn&ps=20&platform=web"
                        val builder = Request.Builder()
                            .url(url)
                            .header("User-Agent", BASE_UA)
                            .header("Referer", "https://space.bilibili.com/$uid/favlist")
                        if (sessionCookie.isNotBlank()) builder.header("Cookie", sessionCookie)
                        val response = okHttpClient.newCall(builder.get().build()).execute()
                        if (!response.isSuccessful) break
                        val body = response.body?.string() ?: break
                        val obj = JSONObject(body)
                        if (obj.optInt("code", -1) != 0) break
                        val data = obj.optJSONObject("data") ?: break
                        // collected/list 返回 data.list（收藏的收藏夹数组）；has_more 可能是 1(int) 或 true
                        val listArr = data.optJSONArray("list") ?: break
                        if (listArr.length() == 0) break
                        for (i in 0 until listArr.length()) {
                            val item = listArr.optJSONObject(i) ?: continue
                            result[item.optLong("id", 0L)] = BilibiliFavoriteFolder(
                                id = item.optLong("id", 0L),
                                title = item.optString("title", ""),
                                mediaCount = item.optInt("media_count", 0)
                            )
                        }
                        // ⚡ JSONObject.optBoolean 对数字 1 返回 false，B 站 has_more 常返回 1 → 必须兼容
                        val hasMore = data.optInt("has_more", 0) == 1 || data.optBoolean("has_more", false)
                        if (!hasMore || listArr.length() < 20) break
                        pn++
                    }
                }
                result.values.toList()
            } catch (e: Exception) {
                Timber.e(e, "Bilibili get favorite folders failed")
                emptyList()
            }
        }
    }

    /** 获取收藏夹内视频（分页，pn 从 1 开始；私有收藏夹需携带会话 cookie） */
    suspend fun getFavoriteResources(mediaId: Long, pn: Int = 1, csrf: String, cookie: String = ""): List<BilibiliFavoriteVideo> {
        return getFavoriteResourcesPage(mediaId, pn, csrf, cookie).videos
    }

    /** 获取收藏夹内视频分页结果（含 hasMore / 总数，供列表滚动加载更多） */
    suspend fun getFavoriteResourcesPage(mediaId: Long, pn: Int = 1, csrf: String, cookie: String = ""): BilibiliFavoritePage {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$FAV_API/resource/list?media_id=$mediaId&pn=$pn&ps=20&platform=web"
                val sessionCookie = buildSessionCookie(cookie)
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", BASE_UA)
                    .header("Referer", "https://space.bilibili.com/favlist?fid=$mediaId")
                if (sessionCookie.isNotBlank()) builder.header("Cookie", sessionCookie)
                val request = builder.get().build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext BilibiliFavoritePage(emptyList(), hasMore = false)
                val body = response.body?.string() ?: return@withContext BilibiliFavoritePage(emptyList(), hasMore = false)
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) return@withContext BilibiliFavoritePage(emptyList(), hasMore = false)
                val data = obj.optJSONObject("data") ?: return@withContext BilibiliFavoritePage(emptyList(), hasMore = false)
                val arr = data.optJSONArray("medias") ?: return@withContext BilibiliFavoritePage(emptyList(), hasMore = false)
                // ⚡ JSONObject.optBoolean 对数字 1 返回 false，B 站 has_more 常返回 1 → 必须兼容
                val hasMore = data.optInt("has_more", 0) == 1 || data.optBoolean("has_more", false)
                val total = data.optJSONObject("info")?.optInt("media_count", 0) ?: 0
                val videos = mutableListOf<BilibiliFavoriteVideo>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val upper = item.optJSONObject("upper")
                    // ⚡ 实测：medias[].id 是新版资源唯一 ID（非 aid），cid 在 ugc.first_cid
                    // （cnt_info 里没有 cid），duration 单位为秒。播放时以 bvid 回源解析 aid/cid。
                    videos.add(
                        BilibiliFavoriteVideo(
                            aid = item.optLong("id", 0L),
                            bvid = item.optString("bvid", "").ifBlank { item.optString("bv_id", "") },
                            title = item.optString("title", ""),
                            cover = item.optString("cover", "").let {
                                if (it.startsWith("//")) "https:$it" else it
                            },
                            upName = upper?.optString("name", "") ?: "",
                            duration = item.optLong("duration", 0L) * 1000L,
                            cid = item.optJSONObject("ugc")?.optLong("first_cid", 0L) ?: 0L
                        )
                    )
                }
                BilibiliFavoritePage(videos = videos, hasMore = hasMore, total = total)
            } catch (e: Exception) {
                Timber.e(e, "Bilibili get favorite resources failed")
                BilibiliFavoritePage(emptyList(), hasMore = false)
            }
        }
    }

    /** 登录后拉取用户信息（/x/web-interface/nav） */
    suspend fun getNavUserInfo(cookie: String): BilibiliUserInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(NAV_API)
                    .header("User-Agent", BASE_UA)
                    .header("Cookie", cookie)
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optInt("code", -1) != 0) return@withContext null
                val data = obj.optJSONObject("data") ?: return@withContext null
                val face = data.optString("face", "").let {
                    if (it.startsWith("//")) "https:$it" else it
                }
                BilibiliUserInfo(
                    uid = data.optLong("mid", 0L),
                    uname = data.optString("uname", ""),
                    face = face
                )
            } catch (e: Exception) {
                Timber.e(e, "Bilibili get nav user info failed")
                null
            }
        }
    }

    suspend fun getPlayUrl(aid: Long, cid: Long, bvid: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 先尝试较高清晰度；若失败或无可用流，降级到 480P（未登录更稳定）。
                fetchPlayUrl(aid, cid, bvid, qn = 80)?.let { return@withContext it }

                Timber.w("Bilibili play URL qn=80 unavailable, falling back to qn=32")
                fetchPlayUrl(aid, cid, bvid, qn = 32)
            } catch (e: Exception) {
                Timber.e(e, "Bilibili play URL exception")
                null
            }
        }
    }

    private suspend fun fetchPlayUrl(
        aid: Long,
        cid: Long,
        bvid: String,
        qn: Int,
        allowRetry: Boolean = true
    ): String? {
        return withContext(Dispatchers.IO) {
            if (!ensureMixinKey()) {
                Timber.e("Failed to get mixin key for play URL")
                return@withContext null
            }

            val body = requestPlayUrl(aid, cid, bvid, qn) ?: return@withContext null
            Timber.d("Bilibili play URL (qn=$qn) response: ${body.take(1000)}")

            val obj = JSONObject(body)
            val code = obj.optInt("code", -1)
            if (code == -403 || code == 100016) {
                Timber.w("Bilibili play URL WBI signature invalid (code=$code), refreshing mixin key")
                sMixinKey = null
                if (allowRetry && ensureMixinKey()) {
                    return@withContext fetchPlayUrl(aid, cid, bvid, qn, allowRetry = false)
                }
                return@withContext null
            }
            if (code != 0) {
                Timber.e("Bilibili play URL API returned error: code=$code, message=${obj.optString("message")}, ttl=${obj.optInt("ttl")}")
                return@withContext null
            }

            val data = obj.optJSONObject("data") ?: return@withContext null
            extractPlayableUrl(data)
        }
    }

    private suspend fun requestPlayUrl(aid: Long, cid: Long, bvid: String, qn: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val params = mutableMapOf(
                    "cid" to cid.toString(),
                    "qn" to qn.toString(),
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

                response.body?.string()
            } catch (e: Exception) {
                Timber.e(e, "Bilibili play URL network exception")
                null
            }
        }
    }

    private fun extractPlayableUrl(data: JSONObject): String? {
        val dash = data.optJSONObject("dash")
        if (dash != null) {
            val audioArr = dash.optJSONArray("audio")
            if (audioArr != null && audioArr.length() > 0) {
                selectBestAudioUrl(audioArr)?.let { return it }
            } else {
                Timber.w("No audio tracks found in DASH")
            }
        } else {
            Timber.w("No DASH data found")
        }

        val durl = data.optJSONArray("durl")
        if (durl != null && durl.length() > 0) {
            Timber.d("Found ${durl.length()} durl items")
            extractUrlFromDurl(durl.optJSONObject(0))?.let { return it }
        } else {
            Timber.w("No durl data found")
        }

        Timber.w("No playable URL found in response")
        return null
    }

    /**
     * 从 DASH 音频列表中选择最稳定的音轨：
     * 优先已知 AAC 流（30251 > 30250 > 30232 > 30216），
     * 没有 AAC 时回退到 id 最大的音轨（可能是 Hi-Res/杜比）。
     */
    private fun selectBestAudioUrl(audioArr: JSONArray): String? {
        val candidates = mutableListOf<Pair<Int, JSONObject>>()
        for (i in 0 until audioArr.length()) {
            val audioObj = audioArr.optJSONObject(i) ?: continue
            val id = audioObj.optInt("id", 0)
            if (id > 0) {
                candidates.add(id to audioObj)
            }
        }
        if (candidates.isEmpty()) return null

        val aacIds = listOf(30251, 30250, 30232, 30216)
        val bestAac = candidates.filter { it.first in aacIds }.maxByOrNull { it.first }
        val chosen = bestAac ?: candidates.maxByOrNull { it.first }
        val audioObj = chosen?.second ?: return null

        Timber.d("Selected Bilibili audio track id=${chosen.first}")
        return extractUrlFromAudioObj(audioObj)
    }

    private fun extractUrlFromAudioObj(audioObj: JSONObject): String? {
        val baseUrl = audioObj.optString("base_url", "")
        var url = baseUrl
        if (url.isBlank()) {
            val backupUrlString = audioObj.optString("backup_url", "")
            url = backupUrlString
        }
        if (url.isBlank()) {
            val backupUrls = audioObj.optJSONArray("backup_url")
            if (backupUrls != null && backupUrls.length() > 0) {
                url = backupUrls.optString(0, "")
            }
        }
        return normalizeBilibiliUrl(url)
    }

    private fun extractUrlFromDurl(durlObj: JSONObject?): String? {
        if (durlObj == null) return null
        var url = durlObj.optString("url", "")
        if (url.isBlank()) {
            val backupUrls = durlObj.optJSONArray("backup_url")
            if (backupUrls != null && backupUrls.length() > 0) {
                url = backupUrls.optString(0, "")
            }
        }
        return normalizeBilibiliUrl(url)
    }

    private fun normalizeBilibiliUrl(url: String): String? {
        if (url.isBlank()) return null
        val normalized = if (url.startsWith("//")) "https:$url" else url
        Timber.d("Found Bilibili playable URL: ${normalized.take(80)}...")
        return normalized
    }
}