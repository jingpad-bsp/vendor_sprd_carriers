LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)

RES_DIRS := $(call all-subdir-named-dirs,res,.)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(RES_DIRS))

LOCAL_PACKAGE_NAME := SprdDialer_reliance_rro

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform

LOCAL_AAPT_FLAGS += --auto-add-overlay

include $(BUILD_RRO_PACKAGE)
