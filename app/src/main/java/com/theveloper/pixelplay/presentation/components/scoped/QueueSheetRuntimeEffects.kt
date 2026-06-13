package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Hosts lifecycle effects for queue sheet synchronization and haptic feedback.
 * Behavior mirrors the previous inline effects in UnifiedPlayerSheet.
 */
@Composable
internal fun QueueSheetRuntimeEffects(
    queueSheetController: QueueSheetController,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueHiddenOffsetPx: Float,
    showQueueSheet: Boolean,
    allowQueueSheetInteraction: Boolean,
    onTopEdgeReached: () -> Unit
) {
    val onTopEdgeReachedState = rememberUpdatedState(onTopEdgeReached)
    val queueHiddenOffsetPxState = rememberUpdatedState(queueHiddenOffsetPx)

    LaunchedEffect(queueHiddenOffsetPx) {
        queueSheetController.syncOffsetToVisibility()
    }

    LaunchedEffect(showQueueSheet, queueHiddenOffsetPx) {
        queueSheetController.syncCollapsedWhenHidden()
    }

    // Use .first{} instead of collectLatest so the haptic fires exactly once per open:
    // the spring animation can overshoot (offset briefly > 0.5f after first crossing it),
    // which would reset hasHitTopEdge in a collectLatest loop and cause a second haptic.
    LaunchedEffect(showQueueSheet) {
        if (!showQueueSheet || queueSheetOffset.value <= 0.5f) return@LaunchedEffect
        snapshotFlow { queueSheetOffset.value to queueHiddenOffsetPxState.value }
            .first { (offset, hiddenOffset) -> hiddenOffset > 0f && offset <= 0.5f }
        onTopEdgeReachedState.value()
    }

    LaunchedEffect(allowQueueSheetInteraction, queueHiddenOffsetPx) {
        queueSheetController.forceCollapseIfInteractionDisabled()
    }
}
