package com.theveloper.pixelplay.data.service.audioengine

import android.content.Context
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AudioEngine(private val context: Context) {
    private val pipeline = AudioPipeline()
    private val outputManager = OutputManager(context)
    private val decoderBuffer = RingBuffer(1024 * 1024)
    private val dspBuffer = RingBuffer(1024 * 1024)
    private var sampleRate = 44100
    private var channelCount = 2
    private var bitDepth = 32
    private var isRunning = AtomicBoolean(false)
    private var dspExecutor: ExecutorService? = null
    private var outputExecutor: ExecutorService? = null

    fun configure(sampleRate: Int, channelCount: Int, bitDepth: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitDepth = bitDepth
        pipeline.configure(sampleRate, channelCount, bitDepth)
        outputManager.configure(sampleRate, channelCount, bitDepth)
        Timber.d("AudioEngine configured: %dHz, %d channels, %d-bit", sampleRate, channelCount, bitDepth)
    }

    fun addProcessor(processor: AudioProcessor) {
        pipeline.addProcessor(processor)
    }

    fun removeProcessor(processor: AudioProcessor) {
        pipeline.removeProcessor(processor)
    }

    fun getProcessors(): List<AudioProcessor> = pipeline.getProcessors()

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            dspExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "AudioEngine-DSP").apply { priority = Thread.MAX_PRIORITY }
            }
            outputExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "AudioEngine-Output").apply { priority = Thread.MAX_PRIORITY }
            }
            outputManager.start()
            startDSPLoop()
            startOutputLoop()
            Timber.d("AudioEngine started")
        }
    }

    fun pause() {
        outputManager.pause()
        Timber.d("AudioEngine paused")
    }

    fun resume() {
        outputManager.resume()
        Timber.d("AudioEngine resumed")
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            dspExecutor?.shutdownNow()
            outputExecutor?.shutdownNow()
            outputManager.stop()
            decoderBuffer.clear()
            dspBuffer.clear()
            pipeline.flush()
            Timber.d("AudioEngine stopped")
        }
    }

    fun release() {
        stop()
        pipeline.release()
        outputManager.release()
        Timber.d("AudioEngine released")
    }

    fun write(buffer: FloatArray, offset: Int, length: Int): Int {
        return decoderBuffer.write(buffer, offset, length)
    }

    fun write(buffer: ShortArray, offset: Int, length: Int): Int {
        val floatBuffer = FloatArray(length)
        for (i in 0 until length) {
            floatBuffer[i] = buffer[offset + i] / Short.MAX_VALUE.toFloat()
        }
        return decoderBuffer.write(floatBuffer, 0, length)
    }

    private fun startDSPLoop() {
        dspExecutor?.execute {
            val workBuffer = FloatArray(8192)
            while (isRunning.get()) {
                val read = decoderBuffer.read(workBuffer, 0, workBuffer.size)
                if (read > 0) {
                    pipeline.process(workBuffer, 0, read)
                    dspBuffer.write(workBuffer, 0, read)
                } else {
                    Thread.yield()
                }
            }
        }
    }

    private fun startOutputLoop() {
        outputExecutor?.execute {
            val workBuffer = FloatArray(8192)
            while (isRunning.get()) {
                val read = dspBuffer.read(workBuffer, 0, workBuffer.size)
                if (read > 0) {
                    outputManager.write(workBuffer, 0, read)
                } else {
                    Thread.yield()
                }
            }
        }
    }

    fun isRunning(): Boolean = isRunning.get()

fun flushPipeline() {
    pipeline.flush()
    decoderBuffer.clear()
    dspBuffer.clear()
}
}