package com.theveloper.pixelplay.presentation.qqmusic.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class QqMusicLoginState {
    object Idle : QqMusicLoginState()
    data class Loading(val message: String) : QqMusicLoginState()
    data class Success(val nickname: String) : QqMusicLoginState()
    data class Error(val message: String) : QqMusicLoginState()
}

/** 手机号登录面板的输入 / 状态 */
data class QqMusicPhoneUiState(
    val phone: String = "",
    val code: String = "",
    val sendingCode: Boolean = false,
    val submitting: Boolean = false,
    val countdown: Int = 0,
    val codeSent: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class QqMusicLoginViewModel @Inject constructor(
    private val repository: QqMusicRepository
) : ViewModel() {

    private val _state = MutableStateFlow<QqMusicLoginState>(QqMusicLoginState.Idle)
    val state: StateFlow<QqMusicLoginState> = _state.asStateFlow()

    // ─── 手机号 + 短信验证码登录 ────────────────────────────────────────────────
    private val _phoneUi = MutableStateFlow(QqMusicPhoneUiState())
    val phoneUi: StateFlow<QqMusicPhoneUiState> = _phoneUi.asStateFlow()

    fun onPhoneChanged(value: String) {
        _phoneUi.update { it.copy(phone = value.filter(Char::isDigit).take(11), error = null) }
    }

    fun onCodeChanged(value: String) {
        _phoneUi.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }
    }

    fun sendPhoneCode() {
        val phone = _phoneUi.value.phone
        if (phone.length != 11) {
            _phoneUi.update { it.copy(error = "请输入正确的 11 位手机号") }
            return
        }
        if (_phoneUi.value.sendingCode || _phoneUi.value.countdown > 0) return

        _phoneUi.update { it.copy(sendingCode = true, error = null) }
        viewModelScope.launch {
            repository.sendPhoneLoginCode(phone).fold(
                onSuccess = {
                    _phoneUi.update { it.copy(sendingCode = false, codeSent = true, countdown = 60) }
                    startCountdown()
                },
                onFailure = { err ->
                    _phoneUi.update {
                        it.copy(sendingCode = false, error = err.message ?: "验证码发送失败")
                    }
                }
            )
        }
    }

    fun submitPhoneLogin() {
        val current = _phoneUi.value
        if (current.phone.length != 11) {
            _phoneUi.update { it.copy(error = "请输入正确的 11 位手机号") }
            return
        }
        if (current.code.isBlank()) {
            _phoneUi.update { it.copy(error = "请先输入验证码") }
            return
        }
        if (current.submitting) return

        _phoneUi.update { it.copy(submitting = true, error = null) }
        _state.value = QqMusicLoginState.Loading("正在登录 QQ 音乐…")
        viewModelScope.launch {
            repository.loginWithPhone(current.phone, current.code).fold(
                onSuccess = { nickname ->
                    _phoneUi.update { it.copy(submitting = false) }
                    _state.value = QqMusicLoginState.Success(nickname)
                },
                onFailure = { err ->
                    _phoneUi.update {
                        it.copy(submitting = false, error = err.message ?: "登录失败，请重试")
                    }
                    _state.value = QqMusicLoginState.Idle
                }
            )
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (_phoneUi.value.countdown > 0) {
                delay(1_000)
                _phoneUi.update { it.copy(countdown = (it.countdown - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun clearError() {
        if (_state.value is QqMusicLoginState.Error) {
            _state.value = QqMusicLoginState.Idle
        }
    }

    fun processCookies(cookieJson: String) {
        if (_state.value is QqMusicLoginState.Loading) return
        _state.value = QqMusicLoginState.Loading("Verifying QQ Music session...")

        viewModelScope.launch {
            val result = repository.loginWithCookies(cookieJson)
            result.fold(
                onSuccess = { nickname -> _state.value = QqMusicLoginState.Success(nickname) },
                onFailure = { err ->
                    _state.value = QqMusicLoginState.Error(
                        err.message ?: "QQ Music login failed"
                    )
                }
            )
        }
    }
}
