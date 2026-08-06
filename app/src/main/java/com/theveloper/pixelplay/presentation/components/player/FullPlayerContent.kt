package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.CommentSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils.mimeTypeToFormat
import com.theveloper.pixelplay.utils.LyricsImportFailureReason
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToLong
import com.theveloper.pixelplay.presentation.components.WavySliderExpressive
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val PREVIOUS_TRACK_RESTART_THRESHOLD_MS = 10_000L
private const val SKIP_COMMAND_GUARD_MS = 96L

private enum class SkipDirection { PREVIOUS, NEXT }

private suspend fun validateLyricsImport(
    context: Context,
    uri: Uri
): LyricsImportValidationResult = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver

    var fileName = ""
    var fileSize: Long? = null
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            fileName = if (nameIndex != -1) cursor.getString(nameIndex) else ""
            fileSize = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        }
    }

    contentResolver.openInputStream(uri)?.use { inputStream ->
        LyricsImportSecurity.validateImportedLyricsFile(
            fileName = fileName,
            mimeType = contentResolver.getType(uri),
            inputStream = inputStream,
            reportedSizeBytes = fileSize
        )
    } ?: LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
}

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayerContent(
    currentSong: Song?,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int = -1,
    isShuffleEnabled: Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    allowRealtimeUpdates: Boolean = true,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    repeatModeProvider: () -> Int,
    isShuffleEnabledProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    lyricsProvider: () -> Lyrics? = { null }, 
    // State
    isCastConnecting: Boolean = false,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val isExpanded by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.35f }
    }
    
    if (!isExpanded && currentSheetState == PlayerSheetState.COLLAPSED) {
        return
    }

    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    // 广播电台实时流：隐藏进度条、上一首/下一首、随机/循环、歌词、评论、下载等不可用功能
    val isRadioPlayback = song.id.startsWith("radio://")
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }

    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()

    // Single subscription — replaces 11 independent collectAsStateWithLifecycle calls.
    // distinctUntilChanged in the ViewModel ensures this only emits when something
    // actually changed, batching multiple rapid updates into one recomposition.
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val currentSongArtists = fullPlayerSlice.currentSongArtists
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val lyricsFontFamily by playerViewModel.lyricsFontFamily.collectAsStateWithLifecycle()
    val albumArtQuality = fullPlayerSlice.albumArtQuality
    val gradientEdgeColor by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.primaryContainer,
        animationSpec = tween(durationMillis = 400),
        label = "MetadataGradientEdgeColor"
    )
    val playbackAudioMetadata = fullPlayerSlice.audioMetadata
    val showPlayerFileInfo = fullPlayerSlice.showPlayerFileInfo
    val immersiveLyricsEnabled = fullPlayerSlice.immersiveLyricsEnabled
    val immersiveLyricsTimeout = fullPlayerSlice.immersiveLyricsTimeout
    val isImmersiveTemporarilyDisabled = fullPlayerSlice.isImmersiveTemporarilyDisabled
    val isRemotePlaybackActive = fullPlayerSlice.isRemotePlaybackActive
    val selectedRouteName = fullPlayerSlice.selectedRouteName
    val isBluetoothEnabled = fullPlayerSlice.isBluetoothEnabled
    val bluetoothName = fullPlayerSlice.bluetoothName
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val queueGestureBottomExclusion = maxOf(20.dp, navigationBarBottomInset + 8.dp)
    val queueGestureBottomExclusionPx = with(LocalDensity.current) {
        queueGestureBottomExclusion.toPx()
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val fileImportScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                fileImportScope.launch {
                    try {
                        val validation = validateLyricsImport(context, it)
                        val validatedImport: ValidatedLyricsImport = when (validation) {
                            is LyricsImportValidationResult.Valid -> validation.value
                            is LyricsImportValidationResult.Invalid -> {
                                playerViewModel.sendToast(
                                    LyricsImportSecurity.messageFor(validation.reason)
                                )
                                return@launch
                            }
                        }

                        val currentSongId = currentSong?.id?.toLongOrNull()
                        if (currentSongId == null) {
                            playerViewModel.sendToast("No song selected for lyrics import.")
                            return@launch
                        }

                        playerViewModel.importLyricsFromFile(currentSongId, validatedImport)
                        showFetchLyricsDialog = false
                        showLyricsSheet = true
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading imported lyrics file")
                        playerViewModel.sendToast("Error reading file.")
                    }
                }
            }
        }
    )

    // 字体文件选择器 — 用于导入自定义字体
    val fontFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                fileImportScope.launch {
                    try {
                        val contentResolver = context.contentResolver
                        val fileName = run {
                            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
                            val cursor = contentResolver.query(it, projection, null, null, null)
                            cursor?.use { c ->
                                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                c.moveToFirst()
                                c.getString(nameIndex)
                            } ?: "custom_font.ttf"
                        }

                        if (!fileName.endsWith(".ttf", true) && !fileName.endsWith(".otf", true)) {
                            playerViewModel.sendToast("请选择 .ttf 或 .otf 字体文件")
                            return@launch
                        }

                        // 确保字体目录存在
                        val fontsDir = java.io.File(context.filesDir, "fonts")
                        if (!fontsDir.exists()) fontsDir.mkdirs()

                        // 复制文件到应用内部存储
                        val destFile = java.io.File(fontsDir, fileName)
                        context.contentResolver.openInputStream(it)?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // 设置为当前歌词字体
                        playerViewModel.setLyricsFontFamily("CUSTOM:$fileName")
                        playerViewModel.sendToast("字体已导入")
                    } catch (e: Exception) {
                        Timber.e(e, "Error importing font file")
                        playerViewModel.sendToast("字体导入失败")
                    }
                }
            }
        }
    )

    // totalDurationValue is derived from stablePlayerState, so it's fine.
    // OPTIMIZATION: Use passed provider instead of collecting flow
    val totalDurationValue = totalDurationProvider()

    val playerOnBaseColor by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onPrimaryContainer,
        animationSpec = tween(durationMillis = 400),
        label = "PlayerOnBaseColor"
    )
    val playerAccentColor by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.primary,
        animationSpec = tween(durationMillis = 400),
        label = "PlayerAccentColor"
    )
    val playerOnAccentColor by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onPrimary,
        animationSpec = tween(durationMillis = 400),
        label = "PlayerOnAccentColor"
    )

    val transportPlayPauseColors = TransportButtonColors(
        container = androidx.compose.animation.animateColorAsState(
            targetValue = LocalMaterialTheme.current.tertiaryFixedDim,
            animationSpec = tween(durationMillis = 400),
            label = "TransportPlayPauseContainer"
        ).value,
        content = androidx.compose.animation.animateColorAsState(
            targetValue = LocalMaterialTheme.current.onTertiaryFixed,
            animationSpec = tween(durationMillis = 400),
            label = "TransportPlayPauseContent"
        ).value
    )
    val transportSkipColors = TransportButtonColors(
        container = androidx.compose.animation.animateColorAsState(
            targetValue = LocalMaterialTheme.current.secondaryFixedDim,
            animationSpec = tween(durationMillis = 400),
            label = "TransportSkipContainer"
        ).value,
        content = androidx.compose.animation.animateColorAsState(
            targetValue = LocalMaterialTheme.current.onSecondaryFixed,
            animationSpec = tween(durationMillis = 400),
            label = "TransportSkipContent"
        ).value
    )
    val transportSkipButtonColors = TransportButtonColors(
        container = playerAccentColor,
        content = playerOnAccentColor
    )
    val progressActiveColor = playerOnBaseColor

    val placeholderColor = playerOnBaseColor.copy(alpha = 0.1f)
    val placeholderOnColor = playerOnBaseColor.copy(alpha = 0.2f)

    // ⚡ Optimization: Consolidate color animations at top level
    // These animations were duplicated in BottomToggleRow
    val surfaceContainerLowest by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.surfaceContainerLowest,
        animationSpec = tween(durationMillis = 400),
        label = "SurfaceContainerLowest"
    )
    val onSurface by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onSurface,
        animationSpec = tween(durationMillis = 400),
        label = "OnSurface"
    )
    val primaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.primaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "PrimaryFixed"
    )
    val onPrimaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onPrimaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "OnPrimaryFixed"
    )
    val secondaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.secondaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "SecondaryFixed"
    )
    val onSecondaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onSecondaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "OnSecondaryFixed"
    )
    val tertiaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.tertiaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "TertiaryFixed"
    )
    val onTertiaryFixed by androidx.compose.animation.animateColorAsState(
        targetValue = LocalMaterialTheme.current.onTertiaryFixed,
        animationSpec = tween(durationMillis = 400),
        label = "OnTertiaryFixed"
    )

    // 按窗口实际宽高比判定横屏/平板布局：平板小窗/分屏变窄时自动切换为手机模式。
    // 使用 LocalWindowInfo（真实窗口尺寸）而非 Configuration，避免部分平板 ROM
    // 在分屏/小窗时不更新 Configuration 导致布局不切换
    // 1:1（width == height）也按平板/横屏布局处理，避免竖屏电台封面与按钮遮挡
    val isLandscape = with(LocalWindowInfo.current.containerSize) { width >= height }


    // Lógica para el botón de Lyrics en el reproductor expandido
    val latestShowLyricsSheet by rememberUpdatedState(showLyricsSheet)
    val onLyricsClick = remember {{ showLyricsSheet = true }}

    val onCommentClick = remember {{ showCommentSheet = true }}

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = song, // Use 'song' which is derived from args/retained
                onConfirm = { forcePick ->
                    // El usuario confirma, iniciamos la búsqueda
                    playerViewModel.fetchLyricsForCurrentSong(forcePick)
                },
                onPickResult = { result ->
                    playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
                },
                onManualSearch = { title, artist ->
                    playerViewModel.searchLyricsManually(title, artist)
                },
                onDismiss = {
                    // El usuario cancela o cierra el diálogo
                    showFetchLyricsDialog = false
                    playerViewModel.resetLyricsSearchState()
                },
                onImport = {
                    filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes())
                }
            )
        }
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.PickResult -> {
                // 自动显示歌词搜索对话框
                if (!showFetchLyricsDialog) {
                    showFetchLyricsDialog = true
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            is LyricsSearchUiState.NotFound -> {
                // 自动显示歌词搜索对话框（允许手动搜索）
                if (!showFetchLyricsDialog) {
                    showFetchLyricsDialog = true
                }
            }
            else -> Unit
        }
    }

    val latestCurrentPlaybackQueue by rememberUpdatedState(currentPlaybackQueue)
    val latestCurrentQueueSourceName by rememberUpdatedState(currentQueueSourceName)
    val onAlbumSongSelected: (Song, Int) -> Unit = remember {{ newSong, index ->
        playerViewModel.showAndPlaySong(
            song = newSong,
            contextSongs = latestCurrentPlaybackQueue,
            queueName = latestCurrentQueueSourceName,
            indexInQueue = index
        )
    }}

    val onSongMetadataQueueClick = remember {{
        showSongInfoBottomSheet = true
        onShowQueueClicked()
    }}

    val latestSong by rememberUpdatedState(song)
    val latestCurrentSongArtists by rememberUpdatedState(currentSongArtists)
    val latestShowArtistPicker by rememberUpdatedState(showArtistPicker)
    val onSongMetadataArtistClick = remember {{
        val resolvedArtistId = latestCurrentSongArtists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: latestSong.artistId
        if (latestCurrentSongArtists.size > 1) {
            showArtistPicker = true
        } else {
            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId, latestSong.neteaseId)
        }
    }}

    var pendingCarouselIndex by remember { mutableStateOf<Int?>(null) }
    val currentQueueIndex = remember(song.id, currentMediaItemIndex, currentPlaybackQueue) {
        resolveQueueIndex(
            queue = currentPlaybackQueue,
            songId = song.id,
            currentMediaItemIndex = currentMediaItemIndex
        )
    }
    val skipRequests = remember {
        MutableSharedFlow<SkipDirection>(
            extraBufferCapacity = 16
        )
    }
    val latestQueue by rememberUpdatedState(currentPlaybackQueue)
    val latestSongId by rememberUpdatedState(song.id)
    val latestCurrentQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestRepeatMode by rememberUpdatedState(repeatMode)
    val latestIsRemotePlaybackActive by rememberUpdatedState(isRemotePlaybackActive)
    val latestCurrentPositionProvider by rememberUpdatedState(currentPositionProvider)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    LaunchedEffect(currentQueueIndex, pendingCarouselIndex) {
        if (pendingCarouselIndex == currentQueueIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(pendingCarouselIndex, currentQueueIndex) {
        val targetIndex = pendingCarouselIndex ?: return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        if (pendingCarouselIndex == targetIndex && currentQueueIndex != targetIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(skipRequests) {
        skipRequests.collect { direction ->
            when (direction) {
                SkipDirection.NEXT -> latestOnNext()
                SkipDirection.PREVIOUS -> latestOnPrevious()
            }

            kotlinx.coroutines.delay(SKIP_COMMAND_GUARD_MS)
        }
    }

    val predictSkipCarouselIndex = remember {{ direction: SkipDirection ->
        val queueSnapshot = latestQueue
        val baseIndex = pendingCarouselIndex
            ?: latestCurrentQueueIndex
            ?: queueSnapshot.indexOfFirst { it.id == latestSongId }.takeIf { it >= 0 }

        when (direction) {
            SkipDirection.NEXT -> predictSkipNextCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
            SkipDirection.PREVIOUS -> predictSkipPreviousCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                currentPositionMs = latestCurrentPositionProvider(),
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
        }
    }}

    val requestSkip = remember {{ direction: SkipDirection ->
        val predictedTargetIndex = predictSkipCarouselIndex(direction)
        if (skipRequests.tryEmit(direction) && predictedTargetIndex != null) {
            pendingCarouselIndex = predictedTargetIndex
        }
    }}

    val onNextWithOptimisticCarousel = remember {{ requestSkip(SkipDirection.NEXT); Unit }}

    val onPreviousWithOptimisticCarousel = remember {{ requestSkip(SkipDirection.PREVIOUS); Unit }}

    val albumCoverSection: @Composable (Modifier) -> Unit = { modifier ->
        FullPlayerAlbumCoverSection(
            song = song,
            currentPlaybackQueue = currentPlaybackQueue,
            currentMediaItemIndex = currentQueueIndex ?: currentMediaItemIndex,
            carouselStyle = carouselStyle,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            isPlayingProvider = isPlayingProvider,
            playWhenReadyProvider = playWhenReadyProvider,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            albumArtQuality = albumArtQuality,
            requestedScrollIndex = pendingCarouselIndex,
            onSongSelected = onAlbumSongSelected,
            onAlbumClick = { albumSong ->
                playerViewModel.triggerAlbumNavigationFromPlayer(albumSong.albumId)
            },
            modifier = modifier
        )
    }

    val playerProgressSection: @Composable () -> Unit = {
        if (isRadioPlayback) {
            // 广播电台为实时流：无可拖动进度，显示热门电台推荐（可快速切换）
            RadioRecommendationSection(
                currentSongId = song.id,
                onBaseColor = playerOnBaseColor,
                baseColor = LocalMaterialTheme.current.primaryContainer,
                onStationClick = { station ->
                    playerViewModel.playUrl(
                        url = station.streamUrl,
                        title = station.name,
                        artist = station.country.ifBlank { "Radio" },
                        cover = station.favicon,
                        songId = "radio://${station.stationUuid}"
                    )
                }
            )
        } else {
            FullPlayerProgressSection(
                song = song,
                playbackMetadataMediaId = playbackAudioMetadata.mediaId,
                playbackMetadataMimeType = playbackAudioMetadata.mimeType,
                playbackMetadataBitrate = playbackAudioMetadata.bitrate,
                playbackMetadataSampleRate = playbackAudioMetadata.sampleRate,
                currentPositionProvider = currentPositionProvider,
                totalDurationValue = totalDurationValue,
                showPlayerFileInfo = showPlayerFileInfo,
                onSeek = onSeek,
                expansionFractionProvider = expansionFractionProvider,
                isPlayingProvider = isPlayingProvider,
                currentSheetState = currentSheetState,
                progressActiveColor = progressActiveColor,
                playerOnBaseColor = playerOnBaseColor,
                allowRealtimeUpdates = allowRealtimeUpdates,
                isSheetDragGestureActive = isSheetDragGestureActive,
                loadingTweaks = loadingTweaks
            )
        }
    }

    val controlsSection: @Composable () -> Unit = {
        val downloads by playerViewModel.downloads.collectAsStateWithLifecycle()
        val downloadInfo = remember(currentSong?.id, downloads) {
            currentSong?.let { song -> downloads.find { it.songId == song.id } }
        }
        val isOnlineSong = currentSong?.let { playerViewModel.isOnlineSong(it) } == true
        FullPlayerControlsSection(
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isPlayingProvider = isPlayingProvider,
            onPrevious = onPreviousWithOptimisticCarousel,
            onPlayPause = onPlayPause,
            onNext = onNextWithOptimisticCarousel,
            transportPlayPauseColors = transportPlayPauseColors,
            transportSkipColors = transportSkipButtonColors,
            isShuffleEnabledProvider = isShuffleEnabledProvider,
            shuffleTransitionInProgress = shuffleTransitionInProgress,
            repeatModeProvider = repeatModeProvider,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            isOnlineSong = isOnlineSong,
            onDownloadClick = onDownloadClick,
            downloadProgress = downloadInfo?.progress.takeIf { it != 0f || downloadInfo?.isComplete == false },
            isDownloadComplete = downloadInfo?.isComplete == true,
            isDownloadFailed = downloadInfo?.isFailed == true,
            isRadioPlayback = isRadioPlayback,
            surfaceContainerLowest = surfaceContainerLowest,
            onSurface = onSurface,
            primaryFixed = primaryFixed,
            onPrimaryFixed = onPrimaryFixed,
            secondaryFixed = secondaryFixed,
            onSecondaryFixed = onSecondaryFixed,
            tertiaryFixed = tertiaryFixed,
            onTertiaryFixed = onTertiaryFixed,
        )
    }

    val portraitSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            currentQueueSourceName = currentQueueSourceName,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = false,
            isRadioPlayback = isRadioPlayback,
            onLyricsClick = onLyricsClick,
            onCommentClick = onCommentClick,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = gradientEdgeColor,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }

    val landscapeSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            currentQueueSourceName = currentQueueSourceName,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = true,
            isRadioPlayback = isRadioPlayback,
            onLyricsClick = onLyricsClick,
            onCommentClick = onCommentClick,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = gradientEdgeColor,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.pointerInput(currentSheetState, queueGestureBottomExclusionPx, isRadioPlayback) {
            val queueDragActivationThresholdPx = 4.dp.toPx()
            val quickFlickVelocityThreshold = -520f

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Check condition AFTER the down event occurs
                val isFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.99f

                if (!isFullyExpanded || isRadioPlayback) {
                    // 广播电台为实时流，没有播放列表：上滑不进入队列
                    return@awaitEachGesture
                }

                val bottomGestureBoundaryY =
                    (size.height.toFloat() - queueGestureBottomExclusionPx).coerceAtLeast(0f)
                if (down.position.y >= bottomGestureBoundaryY) {
                    // Let the system Home/back gesture win near the bottom edge.
                    return@awaitEachGesture
                }

                // Proceed with gesture logic
                var dragConsumedByQueue = false
                val velocityTracker = VelocityTracker()
                var totalDrag = 0f
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                drag(down.id) { change ->
                    val dragAmount = change.positionChange().y
                    totalDrag += dragAmount
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val isDraggingUp = totalDrag < -queueDragActivationThresholdPx

                    if (isDraggingUp && !dragConsumedByQueue) {
                        dragConsumedByQueue = true
                        onQueueDragStart()
                    }

                    if (dragConsumedByQueue) {
                        change.consume()
                        onQueueDrag(dragAmount)
                    }
                }

                val velocity = velocityTracker.calculateVelocity().y
                if (dragConsumedByQueue) {
                    onQueueRelease(totalDrag, velocity)
                } else if (
                    totalDrag < -(queueDragActivationThresholdPx * 2f) &&
                    velocity < quickFlickVelocityThreshold
                ) {
                    // Treat short/fast upward flick as queue-open intent.
                    onQueueRelease(totalDrag, velocity)
                }
            }
        },
        topBar = {
            // MD3: TopAppBar 在竖屏时滑入，横屏时向上滑出淡出
            AnimatedVisibility(
                visible = !isLandscape,
                enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                       slideOutVertically(
                           targetOffsetY = { -it / 2 },
                           animationSpec = tween(220, easing = FastOutSlowInEasing)
                       )
            ) {
                TopAppBar(
                    modifier = Modifier.graphicsLayer {
                        val fraction = expansionFractionProvider()
                        // TopBar should always fade in smoothly, ignoring delayAll to avoid empty UI
                        val startThreshold = 0f
                        val endThreshold = 1f
                        alpha = ((fraction - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    ),
                    title = {
                        if (!isCastConnecting) {
                            AnimatedVisibility(visible = (!isRemotePlaybackActive)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        modifier = Modifier.padding(start = 18.dp),
                                        text = stringResource(R.string.setcat_now_playing),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (currentSong != null && (currentSong.telegramChatId != null || currentSong.contentUriString.startsWith("telegram:"))) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Rounded.Cloud,
                                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_cloud_stream),
                                            tint = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(start = 8.dp).size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                // Ancho total = 14dp de padding + 42dp del botón
                                .width(56.dp)
                                .height(42.dp),
                            // 2. Alinea el contenido (el botón) al final (derecha) y centrado verticalmente
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            // 3. Tu botón circular original, sin cambios
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(playerOnAccentColor.copy(alpha = 0.7f))
                                    .clickable(onClick = onCollapse),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                                    contentDescription = stringResource(R.string.presentation_batch_g_player_cd_collapse),
                                    tint = playerAccentColor
                                )
                            }
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier
                                .padding(end = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val showCastLabel = isCastConnecting || (isRemotePlaybackActive && selectedRouteName != null)
                            val isBluetoothActive =
                                isBluetoothEnabled && !bluetoothName.isNullOrEmpty() && !isRemotePlaybackActive && !isCastConnecting
                            val castIconPainter = when {
                                isCastConnecting || isRemotePlaybackActive -> painterResource(R.drawable.rounded_cast_24)
                                isBluetoothActive -> painterResource(R.drawable.rounded_bluetooth_24)
                                else -> painterResource(R.drawable.rounded_mobile_speaker_24)
                            }
                            val castCornersExpanded = 50.dp
                            val castCornersCompact = 6.dp
                            val castTopStart = castCornersExpanded
                            val castTopEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castBottomStart = castCornersExpanded
                            val castBottomEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castContainerColor = playerOnAccentColor.copy(alpha = 0.7f)
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .align(Alignment.CenterVertically)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    .widthIn(
                                        min = 50.dp,
                                        max = if (showCastLabel) 190.dp else 58.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = castTopStart.coerceAtLeast(0.dp),
                                            topEnd = castTopEnd.coerceAtLeast(0.dp),
                                            bottomStart = castBottomStart.coerceAtLeast(0.dp),
                                            bottomEnd = castBottomEnd.coerceAtLeast(0.dp)
                                        )
                                    )
                                    .background(castContainerColor)
                                    .clickable { onShowCastClicked() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        painter = castIconPainter,
                                        contentDescription = when {
                                            isCastConnecting || isRemotePlaybackActive -> stringResource(R.string.presentation_batch_g_player_cd_cast)
                                            isBluetoothActive -> stringResource(R.string.presentation_batch_g_player_cd_bluetooth)
                                            else -> stringResource(R.string.presentation_batch_g_player_cd_local_playback)
                                        },
                                        tint = playerAccentColor
                                    )
                                    AnimatedVisibility(visible = showCastLabel) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(8.dp))
                                            AnimatedContent(
                                                targetState = when {
                                                    isCastConnecting -> stringResource(R.string.presentation_batch_g_player_connecting)
                                                    isRemotePlaybackActive && selectedRouteName != null -> selectedRouteName
                                                    else -> ""
                                                },
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                                                },
                                                label = "castButtonLabel"
                                            ) { label ->
                                                Row(
                                                    modifier = Modifier.padding(end = 16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = playerAccentColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    AnimatedVisibility(visible = isCastConnecting) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier
                                                                .size(14.dp),
                                                            strokeWidth = 2.dp,
                                                            color = playerAccentColor
                                                        )
                                                    }
                                                    if (isRemotePlaybackActive && !isCastConnecting) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(LocalMaterialTheme.current.onTertiaryContainer)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Queue Button（广播电台播放时不显示：实时流没有播放列表）
                            if (!isRadioPlayback) {
                                Box(
                                    modifier = Modifier
                                        .size(height = 42.dp, width = 50.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 6.dp,
                                                topEnd = 50.dp,
                                                bottomStart = 6.dp,
                                                bottomEnd = 50.dp
                                            )
                                        )
                                        .background(playerOnAccentColor.copy(alpha = 0.7f))
                                        .clickable {
                                            showSongInfoBottomSheet = true
                                            onShowQueueClicked()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_queue_music_24),
                                        contentDescription = stringResource(R.string.presentation_batch_g_player_cd_queue),
                                        tint = playerAccentColor
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        // MD3: 方向变化时先 alpha=0 再淡入新布局，避免双布局同时测量导致错位
        var contentVisible by remember(isLandscape) { mutableStateOf(false) }
        LaunchedEffect(isLandscape) { contentVisible = true }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "orientationAlpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            if (isLandscape) {
                FullPlayerLandscapeContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = landscapeSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection,
                    isRadioPlayback = isRadioPlayback
                )
            } else {
                FullPlayerPortraitContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = portraitSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection,
                    isRadioPlayback = isRadioPlayback
                )
            }
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playbackPositionFlow = playerViewModel.currentPlaybackPosition,
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes()) },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong?.id?.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            lyricsTextStyle = MaterialTheme.typography.titleLarge,
            lyricsFontSize = fullPlayerSlice.lyricsFontSize,
            onLyricsFontSizeChange = { playerViewModel.setLyricsFontSize(it) },
            lyricsFontFamily = lyricsFontFamily,
            onLyricsFontFamilyChange = { playerViewModel.setLyricsFontFamily(it) },
            onImportCustomFont = { fontFilePickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/font-sfnt")) },
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showLyricsSheet = false },
            onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
            onTranslateViaAi = { playerViewModel.translateLyricsViaAi() },
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            },
            onNext = onNext,
            onPrev = onPrevious,
            immersiveLyricsEnabled = immersiveLyricsEnabled,
            immersiveLyricsTimeout = immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
            onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            showLyricsTrackInfo = fullPlayerSlice.showLyricsTrackInfo
        )
    }

    AnimatedVisibility(
        visible = showCommentSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        // 从歌曲对象中解析出网易云/在线源的歌曲 ID
        val resolvedSongId = remember(song) { resolveCommentSongId(song) }
        // cookie 优先取设置页登录的完整 cookie；为空时用内置 SDK 会话拼接兜底
        val commentCookie = playerViewModel.neteaseCookie.ifBlank {
            net.moriafly.ncm.NcmSession.INSTANCE?.cookies?.entries
                ?.joinToString("; ") { "${it.key}=${it.value}" }
        }?.takeIf { it.isNotBlank() }
        CommentSheet(
            songId = resolvedSongId,
            songTitle = song.title,
            songArtist = song.displayArtist,
            api = playerViewModel.lxSearchApi,
            personalFmApi = playerViewModel.personalFmApi,
            cookie = commentCookie,
            currentUserId = playerViewModel.neteaseUserId,
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showCommentSheet = false }
        )
    }

    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && currentSongArtists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            song = song,
            artists = currentSongArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                playerViewModel.triggerArtistNavigationFromPlayer(artist.id, song.neteaseId)
                showArtistPicker = false
            }
        )
    }
}


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun FullPlayerAlbumCoverSection(
    song: Song,
    currentPlaybackQueue: ImmutableList<Song>,
    currentMediaItemIndex: Int,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    placeholderColor: Color,
    placeholderOnColor: Color,
    albumArtQuality: AlbumArtQuality,
    requestedScrollIndex: Int?,
    onSongSelected: (Song, Int) -> Unit,
    onAlbumClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayAlbumCarousel
    val shouldApplyPausedScale = !isPlayingProvider() && !playWhenReadyProvider()
    // Use a short deterministic tween instead of spring(StiffnessLow). The original
    // spring took ~1s to settle, producing ~60 frames of graphicsLayer invalidations
    // that overlapped with any subsequent sheet-collapse gesture. A 260 ms tween
    // finishes well before the user can start the next gesture, keeping the album
    // art's "pause squish" visible but removing the long tail of frame work.
    val albumArtScale by animateFloatAsState(
        targetValue = if (shouldApplyPausedScale) 0.95f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AlbumArtScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 计算基础尺寸
        val externalHeightConstraint = maxHeight
        val widthBasedHeight = when (carouselStyle) {
            CarouselStyle.NO_PEEK -> maxWidth
            CarouselStyle.ONE_PEEK -> maxWidth * 0.8f
            CarouselStyle.TWO_PEEK -> maxWidth * 0.6f
            else -> maxWidth
        }
        
        // 竖屏模式：封面应该是正方形，取宽度的最小值
        // 横屏模式：使用外部高度约束或宽度计算高度的较小值
        val carouselHeight = if (externalHeightConstraint < maxWidth) {
            // 竖屏模式：取外部高度约束（正方形）和宽度计算高度的较小值
            // 正方形时 externalHeightConstraint 应该等于宽度，所以 minOf 会取较小的那个
            minOf(externalHeightConstraint, widthBasedHeight)
        } else {
            // 横屏模式：使用外部高度约束或宽度计算高度的较小值
            minOf(externalHeightConstraint, widthBasedHeight)
        }

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
            switchOnDragRelease = loadingTweaks.switchOnDragRelease,
            isSheetDragGestureActive = isSheetDragGestureActive,
            sharedBoundsModifier = Modifier.widthIn(max = carouselHeight).height(carouselHeight),
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.08f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(
                        Modifier
                            .widthIn(max = carouselHeight) // 正方形
                            .height(carouselHeight)
                            .graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                    )
                } else {
                    AlbumPlaceholder(
                        height = carouselHeight,
                        color = placeholderColor,
                        onColor = placeholderOnColor,
                        modifier = Modifier
                            .widthIn(max = carouselHeight) // 正方形
                            .graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                    )
                }
            }
        ) {
            AlbumCarouselSection(
                currentSong = song,
                queue = currentPlaybackQueue,
                expansionFraction = expansionFractionProvider(),
                currentMediaItemIndex = currentMediaItemIndex,
                requestedScrollIndex = requestedScrollIndex,
                onSongSelected = { newSong, index ->
                    if (newSong.id != song.id || index != currentMediaItemIndex) {
                        onSongSelected(newSong, index)
                    }
                },
                onAlbumClick = onAlbumClick,
                carouselStyle = carouselStyle,
                modifier = Modifier
                    .widthIn(max = carouselHeight) // 限制宽度等于高度，实现正方形
                    .height(carouselHeight)
                    .graphicsLayer {
                        scaleX = albumArtScale
                        scaleY = albumArtScale
                    },
                albumArtQuality = albumArtQuality
            )
        }
    }
}

