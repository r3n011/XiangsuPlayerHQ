/*
 * aaudio_output.c
 *
 * AAudio 音频输出 JNI 实现（Android O+ 低延迟原生音频）
 *
 * 与 Media3 1.10.1 的 AudioOutput 接口对接：
 *   - write 语义与 AudioTrack.write(WRITE_NON_BLOCKING) 兼容：
 *     AAudioStream_write 阻塞至多 AAUDIO_WRITE_TIMEOUT_MS，返回实际写入字节数，
 *     未写满时调用方（DefaultAudioSink）会背压重试。
 *   - AAudio 会自动做采样率 / 格式(PCM16<->FLOAT) / 声道(up/downmix) 转换，
 *     因此直接按 Media3 输出的采样率/声道/编码创建流即可。
 *   - 位置报告：AAudioStream_getFramesRead（输出流 = 已播放帧数）。
 *
 * 注意：AAudio 没有应用层音量 API，音量系数在写入时对样本缩放实现。
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <android/log.h>

#include <aaudio/AAudio.h>

#define LOG_TAG "AaudioOutput"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* 单次 write 最多阻塞的时间（毫秒），避免卡死 Media3 播放线程 */
#define AAUDIO_WRITE_TIMEOUT_MS 200

typedef struct {
    AAudioStream *stream;
    float volume;
    int32_t sample_rate;
    int32_t channels;
    aaudio_format_t format;
    int32_t bytes_per_frame;
    /* flush 时刻的 framesRead 基准，flush 后位置从 0 开始计数 */
    int64_t flush_base_frames;
    int started;
} aaudio_output;

/* 阻塞等待流进入指定状态（含目标状态），超时返回 -1 */
static int wait_for_state(AAudioStream *stream, aaudio_stream_state_t current,
                          aaudio_stream_state_t target) {
    aaudio_stream_state_t next = current;
    int64_t timeout_ns = 500 * 1000 * 1000LL; /* 500ms */
    aaudio_result_t rc = AAudioStream_waitForStateChange(stream, next, &next, timeout_ns);
    while (rc == AAUDIO_OK && next != target) {
        if (next == AAUDIO_STREAM_STATE_DISCONNECTED) break;
        timeout_ns = 500 * 1000 * 1000LL;
        rc = AAudioStream_waitForStateChange(stream, next, &next, timeout_ns);
    }
    return (rc == AAUDIO_OK && next == target) ? 0 : -1;
}

static aaudio_output *get_handle(JNIEnv *env, jlong handle) {
    return (aaudio_output *) handle;
}

/* 将整块字节按 volume 缩放（float 直接乘，I16 乘后截断） */
static void scale_samples(uint8_t *data, int32_t bytes, int bytes_per_frame,
                          float volume) {
    int i;
    if (bytes_per_frame == 4) { /* float32 */
        int n = bytes / 4;
        float *p = (float *) data;
        for (i = 0; i < n; i++) p[i] *= volume;
    } else if (bytes_per_frame == 2) { /* int16 */
        int n = bytes / 2;
        short *p = (short *) data;
        for (i = 0; i < n; i++) {
            float v = (float) p[i] * volume;
            if (v > 32767.0f) v = 32767.0f;
            else if (v < -32768.0f) v = -32768.0f;
            p[i] = (short) v;
        }
    }
}

