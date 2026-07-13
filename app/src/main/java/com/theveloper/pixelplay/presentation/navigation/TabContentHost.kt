package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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

private const val TabTransitionDuration = 150

@Composable
internal fun TabContentHost(
    currentRoute: String?,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    Crossfade(
        targetState = currentRoute,
        animationSpec = tween(TabTransitionDuration),
        modifier = Modifier,
        label = "TabTransition"
    ) { route ->
        when (route) {
            Screen.Home.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    HomeScreen(
                        navController = navController,
                        paddingValuesParent = paddingValues,
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    )
                }
            }
            Screen.Search.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    )
                }
            }
            Screen.Library.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
            }
            Screen.Settings.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            Screen.CloudMusicSettings.route -> {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    CloudMusicSettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
