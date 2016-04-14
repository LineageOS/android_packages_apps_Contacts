#
# Copyright (C) 2016 The CyanogenMod Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

LOCAL_STATIC_JAVA_LIBRARIES := espresso \
    contacts-ub-uiautomator

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Notice that we don't have to include the src files of Contacts because, by
# running the tests using an instrumentation targeting Contacts, we
# automatically get all of its classes loaded into our environment.

LOCAL_PACKAGE_NAME := ContactsAndroidTests

LOCAL_INSTRUMENTATION_FOR := Contacts

LOCAL_CERTIFICATE := shared

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
        espresso:../../../../prebuilts/misc/common/android-support-test/espresso-core.jar \
        contacts-ub-uiautomator:../../../../prebuilts/misc/common/ub-uiautomator/ub-uiautomator.jar

include $(BUILD_MULTI_PREBUILT)
