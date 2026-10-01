package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

/** 启动动画样式的 DataStore key（UI 与仓库共用） */
val STARTUP_ANIMATION_STYLE_PREF_KEY = stringPreferencesKey("startup_animation_style")

/**
 * 进入主界面时的启动动画样式。
 */
enum class StartupAnimationStyle(val displayName: String) {
    /** 原版 XiangsuPlayer 的浮出效果：内容整体延迟 100ms 后 600ms 淡入 */
    EMERGE("浮出"),

    /** 缩放浮现（Rhythm 风格）：1000ms 淡入 + 0.92→1.0 缩放 */
    SCALE("缩放"),

    /** 直接显示，无过渡动画 */
    NONE("关闭");

    companion object {
        fun fromName(name: String?): StartupAnimationStyle =
            entries.firstOrNull { it.name == name } ?: EMERGE
    }
}
