package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 蓝奏云直链解析 API
 *
 * 基于 Python 版 lanzouAPI (v2.0.3) 转译。
 * 实现了 acw_sc__v2 cookie 生成算法以绕过反爬机制。
 *
 * 流程：
 * 1. GET 分享页，若返回 arg1 反爬页则生成 acw_sc__v2 cookie 后重试
 * 2. 从 HTML 提取 JS 参数，POST filemoreajax.php 获取文件列表 JSON
 * 3. GET 文件下载页 → 提取 arg1 → 生成 cookie → 重试 → 提取 /fn 链接
 * 4. GET /fn 页 → 提取 ajax 参数 → POST /ajaxm → 从 JSON 取 dom+url 拼接直链
 */
class LanzouCloudApi {

    private companion object {
        // 蓝奏云反爬 KEY（硬编码）
        private const val ACW_KEY = "3000176000856006061501533003690027800375"

        // arg1 字符重排序表（Python 版 order 各元素减 1）
        private val ACW_ORDER = intArrayOf(
            14, 34, 28, 23, 32, 15, 0, 37, 9, 8,
            18, 30, 39, 26, 21, 22, 24, 12, 5, 10,
            38, 17, 19, 7, 13, 20, 31, 25, 1, 29,
            6, 3, 16, 4, 2, 27, 33, 36, 11, 35
        )

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0"

        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    data class LanzouFileInfo(
        val fileName: String,
        val fileSize: String,
        val downloadUrl: String,
        val versionName: String? = null  // 从文件名解析的版本号
    )

    private data class FileEntry(
        val id: String,
        val name: String,
        val size: String
    )

    private data class FileListParams(
        val lx: Int,
        val up: Int,
        val ls: Int,
        val rep: String,
        val t: String,
        val k: String,
        val fid: Int,
        val uid: String,
        val pgs: Int,
        val puid: String
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
     * 生成 acw_sc__v2 cookie 值
     *
     * 算法（对应 Python 版 ky 函数）：
     * 1. 按 ACW_ORDER 从 arg1 取字符重排
     * 2. 与 ACW_KEY 逐字节（每两个 hex 字符）XOR
     * 3. 拼接为 hex 字符串
     */
    private fun generateAcwCookieValue(arg1: String): String {
        // 1. 按 ACW_ORDER 从 arg1 取字符重排
        val u = StringBuilder()
        for (pos in ACW_ORDER) {
            if (pos < arg1.length) {
                u.append(arg1[pos])
            }
        }

        // 2. 与 KEY 逐字节（每两个 hex 字符）XOR
        val result = StringBuilder()
        var i = 0
        while (i + 1 < u.length && i + 1 < ACW_KEY.length) {
            val a = u.substring(i, i + 2).toInt(16)
            val b = ACW_KEY.substring(i, i + 2).toInt(16)
            result.append((a xor b).toString(16).padStart(2, '0'))
            i += 2
        }
        return result.toString()
    }

    /**
     * 从 HTML 中提取 arg1（反爬参数）
     */
    private fun extractArg1(html: String): String? {
        val pattern = Regex("""var\s+arg1\s*=\s*'([^']+)'""")
        return pattern.find(html)?.groupValues?.get(1)
    }

    /**
     * 从分享页 HTML 提取 POST filemoreajax.php 所需参数
     */
    private fun extractFileListParams(html: String): FileListParams? {
        return try {
            val lx = Regex("""'lx':(\d+),""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val up = Regex("""'up':(\d+),""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val ls = Regex("""'ls':(\d+),""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null

            // rep 值带引号，去掉引号
            val rep = Regex("""'rep':\s*'([^']*)'""").find(html)?.groupValues?.get(1) ?: return null

            // t 是变量引用：'t':varName  →  varName = 'value'
            val tVar = Regex("""'t'\s*:\s*(\w+)""").find(html)?.groupValues?.get(1) ?: return null
            val tVal = Regex("""$tVar\s*=\s*'(\d+)'""").find(html)?.groupValues?.get(1) ?: return null

            // k 可能是变量引用，也可能直接是值
            val kVarMatch = Regex("""'k'\s*:\s*(\w+)""").find(html)
            val kVal = if (kVarMatch != null) {
                val kVar = kVarMatch.groupValues[1]
                Regex("""$kVar\s*=\s*'([a-f0-9]+)'""").find(html)?.groupValues?.get(1)
            } else {
                // 兜底：查找 _h59t8 变量
                Regex("""var\s+_h59t8\s*=\s*'([a-f0-9]+)'""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)
            } ?: return null

            val fid = Regex("""'fid':(\d+),""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val uid = Regex("""'uid':'([^']+)'""").find(html)?.groupValues?.get(1) ?: return null
            val pgs = Regex("""pgs\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val puid = Regex("""'puid':'([^']+)'""").find(html)?.groupValues?.get(1) ?: return null

            FileListParams(lx, up, ls, rep, tVal, kVal, fid, uid, pgs, puid)
        } catch (e: Exception) {
            Timber.w(e, "提取文件列表参数失败")
            null
        }
    }

    /**
     * 解析 filemoreajax.php 返回的 JSON，提取文件列表
     */
    private fun parseFileListJson(json: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()

        val ztMatch = Regex(""""zt"\s*:\s*(\d+)""").find(json)
        if (ztMatch?.groupValues?.get(1) != "1") {
            val infoMatch = Regex(""""info"\s*:\s*"([^"]*)"""").find(json)
            Timber.w("蓝奏云文件列表获取失败: ${infoMatch?.groupValues?.get(1) ?: "未知"}")
            return entries
        }

        // 提取 text 数组中每个文件对象
        val objectPattern = Regex("""\{[^{}]*"name_all"[^{}]*\}""")
        objectPattern.findAll(json).forEach { objMatch ->
            val objStr = objMatch.value
            val id = Regex(""""id"\s*:\s*"([^"]*)"""").find(objStr)?.groupValues?.get(1) ?: return@forEach
            val name = Regex(""""name_all"\s*:\s*"([^"]*)"""").find(objStr)?.groupValues?.get(1) ?: return@forEach
            val size = Regex(""""size"\s*:\s*"([^"]*)"""").find(objStr)?.groupValues?.get(1) ?: ""
            entries.add(FileEntry(id, name, size))
        }

        Timber.d("从蓝奏云解析到 ${entries.size} 个文件")
        return entries
    }

    /**
     * 获取文件的真实下载直链（对应 Python 版 PAGE2）
     *
     * @param baseUrl 蓝奏云基础 URL（如 https://wwbvc.lanzn.com）
     * @param shareUrl 分享链接（用作 Referer）
     * @param fileId 文件 ID
     * @param cookies cookie 存储
     */
    private fun getRealDownloadUrl(
        baseUrl: String,
        shareUrl: String,
        fileId: String,
        cookies: MutableMap<String, String>
    ): String? {
        val downloadPageUrl = "$baseUrl/$fileId"

        // Step 1: GET 下载页，获取 arg1
        var html = fetchGet(downloadPageUrl, shareUrl, cookies)
        val arg1 = extractArg1(html) ?: run {
            Timber.w("下载页未找到 arg1: $downloadPageUrl")
            return null
        }

        // Step 2: 生成 acw_sc__v2 cookie
        cookies["acw_sc__v2"] = generateAcwCookieValue(arg1)

        // Step 3: 带 cookie 重新 GET 下载页
        html = fetchGet(downloadPageUrl, downloadPageUrl, cookies)

        // Step 4: 提取 /fn... 下载按钮链接
        val fnMatch = Regex("""src="(/fn[^"]+)"""").find(html) ?: run {
            Timber.w("下载页未找到 /fn 链接")
            return null
        }
        val downloadButtonUrl = "$baseUrl${fnMatch.groupValues[1]}"

        // Step 5: GET 下载按钮页
        val buttonHtml = fetchGet(downloadButtonUrl, downloadPageUrl, cookies)

        // Step 6: 提取 ajax 请求参数
        val action = Regex("""'action':\s*'([^']+)'""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 action"); return null
        }
        val ajaxdata = Regex("""var\s+ajaxdata\s*=\s*'([^']+)';""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 ajaxdata"); return null
        }
        val wpSign = Regex("""var\s+wp_sign\s*=\s*'([^']+)';""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 wp_sign"); return null
        }
        val kdns = Regex("""var\s+kdns\s*=\s*(\d+);""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 kdns"); return null
        }
        val websign = Regex("""'websign':\s*'([^']+)'""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 websign"); return null
        }
        val ves = Regex("""'ves':\s*(\d+)""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 ves"); return null
        }
        val ajaxUrlPath = Regex("""url\s*:\s*'(\/ajaxm[^']+)'""").find(buttonHtml)?.groupValues?.get(1) ?: run {
            Timber.w("未找到 ajaxm URL"); return null
        }

        val getDownloadUrlApi = "$baseUrl$ajaxUrlPath"

        // Step 7: POST 获取下载链接 JSON
        val postData = buildString {
            append("action=").append(URLEncoder.encode(action, "UTF-8"))
            append("&websignkey=").append(URLEncoder.encode(ajaxdata, "UTF-8"))
            append("&signs=").append(URLEncoder.encode(ajaxdata, "UTF-8"))
            append("&sign=").append(URLEncoder.encode(wpSign, "UTF-8"))
            append("&websign=").append(URLEncoder.encode(websign, "UTF-8"))
            append("&kd=").append(kdns)
            append("&ves=").append(ves)
        }

        val downloadJson = fetchPost(getDownloadUrlApi, postData, downloadButtonUrl, cookies)

        // Step 8: 从 JSON 提取 dom 和 url，拼接直链
        val domMatch = Regex(""""dom"\s*:\s*"([^"]*)"""").find(downloadJson)
        val urlMatch = Regex(""""url"\s*:\s*"([^"]*)"""").find(downloadJson)

        if (domMatch != null && urlMatch != null) {
            val finalUrl = "${domMatch.groupValues[1]}/file/${urlMatch.groupValues[1]}"
            Timber.d("获取到蓝奏云直链: $finalUrl")
            return finalUrl
        }

        Timber.w("下载链接 JSON 解析失败: $downloadJson")
        return null
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
                val baseUrl = getBaseUrl(shareUrl)
                val cookies = mutableMapOf<String, String>()

                // Step 1: 访问分享页
                var html = fetchGet(shareUrl, shareUrl, cookies)

                // 检查是否命中反爬（页面含 arg1），若是则生成 cookie 后重试
                val arg1 = extractArg1(html)
                if (arg1 != null) {
                    cookies["acw_sc__v2"] = generateAcwCookieValue(arg1)
                    html = fetchGet(shareUrl, shareUrl, cookies)
                }

                // Step 2: 从 HTML 提取 POST 参数
                val params = extractFileListParams(html)
                if (params == null) {
                    return@withContext Result.failure(Exception("无法从分享页提取文件列表参数"))
                }

                // Step 3: POST filemoreajax.php 获取文件列表 JSON
                val apiUrl = "$baseUrl/filemoreajax.php?file=${params.fid}"
                val postData = buildString {
                    append("lx=").append(params.lx)
                    append("&fid=").append(params.fid)
                    append("&uid=").append(URLEncoder.encode(params.uid, "UTF-8"))
                    append("&puid=").append(URLEncoder.encode(params.puid, "UTF-8"))
                    append("&pg=").append(params.pgs)
                    append("&rep=").append(URLEncoder.encode(params.rep, "UTF-8"))
                    append("&t=").append(params.t)
                    append("&k=").append(params.k)
                    append("&up=").append(params.up)
                    append("&ls=").append(params.ls)
                    if (password != null) {
                        append("&pwd=").append(URLEncoder.encode(password, "UTF-8"))
                    }
                }

                val fileListJson = fetchPost(apiUrl, postData, shareUrl, cookies)
                val fileEntries = parseFileListJson(fileListJson)

                if (fileEntries.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                // Step 4: 对每个文件获取真实下载直链
                val files = mutableListOf<LanzouFileInfo>()
                for (entry in fileEntries) {
                    try {
                        val downloadUrl = getRealDownloadUrl(baseUrl, shareUrl, entry.id, cookies)
                        if (downloadUrl != null) {
                            files.add(
                                LanzouFileInfo(
                                    fileName = entry.name,
                                    fileSize = entry.size,
                                    downloadUrl = downloadUrl,
                                    versionName = parseVersionFromFileName(entry.name)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "获取文件下载链接失败: ${entry.name}")
                    }
                }

                Result.success(files)
            } catch (e: Exception) {
                Timber.e(e, "解析蓝奏云分享链接失败")
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

    // === HTTP 工具方法 ===

    private fun getBaseUrl(shareUrl: String): String {
        val url = URL(shareUrl)
        return "${url.protocol}://${url.host}"
    }

    private fun fetchGet(
        url: String,
        referer: String?,
        cookies: MutableMap<String, String>
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            addRequestProperty("User-Agent", USER_AGENT)
            addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            addRequestProperty("Accept-Language", "zh-CN,zh-HK;q=0.9,zh;q=0.8,en;q=0.7,en-GB;q=0.6,en-US;q=0.5")
            addRequestProperty("Cache-Control", "max-age=0")
            addRequestProperty("Connection", "keep-alive")
            addRequestProperty("DNT", "1")
            addRequestProperty("Upgrade-Insecure-Requests", "1")
            referer?.let { addRequestProperty("Referer", it) }
            if (cookies.isNotEmpty()) {
                addRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
        }

        try {
            val code = conn.responseCode
            extractResponseCookies(conn, cookies)
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchPost(
        url: String,
        body: String,
        referer: String?,
        cookies: MutableMap<String, String>
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            addRequestProperty("User-Agent", USER_AGENT)
            addRequestProperty("Accept", "application/json, text/javascript, */*")
            addRequestProperty("Accept-Language", "zh-CN,zh-HK;q=0.9,zh;q=0.8,en;q=0.7,en-GB;q=0.6,en-US;q=0.5")
            addRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            addRequestProperty("X-Requested-With", "XMLHttpRequest")
            addRequestProperty("Connection", "keep-alive")
            addRequestProperty("DNT", "1")
            referer?.let { addRequestProperty("Referer", it) }
            if (cookies.isNotEmpty()) {
                addRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
        }

        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            extractResponseCookies(conn, cookies)
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    private fun extractResponseCookies(conn: HttpURLConnection, cookies: MutableMap<String, String>) {
        val setCookies = conn.headerFields["Set-Cookie"] ?: return
        for (item in setCookies) {
            val cookie = item.substringBefore(";").trim()
            val eq = cookie.indexOf('=')
            if (eq > 0) {
                cookies[cookie.substring(0, eq)] = cookie.substring(eq + 1)
            }
        }
    }
}