@Composable
private fun FullPlayerControlsSection(
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    transportPlayPauseColors: TransportButtonColors,
    transportSkipColors: TransportButtonColors,
    isShuffleEnabledProvider: () -> Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatModeProvider: () -> Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isOnlineSong: Boolean,
    onDownloadClick: () -> Unit,
    downloadProgress: Float?,
    isDownloadComplete: Boolean,
    isDownloadFailed: Boolean,
    isRadioPlayback: Boolean = false,
    surfaceContainerLowest: Color,
    onSurface: Color,
    primaryFixed: Color,
    onPrimaryFixed: Color,
    secondaryFixed: Color,
    onSecondaryFixed: Color,
    tertiaryFixed: Color,
    onTertiaryFixed: Color,
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        // 广播时播放/暂停已移入底部行，控制区仅剩一行（56dp），无需保留 180dp 固定高度，
        // 否则会留出大片空白（表现为“推荐区与播放控制距离太大”）并挤压推荐区空间
        sharedBoundsModifier = Modifier
            .fillMaxWidth()
            .height(if (isRadioPlayback) 56.dp else 180.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.42f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders || isRadioPlayback) {
                Box(Modifier.fillMaxWidth().height(if (isRadioPlayback) 56.dp else 180.dp))
            } else {
                ControlsPlaceholder(placeholderColor, placeholderOnColor)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 广播电台实时流：播放/暂停按钮已移到底部行的收藏按钮右侧
            if (!isRadioPlayback) {
                AnimatedPlaybackControls(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    isPlayingProvider = isPlayingProvider,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    height = 72.dp,
                    pressAnimationSpec = controlSpatialSpec,
                    releaseDelay = 220L,
                    colorOtherButtons = transportSkipColors.container,
                    colorPlayPause = transportPlayPauseColors.container,
                    tintPlayPauseIcon = transportPlayPauseColors.content,
                    tintOtherIcons = transportSkipColors.content,
                    colorPreviousButton = transportSkipColors.container,
                    colorNextButton = transportSkipColors.container,
                    tintPreviousIcon = transportSkipColors.content,
                    tintNextIcon = transportSkipColors.content
                )
            }

            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 26.dp),
                isShuffleEnabled = isShuffleEnabledProvider(),
                isShuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatMode = repeatModeProvider(),
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                isOnlineSong = isOnlineSong && !isRadioPlayback,
                onDownloadClick = onDownloadClick,
                downloadProgress = downloadProgress,
                isDownloadComplete = isDownloadComplete,
                isDownloadFailed = isDownloadFailed,
                isRadioPlayback = isRadioPlayback,
                showRadioPlayPause = isRadioPlayback,
                radioIsPlayingProvider = isPlayingProvider,
                onRadioPlayPause = onPlayPause,
                radioPlayPauseColor = transportPlayPauseColors.container,
                radioPlayPauseContentColor = transportPlayPauseColors.content,
                surfaceContainerLowest = surfaceContainerLowest,
                onSurface = onSurface,
                primaryFixed = primaryFixed,
                onPrimaryFixed = onPrimaryFixed,
                secondaryFixed = secondaryFixed,
                onSecondaryFixed = onSecondaryFixed,
                tertiaryFixed = tertiaryFixed,
                onTertiaryFixed = onTertiaryFixed,
            )
        }
    }
}

