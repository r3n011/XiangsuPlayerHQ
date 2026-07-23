package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.IntOffset

private const val TabTransitionDuration = 380
private val TabEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val TabTransitionSpec = tween<IntOffset>(durationMillis = TabTransitionDuration, easing = TabEasing)
private val TabFadeSpec = tween<Float>(durationMillis = TabTransitionDuration / 2, easing = TabEasing)

@Composable
internal fun TabContentHost(
    currentRoute: String?,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            val fromRoute = initialState
            val toRoute = targetState
            val dir = tabDirection(fromRoute, toRoute)
            when (dir) {
                TabDirection.FORWARD -> (
                    slideInHorizontally(
                        animationSpec = TabTransitionSpec,
                        initialOffsetX = { (it * 0.5f).toInt() }
                    ) + fadeIn(animationSpec = TabFadeSpec)
                ) togetherWith (
                    slideOutHorizontally(
                        animationSpec = TabTransitionSpec,
                        targetOffsetX = { -(it * 0.5f).toInt() }
                    ) + fadeOut(animationSpec = TabFadeSpec)
                )
                TabDirection.BACKWARD -> (
                    slideInHorizontally(
                        animationSpec = TabTransitionSpec,
                        initialOffsetX = { -(it * 0.5f).toInt() }
                    ) + fadeIn(animationSpec = TabFadeSpec)
                ) togetherWith (
                    slideOutHorizontally(
                        animationSpec = TabTransitionSpec,
                        targetOffsetX = { (it * 0.5f).toInt() }
                    ) + fadeOut(animationSpec = TabFadeSpec)
                )
                null -> fadeIn(animationSpec = tween(TabTransitionDuration / 2)) togetherWith
                        fadeOut(animationSpec = tween(TabTransitionDuration / 2))
            }
        },
        modifier = Modifier,
        label = "TabTransition"
    ) { route ->
        when (route) {
            Screen.Home.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    HomeScreen(
                        navController = navController,
                        paddingValuesParent = paddingValues,
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    )
                }
            }
            Screen.Search.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    )
                }
            }
            Screen.Library.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
            }
            Screen.Settings.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    SettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            Screen.CloudMusicSettings.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    CloudMusicSettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private enum class TabDirection {
    FORWARD,
    BACKWARD
}

private fun tabRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    Screen.Settings.route -> 3
    Screen.CloudMusicSettings.route -> 4
    else -> null
}

private fun tabDirection(previous: String?, current: String?): TabDirection? {
    val fromIndex = tabRouteIndex(previous) ?: return null
    val toIndex = tabRouteIndex(current) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) TabDirection.FORWARD else TabDirection.BACKWARD
}
