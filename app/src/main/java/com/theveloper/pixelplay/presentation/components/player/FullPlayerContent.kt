package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.rememberWindowIsLandscape
import com.theveloper.pixelplay.presentation.components.BilibiliCommentSheet
import com.theveloper.pixelplay.presentation.components.CommentSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.collectAsState
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
import com.theveloper.pixelplay.data.preferences.PlayerBackgroundMode
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.AppleMusicRotatingBackground
import com.theveloper.pixelplay.presentation.components.CustomPlayerBackground
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.lyricsSheetColors
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AutoEqViewModel
import com.theveloper.pixelplay.data.autoeq.AutoEQProfile
import com.theveloper.pixelplay.data.autoeq.UserAudioDevice
import com.theveloper.pixelplay.presentation.components.autoeq.AutoEQSuggestionDialog
import com.theveloper.pixelplay.presentation.components.autoeq.DeviceConfigurationBottomSheet
import com.theveloper.pixelplay.data.preferences.TabletPlayerLayout
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.data.preferences.dataStore
import androidx.compose.foundation.verticalScroll
import com.theveloper.pixelplay.presentation.components.SyncedLyricsList
import com.theveloper.pixelplay.presentation.components.PlainLyricsLine
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

/**
 * 解析歌曲的 B 站 bvid：优先使用 bilibiliBvid 字段；
 * 数据库/队列恢复的歌曲没有该字段时，从 contentUriString（bilibili://{bvid}/{cid}/{aid}）解析。
 * ⚡ 仅接受 "BV" 开头的真实 bvid：B 站音频等无 bvid 歌曲会用 aid 兜底存成
 * bilibili://{aid}/{cid}/{aid}，若当作 bvid 去查评论会得到"无法获取视频信息"。
 */
