package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import com.theveloper.pixelplay.presentation.components.BackupModuleSelectionDialog
import com.theveloper.pixelplay.presentation.components.TranscodeCacheListDialog
import com.theveloper.pixelplay.utils.TranscodeCacheManager
import com.theveloper.pixelplay.utils.KuromojiEngine
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.data.preferences.STARTUP_ANIMATION_STYLE_PREF_KEY
import com.theveloper.pixelplay.data.preferences.StartupAnimationStyle
import com.theveloper.pixelplay.data.preferences.MiniPlayerStyle
import com.theveloper.pixelplay.data.preferences.MINI_PLAYER_STYLE_PREF_KEY
import com.theveloper.pixelplay.data.preferences.MINI_PLAYER_CORNER_RADIUS_PREF_KEY
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import java.util.Locale
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import android.content.Context
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Palette
import com.theveloper.pixelplay.presentation.components.ExpressiveButtonGroup
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.backup.model.BackupHistoryEntry
import com.theveloper.pixelplay.data.backup.model.BackupOperationType
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupFormat
import com.theveloper.pixelplay.data.backup.model.BackupTransferProgressUpdate
import com.theveloper.pixelplay.data.backup.model.ModuleRestoreDetail
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.BackgroundStyle
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.MusicQualityCatalog
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.NavRailStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.PlayerBackgroundMode
import com.theveloper.pixelplay.data.preferences.TabletPlayerLayout
import com.theveloper.pixelplay.data.preferences.PlayerStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import com.theveloper.pixelplay.presentation.viewmodel.ApiSettingsViewModel
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.CustomPlayerBackground
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.FileExplorerDialog
import com.theveloper.pixelplay.presentation.components.GlyphMatrixPreview
import com.theveloper.pixelplay.presentation.components.LyricsSourceOrderSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.presentation.components.PlayerProgressStyle
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.theveloper.pixelplay.presentation.components.PlayerThumbStyle
import com.theveloper.pixelplay.presentation.components.StyledPlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.PlayerProgressPreferences
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.lerp
import com.theveloper.pixelplay.presentation.components.OptimizedAlbumArt
import com.theveloper.pixelplay.presentation.components.player.ImmersiveCoverBackdrop
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.presentation.components.player.AnimatedPlaybackControls
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 播放器与歌词外观 —— 独立子页：把「迷你播放条 / 正在播放 / 悬浮歌词 / 歌词界面」
 * 四个子节集中到一处。（顶部实时预览已下线，改回纯选项列表。）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerLyricsAppearanceScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    // 搬入的「迷你播放条」子节用它写入 DataStore（原分支同名局部变量）
    val context = LocalContext.current
    val startupScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PlayerLyricsAppearanceHeader(onBackClick = onBackClick) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
                // ⚡ 迷你播放条样式（经典 / Rhythm）
                val miniPlayerStyle by remember {
                    context.dataStore.data.map { prefs ->
                        MiniPlayerStyle.fromName(prefs[MINI_PLAYER_STYLE_PREF_KEY])
                    }
                }.collectAsStateWithLifecycle(initialValue = MiniPlayerStyle.CLASSIC)

                SettingsSubsection(title = stringResource(R.string.setcat_appearance_miniplayer_section)) {
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_miniplayer_style_label),
                        description = stringResource(R.string.setcat_miniplayer_style_desc),
                        options = mapOf(
                            MiniPlayerStyle.CLASSIC.name to stringResource(R.string.setcat_miniplayer_style_classic),
                            MiniPlayerStyle.MATERIAL.name to stringResource(R.string.setcat_miniplayer_style_material),
                            MiniPlayerStyle.EXPRESSIVE.name to stringResource(R.string.setcat_miniplayer_style_expressive)
                        ),
                        selectedKey = miniPlayerStyle.name,
                        onSelectionChanged = { key ->
                            val style = MiniPlayerStyle.fromName(key)
                            startupScope.launch {
                                context.dataStore.edit { prefs ->
                                    prefs[MINI_PLAYER_STYLE_PREF_KEY] = style.name
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.MusicNote,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )

                    // ⚡ 迷你条圆角：独立设置，不再跟随导航栏圆角
                    val miniRadius by remember {
                        context.dataStore.data.map { prefs ->
                            prefs[MINI_PLAYER_CORNER_RADIUS_PREF_KEY] ?: 28
                        }
                    }.collectAsStateWithLifecycle(initialValue = 28)
                    var miniRadiusDraft by remember(miniRadius) {
                        mutableStateOf(miniRadius.toFloat())
                    }
                    SliderSettingsItem(
                        label = stringResource(R.string.setcat_miniplayer_corner_radius),
                        value = miniRadiusDraft,
                        valueRange = 0f..32f,
                        steps = 30,
                        onValueChange = { miniRadiusDraft = it },
                        onValueChangeFinished = {
                            val radiusDp = miniRadiusDraft.roundToInt()
                            startupScope.launch {
                                context.dataStore.edit { prefs ->
                                    prefs[MINI_PLAYER_CORNER_RADIUS_PREF_KEY] = radiusDp
                                }
                            }
                        },
                        valueText = { value -> "${value.roundToInt()} dp" }
                    )
                }

                SettingsSubsection(title = stringResource(R.string.setcat_now_playing)) {
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_player_theme_label),
                        description = stringResource(R.string.setcat_player_theme_desc),
                        options = mapOf(
                            ThemePreference.ALBUM_ART to stringResource(R.string.setcat_player_theme_album_art),
                            ThemePreference.DYNAMIC to stringResource(R.string.setcat_player_theme_dynamic),
                            ThemePreference.IMMERSIVE to stringResource(R.string.setcat_player_theme_immersive)
                        ),
                        selectedKey = uiState.playerThemePreference,
                        onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
                        leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // ⚡ Apple Music 动态封面（Canvas）：默认关闭（会产生少量在线请求）。
                    //    开启后当前歌曲命中动态视频封面时，封面会渐变切换为动态封面。
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_apple_canvas_title),
                        subtitle = stringResource(R.string.setcat_apple_canvas_subtitle),
                        checked = uiState.appleCanvasEnabled,
                        onCheckedChange = { settingsViewModel.setAppleCanvasEnabled(it) },
                        leadingIcon = { Icon(Icons.Rounded.Animation, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // Tablet player layout option
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_tablet_player_layout_label),
                        description = stringResource(R.string.setcat_tablet_player_layout_desc),
                        options = mapOf(
                            TabletPlayerLayout.VERTICAL.storageKey to stringResource(R.string.setcat_tablet_layout_vertical),
                            TabletPlayerLayout.PARALLEL.storageKey to stringResource(R.string.setcat_tablet_layout_parallel)
                        ),
                        selectedKey = uiState.tabletPlayerLayout.storageKey,
                        onSelectionChanged = { key -> settingsViewModel.setTabletPlayerLayout(TabletPlayerLayout.fromStorageKey(key)) },
                        leadingIcon = { Icon(Icons.Rounded.TabletAndroid, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // 播放器样式：经典 / Expressive（模仿 Rhythm 的 ExpressiveButtonGroup 切换）
                    val isExpressiveStyleActive = uiState.playerStyle == PlayerStyle.EXPRESSIVE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Album,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.setcat_player_style_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.setcat_player_style_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ExpressiveButtonGroup(
                            items = listOf(
                                stringResource(R.string.setcat_player_style_classic),
                                stringResource(R.string.setcat_player_style_expressive)
                            ),
                            selectedIndex = if (isExpressiveStyleActive) 1 else 0,
                            onItemClick = { index ->
                                settingsViewModel.setPlayerStyle(if (index == 1) PlayerStyle.EXPRESSIVE else PlayerStyle.CLASSIC)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 经典样式专属：播放器控制（模仿 Rhythm 的 Player Controls 分组）
                    if (!isExpressiveStyleActive) {
                        Text(
                            text = stringResource(R.string.setcat_player_controls_group),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                        // ⚡ 控制按钮无色块样式：上一曲/播放/下一曲渲染为纯图标
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_transport_flat_style_title),
                            subtitle = stringResource(R.string.setcat_transport_flat_style_subtitle),
                            checked = uiState.transportControlsFlatStyle,
                            onCheckedChange = settingsViewModel::setTransportControlsFlatStyle,
                            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                    }

                    // Expressive 样式专属（模仿 Rhythm 的 settings_expressive_player 分组，全部映射真实功能）
                    if (isExpressiveStyleActive) {
                        Text(
                            text = stringResource(R.string.setcat_expressive_group_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                        // 强调色背景：控制卡片与功能胶囊改用封面强调色玻璃
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_expressive_accent_title),
                            subtitle = stringResource(R.string.setcat_expressive_accent_desc),
                            checked = uiState.playerAccentBackground,
                            onCheckedChange = settingsViewModel::setPlayerAccentBackground,
                            leadingIcon = { Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                        // 合并控件：底部功能胶囊并入控制卡片
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_expressive_merge_title),
                            subtitle = stringResource(R.string.setcat_expressive_merge_desc),
                            checked = uiState.playerMergeControls,
                            onCheckedChange = settingsViewModel::setPlayerMergeControls,
                            leadingIcon = { Icon(Icons.Rounded.MergeType, null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                    }

                    // ⚡ 播放器不透明度（独立设置项：应用到播放器/歌词界面除背景外的所有元素）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setcat_custom_player_controls_opacity_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = uiState.customPlayerControlsOpacity.toFloat(),
                                onValueChange = { settingsViewModel.setCustomPlayerControlsOpacity(it.toInt()) },
                                valueRange = ThemePreferencesRepository.MIN_CUSTOM_CONTROLS_OPACITY.toFloat()..ThemePreferencesRepository.MAX_CUSTOM_CONTROLS_OPACITY.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${uiState.customPlayerControlsOpacity}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.setcat_custom_player_controls_opacity_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_lyrics_gradient_overlay_title),
                        subtitle = stringResource(R.string.setcat_lyrics_gradient_overlay_desc),
                        checked = uiState.lyricsGradientOverlayEnabled,
                        onCheckedChange = { settingsViewModel.setLyricsGradientOverlayEnabled(it) },
                        leadingIcon = { Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // ⚡ 背景样式三选一（原「歌词绚丽背景」开关升级而来）：
                    //    绚丽背景 = 流体渐变；取色背景 = 静态封面主色；纯色背景 = 跟随主题
                    //    （即原开关关闭时的观感）。旧开关的「开/关」自动迁移为 绚丽/纯色。
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_lyrics_bg_style_title),
                        description = stringResource(R.string.setcat_lyrics_bg_style_desc),
                        options = mapOf(
                            BackgroundStyle.VIBRANT.name to stringResource(R.string.setcat_bg_style_vibrant),
                            BackgroundStyle.PALETTE.name to stringResource(R.string.setcat_bg_style_palette),
                            BackgroundStyle.SOLID.name to stringResource(R.string.setcat_bg_style_solid)
                        ),
                        selectedKey = uiState.lyricsBackgroundStyle.name,
                        onSelectionChanged = { key ->
                            settingsViewModel.setLyricsBackgroundStyle(BackgroundStyle.fromName(key))
                        },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // ⚡ 背景样式三选一（原「播放器绚丽背景」开关升级而来）
                    ThemeSelectorItem(
                        label = stringResource(R.string.setcat_player_bg_style_title),
                        description = stringResource(R.string.setcat_player_bg_style_desc),
                        options = mapOf(
                            BackgroundStyle.VIBRANT.name to stringResource(R.string.setcat_bg_style_vibrant),
                            BackgroundStyle.PALETTE.name to stringResource(R.string.setcat_bg_style_palette),
                            BackgroundStyle.SOLID.name to stringResource(R.string.setcat_bg_style_solid)
                        ),
                        selectedKey = uiState.playerBackgroundStyle.name,
                        onSelectionChanged = { key ->
                            settingsViewModel.setPlayerBackgroundStyle(BackgroundStyle.fromName(key))
                        },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // ⚡ 按钮 / 文字强制黑白（跟随主题明暗：暗色主题白色、亮色主题黑色）。
                    //    默认关闭，关闭时与当前观感完全一致。
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_lyrics_mono_colors_title),
                        subtitle = stringResource(R.string.setcat_lyrics_mono_colors_desc),
                        checked = uiState.lyricsMonoContentColors,
                        onCheckedChange = { settingsViewModel.setLyricsMonoContentColors(it) },
                        leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.secondary) }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_player_mono_colors_title),
                        subtitle = stringResource(R.string.setcat_player_mono_colors_desc),
                        checked = uiState.playerMonoContentColors,
                        onCheckedChange = { settingsViewModel.setPlayerMonoContentColors(it) },
                        leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    // ⚡ 自定义播放器背景（应用到播放器界面与歌词界面）
                    val context = LocalContext.current
                    val customBgPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri ->
                            if (uri != null) {
                                // 持久化读取权限：重启后仍可读取所选图片
                                runCatching {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }
                                settingsViewModel.setCustomPlayerBackgroundUri(uri.toString())
                                settingsViewModel.setCustomPlayerBackgroundEnabled(true)
                            }
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_custom_player_background_title),
                        subtitle = stringResource(R.string.setcat_custom_player_background_subtitle),
                        checked = uiState.customPlayerBackgroundEnabled,
                        onCheckedChange = { settingsViewModel.setCustomPlayerBackgroundEnabled(it) },
                        leadingIcon = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.secondary) }
                    )
                    // 打开开关后显示背景预览与选择/移除按钮
                    AnimatedVisibility(
                        visible = uiState.customPlayerBackgroundEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            // ⚡ 展开的额外选项统一用容器卡片包裹（与 SwitchSettingItem 等行容器一致：全宽 + 10dp 圆角）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (!uiState.customPlayerBackgroundUri.isNullOrBlank()) {
                                    // ⚡ 实时预览：与真实播放器背景一致——按设备屏幕宽高比渲染（竖屏/横屏随窗口变化），
                                    //    并实时套用当前显示模式（铺满/拉伸）与模糊强度，所见即所得
                                    val screenAspect = with(LocalWindowInfo.current.containerSize) {
                                        width.toFloat() / height.toFloat()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .fillMaxWidth(0.55f)
                                            .aspectRatio(screenAspect)
                                            .padding(vertical = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        CustomPlayerBackground(
                                            modifier = Modifier.fillMaxSize(),
                                            enabled = true,
                                            uri = uiState.customPlayerBackgroundUri,
                                            mode = uiState.customPlayerBackgroundMode,
                                            blurRadius = uiState.customPlayerBackgroundBlurRadius
                                        )
                                    }
                                    // ⚡ 背景显示模式编辑：铺满 / 拉伸
                                    Text(
                                        text = stringResource(R.string.setcat_custom_player_background_mode_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                    SingleChoiceSegmentedButtonRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        PlayerBackgroundMode.entries.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = uiState.customPlayerBackgroundMode == mode,
                                                onClick = { settingsViewModel.setCustomPlayerBackgroundMode(mode) },
                                                shape = SegmentedButtonDefaults.itemShape(
                                                    index = index,
                                                    count = PlayerBackgroundMode.entries.size
                                                )
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        when (mode) {
                                                            PlayerBackgroundMode.Cover -> R.string.setcat_custom_player_background_mode_cover
                                                            PlayerBackgroundMode.Stretch -> R.string.setcat_custom_player_background_mode_stretch
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    // ⚡ 模糊强度（0=关闭）：滑块实时驱动预览
                                    Text(
                                        text = stringResource(R.string.setcat_custom_player_background_blur_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Slider(
                                            value = uiState.customPlayerBackgroundBlurRadius.toFloat(),
                                            onValueChange = { settingsViewModel.setCustomPlayerBackgroundBlurRadius(it.toInt()) },
                                            valueRange = 0f..ThemePreferencesRepository.MAX_CUSTOM_BACKGROUND_BLUR.toFloat(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (uiState.customPlayerBackgroundBlurRadius > 0)
                                                uiState.customPlayerBackgroundBlurRadius.toString()
                                            else
                                                stringResource(R.string.setcat_custom_player_background_blur_off),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                                        )
                                    }
                                }
                                // ⚡ 背景图片选择 / 移除按钮
                                Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                                    TextButton(
                                        onClick = {
                                            customBgPicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.AddPhotoAlternate,
                                            null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(
                                                if (uiState.customPlayerBackgroundUri.isNullOrBlank())
                                                    R.string.setcat_custom_player_background_pick
                                                else
                                                    R.string.setcat_custom_player_background_change
                                            )
                                        )
                                    }
                                    if (!uiState.customPlayerBackgroundUri.isNullOrBlank()) {
                                        TextButton(
                                            onClick = {
                                                settingsViewModel.setCustomPlayerBackgroundUri(null)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.setcat_custom_player_background_remove),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_show_player_file_info_title),
                        subtitle = stringResource(R.string.setcat_show_player_file_info_subtitle),
                        checked = uiState.showPlayerFileInfo,
                        onCheckedChange = { settingsViewModel.setShowPlayerFileInfo(it) },
                        leadingIcon = { Icon(painterResource(R.drawable.rounded_attach_file_24), null, tint = MaterialTheme.colorScheme.secondary) }
                    )
                    SettingsItem(
                        title = stringResource(R.string.setcat_album_art_palette_title),
                        subtitle = stringResource(R.string.setcat_album_art_palette_subtitle, uiState.albumArtPaletteStyle.label),
                        leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { navController.navigateSafely(Screen.PaletteStyle.route) }
                    )
                    // ⚡ 自定义播放进度条：轨道样式 / 滑块样式 / 播放时旋转（对齐 Rhythm）
                    val playerProgressPrefs by settingsViewModel.playerProgressPreferences.collectAsStateWithLifecycle()
                    SettingsItem(
                        title = stringResource(R.string.setcat_player_progress_bar_title),
                        subtitle = stringResource(
                            R.string.setcat_player_progress_bar_subtitle,
                            stringResource(PlayerProgressStyle.fromStorage(playerProgressPrefs.style).labelResId)
                        ),
                        leadingIcon = { Icon(Icons.Outlined.LinearScale, null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { navController.navigateSafely(Screen.PlayerProgressStyle.route) }
                    )
                    // ⚡ 播放器封面固定使用旧版单张模式，不再提供预览轮播开关。
                }

                // ⚡ 悬浮歌词（桌面歌词）：系统级悬浮窗，必须先拿到「显示在其他应用上层」权限。
                //   启动系统授权页后不依赖 ActivityResult（部分 ROM 不回传结果），
                //   改为在页面回到前台时复查权限，拿到才真正打开开关。
                val floatingLyricsContext = LocalContext.current
                var awaitingOverlayPermission by remember { mutableStateOf(false) }
                LifecycleResumeEffect(Unit) {
                    if (awaitingOverlayPermission) {
                        awaitingOverlayPermission = false
                        if (Settings.canDrawOverlays(floatingLyricsContext)) {
                            settingsViewModel.setFloatingLyricsEnabled(true)
                        }
                    }
                    onPauseOrDispose { }
                }
                SettingsSubsection(title = stringResource(R.string.setcat_floating_lyrics_section)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_floating_lyrics_title),
                        subtitle = stringResource(R.string.setcat_floating_lyrics_subtitle),
                        checked = uiState.floatingLyricsEnabled,
                        onCheckedChange = { checked ->
                            when {
                                !checked -> settingsViewModel.setFloatingLyricsEnabled(false)
                                Settings.canDrawOverlays(floatingLyricsContext) ->
                                    settingsViewModel.setFloatingLyricsEnabled(true)
                                else -> {
                                    awaitingOverlayPermission = true
                                    runCatching {
                                        floatingLyricsContext.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${floatingLyricsContext.packageName}")
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Subtitles,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )

                    if (uiState.floatingLyricsEnabled) {
                        // 使用说明：手势不看说明基本发现不了，直接在设置里写清楚
                        Text(
                            text = stringResource(R.string.setcat_floating_lyrics_usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setcat_floating_lyrics_lines_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            ExpressiveButtonGroup(
                                items = listOf(
                                    stringResource(R.string.setcat_floating_lyrics_lines_single),
                                    stringResource(R.string.setcat_floating_lyrics_lines_double),
                                    stringResource(R.string.setcat_floating_lyrics_lines_multi)
                                ),
                                selectedIndex = when (uiState.floatingLyricsLineMode) {
                                    UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_SINGLE -> 0
                                    UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_MULTI -> 2
                                    else -> 1
                                },
                                onItemClick = { index ->
                                    settingsViewModel.setFloatingLyricsLineMode(
                                        when (index) {
                                            0 -> UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_SINGLE
                                            2 -> UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_MULTI
                                            else -> UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_DOUBLE
                                        }
                                    )
                                }
                            )
                        }
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_floating_lyrics_lock_title),
                            subtitle = stringResource(R.string.setcat_floating_lyrics_lock_subtitle),
                            checked = uiState.floatingLyricsLocked,
                            onCheckedChange = { settingsViewModel.setFloatingLyricsLocked(it) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Lock,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_floating_lyrics_edge_snap_title),
                            subtitle = stringResource(R.string.setcat_floating_lyrics_edge_snap_subtitle),
                            checked = uiState.floatingLyricsEdgeSnap,
                            onCheckedChange = { settingsViewModel.setFloatingLyricsEdgeSnap(it) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.UnfoldMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.setcat_floating_lyrics_animation_title),
                            subtitle = stringResource(R.string.setcat_floating_lyrics_animation_subtitle),
                            checked = uiState.floatingLyricsAnimations,
                            onCheckedChange = { settingsViewModel.setFloatingLyricsAnimations(it) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Animation,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )

                        // 整体大小：字号、间距、控制条按钮、贴边箭头一起缩放
                        var floatingScaleDraft by remember {
                            mutableStateOf(uiState.floatingLyricsScale.toFloat())
                        }
                        LaunchedEffect(uiState.floatingLyricsScale) {
                            floatingScaleDraft = uiState.floatingLyricsScale.toFloat()
                        }
                        SliderSettingsItem(
                            label = stringResource(R.string.setcat_floating_lyrics_scale_title),
                            value = floatingScaleDraft,
                            valueRange = UserPreferencesRepository.FLOATING_LYRICS_SCALE_MIN.toFloat()..
                                UserPreferencesRepository.FLOATING_LYRICS_SCALE_MAX.toFloat(),
                            // 80~140、步长 10 → 端点之间还有 5 个离散值
                            steps = (UserPreferencesRepository.FLOATING_LYRICS_SCALE_MAX -
                                UserPreferencesRepository.FLOATING_LYRICS_SCALE_MIN) / 10 - 1,
                            // 拖动过程中就生效：悬浮窗会实时跟着变大变小，
                            // 否则松手才看到效果，等于闭着眼调。滑块是离散档位，写入次数很有限。
                            onValueChange = {
                                floatingScaleDraft = it
                                settingsViewModel.setFloatingLyricsScale(it.roundToInt())
                            },
                            valueText = { "${it.roundToInt()}%" }
                        )

                        // 背景不透明度：0 = 完全透明，只留文字
                        var floatingAlphaDraft by remember {
                            mutableStateOf(
                                uiState.floatingLyricsBackgroundAlpha.toFloat()
                            )
                        }
                        LaunchedEffect(uiState.floatingLyricsBackgroundAlpha) {
                            floatingAlphaDraft =
                                uiState.floatingLyricsBackgroundAlpha.toFloat()
                        }
                        SliderSettingsItem(
                            label = stringResource(R.string.setcat_floating_lyrics_bg_alpha_title),
                            value = floatingAlphaDraft,
                            valueRange = 0f..100f,
                            // 0~100、步长 10 → 9 个中间点
                            steps = 9,
                            // 同样拖动中实时生效，方便边调边看
                            onValueChange = {
                                floatingAlphaDraft = it
                                settingsViewModel.setFloatingLyricsBackgroundAlpha(
                                    it.roundToInt()
                                )
                            },
                            valueText = { "${it.roundToInt()}%" }
                        )
                    }
                }

                SettingsSubsection(title = stringResource(R.string.setcat_lyrics_screen)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_immersive_lyrics_title),
                        subtitle = stringResource(R.string.setcat_immersive_lyrics_subtitle),
                        checked = uiState.immersiveLyricsEnabled,
                        onCheckedChange = { settingsViewModel.setImmersiveLyricsEnabled(it) },
                        leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
                    )

                    AnimatedVisibility(
                        visible = uiState.immersiveLyricsEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        // ⚡ 必须显式 Column：AnimatedVisibility 内的多个子项会被同一布局叠放，
                        //    之前「自动隐藏延时」被下面的开关盖住就是这个原因；
                        //    间距与子节保持一致（2dp），避免两项贴在一起
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ThemeSelectorItem(
                                label = stringResource(R.string.setcat_auto_hide_delay_label),
                                description = stringResource(R.string.setcat_auto_hide_delay_desc),
                                options = mapOf(
                                    "3000" to stringResource(R.string.setcat_lyrics_delay_3s),
                                    "4000" to stringResource(R.string.setcat_lyrics_delay_4s),
                                    "5000" to stringResource(R.string.setcat_lyrics_delay_5s),
                                    "6000" to stringResource(R.string.setcat_lyrics_delay_6s)
                                ),
                                selectedKey = uiState.immersiveLyricsTimeout.toString(),
                                onSelectionChanged = { settingsViewModel.setImmersiveLyricsTimeout(it.toLong()) },
                                leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.secondary) }
                            )

                            // ⚡ 沉浸时不放大歌词字体（默认关闭 = 沉浸时字号仍 ×1.25）
                            SwitchSettingItem(
                                title = stringResource(R.string.setcat_immersive_keep_font_size_title),
                                subtitle = stringResource(R.string.setcat_immersive_keep_font_size_subtitle),
                                checked = uiState.immersiveLyricsKeepFontSize,
                                onCheckedChange = { settingsViewModel.setImmersiveLyricsKeepFontSize(it) },
                                leadingIcon = { Icon(Icons.Rounded.FormatSize, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                        }
                    }

                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_show_lyrics_track_info_title),
                        subtitle = stringResource(R.string.setcat_show_lyrics_track_info_subtitle),
                        checked = uiState.showLyricsTrackInfo,
                        onCheckedChange = { settingsViewModel.setShowLyricsTrackInfo(it) },
                        leadingIcon = { Icon(painterResource(R.drawable.rounded_music_note_24), null, tint = MaterialTheme.colorScheme.secondary) }
                    )
                }

            // 底部避让迷你播放条
            Spacer(modifier = Modifier.height(MiniPlayerHeight + 16.dp))
        }
    }
}

// ────────────────────────────── 顶栏 ──────────────────────────────

@Composable
private fun PlayerLyricsAppearanceHeader(onBackClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setcat_player_lyrics_appearance_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.setcat_player_lyrics_appearance_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