@Composable
private fun FullPlayerProgressSection(
    song: Song,
    playbackMetadataMediaId: String?,
    playbackMetadataMimeType: String?,
    playbackMetadataBitrate: Int?,
    playbackMetadataSampleRate: Int?,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    showPlayerFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    progressActiveColor: Color,
    playerOnBaseColor: Color,
    allowRealtimeUpdates: Boolean,
    isSheetDragGestureActive: Boolean,
    loadingTweaks: FullPlayerLoadingTweaks
) {
    val isMetadataForCurrentSong = playbackMetadataMediaId == song.id
    val audioMimeType = if (isMetadataForCurrentSong) {
        playbackMetadataMimeType ?: song.mimeType
    } else {
        song.mimeType
    }
    val audioBitrate = if (isMetadataForCurrentSong) {
        playbackMetadataBitrate ?: song.bitrate
    } else {
        song.bitrate
    }
    val audioSampleRate = if (isMetadataForCurrentSong) {
        playbackMetadataSampleRate ?: song.sampleRate
    } else {
        song.sampleRate
    }

    PlayerProgressBarSection(
        songId = song.id,
        currentPositionProvider = currentPositionProvider,
        totalDurationValue = totalDurationValue,
        songDurationHintMs = song.duration,
        audioMimeType = audioMimeType,
        audioBitrate = audioBitrate,
        audioSampleRate = audioSampleRate,
        showAudioFileInfo = showPlayerFileInfo,
        onSeek = onSeek,
        expansionFractionProvider = expansionFractionProvider,
        isPlayingProvider = isPlayingProvider,
        currentSheetState = currentSheetState,
        activeTrackColor = progressActiveColor,
        inactiveTrackColor = playerOnBaseColor.copy(alpha = 0.2f),
        thumbColor = progressActiveColor,
        timeTextColor = playerOnBaseColor,
        allowRealtimeUpdates = allowRealtimeUpdates,
        isSheetDragGestureActive = isSheetDragGestureActive,
        loadingTweaks = loadingTweaks
    )
}

