package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.lx.LxSearchApi
import com.theveloper.pixelplay.data.lx.NeteaseComment
import com.theveloper.pixelplay.data.lx.NeteaseCommentResult
import com.theveloper.pixelplay.data.lx.NeteaseUserDetail
import dagger.hilt.android.EntryPointAccessors
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    songId: String,
    songTitle: String,
    songArtist: String,
    api: LxSearchApi,
    colorScheme: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val pageSize = 20

    // 整个评论数据（普通评论）。MutableState 以便于原地追加。
    val commentsState: MutableState<List<NeteaseComment>> = remember { mutableStateOf(emptyList()) }
    val hotCommentsState: MutableState<List<NeteaseComment>> = remember { mutableStateOf(emptyList()) }
    val hasMoreState = remember { mutableStateOf(true) }
    val isLoadingState = remember { mutableStateOf(false) }
    val isInitialLoadingState = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<String?>(null) }

    // 当前分页游标
    val offsetState = remember { mutableStateOf(0) }
    val beforeState = remember { mutableStateOf<Long?>(null) }

    // 用户头像缓存 (userId -> avatarUrl)
    val userAvatarCache = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // 本地函数：从 /user/detail 拉取头像（评论返回的 avatarUrl 有时是占位图，
    // 这里尝试再抓一次更精确的头像）
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

    // 首次加载
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
            // hasMore 判断：API 返回 true 或者已加载数量 >= limit，说明还有下一页
            val serverHasMore = result.hasMore
            val heuristicHasMore = result.comments.size >= pageSize
            hasMoreState.value = serverHasMore || heuristicHasMore
            offsetState.value = result.comments.size
            beforeState.value = if (result.comments.isNotEmpty()) result.cursor else null

            // 并行拉取头像
            val firstPageUsers = (result.hotComments + result.comments)
            fetchUserAvatarsIfNeeded(firstPageUsers)
        } catch (t: Throwable) {
            Timber.e(t, "首次加载评论失败")
            errorState.value = t.message ?: "加载失败"
        } finally {
            isLoadingState.value = false
            isInitialLoadingState.value = false
        }
    }

    // 滚动到底部自动加载下一页（基于 offset 累加）
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            // 显示过的最后一项索引
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // 离底部不到 5 项时，触发加载更多；并且必须至少已经显示过内容
            lastVisibleIndex >= 0 && totalItems > 0 && lastVisibleIndex >= totalItems - 5
        }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (!nearBottom) return@collect
                if (isLoadingState.value) return@collect
                if (!hasMoreState.value) return@collect
                // 双重保险：offset 不能超过已有数据太多
                if (offsetState.value < commentsState.value.size && commentsState.value.isNotEmpty()) {
                    // 正常情况下 offset 与列表长度一致，避免与 hot/hot+comments 对齐问题
                    offsetState.value = commentsState.value.size
                }

                isLoadingState.value = true
                try {
                    // 超过 5000 条后改用 before 分页
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
                    }
                    // 真正的停止条件：服务端 hasMore=false 或者返回数量 < limit
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

    val containerColor = colorScheme.surface
    val contentColor = colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "评论",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1
                        )
                        Text(
                            text = "$songArtist · $songTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.65f),
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
                            tint = contentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = contentColor,
                    actionIconContentColor = contentColor
                )
            )

            when {
                isInitialLoadingState.value && commentsState.value.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }

                !errorState.value.isNullOrBlank() && commentsState.value.isEmpty() && hotCommentsState.value.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorState.value ?: "加载失败",
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    val hasHot = hotCommentsState.value.isNotEmpty()
                    val hasRegular = commentsState.value.isNotEmpty()

                    if (!hasHot && !hasRegular) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无评论，快来抢沙发~",
                                color = contentColor.copy(alpha = 0.55f)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = 24.dp + WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding()
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (hasHot) {
                                item {
                                    Text(
                                        text = "精彩评论 (${hotCommentsState.value.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                items(
                                    items = hotCommentsState.value,
                                    key = { "hot_${it.commentId}" }
                                ) { comment ->
                                    CommentRow(
                                        comment = comment,
                                        avatarOverride = userAvatarCache.value[comment.user.userId],
                                        accentColor = colorScheme.primary,
                                        contentColor = contentColor,
                                        containerColor = colorScheme.surfaceColorAtElevation(1.dp)
                                    )
                                }
                            }

                            if (hasRegular) {
                                item {
                                    Text(
                                        text = "最新评论",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor,
                                        modifier = Modifier.padding(
                                            start = 8.dp,
                                            end = 8.dp,
                                            top = 8.dp,
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
                                        accentColor = colorScheme.primary,
                                        contentColor = contentColor,
                                        containerColor = colorScheme.surfaceColorAtElevation(1.dp)
                                    )
                                }
                            }

                            if (isLoadingState.value && !isInitialLoadingState.value) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "正在加载更多…",
                                            color = contentColor.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (!hasMoreState.value && commentsState.value.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "— 已经到底啦 —",
                                            color = contentColor.copy(alpha = 0.45f),
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
}

@Composable
private fun CommentRow(
    comment: NeteaseComment,
    avatarOverride: String?,
    accentColor: Color,
    contentColor: Color,
    containerColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 头像
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
                    .background(containerColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.user.nickname.firstOrNull()?.uppercase()?.toString()
                        ?: "U",
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = comment.user.nickname.ifBlank { "匿名用户" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comment.likedCount > 0) {
                    Text(
                        text = "♥ ${comment.likedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = comment.content.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = 20.sp
            )

            if (comment.timeStr.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.45f)
                )
            }
        }
    }
}
