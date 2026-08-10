package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        // ⚡ 应用级调色盘开关：打开 = 使用自定义调色盘染色整个应用（播放器内部除外），
        //    关闭 = 跟随壁纸动态取色
        val APP_PALETTE_ENABLED = booleanPreferencesKey("app_palette_enabled_v1")
    }

    companion object {
        // 默认种子色：蓝色（避免 Android 10 等无系统取色时默认紫色）
        const val DEFAULT_CUSTOM_PALETTE_SEED = 0xFF2196F3.toInt()
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

    // ⚡ 应用级调色盘开关（独立于播放器主题）
    val appPaletteEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.APP_PALETTE_ENABLED] ?: false
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

    suspend fun setAppPaletteEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_PALETTE_ENABLED] = enabled
        }

    /**
     * ⚡ 旧版迁移：v9.3 之前"播放器自定义调色盘"（CUSTOM_PALETTE）会同时染色整个应用。
     *   新版本调色盘与应用主题解耦，播放器不再拥有自定义调色盘选项。
     *   迁移策略：播放器主题回落为封面取色；若用户此前选过自定义调色盘，
     *   则自动打开应用级调色盘（沿用同一种子色），保持应用配色连续。
     */
    suspend fun migrateLegacyPlayerPaletteToAppPalette() {
        val current = dataStore.data.first()
        if (current[Keys.PLAYER_THEME_PREFERENCE] != ThemePreference.CUSTOM_PALETTE) return
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_PALETTE_ENABLED] == null) {
                preferences[Keys.APP_PALETTE_ENABLED] = true
            }
            preferences[Keys.PLAYER_THEME_PREFERENCE] = ThemePreference.ALBUM_ART
        }
    }
}
