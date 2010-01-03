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
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 */
public class CallDetailActivity extends ListActivity implements View.OnCreateContextMenuListener {
    private static final String TAG = "CallDetail";

    private TextView mCallType;
    private ImageView mCallTypeIcon;
    private TextView mCallTime;
    private TextView mCallDuration;
    //Geesun Add
    private int mNoPhotoResource;
    Uri personUri = null ;
    private ImageView mPhotoView;    
    
    private String mNumber = null;
    
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
    private static final int MENU_ITEM_DELETE_ALL = 2;
    private static final int MENU_ITEM_DELETE_ALL_INCOMING = 3;
    private static final int MENU_ITEM_DELETE_ALL_OUTGOING = 4;
    private static final int MENU_ITEM_DELETE_ALL_MISSED = 5;
    private static final int MENU_ITEM_VIEW_CONTACT = 6;
    private static final int MENU_ITEM_ADD_CONTACT = 7;
    
    private ViewAdapter adapter;
    private List<ViewEntryData> logs;
    private SharedPreferences prefs;

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
        
    }
    
    @Override
    public void onResume() {
        super.onResume();
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
    	public long date;
    	public long duration;
    	public int callType;    	
    }
    /**
     * Update user interface with details of given call.
     * 
     * @param callUri Uri into {@link CallLog.Calls}
     */
    private void updateData() {
    	Bundle bundle = getIntent().getExtras();
    	String number = bundle.getString("NUMBER");
    	//Toast.makeText(this, number, Toast.LENGTH_LONG).show();
    	
        StringBuilder where = new StringBuilder();
        where.append(Calls.NUMBER);
        where.append(" = '" + number + "'");
        
        Cursor callCursor = getContentResolver().query(Calls.CONTENT_URI, CALL_LOG_PROJECTION,where.toString(), null,
        		Calls.DEFAULT_SORT_ORDER);
        
        mNumber = number;
        
    	ContentResolver resolver = getContentResolver();
      
    	TextView tvName = (TextView) findViewById(R.id.name);
    	TextView tvNumber = (TextView) findViewById(R.id.number);
    	
    	if (mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                mNumber.equals(CallerInfo.PRIVATE_NUMBER)) {
            // List is empty, let the empty view show instead.
            TextView emptyText = (TextView) findViewById(R.id.emptyText);
            if (emptyText != null) {
                emptyText.setText(mNumber.equals(CallerInfo.PRIVATE_NUMBER) 
                        ? R.string.private_num : R.string.unknown);
            }
            mPhotoView.setImageResource(mNoPhotoResource);
        } else {
            // Perform a reverse-phonebook lookup to find the PERSON_ID
            String callLabel = null;
            
            Uri phoneUri = Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, Uri.encode(mNumber));
            Cursor phonesCursor = resolver.query(phoneUri, PHONES_PROJECTION, null, null, null);
            try {
                if (phonesCursor != null && phonesCursor.moveToFirst()) {
                    long personId = phonesCursor.getLong(COLUMN_INDEX_ID);
                    personUri = ContentUris.withAppendedId(
                            Contacts.People.CONTENT_URI, personId);
                    // Load the photo
                    mPhotoView.setImageBitmap(People.loadContactPhoto(this, personUri, mNoPhotoResource,
                            null /* use the default options */));
                    
                    tvName.setText(phonesCursor.getString(COLUMN_INDEX_NAME));
                    tvNumber.setText(mNumber);
                } else {
                	tvName.setText("("+ getString(R.string.unknown) + ")");
                	tvNumber.setText(mNumber);// = PhoneNumberUtils.formatNumber(mNumber);
                	//tvNumber.setVisibility(View.GONE);
                	mPhotoView.setImageResource(mNoPhotoResource);
                }
            } finally {
                if (phonesCursor != null) phonesCursor.close();
            }
        }
        
        int size = Integer.parseInt(prefs.getString("cl_view_contact_pic_size", "78"));
        //Wysie_Soh: Set contact picture size
        mPhotoView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
    	
    	logs = new ArrayList<ViewEntryData>();
    	ViewEntryData firstPlaceHolder = new ViewEntryData();
    	firstPlaceHolder.number = mNumber;
    	logs.add(firstPlaceHolder);
        try {
            if (callCursor != null && callCursor.moveToFirst()) {
            	
            	do {
            		ViewEntryData data = new ViewEntryData();
	                // Read call log specifics
            		data.id = callCursor.getLong(LOG_COLUMN_INDEX);
            		data.date = callCursor.getLong(DATE_COLUMN_INDEX);
            		data.duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            		data.callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
	                
            		logs.add(data);	              
            	}while(callCursor.moveToNext());
            } else {
                // Something went wrong reading in our primary data, so we're going to
                // bail out and show error to users.
                Toast.makeText(this, R.string.toast_call_detail_error,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
        
        adapter = new ViewAdapter(this, logs);
        setListAdapter(adapter);         
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

            
        	RelativeLayout layout = (RelativeLayout) convertView.findViewById(R.id.line_action);
            LinearLayout layout_logs = (LinearLayout) convertView.findViewById(R.id.line_log);
            
            if(position == 0){
            	layout_logs.setVisibility(View.GONE);
            	layout.setVisibility(View.VISIBLE);
                ImageView call_icon = (ImageView) convertView.findViewById(R.id.call_icon);
                ImageView sms_icon = (ImageView) convertView.findViewById(R.id.sms_icon);
                TextView tvCall = (TextView) convertView.findViewById(R.id.call);
               
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
                TextView tvTime = (TextView) convertView.findViewById(R.id.time);
                TextView tvDuration = (TextView) convertView.findViewById(R.id.duration);
                TextView tvType = (TextView) convertView.findViewById(R.id.type);
                // Pull out string in format [relative], [date]
                CharSequence dateClause = DateUtils.formatDateRange(mContext, date, date,
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                        DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
                tvTime.setText(dateClause);
                
                // Set the duration
                if (callType == Calls.MISSED_TYPE) {
                	tvDuration.setVisibility(View.GONE);
                } else {
                	tvDuration.setVisibility(View.VISIBLE);
                	tvDuration.setText(formatDuration(duration));
                }
    
                // Set the call type icon and caption                
                switch (callType) {
                    case Calls.INCOMING_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_incoming_call);
                        tvType.setText(R.string.type_incoming);
                       
                        break;
    
                    case Calls.OUTGOING_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_outgoing_call);
                        tvType.setText(R.string.type_outgoing);
                        
                        break;
    
                    case Calls.MISSED_TYPE:
                        mCallTypeIcon.setImageResource(R.drawable.ic_call_log_header_missed_call);
                        tvType.setText(R.string.type_missed);
                        
                        break;
                }                
                convertView.setTag(entry);
            }
            
            return convertView;
        }

		public void onClick(View v) {			
            Intent intent = (Intent) v.getTag();

           if(intent != null)
            mContext.startActivity(intent);
		}
    }   

    
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_DELETE_ALL, 0, R.string.recentCalls_deleteAll).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
                
        /* Wysie: WIP. Not working for now :(
        menu.add(0, MENU_ITEM_DELETE_ALL_INCOMING, 0, R.string.recentCalls_deleteAllIncoming).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_OUTGOING, 0, R.string.recentCalls_deleteAllOutgoing).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, MENU_ITEM_DELETE_ALL_MISSED, 0, R.string.recentCalls_deleteAllMissed).setIcon(
                android.R.drawable.ic_menu_close_clear_cancel);
        */
    
        if (personUri != null) {
            menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
            	.setIcon(R.drawable.ic_tab_contacts); 
        } else if (!(mNumber.equals(CallerInfo.UNKNOWN_NUMBER) ||
                mNumber.equals(CallerInfo.PRIVATE_NUMBER))) {
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
        if (menuInfo.position == 0) {
            return;
        }
    
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);
    }
    
	public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        ViewEntryData entryData = (ViewEntryData)adapter.getItem(info.position);
	
		switch(item.getItemId()) {		
		    case MENU_ITEM_DELETE:
		        getContentResolver().delete(Calls.CONTENT_URI, "Calls._ID=?", new String[] { Long.toString(entryData.id) } );
                logs.remove(entryData);
                if(logs.size() != 1) { //1 because the top row is for calling/smsing
                	adapter.notifyDataSetChanged();
                }
                else {                	
                	finish();
                }
                return true;
		
		}
        
		return super.onContextItemSelected(item);
	}
	
    private void clearCallLog() {        
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage("Are you sure you want to clear all call records of " + mNumber + "?");
      
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    getContentResolver().delete(Calls.CONTENT_URI, Calls.NUMBER + "=?",
                                                new String[] { mNumber });
                    logs.clear();
                    finish();
                }
            });
        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });
        
            alert.show();
        } else {
            getContentResolver().delete(Calls.CONTENT_URI, Calls.NUMBER + "=?",
                                        new String[] { mNumber });
            logs.clear();
            finish();
        }
    }

    private void clearCallLogType(final int type) {
        String message = null;
        
        if (type == Calls.INCOMING_TYPE) {
            message = "Are you sure you want to clear all incoming call records from " + mNumber + "?" ;            
        } else if (type == Calls.OUTGOING_TYPE) {
            message = "Are you sure you want to clear all outgoing call records to " + mNumber + "?" ;
        } else if (type == Calls.MISSED_TYPE) {
            message = "Are you sure you want to clear all missed call records from " + mNumber + "?" ;
        }
        
        if (prefs.getBoolean("cl_ask_before_clear", false)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.alert_clear_call_log_title);
            alert.setMessage(message);
            alert.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    getContentResolver().delete(Calls.CONTENT_URI, Calls.TYPE + "=? AND " + Calls.NUMBER + "=?",
                                                new String[] { Integer.toString(type), mNumber });
                }
            });        
            alert.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {// Canceled.
                }
            });        
            alert.show();
            
        } else {
            getContentResolver().delete(Calls.CONTENT_URI, Calls.TYPE + "=? AND " + Calls.NUMBER + "=?",
                                        new String[] { Integer.toString(type), mNumber });
        }
        updateData();
        if(logs.size() != 1) { //1 because the top row is for calling/smsing
            adapter.notifyDataSetInvalidated();
            Log.d("WYSIE", "Data Changed 2");
        }
        else {                	
            finish();
        }        
        
    }

}
