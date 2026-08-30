package com.theveloper.pixelplay.presentation.components

import android.os.Build
import android.widget.Toast
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.ui.theme.resolveLyricsFontFamily
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import com.theveloper.pixelplay.presentation.components.scoped.LyricsPredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.consumePositionChange
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import com.theveloper.pixelplay.presentation.components.snapping.ExperimentalSnapperApi
import com.theveloper.pixelplay.presentation.components.snapping.SnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberSnapperFlingBehavior
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.presentation.components.subcomps.LyricsMoreBottomSheet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.data.preferences.PlayerBackgroundMode

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.utils.MultiLangRomanizer

internal data class LyricsSheetColors(
    val container: Color,
    val content: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val accent: Color,
    val accentContent: Color,
    val lyricHighlight: Color,
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val syncButtonContainer: Color,
    val syncButtonContent: Color
)

internal fun lyricsSheetColors(colorScheme: ColorScheme): LyricsSheetColors {
    val container = colorScheme.primaryContainer
    val content = colorScheme.onPrimaryContainer
    val accent = colorScheme.primary
    val accentContent = colorScheme.onPrimary

    return LyricsSheetColors(
        container = container,
        content = content,
        controlContainer = colorScheme.surfaceContainerLowest,
        controlContent = colorScheme.onSurface,
        accent = accent,
        accentContent = accentContent,
        lyricHighlight = preferredContrastColor(
            background = container,
            preferred = accent,
            fallback = content
        ),
        playPauseContainer = colorScheme.tertiaryFixedDim,
        playPauseContent = colorScheme.onTertiaryFixed,
        syncButtonContainer = colorScheme.secondaryFixedDim,
        syncButtonContent = colorScheme.onSecondaryFixed
    )
}

private fun preferredContrastColor(
    background: Color,
    preferred: Color,
    fallback: Color,
    minContrastRatio: Double = 4.5
): Color {
    if (contrastRatio(preferred, background) >= minContrastRatio) return preferred
    if (contrastRatio(fallback, background) >= minContrastRatio) return fallback

    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val argb = encodedSrgbArgb()
    val red = linearizedChannel((argb shr 16) and 0xFF)
    val green = linearizedChannel((argb shr 8) and 0xFF)
    val blue = linearizedChannel(argb and 0xFF)
    return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
}

private fun Color.encodedSrgbArgb(): Int = (value shr 32).toInt()

