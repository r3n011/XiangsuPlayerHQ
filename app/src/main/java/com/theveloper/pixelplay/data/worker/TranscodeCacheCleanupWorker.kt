package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.TranscodeCacheManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * 转码缓存清理 Worker
 *
 * 定期检查并清理过期的转码缓存文件：
 * - 默认每 12 小时执行一次
 * - 根据用户设置的 TTL（最后播放后 X 天）清理过期缓存
 * - 同时清理孤立的数据库记录（文件已不存在但数据库有记录）
 */
@HiltWorker
class TranscodeCacheCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "transcode_cache_cleanup"
        const val DEFAULT_TTL_DAYS = 3

        /**
         * 清理 Worker 的输入参数键
         */
        const val INPUT_TTL_MS = "ttl_ms"
        const val INPUT_FORCE_CLEAN = "force_clean"
    }

    override suspend fun doWork(): Result {
        Timber.d("TranscodeCacheCleanupWorker: Starting cache cleanup...")

        return try {
            // 1. 获取 TTL 设置
            val ttlMs = inputData.getLong(
                INPUT_TTL_MS,
                userPreferencesRepository.getTranscodeCacheTtl()
            )
            val forceClean = inputData.getBoolean(INPUT_FORCE_CLEAN, false)

            // 2. 检查 TTL 是否为永不过期
            if (ttlMs >= Long.MAX_VALUE && !forceClean) {
                Timber.d("TranscodeCacheCleanupWorker: TTL is set to never, skipping cleanup")
                return Result.success()
            }

            // 3. 执行清理
            val deletedCount = if (forceClean) {
                // 强制清理：清除所有缓存
                clearAllCaches()
            } else {
                // 按 TTL 清理
                TranscodeCacheManager.cleanExpiredByTtl(ttlMs)
            }

            Timber.d("TranscodeCacheCleanupWorker: Cleaned $deletedCount cache entries")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TranscodeCacheCleanupWorker: Cleanup failed")
            Result.retry()
        }
    }

    /**
     * 清除所有缓存
     */
    private suspend fun clearAllCaches(): Int {
        val count = TranscodeCacheManager.getCacheCount()
        TranscodeCacheManager.clearAllCache()
        return count
    }
}
