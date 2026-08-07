package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import java.util.Locale
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.screens.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.AccountsScreen
import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.screens.HeadphonePresetScreen
import com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen
import com.theveloper.pixelplay.presentation.screens.CloudMusicSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DotDeviceSettingsScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.ExperimentalSettingsScreen

// SettingsTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    // 使用真实窗口宽度（LocalWindowInfo）而非 Configuration：
    // 部分平板在分屏/小窗/旋转时 Configuration 不更新，导致手机/平板模式不切换
    val windowWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isTablet = windowWidthDp >= 840.dp

    if (isTablet) {
        TabletSettingsScreen(
            outerNavController = navController,
            playerViewModel = playerViewModel,
            onBackClick = onNavigationIconClick,
            settingsViewModel = settingsViewModel
        )
    } else {
        PhoneSettingsScreen(
            navController = navController,
            playerViewModel = playerViewModel,
            onNavigationIconClick = onNavigationIconClick,
            settingsViewModel = settingsViewModel
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel
) {

    // Animation effects
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by
            transition.animateFloat(
                    label = "ContentAlpha",
                    transitionSpec = { tween(durationMillis = 500) }
            ) { if (it) 1f else 0f }

    val contentOffset by
            transition.animateDp(
                    label = "ContentOffset",
                    transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
            ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp 

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isSheetVisible by playerViewModel.isSheetVisible.collectAsStateWithLifecycle()
    val hasMiniPlayer = stablePlayerState.currentSong?.id != null && isSheetVisible
    val launchTab = uiState.launchTab
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

    var showCornerRadiusOverlay by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchPlaceholder = stringResource(R.string.settings_search_placeholder)

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
                1f -
                        ((topBarHeight.value - minTopBarHeightPx) /
                                        (maxTopBarHeightPx - minTopBarHeightPx))
                                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
            modifier =
                    Modifier.nestedScroll(nestedScrollConnection).fillMaxSize().graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (hasMiniPlayer) {
                        MiniPlayerHeight + NavBarContentHeight +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        MiniPlayerBottomSpacer
                    } else {
                        NavBarContentHeight +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current)
        ) {
            item {
                // 搜索框（与 SearchScreen 样式一致）
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = {},
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        text = searchPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                // 搜索结果切换显示
                AnimatedContent(
                    targetState = searchQuery.isNotBlank(),
                    label = "settings_search_transition"
                ) { isSearching ->
                    if (isSearching) {
                        SettingsSearchResults(
                            query = searchQuery,
                            navController = navController
                        )
                    } else {
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        SettingsCategoryGrid(
                            navController = navController,
                            isDark = isDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        CollapsibleCommonTopBar(
                title = stringResource(R.string.settings_top_bar_title),
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackClick = onNavigationIconClick
        )

        // Block interaction during transition
        var isTransitioning by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(com.theveloper.pixelplay.presentation.navigation.TRANSITION_DURATION.toLong())
            isTransitioning = false
        }

        if (isTransitioning) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                     awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
            )
        }
    }
}

// 搜索索引项：标题、副标题、点击跳转路由
private data class SettingsSearchItem(
    val title: String,
    val subtitle: String,
    val categoryTitle: String,
    val onClick: () -> Unit
)

/**
 * 获取设置分类标题映射（使用字符串资源支持多语言）
 */
@Composable
private fun getSettingsCategoryTitles(): Map<String, String> {
    return mapOf(
        SettingsCategory.AI_INTEGRATION.id to stringResource(R.string.settings_search_category_ai_integration),
        SettingsCategory.WEB_REMOTE.id to stringResource(R.string.settings_category_web_remote_title),
        SettingsCategory.LIBRARY.id to stringResource(R.string.settings_search_category_library),
        SettingsCategory.APPEARANCE.id to stringResource(R.string.settings_search_category_appearance),
        SettingsCategory.PLAYBACK.id to stringResource(R.string.settings_search_category_playback),
        SettingsCategory.BEHAVIOR.id to stringResource(R.string.settings_search_category_behavior),
        SettingsCategory.BACKUP_RESTORE.id to stringResource(R.string.settings_search_category_backup_restore),
        SettingsCategory.DEVELOPER.id to stringResource(R.string.settings_search_category_developer),
        SettingsCategory.EQUALIZER.id to stringResource(R.string.settings_search_category_equalizer),
        SettingsCategory.DEVICE_CAPABILITIES.id to stringResource(R.string.settings_search_category_device_capabilities),
        SettingsCategory.ABOUT.id to stringResource(R.string.settings_search_category_about)
    )
}

/**
 * 获取设置关键词列表（用于搜索匹配）
 */
@Composable
private fun getSettingsKeywordItems(): List<Pair<String, SettingsCategory>> {
    return listOf(
        stringResource(R.string.settings_search_keyword_appearance) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_theme) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_corner_radius) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_blur) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_scroll) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_lyrics) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_playback) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_bluetooth) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_headphones) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_equalizer) to SettingsCategory.EQUALIZER,
        stringResource(R.string.settings_search_keyword_crossfade) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_hifi) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_shuffle) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_folder) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_artist) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_album) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_sync) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_cache) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_backup) to SettingsCategory.BACKUP_RESTORE,
        stringResource(R.string.settings_search_keyword_restore) to SettingsCategory.BACKUP_RESTORE,
        stringResource(R.string.settings_search_keyword_export) to SettingsCategory.BACKUP_RESTORE,
        stringResource(R.string.settings_search_keyword_import) to SettingsCategory.BACKUP_RESTORE,
        stringResource(R.string.settings_search_keyword_gesture) to SettingsCategory.BEHAVIOR,
        stringResource(R.string.settings_search_keyword_haptics) to SettingsCategory.BEHAVIOR,
        stringResource(R.string.settings_search_keyword_developer) to SettingsCategory.DEVELOPER,
        stringResource(R.string.settings_search_keyword_device) to SettingsCategory.DEVICE_CAPABILITIES,
        stringResource(R.string.settings_search_keyword_account) to SettingsCategory.ABOUT,
        stringResource(R.string.settings_search_keyword_about) to SettingsCategory.ABOUT,
        stringResource(R.string.settings_search_keyword_online_source) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_color) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_language) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_navigation) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_player) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_queue) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_mixer) to SettingsCategory.EQUALIZER,
        stringResource(R.string.settings_search_keyword_album_art) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_scan) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_min_duration) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_ai) to SettingsCategory.AI_INTEGRATION,
        stringResource(R.string.settings_search_keyword_model) to SettingsCategory.AI_INTEGRATION,
        stringResource(R.string.settings_search_keyword_token) to SettingsCategory.AI_INTEGRATION,
        stringResource(R.string.settings_search_keyword_recommendation) to SettingsCategory.AI_INTEGRATION,
        stringResource(R.string.settings_search_keyword_remote) to SettingsCategory.AI_INTEGRATION,
        stringResource(R.string.settings_search_keyword_immersive_lyrics) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_auto_hide) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_replaygain) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_cast) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_usb) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_aaudio) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_car_mode) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_transcode) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_download) to SettingsCategory.BEHAVIOR,
        stringResource(R.string.settings_search_keyword_daily_mix) to SettingsCategory.DEVELOPER,
        stringResource(R.string.settings_search_keyword_stats) to SettingsCategory.DEVELOPER,
        stringResource(R.string.settings_search_keyword_maintenance) to SettingsCategory.DEVELOPER,
        stringResource(R.string.settings_search_keyword_diagnostics) to SettingsCategory.DEVELOPER,
        stringResource(R.string.settings_search_keyword_palette) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_now_playing) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_compact) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_collage) to SettingsCategory.APPEARANCE,
        stringResource(R.string.settings_search_keyword_excluded) to SettingsCategory.LIBRARY,
        stringResource(R.string.settings_search_keyword_battery) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_background) to SettingsCategory.PLAYBACK,
        stringResource(R.string.settings_search_keyword_filter) to SettingsCategory.LIBRARY
    )
}