private fun linearizedChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    lyricsTextStyle: TextStyle,
    lyricsFontSize: String,
    onLyricsFontSizeChange: (String) -> Unit,
    lyricsFontFamily: String,
    onLyricsFontFamilyChange: (String) -> Unit,
    onImportCustomFont: () -> Unit,
    colorScheme: ColorScheme,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    onSaveLyricsToFile: (Song, Lyrics, Boolean) -> Unit,
    onTranslateViaAi: () -> Unit,
    onExplainLyricsViaAi: () -> Unit,
    isExplainingLyrics: Boolean = false,
    lyricsExplanation: String? = null,
    lyricsExplanationEnabled: Boolean = false,
    onDismissExplanation: () -> Unit = {},
    // BottomToggleRow Params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    showLyricsTrackInfo: Boolean,
    customPlayerBackgroundEnabled: Boolean,
    customPlayerBackgroundUri: String?,
    customPlayerBackgroundMode: PlayerBackgroundMode,
    customPlayerBackgroundBlurRadius: Int,
    customPlayerControlsOpacity: Int,
    lyricsGradientOverlayEnabled: Boolean,
    lyricsSolidOverlayAlpha: Float = 0f,
    lyricsVibrantBackgroundEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    swipeThreshold: Dp = 100.dp,
    highlightZoneFraction: Float = 0.08f, // Reduced from 0.22 for less padding
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null // null = auto-detect from preference
) {
    // ─── Enter / Exit animation state ────────────────────────────────────────
    // Mirrors the player-sheet pattern: a plain Float in state drives graphicsLayer
    // at draw-phase (no recomposition per frame). 0f = fully visible, 1f = dismissed.
    var backProgress by remember { mutableFloatStateOf(1f) }

    // Draw-phase lambda provider — read only inside graphicsLayer so layout is never
    // re-triggered during the gesture (same technique as SheetVisualState).
    val backProgressProvider = rememberUpdatedState(backProgress)

    // Enter animation: slide up from +6 % height + fade in.
    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    // Predictive-back (Android 13+) or plain back on older devices.
    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    // ── 拆分订阅：避免 totalDuration 每 250ms 更新导致整个页面重组 ──
    val lyrics by stablePlayerStateFlow
        .map { it.lyrics }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = null)
    val isLoadingLyrics by stablePlayerStateFlow
        .map { it.isLoadingLyrics }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = false)
    val isPlaying by stablePlayerStateFlow
        .map { it.isPlaying }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = false)
    val currentSong by stablePlayerStateFlow
        .map { it.currentSong }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = null)
    // totalDuration 只在 SyncedLyricsList 内部使用，单独订阅
    val totalDuration by stablePlayerStateFlow
        .map { it.totalDuration }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = 0L)

    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    val backgroundColor = sheetColors.controlContainer
    val onBackgroundColor = sheetColors.controlContent
    val containerColor = sheetColors.container
    val contentColor = sheetColors.content
    val accentColor = sheetColors.accent
    val onAccentColor = sheetColors.accentContent
    val lyricHighlightColor = sheetColors.lyricHighlight
    val playPauseColor = sheetColors.playPauseContainer
    val onPlayPauseColor = sheetColors.playPauseContent

    val hasTranslatedLyrics = remember(lyrics) {
        // Translated lyrics read same timestamp on the lrc, not possible in plain type lyrics
        lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
    }

    val hasRomanizedLyrics = remember(lyrics) {
        val hasSynced = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true
        val hasPlain = lyrics?.plain?.any { line ->
            MultiLangRomanizer.isScriptThatNeedsRomanization(line)
        } == true
        hasSynced || hasPlain
    }

    val context = LocalContext.current

    // Read lyrics alignment preference internally from DataStore
    val lyricsAlignmentFlow = remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }
    val lyricsAlignment by lyricsAlignmentFlow.collectAsStateWithLifecycle(initialValue = "left")

    // Read lyrics translation preference internally from DataStore
    val showLyricsTranslationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }
    val showLyricsTranslation by showLyricsTranslationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read lyrics romanization preference internally from DataStore
    val showLyricsRomanizationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_romanization")] ?: true }
    }
    val showLyricsRomanization by showLyricsRomanizationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read animated lyrics preference internally from DataStore
    val useAnimatedLyricsFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: true }
    }
    val useAnimatedLyrics by useAnimatedLyricsFlow.collectAsStateWithLifecycle(initialValue = true)

    val animatedLyricsBlurEnabledFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
    }
    val animatedLyricsBlurEnabled by animatedLyricsBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    val disableBlurAllOverFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("disable_blur_all_over")] ?: false }
    }
    val disableBlurAllOver by disableBlurAllOverFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurStrengthFlow = remember(context) {
        context.dataStore.data.map { it[androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
    }
    val animatedLyricsBlurStrength by animatedLyricsBlurStrengthFlow.collectAsStateWithLifecycle(initialValue = 2.5f)

    // Read keep-screen-on preference from DataStore
    val keepScreenOnFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    // Sync DataStore → local state
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }
    val coroutineScope = rememberCoroutineScope()

    // Apply FLAG_KEEP_SCREEN_ON via the window when enabled
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activityWindow = remember(view) { (view.context as? android.app.Activity)?.window }

    // Hide the status bar AND the bottom navigation bar (三大金刚键/手势小条) for a
    // fully immersive lyrics screen; restore both on exit.
    DisposableEffect(Unit) {
        val window = activityWindow
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previousFlags = window?.attributes?.flags ?: 0
        val previousSystemBarsBehavior = insetsController?.systemBarsBehavior
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        insetsController?.let {
            it.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )
        }
        onDispose {
            if (window != null) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.attributes = window.attributes.apply { flags = previousFlags }
            }
            insetsController?.let {
                it.show(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                )
                previousSystemBarsBehavior?.let { behavior ->
                    it.systemBarsBehavior = behavior
                }
            }
        }
    }

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedAutoscrollSpec = autoscrollAnimationSpec ?: if (useAnimatedLyrics) {
        spring(
            stiffness = 280f,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )
    } else {
        tween(durationMillis = 450, easing = FastOutSlowInEasing)
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    // Flag to prevent dialog from showing briefly after reset
    var wasResetTriggered by remember { mutableStateOf(false) }
    // Save lyrics dialog state
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    var previewSeekPositionMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }

    // 关键修复：当 isLoadingLyrics 为 true 或搜索正在加载时，
    // 即使有旧歌词也显示加载中状态，避免切歌后短暂显示上一首歌的歌词
    var showSyncedLyrics by remember(lyrics, isLoadingLyrics, lyricsSearchUiState) {
        mutableStateOf(
            if (isLoadingLyrics || lyricsSearchUiState is LyricsSearchUiState.Loading) {
                null
            } else {
                when {
                    !lyrics?.synced.isNullOrEmpty() -> true
                    !lyrics?.plain.isNullOrEmpty() -> false
                    else -> null
                }
            }
        )
    }

    val hasSyncedLyrics = remember(lyrics, isLoadingLyrics, lyricsSearchUiState) {
        !isLoadingLyrics &&
            lyricsSearchUiState !is LyricsSearchUiState.Loading &&
            !lyrics?.synced.isNullOrEmpty()
    }

    // Immersive Mode State
    var immersiveMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Swipe Gesture State
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var hasTriggeredAction by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val overlayTranslation = remember { Animatable(0f) }
    val swipeProgress = remember { Animatable(0f) }

    // Reset keep-screen-on when the physical screen goes off (power button / OEM sleep gesture).
    // ACTION_SCREEN_OFF is a guaranteed platform broadcast; no OEM can suppress it.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Auto-hide controls logic
    LaunchedEffect(immersiveLyricsEnabled, lastInteractionTime, showSyncedLyrics, isImmersiveTemporarilyDisabled) {
        if (immersiveLyricsEnabled && showSyncedLyrics == true && !isImmersiveTemporarilyDisabled) {
            delay(immersiveLyricsTimeout)
            immersiveMode = true
        } else {
            immersiveMode = false
        }
    }

    // Font Scaling
    val baseFontSize = when (lyricsFontSize) {
        "SMALL" -> 14.sp
        "DEFAULT" -> 20.sp
        "LARGE" -> 26.sp
        "EXTRA_LARGE" -> 32.sp
        else -> 20.sp
    }

    val fontScale by animateFloatAsState(
        targetValue = if (immersiveMode) 1.25f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fontScale"
    )
    
    val sheetContext = LocalContext.current
    val selectedFontFamily = remember(lyricsFontFamily) {
        resolveLyricsFontFamily(sheetContext, lyricsFontFamily)
    }

    val scaledTextStyle = lyricsTextStyle.copy(
        fontFamily = selectedFontFamily,
        fontSize = baseFontSize * fontScale,
        lineHeight = (baseFontSize.value * 1.4f).sp * fontScale
    )

    fun resetImmersiveTimer() {
        lastInteractionTime = System.currentTimeMillis()
        immersiveMode = false
    }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics, lyricsSearchUiState) {
        val song = currentSong
        if (song != null && lyrics == null && !isLoadingLyrics) {
            if (lyricsSearchUiState is LyricsSearchUiState.Idle && !wasResetTriggered) {
                val uri = song.contentUriString
                val isLocal = song.neteaseId == null &&
                    !uri.startsWith("netease://", ignoreCase = true) &&
                    !uri.startsWith("cloud://lx/", ignoreCase = true) &&
                    song.qqMusicMid == null &&
                    song.navidromeId == null &&
                    song.jellyfinId == null &&
                    song.gdriveFileId == null
                if (isLocal) {
                    showFetchLyricsDialog = true
                } else {
                    onSearchLyrics(false)
                }
            } else if (lyricsSearchUiState is LyricsSearchUiState.PickResult ||
                lyricsSearchUiState is LyricsSearchUiState.NotFound ||
                lyricsSearchUiState is LyricsSearchUiState.Error
            ) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false
        }
    }

    if (showFetchLyricsDialog && lyrics == null) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = onSearchLyrics,
                onPickResult = onPickResult,
                onManualSearch = onManualSearch,
                onDismiss = {
                    showFetchLyricsDialog = false
                    wasResetTriggered = true
                    onDismissLyricsSearch()
                    if (lyrics == null && !isLoadingLyrics) {
                        onBackClick()
                    }
                },
                onImport = onImportLyrics
            )
        }
    }

    // Save Lyrics Dialog
    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.save_lyrics_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.save_lyrics_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_synced_lyrics))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_plain_lyrics))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // ─── Enter / Predictive-back exit transformation ──────────────────
            // Read backProgressProvider inside graphicsLayer (draw-phase) — no layout
            // pass is triggered per gesture frame, same pattern as SheetVisualState.
            // 0f = fully visible, 1f = fully dismissed.
            // Effect: scale down to 92 % + slide down 8 % of height + fade to 72 % alpha.
            // Matches Android predictive back spec for full-screen destinations and
            // mirrors the scale+alpha treatment used across the rest of the app.
            .graphicsLayer {
                val p = backProgressProvider.value
                val scale = lerp(1f, 0.92f, p)
                scaleX = scale
                scaleY = scale
                translationY = lerp(0f, size.height * 0.08f, p)
            }
            .clip(RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isSwipeActive = true
                        hasTriggeredAction = false
                        dragOffset = 0f
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            swipeProgress.snapTo(0f)
                        }
                    },
                    onDragEnd = {
                        isSwipeActive = false
                        val committed = abs(dragOffset) > swipeThresholdPx && !hasTriggeredAction 
                        
                        if (committed) {
                            if (dragOffset > 0) onPrev() else onNext()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        coroutineScope.launch {
                             swipeProgress.animateTo(0f, tween(200))
                             dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isSwipeActive = false
                        dragOffset = 0f
                        coroutineScope.launch {
                            swipeProgress.animateTo(0f, tween(200))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        resetImmersiveTimer()
                        
                        if (!hasTriggeredAction) {
                            dragOffset += dragAmount.x
                            val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            
                            coroutineScope.launch {
                                swipeProgress.snapTo(progress)
                            }
                        }
                    }
                )
            },
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = WindowInsets(0),
        // Removed TopBar and FAB
    ) { paddingValues ->
        val hasOverlay = lyricsSolidOverlayAlpha > 0f || lyricsGradientOverlayEnabled
            val controlsBackground = if (hasOverlay) backgroundColor else Color.Transparent
            Box(modifier = Modifier.fillMaxSize()) {
            var showExplanationSheet by remember { mutableStateOf(false) }
            // 歌词解析完成提示
            val explanationContext = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(lyricsExplanation) {
                if (lyricsExplanation != null && showExplanationSheet) {
                    // 已经在显示了，不需要提示
                } else if (lyricsExplanation != null) {
                    Toast.makeText(explanationContext, explanationContext.getString(R.string.ai_lyrics_explanation_title) + " ✓", Toast.LENGTH_SHORT).show()
                }
            }
            // 纯色底色遮罩：受透明度滑块直接控制（默认 60%）
            if (lyricsSolidOverlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(containerColor.copy(alpha = lyricsSolidOverlayAlpha))
                )
            }

            // 自定义播放器背景优先：开启并已选图时，用它替代专辑封面的旋转背景
            CustomPlayerBackground(
                modifier = Modifier.fillMaxSize(),
                enabled = customPlayerBackgroundEnabled,
                uri = customPlayerBackgroundUri,
                mode = customPlayerBackgroundMode,
                blurRadius = customPlayerBackgroundBlurRadius,
                scrimAlpha = 0f
            )

            val hasCustomBackground =
                customPlayerBackgroundEnabled && !customPlayerBackgroundUri.isNullOrBlank()

            if (!hasCustomBackground && lyricsVibrantBackgroundEnabled) {
                if (Build.VERSION.SDK_INT >= 31 && currentSong?.albumArtUriString != null) {
                    // 高版本：Apple Music 风格 4 块封面旋转 + 重模糊（RenderEffect）
                    AppleMusicRotatingBackground(
                        albumArtUri = currentSong?.albumArtUriString,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = currentSong?.albumArtUriString != null,
                        enter = fadeIn(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SmartImage(
                            model = currentSong?.albumArtUriString,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(radiusX = 40.dp, radiusY = 40.dp)
                                .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // 纯色遮罩优先：开启时用均匀纯色压暗背景，替代渐变遮罩，避免两层叠加
            if (lyricsSolidOverlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(containerColor.copy(alpha = lyricsSolidOverlayAlpha))
                )
            } else if (lyricsGradientOverlayEnabled) {
                // 渐变遮罩：上下柔和渐变，提升文字可读性（受「歌词渐变遮罩」开关控制）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    containerColor.copy(alpha = 0.4f),
                                    containerColor.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    resetImmersiveTimer()
                }
        ) {
            val initialSyncedLineIndex = remember(lyrics, playbackPositionFlow, lyricsSyncOffset) {
                resolveCurrentLineIndex(
                    lines = lyrics?.synced.orEmpty(),
                    position = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                ).coerceAtLeast(0)
            }
            val syncedListState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialSyncedLineIndex
            )
            val staticListState = rememberLazyListState()

            // 控制栏高度估算：播放按钮 78dp + 上下 padding/Spacer 约 42dp ≈ 120dp
            val controlsReservedBottom = if (immersiveMode) 24.dp else 120.dp

            // Lyrics Content - 填满整个区域，控制栏改为悬浮覆盖在底部
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 歌曲信息条：与原版一致 —— 悬浮在歌词内容区左上角（黑胶唱片风格圆形药丸），
                // 背景/文字颜色跟随主题黑白模式（亮色/暗色自动切换）
                // ⚡ 直出渲染，去掉 AnimatedContent 进入动画（淡入+缩放入场）
                if (showLyricsTrackInfo) {
                    LyricsTrackInfo(
                        song = currentSong,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(2f)
                            // ⚡ 避开手机挖孔/刘海：Scaffold contentWindowInsets 为 0（沉浸式
                            // 歌词背景），悬浮的歌曲信息条默认贴着屏幕顶端会被挖孔遮挡，
                            // 这里手动按 safeDrawing（状态栏 ∪ 挖孔）内缩，下移到安全区内。
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                            )
                            .background(
                                color = backgroundColor,
                                shape = CircleShape
                            )
                            .wrapContentWidth(),
                        backgroundColor = backgroundColor,
                        contentColor = onBackgroundColor,
                        isPlaying = isPlaying
                    )
                }

                when (showSyncedLyrics) {
                    null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().hazeSource(MainActivity.LocalHazeState.current),
                            contentPadding = PaddingValues(top = 110.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                        ) {
                            item(key = "loader_or_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val searchLoading = lyricsSearchUiState is LyricsSearchUiState.Loading
                                    if (isLoadingLyrics || searchLoading) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.loading_lyrics),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearWavyProgressIndicator(
                                                trackColor = accentColor.copy(alpha = 0.4f),
                                                color = accentColor,
                                                modifier = Modifier.width(100.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    true -> {
                        val lyricsData = lyrics
                        lyricsData?.synced?.let { synced ->
                            SyncedLyricsList(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(top = 130.dp, bottom = controlsReservedBottom),
                                lines = synced,
                                listState = syncedListState,
                                playbackPositionFlow = playbackPositionFlow,
                                lyricsSyncOffset = lyricsSyncOffset,
                                positionOverrideMs = previewSeekPositionMs,
                                accentColor = lyricHighlightColor,
                                containerColor = containerColor,
                                textStyle = scaledTextStyle,
                                onLineClick = { syncedLine -> 
                                    onSeekTo(
                                        resolveSeekPositionMs(
                                            lineTimeMs = syncedLine.time.toLong(),
                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                        )
                                    )
                                    resetImmersiveTimer()
                                },
                                highlightZoneFraction = highlightZoneFraction,
                                highlightOffsetDp = highlightOffsetDp,
                                autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                useAnimatedLyrics = useAnimatedLyrics,
                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled && !disableBlurAllOver,
                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                immersiveMode = immersiveMode,
                                lyricsAlignment = lyricsAlignment,
                                showTranslation = showLyricsTranslation,
                                showRomanization = showLyricsRomanization,
                                footer = {
                                    if (lyricsData?.areFromRemote == true) {
                                        item(key = "provider_text") {
                                            ProviderText(
                                                providerText = stringResource(R.string.lyrics_provided_by),
                                                uri = stringResource(R.string.lrclib_uri),
                                                textAlign = TextAlign.Center,
                                                accentColor = lyricHighlightColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                },
                                gradientOverlayEnabled = lyricsGradientOverlayEnabled
                            )
                        }
                    }

                    false -> {
                        lyrics?.plain?.let { plain ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = staticListState,
                                contentPadding = PaddingValues(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 130.dp,
                                    bottom = controlsReservedBottom
                                )
                            ) {
                                itemsIndexed(
                                    items = plain,
                                    key = { index, line -> "$index-$line" }
                                ) { _, line ->
                                    PlainLyricsLine(
                                        line = line,
                                        style = scaledTextStyle,
                                        lyricsAlignment = lyricsAlignment,
                                        showTranslation = if (hasTranslatedLyrics) showLyricsTranslation else true,
                                        showRomanization = if (hasRomanizedLyrics) showLyricsRomanization else true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
                
                // 上下渐变遮罩：受「歌词渐变遮罩」开关控制
                if (lyricsGradientOverlayEnabled) {
                    // Top Gradient for fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(containerColor, Color.Transparent)
                                )
                            )
                    )

                    // Bottom Gradient for fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, containerColor)
                                )
                            )
                    )
                }
            }

            // Controls Section (Auto-hide in immersive mode) - 悬浮在歌词内容区底部
            AnimatedVisibility(
                visible = !immersiveMode,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp, end = 16.dp, start = 16.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // Reset timer on any touch down or move in this area
                                    if (event.changes.any { it.pressed }) {
                                         resetImmersiveTimer()
                                    }
                                }
                            }
                        }
                ) {
                                AnimatedVisibility(
                    visible = showSyncedLyrics == true && lyrics?.synced != null && showSyncControls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LyricsSyncControls(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        offsetMillis = lyricsSyncOffset,
                        onOffsetChange = onLyricsSyncOffsetChange,
                        backgroundColor = backgroundColor,
                        accentColor = sheetColors.syncButtonContainer,
                        onAccentColor = sheetColors.syncButtonContent,
                        onBackgroundColor = onBackgroundColor
                    )
                }

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play/Pause Button (Smaller)
                    val playPauseCornerRadius by animateDpAsState(
                        targetValue = if (isPlaying) 18.dp else 50.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "playPauseShape"
                    )

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(playPauseCornerRadius))
                            .background(playPauseColor)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            label = "playPauseIconAnimation"
                        ) { playing ->
                            if (playing) {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Pause",
                                    tint = onPlayPauseColor
                                )
                            } else {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play),
                                    tint = onPlayPauseColor
                                )
                            }
                        }
                    }

                    // Progress Bar
                    LyricsPlaybackSeekBar(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        playbackPositionFlow = playbackPositionFlow,
                        backgroundColor = backgroundColor,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        totalDuration = totalDuration,
                        onSeekTo = onSeekTo,
                        onSeekPreviewChange = { previewSeekPositionMs = it },
                        isPlaying = isPlaying
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Floating Toolbar
                LyricsFloatingToolbar(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    showSyncedLyrics = showSyncedLyrics,
                    hasSyncedLyrics = hasSyncedLyrics,
                    onShowSyncedLyricsChange = { showSyncedLyrics = it },
                    onNavigateBack = {
                        onBackClick()
                    },
                    onMoreClick = { showMoreSheet = true },
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    backProgressProvider = { backProgressProvider.value },
                    isExplainingLyrics = isExplainingLyrics,
                    lyricsExplanation = lyricsExplanation,
                    lyricsExplanationEnabled = lyricsExplanationEnabled,
                    onExplainLyricsViaAi = onExplainLyricsViaAi,
                    onShowExplanation = { showExplanationSheet = true },
                )
             }
            }
        }

        if (showMoreSheet) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                LyricsMoreBottomSheet(
                    onDismissRequest = { showMoreSheet = false },
                    sheetState = moreSheetState,
                    lyrics = lyrics,
                    showSyncedLyrics = showSyncedLyrics == true,
                    isSyncControlsVisible = showSyncControls,
                    onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                    onResetImportedLyrics = {
                        wasResetTriggered = true
                        resetLyricsForCurrentSong()
                    },
                    onSearchLyricsOnline = {
                        onSearchLyrics(true)
                    },
                    onTranslateViaAi = onTranslateViaAi,
                    onExplainLyricsViaAi = onExplainLyricsViaAi,
                    onToggleSyncControls = {
                        resetImmersiveTimer()
                        showSyncControls = !showSyncControls
                    },
                    isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                    onSetImmersiveTemporarilyDisabled = {
                        resetImmersiveTimer()
                        onSetImmersiveTemporarilyDisabled(it)
                    },
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { enabled ->
                        keepScreenOn = enabled
                        coroutineScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = enabled
                            }
                        }
                    },
                    lyricsFontSize = lyricsFontSize,
                    onLyricsFontSizeChange = onLyricsFontSizeChange,
                    lyricsFontFamily = lyricsFontFamily,
                    onLyricsFontFamilyChange = onLyricsFontFamilyChange,
                    onImportCustomFont = onImportCustomFont,
                    lyricsAlignment = lyricsAlignment,
                    onLyricsAlignmentChange = { newAlignment ->
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[stringPreferencesKey("lyrics_alignment")] = newAlignment
                            }
                        }
                    },
                    hasTranslatedLyrics = hasTranslatedLyrics,
                    hasRomanizedLyrics = hasRomanizedLyrics,
                    showTranslation = showLyricsTranslation,
                    showRomanization = showLyricsRomanization,
                    onShowTranslationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                            }
                        }
                    },
                    onShowRomanizationChange = { enabled ->
                        resetImmersiveTimer()
                        coroutineScope.launch {
                            context.dataStore.edit { preferences ->
                                preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                            }
                        }
                    },
                    immersiveLyricsEnabled = immersiveLyricsEnabled,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = {
                        resetImmersiveTimer()
                        onShuffleToggle()
                    },
                    onRepeatToggle = {
                        resetImmersiveTimer()
                        onRepeatToggle()
                    },
                    onFavoriteToggle = {
                        resetImmersiveTimer()
                        onFavoriteToggle()
                    },
                )
            }
        }

        // AI 歌词解析结果底部弹窗（Markdown 渲染）
        if (showExplanationSheet && lyricsExplanation != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val markwon = remember {
                io.noties.markwon.Markwon.builder(context)
                    .usePlugin(io.noties.markwon.image.ImagesPlugin.create())
                    .build()
            }
            val explanationText = lyricsExplanation ?: ""
            val spannable = remember(explanationText) { markwon.toMarkdown(explanationText) }
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val linkColor = MaterialTheme.colorScheme.primary.toArgb()

            ModalBottomSheet(
                onDismissRequest = { showExplanationSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_lyrics_explanation_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            android.widget.TextView(ctx).apply {
                                textSize = 15f
                                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                highlightColor = android.graphics.Color.TRANSPARENT
                            }
                        },
                        update = { tv ->
                            tv.setTextColor(textColor)
                            tv.setLinkTextColor(linkColor)
                            tv.text = spannable
                        }
                    )
                }
            }
        }

       // Show Controls Button (Overlay)
       AnimatedVisibility(
            visible = immersiveMode,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FilledIconButton(
                onClick = { resetImmersiveTimer() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Show Controls"
                )
            }
        }
       
       // Swipe Feedback Overlay
       if (isSwipeActive || swipeProgress.value > 0f) {
           val isNext = dragOffset < 0
           val overlayAlignment = if (isNext) Alignment.CenterEnd else Alignment.CenterStart
           val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious
           
           Box(
               modifier = Modifier
                   .align(overlayAlignment)
                   .size(100.dp) // Base size
                   .padding(
                       start = if(isNext) 0.dp else 6.dp,
                       end = if(isNext) 6.dp else 0.dp
                   )
                   .graphicsLayer {
                        val widthPx = size.width
                        val initialOffset = if(isNext) widthPx else -widthPx
                        translationX = initialOffset * (1f - swipeProgress.value)

                        scaleX = 0.8f + (swipeProgress.value * 0.2f)
                        scaleY = 0.8f + (swipeProgress.value * 0.2f)
                   }
                   .background(
                        color = accentColor, // No alpha modulation
                        shape = RoundedCornerShape(
                            topStart = if(isNext) 360.dp else 8.dp,
                            bottomStart = if(isNext) 360.dp else 8.dp,
                            topEnd = if(isNext) 8.dp else 360.dp,
                            bottomEnd = if(isNext) 8.dp else 360.dp
                        )
                   ),
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = icon,
                   contentDescription = null,
                   modifier = Modifier.size(48.dp),
                   tint = onAccentColor
               )
           }
       }

      }
    }
}

