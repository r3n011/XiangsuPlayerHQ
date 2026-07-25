package com.theveloper.pixelplay.data.qq

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 oiapi.net 溯音酷我接口的搜索 API。
 *
 * API 行为：
 *   - 搜索：GET https://oiapi.net/api/Kuwo?msg=<关键词>&n=<数量>&br=5
 *     当 n>=2 时，返回 data 为数组（每元素含 song/singer/album/picture/rid/time/types[]，
 *     但无 url）。用于获取搜索结果列表。
 *   - 播放：GET https://oiapi.net/api/Kuwo?msg=<歌名 歌手>&n=1&br=5
 *     当 n=1 时，返回 data 为对象（含 url 字段：酷我的音频直接播放地址）。
 *
 * br 参数：
 *   1 = FLAC 无损
 *   5 = 320k mp3
 *   7 = 128k mp3
 */
@Singleton
class QQSearchApi @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private companion object {
        private const val TAG = "QQSearchApi"
        private const val BASE_URL = "https://oiapi.net/api/Kuwo"
        private const val BR_320 = 5
        private const val BR_128 = 7
        private const val BR_FLAC = 1
    }

    /**
     * 搜索到的歌曲信息。
     * 用于展示搜索结果与播放（播放时再通过 song/singer 精确搜索拿 url）。
     */
    data class QQSong(
        /** 酷我歌曲 id (来自 data.id) */
        val id: Long,
        /** 酷我 rid (MUSIC_xxx)，用作去重/缓存 */
        val rid: String,
        val title: String,
        val album: String,
        val singer: String,
        val cover: String,
        /** 歌曲时长，秒 */
        val durationSec: Int,
        /** 当前码率说明，如 "320k mp3" / "128k mp3" / "FLAC" */
        val quality: String = ""
    )

    /**
     * 搜索歌曲。
     * @param keyword 关键词
     * @param page 第几页（oiapi 没有真正分页，此处用作偏移：每次 n=20，第 N 页再拼 query）
     * @param num 每页数量（oiapi 对 n 敏感：n=1 单曲，n>=2 多首）
     */
    suspend fun search(keyword: String, page: Int = 1, num: Int = 20): Result<List<QQSong>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.d("$TAG: search(keyword='$keyword', page=$page, num=$num)")

                // oiapi 没有真正分页，但 n 控制数量。第 2+ 页时加个页码后缀做伪分页。
                val kw = if (page <= 1) keyword else "$keyword page$page"
                val useNum = num.coerceAtLeast(2) // 必须 >=2 才返回数组

                val encoded = java.net.URLEncoder.encode(kw, "UTF-8")
                val url = "$BASE_URL?msg=$encoded&n=$useNum&br=$BR_320"
                Timber.d("$TAG: request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${resp.code}: ${resp.message}"))
                    }
                    val body = resp.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))
                    Timber.d("$TAG: response len=${body.length}")

                    val root = try { JSONObject(body) } catch (e: Exception) {
                        return@withContext Result.failure(Exception("Invalid JSON: ${e.message}"))
                    }

                    val code = root.optInt("code", 0)
                    if (code != 1) {
                        val msg = root.optString("message", "Unknown error")
                        return@withContext Result.failure(Exception("API error (code=$code): $msg"))
                    }

                    // data 可能是对象(n=1)或数组(n>=2)
                    val songs = mutableListOf<QQSong>()
                    val dataAny = root.opt("data")
                    if (dataAny is JSONArray) {
                        for (i in 0 until dataAny.length()) {
                            val item = dataAny.optJSONObject(i) ?: continue
                            parseSongFromJson(item)?.let { songs.add(it) }
                        }
                    } else if (dataAny is JSONObject) {
                        parseSongFromJson(dataAny)?.let { songs.add(it) }
                    }

                    Timber.d("$TAG: parsed ${songs.size} songs")
                    Result.success(songs)
                }
            } catch (e: Exception) {
                Timber.e("$TAG: search exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * 获取单首歌曲的播放 URL。
     * oiapi 不支持用 rid 直接查，所以用 "歌名 歌手" 精确搜索拿 url。
     *
     * @param br 码率：1(FLAC) / 5(320k mp3) / 7(128k mp3)
     */
    suspend fun getPlayUrl(song: QQSong, br: Int = BR_320): Result<String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Timber.d("$TAG: getPlayUrl(song='${song.title}' singer='${song.singer}' br=$br)")

                val exactKw = "${song.title} ${song.singer}"
                val encoded = java.net.URLEncoder.encode(exactKw, "UTF-8")
                val url = "$BASE_URL?msg=$encoded&n=1&br=$br"
                Timber.d("$TAG: play URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    }
                    val body = resp.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response"))

                    val root = try { JSONObject(body) } catch (_: Exception) { null }
                    if (root?.optInt("code", 0) != 1) {
                        return@withContext Result.failure(
                            Exception("API error: ${root?.optString("message")}"))
                    }
                    val data = root.optJSONObject("data")
                        ?: return@withContext Result.failure(Exception("Missing data object"))
                    val u = data.optString("url", "").trim()
                    if (u.startsWith("http")) {
                        Result.success(u)
                    } else {
                        // message 里也可能有 "音乐链接：URL" 作为 fallback
                        val msg = root.optString("message", "")
                        val match = Regex("音乐链接[：:]\\s*(https?://\\S+)").find(msg)
                        val fromMsg = match?.groupValues?.get(1).orEmpty().trim()
                        if (fromMsg.startsWith("http")) Result.success(fromMsg)
                        else Result.failure(Exception("No playable URL in response"))
                    }
                }
            } catch (e: Exception) {
                Timber.e("$TAG: getPlayUrl exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private fun parseSongFromJson(json: JSONObject): QQSong? {
        val song = json.optString("song", "").trim()
        val singer = json.optString("singer", "").trim()
        val album = json.optString("album", "").trim()
        val picture = json.optString("picture", "").trim()
        val rid = json.optString("rid", "").trim()
        val id = json.optLong("id", -1L).let {
            if (it > 0) it
            else {
                // rid 形如 MUSIC_12345，取数字作 id
                val m = Regex("MUSIC_(\\d+)").find(rid)
                m?.groupValues?.get(1)?.toLongOrNull() ?: (song.hashCode().toLong().and(0x7fffffff))
            }
        }
        val time = json.optString("time", "0").toIntOrNull() ?: 0

        if (song.isEmpty()) return null

        // 从 types 里选一个说明性码率（有 mp3 优先）
        val types = json.optJSONArray("types")
        var quality = "320k mp3"
        if (types != null && types.length() > 0) {
            // 找 mp3 320k
            for (i in 0 until types.length()) {
                val t = types.optJSONObject(i) ?: continue
                if (t.optString("format") == "mp3" &&
                    t.optString("bitrate") == "320") {
                    quality = "320k mp3"
                    break
                }
                if (t.optString("format") == "flac") {
                    quality = "FLAC"
                }
            }
        }

        return QQSong(
            id = id,
            rid = rid,
            title = song,
            album = album,
            singer = singer,
            cover = picture,
            durationSec = time,
            quality = quality
        )
    }
}
