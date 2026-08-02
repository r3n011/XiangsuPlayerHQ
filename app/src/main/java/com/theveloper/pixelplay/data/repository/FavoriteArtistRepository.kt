package com.theveloper.pixelplay.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theveloper.pixelplay.data.model.FavoriteArtist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** 独立 DataStore：收藏的歌手（与 settings 数据隔离，避免影响现有备份/恢复逻辑） */
private val Context.favoriteArtistsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorite_artists"
)

/**
 * 收藏歌手仓库
 *
 * 将收藏的网易云歌手持久化为 JSON 列表（新增收藏排在前面），
 * 通过 Flow 对外暴露，供歌手主页收藏按钮与主页收藏歌手卡片使用。
 */
@Singleton
class FavoriteArtistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val key = stringPreferencesKey("favorite_artists_json_v1")

    /** 收藏的歌手列表（按收藏时间倒序） */
    val artists: Flow<List<FavoriteArtist>> = context.favoriteArtistsDataStore.data
        .map { prefs ->
            prefs[key]?.let { raw ->
                runCatching { json.decodeFromString<List<FavoriteArtist>>(raw) }
                    .onFailure { Timber.w(it, "解析收藏歌手数据失败") }
                    .getOrDefault(emptyList())
            } ?: emptyList()
        }
        .map { list -> list.sortedByDescending { it.addedAt } }
        .distinctUntilChanged()

    suspend fun isFavorite(id: Long): Boolean = artistsOnce().any { it.id == id }

    suspend fun artistsOnce(): List<FavoriteArtist> = artists.first()

    suspend fun add(artist: FavoriteArtist) {
        context.favoriteArtistsDataStore.edit { prefs ->
            val current = decode(prefs[key])
            val updated = (current.filterNot { it.id == artist.id } + artist)
                .sortedByDescending { it.addedAt }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun remove(id: Long) {
        context.favoriteArtistsDataStore.edit { prefs ->
            val updated = decode(prefs[key]).filterNot { it.id == id }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun toggle(artist: FavoriteArtist) {
        if (isFavorite(artist.id)) remove(artist.id) else add(artist)
    }

    private fun decode(raw: String?): List<FavoriteArtist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<FavoriteArtist>>(raw) }
            .onFailure { Timber.w(it, "解析收藏歌手数据失败") }
            .getOrDefault(emptyList())
    }
}
