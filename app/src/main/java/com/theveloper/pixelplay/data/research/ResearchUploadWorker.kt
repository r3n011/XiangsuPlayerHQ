package com.theveloper.pixelplay.data.research

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 听歌事件上传 Worker：周期 6 小时到点检查（是否到 nextUploadAfter、首传门槛是否达标），
 * 满足才真正上传；「仅 Wi-Fi」用 UNMETERED 约束表达（项目惯例，仿 UpdateCheckWorker）。
 *
 * 资格只剩 24h 的紧急覆盖不走这里 —— 网络约束会推迟执行，由
 * [ResearchRepository.urgentUploadIfExpiring] 在 App 启动/回前台时直接执行。
 */
@HiltWorker
class ResearchUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ResearchRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        runCatching { repository.uploadIfDue() }
            .onFailure { Timber.w(it, "ResearchUploadWorker: upload attempt failed") }
        // 无论成败都返回 success：失败已保留本地队列，由下次到点重试
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "research_upload_periodic"
        private const val PERIODIC_HOURS = 6L

        /** 注册/更新周期任务；wifiOnly 变化时由仓库层重新调度 */
        fun ensureScheduled(workManager: WorkManager, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ResearchUploadWorker>(
                PERIODIC_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
