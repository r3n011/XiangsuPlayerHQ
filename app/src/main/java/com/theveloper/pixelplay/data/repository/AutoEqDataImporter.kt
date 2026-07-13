package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.WorkerThread
import androidx.sqlite.db.SupportSQLiteDatabase
import com.theveloper.pixelplay.data.database.HeadphoneEqBandEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetDao
import com.theveloper.pixelplay.data.database.HeadphonePresetEntity
import com.theveloper.pixelplay.data.database.PixelPlayDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoEqDataImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PixelPlayDatabase
) {

    private var isImported = false

    @WorkerThread
    suspend fun importIfNeeded() {
        if (isImported) return

        val presetCount = database.headphonePresetDao().getAllPresets().first().size
        if (presetCount > 0) {
            isImported = true
            Timber.d("AutoEq data already imported ($presetCount presets)")
            return
        }

        performImport()
    }

    @WorkerThread
    suspend fun forceImport() {
        database.headphonePresetDao().deleteAllPresets()
        isImported = false
        performImport()
    }

    @WorkerThread
    private suspend fun performImport() {

        try {
            val inputStream = context.assets.open("autoeq/headphone_presets.db")
            val tempFile = context.cacheDir.resolve("autoeq_temp.db")

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val helper = object : SQLiteOpenHelper(context, tempFile.absolutePath, null, 1) {
                override fun onCreate(db: SQLiteDatabase) {}
                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }

            val assetDb = helper.readableDatabase

            var totalPresets = 0
            var totalBands = 0

            val presetsCursor = assetDb.query(
                "headphone_presets",
                arrayOf("id", "name", "brand", "category", "source", "preamp", "band_count", "display_priority"),
                null, null, null, null, null
            )

            val sourceIdMap = mutableMapOf<Long, Long>()

            while (presetsCursor.moveToNext()) {
                val sourceId = presetsCursor.getLong(presetsCursor.getColumnIndexOrThrow("id"))
                val preset = HeadphonePresetEntity(
                    id = 0,
                    name = presetsCursor.getString(presetsCursor.getColumnIndexOrThrow("name")),
                    brand = presetsCursor.getString(presetsCursor.getColumnIndexOrThrow("brand")),
                    category = presetsCursor.getString(presetsCursor.getColumnIndexOrThrow("category")),
                    source = presetsCursor.getString(presetsCursor.getColumnIndexOrThrow("source")),
                    preamp = presetsCursor.getFloat(presetsCursor.getColumnIndexOrThrow("preamp")),
                    bandCount = presetsCursor.getInt(presetsCursor.getColumnIndexOrThrow("band_count")),
                    displayPriority = presetsCursor.getInt(presetsCursor.getColumnIndexOrThrow("display_priority"))
                )

                val newId = database.headphonePresetDao().insertPreset(preset)
                sourceIdMap[sourceId] = newId
                totalPresets++
            }
            presetsCursor.close()

            for ((sourceId, newId) in sourceIdMap) {
                val bandsCursor = assetDb.query(
                    "headphone_eq_bands",
                    arrayOf("filter_order", "filter_type", "frequency", "q", "gain"),
                    "preset_id = ?",
                    arrayOf(sourceId.toString()),
                    null, null, "filter_order ASC"
                )

                val bands = mutableListOf<HeadphoneEqBandEntity>()
                while (bandsCursor.moveToNext()) {
                    bands.add(
                        HeadphoneEqBandEntity(
                            id = 0,
                            presetId = newId,
                            filterOrder = bandsCursor.getInt(bandsCursor.getColumnIndexOrThrow("filter_order")),
                            filterType = bandsCursor.getString(bandsCursor.getColumnIndexOrThrow("filter_type")),
                            frequency = bandsCursor.getFloat(bandsCursor.getColumnIndexOrThrow("frequency")),
                            q = bandsCursor.getFloat(bandsCursor.getColumnIndexOrThrow("q")),
                            gain = bandsCursor.getFloat(bandsCursor.getColumnIndexOrThrow("gain"))
                        )
                    )
                }
                bandsCursor.close()

                database.headphonePresetDao().insertBands(bands)
                totalBands += bands.size
            }

            assetDb.close()
            helper.close()
            tempFile.delete()

            isImported = true
            Timber.d("AutoEq data import completed: $totalPresets presets, $totalBands bands")

        } catch (e: Exception) {
            Timber.e(e, "Failed to import AutoEq data")
        }
    }
}