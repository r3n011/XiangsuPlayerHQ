package com.theveloper.pixelplay.data.kugou

import com.theveloper.pixelplay.data.lx.LxSongInfo
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

/**
 * 酷狗「私人FM」（移植自 md3Music 的 `fm.rs` + `personal_fm_core.dart`）。
 *
 * 上游：`POST https://gateway.kugou.com/v2/personal_recommend`（**必须走网关 + x-router
 * 转发**：`persnfm.service.kugou.com` 是三级域名，证书 `*.kugou.com` 覆盖不到，
 * 直连会 SSLPeerUnverifiedException: Hostname not verified）。
 * - 关键参数：`appid=3116`、`clientver=11440`（概念版）、`mode`（normal/small/peak）、
 *   `song_pool_id`（0 口味 / 1 风格 / 2 探索）、`action=play`、
 *   `key=md5(3116 + 盐 + 11440 + 毫秒时间戳)`；
 * - 请求体是 **JSON**（不是表单），query 上还要带默认参数 + `signature`
 *   （md5(盐 + 按键排序拼接 + body + 盐)，与其它酷狗签名接口同款）；
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
    private val kugouRepository: KugouRepository,
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
            val device = kugouRepository.device
            val dfid = device.dfid.takeIf { it.isNotBlank() } ?: "-"
            val mid = device.mid.takeIf { it.isNotBlank() } ?: MID
            val token = kugouRepository.authToken?.takeIf { it.isNotBlank() }
            val userId = kugouRepository.userId?.toLongOrNull()?.takeIf { it > 0L }

            // ⚡ clienttime 必须是**毫秒时间戳**（参照项目 fm.rs 的 now_ms）：
            //    以前用 "yyyy-MM-dd HH:mm:ss" 的 UTC 时间串，key 和上游校验对不上 → 200304。
            val clientTimeMs = System.currentTimeMillis().toString()
            val body = JSONObject().apply {
                put("appid", APP_ID.toInt())
                put("clienttime", clientTimeMs)
                put("mid", mid)
                put("action", "play")
                put("recommend_source_locked", 0)
                put("song_pool_id", station.songPoolId)
                put("callerid", 0)
                put("m_type", 1)
                put("platform", "ios")
                put("area_code", 1)
                put("remain_songcnt", 0)
                put("clientver", CLIENT_VER.toInt())
                put("is_overplay", 0)
                put("mode", station.mode)
                put("fakem", FAKEM)
                // key = md5(appid + 盐 + clientver + 毫秒时间戳)（参照项目 sign_params_key）
                put("key", signParamsKey(clientTimeMs))
                if (userId != null) {
                    put("userid", userId)
                    put("kguid", userId)
                }
                token?.let { put("token", it) }
                if (!cursorHash.isNullOrBlank()) put("hash", cursorHash)
                if (!cursorSongId.isNullOrBlank()) put("songid", cursorSongId)
            }
            val bodyText = body.toString()

            // 默认参数 + 登录态一起参与签名；signature 写回 query
            val params = linkedMapOf(
                "dfid" to dfid,
                "mid" to mid,
                "uuid" to "-",
                "appid" to APP_ID,
                "clientver" to CLIENT_VER,
                "clienttime" to clientTimeMs,
            )
            token?.let { params["token"] = it }
            userId?.let { params["userid"] = it.toString() }
            val joined = params.entries
                .sortedBy { it.key }
                .joinToString(separator = "") { (k, v) -> "$k=$v" }
            val signature = md5Hex("$ROUTE_SALT$joined$bodyText$ROUTE_SALT")

            val urlBuilder = FM_URL.toHttpUrl().newBuilder()
            params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            urlBuilder.addQueryParameter("signature", signature)

            val request = Request.Builder()
                .url(urlBuilder.build())
                // 走网关转发到 persnfm 服务（直连三级域名会 TLS 主机名校验失败）
                .header("x-router", "persnfm.service.kugou.com")
                .header("User-Agent", UA)
                .header(
                    "Cookie",
                    buildString {
                        append("mid=").append(mid)
                        append("; dfid=").append(dfid)
                    },
                )
                .post(bodyText.toRequestBody(JSON_MEDIA))
                .build()

            val raw = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("私人FM请求失败：HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val root = JSONObject(raw)
            if (root.optInt("status", 1) != 1) {
                error(root.optString("error", "酷狗私人FM返回异常（error_code=${root.optInt("error_code")}）"))
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
            // FM 推荐项没有 img/cover 字段，封面在 trans_param.union_cover（带 {size} 占位）
            item.optJSONObject("trans_param")?.optString("union_cover"),
            item.optString("img"),
            item.optString("cover"),
            item.optString("sizable_cover"),
            albumInfo?.optString("img"),
            albumInfo?.optString("cover"),
        ).orEmpty().normalizeCover()
        // 单位是秒（time_length/timelen 都是秒；LxSongInfo.duration 也按秒传）
        val durationSec = firstNonBlank(
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
            duration = durationSec,
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

    /** 封面 URL：替换 `{size}` 占位并升级 https（酷狗老链接是 http）。 */
    private fun String.normalizeCover(): String =
        replace("`", "").replace("{size}", "480").replace("http://", "https://")

    /** 参考项目的签名：md5(appid + salt + clientver + clienttime)，小写十六进制。 */
    private fun signParamsKey(clientTime: String): String =
        md5Hex("$APP_ID$ROUTE_SALT$CLIENT_VER$clientTime")

    private fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val APP_ID = "3116"
        const val CLIENT_VER = "11440"
        const val ROUTE_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"

        /** 网关地址：三级域名 persnfm.service.kugou.com 证书校验不过，只能走网关 + x-router。 */
        const val FM_URL = "https://gateway.kugou.com/v2/personal_recommend"
        const val FAKEM = "ca981cfc583a4c37f28d2d49000013c16a0a"

        /** 设备身份取不到时的兜底 mid（正常用 KugouRepository.device.mid）。 */
        const val MID = "02:00:00:00:00:00"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val JSON_MEDIA = "application/json;charset=utf-8".toMediaType()
    }
}
