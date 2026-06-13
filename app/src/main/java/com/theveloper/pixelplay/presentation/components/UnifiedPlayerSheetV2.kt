package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import com.theveloper.pixelplay.presentation.components.ExpressiveOfflineDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.sanitizeNavBarCornerRadius
import com.theveloper.pixelplay.presentation.components.scoped.PlayerAlbumNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerArtistNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerSheetPredictiveBackHandler
import com.theveloper.pixelplay.presentation.components.scoped.QueueSheetRuntimeEffects
import com.theveloper.pixelplay.presentation.components.scoped.SheetMotionController
import com.theveloper.pixelplay.presentation.components.scoped.miniPlayerDismissHorizontalGesture
import com.theveloper.pixelplay.presentation.components.scoped.playerSheetVerticalDragGesture
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerCompositionPolicy
import com.theveloper.pixelplay.presentation.components.scoped.rememberCastSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerVisualState
import com.theveloper.pixelplay.presentation.components.scoped.rememberMiniPlayerDismissGestureHandler
import com.theveloper.pixelplay.presentation.components.scoped.rememberPrewarmFullPlayer
import com.theveloper.pixelplay.presentation.components.scoped.rememberQueueSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetActionHandlers
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetBackAndDragState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetInteractionState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetModalOverlayController
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetOverlayState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetThemeState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetVisualState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PlayerUiSheetSliceV2(
    val currentQueueSourceName: String = "",
    val preparingSongId: String? = null
)

