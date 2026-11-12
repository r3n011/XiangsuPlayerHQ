package com.theveloper.pixelplay.data.plugin

import android.content.Context
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件商店客户端（商店协议 v1，路由 /health /api/plugins /zips/:file）。
 *
 * 下载要求：流式写盘、进度回调、SHA-256 校验、单包 ≤ 8MB、zipUrl 主机必须等于商店主机。
 */
@Singleton
class XsStoreClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: XsPluginPaths,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://xiangsudev.cn/plugin-store"
        private const val MAX_ZIP_BYTES = 8L * 1024L * 1024L
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val prefs by lazy {
        context.getSharedPreferences("xs_plugin_store", Context.MODE_PRIVATE)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            val normalized = value.trim().trimEnd('/')
            prefs.edit().putString("base_url", normalized.ifBlank { DEFAULT_BASE_URL }).apply()
        }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun fetchPlugins(): List<XSStorePlugin> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val request = Request.Builder().url("$baseUrl/api/plugins").get().build()
        XsLog.d(XsLog.STORE, "GET %s/api/plugins", baseUrl)
        val body = client.newCall(request).execute().use { response ->
            XsLog.d(XsLog.STORE, "GET /api/plugins -> HTTP %d in %dms", response.code, System.currentTimeMillis() - started)
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        val envelope = json.decodeFromString(XSStoreEnvelope.serializer(), body)
        XsLog.i(XsLog.STORE, "store index: code=%d plugins=%d", envelope.code, envelope.data.size)
        if (envelope.code != 0) throw IOException("store code=${envelope.code} ${envelope.error.orEmpty()}")
        // 只保留与本宿主兼容的类型（capability），并补全 zipUrl
        envelope.data
            .filter { it.type == "capability" && it.id.isNotBlank() && it.zipUrl.isNotBlank() }
            .map { p ->
                if (p.zipUrl.startsWith("http")) p else p.copy(zipUrl = p.zipUrl)
            }
    }

    /**
     * 下载并校验插件 zip，返回本地临时文件。调用方负责导入与删除。
     */
    suspend fun download(
        plugin: XSStorePlugin,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val absolute = resolveZipUrl(plugin.zipUrl)
        val target = paths.tempZip(plugin.id, plugin.version)
        if (target.exists()) target.delete()
        XsLog.i(XsLog.STORE, "download %s v%s <- %s", plugin.id, plugin.version, absolute.substringBefore('?'))

        val request = Request.Builder().url(absolute).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = if (plugin.size > 0) plugin.size else body.contentLength()
            XsLog.d(XsLog.STORE, "download %s: HTTP %d, contentLength=%d", plugin.id, response.code, total)
            if (total > MAX_ZIP_BYTES) throw IOException("安装包超过 8MB 上限")

            var downloaded = 0L
            var lastReport = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        downloaded += read
                        if (downloaded > MAX_ZIP_BYTES) throw IOException("安装包超过 8MB 上限")
                        output.write(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 120) {
                            lastReport = now
                            onProgress(if (total > 0) (downloaded.toFloat() / total) else 0f)
                        }
                    }
                }
            }
            onProgress(1f)
            XsLog.i(
                XsLog.STORE,
                "download ok: %s -> %d bytes in %dms",
                plugin.id,
                downloaded,
                System.currentTimeMillis() - started,
            )
        }

        val expected = plugin.sha256.trim().lowercase()
        if (expected.isNotEmpty()) {
            val actual = sha256(target)
            if (actual != expected) {
                XsLog.e(XsLog.STORE, "sha256 mismatch for %s: expected=%s actual=%s", plugin.id, expected, actual)
                target.delete()
                throw XsInstallException(XsErrors.CHECKSUM_MISMATCH, "安装包校验失败，已丢弃")
            }
            XsLog.d(XsLog.STORE, "sha256 ok for %s", plugin.id)
        }
        target
    }

    private fun resolveZipUrl(zipUrl: String): String {
        val url = if (zipUrl.startsWith("http")) zipUrl else "$baseUrl/${zipUrl.trimStart('/')}"
        // 防 SSRF：zip 主机必须与商店主机一致
        val storeHost = runCatching { URI(baseUrl).host }.getOrNull()
        val zipHost = runCatching { URI(url).host }.getOrNull()
        if (storeHost != null && zipHost != null && !zipHost.equals(storeHost, ignoreCase = true)) {
            throw IOException("zip 主机不在商店域内：$zipHost")
        }
        return url
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
