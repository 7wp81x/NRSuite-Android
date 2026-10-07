#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "libusb.h"

#define LOG_TAG "NRSuiteUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static pthread_mutex_t g_state_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_state_cond = PTHREAD_COND_INITIALIZER;
static int g_active_transfers = 0;
static int g_closing = 0;

static libusb_context *g_ctx = NULL;
static libusb_device_handle *g_handle = NULL;
static int g_usb_fd = -1;
static int g_interface = -1;
static int g_ep_in = -1;
static int g_ep_out = -1;
static int g_open = 0;

static int begin_io(void) {
    pthread_mutex_lock(&g_state_lock);
    if (!g_open || g_handle == NULL || g_closing) {
        pthread_mutex_unlock(&g_state_lock);
        return 0;
    }
    g_active_transfers++;
    pthread_mutex_unlock(&g_state_lock);
    return 1;
}

static void end_io(void) {
    pthread_mutex_lock(&g_state_lock);
    if (g_active_transfers > 0) {
        g_active_transfers--;
    }
    if (g_active_transfers == 0) {
        pthread_cond_broadcast(&g_state_cond);
    }
    pthread_mutex_unlock(&g_state_lock);
}

/* Caller must hold g_state_lock. */
static void close_locked(void) {
    if (g_handle == NULL) {
        g_open = 0;
        g_closing = 0;
        return;
    }

    g_closing = 1;
    while (g_active_transfers > 0) {
        pthread_cond_wait(&g_state_cond, &g_state_lock);
    }

    if (g_interface >= 0) {
        libusb_release_interface(g_handle, g_interface);
    }
    libusb_close(g_handle);
    g_handle = NULL;
    if (g_usb_fd >= 0) {
        close(g_usb_fd);
        g_usb_fd = -1;
    }

    // Keep g_ctx alive for the process lifetime, matching PyUSB/espbridge.
    g_interface = -1;
    g_ep_in = -1;
    g_ep_out = -1;
    g_open = 0;
    g_closing = 0;
    pthread_cond_broadcast(&g_state_cond);
}

static int ch34x_ctrl(uint8_t request, uint16_t value, uint16_t index) {
    int rc = libusb_control_transfer(
            g_handle,
            0x40,
            request,
            value,
            index,
            NULL,
            0,
            500);
    LOGD("libusb_control_transfer req=0x%02X val=0x%04X idx=0x%04X -> %d",
         request, value, index, rc);
    return rc;
}

