/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 *
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.activities;

import android.app.ActionBar;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract.RawContacts;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.ContactsActivity;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.msim.IIccPhoneBookMSim;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.contacts.R;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which
 * account to view contacts.
 */
public class MemoryStatusActivity extends ContactsActivity {
    private static final String TAG = "MemoryStatusActivity";
    private static final int INVALID_COUNT = 0;
    private ListView mListView;
    private View empty;
    private List<AccountListItem> mFilters;
    private AccountListAdapter mAdapter;
    private Handler mHandler;
    private LoaderThread mThread = null;

    private final class AccountListItem {
        public final String accountType;
        public final String accountName;
        public final String dataSet;
        public final Drawable icon;
        public final int total;
        public final int count;

        public AccountListItem(String accountType, String accountName, String dataSet,
                Drawable icon, int total, int count) {
            this.accountType = accountType;
            this.accountName = accountName;
            this.dataSet = dataSet;
            this.icon = icon;
            this.total = total;
            this.count = count;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_memory_list);

        mListView = (ListView) findViewById(android.R.id.list);
        empty = (View) findViewById(R.id.empty);
        mListView.setEmptyView(empty);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mFilters = Lists.newArrayList();
        mAdapter = new AccountListAdapter(this);
        mListView.setAdapter(mAdapter);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mFilters = (List<AccountListItem>) msg.obj;
                mAdapter.notifyDataSetChanged();
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, PeopleActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFilters.isEmpty()) {
            if (mThread == null) {
                mThread = new LoaderThread();
            }
            try {
                mThread.start();
            } catch (Exception e) {
            }
        }
    }

    public class LoaderThread extends Thread {
        @Override
        public void run() {
            List<AccountListItem> list = loadAccountFilters(MemoryStatusActivity.this);
            Message msg = Message.obtain();
            msg.obj = list;
            mHandler.sendMessage(msg);
        }
    }

    private List<AccountListItem> loadAccountFilters(Context context) {
        final ArrayList<AccountListItem> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(true);
        ContentResolver cr = context.getContentResolver();

        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType.isExtension() && !account.hasData(context)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
            int total = INVALID_COUNT;
            int count = INVALID_COUNT;
            if (!TextUtils.isEmpty(account.type)) {
                if (account.type.equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                    total = getAdnCount(MoreContactUtils
                            .getSubscription(account.type, account.name));
                    if (total > 0) {
                        Cursor cursor = cr.query(RawContacts.CONTENT_URI, new String[] {
                                RawContacts._ID
                        }, RawContacts.ACCOUNT_NAME + " = '" + account.name + "' AND "
                                + RawContacts.DELETED + " = 0", null, null);
                        if (cursor != null) {
                            try {
                                count = cursor.getCount();
                            } finally {
                                cursor.close();
                            }
                        }
                    }
                } else {
                    Cursor cursor = cr.query(RawContacts.CONTENT_URI, new String[] {
                            RawContacts._ID
                    }, RawContacts.ACCOUNT_NAME + " = '" + account.name + "' AND "
                            + RawContacts.DELETED + " = 0", null, null);
                    if (cursor != null) {
                        try {
                            count = cursor.getCount();
                        } finally {
                            cursor.close();
                        }
                    }
                }
            }
            accountFilters.add(new AccountListItem(
                    account.type, account.name, account.dataSet, icon, total, count));
        }

        return accountFilters;
    }

    private class AccountListAdapter extends BaseAdapter {
        private final LayoutInflater mLayoutInflater;
        private Context accountContext;

        public AccountListAdapter(Context context) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            accountContext = context;
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public AccountListItem getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final View view;
            AccountListItemViewCache viewCache;
            if (convertView != null) {
                view = convertView;
                viewCache = (AccountListItemViewCache) view.getTag();
            } else {
                view = mLayoutInflater.inflate(R.layout.memory_account_list_item, parent, false);
                viewCache = new AccountListItemViewCache(view);
                view.setTag(viewCache);
            }
            bindView(position, convertView, parent, viewCache);
            return view;
        }

        private void bindView(int position, View convertView, ViewGroup parent,
                AccountListItemViewCache viewCache) {
            final AccountListItem filter = mFilters.get(position);
            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(accountContext);
            final AccountType accountType =
                    accountTypes.getAccountType(filter.accountType, filter.dataSet);
            viewCache.accountName.setText(accountType.getDisplayLabel(accountContext)
                    + "<" + filter.accountName + ">");
            viewCache.totally.setVisibility((filter.total != INVALID_COUNT) ? View.VISIBLE : View.GONE);
            viewCache.count_total.setText(Integer.toString(filter.total));
            viewCache.count_cur.setText(Integer.toString(filter.count));
        }

        /**
         * Cache of the children views of a contact detail entry represented by
         * a {@link GroupListItem}
         */
        public class AccountListItemViewCache {
            public final TextView accountName;
            public final TextView count_total;
            public final TextView count_cur;
            public final LinearLayout totally;

            public AccountListItemViewCache(View view) {
                accountName = (TextView) view.findViewById(R.id.account_name);
                count_total = (TextView) view.findViewById(R.id.count_max);
                count_cur = (TextView) view.findViewById(R.id.count_cur);
                totally = (LinearLayout) view.findViewById(R.id.totally);
            }
        }
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
