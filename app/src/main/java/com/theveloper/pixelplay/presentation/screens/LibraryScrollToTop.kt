package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * 媒体库「回到顶部」按钮的注册表。
 *
 * 媒体库的每个 Tab（歌曲 / 收藏 / 专辑 / 歌手 / 歌单 / 文件夹 / 历史）各自持有自己的列表状态，
 * 父级拿不到；所以这里反过来：**Tab 把自己的状态注册上来**，父级据此决定是否浮出按钮、并执行滚动。
 * 没有注册的 Tab 按钮就不会出现，不会出现"点了没反应"。
 */
class LibraryScrollToTopEntry(
    /** 列表是否已经离开顶部（用于决定按钮显隐，可观察） */
    val awayFromTop: State<Boolean>,
    /** 回到顶部 */
    val scrollToTop: suspend () -> Unit,
)

val LocalLibraryScrollRegistry =
    compositionLocalOf<MutableMap<String, LibraryScrollToTopEntry>?> { null }

/**
 * 注册一个 [LazyListState]（普通列表 Tab 用）。
 *
 * `enabled = false` 时撤销注册 —— 给一个 Tab 内部有多个滚动容器 / 多个子页面的场景用
 * （比如文件夹 Tab 每个子路径都有自己的 [LazyListState]），保证注册表始终指向
 * **当前可见**的列表。离开组合时清理自己的注册，避免指向已经销毁的旧列表。
 */
@Composable
fun LibraryScrollToTopRegistration(
    tabId: String,
    state: LazyListState,
    enabled: Boolean = true,
) {
    val registry = LocalLibraryScrollRegistry.current ?: return
    val awayFromTop = remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    DisposableEffect(registry, state, tabId, enabled) {
        val entry = LibraryScrollToTopEntry(awayFromTop) { state.animateScrollToItem(0) }
        if (enabled) {
            registry[tabId] = entry
        }
        onDispose {
            // 只有仍指向本条目时才移除：防止误删后来者的注册（后注册的会覆盖同 key）
            if (registry[tabId] === entry) registry.remove(tabId)
        }
    }
}

/** 注册一个 [LazyGridState]（网格 Tab 用）。[enabled] 语义同列表版。 */
@Composable
fun LibraryScrollToTopRegistration(
    tabId: String,
    state: LazyGridState,
    enabled: Boolean = true,
) {
    val registry = LocalLibraryScrollRegistry.current ?: return
    val awayFromTop = remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    DisposableEffect(registry, state, tabId, enabled) {
        val entry = LibraryScrollToTopEntry(awayFromTop) { state.animateScrollToItem(0) }
        if (enabled) {
            registry[tabId] = entry
        }
        onDispose {
            if (registry[tabId] === entry) registry.remove(tabId)
        }
    }
}
