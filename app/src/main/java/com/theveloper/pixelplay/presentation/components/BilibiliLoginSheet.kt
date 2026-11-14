package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.utils.localizedStringResource
import com.theveloper.pixelplay.utils.AppLocaleManager

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.bilibili.BilibiliCaptchaChallenge
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BilibiliLoginEntryPoint {
    fun bilibiliSearchApi(): BilibiliSearchApi
    fun bilibiliRepository(): BilibiliRepository
}

private enum class BilibiliAuthMode { Phone, Qr }

/**
 * B 站登录页（全屏覆盖，视觉对齐 Telegram 登录页）。
 * 默认手机号登录（短信验证码两步），可切换扫码登录：
 *   1. 手机号：/x/passport-login/web/sms/send 发送验证码
 *   2. 验证码：/x/passport-login/web/sms/login 换取会话 cookie
 *   扫码：/x/passport-login/web/qrcode/generate + poll
 * 登录成功后保存会话 cookie 并拉取 nav 用户信息（昵称/头像/uid）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliLoginSheet(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, BilibiliLoginEntryPoint::class.java)
    }
    val searchApi = entryPoint.bilibiliSearchApi()
    val repository = entryPoint.bilibiliRepository()

    // —— 登录方式与步骤 ——
    var authMode by remember { mutableStateOf(BilibiliAuthMode.Phone) }
    var phoneStep by remember { mutableIntStateOf(0) } // 0=手机号 1=验证码
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var loginSucceeded by remember { mutableStateOf(false) }

    // —— 手机号登录 ——
    var cid by remember { mutableStateOf("86") }
    var tel by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var smsCaptchaKey by remember { mutableStateOf("") } // 发送验证码时返回，登录时校验
    var resendCountdown by remember { mutableIntStateOf(0) }
    // 极验人机验证：当前 challenge 与完成回调（resolveCaptcha 挂起等待）
    var activeCaptcha by remember { mutableStateOf<BilibiliCaptchaChallenge?>(null) }
    var captchaCallback by remember { mutableStateOf<((GeetestResult?) -> Unit)?>(null) }

    // —— 扫码登录 ——
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrcodeKey by remember { mutableStateOf("") }
    var qrStatusText by remember { mutableStateOf(AppLocaleManager.getString(R.string.ui_generating_qr_code)) }
    var isQrExpired by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    /** 登录成功后：保存 cookie → 拉取用户信息 → 回调关闭。仅在确认会话有效时才关闭页面，否则提示错误。 */
    suspend fun finalizeLogin(cookies: Map<String, String>) {
        loginSucceeded = true
        isLoading = true
        loadingMessage = AppLocaleManager.getString(R.string.ui_residue_kugou_login_fetching_info)
        var success = false
        try {
            Timber.d("Bilibili finalizeLogin: ${cookies.size} cookies from login")
            if (cookies.isNotEmpty()) repository.updateCookies(cookies)
            success = repository.isLoggedIn
            Timber.d("Bilibili finalizeLogin: isLoggedIn=$success")
            if (success) {
                val cookieHeader = repository.getCookieHeader()
                val userInfo = cookieHeader.ifBlank { null }?.let {
                    withContext(Dispatchers.IO) { searchApi.getNavUserInfo(it) }
                }
                if (userInfo != null) {
                    repository.updateUserInfo(userInfo.uid, userInfo.uname, userInfo.face)
                    Timber.d("Bilibili finalizeLogin: nav ok, uname=${userInfo.uname}")
                } else {
                    Timber.w("Bilibili finalizeLogin: nav returned null, 使用默认昵称")
                }
                loadingMessage = AppLocaleManager.getString(R.string.ui_residue_kugou_login_success)
            }
        } catch (t: Throwable) {
            Timber.e(t, "Bilibili login finalize failed")
            success = false
        }
        if (success) {
            delay(500)
            onLoginSuccess()
        } else {
            isLoading = false
            loginSucceeded = false
            inlineError = if (cookies.isEmpty()) {
                AppLocaleManager.getString(R.string.ui_sign_in_failed_no_session_cookie_returned_please_try_again)
            } else {
                AppLocaleManager.getString(R.string.ui_sign_in_failed_invalid_session_please_try_again)
            }
        }
    }

    suspend fun refreshQrCode() {
        isQrExpired = false
        qrStatusText = AppLocaleManager.getString(R.string.ui_generating_qr_code)
        qrBitmap = null
        qrcodeKey = ""
        val result = withContext(Dispatchers.IO) { searchApi.generateLoginQrCode() }
        if (result == null || result.qrcodeKey.isBlank()) {
            qrStatusText = AppLocaleManager.getString(R.string.ui_could_not_generate_qr_code_check_your_connection_and_try)
            isQrExpired = true
            return
        }
        qrcodeKey = result.qrcodeKey
        qrBitmap = generateQrBitmap(result.url)
        qrStatusText = AppLocaleManager.getString(R.string.ui_scan_with_the_bilibili_app_to_sign_in)
    }

    // 切换到扫码模式 / 点击刷新时重新生成二维码
    LaunchedEffect(authMode, refreshTrigger) {
        if (authMode == BilibiliAuthMode.Qr) refreshQrCode()
    }

    // 轮询扫码状态（成功/超时后停止）
    LaunchedEffect(qrcodeKey, authMode) {
        if (authMode != BilibiliAuthMode.Qr || qrcodeKey.isBlank()) return@LaunchedEffect
        var polls = 0
        while (!isLoading && !loginSucceeded && polls < 90) {
            delay(2000)
            polls++
            if (isQrExpired) break
            val poll = withContext(Dispatchers.IO) { searchApi.pollLoginQrCode(qrcodeKey) }
            Timber.d("Bilibili QR poll #$polls: code=${poll.code} success=${poll.success} cookies=${poll.cookies.size} msg=${poll.message}")
            when {
                poll.success -> {
                    finalizeLogin(poll.cookies)
                    return@LaunchedEffect
                }
                poll.code == 86090 -> qrStatusText = AppLocaleManager.getString(R.string.ui_code_scanned_confirm_sign_in_on_your_phone)
                poll.code == 86038 -> {
                    qrStatusText = AppLocaleManager.getString(R.string.ui_qr_code_expired)
                    isQrExpired = true
                }
                poll.code == 86101 -> qrStatusText = AppLocaleManager.getString(R.string.ui_scan_with_the_bilibili_app_to_sign_in)
                else -> if (poll.message.isNotBlank() && poll.code != -1) {
                    qrStatusText = poll.message
                }
            }
        }
    }

    // 重新发送验证码倒计时
    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1000)
            resendCountdown--
        }
    }

    fun sendSms() {
        if (tel.isBlank()) {
            Toast.makeText(context, AppLocaleManager.getString(R.string.ui_residue_kugou_login_phone_placeholder), Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            // 极验人机验证：首次无 gee 参数调用；若 B 站要求人机验证，
            // 弹出 GeetestCaptchaDialog，验证完成后携带 gee_* 参数重发
            var recaptcha: Pair<BilibiliCaptchaChallenge, GeetestResult>? = null
            while (true) {
                isLoading = true
                loadingMessage = AppLocaleManager.getString(R.string.ui_residue_qq_login_loading_sending)
                inlineError = null
                val result = withContext(Dispatchers.IO) {
                    searchApi.sendSmsCode(
                        cid, tel,
                        geeChallenge = recaptcha?.second?.geetestChallenge,
                        geeValidate = recaptcha?.second?.geetestValidate,
                        geeSeccode = recaptcha?.second?.geetestSeccode,
                        recaptchaToken = recaptcha?.first?.token
                    )
                }
                when {
                    result.success -> {
                        isLoading = false
                        smsCaptchaKey = result.captchaKey
                        phoneStep = 1
                        resendCountdown = 60
                        Toast.makeText(context, AppLocaleManager.getString(R.string.ui_verification_code_sent_check_your_messages), Toast.LENGTH_SHORT).show()
                        break
                    }
                    result.captchaRequired && result.challenge != null -> {
                        isLoading = false
                        val challenge = result.challenge
                        // 弹出极验验证对话框，等待用户完成或取消
                        val deferred = CompletableDeferred<GeetestResult?>()
                        activeCaptcha = challenge
                        captchaCallback = { r -> deferred.complete(r) }
                        val solved = try {
                            deferred.await()
                        } finally {
                            activeCaptcha = null
                            captchaCallback = null
                        }
                        if (solved == null) break // 用户取消人机验证
                        recaptcha = challenge to solved
                    }
                    else -> {
                        isLoading = false
                        inlineError = result.message
                        break
                    }
                }
            }
        }
    }

    fun verifySms() {
        if (smsCode.isBlank()) {
            Toast.makeText(context, AppLocaleManager.getString(R.string.ui_enter_the_verification_code), Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isLoading = true
            loadingMessage = AppLocaleManager.getString(R.string.ui_signing_in)
            inlineError = null
            val result = withContext(Dispatchers.IO) {
                searchApi.loginWithSms(cid, tel, smsCode, smsCaptchaKey)
            }
            if (result.success) {
                finalizeLogin(result.cookies)
            } else {
                isLoading = false
                inlineError = result.message.ifBlank { AppLocaleManager.getString(R.string.ui_incorrect_verification_code) }
            }
        }
    }

    // ⚡ 不再拦截返回键：放行系统预测返回（手势跟手），松手后关闭登录页

    // 极验人机验证对话框（B 站短信风控，实现对齐 PiliPlus GeetestWebviewDialog）
    activeCaptcha?.let { challenge ->
        GeetestCaptchaDialog(
            searchApi = searchApi,
            challenge = challenge,
            onFinished = { result ->
                captchaCallback?.invoke(result)
                captchaCallback = null
                activeCaptcha = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedStringResource(R.string.ui_connect_to_bilibili),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BilibiliBrandingHeader()
            Spacer(modifier = Modifier.height(20.dp))

            if (authMode == BilibiliAuthMode.Phone) {
                BilibiliStepIndicator(currentStep = phoneStep, totalSteps = 2)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isLoading) {
                BilibiliLoadingCard(message = loadingMessage)
                Spacer(modifier = Modifier.height(16.dp))
            }

            inlineError?.let { errorText ->
                BilibiliInlineErrorCard(message = errorText, onDismiss = { inlineError = null })
                Spacer(modifier = Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = authMode to phoneStep,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                },
                label = "bilibiliLoginTransition"
            ) { (mode, step) ->
                when (mode) {
                    BilibiliAuthMode.Phone -> {
                        if (step == 0) {
                            BilibiliPhoneNumberField(
                                cid = cid,
                                onCidChanged = { cid = it },
                                tel = tel,
                                onTelChanged = { tel = it },
                                isLoading = isLoading,
                                onSend = ::sendSms
                            )
                        } else {
                            BilibiliSmsCodeField(
                                code = smsCode,
                                onCodeChanged = { smsCode = it },
                                isLoading = isLoading,
                                onVerify = ::verifySms,
                                onEditPhone = { phoneStep = 0 },
                                onResend = ::sendSms,
                                resendCountdown = resendCountdown
                            )
                        }
                    }
                    BilibiliAuthMode.Qr -> {
                        BilibiliQrLoginContent(
                            qrBitmap = qrBitmap,
                            statusText = qrStatusText,
                            isExpired = isQrExpired,
                            loginSucceeded = loginSucceeded,
                            onRefresh = { refreshTrigger++ }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    authMode = if (authMode == BilibiliAuthMode.Phone) BilibiliAuthMode.Qr else BilibiliAuthMode.Phone
                    inlineError = null
                }
            ) {
                Text(
                    text = if (authMode == BilibiliAuthMode.Phone) localizedStringResource(R.string.ui_sign_in_with_qr_code) else localizedStringResource(R.string.ui_sign_in_with_phone_number),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 品牌头部（对齐 Telegram 登录页）
// ─────────────────────────────────────────────────────────

@Composable
private fun BilibiliBrandingHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF0F4)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_bilibili),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = localizedStringResource(R.string.ui_connect_to_bilibili),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = localizedStringResource(R.string.ui_sign_in_to_post_comments_reply_like_report_and_sync),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BilibiliStepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val isCurrent = index == currentStep

            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "bilibiliStepScale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(if (isCurrent) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun BilibiliLoadingCard(message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.ifBlank { AppLocaleManager.getString(R.string.ui_residue_qq_login_loading) },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BilibiliInlineErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(imageVector = Icons.Rounded.Clear, contentDescription = localizedStringResource(R.string.ui_residue_lyrics_more_vibrant_off))
            }
        }
    }
}

@Composable
private fun BilibiliAuthStepHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun BilibiliExpressiveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bilibiliButtonScale"
    )

    MediumExtendedFloatingActionButton(
        text = {
            Text(
                text = if (loading) localizedStringResource(R.string.ui_residue_qq_login_loading) else text,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        },
        icon = {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.width(28.dp))
            } else {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
            }
        },
        onClick = {
            if (enabled && !loading) onClick()
        },
        expanded = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    )
}

