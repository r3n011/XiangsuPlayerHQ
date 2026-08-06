package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 蓝奏云直链解析 API
 *
 * 用于从蓝奏云分享链接获取真实下载 URL。
 * 对齐 LanzouAPI (v2.0.3 / api.js v2.0.1) 最新算法：
 *   - acw_sc__v2 cookie v3：由下载页 `arg1`（40 位十六进制）按 order 表重排后与 KEY 逐字节 XOR 生成
 *   - filemoreajax.php 文件列表：POST 分享页参数获取文件清单（zt==1）
 *   - 逐文件走下载页（arg1 → cookie → /fn → /ajaxm）拿到直链 `${dom}/file/${url}`
 */
class LanzouCloudApi {

    private companion object {
        // 蓝奏云反爬 KEY（硬编码在 JS 中）
        private const val ACW_KEY = "3000176000856006061501533003690027800375"

        // 数组重排序表（v3，40 个 1-based 十六进制位置）
        private val ACW_ORDER = intArrayOf(
            0xf, 0x23, 0x1d, 0x18, 0x21, 0x10, 0x1, 0x26, 0xa, 0x9,
            0x13, 0x1f, 0x28, 0x1b, 0x16, 0x17, 0x19, 0xd, 0x6, 0xb,
            0x27, 0x12, 0x14, 0x8, 0xe, 0x15, 0x20, 0x1a, 0x2, 0x1e,
            0x7, 0x4, 0x11, 0x5, 0x3, 0x1c, 0x22, 0x25, 0xc, 0x24
        )

        // UA（与参考 api.py 一致的 Edge 桌面 UA）
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0"

        // 请求超时
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 20000
    }

    data class LanzouFileInfo(
        val fileName: String,
        val fileSize: String,
        val downloadUrl: String,
        val versionName: String? = null  // 从文件名解析的版本号
    )

