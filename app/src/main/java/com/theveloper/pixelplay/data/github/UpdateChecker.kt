package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    val browser_download_url: String,
    val content_type: String? = null,
    val size: Long = 0
)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val published_at: String,
    val html_url: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Singleton
class UpdateChecker @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val lanzouApi = LanzouCloudApi()

    companion object {
        const val GITHUB_REPO_OWNER = "r3n011"
        const val GITHUB_REPO_NAME = "XiangsuPlayerHQ"
        
        // 蓝奏云配置（域名与 example.py 保持一致：wwbvc.lanzouv.com）
        const val LANZOU_SHARE_URL = "https://wwbvc.lanzouv.com/b011m9azlg"
        const val LANZOU_PASSWORD = "dtu2"
    }

    suspend fun checkForUpdates(): Result<UpdateInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
                val githubToken = GitHubToken.value
                if (githubToken.isNotBlank()) {
                    connection.addRequestProperty("Authorization", "token $githubToken")
                    Timber.d("Using GitHub PAT for authenticated release check")
                }
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = json.decodeFromString<GitHubRelease>(response)
                    Timber.d("Fetched latest release: ${release.tag_name} published at ${release.published_at}, assets: ${release.assets.size}")

                    val publishedAt = parseDateTime(release.published_at)

                    // 解析 APK 下载链接：收集全部 APK 资产并按架构名归类（arm64 / x86 / arm32）
                    val apkAssets = release.assets.filter { it.name.lowercase().endsWith(".apk") }
                    val allApkUrls = apkAssets.map { it.browser_download_url }
                    val apkUrlsByAbi = apkAssets.mapNotNull { asset ->
                        val lower = asset.name.lowercase()
                        val key = when {
                            lower.contains("arm64") -> "arm64"
                            lower.contains("x86_64") || lower.contains("-x86") || lower.contains("x86.") -> "x86"
                            lower.contains("armeabi") || lower.contains("arm32") || lower.contains("-arm.") -> "arm"
                            else -> null
                        }
                        key?.let { it to asset.browser_download_url }
                    }.toMap()

                    Result.success(
                        UpdateInfo(
                            version = release.tag_name,
                            publishedAt = publishedAt,
                            releaseUrl = release.html_url,
                            releaseName = release.name ?: release.tag_name,
                            releaseNotes = release.body ?: "",
                            apkUrl = apkAssets.firstOrNull()?.browser_download_url,
                            allApkUrls = allApkUrls,
                            apkUrlsByAbi = apkUrlsByAbi
                        )
                    )
                } else {
                    val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.e("Failed to fetch release info: $responseCode - $errorMessage")
                    Result.failure(Exception("Failed to fetch release info: $responseCode"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception checking for updates")
                Result.failure(e)
            }
        }
    }

    /**
     * 拉取该仓库的所有 GitHub Release（按最新在前排列），用于「更新日志」窗口展示全部历史版本。
     * @param pageSize 一次请求返回的条数上限（GitHub 单页上限 100）
     */
    suspend fun fetchReleases(pageSize: Int = 100): Result<List<GitHubRelease>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases?per_page=$pageSize"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
                val githubToken = GitHubToken.value
                if (githubToken.isNotBlank()) {
                    connection.addRequestProperty("Authorization", "token $githubToken")
                }
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val releases = json.decodeFromString<List<GitHubRelease>>(response)
                    Timber.d("Fetched ${releases.size} releases for changelog")
                    Result.success(releases)
                } else {
                    val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.e("Failed to fetch releases: $responseCode - $errorMessage")
                    Result.failure(Exception("Failed to fetch releases: $responseCode"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching releases")
                Result.failure(e)
            }
        }
    }

    /**
     * 解析 GitHub API 返回的 ISO 8601 时间字符串（如 "2026-07-20T12:34:56Z"）。
     * 解析失败返回 0L（而非当前时间），避免误报"有更新"。
     */
    private fun parseDateTime(dateTimeString: String): Long {
        return try {
            Instant.parse(dateTimeString.trim()).toEpochMilli()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse date: $dateTimeString")
            0L
        }
    }

    data class UpdateInfo(
        val version: String,
        val publishedAt: Long,
        val releaseUrl: String,
        val releaseName: String,
        val releaseNotes: String,
        val apkUrl: String? = null,
        val allApkUrls: List<String> = emptyList(),
        val apkUrlsByAbi: Map<String, String> = emptyMap(),  // "arm64" / "x86" / "arm" → 下载 URL
        val lanzouFiles: List<LanzouCloudApi.LanzouFileInfo> = emptyList(),
        val isLanzouSynced: Boolean = false  // 蓝奏云版本号是否与 GitHub 一致
    ) {
        /** 设备 ABI → 资产架构键 */
        private fun abiToKey(abi: String): String? = when {
            abi == "arm64-v8a" -> "arm64"
            abi == "x86_64" || abi == "x86" -> "x86"
            abi.startsWith("armeabi") -> "arm"
            else -> null
        }

        /** 按设备 ABI 优先级返回推荐架构键；无匹配资产时返回第一个可用架构 */
        fun preferredArchKey(deviceAbis: List<String>): String? {
            for (abi in deviceAbis) {
                val key = abiToKey(abi) ?: continue
                if (apkUrlsByAbi.containsKey(key)) return key
            }
            return availableArchKeys().firstOrNull()
        }

        /** 可选的架构键列表（按优先级排序，供 UI 展示 64/32 位选择） */
        fun availableArchKeys(): List<String> =
            listOf("arm64", "x86", "arm").filter { apkUrlsByAbi.containsKey(it) }

        /**
         * 判断是否有更新（主判断：版本号比较）。
         *
         * 优先解析 tag_name 与本地 versionName 进行语义化版本比较；
         * 若版本号无法解析，则回退到时间戳比较（publishedAt > lastUpdateTime）。
         *
         * @param currentVersionName 本地应用的 versionName（如 "1.1.0.4"）
         * @param lastUpdateTime 本地 APK 的最后更新时间戳（版本号解析失败时的兜底判断）
         */
        fun hasUpdate(currentVersionName: String, lastUpdateTime: Long = 0L): Boolean {
            val remoteVersion = parseVersionNumber(version)
            val localVersion = parseVersionNumber(currentVersionName)

            // 双方版本号都能解析 → 用版本号比较
            if (remoteVersion != null && localVersion != null) {
                return compareVersions(remoteVersion, localVersion) > 0
            }

            // 版本号无法解析 → 回退到时间戳比较（publishedAt <= 0 时直接返回 false）
            if (publishedAt <= 0L) return false
            return publishedAt > lastUpdateTime
        }

        /**
         * 按设备 ABI 匹配优先下载链接；无匹配时回退到第一个 APK。
         * 避免 x86 设备下错 arm64 分包。
         */
        fun preferredApkUrl(deviceAbis: List<String>): String? {
            preferredArchKey(deviceAbis)?.let { key ->
                apkUrlsByAbi[key]?.let { return it }
            }
            return apkUrl
        }

        /**
         * 获取可用的 APK 下载链接列表（仅对比版本号，不区分架构）
         * 优先使用蓝奏云（如果已同步），否则使用 GitHub
         */
        fun availableApkUrls(): List<String> {
            val urls = mutableListOf<String>()
            if (isLanzouSynced && lanzouFiles.isNotEmpty()) {
                lanzouFiles.forEach { urls.add(it.downloadUrl) }
            }
            allApkUrls.forEach { urls.add(it) }
            if (apkUrl != null && apkUrl !in urls) urls.add(apkUrl)
            return urls
        }
    }

    /**
     * 同步蓝奏云版本信息
     * 检查蓝奏云中的版本是否与 GitHub 一致
     *
     * 蓝奏云存在偶发的反爬校验/网络抖动，导致解析失败或返回空列表，
     * 因此最多重试 5 次（失败或空列表时递增延迟后重试），仍失败则视为未同步。
     */
    suspend fun syncLanzouVersions(updateInfo: UpdateInfo): UpdateInfo {
        return withContext(Dispatchers.IO) {
            var lastError: Throwable? = null

            // 最多尝试 5 次，失败或返回空列表时重试
            for (attempt in 1..5) {
                val outcome = try {
                    lanzouApi.resolveShare(LANZOU_SHARE_URL, LANZOU_PASSWORD).fold(
                        onSuccess = { files ->
                            if (files.isEmpty()) {
                                Timber.w("蓝奏云中没有找到文件（第 $attempt 次尝试）")
                                null
                            } else {
                                // 版本一致性：蓝奏云文件版本号解析失败视为兼容；
                                // 解析成功时只要 蓝奏云版本 ≥ GitHub 版本 即视为已同步
                                // （允许蓝奏云提前发布，避免因版本号格式/位数差异误判为不一致）
                                val githubVersion = updateInfo.version.removePrefix("v")
                                val githubNum = parseVersionNumber(githubVersion)
                                val allSynced = files.all { file ->
                                    val fv = file.versionName
                                    if (fv == null) true
                                    else {
                                        val fNum = parseVersionNumber(fv)
                                        if (fNum == null || githubNum == null) {
                                            fv == updateInfo.version.removePrefix("v")
                                        } else {
                                            compareVersions(fNum, githubNum) >= 0
                                        }
                                    }
                                }

                                if (allSynced) {
                                    Timber.d("蓝奏云版本已同步，找到 ${files.size} 个文件")
                                    updateInfo.copy(
                                        lanzouFiles = files,
                                        isLanzouSynced = true
                                    )
                                } else {
                                    Timber.w("蓝奏云版本与 GitHub 不一致，不使用蓝奏云更新")
                                    val mismatched = files.filter { file ->
                                        file.versionName != null && file.versionName != githubVersion
                                    }
                                    Timber.w("不匹配的文件: ${mismatched.map { it.fileName }}")
                                    updateInfo.copy(
                                        lanzouFiles = files,
                                        isLanzouSynced = false
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            lastError = error
                            Timber.w(error, "无法访问蓝奏云（第 $attempt 次尝试）")
                            null
                        }
                    )
                } catch (e: Exception) {
                    lastError = e
                    Timber.w(e, "同步蓝奏云版本时出错（第 $attempt 次尝试）")
                    null
                }

                // 本次尝试已得到有效结果（无论是否同步），直接返回
                if (outcome != null) return@withContext outcome

                // 未成功 → 递增延迟后重试（第 N 次尝试后延迟 N 秒）
                if (attempt < 5) {
                    delay(attempt * 1000L)
                }
            }

            Timber.e(lastError, "蓝奏云同步重试 5 次后仍失败")
            updateInfo.copy(isLanzouSynced = false)
        }
    }
}

// ─── 版本号工具（文件级，UpdateChecker 与 UpdateInfo 共用） ───────────────────

/**
 * 从 tag_name 或 versionName 中提取纯数字版本号。
 * 支持 "v1.2.3"、"1.2.3"、"v1.1.0.4" 等格式。
 * @return 版本号各段列表（如 [1, 2, 3]），无法解析时返回 null
 */
private fun parseVersionNumber(raw: String): List<Int>? {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V")
    val parts = cleaned.split(".")
    if (parts.isEmpty()) return null
    val numbers = mutableListOf<Int>()
    for (part in parts) {
        val n = part.toIntOrNull() ?: return null
        numbers.add(n)
    }
    return numbers
}

/**
 * 语义化版本比较。逐段比较数字，短数组用 0 补齐。
 * @return 正数表示 a 更新，负数表示 b 更新，0 表示相同
 */
private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until maxLen) {
        val va = a.getOrElse(i) { 0 }
        val vb = b.getOrElse(i) { 0 }
        if (va != vb) return va - vb
    }
    return 0
}
