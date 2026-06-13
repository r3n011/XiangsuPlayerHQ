package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.components.ScreenWrapper
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.screens.CloudMusicSettingsScreen

/**
 * 预加载 4 个 tab 内容，用 AnimatedVisibility 控制切换动画。
 * 这避免了 NavHost 每次切换 tab 时重建页面。
 */
@Composable
internal fun TabContentHost(
    currentRoute: String?,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    val isHome = currentRoute == Screen.Home.route
    val isSearch = currentRoute == Screen.Search.route
    val isLibrary = currentRoute == Screen.Library.route
    val isSettings = currentRoute == Screen.Settings.route
    val isCloudMusicSettings = currentRoute == Screen.CloudMusicSettings.route

    // 统一动画参数：slide + fade，无 scale
    val enterAnim = slideInHorizontally(
        animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        initialOffsetX = { (it * 0.3f).toInt() }
    ) + fadeIn(
        animationSpec = tween(250, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    )

    val exitAnim = slideOutHorizontally(
        animationSpec = tween(250, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)),
        targetOffsetX = { -(it * 0.25f).toInt() }
    ) + fadeOut(
        animationSpec = tween(200, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))
    )

    // 4 个 tab 同时存在，只有匹配路由的可见
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isHome,
            enter = enterAnim,
            exit = exitAnim
        ) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                HomeScreen(
                    navController = navController,
                    paddingValuesParent = paddingValues,
                    playerViewModel = playerViewModel,
                    onOpenSidebar = onOpenSidebar
                )
            }
        }

        AnimatedVisibility(
            visible = isSearch,
            enter = enterAnim,
            exit = exitAnim
        ) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                SearchScreen(
                    paddingValues = paddingValues,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    onSearchBarActiveChange = onSearchBarActiveChange
                )
            }
        }

        AnimatedVisibility(
            visible = isLibrary,
            enter = enterAnim,
            exit = exitAnim
        ) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                LibraryScreen(navController = navController, playerViewModel = playerViewModel)
            }
        }

        AnimatedVisibility(
            visible = isSettings,
            enter = enterAnim,
            exit = exitAnim
        ) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                SettingsScreen(
                    navController = navController,
                    playerViewModel = playerViewModel,
                    onNavigationIconClick = { navController.popBackStack() }
                )
            }
        }

        AnimatedVisibility(
            visible = isCloudMusicSettings,
            enter = enterAnim,
            exit = exitAnim
        ) {
            ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                CloudMusicSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
