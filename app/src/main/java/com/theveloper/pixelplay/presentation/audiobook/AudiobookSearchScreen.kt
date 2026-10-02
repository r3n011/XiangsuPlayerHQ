package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.presentation.components.AppSearchField
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

/**
 * 听书搜索：输入关键词（防抖 350ms）按书名 / 作者 / 演播者搜专辑，
 * 结果行与免费书库一致（复用歌单列表样式）。
 *
 * 输入框直接复用软件搜索页同款组件（AppSearchField），顶栏为收起态的
 * 统一顶栏（模糊 + 标题胶囊），搜索框固定在顶栏下方不随结果滚动。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiobookSearchScreen(
    onBack: () -> Unit,
    onOpenAlbum: (KugouAudiobookAlbum) -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }

    // 输入防抖：停止输入 350ms 后自动搜索；清空则回到初始提示态
    LaunchedEffect(keyword) {
        if (keyword.isBlank()) {
            viewModel.clearSearch()
            return@LaunchedEffect
        }
        delay(350)
        viewModel.search(keyword)
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 搜索页没有大标题：顶栏固定为收起态（胶囊 + 渐进模糊），搜索框紧随其下
    val topBarHeight = 64.dp + statusBarHeight

    // 换关键词出新结果时回到顶部，避免停在上一次结果的中段
    val listState = rememberLazyListState()
    LaunchedEffect(search.query) {
        if (search.results.isNotEmpty()) listState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarHeight),
        ) {
            AppSearchField(
                value = keyword,
                onValueChange = { keyword = it },
                onSearch = { viewModel.search(keyword) },
                placeholder = stringResource(R.string.audiobook_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentDescriptionClear = stringResource(R.string.audiobook_search_clear),
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    search.searching -> CircularProgressIndicator()

                    !search.searched -> AudiobookEmptyState(
                        text = stringResource(R.string.audiobook_search_initial),
                    )

                    search.results.isEmpty() -> AudiobookEmptyState(
                        text = stringResource(R.string.audiobook_search_empty, search.query),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(MainActivity.LocalHazeState.current),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = MiniPlayerHeight +
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(search.results, key = { it.id }) { album ->
                            AudiobookAlbumRow(
                                album = album,
                                onClick = { onOpenAlbum(album) },
                            )
                        }
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.audiobook_search_title),
            collapseFraction = 1f,
            headerHeight = topBarHeight,
            onBackClick = onBack,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
        )
    }
}