/**
 * AI 歌词解释面板 — 独立 Composable 隔离重组范围，
 * 避免加载动画/文本更新触发歌词列表重组导致卡顿。
 */
@Composable
private fun AiLyricsExplanationPanel(
    isExplaining: Boolean,
    explanation: String?,
    immersiveMode: Boolean,
    accentColor: Color,
    onBackgroundColor: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showPanel = isExplaining || explanation != null
    androidx.compose.animation.AnimatedVisibility(
        visible = showPanel && !immersiveMode,
        enter = androidx.compose.animation.fadeIn() +
            androidx.compose.animation.slideInVertically { it / 4 },
        exit = androidx.compose.animation.fadeOut() +
            androidx.compose.animation.slideOutVertically { it / 5 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = accentColor.copy(alpha = 0.14f),
            contentColor = onBackgroundColor,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_lyrics_explanation_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onBackgroundColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (explanation != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel),
                                modifier = Modifier.size(18.dp),
                                tint = onBackgroundColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                if (isExplaining) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                        Text(
                            text = stringResource(R.string.ai_lyrics_explaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBackgroundColor.copy(alpha = 0.85f)
                        )
                    }
                } else if (explanation != null) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBackgroundColor,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    containerColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: LazyListScope.() -> Unit = {},
    gradientOverlayEnabled: Boolean = true
) {
    val density = LocalDensity.current
    // 共享一个 TextMeasurer 给所有歌词行，避免每行单独创建（流畅度优化）
    val sharedTextMeasurer = rememberTextMeasurer()
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()
    val position = remember(playbackPosition, lyricsSyncOffset, positionOverrideMs) {
        positionOverrideMs ?: (playbackPosition + lyricsSyncOffset).coerceAtLeast(0L)
    }
    val isPreviewSeeking = positionOverrideMs != null
    val currentLineIndex by remember(position, lines) {
        derivedStateOf {
            resolveCurrentLineIndex(lines = lines, position = position)
        }
    }
    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }

    // 拖动时取消模糊效果，停止后5秒恢复
    var blurDisabledByDrag by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // 检测滚动状态，仅在用户手动拖动时禁用模糊
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && isUserInteracting) {
            blurDisabledByDrag = true
        } else if (listState.isScrollInProgress && !isUserInteracting) {
            // 自动滚动，不禁用模糊
        } else if (blurDisabledByDrag) {
            // 停止滚动后等待5秒恢复模糊
            kotlinx.coroutines.delay(5000L)
            blurDisabledByDrag = false
        }
    }

    // 点击歌词跳转后5秒恢复模糊
    LaunchedEffect(currentLineIndex) {
        if (blurDisabledByDrag && lastAutoScrolledLineIndex != currentLineIndex) {
            kotlinx.coroutines.delay(5000L)
            blurDisabledByDrag = false
        }
    }

    val effectiveBlurEnabled = animatedLyricsBlurEnabled && !blurDisabledByDrag

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (gradientOverlayEnabled) {
                    Modifier.fadingEdges(
                        edges = FadingEdges(top = 16.dp, bottom = 100.dp),
                        backgroundColor = containerColor
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        LaunchedEffect(currentLineIndex, lines.size, metrics, isPreviewSeeking) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (currentLineIndex !in lines.indices) return@LaunchedEffect
            
            if (listState.layoutInfo.totalItemsCount == 0) {
                kotlinx.coroutines.delay(100L)
                if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect
            }

            if (!hasAlignedInitialLine) {
                listState.scrollToItem(currentLineIndex)
                snapToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = currentLineIndex
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            if (listState.isScrollInProgress && !isPreviewSeeking && !isUserInteracting) {
                return@LaunchedEffect
            }

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(currentLineIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2) {
                    listState.scrollToItem(currentLineIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = currentLineIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = currentLineIndex
                return@LaunchedEffect
            }

            // folia-style spring for scroll: {stiffness:142, damping:28, mass:0.82}
            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                spring(
                    dampingRatio = 0.82f,
                    stiffness = 142f
                )
            } else {
                autoscrollAnimationSpec
            }

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = currentLineIndex,
                animationSpec = dynamicAnimationSpec
            )
            lastAutoScrolledLineIndex = currentLineIndex
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isUserInteracting = true
                            // 等待所有手指抬起
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) {
                                    break
                                }
                            }
                            isUserInteracting = false
                        }
                    },
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
                    val distanceFromCurrent = if (currentLineIndex != -1) abs(currentLineIndex - index) else 100
                    
                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            // Calculate translation dynamically inside graphicsLayer to avoid recomposing the row during scroll
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == index }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f

                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f

                            // folia-style parallax: quadratic curve (distance^2) with sign preservation
                            // creates a more natural "curved track" feel than cubic
                            val maxTranslation = 55f
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * maxTranslation * if (distanceRatio < 0) -1f else 1f
                        }
                    } else Modifier

                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            nextTime = nextTime,
                            position = position,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            animatedLyricsBlurEnabled = effectiveBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = lyricsAlignment,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            accentColor = accentColor,
                            style = textStyle,
                            textMeasurer = sharedTextMeasurer,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = { onLineClick(line) }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            time = line.time,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            nextTime = nextTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
