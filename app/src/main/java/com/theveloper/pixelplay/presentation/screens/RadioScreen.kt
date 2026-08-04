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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.radio.RadioBrowserApi
import com.theveloper.pixelplay.data.radio.RadioCountry
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.RadioMode
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import dev.chrisbanes.haze.hazeSource

/**
 * 网络广播页面（radio-browser.info）
 *
 * 提供热门电台列表与搜索功能，点击电台卡片即可通过 playUrl 播放。
 */
@Composable
fun RadioScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: RadioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var keyword by remember { mutableStateOf("") }
    var showCountrySheet by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    // 与媒体库一致的背景设计：顶部 primaryContainer 半透明色块 + 渐变背景
    val isDarkTheme = isSystemInDarkTheme()
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val gradientColors = remember(isDarkTheme, primaryContainer, onPrimaryContainer) {
        if (isDarkTheme) {
            listOf(
                primaryContainer.copy(alpha = 0.5f),
                Color.Transparent
            )
        } else {
            listOf(
                onPrimaryContainer.copy(alpha = 0.2f),
                Color.Transparent
            )
        }
    }
    val backgroundBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val headerContainerColor = primaryContainer.copy(alpha = 0.4f)
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .hazeSource(MainActivity.LocalHazeState.current)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部颜色填充区（模仿媒体库 header）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerContainerColor)
            ) {
                RadioHeader(
                    onBack = { navController.popBackStack() },
                    onSearch = {
                        if (keyword.isNotBlank()) viewModel.search(keyword)
                    }
                )

                RadioSearchBar(
                    keyword = keyword,
                    onKeywordChange = {
                        keyword = it
                        if (it.isBlank()) viewModel.loadTopStations()
                    },
                    onSearch = {
                        if (keyword.isNotBlank()) viewModel.search(keyword)
                    }
                )

                // 国家筛选：使用与媒体库一致的主标签样式（热门 + 常用国家 + 更多）
                RadioCountryTabs(
                    selectedCode = uiState.countryCode,
                    onAllClick = { viewModel.loadTopStations() },
                    onCountryClick = { code, name -> viewModel.loadCountry(code, name) },
                    onMoreClick = { showCountrySheet = true }
                )
            }

            // 电台列表：与媒体库一致的圆角容器（纯色），内部边缘套一圈遮罩，
            // 让列表看起来被容器物理遮挡
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // 垫底层：与头部同色，保证容器左上/右上圆角裁切处显示头部纯色（不露暗底）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(headerContainerColor)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(color = surfaceContainerLow)
                ) {
                    when {
                    uiState.loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }

                    uiState.error != null -> {
                        RadioEmptyState(
                            text = uiState.error ?: "",
                            modifier = Modifier.fillMaxSize(),
                            onRetry = {
                                when (uiState.mode) {
                                    RadioMode.TOP -> viewModel.loadTopStations()
                                    RadioMode.SEARCH -> viewModel.search(uiState.query)
                                    RadioMode.COUNTRY -> uiState.countryCode?.let { viewModel.loadCountry(it, uiState.query) }
                                }
                            }
                        )
                    }

                    uiState.stations.isEmpty() -> {
                        RadioEmptyState(
                            text = stringResource(R.string.radio_no_results),
                            modifier = Modifier.fillMaxSize(),
                            onRetry = { viewModel.loadTopStations() }
                        )
                    }

                    else -> {
                        // 滚动条显示时右侧留 22dp，隐藏时恢复 12dp，遮罩宽度同步变化
                        val showScrollbar = LocalShowScrollbar.current &&
                            (lazyListState.canScrollForward || lazyListState.canScrollBackward)
                        val endPadding = if (showScrollbar) 22.dp else 12.dp

                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = endPadding,
                                    top = 12.dp,
                                    bottom = MiniPlayerHeight +
                                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = uiState.stations,
                                    key = { it.stationUuid }
                                ) { station ->
                                    RadioStationCard(
                                        station = station,
                                        playerViewModel = playerViewModel,
                                        onClick = {
                                            viewModel.reportClick(station.stationUuid)
                                            playerViewModel.playUrl(
                                                url = station.streamUrl,
                                                title = station.name,
                                                artist = station.country.ifBlank { "Radio" },
                                                cover = station.favicon,
                                                songId = "radio://${station.stationUuid}"
                                            )
                                        }
                                    )
                                }
                            }

                            // 边缘遮罩：与容器同色的实心边框（左 12 / 上 12 / 右 22 / 下 12），
                            // 顶部圆角与外部容器（26dp）同步，列表划动时看起来被容器物理遮挡
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        val radius = 26.dp.toPx()
                                        val leftW = 12.dp.toPx()
                                        val topH = 12.dp.toPx()
                                        val rightW = if (showScrollbar) 22.dp.toPx() else 12.dp.toPx()
                                        val bottomH = 12.dp.toPx()

                                        val outer = RoundRect(
                                            left = 0f,
                                            top = 0f,
                                            right = size.width,
                                            bottom = size.height,
                                            topLeftCornerRadius = CornerRadius(radius),
                                            topRightCornerRadius = CornerRadius(radius),
                                            bottomRightCornerRadius = CornerRadius(0f),
                                            bottomLeftCornerRadius = CornerRadius(0f)
                                        )
                                        // 内轮廓圆角比外部容器小 2px，让边框角落更收紧
                                        val inner = RoundRect(
                                            left = leftW,
                                            top = topH,
                                            right = size.width - rightW,
                                            bottom = size.height - bottomH,
                                            topLeftCornerRadius = CornerRadius(radius - 2f),
                                            topRightCornerRadius = CornerRadius(radius - 2f),
                                            bottomRightCornerRadius = CornerRadius(0f),
                                            bottomLeftCornerRadius = CornerRadius(0f)
                                        )
                                        // 外轮廓 + 内轮廓双子路径，用奇偶填充镂空出边框
                                        val frame = Path().apply {
                                            addRoundRect(outer)
                                            addRoundRect(inner)
                                            fillType = PathFillType.EvenOdd
                                        }
                                        drawPath(path = frame, color = surfaceContainerLow)
                                    }
                            )

                            // 右侧滑动条（模仿媒体库歌曲列表的 ExpressiveScrollBar，不显示拖动预览）
                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(
                                        end = 4.dp,
                                        top = 16.dp,
                                        bottom = MiniPlayerHeight +
                                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                                    ),
                                listState = lazyListState
                            )
                        }
                    }
                }
            }
        }
    }

        if (showCountrySheet) {
            RadioCountryPickerSheet(
                countries = uiState.countries.ifEmpty { RadioBrowserApi.FALLBACK_COUNTRIES },
                selectedCode = uiState.countryCode,
                onDismiss = { showCountrySheet = false },
                onCountryClick = { code, name ->
                    showCountrySheet = false
                    viewModel.loadCountry(code, name)
                }
            )
        }
    }
}

