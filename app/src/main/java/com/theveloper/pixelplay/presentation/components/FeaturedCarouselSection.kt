package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 自动轮播间隔（Rhythm 同款 4.5s）。 */
private const val FEATURED_AUTO_SCROLL_MS = 4_500L

/**
 * 主页顶部精选轮播（逐行移植 Rhythm 的 `ModernFeaturedSection`）。
 *
 * **两种形态是两套完全不同的实现，按 [tabletStyle] 切换：**
 * - **手机（tabletStyle=false）**：`HorizontalUncontainedCarousel` 全宽整屏大图 ——
 *   itemWidth = 容器宽、itemSpacing 0、无 contentPadding、`singleAdvanceFlingBehavior`
 *   一次只翻一页；标题 displaySmall、播放胶囊 56dp、年份 headlineMedium；
 * - **平板 / 卡片槽（tabletStyle=true）**：`HorizontalMultiBrowseCarousel` 多浏览布局 ——
 *   两侧露出缩略图、`CarouselItemScope.maskClip` 给露出的项也套上圆角；
 *   标题 titleLarge、播放胶囊 44dp、年份 titleMedium。
 *
 * 两者都是 4.5s 自动轮播（手机 900ms、平板 800ms tween），回到前台时滚回当前页，
 * 手动拖动期间不自动翻页。
 */
