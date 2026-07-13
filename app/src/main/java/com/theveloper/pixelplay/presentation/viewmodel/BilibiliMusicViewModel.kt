package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi
import com.theveloper.pixelplay.data.bilibili.BilibiliSongInfo
import com.theveloper.pixelplay.data.repository.MusicRepository
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BilibiliUiState(
    val keyword: String = "",
    val searching: Boolean = false,
    val results: List<BilibiliSongInfo> = emptyList(),
    val error: String? = null,
    val isEnd: Boolean = true,
    val isLoadingMore: Boolean = false,
    val progress: Float? = null,
    val progressLabel: String? = null,
)

@HiltViewModel
class BilibiliMusicViewModel @Inject constructor(
    app: Application,
    private val searchApi: BilibiliSearchApi,
    private val musicRepository: MusicRepository,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(BilibiliUiState())
    val uiState: StateFlow<BilibiliUiState> = _uiState.asStateFlow()

    var keyword: String
        get() = _uiState.value.keyword
        set(v) { _uiState.value = _uiState.value.copy(keyword = v) }

    private val _pageSize = 20
    private var _currentPage = 1
    private var _lastKeyword: String? = null

    fun search() {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        _currentPage = 1
        _lastKeyword = kw
        _uiState.value = _uiState.value.copy(
            searching = true,
            error = null,
            isEnd = false,
            isLoadingMore = false,
            results = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchApi.search(kw, page = 1, pageSize = _pageSize)
                val errorMsg = when {
                    result.error != null -> result.error
                    result.list.isEmpty() -> "无结果（请换关键词）"
                    else -> null
                }
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = result.list,
                    isEnd = result.isEnd,
                    error = errorMsg
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = emptyList(),
                    isEnd = true,
                    error = "搜索失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    fun loadMore() {
        val kw = _lastKeyword?.trim() ?: keyword.trim()
        if (kw.isBlank()) return
        if (_uiState.value.searching || _uiState.value.isLoadingMore || _uiState.value.isEnd) return
        if (_uiState.value.results.isEmpty()) return

        val nextPage = _currentPage + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = searchApi.search(kw, page = nextPage, pageSize = _pageSize)
                val existingIds = _uiState.value.results.mapTo(LinkedHashSet()) { it.id }
                val newItems = result.list.filterNot { it.id in existingIds }
                val merged = _uiState.value.results + newItems

                _currentPage = nextPage
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    results = merged,
                    isEnd = result.isEnd || newItems.isEmpty(),
                    error = result.error
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "加载更多失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    fun playSong(
        song: BilibiliSongInfo,
        onOpenPlayer: (url: String, title: String, artist: String, cover: String, songId: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    progress = 0.1f,
                    progressLabel = "获取视频信息…"
                )

                var cidToUse = song.cid
                var aidToUse = song.aid
                var bvidToUse = song.bvid

                Timber.d("Bilibili playSong: aid=$aidToUse, bvid=$bvidToUse, cid=$cidToUse")

                if (cidToUse == 0L) {
                    Timber.d("cid is 0, fetching video detail")
                    val detail = searchApi.getVideoDetail(aidToUse, bvidToUse)
                    if (detail != null) {
                        cidToUse = detail.cid
                        aidToUse = detail.aid
                        bvidToUse = detail.bvid
                        Timber.d("Got detail: aid=$aidToUse, bvid=$bvidToUse, cid=$cidToUse")
                    } else {
                        Timber.w("getVideoDetail returned null, trying pagelist")
                        val pageList = searchApi.getPageList(aidToUse, bvidToUse)
                        if (pageList.isNotEmpty()) {
                            cidToUse = pageList[0].cid
                            Timber.d("Got cid from pagelist: $cidToUse")
                        }
                    }
                }

                if (cidToUse == 0L) {
                    throw Exception("无法获取视频CID")
                }

                _uiState.value = _uiState.value.copy(
                    progress = 0.5f,
                    progressLabel = "解析音频流…"
                )

                Timber.d("Getting play URL: aid=$aidToUse, cid=$cidToUse, bvid=$bvidToUse")
                val url = searchApi.getPlayUrl(aidToUse, cidToUse, bvidToUse)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(progress = null, progressLabel = null)
                    if (url == null) {
                        _uiState.value = _uiState.value.copy(error = "无法获取播放链接，请换一首")
                        return@withContext
                    }

                    Timber.d("Got play URL: ${url.take(50)}...")

                    val coverToUse = song.pic.ifBlank { "" }
                    val savedSongId = try {
                        musicRepository.saveCloudSong(song.toLxSongInfo()).toString()
                    } catch (t: Throwable) {
                        Timber.w("saveCloudSong failed: ${t.message}")
                        "bilibili_${song.id}"
                    }

                    onOpenPlayer(url, song.name, song.singer, coverToUse, savedSongId)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Bilibili playSong exception")
                _uiState.value = _uiState.value.copy(
                    progress = null, progressLabel = null,
                    error = "播放失败: ${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
    }
}

private fun BilibiliSongInfo.toLxSongInfo(): com.theveloper.pixelplay.data.lx.LxSongInfo {
    return com.theveloper.pixelplay.data.lx.LxSongInfo(
        id = id,
        songmid = bvid.ifBlank { aid.toString() },
        hash = id,
        name = name,
        singer = singer,
        albumName = albumName,
        duration = duration,
        pic = pic,
        source = "bilibili"
    )
}