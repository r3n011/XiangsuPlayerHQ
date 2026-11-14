package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.utils.localizedStringResource
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.R

import android.net.Uri
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Share
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
import com.theveloper.pixelplay.data.lx.LxSourceTester
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.LxMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LxUiState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun CloudMusicSettingsScreen(
    onBackClick: () -> Unit,
    onOpenMarket: () -> Unit,
    viewModel: LxMusicViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // ⚡ 音源连通性测试已隐藏（仅隐藏入口，实现代码保留）：需要时取消注释下面三行即可恢复
    // val sourceTests by viewModel.sourceTests.collectAsStateWithLifecycle()
    // val testingAllSources by viewModel.testingAllSources.collectAsStateWithLifecycle()

    // 已安装音源变化（导入/删除/重载）后刷新待测清单
    // LaunchedEffect(state.scriptInfos) {
    //     viewModel.refreshSourceTestList()
    // }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importFromUri(uri)
    }

    // ⚡ 导出全部 JS 音源：选一个保存位置，把所有已导入脚本打包成 zip 写进去
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportAllJs(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.autoInitIfPresent()
    }

    var showImportUrl by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var expandedScript by remember { mutableStateOf<String?>(null) }

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

    // ⚡ 音源临时开关状态（source -> enabled），用于每个脚本的音源管理开关
    // 平台 → 音源脚本 路由（持久化，首个脚本为首选）
    val sourceRouting by viewModel.sourceRouting.collectAsStateWithLifecycle()

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroSourceCard(
                    state = state,
                    onImportFile = { filePicker.launch("*/*") },
                    onImportUrl = { showImportUrl = true },
                    onReload = { viewModel.reloadEngine() }
                )
            }

            item {
                MarketEntryCard(onOpenMarket = onOpenMarket)
            }

            // ⚡ 音源连通性测试卡片已隐藏（仅隐藏入口，实现代码保留）：
            //    需要时取消注释即可恢复。原先它只测**已安装**的音源脚本。
            // item {
            //     SourceConnectivityCard(
            //         rows = sourceTests,
            //         testingAll = testingAllSources,
            //         onTest = { viewModel.testInstalledSource(it) },
            //         onTestAll = { viewModel.testAllInstalledSources() }
            //     )
            // }

            if (state.scriptInfos.isNotEmpty()) {
                item {
                    val scriptsByPlatform = remember(state.scriptInfos) {
                        val map = LinkedHashMap<String, MutableList<String>>()
                        state.scriptInfos.forEach { info ->
                            viewModel.getInstanceSources(info.fileName).keys.forEach { key ->
                                map.getOrPut(key) { mutableListOf() }.add(info.fileName)
                            }
                        }
                        map
                    }
                    val platformNames = remember(state.scriptInfos) {
                        val map = mutableMapOf<String, String>()
                        state.scriptInfos.forEach { info ->
                            viewModel.getInstanceSources(info.fileName).forEach { (key, src) ->
                                if (src.name.isNotBlank()) map.putIfAbsent(key, src.name)
                            }
                        }
                        map
                    }
                    PlatformRoutingCard(
                        scripts = state.scriptInfos,
                        scriptsByPlatform = scriptsByPlatform,
                        platformNames = platformNames,
                        routes = sourceRouting,
                        onConnect = { platform, file -> viewModel.connectSourceRoute(platform, file) },
                        onDisconnect = { platform, file -> viewModel.disconnectSourceRoute(platform, file) },
                        onPrefer = { platform, file -> viewModel.preferSourceRoute(platform, file) },
                        onAuto = { viewModel.autoRouteSources() },
                        onClear = { viewModel.clearSourceRoutes() },
                    )
                }
            }

            item {
                Text(
                    localizedStringResource(R.string.ui_installed_sources, (state.scriptInfos.size).toString()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            state.scriptInfos.forEach { info ->
                item(key = info.fileName) {
                    ScriptSourceCard(
                        info = info,
                        instanceSources = viewModel.getInstanceSources(info.fileName),
                        expanded = expandedScript == info.fileName,
                        onDelete = { pendingDelete = info.fileName },
                        onToggleExpand = {
                            expandedScript = if (expandedScript == info.fileName) null else info.fileName
                        }
                    )
                }
            }

            if (state.scriptInfos.isEmpty()) {
                item {
                    EmptySourceCard()
                }
            }

            item {
                ExportSourcesCard(
                    enabled = state.scriptInfos.isNotEmpty(),
                    onExport = { exportLauncher.launch("pixelplay-js-sources.zip") }
                )
            }

            item {
                UsageInfoCard()
            }
        }

        CollapsibleCommonTopBar(
            title = AppLocaleManager.getString(R.string.ui_custom_sources),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick
        )
    }

    if (showImportUrl) {
        var urlInput by remember { mutableStateOf("https://") }
        PixelAlertDialog(
            onDismissRequest = { showImportUrl = false },
            title = { Text(localizedStringResource(R.string.ui_download_javascript_from_url)) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = urlInput.startsWith("http://") || urlInput.startsWith("https://"),
                    onClick = {
                        viewModel.importFromUrl(urlInput)
                        showImportUrl = false
                    }
                ) { Text(localizedStringResource(R.string.ui_residue_lx_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportUrl = false }) { Text(localizedStringResource(R.string.ui_residue_cancel)) }
            }
        )
    }

    pendingDelete?.let { fileName ->
        PixelAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(localizedStringResource(R.string.ui_delete_script)) },
            text = { Text(localizedStringResource(R.string.ui_delete_its_music_sources_will_no_longer_be_available, (fileName).toString())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeJs(fileName)
                        pendingDelete = null
                    }
                ) { Text(localizedStringResource(R.string.ui_residue_button_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(localizedStringResource(R.string.ui_residue_cancel)) }
            }
        )
    }
}

