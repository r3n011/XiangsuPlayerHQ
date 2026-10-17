package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.netease.HomeFeedTrack
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 主页「在线推荐」卡片集合 —— 4 张卡**各自形态不同**，但统一用 M3E（Expressive）设计语言：
 * 大圆角容器 / `surfaceContainer*` 色阶 / 字重层级 / 官方 carousel。
 *
 * 设计贴合各自作用：
 *  - 每日推荐 → 封面主导（见 [HeroWithRowListSection]）：一天一版、需要快速浏览整版
 *  - 为你推荐 → **M3E 多浏览轮播**：个性化 = 横向探索
 *  - 今日云村热播 → **带序号的榜单列表**：热播 = 榜单语义，序号是核心信息
 *  - 今日热门 → **2 列封面网格**：全站热度 = 看图逛
 *
 * 每张卡只保留**一个主标题**，不再堆副标题/眉标。
 */

/** 统一的区块主标题（只此一个标题，不加副标题），可选右侧「查看全部」。 */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        if (onSeeAll != null) {
            IconButton(onClick = onSeeAll) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.home_card_see_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 「查看全部」弹窗的数据：标题 + 全部歌曲 + 播放方式。
 */
data class HomeSectionTracks(
    val title: String,
    val tracks: List<HomeFeedTrack>,
    val queueName: String,
    /** 每日推荐走既有链路（`playDailyRecommend`），其余走 `playHomeFeedTracks` */
    val isDaily: Boolean = false,
)

/**
 * 「查看全部」弹窗：把某张卡片的**全部**歌曲列出来（首页只展示前几首）。
 * 四张卡共用同一个弹窗，保证交互一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedAllTracksSheet(
    title: String,
    tracks: List<HomeFeedTrack>,
    onTrackClick: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, track -> "${track.id}_$index" }
            ) { index, track ->
                RankRow(
                    rank = index + 1,
                    track = track,
                    onClick = { onTrackClick(index) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 为你推荐：M3E 多浏览轮播（两侧露出邻卡）—— 个性化 = 横向探索
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouCarouselSection(
    title: String,
    tracks: List<HomeFeedTrack>,
    onTrackClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    preferredItemWidth: Dp = 172.dp,
    onSeeAll: (() -> Unit)? = null,
) {
    if (tracks.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(title, onSeeAll = onSeeAll)
        Spacer(Modifier.height(10.dp))
        val carouselState = rememberCarouselState { tracks.size }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = preferredItemWidth,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            minSmallItemWidth = 44.dp,
            maxSmallItemWidth = 120.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val track = tracks.getOrNull(page) ?: return@HorizontalMultiBrowseCarousel
            val shape = AbsoluteSmoothCornerShape(20.dp, 60)
            Card(
                onClick = { onTrackClick(page) },
                shape = shape,
                colors = CardDefaults.cardColors(
                    // ⚡ 配色派生的彩色卡（同 MostListenedRankCard 的处理）
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SmartImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = AbsoluteSmoothCornerShape(20.dp, 60),
                        targetSize = SmartImageListTargetSize
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 今日云村热播：带序号的榜单列表 —— 热播 = 榜单语义
// ══════════════════════════════════════════════════════════════════════

@Composable
fun TrendingRankSection(
    title: String,
    tracks: List<HomeFeedTrack>,
    onTrackClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 5,
    onSeeAll: (() -> Unit)? = null,
) {
    if (tracks.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(title, onSeeAll = onSeeAll)
        Spacer(Modifier.height(6.dp))
        tracks.take(maxItems).forEachIndexed { index, track ->
            RankRow(
                rank = index + 1,
                track = track,
                onClick = { onTrackClick(index) }
            )
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    track: HomeFeedTrack,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(14.dp, 60))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ⚡ 名次徽标：圆形 `#N`（对齐仓库 MostListenedRankCard 的语言），
        //    #1 中性、其余主题色
        Surface(
            shape = CircleShape,
            color = if (rank == 1) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (rank == 1) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        SmartImage(
            model = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier.size(48.dp),
            shape = AbsoluteSmoothCornerShape(12.dp, 60),
            targetSize = SmartImageCompactListTargetSize
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 今日热门：2 列封面网格 —— 全站热度 = 看图逛
// ══════════════════════════════════════════════════════════════════════

@Composable
fun TodayHotGridSection(
    title: String,
    tracks: List<HomeFeedTrack>,
    onTrackClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    rows: Int = 2,
    onSeeAll: (() -> Unit)? = null,
) {
    if (tracks.isEmpty()) return

    val visible = tracks.take(columns * rows)
    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(title, onSeeAll = onSeeAll)
        Spacer(Modifier.height(10.dp))
        visible.chunked(columns).forEachIndexed { rowIndex, rowTracks ->
            if (rowIndex > 0) Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowTracks.forEachIndexed { columnIndex, track ->
                    val index = rowIndex * columns + columnIndex
                    GridTile(
                        track = track,
                        onClick = { onTrackClick(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 最后一行不足时补空位，保证格子宽度一致
                repeat(columns - rowTracks.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GridTile(
    track: HomeFeedTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AbsoluteSmoothCornerShape(18.dp, 60))
            .clickable(onClick = onClick)
    ) {
        Box {
            SmartImage(
                model = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                targetSize = SmartImageListTargetSize
            )
            // 底部渐变压一层歌名，保持"看图逛"的视觉主导
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                        )
                    )
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
