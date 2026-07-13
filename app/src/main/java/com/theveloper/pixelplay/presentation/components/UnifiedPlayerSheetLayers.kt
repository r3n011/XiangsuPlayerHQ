package com.theveloper.pixelplay.presentation.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.presentation.components.player.FullPlayerContent
import com.theveloper.pixelplay.presentation.components.scoped.FullPlayerVisualState
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerRuntimePolicy
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    currentSong: Song?,
    retainedSong: Song?,
    miniPlayerScheme: ColorScheme?,
    overallSheetTopCornerRadiusProvider: () -> Dp,
    infrequentPlayerState: StablePlayerState,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    albumColorScheme: ColorScheme,
    bottomSheetOpenFraction: Float,
    fullPlayerVisualState: FullPlayerVisualState,
    containerHeight: Dp,
    currentQueueSourceName: String,
    currentSheetContentState: PlayerSheetState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isFavorite: Boolean,
    shouldRenderFullPlayer: Boolean = true,
    currentHorizontalPaddingStartPxProvider: () -> Float,
    currentHorizontalPaddingEndPxProvider: () -> Float,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // Toast 订阅：收集 PlayerViewModel.toastEvents，新事件到就弹 Toast
    LaunchedEffect(playerViewModel) {
        playerViewModel.toastEvents
            .distinctUntilChanged()
            .collect { msg ->
                android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
            }
    }
    // ⚡ Optimization: Keep the UI mounted during track changes to avoid flickering.
    // Instead of using currentSong?.let which unmounts everything when currentSong is null,
    // we use the passed retainedSong to bridge the gap between tracks.
    val activeSong = currentSong ?: retainedSong ?: return

    miniPlayerScheme?.let { readyScheme ->
        CompositionLocalProvider(
            LocalMaterialTheme provides readyScheme
        ) {
            val miniPlayerZIndex by remember {
                derivedStateOf {
                    if (playerContentExpansionFraction.value < 0.5f) 1f else 0f
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .graphicsLayer {
                        // Compute miniAlpha in the draw phase from the Animatable,
                        // avoiding per-frame recomposition during gestures.
                        alpha = (1f - playerContentExpansionFraction.value * 2f)
                            .coerceIn(0f, 1f)
                    }
                    .layout { measurable, constraints ->
                        // 量化 fraction 到 5% 步进，减少布局重算频率
                        val rawFraction = playerContentExpansionFraction.value
                        val fraction = (rawFraction * 20f).toInt() / 20f
                        val startPaddingPx = currentHorizontalPaddingStartPxProvider().toInt().coerceAtLeast(0)
                        val endPaddingPx = currentHorizontalPaddingEndPxProvider().toInt().coerceAtLeast(0)
                        
                        val targetWidth = if (fraction > 0f) {
                            (constraints.maxWidth - startPaddingPx - endPaddingPx).coerceAtLeast(0)
                        } else {
                            constraints.maxWidth
                        }
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = targetWidth,
                                maxWidth = targetWidth
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val xOffset = if (fraction > 0f) startPaddingPx else 0
                            placeable.placeRelative(xOffset, 0)
                        }
                    }
                    .zIndex(miniPlayerZIndex)
            ) {
                val isMiniPlayerVisible by remember {
                    derivedStateOf { playerContentExpansionFraction.value < 0.01f }
                }
                val navBarBlurEnabled by playerViewModel.navBarBlurEnabled.collectAsStateWithLifecycle()
                val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()
                MiniPlayerContentInternal(
                    song = activeSong, // Use activeSong
                    isPlaying = infrequentPlayerState.isPlaying,
                    isCastConnecting = isCastConnecting,
                    isPreparingPlayback = isPreparingPlayback,
                    onPlayPause = { playerViewModel.playPause() },
                    onPrevious = { playerViewModel.previousSong() },
                    onNext = { playerViewModel.nextSong() },
                    canScroll = isMiniPlayerVisible && infrequentPlayerState.isPlaying,
                    modifier = Modifier.fillMaxSize().then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && navBarBlurEnabled && !disableBlurAllOver) {
                            Modifier.hazeEffect(
                                state = MainActivity.LocalHazeState.current,
                                style = HazeMaterials.ultraThin(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        } else {
                            Modifier
                        }
                    ),
                    currentPositionProvider = currentPositionProvider,
                    totalDurationProvider = { infrequentPlayerState.totalDuration }
                )
            }
        }
    }

    if (shouldRenderFullPlayer) {
        CompositionLocalProvider(
            LocalMaterialTheme provides albumColorScheme
        ) {
            val fullPlayerScale by remember(bottomSheetOpenFraction) {
                // Keep the depth effect, but avoid aggressive full-screen rescaling on every frame.
                derivedStateOf { lerp(1f, 0.972f, bottomSheetOpenFraction) }
            }

            val fullPlayerZIndex by remember {
                derivedStateOf {
                    if (playerContentExpansionFraction.value < 0.5f) 0f else 1f
                }
            }
            val fullPlayerOffset by remember {
                derivedStateOf {
                    if (playerContentExpansionFraction.value <= 0.35f) IntOffset(0, 10000)
                    else IntOffset.Zero
                }
            }
            val fullPlayerRuntimePolicy = rememberFullPlayerRuntimePolicy(
                currentSheetState = currentSheetContentState,
                expansionFraction = playerContentExpansionFraction,
                bottomSheetOpenFraction = bottomSheetOpenFraction
            )

            // Scoped queue collection: only the FullPlayer subtree observes
            // the queue. Sibling MiniPlayer composable and the whole
            // UnifiedPlayerSheetV2 caller are insulated from queue churn.
            val currentPlaybackQueue by playerViewModel.queueFlow
                .collectAsStateWithLifecycle()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(containerHeight)
                    .graphicsLayer {
                        // Read from FullPlayerVisualState lazy getters in the draw phase;
                        // these read Animatable.value internally → re-draw only, no recomposition.
                        alpha = fullPlayerVisualState.contentAlpha
                        translationY = fullPlayerVisualState.translationY
                        scaleX = fullPlayerScale
                        scaleY = fullPlayerScale
                    }
                    .zIndex(fullPlayerZIndex)
                    .offset { fullPlayerOffset }
            ) {
                val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                val latestIsFavorite = rememberUpdatedState(isFavorite)
                val expansionFractionProvider = remember(playerContentExpansionFraction) {
                    { playerContentExpansionFraction.value }
                }
                val isPlayingProvider = remember {
                    { latestInfrequentPlayerState.value.isPlaying }
                }
                val playWhenReadyProvider = remember {
                    { latestInfrequentPlayerState.value.playWhenReady }
                }
                val repeatModeProvider = remember {
                    { latestInfrequentPlayerState.value.repeatMode }
                }
                val isShuffleEnabledProvider = remember {
                    { latestInfrequentPlayerState.value.isShuffleEnabled }
                }
                val totalDurationProvider = remember {
                    { latestInfrequentPlayerState.value.totalDuration }
                }
                val lyricsProvider = remember {
                    { latestInfrequentPlayerState.value.lyrics }
                }
                val isFavoriteProvider = remember {
                    { latestIsFavorite.value }
                }
                val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                val onCollapse = remember(playerViewModel) {
                    { playerViewModel.collapsePlayerSheet() }
                }
                val onShuffleToggle = remember(playerViewModel) {
                    { playerViewModel.toggleShuffle() }
                }
                val onRepeatToggle = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
                val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleFavorite }
                val onDownloadClick = remember(playerViewModel, currentSong) {
                    {
                        currentSong?.let { song ->
                            playerViewModel.downloadSong(song)
                        }
                        Unit
                    }
                }

                FullPlayerContent(
                    currentSong = currentSong, // Pass nullable original, it has its own internal retained logic
                    currentPlaybackQueue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                    isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                    shuffleTransitionInProgress = infrequentPlayerState.isShuffleTransitionInProgress,
                    repeatMode = infrequentPlayerState.repeatMode,
                    allowRealtimeUpdates = fullPlayerRuntimePolicy.allowRealtimeUpdates,
                    expansionFractionProvider = expansionFractionProvider,
                    currentSheetState = currentSheetContentState,
                    carouselStyle = carouselStyle,
                    loadingTweaks = fullPlayerLoadingTweaks,
                    isSheetDragGestureActive = isSheetDragGestureActive,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = currentPositionProvider,
                    isPlayingProvider = isPlayingProvider,
                    playWhenReadyProvider = playWhenReadyProvider,
                    repeatModeProvider = repeatModeProvider,
                    isShuffleEnabledProvider = isShuffleEnabledProvider,
                    totalDurationProvider = totalDurationProvider,
                    lyricsProvider = lyricsProvider,
                    isCastConnecting = isCastConnecting,
                    isFavoriteProvider = isFavoriteProvider,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCollapse = onCollapse,
                    onShowQueueClicked = onShowQueueClicked,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease,
                    onShowCastClicked = onShowCastClicked,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle,
                    onDownloadClick = onDownloadClick
                )
            }
        }
    }
}


