package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.ApiProviderPreferences
import com.theveloper.pixelplay.data.preferences.LyricsSourceKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「API 管理」页面的状态：歌词来源与歌曲信息补全来源开关 */
data class ApiSettingsUiState(
    val neteaseLyricsEnabled: Boolean = true,
    val amllLyricsEnabled: Boolean = true,
    val lrclibLyricsEnabled: Boolean = true,
    val builtInLyricsEnabled: Boolean = true,
    val bilibiliLyricsEnabled: Boolean = true,
    /** 歌词来源顺序（列表顺序即优先级，可在 API 管理页上移/下移） */
    val lyricsSourceOrder: List<LyricsSourceKey> = LyricsSourceKey.DEFAULT_ORDER,
    val musicBrainzEnabled: Boolean = true,
    val deezerArtistEnabled: Boolean = true
)

@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    private val prefs: ApiProviderPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiSettingsUiState())
    val uiState: StateFlow<ApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.neteaseLyricsEnabled.collect { v -> _uiState.update { it.copy(neteaseLyricsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.amllLyricsEnabled.collect { v -> _uiState.update { it.copy(amllLyricsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.lrclibLyricsEnabled.collect { v -> _uiState.update { it.copy(lrclibLyricsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.builtInLyricsEnabled.collect { v -> _uiState.update { it.copy(builtInLyricsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.bilibiliLyricsEnabled.collect { v -> _uiState.update { it.copy(bilibiliLyricsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.lyricsSourceOrder.collect { v -> _uiState.update { it.copy(lyricsSourceOrder = v) } }
        }
        viewModelScope.launch {
            prefs.musicBrainzEnabled.collect { v -> _uiState.update { it.copy(musicBrainzEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.deezerArtistEnabled.collect { v -> _uiState.update { it.copy(deezerArtistEnabled = v) } }
        }
    }

    fun setNeteaseLyricsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setNeteaseLyricsEnabled(enabled) }

    fun setAmllLyricsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setAmllLyricsEnabled(enabled) }

    fun setLrclibLyricsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setLrclibLyricsEnabled(enabled) }

    fun setBuiltInLyricsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setBuiltInLyricsEnabled(enabled) }

    fun setBilibiliLyricsEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setBilibiliLyricsEnabled(enabled) }

    /** 把某个歌词来源上移/下移一位（[delta] = -1 上移，+1 下移），顺序即优先级 */
    fun moveLyricsSource(key: LyricsSourceKey, delta: Int) {
        val current = _uiState.value.lyricsSourceOrder
        val from = current.indexOf(key)
        val to = from + delta
        if (from < 0 || to < 0 || to >= current.size) return
        val reordered = current.toMutableList().apply {
            add(to, removeAt(from))
        }
        viewModelScope.launch { prefs.setLyricsSourceOrder(reordered) }
    }

    /** 保存拖拽后的来源顺序（列表顺序即优先级） */
    fun setLyricsSourceOrder(order: List<LyricsSourceKey>) =
        viewModelScope.launch { prefs.setLyricsSourceOrder(order) }

    /** 恢复默认来源顺序 */
    fun resetLyricsSourceOrder() {
        viewModelScope.launch { prefs.setLyricsSourceOrder(LyricsSourceKey.DEFAULT_ORDER) }
    }

    fun setMusicBrainzEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setMusicBrainzEnabled(enabled) }

    fun setDeezerArtistEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setDeezerArtistEnabled(enabled) }
}
