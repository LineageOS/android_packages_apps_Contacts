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

import android.content.Context;
import android.database.Cursor;
import android.provider.LocalGroups.Group;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.android.contacts.R;

public class LocalGroupListAdapter extends CursorAdapter {
    private static final String TAG = "LocalGroupListAdapter";

    public LocalGroupListAdapter(Context context) {
        super(context, null);
    }

    public void refresh() {
        super.onContentChanged();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView accountType = (TextView) view.findViewById(R.id.account_type);
        TextView groupTitle = (TextView) view.findViewById(R.id.label);
        TextView groupMemberCount = (TextView) view.findViewById(R.id.count);
        View accountHeader = view.findViewById(R.id.group_list_header);
        View accountHeaderExtraTopPadding = view.findViewById(R.id.header_extra_top_padding);
        View divider = view.findViewById(R.id.divider);

        accountHeaderExtraTopPadding.setVisibility(View.GONE);

        if (cursor.getPosition() == 0) {
            accountType.setText(R.string.title_switch_group_local);
            accountHeader.setVisibility(View.VISIBLE);
            divider.setVisibility(View.GONE);
        } else {
            accountHeader.setVisibility(View.GONE);
            divider.setVisibility(View.VISIBLE);
            accountHeaderExtraTopPadding.setVisibility(View.GONE);
        }

        Group group = Group.restoreGroup(cursor);
        String memberCountString = mContext.getResources().getQuantityString(
                R.plurals.group_list_num_contacts_in_group, group.getCount(),
                group.getCount());
        groupTitle.setText(group.getTitle());
        groupMemberCount.setText(memberCountString);
        view.setTag(group);

    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.group_browse_list_item, null);
        return view;
    }
}
