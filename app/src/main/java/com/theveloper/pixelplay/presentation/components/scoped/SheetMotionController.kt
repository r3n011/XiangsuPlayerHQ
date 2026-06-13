package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex

/**
 * Simplified SheetMotionController: only animates the playerContentExpansionFraction.
 * translationY is derived from fraction in graphicsLayer at draw time.
 * This avoids the previous "two-value sync" problem (translationY vs expansionFraction
 * getting out of sync during animation interrupts).
 */
internal class SheetMotionController(
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>
) {
    suspend fun animateTo(
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f
        mutex.mutate {
            playerContentExpansionFraction.animateTo(
                targetValue = targetFraction,
                initialVelocity = initialVelocity,
                animationSpec = animationSpec
            )
        }
    }

    suspend fun stop() {
        playerContentExpansionFraction.stop()
    }

    suspend fun snapTo(expansionFractionValue: Float) {
        mutex.mutate {
            playerContentExpansionFraction.snapTo(expansionFractionValue)
        }
    }
}
