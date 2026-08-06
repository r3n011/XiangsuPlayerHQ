@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection", "TooManyFunctions")

package net.moriafly.ncm

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 网易云音乐本地 SDK 门面（单例）—— Android App 唯一调用入口
 *
 * ✅ 0 外部依赖：
 *    - javax.crypto（AES/RSA）  →  JDK 自带
 *    - java.net.HttpURLConnection  →  JDK 自带
 *    - timber.log.Timber         →  大多数项目已有；没有的话把 Timber 调用删/换为 android.util.Log
 *    - SharedPreferences          →  Android SDK
 *
 * 🚀 接入方式（3 行）：
 * ```kotlin
 * // 1. 在 Application.onCreate：
 * NcmApi.install(this)
 *
 * // 2. 登录后即可调：
 * lifecycleScope.launch {
 *     val r = NcmApi.search("周杰伦", limit = 10).getOrThrow()
 *     val songs = (r["result"] as? Map<*, *>)?.get("songs") as? List<*> ?: return@launch
 * }
 * ```
 */
object NcmApi {

    // ============================================================
    // 初始化
    // ============================================================

    /**
     * App 启动时调用一次（建议在 Application.onCreate）
     * @param appContext  Application context
     * @param os          加密平台："pc"（默认）/ "android" / "ios" —— 决定 UA + appver
     * @param realIp      可选：注入 X-Real-IP（对海外 IP 限制的场景有用）
     * @param proxy       可选：HTTP 代理 "http://127.0.0.1:7890"
     */
    fun install(
        appContext: Context,
        os: String = "pc",
        realIp: String? = null,
        proxy: String? = null,
    ) {
        val ctx = appContext.applicationContext
        val session = NcmSession.install(ctx)
        session.os = os
        session.realIp = realIp
        session.proxy = proxy
        if (installed) return
        installed = true
        Timber.d("[NcmApi] installed os=$os realIp=$realIp proxy=$proxy")
    }

    /**
     * Java 友好的 Application.onCreate 快捷封装
     */
    fun install(app: Application) = install(app.applicationContext)

    @Volatile
    private var installed: Boolean = false

    private fun ensureInstalled() {
        if (!installed && NcmSession.INSTANCE == null) {
            error("NcmApi 未安装！请先在 Application.onCreate 调用 NcmApi.install(this)")
        }
    }

    val isLogin: Boolean get() = NcmSession.INSTANCE?.isLogin == true
    val userId: Long? get() = NcmSession.INSTANCE?.userId

    /** 运行期切换 os（不同接口对 os=pc 比较友好） */
    fun setOs(os: String) {
        NcmSession.INSTANCE?.os = os
    }

    fun setRealIp(ip: String?) { NcmSession.INSTANCE?.realIp = ip }
    fun setProxy(proxy: String?) { NcmSession.INSTANCE?.proxy = proxy }

    // ============================================================
    // 搜索
    // ============================================================

    suspend fun searchDefault() = runSafely("searchDefault") {
        ensureInstalled()
        NcmModules.searchDefault()
    }

    /**
     * @param type 1=单曲 / 10=专辑 / 100=歌手 / 1000=歌单 / 1004=MV / 1009=电台 / 1014=视频 / 1018=综合
     */
    suspend fun search(
        keyword: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ) = runSafely("search") {
        ensureInstalled()
        NcmModules.search(keyword, type, limit, offset)
    }

    // ============================================================
    // 歌曲播放
    // ============================================================

    /**
     * @param level standard / higher / exhigh / lossless / hires / jyeffect / jymaster / sky
     * @param encodeType flac / mp3 / aac
     */
    suspend fun songUrlV1(
        ids: List<String>,
        level: String = "standard",
        encodeType: String = "flac",
    ) = runSafely("songUrlV1") {
        ensureInstalled()
        NcmModules.songUrlV1(ids, level, encodeType)
    }

    suspend fun songUrl(id: String, br: Int = 320_000) = songUrlV1(listOf(id),
        level = when (br) {
            in 0..128_000 -> "standard"
            in 128_001..192_000 -> "higher"
            in 192_001..320_000 -> "exhigh"
            in 320_001..1_411_000 -> "lossless"
            else -> "hires"
        },
        encodeType = if (br > 1_500_000) "flac" else "mp3",
    )

    /** 判断歌曲是否可播放（VIP 灰歌、下架） */
    suspend fun checkMusicPlayable(id: String, br: Int = 320_000) = runSafely("checkMusic") {
        ensureInstalled()
        NcmModules.checkMusicPlayable(id, br)
    }

    suspend fun songDetail(ids: List<String>) = runSafely("songDetail") {
        ensureInstalled()
        NcmModules.songDetail(ids)
    }

    // ============================================================
    // 登录（手机号/验证码/密码）
    // ============================================================

    /** 发送短信验证码 */
    suspend fun sentSmsCaptcha(phone: String, ctcode: String = "86") = runSafely("sentSmsCaptcha") {
        ensureInstalled()
        NcmModules.sentSmsCaptcha(phone, ctcode)
    }

    /** 校验短信验证码 */
    suspend fun captchaVerify(phone: String, captcha: String, ctcode: String = "86") =
        runSafely("captchaVerify") {
            ensureInstalled()
            NcmModules.captchaVerify(phone, captcha, ctcode)
        }

    /** 手机号登录：captcha 或 password 二选一 */
    suspend fun loginCellphone(
        phone: String,
        captcha: String? = null,
        password: String? = null,
        ctcode: String = "86",
        countrycode: String? = null,
    ) = runSafely("loginCellphone") {
        ensureInstalled()
        NcmModules.loginCellphone(phone, captcha, password, ctcode, countrycode)
    }

    /** cookie 续期 / 登录态检查（App 启动时调用一次，若返回 200 说明登录有效） */
    suspend fun loginRefresh() = runSafely("loginRefresh") {
        ensureInstalled()
        NcmModules.loginRefresh()
    }

    suspend fun logout() = runSafely("logout") {
        ensureInstalled()
        NcmModules.logout()
    }

    // ============================================================
    // 二维码登录（推荐用这个 —— 不需要账号密码 / 短信）
    // ============================================================

    data class QrLoginResult(
        val key: String,
        /** Base64 PNG 图片，直接 setImageBitmap(BitmapFactory.decodeByteArray) */
        val qrPngBase64: String,
    )

    /** Step 1 + 2：一步到位返回 key + Base64 PNG 二维码图 */
    suspend fun qrLoginPrepare(): Result<QrLoginResult> = runSafely("qrLoginPrepare") {
        ensureInstalled()
        val k = NcmModules.qrLoginKey().getOrThrow()
        val img = NcmModules.qrLoginImage(k.key).getOrThrow()
        Result.success(QrLoginResult(k.key, img))
    }

    /**
     * Step 3：轮询扫码状态（120s 超时）
     * @param onStatus code: 801=等待扫码 / 802=已扫未确认 / 803=登录成功 / 800=过期
     */
    suspend fun qrLoginAwait(
        key: String,
        onStatus: suspend (code: Int, raw: Map<String, Any?>) -> Unit = { _, _ -> },
    ) = runSafely("qrLoginAwait") {
        ensureInstalled()
        NcmModules.qrLoginAwait(key, onStatus)
    }

    // ============================================================
    // 首页 / 歌单 / 用户
    // ============================================================

    suspend fun banner(type: Int = 1) = runSafely("banner") {
        ensureInstalled()
        NcmModules.banner(type)
    }

    suspend fun playlistDetail(id: String) = runSafely("playlistDetail") {
        ensureInstalled()
        NcmModules.playlistDetail(id)
    }

    suspend fun userAccount() = runSafely("userAccount") {
        ensureInstalled()
        NcmModules.userAccount()
    }

    // ============================================================
    // 兜底异常包装（把 Kotlin 异常统一包成 Result.failure，便于上层统一 .getOrThrow / .onFailure）
    // ============================================================

    private suspend inline fun <T> runSafely(
        tag: String,
        crossinline block: suspend () -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (t: Throwable) {
            Timber.w(t, "[NcmApi.$tag] failed")
            Result.failure(t as? Exception ?: Exception(t.message ?: "unknown", t))
        }
    }

    // ============================================================
    // Session 管理（调试/调试期有用）
    // ============================================================

    fun dumpSession(): String {
        val sess = NcmSession.INSTANCE ?: return "(not installed)"
        val c = sess.cookies
        val keys = c.keys.joinToString()
        return "os=${sess.os}, realIp=${sess.realIp}, proxy=${sess.proxy}, isLogin=${sess.isLogin}, uid=${sess.userId}, cookies=[$keys]"
    }

