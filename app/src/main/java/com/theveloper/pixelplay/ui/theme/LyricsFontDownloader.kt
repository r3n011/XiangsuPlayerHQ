package com.theveloper.pixelplay.ui.theme

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 歌词字体下载的全局状态。
 *
 * 与 [com.theveloper.pixelplay.utils.KuromojiEngine] 的下载状态同构，二者复用同一个
 * 顶部提示 chip（`DownloadStatusTopChip`），所以字体下载时也会从顶部滑入提示。
 *
 * 注意：字体选择界面（LyricsMoreBottomSheet）是 ModalBottomSheet，位于独立窗口、
 * 在 Activity 主窗口之上，因此字体下载的 chip 需要挂在**该 sheet 自己的内容里**，
 * 挂在 MainActivity 根部会被 sheet 的遮罩盖住。
 */
object LyricsFontDownloader {

    sealed interface State {
        /** 当前没有下载任务 */
        data object Idle : State

        /** 正在下载（progressPercent 为 -1 表示总长度未知） */
        data class Downloading(
            val key: String,
            val displayName: String,
            val progressPercent: Int
        ) : State

        /** 下载完成（用于顶部 chip 的完成态驻留提示） */
        data class Completed(val key: String, val displayName: String) : State

        /** 下载失败 */
        data class Failed(val key: String, val displayName: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 用于"完成/失败态自动回到 Idle"的驻留计时（与 chip 的驻留时长对齐并留出余量） */
    private val settleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 是否有下载在进行（用于禁止并发下载） */
    val isDownloading: Boolean
        get() = _state.value is State.Downloading

    /** 正在下载的字体 key，未在下载时为 null */
    val downloadingKey: String?
        get() = (_state.value as? State.Downloading)?.key

    /**
     * 下载指定可下载字体，并全程维护 [state]。
     * 并发调用会被拒绝（返回 false），避免同一时间发起多个下载。
     */
    suspend fun download(context: Context, key: String): Boolean {
        val font = downloadableFontForKey(key) ?: return false
        if (isDownloading) return false

        _state.value = State.Downloading(key, font.displayName, -1)
        val success = try {
            downloadLyricsFont(context, key) { percent ->
                _state.value = State.Downloading(key, font.displayName, percent)
            }
        } catch (_: Throwable) {
            false
        }
        _state.value = if (success) {
            State.Completed(key, font.displayName)
        } else {
            State.Failed(key, font.displayName)
        }
        // 顶部 chip 会用这个"完成/失败"态做驻留提示；驻留结束后自动回到 Idle，
        // 否则下次打开字体面板时 chip 会因为"初始就是完成态"再弹一次。
        settleScope.launch {
            delay(SETTLE_HOLD_MS)
            val current = _state.value
            if (current is State.Completed || current is State.Failed) {
                _state.value = State.Idle
            }
        }
        return success
    }

    private const val SETTLE_HOLD_MS = 3500L
}
