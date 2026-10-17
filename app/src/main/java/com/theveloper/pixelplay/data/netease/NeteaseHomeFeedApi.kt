package com.theveloper.pixelplay.data.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.NcmJson
import net.moriafly.ncm.NcmSession
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网易云**主页推荐**接口（对齐 lx-music 的请求方式）。
 *
 * 与既有 `PersonalFmApi` / `NeteaseRecommendApi` 相同的约定：直连本地 NcmApi SDK
 * 的加密接口，不经过任何外部代理。
 *
 * ### 传输方式为什么统一用 eapi
 * 参照实现（lx-music）对主页链路是这样分工的：
 *  - `homepage/block/page` / `toplist` / `radio/get` / `user/playlist` / `song/like/get`：**eapi**
 *  - `personalized/newsong` / `program/recommend` / `v1/discovery/new/songs`：登录走 weapi，匿名走 eapi
 *
 * 本类**统一走 eapi**（`NcmApi.rawEapi`）：eapi 的 `header` 里会带 `MUSIC_U`，
 * 登录/匿名两种状态都能用，省掉「weapi 失败再降级」的分支（`NcmRequest` 的 eapi 已内置
 * 匿名 `MUSIC_A` 兜底）。实测 `homepage/block/page` 在 eapi 下可正常返回。
 *
 * ### 本类只做「请求 + 解析」，不做兜底编排与缓存
 * 编排在 [HomeFeedRepository]。
 */
@Singleton
class NeteaseHomeFeedApi @Inject constructor() {

    companion object {
        private const val TAG = "NeteaseHomeFeed"

        /** 主页 server blocks（**必须登录**才有个性化内容） */
        const val PATH_HOME_BLOCK = "/api/homepage/block/page"

        /** 为你推荐 / 个性化新歌 */
        const val PATH_PERSONALIZED_NEW_SONG = "/api/personalized/newsong"

        /** 今日热门（按地区，areaId=0 为「全部」） */
        const val PATH_DISCOVERY_NEW_SONGS = "/api/v1/discovery/new/songs"

        /** 网易云地区 id：全部 / 华语 / 日语 / 韩语 / 欧美 */
        fun areaId(area: String): Int = when (area) {
            "华语", "中国" -> 7
            "日本", "日语" -> 8
            "韩国", "韩语" -> 16
            "欧美" -> 96
            else -> 0
        }
    }

    // ─── 请求 ────────────────────────────────────────────────────────────

