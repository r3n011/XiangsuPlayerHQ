package com.theveloper.pixelplay.presentation.plugin

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.plugin.XSPluginRecord
import com.theveloper.pixelplay.data.plugin.XsHostBridge
import com.theveloper.pixelplay.data.plugin.XsLog
import com.theveloper.pixelplay.data.plugin.XsPluginPaths
import com.theveloper.pixelplay.data.plugin.XsPluginRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 插件自带界面容器（manifest.ui）。
 *
 * 与脚本插件共用同一套 `__xsHost__` 桥：页面 postMessage → 原生 invoke → __xsResolve。
 * 导航仅允许插件目录内的 file://；远程请求必须走 xs.host.network。
 */
@AndroidEntryPoint
class XsPluginWebActivity : ComponentActivity() {

    @Inject lateinit var registry: XsPluginRegistry
    @Inject lateinit var paths: XsPluginPaths
    @Inject lateinit var bridge: XsHostBridge
    @Inject lateinit var themeOverlay: com.theveloper.pixelplay.data.plugin.XsThemeOverlay

    private var webView: WebView? = null
    private lateinit var record: XSPluginRecord

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
        val plugin = registry.get(pluginId)
        if (plugin == null) {
            finish()
            return
        }
        record = plugin
        val ui = plugin.manifest.ui ?: run { finish(); return }
        val entry = paths.uiEntry(pluginId, ui.entry)
        if (!entry.isFile) {
            XsLog.e(XsLog.WEB, "ui entry missing: %s (%s)", pluginId, entry.absolutePath)
            finish()
            return
        }
        XsLog.i(XsLog.WEB, "open panel: %s v%s entry=%s", pluginId, plugin.version, entry.name)

        val root = FrameLayout(this)
        val web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#0D1112"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.scheme == "file") {
                        val allowedRoot = paths.pluginDir(pluginId).canonicalPath
                        val target = File(url.path.orEmpty()).canonicalPath
                        return !target.startsWith(allowedRoot)
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    XsLog.i(XsLog.WEB, "panel page finished: %s", url.substringAfterLast('/'))
                    // 页面加载完成后注入 Context 快照（无权限）
                    val snapshot = bridge.contextSnapshot()
                    view.evaluateJavascript(
                        "window.__xsContext = JSON.parse(${JSONObject.quote(snapshot)});",
                        null,
                    )
                }

                // 插件页渲染进程崩溃时只关闭面板，绝不让宿主 App 一起崩
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                Timber.e("plugin webview renderer gone (crashed=${detail.didCrash()})")
                    XsLog.e(XsLog.WEB, "renderer gone for %s (crashed=%s)", pluginId, detail.didCrash())
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    if (webView === view) webView = null
                    finish()
                    return true
                }
            }
            addJavascriptInterface(HostInterface(pluginId), "__XSAndroid")
            loadUrl("file://${entry.absolutePath}")
        }
        webView = web
        root.addView(web)
        setContentView(root)

        // 主题色变化时实时更新 --xs-accent（XS UI Kit v3 §3.1）
        lifecycleScope.launch {
            themeOverlay.accent.collect { accent ->
                val value = accent ?: "#2DB95A"
                web.evaluateJavascript(
                    "document.documentElement.style.setProperty('--xs-accent', '${'$'}value')",
                    null,
                )
            }
        }
    }

    override fun onDestroy() {
        webView?.let { web ->
            web.loadUrl("about:blank")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private inner class HostInterface(private val pluginId: String) {
        @JavascriptInterface
        fun invoke(id: String, module: String, method: String, argsJson: String) {
            lifecycleScope.launch {
                val result = try {
                    bridge.invoke(record, module, method, argsJson)
                } catch (e: Exception) {
                    Timber.w(e, "webview host invoke failed")
                    JSONObject().put("error", "host_error").toString()
                }
                runOnUiThread {
                    webView?.evaluateJavascript(
                        "window.__xsResolve(${JSONObject.quote(id)}, ${JSONObject.quote(result)})",
                        null,
                    )
                }
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            Timber.d("[xs-ui:%s] %s", pluginId, message)
        }

        /** XS UI Kit v3：插件页读取宿主当前强调色 */
        @JavascriptInterface
        fun accent(): String = themeOverlay.accent.value ?: "#2DB95A"

        /** Context 快照（与 xs.ui.getTheme 同构） */
        @JavascriptInterface
        fun context(): String = bridge.contextSnapshot()
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "plugin_id"
    }
}
