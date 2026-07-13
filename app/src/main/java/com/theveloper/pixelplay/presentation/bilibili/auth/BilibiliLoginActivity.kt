package com.theveloper.pixelplay.presentation.bilibili.auth

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import org.json.JSONObject

@AndroidEntryPoint
class BilibiliLoginActivity : ComponentActivity() {

    companion object {
        const val TARGET_URL = "https://www.bilibili.com/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                BilibiliWebLoginScreen(onClose = { finish() })
            }
        }
    }
}

private data class BilibiliWebUiState(
    val title: String = "",
    val currentUrl: String = BilibiliLoginActivity.TARGET_URL,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoadingPage: Boolean = true,
    val pageProgress: Int = 0,
    val lastError: String? = null
)

sealed interface BilibiliLoginState {
    object Idle : BilibiliLoginState
    object Loading : BilibiliLoginState
    data class Success(val nickname: String) : BilibiliLoginState
    data class Error(val message: String) : BilibiliLoginState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliWebLoginScreen(
    viewModel: BilibiliLoginViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val loginState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var webUiState by remember { mutableStateOf(BilibiliWebUiState()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var pageLoadTimeout by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is BilibiliLoginState.Success -> {
                Toast.makeText(context, "欢迎, ${state.nickname}", Toast.LENGTH_SHORT).show()
                onClose()
            }
            is BilibiliLoginState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    LaunchedEffect(webUiState.isLoadingPage, webUiState.currentUrl) {
        if (!webUiState.isLoadingPage) {
            pageLoadTimeout = false
            return@LaunchedEffect
        }
        delay(20_000)
        if (webUiState.isLoadingPage) {
            pageLoadTimeout = true
            snackbarHostState.showSnackbar("页面加载超时，请检查网络")
        }
    }

    BackHandler(enabled = true) {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            else -> showExitDialog = true
        }
    }

    fun captureAndSubmitCookies() {
        if (loginState is BilibiliLoginState.Loading) return

        readAndProcessCookies(context).fold(
            onSuccess = { cookieJson ->
                viewModel.processCookies(cookieJson)
            },
            onFailure = { error ->
                val message = error.message ?: "无法读取Cookie"
                viewModel.clearError()
                webUiState = webUiState.copy(lastError = message)
            }
        )
    }

    fun navigateBack() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(text = "退出登录") },
            text = { Text(text = "确定要退出登录页面吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onClose()
                    }
                ) {
                    Text(text = "退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(text = "继续")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "登录 Bilibili",
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = ::navigateBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = { webView?.goBack() },
                        enabled = webUiState.canGoBack && loginState !is BilibiliLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "后退"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.goForward() },
                        enabled = webUiState.canGoForward && loginState !is BilibiliLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "前进"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.reload() },
                        enabled = loginState !is BilibiliLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新"
                        )
                    }

                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            webUiState = webUiState.copy(lastError = null)
                            webView?.loadUrl(BilibiliLoginActivity.TARGET_URL)
                        },
                        enabled = loginState !is BilibiliLoginState.Loading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "首页"
                        )
                    }
                },
                floatingActionButton = {
                    androidx.compose.material3.SmallExtendedFloatingActionButton(
                        onClick = ::captureAndSubmitCookies,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        if (loginState is BilibiliLoginState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (loginState) {
                                is BilibiliLoginState.Loading -> "保存中"
                                else -> "完成"
                            },
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (webUiState.isLoadingPage) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { (webUiState.pageProgress / 100f).coerceIn(0f, 1f) }
                )
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = "请在浏览器中登录您的B站账号，登录成功后点击「完成」保存登录状态",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val pageSlowMessage = "页面加载缓慢，请检查网络连接"
            val effectiveError = when {
                pageLoadTimeout -> pageSlowMessage
                webUiState.lastError != null -> webUiState.lastError
                else -> null
            }

            effectiveError?.let { errorText ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                pageLoadTimeout = false
                                webUiState = webUiState.copy(lastError = null)
                                webView?.reload()
                            }
                        ) {
                            Text(text = "重试", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                BilibiliWebView(
                    modifier = Modifier.fillMaxSize(),
                    onWebViewCreated = { created ->
                        webView = created
                        webUiState = webUiState.copy(
                            canGoBack = created.canGoBack(),
                            canGoForward = created.canGoForward(),
                            currentUrl = created.url ?: BilibiliLoginActivity.TARGET_URL,
                            title = created.title.orEmpty()
                        )
                    },
                    onNavigationChanged = { view ->
                        webUiState = webUiState.copy(
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                            currentUrl = view.url ?: BilibiliLoginActivity.TARGET_URL,
                            title = view.title.orEmpty()
                        )
                    },
                    onLoadingChanged = { loading, url ->
                        webUiState = webUiState.copy(
                            isLoadingPage = loading,
                            currentUrl = url ?: webUiState.currentUrl,
                            lastError = if (loading) null else webUiState.lastError
                        )
                    },
                    onProgressChanged = { progress ->
                        webUiState = webUiState.copy(pageProgress = progress.coerceIn(0, 100))
                    },
                    onMainFrameError = { message ->
                        webUiState = webUiState.copy(lastError = message, isLoadingPage = false)
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BilibiliWebView(
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit,
    onNavigationChanged: (WebView) -> Unit,
    onLoadingChanged: (Boolean, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onMainFrameError: (String) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                val loadFailedMessage = "加载失败"
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = BilibiliLoginActivity.DESKTOP_UA
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }

                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true, url)
                        view?.let(onNavigationChanged)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false, url)
                        view?.let(onNavigationChanged)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val description = error?.description?.toString()?.ifBlank {
                                loadFailedMessage
                            } ?: loadFailedMessage
                            onMainFrameError(description)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                            onMainFrameError("HTTP错误: ${errorResponse?.statusCode ?: 0}")
                        }
                    }
                }

                loadUrl(BilibiliLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        }
    )
}

private fun readAndProcessCookies(context: Context): Result<String> {
    return try {
        val cm = CookieManager.getInstance()
        val main = cm.getCookie("https://www.bilibili.com") ?: ""
        val api = cm.getCookie("https://api.bilibili.com") ?: ""
        val merged = listOf(main, api).filter { it.isNotBlank() }.joinToString("; ")

        if (merged.isBlank()) {
            return Result.failure(IllegalStateException("未找到Cookie，请先登录"))
        }

        val map = cookieStringToMap(merged)

        if (!map.containsKey("SESSDATA") && !map.containsKey("bili_jct")) {
            return Result.failure(IllegalStateException("登录信息不完整，请重新登录"))
        }

        val json = JSONObject(map as Map<*, *>).toString()
        Result.success(json)
    } catch (error: Throwable) {
        Result.failure(
            IllegalStateException(
                "读取Cookie失败: ${error.message.orEmpty()}",
                error
            )
        )
    }
}

private fun cookieStringToMap(raw: String): MutableMap<String, String> {
    val map = linkedMapOf<String, String>()
    raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('=') }
        .forEach { part ->
            val idx = part.indexOf('=')
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    return map
}