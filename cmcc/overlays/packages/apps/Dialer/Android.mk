LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

RES_DIRS := $(call all-subdir-named-dirs,res,.)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(RES_DIRS))

LOCAL_PACKAGE_NAME := DialerCMCCOverlays

LOCAL_SDK_VERSION := current

LOCAL_AAPT_FLAGS += --auto-add-overlay

include $(BUILD_RRO_PACKAGE)
