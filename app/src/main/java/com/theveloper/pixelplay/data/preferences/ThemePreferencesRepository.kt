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

/**
 * 自定义播放器背景的显示模式：
 *  - [Cover]：铺满（裁剪填满整个区域，保持比例）
 *  - [Stretch]：拉伸（完全拉伸到区域尺寸，比例可能变形）
 */
enum class PlayerBackgroundMode(val storageKey: String) {
    Cover("cover"),
    Stretch("stretch");

    companion object {
        fun fromStorageKey(key: String?): PlayerBackgroundMode =
            entries.firstOrNull { it.storageKey == key } ?: Cover
    }
}

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
        // ⚡ 自定义播放器背景：开关 + 图片 URI + 显示模式 + 模糊半径（0=关闭，dp 值）（应用到播放器界面与歌词界面）
        val CUSTOM_PLAYER_BACKGROUND_ENABLED = booleanPreferencesKey("custom_player_background_enabled_v1")
        val CUSTOM_PLAYER_BACKGROUND_URI = stringPreferencesKey("custom_player_background_uri_v1")
        val CUSTOM_PLAYER_BACKGROUND_MODE = stringPreferencesKey("custom_player_background_mode_v1")
        val CUSTOM_PLAYER_BACKGROUND_BLUR = intPreferencesKey("custom_player_background_blur_v1")
        // ⚡ 播放器控键透明度（百分比，30-100，默认 100=不透明）（应用到播放器界面与歌词界面主控按钮）
        val CUSTOM_PLAYER_CONTROLS_OPACITY = intPreferencesKey("custom_player_controls_opacity_v1")
        // ⚡ 歌词界面上下两侧渐变遮罩开关
        val LYRICS_GRADIENT_OVERLAY_ENABLED = booleanPreferencesKey("lyrics_gradient_overlay_enabled_v1")
    }

    companion object {
        // 默认种子色：蓝色（避免 Android 10 等无系统取色时默认紫色）
        const val DEFAULT_CUSTOM_PALETTE_SEED = 0xFF2196F3.toInt()
        // ⚡ 自定义播放器背景模糊半径上限（dp）
        const val MAX_CUSTOM_BACKGROUND_BLUR = 30
        // ⚡ 播放器控键透明度范围（百分比）
        const val MIN_CUSTOM_CONTROLS_OPACITY = 30
        const val MAX_CUSTOM_CONTROLS_OPACITY = 100
        const val DEFAULT_CUSTOM_CONTROLS_OPACITY = 100
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

    // ⚡ 自定义播放器背景（应用到播放器界面与歌词界面）
    val customPlayerBackgroundEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PLAYER_BACKGROUND_ENABLED] ?: false
    }
    val customPlayerBackgroundUriFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PLAYER_BACKGROUND_URI]
    }
    val customPlayerBackgroundModeFlow: Flow<PlayerBackgroundMode> = dataStore.data.map { preferences ->
        PlayerBackgroundMode.fromStorageKey(preferences[Keys.CUSTOM_PLAYER_BACKGROUND_MODE])
    }
    val customPlayerBackgroundBlurRadiusFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PLAYER_BACKGROUND_BLUR] ?: 0
    }

    // ⚡ 播放器控键透明度（百分比）与歌词渐变遮罩开关（应用到播放器界面与歌词界面）
    val customPlayerControlsOpacityFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.CUSTOM_PLAYER_CONTROLS_OPACITY] ?: DEFAULT_CUSTOM_CONTROLS_OPACITY
    }
    val lyricsGradientOverlayEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LYRICS_GRADIENT_OVERLAY_ENABLED] ?: true
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

    suspend fun setCustomPlayerBackgroundEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PLAYER_BACKGROUND_ENABLED] = enabled
        }

    suspend fun setCustomPlayerBackgroundUri(uri: String?) =
        dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(Keys.CUSTOM_PLAYER_BACKGROUND_URI)
            } else {
                preferences[Keys.CUSTOM_PLAYER_BACKGROUND_URI] = uri
            }
        }

    suspend fun setCustomPlayerBackgroundMode(mode: PlayerBackgroundMode) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PLAYER_BACKGROUND_MODE] = mode.storageKey
        }

    suspend fun setCustomPlayerBackgroundBlurRadius(radius: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PLAYER_BACKGROUND_BLUR] = radius.coerceIn(0, MAX_CUSTOM_BACKGROUND_BLUR)
        }

    suspend fun setCustomPlayerControlsOpacity(opacity: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PLAYER_CONTROLS_OPACITY] =
                opacity.coerceIn(MIN_CUSTOM_CONTROLS_OPACITY, MAX_CUSTOM_CONTROLS_OPACITY)
        }

    suspend fun setLyricsGradientOverlayEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LYRICS_GRADIENT_OVERLAY_ENABLED] = enabled
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