private fun Song.resolveBilibiliBvid(): String? {
    bilibiliBvid?.takeIf { it.startsWith("BV") }?.let { return it }
    val uriString = contentUriString ?: return null
    if (!uriString.startsWith("bilibili://")) return null
    return runCatching {
        android.net.Uri.parse(uriString).host?.takeIf { it.startsWith("BV") }
    }.getOrNull()
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
    onSpeedToggle: () -> Unit = {},
    onSpeedSet: (Float) -> Unit = {},
) {
    val isExpanded by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.35f }
    }

    // ⚡ 防闪烁：只在"从未展开过"时允许折叠态销毁内容（首次进入省组合开销）。
    // 一旦展开过，hasEverExpanded 置 true，此后收起/再展开都保持内容挂载，
    // 封面与歌曲信息不会因整棵销毁重建而重新加载闪烁。
    var hasEverExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) hasEverExpanded = true
    }
    if (!hasEverExpanded && !isExpanded && currentSheetState == PlayerSheetState.COLLAPSED) {
        return
    }

    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
        // 切歌时清理 AI 歌词解释（实现"单次开启"语义：仅当前歌曲有效）
        playerViewModel.onSongChangedForLyricsExplanation(currentSong?.id)
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    // 广播电台实时流：隐藏进度条、上一首/下一首、随机/循环、歌词、评论、下载等不可用功能
    val isRadioPlayback = song.id.startsWith("radio://")
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    // ⚡ B 站源评论：入口与网易云一致（CD 页评论按钮），点击后全屏打开该视频的评论
    var showBilibiliCommentSheet by remember { mutableStateOf(false) }
    var bilibiliCommentBvid by remember { mutableStateOf("") }

    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()
    val isExplainingLyrics by playerViewModel.isExplainingLyrics.collectAsStateWithLifecycle()
    val lyricsExplanation by playerViewModel.lyricsExplanation.collectAsStateWithLifecycle()
    val isLyricsExplanationSessionEnabled by playerViewModel.isLyricsExplanationSessionEnabled.collectAsStateWithLifecycle()
    val isLyricsExplanationGloballyEnabled by playerViewModel.isLyricsExplanationGloballyEnabled.collectAsStateWithLifecycle()

    // Single subscription — replaces 11 independent collectAsStateWithLifecycle calls.
    // distinctUntilChanged in the ViewModel ensures this only emits when something
    // actually changed, batching multiple rapid updates into one recomposition.
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val currentSongArtists = fullPlayerSlice.currentSongArtists
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val lyricsFontFamily by playerViewModel.lyricsFontFamily.collectAsStateWithLifecycle()
    // ⚡ 自定义播放器背景：开关 + 所选图片 URI + 显示模式 + 模糊半径（0=关闭）（应用到播放器界面与歌词界面）
    val customPlayerBackgroundEnabled by playerViewModel.customPlayerBackgroundEnabled.collectAsStateWithLifecycle()
    val customPlayerBackgroundUri by playerViewModel.customPlayerBackgroundUri.collectAsStateWithLifecycle()
    val customPlayerBackgroundMode by playerViewModel.customPlayerBackgroundMode.collectAsStateWithLifecycle()
    val customPlayerBackgroundBlurRadius by playerViewModel.customPlayerBackgroundBlurRadius.collectAsStateWithLifecycle()
    // ⚡ 播放器控键透明度（百分比）与歌词渐变遮罩开关
    val customPlayerControlsOpacity by playerViewModel.customPlayerControlsOpacity.collectAsStateWithLifecycle()
    val lyricsGradientOverlayEnabled by playerViewModel.lyricsGradientOverlayEnabled.collectAsStateWithLifecycle()
    val lyricsSolidOverlayAlpha by playerViewModel.lyricsSolidOverlayAlpha.collectAsStateWithLifecycle()
    val lyricsVibrantBackgroundEnabled by playerViewModel.lyricsVibrantBackgroundEnabled.collectAsStateWithLifecycle()
    val playerVibrantBackgroundEnabled by playerViewModel.playerVibrantBackgroundEnabled.collectAsStateWithLifecycle()
    val albumArtQuality = fullPlayerSlice.albumArtQuality
    // Tablet player layout preference
    val tabletPlayerLayout by playerViewModel.tabletPlayerLayout.collectAsStateWithLifecycle()
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

    // AutoEQ device suggestion (port from Rhythm)
    val autoEqViewModel: AutoEqViewModel = hiltViewModel()
    val equalizerEnabled by autoEqViewModel.equalizerEnabled.collectAsStateWithLifecycle()
    var showAutoEQSuggestion by remember { mutableStateOf(false) }
    var showDeviceConfigFromSuggestion by remember { mutableStateOf(false) }
    var detectedDevice by remember { mutableStateOf<UserAudioDevice?>(null) }

    // Device detection and AutoEQ suggestion
    LaunchedEffect(bluetoothName) {
        val name = bluetoothName
        if (!name.isNullOrBlank()) {
            val matchedDevice = autoEqViewModel.findMatchingUserDevice(name)
            val activeDevice = autoEqViewModel.getActiveAudioDevice()

            if (matchedDevice != null && autoEqViewModel.shouldShowAutoEQSuggestion(matchedDevice.id)) {
                // Show popup only if:
                // 1. Device has NO preset configured (needs configuration), OR
                // 2. Device has preset but is NOT currently active (needs to be applied)
                val isAlreadyActive = activeDevice?.id == matchedDevice.id &&
                    matchedDevice.autoEQProfileName != null
                if (!isAlreadyActive) {
                    detectedDevice = matchedDevice
                    showAutoEQSuggestion = true
                }
            }
        }
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

    // ⚡ 直接读取 MaterialTheme 颜色，不再使用 animateColorAsState。
    //   SheetThemeState 已在 lerpColorScheme 中做切歌过渡动画（300ms lerp），
    //   此处再套一层 animateColorAsState(400ms) 会导致双重插值——
    //   每个 ColorScheme 变化触发 9 个动画同时启动，每帧 9×400ms 插值 = 严重卡顿。
    //   移除后切歌颜色过渡仍平滑（由 SheetThemeState 驱动），且大幅减少 recomposition。
    val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
    val playerAccentColor = LocalMaterialTheme.current.primary
    val playerOnAccentColor = LocalMaterialTheme.current.onPrimary

    val transportPlayPauseColors = TransportButtonColors(
        container = LocalMaterialTheme.current.tertiaryFixedDim,
        content = LocalMaterialTheme.current.onTertiaryFixed
    )
    val transportSkipColors = TransportButtonColors(
        container = LocalMaterialTheme.current.secondaryFixedDim,
        content = LocalMaterialTheme.current.onSecondaryFixed
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

    // 与主页一致的可靠横屏判断：监听 View 全局布局（旋转/分屏时 View 尺寸必然变化，OnGlobalLayout 必然回调）

    // Lógica para el botón de Lyrics en el reproductor expandido
    val isLandscape = rememberWindowIsLandscape()
    val latestShowLyricsSheet by rememberUpdatedState(showLyricsSheet)
    val onLyricsClick = remember {{ showLyricsSheet = true }}

    // 评论按钮：网易云歌曲打开 CommentSheet，B 站源歌曲打开 BilibiliCommentSheet（入口完全一致）
    val commentTargetSongRef = rememberUpdatedState(song)
    val onCommentClick = remember {
        {
            val targetSong = commentTargetSongRef.value
            if (resolveCommentSongId(targetSong).isNotBlank()) {
                showCommentSheet = true
            } else if (!targetSong.resolveBilibiliBvid().isNullOrBlank()) {
                bilibiliCommentBvid = targetSong.resolveBilibiliBvid().orEmpty()
                showBilibiliCommentSheet = true
            }
        }
    }

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
    // 多位歌手选择列表：优先用歌曲自带的 artists（含真实歌手 ID，漫游/在线/收藏歌曲都能用），
    // 其次用数据库 song_artist_cross_ref 关联（本地媒体），
    // 最后从 displayArtist 按分隔符拆分（在线歌曲 artists 未填充、本地未拆分入库时，
    // 保证 2 位以上艺人也能弹出选择器，而不是直接跳第一位）。
    val pickerArtists = remember(song.id, song.artists, currentSongArtists, song.displayArtist) {
        val fromSong = song.artists.filter { it.name.isNotBlank() && it.id != 0L }
        when {
            fromSong.size > 1 -> fromSong.map { Artist(id = it.id, name = it.name, songCount = 0) }
            currentSongArtists.size > 1 -> currentSongArtists
            else -> {
                val names = song.displayArtist
                    .split("、", "，", ",", "&", "/")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length <= 32 }
                if (names.size > 1) {
                    names.map { Artist(id = 0L, name = it, songCount = 0) }
                } else {
                    currentSongArtists
                }
            }
        }
    }
    val latestPickerArtists by rememberUpdatedState(pickerArtists)
    val onSongMetadataArtistClick = remember {{
        val resolvedArtistId = latestPickerArtists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: latestSong.artistId
        if (latestPickerArtists.size > 1) {
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
                isLandscape = isLandscape,
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
                playbackMetadataBitDepth = playbackAudioMetadata.bitDepth,
                playbackMetadataDisplayLabel = playbackAudioMetadata.displayLabel,
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
        val playbackSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
        val showPlaybackSpeedButton by playerViewModel.showPlaybackSpeedButton.collectAsStateWithLifecycle()
        val pitchFollowSpeed by playerViewModel.pitchFollowSpeed.collectAsStateWithLifecycle()
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
            playbackSpeed = playbackSpeed,
            onSpeedToggle = onSpeedToggle,
            onSpeedSet = onSpeedSet,
            showSpeedButton = showPlaybackSpeedButton,
            pitchFollowSpeed = pitchFollowSpeed,
            onPitchFollowSpeedToggle = playerViewModel::setPitchFollowSpeed,
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
        Box(modifier = Modifier.fillMaxSize()) {
            // ⚡ 自定义播放器背景：置于所有播放器内容之下（开关关闭或未选图时不绘制）
            //    背景不参与「播放器不透明度」——该设置只作用于背景之外的所有元素
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                CustomPlayerBackground(
                    modifier = Modifier.fillMaxSize(),
                    enabled = customPlayerBackgroundEnabled,
                    uri = customPlayerBackgroundUri,
                    mode = customPlayerBackgroundMode,
                    blurRadius = customPlayerBackgroundBlurRadius,
                    scrimAlpha = 0.25f
                )
                // 歌词背景那套「封面旋转 + 重模糊」氛围背景，复刻到播放器背景：
                // 未使用自定义背景图且开启「播放器绚丽背景」开关时启用（与歌词背景分开控制，默认启用）
                if (playerVibrantBackgroundEnabled &&
                    !(customPlayerBackgroundEnabled && !customPlayerBackgroundUri.isNullOrBlank())
                ) {
                    song?.albumArtUriString?.let { albumArtUri ->
                        AppleMusicRotatingBackground(
                            albumArtUri = albumArtUri,
                            modifier = Modifier.fillMaxSize()
                        )
                        // ⚡ 绚丽背景可读性遮罩：1:1 复刻歌词界面的渐变遮罩
                        //   （LyricsSheet 的「歌词渐变遮罩」：containerColor 上 0.4 → 下 0.95）
                        //   顶部较通透保留氛围，底部接近实色保证标题/歌词/控件文字清晰
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            surfaceContainerLowest.copy(alpha = 0.4f),
                                            surfaceContainerLowest.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
            // ⚡ 播放器不透明度：背景之外的所有元素（封面、元信息、进度、控制）统一淡化
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha * (customPlayerControlsOpacity / 100f) }
            ) {
                // Check if we should use parallel layout on tablet
                // ⚡ 竖屏时强制走手机布局，只有横屏平板才启用平行布局
                val configuration = LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 840
                val useParallelLayout = isTablet && isLandscape && tabletPlayerLayout == TabletPlayerLayout.PARALLEL

                // 平行布局歌词设置卡片状态（在调用方管理，以便 metadata 歌词按钮可切换）
                var showParallelLyricsSettings by remember { mutableStateOf(false) }

                if (useParallelLayout) {
                    // 平行布局专用 metadata section：隐藏歌词按钮（歌词面板右下角有设置入口）
                    val parallelSongMetadataSection: @Composable () -> Unit = {
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
                            showLyricsButton = false,
                            onLyricsClick = { },
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

                    // Parallel layout: player on left, lyrics on right
                    FullPlayerParallelLayout(
                        paddingValues = paddingValues,
                        albumCoverSection = albumCoverSection,
                        songMetadataSection = parallelSongMetadataSection,
                        showLyricsSettings = showParallelLyricsSettings,
                        onToggleLyricsSettings = { showParallelLyricsSettings = !showParallelLyricsSettings },
                        playerProgressSection = playerProgressSection,
                        controlsSection = controlsSection,
                        isRadioPlayback = isRadioPlayback,
                        showLyricsSheet = showLyricsSheet,
                        playerViewModel = playerViewModel,
                        lyricsSearchUiState = lyricsSearchUiState,
                        lyricsSyncOffset = lyricsSyncOffset,
                        lyricsFontFamily = lyricsFontFamily,
                        lyricsFontSize = fullPlayerSlice.lyricsFontSize,
                        customPlayerBackgroundEnabled = customPlayerBackgroundEnabled,
                        customPlayerBackgroundUri = customPlayerBackgroundUri,
                        customPlayerBackgroundMode = customPlayerBackgroundMode,
                        customPlayerBackgroundBlurRadius = customPlayerBackgroundBlurRadius,
                        customPlayerControlsOpacity = customPlayerControlsOpacity,
                        lyricsGradientOverlayEnabled = lyricsGradientOverlayEnabled,
                        lyricsSolidOverlayAlpha = lyricsSolidOverlayAlpha,
                        lyricsVibrantBackgroundEnabled = lyricsVibrantBackgroundEnabled,
                        immersiveLyricsEnabled = immersiveLyricsEnabled,
                        immersiveLyricsTimeout = immersiveLyricsTimeout,
                        isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        isFavoriteProvider = isFavoriteProvider,
                        onShuffleToggle = onShuffleToggle,
                        onRepeatToggle = onRepeatToggle,
                        onFavoriteToggle = onFavoriteToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        showLyricsTrackInfo = fullPlayerSlice.showLyricsTrackInfo,
                        isExplainingLyrics = isExplainingLyrics,
                        lyricsExplanation = lyricsExplanation,
                        lyricsExplanationEnabled = isLyricsExplanationGloballyEnabled || isLyricsExplanationSessionEnabled,
                        onDismissExplanation = { playerViewModel.clearLyricsExplanation() }
                    )
                } else if (isLandscape) {
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
    }
    // Only show lyrics sheet overlay when NOT in parallel layout
    // ⚡ 竖屏时平行布局不生效，歌词面板正常显示
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840
    val useParallelLayout = isTablet && isLandscape && tabletPlayerLayout == TabletPlayerLayout.PARALLEL
    if (!useParallelLayout) {
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
            onExplainLyricsViaAi = { playerViewModel.explainCurrentLyrics() },
            isExplainingLyrics = isExplainingLyrics,
            lyricsExplanation = lyricsExplanation,
            lyricsExplanationEnabled = isLyricsExplanationGloballyEnabled || isLyricsExplanationSessionEnabled,
            onDismissExplanation = { playerViewModel.clearLyricsExplanation() },
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
            showLyricsTrackInfo = fullPlayerSlice.showLyricsTrackInfo,
            customPlayerBackgroundEnabled = customPlayerBackgroundEnabled,
            customPlayerBackgroundUri = customPlayerBackgroundUri,
            customPlayerBackgroundMode = customPlayerBackgroundMode,
            customPlayerBackgroundBlurRadius = customPlayerBackgroundBlurRadius,
            customPlayerControlsOpacity = customPlayerControlsOpacity,
            lyricsGradientOverlayEnabled = lyricsGradientOverlayEnabled,
            lyricsSolidOverlayAlpha = lyricsSolidOverlayAlpha,
            lyricsVibrantBackgroundEnabled = lyricsVibrantBackgroundEnabled
        )
    }
    } // end if (!useParallelLayout)

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

    AnimatedVisibility(
        visible = showBilibiliCommentSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        // ⚡ B 站源评论页：入口与网易云完全一致（CD 页评论按钮），按 bvid 拉取该视频评论
        BilibiliCommentSheet(
            bvid = bilibiliCommentBvid,
            videoTitle = song.title,
            upName = song.displayArtist,
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showBilibiliCommentSheet = false }
        )
    }

    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && pickerArtists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            song = song,
            artists = pickerArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                // 网易云多歌手：点击哪个歌手就进哪个歌手的主页（artist.id 即该歌手的网易云 ID）。
                // 统一媒体库歌曲歌手 ID 是名字 hash（负数），透传歌手下标，由 ViewModel 按
                // 歌曲详情真实 artistIds 解析对应歌手，避免"第二歌手永远跳到第一歌手"。
                if (artist.id == 0L && song.neteaseId == null) {
                    // 本地歌曲：displayArtist 拆分出来的名字没有真实 ID，按名字查数据库
                    fileImportScope.launch {
                        val idByName = try {
                            withContext(Dispatchers.IO) { playerViewModel.resolveLocalArtistIdByName(artist.name) }
                        } catch (t: Throwable) {
                            Timber.w(t, "resolveLocalArtistIdByName failed: ${artist.name}")
                            null
                        }
                        if (idByName != null && idByName != 0L) {
                            playerViewModel.triggerArtistNavigationFromPlayer(idByName, null)
                        } else {
                            Toast.makeText(context, "未找到艺人「${artist.name}」", Toast.LENGTH_SHORT).show()
                        }
                        showArtistPicker = false
                    }
                } else {
                    playerViewModel.triggerArtistNavigationFromPlayer(
                        artistId = artist.id,
                        songNeteaseId = song.neteaseId,
                        neteaseArtistId = artist.id.takeIf { song.neteaseId != null && it > 0L },
                        neteaseArtistIndex = pickerArtists.indexOfFirst { it.name == artist.name }
                            .takeIf { it >= 0 }
                    )
                    showArtistPicker = false
                }
            }
        )
    }

    // AutoEQ Suggestion Dialog
    if (showAutoEQSuggestion && detectedDevice != null) {
        val autoEQProfiles by autoEqViewModel.autoEQProfiles.collectAsStateWithLifecycle()

        AutoEQSuggestionDialog(
            deviceName = bluetoothName ?: detectedDevice!!.name,
            savedDevice = detectedDevice!!,
            equalizerEnabled = equalizerEnabled,
            onApplyProfile = {
                // Apply the AutoEQ profile
                val profile = autoEQProfiles
                    .find { it.name == detectedDevice!!.autoEQProfileName }

                if (profile != null) {
                    autoEqViewModel.applyAutoEQProfile(profile)
                    autoEqViewModel.setActiveAudioDevice(detectedDevice!!)
                    Toast.makeText(
                        context,
                        "Applied ${profile.name} profile",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showAutoEQSuggestion = false
            },
            onDismiss = {
                showAutoEQSuggestion = false
            },
            onDontAskAgain = {
                autoEqViewModel.dismissAutoEQSuggestion(detectedDevice!!.id)
                showAutoEQSuggestion = false
            },
            onConfigureDevice = {
                showAutoEQSuggestion = false
                showDeviceConfigFromSuggestion = true
            }
        )
    }

    // Device Configuration Dialog (opened from suggestion)
    if (showDeviceConfigFromSuggestion) {
        DeviceConfigurationBottomSheet(
            autoEqViewModel = autoEqViewModel,
            onDismiss = { showDeviceConfigFromSuggestion = false }
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
    // ⚡ 倍速播放
    playbackSpeed: Float = 1f,
    onSpeedToggle: () -> Unit = {},
    onSpeedSet: (Float) -> Unit = {},
    // ⚡ 底部控制栏是否显示倍速按钮
    showSpeedButton: Boolean = true,
    // ⚡ 倍速变调：开启后音高随倍速自动变调
    pitchFollowSpeed: Boolean = true,
    onPitchFollowSpeedToggle: (Boolean) -> Unit = {},
    // ⚡ 播放器控键透明度：0..1 alpha（100% = 完全不透明，应用到所有控制按钮）
    controlsOpacity: Float = 1f,
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
            // ⚡ 播放器控键透明度：播放/暂停、上/下首、随机/循环/收藏整块统一淡化
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controlsOpacity },
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
                playbackSpeed = playbackSpeed,
                onSpeedToggle = onSpeedToggle,
                onSpeedSet = onSpeedSet,
                showSpeedButton = showSpeedButton,
                // ⚡ 必须透传变调开关参数：否则 BottomToggleRow 使用默认空回调，
                //    弹窗内开关点击无效（「关不了」）
                pitchFollowSpeed = pitchFollowSpeed,
                onPitchFollowSpeedToggle = onPitchFollowSpeedToggle,
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
    playbackMetadataBitDepth: Int?,
    playbackMetadataDisplayLabel: String?,
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
    val audioBitDepth = if (isMetadataForCurrentSong) {
        playbackMetadataBitDepth ?: songBitDepthFallback(song)
    } else {
        songBitDepthFallback(song)
    }

    PlayerProgressBarSection(
        songId = song.id,
        currentPositionProvider = currentPositionProvider,
        totalDurationValue = totalDurationValue,
        songDurationHintMs = song.duration,
        audioMimeType = audioMimeType,
        audioBitrate = audioBitrate,
        audioSampleRate = audioSampleRate,
        audioBitDepth = audioBitDepth,
        // 持久化标签仅在属于当前歌曲时使用，避免切歌后短暂显示上一首的音质标签
        persistedAudioMetaLabel = if (isMetadataForCurrentSong) playbackMetadataDisplayLabel else null,
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
    showLyricsButton: Boolean = true,
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
            showLyricsButton = showLyricsButton,
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
        // 如果方法一 > 方法二，采用方法二，多余空间由封面区吸收（居中留白）
        // 如果方法二 > 方法一，采用方法一，防止按钮被挤出
        val coverSize: Dp = if (coverSizeMethod1 > coverSizeMethod2) {
            coverSizeMethod2
        } else {
            coverSizeMethod1
        }
        
        // 水平padding
        val horizontalPadding = 16.dp
        
        // ⚡ 底部区域不再用 SpaceBetween 硬撑固定高度：长屏时剩余空间会被灌进底部，
        //    导致「进度条与播放控制按钮之间出现大空白」。改为封面区 weight(1f) 吸收
        //    全部多余空间（封面居中，留白均匀分布在封面上下），底部内容自然高度紧凑排列。
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面区域 - 吸收剩余空间，封面居中
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                albumCoverSection(Modifier.size(coverSize))
            }
            
            // 底部信息 + 控制区域 - 自然高度，无中间留白
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                songMetadataSection()
                playerProgressSection()
                Spacer(Modifier.height(4.dp))
                controlsSection()
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
    showLyricsButton: Boolean = true,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    onClickComment: () -> Unit,
    currentQueueSourceName: String,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true },
    isRadioPlayback: Boolean = false
) {
    // 评论依赖网易云接口（加载/发送）或 B 站接口（加载）：只有网易云/B 站歌曲显示评论按钮
    val canShowComment = song?.let {
        resolveCommentSongId(it).isNotBlank() || !it.resolveBilibiliBvid().isNullOrBlank()
    } ?: false
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
                if (!isRadioPlayback && showLyricsButton) {
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
                            painter = painterResource(R.drawable.rounded_mode_comment_24),
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
                    if (showLyricsButton) {
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
                                painter = painterResource(R.drawable.rounded_mode_comment_24),
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

/** 从歌曲对象推断位深兜底：高采样率/DSD 视为 Hi-Res，无损格式给保守值。 */
private fun songBitDepthFallback(song: Song): Int? = when {
    song.mimeType?.contains("dsd", ignoreCase = true) == true ||
        song.mimeType?.contains("dsf", ignoreCase = true) == true ||
        song.mimeType?.contains("dff", ignoreCase = true) == true -> 32
    song.sampleRate != null && song.sampleRate > 48_000 -> 24
    song.mimeType?.contains("flac", ignoreCase = true) == true ||
        song.mimeType?.contains("wav", ignoreCase = true) == true ||
        song.mimeType?.contains("alac", ignoreCase = true) == true -> 16
    else -> null
}

/** 音质标签的最后兜底：任何情况下都不让音质信息消失。 */
private fun songAudioMetaFallbackLabel(mimeType: String?, bitrate: Int?, sampleRate: Int?): String =
    formatAudioMetaLabel(mimeType, bitrate, sampleRate)
        ?: mimeTypeToFormat(mimeType).takeIf { it != "-" }?.uppercase(Locale.getDefault())
        ?: "AUDIO"

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
    audioBitDepth: Int?,
    persistedAudioMetaLabel: String?,
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
    // 优先使用 ViewModel 层持久化的音质标签：它跨 UI 重组存活（打开歌词界面/展开折叠
    // 会回收 PlayerProgressBarSection 的本地 remember 状态），且带"不降级"保护。
    // 探针尚未完成时回退到由 song/元数据即时计算的标签，保证标签始终可见。
    // ⚡ 本地回退在标签缺失时补一层歌曲级兜底：即使 metadata 与 UI 歌曲短暂错位
    // （平板宽屏 carousel 滑动/预渲染期间 mediaId 未同步），也不让音质信息消失。
    val displayAudioMetaLabel = if (showAudioFileInfo) {
        persistedAudioMetaLabel?.takeIf { it.isNotBlank() }
            ?: audioMetaLabel
            ?: songAudioMetaFallbackLabel(audioMimeType, audioBitrate, audioSampleRate)
    } else {
        null
    }
    // Hi-Res 判定：采样率 > 48 kHz 或位深 >= 24 bit（含 DSD/DSF/DFF 32bit）
    val isHiResRaw = (audioSampleRate ?: 0) > 48_000 || (audioBitDepth ?: 0) >= 24
    // ⚡ Hi-Res 稳定性：同一首歌内一旦判定为 Hi-Res 就保持显示（sticky），
    //    避免元数据探针时序 / mediaId 短暂错位导致标识"闪一下就消失"；
    //    切歌时 remember(songId) 自动重置重新判定。
    var isHiResSticky by remember(songId) { mutableStateOf(false) }
    LaunchedEffect(songId, isHiResRaw) {
        if (isHiResRaw) isHiResSticky = true
    }
    val isHiRes = isHiResRaw || isHiResSticky
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
                isHiRes = isHiRes,
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

// Hi-Res 认证徽标：由 hires_audio_badge.xml（源自 hires (1).svg）绘制，
// 调用处通过 ColorFilter.tint 跟随主题自动切换黑白，保证任意背景下可见。

@Composable
private fun EfficientTimeLabels(
    positionState: androidx.compose.runtime.State<Long>,
    duration: Long,
    isVisible: Boolean,
    textColor: Color,
    audioMetaLabel: String?,
    isHiRes: Boolean,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = audioMetaLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHiRes) {
                        // ⚡ Hi-Res 认证指示：左侧认证圆点 + 徽标（徽标缩小并垂直对齐文字中线，避免偏大偏下）
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(textColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Image(
                                painter = painterResource(R.drawable.hires_audio_badge),
                                contentDescription = "Hi-Res",
                                colorFilter = ColorFilter.tint(textColor),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(width = 19.dp, height = 8.dp)
                            )
                        }
                    }
                }
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
                // ⚡ 兜底：等待折叠动画归零最长 1.5s。若展开/折叠动画被中断或卡住
                // （fraction 始终 >0.001），超时后强制关门，避免 gate 永久挂起导致
                // 内容被占位层盖住（表现为进度条/播放信息"完全卡住"）。
                withTimeoutOrNull(1500) {
                    snapshotFlow { expansionFractionProvider().coerceIn(0f, 1f) }
                        .first { fraction -> fraction <= 0.001f }
                }
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

            if (frame.effectiveExpansionFraction <= 0.001f && !frame.isExpandedOverride) {
                isDelayGateOpen = false
            } else if (frame.isExpandedOverride) {
                // ⚡ 展开即开门：展开过程中内容全程可见（alpha 由 baseAlphaProvider 平滑控制），
                // 不再等展开到 appearThreshold(98%) 才显示——否则展开前期全是不透明占位块，
                // 占位块与内容交叉淡入淡出，视觉上表现为"展开时闪几下"。
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
                    (!applyPlaceholderDelayOnClose || isExpanding)
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
            // ⚡ 防闪烁：content 常驻组合，isDelayGateOpen 只通过 contentBlendAlpha 控制显隐。
            // 旧实现用 if(isDelayGateOpen) 条件组合，gate 翻转一次就销毁重建一次内容，
            // 封面图片会重新加载、歌曲信息动画重播 → 展开时闪几下。
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = contentBlendAlpha * baseAlphaProvider()
                }
            ) {
                content()
            }
            // ⚡ 展开态不渲染占位块：占位块与内容交叉淡入淡出是展开闪烁的根源之一。
            // 展开时内容全程可见（alpha 由 baseAlphaProvider 平滑过渡），折叠时才淡入占位块。
            if (showPlaceholders && !isExpandedOverride && placeholderBlendAlpha > 0.001f) {
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
    playbackSpeed: Float = 1f,
    onSpeedToggle: () -> Unit = {},
    onSpeedSet: (Float) -> Unit = {},
    // ⚡ 底部控制栏是否显示倍速按钮
    showSpeedButton: Boolean = true,
    // ⚡ 倍速变调：开启后音高随倍速自动变调
    pitchFollowSpeed: Boolean = true,
    onPitchFollowSpeedToggle: (Boolean) -> Unit = {},
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    var showSpeedSheet by remember { mutableStateOf(false) }

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
            // 倍速按钮：正方形，1x 时右侧圆角匹配其他按钮，其它倍速时为完美圆形
            if (!isRadioPlayback && showSpeedButton) {
                val speedText = formatPlaybackSpeed(playbackSpeed)
                val isActive = playbackSpeed != 1f
                val btnSize = 40.dp
                val halfSize = btnSize / 2
                val btnShape = RoundedCornerShape(
                    topStart = halfSize,
                    topEnd = if (isActive) halfSize else rowCorners,
                    bottomStart = halfSize,
                    bottomEnd = if (isActive) halfSize else rowCorners
                )
                Box(
                    modifier = Modifier
                        .size(btnSize)
                        .background(
                            color = if (isActive) primaryFixed else inactiveBg,
                            shape = btnShape
                        )
                        .clip(btnShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onSpeedToggle() },
                                onLongPress = { showSpeedSheet = true }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) onPrimaryFixed else inactiveContentColor
                    )
                }
            }

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
                            else -> R.drawable.rounded_download_24
                        },
                        contentDesc = "Download",
                        // ⚡ 下载进度：在按钮背景上按比例从左到右填充（模仿 mini player 进度条），
                        // 而不是把进度区域直接拉伸成定宽色块
                        progressFill = if (downloadProgress != null && !isDownloadComplete && !isDownloadFailed)
                            (downloadProgress / 100f).coerceIn(0f, 1f) else 0f,
                        progressFillColor = onPrimaryFixed.copy(alpha = 0.30f)
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

    // 倍速详细调节底部弹窗
    if (showSpeedSheet) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.playback_speed),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                var sliderValue by remember(playbackSpeed) { mutableStateOf(playbackSpeed) }
                val displayText = String.format("%.1fx", sliderValue)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0.5x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "2.0x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSpeedSet(sliderValue) },
                    valueRange = 0.5f..2f,
                    steps = 14
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f).forEach { preset ->
                        val isSelected = sliderValue == preset
                        FilledTonalButton(
                            onClick = {
                                sliderValue = preset
                                onSpeedSet(preset)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${preset}x",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                // ⚡ 变调开关：开启后音高随倍速自动变调（pitch == speed），关闭时保持原调
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        // ⚡ 整行点击用 toggleable 统一处理：若同时保留 Row.clickable + Switch
                        //    的 onCheckedChange，点击 Switch 时两个事件都会触发（Compose 中子
                        //    组件不自动消费父组件点击），两次取反值相反、异步写入竞争 → 开关
                        //    弹回/关不上。
                        .toggleable(
                            value = pitchFollowSpeed,
                            onValueChange = onPitchFollowSpeedToggle
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.playback_speed_pitch_follow),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.playback_speed_pitch_follow_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pitchFollowSpeed,
                        // ⚡ 交互由 Row 的 toggleable 统一处理（避免双重触发），此处仅显示状态
                        onCheckedChange = null
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 格式化倍速显示：四舍五入到 2 位小数并去掉多余尾零，避免 float 浮点误差
 * 显示成 10 位小数（如 1.1000000238x）。1.0 → "1x"，1.1 → "1.1x"，0.75 → "0.75x"。
 */
private fun formatPlaybackSpeed(speed: Float): String {
    val normalized = Math.round(speed * 100) / 100.0
    val text = String.format(java.util.Locale.ROOT, "%.2f", normalized)
        .trimEnd('0')
        .trimEnd('.')
    return "${text}x"
}

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
    onStationClick: (RadioStation) -> Unit,
    isLandscape: Boolean = false
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
    val isLandscapeOrientation = isLandscape

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

/**
 * Parallel layout for tablets: player controls on left, lyrics on right.
 * Left side is scrollable to prevent overflow on smaller tablet windows.
 * Right side only shows lyrics (no controls/search/background).
 */
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerParallelLayout(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit,
    isRadioPlayback: Boolean,
    showLyricsSheet: Boolean,
    playerViewModel: PlayerViewModel,
    lyricsSearchUiState: LyricsSearchUiState,
    lyricsSyncOffset: Int,
    lyricsFontFamily: String,
    lyricsFontSize: String,
    customPlayerBackgroundEnabled: Boolean,
    customPlayerBackgroundUri: String?,
    customPlayerBackgroundMode: com.theveloper.pixelplay.data.preferences.PlayerBackgroundMode,
    customPlayerBackgroundBlurRadius: Int,
    customPlayerControlsOpacity: Int,
    lyricsGradientOverlayEnabled: Boolean,
    lyricsSolidOverlayAlpha: Float,
    lyricsVibrantBackgroundEnabled: Boolean,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    showLyricsSettings: Boolean = false,
    onToggleLyricsSettings: () -> Unit = {},
    showLyricsTrackInfo: Boolean,
    isExplainingLyrics: Boolean,
    lyricsExplanation: String?,
    lyricsExplanationEnabled: Boolean,
    onDismissExplanation: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Left side: Player controls — scrollable to prevent overflow
        androidx.compose.foundation.rememberScrollState().let { scrollState ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Album cover — smaller to leave room for controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .padding(bottom = 16.dp)
                ) {
                    albumCoverSection(Modifier.fillMaxSize())
                }

                // Song metadata
                songMetadataSection()

                Spacer(Modifier.height(12.dp))

                // Progress bar
                playerProgressSection()

                Spacer(Modifier.height(8.dp))

                // Controls
                controlsSection()
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )

        // Right side: Lyrics only (no controls/search/background)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ParallelLyricsPanel(
                stablePlayerStateFlow = playerViewModel.stablePlayerState,
                playbackPositionFlow = playerViewModel.currentPlaybackPosition,
                lyricsSyncOffset = lyricsSyncOffset,
                onSeekTo = { playerViewModel.seekTo(it) },
                onMoreClick = onToggleLyricsSettings,
                gradientOverlayEnabled = lyricsGradientOverlayEnabled,
                lyricsFontFamily = lyricsFontFamily,
                lyricsFontSize = lyricsFontSize
            )

            // 歌词设置卡片（从右侧底部弹出）
            androidx.compose.animation.AnimatedVisibility(
                visible = showLyricsSettings,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    ParallelLyricsSettingsCard(
                        playerViewModel = playerViewModel,
                        modifier = Modifier.fillMaxWidth(),
                        onDismiss = onToggleLyricsSettings
                    )
                }
            }
        }
    }
}

