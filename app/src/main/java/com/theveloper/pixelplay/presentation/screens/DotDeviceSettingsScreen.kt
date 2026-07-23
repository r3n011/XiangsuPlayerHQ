package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.dot.DotDisplayMode
import com.theveloper.pixelplay.data.repository.DotImageRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@Composable
fun DotDeviceSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: DotDeviceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 170.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - (
            (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
        ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (
                    !isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
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
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(MainActivity.LocalHazeState.current),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DotScreenPreview(displayMode = uiState.displayMode)
            }

            item {
                DotDeviceSettingsCard(
                    apiKey = uiState.apiKey,
                    deviceId = uiState.deviceId,
                    autoPushEnabled = uiState.autoPushEnabled,
                    displayMode = uiState.displayMode,
                    isTesting = uiState.isTesting,
                    isPushing = uiState.isPushing,
                    testResult = uiState.testResult,
                    pushResult = uiState.pushResult,
                    onApiKeyChange = viewModel::setApiKey,
                    onDeviceIdChange = viewModel::setDeviceId,
                    onAutoPushToggle = viewModel::setAutoPushEnabled,
                    onDisplayModeChange = viewModel::setDisplayMode,
                    onTestConnection = viewModel::testConnection,
                    onPushNow = viewModel::pushToDevice,
                    onSave = { viewModel.saveCredentials(context) },
                    onClear = { viewModel.clearCredentials(context) }
                )
            }
        }

        CollapsibleCommonTopBar(
            title = "Dot 墨水屏",
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            subtitle = "推送内容到墨水屏设备"
        )
    }
}