// 16 dp Spacer removed to allow dynamic padding in LyricLineRow
                }
                // 底部空白行：让最后几首歌词可以滚到高亮区域，而不是贴在屏幕底部
                items(count = 4) {
                    Spacer(modifier = Modifier.height(80.dp))
                }
                footer()
            }

//            if (metrics.zoneHeight > 0.dp) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .offset(y = metrics.topPadding)
//                        .height(metrics.zoneHeight)
//                        .align(Alignment.TopCenter)
//                        .clip(RoundedCornerShape(18.dp))
//                        .background(accentColor.copy(alpha = 0.12f))
//                        .testTag("synced_highlight_zone")
//                )
//            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineRow(
    line: SyncedLine,
    nextTime: Int,
    position: Long,
    distanceFromCurrent: Int = 100,
    useAnimatedLyrics: Boolean = false,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    accentColor: Color,
    style: TextStyle,
    textMeasurer: TextMeasurer? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }

    // ── 防裁切：放大歌词后自动判断是否超出容器宽度，超出则智能换行 ──
    var containerWidthPx by remember { mutableIntStateOf(0) }
    // 复用父级共享的 TextMeasurer；单独使用时才自行创建
    val measurer = textMeasurer ?: rememberTextMeasurer()
    // 高亮行会被放大（useAnimatedLyrics 时 active scale≈1.08），为其预留放大余量，
    // 换行后即使放大 1.08 倍也不会超出容器，彻底避免裁切。
    // 预算取 1.2（> 实际缩放 1.08），再叠加 Bold 测量，双重保险保证边缘不裁切。
    val activeScale = if (useAnimatedLyrics && distanceFromCurrent == 0) 1.2f else 1f
    val availableWidthPx =
        if (containerWidthPx > 0) (containerWidthPx / activeScale).toInt() else Int.MAX_VALUE
    val wrappedLine = remember(sanitizedLine, style, availableWidthPx) {
        if (availableWidthPx == Int.MAX_VALUE) sanitizedLine
        else wrapLyricLineToFit(sanitizedLine, style, measurer, availableWidthPx)
    }

    val sanitizedWords = remember(line.words) {
        line.words?.let(::sanitizeSyncedWords)
    }
    val sanitizedWordClusters = remember(sanitizedWords) {
        sanitizedWords?.takeIf { it.isNotEmpty() }?.let(::clusterSyncedWords)
    }
    val lineEndTime = remember(line, nextTime) {
        resolveLineEndTimeMs(line, nextTime)
    }
    val isCurrentLine by remember(position, line.time, lineEndTime) {
        derivedStateOf { position in line.time.toLong()..<lineEndTime }
    }
    val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.45f)
    // Apple Music 式弹簧：高阻尼 + 较低 stiffness，仅产生极轻微过冲回弹，
    // 让歌词行缩放/颜色/位移有“重量和惯性”，而不是果冻般的明显弹跳。
    val floatAnimSpec: AnimationSpec<Float> = remember(useAnimatedLyrics) {
        if (useAnimatedLyrics) {
            spring(dampingRatio = 0.9f, stiffness = 150f, visibilityThreshold = 0.005f)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        }
    }
    val colorAnimSpec: AnimationSpec<Color> = remember(useAnimatedLyrics) {
        if (useAnimatedLyrics) {
            spring(dampingRatio = 0.9f, stiffness = 150f)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        }
    }
    val dpAnimSpec: AnimationSpec<Dp> = remember(useAnimatedLyrics) {
        if (useAnimatedLyrics) {
            spring(dampingRatio = 0.9f, stiffness = 150f)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        }
    }
    val paddingAnimSpec: AnimationSpec<Dp> = remember(useAnimatedLyrics) {
        if (useAnimatedLyrics) {
            spring(stiffness = 160f, dampingRatio = 0.4f)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        }
    }

    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unhighlightedColor,
        animationSpec = colorAnimSpec,
        label = "lineColor"
    )

    // folia-style continuous visual treatment:
    // active→scale 1.08, opacity 1.0; distance 1→scale 0.96, opacity 0.68; distance 2→scale 0.90, opacity 0.52; distance 3+→scale 0.84, opacity 0.40
    val (targetScale, targetAlpha) = if (useAnimatedLyrics) {
        when (distanceFromCurrent) {
            0 -> Pair(1.08f, 1.0f)
            1 -> Pair(0.96f, 0.68f)
            2 -> Pair(0.90f, 0.52f)
            else -> Pair(0.84f, 0.40f)
        }
    } else Pair(1f, 1f)

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = floatAnimSpec,
        label = "lineScale"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = floatAnimSpec,
        label = "lineAlpha"
    )

    // folia-style blur: gentle distance cue with non-linear falloff（增强模糊，让非当前行明显虚化）
    // distance 1→2dp, distance 2→4dp, distance 3→6dp, distance 4+→8dp
    val targetBlur = if (useAnimatedLyrics && animatedLyricsBlurEnabled && distanceFromCurrent > 0) {
        (minOf(distanceFromCurrent, 4) * 2).dp
    } else 0.dp
    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = dpAnimSpec,
        label = "lineBlur"
    )

    // 行间距物理弹簧：不同距离的行 stiffness 递减（移动速度不同），
    // dampingRatio 0.38~0.42 产生明显过冲——切换时上方行被“挤压”（间距先明显变小），
    // 下方行被“拉扯”（间距先明显变大），到位后回弹恢复；间距差拉大让弹簧感更明显。
    val targetVerticalPadding = if (useAnimatedLyrics) {
        when (distanceFromCurrent) {
            0 -> if (immersiveMode) 22.dp else 32.dp
            1 -> 14.dp
            2 -> 10.dp
            else -> 7.dp
        }
    } else 12.dp
    val animatedVerticalPadding by animateDpAsState(
        targetValue = targetVerticalPadding,
        animationSpec = paddingAnimSpec,
        label = "linePadding"
    )

    // Animated mode: apply graphicsLayer for scale/alpha transforms (pure draw-phase, no layout)
    // ⚡ 水平留白与容器 padding(12.dp) 合计约 36.dp/侧，保证歌词占满约 4/5 屏宽
    val baseModifier = if (useAnimatedLyrics && !immersiveMode) {
        when (lyricsAlignment) {
            "center" -> modifier.padding(horizontal = 24.dp)
            "right" -> modifier.padding(start = 24.dp)
            else -> modifier.padding(end = 24.dp)
        }
    } else {
        modifier
    }
    val animatedModifier = if (useAnimatedLyrics) {
        baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = 0f
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (lyricsAlignment) {
                        "center" -> 0.5f
                        "right" -> 1f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
    } else baseModifier

    // Roman or Translate Logic
    val translationText = line.translation
    val romanizationText = line.romanization

    val secondaryStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }

    val romanizationColor = lineColor.copy(alpha = lineColor.alpha * 0.85f)
    val translationColor = lineColor.copy(alpha = lineColor.alpha * 0.55f)

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val boxAlignment = when (lyricsAlignment) {
        "center" -> Alignment.TopCenter
        "right" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    if (sanitizedWordClusters.isNullOrEmpty()) {
        Column(
            modifier = animatedModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = animatedVerticalPadding, horizontal = 2.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 在真实文字容器上测量可用宽度：之前测量的是含 36dp 左右 padding 的
                    // 整个 Column，导致 wrapLyricLineToFit 换行预算偏大，长行歌词未换行，
                    // 高亮放大后左右边缘文字溢出被容器裁切"一点"。
                    .onSizeChanged { containerWidthPx = it.width },
                contentAlignment = boxAlignment
            ) {
                // Invisible bold text to reserve layout space and prevent reflow
                Text(
                    text = wrappedLine,
                    style = style,
                    color = Color.Transparent,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible
                )
                Text(
                    text = wrappedLine,
                    style = style,
                    color = lineColor,
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible
                )
            }

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = if (availableWidthPx == Int.MAX_VALUE) romanizationText
                    else wrapLyricLineToFit(romanizationText, secondaryStyle, measurer, availableWidthPx),
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = if (availableWidthPx == Int.MAX_VALUE) translationText
                    else wrapLyricLineToFit(translationText, secondaryStyle, measurer, availableWidthPx),
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    } else {
        val highlightedWordIndex by remember(position, sanitizedWords, line.time, lineEndTime) {
            derivedStateOf {
                resolveHighlightedWordIndex(
                    words = requireNotNull(sanitizedWords),
                    positionMs = position,
                    lineStartTimeMs = line.time.toLong(),
                    lineEndTimeMs = lineEndTime
                )
            }
        }

        Column(
            modifier = animatedModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = animatedVerticalPadding, horizontal = 2.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 同普通歌词分支：在真实文字容器上测量，避免含 36dp padding 的
                    // 外层 Column 导致换行预算偏大，高亮放大后边缘文字溢出被裁切。
                    .onSizeChanged { containerWidthPx = it.width },
                contentAlignment = boxAlignment
            ) {
                // 词级高亮行放大 1.1 倍前预留 1/1.1 宽度，放大后恰好不超出容器，
                // 避免 FlowRow 换行后焦点行边缘文字被容器裁切。
                val flowRowWidthModifier =
                    if (useAnimatedLyrics && distanceFromCurrent == 0 && containerWidthPx > 0) {
                        Modifier.width(with(LocalDensity.current) { (containerWidthPx / 1.08f).toDp() })
                    } else {
                        Modifier.fillMaxWidth()
                    }
                FlowRow(
                    modifier = flowRowWidthModifier,
                    horizontalArrangement = when (lyricsAlignment) {
                        "center" -> Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
                        "right" -> Arrangement.spacedBy(3.dp, Alignment.End)
                        else -> Arrangement.spacedBy(3.dp, Alignment.Start)
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sanitizedWordClusters.forEach { cluster ->
                        cluster.words.forEachIndexed { clusterOffset, word ->
                            val wordIndex = cluster.startIndex + clusterOffset
                            key("${line.time}_${word.time}_${word.word}_$wordIndex") {
                                LyricWordSpan(
                                    word = word,
                                    isHighlighted = isCurrentLine && wordIndex == highlightedWordIndex,
                                    useAnimatedLyrics = useAnimatedLyrics,
                                    style = style,
                                    highlightedColor = accentColor,
                                    unhighlightedColor = unhighlightedColor
                                )
                            }
                        }
                    }
                }
            }

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = if (availableWidthPx == Int.MAX_VALUE) romanizationText
                    else wrapLyricLineToFit(romanizationText, secondaryStyle, measurer, availableWidthPx),
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = if (availableWidthPx == Int.MAX_VALUE) translationText
                    else wrapLyricLineToFit(translationText, secondaryStyle, measurer, availableWidthPx),
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * 歌词防裁切：若单行宽度超过 [maxWidthPx] 则在该字符前插入换行。
 * 换行后 Text 使用 softWrap=true 多行显示，行高随内容自适应，绝不裁切。
 *
 * 流畅度优化：
 * 1. 快速路径：先只测一次整行，若未超宽直接返回原文（大多数行不超宽，避免逐字符测量）
 * 2. 进程级 LRU 缓存：按 (行文本+宽度+样式) 缓存换行结果，滚动重建行时直接命中
 */
private val lyricWrapCache = object {
    private val maxEntries = 600
    private val map = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }
    private val lock = Any()

    fun get(key: String): String? = synchronized(lock) { map[key] }
    fun put(key: String, value: String) {
        synchronized(lock) { map[key] = value }
    }
}

private fun wrapLyricLineToFit(
    line: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    maxWidthPx: Int
): String {
    if (line.isBlank() || maxWidthPx <= 0) return line

    // 缓存 key：行文本 + 宽度 + 样式关键属性（字体大小/字重/字体）
    val styleKey = "${style.fontSize?.value ?: 0}_${style.fontWeight?.weight ?: 0}_${style.fontFamily}"
    val cacheKey = "$maxWidthPx|$styleKey|$line"
    lyricWrapCache.get(cacheKey)?.let { return it }

    // 用加粗样式测量：歌词高亮（焦点）行以 Bold 渲染，若按常规字重预算换行，
    // Bold 实际渲染更宽，放大后左右边缘就会溢出被容器裁切"一点"。
    val measureStyle = style.copy(fontWeight = FontWeight.Bold)

    fun measureWidth(text: String): Int = try {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = measureStyle,
            maxLines = 1,
            overflow = TextOverflow.Clip
        ).size.width
    } catch (t: Throwable) {
        // 测量异常时退回原文本，交由 softWrap 自动处理
        lyricWrapCache.put(cacheKey, line)
        Int.MIN_VALUE
    }

    // 快速路径：先只测一次整行，未超宽直接返回
    val fullWidth = measureWidth(line)
    if (fullWidth == Int.MIN_VALUE) return line
    if (fullWidth <= maxWidthPx) {
        lyricWrapCache.put(cacheKey, line)
        return line
    }

    // 分词换行：英文/数字按空格成词、中文按标点成块，
    // 换行点优先落在词/词组边界，而不是逐字符硬断。
    val tokens = tokenizeLyricForWrapping(line)
    val sb = StringBuilder(line.length + 8)
    var currentLine = ""
    for (token in tokens) {
        val test = currentLine + token
        if (measureWidth(test) <= maxWidthPx) {
            currentLine = test
            continue
        }
        // 当前行放不下该词：先输出已有行（吞掉行尾空格/分隔符）
        if (currentLine.isNotBlank()) {
            sb.append(currentLine.trimEnd(' ', '\t')).append('\n')
        }
        // 单个词仍超宽（无空格的超长中文句/超长单词）：在其内部逐字切分
        if (measureWidth(token) > maxWidthPx) {
            var acc = ""
            for (ch in token) {
                val t = acc + ch
                if (measureWidth(t) > maxWidthPx && acc.isNotEmpty()) {
                    sb.append(acc).append('\n')
                    acc = ch.toString()
                } else {
                    acc = t
                }
            }
            currentLine = acc
        } else {
            currentLine = token
        }
    }
    sb.append(currentLine.trimEnd(' ', '\t'))
    val result = sb.toString()
    lyricWrapCache.put(cacheKey, result)
    return result
}

/**
 * 把歌词切成便于按词边界换行的 token：
 * - 连续的字母/数字/注音符号为一个词（英文按空格自然成词）
 * - 连续中文为一个块（换行点优先选在标点后，避免把词从中间劈开）
 * - 标点与空白作为独立 token，行尾会自动吞掉
 * 若整行没有任何可分词结构，退化为逐字符（保证一定能换行）。
 */
private fun tokenizeLyricForWrapping(text: String): List<String> {
    val tokens = LYRIC_TOKEN_REGEX.findAll(text).map { it.value }.toList()
    return tokens.ifEmpty { text.map { it.toString() } }
}

private val LYRIC_TOKEN_REGEX by lazy {
    Regex("[\\p{L}\\p{N}\\p{M}]+|[\\p{P}\\p{S}]+|\\s+")
}

@Composable
fun LyricWordSpan(
    word: SyncedWord,
    isHighlighted: Boolean,
    useAnimatedLyrics: Boolean = false,
    style: TextStyle,
    highlightedColor: Color,
    unhighlightedColor: Color,
    modifier: Modifier = Modifier
) {
    // folia-style word-level animation: fast, precise spring for word highlighting
    val wordAnimSpec = if (useAnimatedLyrics) spring<Float>(
        stiffness = 380f,
        dampingRatio = 0.88f
    ) else tween(durationMillis = 180, easing = FastOutSlowInEasing)

    val color by animateColorAsState(
        targetValue = if (isHighlighted) highlightedColor else unhighlightedColor,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = 380f,
            dampingRatio = 0.88f
        ) else tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "wordColor"
    )

    // Scale: pop up to 1.10 on highlight, settle back to 1f. Only active when
    // animated lyrics is on — layout is untouched because it's applied in graphicsLayer.
    val scale by animateFloatAsState(
        targetValue = if (useAnimatedLyrics && isHighlighted) 1.10f else 1f,
        animationSpec = wordAnimSpec,
        label = "wordScale"
    )

    // Alpha: unhighlighted words dim slightly so the active word pops without
    // needing a hard color contrast. Only active when animated lyrics is on.
    val alpha by animateFloatAsState(
        targetValue = if (useAnimatedLyrics && !isHighlighted) 0.55f else 1f,
        animationSpec = wordAnimSpec,
        label = "wordAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Invisible bold text to reserve layout space and prevent reflow
        Text(
            text = word.word,
            style = style,
            color = Color.Transparent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = word.word,
            style = style,
            color = color,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            // Scale and alpha applied at draw phase — zero layout impact per frame.
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        )
    }
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier
) {
    val sanitizedLines = remember(line) { line.split("\n") }
    val primaryText = remember(sanitizedLines) { if (sanitizedLines.isNotEmpty()) sanitizeLyricLineText(sanitizedLines[0]) else "" }

    val isRomanizedScript = remember(primaryText) {
        MultiLangRomanizer.isScriptThatNeedsRomanization(primaryText)
    }

    val translationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
        if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val rest = if (sanitizedLines.size > 2) sanitizedLines.drop(2).joinToString("\n") { sanitizeLyricLineText(it) } else ""
            
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin

            if (isFirstRomanization) rest else sanitizedLines.drop(1).joinToString("\n") { sanitizeLyricLineText(it) }
        } else ""
    }

    val romanizationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
         if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin
            
            if (isFirstRomanization) sanitizeLyricLineText(firstExtra) else ""
        } else ""
    }
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val translationStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val translationColor = LocalContentColor.current.copy(alpha = 0.45f)

    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (primaryText.isNotBlank()) {
            Text(text = primaryText, style = style, color = LocalContentColor.current.copy(alpha = 0.7f), textAlign = textAlign)

            if (showRomanization && romanizationText.isNotBlank()) {
                Text(
                    text = romanizationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && translationText.isNotBlank()) {
                Text(
                    text = translationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = if (showRomanization && romanizationText.isNotBlank()) 2.dp else 4.dp)
                )
            }
        }
    }
}

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

