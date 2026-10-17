/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Rhythm (https://github.com/chromahub/rhythm-app) — Apple Music Canvas dynamic artwork.
 */

package com.theveloper.pixelplay.data.canvas

data class CanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    /** 动态封面主 URL（通常为 HLS / .m3u8） */
    val animated: String? = null,
    /** 兜底 URL（主地址播放失败时切换） */
    val videoUrl: String? = null,
) {
    val preferredAnimationUrl: String?
        get() = animated ?: videoUrl
}
