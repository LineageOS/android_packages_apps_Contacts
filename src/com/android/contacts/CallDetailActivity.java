/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.internal.telephony.CallerInfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.Contacts;
import android.provider.CallLog.Calls;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 */
public class CallDetailActivity extends ListActivity implements View.OnCreateContextMenuListener/*, DialogInterface.OnClickListener*/ {
    private static final String TAG = "CallDetail";

    //private TextView mCallType;
    private TextView mCallNumber;
    private ImageView mCallTypeIcon;
    private TextView mCallTime;
    private TextView mCallDuration;
    //Geesun Add
    private int mNoPhotoResource;
    Uri personUri = null ;
    private ImageView mPhotoView;    
    
    private String mNumber = null;
    private static boolean hasAction = true;
    
    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    
    static final String[] CALL_LOG_PROJECTION = new String[] {
    	CallLog.Calls._ID,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
    };
    
    static final int LOG_COLUMN_INDEX = 0;
    static final int DATE_COLUMN_INDEX = 1;
    static final int DURATION_COLUMN_INDEX = 2;
    static final int NUMBER_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    
    static final String[] PHONES_PROJECTION = new String[] {
        Phones.PERSON_ID,
        Phones.DISPLAY_NAME,
        Phones.TYPE,
        Phones.LABEL,
        Phones.NUMBER,
    };
    static final int COLUMN_INDEX_ID = 0;
    static final int COLUMN_INDEX_NAME = 1;
    static final int COLUMN_INDEX_TYPE = 2;
    static final int COLUMN_INDEX_LABEL = 3;
    static final int COLUMN_INDEX_NUMBER = 4;
    
    private static final int MENU_ITEM_DELETE = 1;
    private static final int MENU_ITEM_DELETE_ALL_NAME = 2;
    private static final int MENU_ITEM_DELETE_ALL_INCOMING = 3;
    private static final int MENU_ITEM_DELETE_ALL_OUTGOING = 4;
    private static final int MENU_ITEM_DELETE_ALL_MISSED = 5;
    private static final int MENU_ITEM_VIEW_CONTACT = 6;
    private static final int MENU_ITEM_ADD_CONTACT = 7;
    private static final int MENU_ITEM_DELETE_ALL_NUMBER = 8;
    
    private static final int MENU_ITEM_CALL = 9;
    private static final int MENU_COPY_NUM = 10;
    
    private ViewAdapter adapter;
    private List<ViewEntryData> logs;
    private SharedPreferences prefs;
    private static String displayName;
    private static long personId;
    private static String mVoiceMailNumber;
    private static boolean isRequery = false;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();    

        mPhotoView = (ImageView) findViewById(R.id.photo);
        
        getListView().setOnCreateContextMenuListener(this);
          
        // Set the photo with a random "no contact" image
        long now = SystemClock.elapsedRealtime();
        int num = (int) now & 0xf;
        if (num < 9) {
            // Leaning in from right, common
            mNoPhotoResource = R.drawable.ic_contact_picture;
        } else if (num < 14) {
            // Leaning in from left uncommon
            mNoPhotoResource = R.drawable.ic_contact_picture_2;
        } else {
            // Coming in from the top, rare
            mNoPhotoResource = R.drawable.ic_contact_picture_3;
        }
        
