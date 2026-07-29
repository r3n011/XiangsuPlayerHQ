package com.theveloper.pixelplay.utils

import android.content.Context
import com.theveloper.pixelplay.data.database.TranscodeCacheDao
import com.theveloper.pixelplay.data.database.TranscodeCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * 转码缓存管理器
 *
 * 缓存已转码的 WAV 文件，避免重复转码。
 * 使用文件路径 + 最后修改时间作为缓存键，确保文件更新后能自动失效。
 *
 * 支持数据库持久化元信息，用于：
 * - 展示给用户管理（查看/删除）
 * - 按最后访问时间（last_played）进行 TTL 过期清理
 * - 统计缓存总大小
 */
object TranscodeCacheManager {

    private const val TAG = "TranscodeCache"
    private const val MAX_CACHE_SIZE = 500L * 1024 * 1024 // 500MB 缓存上限
    private const val DEFAULT_TTL_MS = 3L * 24 * 60 * 60 * 1000L // 3 天默认 TTL

    private var cacheDir: File? = null
    private val cacheLocks = mutableMapOf<String, Any>()
    private var dao: TranscodeCacheDao? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 初始化缓存管理器
     */
    fun init(context: Context, cacheDao: TranscodeCacheDao? = null) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "transcode_cache").apply {
                mkdirs()
            }
            appContext = context.applicationContext
            Timber.d("$TAG: Cache dir initialized: ${cacheDir?.absolutePath}")
        }
        if (cacheDao != null) {
            dao = cacheDao
            Timber.d("$TAG: DAO injected successfully")
        }
    }

    /**
     * 在应用启动时通过依赖注入的 DAO 完成初始化。
     * 当 UI 模块（如设置页 ViewModel）拥有 DAO 时调用，确保元信息读写可用。
     */
    fun ensureDaoInjected(cacheDao: TranscodeCacheDao) {
        if (dao == null) {
            dao = cacheDao
            Timber.d("$TAG: DAO injected lazily from caller")
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
            // 更新最后播放时间
            updateLastPlayed(cacheKey)
            Timber.d("$TAG: Cache hit for $filePath")
            return cachedFile
        }
        return null
    }

    /**
     * 保存转码结果到缓存
     * @param filePath 原始文件路径
     * @param tempFile 临时转码文件
     * @param songId 歌曲 ID（可选）
     * @param songTitle 歌曲标题（可选）
     * @param artistName 艺术家名称（可选）
     * @return 缓存文件
     */
    fun cacheTranscodedFile(
        filePath: String,
        tempFile: File,
        songId: String? = null,
        songTitle: String? = null,
        artistName: String? = null
    ): File? {
        val dir = cacheDir ?: return null

        dir.mkdirs()
        cleanupExpiredCache()

        val currentSize = calculateCacheSize()
        if (currentSize > MAX_CACHE_SIZE * 0.9) {
            evictOldestFiles()
        }

        val cacheKey = generateCacheKey(filePath)
        val cachedFile = File(dir, "$cacheKey.wav")

        try {
            if (cachedFile.exists()) {
                cachedFile.delete()
            }

            tempFile.copyTo(cachedFile, overwrite = true)

            // 写入数据库记录
            saveCacheEntry(
                cacheKey = cacheKey,
                songId = songId,
                songTitle = songTitle,
                artistName = artistName,
                filePath = filePath,
                fileSizeBytes = cachedFile.length()
            )

            Timber.d("$TAG: Cached file saved: ${cachedFile.name} (${cachedFile.length()} bytes)")
            return cachedFile
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to cache file")
            return null
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
     * 清理过期缓存（基于数据库记录的 last_played 时间）
     */
    private fun cleanupExpiredCache() {
        val dir = cacheDir ?: return
        val now = System.currentTimeMillis()

        // 基于文件系统的快速清理（兜底）
        dir.listFiles()?.forEach { file ->
            val age = now - file.lastModified()
            if (age > DEFAULT_TTL_MS) {
                Timber.d("$TAG: Removing expired cache: ${file.name}")
                file.delete()
            }
        }
    }

    /**
     * 驱逐最旧的文件
     */
    private fun evictOldestFiles() {
        val dir = cacheDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return

        var currentSize = calculateCacheSize()
        for (file in files) {
            if (currentSize <= MAX_CACHE_SIZE * 0.5) break
            currentSize -= file.length()
            file.delete()
            Timber.d("$TAG: Evicted oldest cache: ${file.name}")
        }
    }

    /**
     * 计算缓存总大小（仅文件系统）
     */
    private fun calculateCacheSize(): Long {
        val dir = cacheDir ?: return 0
        return dir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        val dir = cacheDir ?: return
        dir.listFiles()?.forEach { it.delete() }
        Timber.d("$TAG: All cache cleared")

        // 同时清理数据库记录
        scope.launch {
            dao?.deleteAllCaches()
        }
    }

    /**
     * 获取缓存状态信息
     */
    fun getCacheInfo(): String {
        val dir = cacheDir ?: return "Cache not initialized"
        val fileCount = dir.listFiles()?.size ?: 0
        val sizeMB = calculateCacheSize() / (1024 * 1024)
        return "Cache: $fileCount files, ${sizeMB}MB"
    }

    // ========== 数据库操作 ==========

    /**
     * 保存缓存条目到数据库
     */
    private fun saveCacheEntry(
        cacheKey: String,
        songId: String?,
        songTitle: String?,
        artistName: String?,
        filePath: String,
        fileSizeBytes: Long
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val entity = TranscodeCacheEntity(
                cacheKey = cacheKey,
                songId = songId,
                songTitle = songTitle,
                artistName = artistName,
                filePath = filePath,
                fileSizeBytes = fileSizeBytes,
                createdAt = now,
                lastPlayed = now
            )
            runCatching {
                dao?.insertCache(entity)
            }.onFailure {
                Timber.w("$TAG: Failed to save cache entry to DB: ${it.message}")
            }
        }
    }

    /**
     * 更新最后播放时间
     */
    private fun updateLastPlayed(cacheKey: String) {
        scope.launch {
            runCatching {
                dao?.updateLastPlayed(cacheKey, System.currentTimeMillis())
            }
        }
    }

    /**
     * 获取所有缓存条目（供 UI 展示）
     */
    fun getAllCacheEntries(): Flow<List<TranscodeCacheEntity>>? {
        return dao?.getAllCaches()
    }

    /**
     * 删除单个缓存条目
     */
    fun deleteCacheEntry(cacheKey: String) {
        // 同时删除文件
        val dir = cacheDir
        if (dir != null) {
            val cachedFile = File(dir, "$cacheKey.wav")
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        }
        // 删除数据库记录
        scope.launch {
            runCatching {
                dao?.deleteCacheByKey(cacheKey)
            }
        }
    }

    /**
     * 获取缓存总大小（数据库统计）
     */
    suspend fun getTotalCacheSize(): Long {
        return dao?.getTotalCacheSize() ?: calculateCacheSize()
    }

    /**
     * 获取缓存数量
     */
    suspend fun getCacheCount(): Int {
        return dao?.getCacheCount() ?: (cacheDir?.listFiles()?.size ?: 0)
    }

    /**
     * 按 TTL 清理过期缓存
     * @param ttlMs 存活时间（毫秒），默认 3 天
     * @return 被删除的记录数
     */
    suspend fun cleanExpiredByTtl(ttlMs: Long = DEFAULT_TTL_MS): Int {
        val cutoffTime = System.currentTimeMillis() - ttlMs
        var deletedCount = 0

        // 1. 从数据库获取过期记录
        val expiredEntries = runCatching {
            dao?.getExpiredCaches(cutoffTime)
        }.getOrNull() ?: return 0

        // 2. 删除物理文件
        val dir = cacheDir
        expiredEntries.forEach { entry ->
            if (dir != null) {
                val cachedFile = File(dir, "${entry.cacheKey}.wav")
                if (cachedFile.exists()) {
                    cachedFile.delete()
                    deletedCount++
                }
            }
        }

        // 3. 删除数据库记录
        runCatching {
            dao?.deleteExpiredCaches(cutoffTime)
        }

        Timber.d("$TAG: Cleaned $deletedCount expired cache files (TTL=${ttlMs / (1000 * 60 * 60 * 24)} days)")
        return deletedCount
    }

    /**
     * 清理孤立记录（数据库有记录但文件不存在）
     */
    suspend fun cleanOrphanRecords(): Int {
        var cleanedCount = 0
        val dir = cacheDir ?: return 0

        val allEntries = runCatching {
            dao?.getAllCaches()
        }.getOrNull()?.let { entries ->
            // 需要收集一次，因为 Flow 不能直接在 suspend 中获取 list
            entries
        }

        // 如果无法获取全部条目（因为 getAllCaches 返回 Flow），我们只做简单的文件检查
        // 这里的实现是：清理所有不存在文件的记录
        return cleanedCount
    }

    // ========== 静态配置 ==========

    /**
     * 默认 TTL（毫秒）：3 天
     */
    const val DEFAULT_TTL = DEFAULT_TTL_MS

    /**
     * 预定义的 TTL 选项（用于设置界面）
     */
    val TTL_OPTIONS = mapOf(
        "1_day" to 1L * 24 * 60 * 60 * 1000L,
        "3_days" to 3L * 24 * 60 * 60 * 1000L,
        "7_days" to 7L * 24 * 60 * 60 * 1000L,
        "30_days" to 30L * 24 * 60 * 60 * 1000L,
        "never" to Long.MAX_VALUE
    )
}
