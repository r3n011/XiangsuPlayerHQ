@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection")

package net.moriafly.ncm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NCM 全量模块内核（按 JS 项目 /module 下的 *.js 一比一移植）
 *
 * 【设计思路】
 * 由于 NeteaseCloudMusicApi 有 350+ 模块，结构 95% 完全一致：
 *   输入 params(Map) → 加密(weapi/eapi/linuxapi) → POST /api/xxx → 返回 JSON
 * 所以不用每个模块都单独写 10 行，本文件采用：
 *   - 上层：按业务分类写明确的 suspend fun（IDE 自动补全、类型友好）
 *   - 底层：统一走 NcmRequest.weapi/eapi/linuxapi 3 个调度器
 *
 * 分类：
 *   §1 搜索补充（search_suggest/hot/multimatch/cloudsearch）
 *   §2 歌曲补充（lyric/download_url/chorus/simi_song/playmode/榜单系列）
 *   §3 歌手（artist_*）
 *   §4 专辑（album_*）
 *   §5 歌单（playlist_*，包括创建/增删/收藏/标签/隐私）
 *   §6 评论（comment_*，全部 9 类资源 + like/floor/hot/hug/new）
 *   §7 用户（user_*，资料/歌单/粉丝/follow/云盘/绑定）
 *   §8 电台 DJ（dj_*，含分类/榜单/节目/订阅）
 *   §9 MV & 视频（mv_* / video_* / mlog_*）
 *   §10 个性化推荐（personalized_* / fm_*）
 *   §11 每日推荐 / 历史 / 听歌数据（recommend / history / listen_data_*）
 *   §12 云盘（cloud_*）
 *   §13 签到 / VIP / 云贝 / 等级（daily_signin / vip_* / yunbei_*）
 *   §14 一起听（listentogether_*）
 *   §15 风格（style_*）
 *   §16 私信/消息（msg_* / send_* / topic_*）
 *   §17 动态 / 粉丝中心（event_* / fanscenter_* / musician_*）
 *   §18 歌词上传 / 听歌打卡（scrobble）/ 相似推荐
 *   §19 misc（国家码/日历/版本/banner/ugc/voice/verify）
 */
object NcmModulesFull {

    // ============================================================================================
    // 通用底层（任何未列出的 JS module 都能秒接）
    // ============================================================================================

