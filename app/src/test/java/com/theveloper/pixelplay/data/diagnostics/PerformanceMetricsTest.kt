package com.theveloper.pixelplay.data.diagnostics

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PerformanceMetricsTest {

    @BeforeEach
    fun reset() {
        PerformanceMetrics.resetForTest()
    }

    @Test
    fun timingStat_tracksCountMinMaxAvgLast() {
        PerformanceMetrics.recordTiming(PerformanceMetrics.Timings.METADATA_READ, 10)
        PerformanceMetrics.recordTiming(PerformanceMetrics.Timings.METADATA_READ, 30)
        PerformanceMetrics.recordTiming(PerformanceMetrics.Timings.METADATA_READ, 20)

        val snap = PerformanceMetrics.snapshot().timings[PerformanceMetrics.Timings.METADATA_READ]!!
        assertThat(snap.count).isEqualTo(3)
        assertThat(snap.minMs).isEqualTo(10.0)
        assertThat(snap.maxMs).isEqualTo(30.0)
        assertThat(snap.avgMs).isEqualTo(20.0)
        assertThat(snap.lastMs).isEqualTo(20.0)
    }

    @Test
    fun negativeTimings_areIgnored() {
        PerformanceMetrics.recordTiming(PerformanceMetrics.Timings.FULL_SCAN, -5)
        assertThat(PerformanceMetrics.snapshot().timings).doesNotContainKey(PerformanceMetrics.Timings.FULL_SCAN)
    }

    @Test
    fun counters_accumulate() {
        PerformanceMetrics.increment(PerformanceMetrics.Counters.ARTWORK_CACHE_HIT)
        PerformanceMetrics.increment(PerformanceMetrics.Counters.ARTWORK_CACHE_HIT)
        PerformanceMetrics.increment(PerformanceMetrics.Counters.SONGS_SCANNED, 50)

        val counters = PerformanceMetrics.snapshot().counters
        assertThat(counters[PerformanceMetrics.Counters.ARTWORK_CACHE_HIT]).isEqualTo(2)
        assertThat(counters[PerformanceMetrics.Counters.SONGS_SCANNED]).isEqualTo(50)
    }

    @Test
    fun recordMax_keepsHighestValue() {
        PerformanceMetrics.recordMax(PerformanceMetrics.Maxes.ARTWORK_BYTES, 1000)
        PerformanceMetrics.recordMax(PerformanceMetrics.Maxes.ARTWORK_BYTES, 5000)
        PerformanceMetrics.recordMax(PerformanceMetrics.Maxes.ARTWORK_BYTES, 3000)

        assertThat(PerformanceMetrics.snapshot().maxes[PerformanceMetrics.Maxes.ARTWORK_BYTES]).isEqualTo(5000)
    }

    @Test
    fun embeddedArtwork_flagsLargeArtwork() {
        PerformanceMetrics.recordEmbeddedArtwork(500_000)
        PerformanceMetrics.recordEmbeddedArtwork(2_000_000)

        val snap = PerformanceMetrics.snapshot()
        assertThat(snap.maxes[PerformanceMetrics.Maxes.ARTWORK_BYTES]).isEqualTo(2_000_000)
        assertThat(snap.counters[PerformanceMetrics.Counters.ARTWORK_LARGE]).isEqualTo(1)
    }

    @Test
    fun playbackFormat_recordsMultichannelObservation() {
        PerformanceMetrics.recordPlaybackFormat(channelCount = 6, sampleRate = 96_000, pcmEncoding = 21)

        val snap = PerformanceMetrics.snapshot()
        assertThat(snap.maxes[PerformanceMetrics.Maxes.PLAYBACK_CHANNEL_COUNT]).isEqualTo(6)
        assertThat(snap.maxes[PerformanceMetrics.Maxes.PLAYBACK_SAMPLE_RATE]).isEqualTo(96_000)
        assertThat(snap.counters[PerformanceMetrics.Counters.MULTICHANNEL_PLAYBACKS]).isEqualTo(1)
    }

    @Test
    fun stereoPlayback_doesNotCountAsMultichannel() {
        PerformanceMetrics.recordPlaybackFormat(channelCount = 2, sampleRate = 44_100, pcmEncoding = 2)
        assertThat(PerformanceMetrics.snapshot().counters[PerformanceMetrics.Counters.MULTICHANNEL_PLAYBACKS]).isNull()
    }

    @Test
    fun offloadEvents_areBoundedToTwenty() {
        repeat(25) { PerformanceMetrics.recordOffloadFallback("reason $it", it.toLong()) }

        val snap = PerformanceMetrics.snapshot()
        assertThat(snap.offloadEvents).hasSize(20)
        // Oldest events dropped; newest retained.
        assertThat(snap.offloadEvents.last().reason).isEqualTo("reason 24")
        assertThat(snap.counters[PerformanceMetrics.Counters.OFFLOAD_FALLBACKS]).isEqualTo(25)
    }

    @Test
    fun concurrentRecording_losesNoUpdatesUnderScanLikeContention() {
        // Simulates the worst realistic contention: several scan worker threads recording
        // the same timing/counter/max metrics simultaneously. Asserts exact totals (no lost
        // updates) and, implicitly, no deadlock or exception from the synchronized/atomic paths.
        val threads = 8
        val perThread = 5_000
        val pool = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threads)
        repeat(threads) {
            pool.submit {
                startGate.await()
                try {
                    repeat(perThread) { i ->
                        PerformanceMetrics.recordTiming(PerformanceMetrics.Timings.METADATA_READ, 10)
                        PerformanceMetrics.increment(PerformanceMetrics.Counters.SONGS_SCANNED)
                        PerformanceMetrics.recordMax(PerformanceMetrics.Maxes.ARTWORK_BYTES, i.toLong())
                    }
                } finally {
                    doneGate.countDown()
                }
            }
        }
        startGate.countDown()
        assertThat(doneGate.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()

        val total = (threads * perThread).toLong()
        val snap = PerformanceMetrics.snapshot()
        assertThat(snap.counters[PerformanceMetrics.Counters.SONGS_SCANNED]).isEqualTo(total)
        assertThat(snap.timings[PerformanceMetrics.Timings.METADATA_READ]!!.count).isEqualTo(total)
        assertThat(snap.maxes[PerformanceMetrics.Maxes.ARTWORK_BYTES]).isEqualTo((perThread - 1).toLong())
    }

    @Test
    fun controllers_areDeduplicatedByPackage() {
        PerformanceMetrics.recordControllerConnected("com.google.android.projection.gearhead", true, false, 100)
        PerformanceMetrics.recordControllerConnected("com.google.android.projection.gearhead", true, false, 200)

        val controllers = PerformanceMetrics.snapshot().controllers
        assertThat(controllers).hasSize(1)
        assertThat(controllers.first().isAndroidAuto).isTrue()
    }
}
