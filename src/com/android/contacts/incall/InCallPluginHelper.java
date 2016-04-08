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

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.incall.extension.InCallContactInfo;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.results.InstalledPluginsResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class InCallPluginHelper extends CallMethodHelper {
    private static final String TAG = InCallPluginHelper.class.getSimpleName();

    protected static synchronized InCallPluginHelper getInstance() {
        if (sInstance == null) {
            sInstance = new InCallPluginHelper();
        }
        return (InCallPluginHelper) sInstance;
    }

    public static void init(Context context) {
        InCallPluginHelper helper = getInstance();
        helper.mContext = context;
        helper.mClient = AmbientConnection.CLIENT.get(context);
        helper.mInCallApi = InCallServices.getInstance();
        helper.mMainHandler = new Handler(context.getMainLooper());

        refresh();
    }

    public static void refresh() {
        updateCallPlugins();
    }

    public static void refreshDynamicItems() {
        HashMap<ResultCallback, PendingResult> apiCallbacks = new HashMap<ResultCallback,
                PendingResult>();
        for (ComponentName cn : mCallMethodInfos.keySet()) {
            getCallMethodAuthenticated(cn, apiCallbacks);
            getCallMethodAccountHandle(cn, apiCallbacks);
        }
        executeAll(apiCallbacks);
    }

    public static void refreshPendingIntents(InCallContactInfo contactInfo) {
        for (ComponentName cn : mCallMethodInfos.keySet()) {
            getInviteIntent(cn, contactInfo);
            getDirectorySearchIntent(cn, contactInfo.mLookupUri);
        }
    }

    protected static void updateCallPlugins() {
        if (DEBUG) Log.d(TAG, "+++updateCallPlugins");
        getInstance().mInCallApi.getInstalledPlugins(getInstance().mClient)
                .setResultCallback(new ResultCallback<InstalledPluginsResult>() {
                    @Override
                    public void onResult(InstalledPluginsResult installedPluginsResult) {
                        // got installed components
                        mInstalledPlugins = installedPluginsResult.components;

                        synchronized (mCallMethodInfos) {
                            mCallMethodInfos.clear();
                        }

                        if (mInstalledPlugins == null || mInstalledPlugins.size() == 0) {
                            broadcast();
                            return;
                        }

                        HashMap<ResultCallback, PendingResult> apiCallbacks =
                                new HashMap<ResultCallback, PendingResult>();
                        for (ComponentName cn : mInstalledPlugins) {
                            mCallMethodInfos.put(cn, new CallMethodInfo());
                            getCallMethodInfo(cn, apiCallbacks);
                            getCallMethodMimeType(cn, apiCallbacks);
                            getCallMethodStatus(cn, apiCallbacks);
                            getCallMethodVideoCallableMimeType(cn, apiCallbacks);
                            getCallMethodImMimeType(cn, apiCallbacks);
                            getCallMethodAuthenticated(cn, apiCallbacks);
                            getLoginIntent(cn, apiCallbacks);
                            getNudgeConfiguration(cn, NudgeKey.INCALL_CONTACT_FRAGMENT_LOGIN,
                                    apiCallbacks);
                            getNudgeConfiguration(cn, NudgeKey.INCALL_CONTACT_CARD_LOGIN,
                                    apiCallbacks);
                            getNudgeConfiguration(cn, NudgeKey.INCALL_CONTACT_CARD_DOWNLOAD,
                                    apiCallbacks);
                            getDefaultDirectorySearchIntent(cn, apiCallbacks);
                            // If you add any more callbacks, be sure to update
                            // EXPECTED_RESULT_CALLBACKS
                            // and EXPECTED_DYNAMIC_RESULT_CALLBACKS if the callback is dynamic
                            // with the proper count.
                        }
                        executeAll(apiCallbacks);
                    }
                });
    }

    public static Set<String> getAllPluginComponentNames() {
        Set<String> names = new HashSet<String>();
        HashMap<ComponentName, CallMethodInfo> plugins = InCallPluginHelper.getAllCallMethods();
        for (ComponentName cn : plugins.keySet()) {
            names.add(cn.flattenToString());
        }
        return names;
    }
}