    /**
     * 任意 weapi 调用（兜底，任何新 module 没写函数的都可以用这个）
     * 例：`rawWeapi("/api/song/lyric", mapOf("id" to "4876940", "lv" to -1))`
     */
    suspend fun rawWeapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching { NcmRequest.weapi(path, params, realIp, url).getOrThrow().body }
    }

    suspend fun rawEapi(
        path: String,
        params: Map<String, Any?>,
        realIp: String? = null,
        url: String? = null,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching { NcmRequest.eapi(path, params, realIp, url).getOrThrow().body }
    }

    suspend fun rawLinuxapi(
        path: String,
        params: Map<String, Any?>,
        url: String? = null,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching { NcmRequest.linuxapi(path, params, url).getOrThrow().body }
    }

    // ============================================================================================
    // §1 搜索补充
    // ============================================================================================

    suspend fun searchHot() = rawWeapi("/api/search/hot", mapOf("type" to 1111))
    suspend fun searchHotDetail() = rawWeapi("/api/search/hot/detail", emptyMap())
    suspend fun searchSuggest(keywords: String, type: String? = "mobile") = rawWeapi(
        "/api/search/suggest/web",
        mapOf("s" to keywords) + if (type != null) mapOf("type" to type) else emptyMap()
    )
    suspend fun searchMultimatch(keywords: String, limit: Int = 5) = rawWeapi(
        "/api/search/multimatch", mapOf("s" to keywords, "limit" to limit.toString())
    )
    suspend fun searchMatch(keywords: String) = rawWeapi(
        "/api/search/match", mapOf("s" to keywords)
    )

    /** 云盘搜索（cloudsearch.js） */
    suspend fun cloudSearch(
        keywords: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ) = rawWeapi(
        "/api/cloudsearch/pc",
        mapOf("s" to keywords, "type" to type, "limit" to limit, "offset" to offset, "total" to true)
    )

    // ============================================================================================
    // §2 歌曲补充
    // ============================================================================================

    suspend fun lyricNew(id: String, cp: Boolean = false, tv: Int = -1, lv: Int = -1, rv: Int = -1, kv: Int = -1, yv: Int = -1, ytv: Int = -1, yrv: Int = -1) = rawWeapi(
        "/api/song/lyric?_nmclfl=1",
        mapOf("id" to id, "cp" to cp, "tv" to tv, "lv" to lv, "rv" to rv, "kv" to kv, "yv" to yv, "ytv" to ytv, "yrv" to yrv)
    )

    // 对齐原版 module/lyric.js：body 带 _nmclfl:1
    suspend fun songLyric(id: String, lv: Int = -1, tv: Int = -1, rv: Int = -1, kv: Int = -1) = rawWeapi(
        "/api/song/lyric",
        mapOf("id" to id, "lv" to lv, "tv" to tv, "rv" to rv, "kv" to kv, "_nmclfl" to 1)
    )

    suspend fun songDownloadUrl(id: String, br: Int = 320_000) = rawWeapi(
        "/api/song/enhance/download/url",
        mapOf("id" to id, "br" to br)
    )
    suspend fun songDownloadUrlV1(ids: List<String>, level: String = "standard", encodeType: String = "flac") = rawWeapi(
        "/api/song/enhance/download/url/v1",
        mapOf(
            "ids" to ids.joinToString(",", "[", "]") { "\"$it\"" },
            "level" to level,
            "encodeType" to encodeType,
            "immerseType" to (if (encodeType == "flac") "1" else "0"),
        )
    )

    /** 副歌标记（song_chorus.js） */
    suspend fun songChorus(ids: List<String>) = rawWeapi(
        "/api/chorus/get",
        mapOf("ids" to ids.joinToString(","))
    )

    suspend fun songPurchased(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/song/purchased",
        mapOf("limit" to limit, "offset" to offset)
    )

    suspend fun songDownlist(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/downlist/song",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun songMonthDownlist(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/statistic/download/month",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun songSingleDownlist(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/statistic/download/single",
        mapOf("limit" to limit, "offset" to offset)
    )

    suspend fun songMusicDetail(ids: List<String>) = rawWeapi(
        "/api/song/music/detail/v2",
        mapOf("c" to ids.joinToString(",", "[", "]") { "{\"id\":$it}" })
    )

    suspend fun songLikeCheck(id: String) = rawWeapi(
        "/api/song/like/check", mapOf("id" to id)
    )

    suspend fun songUrl(ids: List<String>, br: Int = 320_000) = rawWeapi(
        "/api/song/player/url",
        mapOf("ids" to ids.joinToString(","), "br" to br)
    )

    suspend fun songUrlNcmget(id: String, br: Int = 320_000) = rawWeapi(
        "/api/song/ncm/get",
        mapOf("id" to id, "br" to br)
    )

    suspend fun songUrlMatch(id: String, br: Int = 320_000) = rawWeapi(
        "/api/song/url/match",
        mapOf("id" to id, "br" to br)
    )

    suspend fun songRedCount() = rawWeapi("/api/point/get", emptyMap())

    suspend fun songDynamicCover(ids: List<String>) = rawWeapi(
        "/api/song/dynamic/cover",
        mapOf("songIds" to ids.joinToString(","))
    )

    suspend fun songWikiSummary(id: String) = rawWeapi(
        "/api/rep/song/wiki/summary", mapOf("songId" to id)
    )

    /** 听歌打卡（scrobble.js —— 播完一首歌提交） */
    suspend fun scrobble(
        id: String,
        sourceid: String = "0",
        time: Long = 240_000L,
    ) = rawWeapi(
        "/api/feedback/weblog",
        mapOf(
            "logs" to NcmJson.toJsonString(
                listOf(
                    mapOf<String, Any?>(
                        "action" to "play",
                        "json" to mapOf<String, Any?>(
                            "download" to 0,
                            "end" to "playcomplete",
                            "id" to (id.toLongOrNull() ?: id),
                            "metadata" to "{\"source\":\"$sourceid\"}",
                            "mobileType" to 1,
                            "sourceId" to sourceid,
                            "type" to "song",
                            "wifi" to 0,
                            "time" to time,
                        )
                    )
                )
            )
        )
    )

    /** 相似推荐：歌曲/歌单/歌手/MV/用户 */
    suspend fun simiSong(id: String, limit: Int = 50, offset: Int = 0) = rawWeapi(
        "/api/discovery/simiSong",
        mapOf("songid" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun simiPlaylist(id: String, limit: Int = 50, offset: Int = 0) = rawWeapi(
        "/api/discovery/simiPlaylist",
        mapOf("id" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun simiArtist(id: String, limit: Int = 50) = rawWeapi(
        "/api/discovery/simiArtist",
        mapOf("artistid" to id, "limit" to limit)
    )
    suspend fun simiMv(mvid: String, limit: Int = 50) = rawWeapi(
        "/api/discovery/simiMV",
        mapOf("mvid" to mvid, "limit" to limit)
    )
    suspend fun simiUser(uid: String, limit: Int = 50) = rawWeapi(
        "/api/v2/user/similar",
        mapOf("uid" to uid, "limit" to limit)
    )

    // 榜单
    suspend fun toplist() = rawWeapi("/api/toplist", emptyMap())
    suspend fun toplistDetail(id: String) = rawWeapi("/api/toplist/detail", mapOf("id" to id))
    suspend fun toplistDetailV2(id: String, limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/top/list",
        mapOf("id" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun toplistArtist() = rawWeapi("/api/toplist/artist", mapOf("type" to "1", "limit" to 100, "offset" to 0))
    suspend fun topList() = rawWeapi("/api/toplist", emptyMap())
    suspend fun topSong(type: Int = 0) = rawWeapi(
        "/api/personalized/newsong",
        mapOf("type" to type)
    )
    suspend fun topArtists(limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/artist/top",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun topPlaylist(
        cat: String = "全部",
        order: String = "hot",
        limit: Int = 50,
        offset: Int = 0,
    ) = rawWeapi(
        "/api/top/playlist",
        mapOf("cat" to cat, "order" to order, "limit" to limit, "offset" to offset)
    )
    suspend fun topPlaylistHighQuality(
        cat: String = "全部",
        limit: Int = 50,
        before: Long = 0,
    ) = rawWeapi(
        "/api/top/playlist/highquality",
        mapOf("cat" to cat, "limit" to limit, "before" to before)
    )
    suspend fun topAlbum(area: String = "ALL", limit: Int = 50, offset: Int = 0, type: String = "new") = rawWeapi(
        "/api/top/album",
        mapOf("area" to area, "limit" to limit, "offset" to offset, "type" to type)
    )
    suspend fun topMv(area: String = "", type: String = "0", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/mv/top/all",
        mapOf("area" to area, "type" to type, "limit" to limit, "offset" to offset)
    )
    suspend fun starPickCommentsSummary(id: String) = rawWeapi(
        "/api/comment/starpick/summary",
        mapOf("threadId" to id)
    )

    // ============================================================================================
    // §3 歌手（artist_*）
    // ============================================================================================

    suspend fun artistDetail(id: String) = rawWeapi(
        "/api/artist/head/info/get",
        mapOf("id" to id)
    )
    suspend fun artistDetailDynamic(id: String) = rawWeapi(
        "/api/artist/detail/dynamic",
        mapOf("id" to id)
    )
    suspend fun artistDesc(id: String, offset: Int = 0, limit: Int = 60) = rawWeapi(
        "/api/artist/introduction",
        mapOf("id" to id, "offset" to offset, "limit" to limit)
    )
    suspend fun artistAlbum(id: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/albums/$id",
        mapOf("offset" to offset, "limit" to limit, "total" to true)
    )
    suspend fun artists(area: Int = -1, type: Int = -1, initial: String = "-1", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/v1/artist/list",
        mapOf("area" to area, "type" to type, "initial" to initial, "limit" to limit, "offset" to offset)
    )
    suspend fun artistList(area: Int = -1, type: Int = -1, initial: String = "-1", limit: Int = 30, offset: Int = 0) = artists(area, type, initial, limit, offset)
    suspend fun artistTopSong(id: String) = rawWeapi(
        "/api/artist/top/song",
        mapOf("id" to id)
    )
    suspend fun artistSublist(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/sublist",
        mapOf("limit" to limit, "offset" to offset, "total" to true)
    )
    suspend fun artistSub(id: String, t: Int = 1) = rawWeapi(
        "/api/artist/sub",
        mapOf("artistId" to id, "artistIds" to "[$id]", "t" to t)
    )
    suspend fun artistSongs(id: String, order: String = "hot", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/v1/artist/songs",
        mapOf("id" to id, "order" to order, "limit" to limit, "offset" to offset)
    )
    suspend fun artistNewSong(limit: Int = 30, area: Int = 97, type: Int = 0) = rawWeapi(
        "/api/discovery/new/artistH5",
        mapOf("limit" to limit, "area" to area, "type" to type)
    )
    suspend fun artistNewMv(artistId: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/mv",
        mapOf("artistId" to artistId, "limit" to limit, "offset" to offset)
    )
    suspend fun artistMv(id: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/mv/list",
        mapOf("artistId" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun artistFollowCount(id: String) = rawWeapi(
        "/api/user/followeds/$id",
        mapOf("time" to System.currentTimeMillis())
    )
    suspend fun artistFans(id: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/fans",
        mapOf("id" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun artistVideo(id: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/artist/mlog",
        mapOf("artistId" to id, "limit" to limit, "offset" to offset, "total" to true)
    )

    // ============================================================================================
    // §4 专辑
    // ============================================================================================

    suspend fun album(id: String) = rawWeapi("/api/v1/album/${id}", emptyMap())
    suspend fun albumDetail(id: String) = rawWeapi("/api/album/v3/detail", mapOf("id" to id, "offset" to 0, "limit" to 1000, "maxCount" to true))
    suspend fun albumDetailDynamic(id: String) = rawWeapi("/api/album/detail/dynamic", mapOf("id" to id))
    suspend fun albumSublist(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/album/sublist",
        mapOf("limit" to limit, "offset" to offset, "total" to true)
    )
    suspend fun albumSub(id: String, t: Int = 1) = rawWeapi(
        "/api/album/sub",
        mapOf("id" to id, "t" to t)
    )
    suspend fun albumPrivilege(ids: List<String>) = rawWeapi(
        "/api/album/privilege",
        mapOf("id" to ids.firstOrNull().orEmpty(), "ids" to ids.joinToString(","))
    )
    suspend fun albumNewest(area: String? = null) = rawWeapi(
        "/api/album/newest",
        if (area != null) mapOf("area" to area) else emptyMap()
    )
    suspend fun albumNew(area: String = "ALL", limit: Int = 30, offset: Int = 0, type: String = "NEW") = rawWeapi(
        "/api/discovery/newAlbumsArea",
        mapOf("area" to area, "limit" to limit, "offset" to offset, "type" to type)
    )
    suspend fun albumListStyle() = rawWeapi("/api/album/style/list", emptyMap())
    suspend fun albumList(area: String = "ALL", style: String = "", year: String = "-1", month: String = "-1", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/album/list",
        mapOf("area" to area, "style" to style, "year" to year, "month" to month, "limit" to limit, "offset" to offset)
    )
    suspend fun albumSongSaleBoard(albumType: Int = 0, year: Int = 2024, type: Int = 1) = rawWeapi(
        "/api/feealbum/songsaleboard",
        mapOf("albumType" to albumType, "year" to year, "type" to type)
    )
    suspend fun digitalAlbumDetail(id: String) = rawWeapi("/api/vip/digitalAlbum/detail", mapOf("id" to id))
    suspend fun digitalAlbumSales(id: String) = rawWeapi("/api/vip/digitalAlbum/sales", mapOf("id" to id))
    suspend fun digitalAlbumOrdering(limit: Int = 10) = rawWeapi("/api/vip/digitalAlbum/ordering", mapOf("limit" to limit))
    suspend fun digitalAlbumPurchased(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/vip/digitalAlbum/purchased",
        mapOf("limit" to limit, "offset" to offset)
    )

    // ============================================================================================
    // §5 歌单（playlist_*）
    // ============================================================================================

    suspend fun playlistCreate(name: String, privacy: Int = 0, type: String = "NORMAL") = rawWeapi(
        "/api/playlist/create",
        mapOf("name" to name, "privacy" to privacy, "type" to type)
    )
    suspend fun playlistDelete(id: String) = rawWeapi(
        "/api/playlist/remove",
        mapOf("ids" to "[$id]")
    )
    suspend fun playlistTrackAdd(pid: String, ids: List<String>, op: String = "add") = rawWeapi(
        "/api/playlist/manipulate/tracks",
        mapOf(
            "op" to op,
            "pid" to pid,
            "trackIds" to NcmJson.toJsonString(ids.map { it.toLongOrNull() ?: it }),
            "imme" to true,
        )
    )
    suspend fun playlistTrackDelete(pid: String, ids: List<String>) = playlistTrackAdd(pid, ids, "del")
    suspend fun playlistTrackAll(id: String, limit: Int = 1000, offset: Int = 0, s: Int = 8) = rawWeapi(
        "/api/v6/playlist/detail",
        mapOf("id" to id, "n" to 100000, "s" to s, "limit" to limit, "offset" to offset)
    )
    suspend fun playlistTracks(id: String, limit: Int = 20, offset: Int = 0, s: Int = 8) = rawWeapi(
        "/api/v6/playlist/track/all",
        mapOf("id" to id, "limit" to limit, "offset" to offset, "s" to s)
    )
    suspend fun playlistSubscribe(id: String, t: Int = 1) = rawWeapi(
        "/api/playlist/subscribe",
        mapOf("id" to id, "t" to t)
    )
    suspend fun playlistUnsubscribe(id: String) = playlistSubscribe(id, 0)
    suspend fun playlistSubscribers(id: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/playlist/subscribers",
        mapOf("id" to id, "limit" to limit, "offset" to offset)
    )
    suspend fun playlistNameUpdate(id: String, name: String) = rawWeapi(
        "/api/playlist/update/name",
        mapOf("id" to id, "name" to name)
    )
    suspend fun playlistDescUpdate(id: String, desc: String) = rawWeapi(
        "/api/playlist/update/desc",
        mapOf("id" to id, "desc" to desc)
    )
    suspend fun playlistCoverUpdate(id: String, /* imgFileId 上传后赋值 */ imgFileId: Long, imgSize: Long = 10_000_000) = rawWeapi(
        "/api/playlist/update/cover",
        mapOf("id" to id, "coverImgId" to imgFileId.toString() + "_" + imgSize)
    )
    suspend fun playlistTagsUpdate(id: String, tags: List<String>) = rawWeapi(
        "/api/playlist/tags/update",
        mapOf("id" to id, "tags" to tags.joinToString(";"))
    )
    suspend fun playlistPrivacy(id: String, privacy: Int = 10) = rawWeapi(
        "/api/playlist/update/privacy",
        mapOf("id" to id, "privacy" to privacy)
    )
    suspend fun playlistOrderUpdate(ids: List<String>) = rawWeapi(
        "/api/playlist/order/update",
        mapOf("ids" to NcmJson.toJsonString(ids))
    )
    suspend fun playlistMyLike(uid: String) = rawWeapi(
        "/api/user/playlist",
        mapOf("uid" to uid, "limit" to 1000, "offset" to 0, "fid" to uid, "category" to 2)
    )
    suspend fun playlistHot() = rawWeapi("/api/playlist/hottags", emptyMap())
    suspend fun playlistHighqualityTags() = rawWeapi("/api/playlist/highquality/tags", emptyMap())
    suspend fun playlistDetailRcmdGet(id: String) = rawWeapi("/api/playlist/detail/rcmd", mapOf("id" to id))
    suspend fun playlistDetailDynamic(id: String) = rawWeapi("/api/playlist/detail/dynamic", mapOf("id" to id))
    suspend fun playlistCatlist() = rawWeapi("/api/playlist/catalogue", emptyMap())
    suspend fun playlistCategoryList() = rawWeapi("/api/playlist/categories", emptyMap())
    suspend fun playlistUpdate(id: String, updater: String = "") = rawWeapi(
        "/api/playlist/track/all",
        mapOf("id" to id, "updater" to updater)
    )
    suspend fun playlistUpdatePlaycount(id: String) = rawWeapi("/api/playlist/update/playcount", mapOf("id" to id))
    suspend fun playlistVideoRecent(id: String, limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/playlist/videorecent",
        mapOf("id" to id, "limit" to limit, "offset" to offset)
    )

    // 对齐原版 module/like.js：POST /api/radio/like，data={alg:'itembased', trackId, like, time:'3'}
    suspend fun like(id: String, like: Boolean = true, time: Long = System.currentTimeMillis()) = rawWeapi(
        "/api/radio/like",
        mapOf(
            "alg" to "itembased",
            "trackId" to (id.toLongOrNull() ?: id),
            "like" to like,
            "time" to "3",
        )
    )
    suspend fun likelist(uid: String) = rawWeapi(
        "/api/song/like/get",
        mapOf("uid" to uid)
    )

    suspend fun plCount() = rawWeapi("/api/playlist/count", emptyMap())
    suspend fun playmodeIntelligenceList(id: String, sid: String, count: Int = 1, startMusicId: String = sid, type: Int = 3) = rawWeapi(
        "/api/playmode/intelligence/list",
        mapOf("id" to id, "songId" to sid, "count" to count, "startMusicId" to startMusicId, "type" to type)
    )
    suspend fun playmodeSongVector(id: String) = rawWeapi(
        "/api/playmode/song/vector",
        mapOf("songId" to id)
    )
    suspend fun playlistImportNameTaskCreate(playlistId: String, name: String, singer: String = "") = rawWeapi(
        "/api/playlist/import/name/create",
        mapOf("playlistId" to playlistId, "songNames" to NcmJson.toJsonString(listOf(mapOf("name" to name, "singername" to singer))))
    )
    suspend fun playlistImportTaskStatus(taskId: String) = rawWeapi(
        "/api/playlist/import/status",
        mapOf("taskId" to taskId)
    )

    // ============================================================================================
    // §6 评论（comment_*）
    //  threadId = 前缀 + id
    //      R_SO_4_ 歌曲 / R_AL_3_ 专辑 / R_PL_0_ 歌单 / R_A_1002_ 歌单描述 / R_MV_5_ MV / R_DJ_7_ 电台节目
    //      R_EV_8_ 动态 / R_VO_6_ 语音 / R_AT_7_ 电台节目
    // ============================================================================================

    enum class CmtType(val prefix: String, val code: Int) {
        SONG("R_SO_4_", 0),
        ALBUM("R_AL_3_", 2),
        PLAYLIST("R_PL_0_", 2),
        PLAYLIST_DESC("R_A_1002_", 2),
        MV("R_MV_5_", 1),
        DJ("R_DJ_7_", 1),
        EVENT("R_EV_8_", 5),
        VOICE("R_VO_6_", 2),
        VIDEO("R_VO_", 5),
    }

    private fun threadOf(type: CmtType, id: String) = type.prefix + id

    suspend fun commentMusic(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.SONG, id, limit, offset, beforeTime)
    suspend fun commentAlbum(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.ALBUM, id, limit, offset, beforeTime)
    suspend fun commentPlaylist(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.PLAYLIST, id, limit, offset, beforeTime)
    suspend fun commentMv(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.MV, id, limit, offset, beforeTime)
    suspend fun commentDj(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.DJ, id, limit, offset, beforeTime)
    suspend fun commentEvent(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.EVENT, id, limit, offset, beforeTime)
    suspend fun commentVideo(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = comment(CmtType.VIDEO, id, limit, offset, beforeTime)
    suspend fun commentNew(id: String, typeCode: Int) = rawWeapi(
        "/api/v1/comment/new",
        mapOf("id" to id, "type" to typeCode, "sortType" to 2, "pageNo" to 1, "pageSize" to 20, "cursor" to -1)
    )
    suspend fun commentFloor(id: String, type: CmtType, parentCommentId: String, limit: Int = 20, time: Long = 0) = rawWeapi(
        "/api/v1/comment/floor",
        mapOf("parentCommentId" to parentCommentId, "threadId" to threadOf(type, id), "limit" to limit, "time" to time)
    )
    suspend fun commentHot(type: CmtType, id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = rawWeapi(
        "/api/v1/comment/hotwall/list",
        mapOf("threadId" to threadOf(type, id), "pageNo" to (offset / limit + 1), "pageSize" to limit, "cursor" to if (beforeTime == 0L) 0 else beforeTime)
    )
    suspend fun commentLike(id: String, cid: String, type: CmtType, t: Int = 1) = rawWeapi(
        // 对齐原版 module/comment_like.js：t=1 → /api/v1/comment/like，t=0 → /api/v1/comment/unlike
        // data 仅带 threadId + commentId（不带 type/t）
        if (t == 1) "/api/v1/comment/like" else "/api/v1/comment/unlike",
        mapOf("threadId" to threadOf(type, id), "commentId" to cid)
    )
    suspend fun commentHugList(id: String, cid: String, type: CmtType) = rawWeapi(
        "/api/comment/hug/list",
        mapOf("threadId" to threadOf(type, id), "commentId" to cid, "targetUserId" to "-1", "pageNo" to 1, "pageSize" to 20, "cursor" to 0, "type" to type.code)
    )
    suspend fun hugComment(id: String, cid: String, type: CmtType, uid: String) = rawWeapi(
        "/api/comment/hug",
        mapOf("threadId" to threadOf(type, id), "commentId" to cid, "targetUserId" to uid, "type" to type.code)
    )
    suspend fun comment(
        type: CmtType,
        id: String,
        limit: Int = 20,
        offset: Int = 0,
        beforeTime: Long = 0,
    ) = rawWeapi(
        "/api/v1/resource/comments/${threadOf(type, id)}",
        mapOf("rid" to id, "limit" to limit, "offset" to offset, "beforeTime" to beforeTime)
    )
    /** 发评论 / 回复评论 / 删评论（t=1 发，t=0 删，t=2 回复）
     *  对齐原版 module/comment.js：
     *    add   → POST /api/resource/comments/add     data={threadId, content}
     *    delete→ POST /api/resource/comments/delete  data={threadId, commentId}
     *    reply → POST /api/resource/comments/reply   data={threadId, commentId, content}
     */
    suspend fun commentSend(
        type: CmtType,
        id: String,
        content: String,
        t: Int = 1,
        commentId: String? = null,
    ) = when (t) {
        // 删除评论（comment.js t=0）
        0 -> rawWeapi(
            "/api/resource/comments/delete",
            buildMap<String, Any?> {
                put("threadId", threadOf(type, id))
                put("commentId", commentId ?: "")
            }
        )
        // 回复评论（comment.js t=2）
        2 -> rawWeapi(
            "/api/resource/comments/reply",
            buildMap<String, Any?> {
                put("threadId", threadOf(type, id))
                put("commentId", commentId ?: "")
                put("content", content)
            }
        )
        // 发评论（comment.js t=1）
        else -> rawWeapi(
            "/api/resource/comments/add",
            buildMap<String, Any?> {
                put("threadId", threadOf(type, id))
                put("content", content)
            }
        )
    }

    // ============================================================================================
    // §7 用户（user_*）
    // ============================================================================================

    suspend fun userDetail(uid: String) = rawWeapi("/api/v1/user/detail/$uid", emptyMap())
    suspend fun userDetailNew(uid: String) = rawWeapi("/api/user/detail/new", mapOf("uid" to uid))
    suspend fun userPlaylist(uid: String, limit: Int = 30, offset: Int = 0, includeVideo: Boolean = true) = rawWeapi(
        "/api/user/playlist",
        mapOf("uid" to uid, "limit" to limit, "offset" to offset, "includeVideo" to includeVideo)
    )
    suspend fun userPlaylistCollect(id: String, seckey: String = "", t: Int = 1) = rawWeapi(
        if (t == 1) "/api/playlist/collect" else "/api/playlist/collect/cancel",
        mapOf("id" to id, "seckey" to seckey, "withPlaylistIds" to true)
    )
    suspend fun userPlaylistCreate(name: String) = playlistCreate(name)
    suspend fun userFollows(uid: String, limit: Int = 30, offset: Int = 0, order: Boolean = true) = rawWeapi(
        "/api/user/getfollows/$uid",
        mapOf("limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun userFolloweds(uid: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/user/getfolloweds",
        mapOf("userId" to uid, "limit" to limit, "offset" to offset)
    )
    suspend fun userMutualfollowGet(uid: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/user/mutualfollow",
        mapOf("uid" to uid, "limit" to limit, "offset" to offset)
    )
    suspend fun userRecord(uid: String, type: Int = 1) = rawWeapi(
        "/api/v1/play/record",
        mapOf("uid" to uid, "type" to type)
    )
    suspend fun userDj(uid: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/user/dj",
        mapOf("uid" to uid, "limit" to limit, "offset" to offset)
    )
    suspend fun userEvent(uid: String, limit: Int = 30, lasttime: Long = -1) = rawWeapi(
        "/api/user/event",
        mapOf("uid" to uid, "limit" to limit, "lasttime" to lasttime.toString())
    )
    suspend fun userFollowMixed(uid: String, type: Int = 0, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/follow/mixed/get",
        mapOf("uid" to uid, "limit" to limit, "offset" to offset, "type" to type)
    )
    suspend fun userLevel() = rawWeapi("/api/user/level", emptyMap())
    suspend fun userMedal() = rawWeapi("/api/user/medal/single", emptyMap())
    suspend fun userSubcount() = rawWeapi("/api/user/subcount", emptyMap())
    suspend fun userBinding() = rawWeapi("/api/user/binding", emptyMap())
    suspend fun userBindingcellphone(phone: String, captcha: String, oldcaptcha: String, ctcode: String = "86") = rawWeapi(
        "/api/user/replacecellphone",
        mapOf("phone" to phone, "captcha" to captcha, "oldcaptcha" to oldcaptcha, "ctcode" to ctcode)
    )
    suspend fun userReplacephone(phone: String, captcha: String, oldcaptcha: String, ctcode: String = "86") = rawWeapi(
        "/api/user/replacecellphone",
        mapOf("phone" to phone, "captcha" to captcha, "oldcaptcha" to oldcaptcha, "ctcode" to ctcode)
    )
    suspend fun userCommentHistory(uid: String, limit: Int = 10, cursor: Long = -1) = rawWeapi(
        "/api/v1/comment/user/history/new",
        mapOf("uid" to uid, "limit" to limit, "time" to cursor, "total" to true, "preview" to false)
    )
    suspend fun userCloud(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/v1/cloud/get",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun userCloudDel(songIds: List<String>) = rawWeapi(
        "/api/cloud/del",
        mapOf("songIds" to songIds.joinToString(","))
    )
    suspend fun userCloudDetail(songId: String, uid: String) = rawWeapi(
        "/api/cloud/user/detail",
        mapOf("songId" to songId, "uid" to uid)
    )
    suspend fun userAudio(uid: String) = rawWeapi("/api/voice/user/voicelist", mapOf("uid" to uid))
    suspend fun userSocialStatus(uid: String) = rawWeapi("/api/social/status/get", mapOf("uid" to uid))
    suspend fun userSocialStatusEdit(content: String, cityCode: String? = null, provinceCode: String? = null, nickname: String? = null, avatarImgId: Long? = null) = rawWeapi(
        "/api/social/status/edit",
        buildMap {
            put("content", content)
            cityCode?.let { put("cityCode", it) }
            provinceCode?.let { put("provinceCode", it) }
            nickname?.let { put("nickname", it) }
            avatarImgId?.let { put("avatarImgId", it.toString()) }
        }
    )
    suspend fun userSocialStatusSupport(id: String, t: Int = 1) = rawWeapi(
        "/api/social/status/support",
        mapOf("id" to id, "t" to t)
    )
    suspend fun userSocialStatusRcmd(uid: String, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/social/status/rcmd",
        mapOf("uid" to uid, "limit" to limit, "offset" to offset)
    )
    suspend fun userUpdate(
        nickname: String,
        avatarImgId: Long,
        gender: Int = 0,
        birthday: Long = 0L,
        signature: String = "",
        city: String = "",
        province: String = "",
        nicknameModifyFlag: Int = 0,
    ) = rawWeapi(
        "/api/user/update",
        mapOf(
            "nickname" to nickname,
            "avatarImgId" to avatarImgId.toString(),
            "gender" to gender,
            "birthday" to birthday.toString(),
            "signature" to signature,
            "city" to city,
            "province" to province,
            "nicknameModifyFlag" to nicknameModifyFlag,
        )
    )
    suspend fun nicknameCheck(nickname: String) = rawWeapi("/api/nickname/check", mapOf("nickname" to nickname))

    suspend fun follow(uid: String, t: Int = 1) = rawWeapi(
        "/api/follow/$uid",
        mapOf("t" to t)
    )

    // ============================================================================================
    // §8 电台 DJ
    // ============================================================================================

    suspend fun djCatelist() = rawWeapi("/api/dj/catelist", emptyMap())
    suspend fun djCategoryExcludeHot() = rawWeapi("/api/dj/category/excludehot", emptyMap())
    suspend fun djCategoryRecommend() = rawWeapi("/api/dj/category/recommend", emptyMap())
    suspend fun djBanner() = rawWeapi("/api/dj/banner", emptyMap())
    suspend fun djDetail(id: String) = rawWeapi("/api/dj/detail/v2", mapOf("id" to id))
    suspend fun djHot(cateId: String = "0", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/dj/hot",
        mapOf("cateId" to cateId, "limit" to limit, "offset" to offset)
    )
    suspend fun djPersonalizeRecommend(cateId: String? = null, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/djradio/personalize/recommend",
        buildMap {
            put("limit", limit); put("offset", offset); cateId?.let { put("cateId", it) }
        }
    )
    suspend fun djPaygift(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/djradio/homepage/paygift",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun djProgram(rid: String, limit: Int = 30, offset: Int = 0, asc: Boolean = false) = rawWeapi(
        "/api/v1/dj/program",
        mapOf("rid" to rid, "limit" to limit, "offset" to offset, "asc" to asc)
    )
    suspend fun djProgramDetail(id: String) = rawWeapi("/api/dj/program/detail", mapOf("id" to id))
    suspend fun djProgramToplist(limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/program/toplist/v1",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun djProgramToplistHours(limit: Int = 100) = rawWeapi("/api/program/toplist/hours", mapOf("limit" to limit))
    suspend fun djRadiotop(limit: Int = 30, offset: Int = 0) = rawWeapi("/api/djradio/toplist", mapOf("limit" to limit, "offset" to offset))
    suspend fun djToplist(limit: Int = 100, type: Int = 1, areaId: Int = 0) = rawWeapi(
        "/api/dj/toplist/newcomer",
        mapOf("limit" to limit, "type" to type, "areaId" to areaId)
    )
    suspend fun djToplistHours(limit: Int = 100) = rawWeapi("/api/djradio/toplist/hours", mapOf("limit" to limit))
    suspend fun djToplistNewcomer(limit: Int = 100) = rawWeapi("/api/dj/toplist/newcomer", mapOf("limit" to limit))
    suspend fun djToplistPopular(limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/djradio/toplist/popular",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun djToplistPay(limit: Int = 100) = rawWeapi("/api/djradio/toplist/pay", mapOf("limit" to limit))
    suspend fun djRecommend() = rawWeapi("/api/djradio/recommend", emptyMap())
    suspend fun djRecommendType(type: String = "1", limit: Int = 10) = rawWeapi(
        "/api/djradio/recommend/type",
        mapOf("type" to type, "limit" to limit)
    )
    suspend fun djRadioHot(cateId: String = "0", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/djradio/hot",
        mapOf("cateId" to cateId, "limit" to limit, "offset" to offset)
    )
    suspend fun djSub(rid: String, t: Int = 1) = rawWeapi(
        "/api/djradio/sub",
        mapOf("id" to rid, "t" to t)
    )
    suspend fun djSublist(limit: Int = 30, offset: Int = 0, needLastProgram: Boolean = true) = rawWeapi(
        "/api/djradio/sublist",
        mapOf("limit" to limit, "offset" to offset, "needLastProgram" to needLastProgram)
    )
    suspend fun djSubscriber(id: String, limit: Int = 30, offset: Int = 0, time: Long = 0) = rawWeapi(
        "/api/djradio/subscriber",
        mapOf("id" to id, "limit" to limit, "offset" to offset, "time" to time.toString())
    )
    suspend fun djTodayPerfered(limit: Int = 10, categoryId: String = "0") = rawWeapi(
        "/api/djradio/homepage/today/perfered",
        mapOf("limit" to limit, "categoryId" to categoryId)
    )
    suspend fun programRecommend(id: String) = rawWeapi("/api/program/recommend", mapOf("id" to id))
    suspend fun broadcastSub() = rawWeapi("/api/broadcast/sub", emptyMap())
    suspend fun broadcastCategoryRegionGet() = rawWeapi("/api/broadcast/category/region", emptyMap())
    suspend fun broadcastChannelCurrentinfo(id: String) = rawWeapi("/api/broadcast/channel/currentinfo", mapOf("id" to id))
    suspend fun broadcastChannelList(areaId: Int = 0, limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/broadcast/channel",
        mapOf("areaId" to areaId, "limit" to limit, "offset" to offset)
    )
    suspend fun broadcastChannelCollectList(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/broadcast/channel/collect",
        mapOf("limit" to limit, "offset" to offset)
    )

    // ============================================================================================
    // §9 MV & Video / Mlog
    // ============================================================================================

    suspend fun mvAll(
        area: String = "全部",
        type: String = "全部",
        order: String = "上升最快",
        limit: Int = 30,
        offset: Int = 0,
    ) = rawWeapi(
        "/api/mv/all",
        mapOf("area" to area, "type" to type, "order" to order, "limit" to limit, "offset" to offset)
    )
    suspend fun mvFirst(limit: Int = 30, offset: Int = 0, order: Boolean = true) = rawWeapi(
        "/api/mv/first",
        mapOf("limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun mvExclusiveRcmd() = rawWeapi("/api/firstpage/exclusive/rcmd", emptyMap())
    suspend fun mvDetail(mvid: String) = rawWeapi("/api/mv/detail", mapOf("mvid" to mvid))
    suspend fun mvDetailInfo(mvid: String) = rawWeapi("/api/mv/detail/info", mapOf("mvid" to mvid))
    suspend fun mvSub(t: Int = 1, mvId: String = "") = rawWeapi(
        "/api/mv/$t",
        mapOf("mvId" to mvId, "mvIds" to "[$mvId]")
    ).let { rawWeapi(if (t == 1) "/api/mv/sub" else "/api/mv/unsub", mapOf("mvId" to mvId)) }
    suspend fun mvSublist(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/mv/sublist",
        mapOf("limit" to limit, "offset" to offset, "total" to true)
    )
    suspend fun mvUrl(id: String, r: Int = 1080) = rawWeapi(
        "/api/song/enhance/play/mv/url",
        mapOf("id" to id, "r" to r)
    )

    suspend fun videoGroup() = rawWeapi("/api/cloudvideo/group/list", emptyMap())
    suspend fun videoGroupList(id: String, offset: Int = 0, needPreviewUrl: Boolean = true) = rawWeapi(
        "/api/cloudvideo/group/video/list",
        mapOf("groupId" to id, "offset" to offset, "need_preview_url" to needPreviewUrl)
    )
    suspend fun videoDetail(id: String) = rawWeapi("/api/v2/video/detail", mapOf("id" to id))
    suspend fun videoDetailInfo(vid: String) = rawWeapi("/api/v2/video/detail/info", mapOf("vid" to vid))
    suspend fun videoUrl(id: String, r: Int = 1080) = rawWeapi(
        "/api/v2/video/url/multi",
        mapOf("id" to id, "resolution" to r)
    )
    suspend fun videoTimelineAll(type: Int = 0, offset: Long = 0, size: Int = 10) = rawWeapi(
        "/api/videotimeline/videogroup/list",
        mapOf("type" to type, "offset" to offset.toString(), "size" to size)
    )
    suspend fun videoTimelineRecommend(offset: Long = 0, size: Int = 10) = rawWeapi(
        "/api/videotimeline/videogroup/recommend/list",
        mapOf("offset" to offset.toString(), "size" to size)
    )
    suspend fun videoCategoryList() = rawWeapi("/api/cloudvideo/category/list", emptyMap())
    suspend fun videoSub(vid: String, t: Int = 1) = rawWeapi(
        if (t == 1) "/api/cloudvideo/video/sub" else "/api/cloudvideo/video/unsub",
        mapOf("id" to vid)
    )
    suspend fun verifyGetQr(type: Int, phone: String, ctcode: String = "86") = rawWeapi(
        "/api/verify/getQr",
        mapOf("type" to type, "phone" to phone, "ctcode" to ctcode)
    )
    suspend fun verifyQrcodestatus(verifyId: String, verifysign: String, type: Int, phone: String, ctcode: String = "86") = rawWeapi(
        "/api/verify/qrcodestatus",
        mapOf("verifyId" to verifyId, "verifysign" to verifysign, "type" to type, "phone" to phone, "ctcode" to ctcode)
    )

    suspend fun mlogUrl(id: String, r: Int = 1080) = rawWeapi(
        "/api/mlog/video/url",
        mapOf("id" to id, "resolution" to r)
    )
    suspend fun mlogToVideo(id: String, type: Int = 1) = rawWeapi(
        "/api/mlog/tovideo",
        mapOf("mlogId" to id, "type" to type)
    )
    suspend fun mlogMusicRcmd(songId: String, limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/mlog/music/rcmd",
        mapOf("songId" to songId, "limit" to limit, "offset" to offset)
    )

    // ============================================================================================
    // §10 个性化推荐 / FM
    // ============================================================================================

    suspend fun personalized(limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/personalized/playlist",
        mapOf("limit" to limit, "offset" to offset, "total" to true, "n" to 1000)
    )
    suspend fun personalizedNewsong(area: Int = 0, type: Int = 0, limit: Int = 10) = rawWeapi(
        "/api/personalized/newsong",
        mapOf("type" to type, "area" to area, "limit" to limit)
    )
    suspend fun personalizedMv() = rawWeapi("/api/personalized/mv", emptyMap())
    suspend fun personalizedDjprogram(limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/personalized/djprogram",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun personalizedPrivatecontent(limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/personalized/privatecontent",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun personalizedPrivatecontentList(limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/personalized/privatecontent/list",
        mapOf("limit" to limit, "offset" to offset, "total" to true)
    )

    suspend fun personalFm(mode: Int = 0, id: String? = null, pid: String? = null, cookie: String? = null) = rawWeapi(
        "/api/v1/radio/get",
        buildMap {
            if (mode == 1) put("mode", "F")
            if (mode == 2) put("mode", "B"); if (id != null) put("id", id)
            if (mode == 3) put("mode", "S"); if (pid != null) put("pid", pid)
        }
    )
    suspend fun personalFmMode(mode: Int, id: String? = null, pid: String? = null) = personalFm(mode, id, pid)
    suspend fun fmTrash(id: String, time: Long = System.currentTimeMillis()) = rawWeapi(
        "/api/radio/trash/add",
        mapOf("alg" to "itemBased", "songId" to id, "time" to time.toString())
    )

    // ============================================================================================
    // §11 每日推荐 / 历史 / 听歌数据
    // ============================================================================================

    suspend fun historyRecommendSongs(limit: Int = 100) = rawWeapi(
        "/api/v1/discovery/recommend/history",
        mapOf("limit" to limit)
    )
    suspend fun historyRecommendSongsDetail(date: String) = rawWeapi(
        "/api/v1/discovery/recommend/history/detail",
        mapOf("date" to date)
    )
    suspend fun listenDataReport(type: Int, data: String) = rawWeapi(
        "/api/listen/data/report",
        mapOf("type" to type, "data" to data)
    )
    suspend fun listenDataRealtimeReport(actionsJson: String) = rawWeapi(
        "/api/playlist/client/statistic/realtime/report",
        mapOf("actions" to actionsJson)
    )
    suspend fun listenDataTodaySong(start: Long = 0, end: Long = 0) = rawWeapi(
        "/api/playrecord/getTodaySongsPlayed",
        buildMap {
            if (start != 0L) put("startTime", start)
            if (end != 0L) put("endTime", end)
        }
    )
    suspend fun listenDataTotal() = rawWeapi("/api/listen/data/total", emptyMap())
    suspend fun listenDataYearReport(year: Int = 0) = rawWeapi(
        if (year == 0) "/api/activity/report/annual" else "/api/activity/report/annual?year=$year",
        emptyMap()
    )

    // ============================================================================================
    // §12 云盘
    // ============================================================================================

    suspend fun cloud(limit: Int = 30, offset: Int = 0) = userCloud(limit, offset)
    suspend fun cloudImport() = rawWeapi("/api/cloud/import", emptyMap())
    suspend fun cloudMatch(songId: String, userSongId: String, artist: String, song: String, album: String) = rawWeapi(
        "/api/cloud/match",
        mapOf("songId" to songId, "userSongId" to userSongId, "artistName" to artist, "songName" to song, "albumName" to album)
    )
    suspend fun cloudLyricGet(id: String, cp: Boolean = false) = rawWeapi(
        "/api/cloud/get",
        mapOf("id" to id)
    ).let { rawWeapi("/api/song/lyric", mapOf("id" to id, "cp" to cp, "lv" to -1, "tv" to -1, "kv" to -1)) }
    suspend fun cloudUploadToken(ext: String = "mp3", size: Long = 0L, bitrate: String = "320000", md5: String? = null) = rawWeapi(
        "/api/cloud/upload/getToken",
        buildMap {
            put("ext", ext); put("size", size.toString()); put("bitrate", bitrate)
            if (md5 != null) put("md5", md5)
        }
    )
    suspend fun cloudUploadComplete(songId: String, md5: String, objKey: String, ext: String = "mp3") = rawWeapi(
        "/api/cloud/upload/v2/info",
        mapOf("songid" to songId, "md5" to md5, "objToken" to objKey, "ext" to ext)
    )

    // ============================================================================================
    // §13 签到 / VIP / 云贝 / 等级 / 音乐日历 / 听书年度报告 / 云贝中心
    // ============================================================================================

    suspend fun dailySignin(type: Int = 0) = rawWeapi(
        if (type == 0) "/api/point/dailyTask" else "/api/point/dailyTask",
        mapOf("type" to type)
    )

    suspend fun signHappyInfo() = rawWeapi("/api/act/sign/happy/info", emptyMap())
    suspend fun signinProgress() = rawWeapi("/api/weapi/users/signin/progress", emptyMap())

    suspend fun vipInfo() = rawWeapi("/api/music-vip-membership/front/vip/info", emptyMap())
    suspend fun vipInfoV2() = rawWeapi("/api/vipnew/apps/vip/info/v2", emptyMap())
    suspend fun vipTasks() = rawWeapi("/api/vipnew/apps/vip/tasks", mapOf("queryVipTask" to 1))
    suspend fun vipSign(time: Long = System.currentTimeMillis()) = rawWeapi(
        "/api/weapi/task/sign",
        mapOf("time" to time.toString())
    )
    suspend fun vipSignInfo() = rawWeapi("/api/weapi/w/sign/info", emptyMap())
    suspend fun vipGrowthpoint() = rawWeapi("/api/music-vip-membership/front/growthpoint/get", emptyMap())
    suspend fun vipGrowthpointGet(type: Int = 0, taskId: String = "") = rawWeapi(
        "/api/music-vip-membership/front/growthpoint/task/receive",
        mapOf("taskType" to type, "taskId" to taskId)
    )
    suspend fun vipGrowthpointDetails(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/music-vip-membership/front/growthpoint/details",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun vipTimemachine(mode: String = "1", platform: String = "iPhone15,3", remoteconfig: String = "1") = rawWeapi(
        "/api/music-vip-membership/v1/timemachine/session/status",
        mapOf("mode" to mode, "platform" to platform, "remoteconfig" to remoteconfig)
    )

    suspend fun yunbeiInfo() = rawWeapi("/api/user/yunbei/info", emptyMap())
    suspend fun yunbeiSign() = rawWeapi("/api/user/yunbei/sign", emptyMap())
    suspend fun yunbeiTasks() = rawWeapi("/api/user/yunbei/tasks", emptyMap())
    suspend fun yunbeiTasksTodo() = rawWeapi("/api/act/lottery/ybtask/todo", emptyMap())
    suspend fun yunbeiTaskFinish(userTaskId: String, depositAmount: Int = 0) = rawWeapi(
        "/api/act/lottery/ybtask/finish",
        mapOf("userTaskId" to userTaskId, "depositAmount" to depositAmount.toString())
    )
    suspend fun yunbeiToday() = rawWeapi("/api/user/yunbei/today", emptyMap())
    suspend fun yunbeiReceipt(id: String) = rawWeapi("/api/yunbei/order/receipt", mapOf("id" to id))
    suspend fun yunbeiExpense(limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/user/yunbei/expense",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun yunbei() = yunbeiInfo()
    suspend fun yunbeiRcmdSong() = rawWeapi("/api/v1/yunbei/rcmd/song/list", emptyMap())
    suspend fun yunbeiRcmdSongHistory(limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/v1/yunbei/rcmd/song/history",
        mapOf("limit" to limit, "offset" to offset)
    )

    // ============================================================================================
    // §14 一起听 listentogether
    // ============================================================================================

    suspend fun listentogetherRoomCreate(playlistId: String, playlistType: Int = 1) = rawWeapi(
        "/api/listentogether/room/create",
        mapOf("playlistId" to playlistId, "playlistType" to playlistType)
    )
    suspend fun listentogetherRoomCheck(roomId: String, inviterId: String, nickname: String = "", avatar: String = "", token: String = "") = rawWeapi(
        "/api/listentogether/room/check",
        mapOf("roomId" to roomId, "inviterId" to inviterId, "nickname" to nickname, "avatar" to avatar, "token" to token)
    )
    suspend fun listentogetherSyncPlaylistGet(roomId: String, lastPlayIndex: Int = 0, lastPlayId: String = "0", lastPlayTime: Long = 0L, wait: Boolean = false) = rawWeapi(
        "/api/listentogether/sync_playlist/get",
        mapOf("roomId" to roomId, "lastPlayIndex" to lastPlayIndex, "lastPlayId" to lastPlayId, "lastPlayTime" to lastPlayTime.toString(), "wait" to wait)
    )
    suspend fun listentogetherSyncListCommand(roomId: String, type: Int, playlistId: String? = null, playIndex: Int? = null, playId: String? = null, playTime: Long? = null, commandId: String = "", checkStatus: Boolean = false) = rawWeapi(
        "/api/listentogether/sync_list_command",
        buildMap {
            put("roomId", roomId); put("type", type); put("commandId", commandId); put("checkStatus", checkStatus)
            playlistId?.let { put("playlistId", it) }
            playIndex?.let { put("playIndex", it.toString()) }
            playId?.let { put("playId", it) }
            playTime?.let { put("playTime", it.toString()) }
        }
    )
    suspend fun listentogetherAccept(roomId: String, inviterId: String, token: String = "") = rawWeapi(
        "/api/listentogether/accept",
        mapOf("roomId" to roomId, "inviterId" to inviterId, "token" to token)
    )
    suspend fun listentogetherEnd(roomId: String, nickname: String = "", avatar: String = "") = rawWeapi(
        "/api/listentogether/end",
        mapOf("roomId" to roomId, "nickname" to nickname, "avatar" to avatar)
    )
    suspend fun listentogetherHeatbeat(roomId: String, playStatus: Int, playId: String = "0", playTime: Long = 0L, playIndex: Int = 0) = rawWeapi(
        "/api/listentogether/heartbeat",
        mapOf("roomId" to roomId, "playStatus" to playStatus, "playId" to playId, "playTime" to playTime.toString(), "playIndex" to playIndex.toString())
    )
    suspend fun listentogetherPlayCommand(roomId: String, commandType: Int, playId: String = "0", playTime: Long = 0L, playIndex: Int = 0) = rawWeapi(
        "/api/listentogether/play_command",
        mapOf("roomId" to roomId, "commandType" to commandType, "playId" to playId, "playTime" to playTime.toString(), "playIndex" to playIndex.toString())
    )
    suspend fun listentogetherStatus(roomId: String) = rawWeapi("/api/listentogether/status/get", mapOf("roomId" to roomId))

    // ============================================================================================
    // §15 风格 style_*
    // ============================================================================================

    suspend fun styleList() = rawWeapi("/api/style/list", emptyMap())
    suspend fun styleDetail(tagId: Long, size: Int = 20, cursor: Long = 0) = rawWeapi(
        "/api/style/detail",
        mapOf("tagId" to tagId.toString(), "size" to size, "cursor" to cursor.toString())
    )
    suspend fun stylePreference() = rawWeapi("/api/style/preference/query", emptyMap())
    suspend fun styleSong(tagId: Long, sort: Int = 0, size: Int = 20, cursor: Long = 0) = rawWeapi(
        "/api/style/song",
        mapOf("tagId" to tagId.toString(), "sort" to sort, "size" to size, "cursor" to cursor.toString())
    )
    suspend fun stylePlaylist(tagId: Long, sort: Int = 0, size: Int = 20, cursor: Long = 0) = rawWeapi(
        "/api/style/playlist",
        mapOf("tagId" to tagId.toString(), "sort" to sort, "size" to size, "cursor" to cursor.toString())
    )
    suspend fun styleArtist(tagId: Long, size: Int = 20, cursor: Long = 0) = rawWeapi(
        "/api/style/artist",
        mapOf("tagId" to tagId.toString(), "size" to size, "cursor" to cursor.toString())
    )
    suspend fun styleAlbum(tagId: Long, size: Int = 20, cursor: Long = 0) = rawWeapi(
        "/api/style/album",
        mapOf("tagId" to tagId.toString(), "size" to size, "cursor" to cursor.toString())
    )
    suspend fun styleSong_new(ids: List<Long>) = styleSong(ids.firstOrNull() ?: 0L)  // 兼容旧命名

    // ============================================================================================
    // §16 私信/消息  / send_* / share / topic / creator
    // ============================================================================================

    suspend fun msgRecentcontact(limit: Int = 100, offset: Int = 0) = rawWeapi(
        "/api/msg/recentcontact/get",
        mapOf("limit" to limit, "total" to true, "offset" to offset)
    )
    suspend fun msgPrivate(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/msg/private/users",
        mapOf("limit" to limit, "total" to true, "offset" to offset)
    )
    suspend fun msgPrivateHistory(uid: String, beforeTime: Long = 0, limit: Int = 30, total: Boolean = true) = rawWeapi(
        "/api/msg/private/history",
        mapOf("userIds" to "[$uid]", "before" to beforeTime.toString(), "limit" to limit, "total" to total)
    )
    suspend fun msgComments(limit: Int = 30, beforeTime: Long = 0) = rawWeapi(
        "/api/v1/msg/comments",
        mapOf("limit" to limit, "beforeTime" to beforeTime.toString())
    )
    suspend fun msgForwards(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/msg/forwards",
        mapOf("limit" to limit, "total" to true, "offset" to offset)
    )
    suspend fun msgNotices(limit: Int = 30, lastTime: Long = 0, isPrev: Boolean = true) = rawWeapi(
        "/api/msg/notices",
        mapOf("limit" to limit, "lastTime" to lastTime.toString(), "isPrev" to isPrev)
    )

    suspend fun sendText(userIds: String, msg: String, type: String = "text") = rawWeapi(
        "/api/msg/private/send/text",
        mapOf("userIds" to userIds, "type" to type, "msg" to msg)
    )
    suspend fun sendSong(userId: String, songId: String, id: String, msg: String = "", csrf: String = "") = rawWeapi(
        "/api/msg/private/send",
        mapOf("userIds" to "[$userId]", "type" to "song", "id" to id, "msg" to msg, "songId" to songId)
    )
    suspend fun sendPlaylist(userId: String, id: String, msg: String = "", csrf: String = "") = rawWeapi(
        "/api/msg/private/send",
        mapOf("userIds" to "[$userId]", "type" to "playlist", "id" to id, "msg" to msg)
    )
    suspend fun sendAlbum(userId: String, id: String, msg: String = "") = rawWeapi(
        "/api/msg/private/send",
        mapOf("userIds" to "[$userId]", "type" to "album", "id" to id, "msg" to msg)
    )

    suspend fun shareResource(type: String, msg: String, id: String) = rawWeapi(
        "/api/share/follow/comment",
        mapOf("type" to type, "msg" to msg, "id" to id)
    )

    suspend fun topicSublist(limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/topic/sublist",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun topicDetail(actId: String) = rawWeapi("/api/act/detail", mapOf("actid" to actId))
    suspend fun topicDetailEventHot(actId: String, limit: Int = 5, threadType: String = "ACTIVITY") = rawWeapi(
        "/api/topic/detail/event/hot",
        mapOf("actid" to actId, "limit" to limit, "threadType" to threadType)
    )

    suspend fun creatorAuthinfoGet() = rawWeapi("/api/creator/official/authInfo/get", emptyMap())
    suspend fun thresholdDetailGet() = rawWeapi("/api/creator/threshold/v3/detail", emptyMap())

    // ============================================================================================
    // §17 动态 / 粉丝中心 / 音乐人 / UGC / 首页 / 激活 / 国家码 / batch / 内部版本号 / AIDJ
    // ============================================================================================

    suspend fun event(limit: Int = 20, lasttime: Long = -1, pagesize: Int = 20) = rawWeapi(
        "/api/v1/event/get",
        mapOf("pagesize" to pagesize, "lastTime" to lasttime.toString())
    )
    suspend fun eventDel(evId: String) = rawWeapi("/api/event/delete", mapOf("evId" to evId))
    suspend fun eventForward(evId: String, forwards: String = "转发") = rawWeapi(
        "/api/event/forward",
        mapOf("evId" to evId, "forwards" to forwards)
    )

    suspend fun fanscenterOverviewGet() = rawWeapi("/api/creator/fanscenter/overview/get", emptyMap())
    suspend fun fanscenterBasicinfoAgeGet(startDate: String = "", endDate: String = "") = rawWeapi(
        "/api/creator/fanscenter/basicinfo/age/get",
        buildMap { if (startDate.isNotBlank()) put("startDate", startDate); if (endDate.isNotBlank()) put("endDate", endDate) }
    )
    suspend fun fanscenterBasicinfoGenderGet(startDate: String = "", endDate: String = "") = rawWeapi(
        "/api/creator/fanscenter/basicinfo/gender/get",
        buildMap { if (startDate.isNotBlank()) put("startDate", startDate); if (endDate.isNotBlank()) put("endDate", endDate) }
    )
    suspend fun fanscenterBasicinfoProvinceGet(startDate: String = "", endDate: String = "") = rawWeapi(
        "/api/creator/fanscenter/basicinfo/province/get",
        buildMap { if (startDate.isNotBlank()) put("startDate", startDate); if (endDate.isNotBlank()) put("endDate", endDate) }
    )
    suspend fun fanscenterTrendList(startDate: String = "", endDate: String = "", limit: Int = 30, offset: Int = 0) = rawWeapi(
        "/api/creator/fanscenter/trend/list",
        buildMap {
            if (startDate.isNotBlank()) put("startDate", startDate)
            if (endDate.isNotBlank()) put("endDate", endDate)
            put("limit", limit); put("offset", offset)
        }
    )

    suspend fun musicianDataOverview(startDate: String = "", endDate: String = "") = rawWeapi(
        "/api/creator/musician/data/overview",
        buildMap { if (startDate.isNotBlank()) put("startDate", startDate); if (endDate.isNotBlank()) put("endDate", endDate) }
    )
    suspend fun musicianPlayTrend(startDate: String = "", endDate: String = "") = rawWeapi(
        "/api/creator/musician/play/trend",
        buildMap { if (startDate.isNotBlank()) put("startDate", startDate); if (endDate.isNotBlank()) put("endDate", endDate) }
    )
    suspend fun musicianCloudbean() = rawWeapi("/api/creator/cloudbean/center/get", emptyMap())
    suspend fun musicianCloudbeanObtain(cloudBean: Int, id: String) = rawWeapi(
        "/api/creator/cloudbean/obtain",
        mapOf("cloudBean" to cloudBean.toString(), "id" to id)
    )
    suspend fun musicianTasks(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/creator/musician/tasks/list",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun musicianTasksNew(limit: Int = 20, offset: Int = 0) = musicianTasks(limit, offset)
    suspend fun musicianVipTasks(limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/creator/musician/vip/tasks/list",
        mapOf("limit" to limit, "offset" to offset)
    )
    suspend fun musicianSign(year: Int, month: Int, day: Int) = rawWeapi(
        "/api/creator/user/collect/get",
        mapOf("year" to year.toString(), "month" to month.toString(), "day" to day.toString())
    )
    suspend fun getMusicFirstListenInfo(
        id: String,
        deviceInfo: String = NcmJson.toJsonString(mapOf("osId" to "osId", "appVersion" to "3.51.1", "sdkVersion" to 33, "deviceId" to "pixel-9")),
    ) = rawWeapi(
        "/api/first/listen/info/v2",
        mapOf("songId" to id, "deviceInfo" to deviceInfo)
    )

    suspend fun ugcDetail(id: String, type: Int = 1, needTrans: Boolean = true, resolution: Int = 1080) = rawWeapi(
        "/api/ugc/detail",
        mapOf("id" to id, "type" to type, "needTrans" to needTrans, "resolution" to resolution)
    )
    suspend fun ugcArtistGet(artistId: String, limit: Int = 20, offset: Int = 0, order: String = "new") = rawWeapi(
        "/api/ugc/artist/get",
        mapOf("artistId" to artistId, "limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun ugcArtistSearch(keywords: String, limit: Int = 10, offset: Int = 0) = rawWeapi(
        "/api/ugc/artist/search",
        mapOf("keywords" to keywords, "limit" to limit, "offset" to offset)
    )
    suspend fun ugcSongGet(songId: String, limit: Int = 20, offset: Int = 0, order: String = "hot") = rawWeapi(
        "/api/ugc/song/get",
        mapOf("songId" to songId, "limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun ugcAlbumGet(albumId: String, limit: Int = 20, offset: Int = 0, order: String = "new") = rawWeapi(
        "/api/ugc/album/get",
        mapOf("albumId" to albumId, "limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun ugcMvGet(mvid: String, limit: Int = 20, offset: Int = 0) = rawWeapi(
        "/api/ugc/mv/get",
        mapOf("mvid" to mvid, "limit" to limit, "offset" to offset)
    )
    suspend fun ugcUserDevote(uid: String, sort: Int = 1, limit: Int = 30, offset: Int = 0, ugcType: Int = 1) = rawWeapi(
        "/api/ugc/user/devote",
        mapOf("uid" to uid, "sortType" to sort, "limit" to limit, "offset" to offset, "ugcType" to ugcType)
    )

    suspend fun homepageBlockPage(pageId: String, refresh: Boolean = false, cursor: String = "0") = rawWeapi(
        "/api/homepage/block/page",
        mapOf("pageId" to pageId, "refresh" to refresh, "cursor" to cursor, "moduleInfo" to NcmJson.toJsonString(mapOf("moduleId" to arrayOf<String>(), "pageId" to pageId)))
    )
    suspend fun homepageDragonBall() = rawWeapi("/api/v1/homepage/dragon/ball/static", emptyMap())

    suspend fun activateInitProfile(
        nickname: String,
        birthday: Long = System.currentTimeMillis(),
        gender: Int = 0,
        avatarImgId: Long = 0L,
        areaCode: String = "86",
    ) = rawWeapi(
        "/api/user/activate/initProfile",
        mapOf("nickname" to nickname, "birthday" to birthday.toString(), "gender" to gender, "avatarImgId" to avatarImgId.toString(), "areaCode" to areaCode)
    )

    suspend fun countriesCodeList() = rawWeapi("/api/lbs/countries/v1", emptyMap())
    suspend fun cellphoneExistenceCheck(phone: String, ctcode: String = "86") = rawWeapi(
        "/api/v1/register/check",
        mapOf("phone" to phone, "ctcode" to ctcode)
    )
    suspend fun calendar(startDate: String? = null, endDate: String? = null, cellPhone: String? = null) = rawWeapi(
        "/api/calendar/v2/list",
        buildMap {
            startDate?.let { put("startDate", it) }
            endDate?.let { put("endDate", it) }
            cellPhone?.let { put("cellPhone", it) }
        }
    )

    /** batch.js —— 同一请求里并发执行多个子接口（官方 eapi/batch） */
    suspend fun batch(batchApi: Map<String, Map<String, Any?>>) = rawEapi(
        "/api/batch",
        mapOf("allApi" to batchApi)
    )

    /** api.js —— 任意路径原封转发 */
    suspend fun raw(path: String, method: String = "POST", params: Map<String, Any?> = emptyMap(), crypto: String = "weapi"): Result<Map<String, Any?>> = when (crypto.lowercase()) {
        "weapi" -> rawWeapi(path, params)
        "eapi" -> rawEapi(path, params)
        "linuxapi" -> rawLinuxapi(path, params)
        else -> Result.failure(IllegalArgumentException("unknown crypto: $crypto"))
    }

    /** eapi_decrypt.js —— 调试用：把 eapi 请求体 hex 解出来 */
    @Suppress("FunctionName")
    fun EapiDecryptRequest(hex: String) = NcmCrypto.eapiReqDecrypt(hex)

    /** inner_version.js / setting.js / weblog.js */
    suspend fun innerVersion() = rawWeapi("/api/setting/innerversion", emptyMap())
    suspend fun setting() = rawWeapi("/api/setting/query", emptyMap())
    suspend fun weblog(logs: String) = rawWeapi("/api/feedback/weblog", mapOf("logs" to logs))

    suspend fun audioMatch(
        ext: String = "mp3",
        md5: String = "",
        bitrate: String = "320",
        duration: Long = 0L,
        sampleRate: Int = 44100,
        channels: Int = 2,
        songId: String? = null,
    ) = rawWeapi(
        "/api/web-fingerprint",
        buildMap<String, Any?> {
            put("ext", ext); put("md5", md5); put("bitrate", bitrate)
            put("duration", duration.toString()); put("sampleRate", sampleRate.toString()); put("channels", channels.toString())
            if (songId != null) put("songId", songId)
        }
    )

    /** avatar_upload.js / voice_upload.js —— 先拿 token，不传文件（真实上传走 HTTP PUT/POST Object Storage） */
    suspend fun avatarUploadToken(imgSize: Long = 10_000_000L, imgMd5: String? = null, imgExt: String = "jpg") = rawWeapi(
        "/api/user/avatar/upload/v1",
        buildMap {
            put("imgSize", imgSize.toString()); put("imgExt", imgExt)
            if (imgMd5 != null) put("md5", imgMd5)
        }
    )
    suspend fun voiceUpload(
        voiceMd5: String = "",
        voiceSize: Long = 0L,
        voiceType: Int = 0,
        bitRate: Int = 320,
        duration: Long = 0L,
        sampleRate: Int = 44100,
        channels: Int = 1,
        volume: Float = 1.0f,
    ) = rawWeapi(
        "/api/voice/upload/v1",
        buildMap {
            put("voiceMd5", voiceMd5); put("voiceSize", voiceSize.toString())
            put("voiceType", voiceType)
            put("bitRate", bitRate.toString()); put("duration", duration.toString())
            put("sampleRate", sampleRate.toString()); put("channels", channels.toString())
            put("volume", volume.toString())
        }
    )

    suspend fun voiceDelete(id: String) = rawWeapi("/api/voice/delete", mapOf("id" to id))
    suspend fun voiceDetail(id: String) = rawWeapi("/api/voice/detail/v1", mapOf("id" to id))
    suspend fun voiceLyric(id: String) = rawWeapi("/api/voice/lyric", mapOf("id" to id))
    suspend fun voiceListSearch(limit: Int = 20, offset: Int = 0, order: String = "hot") = rawWeapi(
        "/api/voice/list/voice/search",
        mapOf("limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun voiceList_list(limit: Int = 20, offset: Int = 0, order: String = "hot") = voiceListSearch(limit, offset, order)
    suspend fun voiceList(limit: Int = 20, offset: Int = 0, order: String = "hot") = rawWeapi(
        "/api/voice/list/voice/recommend",
        mapOf("limit" to limit, "offset" to offset, "order" to order)
    )
    suspend fun voiceListDetail(id: String) = rawWeapi("/api/voice/list/detail", mapOf("id" to id))
    suspend fun voiceListTrans(ids: List<String>) = rawWeapi("/api/voice/list/trans", mapOf("ids" to ids.joinToString(",")))

    // ============================================================================================
    // 补充：登录（login.js 邮箱/账号通用）
    // ============================================================================================

    suspend fun loginEmail(email: String, passwordMd5: String) = rawWeapi(
        "/api/login",
        mapOf("email" to email, "password" to passwordMd5, "rememberLogin" to true)
    )
    suspend fun loginCellphoneV2(phone: String, captcha: String, ctcode: String = "86") = rawWeapi(
        "/api/w/login/cellphone/v2",
        mapOf("phone" to phone, "captcha" to captcha, "ctcode" to ctcode, "rememberLogin" to true)
    )
    suspend fun loginStatus() = rawWeapi("/api/wlogin/status", emptyMap())
    suspend fun loginRefresh() = rawWeapi("/api/login/token/refresh", emptyMap())

    // ============================================================================================
    // 小工具：当前时间戳 / 日期格式化（scrobble 辅助）
    // ============================================================================================

    internal fun nowTs() = System.currentTimeMillis()

    internal fun formatDate(pattern: String, t: Long = nowTs()): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))

    /** 登录 —— 旧通用入口（已在主 NcmModules 中实现），这里兼容别名 */
    internal val legacy_login_cellphone: Nothing get() = error("use loginCellphone() instead")
}
