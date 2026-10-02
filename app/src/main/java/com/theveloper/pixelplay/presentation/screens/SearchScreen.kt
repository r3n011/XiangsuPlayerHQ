package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import coil.compose.AsyncImage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.heightIn

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.AppSearchField
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ToplistHomeSection
import com.theveloper.pixelplay.presentation.components.library.rememberLibraryListGridCells
import com.theveloper.pixelplay.presentation.viewmodel.ToplistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LxMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LxUiState
import com.theveloper.pixelplay.presentation.viewmodel.QQMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.QQSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.BilibiliMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.BilibiliUiState
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.lx.LxArtistInfo
import com.theveloper.pixelplay.data.lx.LxPlaylistInfo
import android.util.Log
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.PlaylistCover
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import androidx.compose.ui.res.stringResource

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    lxViewModel: LxMusicViewModel = hiltViewModel(),
    qqViewModel: QQMusicViewModel = hiltViewModel(),
    bilibiliViewModel: BilibiliMusicViewModel = hiltViewModel(),
    toplistViewModel: ToplistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    // ⚡ 在线搜索仅在「提交」时触发（回车/搜索按钮），避免边输入边请求触发接口风控；
    //    submittedQuery 记录最近一次提交的关键词，在线搜索的 LaunchedEffect 只依赖它。
    var submittedQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    // ⚡ 提交计数：即使关键词与上次相同（如失败后重试），点击搜索/回车也能再次触发在线搜索
    var submitTick by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 剪贴板检测分享链接
    var shareDialogResult by remember { mutableStateOf<com.theveloper.pixelplay.data.share.ShareResult?>(null) }

    // 分享链接确认弹窗（模仿更新弹窗样式）
    if (shareDialogResult != null) {
        val success = shareDialogResult as? com.theveloper.pixelplay.data.share.ShareResult.Success
        if (success != null) {
            val cardShape = RoundedCornerShape(28.dp)
            androidx.compose.material3.BasicAlertDialog(onDismissRequest = { shareDialogResult = null }) {
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // 标题区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("分享链接", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(success.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val matchInfo = if (success.unmatchedCount > 0) {
                                    "匹配 ${success.matchedSongs.size}/${success.totalCount} 首"
                                } else "共 ${success.totalCount} 首"
                                Text(matchInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 歌曲列表（模仿媒体库显示）
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                success.matchedSongs.take(5).forEach { song ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        ) {
                                            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.displayArtist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                if (success.matchedSongs.size > 5) {
                                    Text(
                                        "...还有 ${success.matchedSongs.size - 5} 首",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 按钮区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { shareDialogResult = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("取消") }
                            androidx.compose.material3.Button(
                                onClick = {
                                    if (success.matchedSongs.isNotEmpty()) {
                                        playerViewModel.playSongs(success.matchedSongs, success.matchedSongs.first(), "shared_${success.name}")
                                    }
                                    shareDialogResult = null; searchQuery = ""
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                enabled = success.matchedSongs.isNotEmpty()
                            ) { Text("播放") }
                        }
                    }
                }
            }
        }
    }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    // AI 底部聊天输入框需避开的底部区域：Scaffold 提供的 bottomBar（mini player + 导航）实际高度
    val contentBottomReserve = paddingValues.calculateBottomPadding()
    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val bottomGradientHeight = if (navBarStyle == NavBarStyle.FLOATING) 0.dp
        else resolveMainScreenBottomGradientHeight(navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    // ⚡ 网易云在线搜索子分类：0=歌曲，1=歌手
    var onlineSearchTab by rememberSaveable { mutableStateOf(0) }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    // ⚡ 在线子分类归一化：网易云（ONLINE）含「歌曲/歌手/歌单」，其余在线源仅「歌曲/歌单」，
    //    当用户从网易云切到其他在线源时把残留的「歌手」（1）归一为「歌曲」（0）。
    val onlineSubTab = when {
        currentFilter == SearchFilterType.ONLINE -> onlineSearchTab
        onlineSearchTab == 2 -> 2
        else -> 0
    }
    // ⚡ 搜索范围（本地 / 在线）：单排展示，左侧固定切换卡片决定右侧显示哪一组筛选按钮；
    //    各自记住最后一次的选择，来回切换时不丢失。
    val isOnlineScope = when (currentFilter) {
        SearchFilterType.ONLINE,
        SearchFilterType.KUWO_MUSIC,
        SearchFilterType.BILIBILI_MUSIC,
        SearchFilterType.LX_MUSIC -> true
        else -> false
    }
    var lastLocalFilter by rememberSaveable { mutableStateOf(SearchFilterType.ALL) }
    var lastOnlineFilter by rememberSaveable { mutableStateOf(SearchFilterType.ONLINE) }
    LaunchedEffect(currentFilter) {
        if (isOnlineScope) lastOnlineFilter = currentFilter else lastLocalFilter = currentFilter
    }
    val onlineSearchState by lxViewModel.uiState.collectAsStateWithLifecycle()
    val toplistUiState by toplistViewModel.uiState.collectAsStateWithLifecycle()
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    // ⚡ 滚动压缩顶部筛选栏：上滑收起「本地/在线 + 筛选标签」一行，下滑或回到顶部自动恢复（模仿媒体库）
    val searchChromeDensity = LocalDensity.current
    val searchChromeCollapseThresholdPx = with(searchChromeDensity) { 12.dp.toPx() }
    var searchChromeCollapsed by remember { mutableStateOf(false) }
    var searchChromeAccum by remember { mutableStateOf(0f) }
    val searchChromeConnection = remember(searchChromeCollapseThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                if (searchChromeAccum != 0f && (dy > 0f) != (searchChromeAccum > 0f)) {
                    searchChromeAccum = 0f
                }
                searchChromeAccum += dy
                if (searchChromeAccum <= -searchChromeCollapseThresholdPx) {
                    searchChromeCollapsed = true
                    searchChromeAccum = 0f
                } else if (searchChromeAccum >= searchChromeCollapseThresholdPx) {
                    searchChromeCollapsed = false
                    searchChromeAccum = 0f
                }
                // 只观察不消费，列表滚动不受影响
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 下滑方向仍有未被消费的位移 => 列表已到顶（含惯性滑到顶），自动展开
                if (available.y > 0f && consumed.y == 0f && searchChromeCollapsed) {
                    searchChromeCollapsed = false
                    searchChromeAccum = 0f
                }
                return Offset.Zero
            }
        }
    }
    // 高度 / 透明度都改到布局、绘制阶段按 State 解析，避免逐帧重组
    val searchChromeCollapseProgressState = animateFloatAsState(
        targetValue = if (searchChromeCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "SearchChromeCollapse"
    )
    // 清空关键词回到「分类浏览」时顶栏恢复展开
    LaunchedEffect(searchQuery.isBlank()) {
        if (searchQuery.isBlank()) {
            searchChromeCollapsed = false
            searchChromeAccum = 0f
        }
    }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // ⚡ 本地搜索：保留原有「边输入边搜」体验（SearchStateHolder 内部有 300ms 防抖）
    LaunchedEffect(searchQuery, currentFilter) {
        when (currentFilter) {
            SearchFilterType.ALL,
            SearchFilterType.SONGS,
            SearchFilterType.ALBUMS,
            SearchFilterType.ARTISTS,
            SearchFilterType.PLAYLISTS,
            SearchFilterType.AI_SEARCH -> playerViewModel.performSearch(searchQuery)
            else -> Unit
        }
    }

    // ⚡ 在线搜索：仅在「提交」（回车 / 搜索按钮）后触发，避免边输入边请求触发接口风控。
    //    切筛选或切子分类时，用最近一次提交的关键词重新搜索。
    LaunchedEffect(submittedQuery, submitTick, currentFilter, onlineSubTab, onlineSearchState.selectedSource) {
        val kw = submittedQuery
        if (kw.isBlank()) return@LaunchedEffect
        when (currentFilter) {
            SearchFilterType.ONLINE -> {
                // 网易云：固定使用官方搜索
                // ⚡ 不修改 selectedSource，避免把用户在其他音源上选中的状态悄悄改回网易云
                lxViewModel.keyword = kw
                when (onlineSubTab) {
                    1 -> lxViewModel.searchArtists()
                    2 -> lxViewModel.searchPlaylists(source = "wy")
                    else -> lxViewModel.search(source = "wy")
                }
            }
            SearchFilterType.LX_MUSIC -> {
                // 落雪：按主标签栏选中的音源走 JS 引擎搜索
                lxViewModel.keyword = kw
                if (onlineSubTab == 2) lxViewModel.searchPlaylists() else lxViewModel.search()
            }
            SearchFilterType.KUWO_MUSIC -> {
                qqViewModel.keyword = kw
                qqViewModel.search()
            }
            SearchFilterType.BILIBILI_MUSIC -> {
                bilibiliViewModel.keyword = kw
                bilibiliViewModel.search()
            }
            else -> Unit
        }
    }
    // ⚡ 提交搜索（回车 / 点击搜索按钮）：在线源据此触发请求，避免边输入边搜
    val submitOnlineSearch: () -> Unit = {
        val q = searchQuery.trim()
        if (q.isNotBlank()) playerViewModel.onSearchQuerySubmitted(q)
        submittedQuery = q
        submitTick++
        // 新搜索：展开被滚动收起的筛选栏
        searchChromeCollapsed = false
        searchChromeAccum = 0f
        keyboardController?.hide()
    }

    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }


    val dm = LocalPixelPlayDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .hazeSource(MainActivity.LocalHazeState.current)
            .nestedScroll(searchChromeConnection)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ⚡ 搜索框提取为全局组件（AppSearchField），听书搜索页复用同一视觉
                AppSearchField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        playerViewModel.updateSearchQuery(it)
                        // 检测粘贴的分享链接
                        val detectedLink = com.theveloper.pixelplay.data.share.ShareLinkCodec.extractShareLink(it)
                        if (detectedLink != null) {
                            coroutineScope.launch {
                                val result = playerViewModel.resolveShareLink(detectedLink)
                                when (result) {
                                    is com.theveloper.pixelplay.data.share.ShareResult.Success -> {
                                        if (result.matchedSongs.isNotEmpty() || result.totalCount > 0) {
                                            shareDialogResult = result
                                        }
                                    }
                                    is com.theveloper.pixelplay.data.share.ShareResult.Error -> {
                                        Toast.makeText(context, "分享链接无效", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onSearch = { submitOnlineSearch() },
                    placeholder = stringResource(R.string.search_placeholder),
                    modifier = Modifier.weight(1f),
                    focusRequester = searchInputFocusRequester
                )
            }

            val showGenreBrowse by remember(searchQuery) {
                derivedStateOf { searchQuery.isBlank() }
            }
            AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    GenreCategoriesGrid(
                        genres = genres,
                        onGenreClick = { genre ->
                            Timber.tag("SearchScreen")
                                .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                            val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                            navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenreId))
                        },
                        playerViewModel = playerViewModel,
                        modifier = Modifier.padding(top = 12.dp),
                        header = {
                            // ⚡ 排行榜（从首页移动到搜索页：未搜索时展示，对齐 lx-music 发现页；
                            // 仍遵循设置「首页排行榜」开关）
                            if (toplistUiState.enabled) {
                                ToplistHomeSection(
                                    selectedPlatform = toplistUiState.selectedPlatform,
                                    selectedToplistId = toplistUiState.selectedToplistId,
                                    songs = toplistUiState.displaySongs,
                                    isLoading = toplistUiState.isLoading,
                                    error = toplistUiState.error,
                                    onSelectPlatform = { toplistViewModel.selectPlatform(it) },
                                    onSelectToplist = { toplistViewModel.selectToplist(it) },
                                    onRetry = { toplistViewModel.refresh() },
                                    onOpenAllClick = {
                                        navController.navigateSafely(
                                            Screen.ToplistDetail.createRoute(toplistUiState.selectedToplistId)
                                        )
                                    },
                                    playerViewModel = playerViewModel,
                                    navController = navController
                                )
                            }
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // ⚡ 单排筛选栏：左侧固定「本地 / 在线」切换卡片，右侧为当前分组的可横向滚动筛选按钮
                        //    上滑时整行压缩收起（模仿媒体库顶栏），下滑 / 回到顶部自动恢复
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 淡出在绘制阶段读取 State；高度在布局阶段读取（只触发重布局 / 重绘，不重组）
                                .graphicsLayer {
                                    alpha = 1f - searchChromeCollapseProgressState.value
                                }
                                .clipToBounds()
                                .layout { measurable, constraints ->
                                    val collapse =
                                        searchChromeCollapseProgressState.value.coerceIn(0f, 1f)
                                    val placeable = measurable.measure(constraints)
                                    val collapsedHeight =
                                        (placeable.height * (1f - collapse)).toInt().coerceAtLeast(0)
                                    layout(placeable.width, collapsedHeight) {
                                        // 内容居中裁切，收起过程上下对称
                                        placeable.place(0, -((placeable.height - collapsedHeight) / 2))
                                    }
                                }
                                // ⚡ 左侧与搜索框（24dp）对齐；顶部 12dp + 底部 6dp，
                                //    与下方子分类栏的 6dp 相加 = 12dp，两处行距一致
                                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchScopeToggle(
                                isOnline = isOnlineScope,
                                onScopeChange = { online ->
                                    playerViewModel.updateSearchFilter(
                                        if (online) lastOnlineFilter else lastLocalFilter
                                    )
                                }
                            )
                            val filterScrollState = rememberScrollState()
                            LaunchedEffect(isOnlineScope) { filterScrollState.scrollTo(0) }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(filterScrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isOnlineScope) {
                                    // 在线组：网易云 / B站 + 内置音源（酷我/QQ音乐/酷狗/咪咕）+ 落雪 JS 音源
                                    SearchFilterChip(SearchFilterType.ONLINE, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.BILIBILI_MUSIC, currentFilter, playerViewModel)
                                    // ⚡ 内置音源（落雪同款官方搜索，不依赖 JS 导入）：酷我/QQ音乐/酷狗/咪咕
                                    LxSourceFilterChip(
                                        sourceKey = "kw",
                                        sourceName = "酷我",
                                        selected = currentFilter == SearchFilterType.LX_MUSIC &&
                                            onlineSearchState.selectedSource == "kw",
                                        onClick = {
                                            lxViewModel.selectedSource = "kw"
                                            playerViewModel.updateSearchFilter(SearchFilterType.LX_MUSIC)
                                        }
                                    )
                                    LxSourceFilterChip(
                                        sourceKey = "tx",
                                        sourceName = "QQ音乐",
                                        selected = currentFilter == SearchFilterType.LX_MUSIC &&
                                            onlineSearchState.selectedSource == "tx",
                                        onClick = {
                                            lxViewModel.selectedSource = "tx"
                                            playerViewModel.updateSearchFilter(SearchFilterType.LX_MUSIC)
                                        }
                                    )
                                    LxSourceFilterChip(
                                        sourceKey = "kg",
                                        sourceName = "酷狗",
                                        selected = currentFilter == SearchFilterType.LX_MUSIC &&
                                            onlineSearchState.selectedSource == "kg",
                                        onClick = {
                                            lxViewModel.selectedSource = "kg"
                                            playerViewModel.updateSearchFilter(SearchFilterType.LX_MUSIC)
                                        }
                                    )
                                    LxSourceFilterChip(
                                        sourceKey = "mg",
                                        sourceName = "咪咕",
                                        selected = currentFilter == SearchFilterType.LX_MUSIC &&
                                            onlineSearchState.selectedSource == "mg",
                                        onClick = {
                                            lxViewModel.selectedSource = "mg"
                                            playerViewModel.updateSearchFilter(SearchFilterType.LX_MUSIC)
                                        }
                                    )
                                    // ⚡ 落雪音源：只显示支持 musicSearch 的源（对齐落雪逻辑），点击后走 JS 引擎搜索该源
                                    onlineSearchState.sources.entries
                                        .filter { (_, info) -> info.actions.contains("musicSearch") }
                                        .forEach { (key, info) ->
                                            LxSourceFilterChip(
                                                sourceKey = key,
                                                sourceName = info.name.ifBlank { key },
                                                selected = currentFilter == SearchFilterType.LX_MUSIC &&
                                                    onlineSearchState.selectedSource == key,
                                                onClick = {
                                                    lxViewModel.selectedSource = key
                                                    playerViewModel.updateSearchFilter(SearchFilterType.LX_MUSIC)
                                                }
                                            )
                                        }
                                } else {
                                    // 本地组：仅搜索本机媒体库
                                    SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                                    SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                                }
                            }
                        }
                        Crossfade(
                            targetState = when {
                                currentFilter == SearchFilterType.ONLINE ->
                                    when (onlineSubTab) {
                                        1 -> Triple("online_artist", true, onlineSearchState.searchingArtists)
                                        2 -> Triple("online_playlist", true, onlineSearchState.searchingPlaylists)
                                        else -> Triple("online", true, onlineSearchState.searching)
                                    }
                                currentFilter == SearchFilterType.LX_MUSIC ->
                                    if (onlineSubTab == 2) {
                                        Triple("lx_playlist", true, onlineSearchState.searchingPlaylists)
                                    } else {
                                        Triple("lx", true, onlineSearchState.searching)
                                    }
                                currentFilter == SearchFilterType.KUWO_MUSIC ->
                                    Triple("qq", true, qqViewModel.uiState.value.searching)
                                currentFilter == SearchFilterType.BILIBILI_MUSIC ->
                                    Triple("bilibili", true, bilibiliViewModel.uiState.value.searching)
                                else ->
                                    Triple("local", searchResults.isEmpty(), false)
                            },
                            animationSpec = tween(durationMillis = 190),
                            label = "search_results_fade"
                        ) { (mode, isEmpty, isSearching) ->
                            // ⚡ 网易云子分类：歌曲 / 歌手 / 歌单，走同一渲染分支（含子分类切换）
                            if (mode == "online" || mode == "online_artist" || mode == "online_playlist") {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    OnlineSearchSubTabs(
                                        selectedTab = onlineSubTab,
                                        showArtistTab = true,
                                        onTabSelected = { onlineSearchTab = it }
                                    )
                                    when (onlineSubTab) {
                                        // ⚡ 歌手
                                        1 -> OnlineArtistResults(
                                            state = onlineSearchState,
                                            isSearching = isSearching as Boolean,
                                            // ⚡ 在线搜索改为提交触发，空态文案应以「已提交关键词」为准
                                            searchQuery = submittedQuery,
                                            colorScheme = colorScheme,
                                            onLoadMore = { lxViewModel.loadMoreArtists() },
                                            onArtistClick = { artist ->
                                                artist.id.toLongOrNull()?.let { artistId ->
                                                    if (artistId > 0L) {
                                                        navController.navigateSafely(
                                                            Screen.ArtistHomepage.createRoute(artistId)
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                        // ⚡ 歌单
                                        2 -> OnlinePlaylistResults(
                                            state = onlineSearchState,
                                            isSearching = isSearching as Boolean,
                                            searchQuery = submittedQuery,
                                            colorScheme = colorScheme,
                                            onLoadMore = { lxViewModel.loadMorePlaylists() },
                                            savingPlaylistId = onlineSearchState.savingPlaylistId,
                                            onSavePlaylist = { pl ->
                                                lxViewModel.savePlaylistToLocal(pl) { _, msg ->
                                                    playerViewModel.sendToast(msg)
                                                }
                                            },
                                            onPlaylistClick = { pl ->
                                                // ⚡ 点歌单 → 复用「媒体库歌单详情」界面（只读、不落库），
                                                //    页内提供「保存到本地」按钮
                                                navController.navigateSafely(
                                                    Screen.PlaylistDetail.createRoute(
                                                        PlaylistViewModel.buildOnlinePlaylistId(pl)
                                                    )
                                                )
                                            }
                                        )
                                        // ⚡ 歌曲
                                        else -> OnlineSearchResults(
                                            state = onlineSearchState,
                                            isSearching = isSearching as Boolean,
                                            searchQuery = submittedQuery,
                                            onPlaySong = { song ->
                                                // ⚡ 原子建队：点击歌曲 + 其余搜索结果一次性传入 playSongs。
                                                //    此前 playUrl（重置队列）与逐首 enqueue 并发执行，先后
                                                //    顺序不定导致播放列表时而只剩单曲、时而丢失部分结果。
                                                lxViewModel.playSearchResultWithQueue(
                                                    song = song,
                                                    onPlayQueue = { seeds, startIndex ->
                                                        val songs = seeds.mapNotNull {
                                                            playerViewModel.buildCloudSong(
                                                                it.url, it.title, it.artist, it.cover, it.songId,
                                                                lxSource = it.source,
                                                                platformSongId = it.platformSongId
                                                            )
                                                        }
                                                        val start = songs.getOrNull(startIndex) ?: songs.firstOrNull()
                                                        if (start != null) {
                                                            playerViewModel.playSongs(songs, start, "Cloud Play")
                                                        }
                                                    },
                                                    // ⚡ 后台按页续拉尚未加载的搜索结果，整批追加到队列尾部
                                                    onMoreSeeds = { moreSeeds ->
                                                        val moreSongs = moreSeeds.mapNotNull { seed ->
                                                            playerViewModel.buildCloudSong(
                                                                seed.url, seed.title, seed.artist, seed.cover, seed.songId,
                                                                lxSource = seed.source,
                                                                platformSongId = seed.platformSongId
                                                            )
                                                        }
                                                        playerViewModel.appendCloudSongsToQueue(moreSongs)
                                                    }
                                                )
                                            },
                                            favoriteIds = favoriteSongIds,
                                            onToggleFavorite = { song ->
                                                lxViewModel.toggleFavoriteForSong(song)
                                            },
                                            stableIdFn = { song -> lxViewModel.getStableSongId(song) },
                                            colorScheme = colorScheme,
                                            onLoadMore = { lxViewModel.loadMore() },
                                            currentPlayingSongId = stablePlayerState.currentSong?.id,
                                            isPlaying = stablePlayerState.isPlaying,
                                            loadingSongId = onlineSearchState.loadingSongId,
                                            loadingStep = onlineSearchState.progressLabel
                                        )
                                    }
                                }
                            } else if (mode == "lx" || mode == "lx_playlist") {
                                // ⚡ 落雪音源搜索结果：直接展示所选音源的搜索结果（歌曲 / 歌单）
                                val lxSourceLabel = run {
                                    val key = onlineSearchState.selectedSource
                                    when (key) {
                                        "wy" -> "网易云"
                                        "kw" -> "酷我"
                                        "tx" -> "QQ音乐"
                                        "kg" -> "酷狗"
                                        "mg" -> "咪咕"
                                        else -> onlineSearchState.sources[key]?.name?.ifBlank { key } ?: "音源"
                                    }
                                }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    OnlineSearchSubTabs(
                                        selectedTab = onlineSubTab,
                                        showArtistTab = false,
                                        onTabSelected = { onlineSearchTab = it }
                                    )
                                    if (onlineSubTab == 2) {
                                        OnlinePlaylistResults(
                                            state = onlineSearchState,
                                            isSearching = isSearching as Boolean,
                                            searchQuery = submittedQuery,
                                            colorScheme = colorScheme,
                                            onLoadMore = { lxViewModel.loadMorePlaylists() },
                                            savingPlaylistId = onlineSearchState.savingPlaylistId,
                                            onSavePlaylist = { pl ->
                                                lxViewModel.savePlaylistToLocal(pl) { _, msg ->
                                                    playerViewModel.sendToast(msg)
                                                }
                                            },
                                            onPlaylistClick = { pl ->
                                                // ⚡ 点歌单 → 复用「媒体库歌单详情」界面（只读、不落库），
                                                //    页内提供「保存到本地」按钮
                                                navController.navigateSafely(
                                                    Screen.PlaylistDetail.createRoute(
                                                        PlaylistViewModel.buildOnlinePlaylistId(pl)
                                                    )
                                                )
                                            }
                                        )
                                    } else {
                                        OnlineSearchResults(
                                            state = onlineSearchState,
                                            isSearching = isSearching as Boolean,
                                            searchQuery = submittedQuery,
                                            searchSourceLabel = lxSourceLabel,
                                            onPlaySong = { song ->
                                                // ⚡ 原子建队：点击歌曲 + 其余搜索结果一次性传入 playSongs。
                                                //    此前 playUrl（重置队列）与逐首 enqueue 并发执行，先后
                                                //    顺序不定导致播放列表时而只剩单曲、时而丢失部分结果。
                                                lxViewModel.playSearchResultWithQueue(
                                                    song = song,
                                                    onPlayQueue = { seeds, startIndex ->
                                                        val songs = seeds.mapNotNull {
                                                            playerViewModel.buildCloudSong(
                                                                it.url, it.title, it.artist, it.cover, it.songId,
                                                                lxSource = it.source,
                                                                platformSongId = it.platformSongId
                                                            )
                                                        }
                                                        val start = songs.getOrNull(startIndex) ?: songs.firstOrNull()
                                                        if (start != null) {
                                                            playerViewModel.playSongs(songs, start, "Cloud Play")
                                                        }
                                                    },
                                                    // ⚡ 后台按页续拉尚未加载的搜索结果，整批追加到队列尾部
                                                    onMoreSeeds = { moreSeeds ->
                                                        val moreSongs = moreSeeds.mapNotNull { seed ->
                                                            playerViewModel.buildCloudSong(
                                                                seed.url, seed.title, seed.artist, seed.cover, seed.songId,
                                                                lxSource = seed.source,
                                                                platformSongId = seed.platformSongId
                                                            )
                                                        }
                                                        playerViewModel.appendCloudSongsToQueue(moreSongs)
                                                    }
                                                )
                                            },
                                            favoriteIds = favoriteSongIds,
                                            onToggleFavorite = { song ->
                                                lxViewModel.toggleFavoriteForSong(song)
                                            },
                                            stableIdFn = { song -> lxViewModel.getStableSongId(song) },
                                            colorScheme = colorScheme,
                                            onLoadMore = { lxViewModel.loadMore() },
                                            currentPlayingSongId = stablePlayerState.currentSong?.id,
                                            isPlaying = stablePlayerState.isPlaying,
                                            loadingSongId = onlineSearchState.loadingSongId,
                                            loadingStep = onlineSearchState.progressLabel
                                        )
                                    }
                                }
                            } else if (mode == "qq") {
                                QQSearchResults(
                                    state = qqViewModel.uiState.collectAsStateWithLifecycle().value,
                                    isSearching = isSearching as Boolean,
                                    searchQuery = submittedQuery,
                                    onPlaySong = { song ->
                                        qqViewModel.playSong(song) { url, name, singer, cover, songId ->
                                            playerViewModel.playUrl(url, name, singer, cover, songId)
                                        }
                                        // ⚡ 分批解析其余搜索结果并整批追加（上限 100 首，避免卡顿）
                                        qqViewModel.enqueueAllSearchResults(
                                            qqViewModel.getStableSongId(song)
                                        ) { seeds ->
                                            val moreSongs = seeds.mapNotNull { seed ->
                                                playerViewModel.buildCloudSong(
                                                    seed.url, seed.title, seed.artist, seed.cover, seed.songId
                                                )
                                            }
                                            playerViewModel.appendCloudSongsToQueue(moreSongs)
                                        }
                                    },
                                    colorScheme = colorScheme,
                                    stableIdFn = { song -> qqViewModel.getStableSongId(song) },
                                    onLoadMore = { qqViewModel.loadMore() },
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying
                                )
                            } else if (mode == "bilibili") {
                                BilibiliSearchResults(
                                    state = bilibiliViewModel.uiState.collectAsStateWithLifecycle().value,
                                    isSearching = isSearching as Boolean,
                                    searchQuery = submittedQuery,
                                    onPlaySong = { song ->
                                        bilibiliViewModel.playSong(song) { url, name, singer, cover, songId, bvid ->
                                            playerViewModel.playUrl(url, name, singer, cover, songId, bilibiliBvid = bvid)
                                        }
                                        // ⚡ 分批解析其余搜索结果并整批追加（上限 100 首，避免卡顿）
                                        bilibiliViewModel.enqueueAllSearchResults(
                                            bilibiliViewModel.getStableSongId(song)
                                        ) { seeds ->
                                            val moreSongs = seeds.mapNotNull { seed ->
                                                playerViewModel.buildCloudSong(
                                                    seed.url, seed.title, seed.artist, seed.cover, seed.songId,
                                                    seed.bilibiliBvid
                                                )
                                            }
                                            playerViewModel.appendCloudSongsToQueue(moreSongs)
                                        }
                                    },
                                    colorScheme = colorScheme,
                                    stableIdFn = { song -> bilibiliViewModel.getStableSongId(song) },
                                    onLoadMore = { bilibiliViewModel.loadMore() },
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    loadingSongId = bilibiliViewModel.uiState.value.loadingSongId,
                                    loadingStep = bilibiliViewModel.uiState.value.progressLabel
                                )
                            } else if (isEmpty as Boolean) {
                                EmptySearchResults(
                                    searchQuery = searchQuery,
                                    colorScheme = colorScheme
                                )
                            } else {
                                SearchResultsList(
                                    results = searchResults,
                                    searchQuery = searchQuery,
                                    playerViewModel = playerViewModel,
                                    onItemSelected = {
                                        if (searchQuery.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                                        }
                                    },
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = currentSong?.let { favoriteSongIds.contains(it.id) } ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
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
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
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
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyVerticalGrid(
            columns = rememberLibraryListGridCells(),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = MiniPlayerHeight + systemBarPaddingBottom
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_no_search_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
            onItemSelected()
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyVerticalGrid(
        columns = rememberLibraryListGridCells(),
        modifier = Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                )
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(
                    key = "header_${filterType.name}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    SearchResultSectionHeader(
                        title = stringResource(
                            when (filterType) {
                                SearchFilterType.SONGS -> R.string.search_filter_songs
                                SearchFilterType.ALBUMS -> R.string.search_filter_albums
                                SearchFilterType.ARTISTS -> R.string.search_filter_artists
                                SearchFilterType.PLAYLISTS -> R.string.search_filter_playlists
                                else -> R.string.search_filter_all
                            }
                        )
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRoute(item.artist.id),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = "Album Art: ${album.title}",
                targetSize = SmartImageListTargetSize,
                // 搜索结果封面不落盘，避免磁盘缓存暴涨
                useDiskCache = false,
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = "Artist: ${artist.name}",
                    targetSize = SmartImageListTargetSize,
                    // 搜索结果封面不落盘，避免磁盘缓存暴涨
                    useDiskCache = false,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = "Artist",
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 56.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ⚡ 搜索范围切换卡片：固定在最左侧的单张卡片内切换「本地 / 在线」
@Composable
private fun SearchScopeToggle(
    isOnline: Boolean,
    onScopeChange: (Boolean) -> Unit
) {
    val selectedIndex = if (isOnline) 1 else 0
    val density = LocalDensity.current
    // ⚡ 选中胶囊滑动动画：记录两个分段各自的 (left, width)，指示器按目标值平移 + 变宽，
    //    不再是从一个分段"瞬移"到另一个分段。
    val segmentBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val target = segmentBounds[selectedIndex]
    val indicatorLeft by animateFloatAsState(
        targetValue = target?.first ?: 0f,
        animationSpec = tween(durationMillis = 240),
        label = "scopeIndicatorLeft"
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = target?.second ?: 0f,
        animationSpec = tween(durationMillis = 240),
        label = "scopeIndicatorWidth"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(2.dp)
        ) {
            if (indicatorWidth > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorLeft.roundToInt(), 0) }
                        .width(with(density) { indicatorWidth.toDp() })
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchScopeSegment(
                    label = "本地",
                    selected = selectedIndex == 0,
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val left = coordinates.positionInParent().x
                            val width = coordinates.size.width.toFloat()
                            val current = segmentBounds[0]
                            if (current == null || current.first != left || current.second != width) {
                                segmentBounds[0] = left to width
                            }
                        },
                    onClick = { onScopeChange(false) }
                )
                SearchScopeSegment(
                    label = "在线",
                    selected = selectedIndex == 1,
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val left = coordinates.positionInParent().x
                            val width = coordinates.size.width.toFloat()
                            val current = segmentBounds[1]
                            if (current == null || current.first != left || current.second != width) {
                                segmentBounds[1] = left to width
                            }
                        },
                    onClick = { onScopeChange(true) }
                )
            }
        }
    }
}

@Composable
private fun SearchScopeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 文字颜色跟着指示器一起过渡，避免"胶囊还在滑、字已经变色"
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "scopeSegmentContent"
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

private val SearchChipHeight = 32.dp
private val SearchChipIconSize = 16.dp
private val SearchChipIconSpacing = 6.dp

@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    SearchChip(
        label = when (filterType) {
            SearchFilterType.ALL -> stringResource(R.string.search_filter_all)
            SearchFilterType.SONGS -> stringResource(R.string.search_filter_songs)
            SearchFilterType.ALBUMS -> stringResource(R.string.search_filter_albums)
            SearchFilterType.ARTISTS -> stringResource(R.string.search_filter_artists)
            SearchFilterType.PLAYLISTS -> stringResource(R.string.search_filter_playlists)
            SearchFilterType.ONLINE -> stringResource(R.string.search_filter_online)
            SearchFilterType.KUWO_MUSIC -> stringResource(R.string.search_filter_kuwo)
            SearchFilterType.BILIBILI_MUSIC -> stringResource(R.string.search_filter_bilibili)
            SearchFilterType.LX_MUSIC -> "落雪"
            SearchFilterType.AI_SEARCH -> "AI 搜索"
        },
        selected = filterType == currentFilter,
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        modifier = modifier
    )
}

/**
 * ⚡ 搜索页二级筛选标签：与左侧「本地 / 在线」切换卡片同一套视觉语言
 * （未选中 = surfaceContainerHigh，选中 = primary），高度同为 32dp。
 * 勾选图标用「宽度 + 透明度」动画展开，避免选中瞬间整排标签跳动。
 */
@Composable
private fun SearchChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 常显的前置图标（歌曲 / 歌手 / 歌单子分类）；为空时只在选中时显示勾选图标。 */
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 220),
        label = "SearchChipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "SearchChipContent"
    )
    // 有固定图标时图标槽常开；否则只在选中时展开勾选图标
    val showIcon = leadingIcon != null || selected
    val iconSlotWidth by animateDpAsState(
        targetValue = if (showIcon) SearchChipIconSize + SearchChipIconSpacing else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "SearchChipIconSlot"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (showIcon) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "SearchChipIconAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (showIcon) 1f else 0.8f,
        animationSpec = tween(durationMillis = 220),
        label = "SearchChipIconScale"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(SearchChipHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(iconSlotWidth)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = leadingIcon ?: Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SearchChipIconSize)
                        .graphicsLayer {
                            alpha = iconAlpha
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** ⚡ 落雪音源过滤标签：与网易云/酷我/B站同一排，点击后切到落雪模式搜索该音源 */
@Composable
private fun LxSourceFilterChip(
    sourceKey: String,
    sourceName: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SearchChip(
        label = sourceName,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun OnlineSearchResults(
    state: LxUiState,
    isSearching: Boolean,
    searchQuery: String,
    onPlaySong: (LxSongInfo) -> Unit,
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: ((LxSongInfo) -> Unit)? = null,
    stableIdFn: (LxSongInfo) -> String = { "" },
    colorScheme: androidx.compose.material3.ColorScheme,
    onLoadMore: () -> Unit = {},
    currentPlayingSongId: String? = null,
    isPlaying: Boolean = false,
    /** 正在解析播放链接的歌曲 id（在该歌曲名称右侧显示加载提示） */
    loadingSongId: String? = null,
    /** 当前加载步骤文字（如「正在解析音源…」） */
    loadingStep: String? = null,
    /** 当前搜索的源名称，用于"正在搜索 xx…"提示（避免非网易云音源也显示网易云） */
    searchSourceLabel: String = "网易云音乐"
) {
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    when {
        isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在搜索$searchSourceLabel…",
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // ⚡ 仅在无结果时全屏显示错误（初始搜索失败）
        //   如果已有结果（如加载更多失败），则在列表底部显示错误提示
        state.error != null && state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        state.results.isEmpty() && searchQuery.isBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "输入关键词开始搜索$searchSourceLabel",
                        color = colorScheme.onSurfaceVariant
                    )
                    if (!state.engineReady) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请先在设置中导入 JS 音源",
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "没有找到相关歌曲",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val listState = rememberLazyGridState()
            // ⚡ 监听滚动：当滚动到接近列表底部时触发加载更多
            LaunchedEffect(listState, state.isEnd, state.isLoadingMore, state.results.size) {
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    val totalItems = state.results.size
                    if (visibleItemsInfo.isEmpty()) return@snapshotFlow false
                    val lastVisibleIndex = visibleItemsInfo.last().index
                    // 滚动到倒数第 5 项时触发加载更多；已到最后一页或正在加载时不触发
                    lastVisibleIndex >= totalItems - 5 && !state.isEnd && !state.isLoadingMore
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                columns = rememberLibraryListGridCells(),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = MiniPlayerHeight + systemBarPaddingBottom
                )
            ) {
                items(items = state.results, key = { song -> song.id }) { song ->
                    UnifiedOnlineSongItem(
                        title = song.name,
                        subtitle = listOfNotNull(
                            song.singer.ifBlank { null },
                            song.albumName.ifBlank { null }
                        ).joinToString(" · "),
                        coverUrl = song.pic.takeIf { it.isNotBlank() },
                        isFavorite = favoriteIds.contains(stableIdFn(song)),
                        onToggleFavorite = onToggleFavorite?.let { fav -> { fav(song) } },
                        isPlaying = isPlaying,
                        isCurrentSong = currentPlayingSongId == stableIdFn(song),
                        showLoading = song.id == loadingSongId,
                        loadingLabel = loadingStep,
                        onClick = { onPlaySong(song) }
                    )
                }
                // ⚡ 底部状态行：加载中 / 没有更多 / 加载失败（在已有结果时显示）
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val footerModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                    when {
                        state.isLoadingMore -> {
                            Column(
                                modifier = footerModifier,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "加载更多…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.isEnd -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "—— 没有更多了 ——",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.error != null -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ⚡ 在线搜索子分类切换：歌曲 / 歌手 / 歌单（歌手仅网易云提供）
@Composable
private fun OnlineSearchSubTabs(
    selectedTab: Int,
    showArtistTab: Boolean,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OnlineSubTabChip(
            selected = selectedTab == 0,
            label = "歌曲",
            icon = Icons.Rounded.MusicNote,
            onClick = { onTabSelected(0) }
        )
        if (showArtistTab) {
            OnlineSubTabChip(
                selected = selectedTab == 1,
                label = "歌手",
                icon = Icons.Rounded.Person,
                onClick = { onTabSelected(1) }
            )
        }
        OnlineSubTabChip(
            selected = selectedTab == 2,
            label = "歌单",
            icon = Icons.Rounded.LibraryMusic,
            onClick = { onTabSelected(2) }
        )
    }
}

@Composable
private fun OnlineSubTabChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    // ⚡ 与「本地 / 在线」那一排筛选标签共用 SearchChip：形状、配色、高度、
    //    图标尺寸完全一致，只有"图标常显（歌曲/歌手/歌单）"这一点不同。
    SearchChip(
        label = label,
        selected = selected,
        onClick = onClick,
        leadingIcon = icon
    )
}

// ⚡ 网易云歌手搜索结果列表
@Composable
private fun OnlineArtistResults(
    state: LxUiState,
    isSearching: Boolean,
    searchQuery: String,
    colorScheme: androidx.compose.material3.ColorScheme,
    onLoadMore: () -> Unit = {},
    onArtistClick: (LxArtistInfo) -> Unit = {}
) {
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    when {
        isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在搜索歌手…",
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        state.artistError != null && state.artistResults.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.artistError ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        state.artistResults.isEmpty() && searchQuery.isBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "输入关键词搜索网易云歌手",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        state.artistResults.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "没有找到相关歌手",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val listState = rememberLazyGridState()
            // ⚡ 监听滚动：当滚动到接近列表底部时触发加载更多
            LaunchedEffect(listState, state.artistIsEnd, state.isLoadingMore, state.artistResults.size) {
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    val totalItems = state.artistResults.size
                    if (visibleItemsInfo.isEmpty()) return@snapshotFlow false
                    val lastVisibleIndex = visibleItemsInfo.last().index
                    lastVisibleIndex >= totalItems - 5 && !state.artistIsEnd && !state.isLoadingMore
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                columns = rememberLibraryListGridCells(),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = MiniPlayerHeight + systemBarPaddingBottom
                )
            ) {
                items(items = state.artistResults, key = { artist -> artist.id }) { artist ->
                    UnifiedOnlineArtistItem(
                        artist = artist,
                        colorScheme = colorScheme,
                        onClick = { onArtistClick(artist) }
                    )
                }
                // ⚡ 底部状态行
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val footerModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                    when {
                        state.isLoadingMore -> {
                            Column(
                                modifier = footerModifier,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "加载更多…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.artistIsEnd -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "—— 没有更多了 ——",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.artistError != null -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.artistError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ⚡ 单条歌手搜索结果项
@Composable
private fun UnifiedOnlineArtistItem(
    artist: LxArtistInfo,
    colorScheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape),
        shape = itemShape,
        color = colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (artist.picUrl.isNotBlank()) {
                    SmartImage(
                        model = artist.picUrl,
                        contentDescription = null,
                        targetSize = SmartImageListTargetSize,
                        // 搜索结果封面不落盘，避免磁盘缓存暴涨
                        useDiskCache = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (artist.alias.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist.alias,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ⚡ 在线歌单搜索结果列表（对齐歌曲/歌手列表的分页与底部状态行）
@Composable
private fun OnlinePlaylistResults(
    state: LxUiState,
    isSearching: Boolean,
    searchQuery: String,
    colorScheme: androidx.compose.material3.ColorScheme,
    onLoadMore: () -> Unit = {},
    savingPlaylistId: String? = null,
    onSavePlaylist: (LxPlaylistInfo) -> Unit = {},
    onPlaylistClick: (LxPlaylistInfo) -> Unit = {}
) {
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    when {
        isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在搜索歌单…", color = colorScheme.onSurfaceVariant)
                }
            }
        }
        state.playlistError != null && state.playlistResults.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.playlistError ?: "",
                    color = colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        state.playlistResults.isEmpty() && searchQuery.isBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("输入关键词搜索歌单", color = colorScheme.onSurfaceVariant)
            }
        }
        state.playlistResults.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("没有找到相关歌单", color = colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            val listState = rememberLazyGridState()
            LaunchedEffect(listState, state.playlistIsEnd, state.isLoadingMore, state.playlistResults.size) {
                snapshotFlow {
                    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
                    val totalItems = state.playlistResults.size
                    if (visibleItemsInfo.isEmpty()) return@snapshotFlow false
                    val lastVisibleIndex = visibleItemsInfo.last().index
                    lastVisibleIndex >= totalItems - 5 && !state.playlistIsEnd && !state.isLoadingMore
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                columns = rememberLibraryListGridCells(),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = MiniPlayerHeight + systemBarPaddingBottom
                )
            ) {
                items(items = state.playlistResults, key = { it.source + "_" + it.id }) { playlist ->
                    UnifiedOnlinePlaylistItem(
                        playlist = playlist,
                        colorScheme = colorScheme,
                        isSaving = playlist.id == savingPlaylistId,
                        onSaveClick = { onSavePlaylist(playlist) },
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val footerModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                    when {
                        state.isLoadingMore -> {
                            Column(
                                modifier = footerModifier,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "加载更多…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.playlistIsEnd -> {
                            Box(modifier = footerModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    "—— 没有更多了 ——",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.playlistError != null -> {
                            Box(modifier = footerModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    state.playlistError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ⚡ 单条歌单搜索结果项：点击进入预览（可试听），右侧提供「保存到本地」入口
@Composable
private fun UnifiedOnlinePlaylistItem(
    playlist: LxPlaylistInfo,
    colorScheme: androidx.compose.material3.ColorScheme,
    isSaving: Boolean = false,
    onSaveClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    // 复用软件已有的歌单封面组件（支持远程封面 URL / 4 宫格拼图）
    val coverPreview = remember(playlist.id, playlist.cover) {
        Playlist(
            id = playlist.id,
            name = playlist.name,
            songIds = emptyList(),
            coverImageUri = playlist.cover.ifBlank { null },
            source = "LOCAL"
        )
    }

    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .clickable(onClick = onClick),
        shape = itemShape,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = coverPreview,
                playlistSongs = emptyList(),
                size = 56.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (playlist.author.isNotBlank()) append(playlist.author)
                        if (playlist.trackCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${playlist.trackCount} 首")
                        }
                        val plays = formatPlayCount(playlist.playCount)
                        if (plays.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(plays)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // ⚡ 简介行**恒定占位**（没有简介时用一个空格撑起同样一行）：
                //    之前只有有简介的歌单才多画两行，卡片高度参差不齐（网格里一排高矮不一）。
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playlist.description.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            // 「保存到本地」：把在线歌单歌曲写入媒体库并创建本地歌单
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary
                    )
                } else {
                    IconButton(onClick = onSaveClick) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "保存到本地",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ⚡ 播放量格式化：1.2万 / 3.4亿
private fun formatPlayCount(count: Long): String {
    if (count <= 0L) return ""
    return when {
        count >= 100_000_000L -> String.format("%.1f亿", count / 100_000_000.0)
        count >= 10_000L -> String.format("%.1f万", count / 10_000.0)
        else -> count.toString()
    }
}

@Composable
private fun UnifiedOnlineSongItem(
    title: String,
    subtitle: String,
    coverUrl: String?,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    showLoading: Boolean = false,
    loadingLabel: String? = null,
    onClick: () -> Unit
) {
    // 与搜索界面「歌曲」标签下结果完全一致的显示效果（EnhancedSongListItem）
    // 封面统一为正方形（高亮时变圆形），播放中显示 EQ 动画图标，收藏走右侧按钮
    val song = remember(title, subtitle, coverUrl) {
        Song(
            id = "online",
            title = title.ifBlank { "未知歌曲" },
            artist = subtitle.ifBlank { "未知歌手" },
            artistId = -1L,
            album = "",
            albumId = -1L,
            path = "cloud://online",
            contentUriString = "cloud://online",
            albumArtUriString = coverUrl,
            duration = 0L,
            mimeType = "audio/*",
            bitrate = null,
            sampleRate = null
        )
    }
    EnhancedSongListItem(
        song = song,
        isPlaying = isPlaying,
        isCurrentSong = isCurrentSong,
        albumArtSize = 50.dp,
        showMoreOptionsButton = false,
        showFavoriteButton = false, // 搜索页不显示收藏按钮
        isFavorite = isFavorite,
        showLoading = showLoading,
        loadingLabel = loadingLabel,
        // 在线搜索结果封面不落盘，避免磁盘缓存暴涨
        useDiskCache = false,
        onMoreOptionsClick = {},
        onFavoriteClick = onToggleFavorite ?: {},
        onClick = onClick
    )
}

// ── QQ 音乐搜索结果组件 ──────────────────────────────────────────────
@Composable
private fun QQSearchResults(
    state: QQSearchUiState,
    isSearching: Boolean,
    searchQuery: String,
    onPlaySong: (com.theveloper.pixelplay.data.qq.QQSearchApi.QQSong) -> Unit,
    colorScheme: androidx.compose.material3.ColorScheme,
    stableIdFn: (com.theveloper.pixelplay.data.qq.QQSearchApi.QQSong) -> String,
    onLoadMore: () -> Unit = {},
    currentPlayingSongId: String? = null,
    isPlaying: Boolean = false
) {
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    when {
        isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在搜索酷我…",
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        state.error != null && state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        state.results.isEmpty() && searchQuery.isBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "输入关键词开始搜索酷我音乐",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "没有找到相关歌曲",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val listState = rememberLazyGridState()
            LaunchedEffect(listState, state.isEnd, state.isLoadingMore, state.results.size) {
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    val totalItems = state.results.size
                    if (visibleItemsInfo.isEmpty()) return@snapshotFlow false
                    val lastVisibleIndex = visibleItemsInfo.last().index
                    lastVisibleIndex >= totalItems - 5 && !state.isEnd && !state.isLoadingMore
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                columns = rememberLibraryListGridCells(),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = MiniPlayerHeight + systemBarPaddingBottom
                )
            ) {
                items(
                    items = state.results,
                    key = { song -> song.id }
                ) { song ->
                    UnifiedOnlineSongItem(
                        title = song.title,
                        subtitle = listOfNotNull(
                            song.singer.ifBlank { null },
                            song.album.ifBlank { null }
                        ).joinToString(" · "),
                        coverUrl = song.cover.takeIf { it.isNotBlank() },
                        isFavorite = false,
                        onToggleFavorite = null,
                        isPlaying = isPlaying,
                        isCurrentSong = currentPlayingSongId == stableIdFn(song),
                        onClick = { onPlaySong(song) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val footerModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                    when {
                        state.isLoadingMore -> {
                            Column(
                                modifier = footerModifier,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "加载更多…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.isEnd -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "—— 没有更多了 ——",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.error != null -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.error
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
private fun BilibiliSearchResults(
    state: BilibiliUiState,
    isSearching: Boolean,
    searchQuery: String,
    onPlaySong: (com.theveloper.pixelplay.data.bilibili.BilibiliSongInfo) -> Unit,
    colorScheme: androidx.compose.material3.ColorScheme,
    stableIdFn: (com.theveloper.pixelplay.data.bilibili.BilibiliSongInfo) -> String,
    onLoadMore: () -> Unit,
    currentPlayingSongId: String? = null,
    isPlaying: Boolean = false,
    /** 正在解析播放链接的视频 id（在该歌曲名称右侧显示加载提示） */
    loadingSongId: String? = null,
    /** 当前加载步骤文字（如「解析音频流…」） */
    loadingStep: String? = null
) {
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp
    when {
        isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在搜索B站…",
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        state.error != null && state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        state.results.isEmpty() && searchQuery.isBlank() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "输入关键词开始搜索B站音乐",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        state.results.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "没有找到相关视频",
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            val listState = rememberLazyGridState()
            LaunchedEffect(listState, state.isEnd, state.isLoadingMore, state.results.size) {
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    val totalItems = state.results.size
                    if (visibleItemsInfo.isEmpty()) return@snapshotFlow false
                    val lastVisibleIndex = visibleItemsInfo.last().index
                    lastVisibleIndex >= totalItems - 5 && !state.isEnd && !state.isLoadingMore
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { onLoadMore() }
            }

            LazyVerticalGrid(
                columns = rememberLibraryListGridCells(),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = MiniPlayerHeight + systemBarPaddingBottom
                )
            ) {
                items(
                    items = state.results,
                    key = { song -> song.id }
                ) { song ->
                    UnifiedOnlineSongItem(
                        title = song.name,
                        subtitle = listOfNotNull(
                            song.singer.ifBlank { null },
                            song.albumName.ifBlank { null }
                        ).joinToString(" · "),
                        coverUrl = song.pic.takeIf { it.isNotBlank() },
                        isFavorite = false,
                        onToggleFavorite = null,
                        isPlaying = isPlaying,
                        isCurrentSong = currentPlayingSongId == stableIdFn(song),
                        showLoading = song.id == loadingSongId,
                        loadingLabel = loadingStep,
                        onClick = { onPlaySong(song) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val footerModifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                    when {
                        state.isLoadingMore -> {
                            Column(
                                modifier = footerModifier,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "加载更多…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.isEnd -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "—— 没有更多了 ——",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.error != null -> {
                            Box(
                                modifier = footerModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
