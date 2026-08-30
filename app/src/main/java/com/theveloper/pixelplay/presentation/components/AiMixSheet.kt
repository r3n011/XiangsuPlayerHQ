package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.theveloper.pixelplay.presentation.viewmodel.AiChatMsg
import com.theveloper.pixelplay.presentation.viewmodel.AiChatRole
import com.theveloper.pixelplay.presentation.viewmodel.AiMixHistoryEntry
import com.theveloper.pixelplay.presentation.viewmodel.AiMixViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LxMusicViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

/**
 * AI Mix —— 输入/生成 底部弹窗。
 *
 * 视觉方向（贴合应用主题色板 + 官方 Material3 组件）：
 * - 使用 MaterialTheme.colorScheme 的角色色，随 Material You / 深浅主题自适应
 * - 歌曲数量 / 情绪用官方 FilterChip
 * - 需求输入用官方 OutlinedTextField
 * - 生成用官方 Button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMixSheet(
    aiMixViewModel: AiMixViewModel,
    playerViewModel: PlayerViewModel,
    onDismissRequest: () -> Unit
) {
    val state by aiMixViewModel.uiState.collectAsState()
    val history by aiMixViewModel.history.collectAsState()

    val lxViewModel: LxMusicViewModel = hiltViewModel()

    var requestText by remember { mutableStateOf("") }
    var selectedEmotion by remember { mutableStateOf<String?>(null) }
    var songCount by remember { mutableIntStateOf(10) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (state.phase != AiMixViewModel.Phase.IDLE) {
                AiMixConversation(
                    state = state,
                    lxViewModel = lxViewModel,
                    playerViewModel = playerViewModel,
                    onReset = aiMixViewModel::reset
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    AiMixInput(
                        requestText = requestText,
                        onRequestChange = { requestText = it },
                        selectedEmotion = selectedEmotion,
                        onEmotionSelected = { selectedEmotion = it },
                        songCount = songCount,
                        onCountChange = { songCount = it },
                        fromLocal = state.fromLocal,
                        onGenerate = {
                            aiMixViewModel.build(
                                request = requestText,
                                emotion = selectedEmotion.orEmpty(),
                                count = songCount
                            )
                        }
                    )
                    if (history.isNotEmpty()) {
                        AiMixHistorySection(
                            history = history,
                            onRestore = aiMixViewModel::restoreHistory
                        )
                    }
                }
            }
        }
    }
}

/** AI Mix 历史记录：列出已生成的歌单，点击还原为 READY 对话即可播放。 */
@Composable
private fun AiMixHistorySection(
    history: List<AiMixHistoryEntry>,
    onRestore: (AiMixHistoryEntry) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
    ) {
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = scheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        history.take(6).forEach { entry ->
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onRestore(entry) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.songs.size} 首 · ${if (entry.fromLocal) "本地" else "在线"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/* ============================================================
 *
 * Input
 *
 * ============================================================ */

@Composable
private fun AiMixInput(
    requestText: String,
    onRequestChange: (String) -> Unit,
    selectedEmotion: String?,
    onEmotionSelected: (String?) -> Unit,
    songCount: Int,
    onCountChange: (Int) -> Unit,
    fromLocal: Boolean,
    onGenerate: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        // ── Hero ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "AI Mix",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (fromLocal) "从你的本地曲库生成" else "AI 为你智能生成歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(30.dp))

        // ── 歌曲数量 ──
        SectionTitle("歌曲数量")
        Spacer(Modifier.height(10.dp))

        val countOptions = listOf("Auto" to 10, "15" to 15, "25" to 25, "50" to 50)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            countOptions.forEach { (label, value) ->
                FilterChip(
                    selected = songCount == value,
                    onClick = { onCountChange(value) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = if (songCount == value) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 情绪 ──
        SectionTitle("心情")
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiMixViewModel.emotions().forEach { emotion ->
                val selected = selectedEmotion == emotion
                FilterChip(
                    selected = selected,
                    onClick = { onEmotionSelected(if (selected) null else emotion) },
                    label = { Text(emotion) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 需求描述 ──
        OutlinedTextField(
            value = requestText,
            onValueChange = onRequestChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            placeholder = { Text("例如：想听适合雨天放松的歌，温柔一点…") },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerHigh,
                unfocusedContainerColor = scheme.surfaceContainerHigh,
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outlineVariant
            )
        )

        Spacer(Modifier.height(18.dp))

        // ── 生成 ──
        Button(
            onClick = onGenerate,
            enabled = requestText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "生成歌单",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
    )
}

/* ============================================================
 *
 * Conversation
 *
 * ============================================================ */

@Composable
private fun AiMixConversation(
    state: AiMixViewModel.UiState,
    lxViewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    onReset: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    val itemCount =
        state.messages.size +
            if (state.loading) 1 else 0 +
            if (state.results.isNotEmpty()) 2 + state.results.size else 0

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "AI Mix",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface
                )
                Text(
                    text = if (state.loading) "正在生成…" else "生成完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                AiMixBubble(msg = message)
            }

            if (state.loading) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "AI 正在生成你的 Mix…",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.results.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "歌单「${state.playlistTitle.ifBlank { "AI Mix" }}」",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "共 ${state.results.size} 首 · 点击歌曲播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                items(state.results, key = { it.stableId }) { item ->
                    AiMixSongRow(
                        title = item.title,
                        subtitle = listOfNotNull(item.subtitle, item.sourceLabel).joinToString(" · "),
                        coverUrl = item.coverUrl,
                        onClick = {
                            // 点击歌曲时，把整个 AI 歌单按来源排入播放列表，自动连播
                            val onlineItems = state.results.mapNotNull { it.onlineInfo }
                            val localSongs = state.results.mapNotNull { it.localSong }
                            when {
                                item.onlineInfo != null -> {
                                    val info = item.onlineInfo
                                    lxViewModel.playSong(song = info) { url, name, singer, cover, songId ->
                                        playerViewModel.playUrl(url, name, singer, cover, songId)
                                    }
                                    // 其余在线歌曲自动追加到队列
                                    if (onlineItems.size > 1) {
                                        val excludeId = lxViewModel.getStableSongId(info)
                                        lxViewModel.enqueueSongsList(onlineItems, excludeId) { url, name, singer, cover, songId ->
                                            playerViewModel.playUrl(url, name, singer, cover, songId)
                                        }
                                    }
                                }
                                item.localSong != null -> {
                                    val local = item.localSong
                                    playerViewModel.playSongs(
                                        songsToPlay = if (localSongs.size > 1) localSongs else listOf(local),
                                        startSong = local,
                                        queueName = state.playlistTitle.ifBlank { "AI Mix" }
                                    )
                                }
                            }
                        }
                    )
                }
            } else if (!state.loading && state.phase == AiMixViewModel.Phase.READY) {
                item {
                    Text(
                        text = state.error ?: "没有生成结果，试试重新输入。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(30.dp),
                        textAlign = TextAlign.Center,
                        color = scheme.error
                    )
                }
            }
        }

        // 底部：重新生成
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("重新生成", fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ============================================================
 *
 * Message Bubble
 *
 * ============================================================ */

@Composable
private fun AiMixBubble(msg: AiChatMsg) {
    val scheme = MaterialTheme.colorScheme

    when (msg.role) {
        AiChatRole.USER -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = scheme.primaryContainer,
                    shape = RoundedCornerShape(
                        topStart = 22.dp, topEnd = 22.dp,
                        bottomStart = 22.dp, bottomEnd = 6.dp
                    ),
                    modifier = Modifier.fillMaxWidth(0.82f)
                ) {
                    Text(
                        text = msg.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = scheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AiChatRole.OP -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = scheme.tertiary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = msg.text,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        AiChatRole.AI -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    color = scheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(
                        topStart = 22.dp, topEnd = 22.dp,
                        bottomStart = 6.dp, bottomEnd = 22.dp
                    ),
                    modifier = Modifier.fillMaxWidth(0.82f)
                ) {
                    Text(
                        text = msg.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/* ============================================================
 *
 * Song Row
 *
 * ============================================================ */

@Composable
private fun AiMixSongRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(23.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}