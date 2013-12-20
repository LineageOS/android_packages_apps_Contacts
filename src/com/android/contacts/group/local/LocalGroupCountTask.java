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

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Data;
import android.provider.LocalGroups;
import android.provider.LocalGroups.GroupColumns;
import android.util.Log;
import com.android.contacts.group.GroupBrowseListFragment;

import java.util.ArrayList;

public class LocalGroupCountTask extends AsyncTask<Object, Object, Object> {
    private static final String TAG = "LocalGroupCountTask";
    private Context mContext;
    private GroupBrowseListFragment target;

    public LocalGroupCountTask(Context context, GroupBrowseListFragment fragment) {
        mContext = context;
        target = fragment;
    }

    public LocalGroupCountTask(Context context) {
        mContext = context;
        target = null;
    }

    @Override
    protected void onPostExecute(Object result) {
        if (target != null) {
            target.updateGroupData();
        }
    }

    @Override
    protected Object doInBackground(Object... params) {
        countMemebers();
        return null;
    }

    private void countMemebers() {
        Cursor groups = null;
        final ArrayList<ContentProviderOperation> updateList =
                new ArrayList<ContentProviderOperation>();
        try {
            groups = mContext.getContentResolver().query(LocalGroups.CONTENT_URI, new String[] {
                    GroupColumns._ID
            }, null, null, null);
            while (groups.moveToNext()) {
                countMemebersById(updateList, groups.getLong(0));
            }
        } finally {
            if (groups != null) {
                groups.close();
            }
        }

        if (updateList.size() > 0) {
            try {
                mContext.getContentResolver().applyBatch(LocalGroups.AUTHORITY, updateList);
            } catch (Exception e) {
            }
        }
    }

    private void countMemebersById(ArrayList<ContentProviderOperation> updateList, long groupId) {
        Cursor datas = null;
        try {
            datas = mContext.getContentResolver().query(Data.CONTENT_URI, null,
                    Data.MIMETYPE + "=? and " + LocalGroup.DATA1 + "=?", new String[] {
                            LocalGroup.CONTENT_ITEM_TYPE, String.valueOf(groupId)
                    }, null);
            int count = datas == null ? 0 : datas.getCount();
            Log.d(TAG, "count of members:" + count + " in group:" + groupId);
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(LocalGroups.CONTENT_URI);
            builder.withValue(GroupColumns.COUNT, count);
            builder.withSelection(GroupColumns._ID + "=?", new String[] {
                    String.valueOf(groupId)
            });
            updateList.add(builder.build());
        } finally {
            if (datas != null) {
                datas.close();
            }
        }
    }
}
