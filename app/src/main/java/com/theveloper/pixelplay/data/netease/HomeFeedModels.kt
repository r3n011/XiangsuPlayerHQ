package com.theveloper.pixelplay.data.netease

/**
 * 主页推荐卡片的一条**在线歌曲**（目前只有网易云）。
 *
 * 之所以不直接用 [com.theveloper.pixelplay.data.model.Song]：
 * 这些卡片的数据来自网易云在线接口，歌曲还没进媒体库、也还没解析直链。
 * 播放走的是「占位入队 + 播到哪首解析哪首」的懒解析队列（与每日推荐一致），
 * 所以这里只保留解析/展示/播放所需的最小字段，并提供到
 * [PersonalFmSongDetail] 的转换以复用既有的直链解析链路。
 */
data class HomeFeedTrack(
    /** 网易云 songId（<= 0 视为不可播放，UI 层会过滤） */
    val id: Long,
    val title: String,
    val artist: String,
    val artistIds: List<Long> = emptyList(),
    val albumName: String = "",
    val albumId: Long = -1L,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    /** 网易云给的推荐理由（个性化推荐会带） */
    val reason: String? = null,
    /**
     * 发行/加入时间（毫秒）。用于卡片上的 `NEW` 角标与「N 天前」相对时间。
     * 取网易云的 `publishTime`；取不到时为 0，UI 不显示时间。
     */
    val addedAtMs: Long = 0L,
    /** 网易云 fee：1 / 8 表示需要会员或付费 */
    val fee: Int = 0,
    val source: String = "wy",
) {
    val isPlayable: Boolean get() = id > 0L

    /** 复用每日推荐/FM 的直链解析链路（落雪音源优先、网易云官方兜底）。 */
    fun toPersonalFmDetail(): PersonalFmSongDetail = PersonalFmSongDetail(
        id = id,
        name = title,
        artists = artist.split(", ").map { it.trim() }.filter { it.isNotEmpty() },
        artistIds = artistIds,
        albumName = albumName,
        albumPic = artworkUrl.orEmpty(),
        duration = durationMs,
        fee = fee,
    )
}

/**
 * 网易云首页 server blocks 里「带歌曲」的栏目。
 *
 * 只覆盖主页卡片要用的几类；歌单/播客栏目暂不解析（它们不在本次需求里）。
 */
enum class HomeBlockKind {
    /** 近期云村热播 */
    RECENTLY_TRENDING,

    /** 最近的热门歌曲 */
    REGIONAL_SONGS,

    /** 「根据…为你推荐」个性化推荐 */
    TAILORED,

    /** 从你喜欢的歌开始漫游 */
    ROAMING,

    /** 根据你喜爱的歌曲推荐（相似歌曲） */
    SIMILAR,

    /** 未识别但带歌曲的栏目（保留原始标题） */
    UNKNOWN,
}

/** 一个已解析的 server block（只保留歌曲栏目）。 */
data class HomeBlockSection(
    val kind: HomeBlockKind,
    val title: String,
    val tracks: List<HomeFeedTrack>,
)

/** 主页三张在线推荐卡片的聚合结果（每日推荐走 PlayerViewModel 既有状态，不在此列）。 */
data class HomeFeedSections(
    /** 为你推荐：`/api/personalized/newsong` */
    val forYou: List<HomeFeedTrack> = emptyList(),

    /** 今日云村热播：server block「近期云村热播」优先，兜底 `/api/v1/discovery/new/songs` */
    val todayTrending: List<HomeFeedTrack> = emptyList(),

    /** 全部今日热门：server block「最近的热门歌曲」优先，兜底同接口（做去重避免与上一张重复） */
    val todayHot: List<HomeFeedTrack> = emptyList(),
) {
    val isEmpty: Boolean
        get() = forYou.isEmpty() && todayTrending.isEmpty() && todayHot.isEmpty()
}

/**
 * 每日推荐条目 → 主页卡片条目（复用同一套 Hero/行 UI）。
 *
 * @param addedAtMs 每日推荐是按天更新的，调用方通常传「今天 00:00」，
 *   这样卡片上显示「今天」而不是空白。
 */
fun DailyRecommendSong.toHomeFeedTrack(addedAtMs: Long = 0L): HomeFeedTrack = HomeFeedTrack(
    id = song.id,
    title = song.name,
    artist = song.artistString,
    artistIds = song.artistIds,
    albumName = song.albumName,
    artworkUrl = song.albumPic.takeIf { it.isNotBlank() },
    durationMs = song.duration,
    reason = reason.takeIf { it.isNotBlank() },
    addedAtMs = addedAtMs,
    fee = song.fee,
)
