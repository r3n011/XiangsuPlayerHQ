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
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnosticsController
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import com.theveloper.pixelplay.presentation.viewmodel.LibraryStateHolder
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import com.theveloper.pixelplay.utils.TranscodeCacheManager
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

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
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
        // EARLIEST POSSIBLE POINT to install crash handler
        // (before Hilt injects, before any native libs load, before anything else)
        try {
            CrashHandler.install(base)
            android.util.Log.i("PixelPlay", "CrashHandler installed in attachBaseContext")
        } catch (t: Throwable) {
            // Absolute last resort - don't let crash handler crash us
            android.util.Log.e("PixelPlay", "Failed to install crash handler: ${t.message}")
        }

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

            // Notification channel (for foreground music playback service)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "PixelPlayer Music Playback",
                        NotificationManager.IMPORTANCE_LOW
                    )
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.createNotificationChannel(channel)
                }
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to create notification channel: ${t.message}")
            }

            // Process lifecycle observer - for background/foreground state tracking
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to add lifecycle observer: ${t.message}")
            }

            // Initialize TranscodeCacheManager early so cacheDir is available
            try {
                TranscodeCacheManager.init(this)
            } catch (t: Throwable) {
                android.util.Log.e("PixelPlay", "Failed to init TranscodeCacheManager: ${t.message}")
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
                    "PixelPlayer Music Playback",
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
            // Minimal fallback ImageLoader without custom components
            ImageLoader.Builder(this)
                .crossfade(true)
                .build()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runCatching {
            imageLoader.get().memoryCache?.trimMemory(level)
        }

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

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            runCatching { imageLoader.get().memoryCache?.clear() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .build()

}