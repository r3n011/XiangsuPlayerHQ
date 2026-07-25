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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840

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
    val launchTab = uiState.launchTab
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

    var showCornerRadiusOverlay by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
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
                                        text = "搜索设置",
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
    val settingsCategoryTitles = remember {
        mapOf(
            SettingsCategory.AI_INTEGRATION.id to "AI集成",
            SettingsCategory.LIBRARY.id to "音乐库",
            SettingsCategory.APPEARANCE.id to "外观",
            SettingsCategory.PLAYBACK.id to "播放",
            SettingsCategory.BEHAVIOR.id to "行为",
            SettingsCategory.BACKUP_RESTORE.id to "备份与恢复",
            SettingsCategory.DEVELOPER.id to "开发者",
            SettingsCategory.EQUALIZER.id to "均衡器",
            SettingsCategory.DEVICE_CAPABILITIES.id to "设备能力",
            SettingsCategory.ABOUT.id to "关于"
        )
    }

    // 构建设置项搜索索引（分类 + 典型设置项）
    val searchItems = remember(query, settingsCategoryTitles) {
        val normalizedQuery = query.trim().lowercase()
        val items = mutableListOf<SettingsSearchItem>()

        // 1. 分类入口
        SettingsCategory.entries.forEach { category ->
            val categoryTitle = settingsCategoryTitles[category.id] ?: category.id
            if (categoryTitle.lowercase().contains(normalizedQuery)) {
                items.add(
                    SettingsSearchItem(
                        title = categoryTitle,
                        subtitle = "设置分类",
                        categoryTitle = categoryTitle,
                        onClick = {}
                    )
                )
            }
        }

        // 2. 常见设置项关键词（简化版，覆盖核心设置）
        val keywordItems = listOf(
            "外观" to SettingsCategory.APPEARANCE,
            "主题" to SettingsCategory.APPEARANCE,
            "圆角" to SettingsCategory.APPEARANCE,
            "模糊" to SettingsCategory.APPEARANCE,
            "滚动" to SettingsCategory.APPEARANCE,
            "歌词" to SettingsCategory.PLAYBACK,
            "播放" to SettingsCategory.PLAYBACK,
            "蓝牙" to SettingsCategory.PLAYBACK,
            "耳机" to SettingsCategory.PLAYBACK,
            "均衡器" to SettingsCategory.EQUALIZER,
            "交叉淡化" to SettingsCategory.PLAYBACK,
            "HiFi" to SettingsCategory.PLAYBACK,
            "随机" to SettingsCategory.PLAYBACK,
            "文件夹" to SettingsCategory.LIBRARY,
            "艺术家" to SettingsCategory.LIBRARY,
            "专辑" to SettingsCategory.LIBRARY,
            "同步" to SettingsCategory.LIBRARY,
            "缓存" to SettingsCategory.LIBRARY,
            "备份" to SettingsCategory.BACKUP_RESTORE,
            "恢复" to SettingsCategory.BACKUP_RESTORE,
            "导出" to SettingsCategory.BACKUP_RESTORE,
            "导入" to SettingsCategory.BACKUP_RESTORE,
            "手势" to SettingsCategory.BEHAVIOR,
            "触感" to SettingsCategory.BEHAVIOR,
            "开发者" to SettingsCategory.DEVELOPER,
            "设备" to SettingsCategory.DEVICE_CAPABILITIES,
            "账号" to SettingsCategory.ABOUT,
            "关于" to SettingsCategory.ABOUT,
            "在线音源" to SettingsCategory.LIBRARY,
            "色彩" to SettingsCategory.APPEARANCE,
            "语言" to SettingsCategory.APPEARANCE,
            "导航" to SettingsCategory.APPEARANCE,
            "播放器" to SettingsCategory.PLAYBACK,
            "队列" to SettingsCategory.PLAYBACK,
            "混音" to SettingsCategory.EQUALIZER,
            "专辑封面" to SettingsCategory.LIBRARY,
            "扫描" to SettingsCategory.LIBRARY,
            "最小时长" to SettingsCategory.LIBRARY
        )

        keywordItems.forEach { (keyword, category) ->
            if (keyword.lowercase().contains(normalizedQuery) || normalizedQuery in keyword.lowercase()) {
                val categoryTitle = settingsCategoryTitles[category.id] ?: category.id
                items.add(
                    SettingsSearchItem(
                        title = keyword,
                        subtitle = "$categoryTitle 设置",
                        categoryTitle = categoryTitle,
                        onClick = {}
                    )
                )
            }
        }

        // 去重并限制结果数量
        items.distinctBy { it.title to it.subtitle }.take(20)
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
                        text = "未找到相关设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "${searchItems.size} 个结果",
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

    val settingsCategoryTitles = remember {
        mapOf(
            SettingsCategory.AI_INTEGRATION.id to "AI集成",
            SettingsCategory.LIBRARY.id to "音乐库",
            SettingsCategory.APPEARANCE.id to "外观",
            SettingsCategory.PLAYBACK.id to "播放",
            SettingsCategory.BEHAVIOR.id to "行为",
            SettingsCategory.BACKUP_RESTORE.id to "备份与恢复",
            SettingsCategory.DEVELOPER.id to "开发者",
            SettingsCategory.EQUALIZER.id to "均衡器",
            SettingsCategory.DEVICE_CAPABILITIES.id to "设备能力",
            SettingsCategory.ABOUT.id to "关于"
        )
    }

    val searchItems = remember(searchQuery, settingsCategoryTitles) {
        if (searchQuery.isBlank()) return@remember emptyList<SettingsSearchItem>()
        
        val normalizedQuery = searchQuery.trim().lowercase()
        val items = mutableListOf<SettingsSearchItem>()

        SettingsCategory.entries.forEach { category ->
            val categoryTitle = settingsCategoryTitles[category.id] ?: category.id
            if (categoryTitle.lowercase().contains(normalizedQuery)) {
                items.add(
                    SettingsSearchItem(
                        title = categoryTitle,
                        subtitle = "设置分类",
                        categoryTitle = categoryTitle,
                        onClick = {}
                    )
                )
            }
        }

        val keywordItems = listOf(
            "外观" to SettingsCategory.APPEARANCE,
            "主题" to SettingsCategory.APPEARANCE,
            "圆角" to SettingsCategory.APPEARANCE,
            "模糊" to SettingsCategory.APPEARANCE,
            "歌词" to SettingsCategory.PLAYBACK,
            "播放" to SettingsCategory.PLAYBACK,
            "蓝牙" to SettingsCategory.PLAYBACK,
            "耳机" to SettingsCategory.PLAYBACK,
            "均衡器" to SettingsCategory.EQUALIZER,
            "交叉淡化" to SettingsCategory.PLAYBACK,
            "HiFi" to SettingsCategory.PLAYBACK,
            "随机" to SettingsCategory.PLAYBACK,
            "文件夹" to SettingsCategory.LIBRARY,
            "艺术家" to SettingsCategory.LIBRARY,
            "专辑" to SettingsCategory.LIBRARY,
            "同步" to SettingsCategory.LIBRARY,
            "缓存" to SettingsCategory.LIBRARY,
            "备份" to SettingsCategory.BACKUP_RESTORE,
            "恢复" to SettingsCategory.BACKUP_RESTORE,
            "导出" to SettingsCategory.BACKUP_RESTORE,
            "导入" to SettingsCategory.BACKUP_RESTORE,
            "手势" to SettingsCategory.BEHAVIOR,
            "触感" to SettingsCategory.BEHAVIOR,
            "开发者" to SettingsCategory.DEVELOPER,
            "设备" to SettingsCategory.DEVICE_CAPABILITIES,
            "账号" to SettingsCategory.ABOUT,
            "关于" to SettingsCategory.ABOUT,
            "在线音源" to SettingsCategory.LIBRARY,
            "色彩" to SettingsCategory.APPEARANCE,
            "语言" to SettingsCategory.APPEARANCE,
            "导航" to SettingsCategory.APPEARANCE,
            "播放器" to SettingsCategory.PLAYBACK,
            "队列" to SettingsCategory.PLAYBACK,
            "混音" to SettingsCategory.EQUALIZER,
            "专辑封面" to SettingsCategory.LIBRARY,
            "扫描" to SettingsCategory.LIBRARY,
            "最小时长" to SettingsCategory.LIBRARY
        )

        keywordItems.forEach { (keyword, category) ->
            if (keyword.lowercase().contains(normalizedQuery) || normalizedQuery in keyword.lowercase()) {
                val categoryTitle = settingsCategoryTitles[category.id] ?: category.id
                items.add(
                    SettingsSearchItem(
                        title = keyword,
                        subtitle = "$categoryTitle 设置",
                        categoryTitle = categoryTitle,
                        onClick = {}
                    )
                )
            }
        }

        items.distinctBy { it.title to it.subtitle }.take(20)
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
                                text = "搜索设置",
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
                                text = "未找到相关设置",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${searchItems.size} 个结果",
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