    // =========================================================================
    // ★ 全量 300+ 新模块统一入口（直接委托 NcmModulesFull）
    // 调用示例：
    //   NcmApi.full.songLyric("4876940")
    //   NcmApi.full.like("4876940", true)
    //   NcmApi.full.playlistCreate("我喜欢")
    //   NcmApi.full.commentMusic("4876940", limit = 30)
    // =========================================================================

    /** 全部 300+ 模块集合（对应 /module 下的 *.js）—— IDE 会自动补全方法签名 */
    val full: FullAccess = FullAccess

    object FullAccess {
        // 搜索扩展
        suspend fun searchHot() = NcmApi.runSafely("searchHot") { NcmModulesFull.searchHot() }
        suspend fun searchHotDetail() = NcmApi.runSafely("searchHotDetail") { NcmModulesFull.searchHotDetail() }
        suspend fun searchSuggest(keywords: String, type: String? = "mobile") = NcmApi.runSafely("searchSuggest") { NcmModulesFull.searchSuggest(keywords, type) }
        suspend fun searchMultimatch(keywords: String, limit: Int = 5) = NcmApi.runSafely("searchMultimatch") { NcmModulesFull.searchMultimatch(keywords, limit) }
        suspend fun searchMatch(keywords: String) = NcmApi.runSafely("searchMatch") { NcmModulesFull.searchMatch(keywords) }
        suspend fun cloudSearch(keywords: String, type: Int = 1, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("cloudSearch") { NcmModulesFull.cloudSearch(keywords, type, limit, offset) }

        // 歌词 / 下载 / 歌曲扩展
        suspend fun songLyric(id: String, lv: Int = -1, tv: Int = -1, rv: Int = -1, kv: Int = -1) = NcmApi.runSafely("songLyric") { NcmModulesFull.songLyric(id, lv, tv, rv, kv) }
        suspend fun lyricNew(id: String, cp: Boolean = false) = NcmApi.runSafely("lyricNew") { NcmModulesFull.lyricNew(id, cp, -1, -1, -1, -1, -1, -1, -1) }
        suspend fun songDownloadUrl(id: String, br: Int = 320_000) = NcmApi.runSafely("songDownloadUrl") { NcmModulesFull.songDownloadUrl(id, br) }
        suspend fun songDownloadUrlV1(ids: List<String>, level: String = "standard", encodeType: String = "flac") = NcmApi.runSafely("songDownloadUrlV1") { NcmModulesFull.songDownloadUrlV1(ids, level, encodeType) }
        suspend fun songChorus(ids: List<String>) = NcmApi.runSafely("songChorus") { NcmModulesFull.songChorus(ids) }
        suspend fun songPurchased(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("songPurchased") { NcmModulesFull.songPurchased(limit, offset) }
        suspend fun songDownlist(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("songDownlist") { NcmModulesFull.songDownlist(limit, offset) }
        suspend fun songMonthDownlist(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("songMonthDownlist") { NcmModulesFull.songMonthDownlist(limit, offset) }
        suspend fun songSingleDownlist(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("songSingleDownlist") { NcmModulesFull.songSingleDownlist(limit, offset) }
        suspend fun songMusicDetail(ids: List<String>) = NcmApi.runSafely("songMusicDetail") { NcmModulesFull.songMusicDetail(ids) }
        suspend fun songLikeCheck(id: String) = NcmApi.runSafely("songLikeCheck") { NcmModulesFull.songLikeCheck(id) }
        suspend fun songUrl(ids: List<String>, br: Int = 320_000) = NcmApi.runSafely("songUrl") { NcmModulesFull.songUrl(ids, br) }
        suspend fun songUrlNcmget(id: String, br: Int = 320_000) = NcmApi.runSafely("songUrlNcmget") { NcmModulesFull.songUrlNcmget(id, br) }
        suspend fun songUrlMatch(id: String, br: Int = 320_000) = NcmApi.runSafely("songUrlMatch") { NcmModulesFull.songUrlMatch(id, br) }
        suspend fun songRedCount() = NcmApi.runSafely("songRedCount") { NcmModulesFull.songRedCount() }
        suspend fun songDynamicCover(ids: List<String>) = NcmApi.runSafely("songDynamicCover") { NcmModulesFull.songDynamicCover(ids) }
        suspend fun songWikiSummary(id: String) = NcmApi.runSafely("songWikiSummary") { NcmModulesFull.songWikiSummary(id) }
        suspend fun scrobble(id: String, sourceid: String = "0", time: Long = 240_000L) = NcmApi.runSafely("scrobble") { NcmModulesFull.scrobble(id, sourceid, time) }
        suspend fun getMusicFirstListenInfo(id: String) = NcmApi.runSafely("getMusicFirstListenInfo") { NcmModulesFull.getMusicFirstListenInfo(id) }

        // 相似
        suspend fun simiSong(id: String, limit: Int = 50, offset: Int = 0) = NcmApi.runSafely("simiSong") { NcmModulesFull.simiSong(id, limit, offset) }
        suspend fun simiPlaylist(id: String, limit: Int = 50, offset: Int = 0) = NcmApi.runSafely("simiPlaylist") { NcmModulesFull.simiPlaylist(id, limit, offset) }
        suspend fun simiArtist(id: String, limit: Int = 50) = NcmApi.runSafely("simiArtist") { NcmModulesFull.simiArtist(id, limit) }
        suspend fun simiMv(mvid: String, limit: Int = 50) = NcmApi.runSafely("simiMv") { NcmModulesFull.simiMv(mvid, limit) }
        suspend fun simiUser(uid: String, limit: Int = 50) = NcmApi.runSafely("simiUser") { NcmModulesFull.simiUser(uid, limit) }

        // 榜单
        suspend fun toplist() = NcmApi.runSafely("toplist") { NcmModulesFull.toplist() }
        suspend fun toplistDetail(id: String) = NcmApi.runSafely("toplistDetail") { NcmModulesFull.toplistDetail(id) }
        suspend fun toplistDetailV2(id: String, limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("toplistDetailV2") { NcmModulesFull.toplistDetailV2(id, limit, offset) }
        suspend fun toplistArtist() = NcmApi.runSafely("toplistArtist") { NcmModulesFull.toplistArtist() }
        suspend fun topSong(type: Int = 0) = NcmApi.runSafely("topSong") { NcmModulesFull.topSong(type) }
        suspend fun topArtists(limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("topArtists") { NcmModulesFull.topArtists(limit, offset) }
        suspend fun topPlaylist(cat: String = "全部", order: String = "hot", limit: Int = 50, offset: Int = 0) = NcmApi.runSafely("topPlaylist") { NcmModulesFull.topPlaylist(cat, order, limit, offset) }
        suspend fun topPlaylistHighQuality(cat: String = "全部", limit: Int = 50, before: Long = 0) = NcmApi.runSafely("topPlaylistHighQuality") { NcmModulesFull.topPlaylistHighQuality(cat, limit, before) }
        suspend fun topAlbum(area: String = "ALL", limit: Int = 50, offset: Int = 0, type: String = "new") = NcmApi.runSafely("topAlbum") { NcmModulesFull.topAlbum(area, limit, offset, type) }
        suspend fun topMv(area: String = "", type: String = "0", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("topMv") { NcmModulesFull.topMv(area, type, limit, offset) }

        // 歌手
        suspend fun artistDetail(id: String) = NcmApi.runSafely("artistDetail") { NcmModulesFull.artistDetail(id) }
        suspend fun artistDetailDynamic(id: String) = NcmApi.runSafely("artistDetailDynamic") { NcmModulesFull.artistDetailDynamic(id) }
        suspend fun artistDesc(id: String, offset: Int = 0, limit: Int = 60) = NcmApi.runSafely("artistDesc") { NcmModulesFull.artistDesc(id, offset, limit) }
        suspend fun artistAlbum(id: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistAlbum") { NcmModulesFull.artistAlbum(id, limit, offset) }
        suspend fun artists(area: Int = -1, type: Int = -1, initial: String = "-1", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artists") { NcmModulesFull.artists(area, type, initial, limit, offset) }
        suspend fun artistList(area: Int = -1, type: Int = -1, initial: String = "-1", limit: Int = 30, offset: Int = 0) = artists(area, type, initial, limit, offset)
        suspend fun artistTopSong(id: String) = NcmApi.runSafely("artistTopSong") { NcmModulesFull.artistTopSong(id) }
        suspend fun artistSublist(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistSublist") { NcmModulesFull.artistSublist(limit, offset) }
        suspend fun artistSub(id: String, t: Int = 1) = NcmApi.runSafely("artistSub") { NcmModulesFull.artistSub(id, t) }
        suspend fun artistSongs(id: String, order: String = "hot", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistSongs") { NcmModulesFull.artistSongs(id, order, limit, offset) }
        suspend fun artistNewSong(limit: Int = 30, area: Int = 97, type: Int = 0) = NcmApi.runSafely("artistNewSong") { NcmModulesFull.artistNewSong(limit, area, type) }
        suspend fun artistNewMv(artistId: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistNewMv") { NcmModulesFull.artistNewMv(artistId, limit, offset) }
        suspend fun artistMv(id: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistMv") { NcmModulesFull.artistMv(id, limit, offset) }
        suspend fun artistFollowCount(id: String) = NcmApi.runSafely("artistFollowCount") { NcmModulesFull.artistFollowCount(id) }
        suspend fun artistFans(id: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistFans") { NcmModulesFull.artistFans(id, limit, offset) }
        suspend fun artistVideo(id: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("artistVideo") { NcmModulesFull.artistVideo(id, limit, offset) }

        // 专辑
        suspend fun album(id: String) = NcmApi.runSafely("album") { NcmModulesFull.album(id) }
        suspend fun albumDetail(id: String) = NcmApi.runSafely("albumDetail") { NcmModulesFull.albumDetail(id) }
        suspend fun albumDetailDynamic(id: String) = NcmApi.runSafely("albumDetailDynamic") { NcmModulesFull.albumDetailDynamic(id) }
        suspend fun albumSublist(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("albumSublist") { NcmModulesFull.albumSublist(limit, offset) }
        suspend fun albumSub(id: String, t: Int = 1) = NcmApi.runSafely("albumSub") { NcmModulesFull.albumSub(id, t) }
        suspend fun albumPrivilege(ids: List<String>) = NcmApi.runSafely("albumPrivilege") { NcmModulesFull.albumPrivilege(ids) }
        suspend fun albumNewest(area: String? = null) = NcmApi.runSafely("albumNewest") { NcmModulesFull.albumNewest(area) }
        suspend fun albumNew(area: String = "ALL", limit: Int = 30, offset: Int = 0, type: String = "NEW") = NcmApi.runSafely("albumNew") { NcmModulesFull.albumNew(area, limit, offset, type) }
        suspend fun albumListStyle() = NcmApi.runSafely("albumListStyle") { NcmModulesFull.albumListStyle() }
        suspend fun albumList(area: String = "ALL", style: String = "", year: String = "-1", month: String = "-1", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("albumList") { NcmModulesFull.albumList(area, style, year, month, limit, offset) }
        suspend fun albumSongSaleBoard(albumType: Int = 0, year: Int = 2024, type: Int = 1) = NcmApi.runSafely("albumSongSaleBoard") { NcmModulesFull.albumSongSaleBoard(albumType, year, type) }
        suspend fun digitalAlbumDetail(id: String) = NcmApi.runSafely("digitalAlbumDetail") { NcmModulesFull.digitalAlbumDetail(id) }
        suspend fun digitalAlbumSales(id: String) = NcmApi.runSafely("digitalAlbumSales") { NcmModulesFull.digitalAlbumSales(id) }
        suspend fun digitalAlbumOrdering(limit: Int = 10) = NcmApi.runSafely("digitalAlbumOrdering") { NcmModulesFull.digitalAlbumOrdering(limit) }
        suspend fun digitalAlbumPurchased(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("digitalAlbumPurchased") { NcmModulesFull.digitalAlbumPurchased(limit, offset) }

        // 歌单
        suspend fun playlistCreate(name: String, privacy: Int = 0, type: String = "NORMAL") = NcmApi.runSafely("playlistCreate") { NcmModulesFull.playlistCreate(name, privacy, type) }
        suspend fun playlistDelete(id: String) = NcmApi.runSafely("playlistDelete") { NcmModulesFull.playlistDelete(id) }
        suspend fun playlistTrackAdd(pid: String, ids: List<String>) = NcmApi.runSafely("playlistTrackAdd") { NcmModulesFull.playlistTrackAdd(pid, ids) }
        suspend fun playlistTrackDelete(pid: String, ids: List<String>) = NcmApi.runSafely("playlistTrackDelete") { NcmModulesFull.playlistTrackDelete(pid, ids) }
        suspend fun playlistTrackAll(id: String, limit: Int = 1000, offset: Int = 0, s: Int = 8) = NcmApi.runSafely("playlistTrackAll") { NcmModulesFull.playlistTrackAll(id, limit, offset, s) }
        suspend fun playlistTracks(id: String, limit: Int = 20, offset: Int = 0, s: Int = 8) = NcmApi.runSafely("playlistTracks") { NcmModulesFull.playlistTracks(id, limit, offset, s) }
        suspend fun playlistSubscribe(id: String, t: Int = 1) = NcmApi.runSafely("playlistSubscribe") { NcmModulesFull.playlistSubscribe(id, t) }
        suspend fun playlistUnsubscribe(id: String) = playlistSubscribe(id, 0)
        suspend fun playlistSubscribers(id: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("playlistSubscribers") { NcmModulesFull.playlistSubscribers(id, limit, offset) }
        suspend fun playlistNameUpdate(id: String, name: String) = NcmApi.runSafely("playlistNameUpdate") { NcmModulesFull.playlistNameUpdate(id, name) }
        suspend fun playlistDescUpdate(id: String, desc: String) = NcmApi.runSafely("playlistDescUpdate") { NcmModulesFull.playlistDescUpdate(id, desc) }
        suspend fun playlistCoverUpdate(id: String, imgFileId: Long, imgSize: Long = 10_000_000L) = NcmApi.runSafely("playlistCoverUpdate") { NcmModulesFull.playlistCoverUpdate(id, imgFileId, imgSize) }
        suspend fun playlistTagsUpdate(id: String, tags: List<String>) = NcmApi.runSafely("playlistTagsUpdate") { NcmModulesFull.playlistTagsUpdate(id, tags) }
        suspend fun playlistPrivacy(id: String, privacy: Int = 10) = NcmApi.runSafely("playlistPrivacy") { NcmModulesFull.playlistPrivacy(id, privacy) }
        suspend fun playlistOrderUpdate(ids: List<String>) = NcmApi.runSafely("playlistOrderUpdate") { NcmModulesFull.playlistOrderUpdate(ids) }
        suspend fun playlistMyLike(uid: String) = NcmApi.runSafely("playlistMyLike") { NcmModulesFull.playlistMyLike(uid) }
        suspend fun playlistHot() = NcmApi.runSafely("playlistHot") { NcmModulesFull.playlistHot() }
        suspend fun playlistHighqualityTags() = NcmApi.runSafely("playlistHighqualityTags") { NcmModulesFull.playlistHighqualityTags() }
        suspend fun playlistDetailRcmdGet(id: String) = NcmApi.runSafely("playlistDetailRcmdGet") { NcmModulesFull.playlistDetailRcmdGet(id) }
        suspend fun playlistDetailDynamic(id: String) = NcmApi.runSafely("playlistDetailDynamic") { NcmModulesFull.playlistDetailDynamic(id) }
        suspend fun playlistCatlist() = NcmApi.runSafely("playlistCatlist") { NcmModulesFull.playlistCatlist() }
        suspend fun playlistCategoryList() = NcmApi.runSafely("playlistCategoryList") { NcmModulesFull.playlistCategoryList() }
        suspend fun playlistUpdate(id: String, updater: String = "") = NcmApi.runSafely("playlistUpdate") { NcmModulesFull.playlistUpdate(id, updater) }
        suspend fun playlistUpdatePlaycount(id: String) = NcmApi.runSafely("playlistUpdatePlaycount") { NcmModulesFull.playlistUpdatePlaycount(id) }
        suspend fun playlistVideoRecent(id: String, limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("playlistVideoRecent") { NcmModulesFull.playlistVideoRecent(id, limit, offset) }
        suspend fun playlistImportNameTaskCreate(playlistId: String, name: String, singer: String = "") = NcmApi.runSafely("playlistImportNameTaskCreate") { NcmModulesFull.playlistImportNameTaskCreate(playlistId, name, singer) }
        suspend fun playlistImportTaskStatus(taskId: String) = NcmApi.runSafely("playlistImportTaskStatus") { NcmModulesFull.playlistImportTaskStatus(taskId) }
        suspend fun plCount() = NcmApi.runSafely("plCount") { NcmModulesFull.plCount() }
        suspend fun playmodeIntelligenceList(id: String, sid: String, count: Int = 1, startMusicId: String = sid) = NcmApi.runSafely("playmodeIntelligenceList") { NcmModulesFull.playmodeIntelligenceList(id, sid, count, startMusicId) }
        suspend fun playmodeSongVector(id: String) = NcmApi.runSafely("playmodeSongVector") { NcmModulesFull.playmodeSongVector(id) }

        // 喜欢
        suspend fun like(id: String, like: Boolean = true, time: Long = System.currentTimeMillis()) = NcmApi.runSafely("like") { NcmModulesFull.like(id, like, time) }
        suspend fun likelist(uid: String) = NcmApi.runSafely("likelist") { NcmModulesFull.likelist(uid) }

        // 评论
        suspend fun commentMusic(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentMusic") { NcmModulesFull.commentMusic(id, limit, offset, beforeTime) }
        suspend fun commentAlbum(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentAlbum") { NcmModulesFull.commentAlbum(id, limit, offset, beforeTime) }
        suspend fun commentPlaylist(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentPlaylist") { NcmModulesFull.commentPlaylist(id, limit, offset, beforeTime) }
        suspend fun commentMv(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentMv") { NcmModulesFull.commentMv(id, limit, offset, beforeTime) }
        suspend fun commentDj(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentDj") { NcmModulesFull.commentDj(id, limit, offset, beforeTime) }
        suspend fun commentEvent(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentEvent") { NcmModulesFull.commentEvent(id, limit, offset, beforeTime) }
        suspend fun commentVideo(id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentVideo") { NcmModulesFull.commentVideo(id, limit, offset, beforeTime) }
        suspend fun commentLike(id: String, cid: String, type: NcmModulesFull.CmtType, t: Int = 1) = NcmApi.runSafely("commentLike") { NcmModulesFull.commentLike(id, cid, type, t) }
        suspend fun commentHot(type: NcmModulesFull.CmtType, id: String, limit: Int = 20, offset: Int = 0, beforeTime: Long = 0) = NcmApi.runSafely("commentHot") { NcmModulesFull.commentHot(type, id, limit, offset, beforeTime) }
        suspend fun commentFloor(id: String, type: NcmModulesFull.CmtType, parentCommentId: String, limit: Int = 20, time: Long = 0) = NcmApi.runSafely("commentFloor") { NcmModulesFull.commentFloor(id, type, parentCommentId, limit, time) }
        suspend fun commentNew(id: String, typeCode: Int) = NcmApi.runSafely("commentNew") { NcmModulesFull.commentNew(id, typeCode) }
        suspend fun commentSend(type: NcmModulesFull.CmtType, id: String, content: String, t: Int = 1, commentId: String? = null) = NcmApi.runSafely("commentSend") { NcmModulesFull.commentSend(type, id, content, t, commentId) }
        suspend fun commentHugList(id: String, cid: String, type: NcmModulesFull.CmtType) = NcmApi.runSafely("commentHugList") { NcmModulesFull.commentHugList(id, cid, type) }
        suspend fun hugComment(id: String, cid: String, type: NcmModulesFull.CmtType, uid: String) = NcmApi.runSafely("hugComment") { NcmModulesFull.hugComment(id, cid, type, uid) }
        suspend fun starPickCommentsSummary(id: String) = NcmApi.runSafely("starPickCommentsSummary") { NcmModulesFull.starPickCommentsSummary(id) }

        // 用户
        suspend fun userDetail(uid: String) = NcmApi.runSafely("userDetail") { NcmModulesFull.userDetail(uid) }
        suspend fun userDetailNew(uid: String) = NcmApi.runSafely("userDetailNew") { NcmModulesFull.userDetailNew(uid) }
        suspend fun userPlaylist(uid: String, limit: Int = 30, offset: Int = 0, includeVideo: Boolean = true) = NcmApi.runSafely("userPlaylist") { NcmModulesFull.userPlaylist(uid, limit, offset, includeVideo) }
        suspend fun userPlaylistCollect(id: String, seckey: String = "", t: Int = 1) = NcmApi.runSafely("userPlaylistCollect") { NcmModulesFull.userPlaylistCollect(id, seckey, t) }
        suspend fun userFollows(uid: String, limit: Int = 30, offset: Int = 0, order: Boolean = true) = NcmApi.runSafely("userFollows") { NcmModulesFull.userFollows(uid, limit, offset, order) }
        suspend fun userFolloweds(uid: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userFolloweds") { NcmModulesFull.userFolloweds(uid, limit, offset) }
        suspend fun userMutualfollowGet(uid: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userMutualfollowGet") { NcmModulesFull.userMutualfollowGet(uid, limit, offset) }
        suspend fun userRecord(uid: String, type: Int = 1) = NcmApi.runSafely("userRecord") { NcmModulesFull.userRecord(uid, type) }
        suspend fun userDj(uid: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userDj") { NcmModulesFull.userDj(uid, limit, offset) }
        suspend fun userEvent(uid: String, limit: Int = 30, lasttime: Long = -1) = NcmApi.runSafely("userEvent") { NcmModulesFull.userEvent(uid, limit, lasttime) }
        suspend fun userFollowMixed(uid: String, type: Int = 0, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userFollowMixed") { NcmModulesFull.userFollowMixed(uid, type, limit, offset) }
        suspend fun userLevel() = NcmApi.runSafely("userLevel") { NcmModulesFull.userLevel() }
        suspend fun userMedal() = NcmApi.runSafely("userMedal") { NcmModulesFull.userMedal() }
        suspend fun userSubcount() = NcmApi.runSafely("userSubcount") { NcmModulesFull.userSubcount() }
        suspend fun userBinding() = NcmApi.runSafely("userBinding") { NcmModulesFull.userBinding() }
        suspend fun userBindingcellphone(phone: String, captcha: String, oldcaptcha: String, ctcode: String = "86") = NcmApi.runSafely("userBindingcellphone") { NcmModulesFull.userBindingcellphone(phone, captcha, oldcaptcha, ctcode) }
        suspend fun userReplacephone(phone: String, captcha: String, oldcaptcha: String, ctcode: String = "86") = NcmApi.runSafely("userReplacephone") { NcmModulesFull.userReplacephone(phone, captcha, oldcaptcha, ctcode) }
        suspend fun userCommentHistory(uid: String, limit: Int = 10, cursor: Long = -1) = NcmApi.runSafely("userCommentHistory") { NcmModulesFull.userCommentHistory(uid, limit, cursor) }
        suspend fun userCloud(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userCloud") { NcmModulesFull.userCloud(limit, offset) }
        suspend fun userCloudDel(songIds: List<String>) = NcmApi.runSafely("userCloudDel") { NcmModulesFull.userCloudDel(songIds) }
        suspend fun userCloudDetail(songId: String, uid: String) = NcmApi.runSafely("userCloudDetail") { NcmModulesFull.userCloudDetail(songId, uid) }
        suspend fun userAudio(uid: String) = NcmApi.runSafely("userAudio") { NcmModulesFull.userAudio(uid) }
        suspend fun userSocialStatus(uid: String) = NcmApi.runSafely("userSocialStatus") { NcmModulesFull.userSocialStatus(uid) }
        suspend fun userSocialStatusEdit(content: String, cityCode: String? = null, provinceCode: String? = null, nickname: String? = null, avatarImgId: Long? = null) = NcmApi.runSafely("userSocialStatusEdit") { NcmModulesFull.userSocialStatusEdit(content, cityCode, provinceCode, nickname, avatarImgId) }
        suspend fun userSocialStatusSupport(id: String, t: Int = 1) = NcmApi.runSafely("userSocialStatusSupport") { NcmModulesFull.userSocialStatusSupport(id, t) }
        suspend fun userSocialStatusRcmd(uid: String, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("userSocialStatusRcmd") { NcmModulesFull.userSocialStatusRcmd(uid, limit, offset) }
        suspend fun userUpdate(nickname: String, avatarImgId: Long, gender: Int = 0, birthday: Long = 0L, signature: String = "", city: String = "", province: String = "", nicknameModifyFlag: Int = 0) = NcmApi.runSafely("userUpdate") { NcmModulesFull.userUpdate(nickname, avatarImgId, gender, birthday, signature, city, province, nicknameModifyFlag) }
        suspend fun follow(uid: String, t: Int = 1) = NcmApi.runSafely("follow") { NcmModulesFull.follow(uid, t) }
        suspend fun nicknameCheck(nickname: String) = NcmApi.runSafely("nicknameCheck") { NcmModulesFull.nicknameCheck(nickname) }

        // 电台 DJ
        suspend fun djCatelist() = NcmApi.runSafely("djCatelist") { NcmModulesFull.djCatelist() }
        suspend fun djCategoryExcludeHot() = NcmApi.runSafely("djCategoryExcludeHot") { NcmModulesFull.djCategoryExcludeHot() }
        suspend fun djCategoryRecommend() = NcmApi.runSafely("djCategoryRecommend") { NcmModulesFull.djCategoryRecommend() }
        suspend fun djBanner() = NcmApi.runSafely("djBanner") { NcmModulesFull.djBanner() }
        suspend fun djDetail(id: String) = NcmApi.runSafely("djDetail") { NcmModulesFull.djDetail(id) }
        suspend fun djHot(cateId: String = "0", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("djHot") { NcmModulesFull.djHot(cateId, limit, offset) }
        suspend fun djPersonalizeRecommend(cateId: String? = null, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("djPersonalizeRecommend") { NcmModulesFull.djPersonalizeRecommend(cateId, limit, offset) }
        suspend fun djPaygift(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("djPaygift") { NcmModulesFull.djPaygift(limit, offset) }
        suspend fun djProgram(rid: String, limit: Int = 30, offset: Int = 0, asc: Boolean = false) = NcmApi.runSafely("djProgram") { NcmModulesFull.djProgram(rid, limit, offset, asc) }
        suspend fun djProgramDetail(id: String) = NcmApi.runSafely("djProgramDetail") { NcmModulesFull.djProgramDetail(id) }
        suspend fun djProgramToplist(limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("djProgramToplist") { NcmModulesFull.djProgramToplist(limit, offset) }
        suspend fun djProgramToplistHours(limit: Int = 100) = NcmApi.runSafely("djProgramToplistHours") { NcmModulesFull.djProgramToplistHours(limit) }
        suspend fun djRadiotop(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("djRadiotop") { NcmModulesFull.djRadiotop(limit, offset) }
        suspend fun djToplist(limit: Int = 100, type: Int = 1, areaId: Int = 0) = NcmApi.runSafely("djToplist") { NcmModulesFull.djToplist(limit, type, areaId) }
        suspend fun djToplistHours(limit: Int = 100) = NcmApi.runSafely("djToplistHours") { NcmModulesFull.djToplistHours(limit) }
        suspend fun djToplistNewcomer(limit: Int = 100) = NcmApi.runSafely("djToplistNewcomer") { NcmModulesFull.djToplistNewcomer(limit) }
        suspend fun djToplistPopular(limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("djToplistPopular") { NcmModulesFull.djToplistPopular(limit, offset) }
        suspend fun djToplistPay(limit: Int = 100) = NcmApi.runSafely("djToplistPay") { NcmModulesFull.djToplistPay(limit) }
        suspend fun djRecommend() = NcmApi.runSafely("djRecommend") { NcmModulesFull.djRecommend() }
        suspend fun djRecommendType(type: String = "1", limit: Int = 10) = NcmApi.runSafely("djRecommendType") { NcmModulesFull.djRecommendType(type, limit) }
        suspend fun djRadioHot(cateId: String = "0", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("djRadioHot") { NcmModulesFull.djRadioHot(cateId, limit, offset) }
        suspend fun djSub(rid: String, t: Int = 1) = NcmApi.runSafely("djSub") { NcmModulesFull.djSub(rid, t) }
        suspend fun djSublist(limit: Int = 30, offset: Int = 0, needLastProgram: Boolean = true) = NcmApi.runSafely("djSublist") { NcmModulesFull.djSublist(limit, offset, needLastProgram) }
        suspend fun djSubscriber(id: String, limit: Int = 30, offset: Int = 0, time: Long = 0) = NcmApi.runSafely("djSubscriber") { NcmModulesFull.djSubscriber(id, limit, offset, time) }
        suspend fun djTodayPerfered(limit: Int = 10, categoryId: String = "0") = NcmApi.runSafely("djTodayPerfered") { NcmModulesFull.djTodayPerfered(limit, categoryId) }
        suspend fun programRecommend(id: String) = NcmApi.runSafely("programRecommend") { NcmModulesFull.programRecommend(id) }
        suspend fun broadcastSub() = NcmApi.runSafely("broadcastSub") { NcmModulesFull.broadcastSub() }
        suspend fun broadcastCategoryRegionGet() = NcmApi.runSafely("broadcastCategoryRegionGet") { NcmModulesFull.broadcastCategoryRegionGet() }
        suspend fun broadcastChannelCurrentinfo(id: String) = NcmApi.runSafely("broadcastChannelCurrentinfo") { NcmModulesFull.broadcastChannelCurrentinfo(id) }
        suspend fun broadcastChannelList(areaId: Int = 0, limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("broadcastChannelList") { NcmModulesFull.broadcastChannelList(areaId, limit, offset) }
        suspend fun broadcastChannelCollectList(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("broadcastChannelCollectList") { NcmModulesFull.broadcastChannelCollectList(limit, offset) }

        // MV / Video / Mlog
        suspend fun mvAll(area: String = "全部", type: String = "全部", order: String = "上升最快", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("mvAll") { NcmModulesFull.mvAll(area, type, order, limit, offset) }
        suspend fun mvFirst(limit: Int = 30, offset: Int = 0, order: Boolean = true) = NcmApi.runSafely("mvFirst") { NcmModulesFull.mvFirst(limit, offset, order) }
        suspend fun mvExclusiveRcmd() = NcmApi.runSafely("mvExclusiveRcmd") { NcmModulesFull.mvExclusiveRcmd() }
        suspend fun mvDetail(mvid: String) = NcmApi.runSafely("mvDetail") { NcmModulesFull.mvDetail(mvid) }
        suspend fun mvDetailInfo(mvid: String) = NcmApi.runSafely("mvDetailInfo") { NcmModulesFull.mvDetailInfo(mvid) }
        suspend fun mvSub(t: Int = 1, mvId: String = "") = NcmApi.runSafely("mvSub") { NcmModulesFull.mvSub(t, mvId) }
        suspend fun mvSublist(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("mvSublist") { NcmModulesFull.mvSublist(limit, offset) }
        suspend fun mvUrl(id: String, r: Int = 1080) = NcmApi.runSafely("mvUrl") { NcmModulesFull.mvUrl(id, r) }
        suspend fun videoGroup() = NcmApi.runSafely("videoGroup") { NcmModulesFull.videoGroup() }
        suspend fun videoGroupList(id: String, offset: Int = 0, needPreviewUrl: Boolean = true) = NcmApi.runSafely("videoGroupList") { NcmModulesFull.videoGroupList(id, offset, needPreviewUrl) }
        suspend fun videoDetail(id: String) = NcmApi.runSafely("videoDetail") { NcmModulesFull.videoDetail(id) }
        suspend fun videoDetailInfo(vid: String) = NcmApi.runSafely("videoDetailInfo") { NcmModulesFull.videoDetailInfo(vid) }
        suspend fun videoUrl(id: String, r: Int = 1080) = NcmApi.runSafely("videoUrl") { NcmModulesFull.videoUrl(id, r) }
        suspend fun videoTimelineAll(type: Int = 0, offset: Long = 0, size: Int = 10) = NcmApi.runSafely("videoTimelineAll") { NcmModulesFull.videoTimelineAll(type, offset, size) }
        suspend fun videoTimelineRecommend(offset: Long = 0, size: Int = 10) = NcmApi.runSafely("videoTimelineRecommend") { NcmModulesFull.videoTimelineRecommend(offset, size) }
        suspend fun videoCategoryList() = NcmApi.runSafely("videoCategoryList") { NcmModulesFull.videoCategoryList() }
        suspend fun videoSub(vid: String, t: Int = 1) = NcmApi.runSafely("videoSub") { NcmModulesFull.videoSub(vid, t) }
        suspend fun mlogUrl(id: String, r: Int = 1080) = NcmApi.runSafely("mlogUrl") { NcmModulesFull.mlogUrl(id, r) }
        suspend fun mlogToVideo(id: String, type: Int = 1) = NcmApi.runSafely("mlogToVideo") { NcmModulesFull.mlogToVideo(id, type) }
        suspend fun mlogMusicRcmd(songId: String, limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("mlogMusicRcmd") { NcmModulesFull.mlogMusicRcmd(songId, limit, offset) }
        suspend fun verifyGetQr(type: Int, phone: String, ctcode: String = "86") = NcmApi.runSafely("verifyGetQr") { NcmModulesFull.verifyGetQr(type, phone, ctcode) }
        suspend fun verifyQrcodestatus(verifyId: String, verifysign: String, type: Int, phone: String, ctcode: String = "86") = NcmApi.runSafely("verifyQrcodestatus") { NcmModulesFull.verifyQrcodestatus(verifyId, verifysign, type, phone, ctcode) }

        // 个性化推荐 / FM
        suspend fun personalized(limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("personalized") { NcmModulesFull.personalized(limit, offset) }
        suspend fun personalizedNewsong(area: Int = 0, type: Int = 0, limit: Int = 10) = NcmApi.runSafely("personalizedNewsong") { NcmModulesFull.personalizedNewsong(area, type, limit) }
        suspend fun personalizedMv() = NcmApi.runSafely("personalizedMv") { NcmModulesFull.personalizedMv() }
        suspend fun personalizedDjprogram(limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("personalizedDjprogram") { NcmModulesFull.personalizedDjprogram(limit, offset) }
        suspend fun personalizedPrivatecontent(limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("personalizedPrivatecontent") { NcmModulesFull.personalizedPrivatecontent(limit, offset) }
        suspend fun personalizedPrivatecontentList(limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("personalizedPrivatecontentList") { NcmModulesFull.personalizedPrivatecontentList(limit, offset) }
        suspend fun personalFm(mode: Int = 0, id: String? = null, pid: String? = null) = NcmApi.runSafely("personalFm") { NcmModulesFull.personalFm(mode, id, pid) }
        suspend fun personalFmMode(mode: Int, id: String? = null, pid: String? = null) = personalFm(mode, id, pid)
        suspend fun fmTrash(id: String, time: Long = System.currentTimeMillis()) = NcmApi.runSafely("fmTrash") { NcmModulesFull.fmTrash(id, time) }

        // 每日推荐 / 历史 / 听歌数据
        suspend fun historyRecommendSongs(limit: Int = 100) = NcmApi.runSafely("historyRecommendSongs") { NcmModulesFull.historyRecommendSongs(limit) }
        suspend fun historyRecommendSongsDetail(date: String) = NcmApi.runSafely("historyRecommendSongsDetail") { NcmModulesFull.historyRecommendSongsDetail(date) }
        suspend fun listenDataReport(type: Int, data: String) = NcmApi.runSafely("listenDataReport") { NcmModulesFull.listenDataReport(type, data) }
        suspend fun listenDataRealtimeReport(actionsJson: String) = NcmApi.runSafely("listenDataRealtimeReport") { NcmModulesFull.listenDataRealtimeReport(actionsJson) }
        suspend fun listenDataTodaySong(start: Long = 0, end: Long = 0) = NcmApi.runSafely("listenDataTodaySong") { NcmModulesFull.listenDataTodaySong(start, end) }
        suspend fun listenDataTotal() = NcmApi.runSafely("listenDataTotal") { NcmModulesFull.listenDataTotal() }
        suspend fun listenDataYearReport(year: Int = 0) = NcmApi.runSafely("listenDataYearReport") { NcmModulesFull.listenDataYearReport(year) }

        // 云盘
        suspend fun cloudImport() = NcmApi.runSafely("cloudImport") { NcmModulesFull.cloudImport() }
        suspend fun cloudMatch(songId: String, userSongId: String, artist: String, song: String, album: String) = NcmApi.runSafely("cloudMatch") { NcmModulesFull.cloudMatch(songId, userSongId, artist, song, album) }
        suspend fun cloudLyricGet(id: String, cp: Boolean = false) = NcmApi.runSafely("cloudLyricGet") { NcmModulesFull.cloudLyricGet(id, cp) }
        suspend fun cloudUploadToken(ext: String = "mp3", size: Long = 0L, bitrate: String = "320000", md5: String? = null) = NcmApi.runSafely("cloudUploadToken") { NcmModulesFull.cloudUploadToken(ext, size, bitrate, md5) }
        suspend fun cloudUploadComplete(songId: String, md5: String, objKey: String, ext: String = "mp3") = NcmApi.runSafely("cloudUploadComplete") { NcmModulesFull.cloudUploadComplete(songId, md5, objKey, ext) }

        // 签到 / VIP / 云贝 / 听书年度报告
        suspend fun dailySignin(type: Int = 0) = NcmApi.runSafely("dailySignin") { NcmModulesFull.dailySignin(type) }
        suspend fun signHappyInfo() = NcmApi.runSafely("signHappyInfo") { NcmModulesFull.signHappyInfo() }
        suspend fun signinProgress() = NcmApi.runSafely("signinProgress") { NcmModulesFull.signinProgress() }
        suspend fun vipInfo() = NcmApi.runSafely("vipInfo") { NcmModulesFull.vipInfo() }
        suspend fun vipInfoV2() = NcmApi.runSafely("vipInfoV2") { NcmModulesFull.vipInfoV2() }
        suspend fun vipTasks() = NcmApi.runSafely("vipTasks") { NcmModulesFull.vipTasks() }
        suspend fun vipSign(time: Long = System.currentTimeMillis()) = NcmApi.runSafely("vipSign") { NcmModulesFull.vipSign(time) }
        suspend fun vipSignInfo() = NcmApi.runSafely("vipSignInfo") { NcmModulesFull.vipSignInfo() }
        suspend fun vipGrowthpoint() = NcmApi.runSafely("vipGrowthpoint") { NcmModulesFull.vipGrowthpoint() }
        suspend fun vipGrowthpointGet(type: Int = 0, taskId: String = "") = NcmApi.runSafely("vipGrowthpointGet") { NcmModulesFull.vipGrowthpointGet(type, taskId) }
        suspend fun vipGrowthpointDetails(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("vipGrowthpointDetails") { NcmModulesFull.vipGrowthpointDetails(limit, offset) }
        suspend fun vipTimemachine(mode: String = "1", platform: String = "iPhone15,3") = NcmApi.runSafely("vipTimemachine") { NcmModulesFull.vipTimemachine(mode, platform) }
        suspend fun yunbeiInfo() = NcmApi.runSafely("yunbeiInfo") { NcmModulesFull.yunbeiInfo() }
        suspend fun yunbeiSign() = NcmApi.runSafely("yunbeiSign") { NcmModulesFull.yunbeiSign() }
        suspend fun yunbeiTasks() = NcmApi.runSafely("yunbeiTasks") { NcmModulesFull.yunbeiTasks() }
        suspend fun yunbeiTasksTodo() = NcmApi.runSafely("yunbeiTasksTodo") { NcmModulesFull.yunbeiTasksTodo() }
        suspend fun yunbeiTaskFinish(userTaskId: String, depositAmount: Int = 0) = NcmApi.runSafely("yunbeiTaskFinish") { NcmModulesFull.yunbeiTaskFinish(userTaskId, depositAmount) }
        suspend fun yunbeiToday() = NcmApi.runSafely("yunbeiToday") { NcmModulesFull.yunbeiToday() }
        suspend fun yunbeiReceipt(id: String) = NcmApi.runSafely("yunbeiReceipt") { NcmModulesFull.yunbeiReceipt(id) }
        suspend fun yunbeiExpense(limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("yunbeiExpense") { NcmModulesFull.yunbeiExpense(limit, offset) }
        suspend fun yunbeiRcmdSong() = NcmApi.runSafely("yunbeiRcmdSong") { NcmModulesFull.yunbeiRcmdSong() }
        suspend fun yunbeiRcmdSongHistory(limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("yunbeiRcmdSongHistory") { NcmModulesFull.yunbeiRcmdSongHistory(limit, offset) }
        suspend fun summaryAnnual() = NcmApi.runSafely("summaryAnnual") { NcmModulesFull.listenDataYearReport(0) }

        // 一起听
        suspend fun listentogetherRoomCreate(playlistId: String, playlistType: Int = 1) = NcmApi.runSafely("listentogetherRoomCreate") { NcmModulesFull.listentogetherRoomCreate(playlistId, playlistType) }
        suspend fun listentogetherRoomCheck(roomId: String, inviterId: String, nickname: String = "", avatar: String = "", token: String = "") = NcmApi.runSafely("listentogetherRoomCheck") { NcmModulesFull.listentogetherRoomCheck(roomId, inviterId, nickname, avatar, token) }
        suspend fun listentogetherSyncPlaylistGet(roomId: String, lastPlayIndex: Int = 0, lastPlayId: String = "0", lastPlayTime: Long = 0L, wait: Boolean = false) = NcmApi.runSafely("listentogetherSyncPlaylistGet") { NcmModulesFull.listentogetherSyncPlaylistGet(roomId, lastPlayIndex, lastPlayId, lastPlayTime, wait) }
        suspend fun listentogetherSyncListCommand(roomId: String, type: Int, playlistId: String? = null, playIndex: Int? = null, playId: String? = null, playTime: Long? = null, commandId: String = "", checkStatus: Boolean = false) = NcmApi.runSafely("listentogetherSyncListCommand") { NcmModulesFull.listentogetherSyncListCommand(roomId, type, playlistId, playIndex, playId, playTime, commandId, checkStatus) }
        suspend fun listentogetherAccept(roomId: String, inviterId: String, token: String = "") = NcmApi.runSafely("listentogetherAccept") { NcmModulesFull.listentogetherAccept(roomId, inviterId, token) }
        suspend fun listentogetherEnd(roomId: String, nickname: String = "", avatar: String = "") = NcmApi.runSafely("listentogetherEnd") { NcmModulesFull.listentogetherEnd(roomId, nickname, avatar) }
        suspend fun listentogetherHeatbeat(roomId: String, playStatus: Int, playId: String = "0", playTime: Long = 0L, playIndex: Int = 0) = NcmApi.runSafely("listentogetherHeatbeat") { NcmModulesFull.listentogetherHeatbeat(roomId, playStatus, playId, playTime, playIndex) }
        suspend fun listentogetherPlayCommand(roomId: String, commandType: Int, playId: String = "0", playTime: Long = 0L, playIndex: Int = 0) = NcmApi.runSafely("listentogetherPlayCommand") { NcmModulesFull.listentogetherPlayCommand(roomId, commandType, playId, playTime, playIndex) }
        suspend fun listentogetherStatus(roomId: String) = NcmApi.runSafely("listentogetherStatus") { NcmModulesFull.listentogetherStatus(roomId) }

        // 风格
        suspend fun styleList() = NcmApi.runSafely("styleList") { NcmModulesFull.styleList() }
        suspend fun styleDetail(tagId: Long, size: Int = 20, cursor: Long = 0) = NcmApi.runSafely("styleDetail") { NcmModulesFull.styleDetail(tagId, size, cursor) }
        suspend fun stylePreference() = NcmApi.runSafely("stylePreference") { NcmModulesFull.stylePreference() }
        suspend fun styleSong(tagId: Long, sort: Int = 0, size: Int = 20, cursor: Long = 0) = NcmApi.runSafely("styleSong") { NcmModulesFull.styleSong(tagId, sort, size, cursor) }
        suspend fun stylePlaylist(tagId: Long, sort: Int = 0, size: Int = 20, cursor: Long = 0) = NcmApi.runSafely("stylePlaylist") { NcmModulesFull.stylePlaylist(tagId, sort, size, cursor) }
        suspend fun styleArtist(tagId: Long, size: Int = 20, cursor: Long = 0) = NcmApi.runSafely("styleArtist") { NcmModulesFull.styleArtist(tagId, size, cursor) }
        suspend fun styleAlbum(tagId: Long, size: Int = 20, cursor: Long = 0) = NcmApi.runSafely("styleAlbum") { NcmModulesFull.styleAlbum(tagId, size, cursor) }

        // 私信 / 消息
        suspend fun msgRecentcontact(limit: Int = 100, offset: Int = 0) = NcmApi.runSafely("msgRecentcontact") { NcmModulesFull.msgRecentcontact(limit, offset) }
        suspend fun msgPrivate(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("msgPrivate") { NcmModulesFull.msgPrivate(limit, offset) }
        suspend fun msgPrivateHistory(uid: String, beforeTime: Long = 0, limit: Int = 30, total: Boolean = true) = NcmApi.runSafely("msgPrivateHistory") { NcmModulesFull.msgPrivateHistory(uid, beforeTime, limit, total) }
        suspend fun msgComments(limit: Int = 30, beforeTime: Long = 0) = NcmApi.runSafely("msgComments") { NcmModulesFull.msgComments(limit, beforeTime) }
        suspend fun msgForwards(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("msgForwards") { NcmModulesFull.msgForwards(limit, offset) }
        suspend fun msgNotices(limit: Int = 30, lastTime: Long = 0, isPrev: Boolean = true) = NcmApi.runSafely("msgNotices") { NcmModulesFull.msgNotices(limit, lastTime, isPrev) }
        suspend fun sendText(userIds: String, msg: String, type: String = "text") = NcmApi.runSafely("sendText") { NcmModulesFull.sendText(userIds, msg, type) }
        suspend fun sendSong(userId: String, songId: String, id: String, msg: String = "") = NcmApi.runSafely("sendSong") { NcmModulesFull.sendSong(userId, songId, id, msg) }
        suspend fun sendPlaylist(userId: String, id: String, msg: String = "") = NcmApi.runSafely("sendPlaylist") { NcmModulesFull.sendPlaylist(userId, id, msg) }
        suspend fun sendAlbum(userId: String, id: String, msg: String = "") = NcmApi.runSafely("sendAlbum") { NcmModulesFull.sendAlbum(userId, id, msg) }
        suspend fun shareResource(type: String, msg: String, id: String) = NcmApi.runSafely("shareResource") { NcmModulesFull.shareResource(type, msg, id) }
        suspend fun topicSublist(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("topicSublist") { NcmModulesFull.topicSublist(limit, offset) }
        suspend fun topicDetail(actId: String) = NcmApi.runSafely("topicDetail") { NcmModulesFull.topicDetail(actId) }
        suspend fun topicDetailEventHot(actId: String, limit: Int = 5, threadType: String = "ACTIVITY") = NcmApi.runSafely("topicDetailEventHot") { NcmModulesFull.topicDetailEventHot(actId, limit, threadType) }

        // 创作者 / UGC
        suspend fun creatorAuthinfoGet() = NcmApi.runSafely("creatorAuthinfoGet") { NcmModulesFull.creatorAuthinfoGet() }
        suspend fun thresholdDetailGet() = NcmApi.runSafely("thresholdDetailGet") { NcmModulesFull.thresholdDetailGet() }
        suspend fun ugcDetail(id: String, type: Int = 1, needTrans: Boolean = true, resolution: Int = 1080) = NcmApi.runSafely("ugcDetail") { NcmModulesFull.ugcDetail(id, type, needTrans, resolution) }
        suspend fun ugcArtistGet(artistId: String, limit: Int = 20, offset: Int = 0, order: String = "new") = NcmApi.runSafely("ugcArtistGet") { NcmModulesFull.ugcArtistGet(artistId, limit, offset, order) }
        suspend fun ugcArtistSearch(keywords: String, limit: Int = 10, offset: Int = 0) = NcmApi.runSafely("ugcArtistSearch") { NcmModulesFull.ugcArtistSearch(keywords, limit, offset) }
        suspend fun ugcSongGet(songId: String, limit: Int = 20, offset: Int = 0, order: String = "hot") = NcmApi.runSafely("ugcSongGet") { NcmModulesFull.ugcSongGet(songId, limit, offset, order) }
        suspend fun ugcAlbumGet(albumId: String, limit: Int = 20, offset: Int = 0, order: String = "new") = NcmApi.runSafely("ugcAlbumGet") { NcmModulesFull.ugcAlbumGet(albumId, limit, offset, order) }
        suspend fun ugcMvGet(mvid: String, limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("ugcMvGet") { NcmModulesFull.ugcMvGet(mvid, limit, offset) }
        suspend fun ugcUserDevote(uid: String, sort: Int = 1, limit: Int = 30, offset: Int = 0, ugcType: Int = 1) = NcmApi.runSafely("ugcUserDevote") { NcmModulesFull.ugcUserDevote(uid, sort, limit, offset, ugcType) }

        // 动态 / 粉丝中心 / 音乐人
        suspend fun event(limit: Int = 20, lasttime: Long = -1, pagesize: Int = 20) = NcmApi.runSafely("event") { NcmModulesFull.event(limit, lasttime, pagesize) }
        suspend fun eventDel(evId: String) = NcmApi.runSafely("eventDel") { NcmModulesFull.eventDel(evId) }
        suspend fun eventForward(evId: String, forwards: String = "转发") = NcmApi.runSafely("eventForward") { NcmModulesFull.eventForward(evId, forwards) }
        suspend fun fanscenterOverviewGet() = NcmApi.runSafely("fanscenterOverviewGet") { NcmModulesFull.fanscenterOverviewGet() }
        suspend fun fanscenterBasicinfoAgeGet(startDate: String = "", endDate: String = "") = NcmApi.runSafely("fanscenterBasicinfoAgeGet") { NcmModulesFull.fanscenterBasicinfoAgeGet(startDate, endDate) }
        suspend fun fanscenterBasicinfoGenderGet(startDate: String = "", endDate: String = "") = NcmApi.runSafely("fanscenterBasicinfoGenderGet") { NcmModulesFull.fanscenterBasicinfoGenderGet(startDate, endDate) }
        suspend fun fanscenterBasicinfoProvinceGet(startDate: String = "", endDate: String = "") = NcmApi.runSafely("fanscenterBasicinfoProvinceGet") { NcmModulesFull.fanscenterBasicinfoProvinceGet(startDate, endDate) }
        suspend fun fanscenterTrendList(startDate: String = "", endDate: String = "", limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("fanscenterTrendList") { NcmModulesFull.fanscenterTrendList(startDate, endDate, limit, offset) }
        suspend fun musicianDataOverview(startDate: String = "", endDate: String = "") = NcmApi.runSafely("musicianDataOverview") { NcmModulesFull.musicianDataOverview(startDate, endDate) }
        suspend fun musicianPlayTrend(startDate: String = "", endDate: String = "") = NcmApi.runSafely("musicianPlayTrend") { NcmModulesFull.musicianPlayTrend(startDate, endDate) }
        suspend fun musicianCloudbean() = NcmApi.runSafely("musicianCloudbean") { NcmModulesFull.musicianCloudbean() }
        suspend fun musicianCloudbeanObtain(cloudBean: Int, id: String) = NcmApi.runSafely("musicianCloudbeanObtain") { NcmModulesFull.musicianCloudbeanObtain(cloudBean, id) }
        suspend fun musicianTasks(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("musicianTasks") { NcmModulesFull.musicianTasks(limit, offset) }
        suspend fun musicianTasksNew(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("musicianTasksNew") { NcmModulesFull.musicianTasksNew(limit, offset) }
        suspend fun musicianVipTasks(limit: Int = 20, offset: Int = 0) = NcmApi.runSafely("musicianVipTasks") { NcmModulesFull.musicianVipTasks(limit, offset) }
        suspend fun musicianSign(year: Int, month: Int, day: Int) = NcmApi.runSafely("musicianSign") { NcmModulesFull.musicianSign(year, month, day) }

        // 上传 token / 语音
        suspend fun avatarUploadToken(imgSize: Long = 10_000_000L, imgMd5: String? = null, imgExt: String = "jpg") = NcmApi.runSafely("avatarUploadToken") { NcmModulesFull.avatarUploadToken(imgSize, imgMd5, imgExt) }
        suspend fun audioMatch(ext: String = "mp3", md5: String = "", bitrate: String = "320", duration: Long = 0L, sampleRate: Int = 44100, channels: Int = 2, songId: String? = null) = NcmApi.runSafely("audioMatch") { NcmModulesFull.audioMatch(ext, md5, bitrate, duration, sampleRate, channels, songId) }
        suspend fun voiceUpload(voiceMd5: String = "", voiceSize: Long = 0L, voiceType: Int = 0, bitRate: Int = 320, duration: Long = 0L, sampleRate: Int = 44100, channels: Int = 1, volume: Float = 1.0f) = NcmApi.runSafely("voiceUpload") { NcmModulesFull.voiceUpload(voiceMd5, voiceSize, voiceType, bitRate, duration, sampleRate, channels, volume) }
        suspend fun voiceDelete(id: String) = NcmApi.runSafely("voiceDelete") { NcmModulesFull.voiceDelete(id) }
        suspend fun voiceDetail(id: String) = NcmApi.runSafely("voiceDetail") { NcmModulesFull.voiceDetail(id) }
        suspend fun voiceLyric(id: String) = NcmApi.runSafely("voiceLyric") { NcmModulesFull.voiceLyric(id) }
        suspend fun voiceListSearch(limit: Int = 20, offset: Int = 0, order: String = "hot") = NcmApi.runSafely("voiceListSearch") { NcmModulesFull.voiceListSearch(limit, offset, order) }
        suspend fun voiceList(limit: Int = 20, offset: Int = 0, order: String = "hot") = NcmApi.runSafely("voiceList") { NcmModulesFull.voiceList(limit, offset, order) }
        suspend fun voiceListDetail(id: String) = NcmApi.runSafely("voiceListDetail") { NcmModulesFull.voiceListDetail(id) }
        suspend fun voiceListTrans(ids: List<String>) = NcmApi.runSafely("voiceListTrans") { NcmModulesFull.voiceListTrans(ids) }

        // Misc
        suspend fun countriesCodeList() = NcmApi.runSafely("countriesCodeList") { NcmModulesFull.countriesCodeList() }
        suspend fun cellphoneExistenceCheck(phone: String, ctcode: String = "86") = NcmApi.runSafely("cellphoneExistenceCheck") { NcmModulesFull.cellphoneExistenceCheck(phone, ctcode) }
        suspend fun calendar(startDate: String? = null, endDate: String? = null, cellPhone: String? = null) = NcmApi.runSafely("calendar") { NcmModulesFull.calendar(startDate, endDate, cellPhone) }
        suspend fun batch(batchApi: Map<String, Map<String, Any?>>) = NcmApi.runSafely("batch") { NcmModulesFull.batch(batchApi) }
        suspend fun activateInitProfile(nickname: String, birthday: Long = System.currentTimeMillis(), gender: Int = 0, avatarImgId: Long = 0L, areaCode: String = "86") = NcmApi.runSafely("activateInitProfile") { NcmModulesFull.activateInitProfile(nickname, birthday, gender, avatarImgId, areaCode) }
        suspend fun homepageBlockPage(pageId: String, refresh: Boolean = false, cursor: String = "0") = NcmApi.runSafely("homepageBlockPage") { NcmModulesFull.homepageBlockPage(pageId, refresh, cursor) }
        suspend fun homepageDragonBall() = NcmApi.runSafely("homepageDragonBall") { NcmModulesFull.homepageDragonBall() }
        suspend fun hotTopic(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("hotTopic") { NcmModulesFull.rawWeapi("/api/hot/topic", mapOf("limit" to limit, "offset" to offset)) }
        suspend fun get_userids(ids: List<String>) = NcmApi.runSafely("getUserIds") { NcmModulesFull.rawWeapi("/api/user/getUserIds", mapOf("userIds" to ids.joinToString(","))) }
        suspend fun activateInitProfile() = NcmApi.runSafely("activateInitProfile") { NcmModulesFull.rawWeapi("/api/activate/init/profile", emptyMap()) }
        suspend fun sheetPreview(id: String) = NcmApi.runSafely("sheetPreview") { NcmModulesFull.rawWeapi("/api/workbench/preview", mapOf("id" to id)) }
        suspend fun sheetList(limit: Int = 30, offset: Int = 0) = NcmApi.runSafely("sheetList") { NcmModulesFull.rawWeapi("/api/sheet/list", mapOf("limit" to limit, "offset" to offset)) }
        suspend fun aidjContentRcmd() = NcmApi.runSafely("aidjContentRcmd") { NcmModulesFull.rawWeapi("/api/aidj/content/rcmd", emptyMap()) }

        // 登录扩展
        suspend fun loginEmail(email: String, passwordMd5: String) = NcmApi.runSafely("loginEmail") { NcmModulesFull.loginEmail(email, passwordMd5) }
        suspend fun loginStatus() = NcmApi.runSafely("loginStatus") { NcmModulesFull.loginStatus() }
        suspend fun innerVersion() = NcmApi.runSafely("innerVersion") { NcmModulesFull.innerVersion() }
        suspend fun setting() = NcmApi.runSafely("setting") { NcmModulesFull.setting() }
        suspend fun weblog(logs: String) = NcmApi.runSafely("weblog") { NcmModulesFull.weblog(logs) }
        suspend fun eapiDecryptRequest(hex: String) = NcmModulesFull.EapiDecryptRequest(hex)

        // ========================================================================
        // ★ 兜底通用入口（任何 module/*.js 都能秒接，不用写新方法）
        // ========================================================================
        suspend fun rawWeapi(path: String, params: Map<String, Any?>) = NcmApi.runSafely("rawWeapi") { NcmModulesFull.rawWeapi(path, params) }
        suspend fun rawEapi(path: String, params: Map<String, Any?>) = NcmApi.runSafely("rawEapi") { NcmModulesFull.rawEapi(path, params) }
        suspend fun rawLinuxapi(path: String, params: Map<String, Any?>) = NcmApi.runSafely("rawLinuxapi") { NcmModulesFull.rawLinuxapi(path, params) }
    }

    // 让 runSafely 变成 public（给 FullAccess 委托用）
    internal suspend fun <T> publicRunSafely(
        tag: String,
        block: suspend () -> Result<T>,
    ): Result<T> = runSafely(tag, block)
}
