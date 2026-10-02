package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.chrisbanes.haze.hazeSource
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 听书主页（书架）：免费书库入口 + 每日推荐 / 排行榜推荐 / 每周推荐 / VIP 推荐
 * 四个横滑分区（每本一张「书」卡片，封面 + 书名 + 作者）。
 *
 * 顶栏使用软件统一的「渐入式顶栏」（滚动收起 / 回顶展开，渐进模糊 + 标题胶囊）；
 * 数据全部来自酷狗长音频接口（匿名可用）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiobookScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAlbum: (KugouAudiobookAlbum) -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHome() }

    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val topBarState = rememberAudiobookTopBarState(listState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarState.nestedScrollConnection),
    ) {
        when {
            // 首次加载（还没有任何数据）才铺满转圈；下拉刷新时保留列表只显示顶部指示器
            home.loading && !home.hasAnyData -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            !home.hasAnyData -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarState.heightDp),
                contentAlignment = Alignment.Center,
            ) {
                AudiobookEmptyState(
                    text = stringResource(R.string.audiobook_home_empty),
                    onRetry = { viewModel.loadHome(force = true) },
                )
            }

            else -> PullToRefreshBox(
                isRefreshing = home.loading,
                onRefresh = { viewModel.loadHome(force = true) },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    // 顶栏是叠加在内容之上的，指示器要下移到顶栏下方才可见
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = home.loading,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topBarState.heightDp),
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(MainActivity.LocalHazeState.current),
                    contentPadding = PaddingValues(
                        top = topBarState.heightDp + 8.dp,
                        bottom = MiniPlayerHeight +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            16.dp,
                    ),
                ) {
                    item(key = "library_entry") {
                        FreeLibraryEntryCard(onClick = onOpenLibrary)
                    }
                    AudiobookSectionKind.entries.forEach { kind ->
                        val albums = home.albumsOf(kind)
                        if (albums.isEmpty()) return@forEach
                        item(key = "header_${kind.name}") {
                            AudiobookSectionHeader(stringResource(kind.titleRes()))
                        }
                        item(key = "row_${kind.name}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(albums, key = { "${kind.name}_${it.id}" }) { album ->
                                    AudiobookAlbumCard(
                                        album = album,
                                        onClick = { onOpenAlbum(album) },
                                        modifier = Modifier.width(140.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 渐入式顶栏：与关于页 / 账户页同款（渐进模糊遮罩 + 收起标题胶囊）
        CollapsibleCommonTopBar(
            title = stringResource(R.string.audiobook_title),
            collapseFraction = topBarState.collapseFraction,
            headerHeight = topBarState.heightDp,
            onBackClick = onBack,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
            actions = {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.audiobook_search_cd),
                    )
                }
            },
        )
    }
}

private fun AudiobookSectionKind.titleRes(): Int = when (this) {
    AudiobookSectionKind.DAILY -> R.string.audiobook_section_daily
    AudiobookSectionKind.RANK -> R.string.audiobook_section_rank
    AudiobookSectionKind.WEEK -> R.string.audiobook_section_week
    AudiobookSectionKind.VIP -> R.string.audiobook_section_vip
}

/** 免费听书库入口卡片（分类 / 排序 / 男频女频 / 连载状态）。 */
@Composable
private fun FreeLibraryEntryCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AbsoluteSmoothCornerShape(12.dp, 60))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audiobook_free_library),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.audiobook_free_library_desc),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
