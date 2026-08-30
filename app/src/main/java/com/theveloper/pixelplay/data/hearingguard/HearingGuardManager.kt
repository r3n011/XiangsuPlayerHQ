package com.theveloper.pixelplay.data.hearingguard

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.preferences.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 听力保护管理器
 *
 * 基于 WHO 安全听音指南，根据用户年龄和性别计算每日听音计划，
 * 跟踪实时听音时长，在达到限制时自动暂停并提醒休息。
 */
@Singleton
class HearingGuardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_CONFIG = stringPreferencesKey("hearing_guard_config")
        private val KEY_ENABLED = booleanPreferencesKey("hearing_guard_enabled")
        private val KEY_TODAY_DATE = stringPreferencesKey("hearing_guard_today_date")
        private val KEY_TODAY_MS = longPreferencesKey("hearing_guard_today_ms")
    }

    private val _state = MutableStateFlow(HearingGuardState())
    val state: StateFlow<HearingGuardState> = _state.asStateFlow()

    // 追踪间隔（1秒）
    private val TICK_INTERVAL_MS = 1000L

    // 上次 tick 的时间戳
    private var lastTickTimestamp: Long = 0L

    // 内部是否正在播放
    private var internalIsPlaying = false

    init {
        scope.launch {
            loadConfig()
            startTickLoop()
        }
    }

    /**
     * 根据年龄和性别计算听音计划
     *
     * 参考 WHO 安全听音指南：
     * - 儿童 (≤12岁): 更严格限制
     * - 青少年 (13-17岁): 中等限制
     * - 成人 (18-64岁): 标准限制
     * - 老年人 (65+): 稍宽松
     */
    private fun calculatePlan(gender: Gender, age: Int): ListeningPlan {
        val isMale = gender == Gender.MALE
        return when {
            age <= 12 -> ListeningPlan(
                sessionLimitMinutes = if (isMale) 30 else 25,
                restMinutes = 15,
                dailyLimitMinutes = if (isMale) 60 else 50
            )
            age <= 17 -> ListeningPlan(
                sessionLimitMinutes = if (isMale) 45 else 40,
                restMinutes = 15,
                dailyLimitMinutes = if (isMale) 90 else 80
            )
            age <= 64 -> ListeningPlan(
                sessionLimitMinutes = if (isMale) 60 else 50,
                restMinutes = 15,
                dailyLimitMinutes = if (isMale) 120 else 100
            )
            else -> ListeningPlan(
                sessionLimitMinutes = if (isMale) 50 else 45,
                restMinutes = 20,
                dailyLimitMinutes = if (isMale) 90 else 80
            )
        }
    }

    /**
     * 设置听力保护配置
     */
    suspend fun setConfig(config: HearingGuardConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CONFIG] = json.encodeToString(config)
        }
        val plan = calculatePlan(config.gender, config.age)
        _state.value = _state.value.copy(
            isConfigured = true,
            config = config,
            plan = plan
        )
    }

    /**
     * 清除配置（禁用听力保护）
     */
    suspend fun clearConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CONFIG)
            prefs.remove(KEY_ENABLED)
        }
        _state.value = HearingGuardState()
    }

    /**
     * 启用/禁用听力保护功能（保留配置）
     */
    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
        _state.value = _state.value.copy(
            enabled = enabled,
            currentSessionMs = if (!enabled) 0L else _state.value.currentSessionMs,
            restReminderTriggered = false
        )
    }

    /**
     * 通知播放状态变化
     */
    fun onPlaybackStateChanged(playing: Boolean) {
        val prev = internalIsPlaying
        internalIsPlaying = playing
        _state.value = _state.value.copy(isPlaying = playing)

        if (playing && !prev) {
            // 从暂停恢复播放，重置 lastTickTimestamp
            lastTickTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * 用户确认休息（关闭提醒弹窗）
     */
    fun onRestConfirmed() {
        _state.value = _state.value.copy(restReminderTriggered = false)
    }

    /**
     * 用户选择继续听（忽略本次会话提醒，重置当前会话计时）
     */
    fun onContinueListening() {
        _state.value = _state.value.copy(
            restReminderTriggered = false,
            currentSessionMs = 0L
        )
    }

    private suspend fun loadConfig() {
        val prefs = context.dataStore.data.first()
        val configJson = prefs[KEY_CONFIG]
        val enabled = prefs[KEY_ENABLED] ?: true
        if (configJson != null) {
            try {
                val config = json.decodeFromString<HearingGuardConfig>(configJson)
                val plan = calculatePlan(config.gender, config.age)
                val todayMs = loadTodayMs(prefs)
                _state.value = _state.value.copy(
                    isConfigured = true,
                    enabled = enabled,
                    config = config,
                    plan = plan,
                    todayListeningMs = todayMs
                )
            } catch (_: Exception) {
                // malformed config, ignore
            }
        }
    }

    private fun loadTodayMs(prefs: androidx.datastore.preferences.core.Preferences): Long {
        val savedDate = prefs[KEY_TODAY_DATE]
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return if (savedDate == today) {
            prefs[KEY_TODAY_MS] ?: 0L
        } else {
            // 新的一天，重置
            scope.launch { saveTodayMs(0L, today) }
            0L
        }
    }

    private suspend fun saveTodayMs(ms: Long, date: String? = null) {
        val today = date ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        context.dataStore.edit { prefs ->
            prefs[KEY_TODAY_DATE] = today
            prefs[KEY_TODAY_MS] = ms
        }
    }

    private suspend fun startTickLoop() {
        lastTickTimestamp = System.currentTimeMillis()
        while (true) {
            delay(TICK_INTERVAL_MS)
            val currentState = _state.value
            if (!currentState.isConfigured || !currentState.enabled || !internalIsPlaying) continue

            val now = System.currentTimeMillis()
            val elapsed = now - lastTickTimestamp
            lastTickTimestamp = now

            // 防止异常大的 elapsed（比如系统休眠）
            val safeElapsed = elapsed.coerceAtMost(5000L)

            val newTodayMs = currentState.todayListeningMs + safeElapsed
            val newSessionMs = currentState.currentSessionMs + safeElapsed
            val plan = currentState.plan ?: continue

            var shouldTriggerRest = false

            // 检查会话限制
            if (newSessionMs >= plan.sessionLimitMinutes * 60_000L) {
                shouldTriggerRest = true
            }

            // 检查每日限制
            val dailyExhausted = newTodayMs >= plan.dailyLimitMinutes * 60_000L

            _state.value = currentState.copy(
                todayListeningMs = newTodayMs,
                currentSessionMs = newSessionMs,
                restReminderTriggered = shouldTriggerRest || dailyExhausted
            )

            // 保存今日听音时长（每10秒保存一次，减少IO）
            if (newTodayMs / 10_000L != currentState.todayListeningMs / 10_000L) {
                saveTodayMs(newTodayMs)
            }
        }
    }
}