    /**
     * 从文件名解析版本号
     * 支持格式：PixelPlay-{versionName}-{versionCode}-{date}-arm64.apk
     *         或：PixelPlay-{versionName}-{versionCode}-{date}-universal.apk
     */
    fun parseVersionFromFileName(fileName: String): String? {
        val apkName = fileName.removeSuffix(".apk")
        // 匹配 PixelPlay-{versionName}-{versionCode}-{date}-{variant} 格式
        val regex = Regex("""PixelPlay-([\d.]+)-\d+-\d+-(arm64|universal)""")
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
                val session = LanzouSession()
                val files = mutableListOf<LanzouFileInfo>()

                // ── PAGE1：进入分享页，提取文件列表参数 ──
                var indexHtml = session.get(shareUrl, referer = shareUrl)
                // 被反爬拦截时页面不含 'lx'，需先从 arg1 生成 acw_sc__v2 cookie 后重试
                if (!indexHtml.contains("'lx'")) {
                    val arg1 = Regex("""var\s+arg1\s*=\s*'([^']+)'""")
                        .find(indexHtml)?.groupValues?.get(1)
                    if (arg1 != null) {
                        session.setCookie("acw_sc__v2", generateAcwCookieV3(arg1))
                        indexHtml = session.get(shareUrl, referer = shareUrl)
                    }
                }

                val host = session.lastHost ?: URL(shareUrl).host
                val params = extractIndexParams(indexHtml)

                // 拉取文件列表
                val listForm = linkedMapOf<String, String>()
                listForm["lx"] = params.lx.toString()
                listForm["fid"] = params.fid.toString()
                listForm["uid"] = params.uid
                listForm["puid"] = params.puid
                listForm["pg"] = params.pgs.toString()
                listForm["rep"] = params.rep
                listForm["t"] = params.t
                listForm["k"] = params.k
                listForm["up"] = params.up.toString()
                listForm["ls"] = params.ls.toString()
                if (!password.isNullOrEmpty()) listForm["pwd"] = password

                val listJson = session.post(
                    "https://$host/filemoreajax.php?file=${params.fid}",
                    listForm,
                    referer = shareUrl
                )
                val listObj = JSONObject(listJson)
                if (listObj.optInt("zt") != 1) {
                    throw Exception("获取文件列表失败: ${listObj.optString("info", listJson)}")
                }
                val text = listObj.optJSONArray("text") ?: JSONArray()
                val fileEntries = mutableListOf<FileEntry>()
                for (i in 0 until text.length()) {
                    val item = text.optJSONObject(i) ?: continue
                    fileEntries.add(
                        FileEntry(
                            id = item.optString("id"),
                            name = item.optString("name_all"),
                            size = item.optString("size")
                        )
                    )
                }
                Timber.d("Lanzou: found ${fileEntries.size} files")

                // ── PAGE2：逐文件解析真实下载直链 ──
                for (entry in fileEntries) {
                    try {
                        val downloadUrl = resolveSingleFile(session, host, entry, shareUrl)
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
                        Timber.w(e, "Lanzou: failed to resolve file: ${entry.name}")
                    }
                }

                Result.success(files)
            } catch (e: Exception) {
                Timber.e(e, "Lanzou: failed to resolve share")
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

    // === 单文件下载直链解析（PAGE2） ===

    private fun resolveSingleFile(
        session: LanzouSession,
        host: String,
        entry: FileEntry,
        shareUrl: String
    ): String? {
        val downloadPageUrl = "https://$host/${entry.id}"

        // 进入下载页，提取 arg1 并生成 acw_sc__v2 cookie，再访问一次拿真实内容
        var dp = session.get(downloadPageUrl, referer = shareUrl)
        val arg1 = Regex("""var\s+arg1\s*=\s*'([^']+)'""").find(dp)?.groupValues?.get(1)
        if (arg1 != null) {
            session.setCookie("acw_sc__v2", generateAcwCookieV3(arg1))
            dp = session.get(downloadPageUrl, referer = downloadPageUrl)
        }

        // 提取下载按钮地址 /fn...
        val fn = Regex("""src="(/fn[^"]+)"""").find(dp)?.groupValues?.get(1) ?: run {
            Timber.w("Lanzou: no /fn found in download page for ${entry.name}")
            return null
        }
        val buttonUrl = "https://$host$fn"
        val buttonPage = session.get(buttonUrl, referer = downloadPageUrl)

        // 提取 ajax 参数
        val action = Regex("""'action':\s*'([^']+)'""").find(buttonPage)?.groupValues?.get(1)
        val ajaxdata = Regex("""var\s+ajaxdata\s*=\s*'([^']+)';""").find(buttonPage)?.groupValues?.get(1)
        val wpSign = Regex("""var\s+wp_sign\s*=\s*'([^']+)';""").find(buttonPage)?.groupValues?.get(1)
        val websign = Regex("""'websign':\s*'([^']+)'""").find(buttonPage)?.groupValues?.get(1)
        val ajaxUrl = Regex("""url\s*:\s*'(/ajaxm[^']+)'""").find(buttonPage)?.groupValues?.get(1)
        if (action == null || ajaxdata == null || wpSign == null || websign == null || ajaxUrl == null) {
            Timber.w("Lanzou: missing ajax params for ${entry.name}")
            return null
        }
        val kdns = Regex("""var\s+kdns\s*=\s*(\d+);""").find(buttonPage)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ves = Regex("""'ves':\s*(\d+)""").find(buttonPage)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val ajaxForm = linkedMapOf<String, String>()
        ajaxForm["action"] = action
        ajaxForm["websignkey"] = ajaxdata
        ajaxForm["signs"] = ajaxdata
        ajaxForm["sign"] = wpSign
        ajaxForm["websign"] = websign
        ajaxForm["kd"] = kdns.toString()
        ajaxForm["ves"] = ves.toString()

        val dlJson = session.post("https://$host$ajaxUrl", ajaxForm, referer = buttonUrl)
        val dlObj = JSONObject(dlJson)
        if (dlObj.optInt("zt") != 1) {
            Timber.w("Lanzou: ajax failed for ${entry.name}: $dlJson")
            return null
        }
        val dom = dlObj.optString("dom")
        val urlPart = dlObj.optString("url")
        if (dom.isBlank() || urlPart.isBlank()) return null
        return "$dom/file/$urlPart"
    }

    // === acw_sc__v2 cookie v3 算法 ===

    /**
     * 由下载页 `arg1`（40 位十六进制）生成 acw_sc__v2 cookie 值。
     * 对齐 LanzouAPI v2.0.3：按 order 表（1-based）重排 arg1，
     * 每 2 个十六进制字符与 KEY 对应 2 字符做 XOR，得到 20 字节 hex。
     */
    private fun generateAcwCookieV3(arg1: String): String {
        if (arg1.length < 40) {
            Timber.w("Lanzou: arg1 too short (${arg1.length})")
        }
        val sb = StringBuilder(40)
        for (pos in ACW_ORDER) {
            if (pos - 1 < arg1.length) sb.append(arg1[pos - 1])
        }
        val reordered = sb.toString()
        val result = StringBuilder(40)
        for (i in 0 until 40 step 2) {
            if (i + 1 >= reordered.length) break
            val a = reordered.substring(i, i + 2).toIntOrNull(16) ?: 0
            val b = ACW_KEY.substring(i, i + 2).toIntOrNull(16) ?: 0
            result.append((a xor b).toString(16).padStart(2, '0'))
        }
        return result.toString()
    }

    // === 分享页参数提取 ===

    private data class IndexParams(
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

    private fun extractIndexParams(html: String): IndexParams {
        fun first(pattern: Regex): String =
            pattern.find(html)?.groupValues?.get(1) ?: throw Exception("无法从分享页提取参数: $pattern")

        val lx = first(Regex("""'lx':(\d+),""")).toInt()
        val up = first(Regex("""'up':(\d+),""")).toInt()
        val ls = first(Regex("""'ls':(\d+),""")).toInt()
        // rep 可能为带引号字符串，去掉引号
        val rep = first(Regex("""'rep':([^,]+),""")).trim('\'', '"', ' ')
        // t 是变量名，真正的值形如 `xxx='12345'`
        val tVar = first(Regex("""'t'\s*:\s*(\w+)"""))
        val t = first(Regex("""$tVar\s*=\s*'(\d+)'"""))
        // k 可能是变量名或直接 hex；没有时兜底 _h59t8
        val k = try {
            val kVar = first(Regex("""'k'\s*:\s*(\w+)"""))
            first(Regex("""$kVar\s*=\s*'([a-f0-9]+)'"""))
        } catch (e: Exception) {
            try {
                first(Regex("""var\s+_h59t8\s*=\s*'([a-f0-9]+)'""", RegexOption.IGNORE_CASE))
            } catch (e2: Exception) {
                Timber.w(e2, "Lanzou: cannot find k in index page")
                ""
            }
        }
        val fid = first(Regex("""'fid':(\d+),""")).toInt()
        val uid = first(Regex("""'uid':'([^']+)',"""))
        val pgs = first(Regex("""pgs\s*=\s*(\d+);""")).toInt()
        val puid = first(Regex("""'puid':'([^']+)',"""))

        return IndexParams(lx, up, ls, rep, t, k, fid, uid, pgs, puid)
    }

    // === 简易 Cookie 会话 ===

    private class LanzouSession {
        val cookies = mutableMapOf<String, String>()
        var lastHost: String? = null

        fun setCookie(name: String, value: String) {
            cookies[name] = value
        }

        fun get(url: String, referer: String? = null): String =
            request("GET", url, null, referer)

        fun post(url: String, form: Map<String, String>, referer: String? = null): String {
            val body = form.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
            return request("POST", url, body, referer)
        }

        private fun encode(s: String): String =
            URLEncoder.encode(s, StandardCharsets.UTF_8.name())

        private fun request(method: String, url: String, body: String?, referer: String?): String {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = true
                addRequestProperty("User-Agent", USER_AGENT)
                addRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                addRequestProperty("Accept-Language", "zh-CN,zh-HK;q=0.9,zh;q=0.8,en;q=0.7")
                if (referer != null) addRequestProperty("Referer", referer)
                if (method == "POST") {
                    doOutput = true
                    addRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    addRequestProperty("X-Requested-With", "XMLHttpRequest")
                }
                if (cookies.isNotEmpty()) {
                    addRequestProperty(
                        "Cookie",
                        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    )
                }
            }

            try {
                if (method == "POST" && body != null) {
                    conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream

                // 提取 Set-Cookie（可多个）
                conn.headerFields?.forEach { (key, values) ->
                    if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                        values.forEach { raw ->
                            val kv = raw.substringBefore(';').trim()
                            val idx = kv.indexOf('=')
                            if (idx > 0) {
                                cookies[kv.substring(0, idx).trim()] = kv.substring(idx + 1).trim()
                            }
                        }
                    }
                }
                lastHost = conn.url.host
                return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            } finally {
                conn.disconnect()
            }
        }
    }

    private data class FileEntry(
        val id: String,
        val name: String,
        val size: String
    )
}
