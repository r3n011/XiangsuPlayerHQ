package com.theveloper.pixelplay.presentation.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.R

/**
 * 设置分类。
 *
 * ⚡ 声明顺序 = 设置首页的排列顺序，统一按「使用频率」从高到低：
 *   1) 日常最常调整：播放、外观、音乐管理、均衡器
 *   2) 在线能力：AI、网页远控、API 管理
 *   3) 低频偏好：行为、备份恢复、更新
 *   4) 特殊设备 / 进阶：设备能力、Glyph 灯带、开发者、关于
 * 新增分类请按同一口径插入，不要直接追加到末尾。
 */
enum class SettingsCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    // ── 1. 日常最常调整 ───────────────────────────────────────────────
    PLAYBACK(
        id = "playback",
        titleRes = R.string.settings_category_playback_title,
        subtitleRes = R.string.settings_category_playback_subtitle,
        icon = Icons.Rounded.MusicNote
    ),
    APPEARANCE(
        id = "appearance",
        titleRes = R.string.settings_category_appearance_title,
        subtitleRes = R.string.settings_category_appearance_subtitle,
        icon = Icons.Rounded.Palette
    ),
    LIBRARY(
        id = "library",
        titleRes = R.string.settings_category_library_title,
        subtitleRes = R.string.settings_category_library_subtitle,
        icon = Icons.Rounded.LibraryMusic
    ),
    EQUALIZER(
        id = "equalizer",
        titleRes = R.string.settings_category_equalizer_title,
        subtitleRes = R.string.settings_category_equalizer_subtitle,
        icon = Icons.Rounded.GraphicEq
    ),

    // ── 2. 在线能力 ──────────────────────────────────────────────────
    AI_INTEGRATION(
        id = "ai_integration",
        titleRes = R.string.settings_category_ai_title,
        subtitleRes = R.string.settings_category_ai_subtitle,
        iconRes = R.drawable.gemini_ai
    ),
    WEB_REMOTE(
        id = "web_remote",
        titleRes = R.string.settings_category_web_remote_title,
        subtitleRes = R.string.settings_category_web_remote_subtitle,
        icon = Icons.Rounded.Science
    ),
    API_MANAGEMENT(
        id = "api_management",
        titleRes = R.string.settings_category_api_title,
        subtitleRes = R.string.settings_category_api_subtitle,
        icon = Icons.Rounded.Cloud
    ),

    // ── 3. 低频偏好 ──────────────────────────────────────────────────
    BEHAVIOR(
        id = "behavior",
        titleRes = R.string.settings_category_behavior_title,
        subtitleRes = R.string.settings_category_behavior_subtitle,
        iconRes = R.drawable.rounded_touch_app_24
    ),
    BACKUP_RESTORE(
        id = "backup_restore",
        titleRes = R.string.settings_category_backup_title,
        subtitleRes = R.string.settings_category_backup_subtitle,
        iconRes = R.drawable.rounded_upload_file_24
    ),
    UPDATES(
        id = "updates",
        titleRes = R.string.settings_category_updates_title,
        subtitleRes = R.string.settings_category_updates_subtitle,
        icon = Icons.Rounded.SystemUpdate
    ),

    // ── 4. 特殊设备 / 进阶（首页单独渲染或垫底）──────────────────────────
    DEVICE_CAPABILITIES(
        id = "device_capabilities",
        titleRes = R.string.settings_category_device_capabilities_title,
        subtitleRes = R.string.settings_category_device_capabilities_subtitle,
        icon = Icons.Rounded.DeveloperBoard
    ),
    GLYPH_MATRIX(
        id = "glyph_matrix",
        titleRes = R.string.settings_category_glyph_matrix_title,
        subtitleRes = R.string.settings_category_glyph_matrix_subtitle,
        icon = Icons.Rounded.GridView
    ),
    DEVELOPER(
        id = "developer",
        titleRes = R.string.settings_category_developer_title,
        subtitleRes = R.string.settings_category_developer_subtitle,
        icon = Icons.Rounded.DeveloperMode
    ),
    ABOUT(
        id = "about",
        titleRes = R.string.settings_category_about_title,
        subtitleRes = R.string.settings_category_about_subtitle,
        icon = Icons.Rounded.Info
    );

    companion object {
        fun fromId(id: String): SettingsCategory? = entries.find { it.id == id }
    }
}
