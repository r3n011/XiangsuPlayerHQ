package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HeadphonePresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: HeadphonePresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBands(bands: List<HeadphoneEqBandEntity>)

    @Query("SELECT * FROM headphone_presets ORDER BY display_priority DESC, name ASC")
    fun getAllPresets(): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT * FROM headphone_presets WHERE category = :category ORDER BY name ASC")
    fun getPresetsByCategory(category: String): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT * FROM headphone_presets WHERE brand = :brand ORDER BY name ASC")
    fun getPresetsByBrand(brand: String): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT * FROM headphone_presets WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchPresets(query: String): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT DISTINCT brand FROM headphone_presets ORDER BY brand ASC")
    fun getAllBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM headphone_presets")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM headphone_eq_bands WHERE preset_id = :presetId ORDER BY filter_order ASC")
    suspend fun getEqBandsForPreset(presetId: Long): List<HeadphoneEqBandEntity>

    @Transaction
    @Query("SELECT * FROM headphone_presets WHERE id = :presetId")
    fun getPresetWithBands(presetId: Long): Flow<HeadphonePresetWithBands?>

    @Query("SELECT * FROM headphone_presets WHERE id = :presetId")
    suspend fun getPresetById(presetId: Long): HeadphonePresetEntity?

    @Query("DELETE FROM headphone_eq_bands")
    suspend fun deleteAllBands()

    @Query("DELETE FROM headphone_presets")
    suspend fun deleteAllPresets()
}

data class HeadphonePresetWithBands(
    @androidx.room.Embedded val preset: HeadphonePresetEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "preset_id"
    )
    val bands: List<HeadphoneEqBandEntity>
)