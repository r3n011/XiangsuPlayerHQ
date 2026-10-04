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
    /** 风控要求安全验证时的 `securityURL`：非空时面板上出现「去完成安全验证」入口 */
    val securityChallengeUrl: String? = null,
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

        _phoneUi.update { it.copy(sendingCode = true, error = null, securityChallengeUrl = null) }
        viewModelScope.launch {
            repository.sendPhoneLoginCode(phone).fold(
                onSuccess = {
                    _phoneUi.update { it.copy(sendingCode = false, codeSent = true, countdown = 60) }
                    startCountdown()
                },
                onFailure = { err ->
                    // ⚡ 风控（code=20276）会带一个 securityURL：不要在界面上直出那一长串，
                    //    改为可操作的提示 + 「去完成安全验证」入口（在应用内 WebView 完成后再重试）。
                    val challenge = err as? com.theveloper.pixelplay.data.remote.qqmusic.QqMusicSecurityChallengeException
                    _phoneUi.update {
                        it.copy(
                            sendingCode = false,
                            error = if (challenge != null) {
                                "QQ 音乐要求先完成安全验证：点下方按钮完成验证，再回来重新获取验证码"
                            } else {
                                err.message ?: "验证码发送失败"
                            },
                            securityChallengeUrl = challenge?.securityUrl?.takeIf { url -> url.isNotBlank() }
                        )
                    }
                }
            )
        }
    }

    /**
     * 把安全验证 WebView 拿到的 cookie 合并进手机号登录链路。
     * 风控验证态靠 cookie 延续，合并后重新获取验证码才会通过。
     */
    fun syncExternalCookies(cookieHeader: String) {
        repository.mergePhoneAuthCookies(cookieHeader)
    }

    /**
     * 安全验证完成：合并 cookie + UA，清掉入口，并**自动重发一次验证码**。
     *
     * ⚡ 以前只合并 cookie、不自动重试，用户完成验证后什么都没发生（「验证了也没用，验证码呢」）。
     *    这里验证一完成就立刻带着验证态重发，成功即进入验证码步骤。
     */
    fun onSecurityVerified(cookieHeader: String, userAgent: String) {
        if (cookieHeader.isNotBlank()) repository.mergePhoneAuthCookies(cookieHeader)
        if (userAgent.isNotBlank()) repository.setPhoneAuthUserAgent(userAgent)
        _phoneUi.update { it.copy(securityChallengeUrl = null, error = null, countdown = 0) }
        sendPhoneCode()
    }

    /** 用户已完成（或放弃）安全验证：清掉入口，避免一直挂在界面上。 */
    fun clearSecurityChallenge() {
        _phoneUi.update { it.copy(securityChallengeUrl = null) }
    }

    /** 关掉内联错误卡片。 */
    fun clearPhoneError() {
        _phoneUi.update { it.copy(error = null) }
    }

    /** 重新打开安全验证弹窗（自动弹出的那个被用户关掉后，面板上的入口还能再进）。 */
    fun reopenSecurityCheck(url: String) {
        if (url.isBlank()) return
        _phoneUi.update { it.copy(securityChallengeUrl = url) }
    }

    /** 从「验证码」步骤退回「手机号」步骤（重新填手机号）。 */
    fun backToPhoneStep() {
        _phoneUi.update { it.copy(codeSent = false, error = null) }
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
                    // ⚡ 风控在「提交验证码登录」这一步同样可能拦截（code=20276 带 securityURL）。
                    //    之前只有发验证码那步处理了挑战，登录步只会把原始异常文本塞进错误卡片，
                    //    没有任何可操作入口 —— 表现就是「输入验证码后提交没有任何用，登录不了」。
                    //    这里对齐发码步：给出可操作提示 + 弹出安全验证 WebView（完成后自动重发验证码）。
                    val challenge = err as? com.theveloper.pixelplay.data.remote.qqmusic.QqMusicSecurityChallengeException
                    _phoneUi.update {
                        it.copy(
                            submitting = false,
                            error = if (challenge != null) {
                                "QQ 音乐要求完成安全验证：完成验证后会自动重发验证码，请用新验证码重新登录"
                            } else {
                                err.message ?: "登录失败，请重试"
                            },
                            securityChallengeUrl = challenge?.securityUrl?.takeIf { url -> url.isNotBlank() }
                        )
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
