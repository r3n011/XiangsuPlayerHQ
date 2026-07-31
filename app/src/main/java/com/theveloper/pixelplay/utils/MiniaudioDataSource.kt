package com.theveloper.pixelplay.utils

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber
import java.io.File

@UnstableApi
class MiniaudioDataSource internal constructor(
    private val filePath: String,
    private val uri: Uri?,
    private val outputFormat: Int,
    private val outputChannels: Int,
    private val outputSampleRate: Int
) : DataSource {

    private var decoder: MiniaudioDecoder? = null
    private var readPosition: Long = 0
    private var totalBytes: Long = 0
    private var headerSize: Int = 44
    private var headerBytes: ByteArray = ByteArray(0)
    private var pcmBytesPerFrame: Int = 0
    private var pcmTotalBytes: Long = 0
    private var frameBuffer: ByteArray = ByteArray(0)

    override fun open(dataSpec: DataSpec): Long {
        val pos = dataSpec.position

        decoder?.close()
        decoder = MiniaudioDecoder.open(filePath, outputFormat, outputChannels, outputSampleRate)
            ?: throw Exception("Failed to open decoder for: $filePath")

        val dec = decoder!!
        pcmBytesPerFrame = dec.bytesPerFrame
        pcmTotalBytes = dec.totalFrames * pcmBytesPerFrame
        totalBytes = headerSize + pcmTotalBytes

        headerBytes = buildWavHeader(dec.outputSampleRate, dec.outputChannels,
            pcmBytesPerFrame, dec.totalFrames)

        val targetPosition = pos.coerceAtLeast(0)

        if (targetPosition < headerSize) {
            readPosition = targetPosition
            return totalBytes - targetPosition
        }

        val dataOffset = targetPosition - headerSize
        val frameSize = pcmBytesPerFrame.toLong()
        val targetFrame = dataOffset / frameSize

        val seekOk = dec.seekToFrame(targetFrame)
        if (!seekOk) {
            Timber.tag(TAG).w("Seek to frame $targetFrame failed, resetting to 0")
            dec.seekToFrame(0)
            readPosition = headerSize.toLong()
        } else {
            readPosition = targetPosition
        }

        val length = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalBytes - readPosition
        }

        Timber.tag(TAG).d(
            "open(): pos=$pos, totalBytes=$totalBytes, headerSize=$headerSize, " +
                "frames=${dec.totalFrames}, bpf=$pcmBytesPerFrame, seekedToFrame=$targetFrame"
        )

        return length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val remaining = totalBytes - readPosition
        if (remaining <= 0) return -1

        val bytesToRead = minOf(readLength, remaining.toInt())

        if (readPosition < headerSize) {
            val headerOffset = readPosition.toInt()
            val headerAvailable = headerSize - headerOffset
            val headerToCopy = minOf(bytesToRead, headerAvailable)

            System.arraycopy(headerBytes, headerOffset, buffer, offset, headerToCopy)
            readPosition += headerToCopy

            if (headerToCopy >= bytesToRead || readPosition >= headerSize) {
                return headerToCopy
            }

            return readPcmData(buffer, offset + headerToCopy, bytesToRead - headerToCopy)
        }

        return readPcmData(buffer, offset, bytesToRead)
    }

    private fun readPcmData(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength <= 0) return 0

        val dataOffset = readPosition - headerSize
        val frameSize = pcmBytesPerFrame.toLong()
        val frameOffsetInFrame = (dataOffset % frameSize).toInt()

        val dec = decoder ?: return -1

        var totalRead = 0
        while (totalRead < readLength) {
            val framesNeeded = ((readLength - totalRead) + pcmBytesPerFrame - 1) / pcmBytesPerFrame

            if (framesNeeded <= 0) break

            val requiredBufferSize = framesNeeded * pcmBytesPerFrame
            if (frameBuffer.size < requiredBufferSize) {
                frameBuffer = ByteArray(requiredBufferSize)
            }

            val bytesRead = dec.readFrames(framesNeeded, frameBuffer)
            if (bytesRead <= 0) {
                if (totalRead == 0) return -1
                break
            }

            val availableBytes = bytesRead
            val skipFirst = if (totalRead == 0) frameOffsetInFrame else 0
            val toCopy = minOf(readLength - totalRead, availableBytes - skipFirst)
            if (toCopy <= 0) break

            System.arraycopy(frameBuffer, skipFirst, buffer, offset + totalRead, toCopy)
            totalRead += toCopy
            readPosition += toCopy
        }

        return if (totalRead <= 0) -1 else totalRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        decoder?.close()
        decoder = null
        readPosition = 0
        totalBytes = 0
        headerSize = 44
        pcmBytesPerFrame = 0
        pcmTotalBytes = 0
        frameBuffer = ByteArray(0)
        Timber.tag(TAG).d("close()")
    }

    override fun addTransferListener(transferListener: TransferListener) {
    }

    private fun buildWavHeader(
        sampleRate: Int,
        channels: Int,
        bytesPerFrame: Int,
        totalFrames: Long
    ): ByteArray {
        val dataSize = totalFrames * bytesPerFrame
        val fileSize = 44 + dataSize
        val byteRate = sampleRate * bytesPerFrame
        val blockAlign = if (channels > 0) bytesPerFrame else 0
        val bitsPerSample = if (channels > 0) (bytesPerFrame / channels) * 8 else 0

        val header = ByteArray(44)
        val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt((fileSize - 8).toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(dataSize.toInt())

        return header
    }

    companion object {
        private const val TAG = "MiniaudioDS"

        private val WAV_EXTENSIONS = setOf("wav", "wave", "rf64")
        private val DFF_EXTENSIONS = setOf("dff", "dsd", "dif")

        fun needsMiniaudioDecoding(filePath: String): Boolean {
            val ext = filePath.substringAfterLast('.', "").lowercase()
            if (ext in DFF_EXTENSIONS) return true
            if (ext !in WAV_EXTENSIONS) return false

            val variant = WavConverter.detectVariant(filePath)
            return when (variant) {
                WavConverter.WavVariant.RF64,
                WavConverter.WavVariant.WAVE64 -> true
                WavConverter.WavVariant.STANDARD -> {
                    val info = WavConverter.readWavInfo(File(filePath))
                    info != null && (
                        info.formatTag != WavConverter.FORMAT_PCM ||
                            info.bitsPerSample > 32
                        )
                }
                WavConverter.WavVariant.UNSUPPORTED -> false
            }
        }
    }
}