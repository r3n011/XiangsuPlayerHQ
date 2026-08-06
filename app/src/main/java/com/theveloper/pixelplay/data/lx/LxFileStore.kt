package com.theveloper.pixelplay.data.lx

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 落雪 userApi JS 文件存储（支持多个 JS 同时保存）。
 *
 * 所有 JS 文件保存在 filesDir/lx_user_js/ 下，以 *.js 结尾。
 * 导入时自动生成唯一文件名（如 userapi_1.js），不会覆盖已有文件。
 */
@Singleton
class LxFileStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val dir: File
        get() = File(appContext.filesDir, "lx_user_js").also { it.mkdirs() }

    /** assets 内置音源所在目录（随 APK 打包，首次启动自动导入） */
    private val bundledAssetDir = "lx_user_js"

    /** 内置音源"仅首次导入"标志：导入完成后置位，用户之后删除的音源不会被自动恢复 */
    private val bundledDoneFlag: File
        get() = File(appContext.filesDir, "lx_user_js_bundled.flag")

    /**
     * 仅首次运行时将 assets 内置音源复制到用户目录（已存在同名文件则跳过，避免覆盖用户导入的版本）。
     * 返回本次实际导入的文件名列表。
     */
    suspend fun ensureBundledSources(): List<String> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<String>()
        if (bundledDoneFlag.exists()) return@withContext imported
        try {
            val names = appContext.assets.list(bundledAssetDir)
                ?.filter { it.endsWith(".js", ignoreCase = true) }
                ?: emptyList()
            for (name in names) {
                val target = File(dir, name)
                if (target.exists() && target.length() > 0) continue
                appContext.assets.open("$bundledAssetDir/$name").use { src ->
                    target.outputStream().use { dst -> src.copyTo(dst) }
                }
                if (target.exists() && target.length() > 0) imported.add(name)
            }
            bundledDoneFlag.writeText("1")
        } catch (t: Throwable) {
            // assets 不存在或读取失败时静默跳过（不阻塞正常功能）
        }
        imported
    }

    /** 兼容旧的单文件入口（userapi.js） */
    fun defaultJsFile(): File = File(dir, "userapi.js")

    /** 目录下所有 JS 文件（按文件名排序） */
    fun listFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".js", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** 是否存在任何 JS 文件 */
    fun hasAnyJs(): Boolean = listFiles().isNotEmpty()

    /** 当前 JS 文件数量 */
    fun count(): Int = listFiles().size

    /** 根据文件名获取文件对象（仅 *.js，防目录穿越） */
    fun fileByName(name: String): File? {
        if (name.isBlank()) return null
        if (name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        if (!name.endsWith(".js", ignoreCase = true)) return null
        val f = File(dir, name)
        return if (f.exists()) f else null
    }

    /** 生成不与现有文件冲突的文件名：base → base_1.js → base_2.js … */
    fun uniqueName(base: String): String {
        var clean = base.trim()
        if (clean.isBlank()) clean = "userapi"
        if (!clean.endsWith(".js", ignoreCase = true)) clean += ".js"
        if (!File(dir, clean).exists()) return clean
        val stem = clean.removeSuffix(".js")
        var i = 1
        while (File(dir, "${stem}_$i.js").exists()) i++
        return "${stem}_$i.js"
    }

    /** 从 Uri 导入 JS，自动生成唯一文件名。返回实际写入的文件名（失败返回 null） */
    suspend fun writeFromUri(uri: Uri, baseName: String = "userapi"): String? =
        withContext(Dispatchers.IO) {
            try {
                val target = File(dir, uniqueName(baseName))
                appContext.contentResolver.openInputStream(uri)?.use { src ->
                    target.outputStream().use { dst -> src.copyTo(dst) }
                }
                if (target.exists() && target.length() > 0) target.name else null
            } catch (t: Throwable) {
                null
            }
        }

    /** 从 URL 下载 JS，自动生成唯一文件名。返回实际写入的文件名（失败返回 null） */
    suspend fun writeFromUrl(url: String, baseName: String = "userapi"): String? =
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return@withContext null
                val target = File(dir, uniqueName(baseName))
                resp.body?.use { body ->
                    target.outputStream().use { dst -> body.byteStream().copyTo(dst) }
                }
                if (target.exists() && target.length() > 200) target.name else null
            } catch (t: Throwable) {
                null
            }
        }

    /** 读取指定文件内容（自动检测 UTF-8 / GBK 编码，避免中文乱码） */
    suspend fun content(file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) null else decodeJsBytes(file.readBytes())
        } catch (t: Throwable) { null }
    }

    /** 读取指定文件名对应的内容（自动检测编码） */
    suspend fun contentByName(name: String): String? = withContext(Dispatchers.IO) {
        val f = fileByName(name) ?: return@withContext null
        try { decodeJsBytes(f.readBytes()) } catch (t: Throwable) { null }
    }

    /** 读取指定文件头部前 N 行（用于简介/调试） */
    suspend fun head(file: File, maxLines: Int = 20): String? = withContext(Dispatchers.IO) {
        val f = file
        if (!f.exists()) return@withContext null
        try {
            decodeJsBytes(f.readBytes()).lineSequence().take(maxLines).joinToString("\n")
        } catch (t: Throwable) { null }
    }

    private fun decodeJsBytes(bytes: ByteArray): String {
        // 先用严格 UTF-8 解码；若遇非法字节（常见于 GBK 编码的 JS），回退 GBK。
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            try {
                String(bytes, java.nio.charset.Charset.forName("GBK"))
            } catch (t: Throwable) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }

    /** 删除指定文件名的 JS */
    fun deleteByName(name: String): Boolean {
        val f = fileByName(name) ?: return false
        return !f.exists() || f.delete()
    }

    /** 删除所有 JS 文件 */
    fun deleteAll(): Boolean {
        var ok = true
        listFiles().forEach { f -> ok = (f.delete() || !f.exists()) && ok }
        return ok
    }
}
