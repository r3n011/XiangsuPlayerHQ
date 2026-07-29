#include <jni.h>
#include <android/log.h>
#include "miniaudio.h"
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "MiniaudioJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_DECODERS 16

typedef struct {
    ma_decoder decoder;
    int active;
} decoder_entry;

static decoder_entry g_decoders[MAX_DECODERS];

static int find_free_slot() {
    for (int i = 0; i < MAX_DECODERS; i++) {
        if (!g_decoders[i].active) return i;
    }
    return -1;
}

static int get_decoder_slot(jlong handle) {
    int slot = (int)handle;
    if (slot < 0 || slot >= MAX_DECODERS) return -1;
    if (!g_decoders[slot].active) return -1;
    return slot;
}

static jlong native_open(JNIEnv *env, jobject thiz, jstring filePath,
        jint outputFormat, jint outputChannels, jint outputSampleRate) {

    const char *path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) {
        LOGE("Failed to get file path string");
        return -1;
    }

    int slot = find_free_slot();
    if (slot < 0) {
        LOGE("No free decoder slots available");
        (*env)->ReleaseStringUTFChars(env, filePath, path);
        return -1;
    }

    ma_format fmt = (ma_format)outputFormat;
    ma_uint32 channels = (ma_uint32)outputChannels;
    ma_uint32 sampleRate = (ma_uint32)outputSampleRate;

    ma_decoder_config config = ma_decoder_config_init(fmt, channels, sampleRate);

    ma_result result = ma_decoder_init_file(path, &config, &g_decoders[slot].decoder);
    if (result != MA_SUCCESS) {
        LOGE("Failed to open decoder for: %s (error: %d)", path, result);
        (*env)->ReleaseStringUTFChars(env, filePath, path);
        return -1;
    }

    g_decoders[slot].active = 1;
    LOGI("Opened decoder: %s (slot=%d)", path, slot);

    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return (jlong)slot;
}

static jint native_readFrames(JNIEnv *env, jobject thiz, jlong handle,
        jbyteArray buffer, jint frameCount) {

    int slot = get_decoder_slot(handle);
    if (slot < 0) {
        LOGE("Invalid decoder handle: %lld", (long long)handle);
        return -1;
    }

    ma_decoder *decoder = &g_decoders[slot].decoder;

    ma_format outFormat;
    ma_uint32 outChannels, outSampleRate;
    ma_channel channelMap[8];
    ma_result fmtResult = ma_decoder_get_data_format(
            decoder, &outFormat, &outChannels, &outSampleRate, channelMap, sizeof(channelMap));

    if (fmtResult != MA_SUCCESS) {
        outFormat = decoder->outputFormat;
        outChannels = decoder->outputChannels;
        outSampleRate = decoder->outputSampleRate;
    }

    int bytesPerFrame = ma_get_bytes_per_frame(outFormat, outChannels);

    jbyte *outputBuffer = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!outputBuffer) {
        LOGE("Failed to get buffer elements");
        return -1;
    }

    ma_uint64 framesRead = 0;
    ma_result result = ma_decoder_read_pcm_frames(decoder, outputBuffer, (ma_uint64)frameCount, &framesRead);

    (*env)->ReleaseByteArrayElements(env, buffer, outputBuffer, 0);

    if (result != MA_SUCCESS) {
        LOGE("Failed to read frames (error: %d)", result);
        return -1;
    }

    return (jint)(framesRead * bytesPerFrame);
}

