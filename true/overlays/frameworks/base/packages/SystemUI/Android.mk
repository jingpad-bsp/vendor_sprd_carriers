LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res-keyguard $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := SystemUI_true_rro

LOCAL_SDK_VERSION := current

LOCAL_AAPT_FLAGS += --auto-add-overlay

include $(BUILD_RRO_PACKAGE)