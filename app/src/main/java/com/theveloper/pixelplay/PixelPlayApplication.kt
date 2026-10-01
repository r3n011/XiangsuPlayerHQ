package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.theveloper.pixelplay.data.analytics.UmengAnalytics
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnosticsController
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.AppStartup
import com.theveloper.pixelplay.utils.StartupTiming
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.TelegramCoilFetcher.Factory>

    @Inject
    lateinit var navidromeCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.NavidromeCoilFetcher.Factory>

    @Inject
    lateinit var jellyfinCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.JellyfinCoilFetcher.Factory>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var telegramRepository: dagger.Lazy<TelegramRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var advancedPerformanceDiagnosticsController: dagger.Lazy<AdvancedPerformanceDiagnosticsController>

    @Inject
    lateinit var lxJsEngine: dagger.Lazy<LxJsEngine>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"

        /** 落雪 JS 引擎预加载前的静置时间：避开首屏后的入场动画，避免抢 CPU/IO */
        private const val LX_PRELOAD_DELAY_MS = 3_000L

        @Volatile
        private var instance: PixelPlayApplication? = null

        /** 获取应用全局 Context（Hilt EntryPoint、ContentResolver 等场景使用，非 UI 上下文） */
        @JvmStatic
        fun appContext(): Context = instance!!

        /** 与 attachBaseContext 同时设置，保证最早可用 */
        private fun setInstance(app: PixelPlayApplication) {
            instance = app
        }
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            runCatching {
                libraryStateHolder.get().restoreAfterTrimIfNeeded()
            }.onFailure { e ->
                android.util.Log.e("PixelPlay", "Failed to restore library state: ${e.message}")
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        // ⚡ 最早的应用代码点：记录进程启动时间，供启动动画按设备实际加载耗时自适应用
        StartupTiming.markProcessStart()

        // EARLIEST POSSIBLE POINT to install crash handler
        // (before Hilt injects, before any native libs load, before anything else)
        try {
            CrashHandler.install(base)
            android.util.Log.i("PixelPlay", "CrashHandler installed in attachBaseContext")
        } catch (t: Throwable) {
            // Absolute last resort - don't let crash handler crash us
            android.util.Log.e("PixelPlay", "Failed to install crash handler: ${t.message}")
        }

        setInstance(this)

        try {
            super.attachBaseContext(AppLocaleManager.wrapContext(base))
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "attachBaseContext failed: ${t.message}")
            super.attachBaseContext(base) // fallback without locale wrapping
        }
    }

    override fun onCreate() {
        try {
            super.onCreate()
        } catch (t: Throwable) {
            // Hilt injection failed or other critical failure during super.onCreate()
            android.util.Log.e("PixelPlay", "FATAL ERROR in super.onCreate(): ${t.message}", t)
            // If Hilt fails, we try a minimal startup without Hilt dependencies
            minimalStartupFallback(t)
            return
        }

        try {
            // Benchmark variant intentionally restarts/kills app process during tests.
            // Avoid persisting those events as user-facing crash reports.
            if (BuildConfig.BUILD_TYPE != "benchmark") {
                CrashHandler.install(this)
            }

            // Timber logging setup
            try {
                if (BuildConfig.DEBUG) {
                    Timber.plant(Timber.DebugTree())
                } else {
                    // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
                    Timber.plant(ReleaseTree())
                }
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to init Timber: ${t.message}")
            }

            // 网易云本地 SDK 初始化（App 进程内直接调官方接口，无需外部代理服务器）
            // ⚡ 冷启动优化：install 内部会 getSharedPreferences 同步读一次磁盘（首启还要建文件），
            //    与首屏完全无关，放到后台线程执行即可 —— 任何真正的接口调用都发生在用户交互之后，
            //    远晚于这里完成；install 本身可重入，不会重复建立会话。
            try {
                startupScope.launch {
                    runCatching { net.moriafly.ncm.NcmApi.install(this@PixelPlayApplication) }
                        .onFailure { t ->
                            android.util.Log.e("PixelPlay", "Failed to init NcmApi: ${t.message}")
                        }
                }
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to schedule NcmApi init: ${t.message}")
            }

            // Notification channel (for foreground music playback service)
            // ⚠️ 必须留在启动路径上：Android 8+ 向不存在的渠道发通知会静默丢弃，
            //    而前台播放服务可能在本进程无 UI（无首帧）时被系统拉起。
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "XiangsuPlayer Music Playback",
                        NotificationManager.IMPORTANCE_LOW
                    )
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.createNotificationChannel(channel)
                }
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to create notification channel: ${t.message}")
            }

            // ⚡ 冷启动优化：下面两项与首屏完全无关，统一延后到首帧绘制完成之后执行。
            //    冷启动时主线程在首帧前的每一毫秒都直接等于用户可见的等待，
            //    这里能省下统计 SDK 初始化与 JS 引擎预热的开销。
            AppStartup.runAfterFirstFrame {
                // 友盟移动统计 U-App：preInit 必须早于 init，且两者都必须在主线程。
                // 延后到首帧后不影响统计准确性（用户此刻还没看到界面）。
                try {
                    UmengAnalytics.preInit(this)
                    UmengAnalytics.init(this)
                    UmengAnalytics.sendOnboardingTestEvent(this)
                } catch (t: Throwable) {
                    android.util.Log.e("PixelPlay", "Failed to init Umeng analytics: ${t.message}")
                }

                // ⚡ 落雪 JS 引擎预加载：首帧后继续静置一段时间再后台初始化音源脚本
                //    （QuickJS 上下文创建 + 脚本解析会抢 CPU/IO，过早启动会拖慢首屏后的
                //    几帧动画）。用户点到搜索页/首次播放远晚于此，按需调用仍会即时等待加载。
                try {
                    startupScope.launch {
                        kotlinx.coroutines.delay(LX_PRELOAD_DELAY_MS)
                        runCatching { lxJsEngine.get().awaitReady(25_000) }
                            .onFailure { t ->
                                android.util.Log.e("PixelPlay", "Failed to preload LxJsEngine: ${t.message}")
                            }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("PixelPlay", "Failed to schedule LxJsEngine preload: ${t.message}")
                }
            }

            // Process lifecycle observer - for background/foreground state tracking
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to add lifecycle observer: ${t.message}")
            }

            // Background startup tasks: cache migration, preferences loading
            // ⚡ Optimization: Defer non-critical initialization to avoid blocking startup
            startupScope.launch {
                // Priority 1: Load essential preferences first (required for UI)
                runCatching {
                    val savedLimit = runCatching {
                        userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
                    }.getOrNull()
                    if (savedLimit != null) {
                        AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
                    }
                }.onFailure { e ->
                    android.util.Log.e("PixelPlay", "Failed to load preferences: ${e.message}")
                }

                // Priority 2: Migrate cache (can wait)
                runCatching {
                    AlbumArtUtils.migrateLegacyCacheLocation(this@PixelPlayApplication)
                }.onFailure { e ->
                    android.util.Log.e("PixelPlay", "Failed to migrate album art cache: ${e.message}")
                }
            }

            // ⚡ Optimization: Defer diagnostics to after first frame
            startupScope.launch {
                kotlinx.coroutines.delay(1000)
                runCatching {
                    advancedPerformanceDiagnosticsController.get().start(startupScope)
                }.onFailure { e ->
                    android.util.Log.e("PixelPlay", "Failed to start diagnostics: ${e.message}")
                }
            }

            android.util.Log.i("PixelPlay", "PixelPlayApplication started successfully")

        } catch (t: Throwable) {
            // Catch any unexpected errors during app startup
            android.util.Log.e("PixelPlay", "FATAL ERROR in onCreate(): ${t.message}", t)
        }
    }

    /**
     * Fallback startup if Hilt injection fails catastrophically.
     * We do the absolute minimum to keep the app from crashing immediately.
     */
    private fun minimalStartupFallback(cause: Throwable) {
        android.util.Log.e("PixelPlay", "Attempting minimal startup fallback after: ${cause.message}")
        try {
            // Just ensure notification channel exists for music playback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "XiangsuPlayer Music Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Minimal fallback also failed: ${t.message}")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return try {
            imageLoader.get().newBuilder()
                // ⚡ 保留 20% 堆内存缓存；但不再禁用硬件位图：
                //   allowHardware(false) 会让每张封面都走软件位图（Skia 每帧上传纹理），
                //   列表快速上滑（媒体库网格/歌曲列表）时会明显掉帧 —— 这是原版没有的设置。
                .allowHardware(true)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20)
                        .build()
                }
                .components {
                    runCatching { add(localArtworkCoilFetcherFactory.get()) }
                        .onFailure { android.util.Log.e("PixelPlay", "Failed to add localArtwork fetcher: ${it.message}") }
                    runCatching { add(telegramCoilFetcherFactory.get()) }
                        .onFailure { android.util.Log.e("PixelPlay", "Failed to add telegram fetcher: ${it.message}") }
                    runCatching { add(navidromeCoilFetcherFactory.get()) }
                        .onFailure { android.util.Log.e("PixelPlay", "Failed to add navidrome fetcher: ${it.message}") }
                    runCatching { add(jellyfinCoilFetcherFactory.get()) }
                        .onFailure { android.util.Log.e("PixelPlay", "Failed to add jellyfin fetcher: ${it.message}") }
                }
                .build()
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to create ImageLoader: ${t.message}")
            // Fallback ImageLoader: 与原版一致开启硬件位图（列表滚动性能），并保留 20% 堆内存缓存
            ImageLoader.Builder(this)
                .crossfade(true)
                .allowHardware(true)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20)
                        .build()
                }
                .build()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // 不再主动 trim Coil 内存缓存。40MB 上限 + 禁用硬件位图已满足项目约束；
        // 手动 trim/clear 会导致 AsyncImagePainter 仍持有旧 drawable，而底层 bitmap 被逐出，
        // 从而出现封面/唱片墙等已显示图片变空白。交由 Coil 自行管理。

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching { themeStateHolder.get().trimMemory(level) }
                .onFailure { android.util.Log.e("PixelPlay", "themeStateHolder.trimMemory failed: ${it.message}") }
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching { artistImageRepository.get().clearCache() }
                .onFailure { android.util.Log.e("PixelPlay", "artistImageRepository.clearCache failed: ${it.message}") }
            runCatching { telegramRepository.get().clearMemoryCache() }
                .onFailure { android.util.Log.e("PixelPlay", "telegramRepository.clearMemoryCache failed: ${it.message}") }
            runCatching { MediaMetadataRetrieverPool.clear() }
                .onFailure { android.util.Log.e("PixelPlay", "MediaMetadataRetrieverPool.clear failed: ${it.message}") }
        }

        runCatching { libraryStateHolder.get().trimMemory(level) }
            .onFailure { android.util.Log.e("PixelPlay", "libraryStateHolder.trimMemory failed: ${it.message}") }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()

}