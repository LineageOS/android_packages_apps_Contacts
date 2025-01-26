/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.contacts.R;

/**
 * Storage for a social status update. Holds a single update.
 * Statuses with timestamps, or with newer timestamps win.
 */
public class DataStatus {
    private String mStatus = null;

    public DataStatus(Cursor cursor) {
        // When creating from cursor row, fill normally
        fromCursor(cursor);
    }

    private void fromCursor(Cursor cursor) {
        mStatus = getString(cursor, Data.STATUS);
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(mStatus);
    }

    private static String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndex(columnName));
    }
}
