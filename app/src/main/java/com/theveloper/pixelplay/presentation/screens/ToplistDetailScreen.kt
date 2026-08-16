package com.theveloper.pixelplay.presentation.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.toplist.ToplistCatalog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ToplistBoardPickerSheet
import com.theveloper.pixelplay.presentation.components.ToplistPlatformTabPill
import com.theveloper.pixelplay.presentation.components.threeShapeSwitch
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ToplistViewModel
import dev.chrisbanes.haze.hazeSource

/**
 * 排行榜全屏页：5 大平台（网易云/QQ音乐/酷狗/酷我/咪咕）全部榜单无限下滑浏览。
 * - 顶部返回 + 平台 tabs + 榜单 chips
 * - 播放全部 / 随机播放（cloud://lx 协议：内置源官方接口 + 落雪引擎兜底）
 * - 本地切片分页：滚动到底触发 loadMore()（纯内存追加，无网络等待）
 * - 打开时隐藏底部导航栏（MainActivity routesWithHiddenNavigationBar）
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToplistDetailScreen(
    entryId: String,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navController: NavController
) {
    val toplistViewModel: ToplistViewModel = hiltViewModel()
    val uiState by toplistViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    // 进入页面先切到指定榜单（仓库缓存命中时秒开，无网络等待）
    LaunchedEffect(entryId) {
        toplistViewModel.selectToplist(entryId)
    }

    // 切换榜单后回到顶部
    LaunchedEffect(uiState.selectedToplistId) {
        lazyListState.scrollToItem(0)
    }

    // ⚡ 无限下滑：距底部剩 3 条时触发本地切片加载（纯内存操作）
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) toplistViewModel.loadMore()
    }

    val displaySongs = uiState.displaySongs
    val allSongs = uiState.allSongs
    val selectedEntry = uiState.selectedEntry
    val queueName = selectedEntry?.name ?: stringResource(R.string.home_toplist_title)

    if (showBoardPicker) {
        ToplistBoardPickerSheet(
            entries = ToplistCatalog.entriesFor(uiState.selectedPlatform),
            selectedToplistId = uiState.selectedToplistId,
            onSelect = { id ->
                toplistViewModel.selectToplist(id)
                showBoardPicker = false
            },
            onDismiss = { showBoardPicker = false }
        )
    }

    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { showSongInfoSheet = false },
            onPlaySong = { playerViewModel.showAndPlaySong(song, allSongs, queueName, isVoluntaryPlay = false) },
            onAddToQueue = { playerViewModel.addSongToQueue(song) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
            onAddToPlayList = { },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                showSongInfoSheet = false
            },
            onNavigateToArtistById = { artistId ->
                navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                showSongInfoSheet = false
            },
            onOpenNeteaseArtistHomepage = {
                playerViewModel.fetchNeteaseArtistId(song.neteaseId ?: 0L) { artistId ->
                    artistId?.let {
                        navController.navigateSafely(Screen.ArtistHomepage.createRoute(it))
                    }
                }
                showSongInfoSheet = false
            },
            onNavigateToGenre = {
                song.genre?.let {
                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                }
                showSongInfoSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.editSongMetadata(
                    song, newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre,
                    newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate
                )
            },
            generateAiMetadata = { fields ->
                playerViewModel.generateAiMetadata(song, fields)
            },
            removeFromListTrigger = { },
            isGeneratingMetadata = false,
            aiMetadataSuccess = false,
            aiError = null,
            onRetryMetadata = { }
        )
    }

    val surfaceContainer = MaterialTheme.colorScheme.surface
    val headerColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surfaceContainer, headerColor) {
        Brush.verticalGradient(
            colors = listOf(
                headerColor.copy(alpha = 0.22f),
                surfaceContainer.copy(alpha = 0.5f),
                surfaceContainer
            ),
            endY = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(MainActivity.LocalHazeState.current)
            .background(backgroundBrush)
    ) {
        when {
            uiState.isLoading && displaySongs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }
            uiState.error != null && displaySongs.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.toplist_loading_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { toplistViewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.toplist_retry))
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── 标题：与完整每日合集一致（3 封面旋转 + 视差 + 可变字体大标题）──
                    item(key = "toplist_header") {
                        ToplistExpressiveHeader(
                            songs = allSongs,
                            scrollState = lazyListState,
                            title = stringResource(R.string.home_toplist_title),
                            subtitle = selectedEntry?.let {
                                "${uiState.selectedPlatform.label} · ${it.name} · ${it.subtitle}"
                            } ?: stringResource(R.string.home_toplist_title),
                            songsCount = allSongs.size
                        )
                    }

                    // ── 平台 tabs（复刻媒体库紧凑大胶囊 LibraryNavigationPill）──
                    // 选中平台胶囊 = 标题段 + 4dp 可见间隙 + 箭头段（同媒体库胶囊导航），
                    // 箭头段点击打开榜单切换器。
                    item(key = "toplist_platform_tabs") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ToplistCatalog.platforms, key = { it.key }) { platform ->
                                ToplistPlatformTabPill(
                                    platform = platform,
                                    selected = platform == uiState.selectedPlatform,
                                    pickerOpen = showBoardPicker,
                                    onClick = { toplistViewModel.selectPlatform(platform) },
                                    onArrowClick = { showBoardPicker = true }
                                )
                            }
                        }
                    }

                    if (displaySongs.isNotEmpty()) {
                        item(key = "toplist_play_buttons") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (allSongs.isNotEmpty()) {
                                            playerViewModel.showAndPlaySong(
                                                song = allSongs.first(),
                                                contextSongs = allSongs,
                                                queueName = queueName,
                                                isVoluntaryPlay = false
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(76.dp),
                                    enabled = allSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(
                                        topStart = 60.dp,
                                        topEnd = 14.dp,
                                        bottomStart = 60.dp,
                                        bottomEnd = 14.dp
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TightWrapText(
                                        text = stringResource(R.string.toplist_detail_play_all),
                                        modifier = Modifier.padding(end = 4.dp),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        lineHeight = 20.sp
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (allSongs.isNotEmpty()) {
                                            val shuffled = allSongs.shuffled()
                                            playerViewModel.showAndPlaySong(
                                                song = shuffled.first(),
                                                contextSongs = shuffled,
                                                queueName = queueName,
                                                isVoluntaryPlay = false
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(76.dp),
                                    enabled = allSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 60.dp,
                                        bottomStart = 14.dp,
                                        bottomEnd = 60.dp
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Shuffle,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TightWrapText(
                                        text = stringResource(R.string.toplist_detail_shuffle),
                                        modifier = Modifier.padding(end = 4.dp),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    items(displaySongs, key = { it.id }) { song ->
                        EnhancedSongListItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            song = song,
                            isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                            isPlaying = stablePlayerState.isPlaying && stablePlayerState.currentSong?.id == song.id,
                            onClick = {
                                playerViewModel.showAndPlaySong(
                                    song = song,
                                    contextSongs = allSongs,
                                    queueName = queueName,
                                    isVoluntaryPlay = false
                                )
                            },
                            onMoreOptionsClick = {
                                playerViewModel.selectSongForInfo(song)
                                showSongInfoSheet = true
                            }
                        )
                    }

                    // 底部加载指示：切片过程中展示小进度
                    if (uiState.hasMore) {
                        item(key = "toplist_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator(modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        FilledIconButton(
            onClick = { navController.popBackStack() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.auth_cd_back)
            )
        }

        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(80.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {
        }

        // Top Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(50.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            Color.Transparent
                        )
                    )
                )
        ) {
        }
    }
}

/**
 * 排行榜标题头：与完整每日合集（ExpressiveDailyMixHeader）完全一致的设计。
 * - 3 张专辑封面交错旋转（180/220/180dp，-15°/0°/15°）+ 视差滚动淡出
 * - 渐变遮罩
 * - 可变字体大标题（gflex_variable 44sp）+ 平台·榜单名 + 歌曲数
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToplistExpressiveHeader(
    songs: List<Song>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    title: String,
    subtitle: String,
    songsCount: Int
) {
    val albumArts = remember(songs) { songs.map { it.albumArtUriString }.distinct().take(3) }
    val parallaxOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.5f else 0f
        }
    }
    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 600f)).coerceIn(0f, 1f)
        }
    }
    val titleStyle = rememberToplistTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-80).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                albumArts.forEachIndexed { index, artUrl ->
                    val size = when (index) {
                        0 -> 180.dp
                        1 -> 220.dp
                        2 -> 180.dp
                        else -> 150.dp
                    }
                    val rotation = when (index) {
                        0 -> -15f
                        1 -> 0f
                        2 -> 15f
                        else -> 0f
                    }
                    val shape = threeShapeSwitch(index, thirdShapeCornerRadius = 30.dp)

                    if (index == 2) {
                        Box(
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(width = size.roundToPx(), height = size.roundToPx())
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    val xOffset = (constraints.maxWidth - placeable.width) / 2
                                    placeable.placeRelative(xOffset, 0)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = rotation }
                                    .clip(shape)
                            ) {
                                SmartImage(
                                    model = artUrl ?: R.drawable.rounded_album_24,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(size)
                                .graphicsLayer { rotationZ = rotation }
                                .clip(shape)
                        ) {
                            SmartImage(
                                model = artUrl ?: R.drawable.rounded_album_24,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 900f
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    modifier = Modifier.padding(start = 3.dp),
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    modifier = Modifier.padding(start = 3.dp),
                    text = stringResource(R.string.toplist_songs_count, songsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberToplistTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(436),
                        FontVariation.width(102f),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 44.sp
        )
    }
}
