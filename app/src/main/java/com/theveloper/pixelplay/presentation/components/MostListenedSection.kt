package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 主页「最多播放」卡片（1:1 对齐参考样式）：
 * - 标题行「Most listened」+ 右侧圆形箭头按钮（点击进入已有统计页 StatsScreen）；
 * - 分段选择 Songs / Albums / Artists（复用项目内 ExpressiveButtonGroup 胶囊分段）；
 * - 两张排行卡并排：左上角 #N 名次徽标、圆形配图、名称 + 「N plays」。
 *
 * 数据直接复用已加载的统计摘要（[PlaybackStatsSummary]），不额外请求；点开界面复用现有
 * 统计页（TopArtists / TopAlbums / SongStats 卡片），不新增详情页。
 */
@Composable
fun MostListenedSection(
    summary: PlaybackStatsSummary,
    onSeeAll: () -> Unit,
    /** ⚡ 歌曲卡片：点击直接播放该歌曲 */
    onPlaySong: (String) -> Unit = {},
    /** ⚡ 专辑卡片：点击打开该专辑 */
    onOpenAlbum: (String) -> Unit = {},
    /** ⚡ 歌手卡片：点击打开该歌手 */
    onOpenArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(MostListenedTab.SONGS) }
    val scheme = MaterialTheme.colorScheme
    val cornerRadius = 28.dp

    Card(
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBR = cornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = cornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = cornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTL = cornerRadius,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // ── 标题行 + 圆形箭头（进入已有统计页） ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_most_listened_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = onSeeAll,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.home_most_listened_title)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 分段：Songs / Albums / Artists ──
            ExpressiveButtonGroup(
                items = listOf(
                    stringResource(R.string.home_most_listened_songs),
                    stringResource(R.string.home_most_listened_albums),
                    stringResource(R.string.home_most_listened_artists)
                ),
                selectedIndex = selectedTab,
                onItemClick = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // ── 两张排行卡（#1 / #2） ──
            val entries = remember(summary, selectedTab) { buildMostListenedEntries(summary, selectedTab) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entries.take(2).forEachIndexed { index, entry ->
                    MostListenedRankCard(
                        entry = entry,
                        rank = index + 1,
                        // 参考图：第二张卡用略偏主题色的底
                        tinted = index == 1,
                        // ⚡ 按当前分段做对应动作：歌曲→播放；专辑→打开专辑；歌手→打开歌手
                        onClick = {
                            when {
                                entry.songId != null -> onPlaySong(entry.songId)
                                entry.albumName != null -> onOpenAlbum(entry.albumName)
                                entry.artistName != null -> onOpenArtist(entry.artistName)
                                else -> onSeeAll()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 数据不足两张时占位，避免单卡被拉伸
                if (entries.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 分段索引常量 */
private object MostListenedTab {
    const val SONGS = 0
    const val ALBUMS = 1
    const val ARTISTS = 2
}

/** 排行项：名称、副标题（N plays）、配图 + 点击动作所需的原始标识 */
private data class MostListenedEntry(
    val label: String,
    val playCount: Int,
    val imageUri: String?,
    /** 歌曲：点击播放 */
    val songId: String? = null,
    /** 专辑：点击打开专辑页 */
    val albumName: String? = null,
    /** 歌手：点击打开歌手页 */
    val artistName: String? = null
)

private fun buildMostListenedEntries(
    summary: PlaybackStatsSummary,
    tab: Int
): List<MostListenedEntry> = when (tab) {
    MostListenedTab.ALBUMS -> summary.topAlbums.map { album ->
        MostListenedEntry(
            label = album.album,
            playCount = album.playCount,
            imageUri = album.albumArtUri,
            albumName = album.album
        )
    }

    MostListenedTab.ARTISTS -> summary.topArtists.map { artist ->
        // 歌手摘要本身不带图：退而使用该歌手最常播放歌曲的封面（离线可用）
        val coverUri = summary.songs
            .firstOrNull { it.artist.equals(artist.artist, ignoreCase = true) }
            ?.albumArtUri
        MostListenedEntry(
            label = artist.artist,
            playCount = artist.playCount,
            imageUri = coverUri,
            artistName = artist.artist
        )
    }

    else -> summary.topSongs.map { song ->
        MostListenedEntry(
            label = song.title,
            playCount = song.playCount,
            imageUri = song.albumArtUri,
            songId = song.songId
        )
    }
}

/** 单张排行卡：#N 徽标 + 圆形配图 + 名称 + N plays */
@Composable
private fun MostListenedRankCard(
    entry: MostListenedEntry,
    rank: Int,
    tinted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (tinted) scheme.secondaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerHigh

    Card(
        onClick = onClick,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 名次徽标：#1 中性、#2 主题色（对齐参考图两张卡的差别）
            Surface(
                shape = CircleShape,
                color = if (rank == 1) scheme.surfaceContainerHighest else scheme.primaryContainer,
                contentColor = if (rank == 1) scheme.onSurface else scheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 圆形配图（无封面时用主题色底 + 首字母）
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (entry.imageUri != null) {
                    SmartImage(
                        model = entry.imageUri,
                        contentDescription = entry.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        targetSize = SmartImageCompactListTargetSize
                    )
                } else {
                    Text(
                        text = entry.label.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.home_most_listened_plays, entry.playCount),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
