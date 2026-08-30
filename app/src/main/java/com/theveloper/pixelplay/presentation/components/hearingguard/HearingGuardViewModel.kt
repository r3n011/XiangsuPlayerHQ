package com.theveloper.pixelplay.presentation.components.hearingguard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.hearingguard.HearingGuardConfig
import com.theveloper.pixelplay.data.hearingguard.HearingGuardManager
import com.theveloper.pixelplay.data.hearingguard.HearingGuardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HearingGuardViewModel @Inject constructor(
    private val manager: HearingGuardManager
) : ViewModel() {

    val state: StateFlow<HearingGuardState> = manager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HearingGuardState())

    fun setConfig(config: HearingGuardConfig) {
        viewModelScope.launch { manager.setConfig(config) }
    }

    fun clearConfig() {
        viewModelScope.launch { manager.clearConfig() }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(enabled) }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        manager.onPlaybackStateChanged(playing)
    }

    fun onRestConfirmed() {
        manager.onRestConfirmed()
    }

    fun onContinueListening() {
        manager.onContinueListening()
    }
}
