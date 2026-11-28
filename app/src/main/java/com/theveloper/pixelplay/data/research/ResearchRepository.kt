package com.theveloper.pixelplay.data.research

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.database.ResearchEventDao
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 活动页十种生命周期状态（规范 05 §1.1，同一套卡片只换文案/颜色/主按钮） */
enum class ResearchPhase {
    /** 未参与（含冷却期单列 COOLDOWN） */
    NOT_PARTICIPATING,
    /** 已同意、采集中（< 首传门槛） */
    COLLECTING,
    /** 已达标、待首次上传 */
    READY_FIRST_UPLOAD,
    /** 生效中 */
    ACTIVE,
    /** 即将到期（≤24h） */
    EXPIRING_SOON,
    /** 已过期（宽限内上传一次可恢复） */
    EXPIRED,
    /** 本周期额度用完（资格仍有效） */
    QUOTA_EXHAUSTED,
    /** 已退出，冷却中 */
    COOLDOWN,
    /** 活动已截止 */
    ENDED,
    /** 上传失败（数据还在本地） */
    UPLOAD_FAILED
}

/** 活动页聚合状态（驱动 ResearchSettingsContent 渲染） */
data class ResearchUiState(
    val phase: ResearchPhase = ResearchPhase.NOT_PARTICIPATING,
    val collectedEvents: Int = 0,
    val minEvents: Int = if (BuildConfig.DEBUG) 2 else 20,
    val firstUploadDone: Boolean = false,
    val expiresAt: Long = 0,
    val quotaLimitTokens: Long = 0,
    val quotaUsedTokens: Long = 0,
    val quotaResetAt: Long = 0,
    val nextUploadAfter: Long = 0,
    val deadline: Long = 0,
    val autoDaily: Boolean = false,
    val wifiOnly: Boolean = true,
    val plaintextEnabled: Boolean = false,
    val licenseKeyMasked: String? = null,
    val cooldownUntil: Long = 0,
    val lastUploadAt: Long = 0,
    val lastUploadCount: Int = 0,
    val uploadInProgress: Boolean = false,
    val uploadError: String? = null,
    val consentRequired: Boolean = false,
    val legalAgreementVersion: String = "",
    val legalPrivacyVersion: String = "",
    val latestNotice: ResearchNoticeSummary? = null,
    val hasUnreadNotice: Boolean = false,
    val pendingWithdraw: Boolean = false,
    val refreshing: Boolean = false
) {
    val quotaRemainingTokens: Long
        get() = (quotaLimitTokens - quotaUsedTokens).coerceAtLeast(0)
}

/**
 * 「听歌数据研究计划」仓库层：注册、事件上传、状态刷新、设置、退出与协议/公告缓存。
 *
 * - 资格与额度一律以服务端 /me 为准，客户端只展示与倒计时（规范 05 §1 引导原则）
 * - 上传成功后清删本地已传事件、用响应 + /me 刷新状态
 * - 退出失败只置 pendingWithdraw，不删本地（避免服务端仍占额度）
 */
