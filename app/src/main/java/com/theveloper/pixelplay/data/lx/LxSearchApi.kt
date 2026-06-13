package com.theveloper.pixelplay.data.lx

import kotlinx.coroutines.Dispatchers
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
    // 搜索 API：vkeys 的 netease 接口（一次返回歌曲 id、歌名、歌手、专辑、封面等）
    // https://api.vkeys.cn/v2/music/netease?word=关键字
    private val SEARCH_API_BASE = "https://api.vkeys.cn/v2/music/netease"

    // vkeys API 获取封面（搜索结果里已经自带 cover，本方法保留给单独需要获取封面的场景使用）
    private val COVER_API_BASE = "https://api.vkeys.cn/v2/music/netease"

    // 网易云评论 / 用户详情 API （由 btwoa 提供的接口）
    private val COMMENT_API_BASE = "https://ncmapi.btwoa.com"

    /**
     * 使用 vkeys 搜索接口，一次返回歌曲 id / 标题 / 歌手 / 专辑 / 封面等，
     * 不需要再单独请求封面，显著提升速度。
     *
     * ⚡ 新增分页支持: 使用 limit/offset 参数实现无限滚动加载更多。
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
                // ⚡ 使用 limit/offset 分页参数，避免一次请求全部结果
                val offset = (page - 1) * pageSize
                val url = "$SEARCH_API_BASE?word=$encodedKeyword&limit=$pageSize&offset=$offset"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.e("搜索API请求失败: ${response.code}")
                    return@withContext LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                }

                val body = response.body?.string()
                    ?: return@withContext LxSearchResult(list = emptyList(), isEnd = true, total = 0)
                parseVkeysSearchResponse(body, pageSize)
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
            val urlBuilder = StringBuilder("$COMMENT_API_BASE/comment/music")
                .append("?id=").append(songId)
                .append("&limit=").append(limit)
                .append("&offset=").append(offset)
            if (before != null && before > 0L) {
                urlBuilder.append("&before=").append(before)
            }
            val url = urlBuilder.toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("获取评论失败: ${response.code}")
                return@withContext NeteaseCommentResult()
            }

            val body = response.body?.string() ?: return@withContext NeteaseCommentResult()
            val rawObj = JSONObject(body)

            if (rawObj.optInt("code", -1) != 200) {
                Timber.e("评论接口返回错误: ${rawObj.optString("message")}")
                return@withContext NeteaseCommentResult()
            }

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
            val url = "$COMMENT_API_BASE/user/detail?uid=$uid"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("获取用户详情失败: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val obj = JSONObject(body)

            if (obj.optInt("code", -1) != 200) {
                Timber.e("用户详情接口返回错误: ${obj.optString("message")}")
                return@withContext null
            }

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
            val url = "$COMMENT_API_BASE/lyric?id=$songId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("获取歌词失败: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val obj = JSONObject(body)

            // 兼容两种结构：{code, lrc:{lyric:"..."}}；或外层带有 data:{...}
            val root = obj.optJSONObject("data") ?: obj

            val lrcObj = root.optJSONObject("lrc")
            val lrcText = lrcObj?.optString("lyric")?.takeIf { it.isNotBlank() }
            if (lrcText != null) return@withContext lrcText

            // 某些实现返回 klyric / tlyric（逐字歌词/翻译歌词），作为兜底
            val tlyric = root.optJSONObject("tlyric")?.optString("lyric").orEmpty()
            if (tlyric.isNotBlank()) return@withContext tlyric

            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "获取歌词异常: $songId")
            null
        }
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

    private fun parseSearchResponse(body: String): LxSearchResult {
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

            LxSearchResult(
                list = list,
                isEnd = list.size < total,
                total = total
            )
        } catch (e: Exception) {
            Timber.e(e, "搜索结果解析失败")
            LxSearchResult(list = emptyList(), isEnd = true, total = 0)
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

        val duration = obj.optLong("duration", 0L)

        return LxSongInfo(
            id = id,
            songmid = id,
            hash = id,
            name = name,
            singer = singer,
            albumName = albumName,
            duration = duration,
            pic = ""   // 初始为空，后续由 viewModel 调用 vkeys API 填充
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
