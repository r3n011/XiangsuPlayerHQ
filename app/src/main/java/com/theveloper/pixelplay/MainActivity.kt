package com.theveloper.pixelplay

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.os.Bundle
import android.os.Trace
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.CallSuper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.github.GitHubAnnouncementPropertiesService
import com.theveloper.pixelplay.data.github.PlayStoreAnnouncementRemoteConfig
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.sanitizeNavBarCornerRadius
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.AllFilesAccessDialog
import com.theveloper.pixelplay.presentation.components.AppSidebarDrawer
import com.theveloper.pixelplay.presentation.components.CrashReportDialog
import com.theveloper.pixelplay.presentation.components.DismissUndoBar
import com.theveloper.pixelplay.presentation.components.DrawerDestination
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlayerInternalNavigationBar
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementDefaults
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementDialog
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementUiModel
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheetV2
import com.theveloper.pixelplay.presentation.components.calculatePlayerSheetCollapsedTargetY
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarSurfaceHeight
import com.theveloper.pixelplay.presentation.components.sanitizeNavigationBarBottomInset
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.TabContentHost
import com.theveloper.pixelplay.presentation.screens.SetupScreen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.NoOpHapticFeedback
import com.theveloper.pixelplay.utils.CrashLogData
import javax.annotation.concurrent.Immutable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration


@Immutable
data class BottomNavItem(
    val label: String,
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int? = null,
    @DrawableRes val selectedIconResId: Int? = null,
    val imageVectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val screen: Screen
)

