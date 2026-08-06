package com.theveloper.pixelplay.data.service.audioengine

/**
 * AAudio 原生输出 JNI 包装（见 cpp/aaudio_output.c）。
 *
 * 所有方法均为静态，返回的 handle 是 native 侧 aaudio_output* 指针。
 * 失败时 nativeCreate 返回 0，write 返回负 AAudio 错误码。
 */
object AaudioNativeOutput {

    init {
        System.loadLibrary("aaudio_output")
    }

    /**
     * 创建 AAudio 输出流。
     * @param sampleRate 采样率（Media3 输出采样率，AAudio 自动转换到设备采样率）
     * @param channels 声道数（1..8）
     * @param format 0=PCM16, 1=FLOAT32
     * @return native handle（0 = 创建失败）
     */
    external fun nativeCreate(sampleRate: Int, channels: Int, format: Int): Long

    /** 启动/恢复播放。返回 0=成功，负值=错误码。 */
    external fun nativeStart(handle: Long): Int

    /** 暂停。返回 0=成功，负值=错误码。 */
    external fun nativePause(handle: Long): Int

    /** 丢弃未播放缓冲（重置位置基准）。返回 0=成功，负值=错误码。 */
    external fun nativeFlush(handle: Long): Int

    /** 排空后停止。返回 0=成功，负值=错误码。 */
    external fun nativeStop(handle: Long): Int

    /** 释放流。返回 0=成功。 */
    external fun nativeRelease(handle: Long): Int

    /**
     * 写入 PCM。
     * @return 实际写入的字节数（0 = 缓冲满等待超时），负值 = AAudio 错误码。
     */
    external fun nativeWrite(handle: Long, data: ByteArray, offset: Int, size: Int): Int

    /** 已播放帧数（相对 flush/stop 基准）。 */
    external fun nativeGetFramesRead(handle: Long): Long

    /** 流的应用侧采样率。 */
    external fun nativeGetSampleRate(handle: Long): Int

    /** 缓冲容量（帧）。 */
    external fun nativeGetBufferCapacityInFrames(handle: Long): Long

    /** 设置音量系数（0..1）。 */
    external fun nativeSetVolume(handle: Long, volume: Float)
}
