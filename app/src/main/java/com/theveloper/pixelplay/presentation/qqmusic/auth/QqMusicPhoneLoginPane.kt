package com.theveloper.pixelplay.presentation.qqmusic.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * QQ 音乐手机号登录整页。
 *
 * ⚡ 布局与动画 **1:1 对齐 B 站登录页**（`BilibiliLoginSheet`）：
 * 品牌头部 → 步骤指示点 → 加载 / 错误卡片 → `AnimatedContent` 步骤切换（横向滑入淡入）
 * → 步骤头（圆形图标 + 标题 + 副标题）→ 输入区 → 胶囊形 Expressive 主按钮 → 底部切换按钮。
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QqMusicPhoneLoginScreen(
    viewModel: QqMusicLoginViewModel,
    onBackToWeb: () -> Unit,
    onClose: () -> Unit,
    /** 风控要求安全验证：把 securityURL 交给 Activity，切到网页登录并加载该验证页 */
    onOpenSecurityCheck: (String) -> Unit = {},
) {
    val loginState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is QqMusicLoginState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_welcome_user, state.nickname),
                    Toast.LENGTH_SHORT
                ).show()
                onClose()
            }

            is QqMusicLoginState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }

            else -> Unit
        }
    }

    BackHandler(enabled = true) { onBackToWeb() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.qq_login_brand_title),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToWeb) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back),
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
        QqMusicPhoneLoginPane(
            viewModel = viewModel,
            onOpenSecurityCheck = onOpenSecurityCheck,
            onBackToWeb = onBackToWeb,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/** 面板内容（与 B 站登录页同结构）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QqMusicPhoneLoginPane(
    viewModel: QqMusicLoginViewModel,
    modifier: Modifier = Modifier,
    /** 风控要求安全验证时：把 securityURL 交给上层，用应用内 WebView 打开完成验证 */
    onOpenSecurityCheck: (String) -> Unit = {},
    onBackToWeb: () -> Unit = {},
) {
    val phoneUi by viewModel.phoneUi.collectAsStateWithLifecycle()
    // 步骤：0 = 手机号，1 = 验证码（与 B 站一致的「两步」结构）
    val step = if (phoneUi.codeSent) 1 else 0
    val isLoading = phoneUi.sendingCode || phoneUi.submitting
    val loadingMessage = when {
        phoneUi.submitting -> stringResource(R.string.qq_login_loading_signing_in)
        phoneUi.sendingCode -> stringResource(R.string.qq_login_loading_sending)
        else -> stringResource(R.string.qq_login_loading)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QqMusicBrandingHeader()
        Spacer(Modifier.height(20.dp))

        QqMusicStepIndicator(currentStep = step, totalSteps = 2)
        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            QqMusicLoadingCard(message = loadingMessage)
            Spacer(Modifier.height(16.dp))
        }

        phoneUi.error?.let { errorText ->
            QqMusicInlineErrorCard(
                message = errorText,
                onDismiss = { viewModel.clearPhoneError() }
            )
            Spacer(Modifier.height(16.dp))
        }

        // ⚡ 风控要求安全验证（code=20276）：给可操作入口，而不是把一长串 securityURL 直出
        phoneUi.securityChallengeUrl?.let { url ->
            QqMusicSecurityCard(
                onOpen = { onOpenSecurityCheck(url) },
                onDismiss = viewModel::clearSecurityChallenge
            )
            Spacer(Modifier.height(16.dp))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { width -> width / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
            },
            label = "qqPhoneLoginTransition"
        ) { currentStep ->
            if (currentStep == 0) {
                QqMusicPhoneNumberStep(
                    phone = phoneUi.phone,
                    onPhoneChanged = viewModel::onPhoneChanged,
                    isLoading = isLoading,
                    onSend = viewModel::sendPhoneCode
                )
            } else {
                QqMusicSmsCodeStep(
                    code = phoneUi.code,
                    onCodeChanged = viewModel::onCodeChanged,
                    isLoading = isLoading,
                    onVerify = viewModel::submitPhoneLogin,
                    onEditPhone = viewModel::backToPhoneStep,
                    onResend = viewModel::sendPhoneCode,
                    resendCountdown = phoneUi.countdown
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onBackToWeb) {
            Text(
                text = stringResource(R.string.qq_login_mode_web),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 品牌头部（对齐 B 站 / Telegram 登录页）
// ─────────────────────────────────────────────────────────

@Composable
private fun QqMusicBrandingHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                // QQ 音乐品牌绿底（固定浅色，与 B 站登录页的固定粉底同款处理）
                .background(Color(0xFFE7F7EE)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.qq_music),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.qq_login_brand_title),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.qq_login_brand_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 步骤指示点：当前步骤放大 1.2 倍（弹性），已完成/当前为主色，其余为描边色。 */
@Composable
private fun QqMusicStepIndicator(currentStep: Int, totalSteps: Int) {
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
                label = "qqStepScale"
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
private fun QqMusicLoadingCard(message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.ifBlank { stringResource(R.string.qq_login_loading) },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QqMusicInlineErrorCard(message: String, onDismiss: () -> Unit) {
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
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = stringResource(R.string.qq_login_security_ignore)
                )
            }
        }
    }
}

/** 安全验证入口卡片（QQ 音乐特有；视觉与其它卡片一致）。 */
@Composable
private fun QqMusicSecurityCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.qq_login_security_title),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.qq_login_security_body),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.qq_login_security_ignore),
                        fontFamily = GoogleSansRounded
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onOpen) {
                    Text(
                        text = stringResource(R.string.qq_login_security_open),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 步骤头：圆形图标 + 标题 + 副标题（与 B 站同款）。 */
@Composable
private fun QqMusicAuthStepHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Spacer(Modifier.width(16.dp))
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
            Spacer(Modifier.height(4.dp))
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

/** 胶囊形主按钮：按下缩小 0.96（弹性），加载时左侧显示进度条。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QqMusicExpressiveButton(
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
        label = "qqButtonScale"
    )

    MediumExtendedFloatingActionButton(
        text = {
            Text(
                text = if (loading) stringResource(R.string.qq_login_loading) else text,
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
        onClick = { if (enabled && !loading) onClick() },
        expanded = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    )
}

// ─────────────────────────────────────────────────────────
// 步骤 1：手机号
// ─────────────────────────────────────────────────────────

@Composable
private fun QqMusicPhoneNumberStep(
    phone: String,
    onPhoneChanged: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    val phoneFocusRequester = remember { FocusRequester() }
    var phoneFocused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        targetValue = if (phoneFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        label = "qqPhoneBorder"
    )
    val containerColor = if (phoneFocused) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QqMusicAuthStepHeader(
            icon = Icons.Rounded.Phone,
            title = stringResource(R.string.kugou_login_phone_label),
            subtitle = stringResource(R.string.qq_login_step_phone_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(containerColor)
                .border(BorderStroke(if (phoneFocused) 2.dp else 1.dp, borderColor), shape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // QQ 音乐手机号登录仅支持中国大陆号码：+86 作为固定前缀（与 B 站布局一致）
                Text(
                    text = "+86",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = phone,
                    onValueChange = { raw ->
                        if (isLoading) return@BasicTextField
                        onPhoneChanged(raw.filter(Char::isDigit).take(11))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(phoneFocusRequester)
                        .onFocusChanged { phoneFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (!isLoading) onSend() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (phone.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.kugou_login_phone_placeholder),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.45f),
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

        Spacer(Modifier.height(28.dp))

        QqMusicExpressiveButton(
            text = stringResource(R.string.kugou_login_code_send),
            onClick = onSend,
            enabled = phone.length == 11 && !isLoading,
            loading = isLoading
        )
    }
}

// ─────────────────────────────────────────────────────────
// 步骤 2：验证码
// ─────────────────────────────────────────────────────────

@Composable
private fun QqMusicSmsCodeStep(
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
        QqMusicAuthStepHeader(
            icon = Icons.Rounded.Sms,
            title = stringResource(R.string.kugou_login_code_label),
            subtitle = stringResource(R.string.qq_login_step_code_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { raw -> onCodeChanged(raw.filter(Char::isDigit).take(6)) },
            label = { Text(stringResource(R.string.kugou_login_code_label), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (!isLoading) onVerify() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        QqMusicExpressiveButton(
            text = stringResource(R.string.kugou_login_phone_submit),
            onClick = onVerify,
            enabled = code.isNotBlank() && !isLoading,
            loading = isLoading
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { if (!isLoading) onEditPhone() }) {
                Text(
                    text = stringResource(R.string.qq_login_edit_phone),
                    fontFamily = GoogleSansRounded
                )
            }
            TextButton(
                onClick = { if (!isLoading && resendCountdown == 0) onResend() },
                enabled = !isLoading && resendCountdown == 0
            ) {
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
