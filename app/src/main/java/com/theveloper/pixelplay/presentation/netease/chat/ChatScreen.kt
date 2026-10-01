package com.theveloper.pixelplay.presentation.netease.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.netease.chat.ChatMessage
import com.theveloper.pixelplay.data.netease.chat.ChatResource
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 单会话聊天页：气泡 + 资源卡片 + 一起听邀请按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selfUserId by viewModel.selfUserId.collectAsStateWithLifecycle()
    val peerNickname by viewModel.peerNickname.collectAsStateWithLifecycle()
    val peerAvatar by viewModel.peerAvatar.collectAsStateWithLifecycle()
    val peerDetail by viewModel.peerDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isTogglingFollow by viewModel.isTogglingFollow.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val emptyPlaylistHint = stringResource(R.string.chat_playlist_empty_hint)

    // ⚡ 这里必须复用 Activity 作用域的 PlayerViewModel（和 MainActivity / 其它页面是同一个实例）。
    //   在 NavHost 目标里直接 hiltViewModel() 拿到的是「按返回栈条目作用域」的另一个实例：
    //   它没有参与主界面的播放器初始化/服务连接，点歌曲卡片会「落库成功但播不出声、点了没反应」。
    val hostActivity = LocalContext.current as? androidx.activity.ComponentActivity
    val playerViewModel: PlayerViewModel = if (hostActivity != null) {
        hiltViewModel(hostActivity)
    } else {
        hiltViewModel()
    }

    // ⚡ 聊天详情页已在路由层隐藏 mini player（MainActivity.routesWithHiddenMiniPlayer），
    //   发送框不需要再为它预留高度，否则输入框下方会留出一大块空白。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.draftRestore.collect { input = it }
    }
    LaunchedEffect(Unit) {
        viewModel.listenTogetherMessages.collect { snackbarHostState.showSnackbar(it) }
    }
    // 滚动到最顶部（reverseLayout 的末尾）时按需加载更早的消息
    val reachedOldest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(reachedOldest, hasMore, isLoadingMore) {
        if (reachedOldest && hasMore && !isLoadingMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(url = peerAvatar, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = peerNickname.ifBlank { viewModel.peerUserId.toString() },
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                actions = {
                    peerDetail?.let { detail ->
                        TextButton(
                            onClick = { viewModel.toggleFollow() },
                            enabled = !isTogglingFollow
                        ) {
                            Text(
                                text = stringResource(
                                    if (detail.followed) R.string.chat_followed else R.string.chat_follow
                                ),
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ⚡ 不把系统底部 inset 也算进 Scaffold 的内容内边距：发送框自己用
        //   navigationBarsPadding() 处理（这样它的底色能一直铺到屏幕底边）。
        //   否则两处叠加 → 输入框下方多出一条空白。
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }

                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages.asReversed(), key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isMine = message.fromUserId == (selfUserId ?: 0L),
                                playerViewModel = playerViewModel,
                                onPlayFailed = { reason ->
                                    scope.launch { snackbarHostState.showSnackbar(reason) }
                                },
                                onJoinTogether = { inviterId, roomId ->
                                    viewModel.joinTogether(inviterId, roomId)
                                },
                                onOpenPlaylist = { playlist ->
                                    scope.launch {
                                        val songs = viewModel.loadPlaylistSongs(playlist.playlistId)
                                        if (songs.isEmpty()) {
                                            snackbarHostState.showSnackbar(emptyPlaylistHint)
                                        } else {
                                            // 整张歌单入队播放，队列名用歌单名
                                            playerViewModel.playCloudSongs(
                                                songs = songs,
                                                queueName = playlist.name,
                                                onFailure = { reason ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(reason)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        if (hasMore || isLoadingMore) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                isSending = isSending,
                onSend = {
                    val text = input
                    input = ""
                    viewModel.sendText(text)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                // ⚡ 取「系统导航栏」与「输入法」两者的较大值，而不是各自叠加：
                //   键盘关闭时只留导航栏高度，键盘弹出时输入框顶到键盘上方。
                //   两者各自 padding 会累加，键盘收起后输入框下方就会多留出一条空白。
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        fontFamily = GoogleSansRounded
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    playerViewModel: PlayerViewModel,
    onPlayFailed: (String) -> Unit,
    onJoinTogether: (inviterId: String, roomId: String) -> Unit,
    onOpenPlaylist: (ChatResource.Playlist) -> Unit
) {
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (message.text.isNotBlank() || message.resource == null) {
                Box(
                    modifier = Modifier
                        .background(bubbleColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = message.text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded
                    )
                }
            }

            message.resource?.let { resource ->
                Spacer(Modifier.height(4.dp))
                ResourceCard(
                    resource = resource,
                    playerViewModel = playerViewModel,
                    onPlayFailed = onPlayFailed,
                    onOpenPlaylist = onOpenPlaylist
                )
            }

            val invitation = message.invitation
            if (invitation != null) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = { onJoinTogether(invitation.inviterId, invitation.roomId) }) {
                    Text(
                        text = stringResource(R.string.chat_join_together),
                        fontFamily = GoogleSansRounded
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    resource: ChatResource,
    playerViewModel: PlayerViewModel,
    onPlayFailed: (String) -> Unit,
    onOpenPlaylist: (ChatResource.Playlist) -> Unit
) {
    val (title, subtitle, cover) = when (resource) {
        is ChatResource.Song -> Triple(resource.name, resource.artists, resource.coverUrl)
        is ChatResource.Playlist -> Triple(resource.name, resource.creator.orEmpty(), resource.coverUrl)
        is ChatResource.Album -> Triple(resource.name, "", resource.coverUrl)
    }

    Card(
        modifier = Modifier.then(
            when (resource) {
                is ChatResource.Song -> {
                    val song = resource
                    Modifier.clickable {
                        playerViewModel.playCloudSongs(
                            songs = listOf(
                                LxSongInfo(
                                    id = song.songId.toString(),
                                    songmid = song.songId.toString(),
                                    hash = song.songId.toString(),
                                    name = song.name,
                                    singer = song.artists,
                                    pic = song.coverUrl.orEmpty(),
                                    source = "wy"
                                )
                            ),
                            queueName = "聊天分享",
                            onFailure = onPlayFailed
                        )
                    }
                }

                // 点击歌单：拉取整张歌单（本地库优先、没同步过的自动回退网络）后整队入列播放，
                // 播放后直接沿用软件既有的队列（播放列表）界面。
                is ChatResource.Playlist -> {
                    val playlist = resource
                    Modifier.clickable { onOpenPlaylist(playlist) }
                }

                // 专辑暂未接入曲目列表，保持不可点击
                is ChatResource.Album -> Modifier
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
