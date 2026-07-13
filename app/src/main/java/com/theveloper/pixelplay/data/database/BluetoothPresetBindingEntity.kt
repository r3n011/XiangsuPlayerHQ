package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bluetooth_preset_bindings",
    foreignKeys = [
        ForeignKey(
            entity = HeadphonePresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["preset_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("device_address"),
        Index("device_name"),
        Index("preset_id")
    ]
)
data class BluetoothPresetBindingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "device_address") val deviceAddress: String?,
    @ColumnInfo(name = "preset_id") val presetId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)