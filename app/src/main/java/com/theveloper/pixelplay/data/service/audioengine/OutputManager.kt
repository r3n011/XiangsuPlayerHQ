package com.theveloper.pixelplay.data.service.audioengine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OutputManager(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var isRunning = false
    private var isPaused = false
    private var bufferSize = 0

    fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        bufferSize = calculateBufferSize(sampleRate, channelCount, bitDepth)
    }

    private fun calculateBufferSize(sampleRate: Int, channelCount: Int, bitDepth: Int): Int {
        val bytesPerSample = bitDepth / 8
        val bytesPerFrame = bytesPerSample * channelCount
        val framesPerBuffer = sampleRate / 50
        return framesPerBuffer * bytesPerFrame
    }

    fun start() {
        if (isRunning) return

        val audioFormat = AudioFormat.Builder()
            .setEncoding(if (bitDepth == 32) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            audioFormat.channelMask,
            audioFormat.encoding
        )

        audioTrack = AudioTrack.Builder()
            .setAudioFormat(audioFormat)
            .setAudioAttributes(audioAttributes)
            .setBufferSizeInBytes(minBufferSize * 2)
            .build()

        audioTrack?.play()
        isRunning = true
        isPaused = false
        Timber.d("OutputManager started: %dHz, %d channels, %d-bit", sampleRate, channelCount, bitDepth)
    }

    fun pause() {
        audioTrack?.pause()
        isPaused = true
    }

    fun resume() {
        audioTrack?.play()
        isPaused = false
    }

    fun stop() {
        audioTrack?.stop()
        isRunning = false
        isPaused = false
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        Timber.d("OutputManager released")
    }

    fun write(buffer: FloatArray, offset: Int, length: Int): Int {
        if (!isRunning || isPaused) return 0

        val audioTrack = audioTrack ?: return 0

        return if (bitDepth == 32) {
            val byteBuffer = ByteBuffer.allocate(length * 4).order(ByteOrder.nativeOrder())
            byteBuffer.asFloatBuffer().put(buffer, offset, length)
            audioTrack.write(byteBuffer, length * 4, AudioTrack.WRITE_BLOCKING)
        } else {
            val shortBuffer = ShortArray(length)
            for (i in 0 until length) {
                shortBuffer[i] = (buffer[offset + i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            audioTrack.write(shortBuffer, 0, length, AudioTrack.WRITE_BLOCKING)
        }
    }

    fun getBufferSize(): Int = bufferSize

    fun isRunning(): Boolean = isRunning

    fun isPaused(): Boolean = isPaused
}