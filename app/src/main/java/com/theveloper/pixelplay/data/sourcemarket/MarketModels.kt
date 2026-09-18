package com.theveloper.pixelplay.data.sourcemarket

import java.io.File

/** zip 包内的一个 JS 音源脚本条目（含完整字节，供安装） */
data class MarketJsEntry(
    val fileName: String,   // 仅 basename，如 "全豆要-聚合音源 v4.1.js"
    val size: Long,
    val bytes: ByteArray
)

/** zip 下载状态流 */
sealed interface ZipDownloadState {
    /** progress ∈ [0,1]，-1 = 未知总大小 */
    data class Downloading(val progress: Float) : ZipDownloadState
    data class Downloaded(val file: File) : ZipDownloadState
    data class Error(val message: String) : ZipDownloadState
}