private fun resolveQueueIndex(
    queue: ImmutableList<Song>,
    songId: String,
    currentMediaItemIndex: Int
): Int? {
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
}

private fun predictSkipNextCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex < queue.lastIndex -> safeCurrentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }
}

private fun predictSkipPreviousCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    currentPositionMs: Long,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    if (currentPositionMs > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex > 0 -> safeCurrentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> queue.lastIndex
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullPlayerSongMetadataSection(
    song: Song,
    currentSongArtists: List<Artist>,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    currentQueueSourceName: String,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isLandscape: Boolean,
    isRadioPlayback: Boolean = false,
    onLyricsClick: () -> Unit,
    onCommentClick: () -> Unit,
    playerOnBaseColor: Color,
    playerViewModel: PlayerViewModel,
    gradientEdgeColor: Color,
    chipColor: Color,
    chipContentColor: Color,
    onQueueClick: () -> Unit,
    onArtistClick: () -> Unit,
    isPlayingProvider: () -> Boolean = { true }
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.20f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(70.dp))
            } else {
                MetadataPlaceholder(
                    expansionFractionProvider = expansionFractionProvider,
                    color = placeholderColor,
                    onColor = placeholderOnColor,
                    showQueueButtons = isLandscape
                )
            }
        }
    ) {
        SongMetadataDisplaySection(
            modifier = Modifier
                .padding(start = 0.dp),
            onClickLyrics = onLyricsClick,
            onClickComment = onCommentClick,
            isRadioPlayback = isRadioPlayback,
            song = song,
            currentSongArtists = currentSongArtists,
            expansionFractionProvider = expansionFractionProvider,
            textColor = playerOnBaseColor,
            artistTextColor = playerOnBaseColor.copy(alpha = 0.7f),
            playerViewModel = playerViewModel,
            gradientEdgeColor = gradientEdgeColor,
            chipColor = chipColor,
            chipContentColor = chipContentColor,
            currentQueueSourceName = currentQueueSourceName,
            showQueueButton = isLandscape,
            onClickQueue = onQueueClick,
            onClickArtist = onArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }
}