@Composable
fun FeaturedCarouselSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /** true = 平板/横屏卡片槽形态（多浏览 + 圆角），false = 手机全宽形态 */
    tabletStyle: Boolean = false,
) {
    if (songs.isEmpty()) return
    BoxWithConstraints(modifier = modifier) {
        val slotWidth = maxWidth
        if (tabletStyle) {
            TabletFeaturedCarousel(
                songs = songs,
                slotWidth = slotWidth,
                onSongClick = onSongClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PhoneFeaturedCarousel(
                songs = songs,
                itemWidth = slotWidth,
                onSongClick = onSongClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 平板 / 卡片槽：多浏览布局（两侧露出缩略图）
// ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletFeaturedCarousel(
    songs: List<Song>,
    slotWidth: Dp,
    onSongClick: (Song) -> Unit,
    onPlayClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState { songs.size }
    FeaturedCarouselResumeEffect(carouselState, songs.size)
    FeaturedAutoScrollEffect(carouselState, songs.size, durationMs = 800)

    val preferredItemWidth = if (slotWidth >= 840.dp) 360.dp else 280.dp
    val horizontalPadding = if (slotWidth >= 840.dp) 32.dp else 16.dp
    val shape = MaterialTheme.shapes.extraLarge

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        itemSpacing = 16.dp,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        minSmallItemWidth = 48.dp,
        maxSmallItemWidth = 140.dp,
        modifier = modifier,
    ) { page ->
        val song = songs.getOrNull(page) ?: return@HorizontalMultiBrowseCarousel
        val haptic = LocalHapticFeedback.current
        Card(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSongClick(song)
            },
            modifier = Modifier
                .fillMaxSize()
                // 两侧露出的「缩略图」也要圆角：靠 maskClip 按同一形状做遮罩（Rhythm 同款）
                .maskClip(shape),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FeaturedCardArtwork(song)
                FeaturedCardScrim(compact = true)
                FeaturedCardContent(
                    song = song,
                    compact = true,
                    onPlayClick = { onPlayClick(song) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 手机：全宽整屏大图（无间距、一次翻一页）
// ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneFeaturedCarousel(
    songs: List<Song>,
    itemWidth: Dp,
    onSongClick: (Song) -> Unit,
    onPlayClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState { songs.size }
    FeaturedCarouselResumeEffect(carouselState, songs.size)
    FeaturedAutoScrollEffect(
        carouselState = carouselState,
        count = songs.size,
        durationMs = 900,
        snapToCurrentOnStart = true,
    )

    HorizontalUncontainedCarousel(
        state = carouselState,
        itemWidth = itemWidth,
        itemSpacing = 0.dp,
        contentPadding = PaddingValues(0.dp),
        // 位置传参：避免不同 material3 版本参数名差异（Rhythm 用 state = ... 的写法）
        flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(carouselState),
        modifier = modifier,
    ) { page ->
        val song = songs.getOrNull(page) ?: return@HorizontalUncontainedCarousel
        val haptic = LocalHapticFeedback.current
        // 手机形态是全宽出血大图：方角、无卡片底（Rhythm 的 phone 分支就是 RectangleShape）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSongClick(song)
                }
        ) {
            FeaturedCardArtwork(song)
            FeaturedCardScrim(compact = false)
            FeaturedCardContent(
                song = song,
                compact = false,
                onPlayClick = { onPlayClick(song) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 卡片内容（两种形态共用，尺寸/排版按 compact 切换）
// ─────────────────────────────────────────────────────────

@Composable
private fun FeaturedCardArtwork(song: Song) {
    SmartImage(
        model = song.albumArtUriString,
        contentDescription = song.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** 渐变遮罩：手机用 background 色系（上下压暗、中间透明），平板用 surface 色系（底部渐入）。 */
@Composable
private fun FeaturedCardScrim(compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (compact) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.background,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                }
            )
    )
}

@Composable
private fun FeaturedCardContent(
    song: Song,
    compact: Boolean,
    onPlayClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val playLabel = stringResource(R.string.song_info_action_play)
    val qualityRes = song.featuredQualityRes()
    val qualityLabel = if (qualityRes != null) stringResource(qualityRes) else null
    val contentPadding = if (compact) 16.dp else 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
        Text(
            text = song.title,
            style = if (compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.displaySmall
            },
            fontWeight = FontWeight.Bold,
            color = if (compact) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            text = song.displayArtist,
            style = if (compact) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayClick()
                },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = if (compact) {
                    PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                } else {
                    PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                },
                modifier = Modifier.height(if (compact) 44.dp else 56.dp),
            ) {
                Text(
                    text = playLabel,
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = if (compact) FontWeight.Bold else FontWeight.ExtraBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (song.year > 0) {
                    Text(
                        text = song.year.toString(),
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = if (compact) FontWeight.Bold else FontWeight.Black,
                        color = if (compact) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                qualityLabel?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 轮播行为（自动翻页 / 回前台归位）
// ─────────────────────────────────────────────────────────

@Composable
private fun FeaturedAutoScrollEffect(
    carouselState: CarouselState,
    count: Int,
    durationMs: Int,
    snapToCurrentOnStart: Boolean = false,
) {
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        if (snapToCurrentOnStart && !carouselState.isScrollInProgress) {
            carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, count - 1))
        }
        while (true) {
            delay(FEATURED_AUTO_SCROLL_MS)
            if (!carouselState.isScrollInProgress) {
                carouselState.animateScrollToItem(
                    (carouselState.currentItem + 1) % count,
                    animationSpec = tween(durationMillis = durationMs),
                )
            }
        }
    }
}

@Composable
private fun FeaturedCarouselResumeEffect(carouselState: CarouselState, count: Int) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, count) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && count > 0 && !carouselState.isScrollInProgress) {
                scope.launch {
                    carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, count - 1))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** 用歌曲已有的编码信息给出一个音质小标（无损 / Hi-Res / HQ）。 */
private fun Song.featuredQualityRes(): Int? {
    val mime = mimeType?.lowercase().orEmpty()
    val br = bitrate ?: 0
    val sr = sampleRate ?: 0
    return when {
        mime.contains("flac") || mime.contains("alac") || mime.contains("ape") ||
            mime.contains("wav") || mime.contains("dsd") -> R.string.home_featured_quality_lossless
        sr >= 96_000 || br >= 1_000 -> R.string.home_featured_quality_hires
        br >= 320 -> R.string.home_featured_quality_hq
        else -> null
    }
}
