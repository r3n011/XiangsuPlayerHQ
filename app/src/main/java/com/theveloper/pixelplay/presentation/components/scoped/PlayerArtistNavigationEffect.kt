package com.theveloper.pixelplay.presentation.components.scoped

import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerArtistNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController?,
    playerViewModel: PlayerViewModel
) {
    val latestExpansionFraction = rememberUpdatedState(playerViewModel.playerContentExpansionFraction)
    LaunchedEffect(navController) {
        playerViewModel.artistNavigationRequests.collectLatest { artistId ->
            latestExpansionFraction.value.snapTo(0f)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.ArtistDetail.createRoute(artistId),
                patternToPop = Screen.ArtistDetail.route
            )
        }
    }
    LaunchedEffect(navController) {
        playerViewModel.neteaseArtistNavigationRequests.collectLatest { artistId ->
            latestExpansionFraction.value.snapTo(0f)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.ArtistHomepage.createRoute(artistId),
                patternToPop = Screen.ArtistHomepage.route
            )
        }
    }
}
