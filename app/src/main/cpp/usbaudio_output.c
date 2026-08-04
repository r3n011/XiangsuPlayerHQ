/*
 * usbaudio_output.c
 *
 * USB 音频输出（等时 OUT 传输）JNI 实现
 *
 * 通过 Android UsbDeviceConnection 的文件描述符包装为 libusb 设备，
 * 找到 USB 音频流的等时 OUT 端点，把来自 Java 层的 PCM 数据
 * 以等时传输方式写入 USB DAC。
 *
 * 参考 moriafly/AndroidUsbAudio 示例：
 *   - libusb_wrap_sys_device + 等时 transfer
 *   - 独立事件线程跑 libusb_handle_events_completed（必选，否则回调永远不触发）
 *   - 优先非 0 alt setting（许多 DAC 仅在 alt != 0 时才开放等时 OUT 带宽）
 */

#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>

#include <libusb.h>

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "UsbAudioOutput"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* 等时传输参数：预提交多个 transfer 保持总线忙碌，降低抖动 */
#define MAX_TRANSFERS       8
#define PACKETS_PER_TRANSFER 10

/* USB 音频类 / vendor-specific 接口 */
#define USB_CLASS_AUDIO     0x01
#define USB_CLASS_VENDOR    0xFF

/* 等时传输端点属性 */
#define ISO_MASK            0x03
#define ISO_TRANSFER        0x01

typedef struct {
    struct libusb_transfer *xfr;
    unsigned char *buffer;
    volatile int in_flight;
} usb_xfer_slot;

static usb_xfer_slot g_slots[MAX_TRANSFERS];
static libusb_context *g_ctx = NULL;
static libusb_device_handle *g_devh = NULL;
static int g_ep_out = 0;          /* 等时 OUT 端点地址 */
static int g_packet_size = 0;     /* wMaxPacketSize（取低 12 位） */
static int g_interface_num = 0;   /* 音频流接口号 */
static int g_altsetting = 0;      /* 选中的 alternate setting */
static volatile int g_running = 0;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static pthread_t g_events_thread = (pthread_t) 0;
static volatile int g_events_quit = 0;

/* 前置声明 */
static void native_stop_internal(JNIEnv *env, jobject thiz);

/* 完成回调：把 transfer 标记为空闲并唤醒等待的写线程 */
static void LIBUSB_CALL xfr_cb(struct libusb_transfer *xfr) {
    int i;

    /* 处理异常：传输失败不崩，仅回收 */
    if (xfr->status != LIBUSB_TRANSFER_COMPLETED) {
        /* 注意：等时包可能单包失败，不影响整体 */
    }

    for (i = 0; i < MAX_TRANSFERS; i++) {
        if (g_slots[i].xfr == xfr) {
            pthread_mutex_lock(&g_lock);
            g_slots[i].in_flight = 0;
            pthread_cond_signal(&g_cond);
            pthread_mutex_unlock(&g_lock);
            break;
        }
    }
}

/* libusb 事件线程：负责驱动回调，没有它 xfr_cb 永远不触发 */
static void *events_thread_func(void *arg) {
    (void) arg;
    libusb_context *ctx = g_ctx;
    struct timeval tv;

    LOGD("events_thread: start");
    while (!g_events_quit) {
        int rc;
        tv.tv_sec = 0;
        tv.tv_usec = 100 * 1000; /* 100ms poll */
        rc = libusb_handle_events_timeout_completed(ctx, &tv, NULL);
        if (rc < 0 && rc != LIBUSB_ERROR_INTERRUPTED) {
            LOGW("events_thread: libusb_handle_events rc=%d (%s)",
                 rc, libusb_error_name(rc));
            /* 出错后短暂 sleep 避免忙等 */
            usleep(10 * 1000);
        }
    }
    LOGD("events_thread: quit");
    return NULL;
}

/*
 * 在当前活动配置中查找音频流的等时 OUT 端点。
 * 策略：优先匹配非 0 altsetting（真正带等时 OUT 带宽的设置），
 *       否则退回到 alt=0。
 */
