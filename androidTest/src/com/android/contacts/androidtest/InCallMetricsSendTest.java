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

package com.android.contacts.androidtest;

import android.content.ComponentName;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.rules.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.incall.InCallMetricsDbHelper;
import com.android.contacts.incall.InCallMetricsHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.ContactsDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class InCallMetricsSendTest {
    private static final String TAG = InCallMetricsSendTest.class.getSimpleName();

    private Context mContext;
    private CallMethodInfo mCm;

    @Rule
    public ActivityTestRule<PeopleActivity> mActivityTestRule = new
            ActivityTestRule<PeopleActivity>(PeopleActivity.class);

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Query plugins
        HashMap<ComponentName, CallMethodInfo> cmMap =
                CallMethodFilters.getAllEnabledAndHiddenCallMethods(
                        ContactsDataSubscription.get(mContext));
        Assert.assertNotNull(cmMap);
        Set<ComponentName> cmKeySet = cmMap.keySet();
        if (cmKeySet.size() == 0) {
            Log.d(TAG, "No InCall plugin installed");
            return;
        }
        // test the first one
        ComponentName cn = cmKeySet.iterator().next();
        mCm = cmMap.get(cn);
        Assert.assertNotNull(mCm);
    }

    @Test
    public void sendTest() {
        populateMetricsInDb();
        InCallMetricsTestUtils.waitFor(500);
        InCallMetricsHelper.prepareAndSend(mContext, null, null);
    }

    private void populateMetricsInDb() {
        // Category: USER_ACTIONS
        // CONTACTS_AUTO_MERGED
        InCallMetricsDbHelper.getInstance(mContext).incrementUserActionsParam(
                mCm.mComponent.flattenToString(),
                "",
                InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED.value(),
                InCallMetricsHelper.Categories.USER_ACTIONS.value(),
                InCallMetricsHelper.Parameters.COUNT.toCol());
        // CONTACTS_MANUAL_MERGED
        InCallMetricsDbHelper.getInstance(mContext).incrementUserActionsParam(
                mCm.mComponent.flattenToString(),
                "",
                InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED.value(),
                InCallMetricsHelper.Categories.USER_ACTIONS.value(),
                InCallMetricsHelper.Parameters.COUNT.toCol());
        // INVITES_SENT
        InCallMetricsHelper.increaseCount(mContext, InCallMetricsHelper.Events.INVITES_SENT,
                mCm.mComponent.flattenToString());
        // DIRECTORY_SEARCH
        InCallMetricsHelper.increaseCount(mContext, InCallMetricsHelper.Events.DIRECTORY_SEARCH,
                mCm.mComponent.flattenToString());
        // Category: In App Nudges
        // INAPP_NUDGE_CONTACTS_TAB_LOGIN
        InCallMetricsHelper.setValue(
                mContext,
                mCm.mComponent,
                InCallMetricsHelper.Categories.INAPP_NUDGES,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN,
                InCallMetricsHelper.Parameters.EVENT_ACCEPTANCE,
                InCallMetricsHelper.EVENT_ACCEPT,
                InCallMetricsHelper.generateNudgeId(mCm.mLoginSubtitle));
        // INAPP_NUDGE_CONTACTS_LOGIN
        InCallMetricsHelper.setValue(
                mContext,
                mCm.mComponent,
                InCallMetricsHelper.Categories.INAPP_NUDGES,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN,
                InCallMetricsHelper.Parameters.EVENT_ACCEPTANCE,
                InCallMetricsHelper.EVENT_ACCEPT,
                InCallMetricsHelper.generateNudgeId(mCm.mLoginNudgeSubtitle));
        // INAPP_NUDGE_CONTACTS_INSTALL
        InCallMetricsHelper.setValue(
                mContext,
                mCm.mComponent,
                InCallMetricsHelper.Categories.INAPP_NUDGES,
                InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL,
                InCallMetricsHelper.Parameters.EVENT_ACCEPTANCE,
                InCallMetricsHelper.EVENT_DISMISS,
                InCallMetricsHelper.generateNudgeId(mCm.mInstallNudgeSubtitle));
    }
}
