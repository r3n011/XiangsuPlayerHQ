package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BluetoothPresetBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(binding: BluetoothPresetBindingEntity)

    @Delete
    suspend fun delete(binding: BluetoothPresetBindingEntity)

    @Query("DELETE FROM bluetooth_preset_bindings WHERE id = :bindingId")
    suspend fun deleteById(bindingId: Long)

    @Query("DELETE FROM bluetooth_preset_bindings WHERE device_address = :address")
    suspend fun deleteByAddress(address: String)

    @Query("SELECT * FROM bluetooth_preset_bindings ORDER BY created_at DESC")
    fun getAllBindings(): Flow<List<BluetoothPresetBindingEntity>>

    @Query("""
        SELECT * FROM bluetooth_preset_bindings 
        WHERE device_address = :address 
        OR (device_address IS NULL AND device_name = :name)
        LIMIT 1
    """)
    fun findBinding(address: String?, name: String): Flow<BluetoothPresetBindingEntity?>

    @Query("SELECT * FROM bluetooth_preset_bindings WHERE preset_id = :presetId")
    fun findBindingsByPreset(presetId: Long): Flow<List<BluetoothPresetBindingEntity>>

    @Query("SELECT COUNT(*) FROM bluetooth_preset_bindings")
    suspend fun getBindingCount(): Int
}