static int find_iso_out_endpoint(libusb_device *dev) {
    struct libusb_config_descriptor *config = NULL;
    int i, alt, ep;
    int best_iface = -1;
    int best_alt = -1;
    int best_ep = -1;
    int best_pkt = 0;
    int fallback_iface = -1;
    int fallback_alt = -1;
    int fallback_ep = -1;
    int fallback_pkt = 0;

    if (libusb_get_active_config_descriptor(dev, &config) < 0) {
        if (libusb_get_config_descriptor(dev, 0, &config) < 0) {
            LOGE("find_iso_out_endpoint: failed to get config descriptor");
            return -1;
        }
    }

    for (i = 0; i < config->bNumInterfaces; i++) {
        const struct libusb_interface *intf = &config->interface[i];
        for (alt = 0; alt < intf->num_altsetting; alt++) {
            const struct libusb_interface_descriptor *desc = &intf->altsetting[alt];
            if (desc->bInterfaceClass != USB_CLASS_AUDIO &&
                desc->bInterfaceClass != USB_CLASS_VENDOR) {
                continue;
            }
            for (ep = 0; ep < desc->bNumEndpoints; ep++) {
                const struct libusb_endpoint_descriptor *e = &desc->endpoint[ep];
                int dir_out = (e->bEndpointAddress & 0x80) == 0;
                int is_iso  = (e->bmAttributes & ISO_MASK) == ISO_TRANSFER;
                if (dir_out && is_iso) {
                    int pkt = (int) (e->wMaxPacketSize & 0x0FFF);
                    if (alt != 0 && pkt > best_pkt) {
                        best_iface = desc->bInterfaceNumber;
                        best_alt   = alt;
                        best_ep    = e->bEndpointAddress;
                        best_pkt   = pkt;
                    }
                    if (alt == 0 && fallback_ep < 0) {
                        fallback_iface = desc->bInterfaceNumber;
                        fallback_alt   = alt;
                        fallback_ep    = e->bEndpointAddress;
                        fallback_pkt   = pkt;
                    }
                }
            }
        }
    }

    libusb_free_config_descriptor(config);

    if (best_ep >= 0) {
        g_ep_out        = best_ep;
        g_packet_size   = best_pkt;
        g_interface_num = best_iface;
        g_altsetting    = best_alt;
        LOGD("Found ISO OUT (preferred alt!=0): ep=%02xh pkt=%d iface=%d alt=%d",
             g_ep_out, g_packet_size, g_interface_num, g_altsetting);
        return 0;
    }
    if (fallback_ep >= 0) {
        g_ep_out        = fallback_ep;
        g_packet_size   = fallback_pkt;
        g_interface_num = fallback_iface;
        g_altsetting    = fallback_alt;
        LOGD("Found ISO OUT (fallback alt=0): ep=%02xh pkt=%d iface=%d alt=%d",
             g_ep_out, g_packet_size, g_interface_num, g_altsetting);
        return 0;
    }
    LOGE("find_iso_out_endpoint: no ISO OUT endpoint (device may not be an audio DAC)");
    return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_service_usb_UsbAudioOutput_nativeSetup(
        JNIEnv *env, jobject thiz, jint fd) {
    int rc, i;

    if (g_running) {
        LOGD("nativeSetup: already running");
        return JNI_TRUE;
    }

    rc = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    rc = libusb_init(&g_ctx);
    if (rc < 0) {
        LOGE("nativeSetup: libusb_init failed: %d", rc);
        g_ctx = NULL;
        return JNI_FALSE;
    }
    libusb_set_debug(g_ctx, LIBUSB_LOG_LEVEL_WARNING);

    rc = libusb_wrap_sys_device(g_ctx, (intptr_t) fd, &g_devh);
    if (rc < 0 || g_devh == NULL) {
        LOGE("nativeSetup: libusb_wrap_sys_device failed: %d", rc);
        libusb_exit(g_ctx);
        g_ctx = NULL;
        return JNI_FALSE;
    }

    libusb_device *dev = libusb_get_device(g_devh);
    if (find_iso_out_endpoint(dev) < 0) {
        libusb_close(g_devh);
        g_devh = NULL;
        libusb_exit(g_ctx);
        g_ctx = NULL;
        return JNI_FALSE;
    }

    /* 如果内核驱动占用音频接口，尝试分离 */
    rc = libusb_kernel_driver_active(g_devh, g_interface_num);
    if (rc == 1) {
        libusb_detach_kernel_driver(g_devh, g_interface_num);
    }

    rc = libusb_claim_interface(g_devh, g_interface_num);
    if (rc < 0) {
        LOGE("nativeSetup: claim interface %d failed: %s", g_interface_num,
             libusb_error_name(rc));
        libusb_close(g_devh);
        g_devh = NULL;
        libusb_exit(g_ctx);
        g_ctx = NULL;
        return JNI_FALSE;
    }

    /* 若选中了非 0 alt，显式设置（有些设备不做这步不会出声音） */
    if (g_altsetting != 0) {
        rc = libusb_set_interface_alt_setting(g_devh, g_interface_num, g_altsetting);
        if (rc < 0) {
            LOGW("nativeSetup: set alt %d iface %d failed (%s); continuing with alt=0",
                 g_altsetting, g_interface_num, libusb_error_name(rc));
            g_altsetting = 0;
        }
    }

    /* 预分配等时 transfer 池 */
    for (i = 0; i < MAX_TRANSFERS; i++) {
        g_slots[i].buffer = (unsigned char *) calloc(1, (size_t) g_packet_size * PACKETS_PER_TRANSFER);
        if (!g_slots[i].buffer) {
            LOGE("nativeSetup: calloc failed");
            native_stop_internal(env, thiz);
            return JNI_FALSE;
        }
        g_slots[i].xfr = libusb_alloc_transfer(PACKETS_PER_TRANSFER);
        if (!g_slots[i].xfr) {
            LOGE("nativeSetup: libusb_alloc_transfer failed");
            native_stop_internal(env, thiz);
            return JNI_FALSE;
        }
        libusb_fill_iso_transfer(
                g_slots[i].xfr, g_devh, g_ep_out, g_slots[i].buffer,
                g_packet_size * PACKETS_PER_TRANSFER, PACKETS_PER_TRANSFER,
                xfr_cb, NULL, 500);
        libusb_set_iso_packet_lengths(g_slots[i].xfr, g_packet_size);
        g_slots[i].in_flight = 0;
    }

    /* 启动事件线程（不跑 handle_events 的话 libusb 回调不会触发） */
    g_events_quit = 0;
    rc = pthread_create(&g_events_thread, NULL, events_thread_func, NULL);
    if (rc != 0) {
        LOGE("nativeSetup: pthread_create failed: %d", rc);
        native_stop_internal(env, thiz);
        return JNI_FALSE;
    }

    g_running = 1;
    LOGD("USB audio output ready: ep=%02xh pkt=%d iface=%d alt=%d xfers=%d",
         g_ep_out, g_packet_size, g_interface_num, g_altsetting, MAX_TRANSFERS);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_service_usb_UsbAudioOutput_nativeWritePcm(
        JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize len;
    jbyte *bytes;
    int total_written = 0;
    int xfr_capacity;

    if (!g_running || !g_devh) {
        return JNI_FALSE;
    }

    len = (*env)->GetArrayLength(env, data);
    if (len <= 0) return JNI_FALSE;
    bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return JNI_FALSE;

    xfr_capacity = g_packet_size * PACKETS_PER_TRANSFER;

    while (total_written < len && g_running) {
        int idx = -1, i;
        struct libusb_transfer *xfr;
        unsigned char *buf;
        int chunk;

        pthread_mutex_lock(&g_lock);
        for (i = 0; i < MAX_TRANSFERS; i++) {
            if (!g_slots[i].in_flight) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            struct timespec ts;
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_nsec += 200L * 1000L * 1000L; /* 最多等 200ms */
            if (ts.tv_nsec >= 1000000000L) {
                ts.tv_sec++;
                ts.tv_nsec -= 1000000000L;
            }
            pthread_cond_timedwait(&g_cond, &g_lock, &ts);
            pthread_mutex_unlock(&g_lock);
            if (!g_running) break;
            continue;
        }
        g_slots[idx].in_flight = 1;
        xfr = g_slots[idx].xfr;
        buf = g_slots[idx].buffer;
        pthread_mutex_unlock(&g_lock);

        chunk = len - total_written;
        if (chunk > xfr_capacity) chunk = xfr_capacity;

        /* 不足一包时补零（静音），保证包长稳定 */
        memset(buf, 0, (size_t) xfr_capacity);
        memcpy(buf, bytes + total_written, (size_t) chunk);
        total_written += chunk;

        if (libusb_submit_transfer(xfr) < 0) {
            LOGE("nativeWritePcm: libusb_submit_transfer failed; abort frame");
            pthread_mutex_lock(&g_lock);
            g_slots[idx].in_flight = 0;
            pthread_cond_signal(&g_cond);
            pthread_mutex_unlock(&g_lock);
            (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
            return JNI_FALSE;
        }
    }

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_service_usb_UsbAudioOutput_nativeIsActive(
        JNIEnv *env, jobject thiz) {
    (void) env; (void) thiz;
    return g_running ? JNI_TRUE : JNI_FALSE;
}

static void native_stop_internal(JNIEnv *env, jobject thiz) {
    int i;
    (void) env; (void) thiz;

    if (!g_running && !g_devh && !g_ctx) return;

    g_running = 0;

    /* 先退出事件线程，再 cancel transfers，避免竞态 */
    g_events_quit = 1;
    if (g_events_thread != (pthread_t) 0) {
        pthread_join(g_events_thread, NULL);
        g_events_thread = (pthread_t) 0;
    }

    /* 取消飞行中的 transfer */
    for (i = 0; i < MAX_TRANSFERS; i++) {
        if (g_slots[i].xfr && g_slots[i].in_flight) {
            libusb_cancel_transfer(g_slots[i].xfr);
            g_slots[i].in_flight = 0;
        }
    }
    /* 再等一小会儿 handle_events 把取消的回调跑完 */
    if (g_ctx != NULL) {
        struct timeval tv = { 0, 50 * 1000 };
        libusb_handle_events_timeout_completed(g_ctx, &tv, NULL);
    }

    /* 释放 transfer 池 */
    for (i = 0; i < MAX_TRANSFERS; i++) {
        if (g_slots[i].xfr) {
            libusb_free_transfer(g_slots[i].xfr);
            g_slots[i].xfr = NULL;
        }
        if (g_slots[i].buffer) {
            free(g_slots[i].buffer);
            g_slots[i].buffer = NULL;
        }
        g_slots[i].in_flight = 0;
    }

    if (g_devh) {
        if (g_altsetting != 0) {
            libusb_set_interface_alt_setting(g_devh, g_interface_num, 0);
        }
        libusb_release_interface(g_devh, g_interface_num);
        libusb_close(g_devh);
        g_devh = NULL;
    }
    if (g_ctx) {
        libusb_exit(g_ctx);
        g_ctx = NULL;
    }

    g_ep_out = 0;
    g_packet_size = 0;
    g_interface_num = 0;
    g_altsetting = 0;

    pthread_mutex_lock(&g_lock);
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);

    LOGD("USB audio output stopped");
}

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_service_usb_UsbAudioOutput_nativeStop(
        JNIEnv *env, jobject thiz) {
    native_stop_internal(env, thiz);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_theveloper_pixelplay_data_service_usb_UsbAudioOutput_nativeClose(
        JNIEnv *env, jobject thiz) {
    native_stop_internal(env, thiz);
    return JNI_TRUE;
}
