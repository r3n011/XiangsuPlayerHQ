package com.theveloper.pixelplay.data.service.usb

import timber.log.Timber

/**
 * USB 音频输出（libusb 等时 OUT 传输）
 *
 * 将 Android UsbDeviceConnection 的文件描述符包装为 libusb 设备，
 * 找到音频流的等时 OUT 端点，把 PCM 数据写入 USB DAC（独占播放）。
 *
 * JNI 实现位于 app/src/main/cpp/usbaudio_output.c。
 */
object UsbAudioOutput {

    private const val TAG = "UsbAudioOutput"

    @Volatile
    private var active = false

    init {
        try {
            System.loadLibrary("usbaudio_output")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "$TAG: 加载 usbaudio_output native 库失败")
        }
    }

    private external fun nativeSetup(fd: Int): Boolean
    private external fun nativeWritePcm(data: ByteArray): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeClose(): Boolean
    private external fun nativeIsActive(): Boolean

    fun isActive(): Boolean = active

    /** 使用 UsbDeviceConnection 的文件描述符启动 USB 音频输出 */
    fun start(fd: Int): Boolean {
        if (fd < 0) {
            Timber.w("$TAG: 无效的 USB 文件描述符 $fd")
            return false
        }
        return try {
            val ok = nativeSetup(fd)
            if (ok) {
                active = true
                Timber.i("$TAG: USB 音频输出已启动 (fd=$fd)")
            } else {
                Timber.e("$TAG: USB 音频输出启动失败 (fd=$fd)")
            }
            ok
        } catch (e: Throwable) {
            Timber.e(e, "$TAG: 启动 USB 音频输出异常")
            false
        }
    }

    /** 写入 PCM 数据到 USB DAC（16bit 立体声） */
    fun writePcm(data: ByteArray): Boolean {
        if (!active || data.isEmpty()) return false
        return try {
            nativeWritePcm(data)
        } catch (e: Throwable) {
            Timber.w(e, "$TAG: 写入 PCM 失败")
            false
        }
    }

    /** 停止并释放 USB 音频输出 */
    fun stop() {
        if (!active && !nativeIsActive()) return
        active = false
        try {
            nativeStop()
            nativeClose()
            Timber.i("$TAG: USB 音频输出已停止")
        } catch (e: Throwable) {
            Timber.w(e, "$TAG: 停止 USB 音频输出异常")
        }
    }
}
