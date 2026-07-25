/*!
 * @name 鏄熸捣闊充箰婧 
 * @description GDAPI | 鑱氬悎 | ChKSz API | 鍏ㄥ钩鍙版敮鎸 24FLAC锛岀綉鏄撱€侀叿鐙楁渶楂樻敮鎸佹瘝甯 
 * @version v3.2.9  1锛屼紭鍖栧惎鍔ㄩ€熷害锛 2,浼樺寲缃戞槗闊宠川鍖归厤闂锛 3锛 3.2.8鐗堟湰浠ヤ笅鐨勬帴鍙ｄ笉鍐嶇淮鎶わ紝musicfree锛宬w1.1.0浠ヤ笅涔熶笉缁存姢
 * @author 涓囧幓浜嗕簡
 * @homepage https://zrcdy.dpdns.org/
 * @lastUpdate 2026-07-23
 * @md5 03f54caaa9508b23bcaf311500c207a0
 */

const { EVENT_NAMES, request, on, send, env } = globalThis.lx;

const URL_CONFIG = {
    domains: {
        primary: 'yy.zddyr.top',
        fallback: 'zrcdy.dpdns.org',
        gdStudio: 'music-api.gdstudio.xyz',
        vip: 'api.chksz.top'
    },
    paths: {
        backend: '/lx/api/',
        version: '/lx/versionh2.php',
        update: '/lx/vers.php',
        ip: '/ip.php',
        gdApi: '/api.php',
        vipApi: '/api/163_music'
    },
    gdParams: 'use_xbridge3=true&loader_name=forest&need_sec_link=1&sec_link_scene=im&theme=light'
};

const buildUrl = (domainKey, pathKey, extraQuery = '') => {
    const domain = URL_CONFIG.domains[domainKey];
    const path = URL_CONFIG.paths[pathKey];
    if (!domain || !path) throw new Error(`URL閰嶇疆閿欒: ${domainKey} / ${pathKey}`);
    return `https://${domain}${path}${extraQuery}`;
};

const SCRIPT_VERSION = 'v3.2.9';
const SOURCE_MAP = { tx: 'qq', mg: 'migu', kw: 'kw', kg: 'kg' };
const PLATFORM_NAMES = { wy: '缃戞槗浜戦煶涔 ', tx: 'QQ闊充箰', kw: '閰锋垜闊充箰', kg: '閰风嫍闊充箰', mg: '鍜挄闊充箰' };
const MUSIC_QUALITIES = {
    wy: ['128k','192k','320k','flac','flac24bit','hires','jyeffect','sky','jymaster'],
    tx: ['128k','192k','320k','flac'],
    kw: ['128k','192k','320k','flac','flac24bit'],
    kg: ['128k','320k','flac','hires','atmos','master'],
    mg: ['128k','320k','flac']
};
const NETEASE_VIP_LEVEL_MAP = { flac: 'lossless', flac24bit: 'hires', hires: 'hires', jyeffect: 'jyeffect', sky: 'sky', jymaster: 'jymaster' };
const NETEASE_VIP_QUALITY_SET = new Set(Object.keys(NETEASE_VIP_LEVEL_MAP));

let userIp = null;
let availablePlatforms = [];
const extraCache = new Map();

function isBuffer(obj) {
    return obj && typeof obj === 'object' &&
        ((typeof Buffer !== 'undefined' && Buffer.isBuffer(obj)) ||
        (typeof obj.constructor === 'function' && obj.constructor.name === 'Buffer'));
}

