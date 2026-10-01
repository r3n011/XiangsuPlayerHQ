package com.theveloper.pixelplay.presentation.netease.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.preferences.dataStore
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveButtonGroup
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.data.netease.chat.ChatContact
import com.theveloper.pixelplay.data.netease.chat.ChatUserSummary
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 消息中心：会话列表 + 用户搜索关注 两个分区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenChat: (userId: Long, name: String, avatar: String?) -> Unit,
    onGoLogin: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.refreshConversations()
    }
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    // ⚡ 顶栏 1:1 模仿「听歌统计」：大标题随滚动在 176dp ↔ (62dp + 状态栏) 之间收起/展开，
    //    分段标签固定在标题下方，内容从「标题 + 标签」之下开始滚动。
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 62.dp + statusBarHeight
    val maxTopBarHeight = 176.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) /
            (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val scrollingDown = delta < 0

                // 列表不在顶部且正在下拉时，先把列表拉回顶部，顶栏不参与
                if (!scrollingDown && (listState.firstVisibleItemIndex > 0 ||
                        listState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsume = !(scrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    // 松手后按滚动位置吸附到「完全展开」或「完全收起」
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != target) {
                coroutineScope.launch {
                    topBarHeight.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val tabsHeight = 56.dp
    val contentTopPadding = currentTopBarHeightDp + tabsHeight + 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(nestedScrollConnection)
    ) {
        when {
            !isLoggedIn -> LoginRequiredContent(
                onGoLogin = onGoLogin,
                topPadding = currentTopBarHeightDp
            )

            selectedTab == 0 -> ConversationsTab(
                listState = listState,
                conversations = conversations,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshConversations() },
                onOpenChat = onOpenChat,
                contentTopPadding = contentTopPadding
            )

            else -> SearchUsersTab(
                listState = listState,
                state = searchState,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                isFollowed = { viewModel.isFollowed(it, searchState) },
                isToggling = { it.userId in searchState.toggling },
                onToggleFollow = { user, target -> viewModel.toggleFollow(user.userId, target) },
                onOpenChat = onOpenChat,
                contentTopPadding = contentTopPadding
            )
        }

        // ⚡ 固定顶栏（大标题 + 分段胶囊标签）：结构 / 配色策略与「听歌统计」一致
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(5f)
        ) {
            val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
            val topBarStyleContext = LocalContext.current
            val newTopBarPrefs by remember(topBarStyleContext) {
                topBarStyleContext.dataStore.data
                    .map { prefs ->
                        (prefs[booleanPreferencesKey("disable_blur_all_over")] ?: false) to
                            (prefs[booleanPreferencesKey("use_new_top_bar")] ?: true)
                    }
            }.collectAsStateWithLifecycle(initialValue = false to true)
            val useNewTopBarStyle = newTopBarPrefs.second && !newTopBarPrefs.first
            // 新版顶栏自带渐进模糊遮罩：不用再加不透明纯色底
            val backgroundColor = if (useNewTopBarStyle) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha)
            }

            Column(
                modifier = Modifier
                    .background(backgroundColor)
                    .padding(bottom = 8.dp)
            ) {
                CollapsibleCommonTopBar(
                    title = stringResource(R.string.chat_title),
                    collapseFraction = collapseFraction,
                    headerHeight = currentTopBarHeightDp,
                    onBackClick = onBack,
                    containerColor = Color.Transparent,
                    actions = {
                        IconButton(onClick = { selectedTab = 1 }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.chat_action_new_chat)
                            )
                        }
                    }
                )

                if (isLoggedIn) {
                    // ⚡ 分段胶囊标签：与设置里的播放器样式切换同款组件，
                    //    替代 Material 默认的下划线标签，和软件整体设计一致
                    ExpressiveButtonGroup(
                        items = listOf(
                            stringResource(R.string.chat_tab_conversations),
                            stringResource(R.string.chat_tab_search)
                        ),
                        selectedIndex = selectedTab,
                        onItemClick = { selectedTab = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConversationsTab(
    listState: LazyListState,
    conversations: List<ChatContact>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (Long, String, String?) -> Unit,
    /** 顶栏（大标题 + 标签行）占用的高度：内容从它下面开始 */
    contentTopPadding: Dp
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentTopPadding)
            )
        }
    ) {
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = contentTopPadding)) {
                EmptyHint(text = stringResource(R.string.chat_empty_conversations))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentTopPadding,
                    bottom = MiniPlayerHeight + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.userId }) { contact ->
                    ConversationRow(contact = contact, onClick = {
                        onOpenChat(contact.userId, contact.nickname, contact.avatarUrl)
                    })
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(contact: ChatContact, onClick: () -> Unit) {
    val shape = AbsoluteSmoothCornerShape(22.dp, 60)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = contact.avatarUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.nickname,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = contact.lastMessagePreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRelativeTime(contact.lastMessageTimeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (contact.unreadCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (contact.unreadCount > 99) "99+" else contact.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SearchUsersTab(
    listState: LazyListState,
    state: UserSearchState,
    onQueryChange: (String) -> Unit,
    isFollowed: (ChatUserSummary) -> Boolean,
    isToggling: (ChatUserSummary) -> Boolean,
    onToggleFollow: (ChatUserSummary, Boolean) -> Unit,
    onOpenChat: (Long, String, String?) -> Unit,
    /** 顶栏（大标题 + 标签行）占用的高度：内容从它下面开始 */
    contentTopPadding: Dp
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = contentTopPadding)) {
        // 全圆角搜索框：与软件其它搜索入口形状一致
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(50),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = {
                Text(
                    text = stringResource(R.string.chat_search_hint),
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )

        when {
            state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            state.query.isBlank() -> EmptyHint(text = stringResource(R.string.chat_empty_search))

            state.results.isEmpty() -> EmptyHint(text = stringResource(R.string.chat_no_results))

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MiniPlayerHeight + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.results, key = { it.userId }) { user ->
                    UserRow(
                        user = user,
                        followed = isFollowed(user),
                        toggling = isToggling(user),
                        onToggleFollow = { target -> onToggleFollow(user, target) },
                        onSendMessage = { onOpenChat(user.userId, user.nickname, user.avatarUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: ChatUserSummary,
    followed: Boolean,
    toggling: Boolean,
    onToggleFollow: (Boolean) -> Unit,
    onSendMessage: () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(22.dp, 60)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = user.avatarUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.nickname,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            user.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (followed) {
                OutlinedButton(onClick = { onToggleFollow(false) }, enabled = !toggling) {
                    Text(stringResource(R.string.chat_unfollow), fontFamily = GoogleSansRounded)
                }
            } else {
                FilledTonalButton(onClick = { onToggleFollow(true) }, enabled = !toggling) {
                    Text(stringResource(R.string.chat_follow), fontFamily = GoogleSansRounded)
                }
            }
            IconButton(onClick = onSendMessage) {
                Icon(
                    Icons.Rounded.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.chat_send_message)
                )
            }
        }
    }
    }
}

@Composable
private fun LoginRequiredContent(onGoLogin: () -> Unit, topPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_need_login),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGoLogin) {
            Text(stringResource(R.string.chat_go_login), fontFamily = GoogleSansRounded)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    SmartImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(size),
        shape = CircleShape
    )
}

/** 会话时间：今天显示 HH:mm，昨天显示「昨天」，同年显示 MM-dd，否则 yyyy-MM-dd */
internal fun formatRelativeTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timeMs }

    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
        sameYear && dayDiff == 1 -> "昨天"
        sameYear -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timeMs))
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMs))
    }
}
