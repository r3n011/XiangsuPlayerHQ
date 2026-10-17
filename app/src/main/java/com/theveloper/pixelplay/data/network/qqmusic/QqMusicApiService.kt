package com.theveloper.pixelplay.data.network.qqmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QqMusicApiService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    // P1-3: Inject singleton OkHttpClient instead of creating a new one.
    // Use newBuilder() to share the base connection pool and dispatcher.
    baseOkHttpClient: OkHttpClient
) {

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    @Volatile
    private var persistedCookies: Map<String, String> = emptyMap()

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val list = cookieStore.getOrPut(host) { mutableListOf() }
                list.removeAll { c -> cookies.any { it.name == c.name } }
                list.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .addInterceptor(com.theveloper.pixelplay.data.remote.qqmusic.QQMusicEncryptInterceptor(
            com.theveloper.pixelplay.data.remote.qqmusic.QQSignGenerator(context)
        ))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun hasLogin(): Boolean {
        return !persistedCookies["uin"].isNullOrBlank() ||
            !persistedCookies["qqmusic_key"].isNullOrBlank() ||
            !persistedCookies["qm_keyst"].isNullOrBlank()
    }

    fun setPersistedCookies(cookies: Map<String, String>) {
        persistedCookies = cookies.toMap()
        logCookieKeyDiagnostics("setPersistedCookies")
        seedCookieJar("y.qq.com")
        seedCookieJar("u6.y.qq.com")
        seedCookieJar("u.y.qq.com")
        seedCookieJar("c.y.qq.com")
    }

    fun logout() {
        cookieStore.clear()
        persistedCookies = emptyMap()
    }

    // ─── Content ───────────────────────────────────────────────────────

    private fun getGTK(): Long {
        val skey = persistedCookies["p_skey"] ?: persistedCookies["skey"] ?: ""
        var hash = 5381L
        for (c in skey) {
            hash += (hash shl 5) + c.code.toLong()
        }
        return hash and 0x7fffffffL
    }

    /**
     * Get user's playlists (created and collected).
     *
     * ⚡ 参数必须与社区验证过的可用格式一致（jsososo/QQMusicApi）：
     *    `ct=20` 缺失会导致接口返回非 0 code（如 -100），从而整个"同步全部歌单"
     *    被抛出异常归零为 "Synced 0 playlist, 0 songs"。
     */
    suspend fun getUserPlaylists(start: Int = 0, count: Int = 100): String = withContext(Dispatchers.IO) {
        val uin = extractUin()
        val gtk = getGTK()
        val ein = start + count
        Timber.d("getUserPlaylists: uin=$uin, gtk=$gtk")
        val url = "https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg?" +
                "format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
                "&platform=yqq&needNewCode=1" +
                "&ct=20&uin=$uin&g_tk=$gtk&cid=205360956&userid=$uin&reqtype=3&sin=$start&ein=$ein"

        makeGetRequest(url)
    }

    /**
     * Get user's created playlists.
     */
    suspend fun getUserCreatedPlaylists(start: Int = 0, size: Int = 100): String = withContext(Dispatchers.IO) {
        val uin = extractUin()
        val gtk = getGTK()
        Timber.d("getUserCreatedPlaylists: uin=$uin, gtk=$gtk, start=$start, size=$size")
        val url = "https://c6.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss?" +
                "format=json&inCharset=utf-8&outCharset=utf-8&notice=0" +
                "&platform=yqq.json&needNewCode=1" +
                "&uin=$uin&g_tk=$gtk&g_tk_new_20200303=$gtk&hostuin=$uin&sin=$start&size=$size"

        makeGetRequest(url)
    }

    /**
     * Get user avatar URL based on encrypted uin from euin cookie.
     */
    fun getUserAvatarUrl(): String? {
        val uin = extractUin()
        if (uin == "0" || uin.isBlank()) return null

        // Use QQ avatar URL instead of QQ Music avatar
        // QQ Music avatar requires specific cookies and may not be set for all users
        // QQ avatar is more reliable: https://q1.qlogo.cn/g?b=qq&nk={QQ_NUMBER}&s=140
        val avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=140"

        Timber.d("getUserAvatarUrl: uin=$uin, url=$avatarUrl")
        return avatarUrl
    }

    /**
     * Get user profile information including nickname.
     */
    suspend fun getUserProfile(): String = withContext(Dispatchers.IO) {
        val uin = extractUin()
        val gtk = getGTK()
        Timber.d("getUserProfile: uin=$uin, gtk=$gtk")
        val url = "https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?" +
                "format=json&inCharset=utf-8&outCharset=utf-8" +
                "&cid=205360956&userid=$uin&reqfrom=1" +
                "&g_tk=$gtk"

        makeGetRequest(url)
    }

    /**
     * Get playlist detail including all songs.
     */
    suspend fun getPlaylistDetail(
        playlistId: Long,
        songBegin: Int = 0,
        songNum: Int = 1000
    ): String = withContext(Dispatchers.IO) {
        val gtk = getGTK()
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?" +
                "type=1&json=1&utf8=1&onlysong=0" +
                "&disstid=$playlistId&song_begin=$songBegin&song_num=$songNum" +
                "&g_tk=$gtk&format=json&inCharset=utf-8&outCharset=utf-8"

        makeGetRequest(url)
    }

    // ─── Song Data ─────────────────────────────────────────────────────

    /**
     * 请求歌曲直链（vkey）。
     *
     * @param filenames 可选的具体文件名列表，格式为 `{音质代码}{media_mid}{扩展名}`，
     *   例如 `F000003OUlho2HcRHC.flac` / `M800003OUlho2HcRHC.mp3`。
     *   支持一次传入多个候选档位，服务器会按顺序在 `midurlinfo` 中逐个返回 purl，
     *   这样可以只用一个请求就探测完整条音质候选链（含 VIP 无权限时的空 purl）。
     * @param quality 期望音质代码（如 `F000`/`M800`/`M500`），作为服务端提示。
     */
    suspend fun getSongDownloadUrl(
        songMid: String,
        songtype: Int = 0,
        filenames: List<String>? = null,
        quality: String? = null
    ): String = withContext(Dispatchers.IO) {
        val uin = extractUin()
        val keyst = extractKeyst()
        val param = mutableMapOf<String, Any>(
            "guid" to "327783793guid",
            "songmid" to listOf(songMid),
            "songtype" to listOf(songtype),
            "uin" to uin,
            "loginflag" to 1,
            "platform" to "20",
            "xcdn" to 1
        )
        if (!filenames.isNullOrEmpty()) {
            param["filename"] = filenames
        }
        if (!quality.isNullOrBlank()) {
            param["quality"] = quality
        }
        val payload = JSONObject(
            mapOf(
                "req_0" to mapOf(
                    "module" to "music.vkey.GetEVkey",
                    "method" to "GetUrl",
                    "param" to param
                ),
                "comm" to mapOf(
                    "uin" to uin,
                    "format" to "json",
                    "ct" to 19,
                    "cv" to 1602,
                    "authst" to keyst
                )
            )
        )

        // Keep host aligned with validated reverse-engineering pipeline.
        makePostRequest("https://u6.y.qq.com/cgi-bin/musics.fcg", payload)
    }

    /**
     * ⚡ 取歌曲的 media_mid（`file.media_mid`）——
     *    QQ 的直链文件名必须是 `${档位前缀}${media_mid}${后缀}`
     *    （对齐 lx-music）；用 songmid 拼会大面积失败。
     *    这里用与 lx-music 相同的免签名 POST（music.pf_song_detail_svr）。
     */
    suspend fun getSongMediaMid(songMid: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject(
                mapOf(
                    "comm" to mapOf("ct" to "19", "cv" to "1859", "uin" to "0"),
                    "req" to mapOf(
                        "module" to "music.pf_song_detail_svr",
                        "method" to "get_song_detail_yqq",
                        "param" to mapOf("song_type" to 0, "song_mid" to songMid)
                    )
                )
            )
            val raw = makePostRequest("https://u.y.qq.com/cgi-bin/musicu.fcg", payload)
            JSONObject(raw)
                .optJSONObject("req")
                ?.optJSONObject("data")
                ?.optJSONObject("track_info")
                ?.optJSONObject("file")
                ?.optString("media_mid")
                ?.takeIf { it.isNotBlank() }
        }.onFailure { Timber.w(it, "QqMusicApiService: getSongMediaMid failed songMid=$songMid") }.getOrNull()
    }

    /**
     * ⚡ 免签名 vkey 取链（对齐 lx-music 的 tx 源实现）：
     *    GET `https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=...`，
     *    module `vkey.GetVkeyServer` / method `CgiGetVkey`，headers `channel`/`uid`。
     *    与旧的 `music.vkey.GetEVkey`（需 WebView 签名 + ag-1 加密）相比：
     *    无需签名、未登录也能拿到免费曲目的 purl，是 QQ 在线播放最稳定的链路。
     *
     *    ⚡ 参数对齐参照实现（见 [vkeyUin] 的说明）：
     *      顶层只有 `comm` + `req_0`（**不发 `loginUin`**）；
     *      `param.uin` 与 `comm.uin` 同值（匿名恒为 "0"）；
     *      `guid="10000"`、`ct=24`、`cv=0`、`loginflag=1`、`platform="20"` 与参照一致。
     *
     * @param filenames 形如 `M500{media_mid}.mp3` / `M800{media_mid}.mp3` / `F000{media_mid}.flac`
     */
    suspend fun getVkeyUrlUnsigned(songMid: String, filenames: List<String>): String = withContext(Dispatchers.IO) {
        val uin = vkeyUin()
        val payload = JSONObject(
            mapOf(
                "req_0" to mapOf(
                    "module" to "vkey.GetVkeyServer",
                    "method" to "CgiGetVkey",
                    "param" to mapOf(
                        "filename" to filenames,
                        "guid" to "10000",
                        "songmid" to listOf(songMid),
                        "songtype" to listOf(0),
                        "uin" to uin,
                        "loginflag" to 1,
                        "platform" to "20"
                    )
                ),
                "comm" to mapOf(
                    "uin" to uin,
                    "format" to "json",
                    "ct" to 24,
                    "cv" to 0
                )
            )
        )
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" +
            java.net.URLEncoder.encode(payload.toString(), "UTF-8")
        val builder = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header("channel", "0146951")
            .header("uid", "1234")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
        okHttpClient.newCall(builder.get().build()).execute().use { response ->
            val bodyBytes = response.body.bytes()
            if (!response.isSuccessful) {
                Timber.w("QqMusicApiService unsigned vkey GET failed: HTTP ${response.code}")
            }
            decompressIfNeeded(bodyBytes)
        }
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    private fun extractUin(): String {
        val uinStr = persistedCookies["uin"] 
            ?: persistedCookies["p_uin"] 
            ?: persistedCookies["luin"] 
            ?: persistedCookies["wxuin"] 
            ?: "0"
        return uinStr.replace(Regex("[^0-9]"), "").ifBlank { "0" }
    }

    /**
     * ⚡ 是否持有可用的播放凭据（`qm_keyst` = QQ 音乐 web 登录的 authst）。
     *
     * 免签名 vkey 链路不需要它；但**签名链路 `music.vkey.GetEVkey` 的 `comm.authst`
     * 就取这个值**，为空时服务端必然拒绝。所以匿名态应该直接跳过签名链路，
     * 而不是发一个注定失败的请求、白占一次全局节流。
     */
    fun hasPlaybackCredential(): Boolean = extractKeyst().isNotBlank()

    /**
     * ⚡ vkey 请求里要用的 uin（对齐参照实现的「匿名链路」语义）。
     *
     * 参照实现（lx-music 的 tx 源 `musicUrl`）把 `param.uin` 与 `comm.uin` **固定写死为 "0"**，
     * 登录凭据只通过 Cookie（`qm_keyst`）传递。
     *
     * 以前这里直接透传 cookie 里的 `uin`：只要本地残留过一个 uin（登录过再退出、
     * 网页登录过程留下的 `uin`/`p_uin`/`luin` 都算），就会带上
     * 「真实 uin + `loginflag=1` 但**没有有效 authst**」去请求，服务端按「登录态但凭据无效」
     * 处理 → 大面积回空 purl，表现就是 QQ 歌曲取不到直链、被迫落到兜底音源。
     *
     * 因此：只有确实持有登录凭据时才透传真实 uin（这样已登录用户仍能拿到 VIP/无损档），
     * 否则一律用 "0"。若要与参照实现**完全一致**（永远 "0"），把下面这行改成 `"0"` 即可。
     */
    private fun vkeyUin(): String =
        if (hasPlaybackCredential()) extractUin() else "0"

    private fun extractKeyst(): String {
        return persistedCookies["qm_keyst"] ?: ""
    }

    /**
     * Make a GET request and handle potential Zlib-compressed responses.
     * QQ Music FCG endpoints often return application/octet-stream with
     * a 5-byte length prefix followed by Zlib-compressed JSON.
     */
    private fun makeGetRequest(url: String): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        buildPersistedCookieHeader()?.let { requestBuilder.header("Cookie", it) }

        return okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyBytes = response.body.bytes()
            if (!response.isSuccessful) {
                Timber.w("QqMusicApiService GET request failed: HTTP ${response.code}")
            }
            decompressIfNeeded(bodyBytes)
        }
    }

    private fun makePostRequest(url: String, payload: JSONObject): String {
        logCookieKeyDiagnostics("makePostRequest")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

        buildPersistedCookieHeader()?.let { requestBuilder.header("Cookie", it) }

        val request = requestBuilder
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            val bodyBytes = response.body.bytes()
            if (!response.isSuccessful) {
                Timber.w("QqMusicApiService POST request failed: HTTP ${response.code}")
            }
            decompressIfNeeded(bodyBytes)
        }
    }

    /**
     * Decompress Zlib-compressed response if needed.
     * QQ Music FCG endpoints return data with a 5-byte prefix + Zlib payload.
     */
    private fun decompressIfNeeded(data: ByteArray): String {
        if (data.isEmpty()) return ""

        // Try direct UTF-8 first (in case it's already plain JSON)
        val directStr = String(data, StandardCharsets.UTF_8).trim()
        if (directStr.startsWith("{") || directStr.startsWith("[")) {
            return directStr
        }

        // QQ 部分 FCG 接口（如 fcg_get_profile_order_asset.fcg）在 format=json 时
        // 仍可能返回 JSONP 包装：MusicJsonCallback({...}) / jsonCallback({...}) / callback({...})。
        // 剥掉包装后再解析，否则 JSONObject 解析抛异常会把整个同步归零。
        val jsonpMatch = Regex("^\\s*\\w+\\((.*)\\)\\s*$", RegexOption.DOT_MATCHES_ALL).find(directStr)
        if (jsonpMatch != null) {
            val inner = jsonpMatch.groupValues[1].trim()
            if (inner.startsWith("{") || inner.startsWith("[")) {
                Timber.d("QqMusicApiService: Stripped JSONP wrapper")
                return inner
            }
        }

        // QQ Music uses a 5-byte prefix (usually \x00\x00\x00\x00\x00) followed by Zlib data
        // The Zlib data starts with 0x78 (e.g., 0x78 0x01, 0x78 0x9C, 0x78 0xDA)
        try {
            // Find the Zlib header (0x78)
            var offset = 0
            for (i in 0 until minOf(data.size, 10)) {
                if (data[i] == 0x78.toByte() && i + 1 < data.size) {
                    offset = i
                    break
                }
            }
            val zlibData = if (offset > 0) data.copyOfRange(offset, data.size) else data
            val inflater = InflaterInputStream(ByteArrayInputStream(zlibData))
            val output = ByteArrayOutputStream()
            inflater.copyTo(output)
            val result = output.toString(StandardCharsets.UTF_8.name())
            Timber.d("QqMusicApiService: Decompressed ${data.size} bytes -> ${result.length} chars")
            return result
        } catch (e: Exception) {
            Timber.w(e, "QqMusicApiService: Zlib decompression failed, returning raw string")
            return directStr
        }
    }

    private fun buildPersistedCookieHeader(): String? {
        if (persistedCookies.isEmpty()) return null
        return persistedCookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun seedCookieJar(host: String) {
        val list = cookieStore.getOrPut(host) { mutableListOf() }
        persistedCookies.forEach { (name, value) ->
            val c = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path("/")
                .build()
            list.removeAll { it.name == name }
            list.add(c)
        }
    }

    private fun logCookieKeyDiagnostics(stage: String) {
        if (persistedCookies.isEmpty()) {
            Timber.d("QqMusicApiService[$stage]: persisted cookies are empty")
            return
        }

        val keys = persistedCookies.keys.sorted()
        val required = listOf("uin", "qm_keyst", "euin", "psrf_qqaccess_token")
        val missing = required.filter { persistedCookies[it].isNullOrBlank() }
        Timber.d("QqMusicApiService[$stage]: cookie keys=%s", keys.joinToString(","))
        Timber.d("QqMusicApiService[$stage]: missing required cookie keys=%s", missing.joinToString(","))
    }
}
