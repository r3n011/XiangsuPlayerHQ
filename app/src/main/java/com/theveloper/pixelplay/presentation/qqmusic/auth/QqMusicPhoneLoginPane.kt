package com.theveloper.pixelplay.presentation.qqmusic.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * QQ 音乐手机号登录整页（顶栏 + 返回 + 成功/失败反馈），与网页登录在同一 Activity 内切换。
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.qq_login_mode_phone),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onBackToWeb,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onBackToWeb,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.qq_login_mode_web),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            QqMusicPhoneLoginPane(
                viewModel = viewModel,
                onOpenSecurityCheck = onOpenSecurityCheck
            )
        }
    }
}

/**
 * QQ 音乐「手机号 + 短信验证码」登录面板（与网页登录同页切换）。
 *
 * 官方接口可能要求安全验证，此时会提示改用网页登录完成验证。
 */
@Composable
fun QqMusicPhoneLoginPane(
    viewModel: QqMusicLoginViewModel,
    modifier: Modifier = Modifier,
    /** 风控要求安全验证时：把 securityURL 交给上层，用应用内 WebView 打开完成验证 */
    onOpenSecurityCheck: (String) -> Unit = {},
) {
    val phoneUi by viewModel.phoneUi.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Text(
                text = stringResource(R.string.qq_login_phone_note),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = phoneUi.phone,
            onValueChange = viewModel::onPhoneChanged,
            label = { Text(stringResource(R.string.kugou_login_phone_label)) },
            placeholder = { Text(stringResource(R.string.kugou_login_phone_placeholder)) },
            singleLine = true,
            enabled = !phoneUi.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::sendPhoneCode,
            enabled = !phoneUi.sendingCode && !phoneUi.submitting &&
                phoneUi.countdown == 0 && phoneUi.phone.length == 11,
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (phoneUi.sendingCode) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (phoneUi.countdown > 0) {
                    stringResource(R.string.kugou_login_code_resend, phoneUi.countdown)
                } else {
                    stringResource(R.string.kugou_login_code_send)
                },
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedTextField(
            value = phoneUi.code,
            onValueChange = viewModel::onCodeChanged,
            label = { Text(stringResource(R.string.kugou_login_code_label)) },
            placeholder = { Text(stringResource(R.string.kugou_login_code_placeholder)) },
            singleLine = true,
            enabled = !phoneUi.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier.fillMaxWidth(),
        )

        phoneUi.error?.let { message ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // ⚡ 风控要求安全验证（code=20276）：不要把那一长串 securityURL 直出给用户，
        //    给一个可操作的入口 —— 在应用内 WebView 打开验证页，完成后再回来重试。
        phoneUi.securityChallengeUrl?.let { url ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.qq_login_security_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.qq_login_security_body),
                        modifier = Modifier.padding(top = 4.dp),
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
                        TextButton(onClick = viewModel::clearSecurityChallenge) {
                            Text(
                                text = stringResource(R.string.qq_login_security_ignore),
                                fontFamily = GoogleSansRounded
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { onOpenSecurityCheck(url) }) {
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

        Button(
            onClick = viewModel::submitPhoneLogin,
            enabled = !phoneUi.submitting && phoneUi.phone.length == 11 && phoneUi.code.isNotBlank(),
            shape = AbsoluteSmoothCornerShape(18.dp, 60),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (phoneUi.submitting) {
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.qq_login_phone_hint),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
