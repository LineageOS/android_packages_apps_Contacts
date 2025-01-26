/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.util;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

/**
 * This class wraps several PhoneNumberUtil calls and TelephonyManager calls. Some of them are
 * the same as the ones in the framework's code base. We can remove those once they are part of
 * the public API.
 */
public class PhoneNumberHelper {

    /**
     * Determines if the specified number is actually a URI (i.e. a SIP address) rather than a
     * regular PSTN phone number, based on whether or not the number contains an "@" character.
     *
     * @param number Phone number
     * @return true if number contains @
     *
     * TODO: Remove if PhoneNumberUtils.isUriNumber(String number) is made public.
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /** Returns true if the given string is dialable by the user from Phone/Dialer app. */
    public static boolean isDialablePhoneNumber(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }

        for (int i = 0, count = str.length(); i < count; i++) {
            if (!(PhoneNumberUtils.isDialable(str.charAt(i))
                || str.charAt(i) == ' '
                || str.charAt(i) == '-'
                || str.charAt(i) == '('
                || str.charAt(i) == ')'
                || str.charAt(i) == '.'
                || str.charAt(i) == '/')) {
                return false;
            }
        }
        return true;
    }
}
