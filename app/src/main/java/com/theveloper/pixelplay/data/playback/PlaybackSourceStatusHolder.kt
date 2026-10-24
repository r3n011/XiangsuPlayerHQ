package com.theveloper.pixelplay.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SourceResolvePhase {
    IDLE,
    RESOLVING,
    SWITCHED,
    FAILED,
}

data class SourceResolveStatus(
    val phase: SourceResolvePhase = SourceResolvePhase.IDLE,
    val message: String? = null,
    val songTitle: String? = null,
    val source: String? = null,
    val previousSource: String? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * 在线歌曲换源状态（对应 lx-music-mobile 的 setStatusText("正在获取播放链接/尝试切换音源")）。
 *
 * 用 StateFlow 暴露给 UI，同时保留一条短消息用于 Toast。
 */
@Singleton
class PlaybackSourceStatusHolder @Inject constructor() {
    private val _status = MutableStateFlow(SourceResolveStatus())
    val status: StateFlow<SourceResolveStatus> = _status.asStateFlow()

    fun resolving(songTitle: String?, source: String? = null) {
        _status.value = SourceResolveStatus(
            phase = SourceResolvePhase.RESOLVING,
            message = "正在获取播放链接…",
            songTitle = songTitle,
            source = source,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun switched(songTitle: String?, from: String?, to: String?) {
        _status.value = SourceResolveStatus(
            phase = SourceResolvePhase.SWITCHED,
            message = if (!from.isNullOrBlank() && !to.isNullOrBlank() && from != to) {
                "已切换音源：$from → $to"
            } else {
                "已获取播放链接：${to.orEmpty()}"
            },
            songTitle = songTitle,
            source = to,
            previousSource = from,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun failed(songTitle: String?, message: String?) {
        _status.value = SourceResolveStatus(
            phase = SourceResolvePhase.FAILED,
            message = message ?: "所有音源都无法解析这首歌",
            songTitle = songTitle,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun clear() {
        _status.value = SourceResolveStatus()
    }
}
