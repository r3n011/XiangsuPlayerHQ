package com.theveloper.pixelplay.presentation.bilibili.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.theveloper.pixelplay.presentation.components.BilibiliLoginSheet
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme

/**
 * B 站登录页（新界面打开，与 Telegram/Netease 登录一致，而不是覆盖在账号设置页里）。
 * 内部为 BilibiliLoginSheet 全屏页：手机号验证码（含极验人机验证）/ 扫码登录。
 */
class BilibiliLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PixelPlayTheme {
                BilibiliLoginSheet(
                    onLoginSuccess = { finish() },
                    onBackClick = { finish() }
                )
            }
        }
    }
}
