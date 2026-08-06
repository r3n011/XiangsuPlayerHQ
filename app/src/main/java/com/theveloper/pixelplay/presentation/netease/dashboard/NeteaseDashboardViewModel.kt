package com.theveloper.pixelplay.presentation.netease.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NeteaseDashboardViewModel @Inject constructor(
    private val repository: NeteaseRepository
) : ViewModel() {

    val playlists: StateFlow<List<NeteasePlaylistEntity>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedPlaylistSongs: StateFlow<List<Song>> = _selectedPlaylistSongs.asStateFlow()

    val userNickname: String? get() = repository.userNickname
    val userAvatar: String? get() = repository.userAvatar
    
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    init {
        // Auto-sync playlists when the dashboard opens
        syncPlaylists()
    }

    fun syncAllPlaylistsAndSongs() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing all playlists and songs..."
            val result = repository.syncAllPlaylistsAndSongs()
            result.fold(
                onSuccess = { summary ->
                    _syncMessage.value = if (summary.failedPlaylistCount == 0) {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs"
                    } else {
                        "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs (${summary.failedPlaylistCount} failed)"
                    }
                },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing playlists..."
            val result = repository.syncUserPlaylists()
            result.fold(
                onSuccess = { _syncMessage.value = "Synced ${it.size} playlists" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    /**
     * 进入媒体库时的自动同步入口（节流 + 防重入）。
     * 内部由 repository 判断登录状态与上次同步间隔：
     * - 未登录 / 1 小时内已同步 → 跳过
     * - 否则全量同步歌单列表 + 歌曲
     */
    fun autoSyncOnLibraryEntry() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.autoSyncOnLibraryEntry()
            result.fold(
                onSuccess = { summary ->
                    _syncMessage.value = if (summary != null) {
                        if (summary.failedPlaylistCount == 0) {
                            "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs"
                        } else {
                            "Synced ${summary.playlistCount} playlists, ${summary.syncedSongCount} songs (${summary.failedPlaylistCount} failed)"
                        }
                    } else {
                        null // 节流跳过，不显示提示
                    }
                },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun syncPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing songs..."
            val result = repository.syncPlaylistSongs(playlistId)
            result.fold(
                onSuccess = { _syncMessage.value = "Synced $it songs" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            repository.getPlaylistSongs(playlistId).collect { songs ->
                _selectedPlaylistSongs.value = songs
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
