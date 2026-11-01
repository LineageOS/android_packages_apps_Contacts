/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.activities;

import android.Manifest;
import android.accounts.Account;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncStatusObserver;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import androidx.annotation.LayoutRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.drawer.DrawerFragment;
import com.android.contacts.drawer.DrawerFragment.DrawerFragmentListener;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.list.AccountFilterActivity;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.list.ContactListFilterController.ContactListFilterListener;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.MultiSelectContactsListFragment;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.logging.Logger;
import com.android.contacts.logging.ScreenEvent.ScreenType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.util.AccountFilterUtil;
import com.android.contacts.util.Constants;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.MaterialColorMapUtils;
import com.android.contacts.util.PermissionsUtil;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contacts.util.SyncUtil;
import com.android.contacts.util.ViewUtil;
import com.android.contacts.widget.FloatingActionButtonController;
import com.android.contactsbind.FeatureHighlightHelper;
import com.android.contactsbind.HelpUtils;
import com.android.contactsbind.ObjectFactory;

import com.google.common.util.concurrent.Futures;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends AppCompatContactsActivity implements
        DrawerFragmentListener,
        SelectAccountDialogFragment.Listener {

    /** Possible views of Contacts app. */
    public enum ContactsView {
        NONE,
        ALL_CONTACTS,
        ASSISTANT,
        ACCOUNT_VIEW,
    }

    private static final String TAG = "PeopleActivity";
    private static final String TAG_ALL = "contacts-all";
    private static final String TAG_UNAVAILABLE = "contacts-unavailable";
    private static final String TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog";
    private static final String TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog";

    public static final String TAG_ASSISTANT = "contacts-assistant";
    public static final String TAG_SECOND_LEVEL = "second-level";
    public static final String TAG_THIRD_LEVEL = "third-level";
    public static final String TAG_ASSISTANT_HELPER = "assistant-helper";
    public static final String TAG_DUPLICATES = "DuplicatesFragment";
    public static final String TAG_DUPLICATES_UTIL = "DuplicatesUtilFragment";

    private static final String KEY_CONTACTS_VIEW = "contactsView";
    private static final String KEY_NEW_GROUP_ACCOUNT = "newGroupAccount";

    private static final long DRAWER_CLOSE_DELAY = 300L;

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;
    private AccountTypeManager mAccountTypeManager;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private boolean wasLastFabAnimationScaleIn = false;

    private ProviderStatusWatcher mProviderStatusWatcher;
    private Integer mProviderStatus;

    private BroadcastReceiver mSaveServiceListener;

    private boolean mShouldSwitchToGroupView;

    private ContactsView mCurrentView;

    private CoordinatorLayout mLayoutRoot;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private DefaultContactBrowseListFragment mContactsListFragment;

    /**
     * True if this activity instance is a re-created one.  i.e. set true after orientation change.
     */
    private boolean mIsRecreatedInstance;

    private boolean mShouldSwitchToAllContacts;

    /** Sequential ID assigned to each instance; used for logging */
    private final int mInstanceId;
    private static final AtomicInteger sNextInstanceId = new AtomicInteger();

    private ContactListFilterController mContactListFilterController;

    /** Navigation drawer related */
    private DrawerLayout mDrawerLayout;
    private DrawerFragment mDrawerFragment;
    private ContactsActionBarDrawerToggle mToggle;
    private Toolbar mToolbar;

    // The account the new group will be created under.
    private AccountWithDataSet mNewGroupAccount;

    private Object mStatusChangeListenerHandle;

    private final Handler mHandler = new Handler();

    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            mHandler.post(new Runnable() {
                public void run() {
                    onSyncStateUpdated();
                }
            });
        }
    };

    // Update sync status for accounts in current ContactListFilter
    private void onSyncStateUpdated() {
        if (isListFragmentInSearchMode() || isListFragmentInSelectionMode()) {
            return;
        }

        final ContactListFilter filter = mContactListFilterController.getFilter();
        if (filter != null) {
            final SwipeRefreshLayout swipeRefreshLayout =
                    mContactsListFragment.getSwipeRefreshLayout();
            if (swipeRefreshLayout == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Can not load swipeRefreshLayout, swipeRefreshLayout is null");
                }
                return;
            }

            final List<AccountWithDataSet> accounts;
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT &&
                    filter.isGoogleAccountType()) {
                accounts = Collections.singletonList(new AccountWithDataSet(filter.accountName,
                        filter.accountType, null));
            } else if (filter.shouldShowSyncState()) {
                accounts = AccountInfo.extractAccounts(
                        mAccountTypeManager.getWritableGoogleAccounts());
            } else {
                accounts = Collections.emptyList();
            }
            if (SyncUtil.isAnySyncing(accounts)) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public void showConnectionErrorMsg() {
        Snackbar.make(mLayoutRoot, R.string.connection_error_message, Snackbar.LENGTH_LONG).show();
    }

    private final ContactListFilterListener mFilterListener = new ContactListFilterListener() {
        @Override
        public void onContactListFilterChanged() {
            final ContactListFilter filter = mContactListFilterController.getFilter();
            handleFilterChangeForFragment(filter);
            handleFilterChangeForActivity(filter);
        }
    };

    private final ProviderStatusListener mProviderStatusListener = new ProviderStatusListener() {
        @Override
        public void onProviderStatusChange() {
            // TODO see if it works with drawer fragment.
            updateViewConfiguration(false);
        }
    };

    private class ContactsActionBarDrawerToggle extends ActionBarDrawerToggle {
        private boolean mMenuClickedBefore = SharedPreferenceUtil.getHamburgerMenuClickedBefore(
                PeopleActivity.this);

        public ContactsActionBarDrawerToggle(AppCompatActivity activity, DrawerLayout drawerLayout,
                Toolbar toolbar, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
            super(activity, drawerLayout, toolbar, openDrawerContentDescRes,
                    closeDrawerContentDescRes);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            if (!mMenuClickedBefore) {
                SharedPreferenceUtil.setHamburgerMenuClickedBefore(PeopleActivity.this);
                mMenuClickedBefore = true;
            }
            drawerView.requestFocus();
            invalidateOptionsMenu();
            // Stop search and selection mode like Gmail and Keep. Otherwise, if user switches to
            // another fragment in navigation drawer, the current search/selection mode will be
            // overlaid by the action bar of the newly-created fragment.
            stopSearchAndSelection();
            updateStatusBarBackground();
        }

        private void stopSearchAndSelection() {
            final MultiSelectContactsListFragment listFragment;
            if (isAllContactsView() || isAccountView()) {
                listFragment = getListFragment();
            } else {
                listFragment = null;
            }
            if (listFragment == null) {
                return;
            }
            final ActionBarAdapter actionBarAdapter = listFragment.getActionBarAdapter();
            if (actionBarAdapter == null) {
                return;
            }
            if (actionBarAdapter.isSearchMode()) {
                actionBarAdapter.setSearchMode(false);
            } else if (actionBarAdapter.isSelectionMode()) {
                actionBarAdapter.setSelectionMode(false);
            }
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            invalidateOptionsMenu();
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            super.onDrawerStateChanged(newState);
            // Set transparent status bar when drawer starts to move.
            if (newState != DrawerLayout.STATE_IDLE) {
                updateStatusBarBackground();
            }
        }
    }


    public PeopleActivity() {
        mInstanceId = sNextInstanceId.getAndIncrement();
        mIntentResolver = new ContactsIntentResolver(this);
        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);
    }

    @Override
    public String toString() {
        // Shown on logcat
        return String.format("%s@%d", getClass().getSimpleName(), mInstanceId);
    }

    private boolean areContactsAvailable() {
        return (mProviderStatus != null) && mProviderStatus.equals(ProviderStatus.STATUS_NORMAL);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
        }

        // Make sure this is *before* calling super.onCreate
        setTheme(R.style.PeopleActivityTheme);
        super.onCreate(savedState);

        mAccountTypeManager = AccountTypeManager.getInstance(this);
        mContactListFilterController = ContactListFilterController.getInstance(this);

        RequestPermissionsActivity.startPermissionActivityIfNeeded(this);

        if (!processIntent(false)) {
            finish();
            return;
        }

        mContactListFilterController.checkFilterValidity(false);

        super.setContentView(R.layout.contacts_drawer_activity);

        // Set up the action bar.
        mToolbar = getView(R.id.toolbar);
        setSupportActionBar(mToolbar);

        // Add shadow under toolbar.
        ViewUtil.addRectangularOutlineProvider(findViewById(R.id.toolbar_parent), getResources());

        // Set up hamburger button.
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerFragment = (DrawerFragment) getFragmentManager().findFragmentById(R.id.drawer);
        mToggle = new ContactsActionBarDrawerToggle(this, mDrawerLayout, mToolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.setDrawerListener(mToggle);
        // Set fallback handler for when drawer is disabled.
        mToggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // Set up navigation mode.
        if (savedState != null) {
            mCurrentView = ContactsView.values()[savedState.getInt(KEY_CONTACTS_VIEW)];
        } else {
            mCurrentView = ContactsView.ALL_CONTACTS;
        }

        if (savedState != null && savedState.containsKey(KEY_NEW_GROUP_ACCOUNT)) {
            mNewGroupAccount = AccountWithDataSet.unstringify(
                    savedState.getString(KEY_NEW_GROUP_ACCOUNT));
        }

        mContactListFilterController.addListener(mFilterListener);
        mProviderStatusWatcher.addListener(mProviderStatusListener);

        mIsRecreatedInstance = (savedState != null);

        createViewsAndFragments();

        // Registered while created, as groups are deleted from GroupActivity, above this one.
        mSaveServiceListener = new SaveServiceListener();
        LocalBroadcastManager.getInstance(this).registerReceiver(mSaveServiceListener,
                new IntentFilter(ContactSaveService.BROADCAST_GROUP_DELETED));

        if (!PermissionsUtil.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        getWindow().setBackgroundDrawable(null);
    }

    @SuppressWarnings("MissingSuperCall") // TODO: Fix me
    @Override
    protected void onNewIntent(Intent intent) {
        final String action = intent.getAction();
        if (GroupUtil.ACTION_CREATE_GROUP.equals(action)) {
            final Uri groupUri = intent.getData();
            if (groupUri == null) {
                toast(R.string.groupSavedErrorToast);
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + groupUri);
            toast(R.string.groupCreatedToast);
            startGroupActivity(groupUri);
            return;
        }

        setIntent(intent);

        if (!processIntent(true)) {
            finish();
            return;
        }

        if (mShouldSwitchToGroupView) {
            startGroupActivity(mRequest.getContactUri());
            mShouldSwitchToGroupView = false;
        }

        mContactListFilterController.checkFilterValidity(false);

        if (!isInSecondLevel()) {
            // Re-initialize ActionBarAdapter because {@link #onNewIntent(Intent)} doesn't invoke
            // {@link Fragment#onActivityCreated(Bundle)} where we initialize ActionBarAdapter
            // initially.
            mContactsListFragment.setParameters(/* ContactsRequest */ mRequest,
                    /* fromOnNewIntent */ true);
            mContactsListFragment.initializeActionBarAdapter(null);
        }

        initializeFabVisibility();
        invalidateOptionsMenuIfNeeded();
    }

    private void toast(int resId) {
        if (resId >= 0) {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Resolve the intent and initialize {@link #mRequest}, and launch another activity if redirect
     * is needed.
     *
     * @param forNewIntent set true if it's called from {@link #onNewIntent(Intent)}.
     * @return {@code true} if {@link PeopleActivity} should continue running.  {@code false}
     *         if it shouldn't, in which case the caller should finish() itself and shouldn't do
     *         farther initialization.
     */
    private boolean processIntent(boolean forNewIntent) {
        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent
                    + " intent=" + getIntent() + " request=" + mRequest);
        }
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            return false;
        }

        switch (mRequest.getActionCode()) {
            case ContactsRequest.ACTION_VIEW_CONTACT: {
                ImplicitIntentsUtil.startQuickContact(
                        this, mRequest.getContactUri(), ScreenType.UNKNOWN);
                return false;
            }
            case ContactsRequest.ACTION_INSERT_GROUP: {
                onCreateGroupMenuItemClicked();
                return true;
            }
            case ContactsRequest.ACTION_VIEW_GROUP:
            case ContactsRequest.ACTION_EDIT_GROUP: {
                mShouldSwitchToGroupView = true;
                return true;
            }
        }
        return true;
    }

    private void createViewsAndFragments() {
        setContentView(R.layout.people_activity);

        final FragmentManager fragmentManager = getFragmentManager();

        setUpListFragment(fragmentManager);

        // Configure floating action button
        mFloatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        final ImageButton floatingActionButton
                = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountFilterUtil.startEditorIntent(PeopleActivity.this, getIntent(),
                        mContactListFilterController.getFilter());
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                mFloatingActionButtonContainer, floatingActionButton);

        invalidateOptionsMenuIfNeeded();

        mLayoutRoot = (CoordinatorLayout) findViewById(R.id.root);

        if (mShouldSwitchToGroupView && !mIsRecreatedInstance) {
            startGroupActivity(mRequest.getContactUri());
            mShouldSwitchToGroupView = false;
        }
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        final ViewGroup parent = (ViewGroup) findViewById(R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(this).inflate(layoutResID, parent);
    }

    private void setUpListFragment(FragmentManager fragmentManager) {
        mContactsListFragment = (DefaultContactBrowseListFragment)
                fragmentManager.findFragmentByTag(TAG_ALL);

        if (mContactsListFragment == null) {
            mContactsListFragment = new DefaultContactBrowseListFragment();
            mContactsListFragment.setAnimateOnLoad(true);
            fragmentManager.beginTransaction()
                    .add(R.id.contacts_list_container, mContactsListFragment, TAG_ALL)
                    .commit();
            fragmentManager.executePendingTransactions();
        }

        mContactsListFragment.setContactsAvailable(areContactsAvailable());
        mContactsListFragment.setListType(mContactListFilterController.getFilterListType());
        mContactsListFragment.setParameters(/* ContactsRequest */ mRequest,
                /* fromOnNewIntent */ false);
    }

    @Override
    protected void onPause() {
        mProviderStatusWatcher.stop();

        super.onPause();

        ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        onSyncStateUpdated();
    }

    @SuppressWarnings("MissingSuperCall") // TODO: Fix me
    @Override
    public void onMultiWindowModeChanged(boolean entering) {
        initializeHomeVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            updateStatusBarBackground();
        }

        if (mShouldSwitchToAllContacts) {
            switchToAllContacts();
        }

        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                mSyncStatusObserver);
        onSyncStateUpdated();

        initializeFabVisibility();
        initializeHomeVisibility();
    }

    public void updateStatusBarBackground() {
        updateStatusBarBackground(/* color */ -1);
    }

    public void updateStatusBarBackground(int color) {
        if (!CompatUtils.isLollipopCompatible()) return;
        if (color == -1) {
            mDrawerLayout.setStatusBarBackgroundColor(
                    MaterialColorMapUtils.getStatusBarColor(this));
        } else {
            mDrawerLayout.setStatusBarBackgroundColor(color);
        }
        mDrawerLayout.invalidate();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    @Override
    protected void onDestroy() {
        if (mSaveServiceListener != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mSaveServiceListener);
        }
        mProviderStatusWatcher.removeListener(mProviderStatusListener);
        mContactListFilterController.removeListener(mFilterListener);
        super.onDestroy();
    }

    private void initializeFabVisibility() {
        mFloatingActionButtonContainer.setVisibility(shouldHideFab() ? View.GONE : View.VISIBLE);
        mFloatingActionButtonController.resetIn();
        wasLastFabAnimationScaleIn = !shouldHideFab();
    }

    private void initializeHomeVisibility() {
        // Remove the navigation icon if we return to the fragment in a search or select state
        if (getToolbar() != null && (isListFragmentInSelectionMode()
                || isListFragmentInSearchMode())) {
            getToolbar().setNavigationIcon(null);
        }
    }

    private boolean shouldHideFab() {
        if (mContactsListFragment != null && mContactsListFragment.getActionBarAdapter() == null
                || isInSecondLevel()) {
            return true;
        }
        return isListFragmentInSearchMode()
                || isListFragmentInSelectionMode();
    }

    public void showFabWithAnimation(boolean showFab) {
        if (mFloatingActionButtonContainer == null) {
            return;
        }
        if (showFab) {
            if (!wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleIn(0);
            }
            wasLastFabAnimationScaleIn = true;

        } else {
            if (wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleOut();
            }
            wasLastFabAnimationScaleIn = false;
        }
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        int providerStatus = mProviderStatusWatcher.getProviderStatus();
        if (!forceUpdate && (mProviderStatus != null)
                && (mProviderStatus.equals(providerStatus))) return;
        mProviderStatus = providerStatus;

        final FragmentManager fragmentManager= getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Change in CP2's provider status may not take effect immediately, see b/30566908.
        // So we need to handle the case where provider status is STATUS_EMPTY and there is
        // actually at least one real account (not "local" account) on device.
        if (shouldShowList()) {
            if (mContactsListFragment != null) {
                final Fragment unavailableFragment = fragmentManager
                        .findFragmentByTag(TAG_UNAVAILABLE);
                if (unavailableFragment != null) {
                    transaction.remove(unavailableFragment);
                }
                if (mContactsListFragment.isHidden()) {
                    transaction.show(mContactsListFragment);
                }
                mContactsListFragment.setContactsAvailable(areContactsAvailable());
                mContactsListFragment.setEnabled(true);
            }
        } else {
            // Setting up the page so that the user can still use the app
            // even without an account.
            if (mContactsListFragment != null) {
                mContactsListFragment.setEnabled(false);
            }
            final ContactsUnavailableFragment fragment = new ContactsUnavailableFragment();
            transaction.hide(mContactsListFragment);
            transaction.replace(R.id.contacts_unavailable_container, fragment, TAG_UNAVAILABLE);
            fragment.updateStatus(mProviderStatus);
        }
        if (!transaction.isEmpty()) {
            transaction.commit();
            fragmentManager.executePendingTransactions();
        }

        invalidateOptionsMenuIfNeeded();
    }

    private boolean shouldShowList() {
        return mProviderStatus != null
                && ((mProviderStatus.equals(ProviderStatus.STATUS_EMPTY)
                && mAccountTypeManager.hasNonLocalAccount())
                || mProviderStatus.equals(ProviderStatus.STATUS_NORMAL));
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (mContactsListFragment != null
                && mContactsListFragment.getOptionsMenuContactsAvailable()
                != areContactsAvailable()) {
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If the drawer is open, consume KEYCODE_BACK event only.
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Should eventually go to onBackPressed().
                return super.onKeyDown(keyCode, event);
            }
            return false;
        }
        // Bring up the search UI if the user starts typing
        final int unicodeChar = event.getUnicodeChar();
        if ((unicodeChar != 0)
                // If COMBINING_ACCENT is set, it's not a unicode character.
                && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                && !Character.isWhitespace(unicodeChar)) {
            if (mContactsListFragment.onKeyDown(unicodeChar)) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }

        // Handle the back event in drawer first.
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer();
            return;
        }

        // Handle the back event in "second level".
        if (isAssistantView()) {
            onBackPressedAssistantView();
            return;
        }

        // If feature highlight is present, let it handle the back event before
        // mContactsListFragment.
        if (FeatureHighlightHelper.tryRemoveHighlight(this)) {
            return;
        }

        // Handle the back event in "first level" - mContactsListFragment.
        if (maybeHandleInListFragment()) {
            return;
        }

        super.onBackPressed();
    }

    private void onBackPressedAssistantView() {
        if (!isInThirdLevel()) {
            switchToAllContacts();
        } else {
            setDrawerLockMode(/* enabled */ true);
            super.onBackPressed();
        }
    }

    // Returns true if back event is handled in this method.
    private boolean maybeHandleInListFragment() {
        if (isListFragmentInSelectionMode()) {
            mContactsListFragment.getActionBarAdapter().setSelectionMode(false);
            return true;
        }

        if (isListFragmentInSearchMode()) {
            mContactsListFragment.getActionBarAdapter().setSearchMode(false);
            if (mContactsListFragment.wasSearchResultClicked()) {
                mContactsListFragment.resetSearchResultClicked();
            } else {
                Logger.logScreenView(this, ScreenType.SEARCH_EXIT);
                Logger.logSearchEvent(mContactsListFragment.createSearchState());
            }
            return true;
        }

        if (!AccountFilterUtil.isAllContactsFilter(mContactListFilterController.getFilter())
                && !mContactsListFragment.isHidden()) {
            // If mContactsListFragment is hidden, then mContactsUnavailableFragment is visible so we
            // don't need to switch to all contacts.
            switchToAllContacts();
            return true;
        }

        return false;
    }

    private boolean isListFragmentInSelectionMode() {
        return mContactsListFragment != null && mContactsListFragment.getActionBarAdapter() != null
                && mContactsListFragment.getActionBarAdapter().isSelectionMode();
    }

    private boolean isListFragmentInSearchMode() {
        return mContactsListFragment != null && mContactsListFragment.getActionBarAdapter() != null
                && mContactsListFragment.getActionBarAdapter().isSearchMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNewGroupAccount != null) {
            outState.putString(KEY_NEW_GROUP_ACCOUNT, mNewGroupAccount.stringify());
        }
        outState.putInt(KEY_CONTACTS_VIEW, mCurrentView.ordinal());
    }

    private void onGroupDeleted(final Intent intent) {
        if (!ContactSaveService.canUndo(intent)) return;

        final AccessibilityManager am =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        //TODO set to INDEFINITE and track user interaction to dismiss b/33208886
        final int accessibilityLength = 15000;
        final int length = am.isEnabled() ? accessibilityLength : Snackbar.LENGTH_LONG;
        final String message = getString(R.string.groupDeletedToast);

        final Snackbar snackbar = Snackbar.make(mLayoutRoot, message, length)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ContactSaveService.startService(PeopleActivity.this,
                                ContactSaveService.createUndoIntent(PeopleActivity.this, intent));
                    }
                }).setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_action_text));

        // Announce for a11y talkback
        mLayoutRoot.announceForAccessibility(message);
        mLayoutRoot.announceForAccessibility(getString(R.string.undo));

        snackbar.show();
    }

    private class SaveServiceListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ContactSaveService.BROADCAST_GROUP_DELETED:
                    onGroupDeleted(intent);
                    break;
            }
        }
    }

    private void onGroupMenuItemClicked(long groupId) {
        final Uri groupUri = ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI,
                groupId);
        // Let the drawer close first.
        new Handler().postDelayed(() -> startGroupActivity(groupUri), DRAWER_CLOSE_DELAY);
    }

    private void startGroupActivity(Uri groupUri) {
        if (!GroupUtil.isGroupUri(groupUri)) {
            toast(R.string.groupLoadErrorToast);
            return;
        }
        startActivity(GroupActivity.createIntent(this, groupUri));
    }

    private void onFilterMenuItemClicked(Intent intent) {
        // We must pop second level first to "restart" mContactsListFragment before changing filter.
        if (isInSecondLevel()) {
            popSecondLevel();
            showFabWithAnimation(/* showFab */ true);
            // HACK: swap the current filter to force listeners to update because the group
            // member view no longer changes the filter. Fix for b/32223767
            final ContactListFilter current = mContactListFilterController.getFilter();
            mContactListFilterController.setContactListFilter(
                    AccountFilterUtil.createContactsFilter(this), false);
            mContactListFilterController.setContactListFilter(current, false);
        }
        mCurrentView = ContactsView.ACCOUNT_VIEW;
        AccountFilterUtil.handleAccountFilterResult(mContactListFilterController,
                AppCompatActivity.RESULT_OK, intent);
    }

    protected void launchAssistant() {
        switchView(ContactsView.ASSISTANT);
    }

    private void switchView(ContactsView contactsView) {
        mCurrentView = contactsView;

        final FragmentManager fragmentManager =  getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        popSecondLevel();
        if (isAssistantView()) {
            Fragment uiFragment = fragmentManager.findFragmentByTag(TAG_ASSISTANT);
            Fragment unavailableFragment = fragmentManager.findFragmentByTag(TAG_UNAVAILABLE);
            if (uiFragment == null) {
                uiFragment = ObjectFactory.getAssistantFragment();
            }
            if (unavailableFragment != null) {
                transaction.remove(unavailableFragment);
            }
            transaction.replace(R.id.contacts_list_container, uiFragment, TAG_ASSISTANT);
            resetToolBarStatusBarColor();
        }
        transaction.addToBackStack(TAG_SECOND_LEVEL);
        transaction.commit();
        fragmentManager.executePendingTransactions();

        showFabWithAnimation(/* showFab */ false);
    }

    public void switchToAllContacts() {
        popSecondLevel();
        mShouldSwitchToAllContacts = false;
        mCurrentView = ContactsView.ALL_CONTACTS;
        mDrawerFragment.setNavigationItemChecked(ContactsView.ALL_CONTACTS);
        showFabWithAnimation(/* showFab */ true);
        mContactsListFragment.scrollToTop();
        resetFilter();
        setTitle(getString(R.string.contactsList));
    }

    private void resetFilter() {
        final Intent intent = new Intent();
        final ContactListFilter filter = AccountFilterUtil.createContactsFilter(this);
        intent.putExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER, filter);
        AccountFilterUtil.handleAccountFilterResult(
                mContactListFilterController, AppCompatActivity.RESULT_OK, intent);
    }

    // Reset toolbar and status bar color to Contacts theme color.
    private void resetToolBarStatusBarColor() {
        findViewById(R.id.toolbar_frame).setBackgroundColor(
                ContextCompat.getColor(this, R.color.primary_color));
        updateStatusBarBackground(ContextCompat.getColor(this, R.color.primary_color_dark));
    }

    protected DefaultContactBrowseListFragment getListFragment() {
        return mContactsListFragment;
    }

    private void handleFilterChangeForFragment(ContactListFilter filter) {
        if (mContactsListFragment.canSetActionBar()) {
            mContactsListFragment.setFilterAndUpdateTitle(filter);
            // Scroll to top after filter is changed.
            mContactsListFragment.scrollToTop();
        }
    }

    private void handleFilterChangeForActivity(ContactListFilter filter) {
        // The filter was changed while this activity was in the background. If we're in the
        // assistant view Switch to the main contacts list when we resume to prevent
        // b/31838582 and b/31829161
        // TODO: this is a hack; we need to do some cleanup of the contact list filter stuff
        if (isAssistantView() && filter.isContactsFilterType()) {
            mShouldSwitchToAllContacts = true;
        }

        if (CompatUtils.isNCompatible()) {
            getWindow().getDecorView()
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        invalidateOptionsMenu();
    }

    public void setDrawerLockMode(boolean enabled) {
        // Prevent drawer from being opened by sliding from the start of screen.
        mDrawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED
                : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        // Order of these statements matter.
        // Display back button and disable drawer indicator.
        if (enabled) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            mToggle.setDrawerIndicatorEnabled(true);
        } else {
            mToggle.setDrawerIndicatorEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mToggle.onConfigurationChanged(newConfig);
    }

    protected void onCreateGroupMenuItemClicked() {
        // Select the account to create the group
        final Bundle extras = getIntent().getExtras();
        final Account account = extras == null ? null :
                (Account) extras.getParcelable(Intents.Insert.EXTRA_ACCOUNT);
        if (account == null) {
            selectAccountForNewGroup();
        } else {
            final String dataSet = extras == null
                    ? null : extras.getString(Intents.Insert.EXTRA_DATA_SET);
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    account.name, account.type, dataSet);
            onAccountChosen(accountWithDataSet, /* extraArgs */ null);
        }
    }

    private void selectAccountForNewGroup() {
        // This should never block because the DrawerFragment loads the accounts and the
        // "Create Label" item only exists when that loading finishes
        final List<AccountInfo> accounts = Futures.getUnchecked(AccountTypeManager.getInstance(this)
                .filterAccountsAsync(AccountTypeManager.AccountFilter.GROUPS_WRITABLE));
        if (accounts.isEmpty()) {
            // We shouldn't present the add group button if there are no writable accounts
            // but check it since it's possible we are started with an Intent.
            Toast.makeText(this, R.string.groupCreateFailedToast, Toast.LENGTH_SHORT).show();
            return;
        }
        // If there is a single writable account, use it w/o showing a dialog.
        if (accounts.size() == 1) {
            onAccountChosen(accounts.get(0).getAccount(), /* extraArgs */ null);
            return;
        }
        SelectAccountDialogFragment.show(getFragmentManager(), R.string.dialog_new_group_account,
                AccountTypeManager.AccountFilter.GROUPS_WRITABLE, /* extraArgs */ null,
                TAG_SELECT_ACCOUNT_DIALOG);
    }

    // Implementation of SelectAccountDialogFragment.Listener
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        mNewGroupAccount = account;
        GroupNameEditDialogFragment.newInstanceForCreation(
                mNewGroupAccount, GroupUtil.ACTION_CREATE_GROUP)
                .show(getFragmentManager(), TAG_GROUP_NAME_EDIT_DIALOG);
    }

    @Override
    public void onAccountSelectorCancelled() {
    }

    // Implementation of DrawerFragmentListener
    @Override
    public void onDrawerItemClicked(){
        closeDrawer();
    }

    @Override
    public void onContactsViewSelected(ContactsView mode) {
        if (mode == ContactsView.ALL_CONTACTS) {
            switchToAllContacts();
        } else if (mode == ContactsView.ASSISTANT) {
            launchAssistant();
        } else {
            throw new IllegalStateException("Unknown view " + mode);
        }
    }

    @Override
    public void onCreateLabelButtonClicked() {
        onCreateGroupMenuItemClicked();
    }

    @Override
    public void onOpenSettings() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(createPreferenceIntent());
            }
        }, DRAWER_CLOSE_DELAY);
    }

    @Override
    public void onLaunchHelpFeedback() {
        HelpUtils.launchHelpAndFeedbackForMainScreen(this);
    }

    @Override
    public void onGroupViewSelected(GroupListItem groupListItem) {
        onGroupMenuItemClicked(groupListItem.getGroupId());
    }

    @Override
    public void onAccountViewSelected(ContactListFilter filter) {
        final Intent intent = new Intent();
        intent.putExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER, filter);
        onFilterMenuItemClicked(intent);
    }

    /*
     * Behavior when select MyInfo on Navigation drawer.
     * Launch in the same way as from Settings->MyInfo.
     * Refer to ContactsPreferenceActivity's openMyInfo.
     */
    @Override
    public void onProfileViewSelected(long profileContactId) {
        if (profileContactId != -1) {
            final Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                    profileContactId);
            ImplicitIntentsUtil.startQuickContact(this, uri, ScreenType.ME_CONTACT);
        } else {
            final Intent intent = new Intent(Intent.ACTION_INSERT,
                    ContactsContract.Contacts.CONTENT_URI);
            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
            ImplicitIntentsUtil.startActivityInApp(this, intent);
        }
    }

    /*
     * Behavior when select Emergency information on Navigation drawer.
     */
    @Override
    public void onEmergencyViewSelected() {
        ImplicitIntentsUtil.startActivityOutsideApp(this,
                ImplicitIntentsUtil.getIntentForEmergencyInfo(this));
    }

    protected boolean isAssistantView() {
        return mCurrentView == ContactsView.ASSISTANT;
    }

    protected boolean isAllContactsView() {
        return mCurrentView == ContactsView.ALL_CONTACTS;
    }

    protected boolean isAccountView() {
        return mCurrentView == ContactsView.ACCOUNT_VIEW;
    }

    public boolean isInSecondLevel() {
        return isAssistantView();
    }

    private boolean isInThirdLevel() {
        return isLastBackStackTag(TAG_THIRD_LEVEL);
    }

    private boolean isLastBackStackTag(String tag) {
        final int count = getFragmentManager().getBackStackEntryCount();
        if (count > 0) {
            final FragmentManager.BackStackEntry last =
                    getFragmentManager().getBackStackEntryAt(count - 1);
            if (tag == null) {
                return last.getName() == null;
            }
            return tag.equals(last.getName());
        }
        return false;
    }

    private void popSecondLevel() {
        getFragmentManager().popBackStackImmediate(
                TAG_ASSISTANT_HELPER, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getFragmentManager().popBackStackImmediate(
                TAG_SECOND_LEVEL, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        resetToolBarStatusBarColor();
    }

    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    private Intent createPreferenceIntent() {
        final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
        intent.putExtra(ContactsPreferenceActivity.EXTRA_NEW_LOCAL_PROFILE,
                ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE);
        return intent;
    }


}
