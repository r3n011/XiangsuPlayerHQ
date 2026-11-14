package com.theveloper.pixelplay.data.preferences

import androidx.annotation.StringRes

import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import androidx.datastore.preferences.core.stringPreferencesKey

/** 启动动画样式的 DataStore key（UI 与仓库共用） */
val STARTUP_ANIMATION_STYLE_PREF_KEY = stringPreferencesKey("startup_animation_style")

/**
 * 进入主界面时的启动动画样式。
 */
enum class StartupAnimationStyle(@StringRes private val displayNameRes: Int) {
    /** 原版 XiangsuPlayer 的浮出效果：内容整体延迟 100ms 后 600ms 淡入 */
    EMERGE(R.string.ui_residue_setcat_startup_style_emerge),

    /** 缩放浮现（Rhythm 风格）：1000ms 淡入 + 0.92→1.0 缩放 */
    SCALE(R.string.ui_residue_presentation_batch_f_scale),

    /** 直接显示，无过渡动画 */
    NONE(R.string.ui_residue_lyrics_more_vibrant_off);

    val displayName: String
        get() = AppLocaleManager.getString(displayNameRes)

    companion object {
        /** 默认样式：缩放（设置里未选过、或存到无效值时都走这个）。 */
        val DEFAULT = SCALE

        fun fromName(name: String?): StartupAnimationStyle =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
