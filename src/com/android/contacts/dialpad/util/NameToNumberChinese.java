
package com.android.contacts.dialpad.util;

import com.android.contacts.util.HanziToPinyin;

/**
 * @author Barami Implementation for Korean normalization. This will change
 *         Hangul character to number by Choseong(Korean word of initial
 *         character).
 */
public class NameToNumberChinese extends NameToNumber {
    public NameToNumberChinese(String t9Chars, String t9Digits) {
        super(t9Chars, t9Digits);
        // TODO Auto-generated constructor stub
    }

    @Override
    public String convert(String name) {
        String hzFirstPinYin = HanziToPinyin.getInstance().getFirstPinYin(name).toLowerCase();
        StringBuilder sb = new StringBuilder(name.length());
        int iTotal = hzFirstPinYin.length();
        for (int i = 0; i < iTotal; i++) {
            int pos = t9Chars.indexOf(hzFirstPinYin.charAt(i));
            if (-1 == pos) {
                pos = 0;
            }
            sb.append(t9Digits.charAt(pos));
        }
        return sb.toString();
    }
}
