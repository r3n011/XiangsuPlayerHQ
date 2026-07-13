package com.theveloper.pixelplay.data.backup.format

import com.google.gson.Gson
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupModuleInfo
import com.theveloper.pixelplay.data.backup.model.DeviceInfo
import com.theveloper.pixelplay.data.backup.model.PlaybackHistoryBackupEntry
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalBackupAdapter @Inject constructor() {

    fun adapt(externalJson: String, gson: Gson): Pair<BackupManifest, Map<String, String>> {
        val externalBackup = gson.fromJson(externalJson, ExternalBackup::class.java)

        val historyEntries = externalBackup.data.playHistory.map { externalEntry ->
            PlaybackHistoryBackupEntry(
                songId = normalizeSongId(externalEntry.songId),
                timestamp = parseTimestamp(externalEntry.playedAt),
                durationMs = (externalEntry.playedDuration * 1000).toLong()
            )
        }

        val historyJson = gson.toJson(historyEntries)
        val historyModuleInfo = buildModuleInfo(historyJson)

        val modules = mutableMapOf<String, String>()
        val modulesInfo = mutableMapOf<String, BackupModuleInfo>()

        if (historyEntries.isNotEmpty()) {
            modules["playback_history"] = historyJson
            modulesInfo["playback_history"] = historyModuleInfo
        }

        val createdAt = parseTimestamp(externalBackup.createdAt)

        val manifest = BackupManifest(
            schemaVersion = 1,
            appVersion = externalBackup.appVersion,
            appVersionCode = 0,
            createdAt = createdAt,
            deviceInfo = DeviceInfo(
                manufacturer = "",
                model = externalBackup.deviceInfo.model,
                androidVersion = parseSystemVersion(externalBackup.deviceInfo.systemVersion)
            ),
            modules = modulesInfo
        )

        return manifest to modules
    }

    private fun normalizeSongId(songId: String): String {
        if (songId.startsWith("netease_")) {
            return songId
        }
        if (songId.startsWith("qqmusic_") || songId.startsWith("qq_")) {
            return songId
        }
        if (songId.startsWith("wy_")) {
            return "netease_" + songId.removePrefix("wy_")
        }
        if (songId.matches(Regex("\\d+"))) {
            return "netease_" + songId
        }
        return songId
    }

    private fun parseTimestamp(timestampStr: String): Long {
        return try {
            if (timestampStr.contains("Z")) {
                Instant.parse(timestampStr).toEpochMilli()
            } else {
                val withZ = timestampStr + "Z"
                Instant.parse(withZ).toEpochMilli()
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseSystemVersion(versionStr: String): Int {
        return try {
            versionStr.toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun buildModuleInfo(json: String): BackupModuleInfo {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return BackupModuleInfo(
            checksum = "sha256:" + sha256(bytes),
            entryCount = countEntries(json),
            sizeBytes = bytes.size.toLong()
        )
    }

    private fun countEntries(json: String): Int {
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                val array = Gson().fromJson(trimmed, com.google.gson.JsonArray::class.java)
                array.size()
            } else {
                1
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
