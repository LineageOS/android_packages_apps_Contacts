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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.QuickContact;
import android.provider.LocalGroups.Group;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.TabState;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailLayoutController;
import com.android.contacts.detail.ContactDetailUpdatesFragment;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.editor.MultiPickContactActivity;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.group.GroupBrowseListFragment;
import com.android.contacts.group.GroupBrowseListFragment.OnGroupBrowserActionListener;
import com.android.contacts.group.GroupDetailFragment;
import com.android.contacts.group.local.AddLocalGroupDialog;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.interactions.ImportExportDialogFragment.ExportToSimThread;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactTileAdapter.DisplayType;
import com.android.contacts.list.ContactTileFrequentFragment;
import com.android.contacts.list.ContactTileListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.preference.DisplayOptionsPreferenceFragment;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.util.AccountPromptUtils;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.Constants;
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.HelpUtils;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.common.util.UriUtils;
import com.android.contacts.common.vcard.ExportVCardActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;
import com.android.contacts.util.XCloudManager;
import com.android.contacts.widget.TransitionAnimationView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts. For xlarge screens, this also displays a detail-pane on
 * the right.
 */
public class PeopleActivity extends ContactsActivity
        implements View.OnCreateContextMenuListener, ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener, ProviderStatusListener {

    private static final String TAG = "PeopleActivity";

    public static String EDITABLE_KEY = "search_contacts";

    public static final String RESULT_KEY = "result";

    private static final int TAB_FADE_IN_DURATION = 500;

    private static final String ENABLE_DEBUG_OPTIONS_HIDDEN_CODE = "debug debug!";

    // These values needs to start at 2. See {@link ContactEntryListFragment}.
    private static final int SUBACTIVITY_NEW_CONTACT = 2;
    private static final int SUBACTIVITY_EDIT_CONTACT = 3;
    private static final int SUBACTIVITY_NEW_GROUP = 4;
    private static final int SUBACTIVITY_EDIT_GROUP = 5;
    private static final int SUBACTIVITY_ACCOUNT_FILTER = 6;

    private final DialogManager mDialogManager = new DialogManager(this);

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;

    private ActionBarAdapter mActionBarAdapter;

    private ContactDetailFragment mContactDetailFragment;

    private ContactLoaderFragment mContactDetailLoaderFragment;
    private final ContactDetailLoaderFragmentListener mContactDetailLoaderFragmentListener =
            new ContactDetailLoaderFragmentListener();

    private GroupDetailFragment mGroupDetailFragment;
    private final GroupDetailFragmentListener mGroupDetailFragmentListener =
            new GroupDetailFragmentListener();

    private ContactTileListFragment.Listener mFavoritesFragmentListener =
            new StrequentContactListFragmentListener();

    private ContactListFilterController mContactListFilterController;

    private ContactsUnavailableFragment mContactsUnavailableFragment;
    private ProviderStatusWatcher mProviderStatusWatcher;
    private ProviderStatusWatcher.Status mProviderStatus;

    private boolean mOptionsMenuContactsAvailable;
    public boolean isLocalGroupsShown;
    private MenuItem switchGroupsMenu;
    private MenuItem addGroupMenu;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private DefaultContactBrowseListFragment mAllFragment;
    private ContactTileListFragment mFavoritesFragment;
    private ContactTileFrequentFragment mFrequentFragment;
    private GroupBrowseListFragment mGroupsFragment;

    private View mFavoritesView;
    private View mBrowserView;
    private TransitionAnimationView mPeopleActivityView;
    private TransitionAnimationView mContactDetailsView;
    private TransitionAnimationView mGroupDetailsView;
    private View mAddGroupImageView;

    /** ViewPager for swipe, used only on the phone (i.e. one-pane mode) */
    private ViewPager mTabPager;
    private TabPagerAdapter mTabPagerAdapter;
    private final TabPagerListener mTabPagerListener = new TabPagerListener();

    private ContactDetailLayoutController mContactDetailLayoutController;

    private boolean mEnableDebugMenuOptions;

    private final Handler mHandler = new Handler();
    private ExportToSimThread mExportThread = null;

    /**
     * True if this activity instance is a re-created one.  i.e. set true after orientation change.
     * This is set in {@link #onCreate} for later use in {@link #onStart}.
     */
    private boolean mIsRecreatedInstance;

    /**
     * If {@link #configureFragments(boolean)} is already called.  Used to avoid calling it twice
     * in {@link #onStart}.
     * (This initialization only needs to be done once in onStart() when the Activity was just
     * created from scratch -- i.e. onCreate() was just called)
     */
    private boolean mFragmentInitialized;

    /**
     * Whether or not the current contact filter is valid or not. We need to do a check on
     * start of the app to verify that the user is not in single contact mode. If so, we should
     * dynamically change the filter, unless the incoming intent specifically requested a contact
     * that should be displayed in that mode.
     */
    private boolean mCurrentFilterIsValid;

    /**
     * This is to disable {@link #onOptionsItemSelected} when we trying to stop the activity.
     */
    private boolean mDisableOptionItemSelected;


    /** Sequential ID assigned to each instance; used for logging */
    private final int mInstanceId;
    private static final AtomicInteger sNextInstanceId = new AtomicInteger();
    // TODO: we need to refactor the export code in future release.
    // QRD enhancement: contacts list for multi contact pick
    private ArrayList<String[]> mContactList;

    // If Receiver is registered, this variable will be true, otherwise false.
    private boolean mIsReceiverRegistered = false;
    private final BroadcastReceiver mExportToSimCompleteListener = new BroadcastReceiver (){
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            if (action.equals(SimContactsConstants.INTENT_EXPORT_COMPLETE)){
                ImportExportDialogFragment.destroyExportToSimThread();
                mExportThread = null;
            }
        }
    };

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

    public boolean areContactsAvailable() {
        return (mProviderStatus != null)
                && mProviderStatus.status == ProviderStatus.STATUS_NORMAL;
    }

    private boolean areContactWritableAccountsAvailable() {
        return ContactsUtils.areContactWritableAccountsAvailable(this);
    }

    private boolean areGroupWritableAccountsAvailable() {
        return ContactsUtils.areGroupWritableAccountsAvailable(this);
    }

    /**
     * Initialize fragments that are (or may not be) in the layout.
     *
     * For the fragments that are in the layout, we initialize them in
     * {@link #createViewsAndFragments(Bundle)} after inflating the layout.
     *
     * However, there are special fragments which may not be in the layout, so we have to do the
     * initialization here.
     * The target fragments are:
     * - {@link ContactDetailFragment} and {@link ContactDetailUpdatesFragment}:  They may not be
     *   in the layout depending on the configuration.  (i.e. portrait)
     * - {@link ContactsUnavailableFragment}: We always create it at runtime.
     */
    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactDetailFragment) {
            mContactDetailFragment = (ContactDetailFragment) fragment;
        } else if (fragment instanceof ContactsUnavailableFragment) {
            mContactsUnavailableFragment = (ContactsUnavailableFragment)fragment;
            mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                    new ContactsUnavailableFragmentListener());
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
        }
        super.onCreate(savedState);

        if (!processIntent(false)) {
            finish();
            return;
        }
        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);
        mContactListFilterController.addListener(this);

        mProviderStatusWatcher.addListener(this);

        mIsRecreatedInstance = (savedState != null);
        createViewsAndFragments(savedState);
        getWindow().setBackgroundDrawableResource(R.color.background_primary);
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        final IntentFilter exportCompleteFilter = new IntentFilter(SimContactsConstants
            .INTENT_EXPORT_COMPLETE);
        registerReceiver(mExportToSimCompleteListener, exportCompleteFilter);
        mIsReceiverRegistered = true;
        }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (!processIntent(true)) {
            finish();
            return;
        }
        mActionBarAdapter.initialize(null, mRequest);

        mContactListFilterController.checkFilterValidity(false);
        mCurrentFilterIsValid = true;

        // Re-configure fragments.
        configureFragments(true /* from request */);
        invalidateOptionsMenuIfNeeded();
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

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            return false;
        }

        if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT
                && !PhoneCapabilityTester.isUsingTwoPanes(this)) {
            redirect = new Intent(this, ContactDetailActivity.class);
            redirect.setAction(Intent.ACTION_VIEW);
            redirect.setData(mRequest.getContactUri());
            startActivity(redirect);
            return false;
        }
        return true;
    }

    private void createViewsAndFragments(Bundle savedState) {
        setContentView(R.layout.people_activity);

        final FragmentManager fragmentManager = getFragmentManager();

        // Hide all tabs (the current tab will later be reshown once a tab is selected)
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Prepare the fragments which are used both on 1-pane and on 2-pane.
        final boolean isUsingTwoPanes = PhoneCapabilityTester.isUsingTwoPanes(this);
        if (isUsingTwoPanes) {
            mFavoritesFragment = getFragment(R.id.favorites_fragment);
            mAllFragment = getFragment(R.id.all_fragment);
            mGroupsFragment = getFragment(R.id.groups_fragment);
        } else {
            mTabPager = getView(R.id.tab_pager);
            mTabPagerAdapter = new TabPagerAdapter();
            mTabPager.setAdapter(mTabPagerAdapter);
            mTabPager.setOnPageChangeListener(mTabPagerListener);

            final String FAVORITE_TAG = "tab-pager-favorite";
            final String ALL_TAG = "tab-pager-all";
            final String GROUPS_TAG = "tab-pager-groups";

            // Create the fragments and add as children of the view pager.
            // The pager adapter will only change the visibility; it'll never create/destroy
            // fragments.
            // However, if it's after screen rotation, the fragments have been re-created by
            // the fragment manager, so first see if there're already the target fragments
            // existing.
            mFavoritesFragment = (ContactTileListFragment)
                    fragmentManager.findFragmentByTag(FAVORITE_TAG);
            mAllFragment = (DefaultContactBrowseListFragment)
                    fragmentManager.findFragmentByTag(ALL_TAG);
            mGroupsFragment = (GroupBrowseListFragment)
                    fragmentManager.findFragmentByTag(GROUPS_TAG);

            if (mFavoritesFragment == null) {
                mFavoritesFragment = new ContactTileListFragment();
                mAllFragment = new DefaultContactBrowseListFragment();
                mGroupsFragment = new GroupBrowseListFragment();

                transaction.add(R.id.tab_pager, mFavoritesFragment, FAVORITE_TAG);
                transaction.add(R.id.tab_pager, mAllFragment, ALL_TAG);
                transaction.add(R.id.tab_pager, mGroupsFragment, GROUPS_TAG);
            }
        }

        mFavoritesFragment.setListener(mFavoritesFragmentListener);

        mAllFragment.setOnContactListActionListener(new ContactBrowserActionListener());

        mGroupsFragment.setListener(new GroupBrowserActionListener());

        // Hide all fragments for now.  We adjust visibility when we get onSelectedTabChanged()
        // from ActionBarAdapter.
        transaction.hide(mFavoritesFragment);
        transaction.hide(mAllFragment);
        transaction.hide(mGroupsFragment);

        if (isUsingTwoPanes) {
            // Prepare 2-pane only fragments/views...

            // Container views for fragments
            mPeopleActivityView = getView(R.id.people_view);
            mFavoritesView = getView(R.id.favorites_view);
            mContactDetailsView = getView(R.id.contact_details_view);
            mGroupDetailsView = getView(R.id.group_details_view);
            mBrowserView = getView(R.id.browse_view);

            // Only favorites tab with two panes has a separate frequent fragment
            if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
                mFrequentFragment = getFragment(R.id.frequent_fragment);
                mFrequentFragment.setListener(mFavoritesFragmentListener);
                mFrequentFragment.setDisplayType(DisplayType.FREQUENT_ONLY);
                mFrequentFragment.enableQuickContact(true);
            }

            mContactDetailLoaderFragment = getFragment(R.id.contact_detail_loader_fragment);
            mContactDetailLoaderFragment.setListener(mContactDetailLoaderFragmentListener);

            mGroupDetailFragment = getFragment(R.id.group_detail_fragment);
            mGroupDetailFragment.setListener(mGroupDetailFragmentListener);
            mGroupDetailFragment.setQuickContact(true);

            if (mContactDetailFragment != null) {
                transaction.hide(mContactDetailFragment);
            }
            transaction.hide(mGroupDetailFragment);

            // Configure contact details
            mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                    getFragmentManager(), mContactDetailsView,
                    findViewById(R.id.contact_detail_container),
                    new ContactDetailFragmentListener());
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();

        // Setting Properties after fragment is created
        if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
            mFavoritesFragment.enableQuickContact(true);
            mFavoritesFragment.setDisplayType(DisplayType.STARRED_ONLY);
        } else {
            // For 2-pane in All and Groups but not in Favorites fragment, show the chevron
            // for quick contact popup
            mFavoritesFragment.enableQuickContact(isUsingTwoPanes);
            mFavoritesFragment.setDisplayType(DisplayType.STREQUENT);
        }

        // Configure action bar
        mActionBarAdapter = new ActionBarAdapter(this, this, getActionBar(), isUsingTwoPanes);
        mActionBarAdapter.initialize(savedState, mRequest);

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    protected void onStart() {
        if (!mFragmentInitialized) {
            mFragmentInitialized = true;
            /* Configure fragments if we haven't.
             *
             * Note it's a one-shot initialization, so we want to do this in {@link #onCreate}.
             *
             * However, because this method may indirectly touch views in fragments but fragments
             * created in {@link #configureContentView} using a {@link FragmentTransaction} will NOT
             * have views until {@link Activity#onCreate} finishes (they would if they were inflated
             * from a layout), we need to do it here in {@link #onStart()}.
             *
             * (When {@link Fragment#onCreateView} is called is different in the former case and
             * in the latter case, unfortunately.)
             *
             * Also, we skip most of the work in it if the activity is a re-created one.
             * (so the argument.)
             */
            configureFragments(!mIsRecreatedInstance);
        } else if (PhoneCapabilityTester.isUsingTwoPanes(this) && !mCurrentFilterIsValid) {
            // We only want to do the filter check in onStart for wide screen devices where it
            // is often possible to get into single contact mode. Only do this check if
            // the filter hasn't already been set properly (i.e. onCreate or onActivityResult).

            // Since there is only one {@link ContactListFilterController} across multiple
            // activity instances, make sure the filter controller is in sync withthe current
            // contact list fragment filter.
            // TODO: Clean this up. Perhaps change {@link ContactListFilterController} to not be a
            // singleton?
            mContactListFilterController.setContactListFilter(mAllFragment.getFilter(), true);
            mContactListFilterController.checkFilterValidity(true);
            mCurrentFilterIsValid = true;
        }
        super.onStart();
    }

    @Override
    protected void onPause() {
        mOptionsMenuContactsAvailable = false;
        mProviderStatusWatcher.stop();
        super.onPause();
        dismissDialog(ImportExportDialogFragment.TAG);
        dismissDialog(SelectAccountDialogFragment.TAG);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        // Re-register the listener, which may have been cleared when onSaveInstanceState was
        // called.  See also: onSaveInstanceState
        mActionBarAdapter.setListener(this);
        mDisableOptionItemSelected = false;
        if (mTabPager != null) {
            mTabPager.setOnPageChangeListener(mTabPagerListener);
        }
        // Current tab may have changed since the last onSaveInstanceState().  Make sure
        // the actual contents match the tab.
        updateFragmentsVisibility();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCurrentFilterIsValid = false;
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(this);

        // Some of variables will be null if this Activity redirects Intent.
        // See also onCreate() or other methods called during the Activity's initialization.
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        if (mContactListFilterController != null) {
            mContactListFilterController.removeListener(this);
        }

        if (mIsReceiverRegistered) {
            unregisterReceiver(mExportToSimCompleteListener);
            mIsReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void dismissDialog(String tag) {
        // when this activity lose focus,dismiss the dialog
        Fragment dialogFragment = getFragmentManager().findFragmentByTag(tag);
        if (dialogFragment != null) {
            if (dialogFragment instanceof DialogFragment) {
                ((DialogFragment) dialogFragment).dismiss();
            }
        }
    }

    private void showAddLocalGroupDialog() {
        new AddLocalGroupDialog(this, new AddLocalGroupDialog.AddGroupListener() {
            @Override
            public void onAddGroup(String name) {
                Group group = new Group();
                group.setTitle(name);
                group.save(getContentResolver());
                mGroupsFragment.updateGroupData();
            };
        }).show();
    }

    private void configureFragments(boolean fromRequest) {
        if (fromRequest) {
            ContactListFilter filter = null;
            int actionCode = mRequest.getActionCode();
            boolean searchMode = mRequest.isSearchMode();
            final int tabToOpen;
            switch (actionCode) {
                case ContactsRequest.ACTION_ALL_CONTACTS:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                    tabToOpen = TabState.ALL;
                    break;
                case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                    filter = ContactListFilter.createFilterWithType(
                            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                    tabToOpen = TabState.ALL;
                    break;

                case ContactsRequest.ACTION_FREQUENT:
                case ContactsRequest.ACTION_STREQUENT:
                case ContactsRequest.ACTION_STARRED:
                    tabToOpen = TabState.FAVORITES;
                    break;
                case ContactsRequest.ACTION_VIEW_CONTACT:
                    // We redirect this intent to the detail activity on 1-pane, so we don't get
                    // here.  It's only for 2-pane.
                    Uri currentlyLoadedContactUri = mContactDetailFragment.getUri();
                    if (currentlyLoadedContactUri != null
                            && !mRequest.getContactUri().equals(currentlyLoadedContactUri)) {
                        mContactDetailsView.setMaskVisibility(true);
                    }
                    tabToOpen = TabState.ALL;
                    break;
                case ContactsRequest.ACTION_GROUP:
                    tabToOpen = TabState.GROUPS;
                    break;
                default:
                    tabToOpen = -1;
                    break;
            }
            if (tabToOpen != -1) {
                mActionBarAdapter.setCurrentTab(tabToOpen);
            }

            if (filter != null) {
                mContactListFilterController.setContactListFilter(filter, false);
                searchMode = false;
            }

            if (mRequest.getContactUri() != null) {
                searchMode = false;
            }

            mActionBarAdapter.setSearchMode(searchMode);
            configureContactListFragmentForRequest();
        }

        configureContactListFragment();
        configureGroupListFragment();

        invalidateOptionsMenuIfNeeded();
    }

    @Override
    public void onContactListFilterChanged() {
        if (mAllFragment == null || !mAllFragment.isAdded()) {
            return;
        }

        mAllFragment.setFilter(mContactListFilterController.getFilter());

        invalidateOptionsMenuIfNeeded();
    }

    private void setupContactDetailFragment(final Uri contactLookupUri) {
        mContactDetailLoaderFragment.loadUri(contactLookupUri);
        invalidateOptionsMenuIfNeeded();
    }

    private void setupGroupDetailFragment(Uri groupUri) {
        // If we are switching from one group to another, do a cross-fade
        if (mGroupDetailFragment != null && mGroupDetailFragment.getGroupUri() != null &&
                !UriUtils.areEqual(mGroupDetailFragment.getGroupUri(), groupUri)) {
            mGroupDetailsView.startMaskTransition(false, -1);
        }
        mGroupDetailFragment.loadGroup(groupUri);
        invalidateOptionsMenuIfNeeded();
    }

    /**
     * Handler for action bar actions.
     */
    @Override
    public void onAction(int action) {
        switch (action) {
            case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                // Tell the fragments that we're in the search mode
                configureFragments(false /* from request */);
                updateFragmentsVisibility();
                invalidateOptionsMenu();
                break;
            case ActionBarAdapter.Listener.Action.STOP_SEARCH_MODE:
                setQueryTextToFragment("");
                updateFragmentsVisibility();
                invalidateOptionsMenu();
                break;
            case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                final String queryString = mActionBarAdapter.getQueryString();
                setQueryTextToFragment(queryString);
                updateDebugOptionsVisibility(
                        ENABLE_DEBUG_OPTIONS_HIDDEN_CODE.equals(queryString));
                break;
            default:
                throw new IllegalStateException("Unkonwn ActionBarAdapter action: " + action);
        }
    }

    @Override
    public void onSelectedTabChanged() {
        updateFragmentsVisibility();
    }

    private void updateDebugOptionsVisibility(boolean visible) {
        if (mEnableDebugMenuOptions != visible) {
            mEnableDebugMenuOptions = visible;
            invalidateOptionsMenu();
        }
    }

    /**
     * Updates the fragment/view visibility according to the current mode, such as
     * {@link ActionBarAdapter#isSearchMode()} and {@link ActionBarAdapter#getCurrentTab()}.
     */
    private void updateFragmentsVisibility() {
        int tab = mActionBarAdapter.getCurrentTab();

        // We use ViewPager on 1-pane.
        if (!PhoneCapabilityTester.isUsingTwoPanes(this)) {
            if (mActionBarAdapter.isSearchMode()) {
                mTabPagerAdapter.setSearchMode(true);
            } else {
                // No smooth scrolling if quitting from the search mode.
                final boolean wasSearchMode = mTabPagerAdapter.isSearchMode();
                mTabPagerAdapter.setSearchMode(false);
                if (mTabPager.getCurrentItem() != tab) {
                    mTabPager.setCurrentItem(tab, !wasSearchMode);
                }
            }
            invalidateOptionsMenu();
            showEmptyStateForTab(tab);
            if (tab == TabState.GROUPS) {
                mGroupsFragment.setAddAccountsVisibility(!areGroupWritableAccountsAvailable());
            }
            return;
        }

        // for the tablet...

        // If in search mode, we use the all list + contact details to show the result.
        if (mActionBarAdapter.isSearchMode()) {
            tab = TabState.ALL;
        }

        switch (tab) {
            case TabState.FAVORITES:
                mFavoritesView.setVisibility(View.VISIBLE);
                mBrowserView.setVisibility(View.GONE);
                mGroupDetailsView.setVisibility(View.GONE);
                mContactDetailsView.setVisibility(View.GONE);
                break;
            case TabState.GROUPS:
                mFavoritesView.setVisibility(View.GONE);
                mBrowserView.setVisibility(View.VISIBLE);
                mGroupDetailsView.setVisibility(View.VISIBLE);
                mContactDetailsView.setVisibility(View.GONE);
                mGroupsFragment.setAddAccountsVisibility(!areGroupWritableAccountsAvailable());
                break;
            case TabState.ALL:
                mFavoritesView.setVisibility(View.GONE);
                mBrowserView.setVisibility(View.VISIBLE);
                mContactDetailsView.setVisibility(View.VISIBLE);
                mGroupDetailsView.setVisibility(View.GONE);
                break;
        }
        mPeopleActivityView.startMaskTransition(false, TAB_FADE_IN_DURATION);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();

        // Note mContactDetailLoaderFragment is an invisible fragment, but we still have to show/
        // hide it so its options menu will be shown/hidden.
        switch (tab) {
            case TabState.FAVORITES:
                showFragment(ft, mFavoritesFragment);
                showFragment(ft, mFrequentFragment);
                hideFragment(ft, mAllFragment);
                hideFragment(ft, mContactDetailLoaderFragment);
                hideFragment(ft, mContactDetailFragment);
                hideFragment(ft, mGroupsFragment);
                hideFragment(ft, mGroupDetailFragment);
                break;
            case TabState.ALL:
                hideFragment(ft, mFavoritesFragment);
                hideFragment(ft, mFrequentFragment);
                showFragment(ft, mAllFragment);
                showFragment(ft, mContactDetailLoaderFragment);
                showFragment(ft, mContactDetailFragment);
                hideFragment(ft, mGroupsFragment);
                hideFragment(ft, mGroupDetailFragment);
                break;
            case TabState.GROUPS:
                hideFragment(ft, mFavoritesFragment);
                hideFragment(ft, mFrequentFragment);
                hideFragment(ft, mAllFragment);
                hideFragment(ft, mContactDetailLoaderFragment);
                hideFragment(ft, mContactDetailFragment);
                showFragment(ft, mGroupsFragment);
                showFragment(ft, mGroupDetailFragment);
                break;
        }
        if (!ft.isEmpty()) {
            ft.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
            // When switching tabs, we need to invalidate options menu, but executing a
            // fragment transaction does it implicitly.  We don't have to call invalidateOptionsMenu
            // manually.
        }
        showEmptyStateForTab(tab);
    }

    private void showEmptyStateForTab(int tab) {
        if (mContactsUnavailableFragment != null) {
            switch (tab) {
                case TabState.FAVORITES:
                    mContactsUnavailableFragment.setMessageText(
                            R.string.listTotalAllContactsZeroStarred, -1);
                    break;
                case TabState.GROUPS:
                    mContactsUnavailableFragment.setMessageText(R.string.noGroups,
                            areGroupWritableAccountsAvailable() ? -1 : R.string.noAccounts);
                    break;
                case TabState.ALL:
                    mContactsUnavailableFragment.setMessageText(R.string.noContacts, -1);
                    break;
            }
        }
    }

    private class TabPagerListener implements ViewPager.OnPageChangeListener {

        // This package-protected constructor is here because of a possible compiler bug.
        // PeopleActivity$1.class should be generated due to the private outer/inner class access
        // needed here.  But for some reason, PeopleActivity$1.class is missing.
        // Since $1 class is needed as a jvm work around to get access to the inner class,
        // changing the constructor to package-protected or public will solve the problem.
        // To verify whether $1 class is needed, javap PeopleActivity$TabPagerListener and look for
        // references to PeopleActivity$1.
        //
        // When the constructor is private and PeopleActivity$1.class is missing, proguard will
        // correctly catch this and throw warnings and error out the build on user/userdebug builds.
        //
        // All private inner classes below also need this fix.
        TabPagerListener() {}

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            // Make sure not in the search mode, in which case position != TabState.ordinal().
            if (!mTabPagerAdapter.isSearchMode()) {
                mActionBarAdapter.setCurrentTab(position, false);
                showEmptyStateForTab(position);
                if (position == TabState.GROUPS) {
                    mGroupsFragment.setAddAccountsVisibility(!areGroupWritableAccountsAvailable());
                }
                invalidateOptionsMenu();
            }
        }
    }

    /**
     * Adapter for the {@link ViewPager}.  Unlike {@link FragmentPagerAdapter},
     * {@link #instantiateItem} returns existing fragments, and {@link #instantiateItem}/
     * {@link #destroyItem} show/hide fragments instead of attaching/detaching.
     *
     * In search mode, we always show the "all" fragment, and disable the swipe.  We change the
     * number of items to 1 to disable the swipe.
     *
     * TODO figure out a more straight way to disable swipe.
     */
    private class TabPagerAdapter extends PagerAdapter {
        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;

        private boolean mTabPagerAdapterSearchMode;

        private Fragment mCurrentPrimaryItem;

        public TabPagerAdapter() {
            mFragmentManager = getFragmentManager();
        }

        public boolean isSearchMode() {
            return mTabPagerAdapterSearchMode;
        }

        public void setSearchMode(boolean searchMode) {
            if (searchMode == mTabPagerAdapterSearchMode) {
                return;
            }
            mTabPagerAdapterSearchMode = searchMode;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabPagerAdapterSearchMode ? 1 : TabState.COUNT;
        }

        /** Gets called when the number of items changes. */
        @Override
        public int getItemPosition(Object object) {
            if (mTabPagerAdapterSearchMode) {
                if (object == mAllFragment) {
                    return 0; // Only 1 page in search mode
                }
            } else {
                if (object == mFavoritesFragment) {
                    return TabState.FAVORITES;
                }
                if (object == mAllFragment) {
                    return TabState.ALL;
                }
                if (object == mGroupsFragment) {
                    return TabState.GROUPS;
                }
            }
            return POSITION_NONE;
        }

        @Override
        public void startUpdate(ViewGroup container) {
        }

        private Fragment getFragment(int position) {
            if (mTabPagerAdapterSearchMode) {
                if (position != 0) {
                    // This has only been observed in monkey tests.
                    // Let's log this issue, but not crash
                    Log.w(TAG, "Request fragment at position=" + position + ", eventhough we " +
                            "are in search mode");
                }
                return mAllFragment;
            } else {
                if (position == TabState.FAVORITES) {
                    return mFavoritesFragment;
                } else if (position == TabState.ALL) {
                    return mAllFragment;
                } else if (position == TabState.GROUPS) {
                    return mGroupsFragment;
                }
            }
            throw new IllegalArgumentException("position: " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            Fragment f = getFragment(position);
            mCurTransaction.show(f);

            // Non primary pages are not visible.
            f.setUserVisibleHint(f == mCurrentPrimaryItem);
            return f;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.hide((Fragment) object);
        }

        @Override
        public void finishUpdate(ViewGroup container) {
            if (mCurTransaction != null) {
                mCurTransaction.commitAllowingStateLoss();
                mCurTransaction = null;
                mFragmentManager.executePendingTransactions();
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment) object).getView() == view;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            Fragment fragment = (Fragment) object;
            if (mCurrentPrimaryItem != fragment) {
                if (mCurrentPrimaryItem != null) {
                    mCurrentPrimaryItem.setUserVisibleHint(false);
                }
                if (fragment != null) {
                    fragment.setUserVisibleHint(true);
                }
                mCurrentPrimaryItem = fragment;
            }
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }
    }

    private void setQueryTextToFragment(String query) {
        mAllFragment.setQueryString(query, true);
        mAllFragment.setVisibleScrollbarEnabled(!mAllFragment.isSearchMode());
    }

    private void configureContactListFragmentForRequest() {
        Uri contactUri = mRequest.getContactUri();
        if (contactUri != null) {
            // For an incoming request, explicitly require a selection if we are on 2-pane UI,
            // (i.e. even if we view the same selected contact, the contact may no longer be
            // in the list, so we must refresh the list).
            if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
                mAllFragment.setSelectionRequired(true);
            }
            mAllFragment.setSelectedContactUri(contactUri);
        }

        mAllFragment.setFilter(mContactListFilterController.getFilter());
        setQueryTextToFragment(mActionBarAdapter.getQueryString());

        if (mRequest.isDirectorySearchEnabled()) {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            mAllFragment.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    private void configureContactListFragment() {
        // Filter may be changed when this Activity is in background.
        mAllFragment.setFilter(mContactListFilterController.getFilter());

        final boolean useTwoPane = PhoneCapabilityTester.isUsingTwoPanes(this);

        mAllFragment.setVerticalScrollbarPosition(getScrollBarPosition(useTwoPane));
        mAllFragment.setSelectionVisible(useTwoPane);
        mAllFragment.setQuickContactEnabled(!useTwoPane);
    }

    private int getScrollBarPosition(boolean useTwoPane) {
        final boolean isLayoutRtl = isRTL();
        final int position;
        if (useTwoPane) {
            position = isLayoutRtl ? View.SCROLLBAR_POSITION_RIGHT : View.SCROLLBAR_POSITION_LEFT;
        } else {
            position = isLayoutRtl ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT;
        }
        return position;
    }

    private boolean isRTL() {
        final Locale locale = Locale.getDefault();
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
    }

    private void configureGroupListFragment() {
        final boolean useTwoPane = PhoneCapabilityTester.isUsingTwoPanes(this);
        mGroupsFragment.setVerticalScrollbarPosition(getScrollBarPosition(useTwoPane));
        mGroupsFragment.setSelectionVisible(useTwoPane);
    }

    @Override
    public void onProviderStatusChange() {
        updateViewConfiguration(false);
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        ProviderStatusWatcher.Status providerStatus = mProviderStatusWatcher.getProviderStatus();
        if (!forceUpdate && (mProviderStatus != null)
                && (providerStatus.status == mProviderStatus.status)) return;
        mProviderStatus = providerStatus;

        View contactsUnavailableView = findViewById(R.id.contacts_unavailable_view);
        View mainView = findViewById(R.id.main_view);

        if (mProviderStatus.status == ProviderStatus.STATUS_NORMAL) {
            // Ensure that the mTabPager is visible; we may have made it invisible below.
            contactsUnavailableView.setVisibility(View.GONE);
            // Invalidate menu item when ProviderWatcher notify status is normal.
            invalidateOptionsMenu();
            if (mTabPager != null) {
                mTabPager.setVisibility(View.VISIBLE);
            }

            if (mainView != null) {
                mainView.setVisibility(View.VISIBLE);
            }
            if (mAllFragment != null) {
                mAllFragment.setEnabled(true);
            }
        } else {
            // If there are no accounts on the device and we should show the "no account" prompt
            // (based on {@link SharedPreferences}), then launch the account setup activity so the
            // user can sign-in or create an account.
            //
            // Also check for ability to modify accounts.  In limited user mode, you can't modify
            // accounts so there is no point sending users to account setup activity.
            final UserManager userManager = UserManager.get(this);
            final boolean disallowModifyAccounts = userManager.getUserRestrictions().getBoolean(
                    UserManager.DISALLOW_MODIFY_ACCOUNTS);
            if (!disallowModifyAccounts && !areContactWritableAccountsAvailable() &&
                    AccountPromptUtils.shouldShowAccountPrompt(this)) {
                AccountPromptUtils.launchAccountPrompt(this);
                return;
            }

            // Otherwise, continue setting up the page so that the user can still use the app
            // without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
            }
            if (mContactsUnavailableFragment == null) {
                mContactsUnavailableFragment = new ContactsUnavailableFragment();
                mContactsUnavailableFragment.setOnContactsUnavailableActionListener(
                        new ContactsUnavailableFragmentListener());
                getFragmentManager().beginTransaction()
                        .replace(R.id.contacts_unavailable_container, mContactsUnavailableFragment)
                        .commitAllowingStateLoss();
            }
            mContactsUnavailableFragment.updateStatus(mProviderStatus);

            // Show the contactsUnavailableView, and hide the mTabPager so that we don't
            // see it sliding in underneath the contactsUnavailableView at the edges.
            contactsUnavailableView.setVisibility(View.VISIBLE);
            if (mTabPager != null) {
                mTabPager.setVisibility(View.GONE);
            }

            if (mainView != null) {
                mainView.setVisibility(View.INVISIBLE);
            }

            showEmptyStateForTab(mActionBarAdapter.getCurrentTab());
        }

        invalidateOptionsMenuIfNeeded();
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        ContactBrowserActionListener() {}

        @Override
        public void onSelectionChange() {
            if (PhoneCapabilityTester.isUsingTwoPanes(PeopleActivity.this)) {
                setupContactDetailFragment(mAllFragment.getSelectedContactUri());
            }
        }

        @Override
        public void onViewContactAction(Uri contactLookupUri) {
            if (PhoneCapabilityTester.isUsingTwoPanes(PeopleActivity.this)) {
                setupContactDetailFragment(contactLookupUri);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, contactLookupUri);
                startActivity(intent);
            }
        }

        @Override
        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        @Override
        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.putExtra(
                    ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            startActivityForResult(intent, SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onAddToFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 1);
            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onRemoveFromFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            getContentResolver().update(contactUri, values, null, null);
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter;
            ContactListFilter currentFilter = mAllFragment.getFilter();
            if (currentFilter != null
                    && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
                mAllFragment.setFilter(filter);
            } else {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                mAllFragment.setFilter(filter, false);
            }
            mContactListFilterController.setContactListFilter(filter, true);
        }
    }

    private class ContactDetailLoaderFragmentListener implements ContactLoaderFragmentListener {
        ContactDetailLoaderFragmentListener() {}

        @Override
        public void onContactNotFound() {
            // Nothing needs to be done here
        }

        @Override
        public void onDetailsLoaded(final Contact result) {
            if (result == null) {
                // Nothing is loaded. Show empty state.
                mContactDetailLayoutController.showEmptyState();
                return;
            }
            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // If the activity is destroyed (or will be destroyed soon), don't update the UI
                    if (isFinishing()) {
                        return;
                    }
                    mContactDetailLayoutController.setContactData(result);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(
                    ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            startActivityForResult(intent, SUBACTIVITY_EDIT_CONTACT);
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(PeopleActivity.this, contactUri, false);
        }
    }

    public class ContactDetailFragmentListener implements ContactDetailFragment.Listener {
        @Override
        public void onItemClicked(Intent intent) {
            if (intent == null) {
                return;
            }
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onCreateRawContactRequested(ArrayList<ContentValues> values,
                AccountWithDataSet account) {
            Toast.makeText(PeopleActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    PeopleActivity.this, values, account,
                    PeopleActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);
        }
    }

    private class ContactsUnavailableFragmentListener
            implements OnContactsUnavailableActionListener {
        ContactsUnavailableFragmentListener() {}

        @Override
        public void onCreateNewContactAction() {
            startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
        }

        @Override
        public void onAddAccountAction() {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.putExtra(Settings.EXTRA_AUTHORITIES,
                    new String[] { ContactsContract.AUTHORITY });
            startActivity(intent);
        }

        @Override
        public void onImportContactsFromFileAction() {
            ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                    PeopleActivity.class);
        }

        @Override
        public void onFreeInternalStorageAction() {
            startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        }
    }

    private final class StrequentContactListFragmentListener
            implements ContactTileListFragment.Listener {
        StrequentContactListFragmentListener() {}

        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (PhoneCapabilityTester.isUsingTwoPanes(PeopleActivity.this)) {
                QuickContact.showQuickContact(PeopleActivity.this, targetRect, contactUri, 0, null);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            // No need to call phone number directly from People app.
            Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
        }
    }

    private final class GroupBrowserActionListener implements OnGroupBrowserActionListener {

        GroupBrowserActionListener() {}

        @Override
        public void onViewGroupAction(Uri groupUri) {
            if (PhoneCapabilityTester.isUsingTwoPanes(PeopleActivity.this)) {
                setupGroupDetailFragment(groupUri);
            } else {
                Intent intent = new Intent(PeopleActivity.this, GroupDetailActivity.class);
                intent.setData(groupUri);
                startActivity(intent);
            }
        }
    }

    private class GroupDetailFragmentListener implements GroupDetailFragment.Listener {

        GroupDetailFragmentListener() {}

        @Override
        public void onGroupSizeUpdated(String size) {
            // Nothing needs to be done here because the size will be displayed in the detail
            // fragment
        }

        @Override
        public void onGroupTitleUpdated(String title) {
            // Nothing needs to be done here because the title will be displayed in the detail
            // fragment
        }

        @Override
        public void onAccountTypeUpdated(String accountTypeString, String dataSet) {
            // Nothing needs to be done here because the group source will be displayed in the
            // detail fragment
        }

        @Override
        public void onEditRequested(Uri groupUri) {
            final Intent intent = new Intent(PeopleActivity.this, GroupEditorActivity.class);
            intent.setData(groupUri);
            intent.setAction(Intent.ACTION_EDIT);
            startActivityForResult(intent, SUBACTIVITY_EDIT_GROUP);
        }

        @Override
        public void onContactSelected(Uri contactUri) {
            // Nothing needs to be done here because either quickcontact will be displayed
            // or activity will take care of selection
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!areContactsAvailable()) {
            // If contacts aren't available, hide all menu items.
            return false;
        }
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.people_options, menu);

        return true;
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (isOptionsMenuChanged()) {
            invalidateOptionsMenu();
        }
    }

    public boolean isOptionsMenuChanged() {
        if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
            return true;
        }

        if (mAllFragment != null && mAllFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mContactDetailLoaderFragment != null &&
                mContactDetailLoaderFragment.isOptionsMenuChanged()) {
            return true;
        }

        if (mGroupDetailFragment != null && mGroupDetailFragment.isOptionsMenuChanged()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuContactsAvailable = areContactsAvailable();
        if (!mOptionsMenuContactsAvailable) {
            return false;
        }
        if (getResources().getBoolean(R.bool.baidu_xcloud_enable)) {
            XCloudManager.getInstance().updateMenuState(menu, this);
        } else {
            final MenuItem autoSyncToXCloudSwitcher = menu.findItem(
                R.id.menu_auto_sync_to_baidu_cloud);
            final MenuItem syncToXCloud = menu.findItem(R.id.menu_sync_to_baidu_cloud);
            if (autoSyncToXCloudSwitcher != null) {
                autoSyncToXCloudSwitcher.setVisible(false);
            }
            if (syncToXCloud != null) {
                syncToXCloud.setVisible(false);
            }
        }
        // Get references to individual menu items in the menu
        final MenuItem addContactMenu = menu.findItem(R.id.menu_add_contact);
        final MenuItem contactsFilterMenu = menu.findItem(R.id.menu_contacts_filter);
        switchGroupsMenu = menu.findItem(R.id.menu_switch_group);

        addGroupMenu = menu.findItem(R.id.menu_add_group);

        final MenuItem clearFrequentsMenu = menu.findItem(R.id.menu_clear_frequents);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);

        final boolean isSearchMode = mActionBarAdapter.isSearchMode();
        if (isSearchMode) {
            addContactMenu.setVisible(false);
            addGroupMenu.setVisible(false);
            contactsFilterMenu.setVisible(false);
            switchGroupsMenu.setVisible(false);
            clearFrequentsMenu.setVisible(false);
            helpMenu.setVisible(false);
            makeMenuItemVisible(menu, R.id.menu_delete, false);
        } else {
            switch (mActionBarAdapter.getCurrentTab()) {
                case TabState.FAVORITES:
                    addContactMenu.setVisible(true);
                    addGroupMenu.setVisible(false);
                    contactsFilterMenu.setVisible(false);
                    switchGroupsMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(hasFrequents());
                    break;
                case TabState.ALL:
                    addContactMenu.setVisible(true);
                    addGroupMenu.setVisible(false);
                    contactsFilterMenu.setVisible(true);
                    switchGroupsMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(false);
                    break;
                case TabState.GROUPS:
                    addContactMenu.setVisible(false);
                    contactsFilterMenu.setVisible(false);
                    clearFrequentsMenu.setVisible(false);
                    switchGroupsMenu.setVisible(true);

                    // Do not display the "new group" button if no accounts are available
                    updateGroupsMenu();
                    break;
            }
            HelpUtils.prepareHelpMenuItem(this, helpMenu, R.string.help_url_people_main);
        }
        final boolean showMiscOptions = !isSearchMode;
        makeMenuItemVisible(menu, R.id.menu_search, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_import_export, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_accounts, showMiscOptions);
        makeMenuItemVisible(menu, R.id.menu_settings,
                showMiscOptions && !ContactsPreferenceActivity.isEmpty(this));

        // Debug options need to be visible even in search mode.
        makeMenuItemVisible(menu, R.id.export_database, mEnableDebugMenuOptions);

        return true;
    }

    /**
     * Returns whether there are any frequently contacted people being displayed
     * @return
     */
    private boolean hasFrequents() {
        if (PhoneCapabilityTester.isUsingTwoPanesInFavorites(this)) {
            return mFrequentFragment.hasFrequents();
        } else {
            return mFavoritesFragment.hasFrequents();
        }
    }

    private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item =menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDisableOptionItemSelected) {
            return false;
        }

        if (getResources().getBoolean(R.bool.baidu_xcloud_enable)) {
            if(XCloudManager.getInstance().handleXCouldRelatedMenuItem(item, this))
                return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home: {
                // The home icon on the action bar is pressed
                if (mActionBarAdapter.isUpShowing()) {
                    // "UP" icon press -- should be treated as "back".
                    onBackPressed();
                }
                return true;
            }
            case R.id.menu_settings: {
                final Intent intent = new Intent(this, ContactsPreferenceActivity.class);
                // as there is only one section right now, make sure it is selected
                // on small screens, this also hides the section selector
                // Due to b/5045558, this code unfortunately only works properly on phones
                boolean settingsAreMultiPane = getResources().getBoolean(
                        com.android.internal.R.bool.preferences_prefer_dual_pane);
                if (!settingsAreMultiPane) {
                    intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                            DisplayOptionsPreferenceFragment.class.getName());
                    intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE,
                            R.string.activity_title_settings);
                }
                startActivity(intent);
                return true;
            }
            case R.id.menu_contacts_filter: {
                AccountFilterUtil.startAccountFilterActivityForResult(
                        this, SUBACTIVITY_ACCOUNT_FILTER,
                        mContactListFilterController.getFilter());
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add_contact: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                // On 2-pane UI, we can let the editor activity finish itself and return
                // to this activity to display the new contact.
                if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    intent.putExtra(
                            ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED,
                            true);
                    startActivityForResult(intent, SUBACTIVITY_NEW_CONTACT);
                } else {
                    // Otherwise, on 1-pane UI, we need the editor to launch the view contact
                    // intent itself.
                    startActivity(intent);
                }
                return true;
            }
            case R.id.menu_add_group: {
                if (isLocalGroupsShown) {
                    showAddLocalGroupDialog();
                } else {
                    createNewGroupWithAccountDisambiguation();
                }
                return true;
            }
            // QRD enhancement: multi contact delete
            case R.id.menu_delete: {
                final Intent intent = new Intent(Intent.ACTION_DELETE, Contacts.CONTENT_URI);
                intent.putExtra(EDITABLE_KEY, mActionBarAdapter.getQueryString());

                ContactListFilter filter = ContactListFilter.restoreDefaultPreferences(
                    PreferenceManager.getDefaultSharedPreferences(this));
                intent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER, filter);

                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                if (!ImportExportDialogFragment.isExportingToSIM()) {
                    ImportExportDialogFragment.show(getFragmentManager(), areContactsAvailable(),
                            PeopleActivity.class);
                } else {
                    new ImportExportDialogFragment().showExportToSIMProgressDialog(
                        PeopleActivity.this);
                }
                return true;
            }
            case R.id.menu_clear_frequents: {
                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                return true;
            }
            case R.id.export_database: {
                final Intent intent = new Intent("com.android.providers.contacts.DUMP_DATABASE");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                return true;
            }

            case R.id.menu_memory_status: {
                final Intent intent = new Intent(this, MemoryStatusActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.menu_switch_group: {
                isLocalGroupsShown = !isLocalGroupsShown;
                updateGroupsMenu();
                mGroupsFragment.updateGroupData();
                return true;
            }
        }
        return false;
    }

    private void createNewGroup() {
        final Intent intent = new Intent(this, GroupEditorActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
    }

    private void updateGroupsMenu() {
        if (areGroupWritableAccountsAvailable() || isLocalGroupsShown) {
            addGroupMenu.setVisible(true);
        } else {
            addGroupMenu.setVisible(false);
        }
        switchGroupsMenu
            .setTitle(isLocalGroupsShown ? R.string.title_switch_group_remote
                : R.string.title_switch_group_local);
        switchGroupsMenu
            .setIcon(isLocalGroupsShown ? R.drawable.ic_remote_group_holo_light
                : R.drawable.ic_location_group_holo_light);
    }


    private void createNewGroupWithAccountDisambiguation() {
        final List<AccountWithDataSet> accounts =
            AccountTypeManager.getInstance(this).getAccounts(true,
                AccountTypeManager.FLAG_ALL_ACCOUNTS_WITHOUT_LOCAL);
        if (accounts.size() <= 1 || mAddGroupImageView == null) {

            // No account to choose or no control to anchor the popup-menu to
            // ==> just go straight to the editor which will disambig if necessary
            final Intent intent = new Intent(this, GroupEditorActivity.class);
            intent.setAction(Intent.ACTION_INSERT);
            startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
            return;
        }

        final ListPopupWindow popup = new ListPopupWindow(this, null);
        popup.setWidth(getResources().getDimensionPixelSize(R.dimen.account_selector_popup_width));
        popup.setAnchorView(mAddGroupImageView);

        // Create a list adapter with all writeable accounts (assume that the writeable accounts all
        // allow group creation).
        final AccountsListAdapter adapter = new AccountsListAdapter(this,
            AccountListFilter.ACCOUNTS_GROUP_WRITABLE);
        popup.setAdapter(adapter);
        popup.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                popup.dismiss();
                AccountWithDataSet account = adapter.getItem(position);
                final Intent intent = new Intent(PeopleActivity.this, GroupEditorActivity.class);
                intent.setAction(Intent.ACTION_INSERT);
                intent.putExtra(Intents.Insert.ACCOUNT, account);
                intent.putExtra(Intents.Insert.DATA_SET, account.dataSet);
                startActivityForResult(intent, SUBACTIVITY_NEW_GROUP);
            }
        });
        popup.setModal(true);
        popup.show();
    }

    @Override
    public boolean onSearchRequested() { // Search key pressed.
        mActionBarAdapter.setSearchMode(true);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_ACCOUNT_FILTER: {
                AccountFilterUtil.handleAccountFilterResult(
                        mContactListFilterController, resultCode, data);
                break;
            }

            case SUBACTIVITY_NEW_CONTACT:
            case SUBACTIVITY_EDIT_CONTACT: {
                if (resultCode == RESULT_OK && PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    mRequest.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                    mAllFragment.setSelectionRequired(true);
                    mAllFragment.setSelectedContactUri(data.getData());
                    // Suppress IME if in search mode
                    if (mActionBarAdapter != null) {
                        mActionBarAdapter.clearFocusOnSearchView();
                    }
                    // No need to change the contact filter
                    mCurrentFilterIsValid = true;
                }
                break;
            }

            case SUBACTIVITY_NEW_GROUP:
            case SUBACTIVITY_EDIT_GROUP: {
                if (resultCode == RESULT_OK && PhoneCapabilityTester.isUsingTwoPanes(this)) {
                    mRequest.setActionCode(ContactsRequest.ACTION_GROUP);
                    mGroupsFragment.setSelectedUri(data.getData());
                }
                break;
            }

            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == RESULT_OK) {
                    mAllFragment.onPickerResult(data);
                }
                break;

            case ImportExportDialogFragment.SUBACTIVITY_MULTI_PICK_CONTACT:
                if (resultCode == RESULT_OK) {
                    mContactList = new ArrayList<String[]>();
                    Bundle b = data.getExtras();
                    Bundle choiceSet = b.getBundle(RESULT_KEY);
                    Set<String> set = choiceSet.keySet();
                    Iterator<String> i = set.iterator();
                    while (i.hasNext()) {
                        String contactInfo[] = choiceSet.getStringArray(i.next());
                        mContactList.add(contactInfo);
                    }
                    Log.d(TAG, "return " + mContactList.size() + " contacts");
                    if (!mContactList.isEmpty()) {
                        if (!ImportExportDialogFragment.isExportingToSIM()) {
                            ImportExportDialogFragment.destroyExportToSimThread();
                            mExportThread =
                                new ImportExportDialogFragment().createExportToSimThread(
                                ImportExportDialogFragment.ExportToSimThread.TYPE_SELECT,
                                ImportExportDialogFragment.mExportSub,mContactList,
                                PeopleActivity.this);
                            mExportThread.start();
                        }
                    }
                }
                break;
            case ImportExportDialogFragment.SUBACTIVITY_EXPORT_CONTACTS:
                if (resultCode == RESULT_OK) {
                    Bundle result = data.getExtras().getBundle(RESULT_KEY);
                    Set<String> keySet = result.keySet();
                    Iterator<String> it = keySet.iterator();
                    StringBuilder selExportBuilder = new StringBuilder();
                    while (it.hasNext()) {
                        String id = it.next();
                        if (0 != selExportBuilder.length()) {
                            selExportBuilder.append(",");
                        }
                        selExportBuilder.append(id);
                    }
                    selExportBuilder.insert(0, "_id IN (");
                    selExportBuilder.append(")");
                    Intent exportIntent = new Intent(this, ExportVCardActivity.class);
                    exportIntent.putExtra("SelExport", selExportBuilder.toString());
                    exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
                            PeopleActivity.class.getName());
                    this.startActivity(exportIntent);
                }
                break;
            case ImportExportDialogFragment.SUBACTIVITY_SHARE_VISIBLE_CONTACTS:
                if (resultCode == RESULT_OK) {
                    Bundle result = data.getExtras().getBundle(RESULT_KEY);
                    StringBuilder uriListBuilder = new StringBuilder();
                    int index = 0;
                    int size =result.keySet().size();
                    // The premise of allowing to share contacts is that the
                    // amount of those contacts which have been selected to
                    // append and will be put into intent as extra data to
                    // deliver is not more that 2000, because too long arguments
                    // will cause TransactionTooLargeException in binder.
                    if (size > ImportExportDialogFragment.MAX_COUNT_ALLOW_SHARE_CONTACT) {
                        String text = getString(R.string.contact_share_failed_toast,
                                ImportExportDialogFragment.MAX_COUNT_ALLOW_SHARE_CONTACT);
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Iterator<String> it = result.keySet().iterator();
                    String[] values = null;
                    while (it.hasNext()) {
                        if (index != 0) {
                            uriListBuilder.append(':');
                        }
                        values = result.getStringArray(it.next());
                        uriListBuilder.append(values[0]);
                        index++;
                    }
                    Uri uri = Uri.withAppendedPath(
                            Contacts.CONTENT_MULTI_VCARD_URI,
                            Uri.encode(uriListBuilder.toString()));
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(Contacts.CONTENT_VCARD_TYPE);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    startActivity(intent);
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment
        switch (keyCode) {
//            case KeyEvent.KEYCODE_CALL: {
//                if (callSelection()) {
//                    return true;
//                }
//                break;
//            }

            case KeyEvent.KEYCODE_DEL: {
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
            default: {
                // Bring up the search UI if the user starts typing
                final int unicodeChar = event.getUnicodeChar();
                if ((unicodeChar != 0)
                        // If COMBINING_ACCENT is set, it's not a unicode character.
                        && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                        && !Character.isWhitespace(unicodeChar)) {
                    String query = new String(new int[]{ unicodeChar }, 0, 1);
                    if (!mActionBarAdapter.isSearchMode()) {
                        mActionBarAdapter.setQueryString(query);
                        mActionBarAdapter.setSearchMode(true);
                        return true;
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setSearchMode(false);
        } else {
            super.onBackPressed();
        }
    }

    private boolean deleteSelection() {
        // TODO move to the fragment
//        if (mActionCode == ContactsRequest.ACTION_DEFAULT) {
//            final int position = mListView.getSelectedItemPosition();
//            if (position != ListView.INVALID_POSITION) {
//                Uri contactUri = getContactUri(position);
//                if (contactUri != null) {
//                    doContactDelete(contactUri);
//                    return true;
//                }
//            }
//        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mActionBarAdapter.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }

        // Clear the listener to make sure we don't get callbacks after onSaveInstanceState,
        // in order to avoid doing fragment transactions after it.
        // TODO Figure out a better way to deal with the issue.
        mDisableOptionItemSelected = true;
        mActionBarAdapter.setListener(null);
        if (mTabPager != null) {
            mTabPager.setOnPageChangeListener(null);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // In our own lifecycle, the focus is saved and restore but later taken away by the
        // ViewPager. As a hack, we force focus on the SearchView if we know that we are searching.
        // This fixes the keyboard going away on screen rotation
        if (mActionBarAdapter.isSearchMode()) {
            mActionBarAdapter.setFocusOnSearchView();
        }
    }

    @Override
    public DialogManager getDialogManager() {
        return mDialogManager;
    }

    // Visible for testing
    public ContactBrowseListFragment getListFragment() {
        return mAllFragment;
    }

    // Visible for testing
    public ContactDetailFragment getDetailFragment() {
        return mContactDetailFragment;
    }
}
