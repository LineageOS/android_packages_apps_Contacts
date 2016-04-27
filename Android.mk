LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon

src_dirs := src $(contacts_common_dir)/src \
    $(phone_common_dir)/src \
    $(phone_common_dir)/src-ambient

res_dirs := res $(contacts_common_dir)/res $(phone_common_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res \
    external/uicommon/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages android.support.v7.cardview \
    --extra-packages com.cyanogen.ambient \
    --extra-packages com.cyngn.uicommon

LOCAL_JAVA_LIBRARIES := telephony-common voip-common ims-common
LOCAL_FULL_LIBS_MANIFEST_FILES := $(LOCAL_PATH)/AndroidManifest_cm.xml
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.vcard \
    android-common \
    uicommon \
    guava \
    android-support-v13 \
    android-support-v7-cardview \
    android-support-v7-palette \
    android-support-v4 \
    libphonenumber \
    org.cyanogenmod.platform.sdk \
    contacts-picaso

LOCAL_PACKAGE_NAME := Contacts
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# utilize ContactsCommon's phone-number-based contact-info lookup
CONTACTS_COMMON_LOOKUP_PROVIDER ?= $(LOCAL_PATH)/$(contacts_common_dir)/info_lookup
include $(CONTACTS_COMMON_LOOKUP_PROVIDER)/phonenumber_lookup_provider.mk

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