        mVoiceMailNumber = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getVoiceMailNumber();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        isRequery = false;
        updateData();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)
                        getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {

                    //Geesun
                    String phoneNum = mNumber;
                    Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", phoneNum, null));
                    startActivity(callIntent);
                    return true;
                }
            }
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    class ViewEntryData{
    	public long id;
    	public String number;
    	public String label;
    	public long date;
    	public long duration;
    	public int callType;
    }
    
    //Class used to sort
    class NumberInfo {
        public String number;
        public String label;
        
        NumberInfo(String n, String l) {
            number = n;
            label = l;
        }
    }
    
    class LogsSortByTime implements Comparator<ViewEntryData>{
        public int compare(ViewEntryData v1, ViewEntryData v2) {
            Long l1 = v1.date;
            Long l2 = v2.date;
            
            return l2.compareTo(l1);
        }
    }
    
    /**
     * Update user interface with details of given call.
     * 
     * @param callUri Uri into {@link CallLog.Calls}
     */
    private void updateData() {
    	Bundle bundle = getIntent().getExtras();
    	personId = bundle.getLong("PERSONID");
    	String number = bundle.getString("NUMBER");
    	ArrayList<NumberInfo> personNumbers = new ArrayList<NumberInfo>();
    	personUri = null;
    	Uri callUri = null;
    	Cursor callCursor = null;
    	logs = new ArrayList<ViewEntryData>();    	
    	
    	//Wysie_Soh: If the personId is valid
    	if (personId > 0) {
    	    personUri = ContentUris.withAppendedId(People.CONTENT_URI, personId);
    	    Uri phonesUri = Uri.withAppendedPath(personUri, People.Phones.CONTENT_DIRECTORY);
            final Cursor phonesCursor = getContentResolver().query(phonesUri, PHONES_PROJECTION, null, null, null);
            displayName = null;
            
            if (phonesCursor != null && phonesCursor.moveToFirst()) {
                int type = -1;
                String label = null;
                CharSequence actualLabel = null;
                do {
                    if (displayName == null)
                        displayName = phonesCursor.getString(COLUMN_INDEX_NAME);
                    type = phonesCursor.getInt(COLUMN_INDEX_TYPE);
                    number = phonesCursor.getString(COLUMN_INDEX_NUMBER);
                    label = phonesCursor.getString(COLUMN_INDEX_LABEL);
                    actualLabel = Phones.getDisplayLabel(this, type, label);
                    personNumbers.add(new NumberInfo(number, actualLabel.toString()));
                } while (phonesCursor.moveToNext());
                
                phonesCursor.close();
            }
            /*
            else {
                //Toast.makeText(this, R.string.toast_call_detail_error_wysie, Toast.LENGTH_SHORT).show();
                Toast.makeText(this, "Testing 123", Toast.LENGTH_SHORT).show();
                this.finish();
                return;
            }
            */
            
            for (NumberInfo i : personNumbers) {
                callUri = Uri.withAppendedPath(Calls.CONTENT_FILTER_URI, Uri.encode(i.number));
                callCursor = getContentResolver().query(callUri, CALL_LOG_PROJECTION, null, null, null);
                
                try {                
                    if (callCursor != null && callCursor.moveToFirst()) {
                        do {
                            ViewEntryData data = new ViewEntryData();
                            data.label = i.label;
                    		data.id = callCursor.getLong(LOG_COLUMN_INDEX);
                       		data.date = callCursor.getLong(DATE_COLUMN_INDEX);
                    		data.duration = callCursor.getLong(DURATION_COLUMN_INDEX);
                    		data.callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
                    		data.number = callCursor.getString(NUMBER_COLUMN_INDEX);	                
                    		logs.add(data);
                        } while(callCursor.moveToNext());
                    }
                }
                finally {
                if (callCursor != null)
                    callCursor.close();
                }
            }
    	}
    	//Wysie_Soh: If no person found, query by number instead
    	else {
    	    int num = 0;
    	    
    	    try {
    	        num = Integer.parseInt(number);
    	    }
    	    catch (NumberFormatException e) {
    	        //Nothing
    	    }
    	        	    
    	    if (num < 0) {
    	        callCursor = getContentResolver().query(Calls.CONTENT_URI, CALL_LOG_PROJECTION, Calls.NUMBER + "=?", new String[] { number }, null);
    	    }
    	    else {    	        	
        	    callUri = Uri.withAppendedPath(Calls.CONTENT_FILTER_URI, Uri.encode(number));
        	    callCursor = getContentResolver().query(callUri, CALL_LOG_PROJECTION, null, null, null);
    	    
                //Wysie_Soh: Loop to find shortest number.
                //For some reason, eg. if you have 91234567 and +6591234567 and say, 010891234567
                //they might not be detected as the same number. This is especially.
                //Might change to binary search in future.
                try {
                    if (callCursor != null && callCursor.moveToFirst()) {
                        do {
                            if (number.length() > callCursor.getString(NUMBER_COLUMN_INDEX).length()) {
                                number = callCursor.getString(NUMBER_COLUMN_INDEX);
                            }
                        } while(callCursor.moveToNext());
                    }
                }
                finally {
                    if (callCursor != null)
                        callCursor.close();
                }
                
                //Wysie_Soh:Requery with shortest number found. This will ensure we get all entries.
                callUri = Uri.withAppendedPath(Calls.CONTENT_FILTER_URI, Uri.encode(number));
                callCursor = getContentResolver().query(callUri, CALL_LOG_PROJECTION, null, null, null);
            }
            
            try {
                if (callCursor != null && callCursor.moveToFirst()) {
                    do {
                		ViewEntryData data = new ViewEntryData();
	                    // Read call log specifics
                		data.id = callCursor.getLong(LOG_COLUMN_INDEX);
                		data.date = callCursor.getLong(DATE_COLUMN_INDEX);
                		data.duration = callCursor.getLong(DURATION_COLUMN_INDEX);
                		data.callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
                		data.number = callCursor.getString(NUMBER_COLUMN_INDEX);            		
	                
                		logs.add(data);	              
                	} while(callCursor.moveToNext());
                }
            } finally {
                if (callCursor != null)
                    callCursor.close();
            }
    	}    	
    	
    	//Sort arraylist here
    	Collections.sort(logs ,new LogsSortByTime());
    	
    	try {    	    	
            mNumber = logs.get(0).number;
        }
        catch (IndexOutOfBoundsException e) { //If logs is size 0, no content, exit.
            if (!isRequery) {
                Toast.makeText(this, R.string.toast_call_detail_error_wysie, Toast.LENGTH_SHORT).show();
            }
            //Toast.makeText(this, "Testing 789", Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }       

    	
    	if (mNumber != null) {            
        	TextView tvName = (TextView) findViewById(R.id.name);

    	    if (mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                    mNumber.equals(CallerInfo.PRIVATE_NUMBER) || mNumber.equals(CallerInfo.PAYPHONE_NUMBER) || mNumber.equals(mVoiceMailNumber)) {
                // List is empty, let the empty view show instead.
                //TextView emptyText = (TextView) findViewById(R.id.emptyText);                
                hasAction = false;               
                
                //if (emptyText != null) {
                    displayName = getString(R.string.unknown);
                    if (mNumber.equals(CallerInfo.PRIVATE_NUMBER)) {
                        displayName = getString(R.string.private_num);                            
                    }
                    else if (mNumber.equals(CallerInfo.PAYPHONE_NUMBER)) {
                        displayName = getString(R.string.payphone);                            
                    }
                    else if (mNumber.equals(mVoiceMailNumber)) {
                        displayName = getString(R.string.voicemail);
                        hasAction = true;
                    }
                //}
                //emptyText.setText(displayName);
                tvName.setText(displayName);
                mPhotoView.setImageResource(mNoPhotoResource);                
            } else {
                if (personId > 0) {
                    tvName.setText(displayName);
                    mPhotoView.setImageBitmap(People.loadContactPhoto(this, personUri, mNoPhotoResource, null /* use the default options */));
                }
                else {
                    tvName.setText("("+ getString(R.string.unknown) + ")");
                    mPhotoView.setImageResource(mNoPhotoResource);
                }
                hasAction = true;
            }
            
            if (hasAction) {
                ViewEntryData firstPlaceHolder = new ViewEntryData();
                firstPlaceHolder.number = mNumber;
                firstPlaceHolder.label = logs.get(0).label;
                logs.add(0, firstPlaceHolder);
            }
            
            int size = Integer.parseInt(prefs.getString("cl_view_contact_pic_size", "78"));
            //Wysie_Soh: Set contact picture size
            mPhotoView.setLayoutParams(new LinearLayout.LayoutParams(size, size));    	
    
            adapter = new ViewAdapter(this, logs);
            setListAdapter(adapter);
        }         
    }

    static final class ViewEntry {
        public int icon = -1;
        public String text = null;
        public Intent intent = null;
        public String label = null;
        public String number = null;
        
        public ViewEntry(int icon, String text, Intent intent) {
            this.icon = icon;
            this.text = text;
            this.intent = intent;
        }
    }

    static final class ViewAdapter extends BaseAdapter 
    	implements View.OnClickListener 
    	{
        
        private final List<ViewEntryData> mLogs;
        
        private final LayoutInflater mInflater;
        
        private Context mContext;
        
        public ViewAdapter(Context context, List<ViewEntryData> logs) {
        	mLogs = logs;
            mContext = context;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        
        private String formatDuration(long elapsedSeconds) {
            long minutes = 0;
            long seconds = 0;

            if (elapsedSeconds >= 60) {
                minutes = elapsedSeconds / 60;
                elapsedSeconds -= minutes * 60;
            }
            seconds = elapsedSeconds;

            return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
        }
        
        public int getCount() {
            return mLogs.size();
        }

        public Object getItem(int position) {
            return mLogs.get(position);
        }

        public long getItemId(int position) {
            return position;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            // Make sure we have a valid convertView to start with
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.call_detail_list_item, parent, false);
            }
            
            // Fill action with icon and text.
             ViewEntryData entry = mLogs.get(position);
        	 long date = entry.date;
        	 long duration = entry.duration;
        	 int callType = entry.callType; 
        	 String num = entry.number;
        	 final String label = entry.label;

            
        	RelativeLayout layout = (RelativeLayout) convertView.findViewById(R.id.line_action);
            LinearLayout layout_logs = (LinearLayout) convertView.findViewById(R.id.line_log);
            
            if(position == 0 && hasAction) {
                if (num.equals(CallerInfo.PRIVATE_NUMBER) || num.equals(CallerInfo.UNKNOWN_NUMBER) ||
                    num.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    layout.setVisibility(View.GONE);
                } else {
                    layout.setVisibility(View.VISIBLE);
                }
            	layout_logs.setVisibility(View.GONE);
                ImageView call_icon = (ImageView) convertView.findViewById(R.id.call_icon);
                ImageView sms_icon = (ImageView) convertView.findViewById(R.id.sms_icon);
                TextView tvCall = (TextView) convertView.findViewById(R.id.call);
                TextView tvNumLabel = (TextView) convertView.findViewById(R.id.most_recent_num_label);
                TextView tvNum = (TextView) convertView.findViewById(R.id.most_recent_num);
               
                //Geesun                
                Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                        Uri.fromParts("tel", entry.number, null));
                
                Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("sms", entry.number, null));
                
                sms_icon.setImageResource(R.drawable.sym_action_sms);
                call_icon.setImageResource(android.R.drawable.sym_action_call);
               
                ViewEntryData firstData = mLogs.get(1);
           	 
	           	switch(firstData.callType){
		           	case Calls.INCOMING_TYPE:
		           		tvCall.setText(mContext.getString(R.string.returnCall));
		           		break;
		           	case Calls.OUTGOING_TYPE:
		           		tvCall.setText(mContext.getString(R.string.callAgain));
		           		break;
		           	case Calls.MISSED_TYPE:
		           		tvCall.setText(mContext.getString(R.string.callBack));
		           		break;
	           	 }
	           	 
                RelativeLayout.LayoutParams newCallLayout = (RelativeLayout.LayoutParams) tvCall.getLayoutParams();
                RelativeLayout.LayoutParams newNumberLayout = (RelativeLayout.LayoutParams) tvNum.getLayoutParams();
	           	 	           	 
	           	if (label != null) {
	           	    tvNumLabel.setText(label);
	           	    tvNumLabel.setVisibility(View.VISIBLE);
                    newCallLayout.addRule(RelativeLayout.ABOVE, R.id.most_recent_num_label);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_BASELINE, R.id.most_recent_num_label);
                    newNumberLayout.setMargins(5, 0, 0, 0);
	           	}
	           	else {
	           	    tvNumLabel.setVisibility(View.GONE);
                    newCallLayout.addRule(RelativeLayout.ABOVE, R.id.most_recent_num);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    newNumberLayout.addRule(RelativeLayout.ALIGN_BASELINE, 0);
                    newNumberLayout.setMargins(0, -10, 0, 8);
	           	}
	           	
                tvCall.setLayoutParams(newCallLayout);
                tvNum.setLayoutParams(newNumberLayout);
                            
                if (num.equals(CallerInfo.PRIVATE_NUMBER)) {
                    num = mContext.getString(R.string.private_num);
                }
                else if (num.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    num = mContext.getString(R.string.unknown);
                }
                else if (num.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    num = mContext.getString(R.string.payphone);                            
                }
	           	
	           	tvNum.setText(num);
                
                call_icon.setTag(callIntent);
                //tvCall.setTag(callIntent);
                sms_icon.setTag(smsIntent);
                call_icon.setOnClickListener(this);                
                sms_icon.setOnClickListener(this);
                //tvCall.setOnClickListener(this); 
                
                convertView.setTag(null);
                
            }else{                
            	layout.setVisibility(View.GONE);
            	layout_logs.setVisibility(View.VISIBLE);
                ImageView mCallTypeIcon = (ImageView) convertView.findViewById(R.id.icon);
                
                if (hasAction) {
                    mCallTypeIcon.setOnClickListener(this);
                    mCallTypeIcon.setTag(num);
                }
                TextView tvLabel = (TextView) convertView.findViewById(R.id.label);
                TextView tvTime = (TextView) convertView.findViewById(R.id.time);
                TextView tvDuration = (TextView) convertView.findViewById(R.id.duration);
                //TextView tvType = (TextView) convertView.findViewById(R.id.type);
                TextView tvNum = (TextView) convertView.findViewById(R.id.type);
                // Pull out string in format [relative], [date]
                CharSequence dateClause = DateUtils.formatDateRange(mContext, date, date,
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | 
                        DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_ABBREV_WEEKDAY);
                tvTime.setText(dateClause);
                
                // Set the duration
                if (callType == Calls.MISSED_TYPE) {
                	tvDuration.setVisibility(View.GONE);
                } else {
                	tvDuration.setVisibility(View.VISIBLE);
                	tvDuration.setText(formatDuration(duration));
                }
    
                // Set the call type icon          
                switch (callType) {
                    case Calls.INCOMING_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_incoming_call);
                        //tvType.setText(R.string.type_incoming);
                       
                        break;
    
                    case Calls.OUTGOING_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_outgoing_call);
                        //tvType.setText(R.string.type_outgoing);
                        
                        break;
    
                    case Calls.MISSED_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_missed_call);
                        //tvType.setText(R.string.type_missed);
                        
                        break;
                }
                
                if (num.equals(CallerInfo.PRIVATE_NUMBER)) {
                    num = mContext.getString(R.string.private_num);
                }
                else if (num.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    num = mContext.getString(R.string.unknown);
                }
                else if (num.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    num = mContext.getString(R.string.payphone);                            
                }
                
                if (entry.label == null) {
                    tvLabel.setVisibility(View.GONE);
                }
                else {
                    tvLabel.setText(entry.label);
                    tvLabel.setVisibility(View.VISIBLE);
                }
                
                tvNum.setText(num);
                                
                convertView.setTag(entry);
            }
            
            return convertView;
        }
        
        //Wysie_Soh: Only the calltype and the icons in the first row have onclick events. In case of the first row,
        //try will succeed. Otherwise it will go to catch.
		public void onClick(View v) {
		    Intent intent = null;
		    
		    try {
                intent = (Intent) v.getTag();
            }
            catch (ClassCastException e) {
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                        Uri.fromParts("tel", (String)v.getTag(), null));
            }
            
            if(intent != null)
                mContext.startActivity(intent);
		}
    }   

    
    public boolean onCreateOptionsMenu(Menu menu) {
        if (personId > 0)
            menu.add(0, MENU_ITEM_DELETE_ALL_NAME, 0, R.string.recentCalls_deleteAll).setIcon(
                    android.R.drawable.ic_menu_close_clear_cancel);
        else
            menu.add(0, MENU_ITEM_DELETE_ALL_NUMBER, 0, R.string.recentCalls_deleteAll).setIcon(
                    android.R.drawable.ic_menu_close_clear_cancel);  


        menu.add(0, MENU_ITEM_DELETE_ALL_INCOMING, 0, R.string.recentCalls_deleteAllIncoming).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_OUTGOING, 0, R.string.recentCalls_deleteAllOutgoing).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_MISSED, 0, R.string.recentCalls_deleteAllMissed).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
       
    
        if (personUri != null) {
            menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
            	.setIcon(R.drawable.ic_tab_contacts); 
        } else if (!(mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                mNumber.equals(CallerInfo.PRIVATE_NUMBER) || mNumber.equals(CallerInfo.PAYPHONE_NUMBER))) {
            menu.add(0, MENU_ITEM_ADD_CONTACT, 0, R.string.recentCalls_addToContact)
            .setIcon(android.R.drawable.ic_menu_add);
        }

        return true;
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position == 0) {
            Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                        Uri.fromParts("tel", mNumber, null));
            startActivity(callIntent);
        }        
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ITEM_DELETE_ALL_NAME: {
            clearCallLogInstances(CallLog.Calls.CACHED_NAME, displayName, displayName); 
            return true;
        }
        case MENU_ITEM_DELETE_ALL_NUMBER: {
            String label = null;
            
            if (mNumber.equals(CallerInfo.UNKNOWN_NUMBER)) {
                label = getString(R.string.unknown);
            } else if (mNumber.equals(CallerInfo.PRIVATE_NUMBER)) {
                label = getString(R.string.private_num);
            } else if (mNumber.equals(CallerInfo.PAYPHONE_NUMBER)) {
                label = getString(R.string.payphone);
            } else if (mNumber.equals(mVoiceMailNumber)) {
                label = getString(R.string.voicemail);
            } else {
                label = mNumber;
            }
            clearCallLogInstances(CallLog.Calls.NUMBER, mNumber, label);
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
        case MENU_ITEM_VIEW_CONTACT: {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, personUri);
            startActivity(viewIntent);
            return true;
        }
        case MENU_ITEM_ADD_CONTACT: {
            Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            createIntent.setType(People.CONTENT_ITEM_TYPE);
            createIntent.putExtra(Insert.PHONE, mNumber);
            startActivity(createIntent);
            return true;
        }
        }
        return super.onOptionsItemSelected(item);

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
        if (menuInfo.position == 0 && hasAction) {
            return;
        }
        
        ViewEntryData entryData = (ViewEntryData)adapter.getItem(menuInfo.position);
        
        //Wysie_Soh: WIP
        String number = entryData.number;
        
        if (!(mNumber.equals(CallerInfo.UNKNOWN_NUMBER) || mNumber.equals(CallerInfo.PRIVATE_NUMBER) 
            || mNumber.equals(CallerInfo.PAYPHONE_NUMBER))) {
            menu.add(0, MENU_ITEM_CALL, 0, getString(R.string.recentCalls_callNumber, number));
            menu.add(0, MENU_COPY_NUM, 0, getString(R.string.menu_copy_string, number));
        }
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);
        
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = getString(R.string.payphone);
        } else if (number.equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
        }
        
        menu.add(0, MENU_ITEM_DELETE_ALL_NUMBER, 0, getString(R.string.menu_cl_clear_type, number));
    }
    
	public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        ViewEntryData entryData = (ViewEntryData)adapter.getItem(info.position);
        String number = entryData.number;
	
		switch(item.getItemId()) {
		    case MENU_ITEM_CALL:
                Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", number, null));
                startActivity(callIntent);
                return true;
            case MENU_COPY_NUM:
                if (entryData != null) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(number);
                 }
		    case MENU_ITEM_DELETE:
		        deleteCallLog("Calls._ID=?", new String[] { Long.toString(entryData.id) } );
                return true;
            case MENU_ITEM_DELETE_ALL_NUMBER:
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
        
		return super.onContextItemSelected(item);
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
	
    private void clearCallLogInstances(final String type, final String value, final String label) { // Clear all instances of a user OR number
        final int compareLength = Integer.parseInt(prefs.getString("cl_exp_grouping_num", "8"));
        final boolean useExpGroup = prefs.getBoolean("cl_use_exp_grouping", false);
        
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
            alert.setNegativeButton(android.R.string.cancel, null);
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
    
    private void deleteCallLog(String where, String[] selArgs) {
        try {
            getContentResolver().delete(Calls.CONTENT_URI, where, selArgs);
            isRequery = true;
            updateData();
        } catch (SQLiteException sqle) {// Nothing :P
        }
    }

    private void clearCallLogType(final int type) {
        final int compareLength = Integer.parseInt(prefs.getString("cl_exp_grouping_num", "8"));
        final boolean useExpGroup = prefs.getBoolean("cl_use_exp_grouping", false);
        
        String message = null;        

        if (personId > 0) {
            if (type == Calls.INCOMING_TYPE) {
                message = getString(R.string.alert_clear_cl_person_incoming, displayName);
            } else if (type == Calls.OUTGOING_TYPE) {
                message = getString(R.string.alert_clear_cl_person_outgoing, displayName);
            } else if (type == Calls.MISSED_TYPE) {
                message = getString(R.string.alert_clear_cl_person_missed, displayName);
            }
        }
        else {
            String label = null;
            if (mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                    mNumber.equals(CallerInfo.PRIVATE_NUMBER) || mNumber.equals(CallerInfo.PAYPHONE_NUMBER) || mNumber.equals(mVoiceMailNumber)) {
                label = displayName;
            }
            else {
                label = mNumber;
            }
            if (type == Calls.INCOMING_TYPE) {
                message = getString(R.string.alert_clear_cl_person_incoming, label);
            } else if (type == Calls.OUTGOING_TYPE) {
                message = getString(R.string.alert_clear_cl_person_outgoing, label);
            } else if (type == Calls.MISSED_TYPE) {
                message = getString(R.string.alert_clear_cl_person_missed, label);
            }
        }
        
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(message);
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    if (personId > 0) {                
                        deleteCallLog(Calls.TYPE + "=? AND " + Calls.CACHED_NAME + "=?", new String[] { Integer.toString(type), displayName });
                    }
                    else {
                        if (!(displayName.equals(getString(R.string.unknown)) || displayName.equals(getString(R.string.private_num)) || 
                            displayName.equals(getString(R.string.payphone)) || displayName.equals(getString(R.string.voicemail))) 
                            && mNumber.length() >= compareLength && useExpGroup) {
                            
                            String num = getShortestNumber(mNumber);
                            deleteCallLog(Calls.TYPE + "=? AND " + Calls.NUMBER + " LIKE '%" + num + "'", new String[] { Integer.toString(type) });
                        }
                        else {      
                            deleteCallLog(Calls.TYPE + "=? AND " + Calls.NUMBER + "=?", new String[] { Integer.toString(type), mNumber });
                        }
                    }
                }
            });                
            alert.setNegativeButton(android.R.string.cancel, null);
            alert.show();
            
        } else {

            if (personId > 0) {                
                deleteCallLog(Calls.TYPE + "=? AND " + Calls.CACHED_NAME + "=?", new String[] { Integer.toString(type), displayName });
            }
            else {
                if (!(displayName.equals(getString(R.string.unknown)) || displayName.equals(getString(R.string.private_num)) || 
                    displayName.equals(getString(R.string.payphone)) || displayName.equals(getString(R.string.voicemail))) 
                    && mNumber.length() >= compareLength && useExpGroup) {
                    
                    String num = getShortestNumber(mNumber);
                    deleteCallLog(Calls.TYPE + "=? AND " + Calls.NUMBER + " LIKE '%" + num + "'", new String[] { Integer.toString(type) });
                }
                else {      
                    deleteCallLog(Calls.TYPE + "=? AND " + Calls.NUMBER + "=?", new String[] { Integer.toString(type), mNumber });
                }
            }
        }
    }
      

}
