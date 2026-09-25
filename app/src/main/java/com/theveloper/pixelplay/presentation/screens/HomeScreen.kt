package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.components.CarModeQuickActionsCard

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import com.theveloper.pixelplay.rememberWindowIsLandscape
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.FavoriteArtistsSection
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.HomeCardOrderSheet
import com.theveloper.pixelplay.presentation.components.HomeCardOrderEntry
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSection
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.StatsOverviewCard
import com.theveloper.pixelplay.presentation.components.AiRecommendationCard
import com.theveloper.pixelplay.presentation.components.AiMixSheet
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AiMixViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.FavoriteArtistViewModel
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardCapsule
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardViewModel
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardSetupDialog
import com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardStatusSheet
import com.theveloper.pixelplay.presentation.components.hearingguard.RestReminderDialog
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

/**
 * 九边波浪（太阳 / Cookie）形状：绕中心画 9 个交替内外半径的顶点，
 * 形成带锯齿波浪外圈的多边形容器。
 */
private class NineWaveShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.width / 2f
        val inner = outer * 0.78f
        val spikes = 9
        val total = spikes * 2
        val step = (2.0 * PI) / total
        val path = Path()
        var angle = -PI / 2
        path.moveTo(
            (cx + outer * cos(angle)).toFloat(),
            (cy + outer * sin(angle)).toFloat()
        )
        for (i in 1..total) {
            val r = if (i % 2 == 0) outer else inner
            angle += step
            path.lineTo(
                (cx + r * cos(angle)).toFloat(),
                (cy + r * sin(angle)).toFloat()
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}

private val NineWaveShapeInstance = NineWaveShape()

// Modern HomeScreen with collapsible top bar and staggered grid layout
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    neteaseViewModel: NeteaseDashboardViewModel = hiltViewModel(),
    qqMusicViewModel: QqMusicDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit,
    // 从非 Tab 页面返回主页时递增，用于通知主页回到顶部（由导航层维护）
    homeScrollToTopTrigger: Int = 0
) {
    val context = LocalContext.current
    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    // ⚡ 收藏的歌手
    val favoriteArtistViewModel: FavoriteArtistViewModel = hiltViewModel()
    val favoriteArtists by favoriteArtistViewModel.artists.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    // ⚡ 首页顶部留白高度（dp）：设置页「外观 → 主页拼贴」中可调，实时生效
    val homeTopWhitespaceDp by settingsViewModel.homeTopWhitespaceDp.collectAsStateWithLifecycle()
    val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isAiRecommendationCardEnabled by settingsViewModel.isAiRecommendationCardEnabled.collectAsStateWithLifecycle()
    val isAiRecommendationManualOnly by settingsViewModel.isAiRecommendationManualOnly.collectAsStateWithLifecycle()
    val isCarModeEnabled by remember(settingsViewModel.uiState) {
        settingsViewModel.uiState.map { it.carModeEnabled }
    }.collectAsStateWithLifecycle(initialValue = false)
    
    // ⚡ Optimization: Delay non-critical data loading
    // Load playback history immediately (needed for UI)
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    
    // Defer mix songs loading until first frame
    var shouldLoadMixData by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        shouldLoadMixData = true
    }
    
    // Only collect mix data when needed
    // ⚡ initialValue 用 StateFlow 当前值而非空列表：离开首页再返回时组合会重建，
    //    若用空列表作初始值，唱片墙会先消失一帧再出现（闪烁）。
    val dailyMixSongs by remember {
        playerViewModel.dailyMixSongs
    }.collectAsStateWithLifecycle(
        initialValue = playerViewModel.dailyMixSongs.value,
        minActiveState = androidx.lifecycle.Lifecycle.State.RESUMED
    )

    val curatedYourMixSongs by remember {
        playerViewModel.yourMixSongs
    }.collectAsStateWithLifecycle(
        initialValue = playerViewModel.yourMixSongs.value,
        minActiveState = androidx.lifecycle.Lifecycle.State.RESUMED
    )

    val homeMixPreviewSongs by remember {
        playerViewModel.homeMixPreviewSongs
    }.collectAsStateWithLifecycle(
        initialValue = playerViewModel.homeMixPreviewSongs.value,
        minActiveState = androidx.lifecycle.Lifecycle.State.RESUMED
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs) {
        when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> homeMixPreviewSongs
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }
    // 记录是否已成功加载过 mix：从其他页面返回时数据会短暂为空，
    // 借助它避免每次返回主页都重新触发"加载中"占位，造成强制刷新的观感。
    var hasMixLoadedBefore by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(yourMixSongs.isEmpty()) {
        if (yourMixSongs.isNotEmpty()) {
            hasMixLoadedBefore = true
        }
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty() && !hasMixLoadedBefore) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed && !hasMixLoadedBefore
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    // Keep the visible Home snapshot stable and only refresh it once the screen is off-screen.
    var recentlyPlayedSongs by rememberSaveable { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"

    // 2) Observar sólo el currentSong (o null) para saber si mostrar padding
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)

    // 3) Observe shuffle state for sync
    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val bottomGradientHeight = if (navBarStyle == NavBarStyle.FLOATING) 0.dp
        else resolveMainScreenBottomGradientHeight(navBarCompactMode)

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var showNeteaseLoginRequiredDialog by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    var showHearingGuardSetup by remember { mutableStateOf(false) }
    var showHearingGuardStatusSheet by remember { mutableStateOf(false) }
    var hasShownSetupHint by rememberSaveable { mutableStateOf(false) }
    var showHearingGuardRestReminder by remember { mutableStateOf(false) }
    var showAiMixSheet by remember { mutableStateOf(false) }
    var showHomeCardOrderSheet by remember { mutableStateOf(false) }
    val aiMixViewModel: AiMixViewModel = hiltViewModel()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val betaSheetState = rememberModalBottomSheetState()

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()

    // 首页内容卡片顺序（默认顺序；用户自定义顺序里缺失的卡片按默认顺序追加尾部）
    val homeCardOrder by settingsViewModel.homeCardOrder.collectAsStateWithLifecycle()
    val defaultHomeCardOrder = listOf(
        "ai_recommendation", "daily_mix", "favorite_artists", "recently_played", "stats"
    )
    val homeCardsInOrder = remember(homeCardOrder) {
        if (homeCardOrder.isEmpty()) {
            defaultHomeCardOrder
        } else {
            defaultHomeCardOrder.sortedBy { id ->
                homeCardOrder.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
            }
        }
    }

    // 编辑弹窗的卡片条目（当前顺序 + 显示名称）
    val homeCardOrderEntries = remember(homeCardsInOrder) {
        homeCardsInOrder.mapNotNull { cardId ->
            val titleRes = when (cardId) {
                "ai_recommendation" -> R.string.home_card_ai_recommendation
                "daily_mix" -> R.string.presentation_batch_g_daily_mix_heading
                "favorite_artists" -> R.string.home_favorite_artists_title
                "recently_played" -> R.string.presentation_batch_g_recently_played_title
                "stats" -> R.string.presentation_batch_g_stats_overview_title
                else -> null
            }
            titleRes?.let { HomeCardOrderEntry(cardId, context.getString(it)) }
        }
    }

    // 听力保护
    val hearingGuardViewModel: HearingGuardViewModel = hiltViewModel()
    val hearingGuardState by hearingGuardViewModel.state.collectAsStateWithLifecycle()

    // 跟踪播放状态，通知听力保护管理器
    val isPlaying by remember(playerViewModel) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(isPlaying) {
        hearingGuardViewModel.onPlaybackStateChanged(isPlaying)
    }

    // 听力保护休息提醒触发时，自动暂停播放
    LaunchedEffect(hearingGuardState.restReminderTriggered) {
        if (hearingGuardState.restReminderTriggered) {
            showHearingGuardRestReminder = true
            // 暂停播放（通过 playPause toggle，此时正在播放所以会暂停）
            if (isPlaying) playerViewModel.playPause()
        }
    }

    // 主页滚动状态用 rememberSaveable 保存：Tab 切换（主页离开组合再回来）时恢复上次
    // 的滚动位置，避免整页回到顶部造成"重载闪一下"的观感。
    // 从详情页等非 Tab 页面返回主页时，由 homeScrollToTopTrigger 通知强制回到顶部。
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } }
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    // 从非 Tab 页面（详情页/专辑/设置等）返回主页时，导航层会递增该值，通知主页回到顶部。
    LaunchedEffect(homeScrollToTopTrigger) {
        if (homeScrollToTopTrigger > 0) {
            listState.scrollToItem(0)
        }
    }

    // Drawer state for sidebar
    // 与全屏播放器一致的可靠横屏判断：监听 View 全局布局（旋转/分屏时 View 尺寸必然变化）
    val isLandscape = rememberWindowIsLandscape()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val shouldShowCleanInstallDisclaimer =
        settingsUiState.beta05CleanInstallDisclaimerDismissed == false &&
            !cleanInstallDisclaimerDismissedThisSession

    // Close sidebar when player opens on tablet (landscape) or when player expands
    LaunchedEffect(currentSong, isLandscape) {
        if (isLandscape && currentSong != null && drawerState.isOpen) {
            drawerState.close()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
    Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .hazeSource(MainActivity.LocalHazeState.current),
                contentPadding = PaddingValues(
                    start = paddingValuesParent.calculateStartPadding(layoutDirection),
                    // ⚡ 首页顶部留白：innerPadding（顶栏+状态栏）+ 用户可调的额外留白高度
                    top = innerPadding.calculateTopPadding() + homeTopWhitespaceDp.dp,
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + (if (isLandscape) 12.dp else 24.dp) + bottomPadding,
                    end = paddingValuesParent.calculateEndPadding(layoutDirection)
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (yourMixSongs.isEmpty()) {
                    item(
                        key = "your_mix_placeholder",
                        contentType = "your_mix_placeholder"
                    ) {
                        if (!isCarModeEnabled && shouldShowYourMixLoadingPlaceholder) {
                            YourMixLoadingPlaceholder()
                        } else if (!isCarModeEnabled && !hasMixLoadedBefore) {
                            YourMixEmptyPlaceholder(
                                onRefresh = {
                                    homePlaceholderRefreshGeneration++
                                    hasMixLoadedBefore = false
                                    settingsViewModel.refreshLibrary()
                                    playerViewModel.forceUpdateDailyMix()
                                }
                            )
                        } else if (!isCarModeEnabled) {
                            // 已加载过 mix（如从其他页面返回时数据短暂为空）：
                            // 保持与 YourMixHeader 相同的高度，避免列表跳动与占位闪烁。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(256.dp)
                            )
                        }
                    }
                } else if (!isCarModeEnabled) {
                    item(
                        key = "your_mix_header",
                        contentType = "your_mix_header"
                    ) {
                        YourMixHeader(
                            song = yourMixSong,
                            isShuffleEnabled = isShuffleEnabled,
                            onPlayShuffled = {
                                if (usesFallbackHomeMix) {
                                    playerViewModel.shuffleAllSongs(queueName = "Your Mix")
                                } else {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = yourMixSongs,
                                        queueName = "Your Mix",
                                        startAtZero = true,
                                    )
                                }
                            },
                            onAiMixClick = { showAiMixSheet = true }
                        )
                    }
                }

                if (isCarModeEnabled) {
                    item(
                        key = "car_mode_quick_actions",
                        contentType = "car_mode_quick_actions"
                    ) {
                        CarModeQuickActionsCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onSearchClick = { navController.navigateSafely(Screen.Search.route) },
                            onRoamingClick = {
                                if (isNeteaseLoggedIn) {
                                    playerViewModel.startRoamingMode()
                                } else {
                                    showNeteaseLoginRequiredDialog = true
                                }
                            },
                            onLibraryClick = { navController.navigateSafely(Screen.Library.route) },
                            onSettingsClick = { navController.navigateSafely(Screen.Settings.route) }
                        )
                    }
                }

                // Collage — on tablet (landscape): shown inside horizontal row below
                if (!isLandscape && yourMixSongs.isNotEmpty()) {
                    item(
                        key = "album_art_collage",
                        contentType = "album_art_collage"
                    ) {
                        val basePattern = settingsUiState.collagePattern
                        val isAutoRotate = settingsUiState.collageAutoRotate
                        val patterns = remember { CollagePattern.entries }

                        val activePattern = if (isAutoRotate) {
                            var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                            LaunchedEffect(Unit) { rotationIndex++ }
                            remember(rotationIndex) {
                                patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                            }
                        } else {
                            basePattern
                        }

                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            songs = yourMixSongs,
                            padding = 16.dp,
                            height = if (isCarModeEnabled) 250.dp else 400.dp,
                            pattern = activePattern,
                            onSongClick = { song ->
                                if (usesFallbackHomeMix) {
                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                } else {
                                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                }
                            }
                        )
                    }
                }

                if (isLandscape) {
                    item(
                        key = "horizontal_sections_row",
                        contentType = "horizontal_sections_row"
                    ) {
                        val cardWidth = if (isCarModeEnabled) 400.dp else 420.dp
                        val cardHeight = if (isCarModeEnabled) 450.dp else 500.dp
                        // 显式管理横向滚动状态：mix 数据加载后唱片墙(首卡)会插入到行首，
                        // 强制回到最左侧，避免行内状态停留在唱片墙中间位置。
                        val landscapeRowState = rememberLazyListState()
                        LaunchedEffect(yourMixSongs.isNotEmpty()) {
                            if (yourMixSongs.isNotEmpty() && landscapeRowState.firstVisibleItemIndex > 0) {
                                landscapeRowState.scrollToItem(0)
                            }
                        }
                        LazyRow(
                            state = landscapeRowState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight),
                            contentPadding = PaddingValues(horizontal = if (isCarModeEnabled) 24.dp else 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (isCarModeEnabled) 24.dp else 16.dp)
                        ) {
                            if (yourMixSongs.isNotEmpty()) {
                                item(key = "card_collage", contentType = "card") {
                                    val basePattern = settingsUiState.collagePattern
                                    val isAutoRotate = settingsUiState.collageAutoRotate
                                    val patterns = remember { CollagePattern.entries }
                                    val activePattern = if (isAutoRotate) {
                                        var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                                        LaunchedEffect(Unit) { rotationIndex++ }
                                        remember(rotationIndex) {
                                            patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                                        }
                                    } else {
                                        basePattern
                                    }
                                    Card(
                                        modifier = Modifier.width(cardWidth).fillMaxHeight(),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        AlbumArtCollage(
                                            modifier = Modifier.fillMaxSize(),
                                            songs = yourMixSongs,
                                            padding = 8.dp,
                                            height = Dp.Unspecified,
                                            pattern = activePattern,
                                            onSongClick = { song ->
                                                if (usesFallbackHomeMix) {
                                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                                } else {
                                                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // 横向行内内容卡片按用户自定义顺序渲染（card_collage 固定首卡）
                            homeCardsInOrder.forEach { cardId ->
                                when (cardId) {
                                    "ai_recommendation" -> if (isAiRecommendationCardEnabled && !isCarModeEnabled) {
                                        item(key = "card_ai_recommendation", contentType = "card") {
                                            Card(
                                                modifier = Modifier.width(cardWidth).fillMaxHeight(),
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                AiRecommendationCard(
                                                    modifier = Modifier.fillMaxSize(),
                                                    playerViewModel = playerViewModel,
                                                    recentlyPlayedSongs = recentlyPlayedQueue,
                                                    isManualOnly = isAiRecommendationManualOnly,
                                                    onClickOpen = {
                                                        navController.navigateSafely(Screen.AiMixScreen.route)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    "daily_mix" -> if (dailyMixSongs.isNotEmpty()) {
                                        item(key = "card_daily_mix", contentType = "card") {
                                            DailyMixSection(
                                                modifier = Modifier.width(cardWidth).fillMaxHeight(),
                                                songs = dailyMixSongs,
                                                onClickOpen = {
                                                    navController.navigateSafely(Screen.DailyMixScreen.route)
                                                },
                                                onNavigateToAlbum = { song ->
                                                    navController.navigateSafelyReplacing(
                                                        route = Screen.AlbumDetail.createRoute(song.albumId),
                                                        patternToPop = Screen.AlbumDetail.route
                                                    )
                                                },
                                                onNavigateToArtist = { song ->
                                                    navController.navigateSafelyReplacing(
                                                        route = Screen.ArtistDetail.createRoute(song.artistId),
                                                        patternToPop = Screen.ArtistDetail.route
                                                    )
                                                },
                                                onNavigateToGenre = { song ->
                                                    song.genre?.let {
                                                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                                    }
                                                },
                                                onNavigateToNeteaseArtistHomepage = { neteaseArtistId ->
                                                    navController.navigateSafelyReplacing(
                                                        route = Screen.ArtistHomepage.createRoute(neteaseArtistId),
                                                        patternToPop = Screen.ArtistHomepage.route
                                                    )
                                                },
                                                playerViewModel = playerViewModel
                                            )
                                        }
                                    }

                                    "favorite_artists" -> if (favoriteArtists.isNotEmpty()) {
                                        item(key = "card_favorite_artists", contentType = "card") {
                                            Card(
                                                modifier = Modifier.width(cardWidth).fillMaxHeight(),
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                FavoriteArtistsSection(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(top = 16.dp),
                                                    artists = favoriteArtists,
                                                    onArtistClick = { artist ->
                                                        navController.navigateSafely(
                                                            Screen.ArtistHomepage.createRoute(artist.id)
                                                        )
                                                    },
                                                    isTabletMode = true
                                                )
                                            }
                                        }
                                    }

                                    "recently_played" -> if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                                        item(key = "card_recently_played", contentType = "card") {
                                            Box(
                                                modifier = Modifier
                                                    .width(cardWidth)
                                                    .fillMaxHeight()
                                            ) {
                                                RecentlyPlayedSection(
                                                    songs = recentlyPlayedSongs,
                                                    onSongClick = { song ->
                                                        if (recentlyPlayedQueue.isNotEmpty()) {
                                                            playerViewModel.playSongs(
                                                                songsToPlay = recentlyPlayedQueue,
                                                                startSong = song,
                                                                queueName = "Recently Played"
                                                            )
                                                        }
                                                    },
                                                    onOpenAllClick = {
                                                        navController.navigateSafely(Screen.RecentlyPlayed.route)
                                                    },
                                                    themeStateHolder = playerViewModel.themeStateHolder,
                                                    currentSongId = currentSong?.id,
                                                    contentPadding = PaddingValues(start = 8.dp, end = 24.dp),
                                                    isTabletMode = true
                                                )
                                            }
                                        }
                                    }

                                    "stats" -> if (homeStatsOverview != null && !isCarModeEnabled) {
                                        item(key = "card_stats", contentType = "card") {
                                            StatsOverviewCard(
                                                modifier = Modifier.width(cardWidth).fillMaxHeight(),
                                                summary = homeStatsOverview,
                                                onClick = { navController.navigateSafely(Screen.Stats.route) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 内容卡片按用户自定义顺序渲染（homeCardsInOrder）
                    homeCardsInOrder.forEach { cardId ->
                        when (cardId) {
                            // AI Recommendation Card - disabled in car mode for performance
                            "ai_recommendation" -> if (isAiRecommendationCardEnabled && !isCarModeEnabled) {
                                item(
                                    key = "ai_recommendation_card",
                                    contentType = "ai_recommendation_card"
                                ) {
                                    AiRecommendationCard(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        playerViewModel = playerViewModel,
                                        recentlyPlayedSongs = recentlyPlayedQueue,
                                        isManualOnly = isAiRecommendationManualOnly,
                                        onClickOpen = {
                                                navController.navigateSafely(Screen.AiMixScreen.route)
                                            }
                                        )
                                }
                            }

                            // Daily Mix
                            "daily_mix" -> if (dailyMixSongs.isNotEmpty()) {
                                item(
                                    key = "daily_mix_section",
                                    contentType = "daily_mix_section"
                                ) {
                                    DailyMixSection(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        songs = dailyMixSongs,
                                        onClickOpen = {
                                            navController.navigateSafely(Screen.DailyMixScreen.route)
                                        },
                                        onNavigateToAlbum = { song ->
                                            navController.navigateSafelyReplacing(
                                                route = Screen.AlbumDetail.createRoute(song.albumId),
                                                patternToPop = Screen.AlbumDetail.route
                                            )
                                        },
                                        onNavigateToArtist = { song ->
                                            navController.navigateSafelyReplacing(
                                                route = Screen.ArtistDetail.createRoute(song.artistId),
                                                patternToPop = Screen.ArtistDetail.route
                                            )
                                        },
                                        onNavigateToGenre = { song ->
                                            song.genre?.let {
                                                navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                            }
                                        },
                                        onNavigateToNeteaseArtistHomepage = { neteaseArtistId ->
                                            navController.navigateSafelyReplacing(
                                                route = Screen.ArtistHomepage.createRoute(neteaseArtistId),
                                                patternToPop = Screen.ArtistHomepage.route
                                            )
                                        },
                                        playerViewModel = playerViewModel
                                    )
                                }
                            }

                            // ⚡ 收藏的歌手卡片（平板：自动换行上下滑动；手机：横向滑动不变）
                            "favorite_artists" -> if (favoriteArtists.isNotEmpty()) {
                                item(
                                    key = "favorite_artists_section",
                                    contentType = "favorite_artists_section"
                                ) {
                                    FavoriteArtistsSection(
                                        artists = favoriteArtists,
                                        onArtistClick = { artist ->
                                            navController.navigateSafely(
                                                Screen.ArtistHomepage.createRoute(artist.id)
                                            )
                                        },
                                        isTabletMode = LocalConfiguration.current.screenWidthDp >= 600
                                    )
                                }
                            }

                            "recently_played" -> if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                                item(
                                    key = "recently_played_section",
                                    contentType = "recently_played_section"
                                ) {
                                    RecentlyPlayedSection(
                                        songs = recentlyPlayedSongs,
                                        onSongClick = { song ->
                                            if (recentlyPlayedQueue.isNotEmpty()) {
                                                playerViewModel.playSongs(
                                                    songsToPlay = recentlyPlayedQueue,
                                                    startSong = song,
                                                    queueName = "Recently Played"
                                                )
                                            }
                                        },
                                        onOpenAllClick = {
                                            navController.navigateSafely(Screen.RecentlyPlayed.route)
                                        },
                                        themeStateHolder = playerViewModel.themeStateHolder,
                                        currentSongId = currentSong?.id,
                                        contentPadding = PaddingValues(start = 8.dp, end = 24.dp)
                                    )
                                }
                            }

                            // Stats card - disabled in car mode for performance
                            "stats" -> if (homeStatsOverview != null && !isCarModeEnabled) {
                                item(
                                    key = "listening_stats_preview",
                                    contentType = "listening_stats_preview"
                                ) {
                                    StatsOverviewCard(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        summary = homeStatsOverview,
                                        onClick = { navController.navigateSafely(Screen.Stats.route) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacer to fill gap between content and mini player
                item(key = "bottom_spacer", contentType = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(bottomPadding))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }

        // ⚡ 顶部栏改为悬浮覆盖（不再占用固定 64dp 高度）：顶部留白完全由
        //    「首页顶部留白」设置控制，设为 0 时内容紧贴状态栏下方，不再有默认大留白。
        //    必须置于外层 Box 内部：Modifier.align 依赖 BoxScope 接收者。
        if (!isLandscape) {
            val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()
            val useNewTopBar by playerViewModel.useNewTopBar.collectAsStateWithLifecycle()
            HomeGradientTopBar(
                onTelegramClick = {
                    showStreamingProviderSheet = true
                },
                isScrolled = isScrolledPastThreshold.value,
                disableBlurAllOver = disableBlurAllOver,
                useNewTopBar = useNewTopBar,
                hearingGuardState = hearingGuardState,
                onHearingGuardClick = {
                    if (!hearingGuardState.isConfigured) hasShownSetupHint = true
                    showHearingGuardStatusSheet = true
                },
                onEditHomeOrder = { showHomeCardOrderSheet = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            // 平板横向：像素卫士胶囊浮动在首页右上角（与手机模式一致），替代原左侧导航栏底部盾牌
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = androidx.compose.foundation.layout.WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding(),
                        end = 16.dp
                    )
                    .zIndex(10f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑首页卡片顺序按钮（位于像素卫士胶囊左侧）
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { showHomeCardOrderSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.home_edit_card_order)
                    )
                }
                HearingGuardCapsule(
                    state = hearingGuardState,
                    onClick = {
                        if (!hearingGuardState.isConfigured) hasShownSetupHint = true
                        showHearingGuardStatusSheet = true
                    }
                )
            }
        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState,
            //contentWindowInsets = { WindowInsets.statusBars.only(WindowInsets.statusBars) }
        ) {
            BetaInfoBottomSheet()
        }
    }
    if (showHomeCardOrderSheet) {
        HomeCardOrderSheet(
            entries = homeCardOrderEntries,
            onReorder = { newOrder ->
                settingsViewModel.setHomeCardOrder(newOrder)
            },
            onReset = {
                settingsViewModel.setHomeCardOrder(emptyList())
            },
            onDismiss = { showHomeCardOrderSheet = false }
        )
    }
    if (showStreamingProviderSheet) {
        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isQqMusicLoggedIn by qqMusicViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            isNeteaseLoggedIn = isNeteaseLoggedIn,
            onNavigateToNeteaseDashboard = {
                navController.navigateSafely(Screen.NeteaseDashboard.route)
            },
            isQqMusicLoggedIn = isQqMusicLoggedIn,
            onNavigateToQqMusicDashboard = {
                navController.navigateSafely(Screen.QqMusicDashboard.route)
            },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            }
        )
    }
    if (showNeteaseLoginRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showNeteaseLoginRequiredDialog = false },
            title = { Text("需要登录网易云") },
            text = { Text("漫游模式需要登录网易云账号后才能使用，是否前往登录？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNeteaseLoginRequiredDialog = false
                        navController.navigateSafely(Screen.NeteaseDashboard.route)
                    }
                ) {
                    Text("去登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNeteaseLoginRequiredDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }

    // 听力保护状态卡片（从下往上弹出）
    if (showHearingGuardStatusSheet) {
        HearingGuardStatusSheet(
            state = hearingGuardState,
            showSetupHint = hasShownSetupHint && !hearingGuardState.isConfigured,
            onSettingsClick = {
                showHearingGuardStatusSheet = false
                showHearingGuardSetup = true
            },
            onDismissRequest = { showHearingGuardStatusSheet = false }
        )
    }

    // AI Mix 歌单生成卡片（从下往上弹出）：每次打开都重置为全新的输入态
    if (showAiMixSheet) {
        LaunchedEffect(Unit) { aiMixViewModel.reset() }
        AiMixSheet(
            aiMixViewModel = aiMixViewModel,
            playerViewModel = playerViewModel,
            onDismissRequest = { showAiMixSheet = false }
        )
    }

    // 听力保护设置弹窗
    if (showHearingGuardSetup) {
        HearingGuardSetupDialog(
            currentConfig = hearingGuardState.config,
            isEnabled = hearingGuardState.enabled,
            onEnabledChange = { hearingGuardViewModel.setEnabled(it) },
            onConfirm = { config ->
                hearingGuardViewModel.setConfig(config)
                showHearingGuardSetup = false
            },
            onDisable = {
                hearingGuardViewModel.clearConfig()
                showHearingGuardSetup = false
            },
            onDismiss = { showHearingGuardSetup = false }
        )
    }

    // 听力保护休息提醒弹窗
    if (showHearingGuardRestReminder && hearingGuardState.restReminderTriggered) {
        RestReminderDialog(
            state = hearingGuardState,
            onRestConfirm = {
                hearingGuardViewModel.onRestConfirmed()
                showHearingGuardRestReminder = false
            },
            onContinueListening = {
                hearingGuardViewModel.onContinueListening()
                showHearingGuardRestReminder = false
                // 恢复播放（通过 playPause toggle，此时已暂停所以会播放）
                if (!isPlaying) playerViewModel.playPause()
            },
            onDismiss = {
                hearingGuardViewModel.onRestConfirmed()
                showHearingGuardRestReminder = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YourMixLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(128.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun YourMixEmptyPlaceholder(
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 28.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 28.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 28.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 28.dp,
                    smoothnessAsPercentBL = 60,
                ),
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_empty_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_empty_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FilledTonalButton(
                onClick = onRefresh,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 22.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 22.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 22.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 22.dp,
                    smoothnessAsPercentBL = 60,
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    isShuffleEnabled: Boolean = false,
    onPlayShuffled: () -> Unit,
    onAiMixClick: () -> Unit = {}
) {
    val buttonCorners = 68.dp
    val colors = MaterialTheme.colorScheme

    val titleStyle = rememberYourMixTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) {
            // Your Mix Title
            Text(
                text = stringResource(R.string.home_your_mix_title),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            // Artist/Song subtitle
            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        // Play Button - color changes based on shuffle state
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp)
        ) {
            LargeExtendedFloatingActionButton(
                onClick = onPlayShuffled,
                containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
                contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = buttonCorners,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = buttonCorners,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = buttonCorners,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = buttonCorners,
                    smoothnessAsPercentBL = 60,
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_shuffle_24),
                    contentDescription = stringResource(R.string.cd_shuffle_play),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}


// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, title),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist, style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp), // similar al tamaño del ícono
                    color = colors.primary,
                    isPlaying = isPlaying  // o conectalo a tu estado real de reproducción
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the stablePlayerState once
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    // Derive isThisSongPlaying using remember
    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id
    }

    // Call the presentational composable
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 64.sp,
            lineHeight = 62.sp
        )
    }
}
