package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.data.kugou.KugouAudiobookChapter
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

/**
 * 听书页共用的「渐入式顶栏」状态（对齐关于页 / 账户页 / 专辑详情页）：
 *
 * - 列表停在顶部时顶栏保持展开（大标题）；
 * - 列表顶部向下滚时把滚动量用来收起顶栏，滚回顶部松手后自动弹回展开；
 * - 收起进度 [collapseFraction] 直接喂给 `CollapsibleCommonTopBar`，
 *   由它完成「渐进模糊遮罩 + 收起标题胶囊」的淡入。
 *
 * 页面用法：`Modifier.nestedScroll(state.nestedScrollConnection)` 挂在内容容器上，
 * 列表顶部 contentPadding 用 [heightDp]，顶栏叠在内容之上。
 */
@Stable
class AudiobookTopBarState internal constructor(
    private val heightPx: Animatable<Float, AnimationVector1D>,
    private val density: Density,
    private val minHeightPx: Float,
    private val maxHeightPx: Float,
) {
    /** 0 = 完全展开，1 = 完全收起（`CollapsibleCommonTopBar` 的 collapseFraction）。 */
    var collapseFraction by mutableStateOf(0f)
        internal set

    /** 顶栏当前高度：列表内容按它留顶部 padding，避免首项被顶栏遮住。 */
    val heightDp: Dp
        get() = with(density) { heightPx.value.toDp() }

    internal var nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {}
}

/**
 * 创建 [AudiobookTopBarState]：绑定听书页的 [listState]，自动处理
 * 「滚动收起 / 回顶展开」与收起进度换算。
 *
 * @param maxTopBarHeight 展开态高度（收起态固定为 64dp + 状态栏高度）
 */
@Composable
fun rememberAudiobookTopBarState(
    listState: LazyListState,
    maxTopBarHeight: Dp = 140.dp,
): AudiobookTopBarState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val minHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxHeightPx = with(density) { maxTopBarHeight.toPx() }
    val heightPx = remember { Animatable(maxHeightPx) }

    val state = remember(heightPx, density, minHeightPx, maxHeightPx) {
        AudiobookTopBarState(heightPx, density, minHeightPx, maxHeightPx)
    }

    LaunchedEffect(heightPx.value) {
        state.collapseFraction = 1f - (
            (heightPx.value - minHeightPx) / (maxHeightPx - minHeightPx)
            ).coerceIn(0f, 1f)
    }

    // 松手后：回到列表顶部就弹回展开，否则保持收起
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val target = if (atTop) maxHeightPx else minHeightPx
            if (heightPx.value != target) {
                heightPx.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }

    state.nestedScrollConnection = remember(listState, heightPx, scope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 只在「列表在顶部 + 向下滚」时用滚动量收起顶栏；
                // 上拉（回顶 / 下拉刷新）完全不拦截，保证下拉刷新仍可用。
                if (available.y >= 0f) return Offset.Zero
                if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                    return Offset.Zero
                }
                val previousHeight = heightPx.value
                val newHeight = (previousHeight + available.y).coerceIn(minHeightPx, maxHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) {
                    scope.launch { heightPx.snapTo(newHeight) }
                }
                return Offset(0f, consumed)
            }
        }
    }

    return state
}

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
        // 与软件内歌曲行/列表卡一致的圆角语言（12dp 直角的旧样式偏「网页感」）
        shape = RoundedCornerShape(20.dp),
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
    // 视觉对齐软件里的歌曲行（EnhancedSongListItem）：surfaceContainerLow 容器 +
    // 播放中 primaryContainer 高亮 + 22dp 圆角，章节列表不再是一排「裸」文字。
    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "audiobookChapterContainerColor",
    )
    val contentColor = if (isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chapter.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.audiobook_retry),
                    fontFamily = GoogleSansRounded,
                )
            }
        }
    }
}
