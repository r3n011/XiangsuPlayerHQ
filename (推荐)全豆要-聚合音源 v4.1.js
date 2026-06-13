/*!
 * @name 全豆要[聚合音源]
 * @description 迭代4.1版本，聚合 星海/溯音/念心/长青/歌一刀专属汽水音乐，多链路自动回退
 * @version v4.1
 * @author 全豆要 and Gemini优化 Toskysun去混淆 TZB679兼容性处理，修复部分平台播放无法获取链接语音问题
 */

// --- 常量定义 ---
const CACHE_TTL_MS = 21600000;
const CACHE_MAX_SIZE = 500;
const HTTP_URL_REGEX = /^https?:\/\//i;

// API 端点
const XINGHAI_MAIN_API = "https://music-api.gdstudio.xyz/api.php?use_xbridge3=true&loader_name=forest&need_sec_link=1&sec_link_scene=im&theme=light";
const XINGHAI_BACKUP_API = "https://music-dl.sayqz.com/api/";
const SUYIN_QQ_API = "https://oiapi.net/api/QQ_Music";
const SUYIN_QQ_KEY = "oiapi-ef6133b7-ac2f-dc7d-878c-d3e207a82575";
const SUYIN_163_API = "https://oiapi.net/api/Music_163";
const SUYIN_KUWO_API = "https://oiapi.net/api/Kuwo";
const SUYIN_MIGU_API = "https://api.xcvts.cn/api/music/migu";

// 长青SVIP URL模板
const CHANGQING_URL_TEMPLATES = {
  tx: "http://175.27.166.236/kgqq/qq.php?type=mp3&id={id}&level={level}",
  wy: "http://175.27.166.236/wy/wy.php?type=mp3&id={id}&level={level}",
  kw: "https://musicapi.haitangw.net/music/kw.php?type=mp3&id={id}&level={level}",
  kg: "https://music.haitangw.cc/kgqq/kg.php?type=mp3&id={id}&level={level}",
  mg: "https://music.haitangw.cc/musicapi/mg.php?type=mp3&id={id}&level={level}"
};

// 念心SVIP URL模板
const NIANXIN_URL_TEMPLATES = {
  tx: "https://music.nxinxz.com/kgqq/tx.php?id={id}&level={level}&type=mp3",
  wy: "http://music.nxinxz.com/wy.php?id={id}&level={level}&type=mp3",
  kw: "http://music.nxinxz.com/kw.php?id={id}&level={level}&type=mp3",
  kg: "https://music.nxinxz.com/kgqq/kg.php?id={id}&level={level}&type=mp3",
  mg: "http://music.nxinxz.com/mg.php?id={id}&level={level}&type=mp3"
};

// 汽水VIP
const QISHUI_SOURCE_ID = "qsvip";
const QISHUI_SOURCE_NAME = "汽水VIP";
const QISHUI_API_HTTPS = "https://api.vsaa.cn/api/music.qishui.vip";
const QISHUI_API_HTTP = "http://api.vsaa.cn/api/music.qishui.vip";
const QISHUI_PROXY_API = "https://proxy.qishui.vsaa.cn/qishui/proxy";

// 各平台支持的音质列表
const PLATFORM_QUALITIES = {
  wy: ["24bit", "flac", "320k", "192k", "128k"],
  tx: ["24bit", "flac", "320k", "192k", "128k"],
  kw: ["24bit", "flac", "320k", "192k", "128k"],
  kg: ["24bit", "flac", "320k", "192k", "128k"],
  mg: ["24bit", "flac", "320k", "192k", "128k"]
};

// 平台ID映射到星海主API名称
const PLATFORM_TO_XINGHAI = {
  wy: "netease",
  tx: "tencent",
  kw: "kuwo",
  kg: "kugou",
  mg: "migu"
};

// 音质到星海主API码率参数
const QUALITY_TO_BR = {
  "128k": "128",
  "192k": "192",
  "320k": "320",
  flac: "740",
  flac24bit: "999",
  "24bit": "999"
};

// 平台ID映射到星海备API名称
const PLATFORM_TO_XINGHAI_BACKUP = {
  wy: "netease",
  tx: "qq",
  kw: "kuwo"
};

// 音质到溯音QQ码率参数
const QUALITY_TO_SUYIN_QQ_BR = {
  "128k": 7,
  "320k": 5,
  flac: 4,
  hires: 3,
  atmos: 2,
  master: 1,
  "24bit": 1
};

// 音质到溯音酷我码率参数
const QUALITY_TO_KUWO_BR = {
  flac: 1,
  "320k": 5,
  "128k": 7,
  "24bit": 1
};

// 高品质音质集合
const HIRES_QUALITY_SET = new Set(["24bit", "flac", "flac24bit", "hires", "master", "atmos"]);

// URL缓存
const urlCache = new Map();

const {
  EVENT_NAMES,
  request,
  on,
  send
} = globalThis.lx;

// 空函数（占位/日志）
function noop() {}

// 发起HTTP请求，返回 Promise<{statusCode, headers, body}>
function httpRequest(url, options = { method: "GET" }) {
  return new Promise((resolve, reject) => {
    request(url, {
      timeout: 2000,
      ...options
    }, (err, res) => {
      if (err) {
        return reject(new Error("请求错误: " + err.message));
      }
      let body = res?.body;
      if (typeof body === "string") {
        const trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
          try {
            body = JSON.parse(trimmed);
          } catch (e2) {}
        }
      }
      resolve({
        statusCode: res?.statusCode ?? 0,
        headers: res?.headers || {},
        body: body
      });
    });
  });
}

// 发起GET请求，自动拼接查询参数，返回响应body
async function httpGet(url, params = {}) {
  const queryStr = Object.keys(params)
    .filter(k => params[k] !== undefined && params[k] !== null)
    .map(k => encodeURIComponent(k) + "=" + encodeURIComponent(params[k]))
    .join("&");
  const sep = url.includes("?") ? "&" : "?";
  const fullUrl = "" + url + (queryStr ? sep + queryStr : "");
  const res = await httpRequest(fullUrl, {
    method: "GET",
    timeout: 2000
  });
  if (res.statusCode >= 400) {
    throw new Error("HTTP错误: " + res.statusCode);
  }
  return res.body;
}

// 构建查询字符串（带前导 ?）
function buildQueryString(params = {}) {
  const parts = Object.keys(params)
    .filter(k => params[k] !== undefined && params[k] !== null)
    .map(k => encodeURIComponent(String(k)) + "=" + encodeURIComponent(String(params[k])));
  if (parts.length) {
    return "?" + parts.join("&");
  } else {
    return "";
  }
}

// 带fallback的GET请求（汽水VIP自动尝试https/http）
async function httpGetWithFallback(url, params = {}, timeout =