/**
 * 歌词设置卡片（平行布局），与普通模式 LyricsMoreBottomSheet 的外观设置一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParallelLyricsSettingsCard(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val lyricsFontFamily by playerViewModel.lyricsFontFamily.collectAsStateWithLifecycle()

    // 读取 DataStore 偏好
    val lyricsAlignment by remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }.collectAsStateWithLifecycle(initialValue = "left")

    val showTranslation by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val showRomanization by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_romanization")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val useAnimatedLyrics by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    // 获取当前歌词是否有翻译/罗马音
    val lyrics by playerViewModel.stablePlayerState
        .map { it.lyrics }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = null)
    val hasTranslation = lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
    val hasRomanization = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true

    // 构建字体选项列表：预定义 + 自定义字体
    var customFontsRefreshTick by remember { mutableStateOf(0) }
    val currentFontKey by context.dataStore.data
        .map { it[stringPreferencesKey("lyrics_font_family")] ?: "DEFAULT" }
        .distinctUntilChanged()
        .collectAsState(initial = "DEFAULT")
    val customFonts = remember(customFontsRefreshTick, currentFontKey) {
        com.theveloper.pixelplay.ui.theme.listCustomFonts(context).map {
            "${com.theveloper.pixelplay.ui.theme.CUSTOM_FONT_PREFIX}$it"
        }
    }
    val predefinedFonts = com.theveloper.pixelplay.ui.theme.LyricsFontDisplayNames.keys.toList()
    val allFontFamilies = predefinedFonts + customFonts

    fun fontDisplayName(key: String): String =
        if (com.theveloper.pixelplay.ui.theme.isCustomFontKey(key))
            com.theveloper.pixelplay.ui.theme.customFontDisplayName(key)
        else com.theveloper.pixelplay.ui.theme.LyricsFontDisplayNames[key] ?: key

    val onFontLongClick: (String) -> Unit = { key ->
        if (com.theveloper.pixelplay.ui.theme.isCustomFontKey(key)) {
            com.theveloper.pixelplay.ui.theme.deleteCustomFont(context, key)
            if (lyricsFontFamily == key) {
                playerViewModel.setLyricsFontFamily("DEFAULT")
            }
            customFontsRefreshTick++
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题栏 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandLess,
                        contentDescription = "收起",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 对齐方式
            Text(
                text = "对齐方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val alignmentOptions = listOf("left", "center", "right")
            val alignmentLabels = listOf("左", "中", "右")
            val alignmentIcons = listOf(Icons.Rounded.FormatAlignLeft, Icons.Rounded.FormatAlignCenter, Icons.Rounded.FormatAlignRight)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                alignmentOptions.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = lyricsAlignment == value,
                        onClick = {
                            scope.launch {
                                context.dataStore.edit { it[stringPreferencesKey("lyrics_alignment")] = value }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, alignmentOptions.size),
                        icon = { Icon(alignmentIcons[index], contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(alignmentLabels[index], fontSize = 12.sp) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            // 字体大小
            Text(
                text = "字体大小",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val sizeOptions = listOf("SMALL", "DEFAULT", "LARGE", "EXTRA_LARGE")
            val sizeLabels = listOf("S", "M", "L", "XL")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sizeOptions.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = fullPlayerSlice.lyricsFontSize == value,
                        onClick = { playerViewModel.setLyricsFontSize(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, sizeOptions.size),
                        label = { Text(sizeLabels[index], fontSize = 12.sp) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            // 字体选择
            Text(
                text = "字体",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allFontFamilies.forEach { family ->
                    val isActive = lyricsFontFamily == family
                    val isDeletable = com.theveloper.pixelplay.ui.theme.isCustomFontKey(family)
                    val pressProgress = remember { Animatable(0f) }
                    val dangerColor = MaterialTheme.colorScheme.error
                    val targetBg = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    val bgColor = lerpColor(targetBg, dangerColor, pressProgress.value)
                    val targetContent = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    val txtColor = lerpColor(targetContent, Color.White, pressProgress.value)
                    val corner by animateDpAsState(
                        targetValue = if (isActive) 50.dp else 12.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "FontCorner"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(bgColor)
                            .pointerInput(isDeletable) {
                                if (isDeletable) {
                                    detectTapGestures(
                                        onPress = {
                                            pressProgress.animateTo(1f, tween(durationMillis = 600))
                                            tryAwaitRelease()
                                            pressProgress.animateTo(0f, tween(durationMillis = 200))
                                        },
                                        onTap = { playerViewModel.setLyricsFontFamily(family) },
                                        onLongPress = { onFontLongClick(family) }
                                    )
                                } else {
                                    detectTapGestures(onTap = { playerViewModel.setLyricsFontFamily(family) })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fontDisplayName(family),
                            color = txtColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            softWrap = true,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // 动画歌词
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("动画歌词", style = MaterialTheme.typography.bodyMedium)
                    Text("逐行高亮动画效果", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = useAnimatedLyrics,
                    onCheckedChange = {
                        scope.launch {
                            context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("use_animated_lyrics")] = it }
                        }
                    }
                )
            }

            // 翻译
            if (hasTranslation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("显示翻译", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showTranslation,
                        onCheckedChange = {
                            scope.launch {
                                context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("show_lyrics_translation")] = it }
                            }
                        }
                    )
                }
            }

            // 罗马音
            if (hasRomanization) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("显示罗马音", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showRomanization,
                        onCheckedChange = {
                            scope.launch {
                                context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("show_romanization")] = it }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Lightweight lyrics-only panel for parallel layout.
 * Shows synced/plain lyrics without background, controls, search, or other extras.
 */
