package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 在线歌词来源（可自由排序，列表顺序即优先级）。
 *
 * 国内网络环境下 LRCLIB / MusicBrainz 等海外服务经常不可达，
 * 用户可以在「API 管理」里把可用的接口逐个上移/下移，而不是只选三个预设档位。
 *
 * ⚡ 新增歌词来源时只要在这里加一个枚举值，UI 与解析链会自动纳入
 * （已保存的顺序里缺少的新来源会自动补到末尾）。
 */
enum class LyricsSourceKey(val displayName: String, val stepKey: String) {
    NETEASE("网易云", "netease"),
    AMLL("AMLL 逐字", "amll"),
    BUILT_IN("内置源", "builtin"),
    BILIBILI("B 站", "bilibili"),
    LRCLIB("LRCLIB", "lrclib");

    companion object {
        /** 默认顺序（与原「网易云优先」预设一致） */
        val DEFAULT_ORDER: List<LyricsSourceKey> =
            listOf(NETEASE, AMLL, BUILT_IN, BILIBILI, LRCLIB)

        fun fromName(name: String?): LyricsSourceKey? =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/** 一次读取的 API 开关快照（供 Repository 在单次请求内使用，避免多次读 DataStore） */
data class ApiProviderSnapshot(
    val neteaseLyricsEnabled: Boolean = true,
    val amllLyricsEnabled: Boolean = true,
    val lrclibLyricsEnabled: Boolean = true,
    val builtInLyricsEnabled: Boolean = true,
    val bilibiliLyricsEnabled: Boolean = true,
    /** 歌词来源顺序（列表顺序即优先级） */
    val lyricsSourceOrder: List<LyricsSourceKey> = LyricsSourceKey.DEFAULT_ORDER,
    val musicBrainzEnabled: Boolean = true,
    val deezerArtistEnabled: Boolean = true
)

/**
 * 外部 API 开关与优先级偏好（歌词来源 + 歌曲信息补全来源）。
 *
 * 与 [UserPreferencesRepository] 共用同一个 DataStore（settings）。
 */
@Singleton
class ApiProviderPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val LYRICS_NETEASE_ENABLED = booleanPreferencesKey("api_lyrics_netease_enabled")
        val LYRICS_AMLL_ENABLED = booleanPreferencesKey("api_lyrics_amll_enabled")
        val LYRICS_LRCLIB_ENABLED = booleanPreferencesKey("api_lyrics_lrclib_enabled")
        val LYRICS_BUILTIN_ENABLED = booleanPreferencesKey("api_lyrics_builtin_enabled")
        val LYRICS_BILIBILI_ENABLED = booleanPreferencesKey("api_lyrics_bilibili_enabled")
        val LYRICS_SOURCE_ORDER = stringPreferencesKey("api_lyrics_source_order")
        val METADATA_MUSICBRAINZ_ENABLED = booleanPreferencesKey("api_metadata_musicbrainz_enabled")
        val METADATA_DEEZER_ARTIST_ENABLED = booleanPreferencesKey("api_metadata_deezer_artist_enabled")
    }

    // ─── 歌词来源开关 ───────────────────────────────────────────────

    /** 网易云歌词（官方 NCM SDK / 镜像兜底） */
    val neteaseLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LYRICS_NETEASE_ENABLED] ?: true }

    /** AMLLDB 逐字歌词（仅网易云歌曲） */
    val amllLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LYRICS_AMLL_ENABLED] ?: true }

    /** LRCLIB（海外，国内常不可用） */
    val lrclibLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LYRICS_LRCLIB_ENABLED] ?: true }

    /** 内置源歌词（QQ / 酷狗 / 酷我 / 咪咕） */
    val builtInLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LYRICS_BUILTIN_ENABLED] ?: true }

    /** B 站视频字幕歌词 */
    val bilibiliLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.LYRICS_BILIBILI_ENABLED] ?: true }

    /** 歌词来源顺序（列表顺序即优先级） */
    val lyricsSourceOrder: Flow<List<LyricsSourceKey>> =
        dataStore.data.map { parseSourceOrder(it[Keys.LYRICS_SOURCE_ORDER]) }

    // ─── 歌曲信息补全开关 ───────────────────────────────────────────

    /** MusicBrainz + CoverArtArchive 元数据/封面补全（海外，国内常不可用） */
    val musicBrainzEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.METADATA_MUSICBRAINZ_ENABLED] ?: true }

    /** Deezer 艺术家图片补全（海外，国内常不可用） */
    val deezerArtistEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.METADATA_DEEZER_ARTIST_ENABLED] ?: true }

    /** 单次读取全部开关，供 Repository 在一次请求内使用 */
    suspend fun current(): ApiProviderSnapshot {
        val prefs = dataStore.data.first()
        return ApiProviderSnapshot(
            neteaseLyricsEnabled = prefs[Keys.LYRICS_NETEASE_ENABLED] ?: true,
            amllLyricsEnabled = prefs[Keys.LYRICS_AMLL_ENABLED] ?: true,
            lrclibLyricsEnabled = prefs[Keys.LYRICS_LRCLIB_ENABLED] ?: true,
            builtInLyricsEnabled = prefs[Keys.LYRICS_BUILTIN_ENABLED] ?: true,
            bilibiliLyricsEnabled = prefs[Keys.LYRICS_BILIBILI_ENABLED] ?: true,
            lyricsSourceOrder = parseSourceOrder(prefs[Keys.LYRICS_SOURCE_ORDER]),
            musicBrainzEnabled = prefs[Keys.METADATA_MUSICBRAINZ_ENABLED] ?: true,
            deezerArtistEnabled = prefs[Keys.METADATA_DEEZER_ARTIST_ENABLED] ?: true
        )
    }

    // ─── setters ───────────────────────────────────────────────────

    suspend fun setNeteaseLyricsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LYRICS_NETEASE_ENABLED] = enabled }

    suspend fun setAmllLyricsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LYRICS_AMLL_ENABLED] = enabled }

    suspend fun setLrclibLyricsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LYRICS_LRCLIB_ENABLED] = enabled }

    suspend fun setBuiltInLyricsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LYRICS_BUILTIN_ENABLED] = enabled }

    suspend fun setBilibiliLyricsEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.LYRICS_BILIBILI_ENABLED] = enabled }

    /** 保存歌词来源顺序（列表顺序即优先级） */
    suspend fun setLyricsSourceOrder(order: List<LyricsSourceKey>) =
        dataStore.edit { it[Keys.LYRICS_SOURCE_ORDER] = order.joinToString(",") { key -> key.name } }

    /**
     * 解析已保存的顺序：
     * 未知/重复项丢弃，缺失的来源按默认顺序补到末尾（新增来源免迁移）。
     */
    private fun parseSourceOrder(raw: String?): List<LyricsSourceKey> {
        val saved = raw.orEmpty()
            .split(',')
            .mapNotNull { LyricsSourceKey.fromName(it) }
            .distinct()
        return if (saved.isEmpty()) {
            LyricsSourceKey.DEFAULT_ORDER
        } else {
            saved + LyricsSourceKey.DEFAULT_ORDER.filterNot { it in saved }
        }
    }

    suspend fun setMusicBrainzEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.METADATA_MUSICBRAINZ_ENABLED] = enabled }

    suspend fun setDeezerArtistEnabled(enabled: Boolean) =
        dataStore.edit { it[Keys.METADATA_DEEZER_ARTIST_ENABLED] = enabled }
}
