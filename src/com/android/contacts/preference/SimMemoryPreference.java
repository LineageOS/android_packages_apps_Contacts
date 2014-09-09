/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.preference;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.provider.ContactsContract;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import com.android.contacts.R;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.msim.IIccPhoneBookMSim;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import java.util.List;

public final class SimMemoryPreference extends Preference {

    private static final String TAG = SimMemoryPreference.class.getSimpleName();

    private static final int INVALID_COUNT = 0;

    private ContactsPreferences mPreferences;
    private Context mContext;

    SimMemoryLoadingFinishedCallback mCallback = null;
    public interface SimMemoryLoadingFinishedCallback {
        public void onLoadingFinished(boolean hasSimContacts);
    }

    public SimMemoryLoadingFinishedCallback getCallback() {
        return mCallback;
    }

    public void setCallback(SimMemoryLoadingFinishedCallback callback) {
        this.mCallback = callback;
    }

    public SimMemoryPreference(Context context) {
        super(context);
    }

    public SimMemoryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void prepare() {
        mContext = getContext();

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(true);
        ContentResolver cr = mContext.getContentResolver();

        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType.isExtension() && !account.hasData(mContext)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            int total = INVALID_COUNT;
            int count = INVALID_COUNT;
            if (!TextUtils.isEmpty(account.type)) {
                if (account.type.equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                    total = getAdnCount(MoreContactUtils
                            .getSubscription(account.type, account.name));
                    if (total > 0) {
                        Cursor cursor = cr.query(ContactsContract.RawContacts.CONTENT_URI, new String[] {
                                ContactsContract.RawContacts._ID
                        }, ContactsContract.RawContacts.ACCOUNT_NAME + " = '" + account.name + "' AND "
                                + ContactsContract.RawContacts.DELETED + " = 0", null, null);
                        if (cursor != null) {
                            try {
                                count = cursor.getCount();
                            } finally {
                                cursor.close();
                            }
                        }
                    }
                    String s = String.format(
                            mContext.getString(R.string.memory_status_preference_summary),
                            count, total);
                    setSummary(s);
                    if (mCallback != null) {
                        mCallback.onLoadingFinished(true);
                    }
                    return;
                }
            }
        }
        if (mCallback != null) {
            mCallback.onLoadingFinished(false);
        }
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    protected boolean persistString(String value) {
        int newValue = Integer.parseInt(value);
        if (newValue != mPreferences.getDisplayOrder()) {
            mPreferences.setDisplayOrder(newValue);
            notifyChanged();
        }
        return true;
    }

    public static int getMSimCardMaxCount(int subscription) {
        int count = INVALID_COUNT;
        try {
            IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(
                    ServiceManager.getService("simphonebook_msim"));
            if (iccIpb != null) {
                List<AdnRecord> list = iccIpb.getAdnRecordsInEf(IccConstants.EF_ADN, subscription);
                if (null != list) {
                    count = list.size();
                }
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to IIccPhoneBookMSim", ex);
        }
        return count;
    }

    public static int getSimCardMaxCount() {
        int count = INVALID_COUNT;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                List<AdnRecord> list = iccIpb.getAdnRecordsInEf(IccConstants.EF_ADN);
                if (null != list) {
                    count = list.size();
                }
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to IIccPhoneBook", ex);
        }
        return count;
    }

    public static int getAdnCount(int sub) {
        if (sub == SimContactsConstants.SUB_INVALID) {
            return INVALID_COUNT;
        }
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            return getMSimCardMaxCount(sub);
        } else {
            return getSimCardMaxCount();
        }
    }
}
