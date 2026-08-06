@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import timber.log.Timber
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.ArtistAlbumSection
import com.theveloper.pixelplay.presentation.viewmodel.ArtistHomepageViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor
import com.theveloper.pixelplay.presentation.viewmodel.FavoriteArtistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.hazeSource

/**
 * Hilt EntryPoint：获取封面取色处理器（歌手页专辑卡片按各自封面取色）
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArtistHomepageColorEntryPoint {
    fun colorSchemeProcessor(): ColorSchemeProcessor
}

@Composable
fun ArtistHomepageScreen(
    artistId: Long,
    artistName: String?,
    artistAvatar: String?,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistHomepageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    // ⚡ 收藏歌手
    val favoriteArtistViewModel: FavoriteArtistViewModel = hiltViewModel()
    val favoriteArtistIds by favoriteArtistViewModel.favoriteIds.collectAsStateWithLifecycle()
    val isArtistFavorite = favoriteArtistIds.contains(artistId)

    // 封面取色处理器：专辑列表每个卡片从自己的封面取色
    val context = LocalContext.current
    val colorSchemeProcessor = remember(context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ArtistHomepageColorEntryPoint::class.java
        )
        entryPoint.colorSchemeProcessor()
    }
    val albumPaletteStyle by playerViewModel.albumArtPaletteStyle.collectAsStateWithLifecycle()

    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showSongSortSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = MiniPlayerHeight + systemNavBarInset + 16.dp

    val lazyListState = rememberLazyListState()

    // 自动加载更多（歌曲/专辑均滚动到底部自动加载）
    val shouldLoadMore by remember {
        derivedStateOf {
            val isAlbumsTab = uiState.selectedTab == "albums"
            val guard = if (isAlbumsTab) {
                uiState.isLoading || uiState.isLoadingMoreAlbums || !uiState.albumHasMore
            } else {
                uiState.isLoading || uiState.isLoadingMore || !uiState.hasMore
            }
            if (guard) {
                false
            } else {
                val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                val totalItems = lazyListState.layoutInfo.totalItemsCount
                lastVisibleItemIndex != null && lastVisibleItemIndex >= totalItems - 3
            }
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.selectedTab) {
        if (shouldLoadMore) {
            when (uiState.selectedTab) {
                "albums" -> viewModel.loadMoreAlbums(playerViewModel.neteaseCookie)
                else -> viewModel.loadMoreSongs(playerViewModel.neteaseCookie)
            }
        }
    }

    // 当 artistId 变化时触发加载
    androidx.compose.runtime.LaunchedEffect(artistId) {
        if (artistId > 0) {
            Timber.d("ArtistHomepageScreen: Loading artist data for artistId=$artistId")
            viewModel.loadArtistData(artistId, playerViewModel.neteaseCookie)
        }
    }

    val backgroundHeight = 320.dp
    val surfaceContainer = MaterialTheme.colorScheme.surface

    // 渐隐遮罩：从顶部透明到底部实色（让背景图自然过渡到内容区域）
    val fadeBrush = remember(surfaceContainer) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                surfaceContainer.copy(alpha = 0.3f),
                surfaceContainer.copy(alpha = 0.7f),
                surfaceContainer
            ),
            startY = 0f,
            endY = Float.POSITIVE_INFINITY
        )
    }

    val orderTitle = if (uiState.order == "hot") "热门歌曲" else "最新歌曲"

    // 歌曲信息底部弹窗
    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        val removeFromListTrigger = remember { {} }
        com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { showSongInfoSheet = false },
            onPlaySong = {
                playerViewModel.showAndPlaySong(
                    song,
                    uiState.songs,
                    uiState.artistName.ifBlank { "歌手热门歌曲" },
                    isVoluntaryPlay = false
                )
            },
            onAddToQueue = { playerViewModel.addSongToQueue(song) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
            onAddToPlayList = { },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                navController.navigateSafely(
                    com.theveloper.pixelplay.presentation.navigation.Screen.AlbumDetail.createRoute(
                        song.albumId
                    )
                )
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                navController.navigateSafely(
                    com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.createRoute(
                        song.artistId
                    )
                )
                showSongInfoSheet = false
            },
            onNavigateToArtistById = { aid ->
                navController.navigateSafely(
                    com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.createRoute(aid)
                )
                showSongInfoSheet = false
            },
            onOpenNeteaseArtistHomepage = {
                playerViewModel.fetchNeteaseArtistId(song.neteaseId ?: 0L) { aid ->
                    aid?.let {
                        navController.navigateSafely(
                            Screen.ArtistHomepage.createRoute(it)
                        )
                    }
                }
                showSongInfoSheet = false
                Unit
            },
            onNavigateToGenre = {},
            onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
            generateAiMetadata = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Result.failure(Exception("AI metadata not supported"))
            } },
            removeFromListTrigger = removeFromListTrigger,
            isGeneratingMetadata = false,
            aiMetadataSuccess = false,
            aiError = null,
            onRetryMetadata = {}
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceContainer)
    ) {
        // 视差背景图（仅在有背景图 URL 时显示）
        if (!uiState.isLoading && uiState.backgroundUrl.isNotBlank()) {
            val scrollOffset by remember {
                derivedStateOf {
                    if (lazyListState.firstVisibleItemIndex == 0) {
                        -lazyListState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        -10000f
                    }
                }
            }

            AsyncImage(
                model = uiState.backgroundUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backgroundHeight + 200.dp)
                    .graphicsLayer {
                        val overscroll = if (scrollOffset > 0) scrollOffset else 0f
                        translationY = -overscroll * 0.3f
                        val scale = 1f + (overscroll / 1000f)
                        scaleX = scale
                        scaleY = scale
                        alpha = if (scrollOffset < -backgroundHeight.toPx() * 0.8f) 0.15f else 1f
                    },
                contentScale = ContentScale.Crop,
                placeholder = null,
                error = null
            )

            // 渐隐遮罩：从透明到背景色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backgroundHeight + 200.dp)
                    .background(fadeBrush)
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }

            uiState.error != null -> {
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

            else -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(MainActivity.LocalHazeState.current),
                    contentPadding = PaddingValues(bottom = bottomBarHeightDp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 透明 Spacer：留出背景图可见区域
                    item(key = "background_spacer") {
                        Spacer(Modifier.height(backgroundHeight - 80.dp))
                    }

                    // 歌手头像和名字
                    item(key = "artist_header") {
                        ArtistHomepageHeader(
                            artistName = uiState.artistName.ifBlank { artistName ?: "未知歌手" },
                            artistAvatar = uiState.artistAvatar.ifBlank { artistAvatar ?: "" },
                            identifyTag = uiState.identifyTag,
                            identityImages = uiState.identityImages,
                            briefDesc = uiState.briefDesc,
                            alias = uiState.alias,
                            tags = uiState.tags,
                            songCount = uiState.songCount,
                            albumCount = uiState.albumCount,
                            isFavorite = isArtistFavorite,
                            onFavoriteClick = {
                                favoriteArtistViewModel.toggleFavorite(
                                    id = artistId,
                                    name = uiState.artistName.ifBlank { artistName ?: "未知歌手" },
                                    avatar = uiState.artistAvatar.ifBlank { artistAvatar ?: "" },
                                    alias = uiState.alias.joinToString(" / ")
                                )
                            }
                        )
                    }

                    // 播放/随机播放按钮
                    item(key = "play_shuffle_buttons") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.songs.isNotEmpty()) {
                                        playerViewModel.playSongs(
                                            uiState.songs,
                                            uiState.songs.first(),
                                            uiState.artistName.ifBlank { "歌手歌曲" }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp),
                                enabled = uiState.songs.isNotEmpty(),
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
                                    contentDescription = stringResource(R.string.cd_play),
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(
                                    text = stringResource(R.string.cd_play),
                                    modifier = Modifier.padding(end = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (uiState.songs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = uiState.songs,
                                            queueName = uiState.artistName.ifBlank { "歌手歌曲" },
                                            startAtZero = true
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp),
                                enabled = uiState.songs.isNotEmpty(),
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
                                    contentDescription = "随机播放",
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(
                                    text = "随机",
                                    modifier = Modifier.padding(end = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 歌曲 / 专辑 切换标题（模仿媒体库 PrimaryScrollableTabRow）
                    item(key = "songs_albums_tabs") {
                        val tabs = listOf(
                            "歌曲" to "songs",
                            "专辑" to "albums"
                        )
                        val selectedTabIndex = remember(uiState.selectedTab) {
                            tabs.indexOfFirst { it.second == uiState.selectedTab }.coerceAtLeast(0)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = Color.Transparent,
                                edgePadding = 0.dp,
                                indicator = {},
                                divider = {},
                                modifier = Modifier.weight(1f)
                            ) {
                                tabs.forEachIndexed { index, (label, code) ->
                                    TabAnimation(
                                        index = index,
                                        title = code,
                                        selectedIndex = selectedTabIndex,
                                        onClick = { viewModel.selectTab(code) }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // 排序选择（仅在歌曲 tab 时显示，模仿媒体库底部弹窗）
                            if (uiState.selectedTab == "songs" && (uiState.songs.isNotEmpty() || uiState.isLoading)) {
                                TextButton(
                                    onClick = { showSongSortSheet = true },
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text(
                                        text = if (uiState.order == "hot") "热门" else "最新",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (uiState.selectedTab == "songs")
                                    "${uiState.songs.size}/${uiState.songCount}"
                                else
                                    "${uiState.albums.size}/${uiState.albumCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 根据 tab 显示歌曲列表或专辑列表
                    if (uiState.selectedTab == "songs") {
                        if (uiState.songs.isEmpty() && uiState.isInitialLoading) {
                            item(key = "songs_loading") {
                                ArtistHomepageLoadingHint(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        items(uiState.songs, key = { it.id }) { song ->
                            LibraryPlaybackAwareSongItem(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                song = song,
                                playerViewModel = playerViewModel,
                                onMoreOptionsClick = { playerViewModel.selectSongForInfo(song) },
                                onClick = {
                                    playerViewModel.showAndPlaySong(
                                        song,
                                        uiState.songs,
                                        uiState.artistName.ifBlank { "歌手歌曲" },
                                        isVoluntaryPlay = false
                                    )
                                }
                            )
                        }

                    // 加载更多 / 已全部加载
                    item(key = "load_more") {
                        if (uiState.isLoadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator()
                            }
                        } else if (uiState.hasMore && uiState.songs.isNotEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.loadMoreSongs(playerViewModel.neteaseCookie)
                                    },
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "加载更多",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else if (uiState.songs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "已加载全部 ${uiState.songs.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    }
                    
                    if (uiState.selectedTab == "albums") {
                        if (uiState.albums.isEmpty() && uiState.isInitialLoading) {
                            item(key = "albums_loading") {
                                ArtistHomepageLoadingHint(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        items(uiState.albums, key = { it.albumId }) { album ->
                            ArtistAlbumCard(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                album = album,
                                colorSchemeProcessor = colorSchemeProcessor,
                                paletteStyle = albumPaletteStyle,
                                onClick = {
                                    navController.navigateSafely(
                                        Screen.AlbumDetail.createRoute(album.albumId)
                                    )
                                }
                            )
                        }
                        item(key = "load_more_albums") {
                            if (uiState.albums.isNotEmpty()) {
                                if (uiState.isLoadingMoreAlbums) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ContainedLoadingIndicator()
                                    }
                                } else if (uiState.albumHasMore) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.loadMoreAlbums(playerViewModel.neteaseCookie)
                                            },
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "加载更多专辑",
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "已加载全部 ${uiState.albums.size} 张专辑",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 返回按钮
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

        // 顶部渐变
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
        )

        // 底部渐变
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
        )

        // 歌曲排序底部弹窗（模仿媒体库排序 sheet）
        if (showSongSortSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val sortOptions = listOf("hot" to "热门歌曲", "time" to "最新歌曲")
            ModalBottomSheet(
                onDismissRequest = { showSongSortSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "排序方式",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    sortOptions.forEach { (code, label) ->
                        val isSelected = uiState.order == code
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.changeOrder(code, playerViewModel.neteaseCookie)
                                    showSongSortSheet = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHomepageHeader(
    artistName: String,
    artistAvatar: String,
    identifyTag: String,
    identityImages: List<String>,
    briefDesc: String,
    alias: List<String>,
    tags: List<String>,
    songCount: Int,
    albumCount: Int,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头像（圆形）+ 右下角收藏按钮
        Box(contentAlignment = Alignment.BottomEnd) {
            if (artistAvatar.isNotBlank()) {
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(160.dp)
                ) {
                    AsyncImage(
                        model = artistAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = null,
                        error = null
                    )
                }
            } else {
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(160.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(70.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .size(44.dp)
                    .offset(x = 4.dp, y = 4.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isFavorite) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.cd_unfavorite_artist else R.string.cd_favorite_artist
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 歌手名 + 网易云音乐来源标识
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 网易云图标：红色圆底 + 白色云朵，标识该歌手来自网易云音乐
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEC4141)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "网易云音乐",
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            }
        }

        // 认证标识（图片标识）
        if (identityImages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                identityImages.take(3).forEach { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else if (identifyTag.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = identifyTag,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(8.dp))

        // 歌曲/专辑数量
        Text(
            text = "$songCount 首歌曲 · $albumCount 张专辑",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 别名 / 标签
        if (alias.isNotEmpty() || tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (alias + tags).distinct().take(10).forEach { tag ->
                    androidx.compose.material3.SuggestionChip(
                        onClick = { },
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        enabled = false
                    )
                }
            }
        }

        // 个人简介
        if (briefDesc.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "歌手简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = briefDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ArtistAlbumCard(
    modifier: Modifier = Modifier,
    album: com.theveloper.pixelplay.presentation.viewmodel.NeteaseArtistAlbumSection,
    colorSchemeProcessor: ColorSchemeProcessor,
    paletteStyle: AlbumArtPaletteStyle,
    onClick: () -> Unit
) {
    val cardCornerRadius = 16.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)
    val isDarkTheme = isSystemInDarkTheme()

    // 从该专辑自己的封面异步取色（生成明/暗两套配色）
    var albumScheme by remember { mutableStateOf<ColorSchemePair?>(null) }
    LaunchedEffect(album.coverUrl, paletteStyle) {
        if (album.coverUrl.isNotBlank()) {
            runCatching {
                colorSchemeProcessor.getOrGenerateColorScheme(album.coverUrl, paletteStyle)
            }.onSuccess { albumScheme = it }
                .onFailure { Timber.w(it, "专辑封面取色失败: ${album.coverUrl}") }
        }
    }
    val cardScheme = if (isDarkTheme) albumScheme?.dark else albumScheme?.light
    // 取色完成前回退到全局主题色，完成后整卡切换为该专辑封面的配色
    val gradientBaseColor = cardScheme?.primaryContainer ?: MaterialTheme.colorScheme.primaryContainer
    val onGradientColor = cardScheme?.onPrimaryContainer ?: MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = gradientBaseColor.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT: Album Art
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxHeight()
                ) {
                    if (album.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = album.coverUrl,
                            contentDescription = album.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        gradientBaseColor
                                    )
                                )
                            )
                    )
                }

                // MIDDLE: Solid Background
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(gradientBaseColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            color = onGradientColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val yearText = if (album.year != null) "${album.year}" else ""
                        val countText = if (album.songCount > 0) "${album.songCount} 首" else ""
                        val metaText = listOf(yearText, countText)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")

                        if (metaText.isNotBlank()) {
                            Text(
                                text = metaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = onGradientColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 歌手主页数据加载中的提示（首屏异步加载歌曲/专辑期间显示）。
 */
@Composable
private fun ArtistHomepageLoadingHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ContainedLoadingIndicator()
        Text(
            text = "加载中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
