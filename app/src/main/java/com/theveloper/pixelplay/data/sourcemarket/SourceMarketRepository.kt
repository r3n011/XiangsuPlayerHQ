package com.theveloper.pixelplay.data.sourcemarket

import android.content.Context
import com.theveloper.pixelplay.data.github.GitHubRelease
import com.theveloper.pixelplay.data.github.GitHubReleaseAsset
import com.theveloper.pixelplay.data.github.GitHubToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 像素音源市场数据源：从固定 GitHub 仓库拉取 releases，下载 zip 音源包并提取 .js。
 *
 * GitHub API 调用复用 UpdateChecker 的 HttpURLConnection + kotlinx.serialization 范式；
 * zip 下载对 GitHub 直链做镜像加速扩展（同 ApkDownloadInstaller）。
 */
@Singleton
class SourceMarketRepository @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val REPO_OWNER = "guoyue2010"
        const val REPO_NAME = "lxmusic-"

        /** 单个 js 条目读取上限（防 zip bomb） */
        private const val MAX_ENTRY_BYTES = 4 * 1024 * 1024

        /** GitHub 下载加速镜像（顺序即尝试顺序，末尾自动补官方原地址兜底） */
        private val MIRROR_PREFIXES = listOf(
            "https://ghproxy.net/",
            "https://mirror.ghproxy.com/",
            "https://gh-proxy.com/",
            "https://ghproxy.homeboyc.cn/",
            "https://github.akams.cn/"
        )
    }

    /** 拉取音源仓库的全部 Release（最新在前） */
    suspend fun fetchReleases(): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=100"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
            val githubToken = GitHubToken.value
            if (githubToken.isNotBlank()) {
                connection.addRequestProperty("Authorization", "token $githubToken")
            }
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = json.decodeFromString<List<GitHubRelease>>(response)
                Result.success(releases)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Timber.e("SourceMarket: fetchReleases failed ${connection.responseCode} - $err")
                Result.failure(Exception("获取发布列表失败: HTTP ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SourceMarket: fetchReleases exception")
            Result.failure(e)
        }
    }

    /** 取出 release 的 zip 附件（第一个 .zip） */
    fun zipAssetOf(release: GitHubRelease): GitHubReleaseAsset? =
        release.assets.firstOrNull { it.name.lowercase().endsWith(".zip") }

    /** zip 下载缓存路径：cacheDir/source_market/{tag}.zip */
    fun cachedZipFile(tag: String): File {
        val dir = File(appContext.cacheDir, "source_market").also { it.mkdirs() }
        return File(dir, "$tag.zip")
    }

    /**
     * 下载 release 的 zip 到缓存目录，返回进度流。
     * 镜像优先 + 官方直链兜底；校验 HTTP 2xx、content-type、ZIP 魔数。
     */
    fun downloadZip(asset: GitHubReleaseAsset, tag: String): Flow<ZipDownloadState> = flow {
        emit(ZipDownloadState.Downloading(0f))
        val target = cachedZipFile(tag)
        val candidates = if (asset.browser_download_url.startsWith("https://github.com/")) {
            MIRROR_PREFIXES.map { it + asset.browser_download_url } + asset.browser_download_url
        } else {
            listOf(asset.browser_download_url)
        }

        var lastError: String? = null
        for ((index, candidateUrl) in candidates.withIndex()) {
            var connection: HttpURLConnection? = null
            try {
                Timber.d("SourceMarket: 下载源 [${index + 1}/${candidates.size}]: $candidateUrl")
                connection = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    addRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    addRequestProperty("Accept", "application/octet-stream,*/*")
                    addRequestProperty("Accept-Encoding", "identity")
                    instanceFollowRedirects = true
                }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw RuntimeException("下载失败: HTTP $responseCode")
                }
                val contentType = connection.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw RuntimeException("响应不是 zip（content-type=$contentType）")
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                emit(ZipDownloadState.Downloading((done.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
                // 校验 ZIP 魔数（PK\x03\x04），防止镜像返回 HTML 错误页
                val headerOk = target.exists() && target.length() >= 4 &&
                    target.inputStream().use { ins ->
                        val h = ByteArray(4)
                        ins.read(h) == 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() &&
                            h[2] == 3.toByte() && h[3] == 4.toByte()
                    }
                if (!headerOk) {
                    throw RuntimeException("下载内容不是有效 zip")
                }
                Timber.d("SourceMarket: 下载成功 ${target.length()} bytes from $candidateUrl")
                emit(ZipDownloadState.Downloaded(target))
                return@flow
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Timber.w(e, "SourceMarket: 下载源 ${index + 1} 失败")
                runCatching { target.delete() }
            } finally {
                connection?.disconnect()
            }
        }
        emit(ZipDownloadState.Error(lastError ?: "下载失败"))
    }.flowOn(Dispatchers.IO)

    /**
     * 解析 zip 内所有 .js 条目（含字节）。只取 basename 防目录穿越；
     * 单条目上限 [MAX_ENTRY_BYTES]；同名去重（保留首个）。
     */
    suspend fun inspectZip(zipFile: File): List<MarketJsEntry> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, MarketJsEntry>()
        try {
            ZipInputStream(zipFile.inputStream(), Charsets.UTF_8).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val rawName = entry.name
                    if (!rawName.endsWith(".js", ignoreCase = true) || entry.isDirectory) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    // 防目录穿越：仅取 basename
                    val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
                    if (baseName.isBlank() || baseName == "." || baseName == "..") {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.size <= MAX_ENTRY_BYTES && !result.containsKey(baseName)) {
                        val bytes = ByteArrayOutputStream().also { out ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            var total = 0L
                            while (zis.read(buf).also { read = it } != -1 && total < MAX_ENTRY_BYTES) {
                                out.write(buf, 0, read)
                                total += read
                            }
                        }.toByteArray()
                        if (bytes.isNotEmpty()) {
                            result[baseName] = MarketJsEntry(fileName = baseName, size = bytes.size.toLong(), bytes = bytes)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SourceMarket: inspectZip failed for ${zipFile.name}")
        }
        result.values.toList()
    }
}
