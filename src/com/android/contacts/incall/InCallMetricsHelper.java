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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.ContactsDataSubscription;
import com.cyanogen.ambient.analytics.AnalyticsServices;
import com.cyanogen.ambient.analytics.Event;
import com.cyanogen.ambient.incall.InCallServices;
import com.google.common.base.Joiner;
import cyanogenmod.providers.CMSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class InCallMetricsHelper {
    private static final String TAG = InCallMetricsHelper.class.getSimpleName();

    private static final boolean DEBUG = false;
    private static final String CATEGORY_PREFIX = "contacts.incall.";
    private static final int REQUEST_CODE = 777;

    public static final String NUDGE_ID_INVALID = "-1";
    public static final int EVENT_DISMISS = 0;
    public static final int EVENT_ACCEPT = 1;

    private static InCallMetricsHelper sInstance;
    private InCallMetricsDbHelper mDbHelper;
    private static final String HANDLER_THREAD_NAME = "InCallMetricsHandler";
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Context mContext;

    public enum Categories {
        USER_ACTIONS("USER_ACTIONS"),
        INAPP_NUDGES("INAPP_NUDGES"),
        UNKNOWN("UNKNOWN");

        private String mValue;
        Categories(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Events {
        CONTACTS_MANUAL_MERGED("CONTACTS_MANUAL_MERGED"),
        CONTACTS_AUTO_MERGED("CONTACTS_AUTO_MERGED"),
        INVITES_SENT("INVITES_SENT"),
        DIRECTORY_SEARCH("DIRECTORY_SEARCH"),
        INAPP_NUDGE_CONTACTS_LOGIN("INAPP_NUDGE_CONTACTS_LOGIN"),
        INAPP_NUDGE_CONTACTS_INSTALL("INAPP_NUDGE_CONTACTS_INSTALL"),
        INAPP_NUDGE_CONTACTS_TAB_LOGIN("INAPP_NUDGE_CONTACTS_TAB_LOGIN"),
        UNKNOWN("UNKNOWN");

        private String mValue;
        Events(String s) {
            mValue = s;
        }
        public String value() {
            return mValue;
        }
    }

    public enum Parameters {
        ACTION_LOCATION("ACTION_LOCATION"),
        CATEGORY("CATEGORY"),
        COUNT("COUNT"),
        EVENT_ACCEPTANCE("EVENT_ACCEPTANCE"),
        EVENT_ACCEPTANCE_TIME("EVENT_ACCEPTANCE_TIME"),
        EVENT_NAME("EVENT_NAME"),
        NUDGE_ID("NUDGE_ID"),
        PROVIDER_NAME("PROVIDER_NAME");

        private String mValue;

        Parameters(String s) {
            mValue = s;
        }

        public String value() {
            return mValue;
        }

        public String toCol() {
            return mValue.toLowerCase();
        }
   }
    private static Comparator<String> sRawIdComparator = new Comparator<String> () {
        @Override
        public int compare(String o1, String o2)  {
            return Integer.parseInt(o1) - Integer.parseInt(o2);
        }
    };

    public static void init(Context context) {
        InCallMetricsHelper helper = getInstance(context);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, InCallMetricsService.class);

        // scheduled every 24h
        PendingIntent pendingIntent = PendingIntent.getService(context, REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
    }

    public static synchronized InCallMetricsHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new InCallMetricsHelper();
            sInstance.mContext = context;
            // init handler
            sInstance.mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
            sInstance.mHandlerThread.start();
            sInstance.mHandler = new Handler(sInstance.mHandlerThread.getLooper());
            sInstance.mDbHelper = InCallMetricsDbHelper.getInstance(context);
        }
        return sInstance;
    }

    /**
     * Gather all metrics entries from tables and send to Ambient. Called from
     * InCallMetricsService (IntentService)
     *
     * @param  context  context to be used in db
     */
    public static void prepareAndSend(Context context) {
        if (!statsOptIn(context)) {
            return;
        }

        InCallMetricsHelper helper = getInstance(context);
        // In App events
        List<ContentValues> allList = helper.mDbHelper.getAllEntries(Categories.INAPP_NUDGES, true);
        // User Actions events
        allList.addAll(helper.mDbHelper.getAllEntries(Categories.USER_ACTIONS, true));
        for (ContentValues cv : allList) {
            Categories cat = cv.containsKey(Parameters.CATEGORY.toCol()) ?
                    Categories.valueOf(cv.getAsString(Parameters.CATEGORY.toCol())) :
                    Categories.UNKNOWN;
            Events event = cv.containsKey(Parameters.EVENT_NAME.toCol()) ?
                    Events.valueOf(cv.getAsString(Parameters.EVENT_NAME.toCol())) :
                    Events.UNKNOWN;
            Set<String> plugins = ContactsDataSubscription.get(context)
                    .getAllPluginComponentNames();
            sendEvent(context, cat, event, getExtraFields(cat, event, cv), plugins);
        }
    }

    /**
     * Map ContentValues pair from db to <Parameters, value> pairs
     *
     * @param  event    metric event
     * @param  cat      metric category
     * @param  cv
     */
    private static HashMap<Parameters, Object> getExtraFields(Categories cat, Events event,
            ContentValues cv) {
        HashMap<Parameters, Object> map = new HashMap<Parameters, Object>();
        map.put(Parameters.PROVIDER_NAME, cv.getAsString(Parameters.PROVIDER_NAME.toCol()));
        switch (cat) {
            case INAPP_NUDGES:
                switch (event) {
                    case INAPP_NUDGE_CONTACTS_LOGIN:
                    case INAPP_NUDGE_CONTACTS_INSTALL:
                    case INAPP_NUDGE_CONTACTS_TAB_LOGIN:
                        map.put(Parameters.COUNT, cv.getAsInteger(Parameters.COUNT.toCol()));
                        map.put(Parameters.NUDGE_ID, cv.getAsString(Parameters.NUDGE_ID.toCol()));
                        map.put(Parameters.EVENT_ACCEPTANCE_TIME,
                                cv.getAsInteger(Parameters.EVENT_ACCEPTANCE_TIME.toCol()));
                        map.put(Parameters.EVENT_ACCEPTANCE,
                                cv.getAsInteger(Parameters.EVENT_ACCEPTANCE.toCol()) == 0 ?
                                        Boolean.FALSE : Boolean.TRUE);
                        break;
                    default:
                        break;
                }
                break;
            case USER_ACTIONS:
                switch (event) {
                    case CONTACTS_AUTO_MERGED:
                    case CONTACTS_MANUAL_MERGED:
                    case INVITES_SENT:
                        map.put(Parameters.COUNT, cv.getAsInteger(Parameters.COUNT.toCol()));
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        return map;
    }

    /**
     * Send AnalyticsService events to Ambient and InCall plugins
     *
     * @param  context      context
     * @param  cat          metric category
     * @param  eventName    metric event name
     * @param  extraFields  <Parameter, Object> pairs to be included in Ambient Event
     * @param  pluginSet    a set of available plugin component names for sending lookup
     */
    private static void sendEvent(Context context, Categories cat, Events eventName,
            HashMap<Parameters, Object> extraFields, Set<String> pluginSet) {
        Event.Builder eventBuilder = new Event.Builder(CATEGORY_PREFIX + cat.value(), eventName
                .value());
        if (extraFields != null && extraFields.size() > 0) {
            for (Parameters param : extraFields.keySet()) {
                eventBuilder.addField(param.value(), String.valueOf(extraFields.get(param)));
            }
        }
        Event event = eventBuilder.build();
        if (DEBUG) Log.d(TAG, event.toString());
        // send to Ambient
        AnalyticsServices.AnalyticsApi.sendEvent(AmbientConnection.CLIENT.get(context), event);
        // send to selective plugin
        String providers = extraFields.containsKey(Parameters.PROVIDER_NAME) ?
                (String) extraFields.get(Parameters.PROVIDER_NAME) : "";
        sendEventToProviders(context, cat, eventName, event, providers, pluginSet);
    }

    /**
     * Send Ambient Events to InCall providers, if they exist in the set of currently available
     * InCall plugins
     *
     * @param  context      context
     * @param  cat          metric category
     * @param  eventName    metric event name
     * @param  pluginSet    a set of available plugin component names for sending lookup
     */
    private static void sendEventToProviders(Context context, Categories cat, Events eventName,
            Event event, String providers, Set<String> pluginSet) {
        if (!isWhiteListed(cat, eventName)) {
            return;
        }
        if (DEBUG) Log.d(TAG, "sendEventToProviders:" + providers);
        String[] providerList = providers.split(",");
        for (String provider : providerList) {
            if (pluginSet.contains(provider)) {
                if (DEBUG) Log.d(TAG, "sendEventToProvider:" + provider);
                InCallServices.getInstance().sendAnalyticsEventToPlugin(
                        AmbientConnection.CLIENT.get(context),
                        ComponentName.unflattenFromString(provider), event);
            }
        }
    }

    /**
     * Check if the category/event are whitelisted to be able to sent to InCall providers
     *
     * @param  cat          metric category
     * @param  eventName    metric event name
     */
    private static boolean isWhiteListed(Categories cat, Events eventName) {
        return true;
    }

    public static void increaseCount(final Context context, final Events event, final String
            provider) {
        final InCallMetricsHelper helper = getInstance(context);
        helper.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!statsOptIn(context)) {
                    return;
                }
                helper.mDbHelper.incrementUserActionsParam(provider, "", event.value(),
                        Categories.USER_ACTIONS.value(),
                        Parameters.COUNT.value().toLowerCase());
            }
        });
    }

    /**
     * Set a specific parameter to a certain value
     *
     * @param  context      context
     * @param  component    InCall provider component name
     * @param  cat          metric category
     * @param  event        metric event name
     * @param  param        metric parameter to set
     * @param  value        metric parameter value to set to
     * @param  nudgeId      nudge ID corresponding to the
     */
    public static void setValue(final Context context, final ComponentName component,
            final Categories cat, final Events event, final Parameters param,
            final Object value, final String nudgeId) {
        final InCallMetricsHelper helper = getInstance(context);
        helper.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!statsOptIn(context)) {
                    return;
                }
                switch (cat) {
                    case INAPP_NUDGES:
                        switch (param) {
                            case EVENT_ACCEPTANCE:
                                helper.mDbHelper.setInAppAcceptance(component.flattenToString(),
                                        event.value(), cat.value(), (Integer) value, nudgeId);
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    /**
     * Increases the impression count for different nudges in
     *
     * @param  context  context
     * @param  cmi      CallMethodInfo for the entry
     * @pram   even     Events type
     */
    public static void increaseImpressionCount(final Context context, final CallMethodInfo cmi,
            final Events event) {
        if (cmi == null || cmi.mComponent == null) {
            return;
        }
        final InCallMetricsHelper helper = getInstance(context);
        helper.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!statsOptIn(context)) {
                    return;
                }
                switch (event) {
                    case INAPP_NUDGE_CONTACTS_INSTALL:
                        helper.mDbHelper.incrementInAppParam(cmi.mComponent.flattenToString(),
                                event.value(), Categories.INAPP_NUDGES.value(),
                                generateNudgeId(cmi.mInstallNudgeSubtitle),
                                Parameters.COUNT.toCol());
                        break;
                    case INAPP_NUDGE_CONTACTS_LOGIN:
                        helper.mDbHelper.incrementInAppParam(cmi.mComponent.flattenToString(),
                                event.value(), Categories.INAPP_NUDGES.value(),
                                generateNudgeId(cmi.mLoginNudgeSubtitle),
                                Parameters.COUNT.toCol());
                        break;
                    case INAPP_NUDGE_CONTACTS_TAB_LOGIN:
                        if (!cmi.mIsAuthenticated) {
                            helper.mDbHelper.incrementInAppParam(cmi.mComponent.flattenToString(),
                                    event.value(),
                                    Categories.INAPP_NUDGES.value(),
                                    generateNudgeId(cmi.mLoginSubtitle),
                                    Parameters.COUNT.toCol());
                        }
                    default:
                        break;
                }
            }
        });
    }

    /**
     * Increases manual contact merge counts
     *
     * @param  context          context
     * @param  contactIdForJoin the primary contact ID to be merged
     * @param  contactId        the secondary contact ID to be merged
     */
    public static void increaseContactManualMergeCount(final Context context,
            final long contactIdForJoin, final long contactId) {
        final InCallMetricsHelper helper = getInstance(context);
        helper.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!statsOptIn(context)) {
                    return;
                }
                SortedSet<String> rawIdSet = new TreeSet<String>(sRawIdComparator);
                SortedSet<String> providerSet = queryContactProviderByContactIds(context,
                        contactIdForJoin, contactId, rawIdSet);
                String[] rawIdArray = rawIdSet.toArray(new String[rawIdSet.size()]);
                List<String> providerList = new ArrayList<String>(providerSet);
                String joinedProvider = providerList.size() == 0 ? "" :
                        Joiner.on(",").skipNulls().join(providerList);
                String joinedRawIds = rawIdArray.length == 0 ? "" :
                        Joiner.on(",").skipNulls().join(rawIdArray);
                helper.mDbHelper.incrementUserActionsParam(joinedProvider, joinedRawIds,
                        InCallMetricsHelper.Events.CONTACTS_MANUAL_MERGED.value(),
                        Categories.USER_ACTIONS.value(), Parameters.COUNT.toCol());
            }
        });
    }

    /**
     * Increases auto contact merge counts
     *
     * @param  context  context
     * @param  rawIds   the merged raw contact IDs
     */
    public static void increaseContactAutoMergeCount(final Context context, final String rawIds) {
        final InCallMetricsHelper helper = getInstance(context);
        helper.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!statsOptIn(context)) {
                    return;
                }
                String[] rawIdArray = rawIds.split(",");
                Arrays.sort(rawIdArray, sRawIdComparator);
                SortedSet<String> providerSet = queryContactProviderByRawContactIds(context,
                        rawIdArray);
                List<String> providerList = new ArrayList<String>(providerSet);

                String joinedProvider = providerList.size() == 0 ? "" :
                        Joiner.on(",").skipNulls().join(providerList);
                String joinedRawIds = rawIdArray.length == 0 ? "" :
                        Joiner.on(",").skipNulls().join(rawIdArray);
                helper.mDbHelper.incrementUserActionsParam(joinedProvider,
                        joinedRawIds,
                        InCallMetricsHelper.Events.CONTACTS_AUTO_MERGED.value(),
                        Categories.USER_ACTIONS.value(), Parameters.COUNT.toCol());
            }
        });

    }

    /**
     * Check if the provided contact IDs is from an account type that matches a InCall provider.
     *
     * @param  context     context
     * @param  contactId   the primary contact ID to be merged
     * @param  contactId2  the secondary contact ID to be merged
     * @param  rawIdSet    merged raw contac ID set filled by this method
     */
    private static SortedSet<String> queryContactProviderByContactIds(Context context, long
            contactId, long contactId2, Set<String> rawIdSet) {
        SortedSet<String> providerSet = new TreeSet<String>();
        HashMap<String, String> pluginMap = InCallPluginUtils.getPluginAccountComponentPairs
                (context);
        try (Cursor cursor = context.getContentResolver().query(RawContacts.CONTENT_URI,
                    new String[]{RawContacts.ACCOUNT_TYPE, RawContacts._ID},
                    RawContacts.CONTACT_ID + "=? OR " + RawContacts.CONTACT_ID + "=?",
                    new String[]{String.valueOf(contactId), String.valueOf(contactId2)}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String accountType = cursor.getString(0); // RawContacts.ACCOUNT_TYPE
                    if (pluginMap.containsKey(accountType)) {
                        // a plugin, use component name
                        providerSet.add(pluginMap.get(accountType));
                    } else {
                        // not a plugin, use account type instead
                        providerSet.add(accountType);
                    }
                    rawIdSet.add(String.valueOf(cursor.getInt(1))); // _ID
                    if (DEBUG) Log.d(TAG, "queryContactProvider:" + cursor.getString(0));
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "queryContactProiderByContactIds exception: ", e);
        }
        return providerSet;
    }

    /**
     * Check if the provided raw contact IDs is from an account type that matches an InCall
     * provider.
     *
     * @param  context  context
     * @param  rawIds   raw contact IDs of the contacts
     */
    private static SortedSet<String> queryContactProviderByRawContactIds(Context context, String[]
            rawIds) {
        SortedSet<String> providerSet = new TreeSet<String>();
        HashMap<String, String> pluginMap = InCallPluginUtils.getPluginAccountComponentPairs
                (context);
        for (String rawId : rawIds) {
            try (Cursor cursor = context.getContentResolver().query(RawContacts.CONTENT_URI,
                        new String[]{RawContacts.ACCOUNT_TYPE},
                        RawContacts._ID + "=?",
                        new String[]{rawId}, null)) {
                if (cursor != null) {
                    while (cursor.moveToFirst()) {
                        String accountType = cursor.getString(0); // RawContacts.ACCOUNT_TYPE
                        if (pluginMap.containsKey(accountType)) {
                            providerSet.add(pluginMap.get(accountType));
                        } else {
                            providerSet.add(accountType);
                        }
                        providerSet.add(accountType);
                        if (DEBUG) Log.d(TAG, "queryContactProvider:" + accountType);
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "queryContactProviderByRawContactIds exception: ", e);
            }
        }
        return providerSet;
    }

    public static String generateNudgeId(String data) {
        if (TextUtils.isEmpty(data)) {
            return "";
        }
        return java.util.UUID.nameUUIDFromBytes(data.getBytes()).toString();
    }

    private static boolean statsOptIn(Context context) {
        return CMSettings.Secure.getInt(context.getContentResolver(),
                CMSettings.Secure.STATS_COLLECTION, 1) == 1;
    }
}