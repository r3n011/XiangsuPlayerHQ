package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AlbumArtScanPolicyTest {

    @Test
    fun `library scan defers unknown embedded artwork to lazy local artwork uri`() {
        val uri = resolveAlbumArtUriForLibraryScan(
            songId = 42L,
            hasCachedArtwork = false,
            hasNoArtworkMarker = false,
            forceRefresh = false
        )

        assertThat(uri).isEqualTo(LocalArtworkUri.buildSongUri(42L))
    }

    @Test
    fun `library scan respects existing no artwork marker`() {
        val uri = resolveAlbumArtUriForLibraryScan(
            songId = 42L,
            hasCachedArtwork = false,
            hasNoArtworkMarker = true,
            forceRefresh = false
        )

        assertThat(uri).isNull()
    }

    @Test
    fun `library scan force refresh retries artwork lazily despite no artwork marker`() {
        val uri = resolveAlbumArtUriForLibraryScan(
            songId = 42L,
            hasCachedArtwork = false,
            hasNoArtworkMarker = true,
            forceRefresh = true
        )

        assertThat(uri).isEqualTo(LocalArtworkUri.buildSongUri(42L))
    }
}
