package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxJsEngine
import com.theveloper.pixelplay.data.lx.LxSongInfo
import com.theveloper.pixelplay.data.qq.QQSearchApi
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 搜索状态（QQ 音乐搜索，官方 zzcSign 签名优先）
 */
data class QQSearchUiState(
    val keyword: String = "",
    val searching: Boolean = false,
    val results: List<QQSearchApi.QQSong> = emptyList(),
    val error: String? = null,
    // 分页相关字段
    val page: Int = 1,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false
)

/** 当前搜索结果来源（分页时保持一致） */
private enum class ResultSource { OFFICIAL_TX, OIAPI, LX_KW }

@HiltViewModel
class QQMusicViewModel @Inject constructor(
    app: Application,
    private val qqSearchApi: QQSearchApi,
    private val lxJsEngine: LxJsEngine,
    private val builtInSourceSearchApi: BuiltInSourceSearchApi,
    private val userPreferencesRepository: UserPreferencesRepository,
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(QQSearchUiState())
    val uiState: StateFlow<QQSearchUiState> = _uiState.asStateFlow()

    /** 当前结果来源（分页时保持一致） */
    private var _resultSource = ResultSource.OFFICIAL_TX

    var keyword: String
        get() = _uiState.value.keyword
        set(v) { _uiState.value = _uiState.value.copy(keyword = v) }

    /**
     * 搜索歌曲（与落雪 lx-music 同款防风控方案）：
     * 1. 官方 QQ 音乐 u.y.qq.com + zzcSign 签名搜索（真 QQ 音源）
     * 2. 兜底：oiapi 酷我 API
     * 3. 兜底：落雪 JS 引擎的酷我(kw)音源
     */
    fun search() {
        val kw = _uiState.value.keyword.trim()
        if (kw.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searching = true,
                error = null,
                results = emptyList(),
                page = 1,
                isEnd = false,
                isLoadingMore = false
            )

            // 1. 官方 QQ 音乐 zzcSign 搜索
            var songs: List<QQSearchApi.QQSong> = emptyList()
            var errMsg: String? = null
            var source = ResultSource.OFFICIAL_TX
            val official = runCatching {
                builtInSourceSearchApi.search("tx", kw, 1, 20)
            }.getOrNull()
            songs = official?.list?.mapNotNull { it.toQQSong() }.orEmpty()
            if (songs.isEmpty()) {
                Timber.d("QQMusic search: official tx empty/failed, fallback to oiapi kw")
                // 2. 兜底：oiapi 酷我
                val result = qqSearchApi.search(kw, page = 1)
                songs = result.getOrNull().orEmpty()
                errMsg = result.exceptionOrNull()?.message
                source = ResultSource.OIAPI
            }
            if (songs.isEmpty()) {
                Timber.d("QQMusic search: oiapi empty/failed, fallback to lx kw")
                // 3. 兜底：落雪 kw 音源
                val lx = runCatching {
                    lxJsEngine.search(kw, source = "kw", page = 1, pagesize = 20)
                }.getOrNull()
                songs = lx?.list?.mapNotNull { it.toQQSong() }.orEmpty()
                source = ResultSource.LX_KW
                if (songs.isEmpty() && lx == null) {
                    errMsg = errMsg ?: "搜索失败"
                }
            }
            _resultSource = source
            if (songs.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    results = songs,
                    error = null,
                    isEnd = source != ResultSource.OFFICIAL_TX || songs.size < 20
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    error = errMsg ?: "搜索失败",
                    isEnd = true
                )
            }
        }
    }

    /**
     * 加载下一页搜索结果（与当前结果来源保持一致）
     */
    fun loadMore() {
        val kw = _uiState.value.keyword.trim()
        if (kw.isEmpty() || _uiState.value.isEnd) return
        if (_uiState.value.searching || _uiState.value.isLoadingMore) return
        if (_uiState.value.results.isEmpty()) return

        val nextPage = _uiState.value.page + 1
        _uiState.value = _uiState.value.copy(isLoadingMore = true, error = null)
        viewModelScope.launch {
            val newSongs = when (_resultSource) {
                ResultSource.OFFICIAL_TX -> {
                    val lx = runCatching {
                        builtInSourceSearchApi.search("tx", kw, nextPage, 20)
                    }.getOrNull()
                    lx?.list?.mapNotNull { it.toQQSong() }.orEmpty()
                }
                ResultSource.OIAPI -> {
                    qqSearchApi.search(kw, page = nextPage).getOrDefault(emptyList())
                }
                ResultSource.LX_KW -> {
                    val lx = runCatching {
                        lxJsEngine.search(kw, source = "kw", page = nextPage, pagesize = 20)
                    }.getOrNull()
                    lx?.list?.mapNotNull { it.toQQSong() }.orEmpty()
                }
            }
            val existingIds = _uiState.value.results.mapTo(LinkedHashSet()) { it.id }
            val merged = _uiState.value.results + newSongs.filterNot { it.id in existingIds }
            _uiState.value = _uiState.value.copy(
                page = nextPage,
                isLoadingMore = false,
                isEnd = newSongs.size < 20,
                results = merged
            )
        }
    }

    /**
     * 生成稳定的歌曲 id（给播放队列用）
     */
    fun getStableSongId(song: QQSearchApi.QQSong): String =
        if (song.songmid.isNotBlank()) "qq_${song.songmid}" else "kw_${song.id}"

    /**
     * 播放某首歌曲（与落雪 lx-music 同款防风控方案）：
     * 1. 官方 QQ 音源：songmid 可用时，走落雪 JS 引擎 tx 多代理竞速（真 QQ 音源）
     * 2. 兜底：oiapi 酷我 br 尝试链（1=FLAC, 5=320k, 7=128k）
     * 3. 兜底：落雪 JS 引擎的酷我(kw)音源
     */
    fun playSong(
        song: QQSearchApi.QQSong,
        onUrlReady: (String, String, String, String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("QQMusic playSong: '${song.title}' - '${song.singer}' songmid=${song.songmid}")

            val quality = try {
                userPreferencesRepository.musicQualityFlow.first().lxValue
            } catch (_: Exception) {
                "320k"
            }
            val brs = when (quality) {
                "24bit", "flac" -> listOf(1, 5, 7) // FLAC → 320k → 128k
                "320k" -> listOf(5, 7)
                "128k" -> listOf(7)
                else -> listOf(5, 7)
            }
            Timber.d("QQMusic playSong: quality=$quality brChain=$brs")

            var playUrl: String? = null

            // 1. 官方 QQ 音源（落雪 tx 多代理竞速）
            if (song.songmid.isNotBlank()) {
                playUrl = lxTxPlayUrl(song, quality)
                Timber.d("QQMusic playSong: tx result=${playUrl?.take(80)}")
            }

            // 2. 兜底：oiapi 酷我
            if (playUrl.isNullOrEmpty()) {
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
            }

            // 3. 兜底：落雪 kw 音源
            if (playUrl.isNullOrEmpty()) {
                // 兜底：落雪 kw 音源（仅引擎已加载时尝试，避免空引擎白白等待）
                Timber.d("QQMusic playSong: all above failed, fallback to lx kw")
                playUrl = lxFallbackPlayUrl(song, quality)
            }

            if (!playUrl.isNullOrEmpty()) {
                onUrlReady(playUrl, song.title, song.singer, song.cover, getStableSongId(song))
            } else {
                Timber.e("QQMusic playSong: ALL sources failed for '${song.title}'")
                onUrlReady("", song.title, song.singer, song.cover, getStableSongId(song))
            }
        }
    }

    /** 官方 QQ 音源取播放链接：落雪 JS 引擎 tx 多代理竞速（质量链递减） */
    private suspend fun lxTxPlayUrl(song: QQSearchApi.QQSong, quality: String): String? {
        if (!lxJsEngine.isReady()) return null
        val songMap = mapOf(
            "id" to song.songmid,
            "songmid" to song.songmid,
            "mid" to song.songmid,
            "strMediaMid" to song.songmid,
            "name" to song.title,
            "singer" to song.singer,
            "artists" to song.singer,
            "album" to song.album,
            "albumName" to song.album,
            "duration" to song.durationSec.toLong(),
            "cover" to song.cover,
            "pic" to song.cover
        )
        val chain = linkedSetOf(quality, "flac", "320k", "128k")
        for (q in chain) {
            val u = runCatching { lxJsEngine.getPlayUrl("tx", songMap, q) }.getOrNull()
            if (!u.isNullOrBlank()) {
                Timber.d("QQMusic lxTx: got url quality=$q")
                return u
            }
        }
        return null
    }

    /** 落雪 kw 音源兜底取播放链接（质量链递减） */
    private suspend fun lxFallbackPlayUrl(song: QQSearchApi.QQSong, quality: String): String? {
        if (!lxJsEngine.isReady()) return null
        val songMap = mapOf(
            "id" to song.id.toString(),
            "vid" to song.id.toString(),
            "songmid" to song.id.toString(),
            "hash" to (song.rid.ifBlank { song.id.toString() }),
            "name" to song.title,
            "singer" to song.singer,
            "artists" to song.singer,
            "album" to song.album,
            "albumName" to song.album,
            "duration" to song.durationSec.toLong(),
            "cover" to song.cover,
            "pic" to song.cover
        )
        val chain = linkedSetOf(quality, "flac", "320k", "128k")
        for (q in chain) {
            val u = runCatching { lxJsEngine.getPlayUrl("kw", songMap, q) }.getOrNull()
            if (!u.isNullOrBlank()) {
                Timber.d("QQMusic lxFallback: got url quality=$q")
                return u
            }
        }
        return null
    }

    /** 落雪搜索结果 → QQSong（songmid 填充，供官方 tx 音源直连播放） */
    private fun LxSongInfo.toQQSong(): QQSearchApi.QQSong? {
        if (name.isBlank()) return null
        return QQSearchApi.QQSong(
            id = id.toLongOrNull() ?: id.hashCode().toLong().and(0x7fffffff),
            rid = id,
            songmid = songmid.ifBlank { "" },
            title = name,
            album = albumName,
            singer = singer,
            cover = pic,
            durationSec = duration.toInt(),
            quality = ""
        )
    }
}
