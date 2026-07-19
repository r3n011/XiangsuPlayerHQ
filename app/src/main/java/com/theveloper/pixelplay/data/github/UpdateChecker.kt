package com.theveloper.pixelplay.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val published_at: String,
    val html_url: String,
    val name: String? = null,
    val body: String? = null
)

@Singleton
class UpdateChecker @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val GITHUB_REPO_OWNER = "r3n011"
        const val GITHUB_REPO_NAME = "XiangsuPlayerHQ"
        const val DOWNLOAD_URL = "https://pixel.grammx.asia/"
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
                    Timber.d("Fetched latest release: ${release.tag_name} published at ${release.published_at}")

                    val publishedAt = parseDateTime(release.published_at)
                    Result.success(
                        UpdateInfo(
                            version = release.tag_name,
                            publishedAt = publishedAt,
                            releaseUrl = release.html_url,
                            downloadUrl = DOWNLOAD_URL,
                            releaseName = release.name ?: release.tag_name,
                            releaseNotes = release.body ?: ""
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
        val downloadUrl: String,
        val releaseName: String,
        val releaseNotes: String
    )
}