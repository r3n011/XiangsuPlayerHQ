package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal data class FullPlayerCompositionPolicy(
    val shouldRenderFullPlayer: Boolean
)

/**
 * Decides whether the full-player composable tree should be in composition.
 *
 * Accepts [Animatable] instead of a raw Float so that the expansion fraction is
 * read inside [derivedStateOf] / [snapshotFlow] — never as a `remember` key or
 * `LaunchedEffect` key. This prevents per-frame recomposition of the caller during
 * sheet drag gestures.
 */
@Composable
internal fun rememberFullPlayerCompositionPolicy(
    currentSongId: String?,
    currentSheetState: PlayerSheetState,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    collapsedWarmDelayMs: Long = 300L,
    expandAnimationSettleDelayMs: Long = 200L
): FullPlayerCompositionPolicy {
    // 不用 currentSongId 作 remember key：一旦组合过就保持挂载，切歌/收起都不销毁主层，
    // 避免展开动画期间封面与歌曲信息因整棵重建而闪烁。
    var keepFullPlayerComposed by remember { mutableStateOf(false) }

    LaunchedEffect(currentSongId, currentSheetState) {
        if (currentSongId == null) {
            keepFullPlayerComposed = false
            return@LaunchedEffect
        }
        if (keepFullPlayerComposed) {
            return@LaunchedEffect
        }
        if (currentSheetState == PlayerSheetState.EXPANDED) {
            delay(expandAnimationSettleDelayMs)
        } else {
            delay(collapsedWarmDelayMs)
        }
        keepFullPlayerComposed = true
    }

    val shouldRenderFullPlayer by remember {
        derivedStateOf {
            currentSongId != null && keepFullPlayerComposed
        }
    }

    return FullPlayerCompositionPolicy(
        shouldRenderFullPlayer = shouldRenderFullPlayer
    )
}
