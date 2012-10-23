/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package com.android.contacts.dialpad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.dialpad.util.NameToNumber;
import com.android.contacts.dialpad.util.NameToNumberFactory;

/**
 * @author shade, Danesh, pawitp
 */
class T9SearchCache {
    public interface Callback {
        public void onLoadFinished();
    }

    // List sort modes
    private static final int NAME_FIRST = 1;
    private static final int NUMBER_FIRST = 2;

    // Phone number queries
    private static final String[] PHONE_PROJECTION = new String[] {
        Phone.NUMBER,
        Phone.CONTACT_ID,
        Phone.IS_SUPER_PRIMARY,
        Phone.TYPE,
        Phone.LABEL
    };
    private static final int PHONE_COLUMN_NUMBER = 0;
    private static final int PHONE_COLUMN_CONTACT = 1;
    private static final int PHONE_COLUMN_PRIMARY = 2;
    private static final int PHONE_COLUMN_TYPE = 3;
    private static final int PHONE_COLUMN_LABEL = 4;

    private static final String PHONE_ID_SELECTION = Contacts.Data.MIMETYPE + " = ? ";
    private static final String[] PHONE_ID_SELECTION_ARGS = new String[] {
        Phone.CONTENT_ITEM_TYPE
    };
    private static final String PHONE_SORT = Phone.CONTACT_ID + " ASC";

    private static final String[] CONTACT_PROJECTION = new String[] {
        Contacts._ID,
        Contacts.DISPLAY_NAME,
        Contacts.TIMES_CONTACTED,
        Contacts.PHOTO_THUMBNAIL_URI
    };
    private static final int CONTACT_COLUMN_ID = 0;
    private static final int CONTACT_COLUMN_NAME = 1;
    private static final int CONTACT_COLUMN_CONTACTED = 2;
    private static final int CONTACT_COLUMN_PHOTO_URI = 3;

    private static final String CONTACT_QUERY = Contacts.HAS_PHONE_NUMBER + " > 0";
    private static final String CONTACT_SORT = Contacts._ID + " ASC";

    // Local variables
    private static T9SearchCache sInstance = null;

    private Context mContext;
    private int mHighlightColor;
    private Set<Callback> mCallbacks = new HashSet<Callback>();

    private LoadTask mLoadTask;
    private boolean mLoaded;
    private Set<ContactItem> mAllResults = new LinkedHashSet<ContactItem>();

    private ArrayList<ContactItem> mContacts = new ArrayList<ContactItem>();
    private String mPrevInput;

    private String mT9Chars;
    private String mT9Digits;

