package com.theveloper.pixelplay.data.kugou

import android.net.Uri
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** 听书（酷狗长音频）专辑。 */
data class KugouAudiobookAlbum(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val author: String?,
    val chapterCount: Int = 0,
    val intro: String? = null,
)

/** 听书章节；[canPlay] 为 false（付费/仅试听）的章节不进列表、不参与播放。 */
data class KugouAudiobookChapter(
    val hash: String,
    val name: String,
    val author: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val canPlay: Boolean,
    /** 酷狗长音频的 album_audio_id：`/v5/url` 直链解析必传（直链路径里的 mx 参数）。 */
    val albumAudioId: String? = null,
)

/** 免费书库一页。 */
data class KugouAudiobookPage(
    val albums: List<KugouAudiobookAlbum>,
    val hasMore: Boolean,
)

/** 听书分类标签（tag_id + 名称）。 */
data class KugouAudiobookTag(val id: Int, val name: String)

/**
 * 听书播放：章节 → `kgaudio://{urlEncodedJson}` 占位 URI。
 *
 * 播放时由 DualPlayerEngine **直接**调酷狗官方 `/v5/url`（trackercdn）解析直链
 * （与参照项目 md3Music 的 `song_url.js` 同一条链路），**不经过落雪 JS 音源链**：
 * 长音频在 JS 音源里没有对应实现，走 `cloud://lx` 只会白等引擎就绪、再落到同一个官方接口。
 */
fun KugouAudiobookChapter.toKugouAudioUri(albumId: String): String {
    val json = JSONObject().apply {
        put("hash", hash)
        put("albumId", albumId)
        if (!albumAudioId.isNullOrBlank()) put("albumAudioId", albumAudioId)
        put("name", name)
    }
    val encoded = java.net.URLEncoder.encode(json.toString(), "UTF-8").replace("+", "%20")
    return "kgaudio://$encoded"
}

/**
 * 酷狗听书（长音频）接口：书架推荐分区 / 免费书库 / 分类标签 / 专辑详情 / 章节列表 / 搜索。
 *
 * 移植自 md3Music 的 Rust 实现（`longaudio.rs` / `search_mixed.rs`），签名规则与 [KugouAccountApi] 同款：
 * `signature = md5(salt + 参数按键排序拼接 + body + salt)`。
 * 两套盐：
 * - 概念版（默认）：appid=3116 / clientver=11440 / salt=`LnT6...`（推荐分区用）；
 * - 标准版（[standard]）：appid=1005 / clientver=20789 / salt=`OIlwieks28dk2k092lksi2UIkp`
 *   —— 免费书库 / 分类 / 章节列表必须标准盐，否则上游把章节的 `fail_process` 归 0，
 *   付费章节会被误判成免费（播放时才报错）。
 *
 * 全部接口匿名可用；已登录时自动带 token/userid（会员章节能拿到更高音质）。
 */
