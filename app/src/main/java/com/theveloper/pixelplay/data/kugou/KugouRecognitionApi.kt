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

/**
 * 一次酷狗识曲命中的原始信息（尚未解析成可播放的 Song）。
 *
 * 酷狗识别接口直接返回歌名/歌手/hash/album_audio_id，[KugouRecognitionApi] 只负责拿回来，
 * 由上层（[com.theveloper.pixelplay.data.recognition.SongRecognitionRepository]）再落到
 * 内置音源搜索转成 `cloud://lx` 的在线歌曲。
 */
data class KugouRecognitionHit(
    val name: String,
    val artist: String,
    val hash: String?,
    val albumAudioId: String?,
    val albumName: String?,
    val coverUrl: String?,
    val durationMs: Long,
)

/**
 * 酷狗听歌识曲（音频指纹匹配）。
 *
 * 移植自参照项目 md3Music 的 `audio_match.rs` + `song_recognition_page.dart`：
 * 1. 客户端把 8 kHz / 单声道 / PCM16 的原始录音（已降采样 + 增益归一化）作为**二进制 body**；
 * 2. `POST /fingerprint.service/v1/music_trackid_mulit`，query 带 `fpid / area_code / include_unpublish /
 *    useid / multi_result` 以及概念版 android 签名（appid=3116 / clientver=11440 / 盐见 [ROUTE]）；
 * 3. 二进制 body 的签名规则是 `md5(盐 || 排序参数拼接 || body 原始字节 || 盐)`（不是把 body 当字符串），
 *    见参照项目 `helper::signature_android_params` 的 `is_buffer` 分支。
 *
 * 与网易云识曲（[com.theveloper.pixelplay.data.netease.SongRecognitionClient]）**并行**执行，
 * 两条链路共用同一次录音，互不阻塞 —— 一家没命中还有另一家。
 */
@Singleton
class KugouRecognitionApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val kugouRepository: KugouRepository,
) {

    /**
     * 用一段 PCM16 录音识别。返回命中的候选（可能多条，`multi_result=1`）。
     * 未识别时返回空列表；网络/服务异常返回 [Result.failure]。
     */
    suspend fun match(pcm16: ByteArray): Result<List<KugouRecognitionHit>> = io {
        if (pcm16.isEmpty()) return@io emptyList()

        val userId = kugouRepository.userId
        val token = kugouRepository.authToken
        val device = runCatching { kugouRepository.device }.getOrNull()
        val deviceDfid = device?.dfid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        val deviceMid = device?.mid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        val deviceGuid = device?.guid?.takeIf { it.isNotBlank() && it != ANONYMOUS_DEVICE }
        val clientTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString()
        val useridNum = userId?.toLongOrNull() ?: 0L

        val params = LinkedHashMap<String, String>()
        params["dfid"] = deviceDfid ?: ANONYMOUS_DEVICE
        params["mid"] = deviceMid ?: ANONYMOUS_DEVICE
        params["uuid"] = deviceGuid ?: "-"
        params["appid"] = APP_ID
        params["clientver"] = CLIENT_VER
        params["clienttime"] = clientTime
        if (!token.isNullOrBlank()) params["token"] = token
        if (useridNum != 0L) params["userid"] = useridNum.toString()
        // ─── audio_match 模块参数（与参照项目 audio_match.rs 一致）───
        params["fpid"] = System.currentTimeMillis().toString()
        params["area_code"] = "1"
        params["include_unpublish"] = "1"
        params["useid"] = useridNum.toString()
        params["multi_result"] = "1"

        val joined = params.entries
            .sortedBy { it.key }
            .joinToString(separator = "") { (k, v) -> "$k=$v" }
        val signature = md5HexBytes(
            ROUTE.toByteArray(Charsets.UTF_8),
            joined.toByteArray(Charsets.UTF_8),
            pcm16,
            ROUTE.toByteArray(Charsets.UTF_8),
        )

        val urlBuilder = Uri.parse("$GATEWAY$PATH").buildUpon()
        params.forEach { (k, v) -> urlBuilder.appendQueryParameter(k, v) }
        urlBuilder.appendQueryParameter("signature", signature)

        val request = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", DEFAULT_UA)
            .header("content-type", "application/octet-stream")
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
                    if (useridNum != 0L) append("; userid=").append(useridNum)
                },
            )
            .post(pcm16.toRequestBody(BINARY_MEDIA))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            // 未识别时上游可能回 502 + JSON body（含 error_code），先解析 body 再判断
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return@use emptyList()
            val data = root.opt("data")
            val array: JSONArray = when (data) {
                is JSONArray -> data
                is JSONObject -> data.optJSONArray("list")
                    ?: data.optJSONArray("songs")
                    ?: data.optJSONArray("data")
                    ?: JSONArray()
                else -> JSONArray()
            }
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    parseHit(item)?.let(::add)
                }
            }
        }
    }

    private fun parseHit(item: JSONObject): KugouRecognitionHit? {
        val name = firstNonBlank(
            item.optString("songname"),
            item.optString("song_name"),
            item.optString("name"),
            item.optString("SongName"),
        ) ?: return null
        return KugouRecognitionHit(
            name = name,
            artist = firstNonBlank(
                item.optString("singername"),
                item.optString("singer_name"),
                item.optString("SingerName"),
                item.optString("author_name"),
            ).orEmpty(),
            hash = firstNonBlank(item.optString("hash"), item.optString("FileHash"), item.optString("file_hash")),
            albumAudioId = firstNonBlank(
                item.optString("album_audio_id"),
                item.optString("MixSongID"),
                item.optString("mixsongid"),
            ),
            albumName = firstNonBlank(
                item.optString("album_name"),
                item.optString("AlbumName"),
                item.optString("albumname"),
            ),
            coverUrl = normalizeCover(
                firstNonBlank(item.optString("imgurl"), item.optString("sizable_cover"), item.optString("union_cover"))
            ),
            durationMs = firstNonBlank(item.optString("timelength"), item.optString("duration"))
                ?.toLongOrNull()?.takeIf { it > 0 } ?: 0L,
        )
    }

    private fun normalizeCover(raw: String?): String? = raw
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?.replace("{size}", "480")
        ?.replace("http://", "https://")

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    /** md5(盐 || 排序参数 || body 原始字节 || 盐)：二进制 body 必须按字节参与，不能当字符串。 */
    private fun md5HexBytes(vararg parts: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        parts.forEach(digest::update)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).onFailure { Timber.w(it, "KugouRecognitionApi: request failed") }
    }

    private companion object {
        const val GATEWAY = "https://gateway.kugou.com"
        const val PATH = "/fingerprint.service/v1/music_trackid_mulit"
        /** 概念版（与账号/听书接口一致）：识别接口沿用参照项目默认的非标准签名。 */
        const val APP_ID = "3116"
        const val CLIENT_VER = "11440"
        const val ROUTE = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        const val ANONYMOUS_DEVICE = "-"
        const val DEFAULT_UA = "KuGou/11490 (Android)"
        val BINARY_MEDIA = "application/octet-stream".toMediaType()
    }
}
