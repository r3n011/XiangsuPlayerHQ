package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max

internal data class SheetOverlayState(
    val internalIsKeyboardVisible: Boolean,
    val actuallyShowSheetContent: Boolean,
    val isQueueVisible: Boolean,
    val queueVisualOpenFraction: Float,
    val bottomSheetOpenFraction: Float,
    val queueScrimAlpha: Float
)

@Composable
internal fun rememberSheetOverlayState(
    density: Density,
    showPlayerContentArea: Boolean,
    hideMiniPlayer: Boolean,
    showQueueSheet: Boolean,
    isQueueCollapsing: Boolean,
    queueHiddenOffsetPx: Float,
    screenHeightPx: Float,
    castSheetOpenFraction: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queuePredictiveBackProgress: Animatable<Float, AnimationVector1D>
): SheetOverlayState {
    var internalIsKeyboardVisible by remember { mutableStateOf(false) }

    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .distinctUntilChanged()
            .collectLatest { isVisible ->
                if (internalIsKeyboardVisible != isVisible) {
                    internalIsKeyboardVisible = isVisible
                }
            }
    }

    val shouldShowSheet by remember(showPlayerContentArea, hideMiniPlayer) {
        derivedStateOf { showPlayerContentArea && !hideMiniPlayer }
    }

    // Keep the sheet mounted while IME is visible to avoid mini-player flicker/recomposition
    // when the keyboard opens/closes (notably in Search).
    val actuallyShowSheetContent by remember(shouldShowSheet) {
        derivedStateOf { shouldShowSheet }
    }

    val isQueueVisible by remember(showQueueSheet, queueHiddenOffsetPx, screenHeightPx) {
        derivedStateOf {
            showQueueSheet &&
                queueHiddenOffsetPx > 0f &&
                screenHeightPx > 0f
        }
    }

    val queueVisualOpenFractionState = remember(
        showQueueSheet,
        isQueueCollapsing,
        queueSheetOffset,
        queueHiddenOffsetPx,
        queuePredictiveBackProgress
    ) {
        derivedStateOf {
            if ((showQueueSheet || isQueueCollapsing) && queueHiddenOffsetPx > 0f) {
                val dragFraction = (1f - (queueSheetOffset.value / queueHiddenOffsetPx)).coerceIn(0f, 1f)
                val backFraction = 1f - queuePredictiveBackProgress.value
                dragFraction * backFraction
            } else {
                0f
            }
        }
    }
    val queueVisualOpenFraction by queueVisualOpenFractionState
 
    val bottomSheetOpenFractionState = remember(queueVisualOpenFractionState, castSheetOpenFraction) {
        derivedStateOf { max(queueVisualOpenFractionState.value, castSheetOpenFraction) }
    }
    val bottomSheetOpenFraction by bottomSheetOpenFractionState
 
    val queueScrimAlphaState = remember(queueVisualOpenFractionState) {
        derivedStateOf {
            (queueVisualOpenFractionState.value * 0.45f).coerceIn(0f, 0.45f)
        }
    }
    val queueScrimAlpha by queueScrimAlphaState

    return SheetOverlayState(
        internalIsKeyboardVisible = internalIsKeyboardVisible,
        actuallyShowSheetContent = actuallyShowSheetContent,
        isQueueVisible = isQueueVisible,
        queueVisualOpenFraction = queueVisualOpenFraction,
        bottomSheetOpenFraction = bottomSheetOpenFraction,
        queueScrimAlpha = queueScrimAlpha
    )
}
