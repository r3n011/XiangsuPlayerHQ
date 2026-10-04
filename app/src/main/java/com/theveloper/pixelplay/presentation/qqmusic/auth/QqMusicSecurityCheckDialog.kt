package com.theveloper.pixelplay.presentation.qqmusic.auth

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * QQ 音乐安全验证弹窗（风控返回 `code=20276` + `securityURL` 时弹出）。
 *
 * 交互对齐 B 站登录页的极验弹窗：**在当前页面之上弹一个 WebView 弹窗**完成人机验证，
 * 而不是把整页切到网页登录（以前那样用户完成验证后不知道要做什么）。
 *
 * 验证完成后由调用方读取 WebView 的 cookie 与 UA 合并进手机号登录链路并**自动重发验证码**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun QqMusicSecurityCheckDialog(
    url: String,
    onDismiss: () -> Unit,
    onFinished: (cookieHeader: String, userAgent: String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var loading by remember { mutableStateOf(true) }
        var progress by remember { mutableIntStateOf(0) }

        // 验证完成：把 WebView 的 cookie 与 UA 交回上层（用于重发验证码）
        fun finish() {
            val view = webView
            val currentUrl = view?.url ?: url
            val cookies = runCatching {
                CookieManager.getInstance().getCookie(currentUrl)
            }.getOrNull().orEmpty()
            val userAgent = runCatching { view?.settings?.userAgentString }.getOrNull().orEmpty()
            onFinished(cookies, userAgent)
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
                webView = null
            }
        }

        Surface(
            modifier = Modifier
                // ⚡ 不再全屏铺满：验证页（halfScreen=true）本身是手机版布局，
                //    全屏在平板上会显得又空又大。改为居中的手机比例弹窗 ——
                //    宽度不超过 460dp，高度占屏幕 85%，任何设备上都是一列可读的验证框。
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 40.dp)
                .widthIn(max = 460.dp)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏：标题 + 关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QQ 音乐安全验证",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                // 验证页
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        val view = WebView(ctx)
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        view.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // 手机版验证页（securityURL 带 halfScreen=true），用移动 UA
                            userAgentString = MOBILE_UA
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(view, true)
                        }
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                                progress = newProgress.coerceIn(0, 100)
                            }
                        }
                        view.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView?, finishedUrl: String?) {
                                loading = false
                            }
                        }
                        view.loadUrl(url)
                        webView = view
                        view
                    }
                )

                // 底部：提示 + 完成
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "按页面提示完成验证后点「已完成验证」",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { finish() },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "已完成验证",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** 手机版 UA：验证页（`halfScreen=true`）按移动端渲染，与后续接口请求保持一致。 */
internal const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"
