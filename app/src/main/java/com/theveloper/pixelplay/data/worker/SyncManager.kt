package com.theveloper.pixelplay.data.worker

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.AppStartup
import com.theveloper.pixelplay.utils.buildLocalAudioSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.data.observer.MediaStoreObserver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Data class representing the progress of the sync operation.
 */
data class SyncProgress(
    val isRunning: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val isCompleted: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE
) {
    enum class SyncPhase {
        IDLE,
        FETCHING_MEDIASTORE,
        PROCESSING_FILES,
        SAVING_TO_DATABASE,
        SCANNING_LRC,
        CLEANING_CACHE,
        SYNCING_CLOUD,
        COMPLETING
    }

    val progress: Float
        get() = if (totalCount > 0) currentCount.toFloat() / totalCount else 0f

    val hasProgress: Boolean
        get() = totalCount > 0
    }

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaStoreObserver: MediaStoreObserver
) {
    // ⚡ 冷启动优化：WorkManager 初始化（内部会建 DB/调度器/追踪器）改为惰性，
    //    不再在 Hilt 注入 SyncManager 时同步发生，而是等真正用到才初始化。
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaStoreAutoSyncJob: Job? = null
    private val autoSyncLock = Any()
    // In-memory only: lives in this @Singleton for the process lifetime, so no leak.
    @Volatile
    private var lastForegroundSyncTime = 0L

    // EXPONE UN FLOW<BOOLEAN> SIMPLE
    val isSyncing: Flow<Boolean> =
        // flow { emitAll(...) }：把 getWorkInfosForUniqueWorkFlow 的调用推迟到真正开始收集时，
        // 配合 workManager 的惰性初始化，避免构造 SyncManager 就把 WorkManager 拉起来。
        flow { emitAll(workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)) }
            .map { workInfos ->
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
                // A freshly enqueued worker (runAttemptCount == 0) is about to start, so it
                // counts as syncing. An ENQUEUED worker with runAttemptCount > 0 is sitting in
                // retry backoff — and the only retry path is SyncWorker deferring an INCREMENTAL
                // sync while playback is active (see SyncWorker.doWork). It does no work during
                // that window (up to ~16 min of exponential backoff), so we keep the
                // "Syncing library…" indicator off instead of showing it indefinitely.
                val isFreshlyEnqueued = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0
                }
                isRunning || isFreshlyEnqueued
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    init {
        // ⚡ 冷启动优化：SyncManager 在 MainActivity.super.onCreate() 的 Hilt 注入阶段就被构造，
        //    但下面这些工作（周期任务调度、前台/存储观察者注册）与首屏完全无关。
        //    延后到首帧绘制完成之后再执行，把这段时间还给首帧。
        AppStartup.runAfterFirstFrame {
            observeStorageChanges()
            observeAppForeground()
            schedulePeriodicMaintenance()
            // 后台自动更新：按用户频率（daily/weekly）周期检查并静默下载 APK
            UpdateCheckWorker.schedule(context, userPreferencesRepository, sharingScope)
        }
    }

    /**
     * Schedules the once-a-day heavy maintenance (LRC/cache/cloud). Uses a dedicated unique
     * name distinct from [SyncWorker.WORK_NAME], so it never drives the foreground sync
     * indicator. KEEP preserves the existing schedule across launches.
     */
    private fun schedulePeriodicMaintenance() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.periodicMaintenanceWork()
        )
    }

    /**
     * Flow that exposes the detailed sync progress including song count.
     */
    val syncProgress: Flow<SyncProgress> =
        // 同上：延后 WorkManager 调用与初始化
        flow { emitAll(workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)) }
            .map { workInfos ->
                val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                val enqueuedWork = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }

                when {
                    runningWork != null -> {
                        val current = runningWork.progress.getInt(SyncWorker.PROGRESS_CURRENT, 0)
                        val total = runningWork.progress.getInt(SyncWorker.PROGRESS_TOTAL, 0)
                        val phaseOrdinal = runningWork.progress.getInt(SyncWorker.PROGRESS_PHASE, 0)
                        val phase = try {
                            SyncProgress.SyncPhase.entries[phaseOrdinal]
                        } catch (e: IndexOutOfBoundsException) {
                            SyncProgress.SyncPhase.IDLE
                        }
                        SyncProgress(
                            isRunning = true,
                            currentCount = current,
                            totalCount = total,
                            isCompleted = false,
                            phase = phase
                        )
                    }
                    succeededWork != null -> {
                        val total = succeededWork.outputData.getInt(SyncWorker.OUTPUT_TOTAL_SONGS, 0)
                        SyncProgress(
                            isRunning = false,
                            currentCount = total,
                            totalCount = total,
                            isCompleted = true,
                            phase = SyncProgress.SyncPhase.COMPLETING
                        )
                    }
                    enqueuedWork != null -> {
                        // Mirror isSyncing: a retry-backoff enqueue (runAttemptCount > 0) is a
                        // sync deferred while playback is active and does no work, so don't
                        // surface it as running. Only a fresh enqueue waiting to start does.
                        if (enqueuedWork.runAttemptCount == 0) {
                            SyncProgress(isRunning = true, isCompleted = false, phase = SyncProgress.SyncPhase.IDLE)
                        } else {
                            SyncProgress()
                        }
                    }
                    else -> SyncProgress()
                }
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    /**
     * Emits `true` while the worker is in the early "library changes" phases —
     * scanning MediaStore for added/removed/modified files and writing them to the
     * unified DB. This is what powers the pull-to-refresh indicator: the UI only
     * needs to confirm that local additions/deletions have landed.
     */
    val isFetchingChanges: Flow<Boolean> = syncProgress
        .map { progress ->
            progress.isRunning && progress.phase in CHANGE_PHASES
        }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    /**
     * Emits `true` while the worker is performing background maintenance that does
     * not gate the user's pull-to-refresh gesture: LRC scanning, album-art cache
     * cleanup, and cloud-source synchronization. This drives the slim linear
     * indicator under [LibraryActionRow].
     */
    val isPerformingMaintenance: Flow<Boolean> = syncProgress
        .map { progress ->
            progress.isRunning && progress.phase in MAINTENANCE_PHASES
        }
        .distinctUntilChanged()
        .shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    fun sync() {
        sharingScope.launch {
            val now = System.currentTimeMillis()
            val lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()
            val shouldRunSync =
                lastSyncTimestamp <= 0L || (now - lastSyncTimestamp) >= MIN_SYNC_INTERVAL_MS

            if (!shouldRunSync) {
                val ageSeconds = (now - lastSyncTimestamp) / 1000
                Log.d(TAG, "Skipping startup sync (last sync ${ageSeconds}s ago)")
                return@launch
            }

            Log.i(TAG, "Startup sync requested - Scheduling Incremental Sync")
            enqueueSyncWork(
                request = SyncWorker.incrementalSyncWork(),
                policy = ExistingWorkPolicy.KEEP,
                notifyObserver = false
            )
        }
    }

    /**
     * Performs an incremental sync, only processing files that have changed
     * since the last sync. Much faster for large libraries with few changes.
     * This is the recommended sync method for pull-to-refresh actions.
     */
    fun incrementalSync() {
        Log.i(TAG, "Incremental sync requested - Scheduling incremental worker")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Performs a full library rescan, ignoring the last sync timestamp.
     * Use this when the user explicitly wants to force a complete rescan.
     */
    fun fullSync() {
        Log.i(TAG, "Full sync requested - Scheduling full sync worker")
        enqueueSyncWork(
            request = SyncWorker.fullSyncWork(),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all existing data including user edits (lyrics, etc.) and rescans.
     * Use when database is corrupted or songs are missing.
     */
    fun rebuildDatabase() {
        Log.i(TAG, "Rebuild database requested - Scheduling rebuild worker")
        enqueueSyncWork(
            request = SyncWorker.rebuildDatabaseWork(),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /**
     * Fuerza una nueva sincronización, reemplazando cualquier trabajo de sincronización
     * existente. Ideal para el botón de "Refrescar Biblioteca".
     */
    fun forceRefresh() {
        Log.i(TAG, "Force refresh requested - Scheduling incremental worker")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    private fun observeStorageChanges() {
        sharingScope.launch {
            mediaStoreObserver.externalMediaStoreChanges.collect {
                scheduleLocalAutoSync()
            }
        }
    }

    private fun scheduleLocalAutoSync() {
        synchronized(autoSyncLock) {
            mediaStoreAutoSyncJob?.cancel()
            mediaStoreAutoSyncJob = sharingScope.launch {
                runLocalAutoSyncAfterDebounce()
            }
        }
    }

    private suspend fun runLocalAutoSyncAfterDebounce() {
        delay(MEDIASTORE_CHANGE_DEBOUNCE_MS)
        // 轻量 staleness 门控：MediaStore 变化事件可能为误报/重复，
        // 只有真实存在新增/修改/删除时才算 stale，避免无意义全量遍历。
        if (!isLibraryStale()) {
            Log.d(TAG, "MediaStore change detected but library is not stale; skipping sync")
            return
        }
        Log.i(TAG, "Storage change detected - scheduling local incremental sync")
        enqueueSyncWork(
            request = SyncWorker.incrementalSyncWork(runMaintenance = false),
            policy = ExistingWorkPolicy.KEEP,
            notifyObserver = false
        )
    }

    /**
     * Lightweight staleness check (mirrors Rhythm's [isLibraryStale]).
     *
     * Instead of a full MediaStore traversal, only projects `_ID` to compare the
     * current song count against the count captured at the last successful sync,
     * then checks whether any file has a DATE_ADDED/DATE_MODIFIED newer than the
     * last sync. Returns `false` (not stale) when nothing changed, letting callers
     * skip the SyncWorker entirely — no full query, no LRC scan, no DB writes.
     */
    private suspend fun isLibraryStale(): Boolean {
        val lastSync = userPreferencesRepository.getLastSyncTimestamp()
        // 从未成功同步过（首装）：必须同步一次建立基准
        if (lastSync <= 0L) {
            Log.d(TAG, "Library stale: never synced")
            return true
        }

        val minSongDurationMs = userPreferencesRepository.getMinSongDuration()
        val prefs = context.getSharedPreferences(LIBRARY_SCAN_PREFS, Context.MODE_PRIVATE)
        val (selection, selectionArgs) = buildLocalAudioSelection(minSongDurationMs)

        // 1) Count comparison: current MediaStore audio count vs. last successful scan.
        val currentCount = runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                selection,
                selectionArgs,
                null
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
        val lastCount = prefs.getInt(KEY_LAST_SCAN_MEDIASTORE_COUNT, -1)
        if (lastCount == -1) {
            // No historical baseline: persist it and force one sync to establish it.
            prefs.edit().putInt(KEY_LAST_SCAN_MEDIASTORE_COUNT, currentCount).apply()
            Log.d(TAG, "Library stale: no historical MediaStore count, persisting $currentCount")
            return true
        }
        if (currentCount != lastCount) {
            Log.d(TAG, "Library stale: MediaStore count changed ($lastCount -> $currentCount)")
            return true
        }

        // 2) Timestamp check: any file added/modified after the last sync.
        val lastSyncSeconds = lastSync / 1000L
        val staleSelection =
            "$selection AND (${MediaStore.Audio.Media.DATE_ADDED} > ? OR ${MediaStore.Audio.Media.DATE_MODIFIED} > ?)"
        val staleArgs = selectionArgs + arrayOf(lastSyncSeconds.toString(), lastSyncSeconds.toString())
        val hasNewerFiles = runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                staleSelection,
                staleArgs,
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (hasNewerFiles) {
            Log.d(TAG, "Library stale: newer files found after last sync")
        }
        return hasNewerFiles
    }

    private fun observeAppForeground() {
        // ProcessLifecycleOwner is application-scoped; the observer and this @Singleton both
        // live for the whole process, so registering once here cannot leak.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                maybeRunForegroundCatchUpSync()
            }
        })
    }

    /**
     * Fast, maintenance-free incremental sync triggered when the app returns to the
     * foreground. Catches files MediaStore indexed while we were backgrounded (the
     * ContentObserver is only registered in the foreground). Guarded by an in-memory
     * cooldown so quick minimize/restore cycles don't pile up redundant work.
     */
    private fun maybeRunForegroundCatchUpSync() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncTime < FOREGROUND_SYNC_COOLDOWN_MS) {
            Log.d(TAG, "Skipping foreground catch-up sync (cooldown active)")
            return
        }
        lastForegroundSyncTime = now
        sharingScope.launch {
            // 轻量 staleness 门控：媒体库无变化时完全跳过 SyncWorker，
            // 避免每次回前台都执行全量 MediaStore 遍历（功耗/内存优化）。
            if (!isLibraryStale()) {
                Log.d(TAG, "Skipping foreground catch-up sync (library not stale)")
                return@launch
            }
            Log.i(TAG, "Foreground catch-up - scheduling local incremental sync")
            enqueueSyncWork(
                request = SyncWorker.incrementalSyncWork(runMaintenance = false),
                policy = ExistingWorkPolicy.KEEP,
                notifyObserver = false
            )
        }
    }

    private fun enqueueSyncWork(
        request: OneTimeWorkRequest,
        policy: ExistingWorkPolicy,
        notifyObserver: Boolean = true
    ) {
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            policy,
            request
        )
        if (notifyObserver) {
            // Keep reactive MediaStore-based views in sync with manual refresh actions.
            mediaStoreObserver.forceRescan()
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val MEDIASTORE_CHANGE_DEBOUNCE_MS = 1_500L
        private const val FOREGROUND_SYNC_COOLDOWN_MS = 60_000L

        // 与 SyncWorker 共享的轻量 staleness 基准（MediaStore 歌曲总数）。
        internal const val LIBRARY_SCAN_PREFS = "library_scan_metadata"
        internal const val KEY_LAST_SCAN_MEDIASTORE_COUNT = "last_scan_mediastore_count"

        private val CHANGE_PHASES = setOf(
            SyncProgress.SyncPhase.IDLE,
            SyncProgress.SyncPhase.FETCHING_MEDIASTORE,
            SyncProgress.SyncPhase.PROCESSING_FILES,
            SyncProgress.SyncPhase.SAVING_TO_DATABASE
        )

        private val MAINTENANCE_PHASES = setOf(
            SyncProgress.SyncPhase.SCANNING_LRC,
            SyncProgress.SyncPhase.CLEANING_CACHE,
            SyncProgress.SyncPhase.SYNCING_CLOUD,
            SyncProgress.SyncPhase.COMPLETING
        )
    }
}
