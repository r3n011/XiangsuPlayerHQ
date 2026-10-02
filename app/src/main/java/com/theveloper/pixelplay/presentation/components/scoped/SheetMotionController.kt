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
    /** 当前占用 mutex 的程序类型：展开/折叠动画、锚点微调弹簧，或空闲。 */
    private enum class Program { NONE, MOTION, SYNC }

    private var runningProgram = Program.NONE
    private var lastTargetExpanded = false
    private var lastCanExpand = true

    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        lastTargetExpanded = targetExpanded
        lastCanExpand = canExpand
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
            runningProgram = Program.MOTION
            try {
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
            } finally {
                runningProgram = Program.NONE
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        mutex.mutate {
            runningProgram = Program.NONE
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
     * 把 mini player 平移到最新 collapsed 目标（保持当前展开比例不变）。
     * ⚡ 弹簧动画（带过冲）：导航栏隐藏/显示导致 collapsed 目标变化时，
     *   mini player 下移/上移以带过冲的 spring 过渡，不再瞬跳。
     * 已就位（首帧组合/展开态）时无操作。
     *
     * ⚡ 展开/折叠动画在飞行中时不能走这条路：两个 Animatable 共用一把
     *   MutatorMutex，这里直接 mutate 会把折叠动画连同 expansionFraction 一起
     *   掐断在中间值——translationY 停在半路、expansionFraction 冻结，sheet 就
     *   有概率卡在屏幕中央（折叠过程中导航栏重新出现、collapsedY 变化时触发）。
     *   正确做法是用新的 collapsedY 续跑同一个展开/折叠程序。
     */
    suspend fun syncToExpansion(collapsedY: Float) {
        if (
            runningProgram == Program.MOTION &&
            (translationY.isRunning || expansionFraction.isRunning)
        ) {
            animateTo(
                targetExpanded = lastTargetExpanded,
                canExpand = lastCanExpand,
                collapsedY = collapsedY,
                initialVelocity = translationY.velocity
            )
            return
        }
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            runningProgram = Program.SYNC
            try {
                translationY.animateTo(
                    targetValue = adjustedY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } finally {
                runningProgram = Program.NONE
            }
        }
    }
}
