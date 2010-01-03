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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 */
public class CallDetailActivity extends ListActivity 	
	 {
    private static final String TAG = "CallDetail";

    private TextView mCallType;
    private ImageView mCallTypeIcon;
    private TextView mCallTime;
    private TextView mCallDuration;
    //Geesun Add
    private int mNoPhotoResource;
    Uri personUri = null ;
    private ImageView mPhotoView;
    static String mIpPrefix = null; 
    
    
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

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();    

        mPhotoView = (ImageView) findViewById(R.id.photo);
          
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
        
        where.append(" or " + Calls.NUMBER);
        where.append(" = '" + mIpPrefix + number + "'");
        
        where.append(" or " + Calls.NUMBER);
        where.append(" = '" + "+86" + number + "'");
        
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
    	
    	List<ViewEntryData> logs = new ArrayList<ViewEntryData>();
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
        
        ViewAdapter adapter = new ViewAdapter(this, logs);
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
    	implements View.OnClickListener,
    	View.OnLongClickListener
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
               
                //TextView tvSms = (TextView) convertView.findViewById(R.id.send_sms);
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
                tvCall.setTag(callIntent);
                sms_icon.setTag(smsIntent);
                //tvSms.setTag(smsIntent);                
                call_icon.setOnClickListener(this);                
                sms_icon.setOnClickListener(this);
                //tvSms.setOnClickListener(this);
                tvCall.setOnClickListener(this); 
                
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
                convertView.setLongClickable(true);
                convertView.setOnLongClickListener(this);
            }
            
            return convertView;
        }

		public void onClick(View v) {			
            Intent intent = (Intent) v.getTag();

           if(intent != null)
            mContext.startActivity(intent);
		}

	    private class DeleteClickListener implements DialogInterface.OnClickListener {
	        private ViewEntryData  mData;
	        
	        public DeleteClickListener(ViewEntryData data) {
	        	mData = data;
	        }

	        public void onClick(DialogInterface dialog, int which) {
                StringBuilder where = new StringBuilder();
                where.append(Calls._ID);
                where.append(" =  " + mData.id);
                mContext.getContentResolver().delete(Calls.CONTENT_URI, where.toString(), null);
                mLogs.remove(mData);
                if(mLogs.size() != 1)
                	notifyDataSetChanged();
                else{
                	
                	((Activity) mContext).finish();
                }
	        }
	    }
	    
		public boolean onLongClick(View v) {
			//v.setBackgroundResource(android.R.drawable.list_selector_background);
			
			ViewEntryData entryData  =  (ViewEntryData) v.getTag();
			if(entryData != null){


                Uri uri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,
                		entryData.id);
                //TODO make this dialog persist across screen rotations
                new AlertDialog.Builder(mContext)
                    .setTitle(R.string.deleteConfirmation_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage("Wysie?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DeleteClickListener(entryData))
                    .show();
			}
			// TODO Auto-generated method stub
			return false;
		}
		
    }

    

    
    public boolean onCreateOptionsMenu(Menu menu) {
        if (personUri != null) {
            menu.add(0, 0, 0, R.string.menu_viewContact)
            	.setIcon(R.drawable.sym_action_view_contact); 
        } else {
            menu.add(0, 0, 0, R.string.recentCalls_addToContact)
            .setIcon(R.drawable.sym_action_add);
        }

        return true;
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        if (personUri != null) {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, personUri);
            startActivity(viewIntent);
        } else {
            Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            createIntent.setType(People.CONTENT_ITEM_TYPE);
            createIntent.putExtra(Insert.PHONE, mNumber);
            startActivity(createIntent);
        }
        
                return true;

    }
}
