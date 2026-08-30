package com.theveloper.pixelplay.data.hearingguard

import kotlinx.serialization.Serializable

/**
 * 性别
 */
@Serializable
enum class Gender {
    MALE, FEMALE
}

/**
 * 听力保护配置
 */
@Serializable
data class HearingGuardConfig(
    val gender: Gender = Gender.MALE,
    val age: Int = 18
)

/**
 * 听力保护每日计划（根据 WHO 安全听音指南计算）
 */
data class ListeningPlan(
    /** 每次最长听歌时间（分钟） */
    val sessionLimitMinutes: Int,
    /** 每次听歌后需要休息的时间（分钟） */
    val restMinutes: Int,
    /** 每日总听音上限（分钟） */
    val dailyLimitMinutes: Int
)

/**
 * 听力保护实时状态
 */
data class HearingGuardState(
    /** 是否已配置 */
    val isConfigured: Boolean = false,
    /** 功能是否启用 */
    val enabled: Boolean = true,
    /** 配置信息 */
    val config: HearingGuardConfig? = null,
    /** 当前计划 */
    val plan: ListeningPlan? = null,
    /** 今日已听音时间（毫秒） */
    val todayListeningMs: Long = 0L,
    /** 当前连续听音时间（毫秒，不包含暂停时间） */
    val currentSessionMs: Long = 0L,
    /** 当前是否正在播放 */
    val isPlaying: Boolean = false,
    /** 是否已触发休息提醒 */
    val restReminderTriggered: Boolean = false
) {
    /** 每日计划进度 (0f ~ 1f+) */
    val dailyProgress: Float
        get() = plan?.let {
            if (it.dailyLimitMinutes <= 0) 0f
            else (todayListeningMs / 60_000f) / it.dailyLimitMinutes
        } ?: 0f

    /** 当前会话进度 (0f ~ 1f+) */
    val sessionProgress: Float
        get() = plan?.let {
            if (it.sessionLimitMinutes <= 0) 0f
            else (currentSessionMs / 60_000f) / it.sessionLimitMinutes
        } ?: 0f

    /** 今日剩余分钟数 */
    val remainingMinutes: Int
        get() = plan?.let {
            ((it.dailyLimitMinutes * 60_000L - todayListeningMs) / 60_000L).coerceAtLeast(0).toInt()
        } ?: 0

    /** 当前会话剩余分钟数 */
    val sessionRemainingMinutes: Int
        get() = plan?.let {
            ((it.sessionLimitMinutes * 60_000L - currentSessionMs) / 60_000L).coerceAtLeast(0).toInt()
        } ?: 0
}