JNIEXPORT jlong JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeCreate(
        JNIEnv *env, jclass clazz, jint sampleRate, jint channels, jint format) {
    (void) env; (void) clazz;
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    aaudio_result_t rc;
    aaudio_output *out = NULL;

    if (sampleRate <= 0 || channels <= 0 || channels > 8) {
        LOGE("nativeCreate: invalid params rate=%d ch=%d", sampleRate, channels);
        return 0;
    }

    rc = AAudio_createStreamBuilder(&builder);
    if (rc != AAUDIO_OK || builder == NULL) {
        LOGE("nativeCreate: AAudio_createStreamBuilder failed: %d", rc);
        return 0;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setFormat(builder,
            format == 1 ? AAUDIO_FORMAT_PCM_FLOAT : AAUDIO_FORMAT_PCM_I16);
    /* 音乐播放：不追求极低延迟，让系统选择稳定的大缓冲 deepbuffer 通道 */
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    /* setUsage/setContentType 为 API 28 引入，且默认值即为 MEDIA/MUSIC，无需设置 */

    rc = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (rc != AAUDIO_OK || stream == NULL) {
        LOGE("nativeCreate: openStream failed: %d (%s)", rc, AAudio_convertResultToText(rc));
        return 0;
    }

    out = (aaudio_output *) calloc(1, sizeof(aaudio_output));
    if (!out) {
        AAudioStream_close(stream);
        return 0;
    }
    out->stream = stream;
    out->volume = 1.0f;
    out->sample_rate = AAudioStream_getSampleRate(stream);
    out->channels = AAudioStream_getChannelCount(stream);
    out->format = AAudioStream_getFormat(stream);
    /* NDK 28 头文件已移除 AAudioStream_getBytesPerFrame，按格式计算：
     * I16=2 字节/样本，FLOAT/I32=4 字节/样本，I24_PACKED=3 字节/样本。 */
    out->bytes_per_frame = AAudioStream_getSamplesPerFrame(stream);
    switch (out->format) {
        case AAUDIO_FORMAT_PCM_FLOAT:
        case AAUDIO_FORMAT_PCM_I32:
            out->bytes_per_frame *= 4;
            break;
        case AAUDIO_FORMAT_PCM_I24_PACKED:
            out->bytes_per_frame *= 3;
            break;
        case AAUDIO_FORMAT_PCM_I16:
        default:
            out->bytes_per_frame *= 2;
            break;
    }
    out->flush_base_frames = 0;
    out->started = 0;

    LOGD("nativeCreate: stream ok rate=%d ch=%d fmt=%d bpf=%d cap=%lld",
         out->sample_rate, out->channels, (int) out->format, out->bytes_per_frame,
         (long long) AAudioStream_getBufferCapacityInFrames(stream));
    return (jlong) (intptr_t) out;
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeStart(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return -1;
    if (out->started) return 0;
    aaudio_result_t rc = AAudioStream_requestStart(out->stream);
    if (rc == AAUDIO_OK) {
        aaudio_stream_state_t cur = AAudioStream_getState(out->stream);
        wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_STARTED);
        out->started = 1;
    } else {
        LOGE("nativeStart: requestStart failed: %d (%s)", rc, AAudio_convertResultToText(rc));
    }
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativePause(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return -1;
    /* 按实际流状态判断而不是依赖 out->started：
     * nativeStart 在途（STARTING/RUNNING 但 out->started 尚未置位）时也必须暂停/停止，
     * 否则暂停会静默返回、流随后照样 STARTED 出声。 */
    aaudio_stream_state_t st = AAudioStream_getState(out->stream);
    if (st == AAUDIO_STREAM_STATE_UNINITIALIZED || st == AAUDIO_STREAM_STATE_UNKNOWN ||
        st == AAUDIO_STREAM_STATE_OPEN || st == AAUDIO_STREAM_STATE_STOPPING ||
        st == AAUDIO_STREAM_STATE_STOPPED || st == AAUDIO_STREAM_STATE_CLOSING ||
        st == AAUDIO_STREAM_STATE_CLOSED || st == AAUDIO_STREAM_STATE_DISCONNECTED) {
        return 0; /* 未运行 / 已停止 / 已关闭，无需暂停 */
    }
    /* NDK 28 头文件只保留异步版 requestPause + waitForStateChange */
    aaudio_result_t rc = AAudioStream_requestPause(out->stream);
    if (rc == AAUDIO_ERROR_INVALID_STATE) {
        /* 流正处于 STARTING/RESUMING 等无法暂停的状态（写后立即暂停的竞态）：
         * 回退 requestStop 保证一定静音，否则 pause 静默失败会一直出声。 */
        rc = AAudioStream_requestStop(out->stream);
        if (rc == AAUDIO_OK) {
            aaudio_stream_state_t cur = AAudioStream_getState(out->stream);
            wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_STOPPED);
            out->started = 0;
        } else {
            LOGE("nativePause: requestStop fallback failed: %d (%s)",
                 rc, AAudio_convertResultToText(rc));
        }
        return rc;
    }
    if (rc == AAUDIO_OK) {
        aaudio_stream_state_t cur = AAudioStream_getState(out->stream);
        wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_PAUSED);
        out->started = 0;
    }
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeFlush(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return -1;
    /* flush 仅对非运行态有效：先 requestPause 等 PAUSED 再 requestFlush。
     * NDK 28 头文件只保留异步版 request* + waitForStateChange。 */
    aaudio_result_t rc = AAudioStream_requestPause(out->stream);
    if (rc != AAUDIO_OK && rc != AAUDIO_ERROR_INVALID_STATE) {
        LOGE("nativeFlush: requestPause failed: %d", rc);
        return rc;
    }
    aaudio_stream_state_t cur = AAudioStream_getState(out->stream);
    wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_PAUSED);
    rc = AAudioStream_requestFlush(out->stream);
    if (rc == AAUDIO_OK) {
        cur = AAudioStream_getState(out->stream);
        wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_FLUSHED);
    }
    out->started = 0;
    /* flush 后 framesRead 会继续增长，记录基准使位置从 0 起算 */
    out->flush_base_frames = AAudioStream_getFramesRead(out->stream);
    LOGD("nativeFlush: flush rc=%d base=%lld", rc, (long long) out->flush_base_frames);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeStop(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return -1;
    aaudio_result_t rc = AAudioStream_requestStop(out->stream);
    if (rc == AAUDIO_OK) {
        aaudio_stream_state_t cur = AAudioStream_getState(out->stream);
        wait_for_state(out->stream, cur, AAUDIO_STREAM_STATE_STOPPED);
        out->started = 0;
        out->flush_base_frames = AAudioStream_getFramesRead(out->stream);
    }
    return rc;
}