function safeParseBody(body) {
    if (typeof body === 'string') {
        const trimmed = body.trim();
        if (/^[{["]/.test(trimmed)) { try { return JSON.parse(trimmed); } catch (e) {} }
        return body;
    }
    if (typeof body === 'object' && body !== null) {
        try { if (typeof body.toString === 'function' && body.toString() !== '[object Object]') body = body.toString('utf-8'); } catch (e) {}
        if (typeof body === 'object' && !isBuffer(body)) return body;
    }
    try {
        if (isBuffer(body)) {
            if (globalThis.lx?.utils?.buffer?.bufToString) body = globalThis.lx.utils.buffer.bufToString(body, 'utf-8');
            else if (typeof Buffer !== 'undefined') body = Buffer.from(body).toString('utf-8');
            else body = String(body);
        }
    } catch (e) {}
    if (typeof body === 'string') {
        const trimmed = body.trim();
        if (/^[{["]/.test(trimmed)) { try { return JSON.parse(trimmed); } catch (e) {} }
    }
    return body;
}

const httpFetch = (url, options = {}) => new Promise((resolve, reject) => {
    const start = Date.now();
    request(url, options, (err, resp) => {
        if (err) return reject(err);
        const body = safeParseBody(resp.body);
        resolve({ body, statusCode: resp.statusCode, headers: resp.headers || {}, elapsed: Date.now() - start });
    });
});

function mapQuality(target, avail) {
    const pm = { '鑷诲搧姣嶅甫': 'jymaster', '鑷诲搧闊宠川2.0': 'sky', '鑷诲搧闊宠川AI': 'jyeffect', '鑷诲搧闊宠川': 'jyeffect', 'Hires 鏃犳崯24-Bit': 'hires', 'Hi-Res': 'hires', 'FLAC': 'flac', '320k': '320k', '192k': '192k', '128k': '128k' };
    if (avail.includes(target)) return target;
    const m = pm[target]; if (m && avail.includes(m)) return m;
    const order = ['jymaster', 'sky', 'jyeffect', 'hires', 'flac24bit', 'flac', '320k', '192k', '128k'];
    for (const q of order) if (avail.includes(q)) return q;
    return avail[0] || '128k';
}

async function fetchIp() {
    try {
        const r = await httpFetch(buildUrl('primary', 'ip'), { timeout: 3000 });
        if (r.body?.ip) userIp = r.body.ip;
    } catch (e) {}
}

async function getWyGDUrl(id, q) {
    const brMap = { '128k':'128','192k':'192','320k':'320','flac':'740','flac24bit':'999' };
    const url = buildUrl('gdStudio', 'gdApi', `&${URL_CONFIG.gdParams}&types=url&source=netease&id=${id}&br=${brMap[q]||'320'}`);
    const resp = await httpFetch(url, { headers: { 'User-Agent': 'LX-Music-Mobile' }, timeout: 8000 });
    if (resp.statusCode !== 200 || !resp.body.url) throw new Error('GD鏈繑鍥為煶棰 ');
    return { url: resp.body.url, lyric: null, cover: null };
}

async function getWyVipUrl(id, q) {
    const level = NETEASE_VIP_LEVEL_MAP[q];
    if (!level) throw new Error('涓嶆敮鎸佽鍝佽川');
    const url = buildUrl('vip', 'vipApi', `?id=${id}&level=${level}`);
    const resp = await httpFetch(url, { headers: { 'User-Agent': 'LX-Music-Mobile' }, timeout: 8000 });
    if (resp.statusCode !== 200 || resp.body.code !== 200 || !resp.body.data?.url) throw new Error('VIP鏈繑鍥為煶棰 ');
    return { url: resp.body.data.url, lyric: null, cover: null };
}

async function getUrlFromBackend(source, musicInfo, quality) {
    const backendSource = SOURCE_MAP[source] || source;
    const baseUrl = buildUrl('primary', 'backend');
    const params = {};

    if (backendSource === 'kg') {
        const types = musicInfo._types || {};
        params.source = 'kg';
        params.quality = quality || '';
        params.songmid = musicInfo.songmid || musicInfo.id || '';
        params.albumId = musicInfo.albumId || '';
        params.mainHash = musicInfo.hash || '';
        if (types[quality]?.hash) params.hash = types[quality].hash;
    } else {
        params.source = backendSource;
        params.name = musicInfo.name || '';
        params.singer = musicInfo.singer || '';
        params.songmid = musicInfo.songmid || musicInfo.id || '';
        params.interval = musicInfo.interval || '';
        params.albumName = musicInfo.albumName || musicInfo.album || '';
        params.quality = quality || '';
    }

    if (userIp) params.ip = userIp;
    const query = Object.keys(params).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`).join('&');
    const url = `${baseUrl}?${query}`;
    const resp = await httpFetch(url, { method: 'GET', timeout: 8000 });

    if (resp.statusCode !== 200) throw new Error(`鍚庣鐘舵€佺爜 ${resp.statusCode}`);
    const data = resp.body;
    if (data.code !== 200 || !data.url) throw new Error(data.msg || '鏃犲彲鐢ㄩ摼鎺 ');
    return { url: data.url, lyric: data.lrc || null, cover: data.picture || null };
}

async function fetchMusicUrl(source, musicInfo, quality) {
    const id = musicInfo.hash ?? musicInfo.songmid ?? musicInfo.id;
    if (!id) throw new Error('缂哄皯 songId');
    const actualQuality = mapQuality(quality, MUSIC_QUALITIES[source] || ['128k','320k','flac']);
    let result = { url: '', lyric: null, cover: null };

    if (source === 'wy') {
        if (NETEASE_VIP_QUALITY_SET.has(actualQuality)) {
            try { result = await getWyVipUrl(id, actualQuality); } catch (e) {}
        }
        if (!result.url) result = await getWyGDUrl(id, actualQuality);
    } else {
        result = await getUrlFromBackend(source, musicInfo, actualQuality);
    }

    extraCache.set(id, { lyric: result.lyric, cover: result.cover });
    return result.url;
}

async function tryFetchConfig(url, timeout = 5000) {
    try {
        const resp = await httpFetch(url, { timeout });
        if (resp.statusCode === 200 && resp.body?.version) return resp.body;
    } catch (e) {}
    return null;
}

async function initPlatforms() {
    let latestVersion = null;
    try {
        latestVersion = await Promise.any([tryFetchConfig(buildUrl('primary', 'version')), tryFetchConfig(buildUrl('fallback', 'version'))]);
    } catch (e) {}

    if (latestVersion && compareVersions(latestVersion.version, SCRIPT_VERSION) > 0) {
        send(EVENT_NAMES.updateAlert, { log: latestVersion.changelog || `鍙戠幇鏂扮増鏈  ${latestVersion.version}`, updateUrl: latestVersion.update_url || buildUrl('fallback', 'update') });
    }
    availablePlatforms = ['wy', 'tx', 'kg', 'kw', 'mg'];
}

function compareVersions(a, b) {
    const v1 = a.replace(/^v/, '').split('.').map(Number);
    const v2 = b.replace(/^v/, '').split('.').map(Number);
    for (let i = 0; i < Math.max(v1.length, v2.length); i++) {
        if ((v1[i] || 0) > (v2[i] || 0)) return 1;
        if ((v1[i] || 0) < (v2[i] || 0)) return -1;
    }
    return 0;
}

on(EVENT_NAMES.request, async ({ action, source, info }) => {
    if (!source || !MUSIC_QUALITIES[source]) throw new Error(`涓嶆敮鎸佺殑闊充箰婧 : ${source}`);
    if (action === 'musicUrl') {
        if (!info?.musicInfo || !info.type) throw new Error('鍙傛暟涓嶅畬鏁 ');
        return fetchMusicUrl(source, info.musicInfo, info.type);
    }
    const id = info?.musicInfo?.hash ?? info?.musicInfo?.songmid ?? info?.musicInfo?.id;
    const cached = extraCache.get(id);
    if (action === 'lyric') return cached?.lyric ? { lyric: cached.lyric, tlyric: '' } : null;
    if (action === 'pic') return cached?.cover || null;
    throw new Error(`涓嶆敮鎸佺殑鎿嶄綔: ${action}`);
});

(async () => {
    await initPlatforms();
    fetchIp();
    const sources = {};
    availablePlatforms.forEach(p => { sources[p] = { name: PLATFORM_NAMES[p], type: 'music', actions: ['musicUrl', 'lyric', 'pic'], qualitys: MUSIC_QUALITIES[p] }; });
    send(EVENT_NAMES.inited, { status: true, sources });
})();