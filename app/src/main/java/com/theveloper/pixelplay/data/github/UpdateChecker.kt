package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
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

    private companion object {
        const val GITHUB_REPO_OWNER = "r3n011"
        const val GITHUB_REPO_NAME = "XiangsuPlayerHQ"
    }

    suspend fun checkForUpdates(): Result<UpdateInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.addRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = json.decodeFromString<GitHubRelease>(response)
                    Timber.d("Fetched latest release: ${release.tag_name} published at ${release.published_at}, assets: ${release.assets.size}")

                    val publishedAt = parseDateTime(release.published_at)

                    // 解析 APK 下载链接：匹配 arm64-v8a (64位) 和 armeabi-v7a (32位)
                    var apkArm64: String? = null
                    var apkArmv7: String? = null
                    var apkUniversal: String? = null

                    release.assets.forEach { asset ->
                        val lowerName = asset.name.lowercase()
                        when {
                            lowerName.endsWith(".apk") && lowerName.contains("arm64") -> {
                                apkArm64 = asset.browser_download_url
                            }
                            lowerName.endsWith(".apk") && (lowerName.contains("armv7") || lowerName.contains("armeabi")) -> {
                                apkArmv7 = asset.browser_download_url
                            }
                            lowerName.endsWith(".apk") && lowerName.contains("universal") -> {
                                apkUniversal = asset.browser_download_url
                            }
                            lowerName.endsWith(".apk") && apkUniversal == null && apkArm64 == null && apkArmv7 == null -> {
                                // 兜底：如果没有架构标识，取第一个 APK
                                apkUniversal = asset.browser_download_url
                            }
                        }
                    }

                    Result.success(
                        UpdateInfo(
                            version = release.tag_name,
                            publishedAt = publishedAt,
                            releaseUrl = release.html_url,
                            releaseName = release.name ?: release.tag_name,
                            releaseNotes = release.body ?: "",
                            apkUrlArm64 = apkArm64,
                            apkUrlArmv7 = apkArmv7,
                            apkUrlUniversal = apkUniversal
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

    private fun parseDateTime(dateTimeString: String): Long {
        return try {
            val trimmed = dateTimeString.trim()
            if (trimmed.endsWith("Z")) {
                val withoutZ = trimmed.substring(0, trimmed.length - 1)
                val parts = withoutZ.split("T")
                if (parts.size == 2) {
                    val dateParts = parts[0].split("-")
                    val timeParts = parts[1].split(":")
                    if (dateParts.size == 3 && timeParts.size >= 2) {
                        val year = dateParts[0].toInt()
                        val month = dateParts[1].toInt() - 1
                        val day = dateParts[2].toInt()
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        val second = if (timeParts.size > 2) {
                            val secPart = timeParts[2]
                            if (secPart.contains(".")) secPart.substringBefore(".").toInt()
                            else secPart.toInt()
                        } else 0

                        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        calendar.set(year, month, day, hour, minute, second)
                        calendar.timeInMillis
                    } else {
                        System.currentTimeMillis()
                    }
                } else {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse date: $dateTimeString")
            System.currentTimeMillis()
        }
    }

    data class UpdateInfo(
        val version: String,
        val publishedAt: Long,
        val releaseUrl: String,
        val releaseName: String,
        val releaseNotes: String,
        val apkUrlArm64: String? = null,
        val apkUrlArmv7: String? = null,
        val apkUrlUniversal: String? = null
    ) {
        /**
         * 根据 publishedAt 判断是否有更新。
         * 如果 GitHub 发布时间晚于本地安装时间，则视为有更新。
         */
        fun hasUpdate(installedTime: Long): Boolean {
            return publishedAt > installedTime
        }

        /**
         * 获取可用的 APK 下载链接映射（架构 -> URL）
         */
        fun availableApks(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            apkUrlArmv7?.let { map["32位 (armv7)"] = it }
            apkUrlArm64?.let { map["64位 (arm64)"] = it }
            apkUrlUniversal?.let { map["通用版 (universal)"] = it }
            return map
        }
    }
}
