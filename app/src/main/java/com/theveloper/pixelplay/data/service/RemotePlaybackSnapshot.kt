package com.theveloper.pixelplay.data.service

import android.net.Uri

/** 远端会话媒体快照（纯数据零 GMS）；由 full 源集的 CastSyncCoordinator 从 Cast 状态投影。 */
data class RemotePlaybackSnapshot(
    val occurrenceId: String,
    val songId: String?,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val isPlaying: Boolean,
    val isActuallyPlaying: Boolean,
    val currentPositionMs: Long,
    val totalDurationMs: Long,
    val repeatMode: Int,
    val isShuffleEnabled: Boolean,
)
