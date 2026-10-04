package com.theveloper.pixelplay.presentation.netease.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed class NeteaseLoginState {
    object Idle : NeteaseLoginState()
    data class Loading(val message: String) : NeteaseLoginState()
    data class Success(val nickname: String) : NeteaseLoginState()
    data class Error(val message: String) : NeteaseLoginState()
}

/** 验证码发送状态（手机号登录用） */
sealed class NeteaseCaptchaState {
    object Idle : NeteaseCaptchaState()
    object Sending : NeteaseCaptchaState()
    object Sent : NeteaseCaptchaState()
    data class Error(val message: String) : NeteaseCaptchaState()
}

@HiltViewModel
class NeteaseLoginViewModel @Inject constructor(
    private val repository: NeteaseRepository
) : ViewModel() {

    companion object {
        private const val COOKIE_LOGIN_TIMEOUT_MS = 25_000L
    }

    private val _state = MutableStateFlow<NeteaseLoginState>(NeteaseLoginState.Idle)
    val state: StateFlow<NeteaseLoginState> = _state.asStateFlow()

    private val _captchaState = MutableStateFlow<NeteaseCaptchaState>(NeteaseCaptchaState.Idle)
    val captchaState: StateFlow<NeteaseCaptchaState> = _captchaState.asStateFlow()

    fun clearError() {
        if (_state.value is NeteaseLoginState.Error) {
            _state.value = NeteaseLoginState.Idle
        }
    }

    fun clearCaptchaError() {
        if (_captchaState.value is NeteaseCaptchaState.Error) {
            _captchaState.value = NeteaseCaptchaState.Idle
        }
    }

    /** 发送短信验证码（手机号登录第一步） */
    fun sendCaptcha(phone: String, ctcode: String = "86") {
        if (_captchaState.value == NeteaseCaptchaState.Sending) return
        _captchaState.value = NeteaseCaptchaState.Sending
        viewModelScope.launch {
            repository.sendPhoneCaptcha(phone, ctcode).fold(
                onSuccess = {
                    _captchaState.value = NeteaseCaptchaState.Sent
                },
                onFailure = { error ->
                    _captchaState.value = NeteaseCaptchaState.Error(
                        error.message ?: "验证码发送失败，请稍后重试"
                    )
                }
            )
        }
    }

    /** 手机号 + 验证码登录（默认登录方式） */
    fun loginWithPhoneCaptcha(phone: String, captcha: String, ctcode: String = "86") {
        if (_state.value is NeteaseLoginState.Loading) return
        _state.value = NeteaseLoginState.Loading("正在使用手机号登录网易云…")
        viewModelScope.launch {
            val result = repository.loginWithPhoneCaptcha(phone, captcha, ctcode)
            result.fold(
                onSuccess = { nickname ->
                    _state.value = NeteaseLoginState.Success(nickname)
                },
                onFailure = { error ->
                    _state.value = NeteaseLoginState.Error(
                        error.message ?: "网易云登录失败"
                    )
                }
            )
        }
    }

    fun processCookies(cookieJson: String) {
        if (_state.value is NeteaseLoginState.Loading) return

        _state.value = NeteaseLoginState.Loading("Verifying session with NetEase...")
        viewModelScope.launch {
            val result = try {
                withTimeout(COOKIE_LOGIN_TIMEOUT_MS) {
                    repository.loginWithCookies(cookieJson)
                }
            } catch (_: TimeoutCancellationException) {
                Result.failure(
                    IllegalStateException(
                        "Verification timed out after ${COOKIE_LOGIN_TIMEOUT_MS / 1000}s. Try again."
                    )
                )
            }

            result.fold(
                onSuccess = { nickname ->
                    _state.value = NeteaseLoginState.Success(nickname)
                },
                onFailure = { error ->
                    _state.value = NeteaseLoginState.Error(
                        error.message ?: "NetEase login failed"
                    )
                }
            )
        }
    }
}
