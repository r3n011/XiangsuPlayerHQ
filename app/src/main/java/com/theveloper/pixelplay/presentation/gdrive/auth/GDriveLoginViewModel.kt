package com.theveloper.pixelplay.presentation.gdrive.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class GDriveLoginState {
    object Idle : GDriveLoginState()
    object Loading : GDriveLoginState()
    data class LoggedIn(val email: String) : GDriveLoginState()
    data class FolderSetup(
        val folders: List<FolderItem>,
        val currentPath: List<FolderItem>, // breadcrumb
        val isLoading: Boolean = false
    ) : GDriveLoginState()
    object Success : GDriveLoginState()
    data class Error(val message: String) : GDriveLoginState()
}

data class FolderItem(
    val id: String,
    val name: String,
    val isFolder: Boolean = true
)

@HiltViewModel
class GDriveLoginViewModel @Inject constructor(
    private val repository: GDriveRepository
) : ViewModel() {

    private val _state = MutableStateFlow<GDriveLoginState>(GDriveLoginState.Idle)
    val state: StateFlow<GDriveLoginState> = _state.asStateFlow()

    /**
     * Process the credential from Credential Manager.
     * Exchanges the server auth code for access + refresh tokens.
     */
    fun processCredential(idToken: String, serverAuthCode: String?) {
        _state.value = GDriveLoginState.Loading
        viewModelScope.launch {
            val result = repository.loginWithCredential(idToken, serverAuthCode)
            result.fold(
                onSuccess = { email ->
                    _state.value = GDriveLoginState.LoggedIn(email)
                    // Automatically transition to folder setup
                    browseFolders("root")
                },
                onFailure = { error ->
                    Timber.e(error, "GDrive login failed")
                    _state.value = GDriveLoginState.Error(error.message ?: "Login failed")
                }
            )
        }
    }

    private val breadcrumb = mutableListOf(FolderItem("root", "My Drive"))

    fun browseFolders(parentId: String) {
        viewModelScope.launch {
            _state.value = GDriveLoginState.FolderSetup(
                folders = emptyList(),
                currentPath = breadcrumb.toList(),
                isLoading = true
            )
            val result = repository.listDriveFolders(parentId)
            result.fold(
                onSuccess = { folders ->
                    _state.value = GDriveLoginState.FolderSetup(
                        folders = folders.map { FolderItem(it.id, it.name) },
                        currentPath = breadcrumb.toList(),
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _state.value = GDriveLoginState.Error(
                        "Failed to list folders: ${error.message}"
                    )
                }
            )
        }
    }

    fun navigateIntoFolder(folder: FolderItem) {
        breadcrumb.add(folder)
        browseFolders(folder.id)
    }

    fun navigateBack(): Boolean {
        if (breadcrumb.size <= 1) return false
        breadcrumb.removeAt(breadcrumb.lastIndex)
        browseFolders(breadcrumb.last().id)
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        while (breadcrumb.size > index + 1) {
            breadcrumb.removeAt(breadcrumb.lastIndex)
        }
        browseFolders(breadcrumb.last().id)
    }

    /**
     * Create a "PixelPlay Music" folder in the current directory.
     */
    fun createMusicFolder() {
        val parentId = breadcrumb.lastOrNull()?.id ?: "root"
        viewModelScope.launch {
            _state.value = when (val current = _state.value) {
                is GDriveLoginState.FolderSetup -> current.copy(isLoading = true)
                else -> GDriveLoginState.Loading
            }
            val result = repository.createMusicFolder(parentId)
            result.fold(
                onSuccess = { (folderId, folderName) ->
                    selectFolder(folderId, folderName)
                },
                onFailure = { error ->
                    _state.value = GDriveLoginState.Error(
                        "Failed to create folder: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Select an existing folder as the music source.
     */
    fun selectFolder(folderId: String, folderName: String) {
        viewModelScope.launch {
            _state.value = GDriveLoginState.Loading
            repository.addFolder(folderId, folderName)
            // Trigger initial sync
            val result = repository.syncFolderSongs(folderId)
            result.fold(
                onSuccess = { count ->
                    Timber.d("GDrive folder setup complete: synced $count songs")
                    _state.value = GDriveLoginState.Success
                },
                onFailure = { error ->
                    // Still mark as success since the folder was added
                    Timber.w(error, "Initial sync failed, but folder was added")
                    _state.value = GDriveLoginState.Success
                }
            )
        }
    }
}
