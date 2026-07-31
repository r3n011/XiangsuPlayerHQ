package com.theveloper.pixelplay.utils

import android.content.Context
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 转码缓存管理器
 *
 * 缓存已转码的 WAV 文件，避免重复转码。
 * 使用文件路径 + 最后修改时间作为缓存键，确保文件更新后能自动失效。
 * 通过信号量控制并发转码，并提供可配置的自动清理策略。
 */
object TranscodeCacheManager {

    data class TranscodeCacheEntry(
        val cacheKey: String,
        val originalPath: String,
        val fileName: String,
        val fileSize: Long,
        val lastModified: Long
    )

    private const val TAG = "TranscodeCache"
    private const val DEFAULT_MAX_CACHE_SIZE_MB = 500L
    private const val DEFAULT_MAX_FILE_AGE = 24 * 60 * 60 * 1000L // 24小时过期
    private const val TRANSCODE_LOCK_TIMEOUT_SECONDS = 30L

    private var cacheDir: File? = null
    private val cacheLocks = mutableMapOf<String, Any>()
    private val transcodeLock = Semaphore(1)

    @Volatile
    private var maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_MB * 1024 * 1024
    @Volatile
    private var cleanupThresholdPercent: Int = 80
    @Volatile
    private var autoCleanupEnabled: Boolean = true