static int raw_ctrl(uint8_t bm_request_type, uint8_t request,
                    uint16_t value, uint16_t index,
                    unsigned char *data, uint16_t length) {
    int rc = libusb_control_transfer(
            g_handle,
            bm_request_type,
            request,
            value,
            index,
            data,
            length,
            500);
    LOGD("libusb_control_transfer bm=0x%02X req=0x%02X val=0x%04X idx=0x%04X len=%u -> %d",
         bm_request_type, request, value, index, length, rc);
    return rc;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_open(
        JNIEnv *env, jobject thiz,
        jint fd, jint interface_number, jint ep_in, jint ep_out) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);

    if (g_open || g_handle != NULL) {
        LOGE("native open called while already open");
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }
    if (fd < 0) {
        LOGE("invalid fd: %d", fd);
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    if (g_ctx == NULL) {
        struct libusb_init_option options[1];
        options[0].option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
        options[0].value.ival = 1;
        int rc = libusb_init_context(&g_ctx, options, 1);
        if (rc != LIBUSB_SUCCESS) {
            LOGE("libusb_init_context failed: %d (%s)", rc, libusb_error_name(rc));
            g_ctx = NULL;
            pthread_mutex_unlock(&g_state_lock);
            return JNI_FALSE;
        }
        LOGI("libusb_init_context(NO_DEVICE_DISCOVERY) -> %d", rc);
    }

    // Termux:API passes the child a duplicated fd via SCM_RIGHTS, not the
    // original UsbDeviceConnection fd. Mirror that with dup(2).
    g_usb_fd = dup(fd);
    if (g_usb_fd < 0) {
        LOGE("dup(fd=%d) failed: errno=%d (%s)", fd, errno, strerror(errno));
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    intptr_t sys_dev = (intptr_t) g_usb_fd;
    int rc = libusb_wrap_sys_device(g_ctx, sys_dev, &g_handle);
    if (rc != LIBUSB_SUCCESS) {
        LOGE("libusb_wrap_sys_device failed: %d (%s)", rc, libusb_error_name(rc));
        close(g_usb_fd);
        g_usb_fd = -1;
        g_handle = NULL;
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    rc = libusb_claim_interface(g_handle, interface_number);
    if (rc != LIBUSB_SUCCESS) {
        LOGE("libusb_claim_interface(%d) failed: %d (%s)",
             interface_number, rc, libusb_error_name(rc));
        libusb_close(g_handle);
        g_handle = NULL;
        if (g_usb_fd >= 0) {
            close(g_usb_fd);
            g_usb_fd = -1;
        }
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    g_interface = interface_number;
    g_ep_in = ep_in;
    g_ep_out = ep_out;

    int clear_rc = libusb_clear_halt(g_handle, (unsigned char) g_ep_in);
    LOGD("libusb_clear_halt(0x%02X) -> %d", g_ep_in, clear_rc);
    clear_rc = libusb_clear_halt(g_handle, (unsigned char) g_ep_out);
    LOGD("libusb_clear_halt(0x%02X) -> %d", g_ep_out, clear_rc);

    g_open = 1;
    g_closing = 0;
    LOGI("native libusb opened fd=%d iface=%d epIn=0x%02X epOut=0x%02X",
         fd, interface_number, ep_in, ep_out);

    pthread_mutex_unlock(&g_state_lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_ch34xInit(
        JNIEnv *env, jobject thiz, jint divisor) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);
    if (!g_open || g_handle == NULL) {
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    g_closing = 1;
    while (g_active_transfers > 0) {
        pthread_cond_wait(&g_state_cond, &g_state_lock);
    }

    int ok = 1;

    ok &= ch34x_ctrl(0xA1, 0x0000, 0x0000) >= 0;
    ok &= ch34x_ctrl(0x9A, 0x1312, (uint16_t) divisor) >= 0;
    ok &= ch34x_ctrl(0x9A, 0x2518, 0x00C3) >= 0;

    // Match the working local Termux/espbridge trace: two reset pulses,
    // then leave DTR asserted / RTS released.
    ok &= ch34x_ctrl(0xA4, 0x009F, 0x0000) >= 0;
    usleep(100000);
    ok &= ch34x_ctrl(0xA4, 0x00FF, 0x0000) >= 0;
    usleep(100000);
    ok &= ch34x_ctrl(0xA4, 0x009F, 0x0000) >= 0;
    usleep(100000);
    ok &= ch34x_ctrl(0xA4, 0x00FF, 0x0000) >= 0;
    usleep(500000);
    ok &= ch34x_ctrl(0xA4, 0x00DF, 0x0000) >= 0;
    usleep(50000);

    g_closing = 0;
    pthread_cond_broadcast(&g_state_cond);
    pthread_mutex_unlock(&g_state_lock);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_cp21xxInit(
        JNIEnv *env, jobject thiz, jint baud_rate) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);
    if (!g_open || g_handle == NULL) {
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    g_closing = 1;
    while (g_active_transfers > 0) {
        pthread_cond_wait(&g_state_cond, &g_state_lock);
    }

    int ok = 1;
    ok &= raw_ctrl(0x41, 0x00, 0x0001, 0x0000, NULL, 0) >= 0;

    uint8_t baud[4] = {
            (uint8_t) (baud_rate & 0xFF),
            (uint8_t) ((baud_rate >> 8) & 0xFF),
            (uint8_t) ((baud_rate >> 16) & 0xFF),
            (uint8_t) ((baud_rate >> 24) & 0xFF),
    };
    ok &= raw_ctrl(0x40, 0x1E, 0x0000, 0x0000, baud, sizeof(baud)) >= 0;
    ok &= raw_ctrl(0x40, 0x03, 0x0800, 0x0000, NULL, 0) >= 0;
    // DTR/RTS both deasserted: normal ESP32 auto-reset idle state.
    ok &= raw_ctrl(0x41, 0x07, 0x0300, 0x0000, NULL, 0) >= 0;
    usleep(50000);

    g_closing = 0;
    pthread_cond_broadcast(&g_state_cond);
    pthread_mutex_unlock(&g_state_lock);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_ftdiInit(
        JNIEnv *env, jobject thiz, jint baud_rate) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);
    if (!g_open || g_handle == NULL) {
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    g_closing = 1;
    while (g_active_transfers > 0) {
        pthread_cond_wait(&g_state_cond, &g_state_lock);
    }

    int ok = 1;
    ok &= raw_ctrl(0x40, 0x00, 0x0000, 0x0000, NULL, 0) >= 0;
    usleep(50000);

    int divisor = 3000000 / (baud_rate > 0 ? baud_rate : 115200);
    if (divisor < 1) divisor = 1;
    if (divisor > 0xFFFF) divisor = 0xFFFF;
    ok &= raw_ctrl(0x40, 0x03, (uint16_t) divisor, 0x0000, NULL, 0) >= 0;
    ok &= raw_ctrl(0x40, 0x04, 0x0008, 0x0000, NULL, 0) >= 0;
    // DTR/RTS both deasserted for the classic ESP32 auto-reset circuit.
    ok &= raw_ctrl(0x40, 0x01, 0x0300, 0x0000, NULL, 0) >= 0;
    usleep(100000);

    g_closing = 0;
    pthread_cond_broadcast(&g_state_cond);
    pthread_mutex_unlock(&g_state_lock);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_read(
        JNIEnv *env, jobject thiz,
        jbyteArray buffer, jint timeout_ms) {
    (void) thiz;

    if (!begin_io()) {
        return -1;
    }

    jsize length = (*env)->GetArrayLength(env, buffer);
    if (length <= 0) {
        end_io();
        return 0;
    }

    jbyte *data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        LOGE("GetByteArrayElements failed");
        end_io();
        return -1;
    }

    int transferred = 0;
    int rc = libusb_bulk_transfer(
            g_handle,
            (unsigned char) g_ep_in,
            (unsigned char *) data,
            (int) length,
            &transferred,
            timeout_ms);

    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);

    // PyUSB treats LIBUSB_ERROR_TIMEOUT as a partial transfer when
    // actual_length > 0. This matters for CH340 PONG responses: the
    // response is exactly one 32-byte max-packet frame, so the kernel
    // may report a bulk-IN timeout with 32 bytes already transferred.
    if (transferred > 0 && (rc == LIBUSB_SUCCESS || rc == LIBUSB_ERROR_TIMEOUT)) {
        LOGD("bulk IN transferred=%d rc=%d (%s)",
             transferred, rc, libusb_error_name(rc));
        end_io();
        return transferred;
    }
    if (rc == LIBUSB_ERROR_TIMEOUT) {
        end_io();
        return -1;
    }

    LOGE("bulk IN failed: %d (%s)", rc, libusb_error_name(rc));
    end_io();
    return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_write(
        JNIEnv *env, jobject thiz,
        jbyteArray buffer, jint timeout_ms) {
    (void) thiz;

    if (!begin_io()) {
        return JNI_FALSE;
    }

    jsize length = (*env)->GetArrayLength(env, buffer);
    if (length <= 0) {
        end_io();
        return JNI_TRUE;
    }

    jbyte *data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        LOGE("GetByteArrayElements failed");
        end_io();
        return JNI_FALSE;
    }

    int transferred = 0;
    int rc = libusb_bulk_transfer(
            g_handle,
            (unsigned char) g_ep_out,
            (unsigned char *) data,
            (int) length,
            &transferred,
            timeout_ms);

    (*env)->ReleaseByteArrayElements(env, buffer, data, JNI_ABORT);

    if (rc == LIBUSB_SUCCESS && transferred == (int) length) {
        LOGD("bulk OUT transferred=%d/%d", transferred, (int) length);
        end_io();
        return JNI_TRUE;
    }

    LOGE("bulk OUT failed/timed out: rc=%d (%s) transferred=%d/%d",
         rc, libusb_error_name(rc), transferred, (int) length);
    end_io();
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_resetEndpoints(
        JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);
    if (!g_open || g_handle == NULL) {
        pthread_mutex_unlock(&g_state_lock);
        return JNI_FALSE;
    }

    // Block new transfers and wait for the reader/writer to finish before
    // clearing the endpoint data toggles.
    g_closing = 1;
    while (g_active_transfers > 0) {
        pthread_cond_wait(&g_state_cond, &g_state_lock);
    }

    int rc_in = libusb_clear_halt(g_handle, (unsigned char) g_ep_in);
    int rc_out = libusb_clear_halt(g_handle, (unsigned char) g_ep_out);
    LOGD("libusb_reset_endpoints in=0x%02X -> %d out=0x%02X -> %d",
         g_ep_in, rc_in, g_ep_out, rc_out);

    g_closing = 0;
    pthread_cond_broadcast(&g_state_cond);
    pthread_mutex_unlock(&g_state_lock);
    return (rc_in == LIBUSB_SUCCESS && rc_out == LIBUSB_SUCCESS)
            ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_swp81x_nrsuite_core_usb_NativeUsbBridge_close(
        JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_state_lock);
    if (g_open || g_handle != NULL) {
        LOGI("native libusb closing");
        close_locked();
    }
    pthread_mutex_unlock(&g_state_lock);
}