static jboolean native_seekToFrame(JNIEnv *env, jobject thiz, jlong handle, jlong frameIndex) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return JNI_FALSE;

    ma_result result = ma_decoder_seek_to_pcm_frame(&g_decoders[slot].decoder, (ma_uint64)frameIndex);
    if (result != MA_SUCCESS) {
        LOGE("Seek failed to frame %lld (error: %d)", (long long)frameIndex, result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static jlong native_getTotalFrames(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return -1;

    ma_uint64 length = 0;
    ma_result result = ma_decoder_get_length_in_pcm_frames(&g_decoders[slot].decoder, &length);
    if (result != MA_SUCCESS) {
        LOGE("Failed to get length (error: %d)", result);
        return -1;
    }
    return (jlong)length;
}

static jlong native_getCursorFrame(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return -1;

    ma_uint64 cursor = 0;
    ma_result result = ma_decoder_get_cursor_in_pcm_frames(&g_decoders[slot].decoder, &cursor);
    if (result != MA_SUCCESS) {
        LOGE("Failed to get cursor (error: %d)", result);
        return -1;
    }
    return (jlong)cursor;
}

static void native_close(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return;

    ma_decoder_uninit(&g_decoders[slot].decoder);
    g_decoders[slot].active = 0;
    LOGI("Closed decoder slot %d", slot);
}

static jint native_getOutputSampleRate(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return 0;

    ma_format outFormat;
    ma_uint32 outChannels, outSampleRate;
    ma_channel channelMap[8];
    ma_result result = ma_decoder_get_data_format(
            &g_decoders[slot].decoder, &outFormat, &outChannels, &outSampleRate, channelMap, sizeof(channelMap));

    if (result == MA_SUCCESS) {
        return (jint)outSampleRate;
    }
    return (jint)g_decoders[slot].decoder.outputSampleRate;
}

static jint native_getOutputChannels(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return 0;

    ma_format outFormat;
    ma_uint32 outChannels, outSampleRate;
    ma_channel channelMap[8];
    ma_result result = ma_decoder_get_data_format(
            &g_decoders[slot].decoder, &outFormat, &outChannels, &outSampleRate, channelMap, sizeof(channelMap));

    if (result == MA_SUCCESS) {
        return (jint)outChannels;
    }
    return (jint)g_decoders[slot].decoder.outputChannels;
}

static jint native_getOutputFormat(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return 0;

    ma_format outFormat;
    ma_uint32 outChannels, outSampleRate;
    ma_channel channelMap[8];
    ma_result result = ma_decoder_get_data_format(
            &g_decoders[slot].decoder, &outFormat, &outChannels, &outSampleRate, channelMap, sizeof(channelMap));

    if (result == MA_SUCCESS) {
        return (jint)outFormat;
    }
    return (jint)g_decoders[slot].decoder.outputFormat;
}

static jint native_getBytesPerFrame(JNIEnv *env, jobject thiz, jlong handle) {
    int slot = get_decoder_slot(handle);
    if (slot < 0) return 0;

    ma_format outFormat;
    ma_uint32 outChannels, outSampleRate;
    ma_channel channelMap[8];
    ma_result result = ma_decoder_get_data_format(
            &g_decoders[slot].decoder, &outFormat, &outChannels, &outSampleRate, channelMap, sizeof(channelMap));

    if (result != MA_SUCCESS) {
        outFormat = g_decoders[slot].decoder.outputFormat;
        outChannels = g_decoders[slot].decoder.outputChannels;
    }

    return (jint)ma_get_bytes_per_frame(outFormat, outChannels);
}

static JNINativeMethod g_methods[] = {
    {"nativeOpen",             "(Ljava/lang/String;III)J",  (void*)native_open},
    {"nativeReadFrames",       "(J[BI)I",                   (void*)native_readFrames},
    {"nativeSeekToFrame",      "(JJ)Z",                     (void*)native_seekToFrame},
    {"nativeGetTotalFrames",   "(J)J",                      (void*)native_getTotalFrames},
    {"nativeGetCursorFrame",   "(J)J",                      (void*)native_getCursorFrame},
    {"nativeClose",            "(J)V",                      (void*)native_close},
    {"nativeGetOutputSampleRate",  "(J)I",                  (void*)native_getOutputSampleRate},
    {"nativeGetOutputChannels",    "(J)I",                  (void*)native_getOutputChannels},
    {"nativeGetOutputFormat",      "(J)I",                  (void*)native_getOutputFormat},
    {"nativeGetBytesPerFrame",     "(J)I",                  (void*)native_getBytesPerFrame},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = (*env)->FindClass(env, "com/theveloper/pixelplay/utils/MiniaudioDecoder$Companion");
    if (clazz == NULL) {
        LOGE("Failed to find MiniaudioDecoder$Companion class");
        (*env)->ExceptionClear(env);
        return JNI_ERR;
    }

    int methodCount = sizeof(g_methods) / sizeof(g_methods[0]);
    if ((*env)->RegisterNatives(env, clazz, g_methods, methodCount) != JNI_OK) {
        LOGE("Failed to register native methods");
        return JNI_ERR;
    }

    LOGI("miniaudio_jni loaded successfully, %d methods registered", methodCount);
    return JNI_VERSION_1_6;
}