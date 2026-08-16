package com.theveloper.pixelplay.data.toplist

/**
 * 排行榜静态目录（对齐落雪音乐 musicSdk 各平台 leaderboard.js 的 list 精简版）。
 * 落雪维护的榜单接口经常变动，静态目录是最稳的兜底：各平台榜单 id 是官方长期稳定的。
 */
object ToplistCatalog {

    /** 音源平台（对齐落雪 musicSdk source 标识） */
    enum class Platform(val key: String, val label: String) {
        WY("wy", "网易云"),
        TX("tx", "QQ音乐"),
        KG("kg", "酷狗");

        companion object {
            fun fromKey(key: String): Platform = entries.firstOrNull { it.key == key } ?: WY
        }
    }

    /** 单个榜单。id 全局唯一，格式 "{platform}__{bangid}"（对齐落雪） */
    data class Entry(
        val id: String,
        val name: String,
        val subtitle: String,
        val bangid: String
    ) {
        val platformKey: String get() = id.substringBefore("__")
        val platform: Platform get() = Platform.fromKey(platformKey)
    }

    private fun e(platform: Platform, bangid: String, name: String, subtitle: String = ""): Entry =
        Entry(id = "${platform.key}__$bangid", name = name, subtitle = subtitle, bangid = bangid)

    // ── 网易云（官方歌单，走 getPlaylistDetail 明文 API）──────────────────────
    // 对齐落雪 wy/leaderboard.js 最新 topList（每平台取 10 个）
    private val wyEntries = listOf(
        e(Platform.WY, "19723756", "飙升榜", "热度飙升"),
        e(Platform.WY, "3779629", "新歌榜", "最新发布"),
        e(Platform.WY, "2884035", "原创榜", "原创音乐"),
        e(Platform.WY, "3778678", "热歌榜", "全站最热"),
        e(Platform.WY, "991319590", "说唱榜", "说唱热门"),
        e(Platform.WY, "71384707", "古典榜", "古典音乐"),
        e(Platform.WY, "1978921795", "电音榜", "电音热门"),
        e(Platform.WY, "5453912201", "黑胶VIP爱听榜", "黑胶VIP"),
        e(Platform.WY, "71385702", "ACG榜", "二次元"),
        e(Platform.WY, "745956260", "韩语榜", "韩国热门")
    )

    // ── QQ音乐（musicu.fcg ToplistInfoServer GetDetail）────────────────────
    // 对齐落雪 tx/leaderboard.js 最新 boardList
    private val txEntries = listOf(
        e(Platform.TX, "4", "流行指数榜", "流行热度"),
        e(Platform.TX, "26", "热歌榜", "全站热歌"),
        e(Platform.TX, "27", "新歌榜", "最新发布"),
        e(Platform.TX, "62", "飙升榜", "热度飙升"),
        e(Platform.TX, "58", "说唱榜", "说唱热门"),
        e(Platform.TX, "57", "喜力电音榜", "电音热门"),
        e(Platform.TX, "28", "网络歌曲榜", "网络热门"),
        e(Platform.TX, "5", "内地榜", "内地热门"),
        e(Platform.TX, "3", "欧美榜", "欧美热门"),
        e(Platform.TX, "59", "香港地区榜", "香港热门")
    )

    // ── 酷狗（mobilecdnbj.kugou.com v3/rank/song）───────────────────────────
    // 对齐落雪 kg/leaderboard.js 最新 boardList
    private val kgEntries = listOf(
        e(Platform.KG, "8888", "TOP500", "全站最热"),
        e(Platform.KG, "6666", "飙升榜", "热度飙升"),
        e(Platform.KG, "59703", "蜂鸟流行音乐榜", "蜂鸟音乐"),
        e(Platform.KG, "52144", "抖音热歌榜", "抖音热门"),
        e(Platform.KG, "52767", "快手热歌榜", "快手热门"),
        e(Platform.KG, "24971", "DJ热歌榜", "DJ热门"),
        e(Platform.KG, "23784", "网络红歌榜", "网络热门"),
        e(Platform.KG, "44412", "说唱先锋榜", "说唱热门"),
        e(Platform.KG, "31308", "内地榜", "内地热门"),
        e(Platform.KG, "33160", "电音榜", "电音热门")
    )

    val platforms: List<Platform> = Platform.entries

    /** 全部榜单（按平台分组顺序） */
    val entries: List<Entry> by lazy {
        wyEntries + txEntries + kgEntries
    }

    fun entriesFor(platform: Platform): List<Entry> = when (platform) {
        Platform.WY -> wyEntries
        Platform.TX -> txEntries
        Platform.KG -> kgEntries
    }

    fun findEntry(entryId: String): Entry? = entries.find { it.id == entryId }

    fun findEntry(platform: Platform, bangid: String): Entry? =
        entries.find { it.platform == platform && it.bangid == bangid }

    fun nameOf(entryId: String): String = findEntry(entryId)?.name ?: "排行榜"

    fun fromPlatform(entryId: String): Platform = findEntry(entryId)?.platform ?: Platform.WY

    const val DEFAULT_PLATFORM_KEY = "wy"
    const val DEFAULT_TOPLIST_ID = "wy__3778678"
}
