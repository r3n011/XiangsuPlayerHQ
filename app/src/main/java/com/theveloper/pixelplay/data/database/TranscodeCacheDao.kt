package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscodeCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entry: TranscodeCacheEntity)

    @Update
    suspend fun updateCache(entry: TranscodeCacheEntity)

    @Query("SELECT * FROM transcode_cache ORDER BY last_played DESC")
    fun getAllCaches(): Flow<List<TranscodeCacheEntity>>

    @Query("SELECT * FROM transcode_cache WHERE cache_key = :cacheKey")
    suspend fun getCacheByKey(cacheKey: String): TranscodeCacheEntity?

    @Query("SELECT * FROM transcode_cache WHERE last_played < :beforeTimestamp")
    suspend fun getExpiredCaches(beforeTimestamp: Long): List<TranscodeCacheEntity>

    @Query("DELETE FROM transcode_cache WHERE cache_key = :cacheKey")
    suspend fun deleteCacheByKey(cacheKey: String)

    @Query("DELETE FROM transcode_cache WHERE last_played < :beforeTimestamp")
    suspend fun deleteExpiredCaches(beforeTimestamp: Long): Int

    @Query("DELETE FROM transcode_cache")
    suspend fun deleteAllCaches()

    @Query("SELECT SUM(file_size_bytes) FROM transcode_cache")
    suspend fun getTotalCacheSize(): Long?

    @Query("SELECT COUNT(*) FROM transcode_cache")
    suspend fun getCacheCount(): Int

    @Query("SELECT * FROM transcode_cache WHERE file_path = :filePath")
    suspend fun getCacheByFilePath(filePath: String): TranscodeCacheEntity?

    @Query("UPDATE transcode_cache SET last_played = :timestamp WHERE cache_key = :cacheKey")
    suspend fun updateLastPlayed(cacheKey: String, timestamp: Long)
}
