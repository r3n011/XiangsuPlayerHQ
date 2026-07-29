package com.theveloper.pixelplay.utils

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

object DffDecoder {

    private const val TAG = "DffDecoder"
    private const val DSDIFF_MAGIC = "DSDIFF"
    private const val FRM_CHUNK = "FRM "
    private const val DSD_MAGIC = "DSD "
    private const val DIFF_MAGIC = "DIFF"

    // Progress callback: (progressPercent, stageDescription)
    @Volatile
    var progressCallback: ((Int, String) -> Unit)? = null

    private data class DffInfo(
        val channels: Int,
        val dsdSampleRate: Long,
        val totalDsdFrames: Long,
        val dataStartPosition: Long,
        val dataSize: Long
    )

    private data class DecodeConfig(
        val bitLsbFirst: Boolean,
        val bitOffset: Int,
        val decimationFactor: Int
    )

    fun decodeToWav(filePath: String, outputDir: File): File? {
        val file = File(filePath)
        val info = readDffInfo(file) ?: run {
            Timber.tag(TAG).e("Failed to read DFF info: ${file.name}")
            return null
        }

        val originalName = file.nameWithoutExtension
        outputDir.mkdirs()
        val tempFile = File(outputDir, "dff_decoded_$originalName.wav")

        progressCallback?.invoke(0, "开始转码 ${file.name}")
        TranscodeProgressManager.start(file.name)

        return runCatching {
            val outputSampleRate = selectOutputRate(info.dsdSampleRate)
            val decimationFactor = info.dsdSampleRate / outputSampleRate
            val totalPcmFrames = info.totalDsdFrames / decimationFactor
            val bytesPerSample = 2
            val totalDataBytes = totalPcmFrames * info.channels * bytesPerSample
            val durationSeconds = info.totalDsdFrames.toDouble() / info.dsdSampleRate

            Timber.tag(TAG).i(
                "DFF decode: ${file.name}, ${info.channels}ch, " +
                    "${info.dsdSampleRate}Hz DSD → ${outputSampleRate}Hz PCM, " +
                    "decimation=$decimationFactor, pcmFrames=$totalPcmFrames, " +
                    "duration=%.1fs".format(durationSeconds)
            )

            val header = buildWavHeader(
                outputSampleRate, info.channels, bytesPerSample, totalDataBytes
            )

            // Use 64-tap filter for better performance (was 128)
            val filterTaps = 64
            val filter = designLowpassFilterFloat(decimationFactor.toInt(), filterTaps)
            val filterMid = filterTaps / 2

            RandomAccessFile(file, "r").use { raf ->
                raf.seek(info.dataStartPosition)

                RandomAccessFile(tempFile, "rw").use { outRaf ->
                    outRaf.write(header)

                    // Pre-read a chunk for auto-detection
                    val detectPcmFrames = 128
                    val detectDsdFrames = (detectPcmFrames.toLong() * decimationFactor).toInt()
                    val detectDsdBytes = (detectDsdFrames.toLong() * info.channels / 8)
                        .coerceAtMost(2 * 1024 * 1024).toInt()
                    val detectBuffer = ByteArray(detectDsdBytes)
                    val actualFirstRead = raf.read(detectBuffer, 0, detectDsdBytes)

                    if (actualFirstRead <= 0) {
                        Timber.tag(TAG).e("Failed to read DSD data")
                        return@use null
                    }

                    progressCallback?.invoke(5, "检测音频格式...")
                    TranscodeProgressManager.update(5, "检测音频格式...")

                    val bestConfig = findBestConfig(
                        detectBuffer, actualFirstRead, info.channels,
                        decimationFactor.toInt(), filterTaps, filter, filterMid
                    )

                    Timber.tag(TAG).i(
                        "DSD config: LSB=${bestConfig.bitLsbFirst}, " +
                            "bitOffset=${bestConfig.bitOffset}, " +
                            "decimation=${bestConfig.decimationFactor}"
                    )

                    progressCallback?.invoke(10, "格式检测完成，开始解码...")
                    TranscodeProgressManager.update(10, "格式检测完成，开始解码...")

                    // Production decoding
                    raf.seek(info.dataStartPosition)

                    val chunkFrames = 4096
                    val pcmChunkBytes = chunkFrames * info.channels * bytesPerSample
                    val pcmBuffer = ByteArray(pcmChunkBytes)

                    // Need extra DSD frames for filter ring (filterMid on each side)
                    val ringExtraDsd = filterMid + bestConfig.decimationFactor
                    val dsdChunkFrames = (chunkFrames.toLong() * bestConfig.decimationFactor + ringExtraDsd * 2).toInt()
                    val dsdChunkBytes = (dsdChunkFrames.toLong() * info.channels / 8)
                        .coerceAtMost(4L * 1024 * 1024).toInt()
                    val dsdBuffer = ByteArray(dsdChunkBytes)

                    // Pre-extracted DSD bit plane buffer (float for speed)
                    val dsdBitPlanes = FloatArray(dsdChunkFrames * info.channels)

                    var pcmFramesWritten = 0L
                    var totalBytesWritten = 0L
                    var lastProgress = 10

                    while (pcmFramesWritten < totalPcmFrames) {
                        val pcmFramesInChunk =
                            minOf(chunkFrames.toLong(), totalPcmFrames - pcmFramesWritten)
                        val dsdFramesNeeded =
                            (pcmFramesInChunk * bestConfig.decimationFactor + ringExtraDsd * 2).toInt()
                        val dsdBytesNeeded =
                            (dsdFramesNeeded.toLong() * info.channels / 8)
                                .coerceAtMost(dsdChunkBytes.toLong()).toInt()

                        var dsdBytesRead = 0
                        while (dsdBytesRead < dsdBytesNeeded) {
                            val toRead = minOf(
                                dsdBytesNeeded - dsdBytesRead,
                                dsdChunkBytes - dsdBytesRead
                            )
                            val actualRead = raf.read(dsdBuffer, dsdBytesRead, toRead)
                            if (actualRead <= 0) break
                            dsdBytesRead += actualRead
                        }

                        if (dsdBytesRead == 0) break

                        // Step 1: Pre-extract DSD bits into float array
                        extractDsdBits(
                            dsdBuffer, dsdBytesRead, info.channels,
                            bestConfig, dsdBitPlanes, dsdChunkFrames
                        )

                        // Step 2: Convolve with filter
                        convolveDsdToPcm(
                            dsdBitPlanes, dsdChunkFrames, info.channels,
                            bestConfig, filter, filterMid,
                            pcmBuffer, 0, pcmFramesInChunk.toInt()
                        )

                        pcmFramesWritten += pcmFramesInChunk

                        val writeBytes =
                            (pcmFramesInChunk * info.channels * bytesPerSample).toInt()
                        outRaf.write(pcmBuffer, 0, writeBytes.coerceAtMost(pcmChunkBytes))
                        totalBytesWritten += writeBytes

                        // Report progress every 10%
                        val progress = 10 + ((pcmFramesWritten * 90) / totalPcmFrames).toInt()
                        if (progress >= lastProgress + 10) {
                            lastProgress = progress
                            progressCallback?.invoke(
                                progress,
                                "转码中 ${progress}% (${pcmFramesWritten}/${totalPcmFrames} 帧)"
                            )
                            TranscodeProgressManager.update(progress, "转码中 ${progress}%")
                            Timber.tag(TAG).i(
                                "Transcoding progress: $progress% " +
                                    "($pcmFramesWritten/$totalPcmFrames frames)"
                            )
                        }
                    }

                    progressCallback?.invoke(100, "转码完成")
                    TranscodeProgressManager.update(100, "转码完成")

                    // Fix header
                    val actualDataBytes = totalBytesWritten.toInt()
                    val actualDuration = pcmFramesWritten.toDouble() / outputSampleRate
                    val fixedHeader = buildWavHeader(
                        outputSampleRate, info.channels, bytesPerSample,
                        actualDataBytes.toLong()
                    )
                    outRaf.seek(0)
                    outRaf.write(fixedHeader)

                    Timber.tag(TAG).i(
                        "Decoded ${file.name} → ${tempFile.name} " +
                            "(${tempFile.length()} bytes, $pcmFramesWritten frames, " +
                            "%.1fs)".format(actualDuration)
                    )
                }
            }

            TranscodeProgressManager.complete()
            tempFile
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "DFF decode failed: ${file.name}")
            TranscodeProgressManager.reset()
            null
        }
    }

    private fun selectOutputRate(dsdSampleRate: Long): Int {
        val candidates = mutableListOf<Int>()

        for (div in listOf(32, 64, 16, 128)) {
            val rate = dsdSampleRate / div
            if (rate > 0 && rate <= 352800 && rate.toInt().toLong() == rate) {
                candidates.add(rate.toInt())
            }
        }

        when (dsdSampleRate) {
            2_822_400L -> candidates.add(88200)
            5_644_800L -> candidates.add(176400)
            1_411_200L -> candidates.add(44100)
            44_100_000L -> candidates.add(352800)
        }

        val validRates = candidates
            .filter { it > 0 && it <= 192000 }
            .distinct()
            .sortedDescending()

        if (validRates.isNotEmpty()) {
            return validRates.first()
        }

        return when {
            dsdSampleRate % 44100 == 0L -> 44100
            dsdSampleRate % 48000 == 0L -> 48000
            else -> 44100
        }
    }

    private fun buildWavHeader(
        sampleRate: Int,
        channels: Int,
        bytesPerSample: Int,
        totalDataBytes: Long
    ): ByteArray {
        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt((44 + totalDataBytes - 8).toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        val byteRate = sampleRate * channels * bytesPerSample
        buf.putInt(byteRate)
        buf.putShort((channels * bytesPerSample).toShort())
        buf.putShort((bytesPerSample * 8).toShort())
        buf.put("data".toByteArray())
        buf.putInt(totalDataBytes.toInt())
        return header
    }

    private fun designLowpassFilter(
        decimationFactor: Int,
        tapCount: Int
    ): DoubleArray {
        val nyquist = 1.0 / (2.0 * decimationFactor)
        val cutoff = nyquist * 0.4

        val taps = DoubleArray(tapCount)
        val mid = tapCount / 2

        for (i in 0 until tapCount) {
            val n = i - mid
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (tapCount - 1).toDouble()))
            val s = if (n == 0) {
                2.0 * cutoff
            } else {
                sin(2.0 * PI * cutoff * n) / (PI * n)
            }
            taps[i] = s * w
        }

        var sum = 0.0
        for (t in taps) sum += t
        val norm = decimationFactor.toDouble() / sum
        for (i in 0 until tapCount) taps[i] *= norm

        return taps
    }

    /**
     * Design lowpass filter using FloatArray for better performance on Android
     */
    private fun designLowpassFilterFloat(
        decimationFactor: Int,
        tapCount: Int
    ): FloatArray {
        val nyquist = 1.0f / (2.0f * decimationFactor)
        val cutoff = nyquist * 0.4f
        val piF = PI.toFloat()

        val taps = FloatArray(tapCount)
        val mid = tapCount / 2

        for (i in 0 until tapCount) {
            val n = i - mid
            val w = 0.5f * (1.0f - cos(2.0f * piF * i / (tapCount - 1).toFloat()))
            val s = if (n == 0) {
                2.0f * cutoff
            } else {
                sin(2.0f * piF * cutoff * n) / (piF * n)
            }
            taps[i] = s * w
        }

        var sum = 0.0f
        for (t in taps) sum += t
        val norm = decimationFactor.toFloat() / sum
        for (i in 0 until tapCount) taps[i] *= norm

        return taps
    }

    /**
     * Pre-extract all DSD bits from byte buffer into a float array.
     * Output layout: dsdBitPlanes[frame * channels + channel] = ±1.0
     * This eliminates byte/bit extraction from the hot convolution loop.
     */
    private fun extractDsdBits(
        dsdData: ByteArray,
        dsdBytes: Int,
        channels: Int,
        config: DecodeConfig,
        dsdBitPlanes: FloatArray,
        totalDsdFrames: Int
    ) {
        val bitLsbFirst = config.bitLsbFirst
        val bitOffset = config.bitOffset

        for (frame in 0 until totalDsdFrames) {
            for (ch in 0 until channels) {
                val dsdFrameIdx = frame.toLong() + bitOffset

                val byteIdx = ((dsdFrameIdx / 8).toInt() * channels) + ch
                if (byteIdx in 0 until dsdBytes) {
                    val bitIdx = if (bitLsbFirst) {
                        (dsdFrameIdx % 8).toInt()
                    } else {
                        7 - (dsdFrameIdx % 8).toInt()
                    }
                    val bit = (dsdData[byteIdx].toInt() shr bitIdx) and 1
                    dsdBitPlanes[frame * channels + ch] = if (bit == 1) 1.0f else -1.0f
                } else {
                    dsdBitPlanes[frame * channels + ch] = 0.0f
                }
            }
        }
    }

    /**
     * Fast convolution: pre-extracted DSD bit planes × filter taps → PCM output
     * Inner loop is just multiply-accumulate on a flat float array.
     * Optimized with float array and reduced bounds checking.
     */
    private fun convolveDsdToPcm(
        dsdBitPlanes: FloatArray,
        totalDsdFrames: Int,
        channels: Int,
        config: DecodeConfig,
        filter: FloatArray,
        filterMid: Int,
        pcmOutput: ByteArray,
        pcmOffset: Int,
        pcmFrames: Int
    ) {
        val decimationFactor = config.decimationFactor
        val invDecimation = 1.0f / decimationFactor
        val maxSample = Short.MAX_VALUE.toFloat()
        val minSample = Short.MIN_VALUE.toFloat()

        for (frame in 0 until pcmFrames) {
            val centerFrame = (frame.toLong() * decimationFactor + decimationFactor / 2).toInt()

            for (ch in 0 until channels) {
                var sum = 0.0f

                // Convolve: sum of filter[t] * dsdBits[centerFrame + t - filterMid]
                var idx = (centerFrame - filterMid) * channels + ch
                val filterSize = filter.size
                val bitPlanesSize = dsdBitPlanes.size
                
                for (t in 0 until filterSize) {
                    if (idx >= 0 && idx < bitPlanesSize) {
                        sum += dsdBitPlanes[idx] * filter[t]
                    }
                    idx += channels
                }

                val pcmValue = (sum * invDecimation * maxSample)
                    .coerceIn(minSample, maxSample)
                    .toInt().toShort()

                val outPos = pcmOffset + (frame * channels + ch) * 2
                if (outPos + 1 < pcmOutput.size) {
                    val v = pcmValue.toInt()
                    pcmOutput[outPos] = (v and 0xFF).toByte()
                    pcmOutput[outPos + 1] = ((v shr 8) and 0xFF).toByte()
                }
            }
        }
    }

    private fun findBestConfig(
        dsdData: ByteArray,
        dsdBytes: Int,
        channels: Int,
        baseDecimation: Int,
        filterTaps: Int,
        filter: FloatArray,
        filterMid: Int
    ): DecodeConfig {
        val candidates = mutableListOf<Pair<DecodeConfig, Double>>()
        val pcmBuffer = ByteArray(64 * channels * 2)

        // Only test MSB-first (standard) with bit offsets 0-7
        // This is the DSDIFF standard - reduces search space from 16 to 8
        val decim = baseDecimation

        for (bitOff in 0 until 8) {
            val config = DecodeConfig(
                bitLsbFirst = false,
                bitOffset = bitOff,
                decimationFactor = decim
            )

            val testFrames = 32
            val dsdFramesNeeded = (testFrames.toLong() * decim + filterMid * 2).toInt()
            val dsdFramesNeededSafe = dsdFramesNeeded.coerceAtMost(dsdBytes * 8 / channels)

            // Extract bits
            val bitPlanes = FloatArray(dsdFramesNeededSafe * channels)
            extractDsdBits(
                dsdData, dsdBytes, channels,
                config, bitPlanes, dsdFramesNeededSafe
            )

            // Convolve
            convolveDsdToPcm(
                bitPlanes, dsdFramesNeededSafe, channels,
                config, filter, filterMid,
                pcmBuffer, 0, testFrames
            )

            val score = evaluateAudioQuality(pcmBuffer, 0, testFrames * channels)
            candidates.add(config to score)
        }

        // Also try LSB-first just in case
        for (bitOff in 0 until 8) {
            val config = DecodeConfig(
                bitLsbFirst = true,
                bitOffset = bitOff,
                decimationFactor = decim
            )

            val testFrames = 32
            val dsdFramesNeeded = (testFrames.toLong() * decim + filterMid * 2).toInt()
            val dsdFramesNeededSafe = dsdFramesNeeded.coerceAtMost(dsdBytes * 8 / channels)

            val bitPlanes = FloatArray(dsdFramesNeededSafe * channels)
            extractDsdBits(
                dsdData, dsdBytes, channels,
                config, bitPlanes, dsdFramesNeededSafe
            )

            convolveDsdToPcm(
                bitPlanes, dsdFramesNeededSafe, channels,
                config, filter, filterMid,
                pcmBuffer, 0, testFrames
            )

            val score = evaluateAudioQuality(pcmBuffer, 0, testFrames * channels)
            candidates.add(config to score)
        }

        candidates.sortByDescending { it.second }

        Timber.tag(TAG).d("Top 5 configs:")
        candidates.take(5).forEach { (config, score) ->
            Timber.tag(TAG).d(
                "  LSB=${config.bitLsbFirst}, bitOff=${config.bitOffset}: score=%.1f".format(score)
            )
        }

        return candidates.first().first
    }

    private fun evaluateAudioQuality(buffer: ByteArray, offset: Int, sampleCount: Int): Double {
        if (sampleCount < 4) return 0.0

        var zeroCrossings = 0
        var level = 0.0
        var minVal = Int.MAX_VALUE
        var maxVal = Int.MIN_VALUE

        for (i in 0 until sampleCount) {
            val pos = offset + i * 2
            if (pos + 1 >= buffer.size) break
            val sample = (buffer[pos].toInt() and 0xFF) or
                ((buffer[pos + 1].toInt() and 0xFF) shl 8)
            val signedSample = if (sample > Short.MAX_VALUE.toInt()) {
                sample - 65536
            } else {
                sample
            }
            level += kotlin.math.abs(signedSample)
            if (signedSample < minVal) minVal = signedSample
            if (signedSample > maxVal) maxVal = signedSample

            if (i > 0) {
                val prevPos = offset + (i - 1) * 2
                val prevSample = (buffer[prevPos].toInt() and 0xFF) or
                    ((buffer[prevPos + 1].toInt() and 0xFF) shl 8)
                val prevSigned = if (prevSample > Short.MAX_VALUE.toInt()) {
                    prevSample - 65536
                } else {
                    prevSample
                }
                if ((signedSample >= 0) != (prevSigned >= 0)) {
                    zeroCrossings++
                }
            }
        }

        val actualCount = minOf(sampleCount, (buffer.size - offset) / 2)
        if (actualCount < 4) return 0.0

        val avgLevel = level / actualCount
        val zcRate = zeroCrossings.toDouble() / actualCount
        val range = (maxVal - minVal).toDouble()

        val levelScore = if (avgLevel > 100.0 && avgLevel < 30000.0) {
            1.0
        } else if (avgLevel > 10.0) {
            0.5
        } else {
            0.01
        }

        val zcScore = when {
            zcRate < 0.05 -> 1.0
            zcRate < 0.15 -> 0.7
            zcRate < 0.3 -> 0.3
            else -> 0.0
        }

        val rangeScore = if (range > 1000.0) 1.0 else if (range > 100.0) 0.5 else 0.1

        return levelScore * 0.4 + zcScore * 0.35 + rangeScore * 0.25
    }

    private fun readDffInfo(file: File): DffInfo? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(32)
                val headerRead = raf.read(header)
                val headerStr = String(header, 0, headerRead.coerceAtLeast(0))

                Timber.tag(TAG).d(
                    "File header (${file.length()} bytes): " +
                        header.take(32).joinToString(" ") { "%02X".format(it) }
                )

                when {
                    headerStr.startsWith(DSDIFF_MAGIC) || headerStr.startsWith(DIFF_MAGIC) -> {
                        Timber.tag(TAG).i("Detected DSDIFF container")
                        parseDsdiff(raf, file)
                    }
                    headerStr.startsWith(DSD_MAGIC) -> {
                        Timber.tag(TAG).i("Detected DSD container (raw)")
                        parseRawDsd(raf, file, header)
                    }
                    else -> {
                        Timber.tag(TAG).i("Unknown DFF container, trying raw DSD interpretation")
                        tryRawDsd(file, header)
                    }
                }
            }
        }.getOrNull()
    }

    private fun parseDsdiff(raf: RandomAccessFile, file: File): DffInfo? {
        raf.seek(8)

        val programFormat = raf.read()
        val numChannels = raf.read()

        val srBytes = ByteArray(4)
        raf.readFully(srBytes)
        val dsdSampleRate = ByteBuffer.wrap(srBytes).order(ByteOrder.BIG_ENDIAN).int.toLong()

        Timber.tag(TAG).d("DSDIFF: channels=$numChannels, sampleRate=$dsdSampleRate")

        var totalDataSize = 0L
        var dataStart = raf.filePointer
        var foundSoundData = false

        while (true) {
            val chunkIdBytes = ByteArray(4)
            val read = raf.read(chunkIdBytes)
            if (read < 4) break

            val chunkId = String(chunkIdBytes)
            val chunkSizeBytes = ByteArray(4)
            raf.readFully(chunkSizeBytes)
            val chunkSize = ByteBuffer.wrap(chunkSizeBytes).order(ByteOrder.BIG_ENDIAN).int.toLong()

            if (chunkId == FRM_CHUNK) {
                val soundStart = raf.filePointer

                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val dataType = String(typeBytes)

                if (dataType.startsWith("DSD")) {
                    foundSoundData = true
                    dataStart = raf.filePointer
                    totalDataSize = chunkSize - 4
                    Timber.tag(TAG).d(
                        "Found DSD sound data at $dataStart, size=$totalDataSize"
                    )
                    break
                } else {
                    raf.seek(soundStart + chunkSize - 4)
                }
            } else {
                raf.seek(raf.filePointer + chunkSize)
            }
        }

        if (!foundSoundData) {
            Timber.tag(TAG).e("No DSD sound data found in DSDIFF file")
            return null
        }

        val totalDsdFrames = totalDataSize * 8 / numChannels

        return DffInfo(
            channels = numChannels,
            dsdSampleRate = dsdSampleRate,
            totalDsdFrames = totalDsdFrames,
            dataStartPosition = dataStart,
            dataSize = totalDataSize
        )
    }

    private fun parseRawDsd(raf: RandomAccessFile, file: File, header: ByteArray): DffInfo? {
        val channels = if (header.size >= 17) {
            val ch = header[16].toInt() and 0xFF
            if (ch > 0 && ch <= 8) ch else 2
        } else 2

        val dsdSampleRate = if (header.size >= 21) {
            val sr = ByteBuffer.wrap(header, 17, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
            if (sr > 0) sr else 2822400L
        } else 2822400L

        val dataStart = raf.filePointer

        val totalDataSize = file.length() - dataStart
        val totalDsdFrames = totalDataSize * 8 / channels

        Timber.tag(TAG).d("Raw DSD: channels=$channels, rate=$dsdSampleRate, dataStart=$dataStart")

        return DffInfo(
            channels = channels,
            dsdSampleRate = dsdSampleRate,
            totalDsdFrames = totalDsdFrames,
            dataStartPosition = dataStart,
            dataSize = totalDataSize
        )
    }

    private fun tryRawDsd(file: File, header: ByteArray): DffInfo? {
        val channels = 2
        val dsdSampleRate = 2822400L

        val totalDataSize = file.length()
        val totalDsdFrames = totalDataSize * 8 / channels

        Timber.tag(TAG).d(
            "Raw DSD (no container): channels=$channels, rate=$dsdSampleRate, " +
                "size=$totalDataSize, frames=$totalDsdFrames"
        )

        return DffInfo(
            channels = channels,
            dsdSampleRate = dsdSampleRate,
            totalDsdFrames = totalDsdFrames,
            dataStartPosition = 0,
            dataSize = totalDataSize
        )
    }

    fun isDffFile(filePath: String): Boolean {
        return runCatching {
            val file = File(filePath)
            if (!file.exists() || file.length() < 4) return@runCatching false
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(32)
                raf.read(header)
                val headerStr = String(header)

                val ext = filePath.substringAfterLast('.', "").lowercase()

                headerStr.startsWith(DSDIFF_MAGIC) ||
                    headerStr.startsWith(DIFF_MAGIC) ||
                    headerStr.startsWith(DSD_MAGIC) ||
                    ext in listOf("dff", "dsd", "dif")
            }
        }.getOrDefault(false)
    }
}