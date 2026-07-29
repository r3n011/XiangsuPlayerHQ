package com.theveloper.pixelplay.utils

import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object WavConverter {

    private const val TAG = "WavConverter"
    private const val RIFF_HEADER_SIZE = 12
    private const val FMT_CHUNK_MIN_SIZE = 16

    internal const val FORMAT_PCM = 1
    internal const val FORMAT_IEEE_FLOAT = 3
    internal const val FORMAT_ALAW = 6
    internal const val FORMAT_MLAW = 7

    private const val MAX_SAMPLE_RATE_FOR_32BIT_FLOAT = 384_000

    private const val STANDARD_WAV_RIFF = "RIFF"
    private const val RF64_RIFF = "RF64"
    private const val WAVE_ID = "WAVE"

    private val WAVE64_FMT_GUID =
        byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0xAA.toByte(),
            0x00.toByte(), 0x38.toByte(), 0x9B.toByte(), 0x71.toByte()
        )
    private val WAVE64_DATA_GUID =
        byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0xAA.toByte(),
            0x00.toByte(), 0x38.toByte(), 0x9B.toByte(), 0x71.toByte()
        )

    data class WavInfo(
        val formatTag: Int,
        val numChannels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val dataSize: Long,
        val dataStartPosition: Long,
        val totalFileSize: Long,
        val isRf64: Boolean = false,
        val isWave64: Boolean = false,
        val needsFormatConversion: Boolean = false
    )

    enum class WavVariant {
        STANDARD,
        RF64,
        WAVE64,
        UNSUPPORTED
    }

    fun detectVariant(filePath: String): WavVariant {
        return runCatching {
            val file = File(filePath)
            if (!file.exists() || file.length() < 12) return WavVariant.UNSUPPORTED

            RandomAccessFile(file, "r").use { raf ->
                val id = ByteArray(4)
                raf.readFully(id)
                val riffId = String(id)

                when (riffId) {
                    STANDARD_WAV_RIFF -> {
                        raf.seek(4)
                        val waveId = ByteArray(4)
                        raf.readFully(waveId)
                        if (String(waveId) != WAVE_ID) return WavVariant.UNSUPPORTED

                        // Check first chunk GUID at offset 12 for Wave64
                        raf.seek(12)
                        val firstChunkId = ByteArray(16)
                        val bytesRead = raf.read(firstChunkId)
                        if (bytesRead >= 16 && firstChunkId.contentEquals(WAVE64_FMT_GUID)) {
                            return@use WavVariant.WAVE64
                        }

                        // Also check for Wave64 data GUID as first chunk
                        if (bytesRead >= 16 && firstChunkId.contentEquals(WAVE64_DATA_GUID)) {
                            return@use WavVariant.WAVE64
                        }

                        WavVariant.STANDARD
                    }
                    RF64_RIFF -> {
                        raf.seek(4)
                        val waveId = ByteArray(4)
                        raf.readFully(waveId)
                        if (String(waveId) == WAVE_ID) WavVariant.RF64
                        else WavVariant.UNSUPPORTED
                    }
                    else -> WavVariant.UNSUPPORTED
                }
            }
        }.getOrDefault(WavVariant.UNSUPPORTED)
    }

    fun needsConversion(filePath: String): Boolean {
        return runCatching {
            val file = File(filePath)
            if (!file.exists() || file.length() < RIFF_HEADER_SIZE + FMT_CHUNK_MIN_SIZE) {
                return@runCatching false
            }
            val variant = detectVariant(filePath)
            when (variant) {
                WavVariant.RF64, WavVariant.WAVE64 -> true
                WavVariant.STANDARD -> {
                    val info = readWavInfo(file)
                    if (info == null) return@runCatching false
                    info.formatTag != FORMAT_PCM || info.bitsPerSample > 32
                }
                WavVariant.UNSUPPORTED -> false
            }
        }.getOrDefault(false)
    }

    fun readWavInfo(file: File): WavInfo? {
        return runCatching {
            val variant = detectVariant(file.absolutePath)
            when (variant) {
                WavVariant.RF64 -> readRf64Info(file)
                WavVariant.WAVE64 -> readWave64Info(file)
                WavVariant.STANDARD -> readStandardWavInfo(file)
                WavVariant.UNSUPPORTED -> null
            }
        }.getOrNull()
    }

    private fun readStandardWavInfo(file: File): WavInfo? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val riffId = ByteArray(4)
                raf.readFully(riffId)
                if (String(riffId) != STANDARD_WAV_RIFF) return null

                val fileSize = readLittleEndianInt(raf)
                val waveId = ByteArray(4)
                raf.readFully(waveId)
                if (String(waveId) != WAVE_ID) return null

                var formatTag = 0
                var numChannels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var byteRate = 0
                var blockAlign = 0
                var dataSize = 0L
                var dataStartPosition = 0L
                var foundFmt = false
                var foundData = false

                while (raf.filePointer < raf.length() && (!foundFmt || !foundData)) {
                    val chunkId = ByteArray(4)
                    if (raf.read(chunkId) < 4) break
                    val chunkIdStr = String(chunkId)
                    val chunkSize = readLittleEndianInt(raf).toLong()

                    when (chunkIdStr) {
                        "fmt " -> {
                            formatTag = readLittleEndianShort(raf).toInt()
                            numChannels = readLittleEndianShort(raf).toInt()
                            sampleRate = readLittleEndianInt(raf)
                            byteRate = readLittleEndianInt(raf)
                            blockAlign = readLittleEndianShort(raf).toInt()
                            bitsPerSample = readLittleEndianShort(raf).toInt()

                            if (chunkSize > 16) {
                                raf.seek(raf.filePointer + chunkSize - 16)
                            }
                            foundFmt = true
                        }
                        "data" -> {
                            dataStartPosition = raf.filePointer
                            dataSize = min(chunkSize, raf.length() - dataStartPosition)
                            foundData = true
                            break
                        }
                        else -> {
                            raf.seek(raf.filePointer + chunkSize)
                        }
                    }

                    if (chunkSize % 2 == 1L && chunkIdStr != "data") {
                        raf.seek(raf.filePointer + 1)
                    }
                }

                if (!foundFmt || !foundData) return null

                WavInfo(
                    formatTag = formatTag,
                    numChannels = numChannels,
                    sampleRate = sampleRate,
                    bitsPerSample = bitsPerSample,
                    byteRate = byteRate,
                    blockAlign = blockAlign,
                    dataSize = dataSize,
                    dataStartPosition = dataStartPosition,
                    totalFileSize = file.length()
                )
            }
        }.getOrNull()
    }

    private fun readRf64Info(file: File): WavInfo? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val riffId = ByteArray(4)
                raf.readFully(riffId)
                if (String(riffId) != RF64_RIFF) return null

                val riffSizeLow = readLittleEndianInt(raf).toLong() and 0xFFFFFFFFL
                val waveId = ByteArray(4)
                raf.readFully(waveId)
                if (String(waveId) != WAVE_ID) return null

                var riffSize64 = riffSizeLow
                var dataSize = 0L
                var sampleCount = 0L
                var formatTag = 0
                var numChannels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var byteRate = 0
                var blockAlign = 0
                var dataStartPosition = 0L
                var foundDs64 = false
                var foundFmt = false
                var foundData = false

                while (raf.filePointer < raf.length() && (!foundFmt || !foundData)) {
                    val chunkId = ByteArray(4)
                    if (raf.read(chunkId) < 4) break
                    val chunkIdStr = String(chunkId)
                    val chunkSize = readLittleEndianInt(raf).toLong()

                    when (chunkIdStr) {
                        "ds64" -> {
                            val riffSize = readLittleEndianLong(raf)
                            val dataSizeFromDs64 = readLittleEndianLong(raf)
                            sampleCount = readLittleEndianLong(raf)

                            riffSize64 = riffSize
                            if (dataSizeFromDs64 != 0xFFFFFFFFL) {
                                dataSize = dataSizeFromDs64
                            }

                            if (chunkSize > 24) {
                                raf.seek(raf.filePointer + chunkSize - 24)
                            }
                            foundDs64 = true
                        }
                        "fmt " -> {
                            formatTag = readLittleEndianShort(raf).toInt()
                            numChannels = readLittleEndianShort(raf).toInt()
                            sampleRate = readLittleEndianInt(raf)
                            byteRate = readLittleEndianInt(raf)
                            blockAlign = readLittleEndianShort(raf).toInt()
                            bitsPerSample = readLittleEndianShort(raf).toInt()

                            if (chunkSize > 16) {
                                raf.seek(raf.filePointer + chunkSize - 16)
                            }
                            foundFmt = true
                        }
                        "data" -> {
                            dataStartPosition = raf.filePointer
                            if (!foundDs64 || dataSize == 0L || dataSize == 0xFFFFFFFFL) {
                                dataSize = min(chunkSize, raf.length() - dataStartPosition)
                            }
                            if (dataSize == 0L || dataSize == 0xFFFFFFFFL) {
                                dataSize = raf.length() - dataStartPosition
                            }
                            foundData = true
                            break
                        }
                        else -> {
                            raf.seek(raf.filePointer + chunkSize)
                        }
                    }

                    if (chunkSize % 2 == 1L && chunkIdStr != "data") {
                        raf.seek(raf.filePointer + 1)
                    }
                }

                if (!foundFmt || !foundData) return null

                WavInfo(
                    formatTag = formatTag,
                    numChannels = numChannels,
                    sampleRate = sampleRate,
                    bitsPerSample = bitsPerSample,
                    byteRate = byteRate,
                    blockAlign = blockAlign,
                    dataSize = dataSize,
                    dataStartPosition = dataStartPosition,
                    totalFileSize = file.length(),
                    isRf64 = true,
                    needsFormatConversion = true
                )
            }
        }.getOrNull()
    }

    private fun readWave64Info(file: File): WavInfo? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val riffId = ByteArray(4)
                raf.readFully(riffId)
                if (String(riffId) != STANDARD_WAV_RIFF) return null

                val fileSize = readLittleEndianInt(raf)
                val waveId = ByteArray(4)
                raf.readFully(waveId)
                if (String(waveId) != WAVE_ID) return null

                var formatTag = 0
                var numChannels = 0
                var sampleRate = 0
                var bitsPerSample = 0
                var byteRate = 0
                var blockAlign = 0
                var dataSize = 0L
                var dataStartPosition = 0L
                var foundFmt = false
                var foundData = false

                while (raf.filePointer < raf.length() && (!foundFmt || !foundData)) {
                    val guid = ByteArray(16)
                    if (raf.read(guid) < 16) break
                    val chunkSize = readLittleEndianLong(raf)

                    if (guid.contentEquals(WAVE64_FMT_GUID)) {
                        formatTag = readLittleEndianInt(raf)
                        numChannels = readLittleEndianInt(raf)
                        sampleRate = readLittleEndianInt(raf)
                        byteRate = readLittleEndianInt(raf)
                        blockAlign = readLittleEndianInt(raf)
                        bitsPerSample = readLittleEndianInt(raf)

                        val bytesRead = 6 * 4L
                        if (chunkSize > bytesRead) {
                            raf.seek(raf.filePointer + chunkSize - bytesRead)
                        }
                        foundFmt = true
                    } else if (guid.contentEquals(WAVE64_DATA_GUID)) {
                        dataStartPosition = raf.filePointer
                        dataSize = min(chunkSize, raf.length() - dataStartPosition)
                        foundData = true
                        break
                    } else {
                        raf.seek(raf.filePointer + chunkSize)
                    }
                }

                if (!foundFmt || !foundData) return null

                WavInfo(
                    formatTag = formatTag,
                    numChannels = numChannels,
                    sampleRate = sampleRate,
                    bitsPerSample = bitsPerSample,
                    byteRate = byteRate,
                    blockAlign = blockAlign,
                    dataSize = dataSize,
                    dataStartPosition = dataStartPosition,
                    totalFileSize = file.length(),
                    isWave64 = true,
                    needsFormatConversion = true
                )
            }
        }.getOrNull()
    }

    fun convertToStandardWav(filePath: String): ByteArray? {
        return runCatching {
            val file = File(filePath)
            val variant = detectVariant(filePath)

            when (variant) {
                WavVariant.STANDARD -> convertStandardWavIfNeeded(file)
                WavVariant.RF64 -> convertRf64ToStandard(file)
                WavVariant.WAVE64 -> convertWave64ToStandard(file)
                WavVariant.UNSUPPORTED -> {
                    Timber.tag(TAG).d("Unsupported WAV variant: $filePath")
                    null
                }
            }
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "Failed to convert WAV file: $filePath")
            null
        }
    }

    private fun convertStandardWavIfNeeded(file: File): ByteArray? {
        val info = readWavInfo(file) ?: return null

        val needsBitDepthConversion = info.formatTag == FORMAT_IEEE_FLOAT && info.bitsPerSample == 64
        val needsHighResFix = info.bitsPerSample > 32

        if (!needsBitDepthConversion && !needsHighResFix) {
            return null
        }

        Timber.tag(TAG).i(
            "Converting standard WAV: ${file.name} " +
                "(${info.sampleRate}Hz, ${info.numChannels}ch, ${info.bitsPerSample}bit, ${info.formatTag})"
        )

        val originalData = RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            ByteArray(raf.length().toInt()).also { raf.readFully(it) }
        }

        val bytesPerSample = when {
            needsBitDepthConversion -> 4
            info.bitsPerSample > 32 -> 4
            else -> info.bitsPerSample / 8
        }

        val numSamples = info.dataSize / (info.bitsPerSample / 8)
        val convertedData = ByteArray(
            info.dataStartPosition.toInt() + (numSamples * bytesPerSample).toInt()
        )

        System.arraycopy(
            originalData, 0, convertedData, 0, info.dataStartPosition.toInt()
        )

        val srcBuffer = ByteBuffer.wrap(originalData).order(ByteOrder.LITTLE_ENDIAN)
        val dstBuffer = ByteBuffer.wrap(convertedData).order(ByteOrder.LITTLE_ENDIAN)
        srcBuffer.position(info.dataStartPosition.toInt())
        dstBuffer.position(info.dataStartPosition.toInt())

        var convertedCount = 0L
        for (i in 0 until numSamples) {
            when {
                needsBitDepthConversion && srcBuffer.remaining() >= 8 -> {
                    val doubleValue = srcBuffer.getDouble()
                    dstBuffer.putFloat(doubleValue.toFloat())
                    convertedCount++
                }
                info.bitsPerSample == 32 && info.formatTag == FORMAT_IEEE_FLOAT && srcBuffer.remaining() >= 4 -> {
                    dstBuffer.putFloat(srcBuffer.getFloat())
                    convertedCount++
                }
                info.bitsPerSample == 24 && srcBuffer.remaining() >= 3 -> {
                    val b0 = srcBuffer.get().toInt() and 0xFF
                    val b1 = srcBuffer.get().toInt() and 0xFF
                    val b2 = srcBuffer.get().toInt() and 0xFF
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    dstBuffer.putInt(sample24.coerceIn(0, 0xFFFFFF))
                    convertedCount++
                }
                else -> break
            }
        }

        updateStandardWavHeader(convertedData, info, bytesPerSample, convertedCount)

        Timber.tag(TAG).i("Converted $convertedCount samples")
        return convertedData
    }

    private fun convertRf64ToStandard(file: File): ByteArray? {
        val info = readRf64Info(file) ?: return null

        Timber.tag(TAG).i(
            "Converting RF64 to standard WAV: ${file.name} " +
                "(${info.sampleRate}Hz, ${info.numChannels}ch, ${info.bitsPerSample}bit)"
        )

        val originalData = RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            ByteArray(raf.length().toInt()).also { raf.readFully(it) }
        }

        val bytesPerSample = info.bitsPerSample / 8
        val numSamples = info.dataSize / bytesPerSample

        val convertedData = ByteArray(
            44 + (numSamples * bytesPerSample).toInt()
        )

        val buffer = ByteBuffer.wrap(convertedData).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(STANDARD_WAV_RIFF.toByteArray())
        val dataSize = (numSamples * bytesPerSample).toInt()
        val riffSize = 36 + dataSize
        buffer.putInt(riffSize)
        buffer.put(WAVE_ID.toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(info.formatTag.toShort())
        buffer.putShort(info.numChannels.toShort())
        buffer.putInt(info.sampleRate)
        buffer.putInt(info.byteRate)
        buffer.putShort(info.blockAlign.toShort())
        buffer.putShort(info.bitsPerSample.toShort())

        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        val srcBuffer = ByteBuffer.wrap(originalData).order(ByteOrder.LITTLE_ENDIAN)
        val dstBuffer = ByteBuffer.wrap(convertedData).order(ByteOrder.LITTLE_ENDIAN)
        srcBuffer.position(info.dataStartPosition.toInt())
        dstBuffer.position(44)

        var convertedCount = 0L
        for (i in 0 until numSamples) {
            if (srcBuffer.remaining() < bytesPerSample || dstBuffer.remaining() < bytesPerSample) break
            when (bytesPerSample) {
                1 -> dstBuffer.put(srcBuffer.get())
                2 -> dstBuffer.putShort(srcBuffer.getShort())
                3 -> {
                    val b0 = srcBuffer.get()
                    val b1 = srcBuffer.get()
                    val b2 = srcBuffer.get()
                    dstBuffer.put(b0)
                    dstBuffer.put(b1)
                    dstBuffer.put(b2)
                    dstBuffer.put(0)
                }
                4 -> dstBuffer.putInt(srcBuffer.getInt())
                else -> {
                    for (j in 0 until bytesPerSample) {
                        dstBuffer.put(srcBuffer.get())
                    }
                }
            }
            convertedCount++
        }

        Timber.tag(TAG).i("Converted RF64: $convertedCount samples")
        return convertedData
    }

    private fun convertWave64ToStandard(file: File): ByteArray? {
        val info = readWave64Info(file) ?: return null

        Timber.tag(TAG).i(
            "Converting Wave64 to standard WAV: ${file.name} " +
                "(${info.sampleRate}Hz, ${info.numChannels}ch, ${info.bitsPerSample}bit)"
        )

        val originalData = RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            ByteArray(raf.length().toInt()).also { raf.readFully(it) }
        }

        val bytesPerSample = info.bitsPerSample / 8
        val numSamples = info.dataSize / bytesPerSample

        val convertedData = ByteArray(
            44 + (numSamples * bytesPerSample).toInt()
        )

        val buffer = ByteBuffer.wrap(convertedData).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(STANDARD_WAV_RIFF.toByteArray())
        val dataSize = (numSamples * bytesPerSample).toInt()
        val riffSize = 36 + dataSize
        buffer.putInt(riffSize)
        buffer.put(WAVE_ID.toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(info.formatTag.toShort())
        buffer.putShort(info.numChannels.toShort())
        buffer.putInt(info.sampleRate)
        buffer.putInt(info.byteRate)
        buffer.putShort(info.blockAlign.toShort())
        buffer.putShort(info.bitsPerSample.toShort())

        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        val srcBuffer = ByteBuffer.wrap(originalData).order(ByteOrder.LITTLE_ENDIAN)
        val dstBuffer = ByteBuffer.wrap(convertedData).order(ByteOrder.LITTLE_ENDIAN)
        srcBuffer.position(info.dataStartPosition.toInt())
        dstBuffer.position(44)

        var convertedCount = 0L
        for (i in 0 until numSamples) {
            if (srcBuffer.remaining() < bytesPerSample || dstBuffer.remaining() < bytesPerSample) break
            when (bytesPerSample) {
                1 -> dstBuffer.put(srcBuffer.get())
                2 -> dstBuffer.putShort(srcBuffer.getShort())
                3 -> {
                    val b0 = srcBuffer.get()
                    val b1 = srcBuffer.get()
                    val b2 = srcBuffer.get()
                    dstBuffer.put(b0)
                    dstBuffer.put(b1)
                    dstBuffer.put(b2)
                    dstBuffer.put(0)
                }
                4 -> dstBuffer.putInt(srcBuffer.getInt())
                else -> {
                    for (j in 0 until bytesPerSample) {
                        dstBuffer.put(srcBuffer.get())
                    }
                }
            }
            convertedCount++
        }

        Timber.tag(TAG).i("Converted Wave64: $convertedCount samples")
        return convertedData
    }

    private fun updateStandardWavHeader(
        data: ByteArray,
        originalInfo: WavInfo,
        newBytesPerSample: Int,
        newSampleCount: Long
    ) {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val newDataSize = newSampleCount * newBytesPerSample
        val newFileSize = RIFF_HEADER_SIZE + 16 + newDataSize
        buffer.putInt(4, (newFileSize - 8).toInt())
        buffer.putShort(34, newBytesPerSample.toShort())
        val newByteRate = originalInfo.sampleRate * originalInfo.numChannels * newBytesPerSample
        buffer.putInt(28, newByteRate)
        val newBlockAlign = (originalInfo.numChannels * newBytesPerSample).toShort()
        buffer.putShort(32, newBlockAlign)
        buffer.putInt(40, newDataSize.toInt())
    }

    fun createTempConvertedFile(filePath: String, tempDir: File): String? {
        val file = File(filePath)
        val variant = detectVariant(filePath)
        val info = readWavInfo(file) ?: return null

        val originalName = file.nameWithoutExtension
        val prefix = when (variant) {
            WavVariant.RF64 -> "converted_rf64_"
            WavVariant.WAVE64 -> "converted_wave64_"
            else -> "converted_"
        }
        val tempFile = File(tempDir, "$prefix$originalName.wav")

        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                RandomAccessFile(tempFile, "rw").use { outRaf ->
                    // Build new PCM WAV header
                    val newBytesPerSample = (if (info.formatTag == FORMAT_IEEE_FLOAT) 2 else (info.bitsPerSample / 8)).coerceAtLeast(1)
                    val newSampleCount = info.dataSize / (info.bitsPerSample / 8)
                    val newDataSize = newSampleCount * newBytesPerSample
                    val newFileSize = 44L + newDataSize

                    // Write header
                    val header = ByteArray(44)
                    val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    buf.put("RIFF".toByteArray())
                    buf.putInt((newFileSize - 8).toInt())
                    buf.put("WAVE".toByteArray())
                    buf.put("fmt ".toByteArray())
                    buf.putInt(16)
                    buf.putShort(1) // PCM
                    buf.putShort(info.numChannels.toShort())
                    buf.putInt(info.sampleRate)
                    val newByteRate = info.sampleRate * info.numChannels * newBytesPerSample
                    buf.putInt(newByteRate)
                    val newBlockAlign = (info.numChannels * newBytesPerSample).toShort()
                    buf.putShort(newBlockAlign)
                    buf.putShort((newBytesPerSample * 8).toShort())
                    buf.put("data".toByteArray())
                    buf.putInt(newDataSize.toInt())
                    outRaf.write(header)

                    // Stream-convert PCM data in 1MB chunks
                    val chunkSize = 1024 * 1024
                    val inBuffer = ByteArray(chunkSize)
                    val outBuffer = ByteArray(chunkSize)

                    raf.seek(info.dataStartPosition)
                    var remainingData = info.dataSize
                    var convertedSamples = 0L

                    while (remainingData > 0) {
                        val toRead = minOf(chunkSize.toLong(), remainingData).toInt()
                        val actualRead = raf.read(inBuffer, 0, toRead)
                        if (actualRead <= 0) break

                        val srcBuf = ByteBuffer.wrap(inBuffer, 0, actualRead).order(ByteOrder.LITTLE_ENDIAN)
                        val dstBuf = ByteBuffer.wrap(outBuffer).order(ByteOrder.LITTLE_ENDIAN)

                        val bytesPerSrcSample = info.bitsPerSample / 8
                        val samplesInChunk = actualRead / bytesPerSrcSample

                        for (s in 0 until samplesInChunk) {
                            when {
                                info.formatTag == FORMAT_IEEE_FLOAT && info.bitsPerSample == 64 -> {
                                    if (srcBuf.remaining() < 8) break
                                    val doubleVal = srcBuf.getDouble()
                                    dstBuf.putShort((doubleVal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE.toDouble()).toInt().toShort())
                                    convertedSamples++
                                }
                                info.formatTag == FORMAT_IEEE_FLOAT && info.bitsPerSample == 32 -> {
                                    if (srcBuf.remaining() < 4) break
                                    val floatVal = srcBuf.getFloat()
                                    dstBuf.putShort((floatVal.coerceIn(-1f, 1f) * Short.MAX_VALUE.toFloat()).toInt().toShort())
                                    convertedSamples++
                                }
                                info.bitsPerSample == 32 && info.formatTag == FORMAT_PCM -> {
                                    if (srcBuf.remaining() < 4) break
                                    dstBuf.putInt(srcBuf.getInt())
                                    convertedSamples++
                                }
                                info.bitsPerSample == 24 -> {
                                    if (srcBuf.remaining() < 3) break
                                    val b0 = srcBuf.get().toInt() and 0xFF
                                    val b1 = srcBuf.get().toInt() and 0xFF
                                    val b2 = srcBuf.get().toInt() and 0xFF
                                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                                    dstBuf.putInt(sample24.coerceIn(0, 0xFFFFFF))
                                    convertedSamples++
                                }
                                info.bitsPerSample == 16 -> {
                                    if (srcBuf.remaining() < 2) break
                                    dstBuf.putShort(srcBuf.getShort())
                                    convertedSamples++
                                }
                                info.bitsPerSample == 8 -> {
                                    if (srcBuf.remaining() < 1) break
                                    dstBuf.put(srcBuf.get())
                                    convertedSamples++
                                }
                                else -> {
                                    if (srcBuf.remaining() < bytesPerSrcSample) break
                                    for (b in 0 until bytesPerSrcSample) {
                                        dstBuf.put(srcBuf.get())
                                    }
                                    convertedSamples++
                                }
                            }
                        }

                        val outBytes = convertedSamples * newBytesPerSample
                        outRaf.write(outBuffer, 0, outBytes.toInt().coerceAtMost(chunkSize))
                        remainingData -= actualRead
                    }

                    Timber.tag(TAG).i("Stream-converted ${file.name}: $convertedSamples samples → ${tempFile.length()} bytes")
                }
            }
            tempFile.absolutePath
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "Failed to create temp converted file")
            null
        }
    }

    private fun readLittleEndianInt(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return (b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or
            ((b[3].toInt() and 0xFF) shl 24)
    }

    private fun readLittleEndianShort(raf: RandomAccessFile): Short {
        val b = ByteArray(2)
        raf.readFully(b)
        return ((b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun readLittleEndianLong(raf: RandomAccessFile): Long {
        val b = ByteArray(8)
        raf.readFully(b)
        return (b[0].toLong() and 0xFFL) or
            ((b[1].toLong() and 0xFFL) shl 8) or
            ((b[2].toLong() and 0xFFL) shl 16) or
            ((b[3].toLong() and 0xFFL) shl 24) or
            ((b[4].toLong() and 0xFFL) shl 32) or
            ((b[5].toLong() and 0xFFL) shl 40) or
            ((b[6].toLong() and 0xFFL) shl 48) or
            ((b[7].toLong() and 0xFFL) shl 56)
    }
}