private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var syncManager: SyncManager
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // MD3 Optimization: Release Splash Screen immediately to render UI skeleton.
        // Data loading is handled via optimistic UI and smooth transitions.
        splashScreen.setKeepOnScreenCondition { false }

        // LEER SEÑAL DE BENCHMARK
        val isBenchmarkMode = intent.getBooleanExtra("is_benchmark", false)
        val shouldBenchmarkRebuildDatabase =
            isBenchmarkMode && intent.getBooleanExtra("benchmark_rebuild_database", false)
        Log.i(
            "PixelPlayBenchmark",
            "onCreate benchmark=$isBenchmarkMode rebuildDatabase=$shouldBenchmarkRebuildDatabase"
        )
        if (shouldBenchmarkRebuildDatabase) {
            lifecycleScope.launch {
                userPreferencesRepository.setInitialSetupDone(true)
                Log.i("PixelPlayBenchmark", "Enqueueing benchmark database rebuild")
                syncManager.rebuildDatabase()
                delay(1_500L)
                playerViewModel.prepareBenchmarkPlayerFromLibrary()
            }
        }

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val showScrollbar by userPreferencesRepository.showScrollbarFlow.collectAsStateWithLifecycle(initialValue = true)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            // Crash report dialog state
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            
            // Permissions Logic
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            // Determine if we need to show Setup based on completion OR missing permissions
            val permissionsValid = permissionState.allPermissionsGranted
            val showSetupScreen = remember(isSetupComplete, permissionsValid, isBenchmarkMode) {
                when {
                    isBenchmarkMode -> false
                    isSetupComplete == null -> null
                    else -> !isSetupComplete!! || !permissionsValid
                }
            }

            // Sync Trigger: When we are NOT showing setup (meaning permissions are good and setup is done)
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                     LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                     mainViewModel.startSync()
                }
            }

            // Check for crash log when app starts
            LaunchedEffect(Unit) {
                if (!isBenchmarkMode && CrashHandler.hasCrashLog()) {
                    crashLogData = CrashHandler.getCrashLog()
                    showCrashReportDialog = true
                }
            }

            CompositionLocalProvider(LocalShowScrollbar provides showScrollbar) {
                PixelPlayTheme(
                    darkTheme = useDarkTheme
                ) {
                    var contentVisible by remember { mutableStateOf(false) }
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (contentVisible) 1f else 0f,
                        animationSpec = tween(600, easing = LinearOutSlowInEasing),
                        label = "AppContentAlpha"
                    )

                    LaunchedEffect(Unit) {
                        // Delay slightly to ensure first frame layout is done behind Splash
                        delay(100)
                        contentVisible = true
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, 
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (showSetupScreen == null) {
                            SetupGateLoadingScreen()
                        } else {
                            AnimatedContent(
                                targetState = showSetupScreen,
                                transitionSpec = {
                                    if (targetState) {
                                        // Transition to Setup
                                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                    } else {
                                        // Transition from Setup to Main App
                                        scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                    }
                                },
                                label = "SetupTransition"
                            ) { shouldShowSetup ->
                                if (shouldShowSetup) {
                                    SetupScreen(onSetupComplete = {
                                        // Repository-backed setup completion updates the gate automatically.
                                    })
                                } else {
                                    MainAppContent(playerViewModel, mainViewModel)
                                }
                            }
                        }

                        // Show crash report dialog if needed
                        if (showCrashReportDialog && crashLogData != null) {
                            CrashReportDialog(
                                crashLog = crashLogData!!,
                                onDismiss = {
                                    CrashHandler.clearCrashLog()
                                    crashLogData = null
                                    showCrashReportDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            // Handle shuffle all shortcut / tile
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                android.util.Log.d("TileDebug", "handleIntent: ACTION_SHUFFLE_ALL received")
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null // Clear action to prevent re-triggering
            }
            
            // Handle playlist shortcut
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.theveloper.pixelplay.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.theveloper.pixelplay.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.theveloper.pixelplay.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("MainActivity", "Unable to persist URI permission for $uri", securityException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        android.util.Log.w("MainActivity", "Persistable URI permission not granted for $uri", illegalArgumentException)
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    private fun openExternalUrl(url: String) {
        // Defense in depth: the announcement URL is fetched from a remote
        // properties file on GitHub. If that file is ever tampered with, we
        // must not let it launch arbitrary intents (`intent://...`,
        // `javascript:`, custom schemes, etc.). Allow only the Play Store host.
        val parsed = runCatching { url.toUri() }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        val isPlayStore = scheme == "https" &&
            (host == "play.google.com" || host == "market.android.com")
        if (!isPlayStore) {
            LogUtils.w(this, "Refusing to open non-Play-Store announcement URL: $url")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, parsed)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            LogUtils.w(this, "No activity available to open URL: $url")
        }
    }

    private fun PlayStoreAnnouncementRemoteConfig.toUiModel(context: Context): PlayStoreAnnouncementUiModel {
        val fallback = PlayStoreAnnouncementDefaults.localizedTemplate(context)
        return fallback.copy(
            enabled = enabled,
            playStoreUrl = playStoreUrl ?: fallback.playStoreUrl,
            title = title ?: fallback.title,
            body = body ?: fallback.body,
            primaryActionLabel = primaryActionLabel ?: fallback.primaryActionLabel,
            dismissActionLabel = dismissActionLabel ?: fallback.dismissActionLabel,
            linkPendingMessage = linkPendingMessage ?: fallback.linkPendingMessage,
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SetupGateLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Preparing setup…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsStateWithLifecycle()
        val hasCompletedInitialSync by mainViewModel.hasCompletedInitialSync.collectAsStateWithLifecycle()
        val syncProgress by mainViewModel.syncProgress.collectAsStateWithLifecycle()
        
        // isMediaControllerReady used below for playlist navigation gate
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        // Observe pending playlist navigation
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            // Only process if we have a new playlist ID that hasn't been processed yet
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                // Wait for navigation graph to be ready (retry with delay)
                var success = false
                var attempts = 0
                while (!success && attempts < 50) { // 5 seconds max
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                // Reset so the same playlist can be opened again
                processedPlaylistId = null
            }
        }

        // Estado para controlar si el indicador de carga puede mostrarse después de un delay
        var canShowLoadingIndicator by remember { mutableStateOf(false) }
        // Track when the loading indicator was first shown for minimum display time
        var loadingShownTimestamp by remember { mutableStateOf(0L) }
        val minimumDisplayDuration = 1500L // Show loading for at least 1.5 seconds

        val shouldPotentiallyShowLoading = isSyncing && isLibraryEmpty && !hasCompletedInitialSync

        LaunchedEffect(shouldPotentiallyShowLoading) {
            if (shouldPotentiallyShowLoading) {
                // Espera un breve período antes de permitir que se muestre el indicador de carga
                // Ajusta este valor según sea necesario (por ejemplo, 300-500 ms)
                delay(300L)
                // Vuelve a verificar la condición después del delay,
                // ya que el estado podría haber cambiado.
                if (mainViewModel.isSyncing.value && mainViewModel.isLibraryEmpty.value) {
                    canShowLoadingIndicator = true
                    loadingShownTimestamp = System.currentTimeMillis()
                }
            } else {
                // Ensure minimum display time before hiding
                if (canShowLoadingIndicator && loadingShownTimestamp > 0) {
                    val elapsed = System.currentTimeMillis() - loadingShownTimestamp
                    val remaining = minimumDisplayDuration - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                }
                canShowLoadingIndicator = false
                loadingShownTimestamp = 0L
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (canShowLoadingIndicator) {
                LoadingOverlay(syncProgress)
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.string.nav_bar_home, R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, screen = Screen.Home),
                BottomNavItem("Search", R.string.nav_bar_search, R.drawable.rounded_search_24, R.drawable.rounded_search_24, screen = Screen.Search),
                BottomNavItem("Library", R.string.nav_bar_library, R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, screen = Screen.Library),
                BottomNavItem("Settings", R.string.settings_top_bar_title, R.drawable.rounded_settings_24, R.drawable.rounded_settings_24, screen = Screen.Settings)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var isSearchBarActive by remember { mutableStateOf(false) }

        // ⚡ 在外层收集一次 currentSongId,让底部导航栏和 player sheet 都能共享这个稳定值
        // 避免在 bottomBar lambda 内部每次重组时都执行 collectAsStateWithLifecycle
        val currentSongIdForUI by remember {
            playerViewModel.stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Accounts.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.RecentlyPlayed.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route,
                Screen.About.route,
                Screen.Stats.route,
                Screen.EditTransition.route,
                Screen.Experimental.route,
                Screen.ArtistSettings.route,
                Screen.SettingsCategory.route,
                Screen.DelimiterConfig.route,
                Screen.PaletteStyle.route,
                Screen.DeviceCapabilities.route,
                Screen.EasterEgg.route,
                Screen.WordDelimiterConfig.route
            )
        }
        val isPlayerExpanded by remember {
            derivedStateOf { playerViewModel.playerContentExpansionFraction.value > 0.01f }
        }
        val routeHidden by remember(currentRoute) {
            derivedStateOf {
                currentRoute?.let { route ->
                    routesWithHiddenNavigationBar.any { hiddenRoute ->
                        if (hiddenRoute.contains("{")) {
                            route.startsWith(hiddenRoute.substringBefore("{"))
                        } else {
                            route == hiddenRoute
                        }
                    }
                } ?: false
            }
        }
        val isSearchActive = currentRoute == Screen.Search.route && isSearchBarActive
        // ⚡ 横屏 NavigationRail 隐藏逻辑:只在搜索激活时隐藏,在常规内容页面(每日合集、最近播放、听歌统计等)保持可见
        // ⚡ 竖屏底部导航栏逻辑:仅在搜索激活或路由在隐藏列表中时隐藏
        //   播放器展开时不隐藏底部导航栏:
        //   - 播放器容器已移到最外层 Box(z-index 高于 Scaffold),会自然覆盖在导航栏上面
        //   - 避免"播放器展开 + 导航栏收起"两个动画同时进行导致的卡顿
        val shouldHideBottomNavBar by remember(isSearchActive, routeHidden, isLandscape) {
            derivedStateOf {
                if (isLandscape) false
                else isSearchActive || routeHidden
            }
        }
        val shouldHideNavigationRail by remember(isSearchActive, isLandscape) {
            derivedStateOf {
                if (!isLandscape) false
                else isSearchActive
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
        val useSmoothCorners by playerViewModel.useSmoothCorners.collectAsStateWithLifecycle()
        val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()
        val predictiveBackCollapseFraction by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
        val rootView = LocalView.current
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }

        val systemNavBarInset = sanitizeNavigationBarBottomInset(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
            if (systemNavBarInset > 30.dp) 14.dp else systemNavBarInset
        } else {
            0.dp
        }
        // ⚡ 关键优化：导航栏 padding/corner radius 这些值不应该用动画（动画会每帧触发重组）。
        // 它们只随用户设置/导航条可见性改变（低频事件），直接赋值即可。
        val bottomBarPadding = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else systemNavBarInset
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
        val navBarOccupiedHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

        // ⚡ 关键优化：分离底部导航栏和 NavigationRail 的动画,避免互相干扰
        // 底部导航栏动画 - 仅用于竖屏,考虑 player 展开状态
        val bottomNavBarProgressState: androidx.compose.runtime.State<Float> = animateFloatAsState(
            targetValue = if (shouldHideBottomNavBar) 0f else 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing
            ),
            label = "BottomNavBarVisibility"
        )
        // NavigationRail 动画 - 仅用于横屏,不考虑 player 展开状态(Rail 在左侧,不与 sheet 冲突)
        val navRailProgressState: androidx.compose.runtime.State<Float> = animateFloatAsState(
            targetValue = if (shouldHideNavigationRail) 0f else 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing
            ),
            label = "NavRailVisibility"
        )

        // mini-player 底部边距:使用稳定值,不依赖动画值,避免 sheetCollapsedTargetY 每帧变化
        // 动画由 sheet 内部的 SheetMotionController 处理
        val miniPlayerBottomMarginDp = if (isLandscape) {
            maxOf(systemNavBarInset, 8.dp)
        } else {
            if (shouldHideBottomNavBar) systemNavBarInset else navBarOccupiedHeight
        }

        // NavigationRail 的水平 padding:使用稳定值,不依赖动画值,避免位置抖动
        val navRailPaddingDp = if (isLandscape) {
            // 横屏时,始终给内容留出 80dp 的 Rail 空间
            // 不使用动画值,避免 sheetCollapsedTargetY 每帧变化
            80.dp
        } else {
            0.dp
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val announcementService = remember { GitHubAnnouncementPropertiesService() }
        val context = LocalContext.current
        var playStoreAnnouncement by remember {
            mutableStateOf(PlayStoreAnnouncementDefaults.localizedTemplate(context))
        }
        var showPlayStoreAnnouncement by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (PlayStoreAnnouncementDefaults.LOCAL_PREVIEW_ENABLED) {
                playStoreAnnouncement = PlayStoreAnnouncementDefaults.hardcodedPreview(this@MainActivity)
                showPlayStoreAnnouncement = true
                return@LaunchedEffect
            }

            announcementService.fetchPlayStoreAnnouncement()
                .onSuccess { remoteConfig ->
                    val resolvedAnnouncement = remoteConfig.toUiModel(this@MainActivity)
                    playStoreAnnouncement = resolvedAnnouncement
                    showPlayStoreAnnouncement = resolvedAnnouncement.enabled
                }
                .onFailure { throwable ->
                    LogUtils.w(
                        this@MainActivity,
                        "Remote announcement unavailable. Keeping popup disabled. ${throwable.message ?: ""}",
                    )
                }
        }

        LaunchedEffect(userPreferencesRepository) {
            userPreferencesRepository.clearDeprecatedPlayerSheetPreference()
        }

        CompositionLocalProvider(
            LocalAppHapticsConfig provides appHapticsConfig,
            LocalHapticFeedback provides scopedHapticFeedback
        ) {
            // Auto-close sidebar drawer when player expands
            LaunchedEffect(isPlayerExpanded) {
                if (isPlayerExpanded && drawerState.isOpen) {
                    drawerState.close()
                }
            }
            var showChangelogBottomSheet by remember { mutableStateOf(false) }
        var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
        var showStreamingProviderSheet by remember { mutableStateOf(false) }

        AppSidebarDrawer(
                drawerState = drawerState,
                selectedRoute = currentRoute ?: Screen.Home.route,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination) {
                        DrawerDestination.Home -> navController.navigateSafely(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                        DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                        DrawerDestination.Telegram -> {
                            showStreamingProviderSheet = true
                        }
                        DrawerDestination.Changelog -> {
                            showChangelogBottomSheet = true
                        }
                        DrawerDestination.Beta -> {
                            showBetaInfoBottomSheet = true
                        }
                    }
                }
        ) {

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (isLandscape) {
                        // ⚡ 横屏 NavigationRail:使用独立的 Composable,通过 Stable 参数提升重组性能
                        MainNavigationRail(
                            navController = navController,
                            navItems = commonNavItems,
                            currentRoute = currentRoute,
                            navRailProgressState = navRailProgressState
                        )
                    }

                    // 横屏时内容区域用 padding 限制宽度，确保内容不会被挤出屏幕。
                    // navRailPaddingDp 是稳定值（0dp/80dp，不读动画 State，用 padding 是安全的
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = navRailPaddingDp)
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (!isLandscape) {
                                    MainBottomNavigationBar(
                                        playerViewModel = playerViewModel,
                                        navController = navController,
                                        navItems = commonNavItems,
                                        currentRoute = currentRoute,
                                        currentSongId = currentSongIdForUI,
                                        navBarStyle = navBarStyle,
                                        navBarCompactMode = navBarCompactMode,
                                        navBarCornerRadius = navBarCornerRadius,
                                        useSmoothCorners = useSmoothCorners,
                                        isMiniPlayerDismissing = isMiniPlayerDismissing,
                                        bottomBarPadding = bottomBarPadding,
                                        navBarHeight = navBarHeight,
                                        navBarOccupiedHeight = navBarOccupiedHeight,
                                        horizontalPadding = horizontalPadding,
                                        bottomNavBarProgressState = bottomNavBarProgressState
                                    )
                                }
                            }
                        ) { innerPadding ->
                            val expansionFractionProvider = remember(playerViewModel.playerContentExpansionFraction) {
                                { playerViewModel.playerContentExpansionFraction.value }
                            }
                            val blurEffectCache = remember { BlurEffectCache() }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            if (disableBlurAllOver) {
                                                renderEffect = null
                                            } else {
                                                val expansion = expansionFractionProvider()
                                                val fraction = (expansion * (1f - predictiveBackCollapseFraction)).coerceIn(0f, 1f)
                                                if (fraction <= 0.01f) {
                                                    renderEffect = null
                                                } else {
                                                    val quantizedBlurPx = (fraction * 60f / 4f).roundToInt() * 4f
                                                    renderEffect = blurEffectCache.get(quantizedBlurPx.toFloat())
                                                }
                                            }
                                        }
                                    }
                            ) {
                                TabContentHost(
                                    currentRoute = currentRoute,
                                    paddingValues = innerPadding,
                                    playerViewModel = playerViewModel,
                                    navController = navController,
                                    onSearchBarActiveChange = { isSearchBarActive = it },
                                    onOpenSidebar = { scope.launch { drawerState.open() } }
                                )
                                AppNavigation(
                                    playerViewModel = playerViewModel,
                                    navController = navController,
                                    userPreferencesRepository = userPreferencesRepository
                                )
                            }
                        }
                    }

                    // ⚡ 播放器容器移到最外层 Box，全屏显示（与 NavigationRail 同级）
                    // 横屏时播放器展开态覆盖整个屏幕（包括 NavigationRail 区域），折叠态由 SheetVisualState
                    // 内部的 navRailPadding 让 mini-player 位于内容区域右侧显示
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                val density = LocalDensity.current
                val containerHeight = this.maxHeight
                val screenHeightPx = remember(containerHeight, density) {
                    with(density) { containerHeight.toPx() }
                }

                val showPlayerContentInitially by remember {
                    playerViewModel.stablePlayerState
                        .map { it.currentSong?.id != null }
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue = false)
                val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                val shouldHideMiniPlayer by remember(currentRoute) {
                    derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                }

                val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                // sheet 位置只根据稳定的布局值确定，不依赖动画值（动画在 sheet 内部处理）
                val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                val bottomMarginPx = with(density) { miniPlayerBottomMarginDp.toPx() }
                val sheetCollapsedTargetY = calculatePlayerSheetCollapsedTargetY(
                    containerHeightPx = screenHeightPx,
                    collapsedContentHeightPx = totalSheetHeightWhenContentCollapsedPx,
                    bottomMarginPx = bottomMarginPx,
                    bottomSpacerPx = spacerPx
                )

                // ⚡ isExpandedOrExpanding：只在 playerContentExpansionFraction 跨过 0.01 时改变状态
                // derivedStateOf 会把其他帧的变化都吞掉，不触发重组
                val isExpandedOrExpanding by remember {
                    derivedStateOf { playerViewModel.playerContentExpansionFraction.value > 0.01f }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isExpandedOrExpanding,
                    enter = fadeIn(animationSpec = tween(durationMillis = 350)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 350)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(
                                    alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.35f else 0.6f
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    playerViewModel.collapsePlayerSheet()
                                }
                            }
                    )
                }

                // ⚡ mini-player 裁剪高度:根据播放器展开状态决定
                // - 展开态(isExpandedOrExpanding=true):containerHeight(全屏高度)
                // - 折叠态:sheetCollapsedTargetY + miniH(mini-player 底部位置)
                // 这确保播放器展开时能覆盖整个屏幕,避免底部导航栏区域显示为黑色
                val collapsedClipHeight = remember(
                    showPlayerContentInitially,
                    shouldHideMiniPlayer,
                    currentRoute
                ) {
                    val shouldShowMiniPlayer = showPlayerContentInitially && !shouldHideMiniPlayer &&
                            currentRoute !in setOf(Screen.NavBarCrRad.route)
                    if (shouldShowMiniPlayer) {
                        with(density) {
                            val miniH = MiniPlayerHeight.toPx()
                            (sheetCollapsedTargetY + miniH).toDp().coerceAtLeast(0.dp)
                        }
                    } else {
                        containerHeight
                    }
                }
                val miniPlayerClipHeight = if (isExpandedOrExpanding) containerHeight else collapsedClipHeight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(miniPlayerClipHeight)
                        .clipToBounds()
                ) {
                    // ⚡ isNavBarHidden 使用稳定的布尔值(不读动画 State),
                    // 避免导航切换动画期间每帧触发重组。
                    // 圆角/边距等用稳定值足矣,动画由 sheet 内部的 SheetMotionController 处理。
                    val isNavBarHiddenValue = if (isLandscape) shouldHideNavigationRail else shouldHideBottomNavBar
                    UnifiedPlayerSheetV2(
                        playerViewModel = playerViewModel,
                        sheetCollapsedTargetY = sheetCollapsedTargetY,
                        collapsedStateHorizontalPadding = horizontalPadding,
                        hideMiniPlayer = shouldHideMiniPlayer,
                        containerHeight = containerHeight,
                        navController = navController,
                        isNavBarHidden = isNavBarHiddenValue,
                        navRailPadding = navRailPaddingDp
                    )
                }

                val dismissUndoBarSlice by remember {
                    playerViewModel.playerUiState
                        .map { state ->
                            DismissUndoBarSlice(
                                isVisible = state.showDismissUndoBar,
                                durationMillis = state.undoBarVisibleDuration
                            )
                        }
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                val onUndoDismissPlaylist = remember(playerViewModel) {
                    { playerViewModel.undoDismissPlaylist() }
                }
                val onCloseDismissUndoBar = remember(playerViewModel) {
                    { playerViewModel.hideDismissUndoBar() }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = dismissUndoBarSlice.isVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = navRailPaddingDp)
                        .padding(bottom = miniPlayerBottomMarginDp + MiniPlayerBottomSpacer)
                        .padding(horizontal = horizontalPadding)
                ) {
                    DismissUndoBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MiniPlayerHeight)
                            .padding(horizontal = 14.dp),
                        onUndo = onUndoDismissPlaylist,
                        onClose = onCloseDismissUndoBar,
                        durationMillis = dismissUndoBarSlice.durationMillis
                    )
                }

                if (showPlayStoreAnnouncement) {
                    PlayStoreAnnouncementDialog(
                        announcement = playStoreAnnouncement,
                        onDismiss = { showPlayStoreAnnouncement = false },
                        onOpenPlayStore = { url ->
                            showPlayStoreAnnouncement = false
                            openExternalUrl(url)
                        }
                    )
                }
            }
        }
    }
