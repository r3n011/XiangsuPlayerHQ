package com.theveloper.pixelplay.data.cloudsearch

import com.theveloper.pixelplay.data.lx.LxPlaylistInfo
import com.theveloper.pixelplay.data.lx.LxPlaylistSearchResult
import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.lx.LxSongInfo
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
 * 播放 URL：优先源官方接口（酷狗 hash / 酷我 mobi.s 官方直链），
 * tx/mg 无匿名官方直链时用酷我官方直链兜底（不再依赖已失效的第三方聚合 oiapi）。
 */
@Singleton
class BuiltInSourceSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "BuiltInSearch"

        // ─── 酷我 ───
        private const val KW_SEARCH_URL = "http://search.kuwo.cn/r.s"

        // ─── 酷狗 ───
        private const val KG_SEARCH_URL = "https://songsearch.kugou.com/song_search_v2"
        private const val KG_PLAYLIST_SEARCH_URL = "http://msearchcdn.kugou.com/api/v3/search/special"
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

        // ─── 歌单详情（对齐落雪 musicSdk.*.songList.getListDetail）───
        private const val KG_PLAYLIST_PAGE = "http://www2.kugou.kugou.com/yueku/v9/special/single/"
        private const val KG_GATEWAY_AUDIO_URL = "http://gateway.kugou.com/v2/album_audio/audio"
        private val KG_LIST_DATA_REGEX = Regex("global\\.data = (\\[.+\\]);")
        private const val TX_PLAYLIST_DETAIL_URL =
            "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
        private const val TX_PLAYLIST_SEARCH_URL =
            "http://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist"
        private const val MG_PLAYLIST_SONGS_URL =
            "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0"
        /** 咪咕歌单详情接口单页上限 */
        private const val MG_MAX_PAGE_SIZE = 100
        private const val KW_PLAYLIST_DETAIL_URL = "http://nplserver.kuwo.cn/pl.svc"
        /** 酷我官方直链接口（落雪 kw 音源同源，br 取 320kmp3/128kmp3/2000flac） */
        private const val KW_MOBI_URL = "http://mobi.kuwo.cn/mobi.s"
        private const val KW_APP_SOURCE = "kwplayer_ar_5.1.0.0_B_jiakong_vh.apk"
        private const val MG_IOS_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"

        /** QQ音乐 soso 接口返回的文本带 HTML 实体（如 `&#32;` / `&amp;`） */
        private val HTML_ENTITY_REGEX = Regex("&#(x?)([0-9a-fA-F]+);|&(amp|lt|gt|quot|apos|nbsp);")
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
     * - kg：酷狗官方 hash → URL（按音质传 br；付费/无版权时 url 为空，继续走酷我）
     * - kw：酷我官方直链（mobi.s convert_url_with_sign，用歌曲自身的 rid）
     * - tx/mg：无匿名官方直链，用 "歌名 歌手" 在酷我搜到最匹配的一首再取酷我官方直链兜底
     *
     * 说明：旧实现把 tx/mg/kw 全部交给第三方聚合 `oiapi.net/api/Kuwo`，它返回的是
     * 带签名的酷我限时链（`bitrate$320`），服务端已判定失效 → HTTP 410 Gone → ExoPlayer 2004。
     * 现改为直接用酷我官方接口，并在返回前做一次 Range 校验，避免把失效链接交给播放器。
     *
     * @param quality 落雪音质值："24bit" / "flac" / "320k" / "128k"
     */
    suspend fun resolvePlayUrl(source: String, song: LxSongInfo, quality: String = "320k"): String? = withContext(Dispatchers.IO) {
        try {
            if (source == "kg" && song.hash.isNotBlank()) {
                val official = getKgPlayUrl(song.hash, qualityToKgBr(quality))
                if (!official.isNullOrBlank() && isPlayable(official)) {
                    Timber.d("$TAG: kg official URL ok for '${song.name}' (quality=$quality)")
                    return@withContext official
                }
            }
            // 酷我官方直链兜底：kw 直接用 rid；tx/mg 先按 "歌名 歌手" 搜一首
            val rid = song.id.takeIf { source == "kw" && it.matches(Regex("\\d+")) }
                ?: findKwRid(song.name, song.singer)
            if (rid.isNullOrBlank()) {
                Timber.d("$TAG: no kw rid for source=$source song='${song.name}'")
                return@withContext null
            }
            for (br in qualityToKwBrChain(quality)) {
                val u = getKwPlayUrl(rid, br)
                if (!u.isNullOrBlank() && isPlayable(u)) {
                    Timber.d("$TAG: kw direct URL ok source=$source song='${song.name}' br=$br")
                    return@withContext u
                }
            }
            Timber.d("$TAG: kw direct URL failed source=$source song='${song.name}' quality=$quality")
            null
        } catch (t: Throwable) {
            Timber.e(t, "$TAG: resolvePlayUrl failed source=$source")
            null
        }
    }

    /**
     * 音质 → 酷我 mobi.s 的 br 候选链（从高到低，逐个校验，保证最终一定能播）。
     * 注意：酷我无损（2000flac）对部分歌曲需会员，取不到时自动降到 320k/128k mp3。
     */
    private fun qualityToKwBrChain(quality: String): List<String> {
        val target = when (quality.lowercase()) {
            "flac", "24bit", "flac24bit", "lossless", "hires", "master", "atmos" -> "2000flac"
            "128k", "128" -> "128kmp3"
            else -> "320kmp3"
        }
        return listOf(target, "320kmp3", "128kmp3").distinct()
    }

    /** 酷我官方直链：mobi.s convert_url_with_sign（返回 data.url，带签名） */
    private suspend fun getKwPlayUrl(rid: String, br: String): String? {
        val url = "$KW_MOBI_URL?f=web&source=$KW_APP_SOURCE&type=convert_url_with_sign&br=$br&rid=$rid"
        val body = httpGet(url) ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) return null
        return root.optJSONObject("data")?.optString("url", "")?.takeIf { it.startsWith("http") }
    }

    /** 用 "歌名 歌手" 在酷我搜索，返回最匹配一首的 rid（tx/mg 等无匿名直链音源的兜底） */
    private suspend fun findKwRid(name: String, singer: String): String? {
        val primary = "$name $singer".trim()
        val r = runCatching { searchKw(primary, 1, 5) }.getOrNull()
        r?.list?.firstOrNull()?.id?.takeIf { it.isNotBlank() }?.let { return it }
        if (primary != name.trim()) {
            return runCatching { searchKw(name.trim(), 1, 5) }.getOrNull()
                ?.list?.firstOrNull()?.id?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /** 校验直链真的可播（Range: bytes=0-1，接受 2xx/206），避免把 410 等失效链接交给 ExoPlayer */
    private suspend fun isPlayable(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
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

    /** tx 搜索单次结果：ok=true 表示接口合法响应（code==0），即使没结果也无需重试 */
    private class TxSearchOnceResult(
        val ok: Boolean,
        val list: List<LxSongInfo>,
        val total: Int
    )

    /**
     * QQ 搜索（对齐落雪 lx-music-mobile 的 musicSearch：失败时最多重试 5 次，共 6 次尝试）。
     * 仅当接口返回错误码（如 u.y.qq.com 间歇限流 50000005）或请求异常时才重试；
     * code==0 但确实没有结果（合法空响应）直接返回，不浪费重试（落雪同款行为）。
     */
    private suspend fun searchTx(keyword: String, page: Int, pageSize: Int): LxSearchResult {
        var lastEmpty = LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        repeat(6) { attempt ->
            val r = runCatching { searchTxOnce(keyword, page, pageSize) }.getOrNull()
            if (r != null && r.list.isNotEmpty()) {
                return LxSearchResult(
                    // 对齐落雪：total 来自 meta.estimate_sum，据此判断是否还有下一页
                    isEnd = if (r.total > 0) page * pageSize >= r.total else r.list.size < pageSize,
                    list = r.list,
                    total = r.total
                )
            }
            if (r != null && r.ok) {
                // 合法响应但没有结果：直接返回，不再重试
                return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
            // 请求异常或 code!=0（限流）：指数退避后重试（200/400/800/1600/3200ms），
            // 给 QQ 风控留出喘息，避免高频重试反被限流
            if (attempt < 5) delay(200L * (1 shl attempt))
        }
        return lastEmpty
    }

    private suspend fun searchTxOnce(keyword: String, page: Int, pageSize: Int): TxSearchOnceResult {
        val data = buildTxSearchJson(keyword, page, pageSize)
        val sign = zzcSign(data)
        val request = Request.Builder()
            .url("$TX_SEARCH_URL?sign=$sign")
            // 对齐落雪 httpFetch：携带 Accept: application/json
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "QQMusic 14090508(android 12)")
            .post(data.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        val body = runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()?.let { txDecodeBody(it) }
            ?: return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)

        val root = JSONObject(body)
        // 对齐参考：顶层 code 非 0（如限流 50000005）视为失败，交给上层重试
        if (root.optInt("code", -1) != 0) return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)
        val req = root.optJSONObject("req") ?: return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)
        if (req.optInt("code", -1) != 0) return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)
        val reqData = req.optJSONObject("data") ?: return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)
        val body2 = reqData.optJSONObject("body") ?: return TxSearchOnceResult(ok = false, list = emptyList(), total = 0)
        val itemSong = body2.optJSONArray("item_song") ?: return TxSearchOnceResult(ok = true, list = emptyList(), total = 0)

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
            // 对齐落雪：album.mid 为空/“空”时用歌手头像（singer[0].mid 的 T001 图）兜底
            var pic = ""
            if (albumMid.isNotBlank() && albumMid != "空") {
                pic = "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
            } else if (singers != null && singers.length() > 0) {
                val singerMid = singers.optJSONObject(0)?.optString("mid", "")
                if (!singerMid.isNullOrBlank()) {
                    pic = "https://y.gtimg.cn/music/photo_new/T001R300x300M000$singerMid.jpg"
                }
            }

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
        // 对齐落雪：total 取 meta.estimate_sum（estimate_sum 缺失时退回 list.size）
        val estimateSum = reqData.optJSONObject("meta")?.optInt("estimate_sum", 0) ?: 0
        return TxSearchOnceResult(
            ok = true,
            list = list,
            total = if (estimateSum > 0) estimateSum else list.size
        )
    }

    /** 构造 QQ 搜索请求 JSON（字段顺序必须与落雪一致，sign 依赖序列化字符串） */
    private fun buildTxSearchJson(keyword: String, page: Int, pageSize: Int, searchType: Int = 0): String {
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
            "\"search_type\":$searchType,\"searchid\":\"${(1..16).map { (0..9).random() }.joinToString("")}\"," +
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
                img = normalizeMgImageUrl(img)

                list.add(
                    LxSongInfo(
                        id = songId,
                        songmid = songId,
                        // 咪咕歌词（musicinfo.do）需要 copyrightId，落雪同款
                        hash = data.optString("copyrightId"),
                        name = name,
                        singer = singerNames.joinToString(" / "),
                        albumName = data.optString("album", ""),
                        // 咪咕返回的 duration 单位已是「秒」（如 270）
                        duration = data.optLong("duration", 0L),
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

    // ─── 歌单搜索（对齐落雪 musicSdk.*.songList.search）───────────────────

    /**
     * 内置源歌单搜索：
     * - kw：search.kuwo.cn 的 ft=playlist
     * - kg：msearchcdn.kugou.com/api/v3/search/special
     * - tx：u.y.qq.com 歌单搜索（best-effort）
     * - mg：jadeite.migu.cn 歌单搜索（best-effort）
     * 各源字段差异较大，全部容错解析，失败返回空列表而不抛异常。
     */
    suspend fun searchPlaylists(source: String, keyword: String, page: Int, pageSize: Int): LxPlaylistSearchResult {
        if (keyword.isBlank()) return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
        return withContext(Dispatchers.IO) {
            try {
                when (source) {
                    "kw" -> searchPlaylistsKw(keyword, page, pageSize)
                    "kg" -> searchPlaylistsKg(keyword, page, pageSize)
                    "tx" -> searchPlaylistsTx(keyword, page, pageSize)
                    "mg" -> searchPlaylistsMg(keyword, page, pageSize)
                    else -> LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: searchPlaylists failed source=$source")
                LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
        }
    }

    /**
     * 内置源歌单详情：拉取歌单内的歌曲列表（供「预览」与「保存到本地」使用）。
     * 四个内置源均实现，对齐落雪 musicSdk.{kg,tx,mg,kw}.songList.getListDetail。
     * 各源字段差异较大，全部容错解析，失败返回空结果而不抛异常。
     * @param page/pageSize 仅 mg / kw 分页有效（kg / tx 一次返回全部）
     */
    suspend fun getPlaylistSongs(
        source: String,
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 1000
    ): LxSearchResult {
        if (playlistId.isBlank()) return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        return withContext(Dispatchers.IO) {
            try {
                when (source) {
                    "kg" -> getKgPlaylistSongs(playlistId)
                    "tx" -> getTxPlaylistSongs(playlistId)
                    "mg" -> getMgPlaylistSongs(playlistId, page, pageSize)
                    "kw" -> getKwPlaylistSongs(playlistId, page, pageSize)
                    else -> LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: getPlaylistSongs failed source=$source id=$playlistId")
                LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
        }
    }

    private suspend fun searchPlaylistsKw(keyword: String, page: Int, pageSize: Int): LxPlaylistSearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$KW_SEARCH_URL?client=kt&all=$encoded&pn=${page - 1}&rn=$pageSize" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
            "&newver=1&ft=playlist&cluster=0&strategy=2012&encoding=utf8&rformat=json" +
            "&vermerge=1&mobi=1"
        val body = httpGet(url) ?: return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
        val root = JSONObject(body)
        if (root.optString("SHOW") == "0") return LxPlaylistSearchResult(isEnd = true)
        val abslist = root.optJSONArray("abslist") ?: return LxPlaylistSearchResult(isEnd = true)
        val list = mutableListOf<LxPlaylistInfo>()
        for (i in 0 until abslist.length()) {
            val item = abslist.optJSONObject(i) ?: continue
            val id = item.optString("playlistid").ifBlank { item.optString("PLAYLISTID") }
            if (id.isBlank()) continue
            val name = item.optString("name").ifBlank { item.optString("PLAYLISTNAME") }
            if (name.isBlank()) continue
            val pic = item.optString("pic").ifBlank { item.optString("hts_MVPIC") }
                .ifBlank { item.optString("MVPIC") }
            list.add(
                LxPlaylistInfo(
                    id = id,
                    name = name,
                    cover = pic,
                    author = item.optString("nickname").ifBlank { item.optString("NICKNAME") },
                    trackCount = item.optInt("songnum", item.optInt("SONGNUM", 0)),
                    playCount = item.optLong("playcnt", item.optLong("PLAYCNT", 0L)),
                    description = item.optString("intro", ""),
                    source = "kw"
                )
            )
        }
        val total = root.optInt("TOTAL", list.size)
        return LxPlaylistSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    private suspend fun searchPlaylistsKg(keyword: String, page: Int, pageSize: Int): LxPlaylistSearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$KG_PLAYLIST_SEARCH_URL?keyword=$encoded&page=$page&pagesize=$pageSize&showtype=10"
        val body = httpGet(url) ?: return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: return LxPlaylistSearchResult(isEnd = true)
        val info = data.optJSONArray("info") ?: return LxPlaylistSearchResult(isEnd = true)
        val list = mutableListOf<LxPlaylistInfo>()
        for (i in 0 until info.length()) {
            val item = info.optJSONObject(i) ?: continue
            val id = item.optString("specialid")
            if (id.isBlank()) continue
            val name = item.optString("specialname")
            if (name.isBlank()) continue
            list.add(
                LxPlaylistInfo(
                    id = id,
                    name = name,
                    cover = item.optString("imgurl", "").replace("{size}", "480"),
                    author = item.optString("nickname", ""),
                    trackCount = item.optInt("songcount", 0),
                    playCount = item.optLong("playcount", 0L),
                    description = item.optString("intro", ""),
                    source = "kg"
                )
            )
        }
        val total = data.optInt("total", list.size)
        return LxPlaylistSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    private suspend fun searchPlaylistsTx(keyword: String, page: Int, pageSize: Int): LxPlaylistSearchResult {
        repeat(3) { attempt ->
            val r = runCatching { searchPlaylistsTxOnce(keyword, page, pageSize) }.getOrNull()
            if (r != null) return r
            if (attempt < 2) delay(200L * (1 shl attempt))
        }
        return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
    }

    /**
     * QQ音乐歌单搜索。
     *
     * 走 soso 老接口（与落雪 `musicSdk/tx/songList.js` 一致）：纯 GET、无需签名。
     * 之前用 `u.y.qq.com/cgi-bin/musics.fcg` + `searchType=3` 的签名接口在真机上恒空，
     * 因为该接口的歌单结果并不在 `req.data.body.item_playlist` 下。
     */
    private suspend fun searchPlaylistsTxOnce(
        keyword: String,
        page: Int,
        pageSize: Int
    ): LxPlaylistSearchResult? {
        val url = "$TX_PLAYLIST_SEARCH_URL?page_no=${(page - 1).coerceAtLeast(0)}" +
            "&num_per_page=$pageSize&format=json&query=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&remoteplace=txt.yqq.playlist&inCharset=utf8&outCharset=utf-8"
        val body = httpGetHeaders(
            url,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)",
                "Referer" to "http://y.qq.com/portal/search.html"
            )
        ) ?: return null

        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 0) return null
        val data = root.optJSONObject("data") ?: return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)
        val items = data.optJSONArray("list")
            ?: return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)

        val list = mutableListOf<LxPlaylistInfo>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("dissid").ifBlank { item.optString("docid") }
            if (id.isBlank() || id == "0") continue
            val name = decodeHtmlEntities(item.optString("dissname"))
            if (name.isBlank()) continue
            list.add(
                LxPlaylistInfo(
                    id = id,
                    name = name,
                    cover = item.optString("imgurl"),
                    author = decodeHtmlEntities(item.optJSONObject("creator")?.optString("name").orEmpty()),
                    trackCount = item.optInt("song_count", 0),
                    playCount = item.optLong("listennum", 0L),
                    description = decodeHtmlEntities(item.optString("introduction")).replace("<br>", "\n"),
                    source = "tx"
                )
            )
        }
        val total = data.optInt("sum", list.size)
        return LxPlaylistSearchResult(
            isEnd = if (total > 0) page * pageSize >= total else list.size < pageSize,
            list = list,
            total = total
        )
    }

    private suspend fun searchPlaylistsMg(keyword: String, page: Int, pageSize: Int): LxPlaylistSearchResult {
        val time = System.currentTimeMillis().toString()
        val sign = md5("$keyword$MG_SIGNATURE_MD5$MG_APP_SECRET$MG_DEVICE_ID$time")
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$MG_SEARCH_URL?isCorrect=0&isCopyright=1" +
            "&searchSwitch=%7B%22song%22%3A0%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22bestShow%22%3A0%2C%22songlist%22%3A1%2C%22lyricSong%22%3A0%7D" +
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
        }.getOrNull() ?: return LxPlaylistSearchResult(list = emptyList(), isEnd = true, total = 0)

        val root = JSONObject(body)
        if (root.optString("code") != "000000") return LxPlaylistSearchResult(isEnd = true)
        val listData = root.optJSONObject("songlistResultData")
            ?: root.optJSONObject("songListResultData")
            ?: return LxPlaylistSearchResult(isEnd = true)
        // 咪咕歌单搜索的列表层是「扁平数组」（result），部分版本会包一层分组（resultList）
        val flat = listData.optJSONArray("result")
        val grouped = listData.optJSONArray("resultList")

        val datas = mutableListOf<JSONObject>()
        if (flat != null) {
            for (i in 0 until flat.length()) {
                flat.optJSONObject(i)?.let { datas.add(it) }
            }
        } else if (grouped != null) {
            for (g in 0 until grouped.length()) {
                val group = grouped.optJSONArray(g) ?: continue
                for (i in 0 until group.length()) {
                    group.optJSONObject(i)?.let { datas.add(it) }
                }
            }
        }

        val list = mutableListOf<LxPlaylistInfo>()
        for (data in datas) {
            val id = data.optString("id").ifBlank { data.optString("playlistId") }
                .ifBlank { data.optString("songlistId") }
            if (id.isBlank() || id == "0") continue
            val name = data.optString("name").ifBlank { data.optString("title") }
                .ifBlank { data.optString("songlistName") }
            if (name.isBlank()) continue
            var img = data.optString("musicListPicUrl")
                .ifBlank { data.optString("img3") }.ifBlank { data.optString("img2") }
                .ifBlank { data.optString("img1") }.ifBlank { data.optString("pic") }
            img = normalizeMgImageUrl(img)
            list.add(
                LxPlaylistInfo(
                    id = id,
                    name = name,
                    cover = img,
                    author = data.optString("userName").ifBlank { data.optString("nickName") }
                        .ifBlank { data.optString("nickname") },
                    trackCount = data.optInt("musicNum", data.optInt("songNum", 0)),
                    playCount = data.optLong("playNum", data.optLong("playCount", 0L)),
                    description = data.optString("summary").ifBlank { data.optString("desc") }
                        .ifBlank { data.optString("intro") },
                    source = "mg"
                )
            )
        }
        val total = listData.optInt("totalCount", list.size)
        return LxPlaylistSearchResult(
            isEnd = page * pageSize >= total || list.isEmpty(),
            list = list,
            total = total
        )
    }

    // ─── 歌单详情实现 ────────────────────────────────────────────────────

    /** 酷狗：special/single 页面拿 hash 列表 → gateway 批量换歌曲详情 */
    private suspend fun getKgPlaylistSongs(playlistId: String): LxSearchResult {
        val id = playlistId.removePrefix("id_")
        var hashes: List<String> = emptyList()
        for (attempt in 0 until 3) {
            val html = httpGet("$KG_PLAYLIST_PAGE$id-5-9999.html")
            if (html != null) {
                KG_LIST_DATA_REGEX.find(html)?.let { hashes = parseKgHashList(it.groupValues[1]) }
            }
            if (hashes.isNotEmpty()) break
            if (attempt < 2) delay(300L * (1 shl attempt))
        }
        if (hashes.isEmpty()) return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val list = getKgMusicInfos(hashes)
        if (list.isEmpty()) return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        return LxSearchResult(list = list, isEnd = true, total = list.size)
    }

    /** 解析 `global.data = [{hash:"..."}, ...]` */
    private fun parseKgHashList(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i)?.optString("hash", "") ?: ""
                if (h.isNotBlank()) out.add(h)
            }
            out
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: parse kg hash list failed")
            emptyList()
        }
    }

    /** 按 hash 批量取酷狗歌曲详情（每批 100），对齐落雪 filterData2 字段映射 */
    private suspend fun getKgMusicInfos(hashes: List<String>): List<LxSongInfo> {
        val unique = hashes.distinct()
        if (unique.isEmpty()) return emptyList()
        val out = ArrayList<LxSongInfo>(unique.size)
        val seenAudioIds = HashSet<String>()
        unique.chunked(100).forEach { batch ->
            for (item in postKgAudioBatch(batch)) {
                val audio = item.optJSONObject("audio_info") ?: continue
                val audioId = audio.optString("audio_id", "")
                if (audioId.isNotBlank() && !seenAudioIds.add(audioId)) continue
                val name = item.optString("songname", "")
                if (name.isBlank()) continue
                val hash = audio.optString("hash", "")
                if (hash.isBlank()) continue
                out.add(
                    LxSongInfo(
                        id = hash,
                        hash = hash,
                        name = name,
                        singer = item.optString("author_name", ""),
                        albumName = item.optJSONObject("album_info")?.optString("album_name", "") ?: "",
                        duration = audio.optString("timelength", "0").toLongOrNull()?.div(1000L) ?: 0L,
                        source = "kg"
                    )
                )
            }
        }
        return out
    }

    private suspend fun postKgAudioBatch(hashes: List<String>): List<JSONObject> {
        val dataArr = JSONArray()
        hashes.forEach { h -> dataArr.put(JSONObject().put("hash", h)) }
        val body = JSONObject().apply {
            put("area_code", "1")
            put("show_privilege", 1)
            put("show_album_info", "1")
            put("is_publish", "")
            put("appid", 1005)
            put("clientver", 11451)
            put("mid", "1")
            put("dfid", "-")
            put("clienttime", System.currentTimeMillis())
            put("key", "OIlwieks28dk2k092lksi2UIkp")
            put("fields", "album_info,author_name,audio_info,ori_audio_name,base,songname")
            put("data", dataArr)
        }
        val request = Request.Builder()
            .url(KG_GATEWAY_AUDIO_URL)
            .addHeader("KG-THash", "13a3164")
            .addHeader("KG-RC", "1")
            .addHeader("KG-Fake", "0")
            .addHeader("KG-RF", "00869891")
            .addHeader("User-Agent", "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi")
            .addHeader("x-router", "kmr.service.kugou.com")
            .post(body.toString().toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        val text = runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val errCode = when {
            root.has("error_code") -> root.optInt("error_code", 0)
            root.has("errcode") -> root.optInt("errcode", 0)
            root.has("err_code") -> root.optInt("err_code", 0)
            else -> 0
        }
        if (errCode != 0) return emptyList()
        val arr = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            // body.data 是「数组的数组」，每项取 [0]（落雪 data.map(s => s[0])）
            val item = arr.optJSONArray(i)?.optJSONObject(0) ?: arr.optJSONObject(i)
            if (item != null) out.add(item)
        }
        return out
    }

    /** QQ音乐：fcg_ucc_getcdinfo_byids_cp（落雪 tx/songList.js getListDetail） */
    private suspend fun getTxPlaylistSongs(playlistId: String): LxSearchResult {
        val id = Regex("(\\d+)").find(playlistId)?.groupValues?.get(1) ?: playlistId
        val url = "$TX_PLAYLIST_DETAIL_URL?type=1&json=1&utf8=1&onlysong=0&new_format=1" +
            "&disstid=$id&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8" +
            "&notice=0&platform=yqq.json&needNewCode=0"
        var songlist: JSONArray? = null
        for (attempt in 0 until 3) {
            val body = httpGetHeaders(
                url,
                mapOf(
                    "Origin" to "https://y.qq.com",
                    "Referer" to "https://y.qq.com/n/yqq/playsquare/$id.html",
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
                )
            )
            if (body != null) {
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root != null && root.optInt("code", -1) == 0) {
                    songlist = root.optJSONArray("cdlist")?.optJSONObject(0)?.optJSONArray("songlist")
                    if (songlist != null) break
                    // code==0 但无歌曲：合法空响应，不再重试
                    return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
            }
            if (attempt < 2) delay(300L * (1 shl attempt))
        }
        val songs = songlist ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val list = ArrayList<LxSongInfo>(songs.length())
        for (i in 0 until songs.length()) {
            val item = songs.optJSONObject(i) ?: continue
            val mid = item.optString("mid", "")
            val name = item.optString("title", "").ifBlank { item.optString("name", "") }
            if (mid.isBlank() || name.isBlank()) continue
            val singers = item.optJSONArray("singer")
            val singerNames = mutableListOf<String>()
            if (singers != null) {
                for (j in 0 until singers.length()) {
                    singers.optJSONObject(j)?.optString("name", "")
                        ?.takeIf { it.isNotBlank() }?.let { singerNames.add(it) }
                }
            }
            val album = item.optJSONObject("album")
            val albumMid = album?.optString("mid", "") ?: ""
            val albumName = album?.optString("name", "") ?: ""
            var pic = ""
            if (albumMid.isNotBlank() && albumMid != "空") {
                pic = "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
            } else if (singers != null && singers.length() > 0) {
                val singerMid = singers.optJSONObject(0)?.optString("mid", "")
                if (!singerMid.isNullOrBlank()) {
                    pic = "https://y.gtimg.cn/music/photo_new/T001R300x300M000$singerMid.jpg"
                }
            }
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
        return LxSearchResult(list = list, isEnd = true, total = list.size)
    }

    /**
     * 咪咕：MIGUM3.0 resource/playlist/song/v2.0（落雪 mg/songList.js）
     *
     * 咪咕接口单页最多 100 首，而「保存整张歌单」会一次要 1000 首，因此这里在大页长时逐页拉取。
     */
    private suspend fun getMgPlaylistSongs(playlistId: String, page: Int, pageSize: Int): LxSearchResult {
        val requested = if (pageSize > 0) pageSize else 30
        if (requested <= MG_MAX_PAGE_SIZE) return getMgPlaylistSongsPage(playlistId, page, requested)

        val merged = LinkedHashMap<String, LxSongInfo>()
        var total = 0
        var current = page
        while (merged.size < requested) {
            val r = getMgPlaylistSongsPage(playlistId, current, MG_MAX_PAGE_SIZE)
            if (r.total > 0) total = r.total
            val before = merged.size
            r.list.forEach { merged.putIfAbsent(it.id, it) }
            if (r.list.isEmpty() || r.isEnd || merged.size == before) break
            current += 1
        }
        val list = merged.values.take(requested)
        val effectiveTotal = if (total > 0) total else list.size
        return LxSearchResult(
            list = list,
            isEnd = list.isEmpty() || merged.size >= effectiveTotal,
            total = effectiveTotal
        )
    }

    private suspend fun getMgPlaylistSongsPage(playlistId: String, page: Int, pageSize: Int): LxSearchResult {
        val size = pageSize.coerceIn(1, MG_MAX_PAGE_SIZE)
        val url = "$MG_PLAYLIST_SONGS_URL?pageNo=$page&pageSize=$size&playlistId=$playlistId"
        var songArr: JSONArray? = null
        var total = 0
        for (attempt in 0 until 3) {
            val body = httpGetHeaders(
                url,
                mapOf(
                    "User-Agent" to MG_IOS_UA,
                    "Referer" to "https://m.music.migu.cn/",
                    "channel" to "0146921"
                )
            )
            if (body != null) {
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root != null && root.optString("code") == "000000") {
                    val data = root.optJSONObject("data")
                    songArr = data?.optJSONArray("songList")
                    total = data?.optInt("totalCount", 0) ?: 0
                    break
                }
            }
            if (attempt < 2) delay(300L * (1 shl attempt))
        }
        val songs = songArr ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val list = ArrayList<LxSongInfo>(songs.length())
        val seen = HashSet<String>()
        for (i in 0 until songs.length()) {
            val data = songs.optJSONObject(i) ?: continue
            val songId = data.optString("songId", "")
            if (songId.isBlank() || !seen.add(songId)) continue
            val name = data.optString("songName", "").ifBlank { data.optString("name", "") }
            if (name.isBlank()) continue
            val singerNames = mutableListOf<String>()
            val singers = data.optJSONArray("singerList")
            if (singers != null) {
                for (j in 0 until singers.length()) {
                    singers.optJSONObject(j)?.optString("name", "")
                        ?.takeIf { it.isNotBlank() }?.let { singerNames.add(it) }
                }
            }
            var img = data.optString("img3").ifBlank { data.optString("img2").ifBlank { data.optString("img1") } }
            img = normalizeMgImageUrl(img)
            list.add(
                LxSongInfo(
                    id = songId,
                    songmid = songId,
                    // 咪咕歌词需要 copyrightId（与 searchMg 一致）
                    hash = data.optString("copyrightId", ""),
                    name = name,
                    singer = singerNames.joinToString(" / "),
                    albumName = data.optString("album", ""),
                    // 咪咕返回的 duration 单位已是「秒」（如 270）
                    duration = data.optLong("duration", 0L),
                    pic = img,
                    source = "mg"
                )
            )
        }
        val effectiveTotal = if (total > 0) total else list.size
        return LxSearchResult(
            list = list,
            isEnd = page * size >= effectiveTotal || list.isEmpty(),
            total = effectiveTotal
        )
    }

    /** 酷我：nplserver pl.svc getlistinfo（落雪 kw/songList.js） */
    private suspend fun getKwPlaylistSongs(playlistId: String, page: Int, pageSize: Int): LxSearchResult {
        val id = normalizeKwPlaylistId(playlistId) ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val size = if (pageSize in 1..1000) pageSize else 1000
        val url = "$KW_PLAYLIST_DETAIL_URL?op=getlistinfo&pid=$id&pn=${page - 1}&rn=$size" +
            "&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
        var musiclist: JSONArray? = null
        var total = 0
        var rn = size
        for (attempt in 0 until 3) {
            val body = httpGet(url)
            if (body != null) {
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root != null && root.optString("result") == "ok" && root.has("musiclist")) {
                    musiclist = root.optJSONArray("musiclist")
                    total = root.optInt("total", 0)
                    rn = root.optInt("rn", size).coerceAtLeast(1)
                    break
                }
            }
            if (attempt < 2) delay(300L * (1 shl attempt))
        }
        val songs = musiclist ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        val list = ArrayList<LxSongInfo>(songs.length())
        for (i in 0 until songs.length()) {
            val item = songs.optJSONObject(i) ?: continue
            val rid = item.optString("id", "").ifBlank { item.optString("rid", "") }
            val name = item.optString("name", "")
            if (rid.isBlank() || name.isBlank()) continue
            list.add(
                LxSongInfo(
                    id = rid,
                    songmid = rid,
                    name = name,
                    singer = item.optString("artist", ""),
                    albumName = item.optString("album", ""),
                    duration = item.optLong("duration", 0L),
                    pic = item.optString("pic", ""),
                    source = "kw"
                )
            )
        }
        val effectiveTotal = if (total > 0) total else list.size
        return LxSearchResult(
            list = list,
            isEnd = page * rn >= effectiveTotal || list.isEmpty(),
            total = effectiveTotal
        )
    }

    /**
     * 归一化酷我歌单 id：
     * - 裸数字 pid 直接可用
     * - 落雪的 `digest-{8|5}__{id}` 形式取 `__` 后的部分
     * - 含 `/playlist(_detail)/{id}` 的链接剥出数字
     */
    private fun normalizeKwPlaylistId(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("digest-")) {
            val idx = s.indexOf("__")
            if (idx >= 0) return s.substring(idx + 2).takeIf { it.isNotBlank() }
        }
        if (s.all { it.isDigit() }) return s
        return Regex("/playlist(?:_detail)?/(\\d+)").find(s)?.groupValues?.get(1)
            ?: Regex("(\\d+)").find(s)?.groupValues?.get(1)
    }

    // ─── 工具 ───────────────────────────────────────────────────────────

    /** 咪咕图片可能是相对路径（/data/oss/...）或协议相对（//d.musicapp.migu.cn/...），统一补全 */
    private fun normalizeMgImageUrl(raw: String): String {
        val img = raw.trim()
        if (img.isEmpty()) return ""
        return when {
            img.startsWith("http") -> img
            img.startsWith("//") -> "http:$img"
            else -> "http://d.musicapp.migu.cn$img"
        }
    }

    /** 解码 HTML 实体（QQ音乐歌单名/简介里常见 `&#32;`、`&amp;` 等） */
    private fun decodeHtmlEntities(raw: String): String {
        if (raw.isEmpty() || !raw.contains('&')) return raw
        return HTML_ENTITY_REGEX.replace(raw) { m ->
            val num = m.groupValues[2]
            if (num.isNotEmpty()) {
                val code = if (m.groupValues[1] == "x") num.toIntOrNull(16) else num.toIntOrNull()
                if (code != null && code > 0 && code <= 0x10FFFF) String(Character.toChars(code)) else m.value
            } else {
                when (m.groupValues[3]) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    else -> m.value
                }
            }
        }
    }

    /** GET + 自定义请求头（歌单详情各源需要不同的 UA / Referer / Origin） */
    private suspend fun httpGetHeaders(url: String, headers: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
        }

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
