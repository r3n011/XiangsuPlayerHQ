package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.flow.distinctUntilChanged
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 免费听书库：分类标签 + 排序 / 男频女频 / 连载状态筛选 + 分页专辑列表。
 *
 * 列表行复用「歌单列表」的样式（圆角封面 + 书名 + 作者/集数 + 简介 + 右箭头）。
 * 筛选或下拉刷新回第一页，滚动到底自动加载下一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookLibraryScreen(
    onBack: () -> Unit,
    onOpenAlbum: (KugouAudiobookAlbum) -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureLibraryLoaded() }

    val listState = rememberLazyListState()
    // 滚动到接近底部 → 拉下一页（列表增长后会自动重新判断）
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { atEnd -> if (atEnd) viewModel.loadMoreLibrary() }
    }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.audiobook_free_library),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 分类标签：全部（906）+ 接口动态补的子分类
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "tag_all") {
                    FilterChip(
                        selected = library.tagId == AudiobookLibraryUiState.DEFAULT_TAG_ID,
                        onClick = { viewModel.setLibraryTag(AudiobookLibraryUiState.DEFAULT_TAG_ID) },
                        label = {
                            Text(
                                stringResource(R.string.audiobook_filter_all),
                                fontFamily = GoogleSansRounded,
                            )
                        },
                    )
                }
                items(library.tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = library.tagId == tag.id,
                        onClick = { viewModel.setLibraryTag(tag.id) },
                        label = { Text(tag.name, fontFamily = GoogleSansRounded) },
                    )
                }
            }

            // 排序 / 性别 / 状态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterDropdown(
                    label = stringResource(R.string.audiobook_filter_sort),
                    options = listOf(
                        0 to stringResource(R.string.audiobook_sort_default),
                        1 to stringResource(R.string.audiobook_sort_plays),
                        2 to stringResource(R.string.audiobook_sort_update),
                    ),
                    selected = library.sort,
                    onSelect = viewModel::setLibrarySort,
                    modifier = Modifier.weight(1f),
                )
                FilterDropdown(
                    label = stringResource(R.string.audiobook_filter_gender),
                    options = listOf(
                        0 to stringResource(R.string.audiobook_gender_all),
                        1 to stringResource(R.string.audiobook_gender_male),
                        2 to stringResource(R.string.audiobook_gender_female),
                    ),
                    selected = library.gender,
                    onSelect = viewModel::setLibraryGender,
                    modifier = Modifier.weight(1f),
                )
                FilterDropdown(
                    label = stringResource(R.string.audiobook_filter_status),
                    options = listOf(
                        0 to stringResource(R.string.audiobook_status_all),
                        1 to stringResource(R.string.audiobook_status_serial),
                        2 to stringResource(R.string.audiobook_status_done),
                    ),
                    selected = library.status,
                    onSelect = viewModel::setLibraryStatus,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    library.loading && library.albums.isEmpty() -> CircularProgressIndicator()

                    library.albums.isEmpty() -> AudiobookEmptyState(
                        text = stringResource(R.string.audiobook_library_empty),
                    )

                    else -> PullToRefreshBox(
                        isRefreshing = library.loading,
                        onRefresh = { viewModel.refreshLibrary() },
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = pullState,
                                isRefreshing = library.loading,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(library.albums, key = { it.id }) { album ->
                                AudiobookAlbumRow(
                                    album = album,
                                    onClick = { onOpenAlbum(album) },
                                )
                            }
                            if (library.loadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 小筛选下拉：标签 · 当前值 + 下拉菜单（排序/性别/状态共用）。 */
@Composable
private fun FilterDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = AbsoluteSmoothCornerShape(12.dp, 60),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$label · $currentLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(2.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            fontFamily = GoogleSansRounded,
                            fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}
