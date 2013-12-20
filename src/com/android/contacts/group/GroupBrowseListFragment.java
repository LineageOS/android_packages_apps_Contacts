/*
 * Copyright (C) 2011 The Android Open Source Project

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

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.LocalGroups;
import android.provider.LocalGroups.Group;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.group.local.LocalGroupCountTask;
import com.android.contacts.group.local.LocalGroupListAdapter;
import com.android.contacts.group.local.LocalGroupListLoader;
import com.android.contacts.GroupListLoader;
import com.android.contacts.R;
import com.android.contacts.group.GroupBrowseListAdapter.GroupListItemViewCache;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.list.AutoScrollListView;

/**
 * Fragment to display the list of groups.
 */
public class GroupBrowseListFragment extends Fragment
        implements OnFocusChangeListener, OnTouchListener {

    /**
     * Action callbacks that can be sent by a group list.
     */
    public interface OnGroupBrowserActionListener  {

        /**
         * Opens the specified group for viewing.
         *
         * @param groupUri for the group that the user wishes to view.
         */
        void onViewGroupAction(Uri groupUri);

    }

    private static final String TAG = "GroupBrowseListFragment";

    private static final int LOADER_GROUPS = 1;
    private static final int LOADER_LOCAL_GROUPS = 2;

    private Context mContext;
    private Cursor mGroupListCursor;

    private boolean mSelectionToScreenRequested;

    private static final String EXTRA_KEY_GROUP_URI = "groups.groupUri";

    private View mRootView;
    private AutoScrollListView mListView;
    private TextView mEmptyView;
    private View mAddAccountsView;
    private View mAddAccountButton;

    private GroupBrowseListAdapter mAdapter;
    private LocalGroupListAdapter mLocalAdapter;
    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    private int mVerticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT;

    private OnGroupBrowserActionListener mListener;

    public GroupBrowseListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mSelectedGroupUri = savedInstanceState.getParcelable(EXTRA_KEY_GROUP_URI);
            if (mSelectedGroupUri != null) {
                // The selection may be out of screen, if rotated from portrait to landscape,
                // so ensure it's visible.
                mSelectionToScreenRequested = true;
            }
        }

        mRootView = inflater.inflate(R.layout.group_browse_list_fragment, null);
        mEmptyView = (TextView)mRootView.findViewById(R.id.empty);

        mAdapter = new GroupBrowseListAdapter(mContext);
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setSelectedGroup(mSelectedGroupUri);

        mLocalAdapter = new LocalGroupListAdapter(mContext);

        mListView = (AutoScrollListView) mRootView.findViewById(R.id.list);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object tag = view.getTag();
                if (tag instanceof GroupListItemViewCache) {
                    GroupListItemViewCache groupListItem = (GroupListItemViewCache) tag;
                    if (groupListItem != null) {
                        viewGroup(groupListItem.getUri());
                    }
                } else {
                    goToGroupEdit(getSelectUri(((Group) tag).getId()));
                }
            }
        });

        mListView.setEmptyView(mEmptyView);
        configureVerticalScrollbar();

        mAddAccountsView = mRootView.findViewById(R.id.add_accounts);
        mAddAccountButton = mRootView.findViewById(R.id.add_account_button);
        mAddAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                intent.putExtra(Settings.EXTRA_AUTHORITIES,
                        new String[] { ContactsContract.AUTHORITY });
                startActivity(intent);
            }
        });
        setAddAccountsVisibility(!ContactsUtils.areGroupWritableAccountsAvailable(mContext));

        return mRootView;
    }

    private Uri getSelectUri(long id) {
        return ContentUris.withAppendedId(LocalGroups.CONTENT_URI, id);
    }

    @Override
    public void onResume() {
        super.onResume();
        new LocalGroupCountTask(mContext, this).execute();
    }

    private void goToGroupEdit(Uri data) {
        Intent intent = new Intent(Intent.ACTION_EDIT, data);
        intent.putExtra("data", data);
        intent.setType(LocalGroup.CONTENT_ITEM_TYPE);
        this.startActivity(intent);
    }

    private boolean isLocalShown() {
        PeopleActivity peopleActivity = (PeopleActivity) this.getActivity();
        if (peopleActivity != null) {
            return peopleActivity.isLocalGroupsShown;
        } else {
            return false;
        }
    }

    private void bindLocalGroupList(Cursor data) {
        mEmptyView.setText(R.string.noGroupsHelpText);
        setAddAccountsVisibility(false);
        if (data == null) {
            return;
        }
        mLocalAdapter.changeCursor(data);
    }

    public void updateGroupData() {
        if (this.getActivity() == null) {
            return;
        }
        if (!isLocalShown()) {
            getLoaderManager().restartLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        } else {
            getLoaderManager().restartLoader(LOADER_LOCAL_GROUPS, null, mLocalGroupLoaderListener);
        }
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mLocalGroupLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            mEmptyView.setText(null);
            return new LocalGroupListLoader(mContext);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mListView.setAdapter(mLocalAdapter);
            bindLocalGroupList(data);
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    public void setVerticalScrollbarPosition(int position) {
        mVerticalScrollbarPosition = position;
        if (mListView != null) {
            configureVerticalScrollbar();
        }
    }

    private void configureVerticalScrollbar() {
        mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        int leftPadding = 0;
        int rightPadding = 0;
        if (mVerticalScrollbarPosition == View.SCROLLBAR_POSITION_LEFT) {
            leftPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        } else {
            rightPadding = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.list_visible_scrollbar_padding);
        }
        mListView.setPadding(leftPadding, mListView.getPaddingTop(),
                rightPadding, mListView.getPaddingBottom());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onStart() {
        //local group will update group data in onResume()
        if (!isLocalShown()) {
            updateGroupData();
        }
        super.onStart();
    }

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener =
            new LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            mEmptyView.setText(null);
            return new GroupListLoader(mContext);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // The GroupListLoader register the content observer, it
            // will update the data if the DB changed(e.g. import
            // contacts from VCard, it will update the account groups
            // data). But we should not change mListViwe to display the
            // account groups if we are in the Local Groups UI.
            if (!isLocalShown()) {
                mListView.setAdapter(mAdapter);
            }
            mGroupListCursor = data;
            bindGroupList();
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    private void bindGroupList() {
        mEmptyView.setText(R.string.noGroups);
        setAddAccountsVisibility(!ContactsUtils.areGroupWritableAccountsAvailable(mContext));
        if (mGroupListCursor == null) {
            return;
        }
        mAdapter.setCursor(mGroupListCursor);

        if (mSelectionToScreenRequested) {
            mSelectionToScreenRequested = false;
            requestSelectionToScreen();
        }

        mSelectedGroupUri = mAdapter.getSelectedGroup();
        if (mSelectionVisible && mSelectedGroupUri != null) {
            viewGroup(mSelectedGroupUri);
        }
    }

    public void setListener(OnGroupBrowserActionListener listener) {
        mListener = listener;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
        if (mAdapter != null) {
            mAdapter.setSelectionVisible(mSelectionVisible);
        }
    }

    private void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
        mAdapter.setSelectedGroup(groupUri);
        mListView.invalidateViews();
    }

    private void viewGroup(Uri groupUri) {
        setSelectedGroup(groupUri);
        if (mListener != null) mListener.onViewGroupAction(groupUri);
    }

    public void setSelectedUri(Uri groupUri) {
        viewGroup(groupUri);
        mSelectionToScreenRequested = true;
    }

    protected void requestSelectionToScreen() {
        if (!mSelectionVisible) {
            return; // If selection isn't visible we don't care.
        }
        int selectedPosition = mAdapter.getSelectedGroupPosition();
        if (selectedPosition != -1) {
            mListView.requestPositionToScreen(selectedPosition,
                    true /* smooth scroll requested */);
        }
    }

    private void hideSoftKeyboard() {
        if (mContext == null) {
            return;
        }
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            hideSoftKeyboard();
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EXTRA_KEY_GROUP_URI, mSelectedGroupUri);
    }

    public void setAddAccountsVisibility(boolean visible) {
        if (mAddAccountsView != null) {
            mAddAccountsView.setVisibility(visible && !isLocalShown() ? View.VISIBLE : View.GONE);
        }
    }
}
