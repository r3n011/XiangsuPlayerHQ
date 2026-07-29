package com.theveloper.pixelplay.utils

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile

class MiniaudioDecoder private constructor(
    private val nativeHandle: Long,
    val totalFrames: Long,
    val outputSampleRate: Int,
    val outputChannels: Int,
    val outputFormat: Int,
    val bytesPerFrame: Int
) {
    private var closed = false

    fun readFrames(frameCount: Int): ByteArray? {
        if (closed) return null
        val buffer = ByteArray(frameCount * bytesPerFrame)
        val bytesRead = nativeReadFrames(nativeHandle, buffer, frameCount)
        if (bytesRead <= 0) return null
        return buffer.copyOf(bytesRead)
    }

    fun readFrames(frameCount: Int, buffer: ByteArray): Int {
        if (closed) return 0
        val maxBytes = minOf(buffer.size, frameCount * bytesPerFrame)
        val bytesRead = nativeReadFrames(nativeHandle, buffer, maxBytes / bytesPerFrame)
        return if (bytesRead > 0) bytesRead else 0
    }

    fun seekToFrame(frameIndex: Long): Boolean {
        if (closed) return false
        return nativeSeekToFrame(nativeHandle, frameIndex)
    }

    fun getCursorFrame(): Long {
        if (closed) return 0
        return nativeGetCursorFrame(nativeHandle)
    }

    fun close() {
        if (!closed) {
            nativeClose(nativeHandle)
            closed = true
        }
    }

    fun decodeToTempWav(tempDir: File, originalName: String): File? {
        if (closed) return null
        return runCatching {
            val prefix = "miniaudio_decoded_"
            val tempFile = File(tempDir, "$prefix$originalName.wav")
            val totalDataBytes = totalFrames * bytesPerFrame

            // Build WAV header
            val header = ByteArray(44)
            val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray())
            buf.putInt((44 + totalDataBytes - 8).toInt())
            buf.put("WAVE".toByteArray())
            buf.put("fmt ".toByteArray())
            buf.putInt(16)
            buf.putShort(1) // PCM
            buf.putShort(outputChannels.toShort())
            buf.putInt(outputSampleRate)
            val byteRate = outputSampleRate * bytesPerFrame
            buf.putInt(byteRate)
            val blockAlign = (outputChannels * bytesPerFrame / outputChannels.coerceAtLeast(1)).toShort()
            buf.putShort(blockAlign)
            val bitsPerSample = (bytesPerFrame / outputChannels.coerceAtLeast(1)) * 8
            buf.putShort(bitsPerSample.toShort())
            buf.put("data".toByteArray())
            buf.putInt(totalDataBytes.toInt())

            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.write(header)

                // Stream-decode PCM data in chunks
                val chunkFrames = 4096
                val chunkBytes = chunkFrames * bytesPerFrame
                val buffer = ByteArray(chunkBytes)
                var framesWritten = 0L

                while (framesWritten < totalFrames) {
                    val framesNeeded = minOf(chunkFrames.toLong(), totalFrames - framesWritten).toInt()
                    val actualBytes = readFrames(framesNeeded, buffer)
                    if (actualBytes <= 0) break
                    raf.write(buffer, 0, actualBytes)
                    framesWritten += actualBytes / bytesPerFrame
                }

                Timber.tag(TAG).d("Decoded $framesWritten frames to ${tempFile.absolutePath} (${tempFile.length()} bytes)")
            }
            tempFile
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "Failed to decode to temp WAV")
            null
        }
    }

    protected fun finalize() {
        close()
    }

    companion object {
        private const val TAG = "MiniaudioDecoder"

        const val FORMAT_S16 = 1
        const val FORMAT_S24 = 2
        const val FORMAT_S32 = 3
        const val FORMAT_F32 = 4
        const val FORMAT_U8 = 5
        const val FORMAT_UNKNOWN = 0

        init {
            try {
                System.loadLibrary("miniaudio_jni")
                Timber.tag(TAG).d("miniaudio_jni loaded successfully")
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to load miniaudio_jni library")
            }
        }

        fun open(
            filePath: String,
            outputFormat: Int = FORMAT_S16,
            outputChannels: Int = 0,
            outputSampleRate: Int = 0
        ): MiniaudioDecoder? {
            return try {
                val handle = nativeOpen(filePath, outputFormat, outputChannels, outputSampleRate)
                if (handle < 0) {
                    Timber.tag(TAG).e("Failed to open decoder for: $filePath")
                    return null
                }

                val totalFrames = nativeGetTotalFrames(handle)
                val sampleRate = nativeGetOutputSampleRate(handle)
                val channels = nativeGetOutputChannels(handle)
                val format = nativeGetOutputFormat(handle)
                val bytesPerFrame = nativeGetBytesPerFrame(handle)

                if (totalFrames <= 0 || bytesPerFrame <= 0) {
                    Timber.tag(TAG).e("Invalid format info for: $filePath (frames=$totalFrames, bpf=$bytesPerFrame)")
                    nativeClose(handle)
                    return null
                }

                Timber.tag(TAG).d(
                    "Opened: $filePath (frames=$totalFrames, sr=$sampleRate, ch=$channels, fmt=$format, bpf=$bytesPerFrame)"
                )

                MiniaudioDecoder(handle, totalFrames, sampleRate, channels, format, bytesPerFrame)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Exception opening decoder: $filePath")
                null
            }
        }

        private external fun nativeOpen(
            filePath: String,
            outputFormat: Int,
            outputChannels: Int,
            outputSampleRate: Int
        ): Long

        private external fun nativeReadFrames(
            handle: Long,
            buffer: ByteArray,
            frameCount: Int
        ): Int

        private external fun nativeSeekToFrame(handle: Long, frameIndex: Long): Boolean

        private external fun nativeGetTotalFrames(handle: Long): Long

        private external fun nativeGetCursorFrame(handle: Long): Long

        private external fun nativeClose(handle: Long)

        private external fun nativeGetOutputSampleRate(handle: Long): Int

        private external fun nativeGetOutputChannels(handle: Long): Int

        private external fun nativeGetOutputFormat(handle: Long): Int

        private external fun nativeGetBytesPerFrame(handle: Long): Int
    }
}
