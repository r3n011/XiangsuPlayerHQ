package com.theveloper.pixelplay.data.service.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 设备连接广播接收器
 * 监听 USB 音频设备的连接和断开事件
 *
 * 参考 AndroidUsbAudio-main：UsbManager.ACTION_USB_DEVICE_ATTACHED / DETACHED
 * 通过单例 UsbDacManager 触发刷新 / 清理，而不是再次发送自定义广播。
 */
@Singleton
class UsbConnectionReceiver @Inject constructor(
    private val usbDacManager: UsbDacManager
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbConnectionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null && isAudioDevice(device)) {
                    Log.d(TAG, "USB audio device attached: ${device.deviceName}")
                    usbDacManager.handleDeviceAttached()
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    Log.d(TAG, "USB device detached: ${device.deviceName}")
                    usbDacManager.handleDeviceDetached(device.deviceName)
                }
            }
        }
    }

    private fun isAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val clazz = device.getInterface(i).interfaceClass
            // 标准音频类 (0x01) 或 vendor-specific (0xFF，部分 USB DAC)
            if (clazz == UsbConstants.USB_CLASS_AUDIO || clazz == 0xFF) {
                return true
            }
        }
        return false
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name) as? T
        }
}
