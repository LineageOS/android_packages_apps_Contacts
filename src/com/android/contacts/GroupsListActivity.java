/*
 * Copyright (C) 2008 Google Inc.
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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Contacts.Groups;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.util.Log;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class GroupsListActivity extends ListActivity {
	
	private GroupsListAdapter mAdapter;
    private SharedPreferences ePrefs;    
	
	private static final int CREATE_GROUP_ID = 1;
    private static final String TAG = "GroupsListActivity";

    private static final int RENAME_GROUP_ID = 1;
    private static final int DELETE_GROUP_ID = 2;  

    private static final String[] GROUPS_PROJECTION = new String[] {
        Groups._ID, // 0
        Groups.NAME // 1            
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //Wysie_Soh: Check for version change
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!(prefs.getString("mod_version", "0")).equals(getString(R.string.summary_about_version))) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("mod_version", getString(R.string.summary_about_version));
            editor.commit();
            
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.help_title);
            alert.setMessage(R.string.help_message);
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            
            alert.show();
        }        
        
        ePrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());        
        setTitle(R.string.title_groups_manager);
        setContentView(R.layout.groups_list_content);
        mAdapter = new GroupsListAdapter(this);
        fillData();
        registerForContextMenu(getListView());        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, CREATE_GROUP_ID, 0, R.string.option_create_group).setIcon(android.R.drawable.ic_menu_add);
        return result;
    }    


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
            case CREATE_GROUP_ID:
                AlertDialog.Builder alert = new AlertDialog.Builder(this); 
                alert.setTitle(R.string.option_create_group); 
                alert.setMessage(R.string.alert_create_group_message);
                
                final EditText input = new EditText(this);
                alert.setView(input);
                
                alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String groupName = (input.getText().toString()).trim();
                        
                        if (mAdapter.checkGroupExists(groupName)) {
                            showToast(groupName + " already exists");
                        }
                        else { 
                            Uri newGroup = mAdapter.createGroup(groupName);
                            
                            if (newGroup != null) {
                                showToast(groupName + " successfully created");
                            } else {
                                showToast("Error creating " + groupName);
                            }                      
                            
                            fillData();
                        }
                    }
                });
                 
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }  
                });
                
                alert.show(); 

                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		//super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info;        
        
        try {
             info = (AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor)getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }        

        String groupName = cursor.getString(cursor.getColumnIndex(Groups.NAME));
        
        if (groupName.equals("Starred in Android") || groupName.contains("System Group")) {
            return;
        }

        // Setup the menu header
        menu.setHeaderTitle(cursor.getString(cursor.getColumnIndex(Groups.NAME)));
		
		menu.add(0, RENAME_GROUP_ID, 0, R.string.context_rename_group);
        menu.add(0, DELETE_GROUP_ID, 0, R.string.context_delete_group);
    }

    @Override
	public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Cursor cursor = (Cursor)getListAdapter().getItem(info.position);
        final String groupName = cursor.getString(cursor.getColumnIndex(Groups.NAME));
        final long id = info.id;
	
		switch(item.getItemId()) {		
		    case RENAME_GROUP_ID:
                AlertDialog.Builder alert = new AlertDialog.Builder(this); 
                alert.setTitle(R.string.context_rename_group); 
                alert.setMessage(R.string.alert_rename_group_message);
                
                final EditText input = new EditText(this);
                input.setText(groupName);
                input.selectAll();
                
                alert.setView(input);
                
                alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String newName = (input.getText().toString()).trim();
                        
                        if (newName.equals(groupName)) {
                            showToast("Name not changed");
                        }
                        else {                      
                            boolean success = false;
                            try {
                                success = mAdapter.renameGroup(id, newName);
                                showToast(groupName + " successfully renamed to " + newName);
                            } catch (NullPointerException e) {
                                showToast("Error renaming " + groupName);
                            }
                            
                            if (!success) {
                                showToast("Error renaming " + groupName);
                            }
                            
                            fillData();
                        }
                    }
                });
                 
                alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }  
                });
                
                alert.show();
                
                return true;		        	        
		
        	case DELETE_GROUP_ID:
        	    if (ePrefs.getBoolean("groups_ask_before_del", false)) {
                    AlertDialog.Builder alertDel = new AlertDialog.Builder(this); 
                    alertDel.setTitle(R.string.context_delete_group); 
                    alertDel.setMessage("Are you sure you want to delete " + groupName + "?");
                    alertDel.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            boolean success = mAdapter.deleteGroup(id);                                                        
                            if (success) {
                                showToast(groupName + " successfully deleted");
                            } else {
                                showToast("Error deleting " + groupName);
                            }                            
                            fillData();
                        }
                    });
                     
                    alertDel.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }  
                    });
                    
                    alertDel.show();
        	    }
        	    else {
    	            mAdapter.deleteGroup(id);
                    fillData();
    	        }
	            return true;
		}
        
		return super.onContextItemSelected(item);
	}
	
	private void showToast(String message) {
	    Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }
    
    private void fillData() {
        // Get all of the notes from the database and create the item list
        Cursor c = mAdapter.fetchAllGroups();
        startManagingCursor(c);

        String[] from = new String[] { Groups.NAME };
        int[] to = new int[] { R.id.text1 };
        
        // Now create an array adapter and set it to display using our row
        SimpleCursorAdapter groups = new SimpleCursorAdapter(this, R.layout.groups_list_item, c, from, to);
        setListAdapter(groups);
    }
    
    public class GroupsListAdapter {
    
        private final Context mCtx;
        private ContentResolver resolver;      

        public GroupsListAdapter(Context ctx) {
            this.mCtx = ctx;
            this.resolver = getContentResolver();
        }        
        
        public Cursor fetchAllGroups() {            
            return resolver.query(Groups.CONTENT_URI, GROUPS_PROJECTION, null, null, "UPPER(" + Groups.NAME + ") ASC");    	
        }
        
        public boolean checkGroupExists(String groupName) {
            boolean exists = false;
            
            Cursor c = resolver.query(Groups.CONTENT_URI, GROUPS_PROJECTION, "UPPER(" + Groups.NAME + ")=UPPER('" + groupName + "')", null, null);
            
            if (c.getCount() > 0)
                exists = true;
                
            return exists;            
        }
        
        public Uri createGroup(String groupName) {
            ContentValues values = new ContentValues();
            values.put(Groups.NAME, groupName);
            
            return resolver.insert(Groups.CONTENT_URI, values);
        }
        
        public boolean deleteGroup(long groupId) {
            return resolver.delete(Groups.CONTENT_URI, Groups._ID + "=" + groupId, null) > 0;
        }
        
        public boolean renameGroup(long groupId, String newName) throws NullPointerException {
            ContentValues values = new ContentValues();
            values.put(Groups.NAME, newName);
            
            return resolver.update(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), values, null, null) > 0;
        }
        
    }
}
