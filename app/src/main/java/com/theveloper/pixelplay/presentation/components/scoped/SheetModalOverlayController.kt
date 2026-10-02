package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SaveQueueOverlayData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns transient overlay state that is coupled to the sheet scene:
 * - save queue modal
 * - song info modal selection
 */
internal class SheetModalOverlayController(
    private val scope: CoroutineScope,
    private val queueSheetControllerProvider: () -> QueueSheetController,
    private val animationDurationMs: Int,
    private val onCollapsePlayerSheet: () -> Unit
) {
    var pendingSaveQueueOverlay: SaveQueueOverlayData? by mutableStateOf(null)
        private set

    var selectedSongForInfo: Song? by mutableStateOf(null)
        private set

    /**
     * 歌曲信息卡片打开时落在哪一页（0 = 操作页，1 = 歌曲信息详情页）。
     * 播放器「⋯」菜单点「歌曲信息」时带 1，直接落到信息卡片；
     * 队列内歌曲点击等其它入口默认 0（先给操作）。
     */
    var songInfoInitialPage: Int by mutableIntStateOf(0)
        private set

    fun updateSelectedSongForInfo(song: Song?, initialPage: Int = 0) {
        selectedSongForInfo = song
        songInfoInitialPage = initialPage
    }

    fun dismissSaveQueueOverlay() {
        pendingSaveQueueOverlay = null
    }

    fun launchSaveQueueOverlay(
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) {
        if (pendingSaveQueueOverlay != null) return
        scope.launch {
            queueSheetControllerProvider().animateTo(false)
            onCollapsePlayerSheet()
            delay(animationDurationMs.toLong())
            pendingSaveQueueOverlay = SaveQueueOverlayData(songs, defaultName, onConfirm)
        }
    }
}

@Composable
internal fun rememberSheetModalOverlayController(
    scope: CoroutineScope,
    queueSheetController: QueueSheetController,
    animationDurationMs: Int,
    onCollapsePlayerSheet: () -> Unit
): SheetModalOverlayController {
    val queueSheetControllerState = rememberUpdatedState(queueSheetController)
    val onCollapsePlayerSheetState = rememberUpdatedState(onCollapsePlayerSheet)

    return remember(scope, animationDurationMs) {
        SheetModalOverlayController(
            scope = scope,
            queueSheetControllerProvider = { queueSheetControllerState.value },
            animationDurationMs = animationDurationMs,
            onCollapsePlayerSheet = { onCollapsePlayerSheetState.value() }
        )
    }
}
