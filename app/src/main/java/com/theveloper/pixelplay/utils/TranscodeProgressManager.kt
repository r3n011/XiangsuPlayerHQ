package com.theveloper.pixelplay.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TranscodeProgress(
    val isTranscoding: Boolean = false,
    val progressPercent: Int = 0,
    val stage: String = "",
    val fileName: String = ""
)

object TranscodeProgressManager {
    private val _progress = MutableStateFlow(TranscodeProgress())
    val progress: StateFlow<TranscodeProgress> = _progress.asStateFlow()

    fun start(fileName: String) {
        _progress.value = TranscodeProgress(
            isTranscoding = true,
            progressPercent = 0,
            stage = "开始转码...",
            fileName = fileName
        )
    }

    fun update(percent: Int, stage: String) {
        _progress.update {
            it.copy(
                isTranscoding = true,
                progressPercent = percent.coerceIn(0, 100),
                stage = stage
            )
        }
    }

    fun complete() {
        _progress.value = TranscodeProgress(isTranscoding = false)
    }

    fun reset() {
        _progress.value = TranscodeProgress()
    }
}