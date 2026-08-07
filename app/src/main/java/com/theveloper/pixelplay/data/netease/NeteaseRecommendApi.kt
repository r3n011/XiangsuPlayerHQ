package com.theveloper.pixelplay.data.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.NcmJson
import net.moriafly.ncm.NcmSession
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网易云每日推荐 —— 通过本地 NcmApi SDK 直连网易云官方加密接口（无需外部代理服务器）
 *
 * 数据来源：
 *  1. /api/v1/discovery/recommend/history —— 每日推荐历史（第一条通常为今天）
 *  2. /api/v1/discovery/recommend/history/detail —— 指定日期的每日推荐详情（兜底）
 */
@Singleton
class NeteaseRecommendApi @Inject constructor() {

    private companion object {
        private const val TAG = "NeteaseRecommendApi"
    }

    /** 将 NcmApi（本地 SDK）返回的 Map 转为 JSONObject，便于复用现有解析逻辑。 */
    private fun ncmMapToJson(map: Map<String, Any?>?): JSONObject? {
        if (map == null) return null
        return try {
            JSONObject(NcmJson.toJsonString(map))
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: NcmApi 响应转 JSON 失败")
            null
        }
    }

    /** 将外部传入的 cookie 合并进本地 NcmApi 会话，保证登录态一致。 */
    private fun syncCookieToSession(cookie: String?) {
        if (cookie.isNullOrBlank()) return
        try {
            val session = NcmSession.INSTANCE ?: return
            val map = cookie.split(';')
                .map { it.trim() }
                .filter { '=' in it }
                .associate { val (k, v) = it.split('=', limit = 2); k to v }
            if (map.isNotEmpty()) session.merge(map)
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: syncCookieToSession failed")
        }
    }

