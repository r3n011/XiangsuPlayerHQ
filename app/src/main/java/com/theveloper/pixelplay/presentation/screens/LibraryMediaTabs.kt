@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    kotlinx.coroutines.FlowPreview::class
)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistContainer
import com.theveloper.pixelplay.presentation.components.albumFastScrollLabel
import com.theveloper.pixelplay.presentation.components.artistFastScrollLabel
import com.theveloper.pixelplay.presentation.components.library.rememberLibraryCoverGridColumns
import com.theveloper.pixelplay.presentation.components.library.rememberLibraryListGridCells
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryAlbumsTab(
    albums: LazyPagingItems<Album>,
    customAlbums: ImmutableList<Album>? = null,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    isListView: Boolean,
    currentAlbumSortOption: SortOption,
    onAlbumClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedAlbumIds: Set<Long> = emptySet(),
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectionToggle: (Album) -> Unit = {},
    getSelectionIndex: (Long) -> Int? = { null },
    storageFilter: StorageFilter = StorageFilter.ALL
) {
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val gridState = rememberLazyGridState()
    val listState = rememberLazyGridState()
    val dummyListState = rememberLazyGridState()
    val dummyGridState = rememberLazyGridState()
    // ⚡ 媒体库「回到顶部」：专辑 Tab 有列表 / 网格两种视图，按当前视图注册对应的滚动状态
    LibraryScrollToTopRegistration(
        tabId = com.theveloper.pixelplay.data.model.LibraryTabId.ALBUMS.storageKey,
        state = if (isListView) listState else gridState
    )
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val albumFastScrollLabelProvider = remember(albums, currentAlbumSortOption) {
        { index: Int ->
            albumFastScrollLabel(
                album = albums.peek(index),
                sortOption = currentAlbumSortOption
            )
        }
    }

    var lastHandledAlbumSortKey by remember {
        mutableStateOf(currentAlbumSortOption.storageKey)
    }
    var pendingAlbumSortScrollReset by remember { mutableStateOf(false) }
    var albumSortSawRefreshLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentAlbumSortOption) {
        val currentSortKey = currentAlbumSortOption.storageKey
        if (currentSortKey == lastHandledAlbumSortKey) return@LaunchedEffect
        lastHandledAlbumSortKey = currentSortKey
        pendingAlbumSortScrollReset = true
        albumSortSawRefreshLoading = false
        if (isListView) {
            listState.scrollToItem(0)
        } else {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(albums.loadState.refresh, pendingAlbumSortScrollReset, isListView) {
        if (!pendingAlbumSortScrollReset) return@LaunchedEffect
        if (albums.loadState.refresh is LoadState.Loading) {
            albumSortSawRefreshLoading = true
            return@LaunchedEffect
        }
        if (!albumSortSawRefreshLoading) return@LaunchedEffect
        if (isListView) {
            listState.scrollToItem(0)
        } else {
            gridState.scrollToItem(0)
        }
        pendingAlbumSortScrollReset = false
    }

    // P2-3: Debounce 150ms to avoid firing on every scroll frame.
    // Reduced prefetchCount from 10 to 4 to lower memory/IO pressure.
    LaunchedEffect(albums, gridState, listState, isListView) {
        if (isListView) {
            snapshotFlow { listState.layoutInfo }
                .debounce(150)
                .distinctUntilChanged()
                .collect { layoutInfo ->
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isNotEmpty() && albums.itemCount > 0) {
                        val lastVisibleItemIndex = visibleItemsInfo.last().index
                        val totalItemsCount = albums.itemCount
                        val prefetchThreshold = 5
                        val prefetchCount = 4

                        if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                            val startIndexToPrefetch = lastVisibleItemIndex + 1
                            val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                            (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                                val album = albums.peek(indexToPrefetch)
                                album?.albumArtUriString?.let { uri ->
                                    val request = ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(Size(256, 256))
                                        .build()
                                    imageLoader.enqueue(request)
                                }
                            }
                        }
                    }
                }
        } else {
            snapshotFlow { gridState.layoutInfo }
                .debounce(150)
                .distinctUntilChanged()
                .collect { layoutInfo ->
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isNotEmpty() && albums.itemCount > 0) {
                        val lastVisibleItemIndex = visibleItemsInfo.last().index
                        val totalItemsCount = albums.itemCount
                        val prefetchThreshold = 5
                        val prefetchCount = 4

                        if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                            val startIndexToPrefetch = lastVisibleItemIndex + 1
                            val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                            (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                                val album = albums.peek(indexToPrefetch)
                                album?.albumArtUriString?.let { uri ->
                                    val request = ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(Size(256, 256))
                                        .build()
                                    imageLoader.enqueue(request)
                                }
                            }
                        }
                    }
            }
        }
    }

    val refreshState = albums.loadState.refresh
    val reachedEndOfPagination = albums.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = albums.itemCount == 0 && isLoading

    // Custom order renders the full non-paged list.
    if (currentAlbumSortOption == SortOption.AlbumCustomOrder && customAlbums != null) {
        LibraryAlbumsTabCustomOrderContent(
            albums = customAlbums,
            isListView = isListView,
            playerViewModel = playerViewModel,
            bottomBarHeight = bottomBarHeight,
            onAlbumClick = onAlbumClick,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            isSelectionMode = isSelectionMode,
            selectedAlbumIds = selectedAlbumIds,
            onAlbumLongPress = onAlbumLongPress,
            onAlbumSelectionToggle = onAlbumSelectionToggle,
            storageFilter = storageFilter
        )
        return
    }

    when {
        refreshState is LoadState.Error && albums.itemCount == 0 -> {
            val error = (refreshState as LoadState.Error).error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_albums), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { albums.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        shouldShowInitialLoading -> {
            if (isListView) {
                LazyVerticalGrid(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = PlayerSheetCollapsedCornerRadius,
                                bottomEnd = PlayerSheetCollapsedCornerRadius
                            )
                        )
                        .fillMaxSize(),
                    columns = rememberLibraryListGridCells(),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(8, key = { "skeleton_album_list_$it" }) {
                        AlbumListItem(
                            album = Album.empty(),
                            albumColorSchemePairFlow = MutableStateFlow<ColorSchemePair?>(null),
                            onClick = {},
                            isLoading = true
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = PlayerSheetCollapsedCornerRadius,
                                bottomEnd = PlayerSheetCollapsedCornerRadius
                            )
                        )
                        .fillMaxSize(),
                    columns = rememberLibraryCoverGridColumns(),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(8, key = { "skeleton_album_grid_$it" }) {
                        AlbumGridItemRedesigned(
                            album = Album.empty(),
                            albumColorSchemePairFlow = MutableStateFlow<ColorSchemePair?>(null),
                            onClick = {},
                            isLoading = true
                        )
                    }
                }
            }
        }

        albums.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ALBUMS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                val albumsPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = albumsPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = albumsPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isListView) {
                            val activeListState = if (albums.itemCount > 0) listState else dummyListState
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = if (LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward)) 24.dp else 14.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = activeListState,
                                columns = rememberLibraryListGridCells(),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = albums.itemCount,
                                    key = { index -> albums.peek(index)?.id ?: "album_placeholder_$index" },
                                    contentType = { "album_list_item" }
                                ) { index ->
                                    val album = albums[index]
                                    if (album != null) {
                                        val albumSpecificColorSchemeFlow =
                                            playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                        val rememberedOnClick = remember(album.id, onAlbumClick) {
                                            { onAlbumClick(album.id) }
                                        }
                                        val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                            { onAlbumLongPress(album) }
                                        }
                                        val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                            { onAlbumSelectionToggle(album) }
                                        }
                                        AlbumListItem(
                                            album = album,
                                            albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                            onClick = rememberedOnClick,
                                            isLoading = false,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedAlbumIds.contains(album.id),
                                            selectionIndex = getSelectionIndex(album.id),
                                            onLongPress = rememberedOnLongPress,
                                            onSelectionToggle = rememberedOnSelectionToggle
                                        )
                                    } else {
                                        AlbumListItem(
                                            album = Album.empty(),
                                            albumColorSchemePairFlow = MutableStateFlow<ColorSchemePair?>(null),
                                            onClick = {},
                                            isLoading = true
                                        )
                                    }
                                }
                            }
                            val bottomPadding = if (hasCurrentSong)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                gridState = activeListState,
                                dragLabelProvider = albumFastScrollLabelProvider
                            )
                        } else {
                            val activeGridState = if (albums.itemCount > 0) gridState else dummyGridState
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = if (LocalShowScrollbar.current && (activeGridState.canScrollForward || activeGridState.canScrollBackward)) 24.dp else 14.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = activeGridState,
                                columns = rememberLibraryCoverGridColumns(),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = albums.itemCount,
                                    key = { index -> albums.peek(index)?.id ?: "album_grid_placeholder_$index" },
                                    contentType = { "album_grid_item" }
                                ) { index ->
                                    val album = albums[index]
                                    if (album != null) {
                                        val albumSpecificColorSchemeFlow =
                                            playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                        val rememberedOnClick = remember(album.id, onAlbumClick) {
                                            { onAlbumClick(album.id) }
                                        }
                                        val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                            { onAlbumLongPress(album) }
                                        }
                                        val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                            { onAlbumSelectionToggle(album) }
                                        }
                                        AlbumGridItemRedesigned(
                                            album = album,
                                            albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                            onClick = rememberedOnClick,
                                            isLoading = false,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedAlbumIds.contains(album.id),
                                            selectionIndex = getSelectionIndex(album.id),
                                            onLongPress = rememberedOnLongPress,
                                            onSelectionToggle = rememberedOnSelectionToggle
                                        )
                                    } else {
                                        AlbumGridItemRedesigned(
                                            album = Album.empty(),
                                            albumColorSchemePairFlow = MutableStateFlow<ColorSchemePair?>(null),
                                            onClick = {},
                                            isLoading = true
                                        )
                                    }
                                }
                            }

                            val bottomPadding = if (hasCurrentSong)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                gridState = activeGridState,
                                dragLabelProvider = albumFastScrollLabelProvider
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryArtistsTab(
    artists: LazyPagingItems<Artist>,
    customArtists: ImmutableList<Artist>? = null,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    currentArtistSortOption: SortOption,
    onArtistClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    storageFilter: StorageFilter = StorageFilter.ALL
) {
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val listState = rememberLazyGridState()
    val dummyListState = rememberLazyGridState()
    // ⚡ 媒体库「回到顶部」：注册歌手网格的滚动状态
    LibraryScrollToTopRegistration(
        tabId = com.theveloper.pixelplay.data.model.LibraryTabId.ARTISTS.storageKey,
        state = listState
    )
    val artistFastScrollLabelProvider = remember(artists, currentArtistSortOption) {
        { index: Int ->
            artistFastScrollLabel(
                artist = artists.peek(index),
                sortOption = currentArtistSortOption
            )
        }
    }
    var lastHandledArtistSortKey by remember {
        mutableStateOf(currentArtistSortOption.storageKey)
    }
    var pendingArtistSortScrollReset by remember { mutableStateOf(false) }
    var artistSortSawRefreshLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentArtistSortOption) {
        val currentSortKey = currentArtistSortOption.storageKey
        if (currentSortKey == lastHandledArtistSortKey) return@LaunchedEffect
        lastHandledArtistSortKey = currentSortKey
        pendingArtistSortScrollReset = true
        artistSortSawRefreshLoading = false
        listState.scrollToItem(0)
    }

    LaunchedEffect(artists.loadState.refresh, pendingArtistSortScrollReset) {
        if (!pendingArtistSortScrollReset) return@LaunchedEffect
        if (artists.loadState.refresh is LoadState.Loading) {
            artistSortSawRefreshLoading = true
            return@LaunchedEffect
        }
        if (!artistSortSawRefreshLoading) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingArtistSortScrollReset = false
    }

    val refreshState = artists.loadState.refresh
    val reachedEndOfPagination = artists.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = artists.itemCount == 0 && isLoading

    // Custom order renders the full non-paged list.
    if (currentArtistSortOption == SortOption.ArtistCustomOrder && customArtists != null) {
        LibraryArtistsTabCustomOrderContent(
            artists = customArtists,
            playerViewModel = playerViewModel,
            bottomBarHeight = bottomBarHeight,
            onArtistClick = onArtistClick,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            storageFilter = storageFilter
        )
        return
    }

    when {
        refreshState is LoadState.Error && artists.itemCount == 0 -> {
            val error = (refreshState as LoadState.Error).error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_artists), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { artists.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        shouldShowInitialLoading -> {
            LazyVerticalGrid(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    )
                    .fillMaxSize(),
                columns = rememberLibraryListGridCells(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                item(key = "skeleton_top_spacer") { Spacer(Modifier.height(4.dp)) }
                items(10, key = { "skeleton_artist_$it" }) {
                    ArtistListItem(
                        artist = Artist.empty(),
                        onClick = {},
                        isLoading = true
                    )
                }
            }
        }

        artists.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ARTISTS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val genresPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = genresPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = genresPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val activeListState = if (artists.itemCount > 0) listState else dummyListState
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(start = 12.dp, end = if (LocalShowScrollbar.current && (activeListState.canScrollForward || activeListState.canScrollBackward)) 22.dp else 12.dp, bottom = 6.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 26.dp,
                                        topEnd = 26.dp,
                                        bottomStart = PlayerSheetCollapsedCornerRadius,
                                        bottomEnd = PlayerSheetCollapsedCornerRadius
                                    )
                                ),
                            state = activeListState,
                            columns = rememberLibraryListGridCells(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                        ) {
                            items(
                                count = artists.itemCount,
                                key = { index -> artists.peek(index)?.id ?: "artist_placeholder_$index" },
                                contentType = { "artist" }
                            ) { index ->
                                val artist = artists[index]
                                if (artist != null) {
                                    val rememberedOnClick = remember(artist.id, onArtistClick) {
                                        { onArtistClick(artist.id) }
                                    }
                                    ArtistListItem(artist = artist, onClick = rememberedOnClick)
                                } else {
                                    ArtistListItem(
                                        artist = Artist.empty(),
                                        onClick = {},
                                        isLoading = true
                                    )
                                }
                            }
                        }

                        val bottomPadding = if (hasCurrentSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            gridState = activeListState,
                            dragLabelProvider = artistFastScrollLabelProvider
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryPlaylistsTab(
    playlistUiState: PlaylistUiState,
    filteredPlaylists: List<com.theveloper.pixelplay.data.model.Playlist> = playlistUiState.playlists,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (com.theveloper.pixelplay.data.model.Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (com.theveloper.pixelplay.data.model.Playlist) -> Unit = {},
    onPlaylistOptionsClick: () -> Unit = {},
    onReorder: ((List<String>) -> Unit)? = null,
    dailyRecommendHeader: (@Composable () -> Unit)? = null
) {
    PlaylistContainer(
        playlistUiState = playlistUiState,
        filteredPlaylists = filteredPlaylists,
        currentSortOption = playlistUiState.currentPlaylistSortOption,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        bottomBarHeight = bottomBarHeight,
        navController = navController,
        playerViewModel = playerViewModel,
        isSelectionMode = isSelectionMode,
        selectedPlaylistIds = selectedPlaylistIds,
        onPlaylistLongPress = onPlaylistLongPress,
        onPlaylistSelectionToggle = onPlaylistSelectionToggle,
        onReorder = onReorder,
        dailyRecommendHeader = dailyRecommendHeader,
        // ⚡ 媒体库「回到顶部」：把歌单列表的滚动状态注册给媒体库父级
        scrollToTopRegistrationKey = com.theveloper.pixelplay.data.model.LibraryTabId.PLAYLISTS.storageKey
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryAlbumsTabCustomOrderContent(
    albums: ImmutableList<Album>,
    isListView: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onAlbumClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean,
    selectedAlbumIds: Set<Long>,
    onAlbumLongPress: (Album) -> Unit,
    onAlbumSelectionToggle: (Album) -> Unit,
    storageFilter: StorageFilter
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyGridState()
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    when {
        albums.isEmpty() -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ALBUMS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                val albumsPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = albumsPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = albumsPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isListView) {
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    )
                                    .fillMaxSize(),
                                state = listState,
                                columns = rememberLibraryListGridCells(),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(albums, key = { it.id }, contentType = { "album_list_item" }) { album ->
                                    val albumSpecificColorSchemeFlow =
                                        playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                    val rememberedOnClick = remember(album.id, onAlbumClick) {
                                        { onAlbumClick(album.id) }
                                    }
                                    val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                        { onAlbumLongPress(album) }
                                    }
                                    val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                        { onAlbumSelectionToggle(album) }
                                    }
                                    AlbumListItem(
                                        album = album,
                                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                        onClick = rememberedOnClick,
                                        isLoading = false,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedAlbumIds.contains(album.id),
                                        onLongPress = rememberedOnLongPress,
                                        onSelectionToggle = rememberedOnSelectionToggle
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    )
                                    .fillMaxSize(),
                                state = gridState,
                                columns = rememberLibraryCoverGridColumns(),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(albums, key = { it.id }, contentType = { "album_grid_item" }) { album ->
                                    val albumSpecificColorSchemeFlow =
                                        playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(album.albumArtUriString ?: "")
                                    val rememberedOnClick = remember(album.id, onAlbumClick) {
                                        { onAlbumClick(album.id) }
                                    }
                                    val rememberedOnLongPress = remember(album.id, onAlbumLongPress) {
                                        { onAlbumLongPress(album) }
                                    }
                                    val rememberedOnSelectionToggle = remember(album.id, onAlbumSelectionToggle) {
                                        { onAlbumSelectionToggle(album) }
                                    }
                                    AlbumGridItemRedesigned(
                                        album = album,
                                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                                        onClick = rememberedOnClick,
                                        isLoading = false,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedAlbumIds.contains(album.id),
                                        onLongPress = rememberedOnLongPress,
                                        onSelectionToggle = rememberedOnSelectionToggle
                                    )
                                }
                            }
                        }

                        val bottomPadding = if (hasCurrentSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        if (isListView) {
                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                gridState = listState
                            )
                        } else {
                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                gridState = gridState
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryArtistsTabCustomOrderContent(
    artists: ImmutableList<Artist>,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onArtistClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    storageFilter: StorageFilter
) {
    val listState = rememberLazyGridState()
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    when {
        artists.isEmpty() -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.ARTISTS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val artistsPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = artistsPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = artistsPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 26.dp,
                                        topEnd = 26.dp,
                                        bottomStart = PlayerSheetCollapsedCornerRadius,
                                        bottomEnd = PlayerSheetCollapsedCornerRadius
                                    )
                                )
                                .fillMaxSize(),
                            state = listState,
                            columns = rememberLibraryListGridCells(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                        ) {
                            items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
                                val rememberedOnClick = remember(artist.id, onArtistClick) {
                                    { onArtistClick(artist.id) }
                                }
                                ArtistListItem(artist = artist, onClick = rememberedOnClick)
                            }
                        }

                        val bottomPadding = if (hasCurrentSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            gridState = listState
                        )
                    }
                }
            }
        }
    }
}
