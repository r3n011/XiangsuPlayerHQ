package com.theveloper.pixelplay.data.github

import com.theveloper.pixelplay.BuildConfig
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

    private companion object {
        const val GITHUB_REPO_OWNER = "r3n011"
        const val GITHUB_REPO_NAME = "XiangsuPlayerHQ"
        
        // 蓝奏云配置
        const val LANZOU_SHARE_URL = "https://wwbvc.lanzn.com/b011m9azlg"
        const val LANZOU_PASSWORD = "dtu2"
    }

    suspend fun checkForUpdates(): Result<UpdateInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
                if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                    connection.addRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
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

                    // 解析 APK 下载链接：只取第一个 APK 资产（不区分架构）
                    val apkUrl = release.assets
                        .firstOrNull { it.name.lowercase().endsWith(".apk") }
                        ?.browser_download_url

                    Result.success(
                        UpdateInfo(
                            version = release.tag_name,
                            publishedAt = publishedAt,
                            releaseUrl = release.html_url,
                            releaseName = release.name ?: release.tag_name,
                            releaseNotes = release.body ?: "",
                            apkUrl = apkUrl
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
        val lanzouFiles: List<LanzouCloudApi.LanzouFileInfo> = emptyList(),
        val isLanzouSynced: Boolean = false  // 蓝奏云版本号是否与 GitHub 一致
    ) {
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
         * 获取可用的 APK 下载链接列表（仅对比版本号，不区分架构）
         * 优先使用蓝奏云（如果已同步），否则使用 GitHub
         */
        fun availableApkUrls(): List<String> {
            val urls = mutableListOf<String>()
            if (isLanzouSynced && lanzouFiles.isNotEmpty()) {
                lanzouFiles.forEach { urls.add(it.downloadUrl) }
            }
            apkUrl?.let { urls.add(it) }
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
