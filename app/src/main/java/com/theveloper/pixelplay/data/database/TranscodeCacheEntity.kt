package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 转码缓存实体
 *
 * 记录已转码歌曲的缓存元信息，用于：
 * - 展示给用户管理（查看/删除）
 * - 按最后访问时间（last_played）进行 TTL 过期清理
 * - 统计缓存总大小
 */
@Entity(
    tableName = "transcode_cache",
    indices = [
        Index(value = ["last_played"], name = "index_transcode_cache_last_played"),
        Index(value = ["file_path"], name = "index_transcode_cache_file_path")
    ]
)
data class TranscodeCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    
    @ColumnInfo(name = "song_id")
    val songId: String? = null,
    
    @ColumnInfo(name = "song_title")
    val songTitle: String? = null,
    
    @ColumnInfo(name = "artist_name")
    val artistName: String? = null,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_played")
    val lastPlayed: Long = System.currentTimeMillis()
)
