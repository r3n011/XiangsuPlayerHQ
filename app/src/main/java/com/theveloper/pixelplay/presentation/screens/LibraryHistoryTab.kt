package com.theveloper.pixelplay.presentation.screens

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageCompactListTargetSize
import com.theveloper.pixelplay.presentation.components.library.rememberLibraryListGridCells
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.model.RecentlyPlayedSongUiModel
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 媒体库「历史」tab：展示最近播放过的歌曲（按播放时间倒序、去重）。
 *
 * 卡片样式对齐首页「最近播放」卡片：使用专辑取色的胶囊卡片 + 圆形封面，
 * 并额外显示「最后播放时间」，让历史条目承载更多信息。
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

    // ⚡ 媒体库「回到顶部」：注册历史网格的滚动状态（空态 / 加载态不注册，按钮自然隐藏）
    val historyGridState = rememberLazyGridState()
    LibraryScrollToTopRegistration(
        tabId = LibraryTabId.HISTORY.storageKey,
        state = historyGridState
    )

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
            LazyVerticalGrid(
                state = historyGridState,
                columns = rememberLibraryListGridCells(),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    HistorySongCard(
                        item = item,
                        isCurrentSong = currentSongId == item.song.id,
                        isPlaying = currentSongId == item.song.id && isPlaying,
                        themeStateHolder = playerViewModel.themeStateHolder,
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

/**
 * 历史卡片：首页「最近播放」胶囊卡片的加强版 —— 专辑取色容器 + 圆形封面，
 * 标题 / 歌手之外再显示最后播放时间，右侧保留「更多」入口。
 */
@Composable
private fun HistorySongCard(
    item: RecentlyPlayedSongUiModel,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    themeStateHolder: ThemeStateHolder,
    onClick: () -> Unit,
    onMoreOptionsClick: (Song) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val albumColorSchemeState by remember(item.song.albumArtUriString, themeStateHolder) {
        themeStateHolder.getAlbumColorSchemeFlow(item.song.albumArtUriString.orEmpty())
    }.collectAsStateWithLifecycle()

    val albumColorScheme = remember(albumColorSchemeState, isDark) {
        albumColorSchemeState?.let { if (isDark) it.dark else it.light }
    }

    // 正在播放的歌曲用主题主色高亮，其余使用专辑取色（与首页最近播放卡片一致）
    val targetContainerColor = when {
        isCurrentSong -> MaterialTheme.colorScheme.primaryContainer
        else -> albumColorScheme?.primaryContainer ?: MaterialTheme.colorScheme.surfaceContainerLow
    }
    val targetContentColor = when {
        isCurrentSong -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> albumColorScheme?.onPrimaryContainer ?: MaterialTheme.colorScheme.onSurface
    }

    val animatedContainer by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 280),
        label = "historyCardContainer"
    )
    val animatedContent by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = 280),
        label = "historyCardContent"
    )

    // 胶囊（全圆角）卡片，正在播放时收成 16dp 圆角以示区分
    val shape = remember(isCurrentSong) {
        if (isCurrentSong) RoundedCornerShape(16.dp) else RoundedCornerShape(percent = 50)
    }

    val playedAtLabel = remember(item.lastPlayedTimestamp) {
        formatHistoryPlayedAt(item.lastPlayedTimestamp)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = animatedContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmartImage(
                model = item.song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                shape = CircleShape,
                targetSize = SmartImageCompactListTargetSize,
                modifier = Modifier.size(46.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = animatedContent
                )
                Text(
                    text = item.song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = animatedContent.copy(alpha = 0.80f)
                )
                Text(
                    text = playedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = animatedContent.copy(alpha = 0.62f)
                )
            }
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier.size(width = 18.dp, height = 16.dp),
                    color = animatedContent,
                    isPlaying = isPlaying
                )
            }
            FilledIconButton(
                onClick = { onMoreOptionsClick(item.song) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = animatedContent.copy(alpha = 0.12f),
                    contentColor = animatedContent
                ),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(
                        R.string.presentation_batch_g_list_cd_more_for_title,
                        item.song.title
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 最后播放时间：由系统按当前语言本地化（「刚刚」/「3 小时前」/「昨天」/日期）。 */
private fun formatHistoryPlayedAt(timestamp: Long): String {
    val safeTimestamp = timestamp.coerceAtLeast(0L)
    return DateUtils.getRelativeTimeSpanString(
        safeTimestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