/**
 * V2 real host: no longer delegates to the legacy `UnifiedPlayerSheet`.
 *
 * This path keeps behavior parity, but now owns its own runtime wiring so we can
 * profile and optimize V2 independently while preserving the Experimental switch.
 */
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnifiedPlayerSheetV2(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    navController: NavHostController,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false,
    navRailPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // MediaStore write-permission launcher (for metadata editing without MANAGE_EXTERNAL_STORAGE)
    val writePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onWritePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // MediaStore delete-permission launcher (system delete confirmation dialog)
    val deletePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        playerViewModel.onDeletePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                playerViewModel.toastEvents.collect { message ->
                    Toast.makeText(latestContext, message, Toast.LENGTH_SHORT).show()
                }
            }
            launch {
                playerViewModel.showNoInternetDialog.collect {
                    showNoInternetDialog = true
                }
            }
            launch {
                playerViewModel.writePermissionRequest.collect { intentSender ->
                    writePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
            launch {
                playerViewModel.deletePermissionRequest.collect { intentSender ->
                    deletePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
        }
    }

    if (showNoInternetDialog) {
        ExpressiveOfflineDialog(
            onDismiss = { showNoInternetDialog = false },
            onRetry = {
                 playerViewModel.refreshLocalConnectionInfo()
                 showNoInternetDialog = false
            }
        )
    }

    val infrequentPlayerStateReference = playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val infrequentPlayerState = infrequentPlayerStateReference.value
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentPositionState = playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val remotePositionState = playerViewModel.remotePosition.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val positionToDisplayProvider = remember(isRemotePlaybackActive) {
        {
            if (isRemotePlaybackActive) remotePositionState.value
            else currentPositionState.value
        }
    }

    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()

    val playerUiSheetSlice by remember {
        playerViewModel.playerUiState
            .map { state ->
                PlayerUiSheetSliceV2(
                    currentQueueSourceName = state.currentQueueSourceName,
                    preparingSongId = state.preparingSongId
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerUiSheetSliceV2())
    val currentQueueSourceName = playerUiSheetSlice.currentQueueSourceName
    val preparingSongId = playerUiSheetSlice.preparingSongId

    val currentSheetContentState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
    val predictiveBackSwipeEdge by playerViewModel.predictiveBackSwipeEdge.collectAsStateWithLifecycle()
    val prewarmFullPlayer = rememberPrewarmFullPlayer(infrequentPlayerState.currentSong?.id)

    val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
    val navBarCornerRadius = sanitizeNavBarCornerRadius(playerConfig.navBarCornerRadius)
    val navBarStyle = playerConfig.navBarStyle
    val carouselStyle = playerConfig.carouselStyle
    val fullPlayerLoadingTweaks = playerConfig.fullPlayerLoadingTweaks
    val tapBackgroundClosesPlayer = playerConfig.tapBackgroundClosesPlayer
    val useSmoothCorners = playerConfig.useSmoothCorners
    val playerThemePreference = playerConfig.playerThemePreference

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }
    val swipeDismissProgress by remember(dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val showPlayerContentArea by remember(infrequentPlayerState.currentSong, isCastConnecting) {
        derivedStateOf { infrequentPlayerState.currentSong != null || isCastConnecting }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val isPlayerFullyExpanded by remember(playerContentExpansionFraction, currentSheetContentState) {
        derivedStateOf {
            playerContentExpansionFraction.value >= 0.99f &&
                currentSheetContentState == PlayerSheetState.EXPANDED
        }
    }
    // ⚡ 简化动画:只用一个 Animatable(playerContentExpansionFraction)驱动
    // - translationY = sheetCollapsedTargetY * (1f - fraction):从下方渐入
    // - 去除 scale、overshoot、复杂角半径动画
    // - 单一动画源，避免动画不同步导致卡中间
    // ⚡ 优化：使用简单的 tween 动画（350ms）而不是复杂的弹簧动画
    //   弹簧动画需要每帧进行物理计算，对主线程压力大；tween 只需预先生成缓动曲线
    //   同时将时长从约 255ms 延长到 350ms，使动画更流畅自然
    val sheetAnimationSpec = remember {
        tween<Float>(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        )
    }
    val sheetExpandedTargetY = 0f

    // ⚡ sheetMotionController: 封装 playerContentExpansionFraction 的动画操作
    val sheetMotionController = remember(
        playerContentExpansionFraction,
        sheetAnimationSpec
    ) {
        SheetMotionController(
            playerContentExpansionFraction = playerContentExpansionFraction,
            mutex = androidx.compose.foundation.MutatorMutex(),
            defaultAnimationSpec = sheetAnimationSpec
        )
    }

    // ⚡ animatePlayerSheet: 统一的展开/折叠动画入口
    // 作为 Composable 函数而不是 lambda，以便支持命名参数
    fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float>? = null,
        initialVelocity: Float = 0f
    ) {
        scope.launch {
            if (animationSpec != null) {
                sheetMotionController.animateTo(targetExpanded, animationSpec, initialVelocity)
            } else {
                sheetMotionController.animateTo(targetExpanded, initialVelocity = initialVelocity)
            }
        }
    }

    PlayerArtistNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = null,
        playerViewModel = playerViewModel
    )
    PlayerAlbumNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = null,
        playerViewModel = playerViewModel
    )

    // FullPlayerVisualState now holds lazy getters that read from the Animatable
    // inside graphicsLayer (draw-phase), avoiding per-frame recomposition.
    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = playerContentExpansionFraction,
        initialOffsetY = 0f
    )
    val fullPlayerCompositionPolicy = rememberFullPlayerCompositionPolicy(
        currentSongId = infrequentPlayerState.currentSong?.id,
        currentSheetState = currentSheetContentState,
        expansionFraction = playerContentExpansionFraction
    )
    val shouldRenderFullPlayer = fullPlayerCompositionPolicy.shouldRenderFullPlayer

    // Battery: tell the PlaybackStateHolder when the slider-bearing UI is
    // actually rendered. When it isn't (mini-player only), the position
    // ticker drops from 250 ms to 1 s — slider precision isn't needed.
    DisposableEffect(shouldRenderFullPlayer) {
        playerViewModel.setSliderUiMounted(shouldRenderFullPlayer)
        onDispose { playerViewModel.setSliderUiMounted(false) }
    }

    var previousSheetState by remember { mutableStateOf(currentSheetContentState) }
    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        if (previousSheetState != currentSheetContentState) {
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.UI,
                name = "player_sheet_state_changed"
            ) {
                mapOf(
                    "from" to previousSheetState.name,
                    "to" to currentSheetContentState.name,
                    "showPlayerContentArea" to showPlayerContentArea.toString()
                )
            }
        }
        previousSheetState = currentSheetContentState

        // ⚡ 简化:直接动画 playerContentExpansionFraction
        // 目标值:展开=1f，折叠=0f
        val targetFraction = if (targetExpanded && showPlayerContentArea) 1f else 0f
        scope.launch {
            playerContentExpansionFraction.animateTo(
                targetValue = targetFraction,
                animationSpec = sheetAnimationSpec
            )
        }
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = showPlayerContentArea,
        collapsedStateHorizontalPadding = collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress = predictiveBackCollapseProgress,
        predictiveBackSwipeEdge = predictiveBackSwipeEdge,
        currentSheetContentState = currentSheetContentState,
        playerContentExpansionFraction = playerContentExpansionFraction,
        containerHeight = containerHeight,
        // currentSheetTranslationY 已移除:直接用 fraction 计算
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        navBarStyle = navBarStyle,
        navBarCornerRadiusDp = navBarCornerRadius.dp,
        isNavBarHidden = isNavBarHidden,
        isPlaying = infrequentPlayerState.isPlaying,
        hasCurrentSong = infrequentPlayerState.currentSong != null,
        swipeDismissProgress = swipeDismissProgress,
        navRailPadding = navRailPadding
    )
    val currentBottomPadding = sheetVisualState.currentBottomPadding
    val baseBottomPadding = sheetVisualState.baseBottomPadding
    val playerContentAreaHeightPxProvider = sheetVisualState.playerContentAreaHeightPxProvider
    val visualSheetTranslationYProvider = sheetVisualState.visualSheetTranslationYProvider
    val overallSheetTopCornerRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider
    val playerContentActualBottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider
    val currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider
    val currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider

    val queueSheetState = rememberQueueSheetState(
        scope = scope,
        screenHeightPx = screenHeightPx,
        density = density,
        currentBottomPadding = currentBottomPadding,
        showPlayerContentArea = showPlayerContentArea,
        isPlayerFullyExpanded = isPlayerFullyExpanded
    )
    val showQueueSheet = queueSheetState.showQueueSheet
    val allowQueueSheetInteraction = queueSheetState.allowQueueSheetInteraction
    val queueSheetOffset = queueSheetState.queueSheetOffset
    val queueSheetHeightPx = queueSheetState.queueSheetHeightPx
    val queueHiddenOffsetPx = queueSheetState.queueHiddenOffsetPx
    val queueSheetController = queueSheetState.queueSheetController
    val onQueueSheetHeightPxChange = queueSheetState.onQueueSheetHeightPxChange

    val castSheetState = rememberCastSheetState()
    val sheetBackAndDragState = rememberSheetBackAndDragState(
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val canHandlePlayerBack by remember(
        sheetBackAndDragState.predictiveBackEnabled,
        showQueueSheet,
        castSheetState.showCastSheet
    ) {
        derivedStateOf {
            sheetBackAndDragState.predictiveBackEnabled &&
                !showQueueSheet &&
                !castSheetState.showCastSheet
        }
    }
    val velocityTracker = remember { VelocityTracker() }
    val sheetModalOverlayController = rememberSheetModalOverlayController(
        scope = scope,
        queueSheetController = queueSheetController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onCollapsePlayerSheet = { playerViewModel.collapsePlayerSheet() }
    )
    val pendingSaveQueueOverlay = sheetModalOverlayController.pendingSaveQueueOverlay
    val selectedSongForInfo = sheetModalOverlayController.selectedSongForInfo
    val sheetActionHandlers = rememberSheetActionHandlers(
        scope = scope,
        navController = navController,
        playerViewModel = playerViewModel,
        sheetMotionController = sheetMotionController,
        queueSheetController = queueSheetController,
        sheetModalOverlayController = sheetModalOverlayController
    )

    val hapticFeedback = LocalHapticFeedback.current
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() },
        onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) }
    )

    QueueSheetRuntimeEffects(
        queueSheetController = queueSheetController,
        queueSheetOffset = queueSheetOffset,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        onTopEdgeReached = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    PlayerSheetPredictiveBackHandler(
        enabled = canHandlePlayerBack,
        playerViewModel = playerViewModel,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        sheetMotionController = sheetMotionController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onSwipeEdgeChanged = { playerViewModel.updatePredictiveBackSwipeEdge(it) },
        registrationKey = currentBackStackEntry?.id
    )

    val queuePredictiveBackProgress = remember { Animatable(0f) }
    var queuePredictiveBackSwipeEdge by remember { mutableStateOf<Int?>(null) }

    val sheetOverlayState = rememberSheetOverlayState(
        density = density,
        showPlayerContentArea = showPlayerContentArea,
        hideMiniPlayer = hideMiniPlayer,
        showQueueSheet = showQueueSheet,
        isQueueCollapsing = queueSheetState.isCollapsing,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        screenHeightPx = screenHeightPx,
        castSheetOpenFraction = castSheetState.castSheetOpenFraction,
        queueSheetOffset = queueSheetOffset,
        queuePredictiveBackProgress = queuePredictiveBackProgress
    )
    val internalIsKeyboardVisible = sheetOverlayState.internalIsKeyboardVisible
    val actuallyShowSheetContent = sheetOverlayState.actuallyShowSheetContent
    val isQueueVisible = sheetOverlayState.isQueueVisible
    val bottomSheetOpenFraction = sheetOverlayState.bottomSheetOpenFraction
    val queueScrimAlpha = sheetOverlayState.queueScrimAlpha
    val shouldRenderQueueHost by remember(internalIsKeyboardVisible, selectedSongForInfo) {
        derivedStateOf {
            !internalIsKeyboardVisible || selectedSongForInfo != null
        }
    }
    val isQueueTelemetryActive = showQueueSheet

    LaunchedEffect(showQueueSheet) {
        playerViewModel.updateQueueSheetVisibility(showQueueSheet)
    }
    LaunchedEffect(castSheetState.showCastSheet) {
        playerViewModel.updateCastSheetVisibility(castSheetState.showCastSheet)
    }
    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.updateQueueSheetVisibility(false)
            playerViewModel.updateCastSheetVisibility(false)
        }
    }

    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val currentSong = infrequentPlayerState.currentSong
    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = preparingSongId,
        systemColorScheme = MaterialTheme.colorScheme
    )
    val albumColorScheme = sheetThemeState.albumColorScheme
    val miniPlayerScheme = sheetThemeState.miniPlayerScheme
    val isPreparingPlayback = sheetThemeState.isPreparingPlayback
    val miniReadyAlpha = sheetThemeState.miniReadyAlpha
    val miniAppearScale = sheetThemeState.miniAppearScale
    val playerAreaBackground = sheetThemeState.playerAreaBackground
    // Elevation is only visible in the mini/collapsed state (expansion < 0.18).
    // miniReadyAlpha fades the shadow in during the initial song-appear animation.
    val isDragging = sheetBackAndDragState.isDragging
    val visualCardShadowElevation by remember(showQueueSheet, miniReadyAlpha, isDragging) {
        derivedStateOf {
            if (
                showQueueSheet ||
                isDragging ||
                playerContentExpansionFraction.isRunning ||
                playerContentExpansionFraction.value > 0.18f
            ) {
                0.dp
            } else {
                (3f * miniReadyAlpha).dp
            }
        }
    }

    val sheetInteractionState = rememberSheetInteractionState(
        scope = scope,
        velocityTracker = velocityTracker,
        sheetMotionController = sheetMotionController,
        playerContentExpansionFraction = playerContentExpansionFraction,
        // currentSheetTranslationY 和 visualOvershootScaleY 已移除:单一 fraction 驱动
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        miniPlayerContentHeightPx = miniPlayerContentHeightPx,
        currentSheetContentState = currentSheetContentState,
        showPlayerContentArea = showPlayerContentArea,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        useSmoothCorners = useSmoothCorners,
        isDragging = sheetBackAndDragState.isDragging,
        onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
            if (animationSpec == null) {
                animatePlayerSheet(targetExpanded = targetExpanded)
            } else {
                animatePlayerSheet(
                    targetExpanded = targetExpanded,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity
                )
            }
        },
        onExpandSheetState = { playerViewModel.expandPlayerSheet() },
        onCollapseSheetState = { playerViewModel.collapsePlayerSheet() },
        onDraggingChange = sheetBackAndDragState.onDraggingChange,
        onDraggingPlayerAreaChange = sheetBackAndDragState.onDraggingPlayerAreaChange
    )

    if (!actuallyShowSheetContent) return

    val playerSheetSemanticsDescription = remember(
        currentSheetContentState,
        infrequentPlayerState.currentSong?.title
    ) {
        "PixelPlay player sheet ${currentSheetContentState.name.lowercase()} " +
            (infrequentPlayerState.currentSong?.title ?: "")
    }

    val miniHeightPx = with(density) { com.theveloper.pixelplay.presentation.components.MiniPlayerHeight.toPx() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val translationY = visualSheetTranslationYProvider().roundToInt()
                val overshoot = if (currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging) {
                    -translationY
                } else {
                    if (translationY < 0) -translationY else 0
                }
                val targetHeight = constraints.maxHeight + overshoot
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = targetHeight,
                        maxHeight = targetHeight
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, translationY)
                }
            },
        shadowElevation = 0.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = currentBottomPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = offsetAnimatable.value
                                scaleX = miniAppearScale
                                scaleY = miniAppearScale
                                alpha = miniReadyAlpha
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            // outerLayout:
                            // Measures downstream chain with innerWidth and targetHeightPx.
                            // Places child at startPaddingPx to center it horizontally.
                            // Reports full screen width to parent to satisfy fillMaxWidth() constraints.
                            .layout { measurable, constraints ->
                                val targetHeightPx = playerContentAreaHeightPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val endPaddingPx = currentHorizontalPaddingEndPxProvider()
                                    .toInt().coerceAtLeast(0)
                                val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    .coerceAtLeast(0)
                                
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = innerWidth,
                                        maxWidth = innerWidth,
                                        minHeight = targetHeightPx,
                                        maxHeight = targetHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, targetHeightPx) {
                                    placeable.placeRelative(startPaddingPx, 0)
                                }
                            }
                            // Always apply Modifier.shadow with the dynamic elevation
                            // (0.dp renders nothing). Keeping the modifier chain
                            // structurally stable avoids the costly relayout/redraw
                            // restructure when the elevation crosses 0.dp during
                            // expand/collapse or right after play/pause.
                            .shadow(
                                elevation = visualCardShadowElevation,
                                shape = sheetInteractionState.playerShadowShape,
                                clip = false
                            )
                            .background(
                                color = playerAreaBackground,
                                shape = sheetInteractionState.playerShadowShape
                            )
                            .clip(sheetInteractionState.playerShadowShape)
                            // innerLayout:
                            // Measures the actual player content with full screen height targetContentHeightPx
                            // so that it can render correctly, while reporting targetHeightPx to the outer
                            // clip/background/shadow so that they are perfectly constrained to the miniplayer card bounds.
                            // During drag/animation, we measure at stable full-screen constraints to prevent jank.
                            .layout { measurable, constraints ->
                                val targetContentHeightPx = containerHeight.roundToPx()
                                val fraction = playerContentExpansionFraction.value
                                val startPaddingPx = currentHorizontalPaddingStartPxProvider().toInt()
                                val measureWidth = if (fraction > 0f) {
                                    screenWidthPx.roundToInt()
                                } else {
                                    constraints.maxWidth
                                }
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = measureWidth,
                                        maxWidth = measureWidth,
                                        minHeight = targetContentHeightPx,
                                        maxHeight = targetContentHeightPx
                                    )
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    val xOffset = if (fraction > 0f) -startPaddingPx else 0
                                    placeable.placeRelative(xOffset, 0)
                                }
                            }
                            .miniPlayerDismissHorizontalGesture(
                                enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                handler = miniDismissGestureHandler
                            )
                            .playerSheetVerticalDragGesture(
                                enabled = sheetInteractionState.canDragSheet,
                                handler = sheetInteractionState.sheetVerticalDragGestureHandler
                            )
                            .clickable(
                                enabled = tapBackgroundClosesPlayer || currentSheetContentState == PlayerSheetState.COLLAPSED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // ⚡ 折叠状态点击：显示提示"按住上滑打开播放器"，不展开
                                //   展开状态点击背景：调用 togglePlayerSheetState() 关闭播放器
                                if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                    Toast.makeText(
                                        latestContext,
                                        "按住上滑打开播放器",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (tapBackgroundClosesPlayer) {
                                    playerViewModel.togglePlayerSheetState()
                                }
                            }
                            .semantics {
                                contentDescription = playerSheetSemanticsDescription
                            }
                    ) {
                        UnifiedPlayerMiniAndFullLayers(
                            currentSong = infrequentPlayerState.currentSong,
                            miniPlayerScheme = miniPlayerScheme,
                            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
                            infrequentPlayerState = infrequentPlayerState,
                            isCastConnecting = isCastConnecting,
                            isPreparingPlayback = isPreparingPlayback,
                            playerContentExpansionFraction = playerContentExpansionFraction,
                            albumColorScheme = albumColorScheme,
                            bottomSheetOpenFraction = bottomSheetOpenFraction,
                            fullPlayerVisualState = fullPlayerVisualState,
                            containerHeight = containerHeight,
                            currentQueueSourceName = currentQueueSourceName,
                            currentSheetContentState = currentSheetContentState,
                            carouselStyle = carouselStyle,
                            fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                            isSheetDragGestureActive = sheetBackAndDragState.isDraggingPlayerArea,
                            playerViewModel = playerViewModel,
                            currentPositionProvider = positionToDisplayProvider,
                            isFavorite = isFavorite,
                            shouldRenderFullPlayer = shouldRenderFullPlayer,
                            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
                            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider,
                            onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                            onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                            onQueueDrag = sheetActionHandlers.dragQueueBy,
                            onQueueRelease = sheetActionHandlers.endQueueDrag,
                            onShowCastClicked = castSheetState.openCastSheet
                        )
                    }
                }

                UnifiedPlayerPrewarmLayer(
                    prewarmFullPlayer = prewarmFullPlayer && !shouldRenderFullPlayer,
                    currentSong = infrequentPlayerState.currentSong,
                    containerHeight = containerHeight,
                    albumColorScheme = albumColorScheme,
                    currentQueueSourceName = currentQueueSourceName,
                    infrequentPlayerState = infrequentPlayerState,
                    carouselStyle = carouselStyle,
                    fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = positionToDisplayProvider,
                    isCastConnecting = isCastConnecting,
                    isFavorite = isFavorite,
                    onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                    onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                    onQueueDrag = sheetActionHandlers.dragQueueBy,
                    onQueueRelease = sheetActionHandlers.endQueueDrag
                )
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PredictiveBackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) { progressFlow ->
                    try {
                        progressFlow.collect { backEvent ->
                            queuePredictiveBackSwipeEdge = backEvent.swipeEdge
                            queuePredictiveBackProgress.snapTo(backEvent.progress)
                        }
                        scope.launch {
                            launch {
                                sheetActionHandlers.animateQueueSheet(false)
                            }
                            launch {
                                queuePredictiveBackProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(ANIMATION_DURATION_MS)
                                )
                                queuePredictiveBackSwipeEdge = null
                            }
                        }
                    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                        scope.launch {
                            queuePredictiveBackProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(ANIMATION_DURATION_MS)
                            )
                            queuePredictiveBackSwipeEdge = null
                        }
                    }
                }
            } else {
                BackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) {
                    sheetActionHandlers.animateQueueSheet(false)
                }
            }

            val queuePredictiveBackSwipeEdgeState = rememberUpdatedState(queuePredictiveBackSwipeEdge)

            UnifiedPlayerQueueAndSongInfoHost(
                shouldRenderHost = shouldRenderQueueHost,
                keepQueueSheetWarm = currentSheetContentState == PlayerSheetState.EXPANDED &&
                    !internalIsKeyboardVisible,
                isQueueTelemetryActive = isQueueTelemetryActive,
                albumColorScheme = albumColorScheme,
                queueScrimAlpha = queueScrimAlpha,
                showQueueSheet = showQueueSheet,
                isQueueCollapsing = queueSheetState.isCollapsing,
                queueHiddenOffsetPx = queueHiddenOffsetPx,
                queueSheetOffset = queueSheetOffset,
                queueSheetHeightPx = queueSheetHeightPx,
                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                configurationResetKey = configuration,
                currentQueueSourceName = currentQueueSourceName,
                infrequentPlayerState = infrequentPlayerState,
                playerViewModel = playerViewModel,
                selectedSongForInfo = selectedSongForInfo,
                onSelectedSongForInfoChange = sheetActionHandlers.onSelectedSongForInfoChange,
                onAnimateQueueSheet = sheetActionHandlers.animateQueueSheet,
                onBeginQueueDrag = sheetActionHandlers.beginQueueDrag,
                onDragQueueBy = sheetActionHandlers.dragQueueBy,
                onEndQueueDrag = sheetActionHandlers.endQueueDrag,
                onLaunchSaveQueueOverlay = sheetActionHandlers.onLaunchSaveQueueOverlay,
                onNavigateToAlbum = sheetActionHandlers.onNavigateToAlbum,
                onNavigateToArtist = sheetActionHandlers.onNavigateToArtist,
                onNavigateToGenre = sheetActionHandlers.onNavigateToGenre,
                queuePredictiveBackProgress = queuePredictiveBackProgress,
                queuePredictiveBackSwipeEdge = queuePredictiveBackSwipeEdgeState
            )
        }
    }

    UnifiedPlayerCastLayer(
        showCastSheet = castSheetState.showCastSheet,
        internalIsKeyboardVisible = internalIsKeyboardVisible,
        albumColorScheme = albumColorScheme,
        playerViewModel = playerViewModel,
        onDismiss = castSheetState.dismissCastSheet,
        onExpansionChanged = castSheetState.onCastExpansionChanged
    )

    UnifiedPlayerSaveQueueLayer(
        pendingOverlay = pendingSaveQueueOverlay,
        onDismissOverlay = { sheetModalOverlayController.dismissSaveQueueOverlay() }
    )
}
