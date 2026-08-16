package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.bilibili.BilibiliFavoriteFolder
import com.theveloper.pixelplay.data.bilibili.BilibiliFavoriteVideo
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BilibiliFavoritesEntryPoint {
    fun bilibiliSearchApi(): BilibiliSearchApi
    fun bilibiliRepository(): BilibiliRepository
}

/**
 * B 站收藏同步页（全屏覆盖）。
 * 登录后展示用户创建的收藏夹 → 点击进入收藏夹内视频列表 → 点击视频直接播放（复用 B 站解析链路）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliFavoritesScreen(
    playerViewModel: PlayerViewModel?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, BilibiliFavoritesEntryPoint::class.java)
    }
    val searchApi = entryPoint.bilibiliSearchApi()
    val repository = entryPoint.bilibiliRepository()

    val coroutineScope = rememberCoroutineScope()

    var folders by remember { mutableStateOf<List<BilibiliFavoriteFolder>>(emptyList()) }
    var videos by remember { mutableStateOf<List<BilibiliFavoriteVideo>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<BilibiliFavoriteFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var playingAid by remember { mutableLongStateOf(0L) }

    // 收藏夹内视频分页状态（滚动到底自动加载更多）
    var videoPage by remember { mutableIntStateOf(1) }
    var hasMoreVideos by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val videoListState: LazyListState = rememberLazyListState()

    // 会话 cookie：收藏接口需要携带，否则 B 站返回 -101（未登录）
    val cookieHeader = repository.getCookieHeader()
    val hasSession = cookieHeader.isNotBlank() &&
        (cookieHeader.contains("SESSDATA") || cookieHeader.contains("bili_jct"))
    val csrf = repository.getCsrf()
    val uid = repository.userId

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // 首次进入 / 未登录时加载收藏夹
    LaunchedEffect(Unit) {
        isLoading = true
        errorText = null
        if (!hasSession || uid <= 0L) {
            errorText = "请先在「设置 → 账号」中登录 B 站账号"
            isLoading = false
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            searchApi.getFavoriteFolders(uid, csrf ?: "", cookie = cookieHeader)
        }
        folders = result
        if (result.isEmpty()) errorText = "暂无收藏夹（接口返回为空，请确认已登录 B 站账号）"
        isLoading = false
    }

    // 进入收藏夹时加载视频（第一页）
    LaunchedEffect(selectedFolder) {
        val folder = selectedFolder ?: return@LaunchedEffect
        if (folder.id <= 0L) return@LaunchedEffect
        isLoading = true
        errorText = null
        isLoadingMore = false
        videoListState.scrollToItem(0)
        val result = withContext(Dispatchers.IO) {
            searchApi.getFavoriteResourcesPage(folder.id, pn = 1, csrf = csrf ?: "", cookie = cookieHeader)
        }
        videos = result.videos
        videoPage = 1
        hasMoreVideos = result.hasMore
        if (result.videos.isEmpty()) errorText = "收藏夹是空的"
        isLoading = false
    }

    // 收藏夹视频列表：滚动到底部自动加载下一页
    LaunchedEffect(selectedFolder, videoListState) {
        snapshotFlow {
            val layoutInfo = videoListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= 0 && totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (!nearBottom) return@collect
                if (isLoadingMore || !hasMoreVideos || isLoading) return@collect
                val folder = selectedFolder ?: return@collect
                isLoadingMore = true
                val nextPage = videoPage + 1
                val result = withContext(Dispatchers.IO) {
                    searchApi.getFavoriteResourcesPage(folder.id, pn = nextPage, csrf = csrf ?: "", cookie = cookieHeader)
                }
                if (result.videos.isNotEmpty()) {
                    val existingIds = videos.map { it.aid }.toSet()
                    videos = videos + result.videos.filter { it.aid !in existingIds }
                }
                videoPage = nextPage
                hasMoreVideos = result.hasMore
                isLoadingMore = false
            }
    }

    /**
     * 播放收藏夹：把当前收藏夹的【全部视频】作为播放列表入队，从点击的视频开始播放。
     * 使用 bilibili:// 协议延迟解析播放链接（播放到哪首解析哪首），
     * 避免收藏较多时串行预解析所有 URL 导致长时间"加载视频"卡住。
     */
    fun playVideo(video: BilibiliFavoriteVideo) {
        if (playerViewModel == null) {
            toast("播放器不可用")
            return
        }
        playingAid = video.aid
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 1) 拉取当前收藏夹全部视频 meta（多页直到不足一页）
                val folder = selectedFolder
                val allVideos = mutableListOf<BilibiliFavoriteVideo>()
                if (folder != null && folder.id > 0L) {
                    var pn = 1
                    while (true) {
                        val pageVideos = searchApi.getFavoriteResources(
                            folder.id, pn = pn, csrf = csrf ?: "", cookie = cookieHeader
                        )
                        if (pageVideos.isEmpty()) break
                        allVideos.addAll(pageVideos)
                        if (pageVideos.size < 20) break
                        pn++
                    }
                } else {
                    allVideos.addAll(videos)
                }
                if (allVideos.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        playingAid = 0L
                        toast("收藏夹为空，无法播放")
                    }
                    return@launch
                }

                // 2) 构造 Song：contentUriString 用 bilibili://{bvid}/{cid}/{aid}，
                //    播放引擎按需解析真实播放链接（无 bvid / 无 cid 的合集条目跳过）
                val songs = mutableListOf<Song>()
                var startSong: Song? = null
                for (v in allVideos) {
                    val bvid = v.bvid
                    val cid = v.cid
                    if (bvid.isBlank() || !bvid.startsWith("BV", ignoreCase = true) || cid <= 0L) continue
                    val song = Song(
                        id = "bilibili_fav_${v.aid}",
                        title = v.title.ifBlank { "B 站视频" },
                        artist = v.upName.ifBlank { "Bilibili" },
                        artistId = 0L,
                        album = "",
                        albumId = 0L,
                        path = "",
                        contentUriString = "bilibili://$bvid/$cid/${v.aid}",
                        albumArtUriString = v.cover.takeIf { it.isNotBlank() },
                        duration = v.duration,
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null,
                        neteaseId = null,
                        bilibiliBvid = bvid
                    )
                    if (v.aid == video.aid) startSong = song
                    songs.add(song)
                }

                withContext(Dispatchers.Main) {
                    playingAid = 0L
                    if (songs.isEmpty()) {
                        toast("无法获取播放链接")
                        return@withContext
                    }
                    playerViewModel.playSongs(
                        songsToPlay = songs,
                        startSong = startSong ?: songs.first(),
                        queueName = "B站收藏"
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t, "Bilibili favorites play failed")
                withContext(Dispatchers.Main) {
                    playingAid = 0L
                    toast("播放失败: ${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    BackHandler {
        // 播放器展开时，返回优先收起播放器到 Mini player（而不是退出本页）
        if (playerViewModel != null && playerViewModel.playerContentExpansionFraction.value > 0.01f) {
            playerViewModel.collapsePlayerSheet()
        } else if (selectedFolder != null) {
            selectedFolder = null
        } else {
            onBackClick()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_bilibili),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedFolder == null) "我的 B 站收藏" else selectedFolder?.title ?: "收藏夹",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFolder != null) selectedFolder = null else onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                !errorText.isNullOrBlank() && selectedFolder == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorText ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                selectedFolder == null -> {
                    // —— 收藏夹列表 ——
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp,
                            bottom = 24.dp + MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FavoriteFolderCard(
                                folder = folder,
                                onClick = { selectedFolder = folder }
                            )
                        }
                    }
                }
                else -> {
                    // —— 收藏夹内视频列表 ——
                    if (videos.isEmpty() && !errorText.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = errorText ?: "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = videoListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp,
                                bottom = 24.dp + MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(videos, key = { it.aid }) { video ->
                                FavoriteVideoRow(
                                    video = video,
                                    isPlaying = playingAid == video.aid,
                                    onClick = { playVideo(video) }
                                )
                            }
                            item(key = "load_more_footer") {
                                when {
                                    isLoadingMore -> Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    !hasMoreVideos && videos.size > 20 -> Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "没有更多了",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> Spacer(modifier = Modifier.height(0.dp))
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
private fun FavoriteFolderCard(
    folder: BilibiliFavoriteFolder,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_bilibili),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title.ifBlank { "未命名收藏夹" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.mediaCount} 个视频",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

@Composable
private fun FavoriteVideoRow(
    video: BilibiliFavoriteVideo,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(video.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title.ifBlank { "未命名视频" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.upName.ifBlank { "Bilibili" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 132.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
