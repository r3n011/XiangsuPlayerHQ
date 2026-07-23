package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex

/**
 * Optimized SheetMotionController:
 * - Drives the single source of truth: playerContentExpansionFraction
 * - Uses spring animations for gesture-natural feel and seamless interruption
 * - Unified spring spec for both expand and collapse to avoid visual pops when
 *   the target direction changes mid-animation
 * - Smart initialVelocity: inherits the Animatable's current velocity so gesture
 *   releases feel continuous instead of resetting to zero velocity
 * - Exposes isRunning / currentFraction for callers that need to inspect state
 */
internal class SheetMotionController(
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>
) {

    companion object {
        // ⚡ Unified sheet spring: medium bouncy with medium-low stiffness.
        //    Used for both expand and collapse so direction changes never cause a spec swap/pop.
        val SheetSpringSpec: AnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    /** Whether an animation is currently running. */
    val isRunning: Boolean
        get() = playerContentExpansionFraction.isRunning

    /** Current expansion fraction in [0, 1]. */
    val currentFraction: Float
        get() = playerContentExpansionFraction.value

    /** Velocity of the current animation (0f if idle). */
    val currentVelocity: Float
        get() = playerContentExpansionFraction.velocity

    /**
     * Animate to expanded (1f) or collapsed (0f).
     *
     * Chooses direction-appropriate spring by default.
     * If `animationSpec` is provided by the caller, it is used instead
     * (e.g. SheetVerticalDragGestureHandler passes a spring derived from the user's gesture velocity).
     */
    suspend fun animateTo(
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>? = null,
        initialVelocity: Float = Float.NaN
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f

        // Skip if already at target (avoids a redundant animation that produces a recomposition)
        if (!playerContentExpansionFraction.isRunning &&
            playerContentExpansionFraction.value == targetFraction) {
            return
        }

        val effectiveVelocity = if (initialVelocity.isNaN()) {
            // Inherit current animatable velocity so gesture releases feel continuous
            playerContentExpansionFraction.velocity.coerceIn(-2f, 2f)
        } else {
            initialVelocity
        }

        val effectiveSpec = animationSpec ?: SheetSpringSpec

        mutex.mutate {
            playerContentExpansionFraction.animateTo(
                targetValue = targetFraction,
                initialVelocity = effectiveVelocity,
                animationSpec = effectiveSpec
            )
        }
    }

    /**
     * Animate to an arbitrary fraction value. Useful for custom states (half-open etc.)
     */
    suspend fun animateToFraction(
        targetFraction: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = Float.NaN
    ) {
        val clamped = targetFraction.coerceIn(0f, 1f)
        if (!playerContentExpansionFraction.isRunning &&
            playerContentExpansionFraction.value == clamped) {
            return
        }

        val effectiveVelocity = if (initialVelocity.isNaN()) {
            playerContentExpansionFraction.velocity.coerceIn(-2f, 2f)
        } else {
            initialVelocity
        }

        mutex.mutate {
            playerContentExpansionFraction.animateTo(
                targetValue = clamped,
                initialVelocity = effectiveVelocity,
                animationSpec = animationSpec
            )
        }
    }

    /** Stop any running animation. Called at the start of a drag gesture. */
    suspend fun stop() {
        playerContentExpansionFraction.stop()
    }

    /** Jump to a fraction without animation. Called on every drag movement frame. */
    suspend fun snapTo(expansionFractionValue: Float) {
        mutex.mutate {
            playerContentExpansionFraction.snapTo(expansionFractionValue.coerceIn(0f, 1f))
        }
    }
}
