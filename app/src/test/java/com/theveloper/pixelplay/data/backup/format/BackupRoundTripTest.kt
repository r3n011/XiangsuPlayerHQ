package com.theveloper.pixelplay.data.backup.format

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.DeviceInfo
import com.theveloper.pixelplay.data.backup.model.PlaybackHistoryBackupEntry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 真实写出 → 读回的往返测试：覆盖 PXPL（magic + zip）与外部 .pxpbak（JSON）。
 * 这两个格式此前只有格式探测的单元测试，没有验证导出文件能否被导入。
 */
class BackupRoundTripTest {

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val context: Context = mockk()
    private val resolver: ContentResolver = mockk()
    private val uri: Uri = mockk()
    private val output = ByteArrayOutputStream()

    private val writer = BackupWriter(context, gson)
    private val reader = BackupReader(
        context = context,
        gson = gson,
        formatDetector = BackupFormatDetector(),
        legacyAdapter = LegacyPayloadAdapter(),
        externalBackupAdapter = ExternalBackupAdapter()
    )

    private fun stageInMemoryFile() {
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } returns output
        every { resolver.openInputStream(uri) } answers {
            ByteArrayInputStream(output.toByteArray())
        }
    }

    @Test
    fun `pxpl export can be read back with manifest and payloads`() = runTest {
        stageInMemoryFile()

        val payloads = mapOf(
            BackupSection.FAVORITES.key to """[{"songId":"song-1","title":"x"}]""",
            BackupSection.PLAYLISTS.key to """[{"playlistId":1,"songId":"song-1","position":0}]"""
        )
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            appVersion = "1.2.0",
            appVersionCode = 12,
            createdAt = 1_700_000_000_000,
            deviceInfo = DeviceInfo(manufacturer = "Google", model = "Pixel", androidVersion = 34)
        )

        writer.write(uri, manifest, payloads).getOrThrow()

        val readManifest = reader.readManifest(uri).getOrThrow()
        assertEquals(BackupManifest.CURRENT_SCHEMA_VERSION, readManifest.schemaVersion)
        assertEquals(payloads.keys, readManifest.modules.keys)
        assertEquals(payloads, reader.readAllModulePayloads(uri).getOrThrow())
    }

    @Test
    fun `pxpbak export can be read back as playback history`() = runTest {
        stageInMemoryFile()

        val history = listOf(
            PlaybackHistoryBackupEntry(
                songId = "netease_123",
                timestamp = 1_700_000_000_000,
                durationMs = 180_000
            )
        )
        val manifest = BackupManifest(
            createdAt = 1_700_000_000_000,
            deviceInfo = DeviceInfo(model = "Pixel 7 Pro", androidVersion = 34)
        )

        writer.writeExternalFormat(
            uri = uri,
            manifest = manifest,
            modulePayloads = mapOf(BackupSection.PLAYBACK_HISTORY.key to gson.toJson(history))
        ).getOrThrow()

        // 回归：playHistory 位于文件头 128 字节之后，必须仍能识别为外部格式
        val readManifest = reader.readManifest(uri).getOrThrow()
        assertTrue(readManifest.modules.containsKey(BackupSection.PLAYBACK_HISTORY.key))

        val payload = reader.readModulePayload(uri, BackupSection.PLAYBACK_HISTORY.key).getOrThrow()
        val entries = gson.fromJson(payload, Array<PlaybackHistoryBackupEntry>::class.java)
        assertEquals(1, entries.size)
        assertEquals("netease_123", entries[0].songId)
        assertEquals(180_000L, entries[0].durationMs)
    }

    @Test
    fun `pxpbak export without playback history fails instead of writing an empty file`() = runTest {
        stageInMemoryFile()

        val result = writer.writeExternalFormat(
            uri = uri,
            manifest = BackupManifest(),
            modulePayloads = mapOf(BackupSection.FAVORITES.key to "[]")
        )

        assertTrue(result.isFailure)
    }
}
