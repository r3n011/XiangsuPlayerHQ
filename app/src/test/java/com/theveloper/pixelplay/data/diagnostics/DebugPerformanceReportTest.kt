package com.theveloper.pixelplay.data.diagnostics

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DebugPerformanceReportTest {

    private fun sampleReport(): DebugPerformanceReport = DebugPerformanceReport(
        generatedAtIso = "2026-06-03T12:00:00Z",
        device = DeviceSection(
            manufacturer = "Google",
            model = "Pixel 8",
            brand = "google",
            device = "shiba",
            androidVersion = "15",
            sdkInt = 35,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            memoryClassMb = 256,
            isLowRamDevice = false,
            totalRamBytes = 8_000_000_000,
            availableRamBytes = 3_000_000_000
        ),
        app = AppSection(
            versionName = "1.2.3",
            versionCode = 42,
            buildType = "debug",
            applicationId = "com.theveloper.pixelplay.debug"
        ),
        library = LibrarySection(
            totalSongs = 100,
            localSongs = 90,
            cloudSongs = 10,
            mimeCounts = mapOf("audio/flac" to 60, "audio/mpeg" to 40),
            maxBitrate = 1_411_000,
            minSampleRate = 44_100,
            maxSampleRate = 192_000,
            maxObservedChannels = 6,
            maxObservedPcmEncoding = 21,
            estFileSizeMinBytes = 3_000_000,
            estFileSizeAvgBytes = 25_000_000.0,
            estFileSizeMaxBytes = 80_000_000
        ),
        hiRes = HiResSection(
            hiResCount = 30,
            ultraHiResCount = 10,
            losslessCodecCount = 60,
            likelyExpensiveToDecodeCount = 65,
            multichannelPlaybackObservations = 2,
            largeArtworkObservations = 5
        ),
        artwork = ArtworkSection(
            embeddedArtSeen = true,
            maxEmbeddedBytes = 12_000_000,
            maxDecodedWidth = 1024,
            maxDecodedHeight = 1024,
            cacheHits = 80,
            cacheMisses = 20,
            freshExtractions = 20,
            extractionTiming = ReportTiming(20, 5.0, 45.0, 220.0, 30.0),
            decodeTiming = ReportTiming(10, 8.0, 40.0, 150.0, 12.0)
        ),
        playback = PlaybackSection(
            currentMime = "audio/flac",
            sampleRate = 192_000,
            bitrate = 4_500_000,
            channelCount = 2,
            pcmEncoding = 21,
            decoderName = "c2.android.flac.decoder",
            decoderHardware = false,
            audioOffloadEnabled = false,
            offloadFallbackCount = 1,
            hiFiModeEnabled = true,
            crossfadeEnabled = true,
            crossfadeDurationMs = 2000,
            equalizerEnabled = false,
            replayGainEnabled = true,
            replayGainAlbumMode = false
        ),
        controllers = ControllerSection(
            widgetActive = true,
            wearActive = false,
            androidAutoActive = true,
            connectedControllers = listOf(
                ConnectedController("com.google.android.projection.gearhead", isAndroidAuto = true, isWear = false)
            )
        ),
        timings = mapOf(
            PerformanceMetrics.Timings.FULL_SCAN to ReportTiming(1, 4200.0, 4200.0, 4200.0, 4200.0),
            PerformanceMetrics.Timings.PLAYBACK_PREPARE to ReportTiming(3, 100.0, 250.0, 600.0, 250.0)
        ),
        offloadEvents = listOf(
            OffloadEventEntry(123456, "HAL offload reset detected: STATE_BUFFERING after 200ms of playback")
        ),
        notes = listOf("Hi-res = sample rate > 48 kHz.")
    )

    @Test
    fun json_roundTripsBackToEqualReport() {
        val report = sampleReport()
        val json = report.toJson()
        val decoded = Json.decodeFromString(DebugPerformanceReport.serializer(), json)
        assertThat(decoded).isEqualTo(report)
    }

    @Test
    fun json_includesSchemaVersionAndKeySections() {
        val json = sampleReport().toJson()
        assertThat(json).contains("\"schemaVersion\": ${DebugPerformanceReport.SCHEMA_VERSION}")
        assertThat(json).contains("\"hiRes\"")
        assertThat(json).contains("\"offloadEvents\"")
    }

    @Test
    fun plainText_containsEverySection() {
        val text = sampleReport().toPlainText()
        listOf(
            "== DEVICE ==",
            "== APP ==",
            "== LIBRARY ==",
            "== HI-RES CLASSIFICATION ==",
            "== ARTWORK ==",
            "== PLAYBACK ==",
            "== CONTROLLERS ==",
            "== TIMINGS (ms) ==",
            "== OFFLOAD EVENTS ==",
            "== NOTES =="
        ).forEach { assertThat(text).contains(it) }
    }

    @Test
    fun plainText_surfacesHiResAndOffloadSignals() {
        val text = sampleReport().toPlainText()
        assertThat(text).contains("Hi-res files (>48 kHz): 30")
        assertThat(text).contains("Ultra-hi-res files (>=176.4 kHz): 10")
        assertThat(text).contains("Audio offload enabled: false")
        assertThat(text).contains("HAL offload reset detected")
    }

    @Test
    fun plainText_doesNotLeakPathsByDesign() {
        // The model has no path/title/artist fields at all; assert nothing path-like leaks.
        val text = sampleReport().toPlainText()
        assertThat(text).doesNotContain("/storage/")
        assertThat(text).doesNotContain("/data/")
    }

    @Test
    fun bytes_formatIsLocaleIndependent() {
        assertThat(DebugPerformanceReport.bytes(512)).isEqualTo("512 B")
        assertThat(DebugPerformanceReport.bytes(1_500)).isEqualTo("1.5 KB")
        assertThat(DebugPerformanceReport.bytes(12_000_000)).isEqualTo("12.00 MB")
        assertThat(DebugPerformanceReport.bytes(8_000_000_000)).isEqualTo("8.00 GB")
    }

    @Test
    fun pcmEncodingLabel_mapsKnownEncodings() {
        assertThat(DebugPerformanceReport.pcmEncodingLabel(2)).isEqualTo("16-bit")
        assertThat(DebugPerformanceReport.pcmEncodingLabel(21)).isEqualTo("24-bit")
        assertThat(DebugPerformanceReport.pcmEncodingLabel(4)).isEqualTo("32-bit float")
        assertThat(DebugPerformanceReport.pcmEncodingLabel(999)).contains("999")
    }

    @Test
    fun advancedDiagnosticsTimeline_roundTripsAndRendersWhenPresent() {
        val report = sampleReport().copy(
            advancedDiagnostics = AdvancedDiagnosticsSection(
                enabled = true,
                sessionStartedIso = "2026-06-03T11:00:00Z",
                expiresAtIso = "2026-06-04T11:00:00Z",
                eventCount = 1,
                droppedEventCount = 0,
                events = listOf(
                    AdvancedDiagnosticEventEntry(
                        elapsedRealtimeMs = 123_456L,
                        type = AdvancedPerformanceDiagnostics.EventTypes.USER_MARK,
                        name = "lag_mark",
                        details = mapOf("note" to "Player UI stuttered")
                    )
                )
            )
        )

        val decoded = Json.decodeFromString(DebugPerformanceReport.serializer(), report.toJson())
        val text = report.toPlainText()

        assertThat(decoded).isEqualTo(report)
        assertThat(text).contains("== ADVANCED DIAGNOSTICS ==")
        assertThat(text).contains("lag_mark")
        assertThat(text).contains("Player UI stuttered")
    }
}
