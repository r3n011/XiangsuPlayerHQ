package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Encapsulates vertical drag gesture state and target resolution for the player sheet.
 * Simplified: only playerContentExpansionFraction is animated. translationY is derived
 * from fraction at draw time (graphicsLayer), not tracked as a separate Animatable.
 */
internal class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val sheetMotionController: SheetMotionController,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onDraggingPlayerAreaChange: (Boolean) -> Unit,
    private val onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragSnapJob: Job? = null

    fun onDragStart() {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.stop()
        }
        onDraggingChange(true)
        onDraggingPlayerAreaChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        // Derive initial Y from fraction instead of reading a separate Animatable
        initialYOnDragStart = collapsedYProvider() * (1f - initialFractionOnDragStart)
        accumulatedDragYSinceStart = 0f
    }

    fun onVerticalDrag(
        uptimeMillis: Long,
        position: Offset,
        dragAmount: Float
    ) {
        accumulatedDragYSinceStart += dragAmount
        // Compute current Y from fraction (single source of truth)
        val currentTranslationY = collapsedYProvider() * (1f - playerContentExpansionFraction.value)
        val dragFrame = computeSheetVerticalDragFrame(
            currentTranslationY = currentTranslationY,
            dragAmount = dragAmount,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Only snap fraction — Y is derived from fraction in graphicsLayer
            sheetMotionController.snapTo(dragFrame.expansionFraction)
        }
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)

        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        val velocityThreshold = 150f

        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = velocityThreshold,
            currentFraction = currentFraction
        )

        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                launch {
                    onAnimateSheet(true, null, 0f)
                }
                onExpandSheetState()
            } else {
                launch {
                    onAnimateSheet(
                        false,
                        spring(
                            dampingRatio = collapseSpringDampingForFraction(currentFraction),
                            stiffness = Spring.StiffnessLow
                        ),
                        verticalVelocity
                    )
                }
                onCollapseSheetState()
            }
        }

        accumulatedDragYSinceStart = 0f
    }

    fun onDragCancel() {
        onDragEnd()
    }
}

internal fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(
                    uptimeMillis = change.uptimeMillis,
                    position = change.position,
                    dragAmount = dragAmount
                )
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
