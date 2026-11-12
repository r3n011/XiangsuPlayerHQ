package com.theveloper.pixelplay.data.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件包导入器（zip → 应用私有目录）。
 *
 * 安全约束（文档 05 §5 / 01 §4）：
 *  - 拒绝 `..`、绝对路径、反斜杠、空字节；
 *  - 只解压 manifest.json / main.js / plugin.wasm / agreements/ / ui/ / fonts/ / assets/ 与根目录图片；
 *  - 单条目 ≤ 2MB，解压总量 ≤ 8MB，条目数 ≤ 512；
 *  - 先写临时目录，校验通过后原子替换旧版本。
 */
@Singleton
class XsZipImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: XsPluginPaths,
) {
    companion object {
        private const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L
        private const val MAX_ENTRY_BYTES = 2L * 1024L * 1024L
        private const val MAX_ENTRIES = 512
    }

    fun import(zipFile: File, source: String): XSPluginRecord {
        if (!zipFile.isFile || zipFile.length() == 0L) {
            throw XsInstallException(XsErrors.INVALID_ZIP, "安装包为空")
        }
        XsLog.i(XsLog.IMPORTER, "import start: %s (%d bytes, source=%s)", zipFile.name, zipFile.length(), source)
        val zip = try {
            ZipFile(zipFile)
        } catch (e: Exception) {
            throw XsInstallException(XsErrors.INVALID_ZIP, "zip 结构损坏：${e.message}")
        }

        zip.use { archive ->
            val entries = archive.entries().toList()
            if (entries.size > MAX_ENTRIES) {
                throw XsInstallException(XsErrors.INVALID_ZIP, "条目数超过 $MAX_ENTRIES")
            }

            var total = 0L
            val allowed = mutableListOf<java.util.zip.ZipEntry>()
            for (entry in entries) {
                val name = normalizeEntryName(entry.name)
                if (name == null) {
                    throw XsInstallException(XsErrors.INVALID_ZIP, "非法条目路径：${entry.name}")
                }
                if (entry.isDirectory) continue
                val size = entry.size
                if (size > MAX_ENTRY_BYTES) {
                    throw XsInstallException(XsErrors.INVALID_ZIP, "单文件超过 2MB：$name")
                }
                total += if (size > 0) size else 0
                if (total > MAX_TOTAL_BYTES) {
                    throw XsInstallException(XsErrors.INVALID_ZIP, "解压总量超过 8MB")
                }
                if (!isAllowedEntry(name)) continue
                allowed += entry
            }

            val manifestEntry = allowed.firstOrNull { normalizeEntryName(it.name) == "manifest.json" }
                ?: throw XsInstallException(XsErrors.MISSING_MANIFEST, "包根缺少 manifest.json")
            val manifestRaw = readEntry(archive, manifestEntry)
            val manifest = XsManifestValidator.parse(manifestRaw)
            XsLog.i(
                XsLog.IMPORTER,
                "manifest ok: %s v%s caps=%s actions=%d ui=%s agreement=%s",
                manifest.id,
                manifest.version,
                manifest.capabilities.joinToString { it.id },
                manifest.playerActions.size,
                manifest.ui?.entry ?: "-",
                manifest.agreement ?: "-",
            )

            val hasMain = allowed.any { normalizeEntryName(it.name) == "main.js" }
            val hasWasm = allowed.any { normalizeEntryName(it.name) == "plugin.wasm" }
            if (!hasMain) {
                if (hasWasm) {
                    throw XsInstallException(
                        XsErrors.RUNTIME_UNAVAILABLE,
                        "该插件使用 runtime=wasm（仅 plugin.wasm），安卓 QuickJS 不支持；需要 V8/JSC 宿主",
                    )
                }
                throw XsInstallException(XsErrors.MISSING_MAIN_SCRIPT, "缺少 main.js")
            }
            val mainBytes = readEntryBytes(archive, allowed.first { normalizeEntryName(it.name) == "main.js" })
            if (mainBytes.size > MAX_ENTRY_BYTES) {
                throw XsInstallException(XsErrors.INVALID_ZIP, "main.js 超过 2MB")
            }

            manifest.ui?.let { ui ->
                val entry = normalizeEntryName(ui.entry)
                    ?: throw XsInstallException(XsErrors.INVALID_MANIFEST, "ui.entry 非法")
                if (allowed.none { normalizeEntryName(it.name) == entry }) {
                    throw XsInstallException(XsErrors.INVALID_MANIFEST, "ui.entry 指向的文件不存在：$entry")
                }
            }
            manifest.agreement?.let { agreement ->
                val entry = normalizeEntryName(agreement)
                    ?: throw XsInstallException(XsErrors.INVALID_MANIFEST, "agreement 非法")
                if (allowed.none { normalizeEntryName(it.name) == entry }) {
                    throw XsInstallException(XsErrors.INVALID_MANIFEST, "agreement 指向的文件不存在：$agreement")
                }
            }

            val tempDir = File(paths.pluginsRoot, ".tmp-${manifest.id}-${System.currentTimeMillis()}")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            try {
                for (entry in allowed) {
                    val name = normalizeEntryName(entry.name) ?: continue
                    val target = File(tempDir, name)
                    target.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                // UI 页面注入宿主桥（xs-bridge.js = 桥胶水 + xs-runtime.js）
                manifest.ui?.let { ui ->
                    val entryName = normalizeEntryName(ui.entry) ?: return@let
                    val entryFile = File(tempDir, entryName)
                    if (entryFile.isFile) {
                        val html = entryFile.readText(Charsets.UTF_8)
                        entryFile.writeText(XsBridgeScript.injectIntoHtml(html), Charsets.UTF_8)
                    }
                    val bridgeFile = File(entryFile.parentFile ?: tempDir, "xs-bridge.js")
                    if (!bridgeFile.exists()) {
                        val runtime = readAsset("xs-runtime.js")
                            ?: throw XsInstallException(XsErrors.RUNTIME_UNAVAILABLE, "宿主缺少 xs-runtime.js")
                        bridgeFile.writeText(
                            XsBridgeScript.WEBVIEW + "\n;\n" + runtime,
                            Charsets.UTF_8,
                        )
                    }
                }

                // main.js 也要做一次语法/首行可执行性检查在 runtime 启动时进行；
                // 这里先把 manifest 落盘，供 registry 读取。
                File(tempDir, "manifest.json").writeText(manifestRaw, Charsets.UTF_8)
                XsLog.i(
                    XsLog.IMPORTER,
                    "extracted %d entries (allowed), uiInjected=%s, dir=%s",
                    allowed.size,
                    manifest.ui != null,
                    manifest.id,
                )

                val finalDir = paths.pluginDir(manifest.id)
                if (finalDir.exists()) {
                    val backup = File(paths.pluginsRoot, ".bak-${manifest.id}-${System.currentTimeMillis()}")
                    if (!finalDir.renameTo(backup)) finalDir.deleteRecursively()
                    if (!tempDir.renameTo(finalDir)) {
                        tempDir.copyRecursively(finalDir, overwrite = true)
                        tempDir.deleteRecursively()
                    }
                    backup.deleteRecursively()
                } else if (!tempDir.renameTo(finalDir)) {
                    tempDir.copyRecursively(finalDir, overwrite = true)
                    tempDir.deleteRecursively()
                }

                return XSPluginRecord(
                    manifest = manifest,
                    enabled = false,
                    agreed = false,
                    installedAt = System.currentTimeMillis(),
                    source = source,
                )
            } catch (e: XsInstallException) {
                tempDir.deleteRecursively()
                throw e
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                throw XsInstallException(XsErrors.INVALID_ZIP, "解压失败：${e.message}")
            }
        }
    }

    fun readAsset(name: String): String? = runCatching {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Timber.w(it, "read asset $name failed") }.getOrNull()

    private fun normalizeEntryName(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.contains('\\') || raw.contains('\u0000')) return null
        val name = raw.trimStart('/')
        if (name.contains("..")) return null
        if (raw.startsWith("/") || raw.startsWith("\\")) return null
        if (name.length > 180) return null
        return name
    }

    private fun isAllowedEntry(name: String): Boolean {
        if (name == "manifest.json" || name == "main.js" || name == "plugin.wasm") return true
        if (name.startsWith("agreements/")) return true
        if (name.startsWith("ui/")) return true
        if (name.startsWith("fonts/")) return true
        if (name.startsWith("assets/")) return true
        if (!name.contains('/') && name.matches(Regex(".+\\.(png|jpg|jpeg|webp|svg)$", RegexOption.IGNORE_CASE))) return true
        return false
    }

    private fun readEntry(archive: ZipFile, entry: java.util.zip.ZipEntry): String =
        readEntryBytes(archive, entry).toString(Charsets.UTF_8)

    private fun readEntryBytes(archive: ZipFile, entry: java.util.zip.ZipEntry): ByteArray =
        archive.getInputStream(entry).use { input ->
            if (entry.size > MAX_ENTRY_BYTES) {
                throw XsInstallException(XsErrors.INVALID_ZIP, "单文件超过 2MB：${entry.name}")
            }
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_ENTRY_BYTES) {
                    throw XsInstallException(XsErrors.INVALID_ZIP, "单文件超过 2MB：${entry.name}")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
}