@Composable
private fun FullPlayerPortraitContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit,
    downloadSection: @Composable () -> Unit = {},
    isRadioPlayback: Boolean = false
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (isRadioPlayback) {
            // 电台动态布局：封面占满剩余空间（保持正方形），底部信息/直播指示/控制自适应高度。
            // 手机、平板竖屏/横屏都按可用空间伸缩，不会溢出、不留大片空白。
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 封面：weight 占满剩余空间，正方形居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    albumCoverSection(
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(1f)
                    )
                }
                // 底部：信息 + 直播指示 + 控制按钮，自适应高度
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    songMetadataSection()
                    playerProgressSection()
                    Spacer(Modifier.height(4.dp))
                    controlsSection()
                }
            }
        } else {
        val totalHeight = maxHeight
        val totalWidth = maxWidth
        
        // 封面左右留白
        val coverHorizontalPadding = 12.dp
        
        // 歌曲信息和进度条区域需要的高度
        val metadataProgressHeight = 100.dp
        
        // 播放控制区域需要的最低高度（播放按钮 + 底部切换行 + 间距）
        // FullPlayerControlsSection 内部使用 180dp 固定高度
        val controlsSectionMinHeight = 180.dp
        
        // 整个底部区域的最低高度（确保收藏那三个按钮不被挤出屏幕）
        val bottomMinHeight = metadataProgressHeight + controlsSectionMinHeight + 20.dp
        
        // 方法一：封面边长 = 屏幕高度 - 底部区域最低高度
        val coverSizeMethod1 = (totalHeight - bottomMinHeight).coerceAtLeast(100.dp)
        
        // 方法二：封面边长 = 屏幕宽度 - 左右留白
        val coverSizeMethod2 = totalWidth - coverHorizontalPadding * 2
        
        // 决策规则：
        // 如果方法一 > 方法二，采用方法二，空白用按钮拉高填补
        // 如果方法二 > 方法一，采用方法一，防止按钮被挤出
        val coverSize: Dp
        val bottomHeight: Dp
        
        if (coverSizeMethod1 > coverSizeMethod2) {
            coverSize = coverSizeMethod2
            bottomHeight = (totalHeight - coverSize).coerceAtLeast(bottomMinHeight)
        } else {
            coverSize = coverSizeMethod1
            bottomHeight = bottomMinHeight
        }
        
        // 水平padding
        val horizontalPadding = 16.dp
        
        // 使用Column布局：封面占固定高度，按钮占剩余所有空间
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面区域 - 高度正好等于封面尺寸
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverSize)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                albumCoverSection(Modifier.size(coverSize))
            }
            
            // 控制按钮区域 - 占据剩余所有空间（至少为bottomHeight）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomHeight)
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 歌曲信息和进度条
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    songMetadataSection()
                    playerProgressSection()
                }

                // 播放控制区
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    controlsSection()
                }
            }
        }
        } // end else (普通歌曲布局)
    }
}

@Composable
private fun FullPlayerLandscapeContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit,
    downloadSection: @Composable () -> Unit = {},
    isRadioPlayback: Boolean = false
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRadioPlayback) {
            // 电台播放器精简布局：上面封面，下面信息 + 收藏 + 暂停（无热门电台列表）
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                albumCoverSection(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                songMetadataSection()
                controlsSection()
            }
        } else {
            val spacing = 9.dp
            // 左右两栏各占一半宽度；左侧封面为正方形，
            // 边长 = min(单栏宽度, 行高)。右侧所有内容高度不得超过该边长。
            val coverWidth = (maxWidth - spacing) / 2
            val coverSize = minOf(coverWidth, maxHeight)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                albumCoverSection(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
                Spacer(Modifier.width(spacing))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .height(coverSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    songMetadataSection()
                    playerProgressSection()
                    controlsSection()
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    onClickComment: () -> Unit,
    currentQueueSourceName: String,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true },
    isRadioPlayback: Boolean = false
) {
    // 评论依赖网易云接口（加载/发送），非网易云歌曲（本地/其它在线源）不显示评论按钮
    val canShowComment = song?.let { resolveCommentSongId(it).isNotBlank() } ?: false
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                title = currentSong.title,
                artist = currentSong.displayArtist,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                gradientEdgeColor = gradientEdgeColor,
                playerViewModel = playerViewModel,
                onClickArtist = onClickArtist,
                currentQueueSourceName = currentQueueSourceName,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                isPlayingProvider = isPlayingProvider,
                songId = currentSong.id,
                songNeteaseId = currentSong.neteaseId,
                songContentUriString = currentSong.contentUriString
            )
        }
        
        val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
        val isBuffering = stablePlayerState.isBuffering
        val isTranscoding = stablePlayerState.isTranscoding

        AnimatedVisibility(
            visible = isBuffering || isTranscoding,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 400,
                    delayMillis = 80,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = 80
                )
            ),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200
                )
            )
        ) {
            if (isTranscoding) {
                Surface(
                    shape = CircleShape,
                    color = chipColor,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = chipContentColor
                        )
                        LinearProgressIndicator(
                            progress = { stablePlayerState.transcodeProgressPercent / 100f },
                            modifier = Modifier.width(60.dp).height(4.dp),
                            color = chipContentColor,
                            trackColor = chipContentColor.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "${stablePlayerState.transcodeProgressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = chipContentColor
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = chipColor,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = chipContentColor
                        )
                    }
                }
            }
        }

        if (showQueueButton) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRadioPlayback) {
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 50.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 50.dp,
                                    bottomEnd = 6.dp
                                )
                            )
                            .background(chipColor)
                            .clickable { onClickLyrics() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_lyrics_24),
                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_lyrics),
                            tint = chipContentColor
                        )
                    }
                }
                if (!isRadioPlayback && canShowComment) {
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 6.dp,
                                    bottomEnd = 6.dp
                                )
                            )
                            .background(chipColor)
                            .clickable { onClickComment() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_circle_notifications_24),
                            contentDescription = "Comments",
                            tint = chipContentColor
                        )
                    }
                }
                // 播放列表按钮（广播电台播放时不显示：实时流没有播放列表）
                if (!isRadioPlayback) {
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 50.dp,
                                    bottomStart = 6.dp,
                                    bottomEnd = 50.dp
                                )
                            )
                            .background(chipColor)
                            .clickable { onClickQueue() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_queue_music_24),
                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_queue),
                            tint = chipContentColor
                        )
                    }
                }
            }
        } else {
            // Portrait Mode: Lyrics + Comment buttons side by side (Queue is in TopBar)
            if (isRadioPlayback) {
                // 广播电台：歌词/评论等在线功能不可用，显示 LIVE 直播徽标
                RadioLiveBadge(
                    chipColor = chipColor,
                    chipContentColor = chipContentColor
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasBluetooth by playerViewModel.hasBluetoothOutput.collectAsStateWithLifecycle()
                    val btLyricsEnabled by playerViewModel.bluetoothLyricsEnabled.collectAsStateWithLifecycle()
                    if (hasBluetooth) {
                        FilledIconButton(
                            modifier = Modifier.size(width = 48.dp, height = 48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (btLyricsEnabled) chipContentColor else chipColor,
                                contentColor = if (btLyricsEnabled) chipColor else chipContentColor
                            ),
                            onClick = { playerViewModel.toggleBluetoothLyrics() }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_bluetooth_24),
                                contentDescription = "Bluetooth Lyrics"
                            )
                        }
                    }
                    FilledIconButton(
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = chipColor,
                            contentColor = chipContentColor
                        ),
                        onClick = onClickLyrics,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_lyrics_24),
                            contentDescription = stringResource(R.string.presentation_batch_g_player_cd_lyrics)
                        )
                    }
                    if (canShowComment) {
                        FilledIconButton(
                            modifier = Modifier
                                .size(width = 48.dp, height = 48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = chipColor,
                                contentColor = chipContentColor
                            ),
                            onClick = onClickComment,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_circle_notifications_24),
                                contentDescription = "Comments"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAudioMetaLabel(mimeType: String?, bitrate: Int?, sampleRate: Int?): String? {
    val formatLabel = mimeTypeToFormat(mimeType)
        .takeIf { it != "-" }
        ?.uppercase(Locale.getDefault())

    val parts = buildList {
        sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%.1f kHz", it / 1000.0)) }
        bitrate?.takeIf { it > 0 }?.let { bitrateValue ->
            val kbpsLabel = "${bitrateValue / 1000} kbps"
            if (formatLabel != null) {
                add("$kbpsLabel \u2022 $formatLabel")
            } else {
                add(kbpsLabel)
            }
        } ?: formatLabel?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u2022 ")
}

/**
 * 根据 Song 提取可用于调用网易云评论接口的歌曲 ID。
 * 仅网易云来源的歌曲可显示评论（评论依赖网易云接口）：
 * 1) song.neteaseId (来源为网易云官方/漫游)
 * 2) "netease://xxx" 格式 contentUri 的后半部分
 * 3) "cloud://lx/{json}" 中 JSON 自带的 id 字段 —— 仅当 source 为 "wy"（或未记录，默认网易云）时
 * 其它来源（本地文件、tx/kg/mg/kw 等在线源）一律返回空串，不显示评论按钮。
 */
private fun resolveCommentSongId(song: Song): String {
    // 1) 优先使用 neteaseId
    val neteaseId = song.neteaseId
    if (neteaseId != null && neteaseId > 0L) {
        return neteaseId.toString()
    }

    // 1b) 如果 song id 以 "roaming_" 开头，提取后面的数字作为网易云ID
    if (song.id.startsWith("roaming_", ignoreCase = true)) {
        val numericPart = song.id.removePrefix("roaming_")
        val numericId = numericPart.toLongOrNull()
        if (numericId != null && numericId > 0L) {
            return numericId.toString()
        }
    }

    val contentUri = song.contentUriString
    if (contentUri.isNotBlank()) {
        // 2) netease://{id} 或 netease://{id}?url={encodedUrl} 格式
        if (contentUri.startsWith("netease://", ignoreCase = true)) {
            val part = contentUri
                .removePrefix("netease://")
                .substringBefore('?')
            val numeric = part.toLongOrNull()
            if (numeric != null && numeric > 0L) {
                return numeric.toString()
            }
        }

        // 3) cloud://lx/{urlEncoded JSON} —— 仅网易云音源（source 为空或 "wy"）才取 id，
        //    其它在线源（tx/kg/mg/kw 等）的 id 是它们自己的歌曲 ID，评论接口不适用。
        if (contentUri.startsWith("cloud://lx/", ignoreCase = true)) {
            val tail = contentUri.removePrefix("cloud://lx/")
            val jsonText = try {
                java.net.URLDecoder.decode(tail, "UTF-8")
            } catch (_: Throwable) {
                null
            }
            if (!jsonText.isNullOrBlank()) {
                try {
                    val obj = org.json.JSONObject(jsonText)
                    val source = obj.optString("source", "").trim()
                    if (source.isBlank() || source == "wy") {
                        val rawId = obj.optString("id", "").trim()
                        if (rawId.isNotBlank() && rawId.toLongOrNull() != null) {
                            return rawId
                        }
                    }
                } catch (_: Throwable) {
                    // 忽略解析异常
                }
            }
        }
    }

    return ""
}

@Composable
private fun PlayerProgressBarSection(
    songId: String,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    songDurationHintMs: Long,
    audioMimeType: String?,
    audioBitrate: Int?,
    audioSampleRate: Int?,
    showAudioFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    allowRealtimeUpdates: Boolean = true,
    isSheetDragGestureActive: Boolean = false,
    loadingTweaks: FullPlayerLoadingTweaks? = null,
    modifier: Modifier = Modifier
) {
    val progressSectionHorizontalInset = 0.dp
    val isVisible by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.01f }
    }
    val isExpanded by remember(currentSheetState, expansionFractionProvider) {
        derivedStateOf {
            currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.995f
        }
    }
    val shouldRunRealtimeUpdates = allowRealtimeUpdates && isVisible
    val shouldSampleProgress = isVisible

    val reportedDuration = totalDurationValue.coerceAtLeast(0L)
    val hintDuration = songDurationHintMs.coerceAtLeast(0L)
    val displayDurationValue = when {
        reportedDuration <= 0L && hintDuration <= 0L -> 0L
        reportedDuration <= 0L -> hintDuration
        hintDuration <= 0L -> reportedDuration
        kotlin.math.abs(reportedDuration - hintDuration) <= 1500L -> reportedDuration
        else -> minOf(reportedDuration, hintDuration)
    }
    val audioMetaLabel = remember(showAudioFileInfo, audioMimeType, audioBitrate, audioSampleRate) {
        if (showAudioFileInfo) {
            formatAudioMetaLabel(
                mimeType = audioMimeType,
                bitrate = audioBitrate,
                sampleRate = audioSampleRate
            )
        } else {
            null
        }
    }
    var displayAudioMetaLabel by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(songId, audioMetaLabel, showAudioFileInfo) {
        if (!showAudioFileInfo) {
            displayAudioMetaLabel = null
        } else if (!audioMetaLabel.isNullOrBlank()) {
            displayAudioMetaLabel = audioMetaLabel
        }
        // audioMetaLabel 短暂为空（metadata 刷新中 bitrate/sampleRate 暂缺）时，
        // 保留上一次的音质信息，避免标签显示一下又消失；换歌时 remember(songId) 已重置。
    }
    val durationForCalc = displayDurationValue.coerceAtLeast(1L)
    
    // Pass isVisible to rememberSmoothProgress
    val (smoothProgressState, _) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = displayDurationValue,
        sampleWhilePlayingMs = if (shouldRunRealtimeUpdates && isExpanded) 180L else 500L,
        sampleWhilePausedMs = 800L,
        isVisible = shouldSampleProgress
    )

    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    // Held seek target (fraction) — mirrors PlayerSeekBar so the slider stays where the user
    // dropped it until real playback catches up. Fraction-based so it survives duration drift.
    var targetSeekFraction by remember { mutableFloatStateOf(-1f) }
    var lastSeekFinishedTime by remember { mutableLongStateOf(0L) }

    // Reset seek state on song change to avoid stale position from previous song.
    LaunchedEffect(songId) {
        sliderDragValue = null
        targetSeekFraction = -1f
        lastSeekFinishedTime = 0L
    }

    // Release the held target once smooth progress catches up (within 4%) or after a 5 s
    // safety net — same thresholds as the LyricsSheet PlayerSeekBar. Re-keying on songId
    // restarts the snapshotFlow so the new song's progress drives the catch-up cleanly.
    LaunchedEffect(songId) {
        snapshotFlow { smoothProgressState.value }.collect { progress ->
            if (sliderDragValue != null) return@collect
            val target = targetSeekFraction
            if (target < 0f) return@collect
            val timeSinceSeek = System.currentTimeMillis() - lastSeekFinishedTime
            val diff = kotlin.math.abs(progress - target)
            if (timeSinceSeek > 5000L || diff < 0.04f) {
                targetSeekFraction = -1f
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shouldAnimateWavyProgress by remember(shouldRunRealtimeUpdates, isPlayingProvider) {
        derivedStateOf { shouldRunRealtimeUpdates && isPlayingProvider() }
    }

    // Always drive the thumb from smoothed progress to avoid visual jumps from 500ms raw ticks.
    val animatedProgressState = remember(smoothProgressState) {
        derivedStateOf {
            when {
                sliderDragValue != null -> sliderDragValue!!
                targetSeekFraction >= 0f -> targetSeekFraction
                else -> smoothProgressState.value
            }
        }
    }

    // No LaunchedEffect/snapshotFlow needed anymore. 
    // smoothProgressState is already 60fps animated.

    val effectivePositionState = remember(durationForCalc, animatedProgressState, isVisible, displayDurationValue) {
        derivedStateOf {
             val progress = animatedProgressState.value
             (progress * durationForCalc).roundToLong().coerceIn(0L, displayDurationValue)
        }
    }

    val shouldDelay = loadingTweaks?.let { it.delayAll || it.delayProgressBar } ?: false

    val placeholderColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.25f)
    val placeholderOnColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.2f)

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks?.showPlaceholders ?: false,
        applyPlaceholderDelayOnClose = loadingTweaks?.applyPlaceholdersOnClose ?: true,
        switchOnDragRelease = loadingTweaks?.switchOnDragRelease ?: false,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.08f,
        delayAppearThreshold = (loadingTweaks?.contentAppearThresholdPercent ?: 0) / 100f,
        delayCloseThreshold = 1f - ((loadingTweaks?.contentCloseThresholdPercent ?: 0) / 100f),
        placeholder = {
             if (loadingTweaks?.transparentPlaceholders == true) {
                 Box(Modifier.fillMaxWidth().heightIn(min = 70.dp))
             } else {
                 ProgressPlaceholder(
                     color = placeholderColor,
                     onColor = placeholderOnColor,
                     showAudioMetaChip = showAudioFileInfo && !displayAudioMetaLabel.isNullOrBlank()
                 )
             }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
        ) {
            // Isolated Slider Component
            // Wrapped in a Box with detectVerticalDragGestures to prevent the outer
            // playerSheetVerticalDragGesture from intercepting slider touches. If the
            // user's drag has a vertical component, the inner handler absorbs it (consuming
            // the events) so the sheet-collapse gesture never activates in this area.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onVerticalDrag = { _, _ -> })
                    }
            ) {
                EfficientSlider(
                    valueState = animatedProgressState,
                    onValueChange = { sliderDragValue = it },
                    onValueCommit = { finalValue ->
                        val targetMs = (finalValue * durationForCalc).roundToLong()
                        targetSeekFraction = finalValue
                        lastSeekFinishedTime = System.currentTimeMillis()
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
                            name = "player_seek_commit"
                        ) {
                            mapOf(
                                "targetMs" to targetMs.toString(),
                                "durationMs" to displayDurationValue.toString()
                            )
                        }
                        onSeek(targetMs)
                        sliderDragValue = null
                    },
                    thumbColor = thumbColor,
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor,
                    interactionSource = interactionSource,
                    isPlaying = shouldAnimateWavyProgress,
                    isVisible = isVisible,
                    trackEdgePadding = progressSectionHorizontalInset
                )
            }

            // Isolated Time Labels
            EfficientTimeLabels(
                positionState = effectivePositionState,
                duration = displayDurationValue,
                isVisible = isVisible,
                textColor = timeTextColor,
                audioMetaLabel = displayAudioMetaLabel,
                horizontalTrackInset = progressSectionHorizontalInset
            )
        }
    }
}

