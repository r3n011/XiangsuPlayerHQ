package com.theveloper.pixelplay.data.kugou

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 酷狗账号仓库：登录（扫码 / 手机号验证码）+ 凭证持久化 + 请求 Cookie + 歌单同步入口。
 *
 * 所有对酷狗服务端的调用都走 [KugouAccountApi]（带 AES/RSA/签名，移植自 md3Music 的
 * `login.rs` / `crypto.rs` / `request.rs`）：二维码两个接口用 **web 签名**，
 * 手机号登录/验证码/用户信息/歌单用 **android 签名**，设备 mid 为 `md5(guid)` 的十进制串。
 * 拿到 `token/userid/vip_token` 后存本地，之后所有酷狗请求带上 `Cookie` 即可。
 *
 * 凭证用 [EncryptedSharedPreferences]（失败自动回退明文，与 BilibiliRepository 同款处理）。
 */
@Singleton
class KugouRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountApi: KugouAccountApi,
) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kugou_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse { error ->
        Timber.w(error, "KugouRepository: EncryptedSharedPreferences 不可用，回退明文存储")
        context.getSharedPreferences("kugou_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    /** 登录后的账号信息：昵称（账号页显示）/ 头像（卡片显示）。 */
    private val _accountLabel = MutableStateFlow<String?>(null)
    val accountLabel: StateFlow<String?> = _accountLabel.asStateFlow()

    private val _accountNickname = MutableStateFlow<String?>(null)
    val accountNickname: StateFlow<String?> = _accountNickname.asStateFlow()

    private val _accountAvatarUrl = MutableStateFlow<String?>(null)
    val accountAvatarUrl: StateFlow<String?> = _accountAvatarUrl.asStateFlow()

    val isLoggedIn: Boolean get() = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    /** 设备标识：注册一次后长期复用（参考项目 `device_info.json`）。 */
    private val deviceIdentity: KugouDeviceIdentity
        get() {
            val dfid = prefs.getString(KEY_DFID, null)?.takeIf { it.isNotBlank() }
                ?: randomDfid().also { prefs.edit().putString(KEY_DFID, it).apply() }
            val guid = prefs.getString(KEY_GUID, null)?.takeIf { it.isNotBlank() }
                ?: randomGuid().also { prefs.edit().putString(KEY_GUID, it).apply() }
            val dev = prefs.getString(KEY_DEV, null)?.takeIf { it.isNotBlank() }
                ?: randomDev().also { prefs.edit().putString(KEY_DEV, it).apply() }
            // ⚡ mid = md5(guid) 的十进制串（不是 hex）：所有签名请求的 mid 参数/头都用它，
            //    用错值酷狗会当作非法设备直接拒绝。
            return KugouDeviceIdentity(
                dfid = dfid,
                guid = guid,
                dev = dev,
                mid = KugouAccountApi.md5Decimal(guid),
            )
        }

    /** 当前 token / userid（签名接口用）。 */
    private val tokenOrNull: String? get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    private val userIdOrNull: String? get() = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    /** 当前登录的酷狗 userid（未登录为 null）；账号面板展示用。 */
    val userId: String? get() = userIdOrNull

    /** 设备标识（听书等其它需要签名的酷狗模块复用；听书不要求登录）。 */
    val device: KugouDeviceIdentity get() = deviceIdentity

    /** 登录 token（未登录为 null）；听书等签名请求按登录态带上。 */
    val authToken: String? get() = tokenOrNull

    init {
        // 进程启动就恢复登录态，账号页/内置音源立刻能读到。
        // ⚡ 必须放在所有 StateFlow 声明之后：init 块按声明顺序执行，提前调用会写入尚未初始化的字段。
        restoreSession()
    }

    private fun randomDfid(): String =
        (1..24).map { "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".random() }.joinToString("")

    private fun randomDev(): String =
        (1..10).map { "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".random() }.joinToString("")

    /** 与参考项目一致的 guid：`xxxxxxxx-xxxx-...` 形式。 */
    private fun randomGuid(): String {
        fun chunk(): String = ((65536 * (1 + Math.random())).toInt()).toString(16).substring(1)
        return "${chunk()}${chunk()}-${chunk()}-${chunk()}-${chunk()}-${chunk()}${chunk()}${chunk()}"
    }

    /**
     * 拉取昵称/头像（`/v3/get_my_info`，需要 AES+RSA 签名）。
     * 登录成功、进程恢复、以及账号页手动刷新时都会调用。
     */
    suspend fun refreshUserInfo(): Result<KugouUserInfo> {
        val token = tokenOrNull ?: return Result.failure(IllegalStateException("未登录"))
        val userId = userIdOrNull ?: return Result.failure(IllegalStateException("缺少 userid"))
        return accountApi.fetchUserInfo(token, userId, deviceIdentity)
            .onSuccess { info ->
                _accountNickname.value = info.nickname
                _accountAvatarUrl.value = info.avatarUrl
                _accountLabel.value = info.nickname.ifBlank { "酷狗账号 · $userId" }
                prefs.edit()
                    .putString(KEY_NICKNAME, info.nickname)
                    .putString(KEY_AVATAR, info.avatarUrl)
                    .apply()
            }
            .onFailure { Timber.w(it, "KugouRepository: refreshUserInfo failed") }
    }

    // ─── 手机号登录 ───────────────────────────────────────────────────────

    /** 发送短信验证码（`/v7/send_mobile_code`，android 签名）。 */
    suspend fun sendSmsCode(mobile: String): Result<Boolean> =
        accountApi.sendSmsCode(mobile, deviceIdentity)

    /**
     * 手机号 + 验证码登录；一号多账号时返回候选列表（带 [userId] 再调一次即可）。
     */
    suspend fun loginByPhone(
        mobile: String,
        code: String,
        userId: String? = null,
    ): Result<KugouPhoneLoginResult> = accountApi
        .loginByPhone(mobile, code, deviceIdentity, userId)
        .onSuccess { result ->
            if (!result.token.isNullOrBlank() && !result.userId.isNullOrBlank()) {
                persistCredentials(
                    token = result.token,
                    userId = result.userId,
                    vipToken = result.vipToken.orEmpty(),
                )
            }
        }

    // ─── 歌单同步 ─────────────────────────────────────────────────────────

    /** 拉账号全部歌单（自动翻页，最多 [MAX_PLAYLIST_PAGES] 页）。 */
    suspend fun fetchAllPlaylists(): Result<List<KugouPlaylistBrief>> {
        val token = tokenOrNull ?: return Result.failure(IllegalStateException("未登录"))
        val userId = userIdOrNull ?: return Result.failure(IllegalStateException("缺少 userid"))
        return runCatching {
            val all = mutableListOf<KugouPlaylistBrief>()
            var page = 1
            while (page <= MAX_PLAYLIST_PAGES) {
                val batch = accountApi
                    .fetchPlaylists(token, userId, deviceIdentity, page = page)
                    .getOrThrow()
                if (batch.isEmpty()) break
                all += batch
                if (batch.size < PLAYLIST_PAGE_SIZE) break
                page++
            }
            // 记住「我喜欢」的 listid，红心收藏直接用（setSongLiked）
            all.firstOrNull { it.isDefaultLiked }?.let { liked ->
                prefs.edit().putString(KEY_LIKED_LIST_ID, liked.listId).apply()
            }
            all
        }.onFailure { Timber.w(it, "KugouRepository: fetchAllPlaylists failed") }
    }

    /** 拉某个歌单的全部歌曲（自动翻页）。 */
    suspend fun fetchAllPlaylistSongs(listId: String): Result<List<KugouSongBrief>> {
        val token = tokenOrNull ?: return Result.failure(IllegalStateException("未登录"))
        val userId = userIdOrNull ?: return Result.failure(IllegalStateException("缺少 userid"))
        return runCatching {
            val all = mutableListOf<KugouSongBrief>()
            var page = 1
            while (page <= MAX_SONG_PAGES) {
                val batch = accountApi
                    .fetchPlaylistSongs(token, userId, listId, deviceIdentity, page = page)
                    .getOrThrow()
                if (batch.isEmpty()) break
                all += batch
                if (batch.size < SONG_PAGE_SIZE) break
                page++
            }
            all
        }.onFailure { Timber.w(it, "KugouRepository: fetchAllPlaylistSongs failed") }
    }

    /** 「我喜欢」红心：加入 / 移除（由调用方判断当前是否已收藏）。 */
    suspend fun setSongLiked(
        song: KugouSongBrief,
        liked: Boolean,
    ): Result<Boolean> {
        val token = tokenOrNull ?: return Result.failure(IllegalStateException("未登录"))
        val userId = userIdOrNull ?: return Result.failure(IllegalStateException("缺少 userid"))
        val likedListId = prefs.getString(KEY_LIKED_LIST_ID, null)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("还没有同步到「我喜欢」歌单"))
        return if (liked) {
            accountApi.addSongsToPlaylist(token, userId, likedListId, listOf(song), deviceIdentity)
        } else {
            accountApi.removeSongsFromPlaylist(token, userId, likedListId, listOf(song), deviceIdentity)
        }
    }

    /** 供其它请求（内置酷狗音源等）注入的 Cookie；未登录返回空串。 */
    fun getCookieString(): String {
        val token = prefs.getString(KEY_TOKEN, null) ?: return ""
        val userId = prefs.getString(KEY_USER_ID, null).orEmpty()
        val vipToken = prefs.getString(KEY_VIP_TOKEN, null).orEmpty()
        return buildString {
            append("token=").append(token)
            if (userId.isNotBlank()) append("; userid=").append(userId)
            if (vipToken.isNotBlank()) append("; vip_token=").append(vipToken)
        }
    }

    // ─── 扫码登录 ─────────────────────────────────────────────────────────

    /** 一个二维码会话：key 用于轮询；[contentUrl] 交给 UI 本地渲染二维码；[imageUri] 是接口图兜底。 */
    data class QrSession(val key: String, val imageUri: String?, val contentUrl: String)

    /** 轮询状态，数值与酷狗一致。 */
    enum class QrStatus {
        WAITING,     // 1 / 0：等待扫码
        SCANNED,     // 2 / 803：已扫码，等待确认
        CONFIRMED,   // 4：确认登录成功
        EXPIRED,     // 800：二维码过期
        UNKNOWN,
    }

    /** 申请一个登录二维码（`/v2/qrcode`，web 签名）。 */
    suspend fun createQrSession(): Result<QrSession> = accountApi
        .createQrCode(deviceIdentity)
        .map { QrSession(key = it.key, imageUri = it.imageUrl, contentUrl = it.contentUrl) }
        .onFailure { Timber.w(it, "KugouRepository: createQrSession failed") }

    /**
     * 轮询扫码状态；[QrStatus.CONFIRMED] 时会把 token/userid/vip_token 落盘并置登录态。
     */
    suspend fun pollQrSession(key: String): Result<QrStatus> = accountApi
        .checkQrCode(key, deviceIdentity)
        .mapCatching { data ->
            val status = data.optInt("status", 0)
            when (status) {
                4 -> {
                    val token = data.optString("token")
                    val userId = data.optString("userid")
                    if (token.isBlank() || userId.isBlank()) error("登录成功但没有拿到凭证")
                    persistCredentials(
                        token = token,
                        userId = userId,
                        vipToken = data.optString("vip_token"),
                    )
                    QrStatus.CONFIRMED
                }
                2, 803 -> QrStatus.SCANNED
                1 -> QrStatus.WAITING
                800, 0 -> QrStatus.EXPIRED
                else -> QrStatus.UNKNOWN
            }
        }
        .onFailure { Timber.w(it, "KugouRepository: pollQrSession failed") }

    private fun persistCredentials(token: String, userId: String, vipToken: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_VIP_TOKEN, vipToken.takeIf { it.isNotBlank() })
            .apply()
        // 先用缓存里的昵称/头像顶上（同一账号才有），再后台拉最新
        val cachedNickname = prefs.getString(KEY_NICKNAME, null)
        _accountNickname.value = cachedNickname
        _accountAvatarUrl.value = prefs.getString(KEY_AVATAR, null)
        _accountLabel.value = cachedNickname?.takeIf { it.isNotBlank() } ?: "酷狗账号 · $userId"
        _isLoggedInFlow.value = true
        Timber.i("KugouRepository: logged in (userId=%s)", userId)
        // ⚡ 昵称/头像要走签名接口，登录成功后异步补上
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refreshUserInfo() }
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_VIP_TOKEN)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR)
            .remove(KEY_LIKED_LIST_ID)
            .apply()
        _accountLabel.value = null
        _accountNickname.value = null
        _accountAvatarUrl.value = null
        _isLoggedInFlow.value = false
    }

    /** 进程启动时恢复登录态（由 AppModule/Service 首次取用时也安全）。 */
    fun restoreSession() {
        _isLoggedInFlow.value = isLoggedIn
        if (isLoggedIn) {
            val userId = prefs.getString(KEY_USER_ID, null)
            _accountNickname.value = prefs.getString(KEY_NICKNAME, null)
            _accountAvatarUrl.value = prefs.getString(KEY_AVATAR, null)
            _accountLabel.value = prefs.getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() }
                ?: userId?.let { "酷狗账号 · $it" }
            // 缓存里没有昵称（老版本登录过）时补拉一次
            if (prefs.getString(KEY_NICKNAME, null).isNullOrBlank()) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refreshUserInfo() }
            }
        }
    }

    private companion object {
        const val KEY_TOKEN = "kugou_token"
        const val KEY_USER_ID = "kugou_userid"
        const val KEY_VIP_TOKEN = "kugou_vip_token"
        const val KEY_NICKNAME = "kugou_nickname"
        const val KEY_AVATAR = "kugou_avatar"
        const val KEY_LIKED_LIST_ID = "kugou_liked_list_id"
        const val KEY_DFID = "kugou_dfid"
        const val KEY_GUID = "kugou_guid"
        const val KEY_DEV = "kugou_dev"

        /** 翻页上限，防止接口异常时无限循环。 */
        const val MAX_PLAYLIST_PAGES = 10
        const val PLAYLIST_PAGE_SIZE = 30
        const val MAX_SONG_PAGES = 50
        const val SONG_PAGE_SIZE = 200
    }
}
