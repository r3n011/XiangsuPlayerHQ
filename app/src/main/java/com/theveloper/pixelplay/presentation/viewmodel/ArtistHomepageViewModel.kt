package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseArtistSong
import com.theveloper.pixelplay.data.netease.PersonalFmApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class ArtistHomepageUiState(
    val artistId: Long = 0L,
    val artistName: String = "",
    val artistAvatar: String = "",
    val backgroundUrl: String = "",
    val identifyTag: String = "",
    val identityImages: List<String> = emptyList(),
    val briefDesc: String = "",
    val alias: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val songs: List<Song> = emptyList(),
    val albums: List<NeteaseArtistAlbumSection> = emptyList(),
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val isLoading: Boolean = true,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMoreAlbums: Boolean = false,
    val error: String? = null,
    val order: String = "hot",
    val offset: Int = 0,
    val albumOffset: Int = 0,
    val hasMore: Boolean = true,
    val albumHasMore: Boolean = true,
    val selectedTab: String = "songs"
)

@Immutable
data class NeteaseArtistAlbumSection(
    val albumId: Long,
    val title: String,
    val coverUrl: String,
    val year: Int?,
    val songCount: Int
)

@HiltViewModel
class ArtistHomepageViewModel @Inject constructor(
    private val api: PersonalFmApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistHomepageUiState(isLoading = true))
    val uiState: StateFlow<ArtistHomepageUiState> = _uiState.asStateFlow()

    private var loadedArtistId: Long = 0L
    private val pageSize = 50

    fun loadArtistData(artistId: Long, neteaseCookie: String? = null) {
        if (artistId <= 0) {
            _uiState.value = ArtistHomepageUiState(
                error = "无效的歌手 ID",
                isLoading = false
            )
            return
        }

        if (loadedArtistId == artistId && !_uiState.value.isLoading && _uiState.value.songs.isNotEmpty()) {
            Timber.d("ArtistHomepage: Skipping duplicate load for artistId=$artistId")
            return
        }

        loadedArtistId = artistId
        _uiState.value = ArtistHomepageUiState(
            artistId = artistId,
            isLoading = true
        )

        Timber.d("ArtistHomepage: Loading data for artistId=$artistId")

        viewModelScope.launch(Dispatchers.IO) {
            // 立即进入页面：头部先用导航传入的兜底名称/头像渲染，
            // 背景图/头像等图片均异步加载，不阻塞进入，加载完成后自动刷新；
            // isInitialLoading 标记首屏数据仍在后台加载，供页面显示「加载中」提示
            _uiState.value = ArtistHomepageUiState(
                artistId = artistId,
                isLoading = false,
                isInitialLoading = true
            )

            try {
                val currentOrder = _uiState.value.order

                // 并行加载：歌手基本信息 / 歌曲第一页 / 专辑列表，任一完成后立即更新对应区域
                coroutineScope {
                    val infoDeferred = async { api.fetchArtistInfo(artistId, neteaseCookie) }
                    val songsDeferred = async { api.fetchArtistSongs(artistId, currentOrder, pageSize, 0, neteaseCookie) }
                    val albumsDeferred = async { api.fetchArtistAlbums(artistId, 30, 0, neteaseCookie) }

                    // 1. 歌手基本信息（含背景图、认证、简介、标签等）
                    val infoResult = infoDeferred.await()
                    val artistDetail = infoResult.getOrNull()
                    infoResult.exceptionOrNull()?.let {
                        Timber.w(it, "ArtistHomepage: fetchArtistInfo warning")
                    }
                    _uiState.value = _uiState.value.copy(
                        artistName = artistDetail?.name?.ifBlank { _uiState.value.artistName }
                            ?: _uiState.value.artistName,
                        artistAvatar = artistDetail?.avatarUrl ?: _uiState.value.artistAvatar,
                        backgroundUrl = artistDetail?.backgroundUrl ?: "",
                        identifyTag = artistDetail?.identifyTag ?: "",
                        identityImages = artistDetail?.identityImages ?: emptyList(),
                        briefDesc = artistDetail?.briefDesc ?: "",
                        alias = artistDetail?.alias ?: emptyList(),
                        tags = artistDetail?.tags ?: emptyList()
                    )

                    // 2. 歌手歌曲（第一页）
                    val songsResult = songsDeferred.await()
                    val songList: List<Song> = songsResult.getOrNull()?.first
                        ?.map { it.toSong() }
                        ?: emptyList()
                    val songTotal = songsResult.getOrNull()?.second ?: songList.size
                    songsResult.exceptionOrNull()?.let {
                        Timber.e(it, "ArtistHomepage: fetchArtistSongs failed")
                    }
                    _uiState.value = _uiState.value.copy(
                        songs = songList,
                        songCount = songTotal,
                        offset = songList.size,
                        hasMore = songList.size < songTotal
                    )

                    // 3. 歌手专辑列表
                    val albumsResult = albumsDeferred.await()
                    val albumSections: List<NeteaseArtistAlbumSection> = albumsResult.getOrNull()?.first
                        ?.map { album ->
                            NeteaseArtistAlbumSection(
                                albumId = album.id,
                                title = album.name,
                                coverUrl = album.picUrl,
                                year = if (album.publishTime > 0) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = album.publishTime
                                    cal.get(java.util.Calendar.YEAR)
                                } else null,
                                songCount = album.size
                            )
                        }
                        ?: emptyList()
                    val albumTotal = albumsResult.getOrNull()?.second ?: albumSections.size
                    albumsResult.exceptionOrNull()?.let {
                        Timber.w(it, "ArtistHomepage: fetchArtistAlbums warning")
                    }
                    _uiState.value = _uiState.value.copy(
                        albums = albumSections,
                        albumCount = albumTotal,
                        albumOffset = albumSections.size,
                        albumHasMore = albumSections.size < albumTotal
                    )

                    Timber.d("ArtistHomepage: Loaded ${songList.size}/$songTotal songs, ${albumSections.size} albums for artistId=$artistId order=$currentOrder")
                }

                // 首屏三类数据（信息/歌曲/专辑）均已加载完成，结束「加载中」提示
                _uiState.value = _uiState.value.copy(isInitialLoading = false)
            } catch (t: Throwable) {
                Timber.e(t, "ArtistHomepage: Unexpected error loading artistId=$artistId")
                _uiState.value = _uiState.value.copy(
                    error = "加载失败: ${t.message}"
                )
            }
        }
    }

    fun loadMoreSongs(neteaseCookie: String? = null) {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasMore || currentState.artistId <= 0L) {
            return
        }
        // 首屏歌曲尚未加载完成（offset==0 且列表为空）时跳过自动加载更多，
        // 避免与后台并行加载的首屏请求重复
        if (currentState.offset == 0 && currentState.songs.isEmpty()) {
            return
        }

        val artistId = currentState.artistId
        val currentOrder = currentState.order
        val currentOffset = currentState.offset

        _uiState.value = currentState.copy(isLoadingMore = true)

        Timber.d("ArtistHomepage: Loading more songs for artistId=$artistId order=$currentOrder offset=$currentOffset")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songsResult = api.fetchArtistSongs(artistId, currentOrder, pageSize, currentOffset, neteaseCookie)
                val newSongs: List<Song> = songsResult.getOrNull()?.first
                    ?.map { it.toSong() }
                    ?: emptyList()
                val songTotal = songsResult.getOrNull()?.second ?: (currentOffset + newSongs.size)

                val allSongs = currentState.songs + newSongs
                val newOffset = currentOffset + newSongs.size

                _uiState.value = currentState.copy(
                    songs = allSongs,
                    songCount = songTotal,
                    offset = newOffset,
                    hasMore = newSongs.size >= pageSize && newOffset < songTotal,
                    isLoadingMore = false
                )

                Timber.d("ArtistHomepage: Loaded ${newSongs.size} more songs, total=${allSongs.size}/$songTotal for artistId=$artistId")
            } catch (t: Throwable) {
                Timber.e(t, "ArtistHomepage: loadMoreSongs failed for artistId=$artistId")
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    fun loadMoreAlbums(neteaseCookie: String? = null) {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMoreAlbums || !currentState.albumHasMore || currentState.artistId <= 0L) {
            return
        }

        val artistId = currentState.artistId
        val currentAlbumOffset = currentState.albumOffset

        _uiState.value = currentState.copy(isLoadingMoreAlbums = true)

        Timber.d("ArtistHomepage: Loading more albums for artistId=$artistId offset=$currentAlbumOffset")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val albumsResult = api.fetchArtistAlbums(artistId, pageSize, currentAlbumOffset, neteaseCookie)
                val newAlbums: List<NeteaseArtistAlbumSection> = albumsResult.getOrNull()?.first
                    ?.map { album ->
                        NeteaseArtistAlbumSection(
                            albumId = album.id,
                            title = album.name,
                            coverUrl = album.picUrl,
                            year = if (album.publishTime > 0) {
                                val cal = java.util.Calendar.getInstance()
                                cal.timeInMillis = album.publishTime
                                cal.get(java.util.Calendar.YEAR)
                            } else null,
                            songCount = album.size
                        )
                    }
                    ?: emptyList()
                val albumTotal = albumsResult.getOrNull()?.second ?: (currentAlbumOffset + newAlbums.size)

                val allAlbums = currentState.albums + newAlbums
                val newAlbumOffset = currentAlbumOffset + newAlbums.size

                _uiState.value = currentState.copy(
                    albums = allAlbums,
                    albumCount = albumTotal,
                    albumOffset = newAlbumOffset,
                    albumHasMore = newAlbums.size >= pageSize && newAlbumOffset < albumTotal,
                    isLoadingMoreAlbums = false
                )

                Timber.d("ArtistHomepage: Loaded ${newAlbums.size} more albums, total=${allAlbums.size}/$albumTotal for artistId=$artistId")
            } catch (t: Throwable) {
                Timber.e(t, "ArtistHomepage: loadMoreAlbums failed for artistId=$artistId")
                _uiState.value = currentState.copy(isLoadingMoreAlbums = false)
            }
        }
    }

    fun selectTab(tab: String) {
        val currentState = _uiState.value
        if (currentState.selectedTab == tab) return
        _uiState.value = currentState.copy(selectedTab = tab)
    }

    fun changeOrder(newOrder: String, neteaseCookie: String? = null) {
        val currentState = _uiState.value
        if (currentState.order == newOrder || currentState.artistId <= 0L) {
            return
        }

        val artistId = currentState.artistId
        _uiState.value = currentState.copy(
            isLoading = true,
            songs = emptyList(),
            order = newOrder,
            offset = 0,
            hasMore = true
        )

        Timber.d("ArtistHomepage: Changing order to $newOrder for artistId=$artistId")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val songsResult = api.fetchArtistSongs(artistId, newOrder, pageSize, 0, neteaseCookie)
                val songList: List<Song> = songsResult.getOrNull()?.first
                    ?.map { it.toSong() }
                    ?: emptyList()
                val songTotal = songsResult.getOrNull()?.second ?: songList.size

                _uiState.value = currentState.copy(
                    songs = songList,
                    songCount = songTotal,
                    order = newOrder,
                    offset = songList.size,
                    hasMore = songList.size < songTotal,
                    isLoading = false
                )

                Timber.d("ArtistHomepage: Order changed, loaded ${songList.size}/$songTotal songs for artistId=$artistId order=$newOrder")
            } catch (t: Throwable) {
                Timber.e(t, "ArtistHomepage: changeOrder failed for artistId=$artistId")
                _uiState.value = currentState.copy(isLoading = false)
            }
        }
    }
}

fun NeteaseArtistSong.toSong(): Song {
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
        path = "",
        contentUriString = "netease://$id",
        albumArtUriString = albumPic,
        duration = duration,
        neteaseId = id,
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        dateAdded = System.currentTimeMillis()
    )
}
