package com.theveloper.pixelplay.data.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 音乐陪伴管理器
 * - 切歌时生成衔接语并通过流式 TTS 播报（低延迟）
 * - 首次使用时播放自我介绍
 */
@Singleton
class AiCompanionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsClient: MiMoTtsClient,
    private val aiOrchestrator: AiOrchestrator,
    private val preferencesRepo: AiPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine
) {
    companion object {
        private const val TAG = "AiCompanionManager"
        // 软件开启播放的第一首歌固定台词，不走 AI 生成（带歌名），每次随机挑选一种
        private fun firstSongIntro(song: Song): String = listOf(
            "你好呀，我是粽子，你的私人电台 DJ。今晚我们第一站，就来听《${song.title}》，把它当作我们之间的小小开场白吧。",
            "哈喽，我是你的专属电台 DJ 粽子！今晚的音乐之旅，就从《${song.title}》开始吧，希望它能给你带来好心情～",
            "欢迎收听粽子的私人电台～第一首歌，我们一起来听《${song.title}》，让它成为今晚美好的开端。",
            "嗨，我是粽子！很高兴能陪你听歌，今天第一首为你播的是《${song.title}》，一起沉浸其中吧。",
            "这里是粽子的深夜电台，感谢你的收听。第一站，为你奉上《${song.title}》，愿这段旋律能抚平今天的疲惫。",
            "粽子来啦～新的一天从好歌开始，第一首送上《${song.title}》，准备好了吗？我们一起出发。"
        ).random()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentComment = MutableStateFlow<String?>(null)
    val currentComment: StateFlow<String?> = _currentComment.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isFirstUse = true
    private var lastSongId: String? = null

    // 提前预生成衔接文本：在上一首自然结束前 ~20s 算好下一首的衔接语，切歌时直接播放
    private var pendingComment: String? = null
    private var pendingCommentSongId: String? = null
    private var prefetchJob: Job? = null
    private val prefetchLeadMillis = 20_000L

    // ── 自然播完前的 AI 陪伴时序 ──
    // 上一首播到末尾前 [companionStartLeadMs] 就开始渐出并播放 AI；
    // AI 播到还剩 [companionFadeInBeforeEndMs] 时渐入下一首。
    private val companionStartLeadMs = 5_000L
    private val companionFadeInBeforeEndMs = 8_000L
    /** 提前播一首的陪伴（在歌曲结束前触发）的任务 */
    private var companionAheadJob: Job? = null

    /**
     * 切歌时调用 — 生成衔接语并通过流式 TTS 播放
     */
    fun onSongTransition(
        previousSong: Song?,
        currentSong: Song
    ) {
        // 忽略漫游模式的加载占位歌曲
        if (currentSong.id == "roaming_loading") return
        if (previousSong?.id == "roaming_loading") {
            // 从加载占位切到真实歌曲，视为首次使用（播放开头语）
            val songId = currentSong.id
            if (songId == lastSongId) return
            lastSongId = songId
            playbackJob?.cancel()
            playbackJob = scope.launch {
                try {
                    val isEnabled = preferencesRepo.isCompanionEnabled.first()
                    if (!isEnabled) return@launch
                    val mimoKey = preferencesRepo.mimoApiKey.first()
                    if (mimoKey.isBlank()) return@launch
                    Timber.tag(TAG).d("Companion triggered (roaming intro): ${currentSong.title}")
                    val voiceId = preferencesRepo.companionVoice.first()
                    val speed = preferencesRepo.companionSpeed.first()
                    val text = firstSongIntro(currentSong)
                    if (text.isNullOrBlank()) return@launch
                    _currentComment.value = text
                    playStreaming(mimoKey, text, voiceId, speed)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Companion error (roaming intro)")
                } finally {
                    _currentComment.value = null
                }
            }
            return
        }

        val songId = currentSong.id
        if (songId == lastSongId) return
        lastSongId = songId

        // 真实切歌：取消上一首遗留的预生成任务（手动切歌也会走这里，从而跳过预生成）
        prefetchJob?.cancel()
        companionAheadJob?.cancel()
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val isEnabled = preferencesRepo.isCompanionEnabled.first()
                if (!isEnabled) {
                    Timber.tag(TAG).d("Companion disabled, skipping")
                    return@launch
                }

                val mimoKey = preferencesRepo.mimoApiKey.first()
                if (mimoKey.isBlank()) {
                    Timber.tag(TAG).w("MiMo TTS API Key not configured, skipping companion")
                    return@launch
                }

                Timber.tag(TAG).d("Companion triggered: ${previousSong?.title} -> ${currentSong.title}")

                val voiceId = preferencesRepo.companionVoice.first()
                val speed = preferencesRepo.companionSpeed.first()

                // 优先使用提前预生成好的衔接文本；首播用自我介绍
                val text = if (isFirstUse) {
                    isFirstUse = false
                    firstSongIntro(currentSong)
                } else if (pendingCommentSongId == currentSong.id && !pendingComment.isNullOrBlank()) {
                    val cached = pendingComment
                    pendingComment = null
                    pendingCommentSongId = null
                    Timber.tag(TAG).d("Using prefetched companion text for ${currentSong.title}")
                    cached
                } else {
                    generateTransition(previousSong, currentSong)
                }

                if (text.isNullOrBlank()) {
                    Timber.tag(TAG).w("Generated text is null/blank, skipping TTS")
                    return@launch
                }

                Timber.tag(TAG).d("Companion text: $text")
                _currentComment.value = text
                playStreaming(mimoKey, text, voiceId, speed)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Companion transition failed")
            } finally {
                _currentComment.value = null
            }
        }
    }

    /**
     * 在上一首自然播放到尾声（剩余约 [prefetchLeadMillis]）时调用，
     * 提前生成下一首 [nextSong] 的衔接文本并缓存，切歌时即可立即播放。
     * 手动切歌会触发新的 onSongTransition → 此处 prefetchJob 被取消，从而跳过预生成。
     */
    fun schedulePrefetch(currentSong: Song, durationMillis: Long, nextSong: Song?) {
        prefetchJob?.cancel()
        if (nextSong == null || durationMillis <= prefetchLeadMillis) return
        val delayMs = (durationMillis - prefetchLeadMillis).coerceAtLeast(0L)
        prefetchJob = scope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            val text = generateTransition(currentSong, nextSong)
            if (!text.isNullOrBlank()) {
                pendingComment = text
                pendingCommentSongId = nextSong.id
                Timber.tag(TAG).d("Prefetched companion text for upcoming: ${nextSong.title}")
            }
        }
    }

    /**
     * 在上一首自然播放到末尾前约 [companionStartLeadMs] 时，提前开始 AI 陪伴：
     * 上一曲在这个窗口内渐出，AI 开播；AI 播到还剩 [companionFadeInBeforeEndMs] 时渐入下一曲。
     * 仅用于"自然播完"，手动切歌会触发 onSongTransition 并取消本任务。
     *
     * @param currentSong 正在播放、即将自然结束的上一首
     * @param nextSong    结束后要播的下一首（AI 开场要预告的衔接目标）
     */
    fun scheduleCompanionAhead(
        currentSong: Song,
        nextSong: Song?,
        durationMillis: Long
    ) {
        companionAheadJob?.cancel()
        if (nextSong == null || durationMillis <= companionStartLeadMs) return
        val delayMs = (durationMillis - companionStartLeadMs).coerceAtLeast(0L)
        companionAheadJob = scope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            // 上一首播到最后 5s：渐出并开播 AI（衔接 currentSong → nextSong）
            playCompanionAhead(currentSong, nextSong)
        }
    }

    /** 在歌曲结束前提前播放 AI 陪伴（承接 scheduleCompanionAhead 的触发体） */
    private suspend fun playCompanionAhead(prevSong: Song?, targetSong: Song) {
        try {
            val isEnabled = preferencesRepo.isCompanionEnabled.first()
            if (!isEnabled) return
            val mimoKey = preferencesRepo.mimoApiKey.first()
            if (mimoKey.isBlank()) return
            val voiceId = preferencesRepo.companionVoice.first()
            val speed = preferencesRepo.companionSpeed.first()

            val text = if (pendingCommentSongId == targetSong.id && !pendingComment.isNullOrBlank()) {
                val cached = pendingComment
                pendingComment = null
                pendingCommentSongId = null
                cached
            } else {
                generateTransition(prevSong, targetSong)
            }
            if (text.isNullOrBlank()) return
            // 提前标记，避免真正的 onSongTransition 重复触发同一首的陪伴
            lastSongId = targetSong.id
            _currentComment.value = text
            // 渐出取较长时长（约 4.5s），贴合"末尾 5s 渐出"的听感
            playStreaming(mimoKey, text, voiceId, speed, fadeOutMs = 4_500L)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Companion ahead failed")
        } finally {
            _currentComment.value = null
        }
    }

    /** 预览指定音色（非流式，简短文本） */
    suspend fun previewVoice(apiKey: String, voiceId: String): ByteArray? {
        return ttsClient.synthesize(
            apiKey = apiKey,
            text = MiMoTtsClient.PREVIEW_TEXT,
            styleInstruction = "用温暖亲切的语调，语速适中，像朋友聊天一样自然。",
            voiceId = voiceId
        )
    }

    /** 播放预览音频（MediaPlayer 播放 MP3） */
    suspend fun playPreviewAudio(audioData: ByteArray) {
        stopPlayback()
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.mp3")
                tempFile.writeBytes(audioData)
                withContext(Dispatchers.Main) {
                    val mp = MediaPlayer.create(context, Uri.fromFile(tempFile)) ?: return@withContext
                    _isPlaying.value = true
                    mp.setOnCompletionListener {
                        _isPlaying.value = false
                        it.release()
                        tempFile.delete()
                    }
                    mp.setOnErrorListener { p, _, _ ->
                        _isPlaying.value = false
                        p.release()
                        tempFile.delete()
                        true
                    }
                    mp.start()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Preview playback failed")
                _isPlaying.value = false
            }
        }
    }

    /** 停止当前播放 */
    fun stop() {
        playbackJob?.cancel()
        stopPlayback()
        _currentComment.value = null
    }

    // ─── 流式播放核心 ─────────────────────────────────────────────

    private suspend fun playStreaming(
        apiKey: String,
        text: String,
        voiceId: String,
        speed: Float = 1.0f,
        fadeOutMs: Long = 450L
    ) {
        withContext(Dispatchers.Main) { _isPlaying.value = true }
        // AI 电台：AI 开播前音乐渐出（闪避到低电平），保持背景底噪而非静音
        dualPlayerEngine.setCompanionDucked(true, fadeOutMs = fadeOutMs)

        try {
            val sampleRate = MiMoTtsClient.PCM_SAMPLE_RATE
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
            val bufSize = maxOf(minBuf, 8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(encoding)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            var totalBytes = 0L

            val speedHint = when {
                speed <= 0.75f -> "语速偏慢，从容不迫"
                speed <= 1.0f -> "语速适中，自然流畅"
                speed <= 1.5f -> "语速稍快，轻快活泼"
                else -> "语速较快，干脆利落"
            }

            Timber.tag(TAG).d("Starting TTS streaming, voice=$voiceId, speed=$speed")

            ttsClient.synthesizeStreaming(
                apiKey = apiKey,
                text = text,
                styleInstruction = "你是“粽子”，一个温暖有趣的音乐伙伴。根据文本内容自行选择最合适的语气和情绪来朗读。$speedHint。",
                voiceId = voiceId
            ).collect { chunk ->
                track.write(chunk, 0, chunk.size)
                totalBytes += chunk.size
            }

            Timber.tag(TAG).d("TTS streaming finished, totalBytes=$totalBytes")

            // 等待播放完毕
            if (totalBytes > 0) {
                // flush 不适用于 STREAM 模式，等待 buffer 播完
                val estimatedDurationMs = (totalBytes * 1000L) / (sampleRate * 2) // 16bit mono
                // 渐入下一首：AI 还剩 [companionFadeInBeforeEndMs] 时提前恢复音乐（不等 AI 完全结束）
                if (estimatedDurationMs > companionFadeInBeforeEndMs + 200L) {
                    scope.launch {
                        delay(estimatedDurationMs - companionFadeInBeforeEndMs)
                        dualPlayerEngine.setCompanionDucked(false)
                    }
                }
                kotlinx.coroutines.delay(estimatedDurationMs + 200)
            }

            track.stop()
            track.release()
            audioTrack = null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Streaming playback failed")
            stopPlayback()
        } finally {
            // AI 电台：播报结束，音乐渐入恢复（若已被上面提前渐入，此处幂等到达最终音量）
            dualPlayerEngine.setCompanionDucked(false)
            withContext(Dispatchers.Main) { _isPlaying.value = false }
        }
    }

    private fun stopPlayback() {
        dualPlayerEngine.setCompanionDucked(false)
        try {
            audioTrack?.let {
                try { it.pause() } catch (_: Exception) {}
                try { it.flush() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        audioTrack = null
        _isPlaying.value = false
    }

    // ─── AI 文本生成 ──────────────────────────────────────────────

    private suspend fun generateTransition(previousSong: Song?, currentSong: Song): String? {
        return try {
            val prevName = previousSong?.title ?: ""
            val prevInfo = if (previousSong != null) "《${previousSong.title}》" else "开场"
            val prompt = buildString {
                append("你是一档私人电台的暖场 DJ“粽子”。从${prevInfo}自然过渡到现在的《${currentSong.title}》(${currentSong.artist})，")
                append("来一段有温度的话作为听众的私人点播开场。唠会儿嗑：")
                if (prevName.isNotBlank()) append("顺口说说听完《$prevName》后的感受，")
                append("再说说《${currentSong.title}》适合什么时刻、什么心情听。像朋友深夜分享歌单那样自然亲切，不要正式、不要说教。")
                append("控制在60到100字，直接说内容即可，不要加引号、不要写“粽子说：”这类前缀。")
            }
            // 只使用「AI 集成」设置里配置的主 provider（如 CUSTOM），不做无意义的空 provider 兜底
            aiOrchestrator.generateWithPrimaryProvider(
                prompt = prompt,
                type = AiSystemPromptType.GENERAL,
                temperature = 0.7f,
                timeoutMs = 20_000L
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("AI 陪伴文本生成失败（主 provider）: ${e.message}")
            null
        }
    }
}
