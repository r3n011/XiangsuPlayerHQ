package com.theveloper.pixelplay.data.service.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * USB 设备连接广播接收器
 * 监听 USB 音频设备的连接和断开事件
 */
class UsbConnectionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "UsbConnectionReceiver"
        
        // 广播 Action
        const val ACTION_USB_DEVICE_CONNECTED = "com.theveloper.pixelplay.USB_DEVICE_CONNECTED"
        const val ACTION_USB_DEVICE_DISCONNECTED = "com.theveloper.pixelplay.USB_DEVICE_DISCONNECTED"
        const val ACTION_USB_PERMISSION_GRANTED = "com.theveloper.pixelplay.USB_PERMISSION_GRANTED"
        const val ACTION_USB_PERMISSION_DENIED = "com.theveloper.pixelplay.USB_PERMISSION_DENIED"
        
        const val EXTRA_USB_DEVICE = "usb_device"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null && isAudioDevice(device)) {
                    Log.d(TAG, "USB audio device attached: ${device.deviceName}")
                    val broadcast = Intent(ACTION_USB_DEVICE_CONNECTED).apply {
                        putExtra(EXTRA_USB_DEVICE, device)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcast)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    Log.d(TAG, "USB device detached: ${device.deviceName}")
                    val broadcast = Intent(ACTION_USB_DEVICE_DISCONNECTED).apply {
                        putExtra(EXTRA_USB_DEVICE, device)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcast)
                }
            }
            ACTION_USB_PERMISSION_GRANTED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Log.d(TAG, "USB permission granted for: ${device?.deviceName}")
                // 处理权限授予逻辑
            }
            ACTION_USB_PERMISSION_DENIED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Log.d(TAG, "USB permission denied for: ${device?.deviceName}")
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
}
