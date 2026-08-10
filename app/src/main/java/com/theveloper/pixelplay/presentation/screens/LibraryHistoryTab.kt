package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 媒体库「历史」tab：展示最近播放过的歌曲（按播放时间倒序、去重）。
 */
private const val MAX_HISTORY_ITEMS = 5_000

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryHistoryTab(
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val historySongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = MAX_HISTORY_ITEMS
        )
    }
    val historySongsInitialValue: List<Song>? = remember(historySongIds) {
        if (historySongIds.isEmpty()) emptyList<Song>() else null
    }
    val historySourceSongs by remember(historySongIds, playerViewModel) {
        playerViewModel.observeSongs(historySongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = historySongsInitialValue)

    val historyItems = remember(playbackHistory, historySourceSongs) {
        val source = historySourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = source,
            maxItems = MAX_HISTORY_ITEMS
        )
    }

    if (historySourceSongs == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
        return
    }

    if (historyItems.isEmpty()) {
        LibraryExpressiveEmptyState(
            tabId = LibraryTabId.HISTORY,
            storageFilter = StorageFilter.ALL,
            bottomBarHeight = bottomBarHeight
        )
        return
    }

    val queueSongs = remember(historyItems) { historyItems.map { it.song } }
    val queueName = stringResource(R.string.tab_history)
    val pullToRefreshState = rememberPullToRefreshState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = bottomBarHeight + MiniPlayerHeight + 30.dp
                )
            ) {
                items(
                    items = historyItems,
                    key = { item -> item.song.id },
                    contentType = { "history_song" }
                ) { item ->
                    EnhancedSongListItem(
                        song = item.song,
                        isCurrentSong = currentSongId == item.song.id,
                        isPlaying = currentSongId == item.song.id && isPlaying,
                        isRadio = item.song.isRadioStation,
                        onClick = {
                            playerViewModel.playSongs(
                                songsToPlay = queueSongs,
                                startSong = item.song,
                                queueName = queueName
                            )
                        },
                        onMoreOptionsClick = onMoreOptionsClick
                    )
                }
            }
        }
    }
}
