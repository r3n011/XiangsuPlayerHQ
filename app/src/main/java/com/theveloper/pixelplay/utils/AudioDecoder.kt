package com.theveloper.pixelplay.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

object AudioDecoder {

    private const val TIMEOUT_US = 1000L
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    /** 未分析桶的哨兵值，与 AudioVisualizer.WAVE_UNKNOWN 保持一致。 */
    private const val WAVE_UNKNOWN = -1f

    suspend fun decodeToFloatArray(context: Context, uri: Uri, requiredSamples: Int): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                extractor.release()
                error("No audio track found in the file.")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmData = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false

            while (!isEndOfStream && pcmData.size < requiredSamples) { // --- MODIFICADO: Condición de parada ---
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder input buffer was null, ending decode early")
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                        continue
                    }
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder output buffer was null, skipping chunk")
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                        continue
                    }
                    pcmData.addAll(byteBufferToFloatArray(outputBuffer, format).asList())
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    // Si ya tenemos suficientes muestras, salimos del bucle interno
                    if (pcmData.size >= requiredSamples) break

                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            Timber.tag("AudioDecoder").d("Successfully decoded ${pcmData.size} samples.")

            // --- MODIFICADO: Rellenamos con silencio si la canción es más corta que lo requerido ---
            if (pcmData.size < requiredSamples) {
                val padding = FloatArray(requiredSamples - pcmData.size) { 0f }
                pcmData.addAll(padding.asList())
            }

            // Devolvemos el array con el tamaño exacto
            pcmData.toFloatArray().copyOf(requiredSamples)
        }
    }

    /**
     * 解码整条音轨并压成 [bucketCount] 段峰值，用于本地文件提前生成波形。
     *
     * 与 [decodeToFloatArray] 不同：不驻留整首 PCM（4 分钟立体声约 84MB），
     * 每解出一块立刻并入对应时间桶的峰值后丢弃，内存占用只有 [bucketCount] 个 Float。
     */
    suspend fun decodeWaveformPeaks(
        context: Context,
        uri: Uri,
        bucketCount: Int,
        durationMs: Long
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            require(bucketCount > 0) { "bucketCount must be positive" }
            require(durationMs > 0L) { "duration is required to align waveform buckets" }

            val peaks = FloatArray(bucketCount) { WAVE_UNKNOWN }
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                extractor.release()
                error("No audio track found in the file.")
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var outputFormat = inputFormat
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            var totalFrames = durationMs * sampleRate / 1000L
            var decodedFrames = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            inputDone = true
                        } else {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputIndex, 0, sampleSize, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        val actualRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (actualRate > 0) {
                            sampleRate = actualRate
                            totalFrames = durationMs * sampleRate / 1000L
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            decoder.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                                decodedFrames = mergeWaveformPeaks(
                                    buffer = outputBuffer,
                                    bufferInfo = bufferInfo,
                                    format = outputFormat,
                                    peaks = peaks,
                                    bucketCount = bucketCount,
                                    decodedFrames = decodedFrames,
                                    totalFrames = totalFrames
                                )
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            Timber.tag("AudioDecoder")
                .d("Decoded waveform: $decodedFrames frames -> $bucketCount buckets")

            peaks
        }
    }

    /** 把一块解码输出并入时间桶峰值，返回累计已解码的帧数。 */
    private fun mergeWaveformPeaks(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        format: MediaFormat,
        peaks: FloatArray,
        bucketCount: Int,
        decodedFrames: Long,
        totalFrames: Long
    ): Long {
        if (totalFrames <= 0L) return decodedFrames
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val samples = byteBufferToFloatArray(buffer, format, bufferInfo.offset, bufferInfo.size)
        val frames = samples.size / channelCount
        for (f in 0 until frames) {
            val base = f * channelCount
            var acc = 0f
            for (c in 0 until channelCount) acc += samples[base + c]
            val bucket = ((decodedFrames + f) * bucketCount / totalFrames).toInt()
            if (bucket < 0) continue
            if (bucket >= bucketCount) break
            val amplitude = sqrt(abs(acc / channelCount)).coerceIn(0f, 1f)
            if (amplitude > peaks[bucket]) peaks[bucket] = amplitude
        }
        return decodedFrames + frames
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    /**
     * 按 [offset]/[size] 精确读取一块解码输出（MediaCodec 的输出缓冲默认位置不保证在有效区间内）。
     */
    private fun byteBufferToFloatArray(
        buffer: ByteBuffer,
        format: MediaFormat,
        offset: Int,
        size: Int
    ): FloatArray {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        val slice = buffer.duplicate()
        slice.order(ByteOrder.nativeOrder())
        slice.position(offset)
        slice.limit(offset + size)

        return when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shortBuffer = slice.asShortBuffer()
                FloatArray(shortBuffer.remaining()) {
                    shortBuffer.get().toFloat() / Short.MAX_VALUE
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floatBuffer = slice.asFloatBuffer()
                FloatArray(floatBuffer.remaining()) { floatBuffer.get() }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }

    private fun byteBufferToFloatArray(buffer: ByteBuffer, format: MediaFormat): FloatArray {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        buffer.rewind()

        return when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shortBuffer = buffer.asShortBuffer()
                FloatArray(shortBuffer.remaining()) {
                    shortBuffer.get().toFloat() / Short.MAX_VALUE
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                FloatArray(floatBuffer.remaining()) { floatBuffer.get() }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }
}
