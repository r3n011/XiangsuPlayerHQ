package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.BluetoothPresetBindingEntity
import com.theveloper.pixelplay.data.database.HeadphoneEqBandEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetWithBands
import com.theveloper.pixelplay.data.database.PixelPlayDatabase
import com.theveloper.pixelplay.data.service.audioengine.EQBand
import com.theveloper.pixelplay.data.service.audioengine.EQType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadphonePresetRepository @Inject constructor(
    private val database: PixelPlayDatabase
) {
    private val headphonePresetDao get() = database.headphonePresetDao()
    private val bluetoothPresetBindingDao get() = database.bluetoothPresetBindingDao()
    fun getAllPresets(): Flow<List<HeadphonePresetEntity>> {
        return headphonePresetDao.getAllPresets()
    }

    fun getPresetsByCategory(category: String): Flow<List<HeadphonePresetEntity>> {
        return headphonePresetDao.getPresetsByCategory(category)
    }

    fun getPresetsByBrand(brand: String): Flow<List<HeadphonePresetEntity>> {
        return headphonePresetDao.getPresetsByBrand(brand)
    }

    fun searchPresets(query: String): Flow<List<HeadphonePresetEntity>> {
        return headphonePresetDao.searchPresets(query)
    }

    fun getAllBrands(): Flow<List<String>> {
        return headphonePresetDao.getAllBrands()
    }

    fun getAllCategories(): Flow<List<String>> {
        return headphonePresetDao.getAllCategories()
    }

    fun getPresetWithBands(presetId: Long): Flow<HeadphonePresetWithBands?> {
        return headphonePresetDao.getPresetWithBands(presetId)
    }

    suspend fun getPresetById(presetId: Long): HeadphonePresetEntity? {
        return headphonePresetDao.getPresetById(presetId)
    }

    suspend fun getEqBandsForPreset(presetId: Long): List<EQBand> {
        return headphonePresetDao.getEqBandsForPreset(presetId).map { it.toEQBand() }
    }

    suspend fun bindPresetToDevice(deviceName: String, deviceAddress: String?, presetId: Long) {
        bluetoothPresetBindingDao.insert(
            BluetoothPresetBindingEntity(
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                presetId = presetId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unbindDevice(bindingId: Long) {
        bluetoothPresetBindingDao.deleteById(bindingId)
    }

    fun getAllBindings(): Flow<List<BluetoothPresetBindingEntity>> {
        return bluetoothPresetBindingDao.getAllBindings()
    }

    fun findBinding(deviceAddress: String?, deviceName: String): Flow<BluetoothPresetBindingEntity?> {
        return bluetoothPresetBindingDao.findBinding(deviceAddress, deviceName)
    }

    suspend fun getBindingCount(): Int {
        return bluetoothPresetBindingDao.getBindingCount()
    }

    private fun HeadphoneEqBandEntity.toEQBand(): EQBand {
        val type = when (filterType) {
            "BELL" -> EQType.BELL
            "LOW_SHELF" -> EQType.LOW_SHELF
            "HIGH_SHELF" -> EQType.HIGH_SHELF
            else -> EQType.BELL
        }
        return EQBand(
            frequency = frequency,
            gain = gain,
            q = q,
            type = type
        )
    }
}