    /**
     * 获取今日每日推荐歌曲（本地 SDK 直连官方加密接口，需要登录）
     *
     * 优先调用官方每日推荐接口 /api/v3/discovery/recommend/songs；
     * 该接口不可用时回退到每日推荐历史（/api/v1/discovery/recommend/history + detail）。
     *
     * @param cookie 用户的网易云 cookie 字符串（会合并进本地会话，保证登录态一致）
     * @return 每日推荐结果（日期 + 推荐歌曲列表，每首含推荐理由）
     */
    suspend fun fetchTodayDailyRecommend(cookie: String): Result<DailyRecommendResult> {
        if (cookie.isBlank()) {
            return Result.failure(IllegalStateException("网易云未登录，无法获取每日推荐"))
        }
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Fetching today daily recommend")
                syncCookieToSession(cookie)

                // 主接口：/api/v3/discovery/recommend/songs（官方每日推荐）
                val recommendMap = NcmApi.full.recommendSongs().getOrNull()
                    ?: return@withContext Result.failure(Exception("recommend/songs 请求失败"))
                val recommendRoot = ncmMapToJson(recommendMap)
                    ?: return@withContext Result.failure(Exception("recommend/songs 响应解析失败"))

                // rawWeapi 对 404 等错误 body 也会返回 success，必须显式检查 code
                val code = recommendRoot.optInt("code", -1)
                if (code != 200) {
                    Timber.w("$TAG: recommend/songs code=$code message=${recommendRoot.optString("message")}")
                    return@withContext fetchTodayFromHistoryFallback()
                }

                val dataObj = recommendRoot.optJSONObject("data")
                val dailySongs = dataObj?.optJSONArray("dailySongs")
                val parsed = if (dailySongs != null) {
                    parseDailySongs(dailySongs)
                } else {
                    emptyList()
                }
                if (parsed.isEmpty()) {
                    return@withContext Result.failure(Exception("每日推荐无数据"))
                }

                val date = dataObj?.optString("date", "") ?: ""
                Timber.d("$TAG: Got ${parsed.size} daily recommend songs for $date")
                Result.success(DailyRecommendResult(date = date, songs = parsed))
            } catch (t: Throwable) {
                Timber.e(t, "$TAG: fetchTodayDailyRecommend failed")
                Result.failure(t)
            }
        }
    }

    /**
     * 回退方案：从每日推荐历史（第一条通常为今天）读取，取不到歌曲时再调当日详情接口。
     * 该接口在当前网易云服务端可能返回 404"接口未找到！"，仅作兜底。
     */
    private suspend fun fetchTodayFromHistoryFallback(): Result<DailyRecommendResult> {
        val historyMap = NcmApi.full.historyRecommendSongs(limit = 1).getOrNull()
            ?: return Result.failure(Exception("recommend/history 请求失败"))
        val historyRoot = ncmMapToJson(historyMap)
            ?: return Result.failure(Exception("recommend/history 响应解析失败"))

        // 404 等错误 body 同样需要检查 code
        val historyCode = historyRoot.optInt("code", -1)
        if (historyCode != 200) {
            return Result.failure(Exception("每日推荐接口不可用（code=$historyCode）"))
        }

        val dataArray = historyRoot.optJSONArray("data")
        val firstEntry = dataArray?.optJSONObject(0)
        val date = firstEntry?.optString("date", "") ?: ""

        // 历史条目中通常直接带当天推荐的歌曲
        var songs = firstEntry?.optJSONArray("songs")
        var parsed = if (songs != null && songs.length() > 0) {
            parseDailySongs(songs)
        } else {
            emptyList()
        }

        // 兜底：调当日详情接口
        if (parsed.isEmpty() && date.isNotBlank()) {
            Timber.d("$TAG: history entry has no songs, falling back to detail for date=$date")
            val detailMap = NcmApi.full.historyRecommendSongsDetail(date).getOrNull()
            val detailRoot = ncmMapToJson(detailMap)
            val detailCode = detailRoot?.optInt("code", -1) ?: -1
            val detailData = detailRoot?.optJSONObject("data")
            val detailSongs = detailData?.optJSONArray("dailySongs")
            if (detailCode == 200 && detailSongs != null) {
                parsed = parseDailySongs(detailSongs)
            }
        }

        if (parsed.isEmpty()) {
            return Result.failure(Exception("每日推荐无数据"))
        }

        Timber.d("$TAG: Got ${parsed.size} daily recommend songs for $date (history fallback)")
        return Result.success(DailyRecommendResult(date = date, songs = parsed))
    }

    /**
     * 解析 dailySongs 数组（兼容两种结构）：
     * 1) {song: {...}, reason: "..."}（详情接口 / 历史接口）
     * 2) 每一项直接就是歌曲对象（官方 /api/v3/discovery/recommend/songs 接口返回的裸歌曲对象）
     */
    private fun parseDailySongs(songsArray: org.json.JSONArray): List<DailyRecommendSong> {
        val result = mutableListOf<DailyRecommendSong>()
        for (i in 0 until songsArray.length()) {
            val entry = songsArray.optJSONObject(i) ?: continue
            val songObj = entry.optJSONObject("song")
                ?: entry.optJSONObject("track")
                ?: entry.takeIf { it.has("id") && it.has("name") }
                ?: continue
            val songId = songObj.optLong("id")
            if (songId <= 0) continue

            val name = songObj.optString("name", "Unknown")
            val artists = mutableListOf<String>()
            val artistIds = mutableListOf<Long>()
            val arArray = songObj.optJSONArray("ar")
            if (arArray != null) {
                for (j in 0 until arArray.length()) {
                    val ar = arArray.optJSONObject(j) ?: continue
                    ar.optString("name")?.takeIf { it.isNotBlank() }?.let { artists.add(it) }
                    val aid = ar.optLong("id")
                    if (aid > 0) artistIds.add(aid)
                }
            }

            val albumObj = songObj.optJSONObject("al")
            val albumName = albumObj?.optString("name", "Unknown Album") ?: "Unknown Album"
            val albumPic = normalizeUrl(albumObj?.optString("picUrl") ?: "")

            result.add(
                DailyRecommendSong(
                    song = PersonalFmSongDetail(
                        id = songId,
                        name = name,
                        artists = artists,
                        artistIds = artistIds,
                        albumName = albumName,
                        albumPic = albumPic,
                        duration = songObj.optLong("dt", 0L),
                        fee = songObj.optInt("fee", 0)
                    ),
                    reason = entry.optString("reason", "")
                )
            )
        }
        return result
    }

    /** 网易云接口返回的图片 URL 常为协议相对路径或明文 http://，统一补全/升级为 https。 */
    private fun normalizeUrl(raw: String): String {
        val cleaned = raw.trim().replace("`", "")
        return when {
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("http://") -> "https:" + cleaned.removePrefix("http:")
            cleaned.startsWith("/") -> "https://music.163.com$cleaned"
            cleaned.isNotBlank() -> cleaned
            else -> ""
        }
    }
}

/** 每日推荐歌曲条目（歌曲详情 + 网易云推荐理由） */
data class DailyRecommendSong(
    val song: PersonalFmSongDetail,
    val reason: String
)

/** 每日推荐结果 */
data class DailyRecommendResult(
    val date: String,
    val songs: List<DailyRecommendSong>
)
