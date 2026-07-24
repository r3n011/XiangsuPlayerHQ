package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val ALBUM_ART_COLOR_ACCURACY = intPreferencesKey("album_art_color_accuracy_v1")
        val CUSTOM_PALETTE_SEED_COLOR = intPreferencesKey("custom_palette_seed_color_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        // ⚡ 自定义调色盘启用开关：关闭时调色盘不影响全局主题
        val CUSTOM_PALETTE_ENABLED = stringPreferencesKey("custom_palette_enabled_v1")
    }

    companion object {
        // 默认种子色：蓝色（避免 Android 10 等无系统取色时默认紫色）
        const val DEFAULT_CUSTOM_PALETTE_SEED = 0xFF2196F3.toInt()
        // 默认启用自定义调色盘
        const val DEFAULT_CUSTOM_PALETTE_ENABLED = true
    }

    val appThemeModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM
    }

    val playerThemePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.ALBUM_ART
    }

    val albumArtPaletteStyleFlow: Flow<AlbumArtPaletteStyle> = dataStore.data.map { preferences ->
        AlbumArtPaletteStyle.fromStorageKey(preferences[Keys.ALBUM_ART_PALETTE_STYLE])
    }

    val albumArtColorAccuracyFlow: Flow<Int> = dataStore.data.map { preferences ->
        AlbumArtColorAccuracy.clamp(preferences[Keys.ALBUM_ART_COLOR_ACCURACY] ?: AlbumArtColorAccuracy.DEFAULT)
    }

    val customPaletteSeedColorFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PALETTE_SEED_COLOR] ?: DEFAULT_CUSTOM_PALETTE_SEED
    }

    // ⚡ 自定义调色盘启用状态 Flow
    val customPaletteEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PALETTE_ENABLED]?.toBooleanStrictOrNull() ?: DEFAULT_CUSTOM_PALETTE_ENABLED
    }

    suspend fun setPlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
        }

    suspend fun setAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_THEME_MODE] = themeMode
        }

    suspend fun initializeAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_THEME_MODE] == null) {
                preferences[Keys.APP_THEME_MODE] = themeMode
            }
        }

    suspend fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        }

    suspend fun setAlbumArtColorAccuracy(level: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(level)
        }

    suspend fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) = dataStore.edit { preferences ->
        preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(accuracyLevel)
    }

    suspend fun setCustomPaletteSeedColor(seedColor: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PALETTE_SEED_COLOR] = seedColor
        }

    // ⚡ 设置自定义调色盘启用状态
    suspend fun setCustomPaletteEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PALETTE_ENABLED] = enabled.toString()
        }
}
