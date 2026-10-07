LOCAL_PATH := $(call my-dir)

# Vendored libusb 1.0.27 source. Keeping it inside the app repo makes
# GitHub Actions and clean checkouts self-contained.
LIBUSB_REL := libusb
LIBUSB_ROOT := $(LOCAL_PATH)/$(LIBUSB_REL)

# ---------------------------------------------------------------------------
# libusb
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE := usb1.0
LOCAL_SRC_FILES := \
    $(LIBUSB_REL)/libusb/core.c \
    $(LIBUSB_REL)/libusb/descriptor.c \
    $(LIBUSB_REL)/libusb/hotplug.c \
    $(LIBUSB_REL)/libusb/io.c \
    $(LIBUSB_REL)/libusb/sync.c \
    $(LIBUSB_REL)/libusb/strerror.c \
    $(LIBUSB_REL)/libusb/os/linux_usbfs.c \
    $(LIBUSB_REL)/libusb/os/events_posix.c \
    $(LIBUSB_REL)/libusb/os/threads_posix.c \
    $(LIBUSB_REL)/libusb/os/linux_netlink.c

LOCAL_C_INCLUDES := \
    $(LIBUSB_ROOT)/android \
    $(LIBUSB_ROOT)/libusb \
    $(LIBUSB_ROOT)/libusb/os

LOCAL_CFLAGS := -fvisibility=hidden -pthread -Wno-unused-but-set-variable
LOCAL_LDLIBS := -llog

include $(BUILD_STATIC_LIBRARY)

# ---------------------------------------------------------------------------
# JNI bridge
# ---------------------------------------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE := nrsuiteusb
LOCAL_SRC_FILES := native_usb_bridge.c
LOCAL_C_INCLUDES := $(LIBUSB_ROOT)/libusb
LOCAL_CFLAGS := -pthread -Wall -Wextra -Wno-unused-parameter
LOCAL_STATIC_LIBRARIES := usb1.0
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