/**
 * 真实设置项标题索引：直接引用各分类详情页中的设置项标题字符串资源，
 * 使设置搜索能覆盖到所有真实设置项（多语言自动跟随）。
 */
@Composable
private fun getSettingsItemIndex(): List<Pair<String, SettingsCategory>> = listOf(
    // AI 集成
    stringResource(R.string.setcat_ai_provider_section) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_model_selection) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_safe_token_title) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_ai_auto_trigger_section) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_ai_auto_playlist_title) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_ai_recommendation_section) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_ai_recommendation_card_title) to SettingsCategory.AI_INTEGRATION,
    stringResource(R.string.setcat_ai_recommendation_manual_title) to SettingsCategory.AI_INTEGRATION,
    // 网页遥控
    stringResource(R.string.setcat_web_remote_section) to SettingsCategory.WEB_REMOTE,
    stringResource(R.string.setcat_web_remote_enabled_title) to SettingsCategory.WEB_REMOTE,
    stringResource(R.string.setcat_web_remote_sync_title) to SettingsCategory.WEB_REMOTE,
    stringResource(R.string.setcat_web_remote_server_status) to SettingsCategory.WEB_REMOTE,
    stringResource(R.string.setcat_web_remote_server_address) to SettingsCategory.WEB_REMOTE,
    stringResource(R.string.setcat_web_remote_pin) to SettingsCategory.WEB_REMOTE,
    // 媒体库
    stringResource(R.string.setcat_library_structure) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_excluded_directories_title) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_artists_title) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_filtering) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_sync_scanning) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_auto_scan_lrc_title) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_lyrics_management) to SettingsCategory.LIBRARY,
    stringResource(R.string.setcat_reset_imported_lyrics_title) to SettingsCategory.LIBRARY,
    // 外观
    stringResource(R.string.setcat_global_theme) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_smooth_corners_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_disable_blur_all_over_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_show_scrollbar_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_now_playing) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_show_player_file_info_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_album_art_palette_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_home_collage) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_auto_rotate_patterns_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_navigation_bar) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_compact_mode_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_navbar_corner_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_lyrics_screen) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_immersive_lyrics_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_app_navigation_section) to SettingsCategory.APPEARANCE,
    // 播放
    stringResource(R.string.setcat_background_playback) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_battery_optimization_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_replaygain_section) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_replaygain_enable_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_cast) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_headphones) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_headphones_resume_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_queue_transitions) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_hifi_mode_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_usb_exclusive_mode_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_aaudio_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_persistent_shuffle_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_show_queue_history_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_show_lyrics_track_info_title) to SettingsCategory.APPEARANCE,
    stringResource(R.string.setcat_car_mode_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_transcode_cache_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_transcode_auto_cleanup_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.setcat_transcode_clear_cache_title) to SettingsCategory.PLAYBACK,
    // 播放音质（在线音源统一音质）
    stringResource(R.string.music_quality_title) to SettingsCategory.PLAYBACK,
    stringResource(R.string.music_quality_hires) to SettingsCategory.PLAYBACK,
    stringResource(R.string.music_quality_flac) to SettingsCategory.PLAYBACK,
    stringResource(R.string.music_quality_high) to SettingsCategory.PLAYBACK,
    stringResource(R.string.music_quality_standard) to SettingsCategory.PLAYBACK,
    // 行为
    stringResource(R.string.setcat_download_settings) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_download_path_title) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_folders) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_folder_back_gesture_title) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_player_gestures) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_tap_bg_closes_title) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_haptics) to SettingsCategory.BEHAVIOR,
    stringResource(R.string.setcat_haptic_feedback_title) to SettingsCategory.BEHAVIOR,
    // 备份与恢复
    stringResource(R.string.setcat_create_backup) to SettingsCategory.BACKUP_RESTORE,
    stringResource(R.string.setcat_export_backup_title) to SettingsCategory.BACKUP_RESTORE,
    stringResource(R.string.setcat_restore_backup_section) to SettingsCategory.BACKUP_RESTORE,
    stringResource(R.string.setcat_import_backup_title) to SettingsCategory.BACKUP_RESTORE,
    // 开发者
    stringResource(R.string.setcat_experiments) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_experimental_title) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_test_setup_title) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_maintenance) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_force_daily_mix_title) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_force_stats_title) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_force_palette_title) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_diagnostics) to SettingsCategory.DEVELOPER,
    stringResource(R.string.setcat_trigger_crash_title) to SettingsCategory.DEVELOPER,
    // 关于
    stringResource(R.string.setcat_application) to SettingsCategory.ABOUT,
    stringResource(R.string.setcat_about_xiangsuplayer_title) to SettingsCategory.ABOUT,
    // 均衡器 / 设备能力
    stringResource(R.string.settings_category_equalizer_title) to SettingsCategory.EQUALIZER,
    stringResource(R.string.settings_category_device_capabilities_title) to SettingsCategory.DEVICE_CAPABILITIES
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCategoryGrid(
    navController: NavController,
    isDark: Boolean
) {
    ExpressiveSettingsGroup {
        val mainCategories = SettingsCategory.entries.filter {
            it != SettingsCategory.ABOUT &&
            it != SettingsCategory.DEVICE_CAPABILITIES
        }

        val totalItems = mainCategories.size + 5 // Device + Accounts + CloudMusic + Dot + About
        fun shapeFor(index: Int) =
            when {
                totalItems == 1 -> RoundedCornerShape(24.dp)
                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(4.dp)
            }

        var itemIndex = 0

        mainCategories.forEach { category ->
            val colors = getCategoryColors(category, isDark)

            ExpressiveCategoryItem(
                category = category,
                customColors = colors,
                onClick = {
                    if (category == SettingsCategory.EQUALIZER) {
                        navController.navigateSafely(Screen.Equalizer.route)
                    } else {
                        navController.navigateSafely(Screen.SettingsCategory.createRoute(category.id))
                    }
                },
                shape = shapeFor(itemIndex)
            )
            if (itemIndex < totalItems - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
            itemIndex++
        }

        ExpressiveCategoryItem(
            category = SettingsCategory.DEVICE_CAPABILITIES,
            customColors = getCategoryColors(SettingsCategory.DEVICE_CAPABILITIES, isDark),
            onClick = { navController.navigateSafely(Screen.DeviceCapabilities.route) },
            shape = shapeFor(itemIndex)
        )
        if (itemIndex < totalItems - 1) {
            Spacer(modifier = Modifier.height(2.dp))
        }
        itemIndex++

        ExpressiveNavigationItem(
            title = stringResource(R.string.settings_accounts_row_title),
            subtitle = stringResource(R.string.settings_accounts_row_subtitle),
            icon = Icons.Rounded.AccountCircle,
            colors = getAccountsColors(isDark),
            onClick = { navController.navigateSafely(Screen.Accounts.route) },
            shape = shapeFor(itemIndex)
        )
        if (itemIndex < totalItems - 1) {
            Spacer(modifier = Modifier.height(2.dp))
        }
        itemIndex++

        ExpressiveNavigationItem(
            title = "在线音源",
            subtitle = "管理 JS 音乐源",
            icon = Icons.Rounded.Cloud,
            colors = getAccountsColors(isDark),
            onClick = { navController.navigateSafely(Screen.CloudMusicSettings.route) },
            shape = shapeFor(itemIndex)
        )
        if (itemIndex < totalItems - 1) {
            Spacer(modifier = Modifier.height(2.dp))
        }
        itemIndex++

        ExpressiveNavigationItem(
            title = "Dot 墨水屏",
            subtitle = "推送专辑封面到墨水屏设备",
            icon = Icons.Rounded.Palette,
            colors = getAccountsColors(isDark),
            onClick = { navController.navigateSafely(Screen.DotDeviceSettings.route) },
            shape = shapeFor(itemIndex)
        )
        if (itemIndex < totalItems - 1) {
            Spacer(modifier = Modifier.height(2.dp))
        }
        itemIndex++

        ExpressiveCategoryItem(
            category = SettingsCategory.ABOUT,
            customColors = getCategoryColors(SettingsCategory.ABOUT, isDark),
            onClick = { navController.navigateSafely("about") },
            shape = shapeFor(itemIndex)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSearchResults(
    query: String,
    navController: NavController
) {
    val settingsCategoryTitles = getSettingsCategoryTitles()
    val keywordItems = getSettingsKeywordItems()
    val settingItemIndex = getSettingsItemIndex()
    val categorySubtitle = stringResource(R.string.settings_search_category_subtitle)
    val categorySettingsFormat = stringResource(R.string.settings_search_category_settings_format)
    val noResultsText = stringResource(R.string.settings_search_no_results)
    val resultsCountFormat = stringResource(R.string.settings_search_results_count_format)

    // 构建设置项搜索索引（分类 + 关键词 + 真实设置项标题，统一模糊匹配并按相关度排序）
    val searchItems = remember(
        query,
        settingsCategoryTitles,
        keywordItems,
        settingItemIndex,
        categorySubtitle,
        categorySettingsFormat
    ) {
        buildSettingsSearchItems(
            rawQuery = query,
            categoryTitles = settingsCategoryTitles,
            keywordItems = keywordItems,
            settingItemIndex = settingItemIndex,
            categorySubtitle = categorySubtitle,
            categorySettingsFormat = categorySettingsFormat
        )
    }

    // 渲染搜索结果
    Column(modifier = Modifier.fillMaxWidth()) {
        if (searchItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = noResultsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = resultsCountFormat.format(searchItems.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            ExpressiveSettingsGroup {
                searchItems.forEachIndexed { index, item ->
                    val totalItems = searchItems.size
                    val shape = when {
                        totalItems == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                        index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(4.dp)
                    }

                    val category = SettingsCategory.entries.find {
                        settingsCategoryTitles[it.id] == item.categoryTitle
                    }
                    val colors = if (category != null) {
                        getCategoryColors(category, isDark)
                    } else {
                        getAccountsColors(isDark)
                    }

                    // 点击跳转到对应的分类详情页
                    val onClickAction: () -> Unit = {
                        when {
                            item.categoryTitle == "均衡器" -> navController.navigateSafely(Screen.Equalizer.route)
                            item.categoryTitle == "设备能力" -> navController.navigateSafely(Screen.DeviceCapabilities.route)
                            item.categoryTitle == "关于" -> navController.navigateSafely("about")
                            else -> {
                                val cat = SettingsCategory.entries.find {
                                    settingsCategoryTitles[it.id] == item.categoryTitle
                                }
                                if (cat != null) {
                                    navController.navigateSafely(Screen.SettingsCategory.createRoute(cat.id))
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onClickAction,
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp).fillMaxSize()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(colors.first)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.second,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (index < searchItems.size - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSettingsScreen(
    outerNavController: NavController,
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sideBarWidth = 360.dp
    val contentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        MiniPlayerHeight + 8.dp

    val detailNavController = rememberNavController()
    val currentDestination by androidx.compose.runtime.produceState<androidx.navigation.NavDestination?>(
        initialValue = detailNavController.currentDestination
    ) {
        val listener = NavController.OnDestinationChangedListener { controller, _, _ ->
            value = controller.currentDestination
        }
        detailNavController.addOnDestinationChangedListener(listener)
        awaitDispose { detailNavController.removeOnDestinationChangedListener(listener) }
    }

    val currentDetailKey = currentDestination?.let { dest ->
        val r = dest.route
        when {
            r != null && r.startsWith("settings_category/") -> r.removePrefix("settings_category/")
            r == Screen.Equalizer.route -> "equalizer"
            r == Screen.DeviceCapabilities.route -> "device_capabilities"
            r == Screen.Accounts.route -> "accounts"
            r == "about" -> "about"
            else -> SettingsCategory.LIBRARY.id
        }
    } ?: SettingsCategory.LIBRARY.id

    val startDestination = Screen.SettingsCategory.createRoute(SettingsCategory.LIBRARY.id)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusBarHeight)
    ) {
        Box(
            modifier = Modifier
                .width(sideBarWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val lazyListState = rememberLazyListState()
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    start = 12.dp,
                    end = 12.dp,
                    bottom = contentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.settings_top_bar_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                item {
                    TabletSettingsSearchAndCategories(
                        searchQuery = "",
                        isDark = isDark,
                        currentDetailKey = currentDetailKey,
                        detailNavController = detailNavController
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            NavHost(
                navController = detailNavController,
                startDestination = startDestination
            ) {
                composable(
                    route = Screen.SettingsCategory.route,
                    arguments = listOf(navArgument("categoryId") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val categoryId = backStackEntry.arguments?.getString("categoryId")
                    if (categoryId != null) {
                        SettingsCategoryScreen(
                            categoryId = categoryId,
                            navController = detailNavController,
                            playerViewModel = playerViewModel,
                            onBackClick = {},
                            showBackButton = false
                        )
                    }
                }
                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        navController = detailNavController,
                        playerViewModel = playerViewModel
                    )
                }
                composable(Screen.HeadphonePreset.route) {
                    HeadphonePresetScreen(
                        navController = detailNavController
                    )
                }
                composable(Screen.DeviceCapabilities.route) {
                    DeviceCapabilitiesScreen(
                        navController = detailNavController,
                        playerViewModel = playerViewModel
                    )
                }
                composable(Screen.Accounts.route) {
                    AccountsScreen(
                        onBackClick = {},
                        onOpenNeteaseDashboard = { outerNavController.navigateSafely(Screen.NeteaseDashboard.route) },
                        onOpenQqMusicDashboard = { outerNavController.navigateSafely(Screen.QqMusicDashboard.route) },
                        onOpenNavidromeDashboard = { outerNavController.navigateSafely(Screen.NavidromeDashboard.route) },
                        onOpenJellyfinDashboard = { outerNavController.navigateSafely(Screen.JellyfinDashboard.route) },
                        showBackButton = false
                    )
                }
                composable(Screen.CloudMusicSettings.route) {
                    CloudMusicSettingsScreen(
                        onBackClick = {}
                    )
                }
                composable(Screen.DotDeviceSettings.route) {
                    DotDeviceSettingsScreen(
                        onBackClick = {}
                    )
                }
                composable("about") {
                    AboutScreen(
                        navController = detailNavController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = {},
                        showBackButton = false
                    )
                }
                composable(Screen.PaletteStyle.route) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { detailNavController.popBackStack() }
                    )
                }
                composable(Screen.Experimental.route) {
                    ExperimentalSettingsScreen(
                        navController = detailNavController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { detailNavController.popBackStack() }
                    )
                }
                composable(Screen.ArtistSettings.route) {
                    ArtistSettingsScreen(navController = detailNavController)
                }
                composable(Screen.DelimiterConfig.route) {
                    DelimiterConfigScreen(navController = detailNavController)
                }
                composable(Screen.WordDelimiterConfig.route) {
                    WordDelimiterConfigScreen(navController = detailNavController)
                }
                composable(Screen.NavBarCrRad.route) {
                    NavBarCornerRadiusScreen(navController = detailNavController)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletCategoryItem(
    category: SettingsCategory,
    customColors: Pair<Color, Color>,
    selected: Boolean,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(customColors.first)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = customColors.second,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = customColors.second,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: Pair<Color, Color>,
    selected: Boolean,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun ExpressiveNavigationItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    customColors: Pair<Color, Color>? = null
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp) 
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            // Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(customColors?.first ?: MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
//            // Chevron or indicator
//             Box(
//                contentAlignment = Alignment.Center,
//                modifier = Modifier
//                    .size(36.dp)
//                    .clip(CircleShape)
//                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
//            ) {
//                 Icon(
//                    imageVector = Icons.Rounded.ChevronRight,
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.onSurface,
//                    modifier = Modifier.size(20.dp)
//                )
//            }
        }
    }
}

private fun getAccountsColors(isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        Color(0xFF37474F) to Color(0xFFBBD9E8)
    } else {
        Color(0xFFD6EAF5) to Color(0xFF103548)
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
    ) {
        content()
    }
}

private fun getCategoryColors(category: SettingsCategory, isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        when (category) {
            SettingsCategory.AI_INTEGRATION -> Color(0xFF5B3FA0) to Color(0xFFE8DFFF)
            SettingsCategory.WEB_REMOTE -> Color(0xFF2E7D32) to Color(0xFFC8E6C9)
            SettingsCategory.LIBRARY -> Color(0xFF004A77) to Color(0xFFC2E7FF) 
            SettingsCategory.APPEARANCE -> Color(0xFF7D5260) to Color(0xFFFFD8E4) 
            SettingsCategory.PLAYBACK -> Color(0xFF633B48) to Color(0xFFFFD8EC) 
            SettingsCategory.BEHAVIOR -> Color(0xFF3E4C63) to Color(0xFFD7E3FF)
            SettingsCategory.BACKUP_RESTORE -> Color(0xFF3B4869) to Color(0xFFD9E2FF)
            SettingsCategory.DEVELOPER -> Color(0xFF324F34) to Color(0xFFCBEFD0) 
            SettingsCategory.EQUALIZER -> Color(0xFF6E4E13) to Color(0xFFFFDEAC) 
            SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFF004D61) to Color(0xFFACEFEE)
            SettingsCategory.ABOUT -> Color(0xFF3F474D) to Color(0xFFDEE3EB) 
        }
    } else {
        when (category) {
            SettingsCategory.AI_INTEGRATION -> Color(0xFFE8DFFF) to Color(0xFF4A2E8A)
            SettingsCategory.WEB_REMOTE -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
            SettingsCategory.LIBRARY -> Color(0xFFD7E3FF) to Color(0xFF005AC1)
            SettingsCategory.APPEARANCE -> Color(0xFFFFD8E4) to Color(0xFF631835)
            SettingsCategory.PLAYBACK -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
            SettingsCategory.BEHAVIOR -> Color(0xFFD7E3FF) to Color(0xFF253347)
            SettingsCategory.BACKUP_RESTORE -> Color(0xFFD9E2FF) to Color(0xFF27304E)
            SettingsCategory.DEVELOPER -> Color(0xFFCBEFD0) to Color(0xFF042106)
            SettingsCategory.EQUALIZER -> Color(0xFFFFDEAC) to Color(0xFF281900)
            SettingsCategory.DEVICE_CAPABILITIES -> Color(0xFFACEFEE) to Color(0xFF002022)
            SettingsCategory.ABOUT -> Color(0xFFEFF1F7) to Color(0xFF44474F)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSettingsSearchAndCategories(
    searchQuery: String,
    isDark: Boolean,
    currentDetailKey: String,
    detailNavController: NavController
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    val settingsCategoryTitles = getSettingsCategoryTitles()
    val keywordItems = getSettingsKeywordItems()
    val settingItemIndex = getSettingsItemIndex()
    val categorySubtitle = stringResource(R.string.settings_search_category_subtitle)
    val categorySettingsFormat = stringResource(R.string.settings_search_category_settings_format)
    val searchPlaceholder = stringResource(R.string.settings_search_placeholder)
    val noResultsText = stringResource(R.string.settings_search_no_results)
    val resultsCountFormat = stringResource(R.string.settings_search_results_count_format)

    val searchItems = remember(
        searchQuery,
        settingsCategoryTitles,
        keywordItems,
        settingItemIndex,
        categorySubtitle,
        categorySettingsFormat
    ) {
        if (searchQuery.isBlank()) return@remember emptyList<SettingsSearchItem>()
        buildSettingsSearchItems(
            rawQuery = searchQuery,
            categoryTitles = settingsCategoryTitles,
            keywordItems = keywordItems,
            settingItemIndex = settingItemIndex,
            categorySubtitle = categorySubtitle,
            categorySettingsFormat = categorySettingsFormat
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 搜索框
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = {
                            Text(
                                text = searchPlaceholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = searchBarInputFieldColors
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    inputFieldColors = searchBarInputFieldColors
                ),
                content = {}
            )
        }

        // 搜索结果或分类列表
        AnimatedContent(
            targetState = searchQuery.isNotBlank(),
            label = "tablet_search_transition"
        ) { isSearching ->
            if (isSearching) {
                // 搜索结果
                if (searchItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = noResultsText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = resultsCountFormat.format(searchItems.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                        ExpressiveSettingsGroup {
                            searchItems.forEachIndexed { index, item ->
                                val totalItems = searchItems.size
                                val shape = when {
                                    totalItems == 1 -> RoundedCornerShape(24.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                    else -> RoundedCornerShape(4.dp)
                                }

                                val category = SettingsCategory.entries.find {
                                    settingsCategoryTitles[it.id] == item.categoryTitle
                                }
                                val colors = if (category != null) {
                                    getCategoryColors(category, isDark)
                                } else {
                                    getAccountsColors(isDark)
                                }

                                val onClickAction: () -> Unit = {
                                    when {
                                        item.categoryTitle == "均衡器" -> {
                                            detailNavController.navigate(Screen.Equalizer.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                        item.categoryTitle == "设备能力" -> {
                                            detailNavController.navigate(Screen.DeviceCapabilities.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                        item.categoryTitle == "关于" -> {
                                            detailNavController.navigate("about") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                        else -> {
                                            val cat = SettingsCategory.entries.find {
                                                settingsCategoryTitles[it.id] == item.categoryTitle
                                            }
                                            if (cat != null) {
                                                detailNavController.navigate(Screen.SettingsCategory.createRoute(cat.id)) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    onClick = onClickAction,
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(16.dp).fillMaxSize()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(colors.first)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = colors.second,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (index < searchItems.size - 1) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                // 分类列表
                val mainCategories = SettingsCategory.entries.filter {
                    it != SettingsCategory.ABOUT &&
                        it != SettingsCategory.DEVICE_CAPABILITIES
                }

                val totalItems = mainCategories.size + 5
                fun shapeFor(index: Int) =
                    when {
                        totalItems == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                        index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(4.dp)
                    }

                var itemIndex = 0

                ExpressiveSettingsGroup {
                    mainCategories.forEach { category ->
                        val colors = getCategoryColors(category, isDark)
                        val isSelected = currentDetailKey == category.id

                        TabletCategoryItem(
                            category = category,
                            customColors = colors,
                            selected = isSelected,
                            onClick = {
                                if (isSelected) return@TabletCategoryItem
                                val target = if (category == SettingsCategory.EQUALIZER) {
                                    Screen.Equalizer.route
                                } else {
                                    Screen.SettingsCategory.createRoute(category.id)
                                }
                                detailNavController.navigate(target) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            shape = shapeFor(itemIndex)
                        )
                        if (itemIndex < totalItems - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        itemIndex++
                    }

                    TabletCategoryItem(
                        category = SettingsCategory.DEVICE_CAPABILITIES,
                        customColors = getCategoryColors(SettingsCategory.DEVICE_CAPABILITIES, isDark),
                        selected = currentDetailKey == "device_capabilities",
                        onClick = {
                            if (currentDetailKey == "device_capabilities") return@TabletCategoryItem
                            detailNavController.navigate(Screen.DeviceCapabilities.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    TabletNavigationItem(
                        title = stringResource(R.string.settings_accounts_row_title),
                        subtitle = stringResource(R.string.settings_accounts_row_subtitle),
                        icon = Icons.Rounded.AccountCircle,
                        colors = getAccountsColors(isDark),
                        selected = currentDetailKey == "accounts",
                        onClick = {
                            if (currentDetailKey == "accounts") return@TabletNavigationItem
                            detailNavController.navigate(Screen.Accounts.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    TabletNavigationItem(
                        title = "在线音源",
                        subtitle = "管理 JS 音乐源",
                        icon = Icons.Rounded.Cloud,
                        colors = getAccountsColors(isDark),
                        selected = currentDetailKey == "cloud_music_settings",
                        onClick = {
                            if (currentDetailKey == "cloud_music_settings") return@TabletNavigationItem
                            detailNavController.navigate(Screen.CloudMusicSettings.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    TabletNavigationItem(
                        title = "Dot 墨水屏",
                        subtitle = "推送专辑封面到墨水屏设备",
                        icon = Icons.Rounded.Palette,
                        colors = getAccountsColors(isDark),
                        selected = currentDetailKey == "dot_device_settings",
                        onClick = {
                            if (currentDetailKey == "dot_device_settings") return@TabletNavigationItem
                            detailNavController.navigate(Screen.DotDeviceSettings.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        shape = shapeFor(itemIndex)
                    )
                    if (itemIndex < totalItems - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    itemIndex++

                    TabletCategoryItem(
                        category = SettingsCategory.ABOUT,
                        customColors = getCategoryColors(SettingsCategory.ABOUT, isDark),
                        selected = currentDetailKey == "about",
                        onClick = {
                            if (currentDetailKey == "about") return@TabletCategoryItem
                            detailNavController.navigate("about") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        shape = shapeFor(itemIndex)
                    )
                }
            }
        }
    }
}

// ─── 设置搜索模糊匹配 ─────────────────────────────────────────────────

private val settingsPinyinFormat = HanyuPinyinOutputFormat().apply {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
}

/** 规范化：小写、去除空白与标点，便于匹配。 */
private fun normalizeForSearch(text: String): String =
    text.lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+"), "")

/** Levenshtein 编辑距离（用于错字/漏字容错）。 */
private fun levenshteinDistance(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[a.length][b.length]
}

/** 字符顺序匹配：query 中的字符按顺序（可跳字）全部出现在 candidate 中。 */
private fun isCharSequenceMatch(query: String, candidate: String): Boolean {
    if (query.isEmpty()) return false
    var i = 0
    for (ch in candidate) {
        if (ch == query[i]) {
            i++
            if (i == query.length) return true
        }
    }
    return i == query.length
}

/** 汉字 → 拼音全拼（无空格、无音调），非汉字保留原字符。 */
private fun toPinyinFull(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
        if (ch.code in 0x4E00..0x9FFF) {
            val pinyin = runCatching {
                PinyinHelper.toHanyuPinyinStringArray(ch, settingsPinyinFormat)
            }.getOrNull()?.firstOrNull()
            sb.append(pinyin ?: ch)
        } else {
            sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString()
}

/** 汉字 → 拼音首字母（简拼），字母原样保留（小写）。 */
private fun toPinyinInitials(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
        if (ch.code in 0x4E00..0x9FFF) {
            val pinyin = runCatching {
                PinyinHelper.toHanyuPinyinStringArray(ch, settingsPinyinFormat)
            }.getOrNull()?.firstOrNull()
            sb.append(pinyin?.firstOrNull() ?: ch)
        } else if (ch.isLetter() || ch.isDigit()) {
            sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString()
}

/**
 * 模糊匹配评分：返回 > 0 表示命中，数值越高相关度越高。
 * 匹配层级：完全相等 > 子串（越靠前越高）> 反向包含 > 拼音全拼 > 拼音简拼 >
 * 字符顺序（跳字）> 编辑距离（错字容错）。
 */
private fun fuzzyMatchScore(rawQuery: String, rawCandidate: String): Int {
    val q = normalizeForSearch(rawQuery)
    val c = normalizeForSearch(rawCandidate)
    if (q.isEmpty() || c.isEmpty()) return 0
    if (q == c) return 1_000
    val idx = c.indexOf(q)
    if (idx >= 0) return 800 - idx.coerceAtMost(40) * 5
    if (q.contains(c)) return 620
    // 拼音匹配（输入中文转拼音、拼音全拼/简拼对候选做子串与顺序匹配）
    val qFull = toPinyinFull(rawQuery)
    val cFull = toPinyinFull(rawCandidate)
    if (qFull.isNotEmpty() && cFull.contains(qFull)) return 500
    val qInit = toPinyinInitials(rawQuery)
    val cInit = toPinyinInitials(rawCandidate)
    if (qInit.isNotEmpty() && cInit.contains(qInit)) return 450
    if (isCharSequenceMatch(qFull, cFull)) return 350
    // 编辑距离容错（仅对较短文本计算，避免长文本性能开销）
    val maxLen = maxOf(q.length, c.length)
    if (maxLen <= 12) {
        val threshold = if (maxLen <= 4) 1 else maxLen / 3
        val dist = levenshteinDistance(q, c)
        if (dist in 1..threshold) return 300 - dist * 30
    }
    return 0
}

/**
 * 构建设置搜索结果：对「分类标题 + 关键词 + 真实设置项标题」统一做模糊匹配，
 * 按评分降序排序并去重。
 */
private fun buildSettingsSearchItems(
    rawQuery: String,
    categoryTitles: Map<String, String>,
    keywordItems: List<Pair<String, SettingsCategory>>,
    settingItemIndex: List<Pair<String, SettingsCategory>>,
    categorySubtitle: String,
    categorySettingsFormat: String
): List<SettingsSearchItem> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return emptyList()

    val scored = mutableListOf<Pair<Int, SettingsSearchItem>>()

    // 1. 分类入口
    SettingsCategory.entries.forEach { category ->
        val title = categoryTitles[category.id] ?: category.id
        val score = fuzzyMatchScore(query, title)
        if (score > 0) {
            scored += score to SettingsSearchItem(
                title = title,
                subtitle = categorySubtitle,
                categoryTitle = title,
                onClick = {}
            )
        }
    }

    // 2. 预定义关键词
    keywordItems.forEach { (keyword, category) ->
        val score = fuzzyMatchScore(query, keyword)
        if (score > 0) {
            val categoryTitle = categoryTitles[category.id] ?: category.id
            scored += score to SettingsSearchItem(
                title = keyword,
                subtitle = categorySettingsFormat.format(categoryTitle),
                categoryTitle = categoryTitle,
                onClick = {}
            )
        }
    }

    // 3. 真实设置项标题
    settingItemIndex.forEach { (title, category) ->
        val score = fuzzyMatchScore(query, title)
        if (score > 0) {
            val categoryTitle = categoryTitles[category.id] ?: category.id
            scored += score to SettingsSearchItem(
                title = title,
                subtitle = categorySettingsFormat.format(categoryTitle),
                categoryTitle = categoryTitle,
                onClick = {}
            )
        }
    }

    // 稳定排序（同分保持插入顺序：分类 > 关键词 > 设置项），去重后限数
    return scored
        .sortedByDescending { it.first }
        .map { it.second }
        .distinctBy { it.title to it.subtitle }
        .take(30)
}
