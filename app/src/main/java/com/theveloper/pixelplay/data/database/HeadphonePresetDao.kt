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

    @Query("SELECT * FROM headphone_presets WHERE category LIKE '%' || :type || '%' ORDER BY name ASC")
    fun getPresetsByType(type: String): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT * FROM headphone_presets WHERE brand = :brand ORDER BY name ASC")
    fun getPresetsByBrand(brand: String): Flow<List<HeadphonePresetEntity>>

    @Query("SELECT * FROM headphone_presets WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchPresets(query: String): Flow<List<HeadphonePresetEntity>>

    // 推荐预设：匹配热门耳机系列关键词（模仿 Rhythm 的 getRecommendedProfiles）
    @Query(
        """
        SELECT * FROM headphone_presets
        WHERE name LIKE '%WH-1000XM%' OR name LIKE '%WF-1000XM%' OR name LIKE '%AirPods Pro%'
           OR name LIKE '%AirPods Max%' OR name LIKE '%HD 600%' OR name LIKE '%HD 650%'
           OR name LIKE '%HD 800%' OR name LIKE '%HD 560S%' OR name LIKE '%IE 600%'
           OR name LIKE '%IE 900%' OR name LIKE '%QuietComfort%' OR name LIKE '%Galaxy Buds%'
           OR name LIKE '%Momentum%' OR name LIKE '%ATH-M50X%' OR name LIKE '%DT 770%'
           OR name LIKE '%DT 990%' OR name LIKE '%K701%' OR name LIKE '%K702%'
        ORDER BY display_priority DESC, name ASC
        LIMIT :limit
        """
    )
    fun getRecommendedPresets(limit: Int): Flow<List<HeadphonePresetEntity>>

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