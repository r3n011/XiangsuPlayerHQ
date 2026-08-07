package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.threeShapeSwitch
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.DailyRecommendUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.formatDuration
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 「每日推荐」列表页：展示网易云每日推荐歌单的全部歌曲。
 * 点击卡片进入本页查看歌曲列表，再由用户选择播放/随机播放，而非直接开播。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyRecommendScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navController: NavController
) {
    val dailyRecommendState by playerViewModel.dailyRecommendState.collectAsStateWithLifecycle()
    val currentSongId by remember {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val isShuffleEnabled by remember {
        playerViewModel.stablePlayerState.map { it.isShuffleEnabled }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    // ⚡ 当前正在逐首解析播放链接的歌曲 id（在歌曲标题右侧显示加载提示，与搜索页 loadingSongId 一致）
    val dailyResolvingSongId by playerViewModel.dailyResolvingSongId.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    var showSongInfoSheet by remember { mutableStateOf(false) }

    // 将每日推荐歌曲条目转为用于列表展示的 Song（播放时由 playDailyRecommend 统一解析地址）
    val displaySongs = remember(dailyRecommendState) {
        (dailyRecommendState as? DailyRecommendUiState.Ready)?.songs?.map { entry ->
            val detail = entry.song
            Song(
                id = "daily_${detail.id}",
                title = detail.name,
                artist = detail.artistString,
                artistId = 0L,
                artists = emptyList(),
                album = detail.albumName,
                albumId = 0L,
                path = "",
                contentUriString = "",
                albumArtUriString = detail.albumPic.takeIf { it.isNotBlank() },
                duration = detail.duration,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = System.currentTimeMillis(),
                dateModified = 0L,
                mimeType = "audio/mpeg",
                bitrate = null,
                sampleRate = null,
                telegramFileId = null,
                telegramChatId = null,
                neteaseId = detail.id,
                gdriveFileId = null,
                qqMusicMid = null,
                navidromeId = null,
                jellyfinId = null
            )
        } ?: emptyList()
    }

    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        val songIndex = displaySongs.indexOf(song).coerceAtLeast(0)
        SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { showSongInfoSheet = false },
            onPlaySong = { playerViewModel.playDailyRecommend(startIndex = songIndex) },
            onAddToQueue = { playerViewModel.addSongToQueue(song) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(song) },
            onAddToPlayList = { /* 播放列表页提供，此处占位 */ },
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
            removeFromListTrigger = { /* 每日推荐不支持移除 */ },
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
        when (val state = dailyRecommendState) {
            is DailyRecommendUiState.Loading, DailyRecommendUiState.Hidden -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }

            is DailyRecommendUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.daily_recommend_error),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { playerViewModel.refreshDailyRecommend(force = true) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.daily_recommend_retry))
                    }
                }
            }

            is DailyRecommendUiState.Ready -> {
                if (displaySongs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.daily_recommend_error),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    val totalDuration = remember(displaySongs) { displaySongs.sumOf { it.duration } }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "daily_recommend_header") {
                            DailyRecommendHeader(
                                songs = displaySongs,
                                totalDuration = totalDuration,
                                scrollState = lazyListState
                            )
                        }

                        item(key = "play_shuffle_buttons") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (displaySongs.isNotEmpty()) {
                                            playerViewModel.playDailyRecommend(shuffled = false)
                                            if (isShuffleEnabled) playerViewModel.toggleShuffle()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(76.dp),
                                    enabled = displaySongs.isNotEmpty(),
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
                                        contentDescription = stringResource(R.string.daily_recommend_play_cd),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TightWrapText(
                                        text = stringResource(R.string.presentation_batch_b_play_it),
                                        modifier = Modifier.padding(end = 4.dp),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        lineHeight = 20.sp
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (displaySongs.isNotEmpty()) {
                                            playerViewModel.playDailyRecommend(shuffled = true)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(76.dp),
                                    enabled = displaySongs.isNotEmpty(),
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
                                        contentDescription = stringResource(R.string.shortcut_shuffle_short),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TightWrapText(
                                        text = stringResource(R.string.shortcut_shuffle_short),
                                        modifier = Modifier.padding(end = 4.dp),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 2,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        items(displaySongs, key = { it.id }) { song ->
                            EnhancedSongListItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                song = song,
                                isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                isPlaying = currentSongId == song.id && isPlaying,
                                // ⚡ 正在逐首解析播放链接的歌曲：标题右侧显示"获取播放链接…"（与搜索页一致）
                                showLoading = dailyResolvingSongId == song.id,
                                loadingLabel = "获取播放链接…",
                                onClick = {
                                    playerViewModel.playDailyRecommend(startIndex = displaySongs.indexOf(song).coerceAtLeast(0))
                                },
                                onMoreOptionsClick = {
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoSheet = true
                                }
                            )
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

        // Bottom Gradient（与每日合集页一致）
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

        // Top Gradient（与每日合集页一致）
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

/** 每日推荐页头部：三张错落封面 + 视差滚动 + 大标题（网易云商标）+ 歌曲数量 / 总时长，与每日合集页保持一致 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun DailyRecommendHeader(
    songs: List<Song>,
    totalDuration: Long,
    scrollState: LazyListState
) {
    val title = stringResource(R.string.daily_recommend_title)
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
    val titleStyle = rememberDailyRecommendTitleStyle()

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = titleStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    // 网易云商标
                    Icon(
                        painter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    modifier = Modifier.padding(start = 3.dp),
                    text = pluralStringResource(
                        R.plurals.presentation_batch_b_songs_dot_duration,
                        songs.size,
                        songs.size,
                        formatDuration(totalDuration)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberDailyRecommendTitleStyle(): TextStyle {
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

/**
 * 批量获取播放链接时的进度对话框（样式与搜索页 LxMusicScreen 的 ProgressDialog 一致）。
 * 已废弃：改为在歌曲列表标题右侧显示加载提示（与搜索页 loadingSongId 一致），不再使用弹窗。
 */
