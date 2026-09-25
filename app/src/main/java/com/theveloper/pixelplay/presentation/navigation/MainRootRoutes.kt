package com.theveloper.pixelplay.presentation.navigation

// ⚡ CloudMusicSettings（设置 → 在线音源）已从主根路由移除：
//   它从设置页进入，应与其它设置子页一致使用 AOSP SharedAxis 过渡，
//   而不是主 Tab 间的定向滑动（此前进入/退出动画与其它子页不同）。
internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Search.route,
    Screen.Library.route,
    Screen.Settings.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    Screen.Settings.route -> 3
    else -> null
}
