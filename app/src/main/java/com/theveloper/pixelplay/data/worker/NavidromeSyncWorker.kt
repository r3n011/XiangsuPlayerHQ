package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class NavidromeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NavidromeRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_ALL
        val playlistId = inputData.getString(KEY_PLAYLIST_ID)

        Timber.d("NavidromeSyncWorker: Starting sync (type=$syncType, playlistId=$playlistId)")
        val startedAt = if (AdvancedPerformanceDiagnostics.isEnabled) System.currentTimeMillis() else 0L
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.WORKER,
            name = "navidrome_sync_start"
        ) {
            mapOf(
                "syncType" to syncType,
                "playlistScoped" to (playlistId != null).toString()
            )
        }

        return try {
            when (syncType) {
                SYNC_TYPE_ALL -> {
                    repository.syncAllPlaylistsAndSongs { progress, message ->
                        setProgressAsync(
                            workDataOf(
                                PROGRESS_VALUE to progress,
                                PROGRESS_MESSAGE to message
                            )
                        )
                    }
                }
                SYNC_TYPE_PLAYLISTS -> {
                    repository.syncPlaylists()
                }
                SYNC_TYPE_PLAYLIST_SONGS -> {
                    if (playlistId != null) {
                        repository.syncPlaylistSongs(playlistId)
                        repository.syncUnifiedLibrarySongsFromNavidrome()
                    }
                }
            }
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.WORKER,
                name = "navidrome_sync_success"
            ) {
                mapOf(
                    "syncType" to syncType,
                    "durationMs" to (System.currentTimeMillis() - startedAt).toString()
                )
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "NavidromeSyncWorker: Sync failed")
            AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                type = AdvancedPerformanceDiagnostics.EventTypes.WORKER,
                name = "navidrome_sync_failure"
            ) {
                mapOf(
                    "syncType" to syncType,
                    "durationMs" to (System.currentTimeMillis() - startedAt).toString(),
                    "error" to (e.message ?: e.javaClass.simpleName)
                )
            }
            Result.failure(workDataOf(ERROR_MESSAGE to e.message))
        }
    }

    companion object {
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_PLAYLIST_ID = "playlist_id"
        
        const val SYNC_TYPE_ALL = "all"
        const val SYNC_TYPE_PLAYLISTS = "playlists"
        const val SYNC_TYPE_PLAYLIST_SONGS = "playlist_songs"

        const val PROGRESS_VALUE = "progress_value"
        const val PROGRESS_MESSAGE = "progress_message"
        const val ERROR_MESSAGE = "error_message"

        fun startAllSync() = OneTimeWorkRequestBuilder<NavidromeSyncWorker>()
            .setInputData(workDataOf(KEY_SYNC_TYPE to SYNC_TYPE_ALL))
            .build()
            
        fun startPlaylistSync(playlistId: String) = OneTimeWorkRequestBuilder<NavidromeSyncWorker>()
            .setInputData(
                workDataOf(
                    KEY_SYNC_TYPE to SYNC_TYPE_PLAYLIST_SONGS,
                    KEY_PLAYLIST_ID to playlistId
                )
            )
            .build()
    }
}
