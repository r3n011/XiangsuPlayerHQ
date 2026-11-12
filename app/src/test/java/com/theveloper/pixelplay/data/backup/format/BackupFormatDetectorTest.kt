package com.theveloper.pixelplay.data.backup.format

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupFormatDetectorTest {

    private val detector = BackupFormatDetector()

    @Test
    fun `detects v3 PXPL ZIP format`() {
        // PXPL magic + ZIP header (PK\x03\x04)
        val header = byteArrayOf(
            'P'.code.toByte(), 'X'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(),
            0x50, 0x4B, 0x03, 0x04
        )
        assertEquals(BackupFormatDetector.Format.PXPL_V3_ZIP, detector.detect(header))
    }

    @Test
    fun `detects v2 PXPL GZIP format`() {
        // PXPL magic + GZIP magic (1f 8b)
        val header = byteArrayOf(
            'P'.code.toByte(), 'X'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(),
            0x1f, 0x8b.toByte(), 0x08, 0x00
        )
        assertEquals(BackupFormatDetector.Format.PXPL_V2_GZIP, detector.detect(header))
    }

    @Test
    fun `detects legacy GZIP format`() {
        val header = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals(BackupFormatDetector.Format.LEGACY_GZIP, detector.detect(header))
    }

    @Test
    fun `detects legacy raw JSON format`() {
        val header = "{ \"formatVersion\": 1".toByteArray(Charsets.UTF_8)
        assertEquals(BackupFormatDetector.Format.LEGACY_RAW, detector.detect(header))
    }

    @Test
    fun `returns UNKNOWN for unrecognized format`() {
        val header = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(header))
    }

    @Test
    fun `detects external pxpbak when playHistory follows the first 128 bytes`() {
        val json = """
            {"createdAt":"2026-10-31T03:30:00Z","version":"1.0","appVersion":"1.2.0","deviceInfo":{"model":"Pixel 7 Pro","systemVersion":"34"},"data":{"playHistory":[{"id":"entry-1"}]}}
        """.trimIndent()

        // 回归：playHistory 字段位于 128 字节之后，旧的 128 字节探测会误判成 LEGACY_RAW，
        // 导入时表现为「模块全部消失」。
        assertTrue(json.indexOf("\"playHistory\"") > 128)
        val header = detector.readHeader(json.byteInputStream())
        assertEquals(BackupFormatDetector.Format.EXTERNAL_PXPDAT, detector.detect(header))
    }

    @Test
    fun `readHeader keeps reading when the stream returns short chunks`() {
        val payload = ByteArray(64) { it.toByte() }
        val stream = object : java.io.InputStream() {
            private var index = 0

            override fun read(): Int =
                if (index < payload.size) payload[index++].toInt() and 0xFF else -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (index >= payload.size || length <= 0) return if (index >= payload.size) -1 else 0
                buffer[offset] = payload[index++]
                return 1
            }
        }

        val header = detector.readHeader(stream, 32)
        assertEquals(32, header.size)
        assertArrayEquals(payload.copyOf(32), header)
    }

    @Test
    fun `returns UNKNOWN for empty input`() {
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(byteArrayOf()))
    }

    @Test
    fun `returns UNKNOWN for too-short input`() {
        val header = byteArrayOf('P'.code.toByte(), 'X'.code.toByte())
        assertEquals(BackupFormatDetector.Format.UNKNOWN, detector.detect(header))
    }
}
