package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.netease.HomeFeedTrack
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Calendar

/**
 * 「Hero 卡 + 行列表」推荐卡片（模仿参考 App 的 *Recently added* 区块）。
 *
 * 结构：
 *  - 区块标题（大号粗体 + 右侧「查看全部」箭头）
 *  - **Hero 卡**：整宽渐变底 + 左侧大封面 + 右侧「小字眉标 / 大标题 / 歌手 / 时间胶囊」
 *  - **行列表**：小封面 + 标题/歌手 + 右侧相对时间
 *
 * 这张卡的语义是「新加入 / 新歌」，所以带 `NEW` 角标与相对时间（今天 / N 天前 / 具体日期）。
 * 渐变底色由封面取色（复用 [ThemeStateHolder.getAlbumColorSchemeFlow]），取不到时回退主题色。
 */
@Composable
fun HeroWithRowListSection(
    title: String,
    tracks: List<HomeFeedTrack>,
    onTrackClick: (index: Int) -> Unit,
    onPlayClick: (index: Int) -> Unit = onTrackClick,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 行列表最多显示几条（默认 4 条，与参考 UI 一致） */
    maxRows: Int = 4,
    /** 圆角（沿用首页卡片的 24/28dp 约定） */
    cornerRadius: Dp = 24.dp,
) {
    if (tracks.isEmpty()) return

    val hero = tracks.first()
    val rows = tracks.drop(1).take(maxRows)
    val heroIndex = 0

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 区块标题（只有一个主标题，不堆副标题）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSectionHeader(title = title, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                IconButton(onClick = onSeeAll) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Hero 卡 ──
        HeroCard(
            track = hero,
            cornerRadius = cornerRadius,
            timeLabel = relativeTimeLabel(hero.addedAtMs),
            onClick = { onTrackClick(heroIndex) },
            onPlayClick = { onPlayClick(heroIndex) }
        )

        // ── 行列表 ──
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { rowIndex, track ->
                    val index = rowIndex + 1
                    HeroRow(
                        track = track,
                        timeLabel = relativeTimeLabel(track.addedAtMs),
                        onClick = { onTrackClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    track: HomeFeedTrack,
    cornerRadius: Dp,
    timeLabel: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    // ⚡ 参照仓库里 MostListenedRankCard 的设计语言：整块用「配色派生的彩色卡」，
    //    文字用该色系自己的 on-color，读起来像一张有颜色的实体卡片，
    //    而不是一堆灰底列表。本应用主题本身跟随封面取色，所以这里直接吃主题角色。
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(
        colors = listOf(
            scheme.primaryContainer,
            scheme.secondaryContainer
        )
    )
    val onCard = scheme.onPrimaryContainer

    val shape = AbsoluteSmoothCornerShape(
        cornerRadius = cornerRadius,
        smoothnessAsPercent = 60
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .clip(shape)
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(14.dp),
        // ⚡ 必须显式居中：Box 默认 TopStart 会把内容贴到最小高度的顶部，
        //    表现就是「封面上留白比下留白窄」（与封面槽那次的坑同一类）。
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                SmartImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(112.dp),
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    targetSize = SmartImageListTargetSize
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeLabel != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = onCard.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(onCard.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRow(
    track: HomeFeedTrack,
    timeLabel: String?,
    onClick: () -> Unit,
) {
    val shape = AbsoluteSmoothCornerShape(16.dp, 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            SmartImage(
                model = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.size(52.dp),
                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                targetSize = SmartImageCompactListTargetSize
            )
        }

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

        if (timeLabel != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ─── 工具 ────────────────────────────────────────────────────────────────

/** 今天的 00:00（供「每日推荐」这类按天更新的卡片显示「今天」）。 */
fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * 相对时间文案（参考 UI 的 `2 days ago` / `October 8`）：
 * 刚刚 / 今天 / 昨天 / N 天前 / 具体日期。
 */
private fun relativeTimeLabel(addedAtMs: Long): String? {
    if (addedAtMs <= 0L) return null
    val now = System.currentTimeMillis()
    if (addedAtMs > now) return null
    val diffMs = now - addedAtMs
    val hours = diffMs / (60L * 60L * 1000L)
    val days = diffMs / (24L * 60L * 60L * 1000L)

    return when {
        hours < 1L -> "刚刚"
        hours < 24L -> "今天"
        days < 2L -> "昨天"
        days < 7L -> "$days 天前"
        else -> {
            val calendar = Calendar.getInstance().apply { timeInMillis = addedAtMs }
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val year = calendar.get(Calendar.YEAR)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year == currentYear) "${month}月${day}日" else "${year}年${month}月${day}日"
        }
    }
}
