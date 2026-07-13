@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import timber.log.Timber
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.ArtistAlbumSection
import com.theveloper.pixelplay.presentation.viewmodel.ArtistHomepageViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource

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
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()

    var showSongInfoSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = MiniPlayerHeight + systemNavBarInset + 16.dp

    val lazyListState = rememberLazyListState()

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
                    modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current),
                    contentPadding = PaddingValues(bottom = bottomBarHeightDp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            albumCount = uiState.albumCount
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

                    // 歌曲 / 专辑 切换标题
                    item(key = "songs_albums_tabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabIcon = if (uiState.selectedTab == "songs") Icons.Rounded.MusicNote else Icons.Rounded.Album
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.size(8.dp))
                            // 歌曲 / 专辑 切换按钮
                            FilterChip(
                                selected = uiState.selectedTab == "songs",
                                onClick = { viewModel.selectTab("songs") },
                                label = {
                                    Text(
                                        text = if (uiState.order == "hot") "热门歌曲" else "最新歌曲"
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                            Spacer(Modifier.size(6.dp))
                            FilterChip(
                                selected = uiState.selectedTab == "albums",
                                onClick = { viewModel.selectTab("albums") },
                                label = { Text("专辑") },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (uiState.selectedTab == "songs")
                                    "${uiState.songs.size}/${uiState.songCount}"
                                else
                                    "${uiState.albums.size}/${uiState.albumCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            // 排序切换（仅在歌曲 tab 时显示）
                            if (uiState.selectedTab == "songs" && (uiState.songs.isNotEmpty() || uiState.isLoading)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = uiState.order == "hot",
                                        onClick = { viewModel.changeOrder("hot", playerViewModel.neteaseCookie) },
                                        label = { Text("热门") },
                                        colors = FilterChipDefaults.filterChipColors()
                                    )
                                    FilterChip(
                                        selected = uiState.order == "time",
                                        onClick = { viewModel.changeOrder("time", playerViewModel.neteaseCookie) },
                                        label = { Text("最新") },
                                        colors = FilterChipDefaults.filterChipColors()
                                    )
                                }
                            }
                        }
                    }

                    // 根据 tab 显示歌曲列表或专辑列表
                    if (uiState.selectedTab == "songs") {
                        items(uiState.songs, key = { it.id }) { song ->
                        EnhancedSongListItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            song = song,
                            isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                            isPlaying = stablePlayerState.currentSong?.id == song.id && stablePlayerState.isPlaying,
                            onClick = {
                                playerViewModel.showAndPlaySong(
                                    song,
                                    uiState.songs,
                                    uiState.artistName.ifBlank { "歌手歌曲" },
                                    isVoluntaryPlay = false
                                )
                            },
                            onMoreOptionsClick = {
                                playerViewModel.selectSongForInfo(song)
                                showSongInfoSheet = true
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
                        items(uiState.albums, key = { it.albumId }) { album ->
                            ArtistAlbumCard(
                                album = album,
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
    albumCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头像（圆形）
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

        Spacer(Modifier.height(16.dp))

        // 歌手名
        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

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
    album: com.theveloper.pixelplay.presentation.viewmodel.NeteaseArtistAlbumSection,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (album.coverUrl.isNotBlank()) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )

            val yearText = if (album.year != null) "${album.year}" else ""
            val countText = if (album.songCount > 0) "${album.songCount} 首" else ""
            val metaText = listOf(yearText, countText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")

            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
