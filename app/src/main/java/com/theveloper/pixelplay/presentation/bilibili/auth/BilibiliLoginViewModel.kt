package com.theveloper.pixelplay.presentation.bilibili.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BilibiliLoginViewModel @Inject constructor(
    private val repository: BilibiliRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BilibiliLoginState>(BilibiliLoginState.Idle)
    val state: StateFlow<BilibiliLoginState> = _state.asStateFlow()

    fun processCookies(cookieJson: String) {
        if (_state.value is BilibiliLoginState.Loading) return

        _state.value = BilibiliLoginState.Loading
        viewModelScope.launch {
            val result = repository.loginWithCookies(cookieJson)
            _state.value = when {
                result.isSuccess -> BilibiliLoginState.Success(result.getOrThrow())
                else -> BilibiliLoginState.Error(result.exceptionOrNull()?.message ?: "登录失败")
            }
        }
    }

    fun clearError() {
        if (_state.value is BilibiliLoginState.Error) {
            _state.value = BilibiliLoginState.Idle
        }
    }
}