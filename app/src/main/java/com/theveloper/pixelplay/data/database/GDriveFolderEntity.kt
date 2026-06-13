package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gdrive_folders")
data class GDriveFolderEntity(
    @PrimaryKey val id: String,                                    // Drive folder ID
    val name: String,
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0
)
