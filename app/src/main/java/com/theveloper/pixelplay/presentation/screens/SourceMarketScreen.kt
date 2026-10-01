package com.theveloper.pixelplay.presentation.screens

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.GitHubRelease
import com.theveloper.pixelplay.data.lx.LxSourceTester
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.MarketJsEntryUi
import com.theveloper.pixelplay.presentation.viewmodel.ReleaseState
import com.theveloper.pixelplay.presentation.viewmodel.SourceMarketViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SourceRowUi
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SourceMarketScreen(
    onBackClick: () -> Unit,
    viewModel: SourceMarketViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 安装结果提示（此前 installSuccess / installError 未被消费）
    LaunchedEffect(state.installSuccess, state.installError) {
        val message = state.installSuccess ?: state.installError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 170.dp
    val topBarHeight = remember { Animatable(with(density) { maxTopBarHeight.toPx() }) }

    val collapseFraction = remember { Animatable(0f) }
    LaunchedEffect(topBarHeight.value) {
        collapseFraction.snapTo(
            1f - ((topBarHeight.value - with(density) { minTopBarHeight.toPx() }) /
                (with(density) { maxTopBarHeight.toPx() } - with(density) { minTopBarHeight.toPx() }))
                .coerceIn(0f, 1f)
        )
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta)
                    .coerceIn(with(density) { minTopBarHeight.toPx() }, with(density) { maxTopBarHeight.toPx() })
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }
                val canConsumeScroll = !(isScrollingDown && newHeight == with(density) { minTopBarHeight.toPx() })
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
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
            // 顶部说明卡
            item {
                MarketIntroCard(
                    loading = state.loadingReleases,
                    error = state.releasesError,
                    installedCount = state.releases
                        .flatMap { (state.releaseStates[it.tag_name] as? ReleaseState.Inspected)?.entries.orEmpty() }
                        .count { it.installed },
                    onRefresh = { viewModel.loadReleases() }
                )
            }

            // ⚡ 连通性测试已移到「音源设置页」（设置 → 在线音源），
            //   只测已安装的音源；市场页只负责安装/更新。

            if (state.loadingReleases && state.releases.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.releases.forEach { release ->
                item(key = release.tag_name) {
                    ReleaseCard(
                        release = release,
                        releaseState = state.releaseStates[release.tag_name] ?: ReleaseState.Idle,
                        expanded = state.expandedTag == release.tag_name,
                        installingEntry = state.installingEntry,
                        onToggleExpand = { viewModel.toggleExpand(release.tag_name) },
                        onInstall = { entryUi -> viewModel.installEntry(release.tag_name, entryUi) }
                    )
                }
            }

            val releasesError = state.releasesError
            if (releasesError != null && state.releases.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AbsoluteSmoothCornerShape(20.dp, 60),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = releasesError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // 底部提示条（悬浮在列表上方；必须在 Box 作用域内才能对齐底部）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        )
    }

    // 顶栏（悬浮在 LazyColumn 上方）
    CollapsibleCommonTopBar(
        title = stringResource(R.string.market_title),
        collapseFraction = collapseFraction.value,
        headerHeight = currentTopBarHeightDp,
        onBackClick = onBackClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = (collapseFraction.value * 2f).coerceIn(0f, 1f)
        ),
        actions = {
            IconButton(onClick = { viewModel.loadReleases() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.market_refresh))
            }
        }
    )
}

