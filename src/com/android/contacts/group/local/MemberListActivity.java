/*
 * Copyright (C) 2013, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.group.local;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.LocalGroups;
import android.provider.LocalGroups.GroupColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;
import com.android.contacts.R;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.model.account.PhoneAccountType;
import com.android.contacts.editor.MultiPickContactActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class MemberListActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final int CODE_PICK_MEMBER = 1;

    private Uri uri;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.LOOKUP_KEY,
            Data.RAW_CONTACT_ID, Contacts.PHOTO_THUMBNAIL_URI, Contacts.DISPLAY_NAME_PRIMARY
    };

    static final int SUMMARY_ID_COLUMN_INDEX = 0;

    static final int SUMMARY_DISPLAY_NAME_INDEX = 1;

    static final int SUMMARY_PHOTO_ID_INDEX = 2;

    static final int SUMMARY_LOOKUP_KEY_INDEX = 3;

    static final int SUMMARY_RAW_CONTACTS_ID_INDEX = 4;

    static final int SUMMARY_PHOTO_URI_INDEX = 5;

    static final int SUMMARY_DISPLAY_NAME_PRIMARY_INDEX = 6;

    private static final int QUERY_TOKEN = 1;

    private QueryHandler mQueryHandler;

    private MemberListAdapter mAdapter;

    private AddMembersTask mAddMembersTask;
    private LocalGroups.Group mGroup;

    private ActionMode mActionMode;

    private ListView mListView;

    private TextView emptyText;

    private Bundle mRemoveSet;

    private DeleteMembersThread mDeleteMembersTask;

    private ContactPhotoManager mContactPhotoManager;

    private boolean mPaused = false;

    private Handler mUpdateUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mDeleteMembersTask = null;
            mRemoveSet.clear();
            mAdapter.refresh();
            new LocalGroupCountTask(MemberListActivity.this).execute();
        }
    };

    private class DeleteMembersThread extends Thread {
        public static final int TASK_RUNNING = 1;
        public static final int TASK_CANCEL = 2;
        public static final int OPERATION_DELETE = 1;
        public static final int OPERATION_MOVE = 2;
        private static final int BUFFER_LENGTH = 499;
        private Bundle mRemoveSet = null;
        private int status = TASK_RUNNING;
        private int mOperation = OPERATION_DELETE;
        private int mGroupId;

        public DeleteMembersThread(Bundle removeSet) {
            super();
            this.mRemoveSet = removeSet;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public void setOperation(int operation) {
            this.mOperation = operation;
        }

        public void setGroupId(int groupId) {
            this.mGroupId = groupId;
        }

        private void deleteMembers(ArrayList<ContentProviderOperation> list, ContentResolver cr) {
            if (cr == null || list.size() == 0) {
                return;
            }

            try {
                cr.applyBatch(ContactsContract.AUTHORITY, list);
            } catch (Exception e) {
                Log.e("DeleteMembersThread", "apply batch by buffer error:" + e);
            }
            list.clear();
        }

        @Override
        public void run() {
            ContentProviderOperation.Builder builder = null;
            ContentResolver cr = getContentResolver();
            final ArrayList<ContentProviderOperation> removeList =
                    new ArrayList<ContentProviderOperation>();
            Set<String> keySet = mRemoveSet.keySet();
            Iterator<String> it = keySet.iterator();
            int i = 0;
            while (it.hasNext() && status == TASK_RUNNING) {
                if (OPERATION_DELETE == mOperation) {
                    builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                    builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                            + "=?", new String[] {
                            it.next(), LocalGroup.CONTENT_ITEM_TYPE
                    });
                } else {
                    builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
                    builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                            + "=?", new String[] {
                            it.next(), LocalGroup.CONTENT_ITEM_TYPE
                    });
                    builder.withValue(LocalGroup.DATA1, mGroupId);
                }

                removeList.add(builder.build());
                if (++i % BUFFER_LENGTH == 0) {
                    deleteMembers(removeList, cr);
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        status = TASK_CANCEL;
                        Log.e("DeleteMembersThread", "exception :" + e);
                        break;
                    }
                }
            }

            if (status == TASK_RUNNING && removeList.size() != 0) {
                deleteMembers(removeList, cr);
            } else if (status == TASK_CANCEL) {
                Log.e("DeleteMembersThread", "cancel delete members");
            }
            status = TASK_CANCEL;
            mRemoveSet.clear();
            if (mUpdateUiHandler != null) {
                mUpdateUiHandler.sendEmptyMessage(0);
            }
        }
    }

    @Override
    protected void onStop() {
        if (mDeleteMembersTask != null) {
            mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
            mDeleteMembersTask = null;
        }
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_manage);

        // actionbar setup
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        mRemoveSet = new Bundle();
        mQueryHandler = new QueryHandler(this);
        mAdapter = new MemberListAdapter(this);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        emptyText = (TextView) findViewById(R.id.emptyText);
        mListView = (ListView) findViewById(R.id.member_list);
        mListView.setAdapter(mAdapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setOnItemClickListener(this);
        mListView.setMultiChoiceModeListener(mMultiChoiceModeListener);
        uri = getIntent().getParcelableExtra("data");
        getContentResolver().registerContentObserver(
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(uri.getLastPathSegment())), true, observer);
        mGroup = LocalGroups.Group.restoreGroupById(getContentResolver(),
                Long.parseLong(uri.getLastPathSegment()));
        actionBar.setSubtitle(mGroup.getTitle());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.member_list_options, menu);
        menu.add(0, 0, 0, R.string.menu_option_delete);
        menu.add(0, 1, 0, R.string.edit_local_group_dialog_title);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.add:
                pickMembers();
                break;
            case 0:
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.delete_local_group_dialog_message,
                                    mGroup.getTitle()))
                        .setTitle(R.string.delete_group_dialog_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                                if (mGroup.delete(getContentResolver())) {
                                    finish();
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case 1:
                final EditText editText = new EditText(this);
                editText.setHint(R.string.group_edit_field_hint_text);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.edit_local_group_dialog_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setView(editText)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!TextUtils.isEmpty(editText.getText())) {
                                    String name = editText.getText().toString();
                                    if (checkGroupTitleExist(name)) {
                                        String text = getString(R.string.error_group_exist, name);
                                        Toast.makeText(MemberListActivity.this, text,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        mGroup.setTitle(name);
                                        if (mGroup.update(getContentResolver())) {
                                            getActionBar().setSubtitle(mGroup.getTitle());
                                        }
                                    }
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
        }
        return false;
    }

    private ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mAdapter.refresh();
        }
    };

    private boolean checkGroupTitleExist(String name) {
        Cursor c = null;
        try {
            c = getApplicationContext().getContentResolver().query(
                    LocalGroups.CONTENT_URI, null, LocalGroups.GroupColumns.TITLE + "=?",
                    new String[] {
                            name
                    }, null);
            if (c != null) {
                return c.getCount() > 0;
            } else {
                return false;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mPaused = true;
        mAdapter.changeCursor(null);
    }

    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
    }

    private void updateDisplay(boolean isEmpty) {
        if (isEmpty) {
            mListView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }
    }

    private AbsListView.MultiChoiceModeListener mMultiChoiceModeListener
            = new AbsListView.MultiChoiceModeListener() {

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position,
                                              long id, boolean checked) {
            View v = mListView.getChildAt(position);
            String contactId = (String) v.getTag();
            CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
            if (mRemoveSet.containsKey(contactId)) {
                mRemoveSet.remove(contactId);
            } else {
                mRemoveSet.putString(contactId, contactId);
            }
            setCheckStatus(contactId, checkBox);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.member_list_options_cab, menu);
            mActionMode = mode;
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.move:
                    chooseGroup();
                    return true;
                case R.id.remove:
                    removeContactsFromGroup();
                    mAdapter.refresh();
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mListView.clearChoices();
            mRemoveSet.clear();
            mAdapter.notifyDataSetChanged();
            mActionMode = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        startQuery();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK)
            switch (requestCode) {
                case CODE_PICK_MEMBER:
                    Bundle result = data.getExtras().getBundle("result");

                    // define member object mAddMembersTask to use later.
                    mAddMembersTask = new AddMembersTask(result);
                    mAddMembersTask.execute();
            }
    }

    private void startQuery() {
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        mQueryHandler.startQuery(
                QUERY_TOKEN,
                null,
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(uri.getLastPathSegment())), CONTACTS_SUMMARY_PROJECTION, null,
                null, null);
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<MemberListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MemberListActivity>((MemberListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (mPaused) {
                return;
            }
            final MemberListActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
            updateDisplay(cursor == null || cursor.getCount() == 0);
        }
    }

    private class MemberListAdapter extends CursorAdapter {

        public MemberListAdapter(Context context) {
            super(context, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
            String lookupId = cursor.getString(SUMMARY_LOOKUP_KEY_INDEX);
            long rawContactsId = cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX);
            String displayName = cursor.getString(SUMMARY_DISPLAY_NAME_INDEX);
            long photoId = cursor.getLong(SUMMARY_PHOTO_ID_INDEX);

            TextView contactNameView = (TextView) view.findViewById(R.id.contact_name);
            QuickContactBadge quickContactBadge = (QuickContactBadge) view
                    .findViewById(R.id.contact_icon);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);

            contactNameView
                    .setText(displayName == null ? getString(R.string.unknown) : displayName);
            loadContactsPhotoByCursor(quickContactBadge, cursor);
            quickContactBadge.assignContactUri(Contacts.getLookupUri(contactId, lookupId));

            String key = String.valueOf(rawContactsId);
            setCheckStatus(key, checkBox);
            view.setTag(key);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.member_item, parent, false);
        }

        private void refresh() {
            super.onContentChanged();
            /**
             * Update the removeSet: When user go into contact details screen by
             * click the avatar, then remove it, the content changed but
             * removeSet not, so the tools bar isn't correct. Needn't do this
             * job if removeSet is empty.
             */
            Cursor cursor = getCursor();
            if (!mRemoveSet.isEmpty() && cursor != null && !cursor.isClosed()) {
                cursor.moveToPosition(-1);
                Bundle newRemoveSet = new Bundle();
                while (cursor.moveToNext()) {
                    String contactId = String.valueOf(
                            cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX));
                    if (mRemoveSet.containsKey(contactId)) {
                        newRemoveSet.putString(contactId, contactId);
                    }
                }
                if (newRemoveSet.size() != mRemoveSet.size()) {
                    mRemoveSet = newRemoveSet;
                }
            }
            updateToolsBar();
            updateDisplay(this.getCount() == 0);
        }

    }

    public Bitmap getContactsPhoto(long photoId) {
        Bitmap contactPhoto = null;
        Cursor cursor = null;
        if (photoId > 0)
            try {
                cursor = getContentResolver().query(Data.CONTENT_URI, new String[] {
                        Photo.PHOTO
                }, Photo._ID + "=?", new String[] {
                        String.valueOf(photoId)
                }, null);

                if (cursor != null) {
                    if (cursor.moveToNext()) {
                        byte[] bytes = cursor.getBlob(0);
                        contactPhoto = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        if (contactPhoto == null) {
            contactPhoto = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.ic_contact_picture_holo_light);
        }
        return framePhoto(contactPhoto);
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.ic_contact_picture);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top, width - padding.right, height
                - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return scaleToAppIconSize(b);
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {
        final int mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        // Setup the drawing classes
        Bitmap icon = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        if (!mListView.isItemChecked(position)) {
            mListView.setItemChecked(position, true);
        } else {
            mListView.setItemChecked(position, false);
        }
    }

    private void pickMembers() {
        Intent intent = new Intent(MultiPickContactActivity.ACTION_MULTI_PICK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MultiPickContactActivity.IS_CONTACT,true);
        intent.setClass(this, MultiPickContactActivity.class);
        ContactListFilter filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT,
                PhoneAccountType.ACCOUNT_TYPE, SimContactsConstants.PHONE_NAME, null, null);
        intent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER, filter);
        startActivityForResult(intent, CODE_PICK_MEMBER);
    }

    private void updateToolsBar() {
        if (mDeleteMembersTask != null
                && mDeleteMembersTask.getStatus() == DeleteMembersThread.TASK_RUNNING) {
            return;
        }

        if (mRemoveSet.isEmpty() && mActionMode != null) {
            mActionMode.finish();
        }
    }

    private void setCheckStatus(String contactId, CheckBox checkBox) {
        checkBox.setChecked(mRemoveSet.containsKey(contactId));
    }

    private void chooseGroup() {
        final Cursor cursor = this.getContentResolver().query(LocalGroups.CONTENT_URI,
                new String[] {
                        GroupColumns._ID, GroupColumns.TITLE
                }, null, null, null);
        int index = -1;
        int count = -1;
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                count++;
                if (uri.getLastPathSegment().equals(cursor.getString(0))) {
                    index = count;
                    break;
                }
            }
        }
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                cursor.moveToPosition(which);
                moveContactstoGroup(cursor.getInt(0));
                dialog.dismiss();
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setSingleChoiceItems(cursor, index, GroupColumns.TITLE, listener)
                .setTitle(R.string.group_selector)
                .show();
        dialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                if (cursor != null && !cursor.isClosed())
                    cursor.close();
            }
        });
    }

    private void moveContactstoGroup(int groupId) {
        if (mRemoveSet != null && mRemoveSet.size() > 0) {
            Bundle bundleData = (Bundle) mRemoveSet.clone();
            if (mDeleteMembersTask != null) {
                mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
                mDeleteMembersTask = null;
            }
            mDeleteMembersTask = new DeleteMembersThread(bundleData);
            mDeleteMembersTask.setOperation(DeleteMembersThread.OPERATION_MOVE);
            mDeleteMembersTask.setGroupId(groupId);
            mDeleteMembersTask.start();
        }
    }

    private void removeContactsFromGroup() {
        if (mRemoveSet != null && mRemoveSet.size() > 0) {
            Bundle bundleData = (Bundle) mRemoveSet.clone();
            if (mDeleteMembersTask != null) {
                mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
                mDeleteMembersTask = null;
            }
            mDeleteMembersTask = new DeleteMembersThread(bundleData);
            mDeleteMembersTask.setOperation(DeleteMembersThread.OPERATION_DELETE);
            mDeleteMembersTask.start();
        }
    }

    private void loadContactsPhotoByCursor(ImageView view, Cursor cursor) {
        long photoId = cursor.getLong(SUMMARY_PHOTO_ID_INDEX);

        if (photoId != 0) {
            mContactPhotoManager.loadThumbnail(view, photoId, false, null);
        } else {
            final String photoUriString = cursor.getString(SUMMARY_PHOTO_URI_INDEX);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            DefaultImageRequest request = null;
            if (photoUri == null) {
                request = getDefaultImageRequestFromCursor(cursor,
                        SUMMARY_DISPLAY_NAME_PRIMARY_INDEX, SUMMARY_LOOKUP_KEY_INDEX);
            }
            mContactPhotoManager.loadPhoto(view, photoUri, -1, false, request);
        }
    }

    private DefaultImageRequest getDefaultImageRequestFromCursor(Cursor cursor,
            int displayNameColumn, int lookupKeyColumn) {
        final String displayName = cursor.getString(displayNameColumn);
        final String lookupKey = cursor.getString(lookupKeyColumn);
        return new DefaultImageRequest(displayName, lookupKey);
    }


    class AddMembersTask extends AsyncTask<Object, Object, Object> {
        private ProgressDialog mProgressDialog;
        private boolean mIsAddMembersTaskCanceled;

        private Handler alertHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                if (msg.what == 0) {
                    Toast.makeText(MemberListActivity.this, R.string.toast_not_add,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        };

        private Bundle result;

        private int size;

        AddMembersTask(Bundle result) {
            size = result.size();
            this.result = result;
            HandlerThread thread = new HandlerThread("DownloadTask");
            thread.start();
        }

        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            new LocalGroupCountTask(MemberListActivity.this).execute();
        }

        @Override
        protected void onPreExecute() {
            mIsAddMembersTaskCanceled = false;
            mProgressDialog = new ProgressDialog(MemberListActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(size);
            mProgressDialog.show();
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {

                    // if dialog is canceled, cancel the task also.
                    mIsAddMembersTaskCanceled = true;
                }
            });
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            process();
            return null;
        }

        public void process() {
            boolean hasInvalide = false;
            int progressIncrement = 0;
            ContentValues values = new ContentValues();
            // add Non-null protection of group for monkey test
            if (null != mGroup) {
                values.put(LocalGroup.DATA1, mGroup.getId());
            }

            Set<String> keySet = result.keySet();
            Iterator<String> it = keySet.iterator();

            // add a ContentProviderOperation update list.
            final ArrayList<ContentProviderOperation> updateList =
                    new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;
            mIsAddMembersTaskCanceled = false;
            while (it.hasNext()) {
                if (mIsAddMembersTaskCanceled) {
                    break;
                }
                if (progressIncrement++ % 2 == 0) {
                    if (mProgressDialog != null) {
                        mProgressDialog.incrementProgressBy(2);
                    } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }
                String id = it.next();
                Cursor c = null;
                try {
                    c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, new String[] {
                            ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE
                    }, ContactsContract.RawContacts.CONTACT_ID + "=?", new String[] {
                            id
                    }, null);
                    if (c.moveToNext()) {
                        String rawId = String.valueOf(c.getLong(0));

                        if (!PhoneAccountType.ACCOUNT_TYPE.equals(c.getString(1))) {
                            hasInvalide = true;
                            continue;
                        }

                        builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                        builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                                + "=?", new String[] {
                                rawId, LocalGroup.CONTENT_ITEM_TYPE
                        });

                        // add the delete operation to the update list.
                        updateList.add(builder.build());

                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        // add Non-null protection of group for monkey test
                        if (null != mGroup) {
                            builder.withValue(LocalGroup.DATA1, mGroup.getId());
                        }
                        builder.withValue(Data.RAW_CONTACT_ID, rawId);
                        builder.withValue(Data.MIMETYPE, LocalGroup.CONTENT_ITEM_TYPE);

                        // add the insert operation to the update list.
                        updateList.add(builder.build());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }

            // if task is canceled ,still update the database with the data in
            // updateList.

            // apply batch to execute the delete and insert operation.
            if (updateList.size() > 0) {
                addMembersApplyBatchByBuffer(updateList, getContentResolver());
            }
            if (hasInvalide) {
                alertHandler.sendEmptyMessage(0);
            }
        }

        /**
         * the max length of applyBatch is 500
         */
        private static final int BUFFER_LENGTH = 499;


        private void addMembersApplyBatchByBuffer(ArrayList<ContentProviderOperation> list,
                                                  ContentResolver cr) {
            final ArrayList<ContentProviderOperation> temp =
                    new ArrayList<ContentProviderOperation>(BUFFER_LENGTH);
            int bufferSize = list.size() / BUFFER_LENGTH;
            for (int index = 0; index <= bufferSize; index++) {
                temp.clear();
                if (index == bufferSize) {
                    for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                        temp.add(list.get(i));
                    }
                } else {
                    for (int i = index * BUFFER_LENGTH; i < index * BUFFER_LENGTH + BUFFER_LENGTH;
                         i++) {
                        temp.add(list.get(i));
                    }
                }
                if (!temp.isEmpty()) {
                    try {
                        cr.applyBatch(ContactsContract.AUTHORITY, temp);
                        if (mProgressDialog != null) {
                            mProgressDialog.incrementProgressBy(temp.size() / 4);
                        } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                            mProgressDialog = null;
                        }
                    } catch (Exception e) {
                        Log.e(MemberListActivity.class.getSimpleName(), "apply batch by buffer error:" + e);
                    }
                }
            }
        }
    }
}
