package com.theveloper.pixelplay.presentation.focusmode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FocusPhase {
    STUDY,
    BREAK,
    IDLE
}

class FocusTimerState {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var countdownJob: Job? = null

    // 配置
    var studyDurationMinutes by mutableIntStateOf(25)
    var breakDurationMinutes by mutableIntStateOf(5)

    // 运行时状态
    var currentPhase by mutableStateOf(FocusPhase.IDLE)
    var completedCycles by mutableIntStateOf(0)
    var isRunning by mutableStateOf(false)

    // 剩余时间（毫秒）
    var remainingTimeMs by mutableLongStateOf(0L)

    private val studyDurationMs: Long
        get() = studyDurationMinutes * 60 * 1000L
    private val breakDurationMs: Long
        get() = breakDurationMinutes * 60 * 1000L

    fun start() {
        if (isRunning) return
        isRunning = true
        if (currentPhase == FocusPhase.IDLE) {
            currentPhase = FocusPhase.STUDY
            remainingTimeMs = studyDurationMs
        }
        startCountdown()
    }

    fun pause() {
        isRunning = false
        countdownJob?.cancel()
        countdownJob = null
    }

    fun resume() {
        if (isRunning || remainingTimeMs <= 0) return
        isRunning = true
        startCountdown()
    }

    fun stop() {
        isRunning = false
        countdownJob?.cancel()
        countdownJob = null
        currentPhase = FocusPhase.IDLE
        completedCycles = 0
        remainingTimeMs = 0L
    }

    fun skipToNextPhase() {
        countdownJob?.cancel()
        countdownJob = null
        when (currentPhase) {
            FocusPhase.STUDY -> {
                currentPhase = FocusPhase.BREAK
                remainingTimeMs = breakDurationMs
            }
            FocusPhase.BREAK -> {
                currentPhase = FocusPhase.STUDY
                remainingTimeMs = studyDurationMs
            }
            FocusPhase.IDLE -> {
                currentPhase = FocusPhase.STUDY
                remainingTimeMs = studyDurationMs
            }
        }
        if (isRunning) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (remainingTimeMs > 0 && isRunning) {
                delay(1000L)
                if (!isRunning) break
                remainingTimeMs = (remainingTimeMs - 1000L).coerceAtLeast(0L)
            }
            if (remainingTimeMs <= 0 && isRunning) {
                // 阶段结束
                when (currentPhase) {
                    FocusPhase.STUDY -> {
                        completedCycles += 1
                        currentPhase = FocusPhase.BREAK
                        remainingTimeMs = breakDurationMs
                    }
                    FocusPhase.BREAK -> {
                        currentPhase = FocusPhase.STUDY
                        remainingTimeMs = studyDurationMs
                    }
                    else -> {}
                }
                // 继续下一阶段
                startCountdown()
            }
        }
    }

    fun resetWithConfig(studyMin: Int, breakMin: Int) {
        stop()
        studyDurationMinutes = studyMin
        breakDurationMinutes = breakMin
    }

    fun formatTime(): String {
        val totalSeconds = (remainingTimeMs / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun getCurrentPhaseDurationMinutes(): Int {
        return when (currentPhase) {
            FocusPhase.STUDY -> studyDurationMinutes
            FocusPhase.BREAK -> breakDurationMinutes
            FocusPhase.IDLE -> 0
        }
    }

    fun getProgress(): Float {
        val totalMs = when (currentPhase) {
            FocusPhase.STUDY -> studyDurationMs
            FocusPhase.BREAK -> breakDurationMs
            FocusPhase.IDLE -> return 0f
        }
        if (totalMs <= 0) return 0f
        return 1f - (remainingTimeMs.toFloat() / totalMs.toFloat())
    }
}
