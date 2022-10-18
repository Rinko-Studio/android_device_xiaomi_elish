LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := RemovePackages
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_OVERRIDES_PACKAGES := \
   CarrierServices \ # Google CarrierServices package
   Dialer \ # AOSP Dialer
   GoogleDialer \ # Google Dialer
   Stk \ # Sim ToolKit package
   TeleService \ # AOSP TeleService
   Telecom \ # AOSP Telecom package
   TelephonyProvider \ # AOSP TelephonyProvider
   com.google.android.dialer.support \ # GDailer support jar
   messaging # AOSP Messages app

LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := /dev/null
include $(BUILD_PREBUILT)
