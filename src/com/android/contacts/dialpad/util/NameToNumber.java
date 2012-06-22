/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package com.android.contacts.dialpad.util;

/**
 * Default implements of normalization for alphabet search.
 * Other languages need additional work for search may inherite this and overriding convert function.
 */
public class NameToNumber {
    protected String t9Chars;
    protected String t9Digits;

    // Work is based t9 characters and digits map.
    public NameToNumber(final String t9Chars, final String t9Digits) {
        this.t9Chars = t9Chars;
        this.t9Digits = t9Digits;
    }

    // Copied from https://github.com/CyanogenMod/android_packages_apps_Contacts/commit/63a531957818d631e957e8e0157d45298906e3fb
    public String convert(final String name) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++){
            int pos = t9Chars.indexOf(Character.toLowerCase(name.charAt(i)));
            if (pos == -1) {
                pos = 0;
            }
            sb.append(t9Digits.charAt(pos));
        }
        return sb.toString();
    }
}
