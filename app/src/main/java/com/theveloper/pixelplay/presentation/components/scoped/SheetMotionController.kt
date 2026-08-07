package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Centralizes sheet motion updates so animation/snap logic lives in one place.
 * This keeps behavior stable while enabling an incremental V2 rewrite.
 */
internal class SheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>,
    private val expandedY: Float = 0f
) {
    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (canExpand && targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }

        mutex.mutate {
            coroutineScope {
                launch {
                    translationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec
                    )
                }
                launch {
                    expansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = animationSpec
                    )
                }
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }

    suspend fun snapCollapsed(collapsedY: Float) {
        snapTo(
            translationYValue = collapsedY,
            expansionFractionValue = 0f
        )
    }

    /**
     * 导航栏显示/隐藏（或窗口尺寸变化）导致折叠目标位置变化时调用。
     * 之前用 snapTo 直接跳到新位置，mini player 会"硬切"。
     * 改为带轻微回弹的弹簧动画，让 mini player 平滑滑到新位置，q 弹不突兀。
     */
    suspend fun syncToExpansion(collapsedY: Float) {
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            translationY.animateTo(
                targetValue = adjustedY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
}
