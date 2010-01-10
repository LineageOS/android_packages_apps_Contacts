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


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Locale;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

// Geesun 
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.List;
import android.widget.Toast;


/**
 * Displays a list of call log entries.
 */
public class RecentCallsListActivity extends ListActivity
        implements View.OnCreateContextMenuListener {
    private static final String TAG = "RecentCallsList";

    /** The projection to use when querying the call log table */
    static final String[] CALL_LOG_PROJECTION = new String[] {
        Calls._ID, Calls.NUMBER, Calls.DATE, Calls.DURATION, Calls.TYPE,
        Calls.CACHED_NAME, Calls.CACHED_NUMBER_TYPE, Calls.CACHED_NUMBER_LABEL
    };

    static final int ID_COLUMN_INDEX = 0;
    static final int NUMBER_COLUMN_INDEX = 1;
    static final int DATE_COLUMN_INDEX = 2;
    static final int DURATION_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    static final int CALLER_NAME_COLUMN_INDEX = 5;
    static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;

    /** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
        Phones.PERSON_ID, Phones.DISPLAY_NAME, Phones.TYPE, Phones.LABEL,
        Phones.NUMBER
    };
    
    static final String[] PHONES_ID_PROJECTION = new String[] {
        Phones.PERSON_ID
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;

    private static final int MENU_ITEM_DELETE = 1;
    private static final int MENU_ITEM_DELETE_ALL = 2;
    private static final int MENU_ITEM_VIEW_CONTACTS = 3;
    private static final int MENU_ITEM_TOTAL_CALL_LOG = 4;
    private static final int MENU_ITEM_DELETE_ALL_NAME = 5; // Delete all instances of a particular user
    private static final int MENU_ITEM_DELETE_ALL_NUMBER = 6; // Delete all instances of a particular number
    private static final int MENU_ITEM_DELETE_ALL_INCOMING = 7;
    private static final int MENU_ITEM_DELETE_ALL_OUTGOING = 8;
    private static final int MENU_ITEM_DELETE_ALL_MISSED = 9;

    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;
    
    private static final String CACHEFILENAME = "idCache.dat";

    // RecentCallsAdapter mAdapter;
    // Geesun
    RecentCallsArrayAdapter mArrayAdapter;
    ArrayList<RecentCallsInfo> mListCallLogs = null;   
    private SharedPreferences prefs;
    
    ArrayList<String> numbersCache = null;
    ArrayList<Integer> numbersCachePosition = null;
    SparseArray<Integer> personIdCache = null;
    //SparseArray<Long> tempNumList = null;
    Hashtable<String,Long> tempNumList = null;
    
    private static final int MAX_INT = Integer.MAX_VALUE;
    
    private static int totalIncoming = 0;
    private static int totalOutgoing = 0;
    
    private QueryHandler mQueryHandler;
    String mVoiceMailNumber;
    
    private static boolean relativeTime;
    private static boolean is24hour;
    private static boolean showSeconds;
    private static final String format24HourSeconds = "MMM d, kk:mm:ss";
    private static final String format24Hour = "MMM d, kk:mm";
    private static final String format12HourSeconds = "MMM d, h:mm:ssaa";
    private static final String format12Hour = "MMM d, h:mmaa";
    private static boolean showDialButton;
    private static boolean showContactPic;
    private static boolean showNumber;
    private static boolean showLabel;
    private static boolean useExpGroup;
    
    private static boolean cacheUpdated;

    static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;

        public static ContactInfo EMPTY = new ContactInfo();
    }


    public static final class RecentCallsListItemViews {
        TextView line1View;
        TextView labelView;
        TextView numberView;
        TextView dateView;
        ImageView iconView;
        View dividerView;
        View callView;
        ImageView photoView;
        TextView countView;
    }


    static final class CallerInfoQuery {
        String number;
        int position;
        String name;
        int numberType;
        String numberLabel;
    }


    // Geesun 
    final class RecentCallsInfo {
        public String number;
        public int type;
        public String name;
        public int number_type;
        public String number_label;
        public long date;
        public int duration;
        public int count;
        public long personId;
        RecentCallsInfo() {
            count = 1;
        }
    }

    /**
     * Shared builder used by {@link #formatPhoneNumber(String)} to minimize
     * allocations when formatting phone numbers.
     */
    private static final SpannableStringBuilder sEditable = new SpannableStringBuilder();

    /**
     * Invalid formatting type constant for {@link #sFormattingType}.
     */
    private static final int FORMATTING_TYPE_INVALID = -1;

    /**
     * Cached formatting type for current {@link Locale}, as provided by
     * {@link PhoneNumberUtils#getFormatTypeForLocale(Locale)}.
     */
    private static int sFormattingType = FORMATTING_TYPE_INVALID;

    /** Adapter class to fill in data for the Call Log */
    // Geesun
    final class RecentCallsArrayAdapter extends ArrayAdapter<RecentCallsInfo>
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener {
        HashMap<String, ContactInfo> mContactInfo;
        private final LinkedList<CallerInfoQuery> mRequests;
        private volatile boolean mDone;
        private boolean mLoading = true;
        ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        private CharSequence[] mLabelArray;
        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null;      

        private Drawable mDrawableIncoming;
        private Drawable mDrawableOutgoing;
        private Drawable mDrawableMissed;
        // Geesun
        private int mLayout;
        private LayoutInflater mInflater;
        private Context mContext;
        List<RecentCallsInfo> mCallLogs;

        public void onClick(View view) {
            String number = (String) view.getTag();

            if (!TextUtils.isEmpty(number)) {
                Uri telUri = Uri.fromParts("tel", number, null);

                startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, telUri));
            }
        }

        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD, 1000);
                mFirst = false;
            }
            return true;
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case REDRAW:
                    notifyDataSetChanged();
                    break;

                case START_THREAD:
                    startRequestProcessing();
                    break;
                }
            }
        };

        public RecentCallsArrayAdapter(Context context, int textViewResourceId,
                List<RecentCallsInfo> objects) {
			
            super(context, R.layout.recent_calls_list_item, objects);
            mContext = context;
            mCallLogs = objects;
			
            mLayout = R.layout.recent_calls_list_item;
            mContactInfo = new HashMap<String, ContactInfo>();
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;

            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            mLabelArray = getResources().getTextArray(
                    com.android.internal.R.array.phoneTypes);
            
            mInflater = (LayoutInflater) context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
                    
            mBitmapCache = new SparseArray<SoftReference<Bitmap>>();

            // TODO Auto-generated constructor stub
        }

        public View getView(int position, View convertView, ViewGroup parent) {
	    	
            // Toast.makeText(RecentCallsListActivity.this, "Get View", Toast.LENGTH_LONG).show();
            View v;

            if (convertView == null) {
                v = newView(mContext, position, parent);	            
            } else {
                v = convertView;
            }
            
            bindView(v, mContext, position);
            return v;
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfo.get(number);
        }

        public void startRequestProcessing() {
            mDone = false;
            mCallerIdThread = new Thread(this);
            mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
            mCallerIdThread.start();
        }

        public void stopRequestProcessing() {
            mDone = true;
            if (mCallerIdThread != null) {
                mCallerIdThread.interrupt();
            }
            mHandler.removeMessages(START_THREAD);
        }

        public void clearCache() {
            synchronized (mContactInfo) {
                mContactInfo.clear();
            }
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
            if (TextUtils.equals(ciq.name, ci.name)
                    && TextUtils.equals(ciq.numberLabel, ci.label)
                    && ciq.numberType == ci.type) {
                return;
            }
            ContentValues values = new ContentValues(3);

            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);

            try {
                // Log.e(TAG,"Update call log, number = "+ciq.number );
                RecentCallsListActivity.this.getContentResolver().update(
                        Calls.CONTENT_URI, values,
                        Calls.NUMBER + " like '%" + ciq.number + "'", null);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception while updating call info", e);
            }
        }

        private void enqueueRequest(String number, int position,
                String name, int numberType, String numberLabel) {
            CallerInfoQuery ciq = new CallerInfoQuery();

            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
        }

        private void queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfo.get(ciq.number);

            if (info != null && info != ContactInfo.EMPTY) {
                synchronized (mRequests) {
                    if (mRequests.isEmpty()) {                    	
                        mHandler.sendEmptyMessage(REDRAW);
                    }
                }
            } else {

                Cursor phonesCursor = RecentCallsListActivity.this.getContentResolver().query(
                        Uri.withAppendedPath(Phones.CONTENT_FILTER_URL,
                        Uri.encode(ciq.number)),
                        PHONES_PROJECTION,
                        null,
                        null,
                        null);

                // Log.e(TAG, "number = " + number);
                if (phonesCursor != null) {
                    // Log.e(TAG, "phonesCursor != null ");
                    if (phonesCursor.moveToFirst()) {
                        // Log.e(TAG, "moveToFirst != null ");
                        info = new ContactInfo();
                        info.personId = phonesCursor.getLong(
                                PERSON_ID_COLUMN_INDEX);
                        info.name = phonesCursor.getString(NAME_COLUMN_INDEX);
                        info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
                        // Geesun
                        info.number = ciq.number; // phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);

                        // New incoming phone number invalidates our formatted
                        // cache. Any cache fills happen only on the GUI thread.
                        info.formattedNumber = null;

                        mContactInfo.put(ciq.number, info);
                        // Inform list to update this item, if in view
                        synchronized (mRequests) {
                            if (mRequests.isEmpty()) {
                                mHandler.sendEmptyMessage(REDRAW);
                            }
                        }
                    }
                    phonesCursor.close();
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        public void run() {
            while (!mDone) {
                CallerInfoQuery ciq = null;

                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {// Ignore and continue processing requests
                        }
                    }
                }
                if (ciq != null) {
                    queryContactInfo(ciq);
                }
            }
        }

        public View newView(Context context, int position, ViewGroup parent) {      
	            
            View view = mInflater.inflate(mLayout, parent, false);            
            // Get the views to bind to
            RecentCallsListItemViews views = new RecentCallsListItemViews();

            views.line1View = (TextView) view.findViewById(R.id.line1);
            views.labelView = (TextView) view.findViewById(R.id.label);
            views.numberView = (TextView) view.findViewById(R.id.number);
            views.dateView = (TextView) view.findViewById(R.id.date);
            views.iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            views.iconView.setOnClickListener(this);
            views.dividerView = view.findViewById(R.id.divider);
            views.callView = view.findViewById(R.id.call_icon);
            views.callView.setOnClickListener(this);
            views.photoView = (ImageView) view.findViewById(R.id.photo);
            views.countView = (TextView) view.findViewById(R.id.count);

            view.setTag(views);

            return view;
        }

        public void bindView(View view, Context context, int c) {	        	
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();
	            
            RecentCallsInfo callsinfo = getItem(c);
	            
            // Log.e(TAG," c = " + c + ",callsinfo.number =" + callsinfo.number + " Name =" + callsinfo.name);
	            
            String number = callsinfo.number;
            String formattedNumber = null;
            String callerName = callsinfo.name; // c.getString(CALLER_NAME_COLUMN_INDEX);
            int callerNumberType = callsinfo.number_type;
            String callerNumberLabel = callsinfo.number_label; // c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);
            int count = callsinfo.count;

            // Store away the number so we can call it directly if you click on the call icon
            views.callView.setTag(number);            
	            
            if (!showDialButton) {
                views.iconView.setTag(number);
                views.iconView.setOnClickListener(this);
                //views.iconView.setBackgroundResource(R.drawable.call_background);
            } else {
                views.iconView.setTag(null);
                views.iconView.setOnClickListener(null);
                //views.iconView.setBackgroundResource(0);
            }

            // Lookup contacts with this number
            ContactInfo info = mContactInfo.get(number);

            if (info == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                mContactInfo.put(number, info);
                //Log.e(TAG, "get infor NULL" + number);
                enqueueRequest(number, c, callerName, callerNumberType,
                        callerNumberLabel);
	                
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
	            	
                if (!TextUtils.equals(info.name, callerName)
                        || info.type != callerNumberType
                        || !TextUtils.equals(info.label, callerNumberLabel)) {
                    // Log.e(TAG,"info Name:" + info.name  + " CAll Name:" + callerName );
                    // Log.e(TAG,"info type:" + info.type  + " CAll Type:" + callerNumberType );
                    // Log.e(TAG,"info Label:" + info.label  + " CAll Label:" + callerNumberLabel );
                    // Something is amiss, so sync up.
                    enqueueRequest(number, c, callerName, callerNumberType,
                            callerNumberLabel);
                }
	              
                // Format and cache phone number for found contact
                if (info.formattedNumber == null) {
                    info.formattedNumber = formatPhoneNumber(info.number);
                }
                formattedNumber = info.formattedNumber;
            }
	            
            String name = info.name;
            int ntype = info.type;
            String label = info.label;

            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;

                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number);
            }
            // Set the text lines
            if (!TextUtils.isEmpty(name)) {
                if (count != 1) {
                    views.line1View.setText(name);
                    views.countView.setText("(" + count + ")");
                    views.countView.setVisibility(View.VISIBLE);
                } else {
                    views.line1View.setText(name);
                    views.countView.setVisibility(View.GONE);
                }
	            	
                CharSequence numberLabel = Phones.getDisplayLabel(context, ntype,
                        label, mLabelArray);
                        
                RelativeLayout.LayoutParams newLine1Layout = (RelativeLayout.LayoutParams) views.line1View.getLayoutParams();
                RelativeLayout.LayoutParams newNumberLayout = (RelativeLayout.LayoutParams) views.numberView.getLayoutParams();

                if (showNumber) {       
                    views.numberView.setVisibility(View.VISIBLE);
                    views.numberView.setText(formattedNumber);
                } else {
                    views.numberView.setVisibility(View.GONE);
                }                
                
                if (!TextUtils.isEmpty(numberLabel) && showLabel) {
                    views.labelView.setVisibility(View.VISIBLE);
                    views.labelView.setText(numberLabel);
                    
                    //Wysie_Soh: Set layout rules programmatically                    
                    newLine1Layout.addRule(RelativeLayout.ABOVE, R.id.label);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_BASELINE, R.id.label);
                    newNumberLayout.setMargins(5, 0, 0, 0);                    
                    
                    views.line1View.setLayoutParams(newLine1Layout);
                    views.numberView.setLayoutParams(newNumberLayout);
                    
                } else {
                    views.labelView.setVisibility(View.GONE);
                    
                    //Wysie_Soh: Set layout rules programmatically                    
                    newLine1Layout.addRule(RelativeLayout.ABOVE, R.id.number);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_BASELINE, 0);
                    newNumberLayout.setMargins(0, -10, 0, 8);                    
                    
                    views.line1View.setLayoutParams(newLine1Layout);
                    views.numberView.setLayoutParams(newNumberLayout);
                }
            } else {
                if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    number = getString(R.string.unknown);
                } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                    number = getString(R.string.private_num);
                } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    number = getString(R.string.payphone);
                } else if (number.equals(mVoiceMailNumber)) {
                    number = getString(R.string.voicemail);
                } else {
                    // Just a raw number, and no cache, so format it nicely
                    number = formatPhoneNumber(number);
                }
	                
                if (count != 1) {
                    views.line1View.setText(number);
                    views.countView.setText("(" + count + ")");
                    views.countView.setVisibility(View.VISIBLE);
                } else {
                    views.line1View.setText(number);
                    views.countView.setVisibility(View.GONE);
                }
	                
                views.numberView.setVisibility(View.GONE);
                views.labelView.setVisibility(View.GONE);
            }

            int type = callsinfo.type; // c.getInt(CALL_TYPE_COLUMN_INDEX);
            long date = callsinfo.date; // c.getLong(DATE_COLUMN_INDEX);            
	            
            if (relativeTime) {
                // Set the date/time field by mixing relative and absolute times.
                int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

                views.dateView.setText(
                        DateUtils.getRelativeTimeSpanString(date,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        flags));
            } else {
                String format = null;

                if (is24hour) {
                    if (showSeconds) {
                        format = format24HourSeconds;
                    } else {
                        format = format24Hour;
                    }
                } else {
                    if (showSeconds) {
                        format = format12HourSeconds;
                    } else {
                        format = format12Hour;
                    }                  	
                }
                
                views.dateView.setText(DateFormat.format(format, date));                         
            }
            
            if (showDialButton) {
                views.dividerView.setVisibility(View.VISIBLE);
                views.callView.setVisibility(View.VISIBLE);
            } else {
                views.dividerView.setVisibility(View.GONE);
                views.callView.setVisibility(View.GONE);
            }

            // Set the icon
            switch (type) {
            case Calls.INCOMING_TYPE:
                views.iconView.setImageDrawable(mDrawableIncoming);
                break;

            case Calls.OUTGOING_TYPE:
                views.iconView.setImageDrawable(mDrawableOutgoing);
                break;

            case Calls.MISSED_TYPE:
                views.iconView.setImageDrawable(mDrawableMissed);
                break;
            }

            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
            
            // Set the photo, if requested
            if (showContactPic) {
                
                int personId = (int)callsinfo.personId;
                Bitmap photo = null;                
                SoftReference<Bitmap> ref = mBitmapCache.get(personId);
                if (ref != null) {
                    photo = ref.get();
                }

                if (photo == null && personId != -1) {
                    try {
                        //int id = c.getInt(ID_COLUMN_INDEX);
                        Uri uri = ContentUris.withAppendedId(People.CONTENT_URI, personId);
                        photo = People.loadContactPhoto(context, uri, R.drawable.ic_contact_list_picture, null);
                        mBitmapCache.put(personId, new SoftReference<Bitmap>(photo));
                    } catch (OutOfMemoryError e) {
                        // Not enough memory for the photo, use the default one instead
                        photo = null;
                    } catch (IllegalArgumentException ile) {
                        photo = null;
                    }
                }

                // Bind the photo, or use the fallback no photo resource
                if (photo != null) {
                    views.photoView.setImageBitmap(photo);
                } else {
                    views.photoView.setImageResource(R.drawable.ic_contact_list_picture);
                }
                
                views.photoView.setVisibility(View.VISIBLE);
            }
            else {
                views.photoView.setVisibility(View.GONE);
            }            
        }

    }


    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<RecentCallsListActivity> mActivity;

        /**
         * Simple handler that wraps background calls to catch
         * {@link SQLiteException}, such as when the disk is full.
         */
        protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
            public CatchingWorkerHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    // Perform same query while catching any exceptions
                    super.handleMessage(msg);
                } catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteFullException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteDatabaseCorruptException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                }
            }
        }

        @Override
        protected Handler createHandler(Looper looper) {
            // Provide our special handler that catches exceptions
            return new CatchingWorkerHandler(looper);
        }

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<RecentCallsListActivity>(
                    (RecentCallsListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final RecentCallsListActivity activity = mActivity.get();

            if (activity != null && !activity.isFinishing()) {
                // final RecentCallsListActivity.RecentCallsAdapter callsAdapter = activity.mAdapter;
                final RecentCallsListActivity.RecentCallsArrayAdapter callsAdapter = activity.mArrayAdapter;

                callsAdapter.setLoading(false);
                activity.getUpdateCallLogsItem(cursor);
				   
                // callsAdapter.changeCursor(cursor);
            } else {
                cursor.close();
            }
        }
    }
    
    public void addItemIntoList(RecentCallsInfo item) {
        int numCacheSize = numbersCache.size();
        long personId = item.personId;
        String number = item.number;
        String name = item.name;
        RecentCallsInfo info = null;
        int position = -1;
        String currNum = null;
        
        if (personId == -1) {
            for (int i = 0; i < numCacheSize; i++) {
                currNum = numbersCache.get(i);
                if (currNum.equals(number) || PhoneNumberUtils.compare(currNum, number) || 
                    (useExpGroup && sloppyPhoneNumComparator(currNum, number))) {
                    mListCallLogs.get(numbersCachePosition.get(i)).count++;
                    return;
                }
            }
            
            mListCallLogs.add(item);
            numbersCache.add(item.number);
            numbersCachePosition.add(mListCallLogs.indexOf(item));            
        }
        else {
            try {
                position = personIdCache.get((int)personId);
            } catch (NullPointerException e) {
            }
            if (position == -1) {
                mListCallLogs.add(item);
                personIdCache.put((int)personId, mListCallLogs.indexOf(item));
            }
            else {
                info = mListCallLogs.get(position);
                if (info.personId == personId) {
                    if (info.name == null && name != null) {
                        info.name = name;
                        info.number_label = item.number_label;
                        info.number_type = item.number_type;
                    }                    			
                    info.count++;
                }
            }
        }

		/*
        for (int i = 0; i < mListCallLogs.size(); i++) {
            if ((item.personId != -1 && mListCallLogs.get(i).personId == item.personId) || 
                PhoneNumberUtils.compare(mListCallLogs.get(i).number, item.number) || 
                (prefs.getBoolean("cl_use_exp_grouping", false) && sloppyPhoneNumComparator(mListCallLogs.get(i).number, item.number))) {
                
                if (mListCallLogs.get(i).name == null && item.name != null) {
                    mListCallLogs.get(i).name = item.name;
                    mListCallLogs.get(i).number_label = item.number_label;
                    mListCallLogs.get(i).number_type = item.number_type;
                }
                    			
                mListCallLogs.get(i).count++;
                return;
            }
        }
        mListCallLogs.add(item);
        */
    }
    
    
    //Wysie_Soh: This method is written to make up for the current PhoneNumberUtils.compare
    //The main issues is for these 2 numbers, say, +6591234567 and 010891234567, they will
    //be detected as different numbers, although they are the same.
    //This method simply matches the last 8 digits and returns it as true if the last 7 digits matches.
    private boolean sloppyPhoneNumComparator(String num1, String num2) {
        int compareLength = Integer.parseInt(prefs.getString("cl_exp_grouping_num", "8"));
        
        if (num1.length() < compareLength || num2.length() < compareLength)
            return false;          
        
        String number1 = PhoneNumberUtils.stripSeparators(num1);
        String number2 = PhoneNumberUtils.stripSeparators(num2);        
        number1 = number1.substring(num1.length() - compareLength);
        number2 = number2.substring(num2.length() - compareLength);       

        if (number1.equals(number2))
            return true;
        else
            return false;
    }
    
    public void getUpdateCallLogsItem(Cursor cursor) {
        mListCallLogs.clear();
        numbersCache.clear();
        numbersCachePosition.clear();
        personIdCache.clear();
        if (cursor == null) {
            return;
        }
        
        Cursor phonesCursor = null;        
        int number = -1;
        cacheUpdated = false;
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            do { 
                RecentCallsInfo item = new RecentCallsInfo();

                item.number = cursor.getString(
                        RecentCallsListActivity.NUMBER_COLUMN_INDEX);
                item.type = cursor.getInt(
                        RecentCallsListActivity.CALL_TYPE_COLUMN_INDEX);
                item.name = cursor.getString(
                        RecentCallsListActivity.CALLER_NAME_COLUMN_INDEX);
                // Log.e(TAG,"Name =" + item.name + " number = " + item.number);
				
                item.number_label = cursor.getString(
                        RecentCallsListActivity.CALLER_NUMBERLABEL_COLUMN_INDEX);
                item.number_type = cursor.getInt(
                        RecentCallsListActivity.CALLER_NUMBERTYPE_COLUMN_INDEX);
                // Toast.makeText(RecentCallsListActivity.this, item.name + item.number_label, Toast.LENGTH_LONG).show();
                item.date = cursor.getLong(
                        RecentCallsListActivity.DATE_COLUMN_INDEX);
                item.duration = cursor.getInt(
                        RecentCallsListActivity.DURATION_COLUMN_INDEX);
                        
                //Wysie_Soh: The following portion of code to retrieve personId
                //really slows down the performance. However, without it,
                //call logs will not be grouped by contact, and instead will be grouped
                //by number (non-ideal). Thus, we make use of a Hashtable to keep
                //numbers and ids pairs, and avoid querying. This speeds it up significantly,
                //but it's still slower than default/without querying personId
                item.personId = -1;
                try {            
                    item.personId = tempNumList.get(item.number);
                } catch (NullPointerException e) {
                }

                if (item.personId == -1) {                
                    phonesCursor = getContentResolver().query(
                                    Uri.withAppendedPath(Phones.CONTENT_FILTER_URL,
                                    Uri.encode(item.number)), PHONES_ID_PROJECTION, null, null, null);
                
                    if (phonesCursor != null && phonesCursor.moveToFirst()) {
                        item.personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                        tempNumList.put(item.number, item.personId);
                        cacheUpdated = true;                        
                        phonesCursor.close();
                    }
                }

                addItemIntoList(item);
                
            } while (cursor.moveToNext());        
            cursor.close();
            
        }
        // Log.e(TAG,"getUpdateCallLogsItem");
        numbersCache.clear();
        numbersCachePosition.clear();
        personIdCache.clear();              
        mArrayAdapter.notifyDataSetChanged();
        writeCacheToFile();
		
    }
    
    static String numbersOnly(String s) {
        return s.replaceAll("[^0-9]","");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setContentView(R.layout.recent_calls);

        // Typing here goes to the dialer
        setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mListCallLogs = new ArrayList<RecentCallsInfo>();
        numbersCache =  new ArrayList<String>();
        numbersCachePosition = new ArrayList<Integer>();
        personIdCache = new SparseArray<Integer>();
        //tempNumList = new SparseArray<Long>(); //Wysie_Soh: This list is not cleared (on purpose). So resuming is faster.
        //tempNumList = new Hashtable<String, Long>();        
        // mAdapter = new RecentCallsAdapter();
        mArrayAdapter = new RecentCallsArrayAdapter(this,
                R.layout.recent_calls_list_item, mListCallLogs);
        getListView().setOnCreateContextMenuListener(this);
        // setListAdapter(mAdapter);

        setListAdapter(mArrayAdapter);
    
        mVoiceMailNumber = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getVoiceMailNumber();
        mQueryHandler = new QueryHandler(this);

        // Reset locale-based formatting cache
        sFormattingType = FORMATTING_TYPE_INVALID;       
    }

    @Override
    protected void onResume() {
        loadCacheFromFile();
        
        /*
        mListCallLogs.clear();
        numbersCache.clear();
        numbersCachePosition.clear();
        personIdCache.clear();
        */
        
        relativeTime = prefs.getBoolean("cl_relative_time", false);
        is24hour = DateFormat.is24HourFormat(this);
        showSeconds = prefs.getBoolean("cl_show_seconds", true);
        showDialButton = prefs.getBoolean("cl_show_dial_button", true);
        showContactPic = prefs.getBoolean("cl_show_pic", true);
        showNumber = prefs.getBoolean("cl_show_number", true);
        showLabel = prefs.getBoolean("cl_show_label", true);
        useExpGroup = prefs.getBoolean("cl_use_exp_grouping", false);
    
        // The adapter caches looked up numbers, clear it so they will get
        // looked up again.    	
        if (mArrayAdapter != null) {
            mArrayAdapter.clearCache();
        }
        
        /*
        if (prefs.getBoolean("cl_always_clear_cache", false) && tempNumList != null){
            tempNumList.clear();
        }
        */
        startQuery();
        resetNewCallsFlag();

        super.onResume();

        mArrayAdapter.mPreDrawListener = null; // Let it restart the thread after next draw

    }

    @Override
    protected void onPause() {
        super.onPause();

        // Kill the requests thread
        mArrayAdapter.stopRequestProcessing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mArrayAdapter.stopRequestProcessing();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Clear notifications only when window gains focus.  This activity won't
        // immediately receive focus if the keyguard screen is above it.
        if (hasFocus) {
            try {
                ITelephony iTelephony = ITelephony.Stub.asInterface(
                        ServiceManager.getService("phone"));

                if (iTelephony != null) {
                    iTelephony.cancelMissedCallsNotification();
                } else {
                    Log.w(TAG,
                            "Telephony service is null, can't call "
                            + "cancelMissedCallsNotification");
                }
            } catch (RemoteException e) {
                Log.e(TAG,
                        "Failed to clear missed calls notification due to remote exception");
            }
        }
    }

    /**
     * Format the given phone number using
     * {@link PhoneNumberUtils#formatNumber(android.text.Editable, int)}. This
     * helper method uses {@link #sEditable} and {@link #sFormattingType} to
     * prevent allocations between multiple calls.
     * <p>
     * Because of the shared {@link #sEditable} builder, <b>this method is not
     * thread safe</b>, and should only be called from the GUI thread.
     */
    private String formatPhoneNumber(String number) {
        // Cache formatting type if not already present
        if (sFormattingType == FORMATTING_TYPE_INVALID) {
            sFormattingType = PhoneNumberUtils.getFormatTypeForLocale(
                    Locale.getDefault());
        }

        sEditable.clear();
        sEditable.append(number);

        PhoneNumberUtils.formatNumber(sEditable, sFormattingType);
        return sEditable.toString();
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");

        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);

        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI, values,
                where.toString(), null);
    }

    private void startQuery() {
        mArrayAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI,
                CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_DELETE_ALL, 0, R.string.recentCalls_deleteAll).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_INCOMING, 0, R.string.recentCalls_deleteAllIncoming).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_OUTGOING, 0, R.string.recentCalls_deleteAllOutgoing).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_MISSED, 0, R.string.recentCalls_deleteAllMissed).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_TOTAL_CALL_LOG, 0, R.string.call_log_menu_total_duration).setIcon(
                R.drawable.ic_tab_recent);
        
        Intent i = new Intent(this, ContactsPreferences.class);

        menu.add(0, 0, 0, R.string.menu_preferences).setIcon(android.R.drawable.ic_menu_preferences).setIntent(
                i);
        
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        AdapterView.AdapterContextMenuInfo menuInfo;

        try {
            menuInfo = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return;
        }

        RecentCallsInfo item = mArrayAdapter.getItem(menuInfo.position);

        String number = item.number;
        Uri numberUri = null;
        boolean isVoicemail = false;

        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = getString(R.string.payphone);
        } else if (number.equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
            numberUri = Uri.parse("voicemail:x");
            isVoicemail = true;
        } else {
            numberUri = Uri.fromParts("tel", number, null);
        }

        ContactInfo info = mArrayAdapter.getContactInfo(number);
        boolean contactInfoPresent = (info != null && info != ContactInfo.EMPTY);

        if (contactInfoPresent) {
            menu.setHeaderTitle(info.name);
        } else {
            menu.setHeaderTitle(number);
        }

        if (numberUri != null) {        	
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberUri);

            menu.add(0, 0, 0, getResources().getString(R.string.recentCalls_callNumber, number)).setIntent(
                    intent);
        }

        if (contactInfoPresent) {
            menu.add(0, 0, 0, R.string.menu_viewContact).setIntent(
                    new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(People.CONTENT_URI, info.personId)));
        }

        if (numberUri != null && !isVoicemail) {
            menu.add(0, 0, 0, R.string.recentCalls_editNumberBeforeCall).setIntent(
                    new Intent(Intent.ACTION_DIAL, numberUri));
            menu.add(0, 0, 0, R.string.menu_sendTextMessage).setIntent(
                    new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts("sms", number, null)));
        }
        if (!contactInfoPresent && numberUri != null && !isVoicemail) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);

            intent.setType(People.CONTENT_ITEM_TYPE);
            intent.putExtra(Insert.PHONE, number);
            menu.add(0, 0, 0, R.string.recentCalls_addToContact).setIntent(
                    intent);
        }
        
        // menu.add(0, MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);        
        if (contactInfoPresent) {
            menu.add(0, MENU_ITEM_DELETE_ALL_NAME, 0, getString(R.string.menu_cl_clear_type, info.name));
        }
        else {
            menu.add(0, MENU_ITEM_DELETE_ALL_NUMBER, 0, getString(R.string.menu_cl_clear_type, number));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ITEM_DELETE_ALL: {
            clearCallLog();
            return true;
        }

        case MENU_ITEM_DELETE_ALL_INCOMING: {
            clearCallLogType(Calls.INCOMING_TYPE);
            return true;
        }

        case MENU_ITEM_DELETE_ALL_OUTGOING: {
            clearCallLogType(Calls.OUTGOING_TYPE);
            return true;
        }

        case MENU_ITEM_DELETE_ALL_MISSED: {
            clearCallLogType(Calls.MISSED_TYPE);
            return true;
        }

        case MENU_ITEM_TOTAL_CALL_LOG: {
            // Intent totalCallLog = new Intent(this, TotalCallLog.class);
            // setIntent(totalCallLog);
            // startActivity(totalCallLog);
            showTotalCallLog();
            return true;
        }

        case MENU_ITEM_VIEW_CONTACTS: {
            Intent intent = new Intent(Intent.ACTION_VIEW, People.CONTENT_URI);

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        }
        }
        return super.onOptionsItemSelected(item);
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        private String  mWhere;
        
        public DeleteClickListener(String where) {
            mWhere = where;
        }

        public void onClick(DialogInterface dialog, int which) {
            getContentResolver().delete(Calls.CONTENT_URI, mWhere, null);
            startQuery();
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // Convert the menu info to the proper type
        AdapterView.AdapterContextMenuInfo menuInfo;

        try {
            menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return false;
        }

        switch (item.getItemId()) {

        /*
         case MENU_ITEM_DELETE: {
         RecentCallsInfo item2 =  mArrayAdapter.getItem(menuInfo.position);
         if(item2 != null){
         StringBuilder where = new StringBuilder();
         where.append(Calls.NUMBER);
         where.append(" = '" + item2.number + "'");
         
         new AlertDialog.Builder(this)
         .setTitle(R.string.deleteConfirmation_title)
         .setIcon(android.R.drawable.ic_dialog_alert)
         .setMessage("Delete call log!")
         .setNegativeButton(android.R.string.cancel, null)
         .setPositiveButton(android.R.string.ok, new DeleteClickListener(where.toString()))
         .show();               
         
         //getContentResolver().delete(Calls.CONTENT_URI, where.toString(), null);
         //getContentResolver().delete(Calls.CONTENT_URI, null, where.toString());
         //TODO The change notification should do this automatically, but it isn't working
         // right now. Remove this when the change notification is working properly.
         //startQuery();
         
         }            	
         
         return true;
         }
         */
        case MENU_ITEM_DELETE_ALL_NUMBER: {
            RecentCallsInfo rci = mArrayAdapter.getItem(menuInfo.position);
            String number = rci.number;
            String label = null;
            	
            if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                label = getString(R.string.unknown);
            } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                label = getString(R.string.private_num);
            } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                label = getString(R.string.payphone);
            } else if (number.equals(mVoiceMailNumber)) {
                label = getString(R.string.voicemail);
            } else {
                label = number;
            }
            clearCallLogInstances(CallLog.Calls.NUMBER, number, label);
            
            return true;
        }
            
        case MENU_ITEM_DELETE_ALL_NAME: {
            RecentCallsInfo rci = mArrayAdapter.getItem(menuInfo.position);
            String number = rci.number;               
            	
            if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                number = getString(R.string.unknown);
            } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                number = getString(R.string.private_num);
            } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                number = getString(R.string.payphone);
            } else if (number.equals(mVoiceMailNumber)) {
                number = getString(R.string.voicemail);
            }
            	
            ContactInfo info = mArrayAdapter.getContactInfo(number);

            clearCallLogInstances(CallLog.Calls.CACHED_NAME, info.name,
                    info.name);
            return true;
        }            
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_CALL: {
            long callPressDiff = SystemClock.uptimeMillis()
                    - event.getDownTime();

            if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                // Launch voice dialer
                Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);

                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {}
                return true;
            }
        }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_CALL:
            try {
                ITelephony phone = ITelephony.Stub.asInterface(
                        ServiceManager.checkService("phone"));

                if (phone != null && !phone.isIdle()) {
                    // Let the super class handle it
                    break;
                }
            } catch (RemoteException re) {// Fall through and try to call the contact
            }

            callEntry(getListView().getSelectedItemPosition());
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    private String getBetterNumberFromContacts(String number) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        ContactInfo ci = mArrayAdapter.mContactInfo.get(number);

        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = RecentCallsListActivity.this.getContentResolver().query(
                        Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, number),
                        PHONES_PROJECTION, null, null, null);

                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(
                                MATCHED_NUMBER_COLUMN_INDEX);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {// Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber)
                && (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    private void callEntry(int position) {
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }

        if (mArrayAdapter.getCount() == 0) { 
            return;
        }

        RecentCallsInfo item = mArrayAdapter.getItem(position);
        
        // final Cursor cursor = mAdapter.getCursor();
        // if (cursor != null && cursor.moveToPosition(position)) {
        if (item != null) {
            // String number = cursor.getString(NUMBER_COLUMN_INDEX);
            String number = item.number;

            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)
                    || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }

            int callType = item.type; // cursor.getInt(CALL_TYPE_COLUMN_INDEX);

            if (!number.startsWith("+")
                    && (callType == Calls.INCOMING_TYPE
                            || callType == Calls.MISSED_TYPE)) {
                // If the caller-id matches a contact with a better qualified number, use it
                number = getBetterNumberFromContacts(number);
            }
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    Uri.fromParts("tel", number, null));

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	
        // Toast.makeText(this, "ON click" + mListCallLogs.get(position).number, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, CallDetailActivity.class);

        // intent.setData(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id));
        intent.putExtra("PERSONID", mListCallLogs.get(position).personId);
        intent.putExtra("NUMBER", mListCallLogs.get(position).number);
        startActivity(intent);
    }
    
	private String getShortestNumber(final String number) {
        String num = number;
        Uri callUri = Uri.withAppendedPath(Calls.CONTENT_FILTER_URI, Uri.encode(number));        
        Cursor callCursor = getContentResolver().query(callUri, CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
        		
        //Wysie_Soh: Loop to find shortest number, and requery.
        //For some reason, eg. if you have 91234567 and +6591234567 and say, 010891234567
        //they might not be detected as the same number. This is especially.
        //Might change to binary search in future.
        if (callCursor != null && callCursor.moveToFirst()) {
            do {
                if (number.length() > callCursor.getString(NUMBER_COLUMN_INDEX).length()) {
                    num = callCursor.getString(NUMBER_COLUMN_INDEX);
                }
            }while(callCursor.moveToNext());
        }
        
        return num;
	}

    
    //Wysie_Soh: WIP.
    //If you have say, 91234567 and +6591234567, and you select the
    //"Remove all 91234567" option, the +6591234567 will not be cleared.
    //A more obvious example would be 010891234567 and +6591234567
    private void clearCallLogInstances(final String type, final String value, final String label) { // Clear all instances of a user OR number
        final int compareLength = Integer.parseInt(prefs.getString("cl_exp_grouping_num", "8"));
    	
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(getString(R.string.alert_clear_cl_person, label));
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (type.equals(CallLog.Calls.NUMBER) && !(label.equals(getString(R.string.unknown)) ||
                        label.equals(getString(R.string.private_num)) || label.equals(getString(R.string.payphone)) ||
                        label.equals(getString(R.string.voicemail))) && value.length() >= compareLength && useExpGroup) {
                        String num = getShortestNumber(value);
                        deleteCallLog(type + " LIKE '%" + num + "'", null);
                    }
                    else {
                        deleteCallLog(type + "=?", new String[] { value });
                    }
                }
            });
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });
            alert.show();
        } else {
            if (type.equals(CallLog.Calls.NUMBER) && !(label.equals(getString(R.string.unknown)) ||
                label.equals(getString(R.string.private_num)) || label.equals(getString(R.string.payphone)) ||
                label.equals(getString(R.string.voicemail))) && value.length() >= compareLength && useExpGroup) {
                    String num = getShortestNumber(value);
                    deleteCallLog(type + " LIKE '%" + num + "'", null);
            }
            else {
                deleteCallLog(type + "=?", new String[] { value });
            }
        }
    	
    }
    
    private void clearCallLogType(final int type) {
        int msg = 0;
        
        if (type == Calls.INCOMING_TYPE) {
            msg = R.string.alert_clear_cl_all_incoming;
        } else if (type == Calls.OUTGOING_TYPE) {
            msg = R.string.alert_clear_cl_all_outgoing;
        } else if (type == Calls.MISSED_TYPE) {
            msg = R.string.alert_clear_cl_all_missed;
        }
        
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(msg);
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    deleteCallLog(Calls.TYPE + "=?", new String[] { Integer.toString(type) });
                }
            });        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });        
            alert.show();
            
        } else {
            deleteCallLog(Calls.TYPE + "=?", new String[] { Integer.toString(type) });
        }        
    }
    
    private void deleteCallLog(String where, String[] selArgs) {
        try {
            getContentResolver().delete(Calls.CONTENT_URI, where, selArgs);
            // TODO The change notification should do this automatically, but it isn't working
            // right now. Remove this when the change notification is working properly.
            startQuery();
        } catch (SQLiteException sqle) {// Nothing :P
        }
    }
    
    // Wysie_Soh: Dialog to confirm if user wants to clear call log    
    private void clearCallLog() {
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(R.string.alert_clear_call_log_message);
      
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    deleteCallLog(null, null);
                }
            });
        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });
        
            alert.show();
        } else {
            deleteCallLog(null, null);
        }
    }
    
    private void showTotalCallLog() {
        final Dialog dialog = new Dialog(this);

        dialog.setContentView(R.layout.total_call_log);
        dialog.setTitle(R.string.cl_total_cl);
        dialog.show();
    	
        calcTotalTime();
    	
        TextView incoming = (TextView) dialog.findViewById(R.id.total_in);

        incoming.setText(formatSecToMin(totalIncoming));
        TextView outgoing = (TextView) dialog.findViewById(R.id.total_out);

        outgoing.setText(formatSecToMin(totalOutgoing));

        Button buttonOK = (Button) dialog.findViewById(R.id.buttonOK);

        buttonOK.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }
    
    private String formatSecToMin(int s) {
        int min = s / 60;
        int sec = s % 60;
        String res = min + " mins " + sec + " secs";

        return res;
    }
    
    private void calcTotalTime() {
        totalIncoming = 0;
        totalOutgoing = 0;
        Cursor c = getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI, null, null, null,
                android.provider.CallLog.Calls.DEFAULT_SORT_ORDER);

        startManagingCursor(c);
    	
        int typeColumn = c.getColumnIndex(android.provider.CallLog.Calls.TYPE);
        int durationColumn = c.getColumnIndex(
                android.provider.CallLog.Calls.DURATION);
    	
        if (c.moveToFirst()) {
            do {
                int callType = c.getInt(typeColumn);
                int callDuration = c.getInt(durationColumn);
                    
                switch (callType) {
                case android.provider.CallLog.Calls.INCOMING_TYPE:
                    totalIncoming += callDuration;
                    break;

                case android.provider.CallLog.Calls.OUTGOING_TYPE:
                    totalOutgoing += callDuration;
                    break;
                }
            } while (c.moveToNext());
        }
    }
    
    //WYSIE TODO: Implement manual delete.
    
    //Wysie: The purpose is to save the existing tempNumList hashtable to a binary file
    //so that upon the next start, we can load from here directly, instead of querying
    //CONTENT_FILTER_URL which is very slow.
    private void writeCacheToFile() {
        if (!cacheUpdated)
            return;
        try {
            FileOutputStream fOut = openFileOutput(CACHEFILENAME, 0);
            ObjectOutputStream oOut = new ObjectOutputStream(fOut);
            oOut.writeObject(tempNumList);
            oOut.close();
            fOut.close();
            //Log.d("WYSIE", "FILE SAVE SUCCESS");
        }
        catch (FileNotFoundException e) {
            //Do nothing
        }
        catch (IOException e) {
            //Do nothing
        }
    }
    
    //Wysie: Load the Hashtable from file. Check EditContactActivity for the line
    //deleteFile(CACHEFILENAME). The current logic works by ALWAYS deleting the cached/saved file
    //as long as a contact info has been edited. This ensures that the call logs are always up to date.
    private void loadCacheFromFile() {
        try {
            FileInputStream fIn = openFileInput(CACHEFILENAME);
            ObjectInputStream oIn = new ObjectInputStream(fIn);
            tempNumList = (Hashtable<String,Long>)oIn.readObject();
            oIn.close();
            fIn.close();
            //Log.d("WYSIE", "FILE LOAD SUCCESS");
        }
        //In case of exceptions, create new tempNumList
        catch (ClassNotFoundException e) {
            tempNumList = new Hashtable<String,Long>();
            //Log.d("WYSIE", "FILE LOAD ERROR");
        }
        catch (FileNotFoundException e) {
            tempNumList = new Hashtable<String,Long>();
            //Log.d("WYSIE", "FILE LOAD ERROR");        
        }
        catch (IOException e) {
            tempNumList = new Hashtable<String,Long>();
            //Log.d("WYSIE", "FILE LOAD ERROR");
        }
    }
    
}