@Composable
private fun MarketIntroCard(
    loading: Boolean,
    error: String?,
    installedCount: Int,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp).size(26.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.market_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.market_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    stringResource(R.string.market_installed_count, installedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * 音源连通性测试区块：逐个音源发起真实搜索（JS 音源再取一次播放地址）。
 */
@Composable
private fun SourceTestSection(
    sources: List<SourceRowUi>,
    testingSources: Set<String>,
    onTest: (String) -> Unit,
    onTestAll: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.market_source_test_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.market_source_test_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onTestAll,
                    enabled = sources.isNotEmpty() && testingSources.isEmpty()
                ) {
                    Text(stringResource(R.string.market_test_all))
                }
            }

            Spacer(Modifier.height(4.dp))

            if (sources.isEmpty()) {
                Text(
                    stringResource(R.string.market_no_sources),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                sources.forEach { row ->
                    SourceTestRow(
                        row = row,
                        testing = row.key in testingSources,
                        onTest = { onTest(row.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceTestRow(
    row: SourceRowUi,
    testing: Boolean,
    onTest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (row.jsDriven) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = stringResource(
                            if (row.jsDriven) R.string.market_source_plugin else R.string.market_source_builtin
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.jsDriven) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            val result = row.result
            val statusText = result?.let { statusLabel(it.status) }
            Text(
                text = when {
                    testing -> stringResource(R.string.market_testing)
                    result == null -> row.key
                    else -> "$statusText · ${result.latencyMs}ms · ${result.message}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    testing -> MaterialTheme.colorScheme.onSurfaceVariant
                    result == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    result.status == LxSourceTester.Status.SUCCESS -> MaterialTheme.colorScheme.primary
                    result.status == LxSourceTester.Status.SEARCH_ONLY -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (testing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onTest) {
                Text(stringResource(R.string.market_test))
            }
        }
        if (row.result != null && !testing) {
            Icon(
                imageVector = if (row.result.status == LxSourceTester.Status.FAILED) {
                    Icons.Rounded.Warning
                } else {
                    Icons.Rounded.CheckCircle
                },
                contentDescription = null,
                tint = if (row.result.status == LxSourceTester.Status.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun statusLabel(status: LxSourceTester.Status): String = when (status) {
    LxSourceTester.Status.SUCCESS -> stringResource(R.string.market_test_success)
    LxSourceTester.Status.SEARCH_ONLY -> stringResource(R.string.market_test_search_only)
    LxSourceTester.Status.FAILED -> stringResource(R.string.market_test_failed)
}

@Composable
private fun ReleaseCard(
    release: GitHubRelease,
    releaseState: ReleaseState,
    expanded: Boolean,
    installingEntry: String?,
    onToggleExpand: () -> Unit,
    onInstall: (MarketJsEntryUi) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = release.tag_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val meta = buildList {
                        add(formatDate(release.published_at))
                        release.assets.firstOrNull { it.name.lowercase().endsWith(".zip") }?.let { zip ->
                            add(formatSize(zip.size))
                        }
                    }.joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 展开区
            if (expanded) {
                when (val rs = releaseState) {
                    is ReleaseState.Downloading -> {
                        Spacer(Modifier.height(10.dp))
                        val progress = if (rs.progress >= 0f) rs.progress else 0f
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                        Text(
                            stringResource(R.string.market_downloading, (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    ReleaseState.Inspecting -> {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.market_parsing),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    is ReleaseState.Inspected -> {
                        Spacer(Modifier.height(10.dp))
                        rs.entries.forEach { entryUi ->
                            MarketJsEntryRow(
                                entryUi = entryUi,
                                installing = installingEntry == entryUi.entry.fileName,
                                onInstall = { onInstall(entryUi) }
                            )
                        }
                    }
                    is ReleaseState.Error -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = rs.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ReleaseState.Idle -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.market_tap_expand),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketJsEntryRow(
    entryUi: MarketJsEntryUi,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entryUi.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (entryUi.version.isNotBlank()) add("v${entryUi.version}")
                    if (entryUi.author.isNotBlank()) add(entryUi.author)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entryUi.installed) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.market_installed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (installing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onInstall)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.market_install),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(publishedAt: String): String {
    return try {
        val parsed = java.time.Instant.parse(publishedAt.trim())
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date.from(parsed))
    } catch (e: Exception) {
        publishedAt
    }
}

/** 附件体积：小于 1MB 显示 KB，否则显示 MB */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.getDefault(), "%.0f KB", kb)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
    }
}
