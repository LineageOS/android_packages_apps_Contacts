/*
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

package com.android.contacts.group;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.GroupListLoader;
import com.android.contacts.RcsApiManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.PhoneAccountType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.util.RCSUtil;
import com.android.contacts.R;
import com.google.common.base.Objects;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatUser;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/**
 * Adapter to populate the list of groups.
 */
public class GroupBrowseListAdapter extends BaseAdapter {

    private static final String TAG = "GroupBrowseListAdapter";
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final AccountTypeManager mAccountTypeManager;

    private Cursor mCursor;
    private int normalContactCount = 0;
    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;
    private int mLocalGroupsCount;
    private ArrayList<GroupChatModel> mRcsChatGroups = new ArrayList<GroupChatModel>();
    private HashMap<String, Integer> mCountMap = new HashMap<String, Integer>();

    private class ViewHolder {
        View group_icon_view;
        TextView groupName, groupCount;
        TextView title_view;
    }

    public GroupBrowseListAdapter(Context context) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mAccountTypeManager = AccountTypeManager.getInstance(mContext);
    }

    public void setRcsGroupsData(ArrayList<GroupChatModel> data,HashMap<String, Integer> countMap){

        this.mRcsChatGroups.clear();
        this.mRcsChatGroups.addAll(data);
        this.mCountMap.clear();
        this.mCountMap.putAll(countMap);
        this.notifyDataSetChanged();
    }

    public void setCursor(Cursor cursor) {
        mCursor = cursor;

        // If there's no selected group already and the cursor is valid, then by default, select the
        // first group
        if (mSelectedGroupUri == null && cursor != null && cursor.getCount() > 0) {
            GroupListItem firstItem = (GroupListItem)getItem(0);
            long groupId = (firstItem == null) ? 0 : firstItem.getGroupId();
            mSelectedGroupUri = getGroupUriFromId(groupId);
        }

        notifyDataSetChanged();
    }

    public int getSelectedGroupPosition() {
        if (mSelectedGroupUri == null || mCursor == null || mCursor.getCount() == 0) {
            return -1;
        }

        int index = 0;
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
            Uri uri = getGroupUriFromId(groupId);
            if (mSelectedGroupUri.equals(uri)) {
                  return index;
            }
            index++;
        }
        return -1;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
    }

    public void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
    }

    private boolean isSelectedGroup(Uri groupUri) {
        return mSelectedGroupUri != null && mSelectedGroupUri.equals(groupUri);
    }

    public Uri getSelectedGroup() {
        return mSelectedGroupUri;
    }

    @Override
    public int getCount() {
        mLocalGroupsCount = RCSUtil.getLocalGroupsCount(mContext);
        return (mCursor == null || mCursor.isClosed()) ? 0 : mCursor.getCount();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public GroupListItem getItem(int position) {
        if (mCursor == null || mCursor.isClosed() || !mCursor.moveToPosition(position)) {
            return null;
        }
        String sourceId = mCursor.getString(GroupListLoader.SOURCE_ID);
        String systemId = mCursor.getString(GroupListLoader.SYSTEM_ID);
        String accountName = mCursor.getString(GroupListLoader.ACCOUNT_NAME);
        String accountType = mCursor.getString(GroupListLoader.ACCOUNT_TYPE);
        String dataSet = mCursor.getString(GroupListLoader.DATA_SET);
        long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
        String title = mCursor.getString(GroupListLoader.TITLE);
        int memberCount = mCursor.getInt(GroupListLoader.MEMBER_COUNT);

        if(RCSUtil.getRcsSupport() && (!TextUtils.isEmpty(sourceId))){
            if(sourceId.equals("RCS")){
                accountType=sourceId;
                String strGroupId = mCursor.getString(GroupListLoader.SYSTEM_ID);
                if(TextUtils.isEmpty(strGroupId)){
                    groupId = -1;
                } else {
                    groupId = Long.valueOf(strGroupId);
                }
            }
        }

        // Figure out if this is the first group for this account name / account type pair by
        // checking the previous entry. This is to determine whether or not we need to display an
        // account header in this item.
        int previousIndex = position - 1;
        boolean isFirstGroupInAccount = true;
        if (previousIndex >= 0 && mCursor.moveToPosition(previousIndex)) {
            String previousGroupAccountName = mCursor.getString(GroupListLoader.ACCOUNT_NAME);
            String previousGroupAccountType = mCursor.getString(GroupListLoader.ACCOUNT_TYPE);
            String previousGroupDataSet = mCursor.getString(GroupListLoader.DATA_SET);

            if (accountName.equals(previousGroupAccountName) &&
                    accountType.equals(previousGroupAccountType) &&
                    Objects.equal(dataSet, previousGroupDataSet)) {
                isFirstGroupInAccount = false;
            }
        }

        return new GroupListItem(accountName, accountType, dataSet, groupId, title,systemId,
                isFirstGroupInAccount, memberCount);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        GroupListItem entry = (GroupListItem) getItem(position);
        Log.i(TAG, entry.getAccountType());
        View result;
        GroupListItemViewCache viewCache = null;
        ViewHolder holder = new ViewHolder();
        if (convertView != null) {
            if (entry.getAccountType().equals("RCS")) {
                result = convertView;
                if ((result.getTag()) instanceof ViewHolder)
                    holder = (ViewHolder) result.getTag();
                else {
                    result = mLayoutInflater
                            .inflate(R.layout.rcs_contact_group_item, parent, false);

                    holder.group_icon_view = (View) result.findViewById(R.id.group_icon_view);
                    holder.groupName = (TextView) result.findViewById(R.id.group_name);
                    holder.groupCount = (TextView) result.findViewById(R.id.group_count);
                    holder.title_view = (TextView) result.findViewById(R.id.title_view);
                    result.setTag(holder);
                }
            } else {

                result = convertView;
                if ((result.getTag()) instanceof GroupListItemViewCache)
                    viewCache = (GroupListItemViewCache) result.getTag();
                else {
                    result = mLayoutInflater
                            .inflate(R.layout.group_browse_list_item, parent, false);
                    viewCache = new GroupListItemViewCache(result);
                    result.setTag(viewCache);
                }
            }
        } else {
            if (entry.getAccountType().equals("RCS")) {

                result = mLayoutInflater.inflate(R.layout.rcs_contact_group_item, parent, false);

                holder.group_icon_view = (View) result.findViewById(R.id.group_icon_view);
                holder.groupName = (TextView) result.findViewById(R.id.group_name);
                holder.groupCount = (TextView) result.findViewById(R.id.group_count);
                holder.title_view = (TextView) result.findViewById(R.id.title_view);
                result.setTag(holder);

            } else {
                result = mLayoutInflater.inflate(R.layout.group_browse_list_item, parent, false);
                viewCache = new GroupListItemViewCache(result);
                result.setTag(viewCache);
            }
        }
        if (entry == null)
            return result;

        if (entry.getAccountType().equals("RCS")) {
            holder = (ViewHolder) result.getTag();
            holder.groupName.setText(entry.getTitle());
            String memberCountString = mContext.getResources().getQuantityString(
                    R.plurals.group_list_num_contacts_in_group,
                    RCSUtil.getMessageChatCount(Integer.valueOf(entry.getSystemId())),
                    RCSUtil.getMessageChatCount(Integer.valueOf(entry.getSystemId())));
            holder.groupCount.setText(memberCountString);

            int res_id;
            switch (position % 6) {
            case 0:
                res_id = R.drawable.group_icon_1;
                break;
            case 1:
                res_id = R.drawable.group_icon_2;
                break;
            case 2:
                res_id = R.drawable.group_icon_3;
                break;
            case 3:
                res_id = R.drawable.group_icon_4;
                break;
            case 4:
                res_id = R.drawable.group_icon_5;
                break;
            case 5:
                res_id = R.drawable.group_icon_6;
                break;
            default:
                res_id = R.drawable.group_icon_6;
                break;
            }
            holder.group_icon_view.setBackgroundResource(res_id);
            if(mLocalGroupsCount == position){
                holder.title_view.setVisibility(View.VISIBLE);
                holder.title_view.setText(R.string.rcs_group_chat_item_grouplist);
            } else {
                holder.title_view.setVisibility(View.GONE);
            }

        } else {

            viewCache = (GroupListItemViewCache) result.getTag();
            // Add a header if this is the first group in an account and hide the divider
            if (entry.isFirstGroupInAccount()) {
                bindHeaderView(entry, viewCache);
                viewCache.accountHeader.setVisibility(View.VISIBLE);
                viewCache.divider.setVisibility(View.GONE);
                if (position == 0) {
                    // Have the list's top padding in the first header.
                    //
                    // This allows the ListView to show correct fading effect on top.
                    // If we have topPadding in the ListView itself, an inappropriate padding is
                    // inserted between fading items and the top edge.
                    viewCache.accountHeaderExtraTopPadding.setVisibility(View.VISIBLE);
                } else {
                    viewCache.accountHeaderExtraTopPadding.setVisibility(View.GONE);
                }
            } else {
                viewCache.accountHeader.setVisibility(View.GONE);
                viewCache.divider.setVisibility(View.VISIBLE);
                viewCache.accountHeaderExtraTopPadding.setVisibility(View.GONE);
            }

            // Bind the group data
            Uri groupUri = getGroupUriFromId(entry.getGroupId());
            String memberCountString = mContext.getResources().getQuantityString(
                    R.plurals.group_list_num_contacts_in_group, entry.getMemberCount(),
                    entry.getMemberCount());
            viewCache.setUri(groupUri);
            viewCache.groupTitle.setText(entry.getTitle());
            viewCache.groupMemberCount.setText(memberCountString);

            if (mSelectionVisible) {
                result.setActivated(isSelectedGroup(groupUri));
            }
        }
        return result;
    }

    private void bindHeaderView(GroupListItem entry, GroupListItemViewCache viewCache) {

        if (!entry.getAccountType().equals("RCS")) {
            AccountType accountType = mAccountTypeManager.getAccountType(
                    entry.getAccountType(), entry.getDataSet());
            viewCache.accountType.setText(accountType.getDisplayLabel(mContext));
            // According to the UI SPEC, we will not show the account name for Phone account
            if (!PhoneAccountType.ACCOUNT_TYPE.equals(entry.getAccountType())) {
                viewCache.accountName.setText(entry.getAccountName());
            }
        }
    }

    private static Uri getGroupUriFromId(long groupId) {
        return ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
    }

    /**
     * Cache of the children views of a contact detail entry represented by a
     * {@link GroupListItem}
     */
    public static class GroupListItemViewCache {
        public final TextView accountType;
        public final TextView accountName;
        public final TextView groupTitle;
        public final TextView groupMemberCount;
        public final View accountHeader;
        public final View accountHeaderExtraTopPadding;
        public final View divider;
        private Uri mUri;

        public GroupListItemViewCache(View view) {
            accountType = (TextView) view.findViewById(R.id.account_type);
            accountName = (TextView) view.findViewById(R.id.account_name);
            groupTitle = (TextView) view.findViewById(R.id.label);
            groupMemberCount = (TextView) view.findViewById(R.id.count);
            accountHeader = view.findViewById(R.id.group_list_header);
            accountHeaderExtraTopPadding = view.findViewById(R.id.header_extra_top_padding);
            divider = view.findViewById(R.id.divider);
        }

        public void setUri(Uri uri) {
            mUri = uri;
        }

        public Uri getUri() {
            return mUri;
        }
    }
}
