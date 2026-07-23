package com.theveloper.pixelplay.presentation.navigation

import com.theveloper.pixelplay.presentation.screens.DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.EasterEggScreen
import com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen
import com.theveloper.pixelplay.presentation.screens.AccountsScreen
import com.theveloper.pixelplay.presentation.screens.ArtistDetailScreen
import com.theveloper.pixelplay.presentation.screens.ArtistHomepageScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DailyMixScreen
import com.theveloper.pixelplay.presentation.screens.AiMixScreen
import com.theveloper.pixelplay.presentation.screens.DotDeviceSettingsScreen
import com.theveloper.pixelplay.presentation.screens.EditTransitionScreen
import com.theveloper.pixelplay.presentation.screens.ExperimentalSettingsScreen
import com.theveloper.pixelplay.presentation.screens.GenreDetailScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.RecentlyPlayedScreen

import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.StatsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.screens.LxMusicScreen
import com.theveloper.pixelplay.presentation.screens.HeadphonePresetScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.components.ScreenWrapper

import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardScreen
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardScreen
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardScreen
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardScreen
import com.theveloper.pixelplay.presentation.telegram.dashboard.TelegramDashboardScreen

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    userPreferencesRepository: UserPreferencesRepository
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = userPreferencesRepository.launchTabFlow
            .first()
            .toRoute()
    }

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            enterTransition = { aospSharedAxisEnter() },
            exitTransition = { aospSharedAxisExit() },
            popEnterTransition = { aospSharedAxisPopEnter() },
            popExitTransition = { aospSharedAxisPopExit() }
        ) {
            // Tab 路由: 空占位符，实际内容由 TabContentHost 渲染（预加载）
            composable(
                Screen.Home.route,
                enterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = enterTransition()
                ) },
                exitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = exitTransition()
                ) },
                popEnterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popEnterTransition()
                ) },
                popExitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popExitTransition()
                ) }
            ) { }
            composable(
                Screen.Search.route,
                enterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = enterTransition()
                ) },
                exitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = exitTransition()
                ) },
                popEnterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popEnterTransition()
                ) },
                popExitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popExitTransition()
                ) }
            ) { }
            composable(
                Screen.Library.route,
                enterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = enterTransition()
                ) },
                exitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = exitTransition()
                ) },
                popEnterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popEnterTransition()
                ) },
                popExitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popExitTransition()
                ) }
            ) { }
            composable(
                Screen.Settings.route,
                enterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = enterTransition()
                ) },
                exitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = exitTransition()
                ) },
                popEnterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popEnterTransition()
                ) },
                popExitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popExitTransition()
                ) }
            ) { }
            composable(
                Screen.CloudMusicSettings.route,
                enterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = enterTransition()
                ) },
                exitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = exitTransition()
                ) },
                popEnterTransition = { mainRootEnterTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popEnterTransition()
                ) },
                popExitTransition = { mainRootExitTransition(
                    fromRoute = initialState.destination.route,
                    toRoute = targetState.destination.route,
                    fallback = popExitTransition()
                ) }
            ) { }
            composable(
                Screen.Accounts.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AccountsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenNeteaseDashboard = {
                            navController.navigateSafely(Screen.NeteaseDashboard.route)
                        },
                        onOpenQqMusicDashboard = {
                            navController.navigateSafely(Screen.QqMusicDashboard.route)
                        },
                        onOpenNavidromeDashboard = {
                            navController.navigateSafely(Screen.NavidromeDashboard.route)
                        },
                        onOpenJellyfinDashboard = {
                            navController.navigateSafely(Screen.JellyfinDashboard.route)
                        }
                    )
                }
            }
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val categoryId = backStackEntry.arguments?.getString("categoryId")
                    if (categoryId != null) {
                        SettingsCategoryScreen(
                            categoryId = categoryId,
                            navController = navController,
                            playerViewModel = playerViewModel,
                            settingsViewModel = hiltViewModel(),
                            statsViewModel = hiltViewModel(),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
            composable(
                Screen.Equalizer.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EqualizerScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        equalizerViewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.HeadphonePreset.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    HeadphonePresetScreen(
                        navController = navController,
                        viewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.Stats.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    StatsScreen(
                        navController = navController
                    )
                }
            }
            composable(
                Screen.About.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AboutScreen(
                        navController = navController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.RecentlyPlayed.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    RecentlyPlayedScreen(
                        playerViewModel = playerViewModel,
                        playlistViewModel = hiltViewModel(),
                        navController = navController
                    )
                }
            }
            composable(
                Screen.DailyMixScreen.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DailyMixScreen(
                        mainViewModel = hiltViewModel(),
                        playlistViewModel = hiltViewModel(),
                        playerViewModel = playerViewModel,
                        navController = navController,
                    )
                }
            }
            composable(
                Screen.AiMixScreen.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AiMixScreen(
                        mainViewModel = hiltViewModel(),
                        playlistViewModel = hiltViewModel(),
                        playerViewModel = playerViewModel,
                        navController = navController,
                    )
                }
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val playlistId = backStackEntry.arguments?.getString("playlistId")
                    if (playlistId != null) {
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            onBackClick = { navController.popBackStack() },
                            onDeletePlayListClick = { /* playlist deletion */ },
                            playerViewModel = playerViewModel,
                            playlistViewModel = hiltViewModel(),
                            navController = navController
                        )
                    }
                }
            }
            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val albumId = backStackEntry.arguments?.getString("albumId")
                    if (albumId != null) {
                        AlbumDetailScreen(
                            navController = navController,
                            playerViewModel = playerViewModel,
                            playlistViewModel = hiltViewModel(),
                            albumId = albumId
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val artistId = backStackEntry.arguments?.getString("artistId")
                    if (artistId != null) {
                        ArtistDetailScreen(
                            navController = navController,
                            playerViewModel = playerViewModel,
                            playlistViewModel = hiltViewModel(),
                            artistId = artistId
                        )
                    }
                }
            }
            // 网易云歌手主页
            composable(
                route = Screen.ArtistHomepage.route,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                    if (artistId > 0L) {
                        ArtistHomepageScreen(
                            artistId = artistId,
                            artistName = null,
                            artistAvatar = null,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.GenreDetail.route,
                arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val genreId = backStackEntry.arguments?.getString("genreId")
                    if (genreId != null) {
                        GenreDetailScreen(
                            navController = navController,
                            playerViewModel = playerViewModel,
                            playlistViewModel = hiltViewModel(),
                            genreId = genreId
                        )
                    }
                }
            }
            composable(
                Screen.Experimental.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExperimentalSettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() },
                        settingsViewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.PaletteStyle.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        settingsViewModel = hiltViewModel(),
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSettingsScreen(
                        navController = navController,
                        viewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.DelimiterConfig.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DelimiterConfigScreen(
                        navController = navController,
                        viewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    WordDelimiterConfigScreen(
                        navController = navController,
                        viewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.EasterEgg.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EasterEggScreen(
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val playlistId = backStackEntry.arguments?.getString("playlistId")
                    EditTransitionScreen(
                        navController = navController,
                        viewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.DJSpace.route,
            ) {
                // DJSpace placeholder – no standalone screen currently
            }
            composable(
                Screen.NavBarCrRad.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NavBarCornerRadiusScreen(
                        navController = navController,
                        settingsViewModel = hiltViewModel()
                    )
                }
            }
            composable(
                Screen.DeviceCapabilities.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DeviceCapabilitiesScreen(
                        navController = navController,
                        viewModel = hiltViewModel(),
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.NeteaseDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NeteaseDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.QqMusicDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    QqMusicDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.NavidromeDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NavidromeDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.JellyfinDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    JellyfinDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.DotDeviceSettings.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DotDeviceSettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

        }
    }
}

private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

private enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

// Base duration for bottom-nav switches at 1x — at 0.5x system scale = ~190 ms.
private const val BOTTOM_NAV_TRANSITION_DURATION = 380

// MD3 Expressive easing for bottom-nav switches
private val BottomNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val MAIN_ROOT_TRANSITION_SPEC =
    tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = BottomNavEasing)

private val MAIN_ROOT_FADE_SPEC =
    tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION / 2, easing = BottomNavEasing)

private fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { (it * 0.5f).toInt() }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        // 返回/反方向切换时，目标页面保持不动，避免露出黑底
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { 0 }
        )
    }
    null -> fallback
}

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { (it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}
