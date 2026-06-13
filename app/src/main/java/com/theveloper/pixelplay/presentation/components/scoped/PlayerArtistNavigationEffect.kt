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
            // ⚡ 简化:snap fraction 到 0f（折叠态），然后导航
            latestExpansionFraction.value.snapTo(0f)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.ArtistDetail.createRoute(artistId),
                patternToPop = Screen.ArtistDetail.route
            )
        }
    }
}
