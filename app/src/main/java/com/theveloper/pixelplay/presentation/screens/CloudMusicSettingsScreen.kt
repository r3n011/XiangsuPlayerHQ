package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.lx.LxScriptInfo
import com.theveloper.pixelplay.data.lx.LxSourceInfo
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
    viewModel: LxMusicViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importFromUri(uri)
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
    val sourceToggles by viewModel.sourceToggles.collectAsStateWithLifecycle()

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
                Text(
                    "已安装音源 (${state.scriptInfos.size})",
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
                        sourceToggles = sourceToggles,
                        expanded = expandedScript == info.fileName,
                        onToggleSource = { key, enabled -> viewModel.toggleSource(key, enabled) },
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
                UsageInfoCard()
            }
        }

        CollapsibleCommonTopBar(
            title = "自定义音源",
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            subtitle = "LX user-api v2 播放解析脚本",
            fadeSubtitleOnCollapse = false
        )
    }

    if (showImportUrl) {
        var urlInput by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showImportUrl = false },
            title = { Text("从 URL 下载 JS") },
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
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportUrl = false }) { Text("取消") }
            }
        )
    }

    pendingDelete?.let { fileName ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除脚本") },
            text = { Text("确定删除「$fileName」吗？删除后该脚本的音源将不可用。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeJs(fileName)
                        pendingDelete = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
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
                text = "JavaScript 自定义音源",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Text(
                text = "导入落雪官方 user-api v2 脚本，扩展播放地址、歌词和封面解析。",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.76f)
            )

            if (state.engineReady) {
                Text(
                    text = "已加载 ${state.scriptInfos.size} 个脚本 · v${state.version}",
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
                            text = "导入脚本",
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
                        contentDescription = "从 URL 导入",
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
                        contentDescription = "重新加载",
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
    sourceToggles: Map<String, Boolean>,
    expanded: Boolean,
    onToggleSource: (String, Boolean) -> Unit,
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
                StatusIcon(
                    icon = Icons.Rounded.WorkspacePremium,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 42.dp,
                    iconSize = 22.dp
                )
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
                        contentDescription = "删除 ${info.fileName}",
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
                instanceSources.forEach { (key, srcInfo) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = srcInfo.name.ifBlank { key },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (srcInfo.qualitys.isNotEmpty()) {
                                Text(
                                    text = srcInfo.qualitys.joinToString(" / "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = sourceToggles[key] ?: true,
                            onCheckedChange = { onToggleSource(key, it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
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
                    Text(if (expanded) "收起简介" else "查看简介")
                }
                if (expanded) {
                    listOf(
                        "作者" to info.author,
                        "主页" to info.homepage,
                        "更新时间" to info.lastUpdate,
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
            text = "尚未导入任何 JS 音源脚本，点上方按钮导入即可同时加载多个音源。",
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
                text = "使用说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "1. 可同时导入多个 JS 音源文件（如聚合音源脚本），自动一起生效\n" +
                    "2. 多个脚本注册同一音源时按加载顺序逐个尝试，直到拿到结果\n" +
                    "3. 每个脚本下方的音源开关可临时单独启用/禁用某个音源（运行时生效，重启恢复）\n" +
                    "4. 播放音质设置对所有在线音源生效（网易云、内置源、落雪脚本）\n" +
                    "5. 点每个脚本的「查看简介」可查看作者、主页、更新时间等信息\n" +
                    "6. 启动时会自动加载已导入的所有 JS 文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
