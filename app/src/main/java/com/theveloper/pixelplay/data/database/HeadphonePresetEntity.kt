package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "headphone_presets",
    indices = [
        Index("name"),
        Index("category"),
        Index("brand")
    ]
)
data class HeadphonePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val category: String,
    val source: String,
    val preamp: Float,
    @ColumnInfo(name = "band_count") val bandCount: Int,
    @ColumnInfo(name = "display_priority") val displayPriority: Int = 0
)