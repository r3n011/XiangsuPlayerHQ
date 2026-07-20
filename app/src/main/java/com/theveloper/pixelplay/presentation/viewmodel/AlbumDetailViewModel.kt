package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.netease.NeteaseAlbumDetail
import com.theveloper.pixelplay.data.netease.NeteaseArtistSong
import com.theveloper.pixelplay.data.netease.PersonalFmApi
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
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

sealed class AlbumSongsOrderMode {
    object Manual : AlbumSongsOrderMode()
    data class Sorted(val option: SortOption) : AlbumSongsOrderMode()
}

data class AlbumDetailUiState(
    val album: Album? = null,
    val rawSongs: List<Song> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val albumId: String? = null,
    val currentSongsSortOption: SortOption = SortOption.SongDefaultOrder,
    val albumSongsOrderMode: AlbumSongsOrderMode = AlbumSongsOrderMode.Sorted(SortOption.SongDefaultOrder),
    val albumOrderModes: Map<String, AlbumSongsOrderMode> = emptyMap(),
    val albumManualOrders: Map<String, List<String>> = emptyMap()
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
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
                _uiState.update { it.copy(albumId = albumIdString) }
                loadAlbumData(albumId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_album_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_id_not_found), isLoading = false) }
        }
        observeAlbumOrderModes()
    }

    private fun observeAlbumOrderModes() {
        viewModelScope.launch {
            playlistPreferencesRepository.albumSongOrderModesFlow.collect { storedModes ->
                val resolvedModes = storedModes.mapValues { (_, value) ->
                    decodeAlbumOrderMode(value)
                }
                _uiState.update { it.copy(albumOrderModes = resolvedModes) }
                refreshAlbumOrder()
            }
        }
        viewModelScope.launch {
            playlistPreferencesRepository.albumSongManualOrdersFlow.collect { manualOrders ->
                _uiState.update { it.copy(albumManualOrders = manualOrders) }
                refreshAlbumOrder()
            }
        }
    }

    private fun refreshAlbumOrder() {
        val albumId = _uiState.value.albumId ?: return
        val rawSongs = _uiState.value.rawSongs
        if (rawSongs.isEmpty()) return
        _uiState.update {
            it.copy(songs = applyAlbumOrderToSongs(rawSongs, albumId))
        }
    }

    private fun decodeAlbumOrderMode(value: String): AlbumSongsOrderMode {
        return if (value == MANUAL_ORDER_MODE) {
            AlbumSongsOrderMode.Manual
        } else {
            val option = SortOption.fromStorageKey(value, SortOption.SONGS, SortOption.SongDefaultOrder)
            AlbumSongsOrderMode.Sorted(option)
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
                    val raw = songsFromDb.sortedWith(
                        compareBy<Song> { it.discNumber ?: 1 }
                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )
                    _uiState.update { currentState ->
                        currentState.copy(
                            album = albumFromDb,
                            rawSongs = raw,
                            songs = applyAlbumOrderToSongs(raw, id.toString()),
                            isLoading = false
                        )
                    }
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
                    _uiState.update { currentState ->
                        currentState.copy(
                            album = album,
                            rawSongs = songs,
                            songs = applyAlbumOrderToSongs(songs, id.toString()),
                            isLoading = false
                        )
                    }
                } else {
                    // 如果也没有本地 album，则显示错误
                    if (albumFromDb != null) {
                        val raw = songsFromDb.sortedWith(
                            compareBy<Song> { it.discNumber ?: 1 }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                        _uiState.update { currentState ->
                            currentState.copy(
                                album = albumFromDb,
                                rawSongs = raw,
                                songs = applyAlbumOrderToSongs(raw, id.toString()),
                                isLoading = false
                            )
                        }
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
        val albumId = _uiState.value.album?.id?.toString() ?: return
        _uiState.update {
            it.copy(
                isLoading = false,
                rawSongs = songs,
                songs = applyAlbumOrderToSongs(songs, albumId)
            )
        }
    }

    fun sortAlbumSongs(sortOption: SortOption) {
        val albumId = _uiState.value.albumId ?: return
        val rawSongs = _uiState.value.rawSongs
        if (rawSongs.isEmpty()) return

        val orderedSongs = if (sortOption == SortOption.SongDefaultOrder) {
            defaultAlbumSort(rawSongs)
        } else {
            sortSongsList(rawSongs, sortOption)
        }

        _uiState.update {
            val updatedModes = it.albumOrderModes + (albumId to AlbumSongsOrderMode.Sorted(sortOption))
            it.copy(
                songs = orderedSongs,
                currentSongsSortOption = sortOption,
                albumSongsOrderMode = AlbumSongsOrderMode.Sorted(sortOption),
                albumOrderModes = updatedModes
            )
        }

        viewModelScope.launch {
            playlistPreferencesRepository.setAlbumSongOrderMode(albumId, sortOption.storageKey)
            if (sortOption == SortOption.SongDefaultOrder) {
                playlistPreferencesRepository.clearAlbumSongManualOrder(albumId)
            }
        }
    }

    fun setManualOrderEnabled(enabled: Boolean) {
        val albumId = _uiState.value.albumId ?: return
        val rawSongs = _uiState.value.rawSongs
        if (rawSongs.isEmpty()) return

        if (enabled) {
            val currentSongs = _uiState.value.songs
            _uiState.update {
                val updatedModes = it.albumOrderModes + (albumId to AlbumSongsOrderMode.Manual)
                it.copy(
                    songs = currentSongs,
                    currentSongsSortOption = SortOption.SongDefaultOrder,
                    albumSongsOrderMode = AlbumSongsOrderMode.Manual,
                    albumOrderModes = updatedModes
                )
            }
            viewModelScope.launch {
                playlistPreferencesRepository.setAlbumSongOrderMode(albumId, MANUAL_ORDER_MODE)
                playlistPreferencesRepository.setAlbumSongManualOrder(albumId, currentSongs.map { it.id })
            }
        } else {
            sortAlbumSongs(SortOption.SongDefaultOrder)
        }
    }

    fun reorderSongsInAlbum(fromIndex: Int, toIndex: Int) {
        val albumId = _uiState.value.albumId ?: return
        val currentSongs = _uiState.value.songs.toMutableList()
        if (fromIndex !in currentSongs.indices || toIndex !in currentSongs.indices) return

        val item = currentSongs.removeAt(fromIndex)
        currentSongs.add(toIndex, item)
        val newOrderIds = currentSongs.map { it.id }

        _uiState.update {
            val updatedModes = it.albumOrderModes + (albumId to AlbumSongsOrderMode.Manual)
            it.copy(
                songs = currentSongs,
                albumSongsOrderMode = AlbumSongsOrderMode.Manual,
                albumOrderModes = updatedModes,
                currentSongsSortOption = SortOption.SongDefaultOrder
            )
        }

        viewModelScope.launch {
            playlistPreferencesRepository.setAlbumSongOrderMode(albumId, MANUAL_ORDER_MODE)
            playlistPreferencesRepository.setAlbumSongManualOrder(albumId, newOrderIds)
        }
    }

    private fun applyAlbumOrderToSongs(songs: List<Song>, albumId: String): List<Song> {
        val orderMode = _uiState.value.albumOrderModes[albumId]
            ?: AlbumSongsOrderMode.Sorted(SortOption.SongDefaultOrder)
        return when (orderMode) {
            is AlbumSongsOrderMode.Sorted -> {
                if (orderMode.option == SortOption.SongDefaultOrder) {
                    defaultAlbumSort(songs)
                } else {
                    sortSongsList(songs, orderMode.option)
                }
            }
            AlbumSongsOrderMode.Manual -> {
                val manualOrder = _uiState.value.albumManualOrders[albumId].orEmpty()
                if (manualOrder.isEmpty()) {
                    defaultAlbumSort(songs)
                } else {
                    reorderSongsByManualOrder(songs, manualOrder)
                }
            }
        }
    }

    private fun defaultAlbumSort(songs: List<Song>): List<Song> {
        return songs.sortedWith(
            compareBy<Song> { it.discNumber ?: 1 }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun reorderSongsByManualOrder(songs: List<Song>, manualOrder: List<String>): List<Song> {
        val songMap = songs.associateBy { it.id }
        val ordered = mutableListOf<Song>()
        val placedIds = mutableSetOf<String>()
        manualOrder.forEach { songId ->
            songMap[songId]?.let {
                ordered.add(it)
                placedIds.add(songId)
            }
        }
        // 把新增但未在手动顺序里的歌曲按默认顺序追加到末尾
        songs.filterNot { it.id in placedIds }
            .let { defaultAlbumSort(it) }
            .forEach { ordered.add(it) }
        return ordered
    }

    private fun sortSongsList(
        songs: List<Song>,
        sortOption: SortOption
    ): List<Song> {
        return when (sortOption) {
            SortOption.SongTitleAZ -> songs.sortedWith(
                compareBy<Song> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongTitleZA -> songs.sortedWith(
                compareByDescending<Song> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongArtist -> songs.sortedWith(
                compareBy<Song> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongArtistDesc -> songs.sortedWith(
                compareByDescending<Song> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongAlbum -> songs.sortedWith(
                compareBy<Song> { it.album.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongAlbumDesc -> songs.sortedWith(
                compareByDescending<Song> { it.album.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDuration -> songs.sortedWith(
                compareByDescending<Song> { it.duration }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDurationAsc -> songs.sortedWith(
                compareBy<Song> { it.duration }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDateAdded -> songs.sortedWith(
                compareByDescending<Song> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.SongDateAddedAsc -> songs.sortedWith(
                compareBy<Song> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            else -> defaultAlbumSort(songs)
        }
    }

    companion object {
        private const val MANUAL_ORDER_MODE = "manual"
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
