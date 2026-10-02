package com.theveloper.pixelplay.presentation.kugou

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouRepository
import com.theveloper.pixelplay.data.kugou.KugouUserInfo
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/** 酷狗登录方式。 */
private enum class KugouAuthMode { Qr, Phone }

/**
 * 酷狗登录页（全屏，与 Telegram / 网易云 / B 站 / QQ 音乐的登录页同款结构）：
 * 顶栏返回 + 品牌头部 + 扫码 / 手机号验证码两种方式 + 加载卡片 + 行内错误卡片。
 *
 * 移植自 md3Music 的 `login_page.dart`（扫码 + 手机号两个 Tab，一号多账号时选择账号）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouLoginSheet(
    repository: KugouRepository,
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(KugouAuthMode.Qr) }
    var phoneStep by remember { mutableIntStateOf(0) } // 0=手机号 1=验证码
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }

    // —— 手机号登录 ——
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sendingCode by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(0) }
    var pendingAccounts by remember { mutableStateOf<List<KugouUserInfo>>(emptyList()) }

    // —— 扫码登录 ——
    var qrKey by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var qrStatus by remember { mutableStateOf(KugouRepository.QrStatus.WAITING) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val invalidMobileText = stringResource(R.string.kugou_login_phone_invalid)
    val codeRequiredText = stringResource(R.string.kugou_login_code_required)
    val fetchingInfoText = stringResource(R.string.kugou_login_fetching_info)
    val successText = stringResource(R.string.kugou_login_success)

    // 重新发送倒计时
    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000)
            resendCountdown -= 1
        }
    }

    /** 登录成功：拉一次昵称/头像 → 提示 → 关闭页面。 */
    suspend fun finalizeLogin() {
        isLoading = true
        loadingMessage = fetchingInfoText
        runCatching { repository.refreshUserInfo() }
        loadingMessage = successText
        delay(800)
        isLoading = false
        onLoginSuccess()
    }

    // —— 扫码：申请二维码 ——（切到扫码方式或点刷新都会重新申请）
    LaunchedEffect(authMode, refreshTrigger) {
        if (authMode != KugouAuthMode.Qr) return@LaunchedEffect
        qrError = null
        qrStatus = KugouRepository.QrStatus.WAITING
        qrBitmap = null
        repository.createQrSession()
            .onSuccess { session ->
                qrKey = session.key
                // 参考实现都用内容 URL 本地渲染二维码（接口图不一定返回）
                qrBitmap = withContext(Dispatchers.Default) {
                    generateKugouQrBitmap(session.contentUrl)
                }
                if (qrBitmap == null) qrError = "二维码生成失败，请刷新重试"
            }
            .onFailure { qrError = it.message ?: "获取二维码失败" }
    }

    // —— 扫码：每 2 秒轮询（与参考项目一致）——
    LaunchedEffect(qrKey) {
        if (qrKey.isBlank()) return@LaunchedEffect
        while (true) {
            delay(2_000)
            val result = repository.pollQrSession(qrKey)
            val newStatus = result.getOrElse { error ->
                qrError = error.message ?: "扫码状态查询失败"
                return@LaunchedEffect
            }
            qrStatus = newStatus
            when (newStatus) {
                KugouRepository.QrStatus.CONFIRMED -> {
                    finalizeLogin()
                    return@LaunchedEffect
                }
                KugouRepository.QrStatus.EXPIRED -> {
                    // 过期自动换一张
                    refreshTrigger++
                    return@LaunchedEffect
                }
                else -> Unit
            }
        }
    }

    fun sendCode() {
        if (mobile.length != 11) {
            inlineError = invalidMobileText
            return
        }
        inlineError = null
        sendingCode = true
        scope.launch {
            repository.sendSmsCode(mobile)
                .onSuccess {
                    resendCountdown = 60
                    phoneStep = 1
                }
                .onFailure { inlineError = it.message ?: "验证码发送失败" }
            sendingCode = false
        }
    }

    fun submitPhoneLogin(userId: String? = null) {
        if (mobile.length != 11) {
            inlineError = invalidMobileText
            return
        }
        if (code.isBlank()) {
            inlineError = codeRequiredText
            return
        }
        inlineError = null
        isLoading = true
        loadingMessage = "正在登录…"
        scope.launch {
            repository.loginByPhone(mobile = mobile, code = code, userId = userId)
                .onSuccess { result ->
                    if (!result.token.isNullOrBlank() && !result.userId.isNullOrBlank()) {
                        pendingAccounts = emptyList()
                        finalizeLogin()
                    } else if (result.accounts.isNotEmpty()) {
                        pendingAccounts = result.accounts
                        isLoading = false
                    } else {
                        isLoading = false
                        inlineError = "登录失败：未获取到账号信息"
                    }
                }
                .onFailure {
                    isLoading = false
                    inlineError = it.message ?: "登录失败"
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.kugou_login_title),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
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
            KugouBrandingHeader()
            Spacer(modifier = Modifier.height(20.dp))

            if (authMode == KugouAuthMode.Phone) {
                KugouStepIndicator(currentStep = phoneStep, totalSteps = 2)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isLoading) {
                KugouLoadingCard(message = loadingMessage)
                Spacer(modifier = Modifier.height(16.dp))
            }

            inlineError?.let { errorText ->
                KugouInlineErrorCard(message = errorText, onDismiss = { inlineError = null })
                Spacer(modifier = Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = authMode to phoneStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "kugouLoginTransition"
            ) { (mode, step) ->
                when (mode) {
                    KugouAuthMode.Phone -> {
                        if (step == 0) {
                            KugouPhoneNumberField(
                                mobile = mobile,
                                onMobileChanged = {
                                    mobile = it
                                    inlineError = null
                                },
                                isLoading = sendingCode,
                                onSend = ::sendCode
                            )
                        } else {
                            KugouSmsCodeField(
                                code = code,
                                onCodeChanged = {
                                    code = it
                                    inlineError = null
                                },
                                isLoading = isLoading,
                                resendCountdown = resendCountdown,
                                onVerify = { submitPhoneLogin() },
                                onEditPhone = { phoneStep = 0 },
                                onResend = {
                                    resendCountdown = 0
                                    sendCode()
                                }
                            )
                        }
                    }
                    KugouAuthMode.Qr -> {
                        KugouQrLoginContent(
                            qrBitmap = qrBitmap,
                            status = qrStatus,
                            errorText = qrError,
                            onRefresh = { refreshTrigger++ }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    authMode = if (authMode == KugouAuthMode.Qr) KugouAuthMode.Phone else KugouAuthMode.Qr
                    inlineError = null
                }
            ) {
                Text(
                    text = stringResource(
                        if (authMode == KugouAuthMode.Qr) {
                            R.string.kugou_login_switch_to_phone
                        } else {
                            R.string.kugou_login_switch_to_qr
                        }
                    ),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // 一号多账号：选一个再登录一次
    if (pendingAccounts.isNotEmpty()) {
        PixelAlertDialog(
            onDismissRequest = { pendingAccounts = emptyList() },
            title = { Text(stringResource(R.string.kugou_login_account_pick_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.kugou_login_account_pick_hint))
                    pendingAccounts.forEach { account ->
                        Surface(
                            onClick = { submitPhoneLogin(userId = account.userId) },
                            shape = AbsoluteSmoothCornerShape(16.dp, 60),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (!account.avatarUrl.isNullOrBlank()) {
                                    SmartImage(
                                        model = account.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        shape = CircleShape,
                                        useDiskCache = false,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.nickname.ifBlank { "酷狗账号 ${account.userId}" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = account.userId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingAccounts = emptyList() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────
// 品牌头部 / 步骤指示 / 加载 / 错误卡片（对齐 Bilibili 登录页）
// ─────────────────────────────────────────────────────────

@Composable
private fun KugouBrandingHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 官方品牌标（蓝色圆角底 + 白色 K），圆角用 app 统一的平滑圆角裁一下
        Image(
            painter = painterResource(R.drawable.ic_kugou),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(AbsoluteSmoothCornerShape(22.dp, 60))
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.kugou_login_title),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.kugou_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KugouStepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            Box(
                modifier = Modifier
                    .size(if (index == currentStep) 12.dp else 10.dp)
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
private fun KugouLoadingCard(message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.ifBlank { "请稍候…" },
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
private fun KugouInlineErrorCard(message: String, onDismiss: () -> Unit) {
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
                Text(text = "✕", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 手机号 / 验证码 / 扫码
// ─────────────────────────────────────────────────────────

@Composable
private fun KugouPhoneNumberField(
    mobile: String,
    onMobileChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = mobile,
            onValueChange = { input -> onMobileChanged(input.filter { it.isDigit() }.take(11)) },
            label = { Text(stringResource(R.string.kugou_login_phone_label)) },
            placeholder = { Text(stringResource(R.string.kugou_login_phone_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSend,
            enabled = !isLoading && mobile.length == 11,
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.kugou_login_code_send),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun KugouSmsCodeField(
    code: String,
    onCodeChanged: (String) -> Unit,
    isLoading: Boolean,
    resendCountdown: Int,
    onVerify: () -> Unit,
    onEditPhone: () -> Unit,
    onResend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { input -> onCodeChanged(input.filter { it.isDigit() }.take(6)) },
            label = { Text(stringResource(R.string.kugou_login_code_label)) },
            placeholder = { Text(stringResource(R.string.kugou_login_code_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onVerify,
            enabled = !isLoading && code.isNotBlank(),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.kugou_login_phone_submit),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onEditPhone) {
                Text(
                    text = stringResource(R.string.kugou_login_edit_phone),
                    fontFamily = GoogleSansRounded
                )
            }
            TextButton(onClick = onResend, enabled = resendCountdown == 0) {
                Text(
                    text = if (resendCountdown > 0) {
                        stringResource(R.string.kugou_login_code_resend, resendCountdown)
                    } else {
                        stringResource(R.string.kugou_login_code_send)
                    },
                    fontFamily = GoogleSansRounded
                )
            }
        }
    }
}

@Composable
private fun KugouQrLoginContent(
    qrBitmap: android.graphics.Bitmap?,
    status: KugouRepository.QrStatus,
    errorText: String?,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.kugou_login_qr_cd),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else if (errorText == null) {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = when (status) {
                KugouRepository.QrStatus.WAITING -> stringResource(R.string.kugou_login_status_waiting)
                KugouRepository.QrStatus.SCANNED -> stringResource(R.string.kugou_login_status_scanned)
                KugouRepository.QrStatus.CONFIRMED -> stringResource(R.string.kugou_login_status_confirmed)
                KugouRepository.QrStatus.EXPIRED -> stringResource(R.string.kugou_login_status_expired)
                KugouRepository.QrStatus.UNKNOWN -> stringResource(R.string.kugou_login_status_waiting)
            },
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )

        TextButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.kugou_login_qr_refresh),
                fontFamily = GoogleSansRounded
            )
        }
    }
}

/** 用 ZXing 把二维码内容 URL 渲染成 Bitmap（与 B 站登录页同款做法）。 */
private fun generateKugouQrBitmap(content: String, size: Int = 512): android.graphics.Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x, y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }
    bitmap
} catch (t: Throwable) {
    null
}
