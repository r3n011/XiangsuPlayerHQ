package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.NeteaseComment
import com.theveloper.pixelplay.data.lx.NeteaseCommentResult
import com.theveloper.pixelplay.data.lx.NeteaseUserDetail
import com.theveloper.pixelplay.data.netease.PersonalFmApi
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 歌曲评论页（全屏覆盖）。
 * 基于 LxSearchApi 的 /comment/music 与 /user/detail 接口，
 * 支持懒加载分页（滑到底部自动加载下一页）。
 *
 * 视觉规范遵循 Material Design 3 (Monet)：
 *   - Scaffold + TopAppBar 作为顶层结构
 *   - 评论项使用标准 "list item" 布局（头像 + 标题 + 正文 + 尾部信息）
 *   - HorizontalDivider 作为视觉分隔
 *   - Section header 使用 SuggestionChip 风格
 *   - 色彩 token：onSurface / onSurfaceVariant / outlineVariant / primary
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    songId: String,
    songTitle: String,
    songArtist: String,
    api: LxSearchApi,
    personalFmApi: PersonalFmApi?,
    cookie: String?,
    currentUserId: Long,
    colorScheme: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pageSize = 20

    // —— 状态 ——
    val commentsState: MutableState<List<NeteaseComment>> = remember { mutableStateOf(emptyList()) }
    val hotCommentsState: MutableState<List<NeteaseComment>> = remember { mutableStateOf(emptyList()) }
    val hasMoreState = remember { mutableStateOf(true) }
    val isLoadingState = remember { mutableStateOf(false) }
    val isInitialLoadingState = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val offsetState = remember { mutableStateOf(0) }
    val beforeState = remember { mutableStateOf<Long?>(null) }
    val userAvatarCache = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // —— 发送评论相关状态 ——
    val commentText = remember { mutableStateOf("") }
    val isSending = remember { mutableStateOf(false) }
    val sendError = remember { mutableStateOf<String?>(null) }
    val isLoggedIn = cookie?.isNotBlank() == true
    val songIdLong = songId.toLongOrNull() ?: 0L

    // —— 点赞本地状态: 记录哪些评论被本地点赞 ——
    val likedState = remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val likedCountState = remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    // —— 辅助：懒加载用户头像 ——
    suspend fun fetchUserAvatarsIfNeeded(list: List<NeteaseComment>) {
        val missing = list
            .filter { it.user.userId > 0L }
            .distinctBy { it.user.userId }
            .filterNot { userAvatarCache.value.containsKey(it.user.userId) }
        if (missing.isEmpty()) return

        missing.chunked(4).forEach { chunk ->
            chunk.map { c ->
                scope.launch(Dispatchers.IO) {
                    val detail: NeteaseUserDetail? = try {
                        api.getUserDetail(c.user.userId)
                    } catch (t: Throwable) {
                        Timber.w(t, "getUserDetail 失败 userId=${c.user.userId}")
                        null
                    }
                    if (detail != null && detail.avatarUrl.isNotBlank()) {
                        val current = userAvatarCache.value.toMutableMap()
                        current[detail.userId] = detail.avatarUrl
                        userAvatarCache.value = current
                    }
                }
            }
        }
    }

    // —— 首次加载 ——
    LaunchedEffect(songId) {
        if (songId.isBlank()) {
            errorState.value = "歌曲 ID 为空，无法加载评论"
            isInitialLoadingState.value = false
            return@LaunchedEffect
        }
        isLoadingState.value = true
        errorState.value = null
        try {
            val result: NeteaseCommentResult = withContext(Dispatchers.IO) {
                api.getSongComments(songId = songId, limit = pageSize, offset = 0, before = null)
            }
            commentsState.value = result.comments
            hotCommentsState.value = result.hotComments
            val serverHasMore = result.hasMore
            val heuristicHasMore = result.comments.size >= pageSize
            hasMoreState.value = serverHasMore || heuristicHasMore
            offsetState.value = result.comments.size
            beforeState.value = if (result.comments.isNotEmpty()) result.cursor else null
            val firstPageUsers = (result.hotComments + result.comments)
            fetchUserAvatarsIfNeeded(firstPageUsers)

            // 同步服务器返回的 liked 状态到本地缓存
            val likedMap = mutableMapOf<Long, Boolean>()
            val countMap = mutableMapOf<Long, Int>()
            for (c in result.hotComments + result.comments) {
                likedMap[c.commentId] = c.liked
                countMap[c.commentId] = c.likedCount
            }
            likedState.value = likedMap
            likedCountState.value = countMap
        } catch (t: Throwable) {
            Timber.e(t, "首次加载评论失败")
            errorState.value = t.message ?: "加载失败"
        } finally {
            isLoadingState.value = false
            isInitialLoadingState.value = false
        }
    }

    // —— 滚动到底部自动加载 ——
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= 0 && totalItems > 0 && lastVisibleIndex >= totalItems - 5
        }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (!nearBottom) return@collect
                if (isLoadingState.value) return@collect
                if (!hasMoreState.value) return@collect
                if (offsetState.value < commentsState.value.size && commentsState.value.isNotEmpty()) {
                    offsetState.value = commentsState.value.size
                }
                isLoadingState.value = true
                try {
                    val useBefore = offsetState.value >= 5000 &&
                        (beforeState.value ?: 0L) > 0L
                    val result = withContext(Dispatchers.IO) {
                        api.getSongComments(
                            songId = songId,
                            limit = pageSize,
                            offset = if (useBefore) 0 else offsetState.value,
                            before = if (useBefore) beforeState.value else null
                        )
                    }
                    if (result.comments.isNotEmpty()) {
                        commentsState.value = commentsState.value + result.comments
                        offsetState.value += result.comments.size
                        if (result.cursor > 0L) beforeState.value = result.cursor
                        fetchUserAvatarsIfNeeded(result.comments)

                        // 同步新加载评论的 liked 状态
                        val newLikedMap = likedState.value.toMutableMap()
                        val newCountMap = likedCountState.value.toMutableMap()
                        for (c in result.comments) {
                            newLikedMap[c.commentId] = c.liked
                            newCountMap[c.commentId] = c.likedCount
                        }
                        likedState.value = newLikedMap
                        likedCountState.value = newCountMap
                    }
                    val serverHasMore = result.hasMore
                    val heuristicHasMore = result.comments.size >= pageSize
                    hasMoreState.value = serverHasMore || heuristicHasMore
                } catch (t: Throwable) {
                    Timber.e(t, "加载更多评论失败")
                } finally {
                    isLoadingState.value = false
                }
            }
    }

    BackHandler { onBackClick() }

    // —— 主体：Scaffold + TopAppBar ——
    Scaffold(
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "评论",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "$songArtist · $songTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    actionIconContentColor = colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!sendError.value.isNullOrBlank()) {
                    Text(
                        text = sendError.value ?: "",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                androidx.compose.material3.Surface(
                    color = colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(
                                bottom = maxOf(
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                                    WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoggedIn && personalFmApi != null && songIdLong > 0L) {
                            // 登录状态：搜索风格的圆角输入框
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.3f))
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = "Comment",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val tfStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = colorScheme.onSurface
                                        )
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = commentText.value,
                                            onValueChange = { commentText.value = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            textStyle = tfStyle,
                                            singleLine = false,
                                            maxLines = 3,
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colorScheme.primary),
                                            decorationBox = { innerTextField ->
                                                if (commentText.value.isEmpty()) {
                                                    Text(
                                                        text = "说点什么...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = colorScheme.primary
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val content = commentText.value.trim()
                                            if (content.isBlank() || isSending.value) return@IconButton
                                            isSending.value = true
                                            sendError.value = null
                                            scope.launch {
                                                try {
                                                    val cookieVal = cookie ?: ""
                                                    val success = personalFmApi.sendComment(
                                                        type = 0,
                                                        id = songIdLong,
                                                        content = content,
                                                        cookie = cookieVal
                                                    ).getOrDefault(false)
                                                    if (success) {
                                                        commentText.value = ""
                                                        try {
                                                            val result = withContext(Dispatchers.IO) {
                                                                api.getSongComments(songId = songId, limit = pageSize, offset = 0, before = null)
                                                            }
                                                            commentsState.value = result.comments
                                                            hotCommentsState.value = result.hotComments
                                                            offsetState.value = result.comments.size
                                                            val likedMap = mutableMapOf<Long, Boolean>()
                                                            val countMap = mutableMapOf<Long, Int>()
                                                            for (c in result.hotComments + result.comments) {
                                                                likedMap[c.commentId] = c.liked
                                                                countMap[c.commentId] = c.likedCount
                                                            }
                                                            likedState.value = likedMap
                                                            likedCountState.value = countMap
                                                            listState.animateScrollToItem(0)
                                                        } catch (t: Throwable) {
                                                            Timber.w(t, "刷新评论失败")
                                                        }
                                                    } else {
                                                        sendError.value = "发送失败，请稍后重试"
                                                    }
                                                } catch (t: Throwable) {
                                                    Timber.e(t, "发送评论失败")
                                                    sendError.value = t.message ?: "发送失败"
                                                } finally {
                                                    isSending.value = false
                                                }
                                            }
                                        },
                                        enabled = !isSending.value && commentText.value.isNotBlank()
                                    ) {
                                        if (isSending.value) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Send,
                                                contentDescription = "Send",
                                                tint = if (commentText.value.isNotBlank()) colorScheme.primary else colorScheme.primary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "请先在设置中登录网易云账户后发表评论",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when {
            // 1. 加载中
            isInitialLoadingState.value && commentsState.value.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }
            // 2. 错误
            !errorState.value.isNullOrBlank() && commentsState.value.isEmpty() && hotCommentsState.value.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorState.value ?: "加载失败",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            // 3. 有内容
            else -> {
                val hasHot = hotCommentsState.value.isNotEmpty()
                val hasRegular = commentsState.value.isNotEmpty()

                if (!hasHot && !hasRegular) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无评论，快来抢沙发~",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = 24.dp + WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding()
                        )
                    ) {
                        // —— 精彩评论 section ——
                        if (hasHot) {
                            item(key = "hot_header") {
                                SectionHeader(
                                    text = "精彩评论 (${hotCommentsState.value.size})",
                                    color = colorScheme.primary,
                                    onSurfaceColor = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(
                                items = hotCommentsState.value,
                                key = { "hot_${it.commentId}" }
                            ) { comment ->
                                CommentRow(
                                    comment = comment,
                                    avatarOverride = userAvatarCache.value[comment.user.userId],
                                    primaryColor = colorScheme.primary,
                                    onSurface = colorScheme.onSurface,
                                    onSurfaceVariant = colorScheme.onSurfaceVariant,
                                    liked = likedState.value[comment.commentId] ?: comment.liked,
                                    likedCount = likedCountState.value[comment.commentId] ?: comment.likedCount,
                                    showLike = isLoggedIn && personalFmApi != null && songIdLong > 0L,
                                    canDelete = isLoggedIn && personalFmApi != null && currentUserId > 0L && comment.user.userId == currentUserId,
                                    onLikeToggle = { isLiked ->
                                        scope.launch {
                                            // 先本地更新 UI（乐观更新）
                                            val curLikedMap = likedState.value.toMutableMap()
                                            val curCountMap = likedCountState.value.toMutableMap()
                                            curLikedMap[comment.commentId] = isLiked
                                            curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (isLiked) 1 else -1
                                            likedState.value = curLikedMap
                                            likedCountState.value = curCountMap

                                            try {
                                                val cookieVal = cookie ?: ""
                                                val success = personalFmApi!!.likeComment(
                                                    type = 0,
                                                    id = songIdLong,
                                                    cid = comment.commentId,
                                                    like = isLiked,
                                                    cookie = cookieVal
                                                ).getOrDefault(false)
                                                if (!success) {
                                                    // 失败，回滚
                                                    curLikedMap[comment.commentId] = !isLiked
                                                    curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (!isLiked) 1 else -1
                                                    likedState.value = curLikedMap
                                                    likedCountState.value = curCountMap
                                                }
                                            } catch (t: Throwable) {
                                                Timber.e(t, "点赞失败")
                                                curLikedMap[comment.commentId] = !isLiked
                                                curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (!isLiked) 1 else -1
                                                likedState.value = curLikedMap
                                                likedCountState.value = curCountMap
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            try {
                                                val cookieVal = cookie ?: ""
                                                val success = personalFmApi!!.deleteComment(
                                                    type = 0,
                                                    id = songIdLong,
                                                    commentId = comment.commentId,
                                                    cookie = cookieVal
                                                ).getOrDefault(false)
                                                if (success) {
                                                    // 从列表移除
                                                    hotCommentsState.value = hotCommentsState.value.filter { it.commentId != comment.commentId }
                                                    commentsState.value = commentsState.value.filter { it.commentId != comment.commentId }
                                                }
                                            } catch (t: Throwable) {
                                                Timber.e(t, "删除评论失败")
                                            }
                                        }
                                    },
                                    showDivider = true
                                )
                            }
                        }

                        // —— 最新评论 section ——
                        if (hasRegular) {
                            item(key = "regular_header") {
                                SectionHeader(
                                    text = "最新评论",
                                    color = colorScheme.primary,
                                    onSurfaceColor = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = if (hasHot) 12.dp else 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }
                            items(
                                items = commentsState.value,
                                key = { "c_${it.commentId}" }
                            ) { comment ->
                                CommentRow(
                                    comment = comment,
                                    avatarOverride = userAvatarCache.value[comment.user.userId],
                                    primaryColor = colorScheme.primary,
                                    onSurface = colorScheme.onSurface,
                                    onSurfaceVariant = colorScheme.onSurfaceVariant,
                                    liked = likedState.value[comment.commentId] ?: comment.liked,
                                    likedCount = likedCountState.value[comment.commentId] ?: comment.likedCount,
                                    showLike = isLoggedIn && personalFmApi != null && songIdLong > 0L,
                                    canDelete = isLoggedIn && personalFmApi != null && currentUserId > 0L && comment.user.userId == currentUserId,
                                    onLikeToggle = { isLiked ->
                                        scope.launch {
                                            val curLikedMap = likedState.value.toMutableMap()
                                            val curCountMap = likedCountState.value.toMutableMap()
                                            curLikedMap[comment.commentId] = isLiked
                                            curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (isLiked) 1 else -1
                                            likedState.value = curLikedMap
                                            likedCountState.value = curCountMap

                                            try {
                                                val cookieVal = cookie ?: ""
                                                val success = personalFmApi!!.likeComment(
                                                    type = 0,
                                                    id = songIdLong,
                                                    cid = comment.commentId,
                                                    like = isLiked,
                                                    cookie = cookieVal
                                                ).getOrDefault(false)
                                                if (!success) {
                                                    curLikedMap[comment.commentId] = !isLiked
                                                    curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (!isLiked) 1 else -1
                                                    likedState.value = curLikedMap
                                                    likedCountState.value = curCountMap
                                                }
                                            } catch (t: Throwable) {
                                                Timber.e(t, "点赞失败")
                                                curLikedMap[comment.commentId] = !isLiked
                                                curCountMap[comment.commentId] = (curCountMap[comment.commentId] ?: comment.likedCount) + if (!isLiked) 1 else -1
                                                likedState.value = curLikedMap
                                                likedCountState.value = curCountMap
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            try {
                                                val cookieVal = cookie ?: ""
                                                val success = personalFmApi!!.deleteComment(
                                                    type = 0,
                                                    id = songIdLong,
                                                    commentId = comment.commentId,
                                                    cookie = cookieVal
                                                ).getOrDefault(false)
                                                if (success) {
                                                    hotCommentsState.value = hotCommentsState.value.filter { it.commentId != comment.commentId }
                                                    commentsState.value = commentsState.value.filter { it.commentId != comment.commentId }
                                                }
                                            } catch (t: Throwable) {
                                                Timber.e(t, "删除评论失败")
                                            }
                                        }
                                    },
                                    showDivider = true
                                )
                            }
                        }

                        // —— 加载中 / 到底 状态 ——
                        if (isLoadingState.value && !isInitialLoadingState.value) {
                            item(key = "loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "正在加载更多…",
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        if (!hasMoreState.value && commentsState.value.isNotEmpty()) {
                            item(key = "eol") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "— 已经到底啦 —",
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
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

// —————————————————————————————————————————————————
// Section header：用文字标签（粗体 + 主题色）作为 section 标题
// —————————————————————————————————————————————————
@Composable
private fun SectionHeader(
    text: String,
    color: Color,
    onSurfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
    )
}

// —————————————————————————————————————————————————
// 标准评论行（Material 3 Surface 卡片风格，类似设置页面）
//   container: surfaceContainer + 10dp 圆角
//   leading: 40dp 圆形头像
//   headline: 昵称（titleSmall / Medium weight）
//   body: 评论内容（bodyMedium / onSurface，lineHeight ~1.5）
//   footer: 时间 + 删除按钮 + 点赞按钮（数字可显示完整）
// —————————————————————————————————————————————————
@Composable
private fun CommentRow(
    comment: NeteaseComment,
    avatarOverride: String?,
    primaryColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    liked: Boolean,
    likedCount: Int,
    showLike: Boolean,
    canDelete: Boolean,
    onLikeToggle: (Boolean) -> Unit = {},
    onDelete: () -> Unit = {},
    showDivider: Boolean = true
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // —— leading：头像 ——
            val avatarUrl = avatarOverride?.ifBlank { null }
                ?: comment.user.avatarUrl.ifBlank { null }

            if (avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.user.nickname.firstOrNull()?.uppercase()
                            ?: "U",
                        color = primaryColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // —— headline + body + footer ——
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user.nickname.ifBlank { "匿名用户" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = comment.content.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface,
                    lineHeight = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // —— 底部：时间 + 删除按钮 + 点赞按钮（数字与心形在一行，有足够宽度）——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (comment.timeStr.isNotBlank()) {
                        Text(
                            text = comment.timeStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // —— 删除按钮（仅当前用户自己的评论）——
                    if (canDelete) {
                        androidx.compose.material3.TextButton(
                            onClick = onDelete,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "删除",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // —— 点赞区域：单独 Row 确保数字完整显示 ——
                    if (showLike) {
                        val likeInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (liked) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                                .clickable(
                                    onClick = { onLikeToggle(!liked) },
                                    interactionSource = likeInteractionSource,
                                    indication = null
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (liked) "♥" else "♡",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (liked) MaterialTheme.colorScheme.error else onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatCompactCount(likedCount.coerceAtLeast(0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (liked) MaterialTheme.colorScheme.error else onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (likedCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "♥",
                                style = MaterialTheme.typography.titleSmall,
                                color = onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatCompactCount(likedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// 轻量的点赞数字格式化（>1000 时显示 1.2k 等）
private fun formatCompactCount(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 10000 -> String.format("%.1fk", count / 1000f)
        else -> String.format("%.1fw", count / 10000f)
    }
}
