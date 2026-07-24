package com.theveloper.pixelplay.data.github

import android.os.Build
import com.theveloper.pixelplay.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
private data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Long,
    @SerialName("content_type")
    val contentType: String = "application/vnd.android.package-archive"
)

@Singleton
class UpdateChecker @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val GITHUB_REPO_OWNER = "r3n011"
        const val GITHUB_REPO_NAME = "XiangsuPlayerHQ"
    }

    data class AssetInfo(
        val id: Long,
        val name: String,
        val downloadUrl: String,
        val size: Long,
        val abi: SupportedAbi
    )

    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val publishedAt: Long,
        val releaseUrl: String,
        val releaseName: String,
        val releaseNotes: String,
        val assets: List<AssetInfo>,
        val recommendedAsset: AssetInfo?
    )

    suspend fun checkForUpdates(): Result<UpdateInfo> {
        return try {
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
                Timber.d("Fetched latest release: ${release.tagName} published at ${release.publishedAt}")

                val latestVersionCode = parseVersionCode(release.tagName)
                val assetInfos = release.assets.mapNotNull { asset ->
                    SupportedAbi.fromAssetName(asset.name)?.let { abi ->
                        AssetInfo(
                            id = asset.id,
                            name = asset.name,
                            downloadUrl = asset.browserDownloadUrl,
                            size = asset.size,
                            abi = abi
                        )
                    }
                }

                val recommended = pickRecommendedAsset(assetInfos)

                Result.success(
                    UpdateInfo(
                        version = release.tagName,
                        versionCode = latestVersionCode,
                        publishedAt = parseDateTime(release.publishedAt),
                        releaseUrl = release.htmlUrl,
                        releaseName = release.name ?: release.tagName,
                        releaseNotes = release.body ?: "",
                        assets = assetInfos,
                        recommendedAsset = recommended
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

    /**
     * Returns true when the latest GitHub release is newer than the currently
     * installed build. Falls back to install-time comparison when version code
     * parsing fails.
     */
    fun isUpdateAvailable(updateInfo: UpdateInfo, installedVersionCode: Int = BuildConfig.VERSION_CODE): Boolean {
        return if (updateInfo.versionCode > 0 && installedVersionCode > 0) {
            updateInfo.versionCode > installedVersionCode
        } else {
            updateInfo.publishedAt > System.currentTimeMillis()
        }
    }

    /**
     * Extracts a version code from a GitHub release tag.
     *
     * Supported formats:
     * - `v1.1.0-20` -> 20
     * - `v20` -> 20
     * - `1.1.0-20` -> 20
     *
     * Falls back to `0` if no version code can be parsed.
     */
    fun parseVersionCode(tagName: String): Int {
        val trimmed = tagName.removePrefix("v")
        val suffixDash = trimmed.substringAfterLast("-", "")
        val suffixCode = suffixDash.toIntOrNull()
        if (suffixCode != null) return suffixCode

        val numericOnly = trimmed.filter { it.isDigit() }
        return numericOnly.toIntOrNull() ?: 0
    }

    private fun pickRecommendedAsset(assets: List<AssetInfo>): AssetInfo? {
        if (assets.isEmpty()) return null
        val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        for (deviceAbi in supportedAbis) {
            val match = assets.find { asset ->
                when {
                    deviceAbi.equals("arm64-v8a", ignoreCase = true) && asset.abi == SupportedAbi.ARM64 -> true
                    deviceAbi.equals("armeabi-v7a", ignoreCase = true) && asset.abi == SupportedAbi.ARM32 -> true
                    else -> false
                }
            }
            if (match != null) return match
        }
        return assets.first()
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
}
