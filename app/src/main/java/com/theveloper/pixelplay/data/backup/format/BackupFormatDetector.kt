package com.theveloper.pixelplay.data.backup.format

import java.io.InputStream

class BackupFormatDetector {

    enum class Format {
        PXPL_V3_ZIP,
        PXPL_V2_GZIP,
        LEGACY_GZIP,
        LEGACY_RAW,
        EXTERNAL_PXPDAT,
        UNKNOWN
    }

    fun detect(header: ByteArray): Format {
        if (header.size < 4) return Format.UNKNOWN

        val hasPxplMagic = header[0] == 'P'.code.toByte() &&
            header[1] == 'X'.code.toByte() &&
            header[2] == 'P'.code.toByte() &&
            header[3] == 'L'.code.toByte()

        if (hasPxplMagic) {
            if (header.size < 8) return Format.PXPL_V2_GZIP
            val afterMagic0 = header[4]
            val afterMagic1 = header[5]
            // ZIP local file header: PK\x03\x04
            if (afterMagic0 == 'P'.code.toByte() && afterMagic1 == 'K'.code.toByte()) {
                return Format.PXPL_V3_ZIP
            }
            // GZIP magic: 1f 8b
            if (afterMagic0 == 0x1f.toByte() && afterMagic1 == 0x8b.toByte()) {
                return Format.PXPL_V2_GZIP
            }
            return Format.PXPL_V2_GZIP
        }

        // No PXPL magic: check for raw GZIP
        if (header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
            return Format.LEGACY_GZIP
        }

        // Check for raw JSON (starts with '{')
        if (header[0] == '{'.code.toByte()) {
            if (header.size >= 12) {
                val str = header.decodeToString().lowercase()
                if (str.contains("createdat") && str.contains("data") && str.contains("playhistory")) {
                    return Format.EXTERNAL_PXPDAT
                }
            }
            return Format.LEGACY_RAW
        }

        return Format.UNKNOWN
    }

    /**
     * 读取文件头用于格式判断。
     *
     * 注意：不能只做一次 `read`（可能只返回几个字节），也不能只读 128 字节 ——
     * `.pxpbak`（外部 JSON）的 `playHistory` 字段在 `createdAt/version/deviceInfo`
     * 之后，通常位于 128 字节之后，读少了会把它误判成 LEGACY_RAW，
     * 导入时所有模块都会凭空消失。
     */
    fun readHeader(inputStream: InputStream, size: Int = HEADER_PROBE_BYTES): ByteArray {
        val buffer = ByteArray(size)
        var total = 0
        while (total < size) {
            val read = inputStream.read(buffer, total, size - total)
            if (read == -1) break
            total += read
        }
        return if (total == size) buffer else buffer.copyOf(total)
    }

    companion object {
        val PXPL_MAGIC = byteArrayOf(
            'P'.code.toByte(),
            'X'.code.toByte(),
            'P'.code.toByte(),
            'L'.code.toByte()
        )
        const val PXPL_MAGIC_SIZE = 4

        /** 格式探测读取的头部字节数（需覆盖外部 .pxpbak 的 playHistory 字段） */
        const val HEADER_PROBE_BYTES = 4096
    }
}
