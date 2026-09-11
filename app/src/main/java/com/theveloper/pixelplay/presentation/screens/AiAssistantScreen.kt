package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.viewmodel.AiAssistantViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AiChatMsg
import com.theveloper.pixelplay.presentation.viewmodel.AiChatRole
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

private val assistantSuggestions = listOf(
    "给我生成一份跑步听的歌单",
    "搜周杰伦的老歌",
    "打开音质设置"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    onBackClick: () -> Unit,
    onNavigateToSettings: (SettingsCategory) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.navToSettings) {
        uiState.navToSettings?.let {
            onNavigateToSettings(it)
            viewModel.consumeNavigation()
        }
    }

    // 来新消息时滚动到底部
    LaunchedEffect(uiState.messages.size, uiState.thinking) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "AI 助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (uiState.messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.reset() }) {
                    Icon(Icons.Rounded.ClearAll, contentDescription = null)
                }
            }
        }

        if (uiState.messages.isEmpty()) {
            // 空状态引导：用 weight(1f) 占位，保证底部输入框始终可见
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyState(onSelect = { suggestion -> viewModel.send(suggestion) })
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        onPlaySong = { song, songs ->
                            val ordered = listOf(song) + songs.filter { it.id != song.id }
                            playerViewModel.playCloudSongs(ordered, "AI 助手")
                        }
                    )
                }
            }
        }

        // 底部输入（Gemini 流光边框风格）
        Surface(
            color = colors.surface,
            shadowElevation = 8.dp
        ) {
            var inputFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    // 边框层：聚焦时显示旋转流光，否则显示微弱边框
                    if (inputFocused) {
                        val rotation by rememberInfiniteTransition(
                            label = "geminiBorder"
                        ).animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 4000, easing = LinearEasing)
                            ),
                            label = "geminiRotation"
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = 1.6f
                                    scaleY = 1.6f
                                    rotationZ = rotation
                                }
                                .background(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFF4285F4),
                                            Color(0xFF9B72CB),
                                            Color(0xFFD96570),
                                            Color(0xFF4285F4)
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = colors.outlineVariant,
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )
                    }
                    // 内容挖空层：形成 2dp 均匀边框
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .background(
                                color = colors.surfaceContainerHigh,
                                shape = RoundedCornerShape(26.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { inputFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        // Enter 发送，Shift+Enter 换行
                                        if (event.type == KeyEventType.KeyDown &&
                                            event.key == Key.Enter &&
                                            !event.isShiftPressed
                                        ) {
                                            val t = input.trim()
                                            if (t.isNotEmpty() && !uiState.thinking) {
                                                viewModel.send(t)
                                                input = ""
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = colors.onSurface
                                ),
                                cursorBrush = SolidColor(colors.primary),
                                maxLines = 4,
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (input.isEmpty()) {
                                            Text(
                                                text = "说点什么，或让我帮你搜歌、生成歌单、打开设置…",
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            val canSend = input.isNotBlank() && !uiState.thinking
                            Surface(
                                onClick = {
                                    val t = input.trim()
                                    if (t.isNotEmpty() && !uiState.thinking) {
                                        viewModel.send(t)
                                        input = ""
                                    }
                                },
                                shape = CircleShape,
                                color = if (canSend) colors.primary else colors.surfaceContainerHighest,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (canSend) colors.onPrimary else colors.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "全能 AI 助手",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "我可以帮你搜索歌曲、生成歌单，还能直接帮你找到并打开设置。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        Spacer(Modifier.height(24.dp))
        assistantSuggestions.forEach { s ->
            Surface(
                onClick = { onSelect(s) },
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AiChatMsg, onPlaySong: (LxSongInfo, List<LxSongInfo>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (msg.role) {
        AiChatRole.USER -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                    color = colors.primaryContainer
                ) {
                    Text(
                        text = msg.text,
                        color = colors.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
        AiChatRole.OP -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        AiChatRole.AI -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Column(modifier = Modifier.widthIn(max = 480.dp)) {
                    if (msg.isThinking) {
                        ThinkingCard(msg = msg)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                            color = colors.surfaceContainerHigh
                        ) {
                            Text(
                                text = msg.text + if (msg.streaming) "▍" else "",
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                    if (msg.songs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SongResults(songs = msg.songs, onPlaySong = onPlaySong)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingCard(msg: AiChatMsg) {
    var expanded by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (msg.thinkingDone) "已完成" else "AI 思考中…",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (expanded && msg.thinking.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg.thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun SongResults(songs: List<LxSongInfo>, onPlaySong: (LxSongInfo, List<LxSongInfo>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = "为你找到 ${songs.size} 首 · 点击播放",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            songs.forEach { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song, songs) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song.pic.isNotBlank()) {
                        SmartImage(
                            model = song.pic,
                            contentDescription = null,
                            targetSize = SmartImageListTargetSize,
                            useDiskCache = false,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = song.singer,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}