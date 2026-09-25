package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.NeteaseComment
import com.theveloper.pixelplay.data.lx.NeteaseCommentResult
import com.theveloper.pixelplay.data.lx.NeteaseCommentUser
import com.theveloper.pixelplay.data.lx.NeteaseUserDetail
import com.theveloper.pixelplay.data.netease.PersonalFmApi
import com.theveloper.pixelplay.presentation.components.scoped.LyricsPredictiveBackHandler
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    // ⚡ 发评成功后本地插入的新评论 id：行内做两次高亮脉冲提示
    val highlightedCommentId = remember { mutableStateOf<Long?>(null) }
    val isLoggedIn = cookie?.isNotBlank() == true
    val songIdLong = songId.toLongOrNull() ?: 0L

    // —— 回复评论状态（回复模式下发的是楼中楼）——
    val replyToCommentId = remember { mutableStateOf<Long?>(null) }
    val replyToNickname = remember { mutableStateOf<String?>(null) }

    // —— 楼中楼（评论下的回复）展开状态 ——
    val repliesState = remember { mutableStateOf<Map<Long, List<NeteaseComment>>>(emptyMap()) }
    val expandedReplies = remember { mutableStateOf<Set<Long>>(emptySet()) }
    val repliesLoading = remember { mutableStateOf<Set<Long>>(emptySet()) }
    val repliesError = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // 楼中楼分页：服务端单次最多返回 10 条，用时间游标继续翻页
    val repliesHasMore = remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val repliesTimeCursor = remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }

    /** 懒加载某条评论下的楼中楼回复；[loadMore] = true 时按时间游标追加下一页 */
    suspend fun loadReplies(commentId: Long, loadMore: Boolean = false) {
        if (repliesLoading.value.contains(commentId)) return
        if (!loadMore && repliesState.value.containsKey(commentId)) return
        val startTime = if (loadMore) (repliesTimeCursor.value[commentId] ?: -1L) else -1L
        repliesLoading.value = repliesLoading.value + commentId
        if (!loadMore) {
            repliesError.value = repliesError.value - commentId
            repliesHasMore.value = repliesHasMore.value - commentId
            repliesTimeCursor.value = repliesTimeCursor.value - commentId
        }
        try {
            val page = personalFmApi?.getCommentReplies(
                type = 0,
                id = songIdLong,
                commentId = commentId,
                cookie = cookie,
                time = startTime,
            )?.getOrNull()
            val fetched = page?.replies ?: emptyList()
            val existing = if (loadMore) (repliesState.value[commentId] ?: emptyList()) else emptyList()
            val merged = (existing + fetched).distinctBy { it.commentId }
            repliesState.value = repliesState.value + (commentId to merged)
            // 重新加载成功后清除上一次的错误（如"加载更多"失败）
            repliesError.value = repliesError.value - commentId
            // 返回空 / 游标未推进 → 判定没有更多，避免死循环
            val cursor = page?.nextTime ?: -1L
            val hasMore = page?.hasMore == true &&
                fetched.isNotEmpty() &&
                cursor > 0L &&
                cursor != startTime
            repliesHasMore.value = repliesHasMore.value + (commentId to hasMore)
            if (cursor > 0L) {
                repliesTimeCursor.value = repliesTimeCursor.value + (commentId to cursor)
            }
        } catch (t: Throwable) {
            Timber.e(t, "加载楼中楼失败 commentId=$commentId")
            repliesError.value = repliesError.value + (commentId to (t.message ?: "加载失败"))
        } finally {
            repliesLoading.value = repliesLoading.value - commentId
        }
    }

    // —— 点赞本地状态: 记录哪些评论被本地点赞 ——
    val likedState = remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val likedCountState = remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    // —— 点赞状态持久化：重启后仍保留用户自己的点赞态 ——
    val context = LocalContext.current
    val likesPrefs = remember { context.getSharedPreferences(LIKES_PREFS_NAME, Context.MODE_PRIVATE) }
    val likesStoreKey = remember(songId) { "song_$songId" }
    val persistedLiked = remember(songId) {
        mutableStateOf(readLikedOverrides(likesPrefs, "song_$songId"))
    }

    /** 点赞：先乐观更新，服务端成功后再落盘持久化 */
    suspend fun toggleLike(comment: NeteaseComment, isLiked: Boolean) {
        val cid = comment.commentId
        val prevLiked = likedState.value[cid] ?: comment.liked
        val prevCount = likedCountState.value[cid] ?: comment.likedCount
        likedState.value = likedState.value + (cid to isLiked)
        likedCountState.value = likedCountState.value + (cid to (prevCount + if (isLiked) 1 else -1))

        val ok = try {
            personalFmApi?.likeComment(
                type = 0,
                id = songIdLong,
                cid = cid,
                like = isLiked,
                cookie = cookie ?: ""
            )?.getOrDefault(false) == true
        } catch (t: Throwable) {
            Timber.e(t, "点赞失败")
            false
        }

        if (ok) {
            writeLikedOverride(likesPrefs, likesStoreKey, cid, isLiked)
            persistedLiked.value = persistedLiked.value + (cid to isLiked)
        } else {
            // 失败回滚 + 明确提示：此前静默回滚，用户只看到红心"过一会儿自己消失"
            likedState.value = likedState.value + (cid to prevLiked)
            likedCountState.value = likedCountState.value + (cid to prevCount)
            android.widget.Toast.makeText(
                context,
                if (!isLoggedIn) "点赞失败：请先登录网易云账户" else "点赞失败：请检查网易云登录状态或网络",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * ⚡ 发评成功后本地即时插入新评论并返回其列表 index（无需等服务端刷新）：
     * 官方接口刷新常有索引延迟，导致"必须重新进入评论页才能看到刚发的评论"。
     * @return LazyColumn 中的目标 index；-1 表示未插入
     */
    suspend fun insertMyCommentLocally(newCommentId: Long, content: String): Int {
        if (songIdLong <= 0L || currentUserId <= 0L) return -1
        // 补全当前用户信息（昵称/头像），失败时用占位
        val me: NeteaseUserDetail? = try {
            withContext(Dispatchers.IO) { api.getUserDetail(currentUserId) }
        } catch (t: Throwable) {
            Timber.w(t, "获取当前用户信息失败，使用占位头像")
            null
        }
        val myComment = NeteaseComment(
            commentId = newCommentId.takeIf { it > 0L } ?: -System.currentTimeMillis(),
            content = content,
            time = System.currentTimeMillis(),
            timeStr = "刚刚",
            likedCount = 0,
            liked = false,
            user = NeteaseCommentUser(
                userId = currentUserId,
                nickname = me?.nickname.orEmpty().ifBlank { "我" },
                avatarUrl = me?.avatarUrl.orEmpty()
            )
        )
        // 服务端索引若已建好（重复打开等场景），不重复插入
        commentsState.value.firstOrNull { it.commentId == myComment.commentId }?.let { return -1 }
        // 热评在前面时：[hot_header, 热评…, regular_header, 新评论…] → index = 热评数 + 2；否则 1
        val hotCount = hotCommentsState.value.size
        val index = if (hotCount > 0) hotCount + 2 else 1
        commentsState.value = listOf(myComment) + commentsState.value
        likedState.value = likedState.value + (myComment.commentId to false)
        likedCountState.value = likedCountState.value + (myComment.commentId to 0)
        return index
    }

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
            commentsState.value = result.comments.distinctBy { it.commentId }
            hotCommentsState.value = result.hotComments.distinctBy { it.commentId }
            val serverHasMore = result.hasMore
            val heuristicHasMore = result.comments.size >= pageSize
            hasMoreState.value = serverHasMore || heuristicHasMore
            offsetState.value = result.comments.size
            beforeState.value = if (result.comments.isNotEmpty()) result.cursor else null
            val firstPageUsers = (result.hotComments + result.comments)
            fetchUserAvatarsIfNeeded(firstPageUsers)

            // 同步服务器返回的 liked 状态到本地缓存（本地覆盖表优先，保证重启后点赞态不丢）
            val overrides = persistedLiked.value
            val likedMap = mutableMapOf<Long, Boolean>()
            val countMap = mutableMapOf<Long, Int>()
            for (c in result.hotComments + result.comments) {
                val override = overrides[c.commentId]
                likedMap[c.commentId] = override ?: c.liked
                countMap[c.commentId] = mergeLikeCount(c.likedCount, c.liked, override)
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
                        // ⚡ 去重合并：服务端分页偶发返回重复评论 → LazyColumn 重复 key 直接崩溃
                        val existing = commentsState.value.associateBy { it.commentId }
                        val merged = (existing + result.comments.associateBy { it.commentId }).values.toList()
                        commentsState.value = merged
                        offsetState.value += result.comments.size
                        if (result.cursor > 0L) beforeState.value = result.cursor
                        fetchUserAvatarsIfNeeded(result.comments)

                        // 同步新加载评论的 liked 状态（本地覆盖表优先）
                        val overrides = persistedLiked.value
                        val newLikedMap = likedState.value.toMutableMap()
                        val newCountMap = likedCountState.value.toMutableMap()
                        for (c in result.comments) {
                            val override = overrides[c.commentId]
                            newLikedMap[c.commentId] = override ?: c.liked
                            newCountMap[c.commentId] = mergeLikeCount(c.likedCount, c.liked, override)
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

    // ─── 预测性返回（与歌词页 LyricsSheet 完全一致）──────────────────────
    // backProgress：0f = 完全可见，1f = 已关闭。手势逐帧驱动根节点 graphicsLayer，
    // 缩小到 92% + 下滑 8% 高度；提交后 tween 补全、取消后回弹。
    var backProgress by remember { mutableFloatStateOf(0f) }
    val backProgressProvider = rememberUpdatedState(backProgress)

    // 进入动画：1f → 0f（同歌词页 spring 曲线）
    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    // 预测性返回（Android 13+）或旧设备普通返回
    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    // —— 主体：Scaffold + TopAppBar ——
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            // 与歌词页相同的预测性返回退出变换（draw-phase 读取，不触发重排版）
            .graphicsLayer {
                val p = backProgressProvider.value
                val scale = lerp(1f, 0.92f, p)
                scaleX = scale
                scaleY = scale
                translationY = lerp(0f, size.height * 0.08f, p)
            },
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
                if (replyToNickname.value != null) {
                    // 回复模式提示条：显示正在回复谁，可取消
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "回复 @${replyToNickname.value}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                replyToNickname.value = null
                                replyToCommentId.value = null
                                commentText.value = ""
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "取消回复",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                                                        text = if (replyToNickname.value != null)
                                                            "回复 @${replyToNickname.value}:"
                                                        else
                                                            "说点什么...",
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
                                            val replyId = replyToCommentId.value
                                            scope.launch {
                                                try {
                                                    val cookieVal = cookie ?: ""
                                                    if (replyId != null) {
                                                        // 回复：走楼中楼，成功后刷新该层回复（若已展开）
                                                        val ok = personalFmApi.replyComment(
                                                            type = 0,
                                                            id = songIdLong,
                                                            commentId = replyId,
                                                            content = content,
                                                            cookie = cookieVal
                                                        ).getOrDefault(false)
                                                        if (ok) {
                                                            commentText.value = ""
                                                            replyToCommentId.value = null
                                                            replyToNickname.value = null
                                                            if (expandedReplies.value.contains(replyId)) {
                                                                repliesState.value = repliesState.value + (replyId to emptyList())
                                                                loadReplies(replyId)
                                                            }
                                                        } else {
                                                            sendError.value = "发送失败，请检查网易云登录状态"
                                                        }
                                                    } else {
                                                        // ⚡ 主评论：本地即时插入 + 滚动定位 + 高亮两次。
                                                        //   官方索引延迟导致刷新拉不到刚发的评论，
                                                        //   体验为"必须重新进入评论页才能看到"
                                                        val newCid = personalFmApi.sendComment(
                                                            type = 0,
                                                            id = songIdLong,
                                                            content = content,
                                                            cookie = cookieVal
                                                        ).getOrDefault(-1L)
                                                        if (newCid >= 0L) {
                                                            commentText.value = ""
                                                            val index = insertMyCommentLocally(newCid, content)
                                                            if (index >= 0) {
                                                                listState.animateScrollToItem(index)
                                                                highlightedCommentId.value = commentsState.value
                                                                    .firstOrNull()?.commentId
                                                            }
                                                        } else {
                                                            sendError.value = "发送失败，请检查网易云登录状态"
                                                        }
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
                                        scope.launch { toggleLike(comment, isLiked) }
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
                                    showReply = isLoggedIn && personalFmApi != null,
                                    onReply = {
                                        replyToCommentId.value = comment.commentId
                                        replyToNickname.value = comment.user.nickname
                                        commentText.value = ""
                                    },
                                    replies = repliesState.value[comment.commentId] ?: emptyList(),
                                    isRepliesExpanded = expandedReplies.value.contains(comment.commentId),
                                    isRepliesLoading = repliesLoading.value.contains(comment.commentId),
                                    repliesError = repliesError.value[comment.commentId],
                                    onToggleReplies = {
                                        scope.launch {
                                            if (expandedReplies.value.contains(comment.commentId)) {
                                                expandedReplies.value = expandedReplies.value - comment.commentId
                                            } else {
                                                expandedReplies.value = expandedReplies.value + comment.commentId
                                                loadReplies(comment.commentId)
                                            }
                                        }
                                    },
                                    hasMoreReplies = repliesHasMore.value[comment.commentId] == true,
                                    onLoadMoreReplies = {
                                        scope.launch { loadReplies(comment.commentId, loadMore = true) }
                                    },
                                    highlight = highlightedCommentId.value == comment.commentId,
                                    onHighlightFinished = { highlightedCommentId.value = null },
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
                                        scope.launch { toggleLike(comment, isLiked) }
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
                                    showReply = isLoggedIn && personalFmApi != null,
                                    onReply = {
                                        replyToCommentId.value = comment.commentId
                                        replyToNickname.value = comment.user.nickname
                                        commentText.value = ""
                                    },
                                    replies = repliesState.value[comment.commentId] ?: emptyList(),
                                    isRepliesExpanded = expandedReplies.value.contains(comment.commentId),
                                    isRepliesLoading = repliesLoading.value.contains(comment.commentId),
                                    repliesError = repliesError.value[comment.commentId],
                                    onToggleReplies = {
                                        scope.launch {
                                            if (expandedReplies.value.contains(comment.commentId)) {
                                                expandedReplies.value = expandedReplies.value - comment.commentId
                                            } else {
                                                expandedReplies.value = expandedReplies.value + comment.commentId
                                                loadReplies(comment.commentId)
                                            }
                                        }
                                    },
                                    hasMoreReplies = repliesHasMore.value[comment.commentId] == true,
                                    onLoadMoreReplies = {
                                        scope.launch { loadReplies(comment.commentId, loadMore = true) }
                                    },
                                    highlight = highlightedCommentId.value == comment.commentId,
                                    onHighlightFinished = { highlightedCommentId.value = null },
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
    showReply: Boolean = false,
    onReply: () -> Unit = {},
    showFloor: Boolean = true,
    replies: List<NeteaseComment> = emptyList(),
    isRepliesExpanded: Boolean = false,
    isRepliesLoading: Boolean = false,
    repliesError: String? = null,
    onToggleReplies: () -> Unit = {},
    hasMoreReplies: Boolean = false,
    onLoadMoreReplies: () -> Unit = {},
    /** 发评成功后的定位提示：背景以主题色脉冲两次 */
    highlight: Boolean = false,
    onHighlightFinished: () -> Unit = {},
    showDivider: Boolean = true
) {
    // ⚡ 高亮脉冲：highlight=true 时背景淡入淡出两个来回，提示"已发出"
    val highlightPulse = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight) {
            repeat(2) {
                highlightPulse.snapTo(0f)
                highlightPulse.animateTo(1f, androidx.compose.animation.core.tween(260))
                highlightPulse.animateTo(0f, androidx.compose.animation.core.tween(260))
            }
            onHighlightFinished()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(primaryColor.copy(alpha = 0.14f * highlightPulse.value))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

                    if (showReply) {
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.TextButton(
                            onClick = onReply,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Reply,
                                contentDescription = "回复",
                                tint = onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                        }
                    }

                    // ⚡ 楼中楼入口：固定显示在「回复」按钮右侧（对齐网易云官方布局），
                    //   无论是否有回复都可见——展开后在此处变为「收起回复」
                    if (showFloor) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleReplies)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isRepliesExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = when {
                                    isRepliesExpanded -> "收起回复"
                                    comment.subReplyCount > 0 -> "查看回复 (${comment.subReplyCount})"
                                    else -> "查看回复"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

        // ── 楼中楼回复列表（展开后显示） ──
        if (showFloor && isRepliesExpanded) {
            if (isRepliesLoading && replies.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "加载回复中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!repliesError.isNullOrBlank() && replies.isEmpty()) {
                    Text(
                        text = repliesError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                    )
                } else if (replies.isEmpty()) {
                    Text(
                        text = "暂无回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                    )
                } else {
                    replies.forEachIndexed { index, reply ->
                        FloorReplyItem(
                            reply = reply,
                            primaryColor = primaryColor,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant
                        )
                        if (index != replies.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // —— 回复分页：服务端单次最多 10 条，提供"加载更多回复" ——
                    if (hasMoreReplies) {
                        Row(
                            modifier = Modifier
                                .padding(start = 72.dp, top = 2.dp, bottom = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isRepliesLoading, onClick = onLoadMoreReplies)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRepliesLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = when {
                                    isRepliesLoading -> "加载中…"
                                    !repliesError.isNullOrBlank() -> "加载失败，点击重试"
                                    else -> "加载更多回复"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
        }

        // 评论之间用细线分割，与头像起始对齐（16dp 外边距 + 40dp 头像 + 16dp 间距）
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
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

// —————————————————————————————————————————————————
// 点赞状态持久化
//   网易云的 /comment/like 是幂等写操作，但服务端返回的 liked 不一定实时同步，
//   重启后会出现"点赞了但状态丢失"。这里用本地覆盖表记录用户自己的点赞态。
// —————————————————————————————————————————————————
private const val LIKES_PREFS_NAME = "netease_comment_likes"

/** 读取某首歌的点赞覆盖表：commentId -> liked */
private fun readLikedOverrides(prefs: android.content.SharedPreferences, key: String): Map<Long, Boolean> {
    val raw = prefs.getString(key, null) ?: return emptyMap()
    return try {
        val obj = JSONObject(raw)
        val out = mutableMapOf<Long, Boolean>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val cid = k.toLongOrNull() ?: continue
            out[cid] = obj.optBoolean(k, false)
        }
        out
    } catch (t: Throwable) {
        Timber.w(t, "读取点赞状态失败")
        emptyMap()
    }
}

/** 写入单条评论的点赞态到本地覆盖表 */
private fun writeLikedOverride(
    prefs: android.content.SharedPreferences,
    key: String,
    commentId: Long,
    liked: Boolean
) {
    try {
        val current = readLikedOverrides(prefs, key).toMutableMap()
        current[commentId] = liked
        val obj = JSONObject()
        current.forEach { (cid, v) -> obj.put(cid.toString(), v) }
        prefs.edit().putString(key, obj.toString()).apply()
    } catch (t: Throwable) {
        Timber.w(t, "保存点赞状态失败")
    }
}

/** 本地覆盖态与服务器态不一致时，修正点赞数展示 */
private fun mergeLikeCount(serverLikedCount: Int, serverLiked: Boolean, override: Boolean?): Int {
    if (override == null || override == serverLiked) return serverLikedCount
    return (serverLikedCount + if (override) 1 else -1).coerceAtLeast(0)
}

// —————————————————————————————————————————————————
// 楼中楼单条回复行（与头像对齐缩进，紧凑样式，含"回复 @某某"提示）
// —————————————————————————————————————————————————
@Composable
private fun FloorReplyItem(
    reply: NeteaseComment,
    primaryColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        val avatarUrl = reply.user.avatarUrl.ifBlank { null }
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reply.user.nickname.firstOrNull()?.uppercase() ?: "U",
                    color = primaryColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user.nickname.ifBlank { "匿名用户" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceVariant
                )
                if (reply.beReplied.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "回复 @${reply.beReplied.first().nickname}",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reply.content.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface
            )
        }
    }
}