    private BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mLoaded) {
                return;
            }

            NameToNumber normalizer = NameToNumberFactory.create(mContext, mT9Chars, mT9Digits);
            for (ContactItem contact : mContacts) {
                contact.normalName = normalizer.convert(contact.name);
            }
            notifyLoadFinished();
        }
    };

    public static synchronized T9SearchCache getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (sInstance == null) {
            sInstance = new T9SearchCache(applicationContext);
        }
        return sInstance;
    }

    private T9SearchCache(Context context) {
        mContext = context;
        mContext.registerReceiver(mLocaleChangedReceiver, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        mHighlightColor = context.getResources().getColor(android.R.color.holo_blue_dark);
    }

    public void refresh(Callback cb) {
        mCallbacks.add(cb);
        if (mLoadTask == null || mLoadTask.getStatus() == AsyncTask.Status.FINISHED) {
            mLoaded = false;
            mLoadTask = new LoadTask();
            mLoadTask.execute();
        }
    }

    public void cancelRefresh(Callback cb) {
        mCallbacks.remove(cb);
        if (mCallbacks.isEmpty() && mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
    }

    private class LoadTask extends AsyncTask<Void, Void, Void> {
        private ArrayList<ContactItem> contacts = new ArrayList<ContactItem>();

        @Override
        protected Void doInBackground(Void... args) {
            initT9Map();

            NameToNumber normalizer = NameToNumberFactory.create(mContext, mT9Chars, mT9Digits);

            Cursor contact = mContext.getContentResolver().query(
                    Contacts.CONTENT_URI, CONTACT_PROJECTION, CONTACT_QUERY,
                    null, CONTACT_SORT);
            Cursor phone = mContext.getContentResolver().query(
                    Phone.CONTENT_URI, PHONE_PROJECTION, PHONE_ID_SELECTION,
                    PHONE_ID_SELECTION_ARGS, PHONE_SORT);

            phone.moveToFirst();

            while (contact.moveToNext()) {
                long contactId = contact.getLong(CONTACT_COLUMN_ID);
                String contactName = contact.getString(CONTACT_COLUMN_NAME);
                int contactContactedCount = contact.getInt(CONTACT_COLUMN_CONTACTED);

                if (isCancelled()) {
                    break;
                }

                while (!phone.isAfterLast() && phone.getLong(PHONE_COLUMN_CONTACT) == contactId) {
                    String num = phone.getString(PHONE_COLUMN_NUMBER);
                    ContactItem contactInfo = new ContactItem();

                    contactInfo.id = contactId;
                    contactInfo.name = contactName;
                    contactInfo.number = PhoneNumberUtils.formatNumber(num);
                    contactInfo.normalNumber = removeNonDigits(num);
                    contactInfo.normalName = normalizer.convert(contactName);
                    contactInfo.timesContacted = contactContactedCount;
                    contactInfo.isSuperPrimary = phone.getInt(PHONE_COLUMN_PRIMARY) > 0;
                    contactInfo.groupType = Phone.getTypeLabel(mContext.getResources(),
                            phone.getInt(PHONE_COLUMN_TYPE), phone.getString(PHONE_COLUMN_LABEL));

                    String photoUri = contact.getString(CONTACT_COLUMN_PHOTO_URI);
                    if (photoUri != null) {
                        contactInfo.photo = Uri.parse(photoUri);
                    }

                    contacts.add(contactInfo);
                    phone.moveToNext();
                }
            }

            contact.close();
            phone.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mLoaded = true;
            mPrevInput = null;
            mAllResults.clear();
            mContacts = contacts;
            notifyLoadFinished();
        }
    }

    private void notifyLoadFinished() {
        for (Callback cb : mCallbacks) {
            cb.onLoadFinished();
        }
    }

    public static class T9SearchResult {

        private final ArrayList<ContactItem> mResults;
        private ContactItem mTopContact = new ContactItem();

        public T9SearchResult (final ArrayList<ContactItem> results) {
            mTopContact = results.get(0);
            mResults = results;
            mResults.remove(0);
        }

        public int getNumResults() {
            return mResults.size() + 1;
        }

        public ContactItem getTopContact() {
            return mTopContact;
        }

        public ArrayList<ContactItem> getResults() {
            return mResults;
        }
    }

    public static class ContactItem {
        Uri photo;
        String name;
        String number;
        String normalNumber;
        String normalName;
        int timesContacted;
        int nameMatchId;
        int numberMatchId;
        CharSequence groupType;
        long id;
        boolean isSuperPrimary;
    }

    public T9SearchResult search(String number) {
        if (!mLoaded) {
            return null;
        }

        number = removeNonDigits(number);

        int pos = 0;
        final ArrayList<ContactItem> numberResults = new ArrayList<ContactItem>();
        final ArrayList<ContactItem> nameResults = new ArrayList<ContactItem>();
        boolean newQuery = mPrevInput == null || number.length() <= mPrevInput.length();

        // Go through each contact
        for (ContactItem item : (newQuery ? mContacts : mAllResults)) {
            item.numberMatchId = -1;
            item.nameMatchId = -1;
            pos = item.normalNumber.indexOf(number);
            if (pos != -1) {
                item.numberMatchId = pos;
                numberResults.add(item);
            }
            pos = item.normalName.indexOf(number);
            if (pos != -1) {
                int lastSpace = item.normalName.lastIndexOf("0", pos);
                if (lastSpace == -1) {
                    lastSpace = 0;
                }
                item.nameMatchId = pos - lastSpace;
                nameResults.add(item);
            }
        }

        mAllResults.clear();
        mPrevInput = number;

        Collections.sort(numberResults, sNumberComparator);
        Collections.sort(nameResults, sNameComparator);

        if (nameResults.isEmpty() && numberResults.isEmpty()) {
            return null;
        }

        if (preferSortByName()) {
            mAllResults.addAll(nameResults);
            mAllResults.addAll(numberResults);
        } else {
            mAllResults.addAll(numberResults);
            mAllResults.addAll(nameResults);
        }

        return new T9SearchResult(new ArrayList<ContactItem>(mAllResults));
    }

    private boolean preferSortByName() {
        String mode = PreferenceManager.getDefaultSharedPreferences(mContext).getString("t9_sort", null);
        if (TextUtils.equals(mode, Integer.toString(NUMBER_FIRST))) {
            return false;
        }
        return true;
    }

    private static final Comparator<ContactItem> sNameComparator = new Comparator<ContactItem>() {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret = Integer.compare(lhs.nameMatchId, rhs.nameMatchId);
            if (ret == 0) ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret == 0) ret = Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
            return ret;
        }
    };

    private static final Comparator<ContactItem> sNumberComparator = new Comparator<ContactItem>() {
        @Override
        public int compare(ContactItem lhs, ContactItem rhs) {
            int ret = Integer.compare(lhs.numberMatchId, rhs.numberMatchId);
            if (ret == 0) ret = Integer.compare(rhs.timesContacted, lhs.timesContacted);
            if (ret == 0) ret = Boolean.compare(rhs.isSuperPrimary, lhs.isSuperPrimary);
            return ret;
        }
    };

    private void initT9Map() {
        StringBuilder bT9Chars = new StringBuilder();
        StringBuilder bT9Digits = new StringBuilder();

        for (String item : mContext.getResources().getStringArray(R.array.t9_map)) {
            bT9Chars.append(item);
            for (int i = 0; i < item.length(); i++) {
                bT9Digits.append(item.charAt(0));
            }
        }

        mT9Chars = bT9Chars.toString();
        mT9Digits = bT9Digits.toString();
    }

    private String removeNonDigits(String number) {
        int len = number.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = number.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '*' || ch == '#' || ch == '+') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public T9Adapter createT9Adapter(ArrayList<ContactItem> items, ContactPhotoManager photoLoader) {
        return new T9Adapter(items, photoLoader);
    }

    protected class T9Adapter extends ArrayAdapter<ContactItem> {

        private ArrayList<ContactItem> mItems;
        private LayoutInflater mInflater;
        private ContactPhotoManager mPhotoLoader;
        private View mLoadingView;

        protected T9Adapter(ArrayList<ContactItem> items, ContactPhotoManager photoLoader) {
            super(mContext, 0, items);
            mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mPhotoLoader = photoLoader;
            mItems = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (!mLoaded) {
                if (mLoadingView == null) {
                    mLoadingView = mInflater.inflate(R.layout.row_loading, null);
                }
                return mLoadingView;
            }

            if (convertView == null || convertView.getTag() == null) {
                convertView = mInflater.inflate(R.layout.row, null);
                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.rowName);
                holder.number = (TextView) convertView.findViewById(R.id.rowNumber);
                holder.icon = (QuickContactBadge) convertView.findViewById(R.id.rowBadge);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ContactItem o = mItems.get(position);
            if (o.name == null) {
                holder.name.setText(mContext.getResources().getString(R.string.t9_add_to_contacts));
                holder.number.setVisibility(View.GONE);
                holder.icon.setImageResource(R.drawable.ic_menu_add_field_holo_light);
                holder.icon.assignContactFromPhone(o.number, true);
            } else {
                SpannableStringBuilder nameBuilder = new SpannableStringBuilder();
                nameBuilder.append(o.name);
                if (o.nameMatchId != -1) {
                    int nameStart = o.normalName.indexOf(mPrevInput);
                    if (nameStart <= o.name.length() && nameStart + mPrevInput.length() <= o.name.length()) {
                        nameBuilder.setSpan(new ForegroundColorSpan(mHighlightColor),
                                nameStart, nameStart + mPrevInput.length(),
                                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                }

                SpannableStringBuilder numberBuilder = new SpannableStringBuilder();
                numberBuilder.append(o.normalNumber);
                numberBuilder.append(" (");
                numberBuilder.append(o.groupType);
                numberBuilder.append(")");
                if (o.numberMatchId != -1) {
                    int numberStart = o.numberMatchId;
                    numberBuilder.setSpan(new ForegroundColorSpan(mHighlightColor),
                            numberStart, numberStart + mPrevInput.length(),
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                }

                holder.name.setText(nameBuilder);
                holder.number.setText(numberBuilder);
                holder.number.setVisibility(View.VISIBLE);

                if (o.photo != null) {
                    mPhotoLoader.loadDirectoryPhoto(holder.icon, o.photo, true);
                } else {
                    holder.icon.setImageResource(
                            ContactPhotoManager.getDefaultAvatarResId(false, true));
                }
                holder.icon.assignContactFromPhone(o.number, true);
            }

            return convertView;
        }

        class ViewHolder {
            TextView name;
            TextView number;
            QuickContactBadge icon;
        }

    }

}
