package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

/** 迷你播放条样式的 DataStore key（设置页与仓库共用） */
val MINI_PLAYER_STYLE_PREF_KEY = stringPreferencesKey("mini_player_style")

/** 迷你播放条圆角的 DataStore key（独立于导航栏圆角） */
val MINI_PLAYER_CORNER_RADIUS_PREF_KEY =
    androidx.datastore.preferences.core.intPreferencesKey("mini_player_corner_radius")

/**
 * 迷你播放条样式。
 */
enum class MiniPlayerStyle(val displayName: String) {
    /** 现有样式：圆形封面 + 上/播/下 三键 + 半透明填充式进度 */
    CLASSIC("经典"),

    /** 1:1 对齐 Rhythm 的 MaterialMiniPlayer：圆角封面 + 上/播/下 三键 + 卡片底 */
    MATERIAL("Material"),

    /** 1:1 对齐 Rhythm 的 ExpressiveMiniPlayer：整条胶囊 + 不透明进度填充 +
     *  方形封面 + 单个大播放键（无上下曲） */
    EXPRESSIVE("Expressive");

    companion object {
        fun fromName(name: String?): MiniPlayerStyle =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
