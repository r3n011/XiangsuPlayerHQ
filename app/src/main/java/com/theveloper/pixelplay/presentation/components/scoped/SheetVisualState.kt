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
    /** Draw-phase provider：滚动隐藏底栏时迷你条的额外下移量，必须只在 graphicsLayer 里读 */
    val sheetExtraShiftPxProvider: () -> Float,
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
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    navBarStyle: String,
    navBarCornerRadiusDp: Dp,
    isNavBarHidden: Boolean,
    isPlaying: Boolean,
    hasCurrentSong: Boolean,
    swipeDismissProgress: Float,
    navRailPadding: Dp = 0.dp,
    isLandscape: Boolean = false,
    /** 播放器容器的宽度（px），用于计算折叠态最大宽度 */
    containerWidthPx: Float = 0f,
    /** 折叠态迷你条最大宽度（px，<=0 表示不限制）。仅宽屏横屏时限制，避免被拉成一条长带 */
    collapsedMaxWidthPx: Float = 0f,
    /** 折叠态限制宽度后，多余空间放到左侧（右对齐）；否则左右均分（居中） */
    collapsedAlignEnd: Boolean = false,
    /** 折叠态使用完整胶囊形状（圆角 = 迷你条高度的一半），展开时插值回方角 */
    collapsedCapsule: Boolean = false,
    /** 滚动隐藏时迷你条的下移量（px），仅折叠态生效；展开播放器时自动归零 */
    miniPlayerScrollShiftPxProvider: () -> Float = { 0f }
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
    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            currentSheetTranslationY.value * (1f - progress) +
                (sheetCollapsedTargetY * progress)
        }
    }

    /**
     * 滚动隐藏底栏时迷你条的「额外下移量」（px）。
     *
     * ⚡ 必须放在**绘制阶段**消费（graphicsLayer.translationY），不能混进
     *   [visualSheetTranslationYProvider]：后者在 `Modifier.layout` 里被读取，
     *   而滚动隐藏进度是逐帧变化的 —— 混进去会导致整个播放器面板每帧重新测量/布局
     *   （表现就是"媒体库上滑、底栏收起时卡顿"）。
     *   展开成全屏播放器时 expansionFraction→1，位移自动归零。
     */
    val sheetExtraShiftPxProvider: () -> Float = remember(miniPlayerScrollShiftPxProvider) {
        { miniPlayerScrollShiftPxProvider() * (1f - playerContentExpansionFraction.value) }
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
        currentSheetContentState,
        collapsedCapsule
    ) {
        {
            val collapsedCornerTarget = if (collapsedCapsule) {
                com.theveloper.pixelplay.presentation.components.MiniPlayerHeight / 2
            } else if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                navBarCornerRadiusDp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else if (navBarStyle == NavBarStyle.FLOATING) {
                // ⚡ mini player 圆角跟随用户设置的导航栏圆角：悬浮底栏不应擅自改变
                //   mini player 的形状（此前硬编码 28.dp，开了悬浮底栏圆角就变小）
                navBarCornerRadiusDp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val safeFraction = effectiveFraction.coerceIn(0f, 1f)
            val expandedTarget = 0.dp
            val calculatedNormally = if (showPlayerContentArea) {
                lerp(collapsedCornerTarget, expandedTarget, safeFraction)
            } else {
                if (collapsedCapsule) {
                    com.theveloper.pixelplay.presentation.components.MiniPlayerHeight / 2
                } else if (navBarStyle == NavBarStyle.DEFAULT) {
                    navBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (navBarStyle == NavBarStyle.FLOATING) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }
            }

            // ⚡ 横向拖拽 mini player（dismiss 手势）时，顶部圆角随拖拽进度平滑增大到 32.dp，
            //    与底部圆角联动（卡片感）；松手后 offset 回弹动画带动 progress 平滑归零，
            //    progress=0 时值=collapsedCornerTarget 与常态连续，无瞬跳。
            if (showPlayerContentArea &&
                currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                safeFraction < 0.01f
            ) {
                lerp(collapsedCornerTarget, 32.dp, swipeDismissProgress)
            } else {
                calculatedNormally
            }
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
        currentSheetContentState,
        isLandscape,
        collapsedCapsule
    ) {
        {
            // In landscape (tablet) mode: bottom radius matches top radius
            // (now playing bar is a floating card, not above a nav bar).
            // In portrait: bottom radius matches nav bar top (10.dp for DEFAULT).
            val collapsedRadius = if (collapsedCapsule) {
                com.theveloper.pixelplay.presentation.components.MiniPlayerHeight / 2
            } else if (isNavBarHidden) {
                32.dp
            } else if (isLandscape && navBarStyle == NavBarStyle.DEFAULT) {
                navBarCornerRadiusDp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                10.dp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else if (navBarStyle == NavBarStyle.FLOATING) {
                // ⚡ 与顶部圆角一致：跟随用户设置，悬浮底栏不改变 mini player 形状
                navBarCornerRadiusDp
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
                        } else if (isLandscape && navBarStyle == NavBarStyle.DEFAULT) {
                            navBarCornerRadiusDp
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
                } else if (navBarStyle == NavBarStyle.FLOATING) {
                    calculatedNormally
                } else if (navBarStyle == NavBarStyle.DEFAULT && isLandscape) {
                    // Landscape: bottom radius always matches top (navBarCornerRadiusDp)
                    navBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.DEFAULT) {
                    // ⚡ 横向拖拽 mini player：底部圆角从常规值平滑增大到 32.dp（卡片感），
                    //    松手后 offsetAnimatable 回弹动画带动 swipeDismissProgress 平滑归零，
                    //    圆角随之平滑恢复，progress=0 时值=navBarCornerRadiusDp 与常态连续，无瞬跳。
                    lerp(navBarCornerRadiusDp, 32.dp, swipeDismissProgress)
                } else {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else navBarCornerRadiusDp
                    lerp(baseCollapsedRadius, 32.dp, swipeDismissProgress)
                }
            } else {
                calculatedNormally
            }
        }
    }

    val actualCollapsedStateHorizontalPadding =
        if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp
        else if (navBarStyle == NavBarStyle.FLOATING) 14.dp
        else collapsedStateHorizontalPadding
    val collapsedStateHorizontalPaddingPx = remember(actualCollapsedStateHorizontalPadding, density) {
        with(density) { actualCollapsedStateHorizontalPadding.toPx() }
    }

    // ⚡ 平板/横屏下限制迷你条最大宽度（对齐 Rhythm 的做法）：把多余的水平空间折算成折叠态内边距，
    //    展开时随 safeFraction 插值归零，不影响全屏播放器。
    val collapsedExtraPaddingPx = remember(
        containerWidthPx,
        collapsedMaxWidthPx,
        navRailPaddingPx,
        collapsedStateHorizontalPaddingPx,
        collapsedAlignEnd
    ) {
        if (collapsedMaxWidthPx <= 0f || containerWidthPx <= 0f) {
            0f
        } else {
            val available = containerWidthPx - navRailPaddingPx - collapsedStateHorizontalPaddingPx * 2f
            (available - collapsedMaxWidthPx).coerceAtLeast(0f)
        }
    }
    val collapsedExtraStartPaddingPx =
        if (collapsedAlignEnd) collapsedExtraPaddingPx else collapsedExtraPaddingPx / 2f
    val collapsedExtraEndPaddingPx =
        if (collapsedAlignEnd) 0f else collapsedExtraPaddingPx / 2f

    // Draw-phase lambda providers for horizontal padding — read inside graphicsLayer to avoid
    // per-frame relayout. The lambda captures Animatable/Float refs and reads them at draw time.
    // ⚡ 播放器容器现在是全屏的（移到了最外层 Box 中），所以折叠态需要 navRailPadding 让 mini-player
    // 位于 NavigationRail 右侧显示。展开态不需要 navRailPadding，让播放器全屏显示。
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        navRailPaddingPx,
        collapsedExtraStartPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                // 折叠态：navRailPadding + horizontalPadding + 平板限宽补偿；展开态：0
                val collapsedStartPadding =
                    navRailPaddingPx + collapsedStateHorizontalPaddingPx + collapsedExtraStartPaddingPx
                androidx.compose.ui.util.lerp(collapsedStartPadding, 0f, safeFraction)
            } else {
                // 无内容区域时（无播放列表等），也要考虑 navRailPadding
                navRailPaddingPx + collapsedStateHorizontalPaddingPx + collapsedExtraStartPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        collapsedExtraEndPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                val safeFraction = effectiveFraction.coerceIn(0f, 1f)
                androidx.compose.ui.util.lerp(
                    collapsedStateHorizontalPaddingPx + collapsedExtraEndPaddingPx,
                    0f,
                    safeFraction
                )
            } else {
                collapsedStateHorizontalPaddingPx + collapsedExtraEndPaddingPx
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        sheetExtraShiftPxProvider = sheetExtraShiftPxProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}
