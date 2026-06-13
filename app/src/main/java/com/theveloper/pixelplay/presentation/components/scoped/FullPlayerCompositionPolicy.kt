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
    collapsedWarmDelayMs: Long = 650L
): FullPlayerCompositionPolicy {
    var keepFullPlayerComposed by remember(currentSongId) { mutableStateOf(false) }

    LaunchedEffect(currentSongId, currentSheetState) {
        if (currentSongId == null) {
            keepFullPlayerComposed = false
            return@LaunchedEffect
        }

        if (currentSheetState == PlayerSheetState.EXPANDED) {
            // ⚡ 关键优化：展开状态时，等待动画完全结束后再开始渲染内容
            // 动画期间（0% → 100%）：只渲染背景/容器，避免内容加载导致卡顿
            // expansionFraction 从 0f → 1f 动画完成后，再设置 keepFullPlayerComposed = true
            snapshotFlow { expansionFraction.value >= 0.99f }
                .first { it }
            keepFullPlayerComposed = true
        } else {
            // Warm the hidden full-player tree after the collapsed state settles.
            // This moves the expensive first composition out of the expand animation.
            delay(collapsedWarmDelayMs)
            keepFullPlayerComposed = true
        }
    }

    // ⚡ 只在 keepFullPlayerComposed = true 时渲染完整播放器内容
    // keepFullPlayerComposed 的设置时机：
    //   1. 展开动画完全结束后（expansionFraction >= 0.99f）
    //   2. 折叠状态稳定后（650ms 延迟，预热）
    // 这样确保动画期间只渲染背景，完全避免内容加载和动画的主线程资源竞争
    val shouldRenderFullPlayer by remember(currentSongId) {
        derivedStateOf {
            currentSongId != null && keepFullPlayerComposed
        }
    }

    return FullPlayerCompositionPolicy(
        shouldRenderFullPlayer = shouldRenderFullPlayer
    )
}
