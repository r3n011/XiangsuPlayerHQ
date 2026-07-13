package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "headphone_eq_bands",
    foreignKeys = [
        ForeignKey(
            entity = HeadphonePresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("preset_id")]
)
data class HeadphoneEqBandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "preset_id") val presetId: Long,
    @ColumnInfo(name = "filter_order") val filterOrder: Int,
    @ColumnInfo(name = "filter_type") val filterType: String,
    val frequency: Float,
    val q: Float,
    val gain: Float
)