/*
 * 播放中停滞自愈：仅重启流（requestStop → requestStart），
 * 不调整 flush_base_frames 基准！
 *
 * 关键：DefaultAudioSink 的 writtenFrames 计数不会因我们的重启而清零，
 * EOS 判定（isEnded = framesRead >= writtenFrames）依赖两者基准一致。
 * 若在这里重定 flush_base（像 nativeStop 那样），position 会突然相对变小，
 * hasPendingData 永远为 true → EOS 永远无法送达 → 歌曲播完卡死 10s。
 * 重启后 framesRead 继续累计，position 保持连续，尾部缓冲被消费后
 * framesRead 到达 writtenFrames，EOS 正常送达，播放器自动切下一首。
 */
JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeRestart(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return -1;

    aaudio_stream_state_t st = AAudioStream_getState(out->stream);
    if (st != AAUDIO_STREAM_STATE_STOPPED && st != AAUDIO_STREAM_STATE_STOPPING) {
        aaudio_result_t rc = AAudioStream_requestStop(out->stream);
        if (rc != AAUDIO_OK && rc != AAUDIO_ERROR_INVALID_STATE) {
            LOGE("nativeRestart: requestStop failed: %d (%s)", rc, AAudio_convertResultToText(rc));
            return rc;
        }
        if (rc == AAUDIO_OK) {
            st = AAudioStream_getState(out->stream);
            wait_for_state(out->stream, st, AAUDIO_STREAM_STATE_STOPPED);
        }
    }

    aaudio_result_t rc = AAudioStream_requestStart(out->stream);
    if (rc == AAUDIO_OK) {
        st = AAudioStream_getState(out->stream);
        wait_for_state(out->stream, st, AAUDIO_STREAM_STATE_STARTED);
        out->started = 1;
    } else {
        LOGE("nativeRestart: requestStart failed: %d (%s)", rc, AAudio_convertResultToText(rc));
    }
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeRelease(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out) return -1;
    if (out->stream) {
        AAudioStream_requestStop(out->stream);
        AAudioStream_close(out->stream);
        out->stream = NULL;
    }
    free(out);
    return 0;
}

/*
 * 写 PCM。返回本次实际写入的字节数（0 = 缓冲满等待超时），负值 = AAudio 错误码。
 * 与 AudioTrack 的 WRITE_NON_BLOCKING 语义兼容：未消费完时返回部分字节，
 * DefaultAudioSink 会保留剩余 buffer 下次继续写。
 */
JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeWrite(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint size) {
    (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    jbyte *bytes;
    int bytes_per_frame;
    int32_t frames;
    aaudio_result_t rc;

    if (!out || !out->stream) return -1;
    if (size <= 0) return 0;

    bytes_per_frame = out->bytes_per_frame;
    if (bytes_per_frame <= 0) return -1;
    frames = size / bytes_per_frame;
    if (frames <= 0) return 0;

    bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return -1;

    /* 音量缩放（volume != 1.0 时原位缩放，写入前完成） */
    if (out->volume != 1.0f) {
        scale_samples((uint8_t *) (bytes + offset), frames * bytes_per_frame,
                      bytes_per_frame, out->volume);
    }

    rc = AAudioStream_write(out->stream, (const void *) (bytes + offset), frames,
                            (int64_t) AAUDIO_WRITE_TIMEOUT_MS * 1000 * 1000LL);

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);

    if (rc < 0) {
        if (rc == AAUDIO_ERROR_TIMEOUT) return 0;
        if (rc == AAUDIO_ERROR_DISCONNECTED) return 0; /* 设备断连：静默丢弃，由上层重建 */
        LOGE("nativeWrite: AAudioStream_write failed: %d (%s)", rc, AAudio_convertResultToText(rc));
        return rc;
    }
    return rc * bytes_per_frame;
}

JNIEXPORT jlong JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeGetFramesRead(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return 0;
    int64_t read = AAudioStream_getFramesRead(out->stream);
    if (read < 0) read = AAudioStream_getFramesWritten(out->stream);
    if (read < 0) read = 0;
    return (jlong) (read - out->flush_base_frames);
}

JNIEXPORT jint JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeGetSampleRate(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out) return 48000;
    return (jint) out->sample_rate;
}

JNIEXPORT jlong JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeGetBufferCapacityInFrames(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out || !out->stream) return 0;
    return (jlong) AAudioStream_getBufferCapacityInFrames(out->stream);
}

JNIEXPORT void JNICALL
Java_com_theveloper_pixelplay_data_service_audioengine_AaudioNativeOutput_nativeSetVolume(
        JNIEnv *env, jclass clazz, jlong handle, jfloat volume) {
    (void) env; (void) clazz;
    aaudio_output *out = get_handle(env, handle);
    if (!out) return;
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    out->volume = volume;
}
