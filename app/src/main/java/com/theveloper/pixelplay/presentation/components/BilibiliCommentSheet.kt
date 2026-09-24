package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.bilibili.BilibiliComment
import com.theveloper.pixelplay.data.bilibili.BilibiliCommentResult
import com.theveloper.pixelplay.data.bilibili.BilibiliReplyInteraction
import com.theveloper.pixelplay.data.bilibili.BilibiliReplyRepliesResult
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BilibiliCommentEntryPoint {
    fun bilibiliSearchApi(): BilibiliSearchApi
    fun bilibiliRepository(): BilibiliRepository
}

/**
 * B 站视频评论页（全屏覆盖）。
 *   - 浏览：热度 / 最新排序、置顶评论、游标分页
 *   - 交互（需登录）：发布评论、回复、点赞、举报
 * 视觉规范与歌曲评论页（CommentSheet）保持一致（MD3）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliCommentSheet(
    bvid: String,
    videoTitle: String,
    upName: String,
    colorScheme: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, BilibiliCommentEntryPoint::class.java)
    }
    val searchApi = entryPoint.bilibiliSearchApi()
    val repository = entryPoint.bilibiliRepository()
    val coroutineScope = rememberCoroutineScope()

    val isLoggedIn = remember { mutableStateOf(repository.isLoggedIn) }
    LaunchedEffect(Unit) {
        repository.isLoggedInFlow.collect { isLoggedIn.value = it }
    }
    val csrf = remember { repository.getCsrf() }
    val cookieHeader = remember { repository.getCookieHeader() }
    val referer = remember(bvid) { "https://www.bilibili.com/video/$bvid" }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val listState: LazyListState = rememberLazyListState()

    // —— 状态 ——
    val commentsState: MutableState<List<BilibiliComment>> = remember { mutableStateOf(emptyList()) }
    val topCommentsState: MutableState<List<BilibiliComment>> = remember { mutableStateOf(emptyList()) }
    val hasMoreState = remember { mutableStateOf(true) }
    val isLoadingState = remember { mutableStateOf(false) }
    val isInitialLoadingState = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val nextOffsetState = remember { mutableStateOf("") }
    val sortMode = remember { mutableIntStateOf(3) } // 3=热度 2=最新
    val resolvedAidState = remember { mutableLongStateOf(0L) } // bvid 解析出的 aid
    val upMidState = remember { mutableLongStateOf(0L) } // 视频 UP 主 uid（来自评论接口 data.upper.mid）

    // —— 交互状态 ——
    val commentText = remember { mutableStateOf("") }           // 底部发布框
    val isPublishing = remember { mutableStateOf(false) }
    val likedRpids = remember { mutableStateMapOf<Long, Boolean>() }   // 已点赞评论
    val hatedRpids = remember { mutableStateMapOf<Long, Boolean>() }   // 已踩评论
    val replyingTo = remember { mutableStateOf<BilibiliComment?>(null) } // 正在回复的评论
    val replyText = remember { mutableStateOf("") }
    val isReplying = remember { mutableStateOf(false) }
    val reportingTarget = remember { mutableStateOf<BilibiliComment?>(null) } // 正在举报的评论
    val isReporting = remember { mutableStateOf(false) }
    // 单条评论「更多」菜单（踩/置顶/举报）
    val moreTarget = remember { mutableStateOf<BilibiliComment?>(null) }
    // 顶栏「更多」菜单（互动设置/评论过滤）
    val showTopMenu = remember { mutableStateOf(false) }
    val showInteractionDialog = remember { mutableStateOf(false) }
    val showFilterDialog = remember { mutableStateOf(false) }

    // —— 楼中楼（子回复）状态 ——
    val expandedSubs = remember { mutableStateMapOf<Long, List<BilibiliComment>>() } // 已展开的子回复列表
    val subLoadingRpids = remember { mutableStateMapOf<Long, Boolean>() }
    val subHasMore = remember { mutableStateMapOf<Long, Boolean>() }
    val subNextPage = remember { mutableStateMapOf<Long, Int>() }

    suspend fun loadFirstPage(aidForLoad: Long, mode: Int) {
        isLoadingState.value = true
        errorState.value = null
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    withTimeout(10000L) {
                        searchApi.getComments(aid = aidForLoad, offset = "", mode = mode, cookie = cookieHeader, isLoggedIn = isLoggedIn.value)
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.w("Bilibili 评论加载超时")
                    BilibiliCommentResult(error = "评论加载超时")
                }
            }
            if (result.error.isNotBlank()) {
                errorState.value = result.error
            } else {
                commentsState.value = result.comments
                topCommentsState.value = result.topComments
                hasMoreState.value = result.hasMore
                nextOffsetState.value = result.nextOffset
                upMidState.longValue = result.upMid
                // 点赞/点踩态对齐：以服务端 reply_control.action 为准（1=已赞 2=已踩）
                likedRpids.clear()
                hatedRpids.clear()
                (result.topComments + result.comments).forEach {
                    when (it.action) {
                        1 -> likedRpids[it.rpid] = true
                        2 -> hatedRpids[it.rpid] = true
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Bilibili 首次加载评论失败")
            errorState.value = t.message ?: "加载失败"
        } finally {
            isLoadingState.value = false
            isInitialLoadingState.value = false
        }
    }

    // —— 首次加载 / 排序切换（bvid → aid → 评论）——
    LaunchedEffect(bvid, sortMode.intValue) {
        isInitialLoadingState.value = true
        // 切换排序时清空楼中楼展开态与点赞/踩本地态
        expandedSubs.clear()
        subLoadingRpids.clear()
        subHasMore.clear()
        subNextPage.clear()
        hatedRpids.clear()
        listState.scrollToItem(0)
        if (bvid.isBlank()) {
            errorState.value = "缺少视频信息，无法加载评论"
            isInitialLoadingState.value = false
            return@LaunchedEffect
        }
        val resolvedAid = withContext(Dispatchers.IO) {
            try {
                withTimeout(8000L) {
                    searchApi.getVideoDetail(aid = 0L, bvid = bvid, cookie = cookieHeader)?.aid ?: 0L
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("Bilibili 解析视频信息超时")
                0L
            }
        }.let { netAid ->
            // ⚡ 网络解析失败（接口 400/风控/超时）时用本地算法解码 BV 号兜底，
            // 保证评论仍能加载（评论接口只需要 oid=aid）
            if (netAid > 0L) netAid else searchApi.bvToAid(bvid)
        }
        if (resolvedAid <= 0L) {
            errorState.value = "无法获取视频信息，无法加载评论"
            isInitialLoadingState.value = false
            return@LaunchedEffect
        }
        resolvedAidState.longValue = resolvedAid
        loadFirstPage(resolvedAid, sortMode.intValue)
    }

    // —— 滚动到底部自动加载 ——
    LaunchedEffect(listState, sortMode.intValue) {
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
                if (nextOffsetState.value.isBlank()) return@collect
                isLoadingState.value = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            withTimeout(10000L) {
                                searchApi.getComments(aid = resolvedAidState.longValue, offset = nextOffsetState.value, mode = sortMode.intValue, cookie = cookieHeader, isLoggedIn = isLoggedIn.value)
                            }
                        } catch (e: TimeoutCancellationException) {
                            Timber.w("Bilibili 加载更多评论超时")
                            BilibiliCommentResult(error = "评论加载超时")
                        }
                    }
                    if (result.error.isNotBlank()) {
                        // 匿名浏览评论接口偶发风控（-412/-449），保留已有列表，提示一次
                        toast("评论加载失败：${result.error}")
                    } else {
                        if (result.comments.isNotEmpty()) {
                            commentsState.value = commentsState.value + result.comments
                        }
                        nextOffsetState.value = result.nextOffset
                        hasMoreState.value = result.hasMore
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Bilibili 加载更多评论失败")
                } finally {
                    isLoadingState.value = false
                }
            }
    }

    // —— 楼中楼（子回复）加载 / 展开 / 收起 ——
    // ⚡ 注意：局部函数必须先声明后使用（Kotlin 局部函数不允许前向引用），
    // 因此 loadSubReplies 必须在 toggleSubReplies 之前声明。
    fun loadSubReplies(root: Long, page: Int) {
        if (subLoadingRpids[root] == true) return
        subLoadingRpids[root] = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    withTimeout(10000L) {
                        searchApi.getReplyReplies(
                            oid = resolvedAidState.longValue,
                            root = root,
                            page = page,
                            csrf = csrf,
                            cookie = cookieHeader,
                            isLoggedIn = isLoggedIn.value
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.w("Bilibili 子回复加载超时")
                    BilibiliReplyRepliesResult(error = "子回复加载超时")
                }
            }
            subLoadingRpids[root] = false
            if (result.error.isNotBlank()) {
                toast("子回复加载失败：${result.error}")
                return@launch
            }
            // 按 rpid 去重合并，避免首屏内嵌子回复与接口结果重复
            val existing = expandedSubs[root].orEmpty().associateBy { it.rpid }
            val merged = (existing.values + result.replies).distinctBy { it.rpid }
            expandedSubs[root] = merged
            subHasMore[root] = result.hasMore
            subNextPage[root] = result.nextPage
        }
    }

    fun toggleSubReplies(comment: BilibiliComment) {
        if (expandedSubs.containsKey(comment.rpid)) {
            expandedSubs.remove(comment.rpid)
            return
        }
        if (subLoadingRpids[comment.rpid] == true) return
        // 首次展开：先放入主列表自带的首屏子回复（对齐 PiliPlus item.replies），再拉取完整列表
        expandedSubs[comment.rpid] = comment.subReplies
        subNextPage[comment.rpid] = 1
        loadSubReplies(comment.rpid, page = 1)
    }

    // —— 踩 / 取消踩 ——
    fun toggleHate(comment: BilibiliComment) {
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            return
        }
        val hated = hatedRpids[comment.rpid] == true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.hateReply(
                    oid = resolvedAidState.longValue,
                    rpid = comment.rpid,
                    action = if (hated) 0 else 1,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            if (result.success) {
                hatedRpids[comment.rpid] = !hated
            } else {
                toast(result.message.ifBlank { "操作失败" })
            }
        }
    }

    // —— 置顶 / 取消置顶（仅视频 UP 主，对齐 PiliPlus onToggleTop）——
    fun toggleTop(comment: BilibiliComment) {
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            return
        }
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.toggleTopReply(
                    oid = resolvedAidState.longValue,
                    rpid = comment.rpid,
                    isUpTop = comment.isUpTop,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            if (result.success) {
                toast(if (comment.isUpTop) "已取消置顶" else "置顶成功")
                loadFirstPage(resolvedAidState.longValue, sortMode.intValue)
            } else {
                toast(result.message.ifBlank { "操作失败" })
            }
        }
    }

    // —— 评论区互动设置（仅 UP 主，对齐 PiliPlus 互动设置面板）——
    fun applyReplySubject(action: Int, successMsg: String) {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.setReplySubject(
                    oid = resolvedAidState.longValue,
                    action = action,
                    csrf = csrf ?: "",
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            toast(if (result.success) successMsg else result.message.ifBlank { "操作失败" })
        }
    }

    // —— 评论过滤设置（对齐 PiliPlus banWordForReply / antiGoodsReply）——
    fun applyFilterSettings(filterEnabled: Boolean, antiGoods: Boolean, banWords: String) {
        repository.commentFilterEnabled = filterEnabled
        repository.antiGoodsFilterEnabled = antiGoods
        repository.commentBanWords = banWords.trim()
        toast("已保存，重新加载评论后生效")
        coroutineScope.launch {
            loadFirstPage(resolvedAidState.longValue, sortMode.intValue)
        }
    }

    // —— 发布主评论 ——
    fun publishComment() {
        val message = commentText.value.trim()
        if (message.isBlank()) return
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            return
        }
        isPublishing.value = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.addReply(
                    oid = resolvedAidState.longValue,
                    root = 0L,
                    parent = 0L,
                    message = message,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            isPublishing.value = false
            if (result.success) {
                commentText.value = ""
                toast("评论发布成功")
                // 重新加载第一页，让新评论可见
                loadFirstPage(resolvedAidState.longValue, sortMode.intValue)
            } else {
                toast(result.message.ifBlank { "评论发布失败" })
            }
        }
    }

    // —— 点赞 / 取消点赞 ——
    fun toggleLike(comment: BilibiliComment) {
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            return
        }
        val liked = likedRpids[comment.rpid] == true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.likeReply(
                    oid = resolvedAidState.longValue,
                    rpid = comment.rpid,
                    action = if (liked) 0 else 1,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            if (result.success) {
                likedRpids[comment.rpid] = !liked
            } else {
                toast(result.message.ifBlank { "点赞失败" })
            }
        }
    }

    // —— 回复评论 ——
    fun sendReply() {
        val target = replyingTo.value ?: return
        val message = replyText.value.trim()
        if (message.isBlank()) return
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            replyingTo.value = null
            return
        }
        isReplying.value = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.addReply(
                    oid = resolvedAidState.longValue,
                    root = target.rpid,
                    parent = target.rpid,
                    message = message,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            isReplying.value = false
            replyingTo.value = null
            replyText.value = ""
            toast(if (result.success) "回复成功" else result.message.ifBlank { "回复失败" })
        }
    }

    // —— 举报评论 ——
    fun sendReport(reason: Int) {
        val target = reportingTarget.value ?: return
        if (!isLoggedIn.value || csrf.isNullOrBlank()) {
            toast("请先登录 B 站账号")
            reportingTarget.value = null
            return
        }
        isReporting.value = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                searchApi.reportReply(
                    oid = resolvedAidState.longValue,
                    rpid = target.rpid,
                    reason = reason,
                    csrf = csrf,
                    referer = referer,
                    cookie = cookieHeader
                )
            }
            isReporting.value = false
            reportingTarget.value = null
            toast(if (result.success) "举报成功，感谢你的反馈" else result.message.ifBlank { "举报失败" })
        }
    }

    BackHandler {
        when {
            reportingTarget.value != null -> reportingTarget.value = null
            replyingTo.value != null -> replyingTo.value = null
            moreTarget.value != null -> moreTarget.value = null
            else -> onBackClick()
        }
    }

    // —— 发射一条主评论 + 其展开的楼中楼子回复（对齐 PiliPlus 列表结构）——
    fun LazyListScope.emitCommentBlock(comment: BilibiliComment, keyPrefix: String) {
        item(key = "$keyPrefix${comment.rpid}") {
            BilibiliCommentRow(
                comment = comment,
                colorScheme = colorScheme,
                isLoggedIn = isLoggedIn.value,
                isLiked = likedRpids[comment.rpid] == true,
                isHated = hatedRpids[comment.rpid] == true,
                canManage = isLoggedIn.value && upMidState.longValue > 0L && repository.userId == upMidState.longValue,
                onLike = { toggleLike(comment) },
                onReply = {
                    if (!isLoggedIn.value) toast("请先登录 B 站账号") else {
                        replyingTo.value = comment
                        replyText.value = ""
                    }
                },
                onMore = { moreTarget.value = comment },
                onToggleSubs = { toggleSubReplies(comment) }
            )
        }
        if (expandedSubs.containsKey(comment.rpid)) {
            val subs = expandedSubs[comment.rpid].orEmpty()
            subs.forEach { sub ->
                item(key = "${keyPrefix}sub_${comment.rpid}_${sub.rpid}") {
                    BilibiliSubReplyRow(
                        comment = sub,
                        colorScheme = colorScheme,
                        isLoggedIn = isLoggedIn.value,
                        isLiked = likedRpids[sub.rpid] == true,
                        onLike = { toggleLike(sub) },
                        onMore = { moreTarget.value = sub }
                    )
                }
            }
            if (subLoadingRpids[comment.rpid] == true) {
                item(key = "${keyPrefix}sub_loading_${comment.rpid}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "加载中…",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (subHasMore[comment.rpid] == true) {
                item(key = "${keyPrefix}sub_more_${comment.rpid}") {
                    Text(
                        text = "展开更多回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 48.dp, top = 6.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                loadSubReplies(comment.rpid, subNextPage[comment.rpid] ?: 1)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

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
                            text = listOfNotNull(upName.ifBlank { null }, videoTitle.ifBlank { null })
                                .joinToString(" · "),
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
                actions = {
                    // —— 顶栏「更多」：互动设置（UP主）+ 评论过滤（对齐 PiliPlus 互动设置/评论设置）——
                    Box {
                        IconButton(onClick = { showTopMenu.value = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多",
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu.value,
                            onDismissRequest = { showTopMenu.value = false }
                        ) {
                            if (isLoggedIn.value && upMidState.longValue > 0L && repository.userId == upMidState.longValue) {
                                DropdownMenuItem(
                                    text = { Text("互动设置") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showTopMenu.value = false
                                        showInteractionDialog.value = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("评论过滤") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showTopMenu.value = false
                                    showFilterDialog.value = true
                                }
                            )
                        }
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
            // —— 底部发布评论栏（对齐网易云 CommentSheet：圆角输入框 + 发送按钮 / 未登录提示）——
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
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
                        if (isLoggedIn.value) {
                            Box(
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
                                    Box(modifier = Modifier.weight(1f)) {
                                        val tfStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = colorScheme.onSurface
                                        )
                                        BasicTextField(
                                            value = commentText.value,
                                            onValueChange = { commentText.value = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            textStyle = tfStyle,
                                            singleLine = false,
                                            maxLines = 3,
                                            cursorBrush = SolidColor(colorScheme.primary),
                                            decorationBox = { innerTextField ->
                                                if (commentText.value.isEmpty()) {
                                                    Text(
                                                        text = "发一条友善的评论",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = colorScheme.primary
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { publishComment() },
                                        enabled = commentText.value.isNotBlank() && !isPublishing.value
                                    ) {
                                        if (isPublishing.value) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Send,
                                                contentDescription = "发布评论",
                                                tint = if (commentText.value.isNotBlank()) colorScheme.primary else colorScheme.primary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "请先登录 B 站账号后发表评论",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // —— 排序切换：热度 / 最新 ——
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val options = listOf(3 to "热度", 2 to "最新")
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = sortMode.intValue == mode,
                        onClick = { sortMode.intValue = mode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label)
                    }
                }
            }

            when {
                // 1. 加载中
                isInitialLoadingState.value && commentsState.value.isEmpty() && topCommentsState.value.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
                // 2. 错误
                !errorState.value.isNullOrBlank() && commentsState.value.isEmpty() && topCommentsState.value.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorState.value ?: "加载失败",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                errorState.value = null
                                coroutineScope.launch {
                                    loadFirstPage(resolvedAidState.longValue, sortMode.intValue)
                                }
                            }
                        ) {
                            Text("重试")
                        }
                    }
                }
                // 3. 有内容
                else -> {
                    val hasTop = topCommentsState.value.isNotEmpty()
                    val hasRegular = commentsState.value.isNotEmpty()

                    if (!hasTop && !hasRegular) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 4.dp,
                                bottom = 24.dp + WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding()
                            )
                        ) {
                            // —— 置顶评论 ——
                            if (hasTop) {
                                item(key = "top_header") {
                                    Text(
                                        text = "置顶评论",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                topCommentsState.value.forEach { comment ->
                                    emitCommentBlock(comment, "top_")
                                }
                            }

                            // —— 全部评论 ——
                            if (hasRegular) {
                                item(key = "regular_header") {
                                    Text(
                                        text = "全部评论 (${commentsState.value.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.primary,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = if (hasTop) 12.dp else 8.dp,
                                            bottom = 4.dp
                                        )
                                    )
                                }
                                commentsState.value.forEach { comment ->
                                    emitCommentBlock(comment, "c_")
                                }
                            }

                            // —— 加载中 / 到底 ——
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

    // —— 回复对话框 ——
    replyingTo.value?.let { target ->
        AlertDialog(
            onDismissRequest = { replyingTo.value = null },
            title = { Text("回复 ${target.nickname.ifBlank { "用户" }}") },
            text = {
                OutlinedTextField(
                    value = replyText.value,
                    onValueChange = { replyText.value = it },
                    placeholder = { Text("写下你的回复…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { sendReply() },
                    enabled = replyText.value.isNotBlank() && !isReplying.value
                ) {
                    if (isReplying.value) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("发送")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { replyingTo.value = null }) { Text("取消") }
            }
        )
    }

    // —— 举报对话框 ——
    reportingTarget.value?.let { target ->
        val reasons = listOf(
            1 to "违法违禁",
            2 to "色情低俗",
            5 to "人身攻击",
            7 to "垃圾广告",
            8 to "引战",
            0 to "其他"
        )
        AlertDialog(
            onDismissRequest = { reportingTarget.value = null },
            title = { Text("举报评论") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "来自 ${target.nickname.ifBlank { "用户" }}：${target.message.take(40)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    reasons.forEach { (code, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { sendReport(code) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { reportingTarget.value = null }) { Text("取消") }
            }
        )
    }

    // —— 单条评论「更多」菜单（踩/置顶/举报，对齐 PiliPlus 评论长按操作）——
    moreTarget.value?.let { target ->
        AlertDialog(
            onDismissRequest = { moreTarget.value = null },
            title = { Text("${target.nickname.ifBlank { "用户" }} 的评论") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = target.message.take(60),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 踩 / 取消踩
                    val hated = hatedRpids[target.rpid] == true
                    Text(
                        text = if (hated) "取消踩" else "踩一下",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hated) colorScheme.primary else colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = isLoggedIn.value) { moreTarget.value = null; toggleHate(target) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    // 置顶 / 取消置顶（仅 UP 主可见）
                    if (isLoggedIn.value && upMidState.longValue > 0L && repository.userId == upMidState.longValue) {
                        Text(
                            text = if (target.isUpTop) "取消置顶" else "置顶评论",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { moreTarget.value = null; toggleTop(target) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    // 举报
                    Text(
                        text = "举报",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = isLoggedIn.value) { moreTarget.value = null; reportingTarget.value = target }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moreTarget.value = null }) { Text("取消") }
            }
        )
    }

    // —— 互动设置对话框（仅 UP 主，对齐 PiliPlus author_panel 互动设置面板）——
    if (showInteractionDialog.value) {
        BilibiliInteractionDialog(
            searchApi = searchApi,
            oid = resolvedAidState.longValue,
            cookie = cookieHeader,
            onDismiss = { showInteractionDialog.value = false },
            onApply = { action, msg -> applyReplySubject(action, msg) }
        )
    }

    // —— 评论过滤对话框（对齐 PiliPlus banWordForReply / antiGoodsReply）——
    if (showFilterDialog.value) {
        BilibiliFilterDialog(
            initialFilterEnabled = repository.commentFilterEnabled,
            initialAntiGoods = repository.antiGoodsFilterEnabled,
            initialBanWords = repository.commentBanWords,
            onDismiss = { showFilterDialog.value = false },
            onApply = { f, a, w ->
                showFilterDialog.value = false
                applyFilterSettings(f, a, w)
            }
        )
    }
}

// —————————————————————————————————————————————————
// 评论行：头像 + 昵称/Lv + 正文 + 时间·回复·点赞·举报
// —————————————————————————————————————————————————
@Composable
private fun BilibiliCommentRow(
    comment: BilibiliComment,
    colorScheme: androidx.compose.material3.ColorScheme,
    isLoggedIn: Boolean,
    isLiked: Boolean,
    isHated: Boolean,
    canManage: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onMore: () -> Unit,
    onToggleSubs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // —— 头像 ——
            if (comment.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(comment.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.nickname.firstOrNull()?.uppercase() ?: "U",
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // —— 昵称 + Lv + 正文 + 底部 ——
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.nickname.ifBlank { "匿名用户" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        // 大会员昵称用粉色调（对齐 PiliPlus colorScheme.vipColor）
                        color = if (comment.vipType == 2) vipNickColor(colorScheme) else colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // UP 主本人评论徽章（对齐 PiliPlus 视频评论 UP 标识）
                    if (comment.isUp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UP",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    // UP 置顶评论徽章（对齐 PiliPlus isUpTop）
                    if (comment.isUpTop) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UP置顶",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    if (comment.level > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lv${comment.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 正文（对齐 PiliPlus：表情内联渲染）
                BilibiliRichText(
                    message = comment.message.ifBlank { " " },
                    emotes = comment.emotes,
                    colorScheme = colorScheme
                )

                // 评论图片（对齐 PiliPlus content.pictures）
                if (comment.pictures.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        comment.pictures.take(3).forEach { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 110.dp, height = 88.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorScheme.surfaceContainerHighest)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // —— 底部：时间 · 回复数(可展开) · 操作（回复/点赞/更多）——
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatCommentTime(comment.ctime) +
                            if (comment.location.isNotBlank()) " • ${comment.location}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (comment.replyCount > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${comment.replyCount}条回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onToggleSubs)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // 回复
                    IconButton(onClick = onReply, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Reply,
                            contentDescription = "回复",
                            tint = colorScheme.onSurfaceVariant.copy(alpha = if (isLoggedIn) 0.8f else 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 点赞（网易云风格：♥/♡ 文本 + 数字，圆角背景块，点赞后红色高亮）
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLiked) colorScheme.errorContainer else Color.Transparent)
                            .clickable(onClick = onLike)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLiked) "♥" else "♡",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isLiked) colorScheme.error else colorScheme.onSurfaceVariant.copy(alpha = if (isLoggedIn) 0.9f else 0.5f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatCompactCount(comment.like.coerceAtLeast(0) + if (isLiked) 1 else 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLiked) colorScheme.error else colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 更多（踩/置顶/举报，对齐 PiliPlus 评论长按操作）
                    IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            tint = colorScheme.onSurfaceVariant.copy(alpha = if (isLoggedIn) 0.8f else 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 评论之间用细线分割（16dp 外边距 + 40dp 头像 + 16dp 间距）
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// —————————————————————————————————————————————————
// 楼中楼子回复行：缩进 + 小头像 + 表情正文 + 点赞/更多
// —————————————————————————————————————————————————
@Composable
private fun BilibiliSubReplyRow(
    comment: BilibiliComment,
    colorScheme: androidx.compose.material3.ColorScheme,
    isLoggedIn: Boolean,
    isLiked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (comment.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(comment.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.nickname.firstOrNull()?.uppercase() ?: "U",
                    color = colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.nickname.ifBlank { "匿名用户" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    // 大会员昵称用粉色调（对齐 PiliPlus colorScheme.vipColor）
                    color = if (comment.vipType == 2) vipNickColor(colorScheme) else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (comment.isUp) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                if (comment.level > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lv${comment.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            BilibiliRichText(
                message = comment.message.ifBlank { " " },
                emotes = comment.emotes,
                colorScheme = colorScheme
            )
            if (comment.pictures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    comment.pictures.take(3).forEach { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 90.dp, height = 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCommentTime(comment.ctime) +
                        if (comment.location.isNotBlank()) " • ${comment.location}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onMore, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = if (isLoggedIn) 0.8f else 0.35f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLiked) colorScheme.errorContainer else Color.Transparent)
                        .clickable(onClick = onLike)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLiked) "♥" else "♡",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isLiked) colorScheme.error else colorScheme.onSurfaceVariant.copy(alpha = if (isLoggedIn) 0.9f else 0.5f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCompactCount(comment.like.coerceAtLeast(0) + if (isLiked) 1 else 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLiked) colorScheme.error else colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// —————————————————————————————————————————————————
// 富文本：消息 + 表情内联渲染（对齐 PiliPlus _buildMessage 的表情处理）
// 用 FlowRow 流式布局把文本段与表情图片混排，避免依赖新版 Compose 的
// InlineTextContent 内联 API（1.12-alpha 已移除）
// —————————————————————————————————————————————————
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BilibiliRichText(
    message: String,
    emotes: Map<String, String>,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    if (emotes.isEmpty()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            lineHeight = 22.sp
        )
        return
    }
    // 按表情文本长度降序匹配，避免 [表情A] 是 [表情AB] 前缀时误匹配
    val tokens = mutableListOf<Pair<String, String?>>() // 文本, 表情URL(非表情为 null)
    var cursor = 0
    val emoteKeys = emotes.keys.sortedByDescending { it.length }
    while (cursor < message.length) {
        var matched = false
        for (key in emoteKeys) {
            if (message.startsWith(key, startIndex = cursor)) {
                tokens.add(key to (emotes[key] ?: ""))
                cursor += key.length
                matched = true
                break
            }
        }
        if (!matched) {
            val next = emoteKeys.map { message.indexOf(it, cursor).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } }
                .minOrNull() ?: Int.MAX_VALUE
            val end = if (next == Int.MAX_VALUE) message.length else next
            if (end > cursor) tokens.add(message.substring(cursor, end) to null)
            cursor = end
        }
    }
    if (tokens.none { it.second != null }) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            lineHeight = 22.sp
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tokens.forEach { (text, url) ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = text,
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 2.dp)
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// —————————————————————————————————————————————————
// 互动设置对话框（仅 UP 主，对齐 PiliPlus author_panel 互动设置面板）
// 打开时先拉取互动状态，再按 can_modify 展示可操作项
// —————————————————————————————————————————————————
@Composable
private fun BilibiliInteractionDialog(
    searchApi: BilibiliSearchApi,
    oid: Long,
    cookie: String,
    onDismiss: () -> Unit,
    onApply: (action: Int, successMsg: String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val interaction = remember { mutableStateOf<BilibiliReplyInteraction?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(oid) {
        isLoading.value = true
        val result = withContext(Dispatchers.IO) {
            searchApi.getReplyInteraction(oid = oid, cookie = cookie)
        }
        if (result.replyCanModify || result.selectionCanModify) {
            interaction.value = result
        } else {
            errorMsg.value = "当前账号无此评论区管理权限"
        }
        isLoading.value = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("互动设置") },
        text = {
            when {
                isLoading.value -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                errorMsg.value != null -> Text(
                    text = errorMsg.value ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> {
                    val data = interaction.value
                    if (data == null) {
                        Text("暂无可用设置")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // 评论精选开关
                            if (data.selectionCanModify) {
                                Text(
                                    text = if (data.selectionEnabled) "停止评论精选" else "开启评论精选",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onApply(if (data.selectionEnabled) 2 else 1, if (data.selectionEnabled) "已停止评论精选" else "已开启评论精选")
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                            // 评论开关
                            if (data.replyCanModify) {
                                Text(
                                    text = if (data.replyEnabled) "关闭评论" else "恢复评论",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onApply(if (data.replyEnabled) 4 else 3, if (data.replyEnabled) "已关闭评论" else "已恢复评论")
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// —————————————————————————————————————————————————
// 评论过滤对话框（对齐 PiliPlus banWordForReply / antiGoodsReply 设置）
// —————————————————————————————————————————————————
@Composable
private fun BilibiliFilterDialog(
    initialFilterEnabled: Boolean,
    initialAntiGoods: Boolean,
    initialBanWords: String,
    onDismiss: () -> Unit,
    onApply: (filterEnabled: Boolean, antiGoods: Boolean, banWords: String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val filterEnabled = remember { mutableStateOf(initialFilterEnabled) }
    val antiGoods = remember { mutableStateOf(initialAntiGoods) }
    val banWords = remember { mutableStateOf(initialBanWords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评论过滤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 关键词过滤
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { filterEnabled.value = !filterEnabled.value }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = filterEnabled.value,
                        onCheckedChange = { filterEnabled.value = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "关键词过滤（隐藏含指定关键词的评论）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = banWords.value,
                    onValueChange = { banWords.value = it },
                    enabled = filterEnabled.value,
                    placeholder = { Text("支持正则表达式，如：广告|代购") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 广告评论过滤
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { antiGoods.value = !antiGoods.value }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = antiGoods.value,
                        onCheckedChange = { antiGoods.value = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "过滤广告评论（高能 B 站商品链接等）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(filterEnabled.value, antiGoods.value, banWords.value) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// 大会员昵称色（对齐 PiliPlus colorScheme.vipColor：亮色 0xFFFF6699 / 暗色 0xFFD44E7D）
private fun vipNickColor(colorScheme: androidx.compose.material3.ColorScheme): Color =
    if (colorScheme.surface.luminance() > 0.5f) Color(0xFFFF6699) else Color(0xFFD44E7D)

private fun formatCommentTime(ctime: Long): String {
    if (ctime <= 0L) return ""
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ctime * 1000L))
    } catch (t: Throwable) {
        ""
    }
}

// 轻量数字格式化（>1000 显示 1.2k 等）
private fun formatCompactCount(count: Int): String {
    return when {
        count < 1000 -> count.toString()
        count < 10000 -> String.format("%.1fk", count / 1000f)
        else -> String.format("%.1fw", count / 10000f)
    }
}