@Composable
private fun EfficientSlider(
    valueState: androidx.compose.runtime.State<Float>,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    interactionSource: MutableInteractionSource,
    isPlaying: Boolean,
    isVisible: Boolean,
    trackEdgePadding: Dp
) {
    val haptics = LocalHapticFeedback.current
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentHaptics = rememberUpdatedState(haptics)
    val lastHapticStep = remember { intArrayOf(-1) }
    val onValueChangeWithHaptics = remember {
        { newValue: Float ->
            val quantized = (newValue.coerceIn(0f, 1f) * 20f).toInt()
            if (quantized != lastHapticStep[0]) {
                lastHapticStep[0] = quantized
                currentHaptics.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            currentOnValueChange.value(newValue)
        }
    }

    WavySliderExpressive(
        value = { valueState.value },
        onValueChange = onValueChangeWithHaptics,
        onValueCommit = onValueCommit,
        interactionSource = interactionSource,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        thumbColor = thumbColor,
        isPlaying = isPlaying,
        isVisible = isVisible,
        trackEdgePadding = trackEdgePadding,
        semanticsLabel = "Playback position",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 0.dp)
    )
}

@Composable
private fun EfficientTimeLabels(
    positionState: androidx.compose.runtime.State<Long>,
    duration: Long,
    isVisible: Boolean,
    textColor: Color,
    audioMetaLabel: String?,
    horizontalTrackInset: Dp
) {
    val coarsePositionMs by remember(isVisible, positionState) {
        derivedStateOf {
            if (!isVisible) 0L
            else (positionState.value.coerceAtLeast(0L) / 1000L) * 1000L
        }
    }
    val posStr by remember(isVisible, coarsePositionMs) {
        derivedStateOf { if (isVisible) formatDuration(coarsePositionMs) else "--:--" }
    }
    val durStr = remember(isVisible, duration) {
        if (isVisible) formatDuration(duration.coerceAtLeast(0L)) else "--:--"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalTrackInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                posStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                durStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        if (!audioMetaLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 58.dp),
                shape = RoundedCornerShape(999.dp),
                color = textColor.copy(alpha = 0.14f),
                contentColor = textColor.copy(alpha = 0.96f)
            ) {
                Text(
                    text = audioMetaLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DelayedContent(
    shouldDelay: Boolean,
    showPlaceholders: Boolean,
    applyPlaceholderDelayOnClose: Boolean,
    switchOnDragRelease: Boolean,
    isSheetDragGestureActive: Boolean,
    sharedBoundsModifier: Modifier = Modifier,
    expansionFractionProvider: () -> Float,
    isExpandedOverride: Boolean = false,
    normalStartThreshold: Float,
    delayAppearThreshold: Float,
    delayCloseThreshold: Float,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val appearThreshold = delayAppearThreshold.coerceIn(0f, 1f)
    val closeThreshold = delayCloseThreshold.coerceIn(0f, 1f)
    var isDelayGateOpen by remember(shouldDelay) { mutableStateOf(!shouldDelay) }

    LaunchedEffect(
        shouldDelay,
        appearThreshold,
        closeThreshold,
        applyPlaceholderDelayOnClose,
        switchOnDragRelease,
        isSheetDragGestureActive,
        isExpandedOverride,
        expansionFractionProvider
    ) {
        if (!shouldDelay) {
            isDelayGateOpen = true
            return@LaunchedEffect
        }

        if (switchOnDragRelease) {
            if (isSheetDragGestureActive) {
                return@LaunchedEffect
            }

            if (isExpandedOverride) {
                isDelayGateOpen = true
            } else {
                snapshotFlow { expansionFractionProvider().coerceIn(0f, 1f) }
                    .first { fraction -> fraction <= 0.001f }
                isDelayGateOpen = false
            }
            return@LaunchedEffect
        }

        var previousExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
        var previousExpandedOverride = isExpandedOverride

        snapshotFlow {
            val rawExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
            val effectiveExpansionFraction =
                if (isExpandedOverride && rawExpansionFraction >= 0.985f) 1f else rawExpansionFraction
            DelayedContentFrame(
                rawExpansionFraction = rawExpansionFraction,
                effectiveExpansionFraction = effectiveExpansionFraction,
                isExpandedOverride = isExpandedOverride
            )
        }.collect { frame ->
            val isCollapsingByFraction =
                frame.rawExpansionFraction < previousExpansionFraction - 0.001f
            val isExpandingByFraction =
                frame.rawExpansionFraction > previousExpansionFraction + 0.001f
            val justStartedCollapsing =
                previousExpandedOverride && !frame.isExpandedOverride
            val justStartedExpanding =
                !previousExpandedOverride && frame.isExpandedOverride
            val isCollapsing = isCollapsingByFraction || justStartedCollapsing
            val isExpanding = isExpandingByFraction || justStartedExpanding
            val isFullyExpanded =
                frame.isExpandedOverride && frame.effectiveExpansionFraction >= 0.985f

            if (frame.effectiveExpansionFraction <= 0.001f && !frame.isExpandedOverride) {
                isDelayGateOpen = false
            } else if (isFullyExpanded) {
                isDelayGateOpen = true
            } else if (isDelayGateOpen) {
                if (applyPlaceholderDelayOnClose &&
                    isCollapsing &&
                    frame.effectiveExpansionFraction <= closeThreshold
                ) {
                    isDelayGateOpen = false
                }
            } else if (
                frame.effectiveExpansionFraction >= appearThreshold &&
                    (!applyPlaceholderDelayOnClose || isExpanding || frame.isExpandedOverride)
            ) {
                isDelayGateOpen = true
            }

            previousExpansionFraction = frame.rawExpansionFraction
            previousExpandedOverride = frame.isExpandedOverride
        }
    }

    val baseAlphaProvider = remember(normalStartThreshold, expansionFractionProvider) {
        {
            ((expansionFractionProvider().coerceIn(0f, 1f) - normalStartThreshold) /
                (1f - normalStartThreshold).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
        }
    }
    val contentBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 1f else 0f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedContentBlendAlpha"
    )
    val placeholderBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 0f else 1f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 360, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedPlaceholderBlendAlpha"
    )

    if (shouldDelay) {
        Box(modifier = sharedBoundsModifier) {
            val shouldComposeContent = isDelayGateOpen

            if (shouldComposeContent) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = contentBlendAlpha * baseAlphaProvider()
                    }
                ) {
                    content()
                }
            }
            if (showPlaceholders && placeholderBlendAlpha > 0.001f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = placeholderBlendAlpha }
                ) {
                    placeholder()
                }
            }
        }
    } else {
        Box(
            modifier = sharedBoundsModifier.graphicsLayer { alpha = baseAlphaProvider() }
        ) {
            content()
        }
    }
}

