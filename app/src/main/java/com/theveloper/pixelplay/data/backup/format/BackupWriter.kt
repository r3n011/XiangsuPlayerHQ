package com.theveloper.pixelplay.data.backup.format

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupModuleInfo
import com.theveloper.pixelplay.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackupGson private val gson: Gson
) {
    suspend fun writeExternalFormat(
        uri: Uri,
        manifest: BackupManifest,
        modulePayloads: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val playbackHistoryJson = modulePayloads["playback_history"]
            if (playbackHistoryJson != null) {
                val historyEntries = gson.fromJson(playbackHistoryJson,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        com.theveloper.pixelplay.data.backup.model.PlaybackHistoryBackupEntry::class.java
                    ).type
                ) as List<com.theveloper.pixelplay.data.backup.model.PlaybackHistoryBackupEntry>

                val externalEntries = historyEntries.map { entry ->
                    ExternalPlayHistoryEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        playedDuration = entry.durationMs / 1000.0,
                        completed = entry.durationMs > 0,
                        artistName = "",
                        songTitle = "",
                        sourceType = detectSourceType(entry.songId),
                        albumName = "",
                        playedAt = java.time.Instant.ofEpochMilli(entry.timestamp).toString(),
                        duration = 0,
                        songId = entry.songId
                    )
                }

                val externalBackup = ExternalBackup(
                    createdAt = java.time.Instant.ofEpochMilli(manifest.createdAt).toString(),
                    version = "1.0",
                    appVersion = manifest.appVersion,
                    deviceInfo = ExternalDeviceInfo(
                        model = manifest.deviceInfo.model,
                        systemVersion = manifest.deviceInfo.androidVersion.toString()
                    ),
                    data = ExternalBackupData(
                        playHistory = externalEntries
                    )
                )

                val json = gson.toJson(externalBackup)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Unable to open output stream for backup")
            }
        }
    }

    private fun detectSourceType(songId: String): String {
        return when {
            songId.startsWith("netease_") || songId.startsWith("wy_") -> "netease"
            songId.startsWith("qqmusic_") || songId.startsWith("qq_") -> "qqmusic"
            else -> "local"
        }
    }
    suspend fun write(
        uri: Uri,
        manifest: BackupManifest,
        modulePayloads: Map<String, String>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val totalSteps = modulePayloads.size + 1
            var currentStep = 0

            // Compute checksums and module info
            val modulesInfo = mutableMapOf<String, BackupModuleInfo>()
            val payloadBytes = mutableMapOf<String, ByteArray>()

            modulePayloads.forEach { (key, jsonPayload) ->
                val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
                payloadBytes[key] = bytes
                modulesInfo[key] = BackupModuleInfo(
                    checksum = "sha256:${sha256(bytes)}",
                    entryCount = countJsonArrayEntries(jsonPayload),
                    sizeBytes = bytes.size.toLong()
                )
            }

            val finalManifest = manifest.copy(modules = modulesInfo)
            val manifestJson = gson.toJson(finalManifest)

            context.contentResolver.openOutputStream(uri)?.use { rawOutput ->
                // Write PXPL magic bytes first
                rawOutput.write(BackupFormatDetector.PXPL_MAGIC)

                // Write ZIP archive
                ZipOutputStream(rawOutput).use { zip ->
                    // Write manifest
                    zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_FILENAME))
                    zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    onProgress(++currentStep, totalSteps)

                    // Write each module payload
                    payloadBytes.forEach { (key, bytes) ->
                        zip.putNextEntry(ZipEntry("$key.json"))
                        zip.write(bytes)
                        zip.closeEntry()
                        onProgress(++currentStep, totalSteps)
                    }
                }
            } ?: throw IllegalStateException("Unable to open output stream for backup")
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun countJsonArrayEntries(json: String): Int {
        // Quick heuristic: count top-level array elements
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                gson.fromJson(trimmed, com.google.gson.JsonArray::class.java)?.size() ?: 0
            } else {
                1
            }
        } catch (_: Exception) {
            0
        }
    }
}
