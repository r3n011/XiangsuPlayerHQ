package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 蓝奏云直链解析 API
 *
 * 用于从蓝奏云分享链接获取真实下载 URL。
 * 实现了 acw_sc__v2 cookie 生成算法以绕过反爬机制。
 */
class LanzouCloudApi {

    private companion object {
        // 蓝奏云反爬 KEY（硬编码在 JS 中）
        private const val ACW_KEY = "3000176000856006061501533003690027800375"

        // 数组重排序
        private val ACW_ORDER = intArrayOf(
            4, 17, 23, 27, 7, 21, 26, 16, 2, 5, 28, 19, 11, 13, 8,
            1, 15, 24, 3, 9, 22, 12, 14, 20, 6, 0, 25, 18, 10
        )

        // UA
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // 请求超时
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    data class LanzouFileInfo(
        val fileName: String,
        val fileSize: String,
        val downloadUrl: String,
        val versionName: String? = null  // 从文件名解析的版本号
    )

    /**
     * 从文件名解析版本号
     * 支持格式：PixelPlay-1.2.3-28-20260729-release.apk
     */
    fun parseVersionFromFileName(fileName: String): String? {
        val apkName = fileName.removeSuffix(".apk")
        // 匹配 PixelPlay-{versionName}-{versionCode}-{date}-{variant} 格式
        val regex = Regex("""PixelPlay-([\d.]+)-\d+-\d+-\w+""")
        val match = regex.find(apkName)
        return match?.groupValues?.get(1)
    }

    /**
     * 解析蓝奏云分享链接，获取所有文件的下载信息
     *
     * @param shareUrl 分享链接（如 https://wwbvc.lanzn.com/b011m9azlg）
     * @param password 提取密码（可选）
     * @return 文件信息列表
     */
    suspend fun resolveShare(
        shareUrl: String,
        password: String? = null
    ): Result<List<LanzouFileInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val files = mutableListOf<LanzouFileInfo>()

                // 使用简单的 cookie 存储
                val cookies = mutableMapOf<String, String>()

                // Step 1: 生成 acw_sc__v2 cookie 并添加到 cookies
                generateAcwCookie(shareUrl, cookies)

                // Step 2: 访问分享页获取文件列表（带 cookie）
                val sharePageHtml = fetchPage(shareUrl, cookies)

                // Step 3: 提取文件信息
                val fileEntries = extractFileEntries(sharePageHtml)

                for (entry in fileEntries) {
                    try {
                        // Step 4: 获取下载页 URL
                        val downloadPageUrl = getDownloadPageUrl(shareUrl, entry, cookies)
                        if (downloadPageUrl == null) continue

                        // Step 5: 获取真实下载链接
                        val downloadInfo = getRealDownloadUrl(downloadPageUrl, entry, cookies)
                        if (downloadInfo != null) {
                            val versionName = parseVersionFromFileName(entry.name)
                            files.add(
                                LanzouFileInfo(
                                    fileName = entry.name,
                                    fileSize = entry.size,
                                    downloadUrl = downloadInfo,
                                    versionName = versionName
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to resolve file: ${entry.name}")
                    }
                }

                Result.success(files)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve Lanzou share")
                Result.failure(e)
            }
        }
    }

    /**
     * 获取指定版本的 APK 下载链接
     *
     * @param shareUrl 分享链接
     * @param password 提取密码
     * @param targetVersion 目标版本号（如 "1.2.3"）
     * @param preferredArch 首选架构（"arm64" 或 "arm32"）
     * @return 下载链接
     */
    suspend fun getDownloadUrl(
        shareUrl: String,
        password: String? = null,
        targetVersion: String? = null,
        preferredArch: String = "arm64"
    ): Result<String> {
        val result = resolveShare(shareUrl, password)
        return result.map { files ->
            if (files.isEmpty()) {
                throw Exception("蓝奏云中没有找到文件")
            }

            // 如果指定了版本号，优先匹配版本号一致的文件
            val targetFiles = if (targetVersion != null) {
                val matched = files.filter { it.versionName == targetVersion }
                if (matched.isNotEmpty()) {
                    matched
                } else {
                    Timber.w("蓝奏云中未找到版本号 $targetVersion 的文件，返回所有文件")
                    files
                }
            } else {
                files
            }

            // 根据架构选择：优先 arm64，其次 arm32，最后任意
            val archKeyword = when (preferredArch) {
                "arm64" -> listOf("arm64", "armv8", "64位")
                "arm32" -> listOf("arm32", "armv7", "32位")
                else -> emptyList()
            }

            val archMatched = archKeyword.firstNotNullOfOrNull { keyword ->
                targetFiles.firstOrNull { it.fileName.contains(keyword, ignoreCase = true) }
            }

            archMatched?.downloadUrl
                ?: targetFiles.firstOrNull()?.downloadUrl
                ?: throw Exception("没有找到可用的下载链接")
        }
    }

    // === 私有方法 ===

    private fun fetchPage(url: String, cookies: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            addRequestProperty("User-Agent", USER_AGENT)
            addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            addRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
        }

        if (cookies.isNotEmpty()) {
            connection.addRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        val responseCode = connection.responseCode
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return inputStream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    /**
     * 生成 acw_sc__v2 cookie
     * 这是蓝奏云的反爬机制，通过特定的数组重排序和 XOR 运算生成
     */
    private fun generateAcwCookie(baseUrl: String, cookies: MutableMap<String, String>) {
        try {
            // 1. 对 KEY 进行重排序
            val keyChars = ACW_KEY.toCharArray()
            val reordered = CharArray(ACW_ORDER.size)
            for (i in ACW_ORDER.indices) {
                reordered[i] = keyChars[ACW_ORDER[i]]
            }

            // 2. 将重排序后的字符转换为数值（每两个字符一组）
            val values = mutableListOf<Int>()
            for (i in reordered.indices step 2) {
                if (i + 1 < reordered.size) {
                    val pair = "${reordered[i]}${reordered[i + 1]}"
                    values.add(pair.toIntOrNull() ?: 0)
                }
            }

            // 3. 生成时间戳相关的值
            val timeStamp = System.currentTimeMillis() / 1000
            val timeValue = (timeStamp % 100000).toInt()

            // 4. 生成 cookie 值
            val cookieParts = mutableListOf<String>()
            for (value in values) {
                cookieParts.add((value xor timeValue).toString())
            }

            // 5. 拼接 cookie
            val cookieValue = cookieParts.joinToString("|")

            // 6. 添加到 cookies
            cookies["acw_sc__v2"] = cookieValue

            Timber.d("Generated acw_sc__v2 cookie: $cookieValue")
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate acw_sc__v2 cookie")
        }
    }

    private data class FileEntry(
        val id: String,
        val name: String,
        val size: String,
        val time: String,
        val pwd: String? = null
    )

    /**
     * 从 HTML 提取文件条目
     */
    private fun extractFileEntries(html: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()

        // 尝试多种正则模式
        val patterns = listOf(
            // 模式 1: 从 JS 变量中提取
            Regex("""data\s*[:=]\s*(\[[\s\S]*?\])"""),
            // 模式 2: 从 HTML 属性中提取
            Regex("""<div[^>]*class="[^"]*file[^"]*"[^>]*data-id="([^"]*)"[^>]*>"""),
            // 模式 3: 从列表项中提取
            Regex("""li[^>]*>[^<]*<span[^>]*class="[^"]*name[^"]*"[^>]*>([^<]+)</span>""")
        )

        // 尝试从 JSON 数据中提取
        val jsonPattern = Regex("""(\{[\s\S]*?"id"[\s\S]*?"name"[\s\S]*?\})""")
        jsonPattern.findAll(html).forEach { match ->
            try {
                val jsonStr = match.value
                val idMatch = Regex(""""id"\s*:\s*"([^"]+)"""").find(jsonStr)
                val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(jsonStr)
                val sizeMatch = Regex(""""size"\s*:\s*"([^"]*)"""").find(jsonStr)
                val timeMatch = Regex(""""time"\s*:\s*"([^"]*)"""").find(jsonStr)
                val pwdMatch = Regex(""""pwd"\s*:\s*"([^"]*)"""").find(jsonStr)

                if (idMatch != null && nameMatch != null) {
                    entries.add(
                        FileEntry(
                            id = idMatch.groupValues[1],
                            name = nameMatch.groupValues[1],
                            size = sizeMatch?.groupValues?.get(1) ?: "",
                            time = timeMatch?.groupValues?.get(1) ?: "",
                            pwd = pwdMatch?.groupValues?.get(1)
                        )
                    )
                }
            } catch (e: Exception) {
                // 忽略解析失败的条目
            }
        }

        // 如果 JSON 解析未找到，尝试从 HTML 结构提取
        if (entries.isEmpty()) {
            // 从文件表格中提取
            val rowPattern = Regex("""<tr[^>]*data-id="([^"]*)"[^>]*>[\s\S]*?<td[^>]*class="[^"]*name[^"]*"[^>]*>([^<]+)</td>[\s\S]*?<td[^>]*class="[^"]*size[^"]*"[^>]*>([^<]+)</td>""")
            rowPattern.findAll(html).forEach { match ->
                entries.add(
                    FileEntry(
                        id = match.groupValues[1],
                        name = match.groupValues[2],
                        size = match.groupValues[3],
                        time = ""
                    )
                )
            }
        }

        // 最后兜底：查找所有 download 链接
        if (entries.isEmpty()) {
            val linkPattern = Regex(""""([^"]*download[^"]*)"[^>]*>.*?<span[^>]*>([^<]+\.apk)</span>""")
            linkPattern.findAll(html).forEach { match ->
                entries.add(
                    FileEntry(
                        id = match.groupValues[1],
                        name = match.groupValues[2],
                        size = "",
                        time = ""
                    )
                )
            }
        }

        Timber.d("Found ${entries.size} file entries from Lanzou share")
        return entries
    }

    private fun getDownloadPageUrl(
        baseUrl: String,
        entry: FileEntry,
        cookies: Map<String, String>
    ): String? {
        return try {
            // 构造下载页面 URL
            val downloadPageUrl = "${baseUrl.let { 
                if (it.endsWith("/")) it else "$it/" 
            }}${entry.id}"
            
            val html = fetchPage(downloadPageUrl, cookies)
            
            // 提取真实下载链接
            val downloadPattern = Regex(""""([^"]*filemore[^"]*|[^"]*download[^"]*action=1[^"]*)"""")
            val match = downloadPattern.find(html)
            
            match?.groupValues?.get(0)?.let { url ->
                if (url.startsWith("/")) "${URL(baseUrl).protocol}://${URL(baseUrl).host}$url"
                else url
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get download page URL")
            null
        }
    }

    private fun getRealDownloadUrl(
        downloadPageUrl: String,
        entry: FileEntry,
        cookies: Map<String, String>
    ): String? {
        return try {
            val html = fetchPage(downloadPageUrl, cookies)

            // 提取真实下载直链
            val directUrlPattern = Regex(""""([^"]*dmpdmp\.com[^"]*|https?://[^"]*\.apk[^"]*)"""")
            val match = directUrlPattern.find(html)

            match?.groupValues?.get(1)?.let { url ->
                // 确保 URL 完整
                if (url.startsWith("//")) "https:$url"
                else if (!url.startsWith("http")) {
                    val base = URL(downloadPageUrl)
                    "${base.protocol}://${base.host}$url"
                } else url
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get real download URL")
            null
        }
    }
}