private data class DelayedContentFrame(
    val rawExpansionFraction: Float,
    val effectiveExpansionFraction: Float,
    val isExpandedOverride: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickArtist: () -> Unit,
    currentQueueSourceName: String,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true },
    songId: String? = null,
    songNeteaseId: Long? = null,
    songContentUriString: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val resolvedArtistId by remember(artists, artistId) {
        derivedStateOf { artists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: artistId }
    }
    val titleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = GoogleSansRounded,
        color = textColor
    )

    val artistStyle = MaterialTheme.typography.titleMedium.copy(
        letterSpacing = 0.sp,
        color = artistTextColor
    )

    Column(
        horizontalAlignment = Alignment.Start,
            modifier = modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction
                translationY = (1f - fraction) * 24f
            }
    ) {
        val isRoaming by playerViewModel.isRoamingMode.collectAsStateWithLifecycle(initialValue = false)
        // contentUri 为 netease://{id}?url={encodedUrl} 格式，表示JS引擎漫游播放的收藏歌曲
        val isNeteaseWithEmbeddedUrl = songContentUriString.startsWith("netease://") && songContentUriString.contains("?url=")
        // isVipRoamingSong: 原始漫游歌曲（roaming_开头）或 收藏的漫游歌曲（netease://?url= 格式）
        val isVipRoamingSong = (songId != null && songNeteaseId != null && songId.startsWith("roaming_")) ||
            (songNeteaseId != null && isNeteaseWithEmbeddedUrl)
        // isNeteaseSong: 纯网易云歌曲（有 neteaseId 但不是通过JS引擎播放的漫游歌曲）
        val isNeteaseSong = songNeteaseId != null && !isVipRoamingSong && !isNeteaseWithEmbeddedUrl
        val hasSourceLabel = currentQueueSourceName.isNotBlank() && currentQueueSourceName != "本地音乐"
        val hasAnySourceLabel = isRoaming || isVipRoamingSong || isNeteaseSong || hasSourceLabel
        if (hasAnySourceLabel) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 单个来源标签的通用样式：限定最大宽度 + 单行省略，避免过长文本挤压其它内容
                val labelTextModifier: Modifier = Modifier
                    .padding(start = 4.dp)
                    .widthIn(max = 120.dp)
                val labelTextStyle = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = artistTextColor
                )
                val iconModifier = Modifier.size(14.dp)
                val labelInnerPadding = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                val surfaceShape = RoundedCornerShape(12.dp)
                val surfaceColor = textColor.copy(alpha = 0.1f)

                if (isRoaming) {
                    Surface(
                        shape = surfaceShape,
                        color = surfaceColor,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = labelInnerPadding
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_wifi_24),
                                contentDescription = null,
                                tint = artistTextColor,
                                modifier = iconModifier
                            )
                            Text(
                                text = "漫游模式",
                                style = labelTextStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = labelTextModifier
                            )
                        }
                    }
                }
            }
        }
        // We pass 1f to AutoScrollingTextOnDemand because the alpha/translation is now handled by the parent Column graphicsLayer
        // and we want it "fully rendered" but hidden/moved by the layer.
        // Actually, AutoScrollingTextOnDemand uses expansionFraction to start scrolling only when fully expanded?
        // Let's check AutoScrollingTextOnDemand. Assuming it uses it for scrolling trigger.
        // If we want to avoid recomposition, we might need to pass the provider or just 1f if scrolling logic handles itself.
        // For now, let's pass the current value from provider for logic correctness, but ideally this component should be optimized too.
        AutoScrollingTextOnDemand(
            text = title,
            style = titleStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier.fillMaxWidth(),
            canScroll = isPlayingProvider()
        )
        Spacer(modifier = Modifier.height(2.dp))



        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },

                onLongClick = {
                    if (isNavigatingToArtist) return@combinedClickable
                    coroutineScope.launch {
                        isNavigatingToArtist = true
                        try {
                            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId, songNeteaseId)
                        } finally {
                            isNavigatingToArtist = false
                        }
                    }
                }
            ),
            canScroll = isPlayingProvider()
        )
    }
}

@Composable
private fun PlaceholderBox(
    modifier: Modifier,
    cornerRadius: Dp = 12.dp,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
private fun AlbumPlaceholder(
    height: Dp,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(18.dp),
        color = color,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                modifier = Modifier.size(86.dp),
                painter = painterResource(R.drawable.pixelplay_base_monochrome),
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
private fun MetadataPlaceholder(
    expansionFractionProvider: () -> Float,
    color: Color,
    onColor: Color,
    showQueueButtons: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .graphicsLayer {
                val expansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
                alpha = expansionFraction.coerceIn(0f, 1f)
                translationY = (1f - expansionFraction.coerceIn(0f, 1f)) * 24f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp) //2.dp
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(27.dp), //30.dp
                cornerRadius = 8.dp,
                color = color
            )
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(17.dp), //20.dp
                cornerRadius = 8.dp,
                color = onColor
            )
        }

        if (showQueueButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 6.dp,
                        bottomStart = 50.dp,
                        bottomEnd = 6.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 50.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 50.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
            }
        } else {
            PlaceholderBox(
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
                cornerRadius = 24.dp,
                color = onColor
            )
        }
    }
}

