package com.theveloper.pixelplay.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Process-wide signal for whether the watch UI is actively interactive.
 *
 * Polling loops, position-update jobs and continuous animations should gate on
 * [isInteractive] so they pause as soon as the activity moves to the background
 * or the watch enters ambient (always-on display) mode. Wear devices have very
 * small batteries, so any work that keeps the CPU/GPU/radio awake while the
 * user is not actually looking at the screen translates directly into drain.
 */
object WearLifecycleState {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    /**
     * True when the activity is foreground AND the watch is not in ambient mode.
     * This is the right signal for "should we keep the CPU busy?".
     */
    val isInteractive: Flow<Boolean> = combine(_isForeground, _isAmbient) { fg, ambient ->
        fg && !ambient
    }.distinctUntilChanged()

    /** Snapshot read for non-suspending call sites. */
    val isInteractiveNow: Boolean
        get() = _isForeground.value && !_isAmbient.value

    fun setForeground(value: Boolean) {
        _isForeground.value = value
    }

    fun setAmbient(value: Boolean) {
        _isAmbient.value = value
    }
}
