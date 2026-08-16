package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theveloper.pixelplay.data.bilibili.BilibiliCaptchaChallenge
import com.theveloper.pixelplay.data.bilibili.BilibiliSearchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * 极验人机验证结果（对应 PiliPlus Geetest 的 getValidate()）。
 * geetestChallenge 为验证完成后的新 challenge。
 */
data class GeetestResult(
    val geetestChallenge: String,
    val geetestValidate: String,
    val geetestSeccode: String
)

/**
 * 极验人机验证对话框（B 站短信/密码登录风控用），实现对齐 PiliPlus 的
 * GeetestWebviewDialog：加载 fullpage.0.0.0.js，初始化 Geetest(config)，
 * 通过 addJavascriptInterface 回传 getValidate() 结果。
 */
@Composable
fun GeetestCaptchaDialog(
    searchApi: BilibiliSearchApi,
    challenge: BilibiliCaptchaChallenge,
    onFinished: (GeetestResult?) -> Unit
) {
    var configJson by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val onFinishedState by rememberUpdatedState(onFinished)

    LaunchedEffect(challenge.gt, challenge.challenge) {
        configJson = withContext(Dispatchers.IO) {
            searchApi.getGeetestConfig(challenge.gt, challenge.challenge)
        }
        if (configJson == null) {
            errorText = "人机验证初始化失败，请改用扫码登录"
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { onFinishedState(null) }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "人机验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onFinishedState(null) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    configJson != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    createGeetestWebView(ctx, configJson!!) { r -> onFinishedState(r) }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    errorText != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorText!!,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { onFinishedState(null) }) {
                                Text("返回")
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createGeetestWebView(
    context: Context,
    configJson: String,
    onFinished: ((GeetestResult?) -> Unit)?
): WebView {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        loadWithOverviewMode = true
        useWideViewPort = true
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    webView.addJavascriptInterface(
        object {
            @JavascriptInterface
            fun onResult(type: String, data: String) {
                when (type) {
                    "success" -> {
                        try {
                            val obj = JSONObject(data)
                            val c = obj.optString("geetest_challenge", "")
                            val v = obj.optString("geetest_validate", "")
                            val s = obj.optString("geetest_seccode", "")
                            if (c.isNotBlank() && v.isNotBlank() && s.isNotBlank()) {
                                onFinished?.invoke(GeetestResult(c, v, s))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "geetest success parse failed: $data")
                        }
                    }
                    "close" -> onFinished?.invoke(null)
                    else -> Timber.d("geetest event: $type $data")
                }
            }
        },
        "AndroidGeetest"
    )
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val js = "t=Geetest($configJson).onSuccess(()=>R(\"success\",t.getValidate())).onError(o=>R(\"error\",o)).onClose(o=>R(\"close\",o));t.onReady(()=>t.verify());"
            view?.evaluateJavascript(js, null)
        }
    }
    val html = """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"></head>
        <body style="margin:0;padding:0;background:#ffffff">
        <script src="https://static.geetest.com/static/js/fullpage.0.0.0.js"></script>
        <script>R=(n,o)=>AndroidGeetest.onResult(n,JSON.stringify(o));</script>
        </body></html>
    """.trimIndent()
    webView.loadDataWithBaseURL("https://static.geetest.com/", html, "text/html", "utf-8", null)
    return webView
}
