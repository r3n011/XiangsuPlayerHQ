package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
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
import androidx.compose.material3.AlertDialog
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
                    IconButton(onClick = { viewModel.removeJs() }) {
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResourceSafe(R.string.lx_js_ready, "已就绪") + "  v" + state.version,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResourceSafe(R.string.lx_sources_label, "音源") + ": " + state.sources.keys.joinToString("·"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = state.selectedSource == "all",
                onClick = { viewModel.selectedSource = "all"; expanded = false },
                label = { Text(stringResourceSafe(R.string.lx_source_all, "全部")) }
            )
        }
        state.sources.entries.take(8).forEach { (key, info) ->
            val label = info.name.ifBlank { key }
            FilterChip(
                selected = state.selectedSource == key,
                onClick = { viewModel.selectedSource = key },
                label = { Text(label) }
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索列表不预加载封面，降低网络请求；播放时再获取封面。
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 12.dp)
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
    AlertDialog(
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
    AlertDialog(
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
    AlertDialog(
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
