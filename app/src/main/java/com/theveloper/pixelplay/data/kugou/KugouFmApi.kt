package com.theveloper.pixelplay.data.kugou

import com.theveloper.pixelplay.data.lx.LxSongInfo
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

/**
 * 酷狗「私人FM」（移植自 md3Music 的 `fm.rs` + `personal_fm_core.dart`）。
 *
 * 上游：`POST https://persnfm.service.kugou.com/v2/personal_recommend`
 * - 关键参数：`appid=3116`、`clientver=11440`（概念版）、`mode`（normal/small/peak）、
 *   `song_pool_id`（0 口味 / 1 风格 / 2 探索）、`action=play`、`key=md5(3116+salt+11440+ms)`；
 * - 续拉游标：带上上一批最后一首的 `hash` + `songid`，返回的仍是「接着听」的一批；
 * - 返回列表字段在 `data.song_list / songs / list / info` 里任取其一；
 * - **不强制登录**（uid=0 时不上送 userid），匿名也能拉。
 *
 * 返回 [LxSongInfo]（source = "kg"），播放链接由 PlayerViewModel 打成 cloud://lx 占位，
 * 实际播放时统一走 lx JS 音源引擎解析（用户导入的 JS 源优先，官方内置源兜底）。
 */
@Singleton
class KugouFmApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /** FM 档位（对齐参考项目的三档）。 */
    enum class Station(val label: String, val mode: String, val songPoolId: Int) {
        FAMILIAR("红心", "normal", 0),
        EXPLORE("探索", "normal", 2),
        NICHE("小众", "small", 1),
    }

    /**
     * 拉一批 FM 推荐。
     * @param station 档位
     * @param cursorHash 上一批最后一首的 hash（可选，用于"接着听"）
     * @param cursorSongId 上一批最后一首的 songid（可选）
     */
    suspend fun fetchNextBatch(
        station: Station = Station.FAMILIAR,
        cursorHash: String? = null,
        cursorSongId: String? = null,
    ): Result<List<LxSongInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val clientTime = utcTimestamp()
            val params = mutableMapOf(
                "appid" to APP_ID,
                "clienttime" to clientTime,
                "mid" to MID,
                "action" to "play",
                "recommend_source_locked" to "0",
                "song_pool_id" to station.songPoolId.toString(),
                "callerid" to "0",
                "m_type" to "1",
                "platform" to "ios",
                "area_code" to "1",
                "remain_songcnt" to "0",
                "clientver" to CLIENT_VER,
                "is_overplay" to "0",
                "mode" to station.mode,
                "fakem" to FAKEM,
                "key" to signParamsKey(clientTime),
            )
            if (!cursorHash.isNullOrBlank()) params["hash"] = cursorHash
            if (!cursorSongId.isNullOrBlank()) params["songid"] = cursorSongId

            val body = params.entries.joinToString("&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }.toRequestBody(FORM_JSON)
            val request = Request.Builder()
                .url(FM_URL)
                .header("x-router", "persnfm.service.kugou.com")
                .header("User-Agent", UA)
                .post(body)
                .build()

            val raw = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("私人FM请求失败：HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val root = JSONObject(raw)
            if (root.optInt("status", 1) != 1) {
                error(root.optString("error", "酷狗私人FM返回异常"))
            }
            val data = root.optJSONObject("data") ?: root
            val array = data.optJSONArray("song_list")
                ?: data.optJSONArray("songs")
                ?: data.optJSONArray("list")
                ?: data.optJSONArray("info")
                ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    parseFmSong(item)?.let(::add)
                }
            }
        }.onFailure { Timber.w(it, "KugouFmApi: fetchNextBatch failed") }
    }

    /** 把酷狗 FM 的一条 JSON 解析成 [LxSongInfo]（字段名兼容嵌套结构）。 */
    private fun parseFmSong(item: JSONObject): LxSongInfo? {
        val albumInfo = item.optJSONObject("album_info")
        val audioInfo = item.optJSONObject("audio_info")
        val songInfo = item.optJSONObject("song_info") ?: item.optJSONObject("base")

        val hash = firstNonBlank(
            item.optString("hash"),
            item.optString("sd_hash"),
            audioInfo?.optString("hash"),
            item.optString("file_hash"),
        ) ?: return null
        val songId = firstNonBlank(
            item.optString("songid"),
            item.optString("song_id"),
            songInfo?.optString("songid"),
            item.optString("audio_id"),
        )
        val name = firstNonBlank(
            item.optString("songname"),
            item.optString("FileName"),
            item.optString("song_name"),
            songInfo?.optString("songname"),
            item.optString("name"),
        ) ?: return null
        val singer = firstNonBlank(
            item.optString("singername"),
            item.optString("singer_name"),
            item.optString("author_name"),
            songInfo?.optString("singername"),
            parseSingers(item),
        ).orEmpty()
        val album = firstNonBlank(
            item.optString("album_name"),
            item.optString("albumname"),
            albumInfo?.optString("album_name"),
        ).orEmpty()
        val pic = firstNonBlank(
            item.optString("img"),
            item.optString("cover"),
            item.optString("sizable_cover"),
            albumInfo?.optString("img"),
            albumInfo?.optString("cover"),
        ).orEmpty()
        val durationMs = firstNonBlank(
            item.optString("timelen"),
            item.optString("duration"),
            audioInfo?.optString("duration"),
            item.optString("time_length"),
        )?.toLongOrNull() ?: 0L
        val albumAudioId = firstNonBlank(
            item.optString("album_audio_id"),
            item.optString("mixsongid"),
            audioInfo?.optString("album_audio_id"),
        )

        return LxSongInfo(
            id = songId ?: hash,
            songmid = songId.orEmpty(),
            hash = hash,
            name = name,
            singer = singer,
            albumName = album,
            pic = pic,
            duration = durationMs,
            source = "kg",
            // 酷狗有些接口要 album_audio_id，塞进 extra 备用（解析直链目前只用 hash/songmid）
            extra = albumAudioId?.takeIf { it.isNotBlank() }?.let { "albumAudioId=$it" }.orEmpty(),
        )
    }

    private fun parseSingers(item: JSONObject): String? {
        val array = item.optJSONArray("singerinfo")
            ?: item.optJSONArray("Singers")
            ?: item.optJSONArray("singer")
            ?: return null
        return buildList {
            for (i in 0 until array.length()) {
                val name = array.optJSONObject(i)?.optString("name")
                    ?: array.optJSONObject(i)?.optString("singer_name")
                if (!name.isNullOrBlank()) add(name)
            }
        }.joinToString("、").takeIf { it.isNotBlank() }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    /** 参考项目的签名：md5(appid + salt + clientver + clienttime)，小写十六进制。 */
    private fun signParamsKey(clientTime: String): String =
        md5Hex("$APP_ID$ROUTE_SALT$CLIENT_VER$clientTime")

    private fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun utcTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private companion object {
        const val APP_ID = "3116"
        const val CLIENT_VER = "11440"
        const val ROUTE_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        const val FM_URL = "https://persnfm.service.kugou.com/v2/personal_recommend"
        const val FAKEM = "ca981cfc583a4c37f28d2d49000013c16a0a"
        const val MID = "02:00:00:00:00:00"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val FORM_JSON = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    }
}
