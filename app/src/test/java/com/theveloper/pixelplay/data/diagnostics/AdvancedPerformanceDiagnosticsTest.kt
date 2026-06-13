package com.theveloper.pixelplay.data.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdvancedPerformanceDiagnosticsTest {

    @BeforeEach
    fun reset() {
        AdvancedPerformanceDiagnostics.resetForTest()
    }

    @Test
    fun recordEvent_whenDisabled_dropsEventAndKeepsNoTimeline() {
        AdvancedPerformanceDiagnostics.recordEvent(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "buffering",
            details = mapOf("state" to "BUFFERING"),
            elapsedRealtimeMs = 100L,
            nowEpochMs = 1_000L
        )

        val snapshot = AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 1_000L)

        assertThat(snapshot.enabled).isFalse()
        assertThat(snapshot.events).isEmpty()
        assertThat(snapshot.droppedEventCount).isEqualTo(0L)
    }

    @Test
    fun recordEventIfEnabled_whenDisabled_doesNotEvaluateDetails() {
        var detailsEvaluated = false

        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.PLAYBACK,
            name = "buffering"
        ) {
            detailsEvaluated = true
            mapOf("state" to "BUFFERING")
        }

        assertThat(detailsEvaluated).isFalse()
        assertThat(AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 1_000L).events).isEmpty()
    }

    @Test
    fun enabledSession_recordsEventsWithSessionBounds() {
        AdvancedPerformanceDiagnostics.startSession(
            startedAtEpochMs = 1_000L,
            durationMs = 60_000L
        )

        AdvancedPerformanceDiagnostics.recordEvent(
            type = AdvancedPerformanceDiagnostics.EventTypes.AUDIO_EFFECT,
            name = "equalizer_attach_start",
            details = mapOf("audioSessionId" to "42"),
            elapsedRealtimeMs = 200L,
            nowEpochMs = 2_000L
        )

        val snapshot = AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 2_000L)

        assertThat(snapshot.enabled).isTrue()
        assertThat(snapshot.sessionStartedEpochMs).isEqualTo(1_000L)
        assertThat(snapshot.expiresAtEpochMs).isEqualTo(61_000L)
        assertThat(snapshot.events).hasSize(1)
        assertThat(snapshot.events.single().type).isEqualTo(AdvancedPerformanceDiagnostics.EventTypes.AUDIO_EFFECT)
        assertThat(snapshot.events.single().details).containsEntry("audioSessionId", "42")
    }

    @Test
    fun eventTimeline_isBoundedAndCountsDroppedEvents() {
        AdvancedPerformanceDiagnostics.startSession(
            startedAtEpochMs = 1_000L,
            durationMs = 60_000L
        )

        repeat(AdvancedPerformanceDiagnostics.MAX_EVENTS + 3) { index ->
            AdvancedPerformanceDiagnostics.recordEvent(
                type = AdvancedPerformanceDiagnostics.EventTypes.WORKER,
                name = "event_$index",
                elapsedRealtimeMs = index.toLong(),
                nowEpochMs = 2_000L
            )
        }

        val snapshot = AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 2_000L)

        assertThat(snapshot.events).hasSize(AdvancedPerformanceDiagnostics.MAX_EVENTS)
        assertThat(snapshot.events.first().name).isEqualTo("event_3")
        assertThat(snapshot.events.last().name).isEqualTo("event_${AdvancedPerformanceDiagnostics.MAX_EVENTS + 2}")
        assertThat(snapshot.droppedEventCount).isEqualTo(3L)
    }

    @Test
    fun expiredSession_disablesAndClearsTimeline() {
        AdvancedPerformanceDiagnostics.startSession(
            startedAtEpochMs = 1_000L,
            durationMs = 1_000L
        )
        AdvancedPerformanceDiagnostics.recordEvent(
            type = AdvancedPerformanceDiagnostics.EventTypes.USER_MARK,
            name = "lag_mark",
            elapsedRealtimeMs = 50L,
            nowEpochMs = 1_500L
        )

        val snapshot = AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 2_000L)

        assertThat(snapshot.enabled).isFalse()
        assertThat(snapshot.events).isEmpty()
        assertThat(snapshot.sessionStartedEpochMs).isNull()
        assertThat(snapshot.expiresAtEpochMs).isNull()
    }

    @Test
    fun markLagNow_recordsUserMarkerOnlyWhenEnabled() {
        AdvancedPerformanceDiagnostics.markLagNow(
            note = "player stuttered",
            elapsedRealtimeMs = 50L,
            nowEpochMs = 1_000L
        )
        assertThat(AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 1_000L).events).isEmpty()

        AdvancedPerformanceDiagnostics.startSession(
            startedAtEpochMs = 1_000L,
            durationMs = 60_000L
        )
        AdvancedPerformanceDiagnostics.markLagNow(
            note = "player stuttered",
            elapsedRealtimeMs = 75L,
            nowEpochMs = 1_500L
        )

        val event = AdvancedPerformanceDiagnostics.snapshot(nowEpochMs = 1_500L).events.single()
        assertThat(event.type).isEqualTo(AdvancedPerformanceDiagnostics.EventTypes.USER_MARK)
        assertThat(event.name).isEqualTo("lag_mark")
        assertThat(event.details).containsEntry("note", "player stuttered")
    }
}
