package com.theveloper.pixelplay.presentation.screens
import androidx.compose.foundation.layout.BoxWithConstraints
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import com.theveloper.pixelplay.presentation.components.FeaturedCarouselSection
import com.theveloper.pixelplay.data.preferences.HOME_TOP_STYLE_FEATURED
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.components.CarModeQuickActionsCard

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Cloud
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.theveloper.pixelplay.presentation.components.RoamingModeSheet
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

/** 主页精选轮播最多展示的歌曲数 */
private const val FEATURED_CAROUSEL_MAX = 12

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
    listenTogetherViewModel: com.theveloper.pixelplay.presentation.netease.dashboard.ListenTogetherViewModel = hiltViewModel(),
    qqMusicViewModel: QqMusicDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    messagesViewModel: com.theveloper.pixelplay.presentation.netease.chat.MessagesViewModel = hiltViewModel(),
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
    // ⚡ 酷狗登录状态（Hilt 单例 StateFlow）：发现卡片的私人FM入口、云端串流卡片都要用
    val kugouRepository = remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            KugouStatusEntryPoint::class.java
        ).kugouRepository()
    }
    val isKugouLoggedIn by kugouRepository.isLoggedInFlow.collectAsStateWithLifecycle()
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
    var showTogetherSheet by remember { mutableStateOf(false) }
    val togetherState by listenTogetherViewModel.state.collectAsStateWithLifecycle()
    var showNeteaseLoginRequiredDialog by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    var showHearingGuardSetup by remember { mutableStateOf(false) }
    var showHearingGuardStatusSheet by remember { mutableStateOf(false) }
    var hasShownSetupHint by rememberSaveable { mutableStateOf(false) }
    var showHearingGuardRestReminder by remember { mutableStateOf(false) }
    var showAiMixSheet by remember { mutableStateOf(false) }
    // ⚡ 网易云漫游模式选择（熟悉 / 探索）：点「发现」卡片的漫游入口先弹这个
    var showRoamingModeSheet by remember { mutableStateOf(false) }
    val roamingMode by playerViewModel.roamingMode.collectAsStateWithLifecycle()
    var showHomeCardOrderSheet by remember { mutableStateOf(false) }
    var showMessagesLoginDialog by remember { mutableStateOf(false) }
    var showFriendPickerSheet by remember { mutableStateOf(false) }
    val unreadMessages by messagesViewModel.unreadTotal.collectAsStateWithLifecycle()
    // 进入主页 / 从聊天页返回主页时刷新未读数（不做后台轮询）
    val currentNavRoute by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentNavRoute?.destination?.route, isNeteaseLoggedIn) {
        if (isNeteaseLoggedIn && currentNavRoute?.destination?.route == Screen.Home.route) {
            messagesViewModel.refreshConversations()
        }
    }
    val aiMixViewModel: AiMixViewModel = hiltViewModel()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val betaSheetState = rememberModalBottomSheetState()

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()

    // 首页内容卡片顺序（默认顺序；用户自定义顺序里缺失的卡片按默认顺序追加尾部）
    val homeCardOrder by settingsViewModel.homeCardOrder.collectAsStateWithLifecycle()
    val defaultHomeCardOrder = listOf(
        "discover", "ai_recommendation", "daily_mix", "favorite_artists", "recently_played", "stats"
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
                "discover" -> R.string.nav_bar_discover
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

    // ⚡ 卡片显隐：排序弹窗里可对每张卡片开关；隐藏集合里的卡片不渲染
    val homeCardHiddenCards by settingsViewModel.homeCardHiddenCards.collectAsStateWithLifecycle()
    val visibleHomeCardsInOrder = remember(homeCardsInOrder, homeCardHiddenCards) {
        homeCardsInOrder.filter { it !in homeCardHiddenCards }
    }

    // ⚡ 发现入口（原底部导航栏中间按钮的功能整体移到主页）：
    //   勾选 1 个目标 → 点击直达；勾选多个 → 弹出选择卡片；全不勾 → 不显示按钮
    val discoverShowRoaming by settingsViewModel.uiState
        .map { it.discoverShowRoaming }.distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = true)
    val discoverShowRadio by settingsViewModel.uiState
        .map { it.discoverShowRadio }.distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = true)
    val discoverShowAi by settingsViewModel.uiState
        .map { it.discoverShowAi }.distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = true)
    // ⚡ 发现入口做成主页卡片（HomeDiscoverCard）：卡片内直接是漫游/电台/AI 三个入口，
    //    不再需要"单目标直达/多目标弹选择"的转发逻辑与顶部按钮
    // ⚡ 发现卡片里还有"心动模式/相似歌曲/听歌识曲"三个固定入口，
    //    所以它不再由旧的三个开关决定显隐（要隐藏走「自定义首页」的卡片显隐）
    val neteaseLoginToast = stringResource(R.string.netease_login_required_toast)
    val kugouLoginToast = stringResource(R.string.kugou_login_required_toast)
    var showSongRecognitionSheet by remember { mutableStateOf(false) }

    // ⚡ 首次打开提示：还没导入 JS 音源 → 引导去「设置 → 在线音源」导入
    //    （只提示一次；已经有音源或提示过就不再打扰）
    var showLxSourcePrompt by remember { mutableStateOf(false) }
    val lxSourcePromptDismissed = settingsUiState.lxSourcePromptDismissed
    LaunchedEffect(lxSourcePromptDismissed) {
        if (lxSourcePromptDismissed) return@LaunchedEffect
        val hasJs = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                LxFileStoreEntryPoint::class.java,
            ).lxFileStore().hasAnyJs()
        }.getOrDefault(true)
        showLxSourcePrompt = !hasJs
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
    // ⚡ 「竖屏强制平板布局」开启时同样按平板/横屏那套内容排版渲染（左侧导航栏由 MainActivity 负责）。
    val forceTabletLayout = MainActivity.LocalForceTabletLayout.current
    val isLandscape = rememberWindowIsLandscape() || forceTabletLayout
    // ⚡ 平板横屏（≥600dp）：对齐 Rhythm 平板的「整宽精选 + 左右两列竖向卡片」布局，
    //    替代横向滚动卡片行；手机横屏仍保留原有横向行。
    //    强制平板布局时不再要求 ≥600dp（竖屏手机/折叠屏宽度可能不到 600dp）。
    val isWideTabletLayout =
        forceTabletLayout || (isLandscape && LocalConfiguration.current.screenWidthDp >= 600)

    /** 卡片当前是否具备渲染条件（数据/开关），双列分组与单列共用。 */
    fun isHomeCardVisibleNow(cardId: String): Boolean = when (cardId) {
        "discover" -> !isCarModeEnabled
        "ai_recommendation" -> isAiRecommendationCardEnabled && !isCarModeEnabled
        "daily_mix" -> dailyMixSongs.isNotEmpty()
        "favorite_artists" -> favoriteArtists.isNotEmpty()
        "recently_played" -> recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow
        "stats" -> homeStatsOverview != null && !isCarModeEnabled
        else -> false
    }

    // 主页顶部样式（拼贴墙 / 精选轮播）：设置 → 外观 → 主页拼贴 → 主页顶部样式
    val homeTopStyle = settingsUiState.homeTopStyle

    val renderableHomeCards = visibleHomeCardsInOrder.filter { isHomeCardVisibleNow(it) }

    /**
     * 单张卡片的渲染体（单列 / 平板双列共用；卡片自带左右 16dp 内边距）。
     *
     * [cardModifier]：平板双列传入等宽修饰符（`fillMaxWidth`，高度由各卡片内容决定，
     *   对齐 Rhythm 的平板双列）；单列传默认空值，布局与原来完全相同。
     * [useTabletCardLayout]：平板双列下按平板卡片布局（与手机横屏横向行一致）。
     */
    @Composable
    fun HomeCardContent(
        cardId: String,
        cardModifier: Modifier = Modifier,
        useTabletCardLayout: Boolean = false,
    ) {
        when (cardId) {
            "discover" -> HomeDiscoverCard(
                modifier = cardModifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                showRoaming = discoverShowRoaming,
                showRadio = discoverShowRadio,
                showAi = discoverShowAi,
                onRoamingClick = {
                    if (isNeteaseLoggedIn) {
                        showRoamingModeSheet = true
                    } else {
                        showNeteaseLoginRequiredDialog = true
                    }
                },
                onRadioClick = { navController.navigateSafely(Screen.Radio.route) },
                onAiClick = { navController.navigateSafely(Screen.AiAssistant.route) },
                // ⚡ 心动模式 / 相似歌曲都要网易云登录：未登录直接去账户设置（与一起听一致）
                onHeartModeClick = {
                    if (isNeteaseLoggedIn) playerViewModel.startHeartMode()
                    else {
                        playerViewModel.sendToast(neteaseLoginToast)
                        navController.navigateSafely(Screen.Accounts.route)
                    }
                },
                onSimilarSongsClick = {
                    if (isNeteaseLoggedIn) playerViewModel.startSimilarSongsMode()
                    else {
                        playerViewModel.sendToast(neteaseLoginToast)
                        navController.navigateSafely(Screen.Accounts.route)
                    }
                },
                onRecognitionClick = { showSongRecognitionSheet = true },
                // ⚡ 私人FM 需要酷狗登录：未登录和网易云那几个入口一样，先提示再去账户设置登录
                onKugouFmClick = {
                    // ⚡ 先按本地凭证自愈一次：凭证还在就直接开 FM，不因为状态错位误提示登录
                    kugouRepository.restoreSession()
                    if (kugouRepository.isLoggedIn) {
                        playerViewModel.startKugouFm()
                    } else {
                        playerViewModel.sendToast(kugouLoginToast)
                        navController.navigateSafely(Screen.Accounts.route)
                    }
                },
                // ⚡ 听书匿名可用，直接进书架
                onAudiobookClick = { navController.navigateSafely(Screen.Audiobook.route) },
                // ⚡ 一起听需要登录网易云（与播放器内入口、云端串流卡片一致）
                onListenTogetherClick = {
                    if (isNeteaseLoggedIn) {
                        showTogetherSheet = true
                    } else {
                        playerViewModel.sendToast(neteaseLoginToast)
                        navController.navigateSafely(Screen.Accounts.route)
                    }
                }
            )

            "ai_recommendation" -> AiRecommendationCard(
                modifier = cardModifier.padding(horizontal = 16.dp),
                playerViewModel = playerViewModel,
                recentlyPlayedSongs = recentlyPlayedQueue,
                isManualOnly = isAiRecommendationManualOnly,
                onClickOpen = {
                    navController.navigateSafely(Screen.AiMixScreen.route)
                }
            )

            "daily_mix" -> DailyMixSection(
                modifier = cardModifier.padding(horizontal = 16.dp),
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

            "favorite_artists" -> FavoriteArtistsSection(
                artists = favoriteArtists,
                onArtistClick = { artist ->
                    navController.navigateSafely(
                        Screen.ArtistHomepage.createRoute(artist.id)
                    )
                },
                // 平板双列下补左右内边距，与其它卡片一致；单列保持原有整宽布局
                modifier = if (useTabletCardLayout) cardModifier.padding(horizontal = 16.dp) else cardModifier,
                isTabletMode = useTabletCardLayout || LocalConfiguration.current.screenWidthDp >= 600
            )

            "recently_played" -> RecentlyPlayedSection(
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
                isTabletMode = useTabletCardLayout,
                modifier = if (useTabletCardLayout) cardModifier.padding(horizontal = 16.dp) else cardModifier
            )

            "stats" -> StatsOverviewCard(
                modifier = cardModifier.padding(horizontal = 16.dp),
                summary = homeStatsOverview ?: return,
                onClick = { navController.navigateSafely(Screen.Stats.route) }
            )
        }
    }

    /**
     * 唱片墙卡片（Card 包裹的拼贴墙）：手机横屏横向行与平板双列共用。
     * 竖屏的整宽大图形态不走这里（保持原样）。
     */
    @Composable
    fun HomeCollageCard(modifier: Modifier = Modifier) {
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
            modifier = modifier,
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

    /**
     * 主页顶部精选轮播（逐行移植 Rhythm 的 `ModernFeaturedSection`，见 [FeaturedCarouselSection]）：
     * 手机是全宽整屏大图（一次翻一页），平板/横屏卡片槽是多浏览布局（两侧露出圆角缩略图），
     * 两种形态各自 4.5s 自动轮播。
     */
    @Composable
    fun HomeFeaturedCarousel(modifier: Modifier = Modifier, tabletStyle: Boolean = false) {
        val featured = remember(yourMixSongs) { yourMixSongs.take(FEATURED_CAROUSEL_MAX) }
        if (featured.isEmpty()) return

        FeaturedCarouselSection(
            songs = featured,
            onSongClick = { song ->
                if (usesFallbackHomeMix) {
                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                } else {
                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                }
            },
            onPlayClick = { song ->
                if (usesFallbackHomeMix) {
                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                } else {
                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                }
            },
            modifier = modifier,
            tabletStyle = tabletStyle,
        )
    }

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
                            onRadioClick = { navController.navigateSafely(Screen.Radio.route) },
                            onLibraryClick = { navController.navigateSafely(Screen.Library.route) },
                            onSettingsClick = { navController.navigateSafely(Screen.Settings.route) }
                        )
                    }
                }

                // 顶部区域：拼贴墙（默认）或精选轮播（设置可切换），竖屏整宽展示
                if (!isLandscape && yourMixSongs.isNotEmpty()) {
                    item(
                        key = "home_top_showcase",
                        contentType = "home_top_showcase"
                    ) {
                        if (homeTopStyle == HOME_TOP_STYLE_FEATURED) {
                            HomeFeaturedCarousel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCarModeEnabled) 250.dp else 360.dp)
                            )
                        } else {
                            // 原拼贴墙（保持原样）
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
                }

                if (isWideTabletLayout) {
                    // ⚡ 平板横屏：对齐 Rhythm 的平板布局 —— 卡片不再横向滚动，
                    //    而是按奇偶分到左右两列、每列内竖向堆叠。
                    //    「精选轮播」是横向多浏览的宽卡片：整宽独占一行（对齐 Rhythm 的平板形态），
                    //    半列宽只露得出一张卡、还常比旁边卡片高出一截；拼贴图案仍作为第一张
                    //    卡片参与双列排列（不是整宽置顶的大图）。
                    val featuredOwnRow =
                        homeTopStyle == HOME_TOP_STYLE_FEATURED && yourMixSongs.isNotEmpty()
                    if (featuredOwnRow) {
                        item(
                            key = "tablet_featured_row",
                            contentType = "tablet_featured_row"
                        ) {
                            HomeFeaturedCarousel(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCarModeEnabled) 250.dp else 360.dp)
                                    .padding(horizontal = 16.dp),
                                tabletStyle = true,
                            )
                        }
                    }
                    item(
                        key = "tablet_two_column_cards",
                        contentType = "tablet_two_column_cards"
                    ) {
                        val tabletCards = buildList {
                            if (yourMixSongs.isNotEmpty() && !featuredOwnRow) add("card_collage")
                            addAll(renderableHomeCards)
                        }
                        val leftCards = tabletCards.filterIndexed { index, _ -> index % 2 == 0 }
                        val rightCards = tabletCards.filterIndexed { index, _ -> index % 2 == 1 }

                        // ⚡ 平板双列只统一**宽度**（两列等宽），高度各卡片按内容自适应
                        //    （对齐 Rhythm 的平板双列：列内卡片高度不再强行拉齐成同一高度）
                        val cardSizeModifier = Modifier.fillMaxWidth()
                        // 唱片墙内部是 fillMaxSize，必须由外层给高度（与竖屏一致）；
                        // 左右内边距与其它卡片保持一致（原来它是整列宽，比别的卡片宽出一截，
                        // 视觉上就会显得其它卡片偏窄）
                        val collageHeight = if (isCarModeEnabled) 250.dp else 360.dp

                        @Composable
                        fun TabletCard(cardId: String) {
                            if (cardId == "card_collage") {
                                HomeCollageCard(
                                    modifier = cardSizeModifier
                                        .height(collageHeight)
                                        .padding(horizontal = 16.dp)
                                )
                            } else {
                                HomeCardContent(
                                    cardId = cardId,
                                    cardModifier = cardSizeModifier,
                                    useTabletCardLayout = true
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                leftCards.forEach { cardId ->
                                    key("tablet_card_$cardId") { TabletCard(cardId) }
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                rightCards.forEach { cardId ->
                                    key("tablet_card_$cardId") { TabletCard(cardId) }
                                }
                            }
                        }
                    }
                } else if (isLandscape) {
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
                                    if (homeTopStyle == HOME_TOP_STYLE_FEATURED) {
                                        HomeFeaturedCarousel(
                                            modifier = Modifier
                                                .width(cardWidth)
                                                .fillMaxHeight(),
                                            tabletStyle = true,
                                        )
                                    } else {
                                        HomeCollageCard(
                                            modifier = Modifier
                                                .width(cardWidth)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }

                            // 横向行内内容卡片按用户自定义顺序渲染（card_collage 固定首卡）；隐藏的卡片不渲染
                            visibleHomeCardsInOrder.forEach { cardId ->
                                when (cardId) {
                                    // ⚡ 发现卡片（漫游/电台/AI 三入口）
                                    "discover" -> if (!isCarModeEnabled) {
                                        item(key = "card_discover", contentType = "card") {
                                            HomeDiscoverCard(
                                                modifier = Modifier
                                                    .width(cardWidth)
                                                    .fillMaxHeight(),
                                                showRoaming = discoverShowRoaming,
                                                showRadio = discoverShowRadio,
                                                showAi = discoverShowAi,
                                                onRoamingClick = {
                                                    if (isNeteaseLoggedIn) {
                                                        showRoamingModeSheet = true
                                                    } else {
                                                        showNeteaseLoginRequiredDialog = true
                                                    }
                                                },
                                                onRadioClick = { navController.navigateSafely(Screen.Radio.route) },
                                                onAiClick = { navController.navigateSafely(Screen.AiAssistant.route) },
                                                onHeartModeClick = {
                                                    if (isNeteaseLoggedIn) playerViewModel.startHeartMode()
                                                    else {
                                                        playerViewModel.sendToast(neteaseLoginToast)
                                                        navController.navigateSafely(Screen.Accounts.route)
                                                    }
                                                },
                                                onSimilarSongsClick = {
                                                    if (isNeteaseLoggedIn) playerViewModel.startSimilarSongsMode()
                                                    else {
                                                        playerViewModel.sendToast(neteaseLoginToast)
                                                        navController.navigateSafely(Screen.Accounts.route)
                                                    }
                                                },
                                                onRecognitionClick = { showSongRecognitionSheet = true },
                                                onKugouFmClick = { playerViewModel.startKugouFm() },
                                                onAudiobookClick = { navController.navigateSafely(Screen.Audiobook.route) },
                                                onListenTogetherClick = {
                                                    if (isNeteaseLoggedIn) {
                                                        showTogetherSheet = true
                                                    } else {
                                                        playerViewModel.sendToast(neteaseLoginToast)
                                                        navController.navigateSafely(Screen.Accounts.route)
                                                    }
                                                }
                                            )
                                        }
                                    }

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
                    // 内容卡片按用户自定义顺序渲染（隐藏/无数据的卡片不渲染）
                    renderableHomeCards.forEach { cardId ->
                        item(key = "home_card_$cardId", contentType = "home_card") {
                            HomeCardContent(cardId)
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
                onMessagesClick = {
                    if (isNeteaseLoggedIn) {
                        navController.navigateSafely(Screen.Messages.route)
                    } else {
                        showMessagesLoginDialog = true
                    }
                },
                unreadCount = unreadMessages,
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
                // ⚡ 云端串流入口：横屏/平板此前漏掉了这个按钮（只在竖屏顶栏里有）
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { showStreamingProviderSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = stringResource(R.string.presentation_batch_g_topbar_cd_telegram)
                    )
                }
            }
            // 平板横向：消息入口浮动在左上角
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = androidx.compose.foundation.layout.WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding(),
                        start = 16.dp
                    )
                    .zIndex(10f)
            ) {
                BadgedBox(
                    badge = {
                        if (unreadMessages > 0) {
                            Badge {
                                Text(if (unreadMessages > 99) "99+" else unreadMessages.toString())
                            }
                        }
                    }
                ) {
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            if (isNeteaseLoggedIn) {
                                navController.navigateSafely(Screen.Messages.route)
                            } else {
                                showMessagesLoginDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = stringResource(R.string.chat_cd_messages)
                        )
                    }
                }
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
            hiddenCards = homeCardHiddenCards,
            onReorder = { newOrder ->
                settingsViewModel.setHomeCardOrder(newOrder)
            },
            onSaveHidden = { hidden ->
                settingsViewModel.setHomeCardHiddenCards(hidden)
            },
            onReset = {
                settingsViewModel.setHomeCardOrder(emptyList())
                settingsViewModel.setHomeCardHiddenCards(emptySet())
            },
            onDismiss = { showHomeCardOrderSheet = false }
        )
    }

    if (showStreamingProviderSheet) {
        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val neteaseLoginRequiredToast = stringResource(R.string.netease_login_required_toast)
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
            },
            isKugouLoggedIn = isKugouLoggedIn,
            onOpenKugouLogin = {
                // ⚡ 与账号页同款自愈：本地凭证还在就直接进酷狗账号面板，
                //   避免凭证存储偶发不可用导致「内存状态与磁盘凭证错位」时误弹登录页。
                kugouRepository.restoreSession()
                if (kugouRepository.isLoggedIn) {
                    navController.navigateSafely(Screen.KugouDashboard.route)
                } else {
                    context.startActivity(
                        android.content.Intent(
                            context,
                            com.theveloper.pixelplay.presentation.kugou.KugouLoginActivity::class.java
                        )
                    )
                }
            },
            onOpenKugouDashboard = {
                navController.navigateSafely(Screen.KugouDashboard.route)
            },
            isListenTogetherActive = togetherState.active,
            listenTogetherSubtitle = togetherState.room?.let { room ->
                "Room #${room.id} · ${room.users.size}人"
            },
            onOpenListenTogether = {
                // ⚡ 未登录网易云 → 直接去「设置 → 账户」登录（一起听必须登录才能用）
                if (isNeteaseLoggedIn) {
                    showTogetherSheet = true
                } else {
                    playerViewModel.sendToast(neteaseLoginRequiredToast)
                    navController.navigateSafely(Screen.Accounts.route)
                }
            }
        )
    }
    if (showSongRecognitionSheet) {
        val recognitionState by playerViewModel.songRecognitionState.collectAsStateWithLifecycle()
        com.theveloper.pixelplay.presentation.components.SongRecognitionSheet(
            state = recognitionState,
            onStartRecognition = { playerViewModel.startSongRecognition() },
            onDismiss = {
                showSongRecognitionSheet = false
                playerViewModel.resetSongRecognition()
            },
            onPlaySong = { song ->
                val songs = (recognitionState as? com.theveloper.pixelplay.presentation.viewmodel.SongRecognitionState.Success)
                    ?.songs.orEmpty()
                playerViewModel.playRecognizedSongs(songs, song)
                showSongRecognitionSheet = false
            }
        )
    }
    if (showTogetherSheet) {
        com.theveloper.pixelplay.presentation.netease.dashboard.ListenTogetherSheet(
            state = togetherState,
            playlists = listenTogetherViewModel.playlists.collectAsStateWithLifecycle().value,
            inviteTextProvider = { listenTogetherViewModel.inviteText() },
            onDismiss = { showTogetherSheet = false },
            onCreateRoom = { listenTogetherViewModel.createRoom(it) },
            onStartRoaming = { listenTogetherViewModel.startRoamingRoom() },
            onJoin = { text -> listenTogetherViewModel.join(text) },
            onLeave = {
                listenTogetherViewModel.leave()
                showTogetherSheet = false
            },
            onInviteFriend = { showFriendPickerSheet = true },
            resolving = listenTogetherViewModel.resolvingInvite.collectAsStateWithLifecycle().value
        )
    }
    if (showFriendPickerSheet) {
        com.theveloper.pixelplay.presentation.netease.chat.FriendPickerSheet(
            inviteTextProvider = { listenTogetherViewModel.inviteText() },
            onDismiss = { showFriendPickerSheet = false }
        )
    }
    if (showMessagesLoginDialog) {
        PixelAlertDialog(
            onDismissRequest = { showMessagesLoginDialog = false },
            title = { Text("需要登录网易云") },
            text = { Text("私信功能需要登录网易云账号后才能使用，是否前往设置中的账户设置？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMessagesLoginDialog = false
                        navController.navigateSafely(Screen.Accounts.route)
                    }
                ) {
                    Text("前往账户设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMessagesLoginDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (showNeteaseLoginRequiredDialog) {
        PixelAlertDialog(
            onDismissRequest = { showNeteaseLoginRequiredDialog = false },
            title = { Text("需要登录网易云") },
            text = { Text("漫游模式需要登录网易云账号后才能使用，是否前往登录？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNeteaseLoginRequiredDialog = false
                        // 去「设置 → 账户」登录（原来跳到网易云面板，位置不对）
                        navController.navigateSafely(Screen.Accounts.route)
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
    if (showLxSourcePrompt) {
        val dismissPrompt: () -> Unit = {
            showLxSourcePrompt = false
            settingsViewModel.setLxSourcePromptDismissed(true)
        }
        PixelAlertDialog(
            onDismissRequest = dismissPrompt,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            title = { Text("还没有导入 JS 音源") },
            text = {
                Text("在线搜索、在线播放和音源市场都需要一个 JS 音源脚本。要不要现在去「设置 → 在线音源」导入一个？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissPrompt()
                        navController.navigateSafely(Screen.CloudMusicSettings.route)
                    }
                ) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = dismissPrompt) { Text("以后再说") }
            },
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

    // ⚡ 漫游模式选择（熟悉 / 探索）：选中即开始漫游并记住选择
    if (showRoamingModeSheet) {
        RoamingModeSheet(
            currentMode = roamingMode,
            onPick = { mode ->
                showRoamingModeSheet = false
                playerViewModel.startRoamingMode(mode)
            },
            onDismissRequest = { showRoamingModeSheet = false }
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


/** 发现卡片内单个入口的展示数据。 */
private data class DiscoverEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
    /** 平台标记：品牌 logo（网易云 / 酷狗），统一裁成圆形显示在右上角 */
    val badgeLogoRes: Int? = null
)

/** 卡片高度达到该值时，三个入口改为纵向铺满（手机横屏横向行给的固定高度）。 */
private val DiscoverFillHeightThreshold = 240.dp

/**
 * 主页「发现」卡片：漫游 / 电台 / AI 三个功能入口。
 *
 * 原底部导航栏中间「发现」按钮的功能整体搬到这里（对齐 Rhythm 把 Discover
 * 作为主页卡片流一员的做法）：卡片可直接调用三个功能，不再需要顶部按钮。
 * 卡片本身参与「自定义首页」的排序与显隐（cardId = "discover"）。
 *
 * ⚡ 自适应两种形态：
 * - 手机竖屏（高度自适应）：标题 + 三个入口**横排**，卡片紧凑（与原来一致）；
 * - 手机横屏横向行（外面给了固定高度）：三个入口**纵向铺满**，不再挤在底部空一大块。
 *   平板双列改成高度自适应后走上面的紧凑形态。
 */
@Composable
fun HomeDiscoverCard(
    modifier: Modifier = Modifier,
    showRoaming: Boolean,
    showRadio: Boolean,
    showAi: Boolean,
    onRoamingClick: () -> Unit,
    onRadioClick: () -> Unit,
    onAiClick: () -> Unit,
    // ⚡ 新增三个入口（心动模式 / 相似歌曲 / 听歌识曲），固定展示
    onHeartModeClick: () -> Unit = {},
    onSimilarSongsClick: () -> Unit = {},
    onRecognitionClick: () -> Unit = {},
    // ⚡ 酷狗私人FM（移植自 md3Music）
    onKugouFmClick: () -> Unit = {},
    // ⚡ 听书（酷狗长音频）/ 一起听（网易云）
    onAudiobookClick: () -> Unit = {},
    onListenTogetherClick: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    // ⚡ 配色语言统一（此前 primary/secondary/tertiary 是随手分配的，九宫格里看着乱）：
    //    网易云系 = primary、酷狗系 = tertiary、本地功能 = secondary；
    //    顺序也按这三组排（网易云 → 酷狗 → 本地），不再交错。
    val neteaseContainer = scheme.primaryContainer
    val neteaseOnContainer = scheme.onPrimaryContainer
    val kugouContainer = scheme.tertiaryContainer
    val kugouOnContainer = scheme.onTertiaryContainer
    val localContainer = scheme.secondaryContainer
    val localOnContainer = scheme.onSecondaryContainer
    val entries = buildList {
        // ── 网易云系 ──
        if (showRoaming) add(
            DiscoverEntry(
                icon = Icons.Rounded.PlayArrow,
                labelRes = R.string.setcat_center_nav_roaming,
                containerColor = neteaseContainer,
                contentColor = neteaseOnContainer,
                onClick = onRoamingClick,
                // ⚡ 漫游是网易云的功能，右上角挂网易云 logo
                badgeLogoRes = R.drawable.netease_cloud_music_logo_icon_206716__1_
            )
        )
        // ⚡ 网易云心动模式：以当前歌曲为种子，"永远接着听"
        add(
            DiscoverEntry(
                icon = Icons.Rounded.Favorite,
                labelRes = R.string.home_discover_heart_mode,
                containerColor = neteaseContainer,
                contentColor = neteaseOnContainer,
                onClick = onHeartModeClick,
                badgeLogoRes = R.drawable.netease_cloud_music_logo_icon_206716__1_
            )
        )
        // ⚡ 相似歌曲：以当前歌曲为种子播一串相似歌（网易云接口）
        add(
            DiscoverEntry(
                icon = Icons.Rounded.LibraryMusic,
                labelRes = R.string.home_discover_similar_songs,
                containerColor = neteaseContainer,
                contentColor = neteaseOnContainer,
                onClick = onSimilarSongsClick,
                badgeLogoRes = R.drawable.netease_cloud_music_logo_icon_206716__1_
            )
        )
        // ⚡ 一起听：点开就用当前播放队列开房（需登录网易云，未登录提示并跳账户）
        add(
            DiscoverEntry(
                icon = Icons.Rounded.Groups,
                labelRes = R.string.home_discover_listen_together,
                containerColor = neteaseContainer,
                contentColor = neteaseOnContainer,
                onClick = onListenTogetherClick,
                badgeLogoRes = R.drawable.netease_cloud_music_logo_icon_206716__1_
            )
        )
        // ── 酷狗系 ──
        // ⚡ 酷狗私人FM：匿名可用的「私人推荐」无限流（移植自 md3Music）
        add(
            DiscoverEntry(
                icon = Icons.Rounded.Podcasts,
                labelRes = R.string.home_discover_kugou_fm,
                containerColor = kugouContainer,
                contentColor = kugouOnContainer,
                onClick = onKugouFmClick,
                // 酷狗官方标是圆角方形，展示时统一裁成圆形
                badgeLogoRes = R.drawable.ic_kugou
            )
        )
        // ⚡ 听书：酷狗长音频书架（推荐分区 + 免费书库 + 搜索），匿名可用
        add(
            DiscoverEntry(
                icon = Icons.Rounded.AutoStories,
                labelRes = R.string.home_discover_audiobook,
                containerColor = kugouContainer,
                contentColor = kugouOnContainer,
                onClick = onAudiobookClick,
                badgeLogoRes = R.drawable.ic_kugou
            )
        )
        // ── 本地功能（不依赖账号）──
        if (showRadio) add(
            DiscoverEntry(
                icon = Icons.Rounded.Radio,
                labelRes = R.string.setcat_center_nav_radio,
                containerColor = localContainer,
                contentColor = localOnContainer,
                onClick = onRadioClick
            )
        )
        if (showAi) add(
            DiscoverEntry(
                icon = Icons.Rounded.AutoAwesome,
                labelRes = R.string.discover_ai_title,
                containerColor = localContainer,
                contentColor = localOnContainer,
                onClick = onAiClick
            )
        )
        // ⚡ 听歌识曲：录一段外放声音，匹配出歌曲
        add(
            DiscoverEntry(
                icon = Icons.Rounded.Mic,
                labelRes = R.string.home_discover_recognition,
                containerColor = localContainer,
                contentColor = localOnContainer,
                onClick = onRecognitionClick
            )
        )
    }

    Card(
        modifier = modifier,
        // 与其它首页卡片统一：平滑大圆角 + surfaceContainerHigh + 无阴影
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // 有界且够高 → 纵向铺满；手机竖屏是无限高约束（列表项），保持横排紧凑布局
            val fillHeight =
                constraints.hasBoundedHeight && maxHeight >= DiscoverFillHeightThreshold
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 卡片头部：与「统计概览」等卡片同款 —— 圆形主色图标胶囊 + 标题/副标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.nav_bar_discover),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.home_discover_card_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ⚡ 纵向逐个铺满只在入口不多时好看：入口变多（现在 9 个）会把每行压到
                //    40dp 以下，图标/文字全被裁掉 → 超过 6 个改用下面的 3 列网格换行。
                if (fillHeight && entries.size <= 6) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        entries.forEach { entry ->
                            DiscoverActionTile(
                                entry = entry,
                                // 整宽且较高 → 横向条目（图标胶囊 + 文字 + 徽标），和设置项/列表行一致
                                horizontal = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                } else {
                    // 固定高度下把入口按钮推到卡片底部；高度自适应时该 Spacer 为 0，布局不变
                    Spacer(Modifier.weight(1f))
                    // ⚡ 每行 3 个等分：入口变多（9 个）时自动换行，不会挤成一排细条
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.chunked(3).forEach { rowEntries ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowEntries.forEach { entry ->
                                    DiscoverActionTile(
                                        entry = entry,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 补齐空位，避免最后一行只有一个入口时被拉满整行
                                repeat(3 - rowEntries.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 发现卡片内的功能入口：中性底 + 品牌色图标胶囊 + 文字（与全 app 的卡片风格一致）。 */
@Composable
private fun DiscoverActionTile(
    entry: DiscoverEntry,
    modifier: Modifier = Modifier,
    /** true = 整宽横向条目（平板/横屏纵向铺满时）；false = 网格里的等分小方块 */
    horizontal: Boolean = false,
) {
    val tileShape = AbsoluteSmoothCornerShape(20.dp, 60)
    Surface(
        modifier = modifier
            .clip(tileShape)
            .clickable(onClick = entry.onClick),
        shape = tileShape,
        // ⚡ 底色不再整块染成 primary/secondary/tertiary：只有图标胶囊保留品牌色，
        //    卡片整体是中性 surface 色调 —— 之前六块彩色瓷砖和软件其它卡片完全不搭。
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DiscoverEntryIcon(entry)
                Text(
                    text = stringResource(entry.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                entry.badgeLogoRes?.let { logoRes -> DiscoverEntryBadge(logoRes) }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    // 高度不受限（横排紧凑形态）时 fillMaxSize 对高度不生效 → 仍是内容高度
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DiscoverEntryIcon(entry)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                // 平台 logo 浮在右上角（不占一行，保证所有入口等高等宽）
                entry.badgeLogoRes?.let { logoRes ->
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        DiscoverEntryBadge(logoRes)
                    }
                }
            }
        }
    }
}

/** 入口图标：36dp 平滑圆角胶囊 + 品牌色（整块卡片不再染色）。 */
@Composable
private fun DiscoverEntryIcon(entry: DiscoverEntry) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(AbsoluteSmoothCornerShape(12.dp, 60))
            .background(entry.containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = entry.contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 平台标记：品牌 logo（网易云 / 酷狗）。
 * ⚡ 统一裁成圆形（酷狗官方标是圆角方形，裁圆后与网易云的圆形标一致），
 *    并加一圈细描边，浅色 logo 落在浅色卡片上也能看清边界。
 */
@Composable
private fun DiscoverEntryBadge(logoRes: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}


/** 取 Hilt 单例 [com.theveloper.pixelplay.data.lx.LxFileStore]（首次打开检查有没有 JS 音源用）。 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface LxFileStoreEntryPoint {
    fun lxFileStore(): com.theveloper.pixelplay.data.lx.LxFileStore
}

/** 取 Hilt 单例 [com.theveloper.pixelplay.data.kugou.KugouRepository]（云端串流卡片显示酷狗登录状态）。 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface KugouStatusEntryPoint {
    fun kugouRepository(): com.theveloper.pixelplay.data.kugou.KugouRepository
}
