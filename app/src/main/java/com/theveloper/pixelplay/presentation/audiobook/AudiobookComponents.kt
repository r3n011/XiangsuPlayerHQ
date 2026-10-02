package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.data.kugou.KugouAudiobookChapter
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.formatDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 书架横滑专辑卡：封面 + 书名 + 作者（一屏能看到多本「书」，对齐参照项目的书架）。
 */
@Composable
fun AudiobookAlbumCard(
    album: KugouAudiobookAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SmartImage(
                model = album.coverUrl,
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                shape = AbsoluteSmoothCornerShape(14.dp, 60),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.author?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.audiobook_unknown_author),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 专辑列表行：视觉与「歌单列表」（PlaylistItem）一致 —— 圆角封面 + 书名 +
 * 作者 / 集数 + 简介一行 + 右箭头，便于在书库 / 搜索结果里快速浏览。
 */
@Composable
fun AudiobookAlbumRow(
    album: KugouAudiobookAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmartImage(
                model = album.coverUrl,
                contentDescription = album.name,
                modifier = Modifier.size(48.dp),
                shape = AbsoluteSmoothCornerShape(10.dp, 60),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildSubtitle(album, stringResource(R.string.audiobook_chapters_short, album.chapterCount))
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                album.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSubtitle(album: KugouAudiobookAlbum, chapterLabel: String): String {
    val parts = buildList {
        album.author?.takeIf { it.isNotBlank() }?.let(::add)
        if (album.chapterCount > 0) add(chapterLabel)
    }
    return parts.joinToString(" · ")
}

/**
 * 章节行：序号 + 章节名（可选演播者）+ 时长；播放中的章节用主色高亮。
 */
@Composable
fun AudiobookChapterRow(
    index: Int,
    chapter: KugouAudiobookChapter,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = GoogleSansRounded,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chapter.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatDuration(chapter.durationMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分区标题：每日推荐 / 排行榜推荐 / 每周推荐 / VIP 推荐。 */
@Composable
fun AudiobookSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 空态 / 失败态：图标 + 文案 +（可选）重试按钮。 */
@Composable
fun AudiobookEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry) {
                Text(stringResource(R.string.audiobook_retry))
            }
        }
    }
}
