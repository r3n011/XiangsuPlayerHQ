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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.radio.RadioCountry
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.RadioMode
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
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
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentTitle = stablePlayerState.currentSong?.title

    var keyword by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    val bgColors = listOf(
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.surface
    )
    val backgroundBrush = remember { Brush.verticalGradient(colors = bgColors, endY = 1200f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .hazeSource(MainActivity.LocalHazeState.current)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            RadioModeChips(
                currentMode = uiState.mode,
                query = uiState.query,
                onTopClick = { viewModel.loadTopStations() }
            )

            // 国家列表筛选
            RadioCountryChips(
                countries = uiState.countries,
                selectedCode = uiState.countryCode,
                onAllClick = { viewModel.loadTopStations() },
                onCountryClick = { code, name -> viewModel.loadCountry(code, name) }
            )

            when {
                uiState.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                uiState.error != null -> {
                    RadioEmptyState(
                        text = uiState.error ?: "",
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
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
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        onRetry = { viewModel.loadTopStations() }
                    )
                }

                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = MiniPlayerHeight +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = uiState.stations,
                            key = { it.stationUuid }
                        ) { station ->
                            RadioStationCard(
                                station = station,
                                isPlaying = station.name == currentTitle,
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
                }
            }
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
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = {
            Text(text = stringResource(R.string.radio_search_hint))
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (keyword.isNotBlank()) {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.radio_search),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch() }
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        )
    )
}

@Composable
private fun RadioModeChips(
    currentMode: RadioMode,
    query: String,
    onTopClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentMode == RadioMode.TOP,
            onClick = onTopClick,
            label = { Text(text = stringResource(R.string.radio_top)) },
            shape = RoundedCornerShape(18.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        if (currentMode != RadioMode.TOP) {
            FilterChip(
                selected = true,
                onClick = {},
                label = {
                    Text(
                        text = when (currentMode) {
                            RadioMode.SEARCH -> stringResource(R.string.radio_search)
                            RadioMode.COUNTRY -> query.ifBlank { stringResource(R.string.radio_search) }
                            RadioMode.TOP -> stringResource(R.string.radio_top)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun RadioCountryChips(
    countries: List<RadioCountry>,
    selectedCode: String?,
    onAllClick: () -> Unit,
    onCountryClick: (String, String) -> Unit
) {
    if (countries.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "__all__") {
            FilterChip(
                selected = selectedCode == null,
                onClick = onAllClick,
                label = { Text(text = stringResource(R.string.radio_all)) },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        items(countries, key = { it.code }) { country ->
            FilterChip(
                selected = selectedCode == country.code,
                onClick = { onCountryClick(country.code, country.name) },
                label = { Text(text = country.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(18.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun RadioStationCard(
    station: RadioStation,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // 模仿媒体库音乐列表的显示方式：圆形封面 + 标题/副标题 + 播放指示
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面：电台 logo，缺失时显示收音机占位图标
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (station.favicon.isNotBlank()) {
                SmartImage(
                    model = station.favicon,
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlaying) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = station.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Spacer(Modifier.width(8.dp))
            PlayingEqIcon(color = colors.primary, isPlaying = true)
        }
    }
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

private fun RadioStation.subtitle(): String {
    val parts = mutableListOf<String>()
    if (country.isNotBlank()) parts += country
    if (codec.isNotBlank()) parts += codec
    if (bitrate > 0) parts += "${bitrate}k"
    if (language.isNotBlank()) parts += language
    return parts.joinToString(" · ")
}
