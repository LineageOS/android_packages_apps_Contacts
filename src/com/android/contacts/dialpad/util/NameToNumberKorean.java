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
 * @author barami
 * Implementation for Korean normalization.
 * This will change Hangul character to number by Choseong(Korean word of initial character).
 */
public class NameToNumberKorean extends NameToNumber {
    // Hangul Chosung (Initial letters of Hangul).
    // Note : Don't change order of initial alphabets. index will be used to calculate.
    private static final String HANGUL_INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final int UNICODE_HANGUL_START = 0xAC00;
    private static final int UNICODE_HANGUL_END = 0xD7AF;
    private static int buffer = 0;

    public NameToNumberKorean(String t9Chars, String t9Digits) {
        super(t9Chars, t9Digits);
        // TODO Auto-generated constructor stub
    }

    @Override
    public String convert(String name) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        // i will make using unicode codepoint.
        for (int i = 0; i < len; i++){
            buffer = name.codePointAt(i);

            int pos;
            if (buffer >= UNICODE_HANGUL_START && buffer <= UNICODE_HANGUL_END) {
                // number of initial character = (codepoint - 0xAC00) / (21 * 28)
                buffer = (buffer - UNICODE_HANGUL_START) / 588;
                pos = t9Chars.indexOf(HANGUL_INITIALS.charAt(buffer));
            } else {
                pos = t9Chars.indexOf(Character.toLowerCase(buffer));
            }

            if (pos == -1) {
                pos = 0;
            }

            sb.append(t9Digits.charAt(pos));
        }
        return sb.toString();
    }
}
