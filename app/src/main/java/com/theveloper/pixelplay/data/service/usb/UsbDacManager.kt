package com.theveloper.pixelplay.data.service.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB DAC 管理器
 *
 * 负责 USB DAC 设备的发现、权限管理、连接和独占模式激活。
 * 参考 Flick 项目与 moriafly/AndroidUsbAudio 示例的实现方式，使用 Android USB Host API。
 */
@Singleton
class UsbDacManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UsbDacManager"

        // USB 权限请求（PendingIntent 广播 action）
        private const val ACTION_USB_PERMISSION = "com.theveloper.pixelplay.USB_PERMISSION"

        // vendor-specific 接口类：部分 USB DAC 未声明标准音频类接口，而是使用自定义类
        private const val USB_CLASS_VENDOR_SPECIFIC = 0xFF

        // 支持的音频采样率
        val SUPPORTED_SAMPLE_RATES = listOf(
            44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000
        )

        // 支持的位深度
        val SUPPORTED_BIT_DEPTHS = listOf(16, 24, 32)
    }

    private val usbManager: UsbManager? by lazy {
        context.getSystemService(Context.USB_SERVICE) as? UsbManager
    }

    // 设备状态
    private val _deviceState = MutableStateFlow(UsbDeviceState.IDLE)
    val deviceState: StateFlow<UsbDeviceState> = _deviceState.asStateFlow()

    // 已连接的 USB 音频设备列表
    private val _connectedDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDeviceInfo>> = _connectedDevices.asStateFlow()

    // 当前激活的设备
    private val _activeDevice = MutableStateFlow<UsbDeviceInfo?>(null)
    val activeDevice: StateFlow<UsbDeviceInfo?> = _activeDevice.asStateFlow()

    // 独占模式是否激活
    private val _exclusiveModeActive = MutableStateFlow(false)
    val exclusiveModeActive: StateFlow<Boolean> = _exclusiveModeActive.asStateFlow()

    // 当前音频格式
    private val _currentFormat = MutableStateFlow<UsbAudioFormat?>(null)
    val currentFormat: StateFlow<UsbAudioFormat?> = _currentFormat.asStateFlow()

    // USB 连接缓存
    private val usbConnections = ConcurrentHashMap<String, UsbDeviceConnection>()

    // 权限请求回调
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private var pendingPermissionDeviceName: String? = null

    /** 动态注册的 USB 权限结果接收器（接收 UsbManager.requestPermission 的系统授权广播） */
    private val permissionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            try {
                context.unregisterReceiver(this)
            } catch (_: IllegalArgumentException) {
                // 已反注册，忽略
            }
            val callback = pendingPermissionCallback
            pendingPermissionCallback = null
            pendingPermissionDeviceName = null
            Timber.d("$TAG: USB permission result granted=$granted for ${device?.deviceName}")
            callback?.invoke(granted)
        }
    }

    /**
     * 扫描 USB 设备
     */
    suspend fun scanDevices(): List<UsbDeviceInfo> {
        val manager = usbManager ?: run {
            Timber.w("$TAG: USB Manager not available")
            return emptyList()
        }

        _deviceState.value = UsbDeviceState.DETECTING

        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val devices = manager.deviceList
                val audioDevices = mutableListOf<UsbDeviceInfo>()

                for ((name, device) in devices) {
                    val isAudio = isUsbAudioDevice(device)
                    if (isAudio) {
                        val info = UsbDeviceInfo(
                            deviceName = name,
                            productName = device.productName ?: "Unknown USB Device",
                            manufacturerName = device.manufacturerName ?: "",
                            vendorId = device.vendorId,
                            productId = device.productId,
                            serialNumber = device.serialNumber,
                            isAudioDevice = true,
                            supportedSampleRates = detectSupportedSampleRates(device),
                            supportedBitDepths = SUPPORTED_BIT_DEPTHS,
                            supportedChannels = listOf(2)
                        )
                        audioDevices.add(info)
                    }
                }

                _connectedDevices.value = audioDevices
                Timber.d("$TAG: Found ${audioDevices.size} USB audio devices")
                audioDevices
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to scan USB devices")
                _deviceState.value = UsbDeviceState.ERROR
                emptyList()
            }
        }
    }

    /**
     * 判断是否是 USB 音频设备
     *
     * 标准 USB 音频类 (0x01) 之外，还需兼容 vendor-specific (0xFF) 接口：
     * 部分 USB DAC / 声卡芯片（如部分国产 DAC）未声明标准音频类接口。
     */
    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            val clazz = usbInterface.interfaceClass
            if (clazz == UsbConstants.USB_CLASS_AUDIO || clazz == USB_CLASS_VENDOR_SPECIFIC) {
                return true
            }
        }
        return false
    }

    /**
     * 检测设备支持的采样率
     */
    private fun detectSupportedSampleRates(device: UsbDevice): List<Int> {
        // 简化处理：默认支持所有常见采样率
        // 实际应通过 USB 描述符解析
        return SUPPORTED_SAMPLE_RATES
    }

    /**
     * 检查是否有 USB 设备权限
     */
    fun hasPermission(deviceName: String): Boolean {
        val manager = usbManager ?: return false
        val device = manager.deviceList[deviceName] ?: return false
        return manager.hasPermission(device)
    }

    /**
     * 请求 USB 设备权限
     *
     * 通过 PendingIntent + 动态注册广播接收器发起系统权限对话框，
     * 参考 moriafly/AndroidUsbAudio 示例（UsbManager.requestPermission + 权限广播）。
     * 注意：必须在 UI 线程（Activity/可组合项）调用。
     */
    fun requestPermission(deviceName: String, callback: (Boolean) -> Unit) {
        val manager = usbManager ?: run {
            callback(false)
            return
        }

        val device = manager.deviceList[deviceName] ?: run {
            Timber.e("$TAG: Device not found: $deviceName")
            callback(false)
            return
        }

        if (manager.hasPermission(device)) {
            callback(true)
            return
        }

        // 保存回调
        pendingPermissionCallback = callback
        pendingPermissionDeviceName = deviceName

        // 动态注册权限结果接收器（保证 PendingIntent 广播能被本进程接收）
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        try {
            context.registerReceiver(permissionResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to register USB permission receiver")
            pendingPermissionCallback = null
            pendingPermissionDeviceName = null
            callback(false)
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE
        )

        Timber.d("$TAG: Requesting USB permission for $deviceName")
        manager.requestPermission(device, pendingIntent)
    }

    /**
     * 请求权限并激活 USB DAC 独占模式
     *
     * 无权限时先拉起系统授权对话框，授权成功后自动激活；失败则回调 false。
     */
    fun requestAndActivateExclusiveMode(
        deviceInfo: UsbDeviceInfo,
        onResult: (Boolean) -> Unit
    ) {
        val manager = usbManager ?: run {
            onResult(false)
            return
        }

        val device = manager.deviceList[deviceInfo.deviceName] ?: run {
            Timber.e("$TAG: Device not found: ${deviceInfo.deviceName}")
            onResult(false)
            return
        }

        if (manager.hasPermission(device)) {
            CoroutineScope(Dispatchers.IO).launch {
                onResult(activateExclusiveMode(deviceInfo))
            }
            return
        }

        requestPermission(deviceInfo.deviceName) { granted ->
            if (granted) {
                Timber.i("$TAG: USB permission granted, activating exclusive mode")
                CoroutineScope(Dispatchers.IO).launch {
                    onResult(activateExclusiveMode(deviceInfo))
                }
            } else {
                Timber.e("$TAG: USB permission denied for ${deviceInfo.deviceName}")
                _deviceState.value = UsbDeviceState.ERROR
                onResult(false)
            }
        }
    }

    /**
     * 激活 USB DAC 独占模式
     */
    suspend fun activateExclusiveMode(deviceInfo: UsbDeviceInfo): Boolean {
        val manager = usbManager ?: run {
            Timber.w("$TAG: USB Manager not available")
            return false
        }

        _deviceState.value = UsbDeviceState.ACTIVATING

        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val device = manager.deviceList[deviceInfo.deviceName] ?: run {
                    Timber.e("$TAG: Device not found: ${deviceInfo.deviceName}")
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 检查权限
                if (!manager.hasPermission(device)) {
                    Timber.e("$TAG: No permission for device: ${deviceInfo.deviceName}")
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 打开设备连接
                val connection = usbConnections[deviceInfo.deviceName] 
                    ?: manager.openDevice(device)
                
                if (connection == null) {
                    Timber.e("$TAG: Failed to open USB device: ${deviceInfo.deviceName}")
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 验证文件描述符
                val fileDescriptor = connection.fileDescriptor
                if (fileDescriptor < 0) {
                    Timber.e("$TAG: Invalid file descriptor for ${deviceInfo.deviceName}")
                    connection.close()
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 存储连接
                usbConnections[deviceInfo.deviceName] = connection

                // 验证音频接口
                if (!validateAudioInterfaces(connection, device)) {
                    Timber.e("$TAG: Failed to validate audio interfaces")
                    closeConnection(deviceInfo.deviceName)
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 激活成功
                _activeDevice.value = deviceInfo
                _exclusiveModeActive.value = true
                _deviceState.value = UsbDeviceState.CONNECTED

                // ⚡ 启动 libusb 等时输出，将 PCM 写入 USB DAC（独占播放）
                UsbAudioOutput.start(connection.fileDescriptor)

                Timber.i("$TAG: USB DAC activated: ${deviceInfo.displayName}")
                true
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to activate exclusive mode")
                _deviceState.value = UsbDeviceState.ERROR
                false
            }
        }
    }

    /**
     * 验证 USB 音频接口
     *
     * 只要设备存在音频相关接口（标准音频类或 vendor-specific）即视为有效。
     * 不再强制要求同时存在 control + streaming 接口：
     * 部分 DAC 仅提供 streaming 接口或使用非标准子类，强制校验会导致激活失败。
     */
    private fun validateAudioInterfaces(
        connection: UsbDeviceConnection,
        device: UsbDevice
    ): Boolean {
        val audioInterfaces = getAudioInterfaces(device)
        
        if (audioInterfaces.isEmpty()) {
            Timber.w("$TAG: No USB audio interfaces found")
            return false
        }

        Timber.d("$TAG: Audio interfaces validated: ${audioInterfaces.size} interfaces")
        return true
    }

    /**
     * 获取 USB 音频接口列表（标准音频类 + vendor-specific）
     */
    private fun getAudioInterfaces(device: UsbDevice): List<UsbInterface> {
        val interfaces = mutableListOf<UsbInterface>()
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            val clazz = usbInterface.interfaceClass
            if (clazz == UsbConstants.USB_CLASS_AUDIO || clazz == USB_CLASS_VENDOR_SPECIFIC) {
                interfaces.add(usbInterface)
            }
        }
        return interfaces
    }

    /**
     * 停用 USB DAC 独占模式
     */
    suspend fun deactivateExclusiveMode(): Boolean {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _exclusiveModeActive.value = false
                _deviceState.value = UsbDeviceState.IDLE
                _currentFormat.value = null

                // 关闭所有连接
                for (deviceName in usbConnections.keys) {
                    closeConnection(deviceName)
                }

                _activeDevice.value = null

                Timber.i("$TAG: USB DAC deactivated")
                true
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to deactivate exclusive mode")
                false
            }
        }
    }

    /**
     * 设置当前音频格式
     */
    fun setAudioFormat(format: UsbAudioFormat) {
        _currentFormat.value = format
        if (_exclusiveModeActive.value) {
            _deviceState.value = UsbDeviceState.STREAMING
        }
    }

    /**
     * 关闭单个设备连接
     */
    private fun closeConnection(deviceName: String) {
        try {
            val connection = usbConnections.remove(deviceName)
            connection?.close()
            Timber.d("$TAG: Connection closed for $deviceName")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Error closing connection for $deviceName")
        }
    }

    /**
     * 关闭所有连接
     */
    fun closeAllConnections() {
        for (deviceName in usbConnections.keys.toList()) {
            closeConnection(deviceName)
        }
        usbConnections.clear()
    }

    /**
     * 处理 USB 权限结果
     */
    fun handlePermissionResult(deviceName: String, granted: Boolean) {
        val callback = pendingPermissionCallback
        pendingPermissionCallback = null
        pendingPermissionDeviceName = null
        callback?.invoke(granted)

        if (granted) {
            // 权限授予后刷新设备列表
            Timber.d("$TAG: USB permission granted for $deviceName")
        }
    }

    /**
     * 处理设备断开
     */
    fun handleDeviceDetached(deviceName: String) {
        closeConnection(deviceName)
        
        if (_activeDevice.value?.deviceName == deviceName) {
            _activeDevice.value = null
            _exclusiveModeActive.value = false
            _deviceState.value = UsbDeviceState.IDLE
            _currentFormat.value = null
        }

        // 刷新设备列表
        val currentDevices = _connectedDevices.value.filter { it.deviceName != deviceName }
        _connectedDevices.value = currentDevices
    }

    /**
     * 处理设备插入
     */
    fun handleDeviceAttached() {
        // 重新扫描设备
        CoroutineScope(Dispatchers.IO).launch {
            scanDevices()
        }
    }

    /**
     * 获取设备信息
     */
    fun getDeviceInfo(deviceName: String): UsbDeviceInfo? {
        return _connectedDevices.value.find { it.deviceName == deviceName }
    }

    /**
     * 是否有活跃的 USB 音频设备
     */
    fun hasActiveUsbDevice(): Boolean {
        return _activeDevice.value != null && _exclusiveModeActive.value
    }

    /**
     * 获取活跃设备的文件描述符（用于原生层）
     */
    fun getActiveFileDescriptor(): Int {
        val deviceName = _activeDevice.value?.deviceName ?: return -1
        val connection = usbConnections[deviceName] ?: return -1
        return connection.fileDescriptor
    }
}
