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

import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.UiObjectNotFoundException;

import android.net.Uri;
import android.support.test.rules.ActivityTestRule;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.ContactSaveService;
import com.android.contacts.incall.InCallMetricsDbHelper;
import com.android.contacts.incall.InCallMetricsHelper;
import com.android.contacts.R;

import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.ContactsDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.HashMap;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class InCallMetricsContactTest {
    private static final String TAG = InCallMetricsContactTest.class.getSimpleName();
    private Context mContext;
    private PeopleActivity mActivity;
    private CallMethodInfo mCm;
    private UiDevice mDevice;

    private static final String TESTER1_NAME = "Tester1";
    private static final String TESTER2_NAME = "Tester2";
    private static final String TESTER1_PHONE = "777-777-7777";
    private static final String TESTER2_PHONE = "123-456-7890";
    private static final String MENU_MERGE = "Merge";
    private static final String SIGN_IN = "Sign in";
    private static final String GET = "Get";

    @Rule
    public ActivityTestRule<PeopleActivity> mActivityRule
            = new ActivityTestRule<PeopleActivity>(PeopleActivity.class);

    @Before
    public void setUp() {
        // get Activity handle under test
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // get CallMethodInfo under test
        //InCallMetricsTestUtils.waitFor(500);
        HashMap<ComponentName, CallMethodInfo> cmMap =
                CallMethodFilters.getAllEnabledAndHiddenCallMethods(
                        ContactsDataSubscription.get(mContext));
        Assert.assertNotNull(cmMap);
        Set<ComponentName> cmKeySet = cmMap.keySet();
        if (cmKeySet.size() == 0) {
            Log.d(TAG, "No InCall plugin installed");
            return;
        }
        // test the first plugin only
        ComponentName cn = cmKeySet.iterator().next();
        mCm = cmMap.get(cn);
        Assert.assertNotNull(mCm);

        // Initialize UiDevice instance
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    /*
     * test metrics: CONTACTS_MANUAL_MERGED
     * precondition : signed in
     */
    @Test
    public void test1() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED.value();
        Log.d(TAG, "-----test1 start -----" + testName);
        InCallMetricsTestUtils.setInCallPluginAuthState(mContext, true);
        InCallMetricsTestUtils.waitFor(100);

        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        // check current impression count
        // show contacts tab login 2x
        // select InCall plugin tab
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri1 =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        Uri contactUri2 =
                InCallMetricsTestUtils.createContact(null, TESTER2_NAME, TESTER2_PHONE, cr);

        InCallMetricsTestUtils.openContactCard(mActivity, contactUri1);
        InCallMetricsTestUtils.waitFor(1000);

        // Click on edit
        UiObject editButton = mDevice.findObject(new UiSelector()
                .resourceId("com.android.contacts:id/menu_edit"));
        try {
            if (editButton.exists()) {
                editButton.click();
                InCallMetricsTestUtils.waitFor(1000);
                // Open menu
                mDevice.pressMenu();
                InCallMetricsTestUtils.waitFor(1000);
                // Click on merge
                UiObject mergeMenu = mDevice.findObject(new UiSelector()
                        .text(MENU_MERGE));
                if (mergeMenu.exists()) {
                    mergeMenu.click();
                    InCallMetricsTestUtils.waitFor(1000);
                    // Select the merge target contact
                    UiObject mergeContact = mDevice.findObject(new UiSelector()
                        .text(TESTER2_NAME));
                    if (mergeContact.exists()) {
                        mergeContact.click();
                    } else {
                        Log.d(TAG, "ERROR: mergeCount does not exist");
                    }
                }
            } else {
                Log.d(TAG, "ERROR: edit button not found");
            }
            InCallMetricsTestUtils.waitFor(100);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // get uri and return uri
        Uri mergedUri = InCallMetricsTestUtils.findContactUriByDisplayName(TESTER1_NAME, cr);

        // clean up contact
        mActivity.startService(ContactSaveService.createDeleteContactIntent(mActivity, mergedUri));
        Log.d(TAG, "-----test1 finish -----");
    }

    /*
     * test metrics: CONTACTS_AUTO_MERGED
     * precondition : signed in
     */
    @Test
    public void test2() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED.value();
        Log.d(TAG, "-----test2 start -----" + testName);
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(100);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // query
        ContentResolver cr = mActivity.getContentResolver();
        Uri matchUri = InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        Uri contactUri =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);

        InCallMetricsTestUtils.waitFor(500);

        // check count
        ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED);
        InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);

        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        InCallMetricsTestUtils.deleteContact(matchUri, cr);
        Log.d(TAG, "-----test2 finish -----");
    }

    /*
     * test metrics: INVITES_SENT
     * precondition : signed in
     */
    @Test
    public void test3() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.INVITES_SENT.value();
        Log.d(TAG, "-----test3 start -----" + testName);
        // precondition signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(100);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));

        // launch activity
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            UiObject inviteButton = mDevice.findObject(new UiSelector().text(
                    mActivity.getResources()
                            .getString(R.string.incall_plugin_invite)));
            if (inviteButton.exists()) {
                inviteButton.click();
                InCallMetricsTestUtils.waitFor(100);
                // check count
                ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                        InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                        InCallMetricsHelper.Events.INVITES_SENT);
                InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
            } else {
                Log.d(TAG, "ERROR: No matching view");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: invite not found");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test3 finish -----");
    }

    /*
     * test metrics: DIRECTORY_SEARCH
     * precondition : signed in
     */
    @Test
    public void test4() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.DIRECTORY_SEARCH.value();
        Log.d(TAG, "-----test4 start -----" + testName);
        // precondition: signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(100);

        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        ContentResolver cr = mActivity.getContentResolver();
        Uri contactUri =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            UiObject searchButton = mDevice.findObject(new UiSelector().text(
                    mActivity.getResources()
                            .getString(R.string.incall_plugin_directory_search, mCm.mName)));
            if (searchButton.exists()) {
                searchButton.click();
                InCallMetricsTestUtils.waitFor(100);
                // check count
                ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                        InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                        InCallMetricsHelper.Events.DIRECTORY_SEARCH);
                InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
            } else {
                Log.d(TAG, "ERROR: search not found");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);
        Log.d(TAG, "-----test4 finish -----");
    }
    /*
     * test metrics: INAPP_NUDGE_CONTACTS_LOGIN
     * precondition : signed out
     */
    @Test
    public void test5() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN.value();
        Log.d(TAG, "-----test5 start -----" + testName);

        // sign out
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, false);
        InCallMetricsTestUtils.waitFor(100);

        ContentResolver cr = mActivity.getContentResolver();
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        Uri contactUri =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            UiObject signinButton = mDevice.findObject(new UiSelector().text(SIGN_IN));
            if (signinButton.exists()) {
                signinButton.click();
                InCallMetricsTestUtils.waitFor(100);
                // check count
                ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                        InCallMetricsDbHelper.Tables.INAPP_TABLE,
                        InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_LOGIN);
                InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
            } else {
                Log.d(TAG, "ERROR: " + SIGN_IN + " not found");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);

        Log.d(TAG, "-----test5 finish -----");
    }

    /*
     * test metrics: DIRECTORY_SEARCH (from contacts plugin tab)
     * precondition : signed in
     */
    @Test
    public void test6() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.DIRECTORY_SEARCH.value();
        Log.d(TAG, "-----test6 start -----" + testName);
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(100);
        try {
            // focus on plugin tab
            onView(withId(R.id.tab_pager)).perform(swipeLeft());

            // click on directory search button
            onView(withId(R.id.floating_action_button)).perform(click());
            InCallMetricsTestUtils.waitFor(100);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.USER_ACTIONS_TABLE,
                    InCallMetricsHelper.Events.DIRECTORY_SEARCH);
            InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        Log.d(TAG, "-----test6 finish -----");
    }

    /*
     * test metrics: INAPP_NUDGE_CONTACTS_TAB_LOGIN
     * precondition : hard signed out
     */
    @Test
    public void test7() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN.value();
        Log.d(TAG, "-----test7 start -----" + testName);
        Log.d(TAG, "Please hard sign out...");
        // clear out db
        Assert.assertTrue(InCallMetricsTestDbUtils.clearAllEntries(mActivity));
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, false);
        InCallMetricsTestUtils.waitFor(100);
        Log.d(TAG, "inAppNudgeContactsTabLogin cm:" + mCm.mName);

        // check current impression count
        // show contacts tab login 2x
        // select InCall plugin tab
        try {
            onView(withId(R.id.tab_pager)).perform(swipeLeft());
            InCallMetricsTestUtils.waitFor(1000);
            // swipe away
            onView(withId(R.id.tab_pager)).perform(swipeRight());
            // select InCall plugin tab again
            onView(withId(R.id.tab_pager)).perform(swipeLeft());
            InCallMetricsTestUtils.waitFor(1000);

            long currentTime = System.currentTimeMillis();

            onView(withId(R.id.plugin_login_button)).perform(click());
            InCallMetricsTestUtils.waitFor(100);
            ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                    InCallMetricsDbHelper.Tables.INAPP_TABLE,
                    InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_TAB_LOGIN);
            InCallMetricsTestUtils.verifyInAppMetrics(testName, entry, 2, currentTime);

        } catch (NoMatchingViewException e) {
            Log.d(TAG, "ERROR: No matching view");
        }
        Log.d(TAG, "-----test7 finish -----");
    }
    /*
      * test metrics: INAPP_NUDGE_CONTACTS_INSTALL
      * precondition : plugin hidden (uninstall dependency)
      */
    @Test
    public void test8() {
        mActivity = (PeopleActivity) mActivityRule.getActivity();
        String testName = InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL.value();
        Log.d(TAG, "-----test8 start -----" + testName);
        Log.d(TAG, "Please uninsatll InCall plugin dependency package...(waiting 10 seconds)");
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        InCallMetricsTestUtils.waitFor(100);

        ContentResolver cr = mActivity.getContentResolver();
        InCallMetricsTestDbUtils.clearAllEntries(mActivity);
        Uri contactUri =
                InCallMetricsTestUtils.createContact(null, TESTER1_NAME, TESTER1_PHONE, cr);
        InCallMetricsTestUtils.openContactCard(mActivity, contactUri);
        InCallMetricsTestUtils.waitFor(1000);
        try {
            UiObject button = mDevice.findObject(new UiSelector().text(GET));
            if (button.exists()) {
                button.click();
                InCallMetricsTestUtils.waitFor(100);
                // check count
                ContentValues entry = InCallMetricsTestDbUtils.getEntry(mActivity,
                        InCallMetricsDbHelper.Tables.INAPP_TABLE,
                        InCallMetricsHelper.Events.INAPP_NUDGE_CONTACTS_INSTALL);
                InCallMetricsTestUtils.verifyUserActionsMetrics(testName, entry, 1);
            } else {
                Log.d(TAG, "ERROR: " + GET + " not found");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "ERROR: No matching View");
        }
        // clean up contact
        InCallMetricsTestUtils.deleteContact(contactUri, cr);

        Log.d(TAG, "-----test8 finish -----");
    }

    @After
    public void cleanup() {
        // change plugin auth state back to signed in
        InCallMetricsTestUtils.setInCallPluginAuthState(mActivity, true);
        // clear out db metrics entries
        InCallMetricsTestDbUtils.clearAllEntries(mActivity);
    }
}