@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerPrewarmLayer(
    prewarmFullPlayer: Boolean,
    currentSong: Song?,
    containerHeight: Dp,
    albumColorScheme: ColorScheme,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isCastConnecting: Boolean,
    isFavorite: Boolean,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    if (prewarmFullPlayer && currentSong != null) {
        // Scoped queue collection: the prewarmed FullPlayer owns its own
        // subscription, keeping the queue out of the outer sheet's state.
        val currentPlaybackQueue by playerViewModel.queueFlow
            .collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalMaterialTheme provides albumColorScheme
        ) {
            Box(
                modifier = Modifier
                    .height(containerHeight)
                    .fillMaxWidth()
                    .alpha(0f)
                    .clipToBounds()
            ) {
                // Memoize closures the same way the main layer does to avoid creating
                // new lambda instances on every recomposition.
                val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                val latestIsFavorite = rememberUpdatedState(isFavorite)
                val isPlayingProvider = remember { { latestInfrequentPlayerState.value.isPlaying } }
                val playWhenReadyProvider = remember { { latestInfrequentPlayerState.value.playWhenReady } }
                val repeatModeProvider = remember { { latestInfrequentPlayerState.value.repeatMode } }
                val isShuffleEnabledProvider = remember { { latestInfrequentPlayerState.value.isShuffleEnabled } }
                val totalDurationProvider = remember { { latestInfrequentPlayerState.value.totalDuration } }
                val lyricsProvider = remember { { latestInfrequentPlayerState.value.lyrics } }
                val isFavoriteProvider = remember { { latestIsFavorite.value } }
                val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                val onShuffleToggle = remember(playerViewModel) { { playerViewModel.toggleShuffle() } }
                val onRepeatToggle = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
                val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleFavorite }
                val onDownloadClick = remember(playerViewModel, currentSong) {
                    {
                        currentSong?.let { song ->
                            playerViewModel.downloadSong(song)
                        }
                        Unit
                    }
                }

                FullPlayerContent(
                    currentSong = currentSong,
                    currentPlaybackQueue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                    isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                    shuffleTransitionInProgress = infrequentPlayerState.isShuffleTransitionInProgress,
                    repeatMode = infrequentPlayerState.repeatMode,
                    allowRealtimeUpdates = false,
                    expansionFractionProvider = { 1f },
                    currentSheetState = PlayerSheetState.EXPANDED,
                    carouselStyle = carouselStyle,
                    loadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = currentPositionProvider,
                    isPlayingProvider = isPlayingProvider,
                    playWhenReadyProvider = playWhenReadyProvider,
                    repeatModeProvider = repeatModeProvider,
                    isShuffleEnabledProvider = isShuffleEnabledProvider,
                    totalDurationProvider = totalDurationProvider,
                    lyricsProvider = lyricsProvider,
                    isCastConnecting = isCastConnecting,
                    isFavoriteProvider = isFavoriteProvider,
                    onShowQueueClicked = onShowQueueClicked,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCollapse = {},
                    onShowCastClicked = {},
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle,
                    onDownloadClick = onDownloadClick
                )
            }
        }
    }
}