Trace.endSection()
    }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun LoadingOverlay(syncProgress: SyncProgress) {
        // Animate progress smoothly instead of jumping in steps
        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = syncProgress.progress,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "SyncProgressAnimation"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing your library...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                if (syncProgress.hasProgress) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun MainBottomNavigationBar(
        playerViewModel: PlayerViewModel,
        navController: NavHostController,
        navItems: kotlinx.collections.immutable.ImmutableList<BottomNavItem>,
        currentRoute: String?,
        currentSongId: Any?,
        navBarStyle: String,
        navBarCompactMode: Boolean,
        navBarCornerRadius: Int,
        useSmoothCorners: Boolean,
        isMiniPlayerDismissing: Boolean,
        bottomBarPadding: androidx.compose.ui.unit.Dp,
        navBarHeight: androidx.compose.ui.unit.Dp,
        navBarOccupiedHeight: androidx.compose.ui.unit.Dp,
        horizontalPadding: androidx.compose.ui.unit.Dp,
        bottomNavBarProgressState: androidx.compose.runtime.State<Float>
    ) {
        // 使用 Stable 参数,Compose 可以在参数不变时跳过重组
        val showPlayerContentArea = currentSongId != null
        val navBarElevation = 3.dp

        val densityLocal = LocalDensity.current
        val navBarCornerRadiusStaticPx = remember(navBarCornerRadius, densityLocal) {
            with(densityLocal) { navBarCornerRadius.dp.toPx() }
        }
        val playerTopCornerTargetPx = remember(densityLocal) { with(densityLocal) { 26.dp.toPx() } }

        var componentHeightPx by remember { mutableStateOf(0) }
        val shadowOverflowPx = remember(navBarElevation, densityLocal) {
            with(densityLocal) { (navBarElevation * 8).toPx() }
        }
        val bottomBarPaddingPx = remember(bottomBarPadding, densityLocal) {
            with(densityLocal) { bottomBarPadding.toPx() }
        }
        val navBarElevationPx = remember(navBarElevation, densityLocal) {
            with(densityLocal) { navBarElevation.toPx() }
        }
        val navBarShapeCache = remember { NavBarShapeCache() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarOccupiedHeight)
                .clipToBounds()
        ) {
            val onSearchIconDoubleTap = remember(playerViewModel) {
                { playerViewModel.onSearchNavIconDoubleTapped() }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarPadding)
                    .onSizeChanged { componentHeightPx = it.height }
                    .graphicsLayer {
                        val expansionHide = if (showPlayerContentArea) {
                            playerViewModel.playerContentExpansionFraction.value.coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val routeHide = (1f - bottomNavBarProgressState.value).coerceIn(0f, 1f)
                        val hideFraction = maxOf(expansionHide, routeHide)
                        translationY = (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * hideFraction
                        alpha = 1f
                    }
                    .height(navBarHeight)
                    .padding(horizontal = horizontalPadding)
                    .graphicsLayer {
                        val fraction = playerViewModel.playerContentExpansionFraction.value
                        val safeFraction = fraction.coerceIn(0f, 1f)
                        val topPx = when {
                            navBarStyle == NavBarStyle.DEFAULT -> {
                                val target = if (showPlayerContentArea && !isMiniPlayerDismissing) {
                                    playerTopCornerTargetPx
                                } else {
                                    navBarCornerRadiusStaticPx
                                }
                                val transitionFraction = (safeFraction / 0.2f).coerceIn(0f, 1f)
                                androidx.compose.ui.util.lerp(
                                    navBarCornerRadiusStaticPx,
                                    target,
                                    transitionFraction
                                )
                            }
                            navBarStyle == NavBarStyle.FULL_WIDTH -> {
                                androidx.compose.ui.util.lerp(
                                    navBarCornerRadiusStaticPx,
                                    playerTopCornerTargetPx,
                                    safeFraction
                                )
                            }
                            showPlayerContentArea -> {
                                val transitionFraction = (fraction / 0.2f).coerceIn(0f, 1f)
                                androidx.compose.ui.util.lerp(
                                    navBarCornerRadiusStaticPx,
                                    playerTopCornerTargetPx,
                                    transitionFraction
                                )
                            }
                            else -> navBarCornerRadiusStaticPx
                        }
                        val bottomPx = when (navBarStyle) {
                            NavBarStyle.FULL_WIDTH -> 0f
                            else -> navBarCornerRadiusStaticPx
                        }
                        shape = navBarShapeCache.get(this, topPx, bottomPx, useSmoothCorners)
                        clip = true
                        shadowElevation = navBarElevationPx
                    },
                color = NavigationBarDefaults.containerColor
            ) {
                PlayerInternalNavigationBar(
                    navController = navController,
                    navItems = navItems,
                    currentRoute = currentRoute,
                    navBarStyle = navBarStyle,
                    compactMode = navBarCompactMode,
                    bottomBarPadding = bottomBarPadding,
                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun MainNavigationRail(
        navController: NavHostController,
        navItems: kotlinx.collections.immutable.ImmutableList<BottomNavItem>,
        currentRoute: String?,
        navRailProgressState: androidx.compose.runtime.State<Float>
    ) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    val visibility = navRailProgressState.value
                    alpha = visibility
                    translationX = (1f - visibility) * -80.dp.toPx()
                }
        ) {
            navItems.forEach { item ->
                val selected = currentRoute != null && currentRoute == item.screen.route
                // ⚡ onClick lambda 用 remember(item.route, navController) 缓存,
                // currentRoute 变化时不会重建 onClick(因为 key 不包含 currentRoute)
                val onClickLambda: () -> Unit = remember(item.screen.route, navController) {
                    {
                        navController.navigateSafely(item.screen.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                NavigationRailItem(
                    selected = selected,
                    onClick = onClickLambda,
                    icon = {
                        when {
                            item.imageVectorIcon != null -> Icon(
                                imageVector = item.imageVectorIcon,
                                contentDescription = null
                            )
                            item.selectedIconResId != null && selected -> Icon(
                                painter = painterResource(id = item.selectedIconResId),
                                contentDescription = null
                            )
                            item.iconResId != null -> Icon(
                                painter = painterResource(id = item.iconResId),
                                contentDescription = null
                            )
                        }
                    },
                    label = {
                        androidx.compose.material3.Text(stringResource(item.labelResId))
                    }
                )
            }
        }
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()

        if (intent.getBooleanExtra("is_benchmark", false)) {
            // Benchmark mode no longer loads dummy data - uses real library data instead
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    override fun onResume() {
        super.onResume()
    }


}

/**
 * Caches the (expensive) RenderEffect Java object so we don't allocate a new
 * blur every animation frame. The radius is quantized at the call site, so this
 * only rebuilds ~25 times across the whole expand animation instead of 60+/sec.
 */
private class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    fun get(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (radiusPx <= 0f) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, AndroidShader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        return cached
    }
}

/**
 * Returns a cached Shape instance for a quantized (top, bottom) radius pair.
 * Because the instance identity is stable while the radii don't move past a
 * sub-pixel threshold, the graphics layer reuses its cached Outline between
 * frames and only re-clips when the radius actually changes.
 */
private class NavBarShapeCache {
    private var lastTopPx: Float = Float.NaN
    private var lastBottomPx: Float = Float.NaN
    private var lastSmooth: Boolean = true
    private var cached: androidx.compose.ui.graphics.Shape = RectangleShape

    fun get(
        density: androidx.compose.ui.unit.Density,
        topPx: Float,
        bottomPx: Float,
        smooth: Boolean
    ): androidx.compose.ui.graphics.Shape {
        if (smooth == lastSmooth &&
            !lastTopPx.isNaN() &&
            kotlin.math.abs(topPx - lastTopPx) < 0.5f &&
            kotlin.math.abs(bottomPx - lastBottomPx) < 0.5f
        ) {
            return cached
        }
        lastTopPx = topPx
        lastBottomPx = bottomPx
        lastSmooth = smooth
        cached = with(density) {
            DynamicSmoothCornerShape(
                useSmoothCorners = smooth,
                topRadius = topPx.toDp(),
                bottomRadius = bottomPx.toDp()
            )
        }
        return cached
    }
}

/**
 * Fixed-radius corner shape. Swaps AbsoluteSmoothCornerShape for a plain
 * RoundedCornerShape when smooth corners are disabled in settings. The radius
 * values are identical in both branches, so the animated radius behavior is
 * unchanged regardless of which delegate is active. The resulting Outline is
 * cached per (size, layoutDirection) so repeated draws are cheap.
 */
private class DynamicSmoothCornerShape(
    private val useSmoothCorners: Boolean,
    private val topRadius: androidx.compose.ui.unit.Dp,
    private val bottomRadius: androidx.compose.ui.unit.Dp
) : androidx.compose.ui.graphics.Shape {

    private var cachedSize: androidx.compose.ui.geometry.Size =
        androidx.compose.ui.geometry.Size.Unspecified
    private var cachedLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        cachedOutline?.let {
            if (cachedSize == size && cachedLayoutDirection == layoutDirection) return it
        }

        val delegate: androidx.compose.ui.graphics.Shape = if (useSmoothCorners) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topRadius,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = topRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = bottomRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = bottomRadius,
                smoothnessAsPercentBR = 60
            )
        } else {
            RoundedCornerShape(
                topStart = topRadius,
                topEnd = topRadius,
                bottomEnd = bottomRadius,
                bottomStart = bottomRadius
            )
        }

        return delegate.createOutline(size, layoutDirection, density).also {
            cachedSize = size
            cachedLayoutDirection = layoutDirection
            cachedOutline = it
        }
    }
}
