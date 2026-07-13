package com.theveloper.pixelplay.data.service.audioengine

import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RingBuffer(private val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private val lock = ReentrantLock()
    private var readIndex = 0
    private var writeIndex = 0
    private var count = 0

    fun write(data: FloatArray, offset: Int, length: Int): Int {
        lock.withLock {
            val available = capacity - count
            val toWrite = length.coerceAtMost(available)
            if (toWrite == 0) return 0

            var written = 0
            while (written < toWrite) {
                val remaining = toWrite - written
                val spaceToEnd = capacity - writeIndex
                val chunk = remaining.coerceAtMost(spaceToEnd)

                data.copyInto(buffer, writeIndex, offset + written, offset + written + chunk)
                writeIndex = (writeIndex + chunk) % capacity
                written += chunk
            }
            count += written
            return written
        }
    }

    fun read(data: FloatArray, offset: Int, length: Int): Int {
        lock.withLock {
            val available = count
            val toRead = length.coerceAtMost(available)
            if (toRead == 0) return 0

            var read = 0
            while (read < toRead) {
                val remaining = toRead - read
                val dataToEnd = capacity - readIndex
                val chunk = remaining.coerceAtMost(dataToEnd)

                buffer.copyInto(data, offset + read, readIndex, readIndex + chunk)
                readIndex = (readIndex + chunk) % capacity
                read += chunk
            }
            count -= read
            return read
        }
    }

    fun peek(data: FloatArray, offset: Int, length: Int): Int {
        lock.withLock {
            val available = count
            val toPeek = length.coerceAtMost(available)
            if (toPeek == 0) return 0

            var peeked = 0
            var tempReadIndex = readIndex
            while (peeked < toPeek) {
                val remaining = toPeek - peeked
                val dataToEnd = capacity - tempReadIndex
                val chunk = remaining.coerceAtMost(dataToEnd)

                buffer.copyInto(data, offset + peeked, tempReadIndex, tempReadIndex + chunk)
                tempReadIndex = (tempReadIndex + chunk) % capacity
                peeked += chunk
            }
            return peeked
        }
    }

    fun clear() {
        lock.withLock {
            readIndex = 0
            writeIndex = 0
            count = 0
        }
    }

    fun isEmpty(): Boolean = count == 0

    fun isFull(): Boolean = count == capacity

    fun size(): Int = count

    fun available(): Int = capacity - count

    fun capacity(): Int = capacity
}