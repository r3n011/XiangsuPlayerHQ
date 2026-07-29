package com.theveloper.pixelplay.utils

import android.content.Context
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * 转码缓存管理器
 * 
 * 缓存已转码的 WAV 文件，避免重复转码。
 * 使用文件路径 + 最后修改时间作为缓存键，确保文件更新后能自动失效。
 */
object TranscodeCacheManager {

    private const val TAG = "TranscodeCache"
    private const val MAX_CACHE_SIZE = 500L * 1024 * 1024 // 500MB 缓存上限
    private const val MAX_FILE_AGE = 24 * 60 * 60 * 1000L // 24小时过期

    private var cacheDir: File? = null
    private val cacheLocks = mutableMapOf<String, Any>()

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
            if (age > MAX_FILE_AGE) {
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
        
        // 检查缓存大小
        val currentSize = calculateCacheSize()
        if (currentSize > MAX_CACHE_SIZE * 0.9) {
            // 缓存接近上限，清理最旧的文件
            evictOldestFiles()
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
     * 清理过期缓存
     */
    private fun cleanupExpiredCache() {
        val dir = cacheDir ?: return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            val age = now - file.lastModified()
            if (age > MAX_FILE_AGE) {
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
     * 计算缓存总大小
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
}
