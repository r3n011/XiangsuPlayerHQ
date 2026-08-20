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
    private val preferencesRepo: AiPreferencesRepository
) {
    companion object {
        private const val TAG = "AiCompanionManager"
        // 软件开启播放的第一首歌固定台词，不走 AI 生成（带歌名）
        private fun firstSongIntro(song: Song) =
            "我是粽子，你的私人音乐陪伴。我们今天来听《${song.title}》"
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

    private suspend fun playStreaming(apiKey: String, text: String, voiceId: String, speed: Float = 1.0f) {
        withContext(Dispatchers.Main) { _isPlaying.value = true }

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
                kotlinx.coroutines.delay(estimatedDurationMs + 200)
            }

            track.stop()
            track.release()
            audioTrack = null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Streaming playback failed")
            stopPlayback()
        } finally {
            withContext(Dispatchers.Main) { _isPlaying.value = false }
        }
    }

    private fun stopPlayback() {
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
            val prevInfo = if (previousSong != null) "《${previousSong.title}》" else "开始"
            val prompt = "从${prevInfo}自然过渡到《${currentSong.title}》(${currentSong.artist})，说一句简短衔接语。不超过30字，不加引号。"
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
