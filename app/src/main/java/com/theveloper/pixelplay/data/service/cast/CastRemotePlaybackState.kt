package com.theveloper.pixelplay.data.service.cast

import com.google.android.gms.cast.MediaStatus

internal data class CastRemotePlaybackProjection(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val isBuffering: Boolean
)

internal object CastRemotePlaybackState {
    fun project(
        mediaStatus: MediaStatus,
        previousPlayIntent: Boolean
    ): CastRemotePlaybackProjection {
        return project(
            playerState = mediaStatus.playerState,
            idleReason = mediaStatus.idleReason,
            previousPlayIntent = previousPlayIntent
        )
    }

    fun project(
        playerState: Int,
        idleReason: Int,
        previousPlayIntent: Boolean
    ): CastRemotePlaybackProjection {
        val isRecoverableError = playerState == MediaStatus.PLAYER_STATE_IDLE &&
            idleReason == MediaStatus.IDLE_REASON_ERROR &&
            previousPlayIntent

        val playWhenReady = when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING,
            MediaStatus.PLAYER_STATE_BUFFERING -> true
            MediaStatus.PLAYER_STATE_PAUSED -> false
            MediaStatus.PLAYER_STATE_IDLE -> isRecoverableError
            else -> previousPlayIntent
        }

        val isPlaying = when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING,
            MediaStatus.PLAYER_STATE_BUFFERING -> true
            MediaStatus.PLAYER_STATE_IDLE -> isRecoverableError
            else -> previousPlayIntent && playerState == MediaStatus.PLAYER_STATE_UNKNOWN
        }

        return CastRemotePlaybackProjection(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            isBuffering = playerState == MediaStatus.PLAYER_STATE_BUFFERING || isRecoverableError
        )
    }
}
