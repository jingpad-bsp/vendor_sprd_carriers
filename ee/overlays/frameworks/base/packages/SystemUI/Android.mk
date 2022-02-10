LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res-keyguard

LOCAL_PACKAGE_NAME := SystemUI_ee_rro

LOCAL_SDK_VERSION := current

include $(BUILD_RRO_PACKAGE)