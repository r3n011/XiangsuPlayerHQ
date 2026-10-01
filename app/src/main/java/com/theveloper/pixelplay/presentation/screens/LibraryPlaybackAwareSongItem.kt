package com.theveloper.pixelplay.presentation.screens

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.listentogether.isNeteaseTogetherSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
internal data class LibrarySongPlaybackUiState(
    val isCurrentSong: Boolean = false,
    val isPlaying: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    modifier: Modifier = Modifier,
    song: Song,
    playerViewModel: PlayerViewModel,
    albumArtSize: Dp = 50.dp,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    isRadio: Boolean = false,
    showFavoriteButton: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    isFavorite: Boolean = false,
    containerColorOverride: Color? = null,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val playbackUiState by remember(song.id, playerViewModel) {
        playerViewModel.stablePlayerState
            .map { state ->
                val isCurrentSong = state.currentSong?.id == song.id
                LibrarySongPlaybackUiState(
                    isCurrentSong = isCurrentSong,
                    isPlaying = isCurrentSong && state.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())

    val context = LocalContext.current
    // ⚡ 一起听激活时，非网易云歌曲整行置灰，点击提示"退出一起听即可播放"
    val togetherActive = playerViewModel.listenTogetherCoordinator.state.value.active
    val dimmedByListenTogether = togetherActive && !song.isNeteaseTogetherSong()

    EnhancedSongListItem(
        modifier = modifier,
        song = song,
        isPlaying = playbackUiState.isPlaying,
        isCurrentSong = playbackUiState.isCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        isRadio = isRadio,
        showFavoriteButton = showFavoriteButton,
        showMoreOptionsButton = showMoreOptionsButton,
        isFavorite = isFavorite,
        containerColorOverride = containerColorOverride,
        dimmed = dimmedByListenTogether,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        onFavoriteClick = onFavoriteClick,
        onClick = {
            if (dimmedByListenTogether) {
                Toast.makeText(context, R.string.listen_together_leave_to_play, Toast.LENGTH_SHORT)
                    .show()
            } else {
                onClick()
            }
        }
    )
}