@Composable
private fun DotDeviceSettingsCard(
    apiKey: String,
    deviceId: String,
    autoPushEnabled: Boolean,
    displayMode: DotDisplayMode,
    isTesting: Boolean,
    isPushing: Boolean,
    testResult: DotDeviceTestResult?,
    pushResult: DotDeviceTestResult?,
    onApiKeyChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onAutoPushToggle: (Boolean) -> Unit,
    onDisplayModeChange: (DotDisplayMode) -> Unit,
    onTestConnection: () -> Unit,
    onPushNow: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.dot_device_api_key_label)) },
                placeholder = { Text(stringResource(R.string.dot_device_api_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = deviceId,
                onValueChange = onDeviceIdChange,
                label = { Text(stringResource(R.string.dot_device_id_label)) },
                placeholder = { Text(stringResource(R.string.dot_device_id_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Surface(
                shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(14.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dot_device_auto_push_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.dot_device_auto_push_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoPushEnabled,
                        onCheckedChange = onAutoPushToggle
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "显示模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DotDisplayMode.entries.forEach { mode ->
                        val isSelected = mode == displayMode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDisplayModeChange(mode) },
                            label = {
                                Text(
                                    text = getDisplayModeName(mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                                selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }

            if (testResult != null) {
                Surface(
                    shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(14.dp, 60),
                    color = if (testResult.success) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (testResult.success) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = if (testResult.success) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = testResult.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (testResult.success) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }

            if (pushResult != null) {
                Surface(
                    shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(14.dp, 60),
                    color = if (pushResult.success) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pushResult.success) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = if (pushResult.success) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = pushResult.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pushResult.success) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onTestConnection,
                    enabled = apiKey.isNotBlank() && deviceId.isNotBlank() && !isTesting,
                    shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(18.dp, 60),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isTesting) "测试中" else "测试连接",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onPushNow,
                    enabled = apiKey.isNotBlank() && deviceId.isNotBlank() && !isPushing,
                    shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(18.dp, 60),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isPushing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isPushing) "推送中" else "立即推送",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (apiKey.isNotBlank() || deviceId.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(18.dp, 60)
                    ) {
                        Text(
                            text = stringResource(R.string.dot_device_clear),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FilledTonalButton(
                        onClick = onSave,
                        modifier = Modifier.weight(2f).height(48.dp),
                        shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(18.dp, 60)
                    ) {
                        Text(
                            text = stringResource(R.string.dot_device_save),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun getDisplayModeName(mode: DotDisplayMode): String {
    return when (mode) {
        DotDisplayMode.NOW_PLAYING -> "当前播放"
        DotDisplayMode.TODAY_STATS -> "今日听歌"
        DotDisplayMode.MONTH_STATS -> "本月统计"
        DotDisplayMode.ALL_TIME_STATS -> "累计统计"
        DotDisplayMode.TOP_SONGS -> "最常听歌曲"
        DotDisplayMode.TOP_ARTISTS -> "最常听艺术家"
        DotDisplayMode.TIME_DISTRIBUTION -> "时段分布"
    }
}

@Composable
private fun DotScreenPreview(displayMode: DotDisplayMode) {
    // Dot screen is 296x152 pixels. Preview uses fixed 296dp width.
    val previewWidth = 296.dp
    val previewHeight = 152.dp

    Card(
        shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(28.dp, 60),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投屏预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "296 × 152",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight),
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFFF0F0F0),
                shadowElevation = 1.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CanvasPreviewContent(displayMode = displayMode)
                }
            }
        }
    }
}

@Composable
private fun CanvasPreviewContent(displayMode: DotDisplayMode) {
    // Scale factor: 296dp / 296px = 1dp per px
    // All sizes match the DotScreenRenderer pixel values directly
    val sf = 1f // 1dp = 1px on dot screen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        when (displayMode) {
            DotDisplayMode.NOW_PLAYING -> NowPlayingPreview(sf)
            DotDisplayMode.TODAY_STATS,
            DotDisplayMode.MONTH_STATS,
            DotDisplayMode.ALL_TIME_STATS -> StatsPreview(sf)
            DotDisplayMode.TOP_SONGS -> TopSongsPreview(sf)
            DotDisplayMode.TOP_ARTISTS -> TopArtistsPreview(sf)
            DotDisplayMode.TIME_DISTRIBUTION -> TimeDistributionPreview(sf)
        }
    }
}

@Composable
private fun NowPlayingPreview(sf: Float) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(androidx.compose.ui.graphics.Color(0xFF444444), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = androidx.compose.ui.graphics.Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "歌曲名称",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black
            )
            Text(
                text = "艺术家名",
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color(0xFF666666)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(androidx.compose.ui.graphics.Color(0xFFDDDDDD), RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .fillMaxHeight()
                .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(4.dp))
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1:23", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        Text("3:45", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "▶ 播放中",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
}

@Composable
private fun StatsPreview(sf: Float) {
    Text(
        text = "听歌统计",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
    Text(
        text = "今日",
        fontSize = 10.sp,
        color = androidx.compose.ui.graphics.Color(0xFF888888)
    )
    Text(
        text = "12小时34分",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("156", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
            Text("播放次数", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("2小时", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
            Text("平均每日", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("最常听歌曲", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
    Text("歌曲名称 · 艺术家 · 50次", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(20, 35, 45, 30, 50, 40, 25)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            )
        }
    }
}

@Composable
private fun TopSongsPreview(sf: Float) {
    Text(
        text = "最常听歌曲",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
    Spacer(modifier = Modifier.height(6.dp))
    val songs = listOf("歌曲名称一" to "艺术家一 · 150次", "歌曲名称二" to "艺术家二 · 120次", "歌曲名称三" to "艺术家三 · 98次", "歌曲名称四" to "艺术家四 · 76次")
    songs.forEachIndexed { idx, (title, desc) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${idx + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF888888),
                modifier = Modifier.width(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
                Text(desc, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
            }
        }
    }
}

@Composable
private fun TopArtistsPreview(sf: Float) {
    Text(
        text = "最常听艺术家",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
    Spacer(modifier = Modifier.height(6.dp))
    val artists = listOf("艺术家一" to "30首歌 · 200次 · 24小时", "艺术家二" to "25首歌 · 180次 · 20小时", "艺术家三" to "18首歌 · 150次 · 16小时", "艺术家四" to "12首歌 · 100次 · 10小时")
    artists.forEachIndexed { idx, (name, detail) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${idx + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF888888),
                modifier = Modifier.width(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
                Text(detail, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
            }
        }
    }
}

@Composable
private fun TimeDistributionPreview(sf: Float) {
    Text(
        text = "时段分布",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Black
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(5, 8, 12, 18, 25, 30, 45, 55, 50, 40, 35, 42, 48, 52, 60, 65, 70, 58, 45, 38, 30, 22, 15, 10)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .background(androidx.compose.ui.graphics.Color.Black)
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("0h", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        Text("6h", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        Text("12h", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        Text("18h", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
        Text("23h", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("听歌高峰", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF888888))
    Text("17:00 · 1小时30分", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
}

data class DotDeviceTestResult(
    val success: Boolean,
    val message: String
)

data class DotDeviceUiState(
    val apiKey: String,
    val deviceId: String,
    val autoPushEnabled: Boolean,
    val displayMode: DotDisplayMode,
    val isTesting: Boolean,
    val isPushing: Boolean,
    val testResult: DotDeviceTestResult?,
    val pushResult: DotDeviceTestResult?
)

@HiltViewModel
class DotDeviceSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dotImageRepository: DotImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DotDeviceUiState(
            apiKey = "",
            deviceId = "",
            autoPushEnabled = false,
            displayMode = DotDisplayMode.NOW_PLAYING,
            isTesting = false,
            isPushing = false,
            testResult = null,
            pushResult = null
        )
    )
    val uiState: StateFlow<DotDeviceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.getDotApiKey()
            val deviceId = userPreferencesRepository.getDotDeviceId()
            val autoPush = userPreferencesRepository.dotAutoPushEnabledFlow.first()
            val modeStr = userPreferencesRepository.dotDisplayModeFlow.first()
            val displayMode = try {
                DotDisplayMode.valueOf(modeStr)
            } catch (e: Exception) {
                DotDisplayMode.NOW_PLAYING
            }

            _uiState.value = _uiState.value.copy(
                apiKey = apiKey,
                deviceId = deviceId,
                autoPushEnabled = autoPush,
                displayMode = displayMode
            )
        }
    }

    fun setApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }

    fun setDeviceId(deviceId: String) {
        _uiState.value = _uiState.value.copy(deviceId = deviceId)
    }

    fun setAutoPushEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoPushEnabled = enabled)
    }

    fun setDisplayMode(mode: DotDisplayMode) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
    }

    fun testConnection() {
        val currentState = _uiState.value
        if (currentState.apiKey.isBlank() || currentState.deviceId.isBlank()) return

        _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)

        viewModelScope.launch {
            dotImageRepository.updateCredentials()
            val result = dotImageRepository.testConnection()

            val testResult = if (result.isSuccess) {
                DotDeviceTestResult(
                    success = true,
                    message = "连接成功"
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                DotDeviceTestResult(
                    success = false,
                    message = "连接失败: $errorMsg"
                )
            }

            _uiState.value = _uiState.value.copy(isTesting = false, testResult = testResult)
        }
    }

    fun pushToDevice() {
        val currentState = _uiState.value
        if (currentState.apiKey.isBlank() || currentState.deviceId.isBlank()) return

        _uiState.value = _uiState.value.copy(isPushing = true, pushResult = null)

        viewModelScope.launch {
            dotImageRepository.updateCredentials()
            val result = dotImageRepository.pushStatsToDot(currentState.displayMode)

            val pushResult = if (result.isSuccess) {
                DotDeviceTestResult(
                    success = true,
                    message = "推送成功"
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                DotDeviceTestResult(
                    success = false,
                    message = "推送失败: $errorMsg"
                )
            }

            _uiState.value = _uiState.value.copy(isPushing = false, pushResult = pushResult)
        }
    }

    fun saveCredentials(context: android.content.Context) {
        val currentState = _uiState.value

        viewModelScope.launch {
            userPreferencesRepository.setDotApiKey(currentState.apiKey)
            userPreferencesRepository.setDotDeviceId(currentState.deviceId)
            userPreferencesRepository.setDotAutoPushEnabled(currentState.autoPushEnabled)
            userPreferencesRepository.setDotDisplayMode(currentState.displayMode.name)
            dotImageRepository.updateCredentials()

            android.widget.Toast.makeText(
                context,
                "Dot 设备设置已保存",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun clearCredentials(context: android.content.Context) {
        viewModelScope.launch {
            userPreferencesRepository.clearDotCredentials()
            dotImageRepository.updateCredentials()

            _uiState.value = _uiState.value.copy(
                apiKey = "",
                deviceId = "",
                autoPushEnabled = false,
                displayMode = DotDisplayMode.NOW_PLAYING,
                testResult = null,
                pushResult = null
            )

            android.widget.Toast.makeText(
                context,
                "Dot 设备设置已清除",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
