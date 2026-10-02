package com.theveloper.pixelplay.presentation.kugou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.theveloper.pixelplay.data.kugou.KugouRepository
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 酷狗登录页（新界面打开，与 Telegram / 网易云 / B 站 / QQ 音乐登录一致，
 * 而不是覆盖在账号设置页里的底部弹窗）。
 * 内部为 [KugouLoginSheet] 全屏页：扫码登录 / 手机号验证码登录。
 */
class KugouLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            KugouLoginEntryPoint::class.java,
        ).kugouRepository()
        setContent {
            PixelPlayTheme {
                KugouLoginSheet(
                    repository = repository,
                    onLoginSuccess = { finish() },
                    onBackClick = { finish() },
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KugouLoginEntryPoint {
    fun kugouRepository(): KugouRepository
}
