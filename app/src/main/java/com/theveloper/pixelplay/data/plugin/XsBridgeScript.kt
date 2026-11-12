package com.theveloper.pixelplay.data.plugin

/**
 * 宿主桥脚本（QuickJS 与 WebView 共用同一套语义）。
 *
 * 运行顺序：本脚本 → xs-runtime.js → 插件 main.js / 页面脚本。
 * `__xsHost__` 的 invoke 返回 Promise，原生侧通过 `__xsResolve(id, json)` 回写。
 */
object XsBridgeScript {

    /** QuickJS 版：没有 window/setTimeout，需要原生补齐定时器。 */
    val QUICKJS: String = """
(function (global) {
  if (global.__xsHost__) return;
  var seq = 0;
  var pending = {};
  global.__xsPending = pending;
  global.__xsResolve = function (id, json) {
    var key = String(id);
    var fn = pending[key];
    if (!fn) return;
    delete pending[key];
    var value = null;
    try { value = JSON.parse(json); } catch (e) { value = null; }
    try { fn(value); } catch (e) {}
  };
  global.__xsHost__ = {
    invoke: function (module, method, args) {
      return new Promise(function (resolve) {
        var id = ++seq;
        pending[String(id)] = resolve;
        try {
          global.__xsNativeInvoke(String(id), String(module), String(method), JSON.stringify(args || []));
        } catch (e) {
          delete pending[String(id)];
          resolve({ error: 'host_bridge_error' });
        }
      });
    },
    registerHooks: function (hooks) { global.__xsHooks = hooks || {}; },
    lifecycleHooks: function (hooks) { global.__xsHooks = hooks || {}; },
    getContext: function () { return global.__xsContext || {}; },
    setContext: function (ctx) { global.__xsContext = ctx || {}; },
    onRuntimeReady: function () { global.__xsReady = true; }
  };

  global.__xsDispatchHook = function (kind, arg) {
    var hooks = global.__xsHooks || {};
    try {
      if (kind === 'enable' && typeof hooks.onEnable === 'function') hooks.onEnable();
      else if (kind === 'disable' && typeof hooks.onDisable === 'function') hooks.onDisable();
      else if (kind === 'action' && typeof hooks.onAction === 'function') hooks.onAction(arg);
    } catch (e) {
      try { global.__xsNativeLog('error', 'hook ' + kind + ' failed: ' + e); } catch (e2) {}
    }
    return true;
  };

  var timerSeq = 0;
  var timers = {};
  global.setTimeout = function (fn, ms) {
    var id = ++timerSeq;
    timers[id] = fn;
    try { global.__xsNativeSetTimeout(String(id), Number(ms) || 0); } catch (e) {}
    return id;
  };
  global.clearTimeout = function (id) {
    delete timers[String(id)];
    try { global.__xsNativeClearTimeout(String(id)); } catch (e) {}
  };
  global.setInterval = function (fn, ms) {
    // v1 用 setTimeout 自递归实现，避免额外 native 通道
    var alive = true;
    function tick() { if (!alive) return; try { fn(); } catch (e) {} global.setTimeout(tick, ms); }
    global.setTimeout(tick, ms);
    return { __xsInterval: true, stop: function () { alive = false; } };
  };
  global.clearInterval = function (h) { if (h && h.stop) h.stop(); };
  global.__xsFireTimer = function (id) {
    var key = String(id);
    var fn = timers[key];
    delete timers[key];
    if (fn) { try { fn(); } catch (e) {} }
  };

  var consoleObj = {
    log: function () { try { global.__xsNativeLog('log', Array.prototype.join.call(arguments, ' ')); } catch (e) {} },
    warn: function () { try { global.__xsNativeLog('warn', Array.prototype.join.call(arguments, ' ')); } catch (e) {} },
    error: function () { try { global.__xsNativeLog('error', Array.prototype.join.call(arguments, ' ')); } catch (e) {} }
  };
  global.console = global.console || consoleObj;
})(typeof globalThis !== 'undefined' ? globalThis : this);
""".trimIndent()

