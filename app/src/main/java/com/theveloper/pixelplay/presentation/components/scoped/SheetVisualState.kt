package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

private const val PREDICTIVE_BACK_SWIPE_EDGE_LEFT = 0
private const val PREDICTIVE_BACK_SWIPE_EDGE_RIGHT = 1

internal data class SheetVisualState(
    val currentBottomPadding: Dp,
    val baseBottomPadding: Dp,
    /** Draw-phase provider: read this inside graphicsLayer to avoid layout relayout per frame. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Layout-phase provider: read inside .offset { } to avoid recomposition per drag frame. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    /** Draw-phase providers: read inside graphicsLayer to avoid layout relayout per frame. */
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
internal fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    predictiveBackSwipeEdge: Int?,
    currentSheetContentState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    // currentSheetTranslationY 已移除:translationY 直接由 fraction 计算
    sheetCollapsedTargetY: Float,
    navBarStyle: String,
    navBarCornerRadiusDp: Dp,
    isNavBarHidden: Boolean,
    isPlaying: Boolean,
    hasCurrentSong: Boolean,
    swipeDismissProgress: Float,
    navRailPadding: Dp = 0.dp
): SheetVisualState {
    // Compute in px to be read inside graphicsLayer (draw phase) — zero relayout per drag frame.
    val density = LocalDensity.current
    val navRailPaddingPx = with(density) { navRailPadding.toPx() }
    val baseBottomPadding = remember(containerHeight, sheetCollapsedTargetY, density) {
        val targetYDp = with(density) { sheetCollapsedTargetY.toDp() }
        (containerHeight - com.theveloper.pixelplay.presentation.components.MiniPlayerHeight - targetYDp)
            .coerceAtLeast(0.dp)
    }

    val currentBottomPadding = 0.dp

    val miniHeightPx = remember(density) { with(density) { com.theveloper.pixelplay.presentation.components.MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val baseBottomPaddingPx = remember(baseBottomPadding, density) { with(density) { baseBottomPadding.toPx() } }
    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    // ⚡ 简化:直接基于 playerContentExpansionFraction 计算 translationY
    // fraction=0 → 折叠态(sheetCollapsedTargetY)，fraction=1 → 展开态(0)
    val visualSheetTranslationYProvider: () -> Float = remember(
        playerContentExpansionFraction,
        sheetCollapsedTargetY
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val fraction = playerContentExpansionFraction.value.coerceIn(0f, 1f)
            val baseTranslationY = sheetCollapsedTargetY * (1f - fraction)
            // predictive back:向折叠态偏移
            baseTranslationY * (1f - progress) + (sheetCollapsedTargetY * progress)
        }
    }

    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        miniHeightPx,
        containerHeightPx,
        visualSheetTranslationYProvider,
        sheetCollapsedTargetY
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                val translationY = visualSheetTranslationYProvider()
                
                if (translationY <= sheetCollapsedTargetY) {
                    val targetBottom = androidx.compose.ui.util.lerp(
                        sheetCollapsedTargetY + miniHeightPx,
                        containerHeightPx,
                        safeFraction
                    )
                    (targetBottom - translationY).coerceAtLeast(0f)
                } else {
                    androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, safeFraction)
                }
            } else {
                0f
            }
        }
    }

    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        navBarStyle,
        navBarCornerRadiusDp,
        isNavBarHidden,
        swipeDismissProgress,
        currentSheetContentState
    ) {
        {
            val collapsedCornerTarget = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                navBarCornerRadiusDp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val expandedTarget = 0.dp
            val calculatedNormally = if (showPlayerContentArea) {
                lerp(collapsedCornerTarget, expandedTarget, safeFraction)
            } else {
                if (navBarStyle == NavBarStyle.DEFAULT) {
                    navBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }
            }

            calculatedNormally
        }
    }

    // isPlaying and hasCurrentSong are only used in the fallback branch when
    // !showPlayerContentArea. Reading them via rememberUpdatedState keeps the
    // shape provider lambda stable across play/pause toggles — so the
    // PlayerSheetDynamicShape instance (and the modifier chain that consumes it)
    // is not recreated on every isPlaying flip.
    val isPlayingState = rememberUpdatedState(isPlaying)
    val hasCurrentSongState = rememberUpdatedState(hasCurrentSong)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        swipeDismissProgress,
        isNavBarHidden,
        navBarCornerRadiusDp,
        currentSheetContentState
    ) {
        {
            val collapsedRadius = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                10.dp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val calculatedNormally =
                if (showPlayerContentArea) {
                    val expandedTarget = 0.dp
                    lerp(collapsedRadius, expandedTarget, safeFraction)
                } else {
                    if (!isPlayingState.value || !hasCurrentSongState.value) {
                        if (isNavBarHidden) {
                            32.dp
                        } else if (navBarStyle == NavBarStyle.DEFAULT) {
                            10.dp
                        } else {
                            navBarCornerRadiusDp
                        }
                    } else {
                        collapsedRadius
                    }
                }

            if (isNavBarHidden) {
                calculatedNormally
            } else if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    calculatedNormally
                } else if (navBarStyle == NavBarStyle.DEFAULT) {
                    lerp(32.dp, navBarCornerRadiusDp, swipeDismissProgress)
                } else {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else navBarCornerRadiusDp
                    lerp(baseCollapsedRadius, navBarCornerRadiusDp, swipeDismissProgress)
                }
            } else {
                calculatedNormally
            }
        }
    }

    val actualCollapsedStateHorizontalPadding =
        if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp else collapsedStateHorizontalPadding
    val collapsedStateHorizontalPaddingPx = remember(actualCollapsedStateHorizontalPadding, density) {
        with(density) { actualCollapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase lambda providers for horizontal padding — read inside graphicsLayer to avoid
    // per-frame relayout. The lambda captures Animatable/Float refs and reads them at draw time.
    // ⚡ 播放器容器现在是全屏的（移到了最外层 Box 中），所以折叠态需要 navRailPadding 让 mini-player
    // 位于 NavigationRail 右侧显示。展开态不需要 navRailPadding，让播放器全屏显示。
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        navRailPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                // 折叠态：navRailPadding + horizontalPadding；展开态：0
                val collapsedStartPadding = navRailPaddingPx + collapsedStateHorizontalPaddingPx
                androidx.compose.ui.util.lerp(collapsedStartPadding, 0f, safeFraction)
            } else {
                // 无内容区域时（无播放列表等），也要考虑 navRailPadding
                navRailPaddingPx + collapsedStateHorizontalPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, safeFraction)
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}
