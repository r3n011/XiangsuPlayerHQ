package com.theveloper.pixelplay.utils

import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * ZIP 条目名编码回退（KI-047）。
 *
 * 部分老打包器（如简体中文版 WinRAR / 好压）以 GBK/GB2312 写入文件名，且不带 UTF-8
 * 标志位（EFS）。java.util.zip 在无 EFS 时按构造传入的 charset 解码条目名：
 *  - 固定 UTF-8：GBK 字节序列按 UTF-8 严格解码会抛 `IllegalArgumentException`
 *    （ZipInputStream 在遍历 [ZipInputStream.nextEntry] 时抛出，无法从中间恢复），
 *    整个解包直接中断；
 *  - 部分混合字节侥幸解码成功但产出 U+FFFD 乱码名，后续按名查找 / 落盘全部错乱。
 *
 * 这里统一「先 UTF-8、解码失败或出现乱码再回退 GB18030 重读」的策略：
 *  - [openZipStream] / [openZipFile]：按序尝试，返回首个成功编码下的结果；
 *  - 消费方在遍历条目名时发现 U+FFFD 可抛 [ZipNameMojibakeException] 触发回退；
 *  - GB18030 ⊇ GBK ⊇ GB2312，作为最终兜底。
 */
object ZipNameCharsets {

    /** 回退用的大字符集。 */
    val FALLBACK: Charset = Charset.forName("GB18030")

    /** 条目名在本轮编码下解码出了 U+FFFD 替换符（乱码），应换下一编码重试。 */
    class ZipNameMojibakeException(message: String) : IllegalStateException(message)

    /** 名字里出现 U+FFFD 即视为本轮编码不对。 */
    fun isMojibake(name: String): Boolean = name.indexOf('\uFFFD') >= 0

    // 供 public inline 函数访问，须用 @PublishedApi 开放可见性
    @PublishedApi
    internal val candidates = listOf(Charsets.UTF_8, FALLBACK)

    /**
     * 按候选编码依次打开 [ZipInputStream] 并完整消费；某一编码下抛
     * [IllegalArgumentException]（严格解码失败）或 [ZipNameMojibakeException]
     * （消费方检测到乱码名）时，关闭流换下一编码从头重试；全部失败抛最后一个异常。
     *
     * 每次尝试都从全新流开始，[consume] 内部须自建可变状态，不要跨尝试复用。
     */
    inline fun <T> openZipStream(file: File, consume: (ZipInputStream) -> T): T {
        var lastError: Exception? = null
        for (charset in candidates) {
            ZipInputStream(file.inputStream().buffered(), charset).use { zis ->
                try {
                    return consume(zis)
                } catch (e: IllegalArgumentException) {
                    lastError = e
                } catch (e: ZipNameMojibakeException) {
                    lastError = e
                }
            }
        }
        val err = lastError
        throw err ?: IllegalStateException("zip 条目名解码失败且无可用回退编码")
    }

    /**
     * 按候选编码依次打开 [ZipFile] 并预读全部条目名做校验（解码惰性发生，须显式触发），
     * 返回首个「全部名字可解码且无乱码」的已打开实例；调用方负责 [ZipFile.close]。
     * 全部失败抛最后一个异常（已打开的实例在抛出前关闭）。
     */
    fun openZipFile(file: File): ZipFile {
        var lastError: Exception? = null
        for (charset in candidates) {
            val zf = ZipFile(file, charset)
            try {
                val names = zf.entries().toList().map { it.name }
                if (names.none { isMojibake(it) }) return zf
                lastError = ZipNameMojibakeException("条目名在 $charset 下出现乱码")
            } catch (e: IllegalArgumentException) {
                lastError = e
            } catch (e: ZipNameMojibakeException) {
                lastError = e
            }
            zf.close()
        }
        val err = lastError
        throw err ?: IllegalStateException("zip 条目名解码失败且无可用回退编码")
    }
}