@Composable
private fun RadioHeader(
    onBack: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Rounded.Radio,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.radio_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RadioSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    // 与搜索页面保持一致的搜索栏样式
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.cd_search_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            BasicTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (keyword.isEmpty()) {
                            Text(
                                text = stringResource(R.string.radio_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        innerTextField()
                    }
                },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            if (keyword.isNotBlank()) {
                IconButton(onClick = { onKeywordChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_clear_search_query),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 搜索触发按钮：点击立即搜索当前关键词
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.cd_search_icon),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** 常用国家快捷筛选（code 与 radio-browser.info 的 ISO 3166-1 一致） */
private val CommonRadioCountries = listOf(
    RadioCountry("CN", "中国"),
    RadioCountry("US", "美国"),
    RadioCountry("GB", "英国"),
    RadioCountry("JP", "日本"),
    RadioCountry("KR", "韩国"),
    RadioCountry("DE", "德国"),
    RadioCountry("FR", "法国"),
    RadioCountry("AU", "澳大利亚")
)

@Composable
private fun RadioCountryTabs(
    selectedCode: String?,
    onAllClick: () -> Unit,
    onCountryClick: (String, String) -> Unit,
    onMoreClick: () -> Unit
) {
    val topLabel = stringResource(R.string.radio_top)
    val moreLabel = stringResource(R.string.radio_more_countries)
    val tabs = remember(topLabel, moreLabel) {
        listOf(RadioCountry("", topLabel)) + CommonRadioCountries + RadioCountry("MORE", moreLabel)
    }
    val selectedTabIndex = remember(selectedCode, tabs) {
        val code = selectedCode ?: ""
        tabs.indexOfFirst { it.code == code }.coerceAtLeast(0)
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        edgePadding = 16.dp,
        indicator = {},
        divider = {}
    ) {
        tabs.forEachIndexed { index, country ->
            val isSelected = selectedTabIndex == index
            TabAnimation(
                index = index,
                title = country.name,
                selectedIndex = selectedTabIndex,
                onClick = {
                    when (country.code) {
                        "" -> onAllClick()
                        "MORE" -> onMoreClick()
                        else -> onCountryClick(country.code, country.name)
                    }
                }
            ) {
                Text(
                    text = country.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 国家选择弹窗：搜索 + 完整国家列表 */
@Composable
private fun RadioCountryPickerSheet(
    countries: List<RadioCountry>,
    selectedCode: String?,
    onDismiss: () -> Unit,
    onCountryClick: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(countries, query) {
        if (query.isBlank()) countries
        else countries.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.radio_country_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.radio_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.radio_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it.code }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selectedCode == country.code) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                                    }
                                )
                                .clickable { onCountryClick(country.code, country.name) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = country.code,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 电台 → 歌曲，复用媒体库收藏列表的展示与播放状态高亮 */
private fun RadioStation.toSong(): Song = Song(
    id = "radio://$stationUuid",
    title = name,
    artist = subtitle(),
    artistId = -1L,
    album = "",
    albumId = -1L,
    path = streamUrl,
    contentUriString = streamUrl,
    albumArtUriString = favicon.ifBlank { null },
    duration = 0L,
    mimeType = "audio/*",
    bitrate = bitrate.takeIf { it > 0 },
    sampleRate = null
)

@Composable
private fun RadioStationCard(
    station: RadioStation,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val song = remember(station) { station.toSong() }

    // 与媒体库歌曲标签一致的显示效果：不显示电台图标和收藏按钮
    // 未选中时用 surfaceContainerHigh 背景，在 surfaceContainerLow 容器上清晰可见
    LibraryPlaybackAwareSongItem(
        song = song,
        playerViewModel = playerViewModel,
        containerColorOverride = MaterialTheme.colorScheme.surfaceContainerHigh,
        showMoreOptionsButton = false,
        onClick = onClick
    )
}

@Composable
private fun RadioEmptyState(
    text: String,
    modifier: Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onRetry) {
                Text(text = stringResource(R.string.radio_retry))
            }
        }
    }
}
