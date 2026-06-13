package com.theveloper.pixelplay.data.service.cast

import com.google.android.gms.cast.MediaStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastRemotePlaybackStateTest {
    @Test
    fun `buffering keeps playback active`() {
        val projection = CastRemotePlaybackState.project(
            playerState = MediaStatus.PLAYER_STATE_BUFFERING,
            idleReason = MediaStatus.IDLE_REASON_NONE,
            previousPlayIntent = false
        )

        assertTrue(projection.isPlaying)
        assertTrue(projection.playWhenReady)
        assertTrue(projection.isBuffering)
    }

    @Test
    fun `recoverable error preserves active playback intent`() {
        val projection = CastRemotePlaybackState.project(
            playerState = MediaStatus.PLAYER_STATE_IDLE,
            idleReason = MediaStatus.IDLE_REASON_ERROR,
            previousPlayIntent = true
        )

        assertTrue(projection.isPlaying)
        assertTrue(projection.playWhenReady)
        assertTrue(projection.isBuffering)
    }

    @Test
    fun `paused clears playback intent`() {
        val projection = CastRemotePlaybackState.project(
            playerState = MediaStatus.PLAYER_STATE_PAUSED,
            idleReason = MediaStatus.IDLE_REASON_NONE,
            previousPlayIntent = true
        )

        assertFalse(projection.isPlaying)
        assertFalse(projection.playWhenReady)
        assertFalse(projection.isBuffering)
    }

    @Test
    fun `finished idle clears playback intent`() {
        val projection = CastRemotePlaybackState.project(
            playerState = MediaStatus.PLAYER_STATE_IDLE,
            idleReason = MediaStatus.IDLE_REASON_FINISHED,
            previousPlayIntent = true
        )

        assertFalse(projection.isPlaying)
        assertFalse(projection.playWhenReady)
        assertFalse(projection.isBuffering)
    }
}
