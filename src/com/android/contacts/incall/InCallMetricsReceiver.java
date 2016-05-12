/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.contacts.incall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

public class InCallMetricsReceiver extends BroadcastReceiver {
    private static final String CONTACT_AUTO_MERGE_KEY_RAW_IDS = "RAW_IDS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        if (action.equals("com.android.contacts.incall.CONTACTS_AUTO_MERGE")) {
            String rawIds = intent.getStringExtra(CONTACT_AUTO_MERGE_KEY_RAW_IDS);
            if (rawIds != null) {
                InCallMetricsHelper.increaseContactAutoMergeCount(context, rawIds);
            }
        }
    }
}