// ─────────────────────────────────────────────────────────
// 手机号登录：手机号步骤
// ─────────────────────────────────────────────────────────

@Composable
private fun BilibiliPhoneNumberField(
    cid: String,
    onCidChanged: (String) -> Unit,
    tel: String,
    onTelChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    val numFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    var codeFieldFocused by remember { mutableStateOf(false) }
    var numFieldFocused by remember { mutableStateOf(false) }
    val isActive = codeFieldFocused || numFieldFocused

    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "bilibiliPhoneBorder"
    )
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BilibiliAuthStepHeader(
            icon = Icons.Rounded.Phone,
            title = AppLocaleManager.getString(R.string.ui_residue_auth_phone_number_label),
            subtitle = AppLocaleManager.getString(R.string.ui_enter_the_phone_number_linked_to_your_bilibili_account_to)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(containerColor)
                .border(BorderStroke(if (isActive) 2.dp else 1.dp, borderColor), shape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                BasicTextField(
                    value = cid,
                    onValueChange = { raw ->
                        if (isLoading) return@BasicTextField
                        val digits = raw.filter(Char::isDigit).take(4)
                        onCidChanged(digits)
                        if (digits.isNotEmpty() && digits.length >= 2) numFocusRequester.requestFocus()
                    },
                    modifier = Modifier
                        .width(40.dp)
                        .focusRequester(codeFocusRequester)
                        .onFocusChanged { codeFieldFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { numFocusRequester.requestFocus() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (cid.isEmpty()) {
                                Text(
                                    text = "86",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                )
                            }
                            inner()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = tel,
                    onValueChange = { raw ->
                        if (isLoading) return@BasicTextField
                        onTelChanged(raw.filter(Char::isDigit).take(15))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(numFocusRequester)
                        .onFocusChanged { numFieldFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!isLoading) onSend()
                    }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (tel.isEmpty()) {
                                Text(
                                    text = localizedStringResource(R.string.ui_residue_auth_phone_number_label),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        BilibiliExpressiveButton(
            text = AppLocaleManager.getString(R.string.ui_get_verification_code),
            onClick = onSend,
            enabled = cid.isNotBlank() && tel.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}

// ─────────────────────────────────────────────────────────
// 手机号登录：验证码步骤
// ─────────────────────────────────────────────────────────

@Composable
private fun BilibiliSmsCodeField(
    code: String,
    onCodeChanged: (String) -> Unit,
    isLoading: Boolean,
    onVerify: () -> Unit,
    onEditPhone: () -> Unit,
    onResend: () -> Unit,
    resendCountdown: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BilibiliAuthStepHeader(
            icon = Icons.Rounded.Sms,
            title = AppLocaleManager.getString(R.string.ui_residue_kugou_login_code_label),
            subtitle = AppLocaleManager.getString(R.string.ui_enter_the_verification_code_from_your_text_message)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChanged(it.filter(Char::isDigit).take(8)) },
            label = { Text(localizedStringResource(R.string.ui_residue_kugou_login_code_label), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (!isLoading) onVerify()
            }),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onEditPhone, enabled = !isLoading) {
                Text(
                    text = localizedStringResource(R.string.ui_residue_kugou_login_edit_phone),
                    fontFamily = GoogleSansRounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onResend, enabled = resendCountdown <= 0 && !isLoading) {
                Text(
                    text = if (resendCountdown > 0) localizedStringResource(R.string.ui_resend_in_s, (resendCountdown).toString()) else localizedStringResource(R.string.ui_resend),
                    fontFamily = GoogleSansRounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        BilibiliExpressiveButton(
            text = AppLocaleManager.getString(R.string.ui_residue_auth_phone_login_action),
            onClick = onVerify,
            enabled = code.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}

// ─────────────────────────────────────────────────────────
// 扫码登录
// ─────────────────────────────────────────────────────────

@Composable
private fun BilibiliQrLoginContent(
    qrBitmap: Bitmap?,
    statusText: String,
    isExpired: Boolean,
    loginSucceeded: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 二维码（白色底 + 圆角卡片）
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = qrBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = AppLocaleManager.getString(R.string.ui_bilibili_sign_in_qr_code),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                )
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                loginSucceeded -> MaterialTheme.colorScheme.primary
                isExpired -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (loginSucceeded || isExpired) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isExpired || qrBitmap == null) {
            BilibiliExpressiveButton(
                text = AppLocaleManager.getString(R.string.ui_residue_kugou_login_qr_refresh),
                onClick = onRefresh,
                enabled = !loginSucceeded,
                loading = false
            )
        } else {
            Text(
                text = localizedStringResource(R.string.ui_open_bilibili_my_page_scan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 用 ZXing 将字符串内容生成为二维码 Bitmap */
private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        Timber.e(e, "Bilibili QR generate failed")
        null
    }
}
