package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.toplist.ToplistCatalog
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private const val ToplistHomePreviewLimit = 4

/**
 * 首页「排行榜」区块（对齐落雪 Home Leaderboard + 本项目每日合辑卡片视觉）：
 * - 容器 Card（手机模式同样有背景容器，平板由外层横屏卡片提供）
 * - 平台 tabs（网易云/QQ音乐/酷狗/酷我/咪咕）→ 点按切换平台
 * - 当前平台榜单 chips → 点按切换榜单（缓存命中秒开）
 * - 歌曲预览固定 4 首、不滚动（模仿每日合辑）
 * - 底部「查看全部」进入全屏榜单页（无限下滑）
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToplistHomeSection(
    selectedPlatform: ToplistCatalog.Platform,
    selectedToplistId: String,
    songs: List<Song>,
    isLoading: Boolean,
    error: String?,
    onSelectPlatform: (ToplistCatalog.Platform) -> Unit,
    onSelectToplist: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenAllClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    isTabletMode: Boolean = false
) {
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }
    val queueName = stringResource(R.string.home_toplist_title)
    val visibleSongs = remember(songs) { songs.take(ToplistHomePreviewLimit) }
    val platformEntries = ToplistCatalog.entriesFor(selectedPlatform)
    val selectedEntry = ToplistCatalog.findEntry(selectedToplistId)

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 标题行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.padding(start = 6.dp),
                            text = stringResource(R.string.home_toplist_title),
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        modifier = Modifier.padding(start = 6.dp),
                        text = stringResource(R.string.home_toplist_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── 平台 tabs（复刻媒体库紧凑大胶囊 LibraryNavigationPill）──
            // 选中平台胶囊 = 标题段 + 4dp 可见间隙 + 箭头段 两个独立 Surface（同底色、
            // tonalElevation 8dp），中间空隙透出父背景形成「分割」；标题 gflex 可变字体同媒体库。
            // 箭头段圆角随展开 4→50 动画，箭头旋转 180°，点击箭头打开榜单切换器。
            LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ToplistCatalog.platforms, key = { it.key }) { platform ->
                    ToplistPlatformTabPill(
                        platform = platform,
                        selected = platform == selectedPlatform,
                        pickerOpen = showBoardPicker,
                        onClick = { onSelectPlatform(platform) },
                        onArrowClick = { showBoardPicker = true }
                    )
                }
            }

            // ── 歌曲预览（固定 4 首、不滚动）──
            when {
                visibleSongs.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        visibleSongs.forEach { song ->
                            EnhancedSongListItem(
                                song = song,
                                isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                isPlaying = stablePlayerState.isPlaying &&
                                    stablePlayerState.currentSong?.id == song.id,
                                containerColorOverride = Color.Transparent,
                                onMoreOptionsClick = {
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoSheet = true
                                },
                                customShape = RoundedCornerShape(10.dp),
                                showAlbumArt = true,
                                onClick = {
                                    playerViewModel.showAndPlaySong(
                                        song = song,
                                        contextSongs = songs,
                                        queueName = queueName,
                                        isVoluntaryPlay = false
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.toplist_loading_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Button(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.toplist_retry))
                        }
                    }
                }
                else -> {
                    Text(
                        modifier = Modifier.padding(vertical = 12.dp),
                        text = stringResource(R.string.toplist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── 查看全部 ──
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                onClick = onOpenAllClick,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.Transparent
                ),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 10.dp,
                    cornerRadiusTR = 10.dp,
                    smoothnessAsPercentTL = 70,
                    smoothnessAsPercentTR = 70,
                    cornerRadiusBL = 60.dp,
                    cornerRadiusBR = 60.dp,
                    smoothnessAsPercentBL = 70,
                    smoothnessAsPercentBR = 70
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.toplist_see_all) + " · " + (selectedEntry?.name ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        painter = painterResource(R.drawable.rounded_arrow_forward_24),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (isTabletMode) {
        // 平板/横屏：外层已有 Card 容器，只渲染内容
        Box(modifier = modifier) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), // 对齐听歌统计卡片
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                content()
            }
        }
    }

    if (showBoardPicker) {
        ToplistBoardPickerSheet(
            entries = platformEntries,
            selectedToplistId = selectedToplistId,
            onSelect = { id ->
                onSelectToplist(id)
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
            onPlaySong = {
                playerViewModel.showAndPlaySong(
                    song = song,
                    contextSongs = songs,
                    queueName = queueName,
                    isVoluntaryPlay = false
                )
            },
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
}

/**
 * 排行榜平台选择胶囊（样式对齐媒体库紧凑大胶囊 LibraryNavigationPill）：
 * - 标题段 + 中间 4dp 可见间隙 + 箭头段（箭头段仅在选中平台时展开显示）
 * - 未选中：整颗胶囊，标题段全圆角（50/50/50/50）
 * - 选中未打开切换器：标题段右角收为 4dp，箭头段展开（左圆角保持 4dp），箭头朝下
 * - 选中且榜单切换器打开（pickerOpen）：箭头段左圆角展开到 50dp，箭头旋转 180° 向上
 * - 选中态两段均为 primaryContainer + tonalElevation 8dp；未选中为 surface/onSurface
 * - 标题使用 gflex 可变字体（同媒体库胶囊），常规字重、字号缩小
 * - 标题段点击切换平台，箭头段点击打开榜单切换器
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ToplistPlatformTabPill(
    platform: ToplistCatalog.Platform,
    selected: Boolean,
    pickerOpen: Boolean,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillRadius = 50.dp
    val innerRadius = 4.dp
    val pillHeight = 44.dp
    val arrowContentWidth = 28.dp
    val pillGap = 4.dp

    val animatedArrowCorner by animateDpAsState(
        // 箭头段左圆角保持 4dp（与标题段右角衔接成"分离"缝），
        // 仅当榜单切换器实际打开（pickerOpen）时才展开到 50dp
        targetValue = if (pickerOpen) pillRadius else innerRadius,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ToplistArrowCorner"
    )
    val arrowRotation by animateFloatAsState(
        // 箭头向上（180°）仅在榜单切换器打开时出现；选中但未打开时保持向下
        targetValue = if (pickerOpen) 180f else 0f,
        label = "ToplistArrowRotation"
    )

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val titleStyle = remember { toplistPlatformPillTitleStyle() }

    Row(
        modifier = modifier.height(pillHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(pillGap)
    ) {
        // ── 标题段（左段）──
        // 未选中：全圆角胶囊；选中：右角收为 4dp 与箭头段衔接
        Surface(
            shape = RoundedCornerShape(
                topStart = pillRadius,
                bottomStart = pillRadius,
                topEnd = if (selected) innerRadius else pillRadius,
                bottomEnd = if (selected) innerRadius else pillRadius
            ),
            tonalElevation = if (selected) 8.dp else 0.dp,
            color = if (selected) containerColor else surfaceColor,
            modifier = Modifier
                .height(pillHeight)
                .clip(
                    RoundedCornerShape(
                        topStart = pillRadius,
                        bottomStart = pillRadius,
                        topEnd = if (selected) innerRadius else pillRadius,
                        bottomEnd = if (selected) innerRadius else pillRadius
                    )
                )
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = platform.label,
                    style = titleStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    color = if (selected) onContainerColor else onSurfaceColor
                )
            }
        }

        // ── 箭头段（右段，仅在选中平台时展开）──
        // 展开动画：宽度 + 淡入；收起动画：宽度收缩 + 淡出。
        // 箭头旋转 180° 向上仅发生在榜单切换器打开（pickerOpen）时。
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(animationSpec = spring(stiffness = 400f)),
            exit = shrinkHorizontally(
                animationSpec = spring(stiffness = 500f)
            ) + fadeOut(animationSpec = spring(stiffness = 500f))
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = animatedArrowCorner,
                    bottomStart = animatedArrowCorner,
                    topEnd = pillRadius,
                    bottomEnd = pillRadius
                ),
                tonalElevation = if (selected) 8.dp else 0.dp,
                color = if (selected) containerColor else surfaceColor,
                modifier = Modifier
                    .height(pillHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = animatedArrowCorner,
                            bottomStart = animatedArrowCorner,
                            topEnd = pillRadius,
                            bottomEnd = pillRadius
                        )
                    )
                    .clickable(onClick = onArrowClick)
            ) {
                Box(
                    modifier = Modifier
                        .width(arrowContentWidth)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrowRotation),
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.toplist_see_all),
                        tint = if (selected) onContainerColor else onSurfaceColor
                    )
                }
            }
        }
    }
}

private fun toplistPlatformPillTitleStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily(
        Font(
            resId = R.font.gflex_variable,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(400),
                FontVariation.width(100f),
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("XTRA", 520f),
                FontVariation.Setting("YOPQ", 90f),
                FontVariation.Setting("YTLC", 505f)
            )
        )
    ),
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.2).sp
)

/**
 * 榜单选择器（模仿媒体库 LibraryTabSwitcherSheet：网格 + 胶囊项，选中高亮）。
 * 动画：ModalBottomSheet 自带 spring 滑动，网格项选中切换由 Surface 颜色/阴影过渡。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToplistBoardPickerSheet(
    entries: List<ToplistCatalog.Entry>,
    selectedToplistId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_toplist_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.home_toplist_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                gridItems(entries, key = { it.id }) { entry ->
                    ToplistBoardGridItem(
                        entry = entry,
                        isSelected = entry.id == selectedToplistId,
                        onClick = { onSelect(entry.id) }
                    )
                }
            }
        }
    }
}

/**
 * 榜单网格项（模仿媒体库 LibraryTabGridItem：20dp 圆角、选中 primaryContainer + 圆形徽标）。
 */
@Composable
fun ToplistBoardGridItem(
    entry: ToplistCatalog.Entry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconContainer = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
