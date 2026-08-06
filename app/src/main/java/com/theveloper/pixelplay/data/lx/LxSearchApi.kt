package com.theveloper.pixelplay.data.lx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.NcmJson
import net.moriafly.ncm.ncmBool
import net.moriafly.ncm.ncmInt
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxSearchApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // vkeys API 获取封面（备用）
    private val COVER_API_BASE = "https://api.vkeys.cn/v2/music/netease"

    // 备用搜索 API：内置 NCM（官方加密 weapi）返回 405 操作频繁时使用
    private val BTWOA_API_BASE = "https://ncmapi.btwoa.com"

    /**
     * 将 NcmApi（本地 SDK）返回的 Map 转为 JSONObject，便于复用现有解析逻辑。
     * NcmApi 直接请求网易云官方加密接口，不依赖任何外部代理服务器。
     */
    private fun ncmMapToJson(map: Map<String, Any?>?): JSONObject? {
        if (map == null) return null
        return try {
            JSONObject(NcmJson.toJsonString(map))
        } catch (t: Throwable) {
            Timber.w(t, "NcmApi 响应转 JSON 失败")
            null
        }
    }

    /**
     * 网易云单曲搜索（本地 SDK 直连官方加密接口，无需外部代理），支持真正的 limit/offset 分页。
     * - limit: 单页返回数量，默认 20
     * - offset: 偏移量，从 0 开始，例如第 2 页 offset = limit
     */
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): LxSearchResult {
        if (keyword.isBlank()) {
            return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        }

        return withContext(Dispatchers.IO) {
            try {
                val offset = (page - 1) * pageSize
                val map = NcmApi.search(keyword, type = 1, limit = pageSize, offset = offset).getOrNull()
                if (map != null) {
                    val code = (map["code"] as? Number)?.toInt() ?: 200
                    if (code != 405) {
                        val root = ncmMapToJson(map)
                            ?: return@withContext LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                        return@withContext parseSearchResponse(root.toString(), pageSize, offset)
                    }
                    // code == 405（操作频繁/被风控）→ 走 btwoa 备用 API
                    Timber.w("内置 NCM 搜索返回 405 操作频繁，切换 btwoa 备用 API 搜索: $keyword")
                } else {
                    Timber.w("内置 NCM 搜索失败，切换 btwoa 备用 API 搜索: $keyword")
                }

                // 备用搜索 API：https://ncmapi.btwoa.com/cloudsearch（与官方 cloudsearch 返回格式一致）
                val url = "$BTWOA_API_BASE/cloudsearch?keywords=${URLEncoder.encode(keyword, "UTF-8")}" +
                        "&limit=$pageSize&offset=$offset&type=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                response.close()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Timber.e("btwoa 备用搜索失败: HTTP ${response.code}")
                    return@withContext LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
                parseSearchResponse(body, pageSize, offset)
            } catch (e: Exception) {
                Timber.e(e, "搜索API请求异常")
                LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
        }
    }

    /**
     * 解析 vkeys 搜索返回：
     * {
     *   "code": 200,
     *   "message": "请求成功！",
     *   "data": [
     *     { "id": 2121994285, "song": "天使的翅膀", "singer": "就这样乐队",
     *       "album": "狂喜的结局", "time": null, "quality": "高清臻音（Spatial Autio）",
     *       "cover": "http://..." }
     *   ]
     * }
     *
     * ⚡ 新增 pageSize 参数用于判断是否是最后一页：
     * - 如果返回结果数量 < pageSize，认为是最后一页（isEnd = true）
     * - 否则还有更多结果可以加载（isEnd = false）
     */
    private fun parseVkeysSearchResponse(body: String, pageSize: Int = 20): LxSearchResult {
        return try {
            val obj = JSONObject(body)
            // 有些封装可能使用 msg 代替 message 或者把列表放在其它字段里
            val code = obj.optInt("code", -1)
            if (code != 200) {
                Timber.w("搜索返回非成功 code: $code, msg=${obj.optString("message")}")
                return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
            val arr = obj.optJSONArray("data")
            if (arr == null || arr.length() == 0) {
                return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
            val list = ArrayList<LxSongInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val idValue = item.opt("id")
                val idStr = when (idValue) {
                    is Number -> idValue.toString()
                    is String -> idValue
                    else -> ""
                }
                list += LxSongInfo(
                    id = idStr,
                    songmid = idStr,
                    name = item.optString("song", "").trim(),
                    singer = item.optString("singer", "").trim(),
                    albumName = item.optString("album", "").trim(),
                    pic = item.optString("cover", "").trim().replace("`", "").trim()
                )
            }
            // ⚡ 根据返回数量判断是否还有更多：如果返回数量 < 每页请求数，认为是最后一页
            val isEnd = list.size < pageSize
            LxSearchResult(list = list, isEnd = isEnd, total = list.size)
        } catch (t: Throwable) {
            Timber.e(t, "解析 vkeys 搜索响应异常")
            LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        }
    }

    /**
     * 调用 vkeys API 获取指定歌曲的封面链接。
     * @param songId 163.com 的歌曲 ID（纯数字）
     * @return 封面 URL，如果获取失败返回 null
     */
    suspend fun getSongCoverFromVkeys(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        try {
            val url = "$COVER_API_BASE?id=$songId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("获取封面失败: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val obj = JSONObject(body)
            if (obj.optInt("code", -1) != 200) {
                Timber.e("vkeys 返回错误: ${obj.optString("message")}")
                return@withContext null
            }

            val data = obj.optJSONObject("data") ?: return@withContext null
            val cover = data.optString("cover", "").trim().ifBlank {
                data.optString("pic", "")
            }.trim()

            if (cover.isBlank()) null else cover
        } catch (e: Exception) {
            Timber.e(e, "获取封面异常")
            null
        }
    }

    /**
     * 调用网易云歌曲详情 API 获取封面链接（本地 SDK 直连官方加密接口）。
     * 返回 JSON 中 songs[0].al.picUrl 即为封面直链。
     * @param songId 网易云歌曲 ID（纯数字字符串）
     * @return 封面 URL，获取失败返回 null
     */
    suspend fun getSongCoverFromDetail(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        try {
            val map = NcmApi.songDetail(listOf(songId)).getOrNull() ?: return@withContext null
            val obj = ncmMapToJson(map) ?: return@withContext null

            val songs = obj.optJSONArray("songs") ?: return@withContext null
            val song = songs.optJSONObject(0) ?: return@withContext null
            val al = song.optJSONObject("al") ?: song.optJSONObject("album")
            val picUrl = al?.optString("picUrl", "")?.trim()?.replace("`", "")
            if (picUrl.isNullOrBlank()) null else picUrl
        } catch (e: Exception) {
            Timber.e(e, "获取封面(detail)异常")
            null
        }
    }

    /**
     * 批量获取多首歌曲的封面链接（一次请求）。
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, coverUrl>，仅包含成功获取封面的条目
     */
    suspend fun batchGetSongCovers(songIds: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        batchGetSongDetails(songIds)
            .mapValues { it.value.albumPic }
            .filterValues { it.isNotBlank() }
    }

    /**
     * 批量获取歌曲详情（一次请求，/api/v3/song/detail）。
     * 返回完整歌曲信息（标题/歌手/专辑/封面/时长），用于歌手页等
     * 接口不返回歌手与专辑字段时补全数据。
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, NeteaseSongDetailInfo>，仅包含成功获取详情的条目
     */
    suspend fun batchGetSongDetails(songIds: List<String>): Map<String, NeteaseSongDetailInfo> =
        withContext(Dispatchers.IO) {
            if (songIds.isEmpty()) return@withContext emptyMap()
            try {
                val map = NcmApi.songDetail(songIds).getOrNull() ?: return@withContext emptyMap()
                val obj = ncmMapToJson(map) ?: return@withContext emptyMap()

                val songs = obj.optJSONArray("songs") ?: return@withContext emptyMap()
                val result = HashMap<String, NeteaseSongDetailInfo>(songs.length())
                for (i in 0 until songs.length()) {
                    val song = songs.optJSONObject(i) ?: continue
                    val id = song.optLong("id")
                    if (id <= 0) continue

                    val artists = mutableListOf<String>()
                    val artistIds = mutableListOf<Long>()
                    val arArray = song.optJSONArray("ar") ?: song.optJSONArray("artists")
                    if (arArray != null) {
                        for (j in 0 until arArray.length()) {
                            val ar = arArray.optJSONObject(j) ?: continue
                            ar.optString("name")?.takeIf { it.isNotBlank() }?.let { artists.add(it) }
                            val aid = ar.optLong("id")
                            if (aid > 0) artistIds.add(aid)
                        }
                    }

                    val al = song.optJSONObject("al") ?: song.optJSONObject("album")
                    val albumName = al?.optString("name", "") ?: ""
                    val picUrl = al?.optString("picUrl", "")?.trim()?.replace("`", "") ?: ""

                    result[id.toString()] = NeteaseSongDetailInfo(
                        id = id,
                        title = song.optString("name", ""),
                        artists = artists,
                        artistIds = artistIds,
                        albumName = albumName,
                        albumPic = picUrl,
                        duration = song.optLong("dt", 0L)
                    )
                }
                result
            } catch (e: Exception) {
                Timber.e(e, "批量获取歌曲详情异常")
                emptyMap()
            }
        }

    // ─── 评论 / 用户详情 ────────────────────────────────────────────────────────

    /**
     * 获取歌曲评论列表。支持分页。
     * @param songId 歌曲 id（纯数字字符串）
     * @param limit 单页数量，默认 20
     * @param offset 偏移量（分页），从 0 开始
     * @param before 分页游标（时间戳），取上一页最后一项的 time
     */
    suspend fun getSongComments(
        songId: String,
        limit: Int = 20,
        offset: Int = 0,
        before: Long? = null
    ): NeteaseCommentResult = withContext(Dispatchers.IO) {        if (songId.isBlank()) return@withContext NeteaseCommentResult()
        try {
            val map = NcmApi.full.commentMusic(
                id = songId,
                limit = limit,
                offset = offset,
                beforeTime = before ?: 0L,
            ).getOrNull() ?: return@withContext NeteaseCommentResult()

            // 本地 SDK 返回结构：{code, hotComments:[...], comments:[...], totalCount, hasMore, time}
            val hotComments = mutableListOf<NeteaseComment>()
            map.ncmList("hotComments").forEach { item ->
                (item as? Map<*, *>)?.let { hotComments.add(parseCommentFromMap(it)) }
            }

            val comments = mutableListOf<NeteaseComment>()
            map.ncmList("comments").forEach { item ->
                (item as? Map<*, *>)?.let { comments.add(parseCommentFromMap(it)) }
            }

            val hasMore = map.ncmBool("hasMore", comments.isNotEmpty())

            val cursor = if (comments.isNotEmpty()) {
                comments.last().time
            } else {
                map.ncmLong("time", 0L)
            }

            NeteaseCommentResult(
                comments = comments,
                hotComments = hotComments,
                hasMore = hasMore,
                totalCount = map.ncmInt("totalCount", 0),
                cursor = cursor
            )
        } catch (e: Exception) {
            Timber.e(e, "获取评论异常")
            NeteaseCommentResult()
        }
    }

    /**
     * 通过用户 id 获取用户详情（包含头像等）。
     */
    suspend fun getUserDetail(uid: Long): NeteaseUserDetail? = withContext(Dispatchers.IO) {
        if (uid <= 0L) return@withContext null
        try {
            val map = NcmApi.full.userDetail(uid.toString()).getOrNull() ?: return@withContext null
            val profile = map.ncmObj("profile")
            if (profile.isEmpty()) return@withContext null

            NeteaseUserDetail(
                userId = profile.ncmLong("userId", uid),
                nickname = profile.ncmString("nickname"),
                avatarUrl = profile.ncmString("avatarUrl"),
                signature = profile.ncmString("signature"),
                description = profile.ncmString("description")
            )
        } catch (e: Exception) {
            Timber.e(e, "获取用户详情异常")
            null
        }
    }

    // ─── 歌词 ────────────────────────────────────────────────────────

    /**
     * 通过网易云歌曲 id 获取 LRC 歌词（本地 SDK 直连官方加密接口）。
     * 返回：LRC 原文（包含时间戳），如果没有则返回 null。
     */
    suspend fun getLyric(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        try {
            val map = NcmApi.full.songLyric(songId).getOrNull() ?: return@withContext null

            val lrcText = map.ncmObj("lrc").ncmString("lyric").takeIf { it.isNotBlank() }
            val tlyricText = map.ncmObj("tlyric").ncmString("lyric").takeIf { it.isNotBlank() }

            // 场景1：原文 + 翻译都有 -> 按时间戳合并返回
            if (lrcText != null && tlyricText != null) {
                Timber.d("getLyric: combining lrc + tlyric for songId=$songId")
                return@withContext mergeLrcWithTranslation(lrcText, tlyricText)
            }

            // 场景2：只有原文 -> 返回原文
            if (lrcText != null) return@withContext lrcText

            // 场景3：只有翻译 -> 返回翻译（作为兜底）
            if (tlyricText != null) return@withContext tlyricText

            // 场景4：klyric 等其他字段作为终极兜底
            val klyric = map.ncmObj("klyric").ncmString("lyric")
            if (klyric.isNotBlank()) return@withContext klyric

            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "获取歌词异常: $songId")
            null
        }
    }

    /**
     * 合并 LRC 与翻译 LRC：智能选择含中文字符更多的一方作为主文本，
     * 然后按时间戳排序后，主文本行之后紧跟相同时间戳的次文本行。
     * LyricsUtils.parseLyrics() 的 pairTranslationLines() 会根据相同时间戳自动配对翻译。
     */
    private fun mergeLrcWithTranslation(lrcText: String, tlyricText: String): String {
        // 判断哪一侧含更多中文字符——中文多的作为主文本（line.line），
        // 另一方作为翻译/次文本（line.translation）。
        // 这样：中文歌曲的 lrc（中文）为主文本，英文歌曲的 tlyric（中文翻译）为主文本。
        val cjkRegex = Regex("[\\u4e00-\\u9fff]")
        val lrcCjkCount = cjkRegex.findAll(lrcText).count()
        val tlyricCjkCount = cjkRegex.findAll(tlyricText).count()
        val preferTlyricAsPrimary = tlyricCjkCount > lrcCjkCount

        val primarySource = if (preferTlyricAsPrimary) tlyricText else lrcText
        val secondarySource = if (preferTlyricAsPrimary) lrcText else tlyricText

        val originalLines = primarySource.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val translationLines = secondarySource.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

        // 提取 [mm:ss.xx] 时间戳 -> 文本内容，过滤掉非歌词行（如 [by:xxx] [ti:xxx] 元数据）
        val timestampLineRegex = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)")

        data class TimedLine(val timestampMs: Long, val text: String, val rawPrefix: String, var used: Boolean = false)

        fun parseTimedLines(lines: List<String>): List<TimedLine> {
            val result = mutableListOf<TimedLine>()
            for (rawLine in lines) {
                val matchResult = timestampLineRegex.find(rawLine) ?: continue
                val minutes = matchResult.groupValues[1].toLong()
                val seconds = matchResult.groupValues[2].toLong()
                val fracStr = matchResult.groupValues[3].ifBlank { "0" }
                val frac = when (fracStr.length) {
                    1 -> fracStr.toLong() * 100L
                    2 -> fracStr.toLong() * 10L
                    3 -> fracStr.toLong()
                    else -> fracStr.padEnd(3, '0').take(3).toLong()
                }
                val timestampMs = minutes * 60_000L + seconds * 1_000L + frac
                val text = matchResult.groupValues[4].trim()
                if (text.isNotBlank()) {
                    result.add(TimedLine(timestampMs, text, rawLine))
                }
            }
            return result
        }

        val originalTimed = parseTimedLines(originalLines)
        val translationTimed = parseTimedLines(translationLines)

        // 如果原文没有时间戳，但翻译有时间戳（或反之），直接用翻译内容拼接
        if (originalTimed.isEmpty()) {
            return originalLines.joinToString("\n")
        }

        if (translationTimed.isEmpty()) {
            return originalLines.joinToString("\n")
        }

        // 按主文本顺序输出，每行主文本后紧跟相同时间戳的次文本行
        val output = mutableListOf<String>()

        // 先复制所有非歌词元数据行（如 [by:xxx]），从主文本中提取
        val metaLineRegex = Regex("^\\[(by|ti|ar|al|au|re|ve|offset|length):.*]", RegexOption.IGNORE_CASE)
        originalLines.forEach { raw ->
            if (metaLineRegex.matches(raw)) {
                output.add(raw)
            }
        }

        // 生成合并后的歌词行：
        // 1) 首先尝试精确时间戳匹配（translationTimed 中未被使用的）
        // 2) 如果没有精确匹配，使用最近邻匹配（±500ms 容差内距离最小的未使用翻译行）
        // 3) 每次匹配成功后标记该行已使用，避免重复使用
        val tolerance = 500L

        for (orig in originalTimed) {
            // 输出原文行：使用规范化后的时间戳格式，确保 LyricsUtils.LRC_LINE_REGEX 能正确解析
            output.add("[${formatMsToLrcTimestamp(orig.timestampMs)}]${orig.text}")

            // 精确时间戳匹配（优先使用未被使用的行）
            val exactMatch = translationTimed
                .firstOrNull { !it.used && it.timestampMs == orig.timestampMs }
            if (exactMatch != null) {
                exactMatch.used = true
                output.add("[${formatMsToLrcTimestamp(orig.timestampMs)}]${exactMatch.text}")
                continue
            }

            // 最近邻匹配：在 ±500ms 范围内找距离最小的未使用翻译行
            val closestMatch = translationTimed
                .filter { !it.used && Math.abs(it.timestampMs - orig.timestampMs) <= tolerance }
                .minByOrNull { Math.abs(it.timestampMs - orig.timestampMs) }

            if (closestMatch != null) {
                closestMatch.used = true
                output.add("[${formatMsToLrcTimestamp(orig.timestampMs)}]${closestMatch.text}")
            }
        }

        return output.joinToString("\n")
    }

    private fun formatMsToLrcTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    /**
     * 简化版：尝试获取给定歌曲在网易云的歌词（已解析的纯 LRC 字符串）。
     * 仅当能从 song 中解析出数字 id 时有效。
     */
    suspend fun getLyricForSong(song: com.theveloper.pixelplay.data.model.Song): String? {
        val id = resolveNeteaseSongId(song) ?: return null
        return getLyric(id.toString())
    }

    /**
     * 从 song 中解析可能的网易云歌曲 id：
     * 1) song.neteaseId
     * 2) "netease://<id>" 格式 contentUri
     * 3) "cloud://lx/{json}" 里的 id 字段，若是纯数字
     * 4) 字符串 song.id 为纯数字
     */
    private fun resolveNeteaseSongId(
        song: com.theveloper.pixelplay.data.model.Song
    ): Long? {
        song.neteaseId?.let { if (it > 0L) return it }
        val uri = song.contentUriString
        if (uri.startsWith("netease://", ignoreCase = true)) {
            val hostPart = uri.removePrefix("netease://")
                .split('/')
                .firstOrNull()
                ?.toLongOrNull()
            if (hostPart != null && hostPart > 0L) return hostPart
        }
        if (uri.startsWith("cloud://lx/", ignoreCase = true)) {
            try {
                val tail = uri.removePrefix("cloud://lx/")
                val decoded = java.net.URLDecoder.decode(tail, "UTF-8")
                val jsonObj = JSONObject(decoded)
                val rawId = jsonObj.optString("id", "").trim()
                if (rawId.isNotBlank()) {
                    val n = rawId.toLongOrNull()
                    if (n != null && n > 0L) return n
                }
            } catch (_: Throwable) {
                // continue
            }
        }
        val fallback = song.id.toLongOrNull()
        if (fallback != null && fallback > 0L) return fallback
        return null
    }

    private fun parseSearchResponse(body: String, pageSize: Int = 20, offset: Int = 0): LxSearchResult {
        return try {
            val obj = JSONObject(body)
            val result = obj.optJSONObject("result") ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            val songs = result.optJSONArray("songs") ?: return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
            val total = result.optInt("songCount", songs.length())

            val list = mutableListOf<LxSongInfo>()
            for (i in 0 until songs.length()) {
                val songObj = songs.optJSONObject(i)
                if (songObj != null) {
                    list.add(parseSongInfo(songObj))
                }
            }

            // 已加载数量 >= 总数量，或本次返回为空，即为最后一页
            val isEnd = list.isEmpty() || (offset + list.size) >= total
            LxSearchResult(
                list = list,
                isEnd = isEnd,
                total = total
            )
        } catch (e: Exception) {
            Timber.e(e, "搜索结果解析失败")
            LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        }
    }

    /**
     * 网易云歌手搜索（type=100，本地 SDK 直连官方加密接口）。
     * 返回 JSON 中 result.artists 数组，每项包含 id / name / alias / picUrl。
     * 支持真正的 limit/offset 分页。
     */
    suspend fun searchArtists(keyword: String, page: Int = 1, pageSize: Int = 20): LxArtistSearchResult {
        if (keyword.isBlank()) {
            return LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
        }

        return withContext(Dispatchers.IO) {
            try {
                val offset = (page - 1) * pageSize
                val map = NcmApi.search(keyword, type = 100, limit = pageSize, offset = offset).getOrNull()
                if (map != null) {
                    val code = (map["code"] as? Number)?.toInt() ?: 200
                    if (code != 405) {
                        val root = ncmMapToJson(map)
                            ?: return@withContext LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
                        return@withContext parseArtistSearchResponse(root.toString(), pageSize, offset)
                    }
                    // code == 405（操作频繁/被风控）→ 走 btwoa 备用 API
                    Timber.w("内置 NCM 歌手搜索返回 405 操作频繁，切换 btwoa 备用 API 搜索: $keyword")
                } else {
                    Timber.w("内置 NCM 歌手搜索失败，切换 btwoa 备用 API 搜索: $keyword")
                }

                // 备用搜索 API：https://ncmapi.btwoa.com/cloudsearch（与官方 cloudsearch 返回格式一致）
                val url = "$BTWOA_API_BASE/cloudsearch?keywords=${URLEncoder.encode(keyword, "UTF-8")}" +
                        "&limit=$pageSize&offset=$offset&type=100"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string()
                response.close()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Timber.e("btwoa 备用歌手搜索失败: HTTP ${response.code}")
                    return@withContext LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
                }
                parseArtistSearchResponse(body, pageSize, offset)
            } catch (e: Exception) {
                Timber.e(e, "歌手搜索API请求异常")
                LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
        }
    }

    private fun parseArtistSearchResponse(body: String, pageSize: Int = 20, offset: Int = 0): LxArtistSearchResult {
        return try {
            val obj = JSONObject(body)
            if (obj.optInt("code", -1) != 200) {
                Timber.w("歌手搜索返回非成功 code: ${obj.optInt("code", -1)}")
                return LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
            }
            val result = obj.optJSONObject("result")
                ?: return LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
            val artists = result.optJSONArray("artists")
                ?: return LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
            val total = result.optInt("artistCount", artists.length())

            val list = mutableListOf<LxArtistInfo>()
            for (i in 0 until artists.length()) {
                val artistObj = artists.optJSONObject(i) ?: continue
                val id = artistObj.optLong("id", 0L)
                val name = artistObj.optString("name", "").trim()
                if (id <= 0L || name.isBlank()) continue

                // 别名（alias 数组），如 "周杰伦 / Jay Chou"
                val aliasArr = artistObj.optJSONArray("alias")
                val alias = if (aliasArr != null && aliasArr.length() > 0) {
                    buildString {
                        for (j in 0 until aliasArr.length()) {
                            val a = aliasArr.optString(j, "").trim()
                            if (a.isNotBlank()) {
                                if (isNotEmpty()) append(" / ")
                                append(a)
                            }
                        }
                    }
                } else {
                    ""
                }

                list.add(
                    LxArtistInfo(
                        id = id.toString(),
                        name = name,
                        alias = alias,
                        picUrl = artistObj.optString("picUrl", "").trim().replace("`", "")
                    )
                )
            }

            // 已加载数量 >= 总数量，或本次返回为空，即为最后一页
            val isEnd = list.isEmpty() || (offset + list.size) >= total
            LxArtistSearchResult(list = list, isEnd = isEnd, total = total)
        } catch (e: Exception) {
            Timber.e(e, "歌手搜索结果解析失败")
            LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
        }
    }

    private fun parseSongInfo(obj: JSONObject): LxSongInfo {
        val id = obj.optString("id", "")
        val name = obj.optString("name", "未知歌曲")

        // 兼容两种响应结构：web 端 artists/album（cloudsearch），移动端 ar/al（search/get 老接口）
        val artists = obj.optJSONArray("artists") ?: obj.optJSONArray("ar")
        val singer = if (artists != null) {
            buildString {
                for (i in 0 until artists.length()) {
                    val artistObj = artists.optJSONObject(i)
                    if (artistObj != null) {
                        if (isNotEmpty()) append("、")
                        append(artistObj.optString("name", ""))
                    }
                }
            }.ifBlank { "未知歌手" }
        } else {
            "未知歌手"
        }

        val album = obj.optJSONObject("album") ?: obj.optJSONObject("al")
        val albumName = album?.optString("name", "") ?: ""
        val pic = album?.optString("picUrl", "")?.trim()?.replace("`", "") ?: ""

        val duration = obj.optLong("duration", 0L).takeIf { it > 0L }
            ?: obj.optLong("dt", 0L)

        return LxSongInfo(
            id = id,
            songmid = id,
            hash = id,
            name = name,
            singer = singer,
            albumName = albumName,
            duration = duration,
            pic = pic
        )
    }

    /** 从 NcmApi（Map 结构）解析单条评论 */
    private fun parseCommentFromMap(obj: Map<*, *>): NeteaseComment {
        val m = obj as? Map<String, Any?> ?: return NeteaseComment()
        val userMap = m.ncmObj("user")
        val user = NeteaseCommentUser(
            userId = userMap.ncmLong("userId", 0L),
            nickname = userMap.ncmString("nickname"),
            avatarUrl = userMap.ncmString("avatarUrl")
        )

        return NeteaseComment(
            commentId = m.ncmLong("commentId", 0L),
            content = m.ncmString("content"),
            time = m.ncmLong("time", 0L),
            timeStr = m.ncmString("timeStr"),
            likedCount = m.ncmInt("likedCount", 0),
            liked = m.ncmBool("liked", false),
            user = user
        )
    }
}

/** 网易云歌曲详情信息（/api/v3/song/detail），用于补全不返回歌手/专辑字段的接口数据。 */
data class NeteaseSongDetailInfo(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val artistIds: List<Long>,
    val albumName: String,
    val albumPic: String,
    val duration: Long
)