/**
 * 已安装音源连通性测试卡：对每个已安装脚本提供的音源做一次真实搜索（JS 音源再取一次播放地址）。
 */
@Composable
private fun SourceConnectivityCard(
    rows: List<LxMusicViewModel.SourceTestUi>,
    testingAll: Boolean,
    onTest: (String) -> Unit,
    onTestAll: () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(22.dp, 60)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedStringResource(R.string.ui_residue_market_source_test_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = localizedStringResource(R.string.ui_search_each_installed_source_and_try_to_get_a_playback),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onTestAll,
                    enabled = !testingAll && rows.isNotEmpty()
                ) {
                    if (testingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (testingAll) localizedStringResource(R.string.ui_residue_market_testing) else localizedStringResource(R.string.ui_residue_market_test_all),
                        fontFamily = GoogleSansRounded
                    )
                }
            }

            if (rows.isEmpty()) {
                Text(
                    text = localizedStringResource(R.string.ui_no_source_scripts_installed_install_one_from_the_source_market),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rows.forEach { row ->
                    SourceTestRow(row = row, onTest = { onTest(row.key) })
                }
            }
        }
    }
}

@Composable
private fun SourceTestRow(
    row: LxMusicViewModel.SourceTestUi,
    onTest: () -> Unit
) {
    val result = row.result
    val statusColor = when {
        row.testing -> MaterialTheme.colorScheme.onSurfaceVariant
        result == null -> MaterialTheme.colorScheme.onSurfaceVariant
        result.status == LxSourceTester.Status.SUCCESS -> MaterialTheme.colorScheme.primary
        result.status == LxSourceTester.Status.SEARCH_ONLY -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val statusText = when {
        row.testing -> AppLocaleManager.getString(R.string.ui_residue_market_testing)
        result == null -> AppLocaleManager.getString(R.string.ui_not_tested)
        result.status == LxSourceTester.Status.SUCCESS -> AppLocaleManager.getString(R.string.ui_residue_market_test_success)
        result.status == LxSourceTester.Status.SEARCH_ONLY -> AppLocaleManager.getString(R.string.ui_search_only)
        else -> AppLocaleManager.getString(R.string.ui_residue_market_test_failed)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (result != null && !row.testing) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${result.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result != null && !row.testing) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(result.message)
                        if (result.sample.isNotBlank()) append(AppLocaleManager.getString(R.string.ui_example, (result.sample).toString()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onTest, enabled = !row.testing) {
            Text(
                text = localizedStringResource(R.string.ui_residue_market_test),
                fontFamily = GoogleSansRounded
            )
        }
    }
}

@Composable
private fun ExportSourcesCard(
    enabled: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onExport),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(R.string.ui_export_all_sources),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (enabled) localizedStringResource(R.string.ui_save_all_imported_javascript_source_scripts_in_a_local_zip)
                    else localizedStringResource(R.string.ui_no_javascript_sources_have_been_imported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketEntryCard(
    onOpenMarket: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMarket),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(R.string.ui_pixel_source_market),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    localizedStringResource(R.string.ui_residue_market_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroSourceCard(
    state: LxUiState,
    onImportFile: () -> Unit,
    onImportUrl: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (state.engineReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (state.engineReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusIcon(
                icon = Icons.Rounded.Code,
                containerColor = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor
            )

            Text(
                text = localizedStringResource(R.string.ui_custom_javascript_sources),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Text(
                text = localizedStringResource(R.string.ui_import_lx_music_user_api_v2_scripts_to_resolve_playback),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.76f)
            )

            if (state.engineReady) {
                Text(
                    text = localizedStringResource(R.string.ui_scripts_loaded_v, (state.scriptInfos.size).toString(), (state.version).toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }

            if (state.initing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.importError != null) {
                Text(
                    text = state.importError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 胶囊导入按钮：浅蓝紫背景 + 深蓝紫内容（Material 3 主色反色）
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onImportFile)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = localizedStringResource(R.string.ui_import_script),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // 从 URL 导入
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.clickable(onClick = onImportUrl)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = localizedStringResource(R.string.ui_import_from_url),
                        tint = contentColor,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                    )
                }

                // 重新加载
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.clickable(onClick = onReload)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = localizedStringResource(R.string.ui_reload),
                        tint = contentColor,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptSourceCard(
    info: LxScriptInfo,
    instanceSources: Map<String, LxSourceInfo>,
    expanded: Boolean,
    onDelete: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ⚡ 每个音源脚本一个**等大的字母头像**（首字母 + 按文件名稳定取色）：
                //    以前所有脚本都用同一个图标、大小也各写各的，认不出来谁是谁。
                //    字母色必须配容器的 on 色，否则深色模式下字和底几乎同色（看不清头像）。
                val avatarLabel = info.name.ifBlank { info.fileName }.take(1).uppercase()
                val avatarPalette = listOf(
                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
                )
                val (avatarColor, avatarContentColor) = avatarPalette[
                    info.fileName.hashCode().let { if (it < 0) -it else it } % avatarPalette.size
                ]
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = avatarColor,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = avatarLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = avatarContentColor,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.name.ifBlank { info.fileName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(info.fileName)
                            if (info.version.isNotBlank()) {
                                append(" · v").append(info.version.removePrefix("v"))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (instanceSources.isNotEmpty()) {
                        Text(
                            text = "Source Key: " + instanceSources.entries.joinToString(" · ") {
                                it.value.name.ifBlank { it.key }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = localizedStringResource(R.string.ui_delete, (info.fileName).toString()),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (info.description.isNotBlank()) {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (instanceSources.isNotEmpty()) {
                Text(
                    text = "提供平台: " + instanceSources.entries.joinToString(" · ") {
                        (it.value.name.ifBlank { it.key }) + " (" + it.key + ")"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (info.author.isNotBlank() || info.homepage.isNotBlank() ||
                info.lastUpdate.isNotBlank() || info.md5.isNotBlank()
            ) {
                TextButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) localizedStringResource(R.string.ui_hide_details) else localizedStringResource(R.string.ui_show_details))
                }
                if (expanded) {
                    listOf(
                        AppLocaleManager.getString(R.string.ui_author) to info.author,
                        AppLocaleManager.getString(R.string.ui_homepage) to info.homepage,
                        AppLocaleManager.getString(R.string.ui_last_updated) to info.lastUpdate,
                        "MD5" to info.md5
                    ).forEach { (label, value) ->
                        if (value.isNotBlank()) {
                            Text(
                                text = "$label: $value",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun EmptySourceCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = localizedStringResource(R.string.ui_no_javascript_source_scripts_imported_use_the_buttons_above_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun UsageInfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = localizedStringResource(R.string.ui_instructions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = localizedStringResource(R.string.ui_1_import_multiple_javascript_source_files_to_use_them_together) +
                    localizedStringResource(R.string.ui_2_if_multiple_scripts_register_the_same_source_they_are) +
                    localizedStringResource(R.string.ui_3_source_switches_below_each_script_temporarily_enable_or_disable) +
                    localizedStringResource(R.string.ui_4_playback_quality_settings_apply_to_all_online_sources_netease) +
                    localizedStringResource(R.string.ui_5_use_show_details_to_view_the_author_homepage_and) +
                    localizedStringResource(R.string.ui_6_all_imported_javascript_files_load_automatically_at_startup),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
