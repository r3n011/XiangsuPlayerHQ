package com.theveloper.pixelplay.data.service.usb

/**
 * USB 设备信息数据类
 */
data class UsbDeviceInfo(
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String? = null,
    val isAudioDevice: Boolean = false,
    val supportedSampleRates: List<Int> = emptyList(),
    val supportedBitDepths: List<Int> = emptyList(),
    val supportedChannels: List<Int> = emptyList()
) {
    val displayName: String
        get() = if (manufacturerName.isNotBlank()) "$manufacturerName $productName" else productName
}

/**
 * USB 设备状态
 */
enum class UsbDeviceState {
    IDLE,           // 空闲
    DETECTING,      // 检测中
    CONNECTED,      // 已连接
    ACTIVATING,     // 激活中
    STREAMING,      // 正在播放
    ERROR           // 错误
}

/**
 * USB 音频格式
 */
data class UsbAudioFormat(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int
)
