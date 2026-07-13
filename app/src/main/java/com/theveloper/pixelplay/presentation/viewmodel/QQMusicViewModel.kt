package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.qq.QQSearchApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 搜索状态（QQ 音乐搜索，基于 oiapi.net 溯音酷我 API）
 */
data class QQSearchUiState(
    val keyword: String = "",
    val searching: Boolean = false,
    val results: List<QQSearchApi.QQSong> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class QQMusicViewModel @Inject constructor(
    app: Application,
    private val qqSearchApi: QQSearchApi,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(QQSearchUiState())
    val uiState: StateFlow<QQSearchUiState> = _uiState.asStateFlow()

    var keyword: String
        get() = _uiState.value.keyword
        set(v) { _uiState.value = _uiState.value.copy(keyword = v) }

    /**
     * 搜索歌曲（走 oiapi.net/api/Kuwo，n=20）
     */
    fun search() {
        val kw = _uiState.value.keyword.trim()
        if (kw.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searching = true, error = null, results = emptyList()
            )
            val result = qqSearchApi.search(kw)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = result.getOrDefault(emptyList()),
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    error = result.exceptionOrNull()?.message ?: "搜索失败"
                )
            }
        }
    }

    /**
     * 生成稳定的歌曲 id（给播放队列用）
     */
    fun getStableSongId(song: QQSearchApi.QQSong): String = "kw_${song.id}"

    /**
     * 播放某首歌曲：先用 br=5(320k mp3) 拿播放 URL，失败回退 br=7(128k)，再失败 FLAC(1)
     */
    fun playSong(
        song: QQSearchApi.QQSong,
        onUrlReady: (String, String, String, String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("QQMusic playSong: '${song.title}' - '${song.singer}'")

            var playUrl: String? = null
            val brs = listOf(5, 7, 1) // 320k mp3 -> 128k mp3 -> FLAC

            for (br in brs) {
                val result = qqSearchApi.getPlayUrl(song, br)
                if (result.isSuccess) {
                    val u = result.getOrDefault("")
                    if (u.isNotEmpty()) {
                        playUrl = u
                        Timber.d("QQMusic playSong: got url br=$br len=${u.length}")
                        break
                    }
                } else {
                    Timber.d("QQMusic playSong: br=$br failed: ${result.exceptionOrNull()?.message}")
                }
            }

            if (!playUrl.isNullOrEmpty()) {
                onUrlReady(playUrl, song.title, song.singer, song.cover, getStableSongId(song))
            } else {
                Timber.e("QQMusic playSong: ALL br failed for '${song.title}'")
                onUrlReady("", song.title, song.singer, song.cover, getStableSongId(song))
            }
        }
    }
}
