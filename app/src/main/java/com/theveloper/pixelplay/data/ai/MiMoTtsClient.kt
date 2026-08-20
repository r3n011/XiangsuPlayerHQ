package com.theveloper.pixelplay.data.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiMo TTS 客户端 — 调用 Xiaomi MiMo V2.5 TTS API 生成语音
 * 支持流式输出（SSE），首字延迟更低
 *
 * API 文档: https://mimo.mi.com/docs/zh-CN/api/audio/tts
 */
@Singleton
class MiMoTtsClient @Inject constructor() {

    companion object {
        private const val TAG = "MiMoTtsClient"
        private const val TTS_BASE_URL = "https://api.xiaomimimo.com/v1/chat/completions"
        private const val MODEL_ID = "mimo-v2.5-tts"

        val BUILT_IN_VOICES = listOf(
            VoiceOption("mimo_default", "默认"),
            VoiceOption("冰糖", "冰糖"),
            VoiceOption("茉莉", "茉莉"),
            VoiceOption("苏打", "苏打"),
            VoiceOption("白桦", "白桦"),
            VoiceOption("Mia", "Mia"),
            VoiceOption("Chloe", "Chloe"),
            VoiceOption("Milo", "Milo"),
            VoiceOption("Dean", "Dean"),
        )

        const val PREVIEW_TEXT = "你好，我是你的音乐伙伴，接下来让我陪你一起聆听。"
        const val PCM_SAMPLE_RATE = 24000
        const val PCM_CHANNEL_COUNT = 1
        const val PCM_BITS_PER_SAMPLE = 16
    }

    data class VoiceOption(val id: String, val displayName: String)

    @Serializable
    private data class TtsMessage(val role: String, val content: String)

    @Serializable
    private data class TtsAudioConfig(
        val format: String = "pcm16",
        val voice: String = "mimo_default"
    )

    @Serializable
    private data class TtsRequest(
        val model: String = MODEL_ID,
        val messages: List<TtsMessage>,
        val audio: TtsAudioConfig,
        val stream: Boolean = true
    )

    // 非流式响应
    @Serializable
    private data class TtsAudioData(val data: String? = null)
    @Serializable
    private data class TtsMsg(val audio: TtsAudioData? = null)
    @Serializable
    private data class TtsChoice(val message: TtsMsg? = null, val delta: TtsDelta? = null)
    @Serializable
    private data class TtsResponse(val choices: List<TtsChoice>)

    // 流式 delta
    @Serializable
    private data class TtsDeltaAudio(val data: String? = null)
    @Serializable
    private data class TtsDelta(val audio: TtsDeltaAudio? = null)

    // 必须 encodeDefaults = true：否则带默认值的字段（model / stream / audio.format / audio.voice）
    // 在序列化时会被省略，导致服务器拿不到 model 而报 "Unsupported model unknown-model"
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 流式合成 — 返回 pcm16 音频数据块 Flow，首字延迟极低
     * 每个 emit 是一个 pcm16 ByteArray chunk，可直接喂给 AudioTrack
     */
    fun synthesizeStreaming(
        apiKey: String,
        text: String,
        styleInstruction: String? = null,
        voiceId: String = "mimo_default"
    ): Flow<ByteArray> = callbackFlow {
        val messages = buildList {
            if (!styleInstruction.isNullOrBlank()) {
                add(TtsMessage(role = "user", content = styleInstruction))
            }
            add(TtsMessage(role = "assistant", content = text))
        }

        val request = TtsRequest(
            model = MODEL_ID,
            messages = messages,
            audio = TtsAudioConfig(format = "pcm16", voice = voiceId),
            stream = true
        )

        val jsonBody = json.encodeToString(TtsRequest.serializer(), request)
        Timber.tag(TAG).d("TTS request: $jsonBody")

        val httpRequest = Request.Builder()
            .url(TTS_BASE_URL)
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.tag(TAG).e(e, "TTS streaming failed")
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Timber.tag(TAG).e("TTS streaming error %d: %s", response.code, errBody)
                    // 把服务器返回的错误详情 + 实际发出的请求体带进异常，方便在上层日志（AiCompanionManager）中直接看到
                    close(IOException(
                        "TTS API error ${response.code}: ${errBody.take(500)}\n" +
                        ">>> Request URL: $TTS_BASE_URL\n" +
                        ">>> Request body: $jsonBody"
                    ))
                    return
                }

                try {
                    val reader: BufferedReader = response.body!!.byteStream().bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (call.isCanceled()) break

                        val l = line ?: continue
                        // SSE 格式: "data: {...}" 或 "data: [DONE]"
                        if (!l.startsWith("data: ")) continue
                        val payload = l.removePrefix("data: ").trim()
                        if (payload == "[DONE]") break

                        try {
                            val chunk = json.decodeFromString(TtsResponse.serializer(), payload)
                            val audioBase64 = chunk.choices.firstOrNull()
                                ?.delta?.audio?.data
                            if (!audioBase64.isNullOrBlank()) {
                                val pcmBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                                trySend(pcmBytes)
                            }
                        } catch (e: Exception) {
                            // 忽略解析失败的行
                            Timber.tag(TAG).w("TTS chunk parse error: %s", e.message)
                        }
                    }
                    reader.close()
                    close()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "TTS streaming read error")
                    close(e)
                } finally {
                    response.body?.close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    /**
     * 非流式合成 — 等待完整音频返回，适合预览等小段音频
     */
    suspend fun synthesize(
        apiKey: String,
        text: String,
        styleInstruction: String? = null,
        voiceId: String = "mimo_default"
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val messages = buildList {
                if (!styleInstruction.isNullOrBlank()) {
                    add(TtsMessage(role = "user", content = styleInstruction))
                }
                add(TtsMessage(role = "assistant", content = text))
            }

            val request = TtsRequest(
                model = MODEL_ID,
                messages = messages,
                audio = TtsAudioConfig(format = "mp3", voice = voiceId),
                stream = false
            )

            val jsonBody = json.encodeToString(TtsRequest.serializer(), request)

            val httpRequest = Request.Builder()
                .url(TTS_BASE_URL)
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Timber.tag(TAG).e("TTS API error %d: %s", response.code, body)
                return@withContext null
            }

            val ttsResponse = json.decodeFromString(TtsResponse.serializer(), body)
            val audioBase64 = ttsResponse.choices.firstOrNull()
                ?.message?.audio?.data

            if (audioBase64.isNullOrBlank()) {
                Timber.tag(TAG).e("TTS response missing audio data")
                return@withContext null
            }

            Base64.decode(audioBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "TTS synthesis failed")
            null
        }
    }
}
