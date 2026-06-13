package com.theveloper.pixelplay.presentation.gdrive.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.GDriveFolderEntity
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GDriveDashboardViewModel @Inject constructor(
    private val repository: GDriveRepository
) : ViewModel() {

    val folders: StateFlow<List<GDriveFolderEntity>> = repository.getFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val userEmail: String? get() = repository.userEmail
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    init {
        // Auto-sync folders when dashboard opens
        syncAllFoldersAndSongs()
    }

    fun syncAllFoldersAndSongs() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing all folders..."
            val result = repository.syncAllFoldersAndSongs()
            result.fold(
                onSuccess = { count ->
                    _syncMessage.value = "Synced $count songs"
                },
                onFailure = {
                    _syncMessage.value = "Sync failed: ${it.message}"
                }
            )
            _isSyncing.value = false
        }
    }

    fun syncFolder(folderId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing folder..."
            val result = repository.syncFolderSongs(folderId)
            result.fold(
                onSuccess = { count ->
                    _syncMessage.value = "Synced $count songs"
                },
                onFailure = {
                    _syncMessage.value = "Sync failed: ${it.message}"
                }
            )
            _isSyncing.value = false
        }
    }

    fun removeFolder(folderId: String) {
        viewModelScope.launch {
            repository.removeFolder(folderId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