    /**
     * 主页 server blocks。
     *
     * 参数与参照实现保持一致：只发 `refresh`（**不带** `pageId`/`cursor`/`moduleInfo`，
     * 那几个字段会让部分账号返回空 blocks）。
     */
    suspend fun fetchHomeBlocks(refresh: Boolean, cookie: String?): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                syncCookieToSession(cookie)
                val params = mapOf<String, Any?>("refresh" to refresh)
                NcmApi.full.rawEapi(PATH_HOME_BLOCK, params).getOrThrow()
                    .let { map -> toJsonObject(map) ?: throw IllegalStateException("主页 blocks 响应解析失败") }
            }.onFailure { Timber.w(it, "$TAG: fetchHomeBlocks failed") }
        }

    /**
     * 为你推荐（个性化新歌）。`type="recommend"` 与参照实现一致。
     *
     * @param areaId 见 [areaId]；0 = 全部
     */
    suspend fun fetchPersonalizedNewSongs(
        areaId: Int,
        limit: Int,
        cookie: String?,
    ): Result<List<HomeFeedTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            syncCookieToSession(cookie)
            val params = mapOf<String, Any?>(
                "type" to "recommend",
                "limit" to limit,
                "areaId" to areaId,
            )
            val root = toJsonObject(NcmApi.full.rawEapi(PATH_PERSONALIZED_NEW_SONG, params).getOrThrow())
                ?: throw IllegalStateException("个性化新歌响应解析失败")
            checkCode(root, "personalized/newsong")

            // 响应形如 { result: [ { id, name, song: {…} | 裸歌曲对象, … } ] }
            val result = root.optJSONArray("result") ?: return@runCatching emptyList()
            (0 until result.length()).mapNotNull { index ->
                val item = result.optJSONObject(index) ?: return@mapNotNull null
                // 优先取嵌套的 song 字段；没有就直接当歌曲对象解析
                parseSongObject(item.optJSONObject("song") ?: item)
            }
        }.onFailure { Timber.w(it, "$TAG: fetchPersonalizedNewSongs failed") }
    }

    /**
     * 今日热门（地区榜）。参照实现**不发 `limit`**，只切片响应，这里保持一致。
     */
    suspend fun fetchDiscoveryNewSongs(
        areaId: Int,
        limit: Int,
        cookie: String?,
    ): Result<List<HomeFeedTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            syncCookieToSession(cookie)
            val params = mapOf<String, Any?>(
                "areaId" to areaId,
                "total" to true,
            )
            val root = toJsonObject(NcmApi.full.rawEapi(PATH_DISCOVERY_NEW_SONGS, params).getOrThrow())
                ?: throw IllegalStateException("今日热门响应解析失败")
            checkCode(root, "discovery/new/songs")

            val data = root.optJSONArray("data") ?: return@runCatching emptyList()
            (0 until data.length())
                .mapNotNull { index -> parseSongObject(data.optJSONObject(index)) }
                .take(limit.coerceIn(1, 100))
        }.onFailure { Timber.w(it, "$TAG: fetchDiscoveryNewSongs failed") }
    }

    // ─── server block 解析 ───────────────────────────────────────────────

    /**
     * 把 `homepage/block/page` 的响应解析成「带歌曲的栏目」列表。
     *
     * 映射规则照抄参照实现（`NeteaseHomeBlockParser`）：
     * 标题先**去掉所有空白字符**再匹配；同一个栏目**第一个命中的 block 生效**。
     */
    fun parseHomeBlocks(root: JSONObject): List<HomeBlockSection> {
        val blocks = root.optJSONObject("data")?.optJSONArray("blocks") ?: return emptyList()
        val out = mutableListOf<HomeBlockSection>()
        val seenKinds = mutableSetOf<HomeBlockKind>()

        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            val code = block.optString("blockCode", "").uppercase()
            val title = normalizedTitle(block)
            val resources = collectResources(block)

            val tracks = resources.mapNotNull { parseSongObject(it) }.distinctBy { it.id }
            if (tracks.isEmpty()) continue

            val kind = classifyBlock(code, title)
            // 第一个命中的同类型 block 生效，与参照实现一致
            if (!seenKinds.add(kind)) continue

            out += HomeBlockSection(kind = kind, title = title, tracks = tracks)
        }
        return out
    }

    private fun classifyBlock(code: String, title: String): HomeBlockKind = when {
        title.contains("近期云村热播") || code == "HOMEPAGE_BLOCK_STYLE_RCMD" ->
            HomeBlockKind.RECENTLY_TRENDING

        title.contains("最近的热门歌曲") -> HomeBlockKind.REGIONAL_SONGS
        title.startsWith("根据") && title.endsWith("为你推荐") -> HomeBlockKind.TAILORED
        title.contains("从你喜欢的歌开始漫游") -> HomeBlockKind.ROAMING
        title.contains("根据你喜爱的歌曲推荐") -> HomeBlockKind.SIMILAR
        else -> HomeBlockKind.UNKNOWN
    }

    /** `uiElement.subTitle.title` → `uiElement.mainTitle.title` → 首个 creative 的主标题。 */
    private fun normalizedTitle(block: JSONObject): String {
        val ui = block.optJSONObject("uiElement")
        val raw = ui?.optJSONObject("subTitle")?.optString("title").orEmpty()
            .ifBlank { ui?.optJSONObject("mainTitle")?.optString("title").orEmpty() }
            .ifBlank {
                block.optJSONArray("creatives")
                    ?.optJSONObject(0)
                    ?.optJSONObject("uiElement")
                    ?.optJSONObject("mainTitle")
                    ?.optString("title")
                    .orEmpty()
            }
        // 参照实现在匹配前去掉所有空白字符
        return raw.filterNot { it.isWhitespace() }
    }

    /** 直连 `resources[]` **和** 每个 `creatives[].resources[]` 都要读。 */
    private fun collectResources(block: JSONObject): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        block.optJSONArray("resources")?.let { array ->
            for (i in 0 until array.length()) out += array.optJSONObject(i) ?: continue
        }
        block.optJSONArray("creatives")?.let { creatives ->
            for (c in 0 until creatives.length()) {
                val resources = creatives.optJSONObject(c)?.optJSONArray("resources") ?: continue
                for (i in 0 until resources.length()) out += resources.optJSONObject(i) ?: continue
            }
        }
        return out
    }

    // ─── 歌曲解析 ────────────────────────────────────────────────────────

    /**
     * 解析一个 resource / 裸歌曲对象。
     *
     * 兼容两种形态（照抄参照实现）：
     *  1. `resourceExtInfo.songData` / `resourceExtInfo.song` 里是完整歌曲对象；
     *  2. `resourceType == "song"`，歌曲信息在 `uiElement` + `resourceExtInfo` 里。
     */
    private fun parseSongObject(raw: JSONObject?): HomeFeedTrack? {
        if (raw == null) return null

        val ext = raw.optJSONObject("resourceExtInfo")
        val full = ext?.optJSONObject("songData") ?: ext?.optJSONObject("song")
        if (full != null) return parseFullSong(full)

        if (!raw.optString("resourceType").equals("song", ignoreCase = true)) return null

        val id = longValue(raw, "resourceId") ?: return null
        val ui = raw.optJSONObject("uiElement")
        val artists = buildList {
            val arr = ext?.optJSONArray("artists")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        return HomeFeedTrack(
            id = id,
            title = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "未知歌曲" },
            artist = artists.joinToString(", ").ifBlank { "未知歌手" },
            artworkUrl = normalizeUrl(ui?.optJSONObject("image")?.optString("imageUrl").orEmpty()),
            durationMs = 0L,
            addedAtMs = ext?.optLong("publishTime", 0L) ?: 0L,
        )
    }

    /** 完整歌曲对象（`ar[]` / `al` / `dt` / `publishTime` 结构）。 */
    private fun parseFullSong(song: JSONObject): HomeFeedTrack? {
        val id = song.optLong("id", 0L)
        if (id <= 0L) return null

        val artistArr = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        val names = mutableListOf<String>()
        val ids = mutableListOf<Long>()
        if (artistArr != null) {
            for (i in 0 until artistArr.length()) {
                val ar = artistArr.optJSONObject(i) ?: continue
                ar.optString("name").takeIf { it.isNotBlank() }?.let { names += it }
                val aid = ar.optLong("id", 0L)
                if (aid > 0L) ids += aid
            }
        }

        val album = song.optJSONObject("al") ?: song.optJSONObject("album")
        val pic = album?.optString("picUrl").orEmpty()
            .ifBlank { album?.optString("blurPicUrl").orEmpty() }

        // 优先取嵌套的 recommend reason（个性化推荐会带）
        val reason = song.optString("reason").takeIf { it.isNotBlank() }

        return HomeFeedTrack(
            id = id,
            title = song.optString("name").takeIf { it.isNotBlank() } ?: "未知歌曲",
            artist = names.joinToString(", ").ifBlank { "未知歌手" },
            artistIds = ids,
            albumName = album?.optString("name").orEmpty(),
            albumId = album?.optLong("id", -1L) ?: -1L,
            artworkUrl = normalizeUrl(pic),
            durationMs = song.optLong("dt", 0L).takeIf { it > 0L } ?: song.optLong("duration", 0L),
            reason = reason,
            addedAtMs = song.optLong("publishTime", 0L),
            fee = song.optInt("fee", 0),
        )
    }

    // ─── 工具 ────────────────────────────────────────────────────────────

    /** `rawEapi` 失败体（如 404）也会返回成功，必须显式校验 code。 */
    private fun checkCode(root: JSONObject?, what: String) {
        if (root == null) throw IllegalStateException("$what 响应为空")
        val code = root.optInt("code", -1)
        if (code != 200) {
            throw IllegalStateException("$what 返回 code=$code ${root.optString("message")}")
        }
    }

    private fun longValue(container: JSONObject, key: String): Long? {
        val value = container.opt(key) ?: return null
        val parsed = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        return parsed?.takeIf { it > 0L }
    }

    /** 网易云返回的图片 URL 常为协议相对（`//p2.music.126.net/...`）或明文 http，统一补 https。 */
    private fun normalizeUrl(raw: String): String? {
        val cleaned = raw.trim().replace("`", "")
        return when {
            cleaned.isEmpty() -> null
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("http://") -> "https:" + cleaned.removePrefix("http:")
            cleaned.startsWith("/") -> "https://music.163.com$cleaned"
            else -> cleaned
        }
    }

    private fun toJsonObject(map: Map<String, Any?>?): JSONObject? {
        if (map == null) return null
        return try {
            JSONObject(NcmJson.toJsonString(map))
        } catch (t: Throwable) {
            Timber.w(t, "$TAG: NcmApi 响应转 JSON 失败")
            null
        }
    }

    /** 将外部 cookie 合并进本地 NcmApi 会话，保证登录态一致。 */
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
}