@Singleton
class ResearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: ResearchAuthStore,
    private val api: ResearchApi,
    private val researchEventDao: ResearchEventDao,
    private val aiUsageDao: com.theveloper.pixelplay.data.database.AiUsageDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: androidx.work.WorkManager,
    @com.theveloper.pixelplay.di.AppScope private val appScope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(ResearchUiState())
    val uiState: StateFlow<ResearchUiState> = _uiState.asStateFlow()

    /** 最近一次协议全文的加载来源（key = agreement/privacy，值 = server/bundled/cache），诊断行用 */
    private val _lastLegalSource = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastLegalSource: StateFlow<Map<String, String>> = _lastLegalSource.asStateFlow()

    private val uploadMutex = Mutex()
    private var lastFailedBatchId: String? = null

    private val legalCacheDir: File by lazy {
        File(context.filesDir, "research_legal").apply { mkdirs() }
    }

    init {
        // 周期上传任务随 Wi-Fi 偏好变化重新调度（UNMETERED ↔ CONNECTED）
        appScope.launch {
            userPreferencesRepository.researchWifiOnlyFlow.collect { wifiOnly ->
                ResearchUploadWorker.ensureScheduled(workManager, wifiOnly)
            }
        }
        appScope.launch {
            // 启动时重试未送达的退出请求；刷新状态；资格临期/首传达标则直接上传
            retryPendingWithdrawIfNeeded()
            refresh(showLoading = false)
            runCatching { urgentUploadIfExpiring() }
        }
    }

    /* ── 状态刷新 ─────────────────────────────────────────────────────── */

    suspend fun refresh(showLoading: Boolean = true) {
        if (showLoading) _uiState.update { it.copy(refreshing = true) }
        try {
            val local = collectLocalState()
            if (!authStore.hasSession()) {
                val cooldownUntil = userPreferencesRepository.researchCooldownUntilFlow.first()
                val phase = if (System.currentTimeMillis() / 1000 < cooldownUntil) {
                    ResearchPhase.COOLDOWN
                } else {
                    ResearchPhase.NOT_PARTICIPATING
                }
                _uiState.update {
                    local.copy(phase = phase, cooldownUntil = cooldownUntil, refreshing = false)
                }
                if (phase == ResearchPhase.COOLDOWN) retryPendingWithdrawIfNeeded()
                return
            }

            // 会话存在 → /me 刷新资格与额度（校时钟偏移、存 hashSaltId）
            val me = runCatching { api.me() }.getOrNull()
            if (me == null) {
                // 离线：用本地缓存渲染，状态保持上次 phase（不误报错误页）
                val cached = _uiState.value
                _uiState.update {
                    collectLocalState().copy(
                        phase = cached.phase,
                        expiresAt = cached.expiresAt,
                        quotaLimitTokens = cached.quotaLimitTokens,
                        quotaUsedTokens = cached.quotaUsedTokens,
                        quotaResetAt = cached.quotaResetAt,
                        refreshing = false
                    )
                }
                refreshRemoteMetaQuiet()
                return
            }
            authStore.serverTimeOffsetSec =
                me.serverTime - System.currentTimeMillis() / 1000

            _uiState.update { state ->
                collectLocalState().copy(
                    phase = derivePhase(me),
                    firstUploadDone = me.key.status != "pending",
                    minEvents = me.upload.minEvents.coerceAtLeast(1),
                    expiresAt = me.key.expiresAt,
                    quotaLimitTokens = me.quota.limitTokens,
                    quotaUsedTokens = me.quota.usedTokens,
                    quotaResetAt = me.quota.resetAt,
                    nextUploadAfter = me.upload.nextUploadAfter,
                    deadline = me.activity?.deadline ?: 0,
                    refreshing = false
                )
            }
            refreshRemoteMetaQuiet()
        } catch (error: Throwable) {
            Timber.w(error, "ResearchRepository: refresh failed")
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    /** 拉公告摘要与未读（失败静默，离线用缓存） */
    private suspend fun refreshRemoteMetaQuiet() {
        runCatching {
            val feed = api.notice()
            val lastRead = userPreferencesRepository.researchLastReadNoticeIdFlow.first()
            _uiState.update {
                it.copy(
                    latestNotice = feed.latest,
                    hasUnreadNotice = feed.latest != null && feed.latest.id != lastRead
                )
            }
            cacheNoticeFeed(feed)
        }
    }

    private suspend fun collectLocalState(): ResearchUiState {
        val collected = researchEventDao.countPending()
        return ResearchUiState(
            collectedEvents = collected,
            minEvents = effectiveMinEvents(),
            autoDaily = userPreferencesRepository.researchAutoDailyFlow.first(),
            wifiOnly = userPreferencesRepository.researchWifiOnlyFlow.first(),
            plaintextEnabled = userPreferencesRepository.researchPlaintextEnabledFlow.first(),
            licenseKeyMasked = authStore.licenseKey?.maskKey(),
            cooldownUntil = userPreferencesRepository.researchCooldownUntilFlow.first(),
            lastUploadAt = userPreferencesRepository.researchLastUploadAtFlow.first(),
            lastUploadCount = userPreferencesRepository.researchLastUploadCountFlow.first(),
            pendingWithdraw = userPreferencesRepository.researchPendingWithdrawFlow.first()
        )
    }

    /** debug 构建降低首传门槛便于联调；release 恒为服务端下发值 */
    private suspend fun effectiveMinEvents(): Int {
        val serverMin = _uiState.value.minEvents
        return if (BuildConfig.DEBUG) {
            minOf(DEBUG_MIN_EVENTS, serverMin.coerceAtLeast(1))
        } else {
            serverMin
        }
    }

    private suspend fun derivePhase(me: ResearchMeResponse): ResearchPhase {
        val now = System.currentTimeMillis() / 1000 + authStore.serverTimeOffsetSec
        me.activity?.deadline?.takeIf { it > 0 }?.let { deadline ->
            if (now >= deadline) return ResearchPhase.ENDED
        }
        return when (me.key.status) {
            "pending" -> if (researchEventDao.countPending() >= effectiveMinEvents()) {
                ResearchPhase.READY_FIRST_UPLOAD
            } else {
                ResearchPhase.COLLECTING
            }
            "expired" -> ResearchPhase.EXPIRED
            "disabled" -> ResearchPhase.EXPIRED
            "active" -> when {
                me.quota.expired || (me.quota.resetAt in 1 until now) -> ResearchPhase.EXPIRED
                me.quota.limitTokens in 1..me.quota.usedTokens -> ResearchPhase.QUOTA_EXHAUSTED
                me.key.expiresAt in 1..(now + URGENT_WINDOW_SEC) -> ResearchPhase.EXPIRING_SOON
                else -> ResearchPhase.ACTIVE
            }
            else -> ResearchPhase.ACTIVE
        }
    }

    /* ── 协议同意与注册 ────────────────────────────────────────────────── */

    /** 拉取协议摘要（进入活动页时调用；离线走本地缓存） */
    suspend fun loadLegalSummary(): ResearchLegalSummary {
        val cached = runCatching { api.legalSummary() }.getOrNull()
        if (cached != null) {
            cacheLegalSummary(cached)
            _uiState.update {
                it.copy(
                    legalAgreementVersion = cached.versions["agreement"] ?: "",
                    legalPrivacyVersion = cached.versions["privacy"] ?: ""
                )
            }
            return cached
        }
        val cachedSummary = readCachedFile("legal_summary.json")
            ?.let { runCatching { legalJson.decodeFromString(ResearchLegalSummary.serializer(), it) }.getOrNull() }
        if (cachedSummary != null) {
            _uiState.update {
                it.copy(
                    legalAgreementVersion = cachedSummary.versions["agreement"] ?: "",
                    legalPrivacyVersion = cachedSummary.versions["privacy"] ?: ""
                )
            }
        }
        return cachedSummary ?: ResearchLegalSummary()
    }

    /**
     * 协议全文三级回退：服务端（最新/版本化）→ 内置 assets（参与前离线必显示）→ 历史缓存。
     * 仅当用户指定了历史版本（version != null）时才允许缓存兜底。
     */
    suspend fun loadLegalDocument(key: String, version: String? = null): ResearchLegalDocument? {
        val remote = runCatching { api.legalDocument(key, version) }
            .onFailure { Timber.w(it, "ResearchRepository: legal/%s fetch failed", key) }
            .getOrNull()
        if (remote != null) {
            Timber.tag("ResearchRepo").d("legal/%s loaded from server, contentLen=%d", key, remote.content.length)
            writeCachedFile("legal_$key.txt", remote.content)
            _lastLegalSource.value = _lastLegalSource.value + (key to "server")
            return remote
        }
        if (version == null) {
            bundledLegalDocument(key)?.let { bundled ->
                Timber.tag("ResearchRepo").w(
                    "legal/%s server unreachable, using bundled asset (contentLen=%d)",
                    key, bundled.content.length
                )
                _lastLegalSource.value = _lastLegalSource.value + (key to "bundled")
                return bundled
            }
        }
        val cached = readCachedFile("legal_$key.txt") ?: return null
        _lastLegalSource.value = _lastLegalSource.value + (key to "cache")
        return ResearchLegalDocument(
            key = key,
            title = if (key == "privacy") "隐私政策补充条款" else "活动用户协议",
            version = version ?: "",
            content = cached
        )
    }

    /** 内置协议全文（assets/research/）：断网 / 服务端不可达时的保底显示 */
    private fun bundledLegalDocument(key: String): ResearchLegalDocument? {
        val assetName = when (key) {
            "agreement" -> "research/legal_agreement.md"
            "privacy" -> "research/legal_privacy.md"
            else -> return null
        }
        val content = runCatching {
            context.assets.open(assetName).use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return ResearchLegalDocument(
            key = key,
            title = if (key == "privacy") "隐私政策补充条款" else "活动用户协议",
            version = "2026-11",
            content = content
        )
    }

    /**
     * 用户同意协议 → 注册。
     *
     * @return true 注册成功；false 失败（错误已写入 uiState.uploadError）
     */
    suspend fun consentAndEnroll(): Boolean {
        val agreementVersion = _uiState.value.legalAgreementVersion.ifBlank { "2026-11" }
        val privacyVersion = _uiState.value.legalPrivacyVersion.ifBlank { "2026-11" }
        val request = ResearchEnrollRequest(
            deviceCode = authStore.getOrCreateDeviceCode(),
            appVersion = BuildConfig.VERSION_NAME,
            osVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            locale = context.resources.configuration.locales[0].toLanguageTag(),
            plaintextEnabled = userPreferencesRepository.researchPlaintextEnabledFlow.first(),
            consent = ResearchConsent(
                agreementVersion = agreementVersion,
                privacyVersion = privacyVersion,
                acceptedAt = System.currentTimeMillis() / 1000
            ),
            attestation = ResearchAttestation(type = "none")
        )
        return try {
            val response = api.enroll(request)
            authStore.licenseKey = response.licenseKey
            authStore.signKeyId = response.auth.signKeyId
            authStore.signKey = response.auth.signKey
            authStore.serverTimeOffsetSec = response.serverTime - System.currentTimeMillis() / 1000
            userPreferencesRepository.setResearchConsent(agreementVersion, privacyVersion, response.serverTime)
            lastFailedBatchId = null
            refresh(showLoading = false)
            // 已达标（旧队列残留）时自动触发首次上传
            if (_uiState.value.phase == ResearchPhase.READY_FIRST_UPLOAD) {
                uploadNow()
            }
            true
        } catch (error: ResearchApiException) {
            if (error.code == "COOLDOWN") {
                val until = System.currentTimeMillis() / 1000 + error.retryAfter
                userPreferencesRepository.setResearchCooldownUntil(until)
                _uiState.update {
                    it.copy(phase = ResearchPhase.COOLDOWN, cooldownUntil = until)
                }
            } else if (error.code == "CONSENT_REQUIRED") {
                _uiState.update { it.copy(consentRequired = true, uploadError = error.message) }
                runCatching { loadLegalSummary() }
            } else {
                _uiState.update { it.copy(uploadError = error.message.ifBlank { error.code }) }
            }
            false
        } catch (error: Exception) {
            Timber.w(error, "ResearchRepository: enroll network error")
            _uiState.update { it.copy(uploadError = NETWORK_ERROR_HINT) }
            false
        }
    }

    /* ── 上传 ─────────────────────────────────────────────────────────── */

    /**
     * 立即上传一次（手动按钮 / Worker 到点 / 紧急覆盖共用入口）。
     *
     * @param ignoreThreshold true = 跳过「≥门槛」检查（资格临期续命场景）
     * @return 上传是否成功
     */
    suspend fun uploadNow(ignoreThreshold: Boolean = false): Boolean = uploadMutex.withLock {
        if (!authStore.hasSession()) return false
        _uiState.update { it.copy(uploadInProgress = true, uploadError = null) }
        try {
            val minEvents = effectiveMinEvents()
            val pendingCount = researchEventDao.countPending()
            val firstUpload = _uiState.value.firstUploadDone.not()
            if (firstUpload && !ignoreThreshold && pendingCount < minEvents) {
                _uiState.update {
                    it.copy(uploadInProgress = false, phase = ResearchPhase.COLLECTING)
                }
                return false
            }

            var batchId = lastFailedBatchId ?: "b_${System.currentTimeMillis() / 1000}_" +
                UUID.randomUUID().toString().take(8)
            val events = researchEventDao.pendingBatch(MAX_BATCH).map { entity ->
                ResearchListenEventPayload(
                    songKey = entity.songKey,
                    titleHash = entity.titleHash,
                    artistHash = entity.artistHash,
                    albumHash = entity.albumHash,
                    source = entity.source,
                    playedAt = entity.playedAt,
                    durationSec = entity.durationSec,
                    playedSec = entity.playedSec,
                    completed = entity.completed,
                    skipped = entity.skipped,
                    playMode = entity.playMode,
                    quality = entity.quality,
                    title = entity.plainTitle,
                    artist = entity.plainArtist,
                    album = entity.plainAlbum
                )
            }
            if (events.isEmpty()) {
                _uiState.update { it.copy(uploadInProgress = false) }
                refresh(showLoading = false)
                return true
            }

            val request = ResearchListenRequest(
                deviceCode = authStore.getOrCreateDeviceCode(),
                batchId = batchId,
                uploadedAt = System.currentTimeMillis() / 1000,
                events = events
            )
            val response = api.listen(request)
            // 成功：删除已传事件（pendingBatch 与 events 同序，取最早的 N 条即本批）
            deleteUploadedBatch(events.size)
            lastFailedBatchId = null
            userPreferencesRepository.setResearchLastUpload(
                System.currentTimeMillis() / 1000,
                response.accepted
            )

            // 上传后 /me 刷新资格与额度（响应只带 accepted/nextUploadAfter）
            refresh(showLoading = false)
            _uiState.update {
                it.copy(
                    uploadInProgress = false,
                    uploadError = null,
                    lastUploadAt = System.currentTimeMillis() / 1000,
                    lastUploadCount = response.accepted
                )
            }
            return true
        } catch (error: ResearchApiException) {
            Timber.w(error, "ResearchRepository: upload failed code=%s", error.code)
            _uiState.update {
                it.copy(
                    uploadInProgress = false,
                    uploadError = error.message.ifBlank { error.code },
                    phase = when (error.code) {
                        "KEY_EXPIRED" -> ResearchPhase.EXPIRED
                        "KEY_DISABLED" -> ResearchPhase.EXPIRED
                        "INVALID_KEY" -> ResearchPhase.NOT_PARTICIPATING
                        else -> ResearchPhase.UPLOAD_FAILED
                    }
                )
            }
            if (error.code == "INVALID_KEY") authStore.clearSession()
            return false
        } catch (error: Exception) {
            Timber.w(error, "ResearchRepository: upload network error")
            // 失败保留本地事件，记录 batchId 供重试复用（幂等）
            lastFailedBatchId = lastFailedBatchId ?: "b_${System.currentTimeMillis() / 1000}_" +
                UUID.randomUUID().toString().take(8)
            _uiState.update {
                it.copy(uploadInProgress = false, uploadError = NETWORK_ERROR_HINT, phase = ResearchPhase.UPLOAD_FAILED)
            }
            return false
        }
    }

    /** 删除已上传的最早 N 条（与 pendingBatch 的取用顺序一致） */
    private suspend fun deleteUploadedBatch(count: Int) {
        val ids = researchEventDao.pendingBatch(count).map { it.id }
        if (ids.isNotEmpty()) researchEventDao.deleteByIds(ids)
    }

    /** Worker / 启动时的到点检查：资格/上传到期且有待传数据时执行 */
    suspend fun uploadIfDue(): Boolean {
        val state = _uiState.value
        if (!authStore.hasSession()) return false
        val now = System.currentTimeMillis() / 1000
        val due = state.nextUploadAfter in 1..now ||
            (state.expiresAt in 1..(now + URGENT_WINDOW_SEC)) ||
            (state.firstUploadDone.not() && state.collectedEvents >= state.minEvents)
        return if (due) uploadNow() else false
    }

    /** 紧急覆盖：资格只剩 24h 时忽略「仅 Wi-Fi」直接上传（规范 §5 上传策略） */
    suspend fun urgentUploadIfExpiring(): Boolean {
        val state = _uiState.value
        if (!authStore.hasSession()) return false
        val now = System.currentTimeMillis() / 1000
        val expiring = state.expiresAt in 1..(now + URGENT_WINDOW_SEC)
        val firstReady = state.firstUploadDone.not() && state.collectedEvents >= state.minEvents
        return if (expiring || firstReady || state.nextUploadAfter in 1..now) {
            uploadNow(ignoreThreshold = expiring)
        } else {
            false
        }
    }

    /* ── 设置 ─────────────────────────────────────────────────────────── */

    suspend fun setAutoDaily(enabled: Boolean) {
        userPreferencesRepository.setResearchAutoDaily(enabled)
        runCatching { api.setAutoDaily(enabled) }
            .onFailure { Timber.w(it, "ResearchRepository: setAutoDaily failed; 本地已记，稍后随上传同步") }
        refresh(showLoading = false)
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        userPreferencesRepository.setResearchWifiOnly(enabled)
    }

    suspend fun setPlaintextEnabled(enabled: Boolean) {
        userPreferencesRepository.setResearchPlaintextEnabled(enabled)
    }

    /* ── 退出与冷却 ───────────────────────────────────────────────────── */

    /** 退出并删除数据。网络失败只置 pendingWithdraw，下次联网重试（不清本地）。 */
    suspend fun withdraw(): Boolean {
        return try {
            api.deleteMe()
            userPreferencesRepository.setResearchPendingWithdraw(false)
            userPreferencesRepository.setResearchCooldownUntil(
                System.currentTimeMillis() / 1000 + DEFAULT_COOLDOWN_SEC
            )
            authStore.clearSession()
            researchEventDao.trimToMaxKeep(0)
            _uiState.update {
                it.copy(
                    phase = ResearchPhase.COOLDOWN,
                    cooldownUntil = System.currentTimeMillis() / 1000 + DEFAULT_COOLDOWN_SEC,
                    uploadError = null
                )
            }
            true
        } catch (error: ResearchApiException) {
            if (error.httpStatus == -1) {
                markPendingWithdraw()
                false
            } else {
                // 服务端明确拒绝（如已不存在）也按成功退出处理，避免卡死
                userPreferencesRepository.setResearchPendingWithdraw(false)
                authStore.clearSession()
                researchEventDao.trimToMaxKeep(0)
                _uiState.update { it.copy(phase = ResearchPhase.NOT_PARTICIPATING) }
                true
            }
        } catch (error: Exception) {
            Timber.w(error, "ResearchRepository: withdraw network error")
            markPendingWithdraw()
            false
        }
    }

    private suspend fun markPendingWithdraw() {
        userPreferencesRepository.setResearchPendingWithdraw(true)
        _uiState.update { it.copy(pendingWithdraw = true, uploadError = WITHDRAW_NETWORK_HINT) }
    }

    private suspend fun retryPendingWithdrawIfNeeded() {
        if (!userPreferencesRepository.researchPendingWithdrawFlow.first()) return
        if (!authStore.hasSession()) return
        runCatching { api.deleteMe() }.onSuccess {
            userPreferencesRepository.setResearchPendingWithdraw(false)
            userPreferencesRepository.setResearchCooldownUntil(
                System.currentTimeMillis() / 1000 + DEFAULT_COOLDOWN_SEC
            )
            authStore.clearSession()
            researchEventDao.trimToMaxKeep(0)
        }
    }

    /* ── AI 代理（规范 03 §7：参与且额度充足时聊天走 /ai/chat） ────────── */

    /** 会话生效且本周期还有剩余额度 → AI 走活动代理；否则回落用户自填 Key */
    fun isAiProxyAvailable(): Boolean {
        val state = _uiState.value
        if (!authStore.hasSession()) return false
        val phase = state.phase
        if (phase != ResearchPhase.ACTIVE && phase != ResearchPhase.EXPIRING_SOON) return false
        return state.quotaRemainingTokens > 0
    }

    /** 每次 AI 调用后用响应头 / SSE 末帧 quota 就地刷新额度（不发额外请求） */
    fun refreshQuotaFromFrame(frame: ResearchAiQuotaFrame) {
        _uiState.update {
            it.copy(
                quotaLimitTokens = frame.limitTokens,
                quotaUsedTokens = frame.usedTokens,
                quotaResetAt = frame.resetAt,
                phase = when {
                    frame.remainingTokens <= 0 && it.phase == ResearchPhase.ACTIVE ->
                        ResearchPhase.QUOTA_EXHAUSTED
                    frame.remainingTokens > 0 && it.phase == ResearchPhase.QUOTA_EXHAUSTED ->
                        ResearchPhase.ACTIVE
                    else -> it.phase
                }
            )
        }
    }

    /** 代理调用的 Token 用量入 ai_usage 表（服务端同口径统计，规范 §7 服务端行为 3） */
    fun recordProxyUsage(usage: ResearchAiUsage) {
        appScope.launch {
            runCatching {
                aiUsageDao.insertUsage(
                    com.theveloper.pixelplay.data.database.AiUsageEntity(
                        timestamp = System.currentTimeMillis(),
                        provider = "Research Proxy",
                        model = "auto",
                        promptType = "PROXY_CHAT",
                        promptTokens = usage.promptTokens,
                        outputTokens = usage.completionTokens,
                        thoughtTokens = 0
                    )
                )
            }
        }
    }

    /* ── 公告 ─────────────────────────────────────────────────────────── */

    suspend fun loadNoticesPage(cursor: String?, limit: Int = 20): ResearchNoticePage {
        val page: ResearchNoticePage = runCatching { api.notices(limit, cursor) }.getOrElse {
            readCachedFile("research_notices_page.json")?.let { cached ->
                runCatching {
                    legalJson.decodeFromString(ResearchNoticePage.serializer(), cached)
                }.getOrNull()
            } ?: ResearchNoticePage()
        }
        if (cursor == null) {
            writeCachedFile(
                "research_notices_page.json",
                legalJson.encodeToString(ResearchNoticePage.serializer(), page)
            )
        }
        return page
    }

    suspend fun markNoticeRead(noticeId: String) {
        userPreferencesRepository.setResearchLastReadNoticeId(noticeId)
        _uiState.update {
            it.copy(hasUnreadNotice = it.latestNotice?.id != noticeId)
        }
    }

    /* ── 导出 ─────────────────────────────────────────────────────────── */

    suspend fun exportRawJson(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        api.exportRaw()
    }

    /* ── 本地缓存 ─────────────────────────────────────────────────────── */

    private val legalJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun writeCachedFile(name: String, content: String) {
        runCatching {
            legalCacheDir.mkdirs()
            File(legalCacheDir, name).writeText(content, Charsets.UTF_8)
        }
    }

    private fun readCachedFile(name: String): String? =
        runCatching { File(legalCacheDir, name).takeIf { it.exists() }?.readText(Charsets.UTF_8) }.getOrNull()

    private fun cacheLegalSummary(summary: ResearchLegalSummary) {
        writeCachedFile("legal_summary.json", legalJson.encodeToString(ResearchLegalSummary.serializer(), summary))
    }

    private fun cacheNoticeFeed(feed: ResearchNoticeFeed) {
        writeCachedFile("research_notice_feed.json", legalJson.encodeToString(ResearchNoticeFeed.serializer(), feed))
    }

    private fun String.maskKey(): String {
        if (length <= 8) return this
        val prefix = substringBefore('-')
        val suffix = substringAfterLast('-')
        return "$prefix-****-****-$suffix"
    }

    private inline fun MutableStateFlow<ResearchUiState>.update(
        transform: (ResearchUiState) -> ResearchUiState
    ) {
        value = transform(value)
    }

    companion object {
        private const val MAX_BATCH = 1000
        private const val DEFAULT_MIN_EVENTS = 20
        private const val DEBUG_MIN_EVENTS = 2
        private const val URGENT_WINDOW_SEC = 24L * 3600
        private const val DEFAULT_COOLDOWN_SEC = 30L * 24 * 3600
        private const val NETWORK_ERROR_HINT = "网络不可用，数据还在本地，稍后重试"
        private const val WITHDRAW_NETWORK_HINT = "退出请求未送达，请稍后重试"
    }
}
