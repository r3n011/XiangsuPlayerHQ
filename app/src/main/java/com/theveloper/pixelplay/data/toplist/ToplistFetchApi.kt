package com.theveloper.pixelplay.data.toplist

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多平台排行榜接口（对齐落雪 musicSdk 各平台 leaderboard.js）：
 * - tx QQ音乐：u.y.qq.com musicu.fcg ToplistInfoServer GetDetail（period 从 pc 端 top.html 抓取）
 * - kg 酷狗：mobilecdnbj.kugou.com api/v3/rank/song（真分页，JSON）
 *
 * 播放：统一构造 cloud://lx/{json} URI（DualPlayerEngine 现成协议：内置源官方接口 + 落雪引擎兜底）。
 */
@Singleton
class ToplistFetchApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "ToplistFetchApi"

        // ── QQ音乐 ──
        private const val TX_LIST_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val TX_PERIOD_URL = "https://c.y.qq.com/node/pc/wk_v15/top.html"
        private const val TX_LIMIT = 300

        // ── 酷狗 ──
        private const val KG_LIST_URL =
            "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=100&area_code=1&page=1&rankid=%s&with_res_tag=0&show_portrait_mv=1"
    }

    /** 按平台拉取一个榜单的全部歌曲 */
    suspend fun fetch(platform: ToplistCatalog.Platform, entry: ToplistCatalog.Entry): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            try {
                if (platform == ToplistCatalog.Platform.WY) {
                    return@withContext Result.failure(Exception("wy handled by NeteaseRepository"))
                }
                val songs = when (platform) {
                    ToplistCatalog.Platform.TX -> fetchTx(entry)
                    ToplistCatalog.Platform.KG -> fetchKg(entry)
                    ToplistCatalog.Platform.WY -> emptyList()
                }
                if (songs.isEmpty()) {
                    Result.failure(Exception("${platform.label} 榜单「${entry.name}」无数据"))
                } else {
                    Result.success(songs)
                }
            } catch (t: Throwable) {
                Timber.w(t, "$TAG: fetch ${platform.key} ${entry.id} failed")
                Result.failure(t)
            }
        }

    // ─── QQ音乐 ─────────────────────────────────────────────────────────

    private suspend fun fetchTx(entry: ToplistCatalog.Entry): List<Song> {
        val period = fetchTxPeriod(entry.bangid)
        val payload = JSONObject()
            .put(
                "toplist",
                JSONObject()
                    .put("module", "musicToplist.ToplistInfoServer")
                    .put("method", "GetDetail")
                    .put(
                        "param",
                        JSONObject()
                            .put("topid", entry.bangid.toIntOrNull() ?: 0)
                            .put("num", TX_LIMIT)
                            .put("period", period)
                    )
            )
            .put(
                "comm",
                JSONObject().put("uin", 0).put("format", "json").put("ct", 20).put("cv", 1859)
            )
        val request = Request.Builder()
            .url(TX_LIST_URL)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header(
                "User-Agent",
                "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"
            )
            .build()
        val body = execute(request) ?: return emptyList()
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList()
        val songList = root.optJSONObject("toplist")
            ?.optJSONObject("data")
            ?.optJSONArray("songInfoList") ?: return emptyList()

        val result = ArrayList<Song>(songList.length())
        for (i in 0 until songList.length()) {
            val item = songList.optJSONObject(i) ?: continue
            val mid = item.optString("mid", "")
            val songId = item.optLong("id", 0L)
            if (mid.isBlank()) continue
            val title = item.optString("title", "")
            if (title.isBlank()) continue
            val singerArr = item.optJSONArray("singer")
            val singer = if (singerArr != null && singerArr.length() > 0) {
                (0 until singerArr.length()).joinToString("/") { idx ->
                    singerArr.optJSONObject(idx)?.optString("name", "") ?: ""
                }
            } else ""
            val album = item.optJSONObject("album")
            val albumName = album?.optString("name", "") ?: ""
            val albumMid = album?.optString("mid", "") ?: ""
            val pic = if (albumMid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg"
            } else null
            val durationMs = item.optLong("interval", 0L) * 1000L
            result.add(buildSong(entry, mid, mid, mid, title, singer, albumName, pic, durationMs))
        }
        return result
    }

    /** 从 pc 端 top.html 抓取各榜单当前期数（对齐落雪 tx/leaderboard.js getPeriods） */
    private suspend fun fetchTxPeriod(bangid: String): String {
        return try {
            val request = Request.Builder().url(TX_PERIOD_URL).build()
            val html = execute(request) ?: return ""
            val pattern = Regex("""data-listname="(.+?)" data-tid=".*?\/(.+?)" data-date="(.+?)".*?<\/i>""")
            var period = ""
            for (m in pattern.findAll(html)) {
                val id = m.groupValues[2]
                val date = m.groupValues[3]
                if (id == bangid) {
                    period = date
                    break
                }
            }
            period
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: fetchTxPeriod failed bangid=$bangid")
            ""
        }
    }

    // ─── 酷狗 ─────────────────────────────────────────────────────────

    private suspend fun fetchKg(entry: ToplistCatalog.Entry): List<Song> {
        val request = Request.Builder().url(String.format(KG_LIST_URL, entry.bangid)).build()
        val body = execute(request) ?: return emptyList()
        val data = JSONObject(body).optJSONObject("data") ?: return emptyList()
        val info = data.optJSONArray("info") ?: return emptyList()

        val result = ArrayList<Song>(info.length())
        for (i in 0 until info.length()) {
            val item = info.optJSONObject(i) ?: continue
            val hash = item.optString("hash", "")
            if (hash.isBlank()) continue
            val title = item.optString("songname", "")
            if (title.isBlank()) continue
            val authors = item.optJSONArray("authors")
            val singer = if (authors != null && authors.length() > 0) {
                (0 until authors.length()).joinToString("/") { idx ->
                    authors.optJSONObject(idx)?.optString("author_name", "") ?: ""
                }
            } else ""
            // 实测：酷狗 rank/song 无 album_name/img 字段；专辑名在 remark，封面在 album_sizable_cover（含 {size} 占位）
            val album = item.optString("remark", "").ifBlank { item.optString("album_name", "") }
            val pic = item.optString("album_sizable_cover", "")
                .replace("{size}", "200")
                .takeIf { it.isNotBlank() }
            val durationSec = item.optLong("duration", 0L)
        result.add(buildSong(entry, hash, hash, hash, title, singer, album, pic, durationSec * 1000L))
        }
        return result
    }

    // ─── 通用 ─────────────────────────────────────────────────────────

    private fun buildSong(
        entry: ToplistCatalog.Entry,
        id: String,
        songmid: String,
        hash: String,
        title: String,
        singer: String,
        album: String,
        pic: String?,
        durationMs: Long
    ): Song {
        val lxJson = JSONObject()
            .put("id", id)
            .put("songmid", songmid)
            .put("hash", hash)
            .put("name", title)
            .put("singer", singer)
            .put("album", album)
            .put("pic", pic ?: "")
            .put("duration", durationMs)
            .put("source", entry.platformKey)
        // artists 数组：脚本 musicUrl 需要数组形式（对齐 cloud://lx 解析）
        val nameList = singer.split("/", "、").map { it.trim() }.filter { it.isNotBlank() }
        val artists = JSONArray()
        nameList.forEach { artists.put(JSONObject().put("id", "").put("name", it)) }
        lxJson.put("artists", artists)
        val cloudUri = "cloud://lx/" + URLEncoder.encode(lxJson.toString(), "UTF-8")
        return Song(
            id = "toplist_${entry.platformKey}_${entry.bangid}_$id",
            title = title,
            artist = singer,
            artistId = 0L,
            album = album,
            albumId = 0L,
            path = "",
            contentUriString = cloudUri,
            albumArtUriString = pic,
            duration = durationMs,
            mimeType = null,
            bitrate = null,
            sampleRate = null
        )
    }

    private suspend fun execute(request: Request): String? {
        return try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("$TAG: HTTP ${resp.code} for ${request.url}")
                    null
                } else {
                    resp.body?.string()
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: request failed ${request.url}")
            null
        }
    }
}
