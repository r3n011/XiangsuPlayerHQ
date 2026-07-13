package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateHolder @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val powerManager: PowerManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    val isScreenInteractive: Boolean
        get() = powerManager.isInteractive

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _isForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                _isForeground.value = false
            }
        })
    }
}