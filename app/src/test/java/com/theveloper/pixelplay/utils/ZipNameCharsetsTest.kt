package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** KI-047：GBK/GB18030 条目名的 zip 必须能回退解析，不能再因固定 UTF-8 而中断。 */
class ZipNameCharsetsTest {

    private val gbkName = "落雪音源/测试源.js"

    private fun writeZip(dir: File, charset: Charset, entries: Map<String, String>): File {
        val file = File(dir, "test-${charset.name()}.zip")
        ZipOutputStream(file.outputStream(), charset).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `openZipStream reads GBK entry names via GB18030 fallback`() {
        val dir = Files.createTempDirectory("zipnames").toFile()
        val file = writeZip(dir, ZipNameCharsets.FALLBACK, mapOf(gbkName to "// js"))

        val names = ZipNameCharsets.openZipStream(file) { zis ->
            buildList {
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        // 与调用方一致：发现乱码名即触发回退
                        if (ZipNameCharsets.isMojibake(e.name)) {
                            throw ZipNameCharsets.ZipNameMojibakeException(e.name)
                        }
                        add(e.name)
                    }
                    e = zis.nextEntry
                }
            }
        }

        assertThat(names).containsExactly(gbkName)
        dir.deleteRecursively()
    }

    @Test
    fun `openZipStream reads UTF-8 zip without fallback`() {
        val dir = Files.createTempDirectory("zipnames").toFile()
        val file = writeZip(dir, Charsets.UTF_8, mapOf(gbkName to "// js"))

        val names = ZipNameCharsets.openZipStream(file) { zis ->
            buildList {
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.isDirectory) add(e.name)
                    e = zis.nextEntry
                }
            }
        }

        assertThat(names).containsExactly(gbkName)
        dir.deleteRecursively()
    }

    @Test
    fun `openZipFile falls back to GB18030 for GBK entry names`() {
        val dir = Files.createTempDirectory("zipnames").toFile()
        val file = writeZip(dir, ZipNameCharsets.FALLBACK, mapOf(gbkName to "// js"))

        ZipNameCharsets.openZipFile(file).use { zf ->
            assertThat(zf.entries().toList().map { it.name }).containsExactly(gbkName)
        }
        dir.deleteRecursively()
    }

    @Test
    fun `openZipFile keeps UTF-8 zip as-is`() {
        val dir = Files.createTempDirectory("zipnames").toFile()
        val file = writeZip(dir, Charsets.UTF_8, mapOf(gbkName to "// js"))

        ZipNameCharsets.openZipFile(file).use { zf ->
            assertThat(zf.entries().toList().map { it.name }).containsExactly(gbkName)
        }
        dir.deleteRecursively()
    }
}