    /**
     * 初始化缓存目录
     */
    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "transcode_cache").apply {
                mkdirs()
            }
            Timber.d("$TAG: Cache dir initialized: ${cacheDir?.absolutePath}")
        }
    }

    /**
     * 配置缓存管理器参数
     */
    fun configure(
        maxCacheSizeMb: Int = (maxCacheSizeBytes / (1024 * 1024)).toInt(),
        cleanupThresholdPercent: Int = this.cleanupThresholdPercent,
        autoCleanupEnabled: Boolean = this.autoCleanupEnabled
    ) {
        this.maxCacheSizeBytes = maxCacheSizeMb.coerceIn(256, 4096).toLong() * 1024 * 1024
        this.cleanupThresholdPercent = cleanupThresholdPercent.coerceIn(50, 95)
        this.autoCleanupEnabled = autoCleanupEnabled
        Timber.d("$TAG: Configured maxSize=${this.maxCacheSizeBytes / (1024 * 1024)}MB, " +
                "threshold=${this.cleanupThresholdPercent}%, autoCleanup=${this.autoCleanupEnabled}")
    }

    /**
     * 带超时地获取转码锁，防止并发转码导致死锁
     * @return 是否成功获取锁
     */
    fun acquireTranscodeLockWithTimeout(timeoutSeconds: Long = TRANSCODE_LOCK_TIMEOUT_SECONDS): Boolean {
        return try {
            transcodeLock.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Timber.w(e, "$TAG: Interrupted while acquiring transcode lock")
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * 释放转码锁
     */
    fun releaseTranscodeLock() {
        try {
            if (transcodeLock.availablePermits() < 1) {
                transcodeLock.release()
                Timber.d("$TAG: Transcode lock released")
            }
        } catch (e: IllegalStateException) {
            Timber.w(e, "$TAG: Failed to release transcode lock")
        }
    }

    /**
     * 重置转码锁，用于锁被异常卡死时的恢复
     */
    fun resetTranscodeLock() {
        try {
            while (transcodeLock.availablePermits() < 1) {
                transcodeLock.release()
            }
            // 确保最终只有一个许可
            while (transcodeLock.availablePermits() > 1) {
                transcodeLock.acquire()
            }
            Timber.d("$TAG: Transcode lock reset, availablePermits=${transcodeLock.availablePermits()}")
        } catch (e: InterruptedException) {
            Timber.w(e, "$TAG: Interrupted while resetting transcode lock")
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 生成缓存键
     * 使用文件路径 + 最后修改时间的 MD5 作为缓存键
     */
    private fun generateCacheKey(filePath: String): String {
        val file = File(filePath)
        val lastModified = file.lastModified()
        val input = "$filePath:$lastModified"
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * 获取缓存文件
     * @return 缓存的 WAV 文件，如果不存在则返回 null
     */
    fun getCachedFile(filePath: String): File? {
        val dir = cacheDir ?: return null
        val cacheKey = generateCacheKey(filePath)
        val cachedFile = File(dir, "$cacheKey.wav")

        if (cachedFile.exists() && cachedFile.length() > 44) {
            // 检查文件是否过期
            val age = System.currentTimeMillis() - cachedFile.lastModified()
            if (age > DEFAULT_MAX_FILE_AGE) {
                Timber.d("$TAG: Cached file expired, deleting: ${cachedFile.name}")
                cachedFile.delete()
                return null
            }
            Timber.d("$TAG: Cache hit for $filePath")
            return cachedFile
        }
        return null
    }

    /**
     * 保存转码结果到缓存
     * @param filePath 原始文件路径
     * @param tempFile 临时转码文件
     * @return 缓存文件
     */
    fun cacheTranscodedFile(filePath: String, tempFile: File): File? {
        val dir = cacheDir ?: return null

        // 确保缓存目录存在
        dir.mkdirs()

        // 清理过期缓存
        cleanupExpiredCache()

        // 自动清理：当缓存超过阈值时清理最旧文件
        if (autoCleanupEnabled) {
            val thresholdBytes = (maxCacheSizeBytes * cleanupThresholdPercent / 100)
            val currentSize = calculateCacheSize()
            if (currentSize > thresholdBytes) {
                Timber.d("$TAG: Cache size $currentSize exceeds threshold $thresholdBytes, evicting")
                evictOldestFiles()
            }
        }

        val cacheKey = generateCacheKey(filePath)
        val cachedFile = File(dir, "$cacheKey.wav")

        try {
            // 删除旧的缓存
            if (cachedFile.exists()) {
                cachedFile.delete()
            }

            // 复制到缓存目录
            tempFile.copyTo(cachedFile, overwrite = true)

            // 保存元数据，用于在设置页展示原文件信息并支持单条删除
            saveCacheMetadata(cacheKey, filePath)

            Timber.d("$TAG: Cached file saved: ${cachedFile.name} (${cachedFile.length()} bytes)")
            return cachedFile
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cache file")
            return null
        }
    }

    /**
     * 保存缓存元数据（原文件路径等）
     */
    private fun saveCacheMetadata(cacheKey: String, originalPath: String) {
        val dir = cacheDir ?: return
        val metaFile = File(dir, "$cacheKey.json")
        try {
            val json = JSONObject().apply {
                put("originalPath", originalPath)
                put("cachedAt", System.currentTimeMillis())
            }
            metaFile.writeText(json.toString())
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to save cache metadata")
        }
    }

    /**
     * 获取或创建缓存锁，防止并发转码
     */
    fun getCacheLock(filePath: String): Any {
        val key = generateCacheKey(filePath)
        return synchronized(cacheLocks) {
            cacheLocks.getOrPut(key) { Any() }
        }
    }

    /**
     * 清理过期缓存
     */
    private fun cleanupExpiredCache() {
        val dir = cacheDir ?: return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            val age = now - file.lastModified()
            if (age > DEFAULT_MAX_FILE_AGE) {
                Timber.d("$TAG: Removing expired cache: ${file.name}")
                file.delete()
            }
        }
    }

    /**
     * 驱逐最旧的文件，直到缓存大小低于阈值
     */
    private fun evictOldestFiles() {
        val dir = cacheDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return

        val targetSize = maxCacheSizeBytes * cleanupThresholdPercent / 100
        var currentSize = calculateCacheSize()
        for (file in files) {
            if (currentSize <= targetSize) break
            currentSize -= file.length()
            file.delete()
            Timber.d("$TAG: Evicted oldest cache: ${file.name}")
        }
    }

    /**
     * 计算缓存总大小
     */
    fun calculateCacheSize(): Long {
        val dir = cacheDir ?: return 0
        return dir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * 获取所有带元数据的缓存条目，按缓存时间倒序排列
     */
    fun getCacheEntries(): List<TranscodeCacheEntry> {
        val dir = cacheDir ?: return emptyList()
        return dir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { metaFile ->
                val key = metaFile.nameWithoutExtension
                val wavFile = File(dir, "$key.wav")
                if (!wavFile.exists()) return@mapNotNull null

                val json = runCatching { JSONObject(metaFile.readText()) }.getOrNull()
                    ?: return@mapNotNull null
                val originalPath = json.optString("originalPath", "")
                if (originalPath.isBlank()) return@mapNotNull null

                TranscodeCacheEntry(
                    cacheKey = key,
                    originalPath = originalPath,
                    fileName = File(originalPath).name,
                    fileSize = wavFile.length(),
                    lastModified = wavFile.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    /**
     * 删除指定缓存条目（包括 WAV 文件与元数据）
     */
    fun deleteCacheEntry(cacheKey: String) {
        val dir = cacheDir ?: return
        File(dir, "$cacheKey.wav").delete()
        File(dir, "$cacheKey.json").delete()
        Timber.d("$TAG: Deleted cache entry $cacheKey")
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        val dir = cacheDir ?: return
        dir.listFiles()?.forEach { it.delete() }
        Timber.d("$TAG: All cache cleared")
    }

    /**
     * 获取缓存状态信息
     */
    fun getCacheInfo(): CacheInfo {
        val dir = cacheDir ?: return CacheInfo(0, 0, 0)
        val fileCount = dir.listFiles()?.size ?: 0
        val sizeBytes = calculateCacheSize()
        val sizeMb = sizeBytes / (1024 * 1024)
        return CacheInfo(fileCount, sizeBytes, sizeMb)
    }

    data class CacheInfo(
        val fileCount: Int,
        val sizeBytes: Long,
        val sizeMb: Long
    )
}
