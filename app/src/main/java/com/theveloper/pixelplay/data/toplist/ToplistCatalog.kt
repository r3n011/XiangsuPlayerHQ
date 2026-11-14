package com.theveloper.pixelplay.data.toplist

import androidx.annotation.StringRes

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

/**
 * 排行榜静态目录（对齐落雪音乐 musicSdk 各平台 leaderboard.js 的 list 精简版）。
 * 落雪维护的榜单接口经常变动，静态目录是最稳的兜底：各平台榜单 id 是官方长期稳定的。
 */
object ToplistCatalog {

    /** 音源平台（对齐落雪 musicSdk source 标识） */
    enum class Platform(val key: String, @StringRes private val labelRes: Int) {
        WY("wy", R.string.ui_residue_source_badge_netease),
        TX("tx", R.string.ui_qq_music),
        KG("kg", R.string.ui_residue_source_badge_kugou);

        val label: String
            get() = AppLocaleManager.getString(labelRes)

        companion object {
            fun fromKey(key: String): Platform = entries.firstOrNull { it.key == key } ?: WY
        }
    }

    /** 单个榜单。id 全局唯一，格式 "{platform}__{bangid}"（对齐落雪） */
    data class Entry(
        val id: String,
        @StringRes private val nameRes: Int,
        @StringRes private val subtitleRes: Int?,
        val bangid: String
    ) {
        val name: String get() = AppLocaleManager.getString(nameRes)
        val subtitle: String get() = subtitleRes?.let { AppLocaleManager.getString(it) }.orEmpty()
        val platformKey: String get() = id.substringBefore("__")
        val platform: Platform get() = Platform.fromKey(platformKey)
    }

    private fun e(platform: Platform, bangid: String, @StringRes name: Int, @StringRes subtitle: Int? = null): Entry =
        Entry(id = "${platform.key}__$bangid", nameRes = name, subtitleRes = subtitle, bangid = bangid)

    // ── 网易云（官方歌单，走 getPlaylistDetail 明文 API）──────────────────────
    // 对齐落雪 wy/leaderboard.js 最新 topList（每平台取 10 个）
    private val wyEntries = listOf(
        e(Platform.WY, "19723756", R.string.ui_rising_chart, R.string.ui_rising_popularity),
        e(Platform.WY, "3779629", R.string.ui_new_songs_chart, R.string.ui_latest_releases),
        e(Platform.WY, "2884035", R.string.ui_original_songs_chart, R.string.ui_original_music),
        e(Platform.WY, "3778678", R.string.ui_hot_songs_chart, R.string.ui_most_popular_overall),
        e(Platform.WY, "991319590", R.string.ui_rap_chart, R.string.ui_popular_rap),
        e(Platform.WY, "71384707", R.string.ui_classical_chart, R.string.ui_classical_music),
        e(Platform.WY, "1978921795", R.string.ui_electronic_chart, R.string.ui_popular_electronic_music),
        e(Platform.WY, "5453912201", R.string.ui_black_vinyl_vip_favorites, R.string.ui_black_vinyl_vip),
        e(Platform.WY, "71385702", R.string.ui_acg_chart, R.string.ui_anime_comics_and_games),
        e(Platform.WY, "745956260", R.string.ui_korean_chart, R.string.ui_popular_korean_music)
    )

    // ── QQ音乐（musicu.fcg ToplistInfoServer GetDetail）────────────────────
    // 对齐落雪 tx/leaderboard.js 最新 boardList
    private val txEntries = listOf(
        e(Platform.TX, "4", R.string.ui_popularity_index_chart, R.string.ui_trending_pop),
        e(Platform.TX, "26", R.string.ui_hot_songs_chart, R.string.ui_popular_songs),
        e(Platform.TX, "27", R.string.ui_new_songs_chart, R.string.ui_latest_releases),
        e(Platform.TX, "62", R.string.ui_rising_chart, R.string.ui_rising_popularity),
        e(Platform.TX, "58", R.string.ui_rap_chart, R.string.ui_popular_rap),
        e(Platform.TX, "57", R.string.ui_heineken_electronic_chart, R.string.ui_popular_electronic_music),
        e(Platform.TX, "28", R.string.ui_internet_songs_chart, R.string.ui_popular_online),
        e(Platform.TX, "5", R.string.ui_mainland_china_chart, R.string.ui_popular_in_mainland_china),
        e(Platform.TX, "3", R.string.ui_western_music_chart, R.string.ui_popular_western_music),
        e(Platform.TX, "59", R.string.ui_hong_kong_chart, R.string.ui_popular_in_hong_kong)
    )

    // ── 酷狗（mobilecdnbj.kugou.com v3/rank/song）───────────────────────────
    // 对齐落雪 kg/leaderboard.js 最新 boardList
    private val kgEntries = listOf(
        e(Platform.KG, "8888", R.string.ui_chart_top500, R.string.ui_most_popular_overall),
        e(Platform.KG, "6666", R.string.ui_rising_chart, R.string.ui_rising_popularity),
        e(Platform.KG, "59703", R.string.ui_hummingbird_pop_chart, R.string.ui_hummingbird_music),
        e(Platform.KG, "52144", R.string.ui_douyin_hits_chart, R.string.ui_popular_on_douyin),
        e(Platform.KG, "52767", R.string.ui_kuaishou_hits_chart, R.string.ui_popular_on_kuaishou),
        e(Platform.KG, "24971", R.string.ui_dj_hits_chart, R.string.ui_popular_dj_tracks),
        e(Platform.KG, "23784", R.string.ui_internet_hits_chart, R.string.ui_popular_online),
        e(Platform.KG, "44412", R.string.ui_rap_pioneers_chart, R.string.ui_popular_rap),
        e(Platform.KG, "31308", R.string.ui_mainland_china_chart, R.string.ui_popular_in_mainland_china),
        e(Platform.KG, "33160", R.string.ui_electronic_chart, R.string.ui_popular_electronic_music)
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

    fun nameOf(entryId: String): String = findEntry(entryId)?.name ?: AppLocaleManager.getString(R.string.ui_residue_home_toplist_title)

    fun fromPlatform(entryId: String): Platform = findEntry(entryId)?.platform ?: Platform.WY

    const val DEFAULT_PLATFORM_KEY = "wy"
    const val DEFAULT_TOPLIST_ID = "wy__3778678"
}
