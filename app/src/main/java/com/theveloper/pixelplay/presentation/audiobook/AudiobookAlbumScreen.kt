package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.chrisbanes.haze.hazeSource
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 听书专辑详情：专辑信息 + 简介（可展开）+ 播放全部 / 随机播放 + 章节列表。
 *
 * 顶栏为软件统一的「渐入式顶栏」：随章节列表滚动收起 / 回顶展开；
 * 章节里只展示免费 / 限免（canPlay）章节，点击即在**整本可播章节**内按顺序播放；
 * 播放链接走 `kgaudio://` 占位、播放时由引擎**直接**调酷狗官方 `/v5/url` 取直链
 * （不走落雪 JS 音源链），相邻章节自动预解析。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiobookAlbumScreen(
    albumId: String,
    albumTitle: String,
    albumCoverUrl: String?,
    albumAuthor: String?,
    albumChapterCount: Int,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSongId = playbackState.currentSong?.id

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId, albumTitle, albumCoverUrl, albumAuthor, albumChapterCount)
    }

    val chapters = detail.chapters
    val coverUrl = detail.coverUrl?.takeIf { it.isNotBlank() } ?: albumCoverUrl
    val displayTitle = detail.title.ifBlank { albumTitle }
    val displayAuthor = detail.author?.takeIf { it.isNotBlank() } ?: albumAuthor
    val chapterTotal = if (detail.chapterCount > 0) detail.chapterCount else chapters.size

    val queueName = stringResource(R.string.audiobook_queue_name, displayTitle)

    val listState = rememberLazyListState()
    val topBarState = rememberAudiobookTopBarState(listState)

    fun playFrom(startIndex: Int, shuffle: Boolean) {
        if (chapters.isEmpty()) return
        val ordered = if (shuffle) chapters.shuffled() else chapters
        playerViewModel.playKugouAudiobookChapters(
            chapters = ordered,
            startIndex = if (shuffle) 0 else startIndex.coerceIn(0, ordered.lastIndex),
            albumId = albumId,
            albumAuthor = displayAuthor,
            albumCoverUrl = coverUrl,
            queueName = queueName,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarState.nestedScrollConnection),
    ) {
        when {
            detail.loading && chapters.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            detail.error && chapters.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarState.heightDp),
                contentAlignment = Alignment.Center,
            ) {
                AudiobookEmptyState(
                    text = stringResource(R.string.audiobook_load_failed),
                    onRetry = { viewModel.retryLoadAlbum() },
                )
            }

            chapters.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarState.heightDp),
                contentAlignment = Alignment.Center,
            ) {
                AudiobookEmptyState(
                    text = stringResource(R.string.audiobook_no_chapters),
                )
            }

            else -> LazyColumn(
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "album_header") {
                    AlbumHeader(
                        title = displayTitle,
                        author = displayAuthor,
                        coverUrl = coverUrl,
                        chapterTotal = chapterTotal,
                        intro = detail.intro,
                    )
                }
                item(key = "album_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { playFrom(0, shuffle = false) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.audiobook_play_all),
                                fontFamily = GoogleSansRounded,
                            )
                        }
                        FilledTonalButton(
                            onClick = { playFrom(0, shuffle = true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.audiobook_shuffle_play),
                                fontFamily = GoogleSansRounded,
                            )
                        }
                    }
                }
                itemsIndexed(chapters, key = { _, chapter -> chapter.hash }) { index, chapter ->
                    AudiobookChapterRow(
                        index = index + 1,
                        chapter = chapter,
                        isPlaying = currentSongId == "lx_kg_${chapter.hash}",
                        onClick = { playFrom(index, shuffle = false) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (detail.autoLoading) {
                    item(key = "auto_loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.audiobook_loading_chapters),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 渐入式顶栏：与关于页 / 账户页 / 专辑详情页同款
        // 书名放顶栏、作者放下面 AlbumHeader，避免同一信息重复显示两遍
        CollapsibleCommonTopBar(
            title = displayTitle,
            collapseFraction = topBarState.collapseFraction,
            headerHeight = topBarState.heightDp,
            onBackClick = onBack,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
        )
    }
}

/** 专辑头部：封面 + 集数 + 简介（超长可展开）。书名 / 作者由顶部渐入式顶栏展示，此处不再重复。 */
@Composable
private fun AlbumHeader(
    title: String,
    author: String?,
    coverUrl: String?,
    chapterTotal: Int,
    intro: String?,
) {
    var introExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmartImage(
                model = coverUrl,
                contentDescription = title,
                modifier = Modifier.size(112.dp),
                shape = AbsoluteSmoothCornerShape(20.dp, 60),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (chapterTotal > 0) {
                    Text(
                        text = stringResource(R.string.audiobook_chapter_count, chapterTotal),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        intro?.takeIf { it.isNotBlank() }?.let { text ->
            Surface(
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (introExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (text.length > 80) {
                        TextButton(
                            onClick = { introExpanded = !introExpanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (introExpanded) R.string.audiobook_intro_collapse
                                    else R.string.audiobook_intro_expand
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = GoogleSansRounded,
                            )
                        }
                    }
                }
            }
        }
    }
}