@Composable
private fun ParallelLyricsPanel(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    onSeekTo: (Long) -> Unit,
    onMoreClick: () -> Unit = {},
    gradientOverlayEnabled: Boolean = true,
    lyricsFontFamily: String = "DEFAULT",
    lyricsFontSize: String = "DEFAULT"
) {
    val lyrics by stablePlayerStateFlow
        .map { it.lyrics }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = null)
    val isLoadingLyrics by stablePlayerStateFlow
        .map { it.isLoadingLyrics }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(initialValue = false)

    // 使用专辑取色主题（与主歌词页一致）
    val colorScheme = LocalMaterialTheme.current
    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    val containerColor = sheetColors.container
    val accentColor = sheetColors.lyricHighlight
    // 应用字体设置（与普通模式 LyricsSheet 的映射一致）
    val context = LocalContext.current
    val parallelBaseFontSize = when (lyricsFontSize) {
        "SMALL" -> 14.sp
        "DEFAULT" -> 20.sp
        "LARGE" -> 26.sp
        "EXTRA_LARGE" -> 32.sp
        else -> 20.sp
    }
    val parallelFontFamily = remember(lyricsFontFamily) {
        com.theveloper.pixelplay.ui.theme.resolveLyricsFontFamily(context, lyricsFontFamily)
    }
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = parallelFontFamily,
        fontSize = parallelBaseFontSize,
        lineHeight = (parallelBaseFontSize.value * 1.4f).sp
    )

    // Read alignment preference
    val lyricsAlignment by remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }.collectAsStateWithLifecycle(initialValue = "left")

    val showTranslation by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val showRomanization by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_romanization")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val useAnimatedLyrics by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val animatedLyricsBlurEnabled by remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
    }.collectAsStateWithLifecycle(initialValue = true)

    val showSynced = remember(lyrics, isLoadingLyrics) {
        when {
            isLoadingLyrics -> null
            !lyrics?.synced.isNullOrEmpty() -> true
            !lyrics?.plain.isNullOrEmpty() -> false
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (showSynced) {
            null -> {
                // Loading
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLoadingLyrics) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.loading_lyrics),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.LinearWavyProgressIndicator(
                                trackColor = accentColor.copy(alpha = 0.4f),
                                color = accentColor,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.lyrics_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            true -> {
                lyrics?.synced?.let { synced ->
                    val syncedListState = rememberLazyListState()
                    SyncedLyricsList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 80.dp, bottom = 80.dp),
                        lines = synced,
                        listState = syncedListState,
                        playbackPositionFlow = playbackPositionFlow,
                        lyricsSyncOffset = lyricsSyncOffset,
                        accentColor = accentColor,
                        containerColor = containerColor,
                        textStyle = textStyle,
                        onLineClick = { line ->
                            onSeekTo((line.time.toLong() - lyricsSyncOffset).coerceAtLeast(0L))
                        },
                        highlightZoneFraction = 0.08f,
                        highlightOffsetDp = 32.dp,
                        autoscrollAnimationSpec = spring(stiffness = Spring.StiffnessLow),
                        useAnimatedLyrics = useAnimatedLyrics,
                        animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                        animatedLyricsBlurStrength = 2.5f,
                        lyricsAlignment = lyricsAlignment,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        gradientOverlayEnabled = gradientOverlayEnabled
                    )
                }
            }

            false -> {
                lyrics?.plain?.let { plain ->
                    val staticListState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = staticListState,
                        contentPadding = PaddingValues(
                            start = 24.dp, end = 24.dp,
                            top = 80.dp, bottom = 80.dp
                        )
                    ) {
                        itemsIndexed(
                            items = plain,
                            key = { index, line -> "$index-$line" }
                        ) { _, line ->
                            com.theveloper.pixelplay.presentation.components.PlainLyricsLine(
                                line = line,
                                style = textStyle,
                                lyricsAlignment = lyricsAlignment,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // 右下角省略号按钮（歌词设置入口）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            FilledIconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "歌词设置"
                )
            }
        }
    }
}
