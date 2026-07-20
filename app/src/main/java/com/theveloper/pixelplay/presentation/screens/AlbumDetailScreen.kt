@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.presentation.components.LibrarySheetToggleCard
import com.theveloper.pixelplay.presentation.components.LibrarySortBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.AlbumDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AlbumSongsOrderMode
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.theveloper.pixelplay.utils.formatSongCount
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

private const val UseSharedCollapsibleTopBarProbe = true

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val baseColorScheme = MaterialTheme.colorScheme
    val albumArtUri = uiState.album?.albumArtUriString?.takeIf { it.isNotBlank() }
    val albumColorSchemeFlow = remember(albumArtUri) {
        albumArtUri?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val albumColorSchemePair = albumColorSchemeFlow?.collectAsStateWithLifecycle()?.value
    val albumColorScheme = remember(albumColorSchemePair, isDarkTheme, baseColorScheme) {
        albumColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
            ?: baseColorScheme
    }
    var headerArtworkLoaded by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    var themeRequestIssued by remember(albumArtUri) { mutableStateOf(albumArtUri == null) }
    LaunchedEffect(albumArtUri, headerArtworkLoaded, themeRequestIssued) {
        if (!themeRequestIssued && headerArtworkLoaded && albumArtUri != null) {
            themeRequestIssued = true
            playerViewModel.themeStateHolder.ensureAlbumColorScheme(albumArtUri)
        }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = albumColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {

        val isMiniPlayerVisible = stablePlayerState.currentSong != null
        val fabBottomPadding by animateDpAsState(
            targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 16.dp,
            label = "fabPadding"
        )

        when {
            uiState.isLoading && uiState.album == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }

            uiState.error != null && uiState.album == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            uiState.album != null -> {
                val album = uiState.album!!
                val songs = uiState.songs
                val isManualMode = uiState.albumSongsOrderMode is AlbumSongsOrderMode.Manual
                var localReorderableSongs by remember(songs) { mutableStateOf(songs) }
                LaunchedEffect(songs) { localReorderableSongs = songs }
                var showSortSheet by remember { mutableStateOf(false) }
                var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
                var lastMovedTo by remember { mutableStateOf<Int?>(null) }
                val listState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(
                    lazyListState = listState,
                    onMove = { from, to ->
                        if (!isManualMode) return@rememberReorderableLazyListState
                        localReorderableSongs = localReorderableSongs.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        if (lastMovedFrom == null) {
                            lastMovedFrom = from.index
                        }
                        lastMovedTo = to.index
                    }
                )
                LaunchedEffect(reorderableState.isAnyItemDragging) {
                    if (!reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
                        viewModel.reorderSongsInAlbum(lastMovedFrom!!, lastMovedTo!!)
                        lastMovedFrom = null
                        lastMovedTo = null
                    }
                }
                val songsByDisc = remember(songs, isManualMode) {
                    if (isManualMode) emptyMap() else songs.groupBy { it.discNumber ?: 1 }
                }

                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val minTopBarHeight = 64.dp + statusBarHeight
                val maxTopBarHeight = 300.dp

                val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
                val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
                val headerImageRequestSize = remember(
                    configuration.screenWidthDp,
                    density.density,
                    maxTopBarHeightPx
                ) {
                    Size(
                        width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                        height = maxTopBarHeightPx.roundToInt()
                    )
                }

                val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
                val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
                    derivedStateOf {
                        1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(
                            0f,
                            1f
                        )
                    }
                }

                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            val delta = available.y
                            val isScrollingDown = delta < 0

                            if (!isScrollingDown && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                                return Offset.Zero
                            }

                            val previousHeight = topBarHeight.value
                            val newHeight =
                                (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                            val consumed = newHeight - previousHeight

                            if (consumed.roundToInt() != 0) {
                                coroutineScope.launch {
                                    topBarHeight.snapTo(newHeight)
                                }
                            }

                            val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                            return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            return super.onPostFling(consumed, available)
                        }
                    }
                }

                LaunchedEffect(listState.isScrollInProgress) {
                    if (!listState.isScrollInProgress) {
                        val shouldExpand =
                            topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
                        val canExpand =
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

                        val targetValue = if (shouldExpand && canExpand) {
                            maxTopBarHeightPx
                        } else {
                            minTopBarHeightPx
                        }

                        if (topBarHeight.value != targetValue) {
                            coroutineScope.launch {
                                topBarHeight.animateTo(
                                    targetValue,
                                    spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface
                        )
                        .nestedScroll(nestedScrollConnection)
                ) {
                    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
                    val showScrollBar =
                        LocalShowScrollbar.current &&
                        collapseFraction > 0.95f &&
                            (listState.canScrollForward || listState.canScrollBackward)

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(MainActivity.LocalHazeState.current)
                            .offset {
                                val extraHeight =
                                    (topBarHeight.value - minTopBarHeightPx).roundToInt()
                                IntOffset(0, extraHeight)
                            },
                        contentPadding = PaddingValues(
                            top = minTopBarHeight + 8.dp,
                            start = 16.dp,
                            end = if (showScrollBar) 24.dp else 16.dp,
                            bottom = fabBottomPadding + 80.dp // To account for FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isManualMode) {
                            items(
                                items = localReorderableSongs,
                                key = { song -> "album_song_${song.id}" },
                                contentType = { "album_song" }
                            ) { song ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = "album_song_${song.id}"
                                ) { isDragging ->
                                    val scale by animateFloatAsState(
                                        targetValue = if (isDragging) 1.02f else 1f,
                                        label = "albumSongItemScale"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.draggableHandle()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = stringResource(R.string.presentation_batch_b_reorder_song)
                                            )
                                        }
                                        EnhancedSongListItem(
                                            modifier = Modifier.weight(1f),
                                            song = song,
                                            isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                            isPlaying = stablePlayerState.isPlaying,
                                            showAlbumArt = false,
                                            onMoreOptionsClick = {
                                                playerViewModel.selectSongForInfo(song)
                                                showSongInfoBottomSheet = true
                                            },
                                            onClick = { playerViewModel.showAndPlaySong(song, localReorderableSongs) }
                                        )
                                    }
                                }
                            }
                        } else {
                            songsByDisc.forEach { (discNumber, discSongs) ->
                                if (songsByDisc.size > 1) {
                                    item(key = "disc_header_$discNumber") {
                                        Text(
                                            text = stringResource(R.string.disc_number_header, discNumber),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                                        )
                                    }
                                }
                                items(
                                    items = discSongs,
                                    key = { song -> "album_song_${song.id}" },
                                    contentType = { "album_song" }
                                ) { song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        isPlaying = stablePlayerState.isPlaying,
                                        showAlbumArt = false,
                                        onMoreOptionsClick = {
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        },
                                        onClick = { playerViewModel.showAndPlaySong(song, localReorderableSongs) }
                                    )
                                }
                            }
                        }
                    }

                    if (showScrollBar) {
                        ExpressiveScrollBar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    top = minTopBarHeight + 12.dp,
                                    bottom = fabBottomPadding + 80.dp
                                )
                        )
                    }

                    val onPlayAlbumClick = {
                        if (localReorderableSongs.isNotEmpty()) {
                            val randomSong = localReorderableSongs.random()
                            playerViewModel.showAndPlaySong(randomSong, localReorderableSongs)
                        }
                    }
                    if (UseSharedCollapsibleTopBarProbe) {
                        SharedAlbumTopBarProbe(
                            album = album,
                            songsCount = localReorderableSongs.size,
                            collapseFraction = collapseFraction,
                            headerHeight = currentTopBarHeightDp,
                            headerImageRequestSize = headerImageRequestSize,
                            onHeaderArtworkState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    headerArtworkLoaded = true
                                }
                            },
                            onBackPressed = { navController.popBackStack() },
                            onPlayClick = onPlayAlbumClick,
                            onSortClick = { showSortSheet = true }
                        )
                    } else {
                        CollapsingAlbumTopBar(
                            album = album,
                            songsCount = localReorderableSongs.size,
                            collapseFraction = collapseFraction,
                            headerHeight = currentTopBarHeightDp,
                            headerImageRequestSize = headerImageRequestSize,
                            onHeaderArtworkState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    headerArtworkLoaded = true
                                }
                            },
                            onBackPressed = { navController.popBackStack() },
                            onPlayClick = onPlayAlbumClick,
                            onSortClick = { showSortSheet = true }
                        )
                    }
                }

                if (showSortSheet) {
                    LibrarySortBottomSheet(
                        title = stringResource(R.string.presentation_batch_b_sort_songs),
                        options = SortOption.SONGS,
                        selectedOption = uiState.currentSongsSortOption,
                        onDismiss = { showSortSheet = false },
                        onOptionSelected = { option ->
                            viewModel.sortAlbumSongs(option)
                            showSortSheet = false
                        },
                        onDirectionToggle = { option ->
                            viewModel.sortAlbumSongs(option)
                        },
                        extraContent = {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.presentation_batch_b_more_options),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                            )
                            LibrarySheetToggleCard(
                                label = stringResource(R.string.presentation_batch_b_reorder),
                                checked = isManualMode,
                                boxBackgroundColor = if (isManualMode) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                boxCornerRadius = if (isManualMode) 18.dp else 50.dp,
                                onCheckedChange = { enabled: Boolean ->
                                    viewModel.setManualOrderEnabled(enabled)
                                }
                            )
                        }
                    )
                }
            }
        }
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo
            val isFavorite = currentSong?.let { favoriteIds.contains(it.id) } ?: false

            if (currentSong != null) {
                val removeFromListTrigger = remember(uiState.songs) {
                    {
                        viewModel.update(uiState.songs.filterNot { it.id == currentSong.id })
                    }
                }
                SongInfoBottomSheet(
                    song = currentSong,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        playerViewModel.toggleFavoriteSpecificSong(currentSong)
                    },
                    onDismiss = { showSongInfoBottomSheet = false },
                    onPlaySong = {
                        playerViewModel.showAndPlaySong(currentSong)
                    },
                    onAddToQueue = {
                        playerViewModel.addSongToQueue(currentSong)
                    },
                    onAddNextToQueue = {
                        playerViewModel.addSongNextToQueue(currentSong)
                    },
                    onAddToPlayList = {
                        showPlaylistBottomSheet = true;
                    },
                    onDeleteFromDevice = playerViewModel::deleteFromDevice,
                    onNavigateToAlbum = {
                        navController.navigateSafelyReplacing(
                            route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                            patternToPop = Screen.AlbumDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtist = {
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                            patternToPop = Screen.ArtistDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtistById = { artistId ->
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRoute(artistId),
                            patternToPop = Screen.ArtistDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToGenre = {
                        currentSong.genre?.let {
                            navController.navigateSafelyReplacing(
                                route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                                patternToPop = Screen.GenreDetail.route
                            )
                        }
                        showSongInfoBottomSheet = false
                    },
                    onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                        playerViewModel.editSongMetadata(
                            currentSong,
                            newTitle,
                            newArtist,
                            newAlbum,
                            newAlbumArtist,
                            newComposer,
                            newGenre,
                            newLyrics,
                            newTrackNumber,
                            newDiscNumber,
                            replayGainTrackGainDb,
                            replayGainAlbumGainDb,
                            coverArtUpdate
                        )
                    },
                    generateAiMetadata = { fields ->
                        playerViewModel.generateAiMetadata(currentSong, fields)
                    },
                    removeFromListTrigger = removeFromListTrigger
                )
                if (showPlaylistBottomSheet) {
                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = listOf(currentSong),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedAlbumTopBarProbe(
    album: Album,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit,
    onSortClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val shuffleAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = titleVerticalBias)

    PixelPlayStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = stringResource(R.string.album_cover_for, album.title),
                contentScale = ContentScale.Crop,
                targetSize = headerImageRequestSize,
                allowHardware = true,
                crossfadeDurationMillis = 0,
                alpha = expandedContentAlpha,
                onState = onHeaderArtworkState,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        CollapsibleCommonTopBar(
            title = album.title,
            subtitle = "${album.artist} • ${formatSongCount(songsCount)}",
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackPressed,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 24.dp,
            expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = {
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.presentation_batch_b_sort_songs)
                    )
                }
            }
        )

        LargeExtendedFloatingActionButton(
            onClick = onPlayClick,
            shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
            modifier = Modifier
                .align(shuffleAlignment)
                .statusBarsPadding()
                .padding(end = 16.dp)
                .graphicsLayer {
                    scaleX = expandedContentAlpha
                    scaleY = expandedContentAlpha
                    alpha = expandedContentAlpha
                }
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.cd_shuffle_play_album))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingAlbumTopBar(
    album: Album,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit,
    onSortClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayDarkTheme.current) Color.Black.copy(alpha = 0.6f) else Color.White.copy(
            alpha = 0.4f
        )

    // Animation Values
    val fabScale = 1f - collapseFraction
    val backgroundAlpha = collapseFraction
    val headerContentAlpha = 1f - (collapseFraction * 2).coerceAtMost(1f)
    val showExpandedArtwork = headerContentAlpha > 0.01f
    val headerOverlayBrush = remember(surfaceColor, headerContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.30f * headerContentAlpha),
                surfaceColor.copy(alpha = 0.90f * headerContentAlpha),
                surfaceColor.copy(alpha = headerContentAlpha)
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(
            colors = listOf(
                statusBarColor,
                Color.Transparent
            )
        )
    }
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }

    // Title animation
    val titleScale = lerp(1f, 0.75f, collapseFraction)
    val titlePaddingStart = lerp(24.dp, 58.dp, collapseFraction)
    val titleMaxLines = if (collapseFraction < 0.5f) 2 else 1
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment =
        BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)
    val yOffsetCorrection = lerp((titleContainerHeight / 2) - 64.dp, 0.dp, collapseFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        PixelPlayStatusBarStyle(color = fallbackStatusBarColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(surfaceColor.copy(alpha = backgroundAlpha))
        ) {
            if (showExpandedArtwork) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = stringResource(R.string.album_cover_for, album.title),
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = headerContentAlpha,
                    onState = onHeaderArtworkState,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(headerOverlayBrush)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(statusBarBrush)
                    .align(Alignment.TopCenter)
            )

            // Top bar content (buttons, title, etc.)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                FilledIconButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 4.dp),
                    onClick = onBackPressed,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
                }

                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 4.dp),
                    onClick = onSortClick
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.presentation_batch_b_sort_songs)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(animatedTitleAlignment)
                        .height(titleContainerHeight)
                        .fillMaxWidth()
                        .offset(y = yOffsetCorrection)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = titlePaddingStart, end = 120.dp)
                            .graphicsLayer {
                                scaleX = titleScale
                                scaleY = titleScale
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.album_detail_meta_line,
                                album.artist,
                                formatSongCount(songsCount)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LargeExtendedFloatingActionButton(
                    onClick = onPlayClick,
                    shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                            alpha = fabScale
                        }
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.cd_shuffle_play_album))
                }
            }
        }
    }
}
