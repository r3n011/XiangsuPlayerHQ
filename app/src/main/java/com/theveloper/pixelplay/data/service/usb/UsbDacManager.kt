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

        // 音频数据接口类：部分 DAC 的 streaming 接口声明为 0x02 而非标准 Audio 0x01
        private const val USB_CLASS_AUDIO_DATA = 0x02

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

    // 权限结果接收器注册状态（统一在 applicationContext 上注册/反注册）
    @Volatile
    private var permissionReceiverRegistered = false

    private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name) as? T
        }

    /**
     * 注册权限结果接收器。
     *
     * ⚡ 关键：USB 权限结果广播由 system_server（UsbService）发送，属于其他进程。
     * Android 13+ 若用 RECEIVER_NOT_EXPORTED 注册则永远收不到系统广播，
     * 表现为"权限弹窗点了没反应 / 授权后回调不触发"。
     * 必须用 RECEIVER_EXPORTED 才能收到系统进程发出的授权结果广播。
     * 为防外部应用伪造，onReceive 中会校验设备名与请求时一致。
     */
    private fun registerPermissionReceiver(context: Context) {
        if (permissionReceiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionResultReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(permissionResultReceiver, filter)
            }
            permissionReceiverRegistered = true
            Timber.d("$TAG: USB permission receiver registered (exported=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU})")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to register USB permission receiver")
            permissionReceiverRegistered = false
        }
    }

    /** 反注册权限结果接收器（幂等，未注册时静默忽略） */
    private fun unregisterPermissionReceiver(context: Context) {
        if (!permissionReceiverRegistered) return
        try {
            context.unregisterReceiver(permissionResultReceiver)
        } catch (_: IllegalArgumentException) {
            // 未注册，忽略
        }
        permissionReceiverRegistered = false
    }

    /** 动态注册的 USB 权限结果接收器（接收 UsbManager.requestPermission 的系统授权广播） */
    private val permissionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)
            // 校验是否与本次请求的设备一致，防止伪造广播串台
            val requestedName = pendingPermissionDeviceName
            if (requestedName != null && device != null && device.deviceName != requestedName) {
                Timber.w("$TAG: Permission result for unexpected device ${device.deviceName}, ignoring")
                return
            }
            val callback = pendingPermissionCallback
            pendingPermissionCallback = null
            pendingPermissionDeviceName = null
            try {
                context.unregisterReceiver(this)
            } catch (_: IllegalArgumentException) {
                // 已反注册，忽略
            }
            permissionReceiverRegistered = false
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
                Timber.d("$TAG: deviceList size=${devices.size}")
                val audioDevices = mutableListOf<UsbDeviceInfo>()

                for ((name, device) in devices) {
                    val ifaceDesc = (0 until device.interfaceCount).joinToString(", ") { i ->
                        val itf = device.getInterface(i)
                        "iface${itf.id}(class=0x${itf.interfaceClass.toString(16)})"
                    }
                    Timber.d("$TAG: USB device: $name vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} ifaces=[$ifaceDesc] hasPermission=${manager.hasPermission(device)}")
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
     * 标准 USB 音频类 (0x01) 之外，还需兼容：
     * - 音频数据类 (0x02)：部分 DAC 的 streaming 接口
     * - vendor-specific (0xFF)：部分 USB DAC / 声卡芯片未声明标准音频类接口
     */
    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            val clazz = usbInterface.interfaceClass
            if (clazz == UsbConstants.USB_CLASS_AUDIO || clazz == USB_CLASS_AUDIO_DATA || clazz == USB_CLASS_VENDOR_SPECIFIC) {
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
     * 请求 USB 设备权限（Activity 上下文版）
     *
     * ⚡ 必须使用 Activity 上下文调用 UsbManager.requestPermission，
     * 否则部分设备 / Android 版本上系统授权对话框不会弹出（表现为"连权限申请弹窗都没有"）。
     * 参考 moriafly/AndroidUsbAudio：权限请求在 Activity（onCreate）中发起。
     */
    fun requestPermission(context: Context, deviceName: String, callback: (Boolean) -> Unit) {
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

        // 统一在 applicationContext 上注册接收器：
        // 1) 接收器是单例成员，若在 Activity / Service 不同 context 间反复 unregister+register，
        //    会出现"Receiver not registered"或重复注册异常导致权限请求静默失败；
        // 2) 注册必须导出（RECEIVER_EXPORTED），否则收不到 system_server 发来的授权广播。
        val appContext = context.applicationContext ?: context
        unregisterPermissionReceiver(appContext)
        registerPermissionReceiver(appContext)
        if (!permissionReceiverRegistered) {
            Timber.e("$TAG: USB permission receiver not registered, aborting permission request")
            pendingPermissionCallback = null
            pendingPermissionDeviceName = null
            callback(false)
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            (device.deviceId and 0xFFFF).toInt(),
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        Timber.d("$TAG: Requesting USB permission for $deviceName via ${context.javaClass.simpleName}")
        try {
            manager.requestPermission(device, pendingIntent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: requestPermission threw for $deviceName")
            pendingPermissionCallback = null
            pendingPermissionDeviceName = null
            callback(false)
        }
    }

    /**
     * 请求 USB 设备权限（Application 上下文版，保留给 Service / 设置开关回退使用）
     */
    fun requestPermission(deviceName: String, callback: (Boolean) -> Unit) {
        requestPermission(context, deviceName, callback)
    }

    /**
     * 请求权限并激活 USB DAC 独占模式（Activity 上下文版）
     *
     * 无权限时先拉起系统授权对话框，授权成功后自动激活；失败则回调 false。
     */
    fun requestAndActivateExclusiveMode(
        context: Context,
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

        requestPermission(context, deviceInfo.deviceName) { granted ->
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
     * 请求权限并激活 USB DAC 独占模式（Application 上下文版，保留给 Service 使用）
     */
    fun requestAndActivateExclusiveMode(
        deviceInfo: UsbDeviceInfo,
        onResult: (Boolean) -> Unit
    ) {
        requestAndActivateExclusiveMode(context, deviceInfo, onResult)
    }

    /**
     * 激活 USB DAC 独占模式
     *
     * 步骤参考 AndroidUsbAudio-main：openDevice → 检查权限 → 对音频接口 claim（forceClaim）
     * → 把文件描述符交给 libusb 等时输出（UsbAudioOutput.start）。
     */
    suspend fun activateExclusiveMode(deviceInfo: UsbDeviceInfo): Boolean {
        val manager = usbManager ?: run {
            Timber.w("$TAG: USB Manager not available")
            return false
        }

        _deviceState.value = UsbDeviceState.ACTIVATING

        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 如果已经有激活中的设备，先关闭 native 输出再切换
            if (UsbAudioOutput.isActive()) {
                try { UsbAudioOutput.stop() } catch (_: Throwable) {}
            }
            val prevActiveName = _activeDevice.value?.deviceName
            if (prevActiveName != null && prevActiveName != deviceInfo.deviceName) {
                closeConnection(prevActiveName)
            }

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
                val oldConnection = usbConnections[deviceInfo.deviceName]
                val connection = oldConnection ?: manager.openDevice(device)

                if (connection == null) {
                    Timber.e("$TAG: Failed to open USB device: ${deviceInfo.deviceName}")
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 验证文件描述符
                val fileDescriptor = connection.fileDescriptor
                if (fileDescriptor < 0) {
                    Timber.e("$TAG: Invalid file descriptor for ${deviceInfo.deviceName}")
                    if (oldConnection == null) connection.close()
                    _deviceState.value = UsbDeviceState.ERROR
                    return@withContext false
                }

                // 对音频接口执行 claim + setInterface（forceClaim，避免因系统驱动占用失败）
                val audioIfaces = getAudioInterfaces(device)
                for (usbInterface in audioIfaces) {
                    try {
                        val claimed = connection.claimInterface(usbInterface, true)
                        if (!claimed) {
                            Timber.w("$TAG: Failed to claim iface ${usbInterface.id} on ${deviceInfo.deviceName}")
                        } else {
                            // 优先尝试非 0 alternate setting（部分 DAC 仅在 alt != 0 上开放等时端点带宽）
                            // 注：Android 中每个 UsbInterface 对象即代表 (interfaceId, alternateSetting) 组合；
                            // 遍历 device 所有接口，找到 id 相同且 alternateSetting > 0 且带等时 OUT 的对象。
                            val ifaceId = usbInterface.id
                            val altCount = device.interfaceCount
                            var bestAlt: android.hardware.usb.UsbInterface? = null
                            var i = altCount - 1
                            while (i >= 0) {
                                val cand = device.getInterface(i)
                                if (cand.id == ifaceId && cand.alternateSetting >= 1) {
                                    val hasOut = (0 until cand.endpointCount).any { j ->
                                        val ep = cand.getEndpoint(j)
                                        val attr = ep.attributes
                                        val addr = ep.address
                                        (attr and 0x03) == 0x01 /* ISO */ &&
                                                (addr.toInt() and 0x80) == 0 /* OUT */
                                    }
                                    if (hasOut) { bestAlt = cand; break }
                                }
                                i--
                            }
                            if (bestAlt != null) {
                                try {
                                    connection.setInterface(bestAlt)
                                    Timber.d(
                                        "$TAG: Set alt setting ${bestAlt.alternateSetting} for iface ${bestAlt.id}"
                                    )
                                } catch (_: Throwable) {}
                            }
                        }
                    } catch (e: Throwable) {
                        Timber.w(e, "$TAG: Claim iface ${usbInterface.id} threw")
                    }
                }

                // 存储连接
                usbConnections[deviceInfo.deviceName] = connection

                // ⚡ 启动 libusb 等时输出，将 PCM 写入 USB DAC（独占播放）
                val ok = UsbAudioOutput.start(connection.fileDescriptor)

                if (ok) {
                    _activeDevice.value = deviceInfo
                    _exclusiveModeActive.value = true
                    _deviceState.value = UsbDeviceState.STREAMING
                    Timber.i("$TAG: USB DAC activated: ${deviceInfo.displayName}")
                } else {
                    Timber.e("$TAG: UsbAudioOutput.start failed for ${deviceInfo.deviceName}")
                    closeConnection(deviceInfo.deviceName)
                    _deviceState.value = UsbDeviceState.ERROR
                }
                ok
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
     * 获取 USB 音频接口列表（标准音频类 + 音频数据类 + vendor-specific）
     */
    private fun getAudioInterfaces(device: UsbDevice): List<UsbInterface> {
        val interfaces = mutableListOf<UsbInterface>()
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            val clazz = usbInterface.interfaceClass
            if (clazz == UsbConstants.USB_CLASS_AUDIO || clazz == USB_CLASS_AUDIO_DATA || clazz == USB_CLASS_VENDOR_SPECIFIC) {
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
                // 先停 native 输出，再释放接口和连接（避免等时 transfer 飞在外面）
                try { UsbAudioOutput.stop() } catch (_: Throwable) {}

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
        // 正在播放时设备拔出，必须先停止 native 等时传输
        try { UsbAudioOutput.stop() } catch (_: Throwable) {}
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
