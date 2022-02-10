LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := libdm_sdk_test
LOCAL_STATIC_JAVA_LIBRARIES += android-async-http
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += httpclient_lib

LOCAL_PRODUCT_MODULE := true
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_REQUIRED_MODULES := libaes-jni.so

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := OpManager

LOCAL_CERTIFICATE := platform

LOCAL_DEX_PREOPT := false

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

#########################
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES :=libdm_sdk_test:libs/dm_sdk_v3.0.0_debug_jdk1.8.jar

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES +=android-async-http:libs/android-async-http-1.4.10.jar

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES +=httpclient_lib:libs/httpclient-4.5.8.jar

include $(BUILD_MULTI_PREBUILT)

#########################
include $(CLEAR_VARS)

LOCAL_MODULE := libaes-jni.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PRODUCT_MODULE := true

ifeq ($(strip $(TARGET_ARCH)),arm64)
LOCAL_SRC_FILES :=libs/arm64-v8a/libaes-jni.so
else ifeq ($(strip $(TARGET_ARCH)),arm)
LOCAL_SRC_FILES :=libs/armeabi-v7a/libaes-jni.so
else ifeq ($(strip $(TARGET_ARCH)),x86)
LOCAL_SRC_FILES :=libs/x86/libaes-jni.so
else ifeq ($(strip $(TARGET_ARCH)),x86_64)
LOCAL_SRC_FILES :=libs/x86_64/libaes-jni.so
endif

LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)

##################
include $(call all-makefiles-under,$(LOCAL_PATH))