@Composable
private fun ProgressPlaceholder(
    color: Color,
    onColor: Color,
    showAudioMetaChip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                cornerRadius = 3.dp,
                color = onColor.copy(alpha = 0.15f)
            )
            // Keep active segment in the layout tree but invisible to avoid visual noise.
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(6.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 3.dp,
                color = color
            )
            // Keep thumb slot aligned but fully transparent.
            PlaceholderBox(
                modifier = Modifier
                    .padding(start = 92.dp)
                    .size(14.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 7.dp,
                color = onColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
            }

            if (showAudioMetaChip) {
                PlaceholderBox(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 96.dp, max = 180.dp)
                        .height(18.dp),
                    cornerRadius = 999.dp,
                    color = onColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun ControlsPlaceholder(color: Color, onColor: Color) {
    val rowCorners = 60.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBR = 60
                    ),
                    color = color,
                    tonalElevation = 0.dp
                ) {}
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp, max = 86.dp)
                .padding(horizontal = 26.dp)
                .padding(bottom = 6.dp)
                .background(
                    color = onColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(rowCorners)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    PlaceholderBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = rowCorners,
                        color = onColor.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

private data class TransportButtonColors(
    val container: Color,
    val content: Color
)

private fun expressivePlayPauseButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.tertiaryFixedDim,
        content = colorScheme.onTertiaryFixed
    )
}

private fun expressiveSkipButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.secondaryFixedDim,
        content = colorScheme.onSecondaryFixed
    )
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    isShuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isOnlineSong: Boolean,
    onDownloadClick: () -> Unit,
    downloadProgress: Float?,
    isDownloadComplete: Boolean,
    isDownloadFailed: Boolean,
    isRadioPlayback: Boolean = false,
    showRadioPlayPause: Boolean = false,
    radioIsPlayingProvider: () -> Boolean = { true },
    onRadioPlayPause: () -> Unit = {},
    radioPlayPauseColor: Color = Color.Unspecified,
    radioPlayPauseContentColor: Color = Color.Unspecified,
    surfaceContainerLowest: Color,
    onSurface: Color,
    primaryFixed: Color,
    onPrimaryFixed: Color,
    secondaryFixed: Color,
    onSecondaryFixed: Color,
    tertiaryFixed: Color,
    onTertiaryFixed: Color,
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp

    val inactiveBg = onSurface.copy(alpha = 0.07f)
    val inactiveContentColor = onSurface


    Box(
        modifier = modifier.background(
            color = surfaceContainerLowest.copy(alpha = 0.7f),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            if (!isRadioPlayback) {
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = isShuffleEnabled,
                    enabled = !isShuffleTransitionInProgress,
                    activeColor = primaryFixed,
                    activeCornerRadius = rowCorners,
                    activeContentColor = onPrimaryFixed,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onShuffleToggle,
                    iconId = R.drawable.rounded_shuffle_24,
                    contentDesc = "Aleatorio"
                )
                val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                    Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                    else -> R.drawable.rounded_repeat_24
                }
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = repeatActive,
                    activeColor = secondaryFixed,
                    activeCornerRadius = rowCorners,
                    activeContentColor = onSecondaryFixed,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onRepeatToggle,
                    iconId = repeatIcon,
                    contentDesc = "Repetir"
                )
            }
            if (isOnlineSong) {
                Box(modifier = commonModifier) {
                    if (downloadProgress != null && !isDownloadComplete && !isDownloadFailed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(AbsoluteSmoothCornerShape(cornerRadiusBL = rowCorners, smoothnessAsPercentTR = 60, cornerRadiusBR = rowCorners, smoothnessAsPercentBL = 60, cornerRadiusTL = rowCorners, smoothnessAsPercentBR = 60, cornerRadiusTR = rowCorners, smoothnessAsPercentTL = 60))
                                .background(primaryFixed)
                                .fillMaxWidth(downloadProgress / 100f)
                                .align(Alignment.CenterStart)
                        )
                    }
                    ToggleSegmentButton(
                        modifier = Modifier.fillMaxSize(),
                        active = downloadProgress != null || isDownloadComplete,
                        activeColor = if (downloadProgress != null && !isDownloadComplete && !isDownloadFailed) Color.Transparent else primaryFixed,
                        activeCornerRadius = rowCorners,
                        activeContentColor = onPrimaryFixed,
                        inactiveColor = inactiveBg,
                        inactiveContentColor = inactiveContentColor,
                        onClick = onDownloadClick,
                        iconId = when {
                            isDownloadComplete -> R.drawable.rounded_check_circle_24
                            isDownloadFailed -> R.drawable.rounded_close_24
                            downloadProgress != null -> R.drawable.rounded_download_24
                            else -> R.drawable.rounded_download_24
                        },
                        contentDesc = "Download"
                    )
                }
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = tertiaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = onTertiaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = "Favorito"
            )
            // 广播电台播放/暂停按钮：放在收藏按钮右侧（上一首/下一首无意义，播放器实时流仅此控制）
            if (showRadioPlayPause) {
                val radioIsPlaying = radioIsPlayingProvider()
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = radioIsPlaying,
                    activeColor = radioPlayPauseColor,
                    activeCornerRadius = rowCorners,
                    activeContentColor = radioPlayPauseContentColor,
                    inactiveColor = radioPlayPauseColor,
                    inactiveContentColor = radioPlayPauseContentColor,
                    onClick = onRadioPlayPause,
                    iconId = if (radioIsPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24,
                    contentDesc = stringResource(R.string.mashup_cd_play_pause)
                )
            }
        }
    }
}

// ─── 广播电台实时流专用组件 ─────────────────────────────────────────────

/**
 * 广播电台播放时的热门电台推荐区。
 * 展示热门电台卡片，点击即可切换电台；仅横屏（右侧布局）显示，
 * 竖屏/数据未加载时不显示任何指示（红点/播放中已移除）。
 */
@Composable
private fun RadioRecommendationSection(
    currentSongId: String,
    onBaseColor: Color,
    baseColor: Color,
    onStationClick: (RadioStation) -> Unit
) {
    val radioViewModel: RadioViewModel = hiltViewModel()
    val uiState by radioViewModel.uiState.collectAsStateWithLifecycle()
    // 进入推荐区时确保热门电台数据已加载（播放器可直接打开，无需先经过电台主页）
    LaunchedEffect(Unit) {
        if (uiState.topStations.isEmpty()) radioViewModel.loadTopStations()
    }
    // 始终使用独立的热门电台列表（与当前列表模式无关），过滤掉当前正在播放的电台
    val topStations = uiState.topStations
    val recommendations = remember(topStations, currentSongId) {
        topStations
            .filter { currentSongId != "radio://${it.stationUuid}" }
            .take(20)
    }
    // 仅横屏（右侧布局）显示热门电台列表；竖屏（手机或平板）不显示任何指示。
    // 按真实窗口宽高比判定：平板小窗变窄时自动回到竖屏（手机）形态；
    // 1:1（width == height）按横屏处理，与全屏播放器布局保持一致
    val isLandscapeOrientation =
        with(LocalWindowInfo.current.containerSize) { width >= height }

    if (!isLandscapeOrientation || recommendations.isEmpty()) return

    // 横屏：热门电台列表放入深色圆角容器（不透明实色，由播放器取色主色加深得到，遵循播放器取色）
    val panelColor = lerpColor(baseColor, Color.Black, 0.45f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        // 圆角与控制区（BottomToggleRow 60dp）同步，视觉一致
        shape = RoundedCornerShape(35.dp),
        color = panelColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = onBaseColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.radio_recommendations),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    color = onBaseColor.copy(alpha = 0.75f)
                )
            }
            // 平板/横屏：媒体库风格列表（圆形封面 + 副标题 + 播放指示），取色跟随播放器
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = recommendations.take(10),
                    key = { it.stationUuid }
                ) { station ->
                    RadioSuggestionCard(
                        station = station,
                        isPlaying = currentSongId == "radio://${station.stationUuid}",
                        onBaseColor = onBaseColor,
                        onClick = { onStationClick(station) }
                    )
                }
            }
        }
    }
}

/**
 * 广播播放器中的热门电台推荐卡片。
 * 与电台主页列表一致的显示效果：卡片式背景 + 方形圆角封面（播放时变圆）+ 播放高亮；
 * 颜色跟随播放器取色（onBaseColor），而非应用主题色。
 */
@Composable
private fun RadioSuggestionCard(
    station: RadioStation,
    isPlaying: Boolean,
    onBaseColor: Color,
    onClick: () -> Unit
) {
    val transition = updateTransition(isPlaying, label = "RadioSuggestionCardTransition")
    val highlightProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400) },
        label = "highlightProgress"
    ) { if (it) 1f else 0f }
    val animatedCornerRadius = lerp(22.dp, 50.dp, highlightProgress)
    val animatedAlbumCornerRadius = lerp(10.dp, 50.dp, highlightProgress)
    val surfaceShape = RoundedCornerShape(animatedCornerRadius)
    val albumShape = RoundedCornerShape(animatedAlbumCornerRadius)

    val baseContainerColor = onBaseColor.copy(alpha = 0.08f)
    val containerColor = lerpColor(baseContainerColor, onBaseColor.copy(alpha = 0.16f), highlightProgress)
    val contentColor = lerpColor(onBaseColor.copy(alpha = 0.9f), onBaseColor, highlightProgress)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(surfaceShape)
            .clickable(onClick = onClick),
        shape = surfaceShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面：电台 logo，缺失时显示收音机占位图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(onBaseColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (station.favicon.isNotBlank()) {
                    SmartImage(
                        model = station.favicon,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        shape = albumShape,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = onBaseColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = station.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBaseColor.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isPlaying) {
                Spacer(Modifier.width(8.dp))
                PlayingEqIcon(color = onBaseColor, isPlaying = true)
            }
        }
    }
}

/**
 * 广播电台 LIVE 徽标（竖屏歌曲信息区右侧，替代歌词/评论按钮）。
 */
@Composable
private fun RadioLiveBadge(
    chipColor: Color,
    chipContentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = chipColor,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = chipContentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.radio_live_badge),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = chipContentColor
            )
        }
    }
}
