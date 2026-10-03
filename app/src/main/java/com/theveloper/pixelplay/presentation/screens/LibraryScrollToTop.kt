package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * 媒体库「回到顶部」按钮的注册表。
 *
 * 媒体库的每个 Tab（歌曲 / 收藏 / 专辑 / 歌手 / 歌单 / 文件夹 / 历史）各自持有自己的列表状态，
 * 父级拿不到；所以这里反过来：**Tab 把自己的状态注册上来**，父级据此决定是否浮出按钮、并执行滚动。
 * 没有注册的 Tab（比如还没接的视图分支）按钮就不会出现，不会出现"点了没反应"。
 */
class LibraryScrollToTopEntry(
    /** 列表是否已经离开顶部（用于决定按钮显隐，可观察） */
    val awayFromTop: State<Boolean>,
    /** 回到顶部 */
    val scrollToTop: suspend () -> Unit,
)

val LocalLibraryScrollRegistry =
    compositionLocalOf<MutableMap<String, LibraryScrollToTopEntry>?> { null }

/** 注册一个 [LazyListState]（普通列表 Tab 用） */
@Composable
fun LibraryScrollToTopRegistration(tabId: String, state: LazyListState) {
    val registry = LocalLibraryScrollRegistry.current ?: return
    val awayFromTop = remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(registry, state) {
        registry[tabId] = LibraryScrollToTopEntry(awayFromTop) { state.animateScrollToItem(0) }
    }
}

/** 注册一个 [LazyGridState]（网格 Tab 用） */
@Composable
fun LibraryScrollToTopRegistration(tabId: String, state: LazyGridState) {
    val registry = LocalLibraryScrollRegistry.current ?: return
    val awayFromTop = remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(registry, state) {
        registry[tabId] = LibraryScrollToTopEntry(awayFromTop) { state.animateScrollToItem(0) }
    }
}
