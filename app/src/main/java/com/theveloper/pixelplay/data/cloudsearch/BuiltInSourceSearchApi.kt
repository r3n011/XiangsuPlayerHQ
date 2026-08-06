package com.theveloper.pixelplay.data.cloudsearch

import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.qq.QQSearchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置音源搜索 API（对齐落雪原版内置 SDK 方案，不依赖导入的 JS）：
 * - kg 酷狗：songsearch.kugou.com 官方搜索
 * - tx QQ音乐：u.y.qq.com + zzcSign 签名（移植落雪 tx/utils/crypto.js）
 * - mg 咪咕：jadeite.migu.cn + MD5 签名（移植落雪 mg/musicSearch.js）
 *
 * 播放 URL：优先源官方接口（酷狗 hash），失败/无版权时用溯音酷我（"歌名 歌手"）兜底。
 */
@Singleton
class BuiltInSourceSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val qqSearchApi: QQSearchApi
) {
    private companion object {
        private const val TAG = "BuiltInSearch"

        // ─── 酷我 ───
        private const val KW_SEARCH_URL = "http://search.kuwo.cn/r.s"

        // ─── 酷狗 ───
        private const val KG_SEARCH_URL = "https://songsearch.kugou.com/song_search_v2"
        private const val KG_PLAY_URL = "http://m.kugou.com/app/i/getSongInfo.php"

        // ─── QQ音乐 ───
        private const val TX_SEARCH_URL = "https://u.y.qq.com/cgi-bin/musics.fcg"
        private val TX_PART_1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
        private val TX_PART_2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
        private val TX_SCRAMBLE_VALUES = intArrayOf(
            89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
            186, 123, 120, 64, 242, 133, 143, 161, 121, 179
        )

        // ─── 咪咕 ───
        private const val MG_SEARCH_URL =
            "https://jadeite.migu.cn/music_search/v3/search/searchAll"
        private const val MG_DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20"
        private const val MG_SIGNATURE_MD5 = "6cdc72a439cef99a3418d2a78aa28c73"
        private const val MG_APP_SECRET = "yyapp2d16148780a1dcc7408e06336b98cfd50"
        private const val MG_RESOURCE_INFO_URL =
            "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=2"
        // 落雪 mg/utils/mrc.js keyArr（TEA 解密密钥）
        private val MG_TEA_KEY = longArrayOf(
            27303562373562475L, 18014862372307051L, 22799692160172081L,
            34058940340699235L, 30962724186095721L, 27303523720101991L,
            27303523720101998L, 31244139033526382L, 28992395054481524L
        )
        private const val MG_TEA_DELTA = 2654435769L

        private const val SUPPORTED_SOURCES = "tx,kg,mg,kw"
    }

    /** 内置源是否支持 */
    fun isSupported(source: String): Boolean = source in SUPPORTED_SOURCES.split(",")

    /**
     * 内置源歌词获取（对齐落雪 musicSdk 的 lyric 实现）：
     * - tx：c.y.qq.com 官方歌词接口（base64，带翻译）
     * - kg：lyrics.kugou.com 两步（search → download，lrc 直接 base64 解码）
     * - kw：m.kuwo.cn 歌词 JSON 接口（lrclist 直接拼 LRC）
     * - 其他（mg 等）：返回失败，由上层走 LRCLIB 兜底
     * @return 原始 LRC 文本（可能含翻译）
     */
    suspend fun getLyric(source: String, song: LxSongInfo): Result<String> = withContext(Dispatchers.IO) {
        try {
            val raw = when (source) {
                "tx" -> getTxLyric(song)
                "kg" -> getKgLyric(song)
                "kw" -> getKwLyric(song)
                "mg" -> getMgLyric(song)
                else -> null
            }
            if (raw.isNullOrBlank()) {
                Result.failure(Exception("No lyric for $source"))
            } else {
                Result.success(raw)
            }
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: getLyric failed source=$source song=${song.name}")
            Result.failure(t)
        }
    }

    // ─── QQ音乐歌词（c.y.qq.com，落雪同款）──────────────────────────────

    private suspend fun getTxLyric(song: LxSongInfo): String? {
        val mid = song.songmid.ifBlank { song.id }
        if (mid.isBlank()) return null
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$mid&g_tk=5381&loginUin=0&hostUin=0&format=json" +
            "&inCharset=utf8&outCharset=utf-8&platform=yqq"
        val body = httpGetWithReferer(url, "https://y.qq.com/portal/player.html") ?: return null
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return null
        val lyric = root.optString("lyric", "")
        if (lyric.isBlank()) return null
        val lrc = String(android.util.Base64.decode(lyric, android.util.Base64.DEFAULT), Charsets.UTF_8)
        val trans = root.optString("trans", "")
        val tlyric = if (trans.isNotBlank()) {
            String(android.util.Base64.decode(trans, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } else ""
        return if (tlyric.isNotBlank()) "$lrc\n$tlyric" else lrc
    }

    // ─── 酷狗歌词（lyrics.kugou.com 两步，落雪同款）──────────────────────

    private suspend fun getKgLyric(song: LxSongInfo): String? {
        val name = song.name
        val hash = song.hash.ifBlank { song.id }
        if (name.isBlank() || hash.isBlank()) return null
        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
            "&keyword=${URLEncoder.encode(name, "UTF-8")}&hash=$hash&timelength=${song.duration}&lrctxt=1"
        val searchBody = httpGetKg(searchUrl) ?: return null
        val candidates = JSONObject(searchBody).optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val first = candidates.optJSONObject(0) ?: return null
        val id = first.optString("id")
        val accessKey = first.optString("accesskey")
        if (id.isBlank() || accessKey.isBlank()) return null
        val dlUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val dlBody = httpGetKg(dlUrl) ?: return null
        val dl = JSONObject(dlBody)
        val content = dl.optString("content", "")
        if (content.isBlank()) return null
        val lrc = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
        // krc 加密内容解码后无时间轴标记，视为失败走兜底
        return if (Regex("\\[\\d{1,2}:\\d{1,2}").containsMatchIn(lrc)) lrc else null
    }

    // ─── 酷我歌词（m.kuwo.cn JSON 接口）────────────────────────────────

    private suspend fun getKwLyric(song: LxSongInfo): String? {
        val id = song.songmid.ifBlank { song.id }
        if (id.isBlank()) return null
        val url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$id"
        val body = httpGet(url) ?: return null
        val data = JSONObject(body).optJSONObject("data") ?: return null
        val lrcList = data.optJSONArray("lrclist") ?: return null
        val sb = StringBuilder()
        for (i in 0 until lrcList.length()) {
            val item = lrcList.optJSONObject(i) ?: continue
            val time = item.optString("time", "")
            val text = item.optString("lineLyric", "")
            if (time.isNotBlank()) {
                // 酷我 lrclist.time 是秒（可带小数）格式，如 "0.0"、"65.43"，
                // 转成标准 LRC 时间戳 [mm:ss.xx] 才能被 LyricsUtils 正确解析。
                val lrcTimestamp = kwSecondsToLrcTimestamp(time) ?: continue
                sb.append("[$lrcTimestamp]").append(text).append('\n')
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    /** 酷我秒格式（"0.0"、"65.43"）→ 标准 LRC 时间戳 "mm:ss.xx"；解析失败返回 null */
    private fun kwSecondsToLrcTimestamp(time: String): String? {
        val parts = time.split(".")
        val totalSeconds = parts.firstOrNull()?.toIntOrNull() ?: return null
        val frac = parts.getOrNull(1)?.take(2)?.padEnd(2, '0') ?: "00"
        val mm = totalSeconds / 60
        val ss = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d.%s", mm, ss, frac)
    }

    // ─── 咪咕歌词（musicinfo 接口 + mrc TEA 解密，落雪同款）──────────────

    private suspend fun getMgLyric(song: LxSongInfo): String? {
        // 优先 copyrightId（存入 hash 字段，落雪同款），其次 songId
        val id = song.hash.ifBlank { song.id.ifBlank { song.songmid } }
        if (id.isBlank()) return null
        val form = okhttp3.FormBody.Builder().add("resourceId", id).build()
        val request = Request.Builder()
            .url(MG_RESOURCE_INFO_URL)
            .post(form)
            .addHeader("Referer", "https://app.c.nf.migu.cn/")
            .addHeader("channel", "0146921")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Mobile Safari/537.36"
            )
            .build()
        val body = runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return null
        val root = JSONObject(body)
        if (root.optString("code") != "000000") return null
        val resource = root.optJSONArray("resource") ?: return null
        if (resource.length() == 0) return null
        val first = resource.optJSONObject(0) ?: return null
        val mrcUrl = first.optString("mrcUrl")
        val lrcUrl = first.optString("lrcUrl")
        val trcUrl = first.optString("trcUrl")

        var lrc: String? = null
        if (mrcUrl.isNotBlank()) {
            val mrcText = httpGetMg(mrcUrl)
            if (!mrcText.isNullOrBlank()) lrc = mgDecryptMrc(mrcText).takeIf { it.isNotBlank() }
        }
        if (lrc.isNullOrBlank() && lrcUrl.isNotBlank()) {
            lrc = httpGetMg(lrcUrl)
        }
        if (lrc.isNullOrBlank()) return null
        if (trcUrl.isNotBlank()) {
            val trc = httpGetMg(trcUrl)
            if (!trc.isNullOrBlank() && trc != lrc) lrc = "$lrc\n$trc"
        }
        return lrc
    }

    /** 咪咕 MRC 解密（移植落雪 mg/utils/mrc.js） */
    private fun mgDecryptMrc(data: String): String {
        if (data.length < 32) return data
        val n = data.length / 16
        val blocks = LongArray(n) { i ->
            java.lang.Long.parseUnsignedLong(data.substring(i * 16, i * 16 + 16), 16)
        }
        mgTeaDecrypt(blocks, MG_TEA_KEY)
        val sb = StringBuilder()
        for (b in blocks) sb.append(String(mgLongToBytes(b), Charsets.UTF_16LE))
        return sb.toString()
    }

    /** TEA 解密循环（落雪 mrc.js teaDecrypt） */
    private fun mgTeaDecrypt(data: LongArray, key: LongArray) {
        val length = data.size
        if (length < 1) return
        var j2 = data[0]
        var j3 = 6L + (52 / length).toLong() * MG_TEA_DELTA
        while (j3 != 0L) {
            val j4 = j3
            val j5 = 3L and (j4 shr 2)
            var j6 = length.toLong()
            while (true) {
                j6--
                if (j6 > 0L) {
                    val j7 = data[(j6 - 1).toInt()]
                    val i = j6.toInt()
                    val a = (j2 xor j4) + (j7 xor key[((3L and j6) xor j5).toInt()])
                    val b = ((j7 shr 5) xor (j2 shl 2)) + ((j2 shr 3) xor (j7 shl 4))
                    j2 = data[i] - (a xor b)
                    data[i] = j2
                } else break
            }
            val j8 = data[length - 1]
            val a = (key[((j6 and 3L) xor j5).toInt()] xor j8) + (j2 xor j4)
            val b = ((j8 shr 5) xor (j2 shl 2)) + ((j2 shr 3) xor (j8 shl 4))
            j2 = data[0] - (a xor b)
            data[0] = j2
            j3 = j4 - MG_TEA_DELTA
        }
    }

    /** long → 8 字节小端（落雪 mrc.js longToBytes） */
    private fun mgLongToBytes(l: Long): ByteArray {
        val result = ByteArray(8)
        var v = l
        for (i in 0 until 8) {
            result[i] = (v and 0xFFL).toByte()
            v = v shr 8
        }
        return result
    }

    /**
     * 内置源搜索（对齐落雪 musicSearch：page 从 1 开始）。
     */
    suspend fun search(source: String, keyword: String, page: Int, pageSize: Int): LxSearchResult {
        if (keyword.isBlank()) return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        return withContext(Dispatchers.IO) {
            try {
                when (source) {
                    "kg" -> searchKg(keyword, page, pageSize)
                    "tx" -> searchTx(keyword, page, pageSize)
                    "mg" -> searchMg(keyword, page, pageSize)
                    "kw" -> searchKw(keyword, page, pageSize)
                    else -> LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: search failed source=$source")
                LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
        }
    }

    /**
     * 播放 URL 解析：
     * - kg：酷狗官方 hash → URL（按音质传 br），空（付费/失效）则溯音酷我兜底
     * - tx/mg：溯音酷我（"歌名 歌手"）兜底
     * @param quality 落雪音质值："24bit" / "flac" / "320k" / "128k"
     */
    suspend fun resolvePlayUrl(source: String, song: LxSongInfo, quality: String = "320k"): String? = withContext(Dispatchers.IO) {
        try {
            if (source == "kg" && song.hash.isNotBlank()) {
                val official = getKgPlayUrl(song.hash, qualityToKgBr(quality))
                if (!official.isNullOrBlank()) {
                    Timber.d("$TAG: kg official URL ok for '${song.name}' (quality=$quality)")
                    return@withContext official
                }
            }
            // 溯音酷我兜底："歌名 歌手" 精确搜索拿 URL（按音质传 br）
            val fallback = qqSearchApi.getPlayUrl(
                QQSearchApi.QQSong(
                    id = 0L, rid = "", title = song.name, album = "",
                    singer = song.singer, cover = "", durationSec = 0
                ),
                br = qualityToKwBr(quality)
            ).getOrNull()
            Timber.d("$TAG: fallback URL source=$source song='${song.name}' quality=$quality: ${fallback != null}")
            fallback
        } catch (t: Throwable) {
            Timber.e(t, "$TAG: resolvePlayUrl failed source=$source")
            null
        }
    }

    /** 音质 → 溯音酷我 br：1(FLAC) / 5(320k) / 7(128k) */
    private fun qualityToKwBr(quality: String): Int = when (quality) {
        "flac", "24bit", "lossless", "hires" -> 1
        "128k", "128" -> 7
        else -> 5
    }

    /** 音质 → 酷狗官方 br：999000(无损) / 320000 / 128000 */
    private fun qualityToKgBr(quality: String): Int = when (quality) {
        "flac", "24bit", "lossless", "hires" -> 999000
        "128k", "128" -> 128000
        else -> 320000
    }

    // ─── 酷我（官方 search.kuwo.cn，落雪同款接口）──────────────────────

    private suspend fun searchKw(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$KW_SEARCH_URL?client=kt&all=$encoded&pn=${page - 1}&rn=$pageSize" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
            "&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json" +
            "&vermerge=1&mobi=1&issubtitle=1"
        val body = httpGet(url) ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val root = JSONObject(body)
        if (root.optString("SHOW") == "0") return LxSearchResult(isEnd = true)
        val abslist = root.optJSONArray("abslist") ?: return LxSearchResult(isEnd = true)
        val list = mutableListOf<LxSongInfo>()
        for (i in 0 until abslist.length()) {
            val item = abslist.optJSONObject(i) ?: continue
            val rid = item.optString("MUSICRID", "").removePrefix("MUSIC_")
            if (rid.isBlank()) continue
            val name = item.optString("SONGNAME")
            if (name.isBlank()) continue
            // hts_MVPIC 是完整 URL；MVPIC / web_albumpic_short 是相对路径，需补前缀
            val pic = item.optString("hts_MVPIC", "").ifBlank {
                item.optString("MVPIC", "").takeIf { it.isNotBlank() }
                    ?.let { "https://img1.kuwo.cn/wmvpic/$it" }
                    ?: item.optString("web_albumpic_short", "").takeIf { it.isNotBlank() }
                        ?.let { "https://img1.kuwo.cn/star/albumcover/$it" }
                    ?: ""
            }
            list.add(
                LxSongInfo(
                    id = rid,
                    songmid = rid,
                    name = name,
                    singer = item.optString("ARTIST", ""),
                    albumName = item.optString("ALBUM", ""),
                    duration = item.optLong("DURATION", 0L),
                    pic = pic,
                    source = "kw"
                )
            )
        }
        val total = root.optInt("TOTAL", list.size)
        return LxSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    // ─── 酷狗 ───────────────────────────────────────────────────────────

    private suspend fun searchKg(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$KG_SEARCH_URL?keyword=$encoded&page=$page&pagesize=$pageSize&userid=0" +
            "&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        val body = httpGet(url) ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: return LxSearchResult(isEnd = true)
        val lists = data.optJSONArray("lists") ?: return LxSearchResult(isEnd = true)
        val list = mutableListOf<LxSongInfo>()
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val name = item.optString("SongName")
            if (name.isBlank()) continue
            val hash = item.optString("FileHash")
            if (hash.isBlank()) continue
            val image = item.optString("Image", "")
            list.add(
                LxSongInfo(
                    id = hash,
                    hash = hash,
                    name = name,
                    singer = item.optString("SingerName", ""),
                    albumName = item.optString("AlbumName", ""),
                    duration = item.optLong("Duration", 0L),
                    pic = image.replace("{size}", "480"),
                    source = "kg"
                )
            )
        }
        val total = data.optInt("total", list.size)
        return LxSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    private suspend fun getKgPlayUrl(hash: String, br: Int = 320000): String? {
        val url = "$KG_PLAY_URL?cmd=playInfo&hash=$hash&br=$br"
        val body = httpGet(url) ?: return null
        val root = JSONObject(body)
        val playUrl = root.optString("url", "")
        if (playUrl.isNotBlank() && !playUrl.startsWith("http")) return null
        return playUrl.takeIf { it.isNotBlank() }
    }

    // ─── QQ音乐（zzcSign）────────────────────────────────────────────────

    /**
     * QQ 搜索（对齐落雪 lx-music-mobile 的 musicSearch：失败时最多重试 5 次，共 6 次尝试）。
     * u.y.qq.com 会间歇性返回错误码/空结果，参考项目依赖多次重试保证可用性。
     */
    private suspend fun searchTx(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        var last: LxSearchResult? = null
        repeat(6) { attempt ->
            last = runCatching { searchTxOnce(keyword, page, pageSize) }.getOrNull()
            if (last != null && last!!.list.isNotEmpty()) return last!!
            // 失败后指数退避（200/400/800/1600/3200ms），给 QQ 风控留出喘息，避免高频重试反被限流
            if (attempt < 5) delay(200L * (1 shl attempt))
        }
        return last ?: LxSearchResult(list = emptyList(), isEnd = true, total = 0)
    }

    private suspend fun searchTxOnce(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        val data = buildTxSearchJson(keyword, page, pageSize)
        val sign = zzcSign(data)
        val request = Request.Builder()
            .url("$TX_SEARCH_URL?sign=$sign")
            .addHeader("User-Agent", "QQMusic 14090508(android 12)")
            .post(data.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        val body = runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()?.let { txDecodeBody(it) }
            ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)

        val root = JSONObject(body)
        // 对齐参考：顶层 code 非 0（如限流 50000005）也视为失败，交给上层重试
        if (root.optInt("code", -1) != 0) return LxSearchResult(isEnd = true)
        val req = root.optJSONObject("req") ?: return LxSearchResult(isEnd = true)
        if (req.optInt("code", -1) != 0) return LxSearchResult(isEnd = true)
        val reqData = req.optJSONObject("data") ?: return LxSearchResult(isEnd = true)
        val body2 = reqData.optJSONObject("body") ?: return LxSearchResult(isEnd = true)
        val itemSong = body2.optJSONArray("item_song") ?: return LxSearchResult(isEnd = true)

        val list = mutableListOf<LxSongInfo>()
        for (i in 0 until itemSong.length()) {
            val item = itemSong.optJSONObject(i) ?: continue
            val file = item.optJSONObject("file") ?: continue
            val mediaMid = file.optString("media_mid")
            if (mediaMid.isBlank()) continue
            val mid = item.optString("mid")
            val name = item.optString("title")
            if (name.isBlank()) continue

            val singerNames = mutableListOf<String>()
            val singers = item.optJSONArray("singer")
            if (singers != null) {
                for (j in 0 until singers.length()) {
                    singers.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { singerNames.add(it) }
                }
            }
            val album = item.optJSONObject("album")
            val albumMid = album?.optString("mid", "") ?: ""
            val albumName = album?.optString("name", "") ?: ""
            val pic = if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"

            list.add(
                LxSongInfo(
                    id = mid,
                    songmid = mid,
                    name = name,
                    singer = singerNames.joinToString(" / "),
                    albumName = albumName,
                    duration = item.optLong("interval", 0L),
                    pic = pic,
                    source = "tx"
                )
            )
        }
        return LxSearchResult(
            isEnd = list.isEmpty(),
            list = list,
            total = list.size
        )
    }

    /** 构造 QQ 搜索请求 JSON（字段顺序必须与落雪一致，sign 依赖序列化字符串） */
    private fun buildTxSearchJson(keyword: String, page: Int, pageSize: Int): String {
        val comm = "\"comm\":{" +
            "\"ct\":\"11\",\"cv\":\"14090508\",\"v\":\"14090508\",\"tmeAppID\":\"qqmusic\"," +
            "\"phonetype\":\"EBG-AN10\",\"deviceScore\":\"553.47\",\"devicelevel\":\"50\"," +
            "\"newdevicelevel\":\"20\",\"rom\":\"HuaWei/EMOTION/EmotionUI_14.2.0\",\"os_ver\":\"12\"," +
            "\"OpenUDID\":\"0\",\"OpenUDID2\":\"0\",\"QIMEI36\":\"0\",\"udid\":\"0\",\"chid\":\"0\"," +
            "\"aid\":\"0\",\"oaid\":\"0\",\"taid\":\"0\",\"tid\":\"0\",\"wid\":\"0\",\"uid\":\"0\"," +
            "\"sid\":\"0\",\"modeSwitch\":\"6\",\"teenMode\":\"0\",\"ui_mode\":\"2\"," +
            "\"nettype\":\"1020\",\"v4ip\":\"\"}"
        val req = "\"req\":{\"module\":\"music.search.SearchCgiService\"," +
            "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{" +
            "\"search_type\":0,\"searchid\":\"${(1..16).map { (0..9).random() }.joinToString("")}\"," +
            "\"query\":\"${escapeJson(keyword)}\",\"page_num\":$page,\"num_per_page\":$pageSize," +
            "\"highlight\":0,\"nqc_flag\":0,\"multi_zhida\":0,\"cat\":2,\"grp\":1,\"sin\":0,\"sem\":0}}"
        return "{$comm,$req}"
    }

    private fun escapeJson(s: String): String = buildString {
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    /**
     * QQ musics.fcg 响应偶尔以 zlib 压缩形式返回（5 字节头 + 0x78 开头）。
     * 先尝试直接按 UTF-8 解析 JSON，失败再找 zlib 头解压。
     */
    private fun txDecodeBody(data: ByteArray): String? {
        val direct = String(data, Charsets.UTF_8).trim()
        if (direct.startsWith("{") || direct.startsWith("[")) return direct
        return try {
            var offset = 0
            for (i in 0 until minOf(data.size, 10)) {
                if (data[i] == 0x78.toByte() && i + 1 < data.size) {
                    offset = i
                    break
                }
            }
            val inflater = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(data.copyOfRange(offset, data.size)))
            val output = java.io.ByteArrayOutputStream()
            inflater.copyTo(output)
            output.toString(Charsets.UTF_8.name())
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: tx 响应解压失败")
            null
        }
    }

    /** 移植落雪 zzcSign（tx/utils/crypto.js） */
    private fun zzcSign(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }
        // JS 数组越界返回 undefined，join 时视为空字符
        val part1 = TX_PART_1_INDEXES.filter { it < hash.length }.joinToString("") { hash[it].toString() }
        val part2 = TX_PART_2_INDEXES.filter { it < hash.length }.joinToString("") { hash[it].toString() }
        val part3 = ByteArray(TX_SCRAMBLE_VALUES.size)
        TX_SCRAMBLE_VALUES.forEachIndexed { i, v ->
            val hex = hash.substring(i * 2, minOf(i * 2 + 2, hash.length))
            val intVal = if (hex.isEmpty()) 0 else hex.toInt(16)
            part3[i] = (v xor intVal).toByte()
        }
        val b64Part = android.util.Base64.encodeToString(part3, android.util.Base64.NO_WRAP)
            .replace("+", "").replace("/", "").replace("=", "")
        return ("zzc$part1$b64Part$part2").lowercase()
    }

    // ─── 咪咕 ───────────────────────────────────────────────────────────

    private suspend fun searchMg(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        val time = System.currentTimeMillis().toString()
        val sign = md5("$keyword$MG_SIGNATURE_MD5$MG_APP_SECRET$MG_DEVICE_ID$time")
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$MG_SEARCH_URL?isCorrect=0&isCopyright=1" +
            "&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D" +
            "&pageSize=$pageSize&text=$encoded&pageNo=$page&sort=0&sid=USS"
        val request = Request.Builder()
            .url(url)
            .addHeader("uiVersion", "A_music_3.6.1")
            .addHeader("deviceId", MG_DEVICE_ID)
            .addHeader("timestamp", time)
            .addHeader("sign", sign)
            .addHeader("channel", "0146921")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
            )
            .build()
        val body = runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)

        val root = JSONObject(body)
        if (root.optString("code") != "000000") return LxSearchResult(isEnd = true)
        val songResult = root.optJSONObject("songResultData") ?: return LxSearchResult(isEnd = true)
        val resultList = songResult.optJSONArray("resultList") ?: return LxSearchResult(isEnd = true)

        val list = mutableListOf<LxSongInfo>()
        for (g in 0 until resultList.length()) {
            val group = resultList.optJSONArray(g) ?: continue
            for (i in 0 until group.length()) {
                val data = group.optJSONObject(i) ?: continue
                val songId = data.optString("songId")
                if (songId.isBlank() || songId == "0") continue
                val name = data.optString("name")
                if (name.isBlank()) continue

                val singerNames = mutableListOf<String>()
                val singers = data.optJSONArray("singerList")
                if (singers != null) {
                    for (j in 0 until singers.length()) {
                        singers.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { singerNames.add(it) }
                    }
                }
                var img = data.optString("img3").ifBlank { data.optString("img2").ifBlank { data.optString("img1") } }
                if (img.isNotBlank() && !img.startsWith("http")) img = "http://d.musicapp.migu.cn$img"

                list.add(
                    LxSongInfo(
                        id = songId,
                        songmid = songId,
                        // 咪咕歌词（musicinfo.do）需要 copyrightId，落雪同款
                        hash = data.optString("copyrightId"),
                        name = name,
                        singer = singerNames.joinToString(" / "),
                        albumName = data.optString("album", ""),
                        duration = data.optLong("duration", 0L) / 1000L,
                        pic = img,
                        source = "mg"
                    )
                )
            }
        }
        val total = songResult.optInt("totalCount", list.size)
        return LxSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    // ─── 工具 ───────────────────────────────────────────────────────────

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    /** GET + 自定义 Referer（QQ歌词接口需要） */
    private suspend fun httpGetWithReferer(url: String, referer: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
        }

    /** GET + 酷狗歌词所需请求头（KG-RC / KG-THash，落雪同款） */
    private suspend fun httpGetKg(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("KG-RC", "1")
                .addHeader("KG-THash", "expand_search_manager.cpp:852736169:451")
                .addHeader("User-Agent", "KuGou2012-9020-ExpandSearchManager")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    /** GET + 咪咕歌词所需请求头（落雪 mg/lyric.js 同款） */
    private suspend fun httpGetMg(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("Referer", "https://app.c.nf.migu.cn/")
                .addHeader("channel", "0146921")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Mobile Safari/537.36"
                )
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }
    }
}
