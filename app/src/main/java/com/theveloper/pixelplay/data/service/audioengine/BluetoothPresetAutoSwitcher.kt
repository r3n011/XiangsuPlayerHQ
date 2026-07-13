package com.theveloper.pixelplay.data.service.audioengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.BluetoothPresetBindingEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetWithBands
import com.theveloper.pixelplay.data.repository.HeadphonePresetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothPresetAutoSwitcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val headphonePresetRepository: HeadphonePresetRepository,
    private val audioProcessorProvider: AudioProcessorProvider
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(Dispatchers.Default)

    private var lastAppliedPresetId: Long? = null
    private var lastConnectedDeviceKey: String? = null

    fun onBluetoothDeviceConnected(deviceName: String, deviceAddress: String?) {
        val deviceKey = deviceAddress ?: deviceName
        if (deviceKey == lastConnectedDeviceKey) {
            Timber.d("Same device connected, skipping preset switch")
            return
        }
        lastConnectedDeviceKey = deviceKey

        scope.launch {
            try {
                val binding = headphonePresetRepository.findBinding(deviceAddress, deviceName).first()
                if (binding == null) {
                    Timber.d("No preset binding found for device: $deviceName")
                    return@launch
                }

                if (binding.presetId == lastAppliedPresetId) {
                    Timber.d("Same preset already applied")
                    return@launch
                }

                applyPreset(binding)
            } catch (e: Exception) {
                Timber.e(e, "Error switching preset for Bluetooth device")
            }
        }
    }

    private suspend fun applyPreset(binding: BluetoothPresetBindingEntity) {
        val presetWithBands = headphonePresetRepository.getPresetWithBands(binding.presetId).first()
        if (presetWithBands == null) {
            Timber.e("Preset not found: ${binding.presetId}")
            return
        }

        withContext(Dispatchers.Main) {
            applyPresetToAudioEngine(presetWithBands)
        }

        lastAppliedPresetId = binding.presetId
        showNotification(presetWithBands.preset.name)

        Timber.d("Applied preset '${presetWithBands.preset.name}' for device '${binding.deviceName}'")
    }

    private fun applyPresetToAudioEngine(preset: HeadphonePresetWithBands) {
        val processor = audioProcessorProvider.getProcessor() ?: return
        val bands = preset.bands.map { entity ->
            val type = when (entity.filterType) {
                "BELL" -> EQType.BELL
                "LOW_SHELF" -> EQType.LOW_SHELF
                "HIGH_SHELF" -> EQType.HIGH_SHELF
                else -> EQType.BELL
            }
            EQBand(
                frequency = entity.frequency,
                gain = entity.gain,
                q = entity.q,
                type = type
            )
        }

        processor.getParametricEQ().setEnabled(true)
        processor.getParametricEQ().setBands(bands)
        processor.getReplayGainProcessor().setPreamp(preset.preset.preamp)
    }

    private fun showNotification(presetName: String) {
        val channelId = "bluetooth_preset_switch"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "耳机预设切换",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "蓝牙设备连接时自动切换耳机预设的通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentTitle("预设已切换")
            .setContentText("已切换到 \"$presetName\" 预设")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PRESET_SWITCH, notification)
    }

    companion object {
        const val NOTIFICATION_ID_PRESET_SWITCH = 1001
    }
}