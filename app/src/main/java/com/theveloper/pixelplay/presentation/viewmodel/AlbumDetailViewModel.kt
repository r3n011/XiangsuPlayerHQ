package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseAlbumDetail
import com.theveloper.pixelplay.data.netease.NeteaseArtistSong
import com.theveloper.pixelplay.data.netease.PersonalFmApi
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val neteaseApi: PersonalFmApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_album_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_id_not_found), isLoading = false) }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 先尝试从本地数据库加载
                val albumFromDb = musicRepository.getAlbumById(id).first()
                val songsFromDb = musicRepository.getSongsForAlbum(id).first()

                if (albumFromDb != null && songsFromDb.isNotEmpty()) {
                    _uiState.value = AlbumDetailUiState(
                        album = albumFromDb,
                        songs = songsFromDb.sortedWith(
                            compareBy<Song> { it.discNumber ?: 1 }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        ),
                        isLoading = false
                    )
                    return@launch
                }

                // 如果本地没有数据，尝试从网易云 API 获取
                Timber.d("AlbumDetail: No local data for albumId=$id, trying Netease API")
                val detailFromApi: NeteaseAlbumDetail? = try {
                    withContext(Dispatchers.IO) {
                        val result = neteaseApi.fetchAlbumDetail(id)
                        if (result.isSuccess) result.getOrThrow() else null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "AlbumDetail: Exception from Netease API for albumId=$id")
                    null
                }

                if (detailFromApi != null && detailFromApi.songs.isNotEmpty()) {
                    Timber.d("AlbumDetail: Got ${detailFromApi.songs.size} songs from Netease API for albumId=$id")
                    val album = createAlbumFromDetail(detailFromApi)
                    val songs = detailFromApi.songs.mapIndexed { idx, neteaseSong ->
                        neteaseSong.toSong(trackNumber = idx + 1)
                    }
                    _uiState.value = AlbumDetailUiState(
                        album = album,
                        songs = songs,
                        isLoading = false
                    )
                } else {
                    // 如果也没有本地 album，则显示错误
                    if (albumFromDb != null) {
                        _uiState.value = AlbumDetailUiState(
                            album = albumFromDb,
                            songs = songsFromDb,
                            isLoading = false
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                error = context.getString(R.string.album_not_found),
                                isLoading = false
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "AlbumDetail: Exception loading albumId=$id")
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun createAlbumFromDetail(detail: NeteaseAlbumDetail): Album {
        val year = if (detail.publishTime > 0) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = detail.publishTime
            cal.get(java.util.Calendar.YEAR)
        } else 0

        return Album(
            id = detail.id,
            title = detail.name,
            artist = detail.songs.firstOrNull()?.artists?.firstOrNull() ?: "",
            year = year,
            dateAdded = System.currentTimeMillis(),
            albumArtUriString = detail.picUrl,
            songCount = detail.songs.size,
            albumArtist = detail.songs.firstOrNull()?.artists?.firstOrNull()
        )
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }
}

private fun NeteaseArtistSong.toSong(trackNumber: Int = 0): Song {
    val displayArtist = if (artists.isNotEmpty()) artists.joinToString(", ") else "Unknown Artist"
    val artistRefs = artists.mapIndexed { index, name ->
        ArtistRef(
            id = artistIds.getOrNull(index) ?: 0L,
            name = name,
            isPrimary = index == 0
        )
    }

    return Song(
        id = "netease_$id",
        title = name,
        artist = displayArtist,
        artistId = artistIds.firstOrNull() ?: 0L,
        artists = artistRefs,
        album = albumName,
        albumId = albumId,
        albumArtist = displayArtist,
        path = "",
        contentUriString = "netease://$id",
        albumArtUriString = albumPic,
        duration = duration,
        neteaseId = id,
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        trackNumber = trackNumber,
        dateAdded = System.currentTimeMillis()
    )
}
