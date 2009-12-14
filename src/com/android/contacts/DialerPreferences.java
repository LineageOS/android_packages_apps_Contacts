/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.Intent;
import java.util.List;
import java.util.ArrayList;
import android.content.pm.ResolveInfo;

import android.util.Log;

public class DialerPreferences extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "DialerPreferences";

    private ListPreference mVMButton;
    private ListPreference mVMHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.dialer_preferences);

        mVMButton = (ListPreference) findPreference("vm_button");
        mVMHandler = (ListPreference) findPreference("vm_handler");

        mVMButton.setOnPreferenceChangeListener(this);
        mVMHandler.setOnPreferenceChangeListener(this);

        loadHandlers();

        updatePrefs(mVMButton, mVMButton.getValue());
        updatePrefs(mVMHandler, mVMHandler.getValue());
    }

    public boolean onPreferenceChange (Preference preference, Object newValue) {
        updatePrefs(preference, newValue);
        return true;
    }

    private void updatePrefs(Preference preference, Object newValue) {
        ListPreference p = (ListPreference) findPreference(preference.getKey());
        try {
            p.setSummary(p.getEntries()[p.findIndexOfValue((String) newValue)]);
        } catch (ArrayIndexOutOfBoundsException e) {
            p.setValue("0");
            updatePrefs(p, p.getValue());
        }
    }

    private void loadHandlers () {
        final PackageManager packageManager = getPackageManager();
        String[] vmHandlers = getResources().getStringArray(R.array.vm_handlers);
        Intent intent;
        ComponentName component;
        List<String> entries = new ArrayList<String>();
        List<String> entryValues = new ArrayList<String>();
        
        entries.add("None (Dial Voicemail Number)");
        entryValues.add("0");

        for (String s : vmHandlers) {
            String [] cmp = s.split("/");
            intent = new Intent(Intent.ACTION_MAIN);
            component = new ComponentName(cmp[1], cmp[1] + cmp[2]);
            intent.setComponent(component);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            List<ResolveInfo> list = 
                packageManager.queryIntentActivities(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() > 0) {
                entries.add(cmp[0]);
                entryValues.add(cmp[1] + "/" + cmp[2]);
            }
        }
        String[] entriesArray = entries.toArray(new String[0]);
        mVMHandler.setEntries(entriesArray);
        String[] entryValuesArray = entryValues.toArray(new String[0]);
        mVMHandler.setEntryValues(entryValuesArray);
    }
}