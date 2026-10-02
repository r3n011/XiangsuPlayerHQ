package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.presentation.viewmodel.LxMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LxUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxMusicScreen(
    onOpenPlayer: (url: String, title: String, artist: String, cover: String, songId: String) -> Unit,
    viewModel: LxMusicViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.importFromUri(uri)
        }
    }

    // ⚡ 导出 JS 音源：选一个保存位置，把所有已导入脚本打包成 zip 写进去
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportAllJs(uri)
    }

    LaunchedEffect(Unit) {
        runCatching {
            viewModel.refreshDisplayOnly()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceSafe(R.string.lx_music_title, "云音")) },
                actions = {
                    IconButton(onClick = { filePicker.launch("application/javascript,*/*") }) {
                        Icon(Icons.Filled.Add, null)
                    }
                    IconButton(onClick = { viewModel.showImportUrl = true }) {
                        Icon(Icons.Filled.Download, null)
                    }
                    IconButton(onClick = { viewModel.reloadEngine() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                    IconButton(onClick = { viewModel.showInfo = true }) {
                        Icon(Icons.Filled.Info, null)
                    }
                    // ⚡ 导出全部 JS 音源（打包 zip，自选保存位置）
                    IconButton(
                        onClick = { exportLauncher.launch("pixelplay-js-sources.zip") },
                        enabled = state.scriptInfos.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Share, stringResourceSafe(R.string.lx_export_js, "导出 JS 音源"))
                    }
                    IconButton(onClick = { viewModel.removeAllJs() }) {
                        Icon(Icons.Filled.DeleteOutline, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.engineReady) {
                EngineNotReadyBanner(state, viewModel, onStartClick = {
                    scope.launch { viewModel.ensureEngineStarted() }
                })
            } else {
                EngineReadyBanner(state)
            }

            SearchBar(state, viewModel)

            if (state.sources.isNotEmpty() && state.engineReady) {
                SourceChipsRow(state, viewModel)
            }

            when {
                state.searching -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.error != null -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.results.isNotEmpty() -> SongList(
                    songs = state.results,
                    onPlay = { song ->
                        scope.launch {
                            viewModel.playSong(song, onOpenPlayer)
                        }
                    }
                )
                else -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResourceSafe(R.string.lx_search_hint, "在上方输入关键词，回车搜索"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (viewModel.showImportUrl) {
        ImportUrlDialog(
            onDismiss = { viewModel.showImportUrl = false },
            onSubmit = { url -> scope.launch { viewModel.importFromUrl(url) } }
        )
    }
    if (viewModel.showInfo) {
        InfoDialog(state, onDismiss = { viewModel.showInfo = false })
    }
    if (state.progress != null) {
        ProgressDialog(state.progress ?: 0f, state.progressLabel ?: "…")
    }
}

@Composable
private fun EngineNotReadyBanner(state: LxUiState, viewModel: LxMusicViewModel, onStartClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResourceSafe(R.string.lx_no_js_title, "还没有导入 JS 音源"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResourceSafe(
                    R.string.lx_no_js_desc,
                    "点右上角 + 从文件选择一个 userApi.js 或 v4.1.js。"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStartClick
            ) {
                Text(stringResourceSafe(R.string.lx_start_engine, "开始使用"))
            }
            if (state.initing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.importError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.importError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EngineReadyBanner(state: LxUiState) {
    val blockShape = remember { AbsoluteSmoothCornerShape(22.dp, 60) }
    Surface(
        shape = blockShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 状态胶囊
            Surface(
                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResourceSafe(R.string.lx_js_ready, "已就绪") + " · v" + state.version,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = state.sources.keys.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(state: LxUiState, viewModel: LxMusicViewModel) {
    OutlinedTextField(
        value = state.keyword,
        onValueChange = { viewModel.keyword = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResourceSafe(R.string.lx_search_placeholder, "搜索")) },
        singleLine = true,
        isError = state.searching,
        trailingIcon = {
            IconButton(onClick = { viewModel.search() }) {
                Icon(Icons.Filled.Search, null)
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { viewModel.search() }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceChipsRow(state: LxUiState, viewModel: LxMusicViewModel) {
    // ⚡ 横向滚动的音源标签：每个音源一个**等大的字母头像**（首字母 + 稳定取色），
    //    选中态与搜索页筛选标签一致（primary 底 + onPrimary 文字）。
    val scrollState = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceChip(
            label = stringResourceSafe(R.string.lx_source_all, "网易云"),
            selected = state.selectedSource == "wy",
            onClick = { viewModel.selectedSource = "wy" },
        )
        state.sources.entries.take(12).forEach { (key, info) ->
            SourceChip(
                label = info.name.ifBlank { key },
                selected = state.selectedSource == key,
                onClick = { viewModel.selectedSource = key },
            )
        }
    }
}

/** 单个音源标签：22dp 圆形字母头像 + 名称；头像大小在所有音源间保持一致。 */
@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "sourceChipContainer",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "sourceChipContent",
    )
    // 头像底色：按名称做稳定取色。
    // ⚡ 字母色必须用容器的 on 色（onXContainer）—— 之前固定用 onSurface，
    //    在深色/专辑取色方案下和容器几乎同色，头像上的字看不清；
    //    选中态整颗胶囊是 primary，头像改成半透明 onPrimary，避免和主色糊在一起。
    val avatarColor: androidx.compose.ui.graphics.Color
    val avatarContentColor: androidx.compose.ui.graphics.Color
    if (selected) {
        avatarColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f)
        avatarContentColor = MaterialTheme.colorScheme.onPrimary
    } else {
        val avatarPalette = listOf(
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        )
        val picked = avatarPalette[(label.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
        avatarColor = picked.first
        avatarContentColor = picked.second
    }

    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = avatarContentColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SongList(
    songs: List<LxSongInfo>,
    onPlay: (LxSongInfo) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(songs, key = { it.id }) { song ->
            SongRow(song, onPlay)
        }
    }
}

@Composable
private fun SongRow(song: LxSongInfo, onPlay: (LxSongInfo) -> Unit) {
    val rowShape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
    Surface(
        onClick = { onPlay(song) },
        shape = rowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索列表不预加载封面，降低网络请求；播放时再获取封面。
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AbsoluteSmoothCornerShape(14.dp, 60))
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    song.name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall
                )
                if (song.singer.isNotBlank() || song.albumName.isNotBlank()) {
                    Text(
                        listOfNotNull(song.singer, song.albumName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { onPlay(song) }) {
                Icon(Icons.Filled.PlayArrow, null)
            }
        }
    }
}

@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var url by remember { mutableStateOf("https://") }
    PixelAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceSafe(R.string.lx_import_url_title, "从 URL 下载 JS")) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = url.startsWith("http://") || url.startsWith("https://"),
                onClick = { onSubmit(url); onDismiss() }
            ) { Text(stringResourceSafe(R.string.lx_import, "导入")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResourceSafe(R.string.cancel, "取消")) } }
    )
}

@Composable
private fun InfoDialog(state: LxUiState, onDismiss: () -> Unit) {
    PixelAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceSafe(R.string.lx_info_title, "JS 引擎信息")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Ready", if (state.engineReady) "✓" else "✗")
                InfoRow("Version", state.version)
                InfoRow("Sources", if (state.sources.isEmpty()) "(none)" else state.sources.keys.joinToString(", "))
                if (!state.engineReady && state.importError != null) {
                    InfoRow("Error", state.importError)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResourceSafe(R.string.lx_ok, "好")) } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProgressDialog(progress: Float, label: String) {
    PixelAlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}%")
            }
        }
    )
}

private fun stringResourceSafe(id: Int, fallback: String): String = fallback