@Singleton
class KugouAudiobookApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val kugouRepository: KugouRepository,
) {

    // ─── 书架：四个推荐分区 ────────────────────────────────────────────────

    /** 每日推荐（`/longaudio/v1/home_new/daily_recommend`）。 */
    suspend fun fetchDailyRecommend(): Result<List<KugouAudiobookAlbum>> = io {
        val root = signedRequest(
            method = "POST",
            path = "/longaudio/v1/home_new/daily_recommend",
            query = mapOf("module_id" to 1, "size" to 30, "page" to 1),
        )
        val data = root.opt("data") ?: root
        when (data) {
            is JSONObject -> parseAlbumArrayDeep(data)
            is JSONArray -> parseAlbumArray(data)
            else -> emptyList()
        }
    }

    /** 排行榜推荐（`/longaudio/v1/home_new/rank_card_recommend`，data[].albums[]）。 */
    suspend fun fetchRankRecommend(): Result<List<KugouAudiobookAlbum>> = io {
        val root = signedRequest(
            method = "GET",
            path = "/longaudio/v1/home_new/rank_card_recommend",
            query = mapOf("platform" to "ios"),
        )
        when (val data = root.opt("data")) {
            is JSONArray -> buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val sub = item.optJSONArray("albums") ?: continue
                    addAll(parseAlbumArray(sub))
                }
            }
            is JSONObject -> parseAlbumArrayDeep(data)
            else -> emptyList()
        }
    }

    /** 每周推荐（`/longaudio/v1/home_new/week_new_albums_recommend`）。 */
    suspend fun fetchWeekRecommend(): Result<List<KugouAudiobookAlbum>> = io {
        val root = signedRequest(
            method = "POST",
            path = "/longaudio/v1/home_new/week_new_albums_recommend",
            query = mapOf("clientver" to 12329),
            body = JSONObject().put("album_playlist", JSONArray()),
        )
        parseAlbumArrayDeep(root.optJSONObject("data") ?: root)
    }

    /** VIP 推荐（`/longaudio/v1/home_new/vip_select_recommend`）。 */
    suspend fun fetchVipRecommend(): Result<List<KugouAudiobookAlbum>> = io {
        val root = signedRequest(
            method = "POST",
            path = "/longaudio/v1/home_new/vip_select_recommend",
            query = mapOf("position" to "2", "clientver" to 12329),
            body = JSONObject().put("album_playlist", JSONArray()),
        )
        parseAlbumArrayDeep(root.optJSONObject("data") ?: root)
    }

    // ─── 免费书库 / 分类 ──────────────────────────────────────────────────

    /**
     * 免费听书库专辑列表（`/longaudio/v1/album/list`，标准盐）：
     * [tagId] 分类（906=有声小说…）、[sort] 0默认/1播放量/2更新时间、
     * [gender] 0不限/1男频/2女频、[status] 0全部/1连载/2完结。
     */
    suspend fun fetchFreeAlbums(
        tagId: Int = 906,
        sort: Int = 0,
        gender: Int = 0,
        status: Int = 0,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<KugouAudiobookPage> = io {
        val root = signedRequest(
            method = "GET",
            path = "/longaudio/v1/album/list",
            standard = true,
            query = mapOf(
                "appid" to 1005,
                "clientver" to 20789,
                "api_ver" to 2,
                "gender" to gender,
                "sort" to sort,
                "tag_id" to tagId,
                "free" to 1,
                "status" to status,
                "page" to page,
                "page_size" to pageSize,
            ),
        )
        val data = root.optJSONObject("data")
        val albums = parseAlbumArray(data?.optJSONArray("data_list") ?: JSONArray())
        // is_end = 1 表示到底（酷狗惯例）
        KugouAudiobookPage(albums = albums, hasMore = data?.optInt("is_end", 0) != 1)
    }

    /** 听书分类标签树（`/v3/list_audiobook_tags`，data[0].son[] 即 24 个子分类）。 */
    suspend fun fetchTags(): Result<List<KugouAudiobookTag>> = io {
        val root = signedRequest(
            method = "GET",
            path = "/v3/list_audiobook_tags",
            standard = true,
            query = mapOf("appid" to 1005, "clientver" to 20789, "platform" to "android"),
            xRouter = "longaudio.kugou.com",
        )
        val son = root.optJSONArray("data")
            ?.optJSONObject(0)
            ?.optJSONArray("son")
            ?: return@io emptyList()
        buildList {
            for (i in 0 until son.length()) {
                val item = son.optJSONObject(i) ?: continue
                val id = item.optInt("tag_id", -1)
                val name = item.optString("tag_name")
                if (id > 0 && name.isNotBlank()) add(KugouAudiobookTag(id, name))
            }
        }
    }

    // ─── 专辑详情 / 章节 ─────────────────────────────────────────────────

    /** 专辑详情（`/openapi/v2/broadcast`）：主要用来补全简介。 */
    suspend fun fetchAlbumDetail(albumId: String): Result<KugouAudiobookAlbum?> = io {
        val body = JSONObject()
            .put("data", JSONArray().put(JSONObject().put("album_id", albumId)))
            .put("show_album_tag", 1)
            .put(
                "fields",
                "album_name,album_id,category,authors,sizable_cover,intro,author_name," +
                    "trans_param,album_tag,mix_intro,full_intro,is_publish",
            )
        val root = signedRequest(
            method = "POST",
            path = "/openapi/v2/broadcast",
            body = body,
            extraHeaders = mapOf("KG-TID" to "78"),
        )
        val data = root.optJSONArray("data") ?: return@io null
        (0 until data.length())
            .mapNotNull { data.optJSONObject(it) }
            .firstOrNull()
            ?.let(::parseAlbum)
            ?.takeIf { it.name.isNotBlank() || it.intro != null }
    }

    /**
     * 专辑章节列表（`/longaudio/v2/album_audios`，标准盐；一次 30~50 条，翻页拉全）。
     * 付费章节（fail_process 非 0）保留在结果里由上层过滤（[KugouAudiobookChapter.canPlay]）。
     */
    suspend fun fetchAlbumChapters(
        albumId: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<List<KugouAudiobookChapter>> = io {
        val body = JSONObject()
            .put("album_id", albumId)
            .put("area_code", 1)
            .put("tagid", 0)
            .put("page", page)
            .put("pagesize", pageSize)
        val root = signedRequest(
            method = "POST",
            path = "/longaudio/v2/album_audios",
            standard = true,
            body = body,
            xRouter = "openapi.kugou.com",
            extraHeaders = mapOf("KG-TID" to "78"),
        )
        val list = when (val data = root.opt("data")) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("audios")
                ?: data.optJSONArray("list")
                ?: data.optJSONArray("audio_list")
            else -> null
        } ?: return@io emptyList()
        buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                parseChapter(item)?.let(::add)
            }
        }
    }

    // ─── 播放直链（/v5/url，trackercdn）──────────────────────────────────

    /**
     * 听书章节直链：酷狗官方 `GET /v5/url`（x-router: trackercdn.kugou.com，概念版签名 +
     * `key = md5(hash + SIGN_KEY_STR + appid + mid + userid)`）。
     *
     * 移植自参照项目 `song_url.js`（`/song/url` 上游）：返回 `url` / `backupUrl` 数组里
     * 第一条可播 http 直链。长音频基本只有 128k，上游对更高音质请求会静默给可用档位；
     * 拿不到 URL 时再退回 128 重试一次。
     */
    suspend fun fetchChapterPlayUrl(
        hash: String,
        albumId: String?,
        albumAudioId: String?,
        quality: String = "320k",
    ): Result<String?> = io {
        if (hash.isBlank()) return@io null
        val mid = ANONYMOUS_DEVICE
        val userId = kugouRepository.userId?.takeIf { it.isNotBlank() && it != "0" } ?: "0"
        val key = md5Hex("$hash$SIGN_KEY_STR$APP_ID$mid$userId")
        for (q in listOf(kgQualityValue(quality), "128").distinct()) {
            val root = signedRequest(
                method = "GET",
                path = "/v5/url",
                xRouter = "trackercdn.kugou.com",
                query = mapOf(
                    "album_id" to (albumId?.takeIf { it.isNotBlank() } ?: "0"),
                    "area_code" to 1,
                    "hash" to hash,
                    "ssa_flag" to "is_fromtrack",
                    "version" to 11430,
                    "page_id" to 967177915,
                    "quality" to q,
                    "album_audio_id" to (albumAudioId?.takeIf { it.isNotBlank() } ?: "0"),
                    "behavior" to "play",
                    "pid" to 411,
                    "cmd" to 26,
                    "pidversion" to 3001,
                    "IsFreePart" to 0,
                    "ppage_id" to "356753938,823673182,967485191",
                    "cdnBackup" to 1,
                    "module" to "",
                    // 覆盖默认的 11440：/v5/url 用 11430（参照项目同款）
                    "clientver" to 11430,
                    "key" to key,
                ),
            )
            extractDirectUrl(root)?.let { return@io it }
        }
        null
    }

    /** 从 `/v5/url` 响应里取第一条可播直链（url 数组优先，其次 backupUrl）。 */
    private fun extractDirectUrl(root: JSONObject): String? {
        fun fromValue(value: Any?): String? = when (value) {
            is String -> value.takeIf { it.startsWith("http") }
            is JSONArray -> (0 until value.length())
                .mapNotNull { i -> value.optString(i).takeIf { it.startsWith("http") } }
                .firstOrNull()
            else -> null
        }
        return fromValue(root.opt("url"))
            ?: fromValue(root.opt("backupUrl"))
            ?: fromValue(root.opt("backup_url"))
    }

    /** App 音质设置值 → `/v5/url` 的 quality 档位字符串。 */
    private fun kgQualityValue(quality: String): String = when (quality.lowercase()) {
        "flac", "24bit", "flac24bit", "lossless", "hires", "master", "atmos" -> "flac"
        "320k", "320" -> "320"
        else -> "128"
    }

    // ─── 搜索 ────────────────────────────────────────────────────────────

    /** 听书搜索（`/complexsearch/v4/search/song`，标准盐；结果按专辑去重）。 */
    suspend fun searchAlbums(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<List<KugouAudiobookAlbum>> = io {
        val root = signedRequest(
            method = "GET",
            path = "/complexsearch/v4/search/song",
            standard = true,
            query = mapOf(
                "appid" to 1005,
                "clientver" to 20789,
                "area_code" to 1,
                "albumhide" to 1,
                "com_user_type" to 0,
                "privilegefilter" to 0,
                "dopicfull" to 1,
                "filter" to 12,
                "platform" to "AndroidFilter",
                "tag" to "em",
                "recver" to 2,
                "iscorrection" to 1,
                "search_ability" to 223,
                "sec_aggre" to 1,
                "sec_aggre_bitmap" to 0,
                "mode_ability" to 1,
                "nocollect" to 1,
                "user_type" to 0,
                "keyword" to keyword,
                "page" to page,
                "pagesize" to pageSize,
            ),
        )
        val lists = root.optJSONObject("data")?.optJSONArray("lists")
            ?: return@io emptyList()
        val seen = HashSet<String>()
        buildList {
            for (i in 0 until lists.length()) {
                val item = lists.optJSONObject(i) ?: continue
                val albumId = firstNonBlank(
                    item.optString("AlbumID"),
                    item.optString("album_id"),
                ) ?: continue
                if (!seen.add(albumId)) continue
                val name = stripHtml(
                    firstNonBlank(item.optString("AlbumName"), item.optString("album_name")).orEmpty()
                )
                if (name.isBlank()) continue
                val cover = firstNonBlank(
                    item.optJSONObject("trans_param")?.optString("union_cover"),
                    item.optString("Image"),
                    item.optString("img"),
                )
                add(
                    KugouAudiobookAlbum(
                        id = albumId,
                        name = name,
                        coverUrl = normalizeCover(cover),
                        author = stripHtml(
                            firstNonBlank(item.optString("SingerName"), item.optString("singer_name"))
                                .orEmpty()
                        ).takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    // ─── 签名请求 ─────────────────────────────────────────────────────────

    /**
     * 带签名的酷狗请求：默认参数（dfid/mid/uuid/appid/clientver/clienttime + 登录态）+
     * [query] 一起参与签名，[body] 原文参与签名；签名写回 query（酷狗接口签名参数在 URL 上）。
     */
    private fun signedRequest(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: JSONObject? = null,
        standard: Boolean = false,
        xRouter: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val token = kugouRepository.authToken
        val userId = kugouRepository.userId
        val clientTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString()
        val appId = if (standard) STD_APP_ID else APP_ID
        val clientVer = if (standard) STD_CLIENT_VER else CLIENT_VER
        val salt = if (standard) STD_ROUTE else ROUTE

        val params = LinkedHashMap<String, String>()
        // ⚡ 设备标识：优先用本机真实设备信息（dfid/mid），拿不到才回落占位 "-"。
        //    参照项目同样是「设备信息优先，其次参数/cookie，最后 '-'」——它靠
        //    /register/dev 拿服务端下发的 dfid 并落盘复用；我们这边 dfid 来自
        //    酷狗登录会话（未登录时为空）。之前无条件写 "-" 会让部分长音频
        //    （如「安全警长啦咘啦哆」这类）在 /v5/url 拿不到直链：参考项目能播、
        //    我们放不出来就是这个差异。
        val device = runCatching { kugouRepository.device }.getOrNull()
        val deviceDfid = device?.dfid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        val deviceMid = device?.mid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        val deviceGuid = device?.guid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        params["dfid"] = deviceDfid ?: ANONYMOUS_DEVICE
        params["mid"] = deviceMid ?: ANONYMOUS_DEVICE
        params["uuid"] = deviceGuid ?: "-"
        params["appid"] = appId
        params["clientver"] = clientVer
        params["clienttime"] = clientTime
        if (!token.isNullOrBlank()) params["token"] = token
        if (!userId.isNullOrBlank() && userId != "0") params["userid"] = userId
        query.forEach { (k, v) -> params[k] = v?.toString().orEmpty() }

        val bodyText = body?.toString().orEmpty()
        val joined = params.entries
            .sortedBy { it.key }
            .joinToString(separator = "") { (k, v) -> "$k=$v" }
        val signature = md5Hex(salt + joined + bodyText + salt)

        val urlBuilder = Uri.parse("$GATEWAY$path").buildUpon()
        params.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }
        urlBuilder.appendQueryParameter("signature", signature)

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", DEFAULT_UA)
            .header("dfid", params["dfid"].orEmpty())
            .header("clienttime", clientTime)
            .header("mid", params["mid"].orEmpty())
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .header(
                "Cookie",
                buildString {
                    append("mid=").append(params["mid"].orEmpty())
                    if (!token.isNullOrBlank()) append("; token=").append(token)
                    if (!userId.isNullOrBlank()) append("; userid=").append(userId)
                },
            )
        if (xRouter != null) requestBuilder.header("x-router", xRouter)
        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
        if (method == "POST") {
            requestBuilder.post(bodyText.toRequestBody(JSON_MEDIA))
        } else {
            requestBuilder.get()
        }
        return okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            JSONObject(text)
        }
    }

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ─── 解析 ────────────────────────────────────────────────────────────

    /** 专辑列表字段多级兜底（推荐分区接口 data.list / data.info / data.albums 任取其一）。 */
    private fun parseAlbumArrayDeep(obj: JSONObject): List<KugouAudiobookAlbum> =
        parseAlbumArray(
            obj.optJSONArray("list")
                ?: obj.optJSONArray("info")
                ?: obj.optJSONArray("albums")
                ?: JSONArray()
        )

    private fun parseAlbumArray(array: JSONArray): List<KugouAudiobookAlbum> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val album = parseAlbum(item)
            if (album.id.isNotBlank() && album.name.isNotBlank()) add(album)
        }
    }

    private fun parseAlbum(item: JSONObject): KugouAudiobookAlbum = KugouAudiobookAlbum(
        id = firstNonBlank(item.optString("id"), item.optString("album_id"), item.optString("albumid"))
            .orEmpty(),
        name = firstNonBlank(
            item.optString("name"),
            item.optString("album_name"),
            item.optString("albumname"),
            item.optString("title"),
        ).orEmpty(),
        coverUrl = normalizeCover(
            firstNonBlank(
                item.optString("sizable_cover"),
                item.optString("img"),
                item.optString("imgurl"),
                item.optString("cover"),
                item.optJSONObject("trans_param")?.optString("union_cover"),
            )
        ),
        author = firstNonBlank(
            item.optString("author"),
            item.optString("author_name"),
            item.optString("singer"),
        ),
        chapterCount = firstNonBlank(
            item.optString("audio_count"),
            item.optString("audiocount"),
            item.optString("audio_total"),
            item.optString("songcount"),
        )?.toIntOrNull() ?: 0,
        intro = firstNonBlank(
            item.optString("intro"),
            item.optString("mix_intro"),
            item.optString("full_intro"),
        ),
    )

    /**
     * 酷狗歌词（听书章节 / 酷狗在线歌都用它）。
     *
     * 与参照项目 `getLyricResult` 同链路：
     * 1. `GET /search/lyric?hash=<小写>&man=yes` → `data.candidates[]`，取第一个候选的 `id` / `accesskey`；
     * 2. `GET /lyric?id=&fmt=lrc&decode=true[&accesskey=]` → `data.content`（decode=true 时服务端已解码，直接是 LRC）。
     *
     * 听书章节之前完全没接歌词：章节模型里没有 lyric 字段，播放走 kgaudio:// 后落到通用链路
     * （网易云 / AMLLDB / LRCLIB）当然搜不到有声书 —— 这就是「听书歌词请求没做」。
     */
    suspend fun fetchLyric(hash: String): Result<String?> = io {
        val normalizedHash = hash.trim().lowercase()
        if (normalizedHash.isBlank()) return@io null

        val search = signedRequest(
            method = "GET",
            path = "/search/lyric",
            query = mapOf("hash" to normalizedHash, "man" to "yes"),
            standard = true,
        )
        val candidates = (search.opt("data") as? JSONObject)?.optJSONArray("candidates")
            ?: search.optJSONArray("candidates")
        val first = (0 until (candidates?.length() ?: 0))
            .mapNotNull { candidates?.optJSONObject(it) }
            .firstOrNull { it.optString("id").isNotBlank() }
            ?: return@io null
        val lyricId = first.optString("id")
        val accessKey = first.optString("accesskey").takeIf { it.isNotBlank() }

        val query = LinkedHashMap<String, Any?>()
        query["id"] = lyricId
        query["fmt"] = "lrc"
        query["decode"] = "true"
        if (accessKey != null) query["accesskey"] = accessKey
        val lyric = signedRequest(
            method = "GET",
            path = "/lyric",
            query = query,
            standard = true,
        )
        val content = (lyric.opt("data") as? JSONObject)?.optString("content").orEmpty()
            .ifBlank { lyric.optString("content") }
        content.takeIf { it.isNotBlank() }
    }

    private fun parseChapter(item: JSONObject): KugouAudiobookChapter? {
        val hash = firstNonBlank(
            item.optString("hash"),
            item.optString("play_hash"),
            item.optString("album_audio_id"),
            item.optString("audio_id"),
            item.optString("mixsongid"),
        ) ?: return null
        // 直链解析（/v5/url）要用的 album_audio_id，与 hash 分开存
        val albumAudioId = firstNonBlank(
            item.optString("album_audio_id"),
            item.optString("mixsongid"),
        )?.takeIf { it != "0" }
        val name = firstNonBlank(
            item.optString("audio_name"),
            item.optString("filename"),
            item.optString("song_name"),
            item.optString("title"),
        ) ?: return null
        val rawDuration = firstNonBlank(
            item.optString("timelength"),
            item.optString("timelength_128"),
            item.optString("timelength_320"),
            item.optString("timelength_high"),
            item.optString("duration"),
        )?.toLongOrNull() ?: 0L
        // fail_process 缺失视为可播；非 0（酷狗惯例 4/32）= 需购买/仅试听
        val fail128 = item.optIntOrNull("fail_process_128")
        val fail = item.optIntOrNull("fail_process")
        val canPlay = (fail128 == null || fail128 == 0) && (fail == null || fail == 0)
        return KugouAudiobookChapter(
            hash = hash,
            name = name,
            author = firstNonBlank(item.optString("author_name"), item.optString("singer_name")),
            durationMs = normalizeDurationSeconds(rawDuration) * 1000L,
            coverUrl = normalizeCover(
                firstNonBlank(
                    item.optJSONObject("trans_param")?.optString("union_cover"),
                    item.optString("img"),
                    item.optString("imgurl"),
                    item.optString("cover"),
                )
            ),
            canPlay = canPlay,
            albumAudioId = albumAudioId,
        )
    }

    /** 时长归一化：> 10000 视为毫秒转秒（与参考项目 `_normalizeDuration` 一致）。 */
    private fun normalizeDurationSeconds(raw: Long): Long = when {
        raw <= 0L -> 0L
        raw > 10_000L -> raw / 1000L
        else -> raw
    }

    /** 封面 URL：替换 `{size}` 占位并升级 https（酷狗老链接是 http）。 */
    private fun normalizeCover(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != "null" }
            ?.replace("`", "")
            ?.replace("{size}", "480")
            ?.replace("http://", "https://")

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]*>"), "").trim()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block)
            .onFailure { Timber.w(it, "KugouAudiobookApi: request failed") }
    }

    private companion object {
        const val GATEWAY = "https://gateway.kugou.com"
        /** 概念版（与账号接口一致） */
        const val APP_ID = "3116"
        const val CLIENT_VER = "11440"
        const val ROUTE = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        /** 标准版：免费书库 / 分类 / 章节 / 搜索必须用它（appid=1005 + 标准盐） */
        const val STD_APP_ID = "1005"
        const val STD_CLIENT_VER = "20789"
        const val STD_ROUTE = "OIlwieks28dk2k092lksi2UIkp"

        /** `/v5/url` 的 `key` 参数盐：md5(hash + 该值 + appid + mid + userid)。 */
        const val SIGN_KEY_STR = "185672dd44712f60bb1736df5a377e82"

        /**
         * 匿名设备标识：本机 dfid/mid 未在酷狗注册，长音频章节 / v5/url 会拒绝
         * 未注册设备（error_code 20028）；参照项目在设备未注册时同样用 "-"。
         */
        const val ANONYMOUS_DEVICE = "-"
        const val DEFAULT_UA = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
        val JSON_MEDIA = "application/json;charset=utf-8".toMediaType()
    }
}
