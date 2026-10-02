package com.theveloper.pixelplay

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog

// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.ViewTreeObserver
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Trace
import android.provider.Settings
import android.util.Log
import android.widget.Toast
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.theveloper.pixelplay.data.preferences.StartupAnimationStyle
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.StartupTiming
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
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
import com.theveloper.pixelplay.presentation.screens.onboarding.OnboardingScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.net.toUri
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
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.github.PlayStoreAnnouncementRemoteConfig
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.NavRailStyle
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
import com.theveloper.pixelplay.presentation.components.DownloadStatusPhase
import com.theveloper.pixelplay.presentation.components.DownloadStatusTopChip
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
import com.theveloper.pixelplay.presentation.components.resolveNavigationBarBottomSpacing
import com.theveloper.pixelplay.presentation.components.sanitizeNavigationBarBottomInset
import com.theveloper.pixelplay.presentation.components.AutoUpdatePrompt
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.TabContentHost

import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.KuromojiEngine
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource


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

/**
 * 可靠的窗口横屏判断（与主页一致）。
 * 直接监听 View 全局布局：旋转/分屏时 View 尺寸必然变化，OnGlobalLayout 必然回调；
 * 不依赖 LocalConfiguration/LocalWindowInfo 的配置更新机制（configChanges 旋转不重建 Activity 时不更新）。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberWindowIsLandscape(): Boolean {
    val view = LocalView.current
    var isLandscape by remember { mutableStateOf(view.width > view.height) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val newValue = view.width > view.height
            if (newValue != isLandscape) isLandscape = newValue
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return isLandscape
}

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val LocalHazeState = androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState> {
            error("No HazeState provided")
        }
    }

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var themeStateHolder: ThemeStateHolder
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var shareLinkHandler: com.theveloper.pixelplay.data.share.ShareLinkHandler
    // ⚡ 一起听邀请消息观察器：前台时轮询未读私信，收到一起听邀请自动弹窗询问
    @Inject
    lateinit var listenTogetherInviteWatcher: com.theveloper.pixelplay.data.listentogether.ListenTogetherInviteWatcher
    // ⚡ 拼音排序键回填（46→47 迁移后一次性补齐历史歌曲的排序键）
    @Inject
    lateinit var songSortKeyBackfill: com.theveloper.pixelplay.data.database.SongSortKeyBackfill
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * 系统启动画面放行条件：真实内容（onboarding / 主界面）组合完成后置 true。
     * 仅主线程读写（pre-draw 回调与 Compose 重组同在主线程）。
     */
    private var isContentReady = false

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        try {
            // Install crash handler as early as possible
            CrashHandler.install(newBase)
            android.util.Log.i("PixelPlay", "MainActivity attachBaseContext: SDK=${android.os.Build.VERSION.SDK_INT}")
            super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "CRITICAL: attachBaseContext failed: ${t.message}", t)
            super.attachBaseContext(newBase) // fallback
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统 splash：主内容组合完成（isContentReady）前一直显示启动图标，
        // 启动期间的重组/布局/首帧绘制成本全部被启动画面遮盖。
        // 必须在 super.onCreate() 之前调用。
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isContentReady }

        android.util.Log.i("PixelPlay", "=== MainActivity.onCreate START ===")
        android.util.Log.i("PixelPlay", "Device SDK: ${android.os.Build.VERSION.SDK_INT}, Model: ${android.os.Build.MODEL}")
        LogUtils.d(this, "onCreate")

        try {
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
            android.util.Log.i("PixelPlay", "enableEdgeToEdge() completed")
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "enableEdgeToEdge failed: ${t.message}", t)
        }

        // ⚡ 纯色启动底用"软件实际取色"：与 PixelPlayTheme 的解析顺序一致
        //   （API 31+ 系统动态取色 > 项目静态配色），在 Compose 首帧之前就把窗口底色
        //   刷成同一个颜色，避免"先闪一层别的纯色底再进应用"。
        applyAppBackgroundToWindow()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                android.util.Log.i("PixelPlay", "isNavigationBarContrastEnforced set")
            }
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "isNavigationBarContrastEnforced failed: ${t.message}", t)
        }

        try {
            super.onCreate(savedInstanceState)
            android.util.Log.i("PixelPlay", "super.onCreate() completed")
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "FATAL: super.onCreate() failed: ${t.message}", t)
            throw t // Cannot recover from this
        }

        // 主线程卡顿检测器（仅 Debug）：后台线程每 200ms 采样主线程帧时间戳，
        // 检测到阻塞 > 500ms 时 dump 主线程调用栈。
        // 注意：检测逻辑必须在后台线程（Choreographer 回调也在主线程，卡顿时无法执行）。
        if (BuildConfig.DEBUG) {
            try {
                val choreographer = android.view.Choreographer.getInstance()
                val lastFrameMs = java.util.concurrent.atomic.AtomicLong(0L)
                choreographer.postFrameCallback(object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        lastFrameMs.set(android.os.SystemClock.uptimeMillis())
                        choreographer.postFrameCallback(this)
                    }
                })
                Thread {
                    var lastLoggedMs = 0L
                    while (true) {
                        Thread.sleep(200)
                        val mainThread = Looper.getMainLooper().thread
                        // 主线程若在等待（闲时 sleep），不算卡顿
                        val state = mainThread.state
                        if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) continue
                        val now = android.os.SystemClock.uptimeMillis()
                        val last = lastFrameMs.get()
                        val elapsed = now - last
                        // 距上次打印至少 2 秒，避免刷屏
                        if (last != 0L && elapsed > 500 && now - lastLoggedMs > 2000) {
                            lastLoggedMs = now
                            val stack = mainThread.stackTrace.joinToString("\n    at ")
                            android.util.Log.w(
                                "MainThreadHang",
                                "主线程阻塞 ${elapsed}ms！调用栈:\n    at $stack"
                            )
                        }
                    }
                }.apply { isDaemon = true }.start()
                android.util.Log.i("PixelPlay", "MainThreadHang detector installed (background sampler)")
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to install MainThreadHang detector: ${t.message}")
            }
        }

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
            val isCarModeEnabled by userPreferencesRepository.carModeEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
            val uiScale by userPreferencesRepository.uiScaleFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferencesRepository.UI_SCALE_DEFAULT)
            val globalColorSchemePair by themeStateHolder.activeGlobalColorSchemePair.collectAsStateWithLifecycle()
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            
            // ⚡ 车机模式不再强制横屏：横竖屏完全交给用户 / 系统。
            //   这里显式复位一次，保证此前被锁成横屏的设备升级后立即恢复「跟随系统」。
            LaunchedEffect(Unit) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            
            // Crash report dialog state
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            var shareLinkResult by remember { mutableStateOf<com.theveloper.pixelplay.data.share.ShareResult?>(null) }

            // 连接剪贴板分享链接检测回调 + 处理 pending URL
            LaunchedEffect(Unit) {
                onShareLinkDetected = { result -> shareLinkResult = result }
                // 处理在 composable 就绪前就检测到的链接
                pendingShareUrl?.let { url ->
                    pendingShareUrl = null
                    val result = shareLinkHandler.resolve(url)
                    when (result) {
                        is com.theveloper.pixelplay.data.share.ShareResult.Success -> {
                            if (result.matchedSongs.isNotEmpty() || result.totalCount > 0) {
                                shareLinkResult = result
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Permissions Logic - Request media and notification permissions on startup
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            val permissionsValid = permissionState.allPermissionsGranted

            // 首次启动（新手引导未完成）时不自动弹权限，由引导页的权限步骤处理。
            // initialValue = null：显式区分「偏好尚未加载」与「已加载且为 true」，
            // 避免全新安装时在数据落位前误触发权限请求。
            val initialSetupDone by userPreferencesRepository.initialSetupDoneFlow
                .collectAsStateWithLifecycle(initialValue = null)

            // ⚡ 启动动画样式（设置 → 外观 → 启动动画）
            val startupAnimationStyleRaw by userPreferencesRepository.startupAnimationStyleFlow
                .collectAsStateWithLifecycle(initialValue = StartupAnimationStyle.DEFAULT.name)
            val startupAnimationStyle = StartupAnimationStyle.fromName(startupAnimationStyleRaw)

            // ⚡ 品牌启动页（中间浮出 XiangsuPlayer）：
            //   ⚠️ 停留时间必须从「首帧真正绘制出来之后」开始计时，而不是从进程启动算。
            //   从进程启动算的话，冷启动时这段时间基本被系统启动画面 + 首帧加载吃掉：
            //   品牌页一闪而过、主界面浮出动画在用户看到界面前就已经播完 ——
            //   表现就是「正常启动看不到进入动画」（只有新手引导完成那一刻，因为界面已经可见，
            //   动画才恰好被看到）。
            var brandSplashVisible by remember { mutableStateOf(true) }
            // 首帧已绘制（系统启动画面即将放行）—— 由下面 PixelPlayTheme 内的
            // LaunchedEffect 在 withFrameNanos 之后置为 true。
            var firstFrameDrawn by remember { mutableStateOf(false) }
            // 进入动画是否已开始。必须等品牌页淡出走完再置 true，否则动画会被淡出层吃掉。
            var enterAnimationStarted by remember { mutableStateOf(false) }
            LaunchedEffect(initialSetupDone, firstFrameDrawn) {
                if (initialSetupDone == null || !firstFrameDrawn) return@LaunchedEffect
                // 基准测试不额外等待，避免影响启动耗时测量
                if (!isBenchmarkMode) kotlinx.coroutines.delay(BRAND_SPLASH_VISIBLE_MS)
                brandSplashVisible = false
                // ⚡ 必须等品牌页淡出（BRAND_SPLASH_EXIT_MS）结束后再启动进入动画：
                //   两者同时启动时，淡出层仍不透明地盖在最上层，而进入动画的缓动
                //   （EaseOutCubic / LinearOutSlowInEasing）把大部分位移集中在最前面的
                //   约 1/3 时长里 —— 正好全部落在被遮住的这段时间，
                //   用户感知就是「根本没有进入动画」。
                kotlinx.coroutines.delay(BRAND_SPLASH_EXIT_MS)
                enterAnimationStarted = true
            }

            // Auto-request permissions when app starts and permissions are not granted
            LaunchedEffect(Unit) {
                if (!permissionsValid && !isBenchmarkMode && initialSetupDone == true) {
                    permissionState.launchMultiplePermissionRequest()
                }
            }

            // Check for crash log when app starts
            LaunchedEffect(Unit) {
                if (!isBenchmarkMode && CrashHandler.hasCrashLog()) {
                    crashLogData = CrashHandler.getCrashLog()
                    showCrashReportDialog = true
                }
            }

            // ⚡ 软件缩放（设置 → 外观）：只缩放 Compose 的 Density（本 App 的排版尺寸），
            //   不写系统 DPI、不影响其它应用。100% 时直接复用原 Density 对象，布局一字不动。
            val baseDensity = LocalDensity.current
            val uiScaleFactor = uiScale / 100f
            val scaledDensity = remember(baseDensity, uiScaleFactor) {
                if (uiScaleFactor == 1f) {
                    baseDensity
                } else {
                    Density(baseDensity.density * uiScaleFactor, baseDensity.fontScale)
                }
            }

            CompositionLocalProvider(
                LocalShowScrollbar provides showScrollbar,
                LocalDensity provides scaledDensity
            ) {
                PixelPlayTheme(
                    darkTheme = useDarkTheme,
                    colorSchemePairOverride = globalColorSchemePair
                ) {
                    // ⚡ 冷启动：首帧绘制完成即放行系统启动画面，不再等待 DataStore 首次读盘
                    //   （此前启动图标要一直停留到偏好读完，是冷启动可见耗时的主要来源）。
                    //   偏好未就绪的这段时间由品牌初始化页承接，用户感知为
                    //   「启动图标 → 初始化页 → 主界面浮出」。
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        isContentReady = true
                        // 首帧已绘制 → 品牌页从此刻开始计算「可见停留」，保证进入动画能被看到
                        firstFrameDrawn = true
                    }

                    // 启动完成后上报 fully-drawn，便于用 TTFD 指标量化冷启动
                    LaunchedEffect(initialSetupDone) {
                        if (initialSetupDone == true) {
                            withFrameNanos { }
                            withFrameNanos { }
                            runCatching { reportFullyDrawn() }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // 预热复杂矢量图（pixelplay_base_monochrome 含超长 path，
                        // 首绘生成 DrawCache 需 ~600ms）。本次首绘发生在系统启动画面
                        // 遮盖下的第一帧，成本不可见；后续展开播放器（封面加载前
                        // AlbumPlaceholder）不再卡顿。
                        // 透明 tint 不影响 DrawCache 缓存内容（缓存不应用 colorFilter）。
                        Icon(
                            painter = painterResource(R.drawable.pixelplay_base_monochrome),
                            contentDescription = null,
                            modifier = Modifier.size(86.dp),
                            tint = Color.Transparent
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            // 首次启动：展示新手引导；完成后进入主界面。
                            // null = 偏好尚未从 DataStore 读出，此时只有下面的品牌浮出层可见。
                            when (initialSetupDone) {
                                null -> Unit

                                false -> {
                                    val onboardingScope = rememberCoroutineScope()
                                    OnboardingScreen(
                                        themePreferencesRepository = themePreferencesRepository,
                                        userPreferencesRepository = userPreferencesRepository,
                                        onFinished = {
                                            onboardingScope.launch {
                                                userPreferencesRepository.setInitialSetupDone(true)
                                            }
                                        }
                                    )
                                }

                                true -> StartupEnterGate(
                                    style = startupAnimationStyle,
                                    // ⚡ 等品牌页「完全淡出」再启动主界面进入动画：
                                    //   衔接但不被淡出层遮蔽（见上方 enterAnimationStarted 的说明）
                                    start = enterAnimationStarted
                                ) {
                                    MainAppContent(playerViewModel, mainViewModel)
                                }
                            }

                            // ⚡ 品牌浮出层：对齐原版 XiangsuPlayer 的启动样式 —— 纯色底上只有
                            //    App 名称文字浮出（淡入 + 上浮 + 轻微放大，不再出现 logo / 转圈）。
                            //    停留到 [BRAND_SPLASH_VISIBLE_MS] 后淡出，淡出走完
                            //    （[BRAND_SPLASH_EXIT_MS]）才启动主界面的进入动画，
                            //    保证这段动画完整可见。
                            androidx.compose.animation.AnimatedVisibility(
                                visible = brandSplashVisible,
                                // ⚡ 放大离场：先「放大」、后「渐隐」，两段刻意错开。
                                //   之前缩放与渐隐同长同缓动，透明度很快就降到看不见，
                                //   观感是「文字直接消失」，放大根本来不及被看到。
                                //   现在 0~FADE_DELAY 期间保持不透明只做放大，之后才开始渐隐，
                                //   与主界面「从略小放大进入」形成推镜衔接。
                                //   总时长取 max(缩放, 渐隐延迟+渐隐) = BRAND_SPLASH_EXIT_MS，
                                //   进入动画等这段走完才启动。
                                exit = scaleOut(
                                    targetScale = BRAND_SPLASH_EXIT_SCALE,
                                    animationSpec = tween(
                                        durationMillis = BRAND_SPLASH_ZOOM_MS.toInt(),
                                        easing = EaseInCubic
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = BRAND_SPLASH_FADE_MS.toInt(),
                                        delayMillis = BRAND_SPLASH_FADE_DELAY_MS.toInt()
                                    )
                                )
                            ) {
                                StartupBrandSplash()
                            }
                        }

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

                        // 分享链接确认弹窗
                        if (shareLinkResult != null) {
                            ShareLinkDialog(
                                result = shareLinkResult!!,
                                onConfirm = {
                                    val success = shareLinkResult as com.theveloper.pixelplay.data.share.ShareResult.Success
                                    if (success.matchedSongs.isNotEmpty()) {
                                        playerViewModel.playSongs(success.matchedSongs, success.matchedSongs.first(), "shared_${success.name}")
                                    }
                                    shareLinkResult = null
                                },
                                onDismiss = { shareLinkResult = null }
                            )
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    /**
     * 把「软件实际使用的背景色」刷到窗口底色上（Compose 首帧绘制前生效）。
     *
     * 解析顺序与 [com.theveloper.pixelplay.ui.theme.PixelPlayTheme] 保持一致：
     *  - API 31+：系统动态取色（动态壁纸配色），浅色/深色跟随系统 uiMode；
     *  - 其它：项目内置的 Light/DarkColorScheme。
     *
     * 这样冷启动看到的纯色底就是软件自己的取色，而不是 XML 里写死的另一种颜色。
     */
    private fun applyAppBackgroundToWindow() {
        runCatching {
            val nightMask = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isNight = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
            // ⚡ 解析顺序必须与 PixelPlayTheme 完全一致：
            //   自定义调色盘覆盖 > 系统动态取色 > 静态配色。
            //   少任何一层都会让窗口底色与 App 实际背景不同色，
            //   表现为冷启动先闪一块不明所以的纯色底。
            val overridePair = themeStateHolder.activeGlobalColorSchemePair.value
            val background = when {
                overridePair != null ->
                    if (isNight) overridePair.dark.background else overridePair.light.background
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val scheme = if (isNight) {
                        androidx.compose.material3.dynamicDarkColorScheme(this)
                    } else {
                        androidx.compose.material3.dynamicLightColorScheme(this)
                    }
                    scheme.background
                }
                isNight -> com.theveloper.pixelplay.ui.theme.DarkColorScheme.background
                else -> com.theveloper.pixelplay.ui.theme.LightColorScheme.background
            }
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(background.toArgb())
            )
        }.onFailure {
            android.util.Log.w("PixelPlay", "applyAppBackgroundToWindow failed: ${it.message}")
        }
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
                val uri = intent.data
                if (uri?.scheme == "xiangsuplayer" && uri.host == "share") {
                    // 像素播放器分享链接
                    val url = uri.toString()
                    lifecycleScope.launch {
                        val result = shareLinkHandler.resolve(url)
                        when (result) {
                            is com.theveloper.pixelplay.data.share.ShareResult.Success -> {
                                if (result.matchedSongs.isNotEmpty() || result.totalCount > 0) {
                                    onShareLinkDetected?.invoke(result)
                                } else {
                                    Toast.makeText(this@MainActivity, "未在本地找到匹配的歌曲", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is com.theveloper.pixelplay.data.share.ShareResult.Error -> {
                                Toast.makeText(this@MainActivity, "分享链接无效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    intent.action = null
                } else {
                    uri?.let {
                        persistUriPermissionIfNeeded(intent, it)
                        playerViewModel.playExternalUri(it)
                    }
                    clearExternalIntentPayload(intent)
                }
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
        val colorScheme = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 应用 Logo 容器
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♫",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        fontSize = 48.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 应用名称
                Text(
                    text = "PixelPlay",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                // 副标题
                Text(
                    text = "正在为您准备音乐体验…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 加载指示器 - 使用标准 CircularProgressIndicator 兼容低版本 Android
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
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
        val isCarModeEnabled by userPreferencesRepository.carModeEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
        
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

            // ⚡ 顶部媒体库同步 chip（模仿 Rhythm MediaScanLoader）：同步进行/完成时
            // 从顶部滑入浮动提示，完成态 2s 后自动消失，可上滑/左右滑关闭。
            LibrarySyncTopChip(
                isSyncing = isSyncing,
                syncProgress = syncProgress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )

            // ⚡ 顶部日语注音引擎下载提示（同款 Rhythm 滑入样式）：首次触发日语罗马音
            // 后台下载 kuromoji 引擎时滑入，完成驻留 2s / 失败驻留 3s，可上滑/左右滑关闭。
            KuromojiEngineTopChip(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (canShowLoadingIndicator) {
                LoadingOverlay(syncProgress)
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        // 按窗口实际宽高比判定横屏/平板布局：平板小窗/分屏变窄时自动切换为手机模式。
        // ⚡ 直接监听 View 全局布局：旋转/分屏时 View 尺寸必然变化，OnGlobalLayout 必然回调，
        // 不依赖 LocalConfiguration/LocalWindowInfo 的配置更新机制（部分 Compose 版本旋转后不触发重组）。
        val mainView = LocalView.current
        var isLandscape by remember { mutableStateOf(mainView.width > mainView.height) }
        DisposableEffect(mainView) {
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                val newValue = mainView.width > mainView.height
                if (newValue != isLandscape) isLandscape = newValue
            }
            mainView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose {
                mainView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
        val isCarModeEnabled by userPreferencesRepository.carModeEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
        // ⚡ 底部导航栏不再有「发现」按钮：漫游/电台/AI 入口统一移到主页顶部
        //   的「发现」按钮（见 HomeScreen 的 HomeDiscoverButton / HomeDiscoverSheet）

        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.string.nav_bar_home, R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, screen = Screen.Home),
                BottomNavItem("Search", R.string.nav_bar_search, R.drawable.rounded_search_24, R.drawable.rounded_search_24, screen = Screen.Search),
                BottomNavItem("Library", R.string.nav_bar_library, R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, screen = Screen.Library),
                BottomNavItem("Settings", R.string.settings_top_bar_title, R.drawable.rounded_settings_24, R.drawable.rounded_settings_fill_24, screen = Screen.Settings)
            ).toImmutableList()
        }

        val hearingGuardViewModel: com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardViewModel = hiltViewModel()
        val hearingGuardState by hearingGuardViewModel.state.collectAsStateWithLifecycle()
        var showHearingGuardSetup by remember { mutableStateOf(false) }
        var showHearingGuardStatus by remember { mutableStateOf(false) }
        var hasShownSetupHint by remember { mutableStateOf(false) }
        val onHearingGuardClick: () -> Unit = {
            if (!hearingGuardState.isConfigured) hasShownSetupHint = true
            showHearingGuardStatus = true
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

        val isPlayingForUI by remember {
            playerViewModel.stablePlayerState
                .map { it.isPlaying }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = false)

        val currentSongForUI by remember {
            playerViewModel.stablePlayerState
                .map { it.currentSong }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Accounts.route,
                Screen.SourceMarket.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.DailyRecommendScreen.route,
                Screen.RecentlyPlayed.route,
                Screen.GenreDetail.route,
                Screen.ToplistDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.ArtistHomepage.route,
                Screen.DJSpace.route,
                Screen.CloudMusicSettings.route,
                // ⚡ 网易云服务页面：整屏面板页，进入后隐藏底部导航栏，避免遮挡最下方歌单
                Screen.NeteaseDashboard.route,
                Screen.NavBarCrRad.route,
                Screen.Radio.route,
                Screen.About.route,
                Screen.Stats.route,
                Screen.EditTransition.route,
                Screen.Experimental.route,
                Screen.ArtistSettings.route,
                Screen.SettingsCategory.route,
                Screen.DelimiterConfig.route,
                Screen.PaletteStyle.route,
                Screen.DeviceCapabilities.route,
                Screen.DotDeviceSettings.route,
                Screen.EasterEgg.route,
                Screen.WordDelimiterConfig.route,
                Screen.ArtistWhitelistConfig.route,
                Screen.Equalizer.route,
                Screen.PlayerProgressStyle.route,
                Screen.AiAssistant.route,
                // ⚡ 消息中心 / 聊天页：整屏列表页，进入后隐藏底部导航栏，避免遮挡最后一条会话
                Screen.Messages.route,
                Screen.Chat.route
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
        // 横屏 NavigationRail：始终保持在平板模式下可见（不因搜索激活而隐藏）
        val shouldHideNavigationRail by remember(isSearchActive, isLandscape) {
            derivedStateOf {
                false
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navRailStyle by playerViewModel.navRailStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
        val useSmoothCorners by playerViewModel.useSmoothCorners.collectAsStateWithLifecycle()
        val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()

        // ⚡ 低版本（API < 31）导航栏毛玻璃：haze / RenderEffect 都不可用，改用
        //    「内容层离屏采集 → 降采样盒式模糊 → 按导航栏位置贴回」的位图兜底。
        //    高版本 enabled=false，返回的 modifier 全是 no-op，原有 haze 路径完全不变。
        val navBarBlurEnabledForBackdrop by playerViewModel.navBarBlurEnabled.collectAsStateWithLifecycle()
        val navBarLowVersionBlur = com.theveloper.pixelplay.presentation.components.blur
            .rememberLowVersionBlurBackdrop(
                enabled = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    com.theveloper.pixelplay.presentation.components.SoftBlur.forceSoftwareBlur) &&
                    navBarBlurEnabledForBackdrop && !disableBlurAllOver
            )
        val navBarBlurEnabled by playerViewModel.navBarBlurEnabled.collectAsStateWithLifecycle()
        val predictiveBackCollapseFraction by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
        val rootView = LocalView.current
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }
        val hazeState = remember {
            // Haze v1.6+ 支持全版本模糊：
            // - API 33+: 硬件 RenderEffect（最优）
            // - API 31-32: RenderEffect + workaround
            // - API 21-30: RenderScript 模糊（实验性，可能略卡但比纯 Scrim 好）
            // 窗口硬件加速时启用模糊，软件渲染时退回 Scrim
            val blurEnabled = rootView.isHardwareAccelerated
            dev.chrisbanes.haze.HazeState(initialBlurEnabled = blurEnabled)
        }

        // ⚡ 使用 getBottom(density) 而非 asPaddingValues().calculateBottomPadding()
        // 后者在部分设备上会返回 0，导致导航栏紧贴屏幕底部边缘
        val densityValue = LocalDensity.current
        val rawSystemNavBarInset = run {
            val px = WindowInsets.navigationBars.getBottom(densityValue)
            sanitizeNavigationBarBottomInset(with(densityValue) { px.toDp() })
        }
        // ⚡ 用户隐藏「小白条」（手势提示条）后，系统会上报 0 inset，悬浮/默认底栏会贴到屏幕下边缘；
        // 这里用最小间距兜底（全宽样式本就延伸到底边，不参与兜底）。
        val systemNavBarInset = resolveNavigationBarBottomSpacing(rawSystemNavBarInset, navBarStyle)

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        // ⚡ 水平留白只跟随系统真实 inset（不要用底部兜底间距，否则隐藏小白条后会多出横向内缩）
        val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
            if (rawSystemNavBarInset > 30.dp) 14.dp else rawSystemNavBarInset
        } else {
            0.dp
        }
        // ⚡ 关键优化：导航栏 padding/corner radius 这些值不应该用动画（动画会每帧触发重组）。
        // 它们只随用户设置/导航条可见性改变（低频事件），直接赋值即可。
        val bottomBarPadding = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else systemNavBarInset
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
        val navBarOccupiedHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

        // ⚡ 滚动隐藏底部 chrome：上滑隐藏底部导航栏（mini player 跟随下移），下滑 / 回到顶部恢复
        val scrollHideChromeEnabled by playerViewModel.scrollHideChrome.collectAsStateWithLifecycle()
        var scrollChromeHidden by rememberSaveable { mutableStateOf(false) }
        // 切换页面时复位，避免进入新页面仍是隐藏态（同时清掉上一页的"到顶"上报）
        LaunchedEffect(currentRoute) {
            scrollChromeHidden = false
            playerViewModel.reportListAtTop(false)
        }
        // 滚回列表顶部时自动恢复底部 chrome（各页面上报）
        val isListAtTop by playerViewModel.isListAtTop.collectAsStateWithLifecycle()
        LaunchedEffect(isListAtTop) { if (isListAtTop) scrollChromeHidden = false }
        // ⚡ 底栏 / 迷你条随滚动隐藏只在「媒体库」页面生效，其它页面保持常驻
        val scrollChromeActive = scrollHideChromeEnabled && currentRoute == Screen.Library.route
        val scrollChromeThresholdPx = with(densityValue) { 12.dp.toPx() }
        val scrollChromeConnection = remember(scrollChromeActive, scrollChromeThresholdPx) {
            // 累计位移：方向反转或触发后归零
            var accumulated = 0f
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!scrollChromeActive || source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = available.y
                    if (dy == 0f) return Offset.Zero
                    if (accumulated != 0f && (dy > 0f) != (accumulated > 0f)) accumulated = 0f
                    accumulated += dy
                    if (accumulated <= -scrollChromeThresholdPx) {
                        scrollChromeHidden = true
                        accumulated = 0f
                    } else if (accumulated >= scrollChromeThresholdPx) {
                        scrollChromeHidden = false
                        accumulated = 0f
                    }
                    // 只观察不消费，保证列表滚动本身不受影响
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    // 下滑方向仍有未被消费的位移 => 列表已在顶部（含惯性滑到顶），自动恢复底部 chrome
                    if (available.y > 0f && consumed.y == 0f && scrollChromeHidden) {
                        scrollChromeHidden = false
                        accumulated = 0f
                    }
                    return Offset.Zero
                }
            }
        }

        // ⚡ 关键优化：分离底部导航栏和 NavigationRail 的动画,避免互相干扰
        // 底部导航栏动画 - 仅用于竖屏,考虑 player 展开状态
        val bottomNavBarProgressState: androidx.compose.runtime.State<Float> = animateFloatAsState(
            targetValue = if (shouldHideBottomNavBar || scrollChromeHidden) 0f else 1f,
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
            when {
                shouldHideBottomNavBar -> systemNavBarInset
                // 悬浮底栏：mini player 直接落到胶囊正上方，底部仅预留 mini 高度 + inset，
                // 消除之前按普通底栏高度(84dp)预留造成 mini player 与悬浮导航之间的大空隙
                navBarStyle == NavBarStyle.FLOATING -> MiniPlayerHeight + systemNavBarInset
                else -> navBarOccupiedHeight
            }
        }

        // ⚡ 迷你条「跟随底栏下移」的最大位移 = 底栏占位 − 系统底部安全区。
        //    这样底栏完全隐藏时，迷你条刚好落到只留系统 inset 的位置：
        //    - 修掉「下移 52dp 后离屏幕底部还有一截空隙」（位移小于底栏实际占位）；
        //    - 修掉「导航栏本就隐藏的页面（消息/聊天）被多推 52dp 挤出屏幕」（此时占位=inset，位移为 0）。
        val miniPlayerScrollShiftMaxDp = if (isLandscape) {
            0.dp
        } else {
            (miniPlayerBottomMarginDp - systemNavBarInset).coerceAtLeast(0.dp)
        }

        // NavigationRail 的水平 padding:使用稳定值,不依赖动画值,避免位置抖动
        val navRailPaddingDp = if (isLandscape && !isCarModeEnabled) {
            // 横屏且非车机模式时,给内容留出 Rail 空间（悬浮 96dp / 停靠 84dp）
            // 不使用动画值,避免 sheetCollapsedTargetY 每帧变化
            if (navRailStyle == NavRailStyle.DOCKED) 84.dp else 96.dp
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
            LocalHapticFeedback provides scopedHapticFeedback,
            LocalHazeState provides hazeState
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
                    // 悬浮导航模式：默认不显示 mini player，点击左侧封面圆直接展开全屏播放器，
                    // 返回/下滑收起后回到悬浮底栏（mini player 仍隐藏）。
                    // ⚠️ 仅在真正显示底部悬浮导航时隐藏 mini player；横屏/平板走 NavigationRail，
                    // 底部导航不悬浮，此时必须保留 mini player，否则平板会因设置了悬浮样式而 mini player 消失。
                    // ⚡ 聊天详情页隐藏迷你播放条（消息列表页保持显示）；
                    //    AiAssistant / NavBarCrRad 原本就隐藏
                    val routesWithHiddenMiniPlayer = remember {
                        setOf(Screen.NavBarCrRad.route, Screen.AiAssistant.route, Screen.Chat.route)
                    }
                    val shouldHideFloatingMini by remember(currentRoute, navBarStyle, isLandscape) {
                        derivedStateOf {
                            currentRoute in routesWithHiddenMiniPlayer
                        }
                    }
                    val onNowPlayingClick = remember(playerViewModel) {
                        {
                            playerViewModel.showPlayer()
                            playerViewModel.expandPlayerSheet()
                        }
                    }
                    val onNowPlayingSwipeDown = remember(playerViewModel) {
                        {
                            playerViewModel.collapsePlayerSheet()
                        }
                    }
                    if (isLandscape && !isCarModeEnabled) {
                        // ⚡ 横屏 NavigationRail:使用独立的 Composable,通过 Stable 参数提升重组性能
                        // 车机模式下隐藏导航栏,为驾驶场景优化
                        MainNavigationRail(
                            navController = navController,
                            navItems = commonNavItems,
                            currentRoute = currentRoute,
                            navRailProgressState = navRailProgressState,
                            navRailStyle = navRailStyle
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
                            modifier = Modifier
                                .fillMaxSize()
                                .then(navBarLowVersionBlur.contentModifier)
                                .nestedScroll(scrollChromeConnection),
                            bottomBar = {
                                if (!isLandscape) {
                                    // ⚡ 这里只放一个「占位 spacer」，底栏本体挪到最外层 Box 的最后渲染
                                    //   （见下方 MainBottomNavigationBar 浮层）——底栏作为浮层永远是最上层，
                                    //   不会被播放器面板 / 展开遮罩压住导致点不动。
                                    //   占位高度在「路由隐藏底栏」的整屏页收为 0，避免页面底部留白
                                    //   （聊天页输入框下方那块空白）。
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(if (routeHidden) 0.dp else navBarOccupiedHeight)
                                    )
                                }
                            }
                        ) { innerPadding ->
                            val expansionFractionProvider = remember(playerViewModel.playerContentExpansionFraction) {
                                { playerViewModel.playerContentExpansionFraction.value }
                            }
                            // Only create BlurEffectCache on Android 12+ to avoid class loading issues on older devices
                            val blurEffectCache = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                remember { BlurEffectCache() }
                            } else {
                                null
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEffectCache != null) {
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
                                AppNavigation(
                                    playerViewModel = playerViewModel,
                                    navController = navController,
                                    userPreferencesRepository = userPreferencesRepository,
                                    paddingValues = innerPadding,
                                    onSearchBarActiveChange = { isSearchBarActive = it },
                                    onOpenSidebar = { scope.launch { drawerState.open() } }
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
                // 悬浮模式：mini player 始终显示。
                // ⚠️ 仅在特定路由时隐藏 mini player；横屏/平板（NavigationRail）不隐藏 mini player。
                val shouldHideMiniPlayer by remember(currentRoute, navBarStyle, isLandscape) {
                    derivedStateOf {
                        currentRoute in routesWithHiddenMiniPlayer
                    }
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

                // ⚡ isExpandedOrExpanding：当 sheet 状态为 EXPANDED 或 playerContentExpansionFraction 跨过 0.01 时为 true。
                // 加入 sheetState 判断后，点击悬浮底栏封面展开瞬间就能立即渲染 sheet 容器。
                val sheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
                val isExpandedOrExpanding by remember(sheetState) {
                    derivedStateOf {
                        sheetState == PlayerSheetState.EXPANDED ||
                            playerViewModel.playerContentExpansionFraction.value > 0.01f
                    }
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

                // ⚡ mini-player 裁剪盒：折叠态时从屏幕顶部一直延伸到「迷你条底边」。
                // - 展开态(isExpandedOrExpanding=true)：用整屏高度，保证播放器铺满全屏；
                // - 折叠态：底边 = 迷你条底边 + 底栏隐藏进度 × 下移量。
                // ⚠️ 底栏可见时必须严格停在迷你条底边：这个裁剪容器是整屏透明的，而内部播放器
                //   面板带一个覆盖整个裁剪区的 clickable（折叠态点击=展开播放器），一旦容器延伸
                //   到底部导航栏上方，导航栏的点击会被整体吃掉（表现为「竖屏导航栏点不动」）。
                //   只有底栏真正隐藏时才放行下移余量——此时底栏已移出屏幕，迷你条下移不会互相遮挡。
                val collapsedBaseClipHeightPx = remember(
                    showPlayerContentInitially,
                    shouldHideMiniPlayer,
                    currentRoute,
                    // ⚠️ 手机/平板模式切换或窗口尺寸变化时必须重新计算：
                    // miniPlayerBottomMarginDp 依赖 isLandscape，若用旧值，播放器 Surface
                    // 会覆盖到底部导航栏上方，拦截触摸导致导航栏无法点击
                    miniPlayerBottomMarginDp,
                    containerHeight,
                    isLandscape,
                    sheetCollapsedTargetY
                ) {
                    val shouldShowMiniPlayer = showPlayerContentInitially && !shouldHideMiniPlayer &&
                            currentRoute !in setOf(Screen.NavBarCrRad.route)
                    if (shouldShowMiniPlayer) {
                        (sheetCollapsedTargetY + with(density) { MiniPlayerHeight.toPx() })
                            .coerceAtLeast(0f)
                    } else {
                        with(density) { containerHeight.toPx() }
                    }
                }
                val miniPlayerShiftMaxPx = with(density) { miniPlayerScrollShiftMaxDp.toPx() }
                // ⚠️ 当没有播放内容且非展开态时（如横滑移除 mini-player 后），
                // 完全不渲染 UnifiedPlayerSheetV2，避免其全屏 Surface 覆盖在底部导航栏上方
                // 拦截触摸事件导致无法切换页面。
                // 悬浮模式下默认隐藏 mini player，只在真正展开全屏播放器时才渲染 sheet 容器，
                // 避免收起后空容器遮挡整个屏幕导致页面无法点击。
                val shouldRenderPlayerSheet = (showPlayerContentInitially && !shouldHideMiniPlayer) || isExpandedOrExpanding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // ⚡ 一个盒子同时管「约束」与「裁剪」两件事，二者刻意分开：
                        //
                        //   ① 传给面板的高度约束恒为「迷你条底边 base」——
                        //      面板内部有一个覆盖整个约束高度的 clickable（折叠态点击=展开播放器），
                        //      而且面板常驻渲染一份全屏播放器内容用于展开动画，它们在屏幕下方的
                        //      命中区正好落在底栏上方，会把底栏点击整体吃掉。约束钉在 base 后，
                        //      面板自身布局不会再越过迷你条底边。
                        //
                        //   ② 对外报告的裁剪高度 = base + 底栏隐藏进度 × 最大下移量——
                        //      底栏可见时裁剪区正好收在迷你条底边（底栏区域内的命中区被裁掉），
                        //      只有底栏真正隐藏时才放行"跟随底栏下移"所需的余量。
                        //
                        //   由于面板的约束（①）不受裁剪高度（②）影响，裁剪高度逐帧变化
                        //   不会重新测量面板子树（约束未变，Compose 复用测量结果），
                        //   所以既不会遮挡底栏、也不会带来"上滑收起卡顿"的逐帧重测开销。
                        .layout { measurable, constraints ->
                            if (isExpandedOrExpanding) {
                                val placeable = measurable.measure(constraints)
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(0, 0)
                                }
                            } else {
                                val sheetHeightPx = collapsedBaseClipHeightPx.roundToInt()
                                    .coerceIn(0, constraints.maxHeight)
                                val hideProgress = (1f - bottomNavBarProgressState.value).coerceIn(0f, 1f)
                                val clipHeightPx = (collapsedBaseClipHeightPx + miniPlayerShiftMaxPx * hideProgress)
                                    .roundToInt()
                                    .coerceIn(0, constraints.maxHeight)
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = sheetHeightPx, maxHeight = sheetHeightPx)
                                )
                                layout(constraints.maxWidth, clipHeightPx) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                        .clipToBounds()
                ) {
                    // ⚡ isNavBarHidden 使用稳定的布尔值(不读动画 State),
                    // 避免导航切换动画期间每帧触发重组。
                    // 圆角/边距等用稳定值足矣,动画由 sheet 内部的 SheetMotionController 处理。
                    if (shouldRenderPlayerSheet) {
                        val isNavBarHiddenValue = if (isLandscape) shouldHideNavigationRail else shouldHideBottomNavBar
                        UnifiedPlayerSheetV2(
                            playerViewModel = playerViewModel,
                            sheetCollapsedTargetY = sheetCollapsedTargetY,
                            collapsedStateHorizontalPadding = horizontalPadding,
                            hideMiniPlayer = shouldHideMiniPlayer,
                            containerHeight = containerHeight,
                            navController = navController,
                            isNavBarHidden = isNavBarHiddenValue,
                            navRailPadding = navRailPaddingDp,
                            isLandscape = isLandscape,
                            isFloatingBottomBar = navBarStyle == NavBarStyle.FLOATING,
                            onFloatingBottomBarCollapse = onNowPlayingSwipeDown,
                            miniPlayerScrollShiftPxProvider = {
                                // （底栏占位 − 系统安全区）× 滚动隐藏进度：迷你条跟随底栏下移但不移出屏幕，
                                //  底栏完全隐藏时正好落到只留系统 inset 的位置
                                // （展开成全屏播放器时由 sheet 内部的 expansionFraction 自动归零）
                                // ⚡ 横屏 / 平板走 NavigationRail，没有需要隐藏的底部导航栏，不做下移
                                with(densityValue) { miniPlayerScrollShiftMaxDp.toPx() } *
                                    (1f - bottomNavBarProgressState.value)
                            }
                        )
                    }
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

                // ⚡ 底部导航栏作为「最外层浮层」最后渲染：任何播放器面板 / 展开遮罩都在它下面，
                //   从结构上保证导航栏永远可点。此前它放在 Scaffold 的 bottomBar 槽里，
                //   一旦上层节点覆盖该区域，点击就被整体吃掉（表现为「点导航栏完全没反应」）。
                //   占位仍由 Scaffold 的 bottomBar spacer 负责，页面底部间距不受影响。
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
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
                            currentSong = currentSongForUI,
                            isPlaying = isPlayingForUI,
                            bottomBarPadding = bottomBarPadding,
                            navBarHeight = navBarHeight,
                            navBarOccupiedHeight = navBarOccupiedHeight,
                            horizontalPadding = horizontalPadding,
                            bottomNavBarProgressState = bottomNavBarProgressState,
                            onNowPlayingClick = onNowPlayingClick,
                            miniPlayerVisible = !shouldHideFloatingMini,
                            // ⚡ 低版本（或强制）软件模糊：必须挂在**底栏内容**这一层，
                            //   与 hazeEffect 同一个节点 —— 底栏 Surface 的底色是不透明的，
                            //   画在它「下面」的模糊会被完全盖住（画在内容层才是覆盖它的底色）。
                            lowVersionBlurModifier = navBarLowVersionBlur.blurModifier
                        )
                    }
                }
            }
        }

        // 听力保护状态卡片（平板模式下点击盾牌弹出，从下往上）
        if (showHearingGuardStatus) {
            com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardStatusSheet(
                state = hearingGuardState,
                showSetupHint = hasShownSetupHint && !hearingGuardState.isConfigured,
                onSettingsClick = {
                    showHearingGuardStatus = false
                    showHearingGuardSetup = true
                },
                onDismissRequest = { showHearingGuardStatus = false }
            )
        }

        // 听力保护设置弹窗（平板模式下由 NavigationRail 底部盾牌触发）
        if (showHearingGuardSetup) {
            com.theveloper.pixelplay.presentation.components.hearingguard.HearingGuardSetupDialog(
                currentConfig = hearingGuardState.config,
                isEnabled = hearingGuardState.enabled,
                onEnabledChange = { hearingGuardViewModel.setEnabled(it) },
                onConfirm = { config ->
                    hearingGuardViewModel.setConfig(config)
                    showHearingGuardSetup = false
                },
                onDisable = {
                    hearingGuardViewModel.clearConfig()
                    showHearingGuardSetup = false
                },
                onDismiss = { showHearingGuardSetup = false }
            )
        }

        // 启动自动检查更新（每天一次，发现新版本时弹窗）
        AutoUpdatePrompt(userPreferencesRepository = userPreferencesRepository)

        // ⚡ 一起听邀请：前台时轮询未读私信，检测到好友的一起听邀请自动弹窗询问是否加入
        LifecycleStartEffect(Unit) {
            listenTogetherInviteWatcher.start()
            onStopOrDispose { listenTogetherInviteWatcher.stop() }
        }
        // ⚡ 拼音排序键回填：46→47 迁移后一次性补齐历史歌曲的排序键（IO 线程，幂等）
        LaunchedEffect(Unit) {
            songSortKeyBackfill.ensureBackfilledAsync()
        }
        val pendingTogetherInvite by listenTogetherInviteWatcher.pendingInvite
            .collectAsStateWithLifecycle()
        pendingTogetherInvite?.let { invite ->
            com.theveloper.pixelplay.presentation.components.PixelAlertDialog(
                onDismissRequest = { listenTogetherInviteWatcher.dismissPendingInvite() },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(stringResource(R.string.together_invite_dialog_title)) },
                text = {
                    Text(stringResource(R.string.together_invite_dialog_body, invite.inviterName))
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { listenTogetherInviteWatcher.acceptPendingInvite() }
                    ) {
                        Text(stringResource(R.string.together_invite_dialog_join))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { listenTogetherInviteWatcher.dismissPendingInvite() }
                    ) {
                        Text(stringResource(R.string.together_invite_dialog_ignore))
                    }
                }
            )
        }

Trace.endSection()
    }
    }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun LoadingOverlay(syncProgress: SyncProgress) {
        val colorScheme = MaterialTheme.colorScheme
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
                .background(colorScheme.surfaceContainer.copy(alpha = 0.92f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "正在准备您的音乐库…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "首次启动需要扫描本地歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (syncProgress.hasProgress) {
                        Spacer(modifier = Modifier.height(24.dp))
                        androidx.compose.material3.LinearWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "已扫描 ${syncProgress.currentCount} / ${syncProgress.totalCount} 首歌曲",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    /**
     * 顶部浮动媒体库同步 chip（模仿 Rhythm MediaScanLoader）。
     *
     * 同步进行中：从顶部滑入，显示 wavy loader + 阶段文案 + 进度；
     * 同步完成：2s 后自动滑出；可上滑/左右滑手动关闭。
     * 不在任何 tab 上常驻，仅在有同步工作时短暂出现。
     */
    @Composable
    private fun LibrarySyncTopChip(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        modifier: Modifier = Modifier
    ) {
        val coroutineScope = rememberCoroutineScope()
        val swipeOffsetX = remember { Animatable(0f) }
        val swipeOffsetY = remember { Animatable(0f) }
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 80.dp.toPx() }

        var exitTransition by remember {
            mutableStateOf(fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it }))
        }
        var manuallyDismissed by remember { mutableStateOf(false) }

        // 完成态短暂驻留：同步结束后保持 chip 2s，显示 "媒体库已更新" 后滑出。
        var showCompleted by remember { mutableStateOf(false) }
        LaunchedEffect(syncProgress.isCompleted) {
            if (syncProgress.isCompleted) {
                showCompleted = true
                delay(2000)
                showCompleted = false
            }
        }

        val visible = !manuallyDismissed && (isSyncing || showCompleted)

        LaunchedEffect(visible) {
            if (visible) {
                swipeOffsetX.snapTo(0f)
                swipeOffsetY.snapTo(0f)
                exitTransition = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it })
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -it }),
            exit = exitTransition,
            modifier = modifier
        ) {
            val scanProgressValue: Float? = if (isSyncing && syncProgress.hasProgress) {
                syncProgress.progress.coerceIn(0f, 1f)
            } else {
                null
            }

            val scanLabelText = when {
                !isSyncing && showCompleted -> stringResource(R.string.sync_library_updated)
                else -> when (syncProgress.phase) {
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                        stringResource(R.string.sync_scanning)
                    SyncProgress.SyncPhase.PROCESSING_FILES,
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                        stringResource(R.string.sync_processing)
                    SyncProgress.SyncPhase.SCANNING_LRC ->
                        stringResource(R.string.library_background_sync_lyrics)
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                        stringResource(R.string.library_background_sync_cache)
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                        stringResource(R.string.library_background_sync_cloud)
                    else -> stringResource(R.string.sync_in_progress)
                }
            }

            val scanStatusText = when {
                !isSyncing && showCompleted -> stringResource(R.string.sync_up_to_date)
                syncProgress.hasProgress -> stringResource(
                    R.string.sync_files_progress,
                    syncProgress.currentCount,
                    syncProgress.totalCount
                )
                else -> stringResource(R.string.sync_in_progress)
            }

            val swipeFraction = remember(swipeOffsetX.value, swipeOffsetY.value) {
                val maxDist = swipeThresholdPx * 1.5f
                val dist = maxOf(kotlin.math.abs(swipeOffsetX.value), kotlin.math.abs(swipeOffsetY.value))
                (dist / maxDist).coerceIn(0f, 1f)
            }
            val chipAlpha = (1f - swipeFraction).coerceIn(0f, 1f)
            val chipScale = (1f - swipeFraction * 0.1f).coerceIn(0.9f, 1f)

            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        translationX = swipeOffsetX.value
                        translationY = swipeOffsetY.value
                        alpha = chipAlpha
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val x = swipeOffsetX.value
                                val y = swipeOffsetY.value
                                if (y < -swipeThresholdPx) {
                                    coroutineScope.launch {
                                        exitTransition = fadeOut(animationSpec = tween(200)) +
                                            slideOutVertically(targetOffsetY = { -it })
                                        swipeOffsetY.animateTo(-500f, tween(200))
                                        manuallyDismissed = true
                                    }
                                } else if (kotlin.math.abs(x) > swipeThresholdPx) {
                                    coroutineScope.launch {
                                        if (x > 0) {
                                            exitTransition = fadeOut(animationSpec = tween(200)) +
                                                slideOutHorizontally(targetOffsetX = { it })
                                        } else {
                                            exitTransition = fadeOut(animationSpec = tween(200)) +
                                                slideOutHorizontally(targetOffsetX = { -it })
                                        }
                                        val targetX = if (x > 0) 1000f else -1000f
                                        swipeOffsetX.animateTo(targetX, tween(200))
                                        manuallyDismissed = true
                                    }
                                } else {
                                    coroutineScope.launch {
                                        launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                        launch { swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                                swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount.y).coerceAtMost(50f))
                            }
                        }
                    },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSyncing) {
                            val currentProgress = scanProgressValue
                            if (currentProgress != null) {
                                CircularWavyProgressIndicator(
                                    progress = { currentProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            } else {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = scanLabelText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Text(
                            text = scanStatusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    // ⚡ 顶部日语注音引擎下载 chip（与 LibrarySyncTopChip 同款 Rhythm 交互）：
    // 首次触发日语罗马音时后台下载 kuromoji 引擎，此期间从顶部滑入提示；
    // 完成态驻留 2s / 失败态驻留 3s 后自动滑出，可上滑/左右滑关闭。
    @Composable
    private fun KuromojiEngineTopChip(modifier: Modifier = Modifier) {
        val engineState by KuromojiEngine.state.collectAsStateWithLifecycle()
        val phase = when (val state = engineState) {
            is KuromojiEngine.EngineState.Downloading -> DownloadStatusPhase.Downloading(state.progressPercent)
            KuromojiEngine.EngineState.Ready -> DownloadStatusPhase.Success
            is KuromojiEngine.EngineState.Failed -> DownloadStatusPhase.Failed(state.message)
            KuromojiEngine.EngineState.NotInstalled -> null
        }
        // chip 的视觉与交互复用通用的 DownloadStatusTopChip（与歌词字体下载共用）
        DownloadStatusTopChip(
            phase = phase,
            label = stringResource(R.string.kuromoji_chip_engine_label),
            progressTextRes = R.string.kuromoji_engine_subtitle_downloading,
            indeterminateTextRes = R.string.kuromoji_engine_subtitle_downloading_unknown,
            successText = stringResource(R.string.kuromoji_chip_ready),
            failedText = stringResource(R.string.kuromoji_chip_failed),
            modifier = modifier
        )
    }


    @OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.ui.graphics.ExperimentalGraphicsApi::class)
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
        currentSong: Song? = null,
        isPlaying: Boolean = false,
        bottomBarPadding: androidx.compose.ui.unit.Dp,
        navBarHeight: androidx.compose.ui.unit.Dp,
        navBarOccupiedHeight: androidx.compose.ui.unit.Dp,
        horizontalPadding: androidx.compose.ui.unit.Dp,
        bottomNavBarProgressState: androidx.compose.runtime.State<Float>,
        onCenterNavClick: () -> Unit = {},
        onNowPlayingClick: () -> Unit = {},
        miniPlayerVisible: Boolean = false,
        /** 低版本 / 强制软件模糊的底栏模糊 modifier（画在内容层，覆盖 Surface 底色） */
        lowVersionBlurModifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ) {
        // 使用 Stable 参数,Compose 可以在参数不变时跳过重组
        val showPlayerContentArea = currentSongId != null
        val navBarElevation = 3.dp
        // ⚡ 底栏只在播放器「真正展开」时才下移让位（见下方 graphicsLayer 的 expansionHide）
        val sheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
        val navBarBlurEnabledState by playerViewModel.navBarBlurEnabled.collectAsStateWithLifecycle()
        val disableBlurAllOverState by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()

        val densityLocal = LocalDensity.current
        val navBarCornerRadiusStaticPx = remember(navBarCornerRadius, densityLocal) {
            with(densityLocal) { navBarCornerRadius.dp.toPx() }
        }
        val playerTopCornerTargetPx = remember(densityLocal) { with(densityLocal) { 26.dp.toPx() } }
        // 导航条背景模糊效果缓存
        val navBarBlurEffectCache = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember { BlurEffectCache() }
        } else {
            null
        }
        // For DEFAULT nav bar style: when music is playing, nav bar top corners should match
        // the now playing bar's bottom corners (10.dp). Without music: use user configured value.
        val nowPlayingBottomRadiusPx = remember(densityLocal) {
            with(densityLocal) { 10.dp.toPx() }
        }

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
                    .then(
                        if (navBarStyle == NavBarStyle.FLOATING) {
                            Modifier.padding(start = 12.dp, end = 12.dp, bottom = bottomBarPadding)
                        } else {
                            Modifier.padding(bottom = bottomBarPadding)
                        }
                    )
                    .onSizeChanged { componentHeightPx = it.height }
                    .graphicsLayer {
                        // ⚡ 只有播放器真正展开时才让底栏下移让位。
                        //   折叠态（包括显示 mini player 时）一律不移动底栏：
                        //   否则一旦 expansionFraction 停在非 0 值，底栏会被这段位移推到
                        //   clipToBounds 之外 —— 表现就是「导航栏看得见却怎么点都没反应」
                        //  （关掉 mini player 后 showPlayerContentArea 变 false 就恢复正常）。
                        val expansionHide =
                            if (showPlayerContentArea && sheetState == PlayerSheetState.EXPANDED) {
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
                            navBarStyle == NavBarStyle.FLOATING -> {
                                // 悬浮底栏：始终使用统一圆角
                                val floatingRadius = with(densityLocal) { 28.dp.toPx() }
                                if (showPlayerContentArea && !isMiniPlayerDismissing) {
                                    val transitionFraction = (safeFraction / 0.2f).coerceIn(0f, 1f)
                                    androidx.compose.ui.util.lerp(
                                        floatingRadius,
                                        playerTopCornerTargetPx,
                                        transitionFraction
                                    )
                                } else {
                                    floatingRadius
                                }
                            }
                            navBarStyle == NavBarStyle.DEFAULT -> {
                                // When music is playing: start from nowPlayingBottomRadius (10.dp)
                                // to match the now playing bar's bottom corners.
                                // When no music: keep user configured nav bar radius.
                                val baseRadius = if (showPlayerContentArea && !isMiniPlayerDismissing) {
                                    nowPlayingBottomRadiusPx
                                } else {
                                    navBarCornerRadiusStaticPx
                                }
                                val target = if (showPlayerContentArea && !isMiniPlayerDismissing) {
                                    playerTopCornerTargetPx
                                } else {
                                    navBarCornerRadiusStaticPx
                                }
                                val transitionFraction = (safeFraction / 0.2f).coerceIn(0f, 1f)
                                androidx.compose.ui.util.lerp(
                                    baseRadius,
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
                            NavBarStyle.FLOATING -> with(densityLocal) { 28.dp.toPx() }
                            else -> navBarCornerRadiusStaticPx
                        }
                        shape = navBarShapeCache.get(this, topPx, bottomPx, useSmoothCorners)
                        clip = true
                        shadowElevation = if (navBarStyle == NavBarStyle.FLOATING) 0f else navBarElevationPx
                    },
                // 悬浮模式不再绘制整块条形底色（只保留三个独立元件悬浮）
                color = if (navBarStyle == NavBarStyle.FLOATING) {
                    androidx.compose.ui.graphics.Color.Transparent
                } else {
                    NavigationBarDefaults.containerColor
                }
            ) {
                PlayerInternalNavigationBar(
                    navController = navController,
                    navItems = navItems,
                    currentRoute = currentRoute,
                    navBarStyle = navBarStyle,
                    compactMode = navBarCompactMode,
                    bottomBarPadding = bottomBarPadding,
                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                    onCenterNavClick = onCenterNavClick,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onNowPlayingClick = onNowPlayingClick,
                    miniPlayerVisible = miniPlayerVisible,
                    // ⚡ 悬浮底栏同样遵守「导航栏模糊」+「禁用所有模糊」设置
                    blurEnabled = navBarBlurEnabledState && !disableBlurAllOverState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(lowVersionBlurModifier)
                        .then(
                            if (navBarBlurEnabledState && !disableBlurAllOverState &&
                                navBarStyle != NavBarStyle.FLOATING &&
                                // 强制软件模糊（开发者测试开关）时不要再叠 haze，避免两层模糊
                                !com.theveloper.pixelplay.presentation.components.SoftBlur.forceSoftwareBlur
                            ) {
                                Modifier.hazeEffect(
                                    state = LocalHazeState.current,
                                    style = dev.chrisbanes.haze.materials.HazeMaterials.ultraThin()
                                ) {
                                    // 中强模糊：加强底栏模糊半径
                                    blurRadius = 40.dp
                                }
                            } else {
                                Modifier
                            }
                        )
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
        navRailProgressState: androidx.compose.runtime.State<Float>,
        navRailStyle: String = NavRailStyle.FLOATING,
        onCenterNavClick: () -> Unit = {},
    ) {
        val isFloating = navRailStyle != NavRailStyle.DOCKED
        // 悬浮：88dp 外框内嵌 80dp 胶囊；停靠：84dp 贴边直角
        val outerWidth = if (isFloating) 88.dp else 84.dp
        val innerWidth = if (isFloating) 80.dp else 84.dp

        val containerColor = if (isFloating) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val railShape = if (isFloating) RoundedCornerShape(28.dp) else RectangleShape
        val tonalElevation = if (isFloating) 3.dp else 1.dp
        val shadowElevation = if (isFloating) 4.dp else 0.dp

        // 参考 Rhythm：设置项固定在底部，其余导航项作为主分组
        val settingsRoute = Screen.Settings.route
        val topItems = remember(navItems, settingsRoute) {
            navItems.filter { it.screen.route != settingsRoute }
        }
        val bottomItems = remember(navItems, settingsRoute) {
            navItems.filter { it.screen.route == settingsRoute }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                // ⚡ 停靠样式不给容器留系统栏内缩：面板底色必须一直延伸到状态栏与系统导航区域，
                //   否则上下会露出页面背景、看起来"没贯通"。内容避让改由内部 Column 负责。
                //   悬浮样式仍是内容高度的胶囊，整体内缩避开系统栏。
                .then(
                    if (isFloating) Modifier.windowInsetsPadding(WindowInsets.systemBars) else Modifier
                )
                .width(outerWidth)
                .graphicsLayer {
                    val visibility = navRailProgressState.value
                    alpha = visibility
                    translationX = (1f - visibility) * -outerWidth.toPx()
                },
            // ⚡ 侧栏不再上下撑满：整体收缩为内容高度并垂直居中，避免上下拉得很长
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .then(
                        if (isFloating) {
                            Modifier.padding(start = 8.dp)
                        } else {
                            Modifier
                        }
                    )
                    .width(innerWidth)
                    // ⚡ 停靠样式：面板底色必须上下贯通（贴满可用高度），
                    //   否则会变成一条居中的短条，"停靠"看起来非常奇怪。
                    //   悬浮样式仍保持内容高度 + 垂直居中。
                    .then(
                        if (isFloating) Modifier.wrapContentHeight() else Modifier.fillMaxHeight()
                    ),
                shape = railShape,
                color = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            if (isFloating) Modifier.wrapContentHeight() else Modifier.fillMaxHeight()
                        )
                        // ⚡ 停靠样式：面板背景已铺满全屏高度，这里只让「内容」避开状态栏与
                        //   系统导航区域，避免导航项被状态栏/手势条压住。
                        .then(
                            if (isFloating) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    topItems.forEach { item ->
                        NavRailEntry(
                            item = item,
                            selected = currentRoute != null && currentRoute == item.screen.route,
                            navController = navController,
                            onCenterNavClick = onCenterNavClick
                        )
                    }
                    // ⚡ 不再额外插 16dp：条目自身上下各有 16dp 留白，
                    //   再加一层会让"设置"和它上方的按钮显得比其它条目间距更大。
                    bottomItems.forEach { item ->
                        NavRailEntry(
                            item = item,
                            selected = currentRoute != null && currentRoute == item.screen.route,
                            navController = navController,
                            onCenterNavClick = onCenterNavClick
                        )
                    }
                }
            }
        }
    }

    /**
     * 单个导航栏条目：56×32dp 图标容器 + 选中药丸（宽度 0→56dp 弹簧展开）+ 下方文字。
     * 条目整体 16dp 圆角，点击涟漪被裁剪在圆角内。
     */
    @Composable
    private fun NavRailEntry(
        item: BottomNavItem,
        selected: Boolean,
        navController: NavHostController,
        onCenterNavClick: () -> Unit
    ) {
        val colors = MaterialTheme.colorScheme
        val isCenterAction = item.screen.route == Screen.Roaming.route
        val onClickLambda: () -> Unit = remember(item.screen.route, navController) {
            {
                if (isCenterAction) {
                    onCenterNavClick()
                } else {
                    navController.navigateSafely(item.screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }

        val iconColor by animateColorAsState(
            targetValue = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            animationSpec = tween(150),
            label = "NavRailIconColor"
        )

        val labelColor by animateColorAsState(
            targetValue = if (selected) colors.onSurface else colors.onSurfaceVariant,
            animationSpec = tween(150),
            label = "NavRailLabelColor"
        )

        val pillWidth by animateDpAsState(
            targetValue = if (selected) 56.dp else 0.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "NavRailPillWidth"
        )

        val iconScale by animateFloatAsState(
            targetValue = if (selected) 1.08f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "NavRailIconScale"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClickLambda),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.primaryContainer)
                )
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                        },
                        label = "NavRailIconTransition"
                    ) { isSelected ->
                        when {
                            item.imageVectorIcon != null -> Icon(
                                imageVector = item.imageVectorIcon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            item.selectedIconResId != null && isSelected -> Icon(
                                painter = painterResource(id = item.selectedIconResId),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            item.iconResId != null -> Icon(
                                painter = painterResource(id = item.iconResId),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(item.labelResId),
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

    private var lastProcessedClip: String? = null
    // shareLinkResult 在 setContent 中定义，通过回调传递
    private var pendingShareUrl: String? = null
    var onShareLinkDetected: ((com.theveloper.pixelplay.data.share.ShareResult) -> Unit)? = null
    private var hasCheckedClipboard = false

    override fun onResume() {
        super.onResume()
        if (!hasCheckedClipboard) {
            hasCheckedClipboard = true
            checkClipboardForShareLink()
        }
    }

    private fun checkClipboardForShareLink() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString()?.trim() ?: return
            if (text == lastProcessedClip) return
            val shareLink = com.theveloper.pixelplay.data.share.ShareLinkCodec.extractShareLink(text) ?: return

            lastProcessedClip = text
            lifecycleScope.launch {
                val result = shareLinkHandler.resolve(shareLink)
                when (result) {
                    is com.theveloper.pixelplay.data.share.ShareResult.Success -> {
                        if (result.matchedSongs.isNotEmpty() || result.totalCount > 0) {
                            val callback = onShareLinkDetected
                            if (callback != null) {
                                callback(result)
                            } else {
                                pendingShareUrl = shareLink
                            }
                        }
                    }
                    is com.theveloper.pixelplay.data.share.ShareResult.Error -> {}
                }
            }
        } catch (_: Exception) {}
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareLinkDialog(
    result: com.theveloper.pixelplay.data.share.ShareResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val success = result as? com.theveloper.pixelplay.data.share.ShareResult.Success ?: return
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    androidx.compose.material3.BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = cardShape,
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 标题区
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    color = colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                color = colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "分享链接",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSecondaryContainer,
                                )
                            }
                            Surface(
                                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                color = colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp).size(18.dp),
                                )
                            }
                        }

                        Text(
                            text = success.name,
                            style = typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val matchInfo = if (success.unmatchedCount > 0) {
                            "匹配到 ${success.matchedSongs.size}/${success.totalCount} 首（${success.unmatchedCount} 首未找到）"
                        } else {
                            "共 ${success.totalCount} 首歌曲"
                        }
                        Text(
                            text = matchInfo,
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 歌曲列表（模仿媒体库显示）
                val songsToShow = success.matchedSongs.take(5)
                if (songsToShow.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        color = colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            songsToShow.forEach { song ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Surface(
                                        shape = AbsoluteSmoothCornerShape(10.dp, 60),
                                        color = colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = typography.bodyMedium,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = song.displayArtist,
                                            style = typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (success.matchedSongs.size > 5) {
                                Text(
                                    text = "...还有 ${success.matchedSongs.size - 5} 首",
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        enabled = success.matchedSongs.isNotEmpty(),
                        shape = actionShape,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            text = "播放",
                            style = typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        shape = actionShape,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            text = "取消",
                            style = typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
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

/** 启动动画预算（ms）：实际时长 = 预算 − 设备已加载耗时，见 [StartupTiming] */
private const val SPLASH_FADE_BUDGET_MS = 600L
private const val EMERGE_FADE_BUDGET_MS = 600L
/**
 * 缩放进入（Rhythm 风格）的时长与缩放起点。
 *
 * Rhythm 原值是 `tween(1000, EaseOutCubic)` + `scaleIn(initialScale = 0.92f)`；
 * 实机观感偏"一闪而过"（EaseOutCubic 把大部分位移压在前 1/3），因此这里保留它的曲线，
 * 只把时长放慢到 1300ms、缩放起点放大到 0.85f，让放大过程真正看得见。
 */
private const val SCALE_FADE_BUDGET_MS = 1300L

/** 缩放进入的起始缩放（0.85f → 1f） */
private const val ENTER_SCALE_START = 0.85f

/**
 * 品牌启动页在「首帧真正绘制出来之后」的最短停留时间。
 * 从首帧计时而非进程启动计时 —— 否则冷启动时这段时间会被系统启动画面吃掉，
 * 用户看不到「XiangsuPlayer 浮出 → 主界面浮出」这两段进入动画。
 */
private const val BRAND_SPLASH_VISIBLE_MS = 500L

/**
 * 品牌页离场总时长：= max([BRAND_SPLASH_ZOOM_MS], [BRAND_SPLASH_FADE_DELAY_MS] + [BRAND_SPLASH_FADE_MS])。
 * 进入动画必须等它走完再启动 —— 否则离场层仍不透明地盖在最上层，
 * 而进入动画的缓动把大部分位移放在最前面，两段重叠时用户看不到进入动画。
 */
private const val BRAND_SPLASH_EXIT_MS = 540L

/** 文字放大离场时长（这段保持不透明，让「放大」真的被看到）。 */
private const val BRAND_SPLASH_ZOOM_MS = 460L

/** 渐隐开始前的延迟：前段只放大、不渐隐，避免文字还没放大就看不见了。 */
private const val BRAND_SPLASH_FADE_DELAY_MS = 240L

/** 渐隐时长。 */
private const val BRAND_SPLASH_FADE_MS = 300L

/** 品牌页放大离场的结束缩放（1f → 该值），与进入动画的反向缩放形成「推镜」衔接 */
private const val BRAND_SPLASH_EXIT_SCALE = 1.3f

/**
 * 品牌浮出页：承接「系统启动画面 → 主界面」之间的空档。
 * 底色取主题解析后的背景色，与随后的主界面完全同色，只有 App 名称文字浮出，
 * 对齐原版 XiangsuPlayer 的启动样式。
 */
@Composable
private fun StartupBrandSplash() {
    // 对齐原版 XiangsuPlayer 的启动样式：只有 App 名称文字浮出，无 logo、无转圈。
    //
    // ⚡ 底色必须跟随 MaterialTheme，而不是 XML 里写死的 @color/pixelplay_background：
    //   界面在 API 31+ 走系统动态取色，开启自定义调色盘时又走用户配色，
    //   静态色一旦与真实背景不一致，品牌页停留的这 500ms 就是一整块「奇怪的纯色色块」。
    //   跟随主题后品牌页与主界面同色，视觉上这层底色等于不存在。
    // 时长自适应：600ms 预算里扣掉设备已加载的耗时，加载慢的设备不会再多等一段固定动画。
    val durationMs = remember { StartupTiming.adaptiveDurationMs(SPLASH_FADE_BUDGET_MS) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing)
        )
    }
    // 浮出位移：从下方 18dp 处上浮到居中
    val riseDistancePx = with(LocalDensity.current) { 18.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // ⚡ 必须用「主题解析后的背景色」，不能用 XML 里写死的 @color/pixelplay_background：
            //   App 真实背景来自 PixelPlayTheme（系统动态取色 / 自定义调色盘 / 静态配色三种来源），
            //   写死的那个色（#F2F7FD / #0F1827）两个都偏蓝，与真实背景不一致时，
            //   品牌页就会先铺一整块「奇怪的蓝色色块」再淡入真实界面。
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            // 固定品牌名（不随语言变化），对齐原版 XiangsuPlayer 的启动文字
            text = "XiangsuPlayer",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 32.sp,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            // 只在绘制阶段读动画值，避免逐帧重组
            modifier = Modifier.graphicsLayer {
                val t = progress.value.coerceIn(0f, 1f)
                alpha = t
                // 浮出：上浮 + 从 0.92 轻微放大到 1.0
                translationY = (1f - t) * riseDistancePx
                val scale = 0.92f + 0.08f * t
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

/**
 * 进入主界面的一次性动画门。
 *
 * 内容在动画开始前就已经组合（只动 graphicsLayer 的 alpha/scale），
 * 避免"先播动画再组合重内容"把首帧推迟。
 *
 * - [StartupAnimationStyle.EMERGE]：原版 XiangsuPlayer 的浮出效果，600ms 淡入
 * - [StartupAnimationStyle.SCALE]：淡入 + 0.85→1.0 缩放（Rhythm 的 EaseOutCubic 曲线，
 *   时长在 Rhythm 的 1000ms 基础上放慢到 1300ms、缩放幅度加到 15%，观感更明显）
 * - [StartupAnimationStyle.NONE]：直接显示
 *
 * [start] 为 false 时保持完全透明（内容已组合好，只是不显示），等品牌页放大离场走完再启动动画。
 */
@Composable
private fun StartupEnterGate(
    style: StartupAnimationStyle,
    start: Boolean = true,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(style, start) {
        if (!start) return@LaunchedEffect
        if (style == StartupAnimationStyle.NONE) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // ⚡ 本动画在品牌页之后才开始，用完整预算即可（不必再扣进程已加载耗时，
        //   否则会被压到下限，用户看不到"浮出"）。
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (style == StartupAnimationStyle.EMERGE) {
                    EMERGE_FADE_BUDGET_MS.toInt()
                } else {
                    SCALE_FADE_BUDGET_MS.toInt()
                },
                easing = if (style == StartupAnimationStyle.EMERGE) LinearOutSlowInEasing else EaseOutCubic
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // progress.value 只在 graphicsLayer 的绘制阶段读取：逐帧只更新图层，
            // 不会触发 content() 重组（避免动画期间把主界面整棵重组一遍）。
            .graphicsLayer {
                alpha = progress.value
                // 缩放起点与品牌页离场终点反向：品牌页放大冲出、主界面从略小放大进入，形成推镜
                val scale = if (style == StartupAnimationStyle.SCALE) {
                    ENTER_SCALE_START + (1f - ENTER_SCALE_START) * progress.value
                } else {
                    1f
                }
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
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
