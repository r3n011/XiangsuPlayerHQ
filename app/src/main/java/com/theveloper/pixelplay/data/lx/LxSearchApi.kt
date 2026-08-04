package com.theveloper.pixelplay.data.lx

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
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

    // 网易云 API 多代理列表：请求时并行竞速，返回第一个数据正确的结果，
    // 单个代理超时/失效不会导致整体失败
    private val NCM_PROXIES = listOf(
        "https://ncmapi.btwoa.com",
        "http://www.young1024.com:666",
        "https://zm.wwoyun.cn",
        "https://music.mcseekeri.com"
    )

    /**
     * 多代理竞速请求：并行请求所有网易云 API 代理，
     * 返回第一个 HTTP 成功且 code==200 的 JSONObject；全部失败返回 null。
     */
    private suspend fun raceGetJson(
        tag: String,
        buildUrl: (String) -> String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val deferreds = NCM_PROXIES.map { base ->
            async {
                try {
                    val url = buildUrl(base)
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.w("$tag HTTP ${response.code} @ $base")
                            return@use null
                        }
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) return@use null
                        val root = JSONObject(body)
                        if (root.optInt("code", -1) != 200) null else root
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.w(t, "$tag 请求异常 @ $base")
                    null
                }
            }
        }
        var remaining = deferreds
        while (remaining.isNotEmpty()) {
            val result = select<JSONObject?> { remaining.forEach { it.onAwait { value -> value } } }
            if (result != null) return@withContext result
            remaining = remaining.filterNot { it.isCompleted }
        }
        Timber.w("$tag 所有代理请求均失败")
        null
    }

    /**
     * 使用 btwoa 提供的 NeteaseCloudMusicApi 搜索接口，支持真正的 limit/offset 分页。
     * - limit: 单页返回数量，默认 20
     * - offset: 偏移量，从 0 开始，例如第 2 页 offset = limit
     */
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): LxSearchResult {
        if (keyword.isBlank()) {
            return LxSearchResult(list = emptyList(), isEnd = true, total = 0)
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val offset = (page - 1) * pageSize
                val root = raceGetJson(tag = "search") { base ->
                    "$base/search?keywords=$encodedKeyword&type=1&limit=$pageSize&offset=$offset"
                } ?: return@withContext LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                parseSearchResponse(root.toString(), pageSize, offset)
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
     * 调用网易云歌曲详情 API 获取封面链接。
     * 接口：`https://ncmapi.btwoa.com/song/detail?ids=<songId>`
     * 返回 JSON 中 songs[0].al.picUrl 即为封面直链。
     * @param songId 网易云歌曲 ID（纯数字字符串）
     * @return 封面 URL，获取失败返回 null
     */
    suspend fun getSongCoverFromDetail(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        try {
            val obj = raceGetJson(tag = "song/detail-cover") { base ->
                "$base/song/detail?ids=$songId"
            } ?: return@withContext null

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
        if (songIds.isEmpty()) return@withContext emptyMap()
        try {
            val idsParam = songIds.joinToString(",")
            val obj = raceGetJson(tag = "song/detail-batch") { base ->
                "$base/song/detail?ids=$idsParam"
            } ?: return@withContext emptyMap()

            val songs = obj.optJSONArray("songs") ?: return@withContext emptyMap()
            val result = HashMap<String, String>(songs.length())
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = song.optLong("id").toString()
                val al = song.optJSONObject("al") ?: song.optJSONObject("album")
                val picUrl = al?.optString("picUrl", "")?.trim()?.replace("`", "")
                if (id.isNotBlank() && !picUrl.isNullOrBlank()) {
                    result[id] = picUrl
                }
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "批量获取封面异常")
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
            val beforeParam = if (before != null && before > 0L) "&before=$before" else ""
            val rawObj = raceGetJson(tag = "comment/music") { base ->
                "$base/comment/music?id=$songId&limit=$limit&offset=$offset$beforeParam"
            } ?: return@withContext NeteaseCommentResult()

            // 某些封装后端会把数据放在 data 字段里；如果存在则从 data 中读取，否则直接从根读取
            val dataWrapper = rawObj.optJSONObject("data")
            val obj = dataWrapper ?: rawObj

            // 解析热门评论
            val hotComments = mutableListOf<NeteaseComment>()
            val hotCommentsArr = obj.optJSONArray("hotComments")
            if (hotCommentsArr != null) {
                for (i in 0 until hotCommentsArr.length()) {
                    val c = hotCommentsArr.optJSONObject(i) ?: continue
                    hotComments.add(parseComment(c))
                }
            }

            // 解析普通评论
            val comments = mutableListOf<NeteaseComment>()
            val commentsArr = obj.optJSONArray("comments")
            if (commentsArr != null) {
                for (i in 0 until commentsArr.length()) {
                    val c = commentsArr.optJSONObject(i) ?: continue
                    comments.add(parseComment(c))
                }
            }

            // hasMore 默认为 true 以便分页继续加载；只有服务器明确返回 false 时才停止
            val rawHasMore = obj.opt("more") ?: obj.opt("hasMore")
            val hasMore = when (rawHasMore) {
                is Boolean -> rawHasMore
                is Number -> rawHasMore.toInt() == 1
                is String -> rawHasMore.equals("true", ignoreCase = true) || rawHasMore == "1"
                else -> comments.isNotEmpty()
            }

            // 游标：最后一条评论的 time（用于超过 5000 条时的 before 参数）
            val cursor = if (comments.isNotEmpty()) {
                comments.last().time
            } else {
                obj.optLong("time", 0L)
            }

            NeteaseCommentResult(
                comments = comments,
                hotComments = hotComments,
                hasMore = hasMore,
                totalCount = obj.optInt("totalCount", 0),
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
            val obj = raceGetJson(tag = "user/detail") { base ->
                "$base/user/detail?uid=$uid"
            } ?: return@withContext null

            val data = obj.optJSONObject("data") ?: return@withContext null
            val profile = data.optJSONObject("profile") ?: return@withContext null

            NeteaseUserDetail(
                userId = profile.optLong("userId", uid),
                nickname = profile.optString("nickname", ""),
                avatarUrl = profile.optString("avatarUrl", ""),
                signature = profile.optString("signature", ""),
                description = profile.optString("description", "")
            )
        } catch (e: Exception) {
            Timber.e(e, "获取用户详情异常")
            null
        }
    }

    // ─── 歌词 ────────────────────────────────────────────────────────

    /**
     * 通过网易云歌曲 id 获取 LRC 歌词。
     * 接口：`https://ncmapi.btwoa.com/lyric?id=<songId>`
     * 返回：LRC 原文（包含时间戳），如果没有则返回 null。
     */
    suspend fun getLyric(songId: String): String? = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext null
        try {
            val obj = raceGetJson(tag = "lyric") { base ->
                "$base/lyric?id=$songId"
            } ?: return@withContext null

            // 兼容两种结构：{code, lrc:{lyric:"..."}}；或外层带有 data:{...}
            val root = obj.optJSONObject("data") ?: obj

            val lrcObj = root.optJSONObject("lrc")
            val lrcText = lrcObj?.optString("lyric")?.takeIf { it.isNotBlank() }

            // 同时获取翻译歌词
            val tlyricObj = root.optJSONObject("tlyric")
            val tlyricText = tlyricObj?.optString("lyric")?.takeIf { it.isNotBlank() }

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
            val klyric = root.optJSONObject("klyric")?.optString("lyric").orEmpty()
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
     * 网易云歌手搜索（type=100）。
     * 接口：`https://ncmapi.btwoa.com/search?keywords=<关键字>&type=100&limit=20&offset=0`
     * 返回 JSON 中 result.artists 数组，每项包含 id / name / alias / picUrl。
     * 支持真正的 limit/offset 分页。
     */
    suspend fun searchArtists(keyword: String, page: Int = 1, pageSize: Int = 20): LxArtistSearchResult {
        if (keyword.isBlank()) {
            return LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val offset = (page - 1) * pageSize
                val root = raceGetJson(tag = "search-artist") { base ->
                    "$base/search?keywords=$encodedKeyword&type=100&limit=$pageSize&offset=$offset"
                } ?: return@withContext LxArtistSearchResult(list = emptyList(), isEnd = true, total = 0)
                parseArtistSearchResponse(root.toString(), pageSize, offset)
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

        val artists = obj.optJSONArray("artists")
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

        val album = obj.optJSONObject("album")
        val albumName = album?.optString("name", "") ?: ""
        val pic = album?.optString("picUrl", "")?.trim()?.replace("`", "") ?: ""

        val duration = obj.optLong("duration", 0L)

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

    private fun parseComment(obj: JSONObject): NeteaseComment {
        val userObj = obj.optJSONObject("user")
        val user = NeteaseCommentUser(
            userId = userObj?.optLong("userId", 0L) ?: 0L,
            nickname = userObj?.optString("nickname", "") ?: "",
            avatarUrl = userObj?.optString("avatarUrl", "") ?: ""
        )

        return NeteaseComment(
            commentId = obj.optLong("commentId", 0L),
            content = obj.optString("content", ""),
            time = obj.optLong("time", 0L),
            timeStr = obj.optString("timeStr", ""),
            likedCount = obj.optInt("likedCount", 0),
            liked = obj.optBoolean("liked", false),
            user = user
        )
    }
}