internal fun resolveLineEndTimeMs(line: SyncedLine, nextLineStartMs: Int): Long {
    val baseEnd = nextLineStartMs.toLong()
    val lastWordStart = line.words?.maxOfOrNull { it.time.toLong() } ?: line.time.toLong()
    return maxOf(baseEnd, lastWordStart + 1L)
}

internal fun resolveHighlightedWordIndex(
    words: List<SyncedWord>,
    positionMs: Long,
    lineStartTimeMs: Long,
    lineEndTimeMs: Long
): Int {
    if (positionMs < lineStartTimeMs || positionMs >= lineEndTimeMs) return -1
    return words.indexOfLast { it.time.toLong() <= positionMs }
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long
): Int {
    if (lines.isEmpty()) return -1

    return lines.withIndex().lastOrNull { (index, line) ->
        val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
        val lineEndTime = resolveLineEndTimeMs(line, nextTime)
        position in line.time.toLong()..<lineEndTime
    }?.index ?: -1
}

@Composable
private fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    isPlaying: Boolean
) {
    if (song == null) return

    val albumShape = CircleShape

    // 黑胶唱片旋转：播放时匀速旋转，暂停时停在原位
    val currentRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 8s 一圈，视觉上仍是清晰的"旋转黑胶"，同时降低长听过程中的 Compose 无效重组
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        } else {
             currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = albumShape,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(albumShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false) // Allow shrinking if content is small
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.7f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )
    }
}
