package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SheetThemeStateTest {

    private val systemScheme = lightColorScheme(primary = Color(0xFF336699))
    private val activeAlbumScheme = lightColorScheme(primary = Color(0xFFAA2244))
    private val previousAlbumScheme = lightColorScheme(primary = Color(0xFF228855))

    @Test
    fun resolvePlayerSheetTargetScheme_withoutAlbumArt_usesSystemScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = false,
            currentSongActiveScheme = null,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertSame(systemScheme, resolved)
    }

    @Test
    fun resolvePlayerSheetTargetScheme_withPendingAlbumPalette_reusesPreviousAlbumScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = true,
            currentSongActiveScheme = null,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertSame(previousAlbumScheme, resolved)
    }

    @Test
    fun resolvePlayerSheetTargetScheme_withReadyAlbumPalette_usesCurrentAlbumScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = true,
            currentSongActiveScheme = activeAlbumScheme,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertSame(activeAlbumScheme, resolved)
    }

    @Test
    fun resolvePlayerSheetTargetScheme_whenNotAlbumArtTheme_alwaysUsesSystemScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = false,
            hasAlbumArt = true,
            currentSongActiveScheme = activeAlbumScheme,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertSame(systemScheme, resolved)
    }
}
