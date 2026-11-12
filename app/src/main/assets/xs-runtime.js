/**
 * xs-runtime v0.1 —— 插件侧 API（唯一真源，双端共用，不得改动）
 *
 * 运行环境：
 *  - 真实宿主（iOS/Android QuickJS）：注入 `globalThis.__xsHost__`
 *  - Node 测试宿主（tooling/xs-pack test）：注入 `globalThis.__xsMockHost__`（见 sdk/mock-host.js）
 *
 * 宿主桥（__xsHost__）必须实现：
 *  - invoke(module, method, args) -> Promise        // Host API 调用（主协议 §7）
 *  - render(rootNode)                               // 挂载视图树
 *  - update(rootNode)                               // 重渲染
 *  - setContext(snapshot)                           // 注入/更新 Context 快照（主协议 §4）
 *  - lifecycleHooks(hooks)                          // 接收 xs.lifecycle 注册的钩子
 */
(function (global) {
  'use strict'

  var host = global.__xsHost__ || global.__xsMockHost__

  var lifecycle = {}
  var registered = {}
  var rootNode = null
  var contextListeners = []

  // ---- Context 快照对象：字段直读 + get()/onChange() ----
  var contextObj = {
    get: function () { return host && typeof host.getContext === 'function' ? host.getContext() : {} },
    onChange: function (cb) {
      contextListeners.push(cb)
      return function () {
        var i = contextListeners.indexOf(cb)
        if (i >= 0) contextListeners.splice(i, 1)
      }
    }
  }

  // ---- View API（主协议 §6.2）----
  function create(type, props, style) {
    return {
      type: type,
      props: props || {},
      style: style || {},
      children: [],
      appendChild: function (child) {
        if (child) this.children.push(child)
        return child
      }
    }
  }
  function appendChild(parent, child) {
    if (parent && child) parent.children.push(child)
    return child
  }
  function render(root) {
    rootNode = root
    if (host && typeof host.render === 'function') host.render(root)
  }
  function update() {
    if (host && typeof host.update === 'function' && rootNode) host.update(rootNode)
  }

  // ---- Host API 面（主协议 §7，双端签名一致）----
  // v2 渲染类模块保留（playback/lyrics/context/navigation/theme/system 等），
  // v3 能力类模块（cast/musicSource/push/device/ui/media/library/storage/network/notify/share）见主协议 §7.1/§7.2
  var API = {
    // 渲染类（schema v2，既有）
    playback: ['play', 'pause', 'toggle', 'next', 'prev', 'seekTo', 'getState', 'getQueue', 'setQueue'],
    lyrics: ['getLines', 'seekToLine', 'setOverride'],
    context: ['get', 'onChange'],
    navigation: ['openPage', 'close'],
    theme: ['get', 'onChange'],
    system: ['toast', 'haptics', 'share', 'setTitle'],
    // 能力类（schema v3）
    cast: ['discover', 'scan', 'play', 'pause', 'resume', 'stop', 'seek', 'status', 'onDeviceChange'],
    musicSource: ['search', 'getPlayUrl', 'getLyrics', 'getArtwork', 'registerProvider'],
    push: ['setEnabled', 'send', 'cancel'],
    device: ['getBridges', 'sendCommand', 'onStatus'],
    ui: ['getTheme', 'applySkin', 'applyCoverStyle', 'applyLyricStyle', 'setNowPlaying', 'reset'],
    media: ['getCurrentSong', 'getPlaybackState', 'play', 'pause', 'toggle', 'next', 'prev', 'seekTo', 'setVolume', 'getQueue', 'addToQueue', 'removeFromQueue', 'onPlaybackStateChange'],
    library: ['search', 'getAlbums', 'getAlbum', 'getSongs', 'getPlaylists', 'getPlaylist'],
    storage: ['get', 'set', 'remove', 'getKeys', 'clear'],
    network: ['request', 'fetch'],
    notify: ['send', 'cancel'],
    share: ['shareText', 'shareSong', 'sharePoster'],
    ai: ['chat', 'status']
  }
  var hostApi = {}
  Object.keys(API).forEach(function (mod) {
    hostApi[mod] = {}
    API[mod].forEach(function (method) {
      hostApi[mod][method] = function () {
        var args = Array.prototype.slice.call(arguments)
        if (host && typeof host.invoke === 'function') return host.invoke(mod, method, args)
        return Promise.resolve({ error: 'no_host' })
      }
    })
  })

  // ---- 确定性工具（golden 测试友好：无随机源）----
  var utils = {
    formatTime: function (ms) {
      var s = Math.max(0, Math.floor((ms || 0) / 1000))
      var m = Math.floor(s / 60)
      var r = s % 60
      return m + ':' + (r < 10 ? '0' : '') + r
    },
    formatCount: function (n) {
      n = n || 0
      if (n >= 10000) return (n / 10000).toFixed(1) + '万'
      return String(n)
    },
    clamp: function (v, min, max) { return Math.min(max, Math.max(min, v)) },
    throttle: function (fn, wait) {
      var last = 0
      var timer = null
      return function () {
        var now = Date.now()
        if (now - last >= wait) {
          last = now
          fn.apply(null, arguments)
        } else {
          if (timer) clearTimeout(timer)
          timer = setTimeout(fn, wait - (now - last))
        }
      }
    }
  }

  var xs = {
    version: '0.1.0',
    create: create,
    appendChild: appendChild,
    render: render,
    update: update,
    lifecycle: function (hooks) {
      lifecycle = hooks || {}
      if (host && typeof host.lifecycleHooks === 'function') host.lifecycleHooks(lifecycle)
    },
    // ---- 能力插件（schema v3）：注册 onEnable/onDisable/onAction（主协议 §3.2）----
    register: function (def) {
      registered = def || {}
      if (host && typeof host.registerHooks === 'function') host.registerHooks(registered)
      return registered
    },
    context: contextObj,
    host: hostApi,
    utils: utils,
    _internal: {
      lifecycle: lifecycle,
      registered: registered,
      getRoot: function () { return rootNode }
    }
  }

  global.xs = xs
  if (host && typeof host.onRuntimeReady === 'function') host.onRuntimeReady(xs)
})(typeof globalThis !== 'undefined' ? globalThis : this)