    /** WebView 版：window 自带定时器，原生桥通过 JavascriptInterface 注入。 */
    val WEBVIEW: String = """
(function (global) {
  if (global.__xsHost__) return;
  var seq = 0;
  var pending = {};
  global.__xsPending = pending;
  global.__xsResolve = function (id, json) {
    var key = String(id);
    var fn = pending[key];
    if (!fn) return;
    delete pending[key];
    var value = null;
    try { value = JSON.parse(json); } catch (e) { value = null; }
    try { fn(value); } catch (e) {}
  };
  global.__xsNativeInvoke = function (id, module, method, argsJson) {
    try {
      if (global.__XSAndroid && global.__XSAndroid.invoke) {
        global.__XSAndroid.invoke(String(id), String(module), String(method), String(argsJson));
      } else {
        global.__xsResolve(id, JSON.stringify({ error: 'no_host' }));
      }
    } catch (e) {
      global.__xsResolve(id, JSON.stringify({ error: 'host_bridge_error' }));
    }
  };
  global.__xsHost__ = {
    invoke: function (module, method, args) {
      return new Promise(function (resolve) {
        var id = ++seq;
        pending[String(id)] = resolve;
        global.__xsNativeInvoke(String(id), String(module), String(method), JSON.stringify(args || []));
      });
    },
    registerHooks: function (hooks) { global.__xsHooks = hooks || {}; },
    lifecycleHooks: function (hooks) { global.__xsHooks = hooks || {}; },
    getContext: function () { return global.__xsContext || {}; },
    setContext: function (ctx) { global.__xsContext = ctx || {}; },
    onRuntimeReady: function () { global.__xsReady = true; }
  };
  global.__xsDispatchHook = function (kind, arg) {
    var hooks = global.__xsHooks || {};
    try {
      if (kind === 'enable' && typeof hooks.onEnable === 'function') hooks.onEnable();
      else if (kind === 'disable' && typeof hooks.onDisable === 'function') hooks.onDisable();
      else if (kind === 'action' && typeof hooks.onAction === 'function') hooks.onAction(arg);
    } catch (e) {}
    return true;
  };

  /* XS UI Kit v3：宿主在 documentStart 注入强调色与页面底色（跟随 App 主题） */
  try {
    var accent = null;
    if (global.__XSAndroid && typeof global.__XSAndroid.accent === 'function') {
      accent = global.__XSAndroid.accent();
    }
    var accentValue = accent || '#2DB95A';
    document.documentElement.style.setProperty('--xs-accent', accentValue);
    var styleEl = document.createElement('style');
    styleEl.id = '__xs_host_theme__';
    styleEl.textContent =
      ':root{--xs-accent:' + accentValue + ';}' +
      'html,body{background:#161B1A;color:rgba(255,255,255,.88);' +
      'font-family:-apple-system,"PingFang SC","HarmonyOS Sans SC",sans-serif;}';
    (document.head || document.documentElement).appendChild(styleEl);
    if (global.__XSAndroid && typeof global.__XSAndroid.context === 'function') {
      global.__xsContext = JSON.parse(global.__XSAndroid.context());
    }
  } catch (e) {}
})(typeof window !== 'undefined' ? window : this);
""".trimIndent()

    private const val INJECT_MARK = "<!--xs-bridge-->"

    /**
     * 把桥脚本注入插件页面，保证在页面脚本之前执行。
     * 只改内存中的 HTML，不破坏原始文件。
     */
    fun injectIntoHtml(html: String): String {
        if (html.contains(INJECT_MARK)) return html
        val tag = "$INJECT_MARK<script src=\"xs-bridge.js\"></script>"
        val headIndex = html.indexOf("<head", ignoreCase = true)
        if (headIndex >= 0) {
            val close = html.indexOf('>', headIndex)
            if (close > 0) {
                return html.substring(0, close + 1) + tag + html.substring(close + 1)
            }
        }
        return tag + html
    }
}
