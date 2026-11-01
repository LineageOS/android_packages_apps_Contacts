/*
 * SPDX-FileCopyrightText: 2010 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.activities

import android.Manifest
import android.accounts.Account
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Groups
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.contacts.ContactSaveService
import com.android.contacts.R
import com.android.contacts.compat.ContactsCompat
import com.android.contacts.editor.ContactEditorFragment
import com.android.contacts.editor.SelectAccountDialogFragment
import com.android.contacts.group.GroupNameEditDialogFragment
import com.android.contacts.group.GroupUtil
import com.android.contacts.interactions.ImportDialogFragment
import com.android.contacts.list.AccountFilterActivity
import com.android.contacts.list.ContactListFilter
import com.android.contacts.list.ContactListFilterController
import com.android.contacts.list.ContactsIntentResolver
import com.android.contacts.list.ContactsRequest
import com.android.contacts.logging.ListEvent
import com.android.contacts.logging.Logger
import com.android.contacts.logging.ScreenEvent.ScreenType
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.ContactListEntry
import com.android.contacts.model.ContactsListStatus
import com.android.contacts.model.DrawerProfile
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.preference.ContactsPreferenceActivity
import com.android.contacts.quickcontact.QuickContactActivity
import com.android.contacts.ui.composables.ContactsDrawer
import com.android.contacts.ui.screens.ContactsListScreen
import com.android.contacts.ui.theme.ContactsTheme
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.AccountSelectionUtil
import com.android.contacts.util.ImplicitIntentsUtil
import com.android.contacts.viewmodels.ContactsListViewModel
import com.android.contacts.viewmodels.DrawerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the contacts of the current filter, the main screen of the app.
 */
class PeopleActivity : ComponentActivity(), ContactSaveService.Listener,
    SelectAccountDialogFragment.Listener {
    // View models
    private val contactsListViewModel: ContactsListViewModel by viewModels()
    private val drawerViewModel: DrawerViewModel by viewModels()

    private val snackbarHostState = SnackbarHostState()

    private val filterController by lazy { ContactListFilterController.getInstance(this) }

    private val groupDeletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onGroupDeleted(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Replace the launch screen theme, make sure this is before super.onCreate().
        setTheme(R.style.ContactsComposeTheme)
        // Groups created from here are saved by ContactSaveService, which then calls us back.
        ContactSaveService.registerListener(this)
        super.onCreate(savedInstanceState)

        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return
        }

        if (!processIntent(intent, recreated = savedInstanceState != null)) {
            finish()
            return
        }
        filterController.checkFilterValidity(false)

        // Registered while created, as groups are deleted from GroupActivity, above this one.
        LocalBroadcastManager.getInstance(this).registerReceiver(
            groupDeletedReceiver, IntentFilter(ContactSaveService.BROADCAST_GROUP_DELETED)
        )

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        enableEdgeToEdge()
        setContent {
            val state by contactsListViewModel.state.collectAsStateWithLifecycle()
            val searchQuery by contactsListViewModel.searchQuery.collectAsStateWithLifecycle()
            val searchState by contactsListViewModel.searchState.collectAsStateWithLifecycle()
            val selectedContactIds by
                contactsListViewModel.selectedContactIds.collectAsStateWithLifecycle()
            val syncState by contactsListViewModel.syncState.collectAsStateWithLifecycle()
            val unavailable by contactsListViewModel.unavailable.collectAsStateWithLifecycle()
            val deletion by contactsListViewModel.deletion.collectAsStateWithLifecycle()
            val enableAutoSyncConfirmation by
                contactsListViewModel.enableAutoSyncConfirmation.collectAsStateWithLifecycle()
            val status by contactsListViewModel.status.collectAsStateWithLifecycle()
            val drawerState by drawerViewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(status) {
                when (val current = status) {
                    ContactsListStatus.Idle -> return@LaunchedEffect

                    is ContactsListStatus.Share -> try {
                        startActivity(current.intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            this@PeopleActivity, R.string.share_error, Toast.LENGTH_SHORT
                        ).show()
                    }

                    ContactsListStatus.ConnectionError -> launch {
                        snackbarHostState.showSnackbar(
                            getString(R.string.connection_error_message),
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
                contactsListViewModel.onStatusHandled()
            }

            ContactsTheme {
                ContactsListScreen(
                    state = state,
                    searchQuery = searchQuery,
                    searchState = searchState,
                    selectedContactIds = selectedContactIds,
                    syncState = syncState,
                    unavailable = unavailable,
                    deletion = deletion,
                    enableAutoSyncConfirmation = enableAutoSyncConfirmation,
                    snackbarHostState = snackbarHostState,
                    drawerContent = { closeDrawer ->
                        ContactsDrawer(
                            state = drawerState,
                            currentFilter = state?.filter,
                            onProfileClick = {
                                closeDrawer()
                                drawerState?.profile?.let(::openProfile)
                            },
                            onAllContactsClick = {
                                closeDrawer()
                                setFilter(AccountFilterUtil.createContactsFilter(this))
                            },
                            onGroupClick = {
                                closeDrawer()
                                startGroupActivity(
                                    ContentUris.withAppendedId(Groups.CONTENT_URI, it.groupId)
                                )
                            },
                            onCreateGroupClick = {
                                closeDrawer()
                                createGroup(null)
                            },
                            onAccountClick = {
                                closeDrawer()
                                setFilter(it.filter)
                            },
                            onEmergencyInfoClick = {
                                closeDrawer()
                                ImplicitIntentsUtil.startActivityOutsideApp(
                                    this, ImplicitIntentsUtil.getIntentForEmergencyInfo(this)
                                )
                            },
                            onSettingsClick = {
                                closeDrawer()
                                startActivity(
                                    Intent(this, ContactsPreferenceActivity::class.java)
                                        .putExtra(
                                            ContactsPreferenceActivity.EXTRA_NEW_LOCAL_PROFILE,
                                            ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE,
                                        )
                                )
                            },
                        )
                    },
                    onContactClick = { contact, position ->
                        openContact(contact, position, searchQuery != null)
                    },
                    onToggleSelected = contactsListViewModel::toggleSelected,
                    onClearSelection = contactsListViewModel::clearSelection,
                    onSearchQueryChange = contactsListViewModel::setSearchQuery,
                    onStopSearch = contactsListViewModel::stopSearch,
                    onShare = contactsListViewModel::shareSelected,
                    onDelete = contactsListViewModel::requestDeleteSelected,
                    onJoin = contactsListViewModel::joinSelected,
                    onConfirmDeletion = contactsListViewModel::confirmDeletion,
                    onCancelDeletion = contactsListViewModel::cancelDeletion,
                    onAddContact = {
                        state?.filter?.let {
                            AccountFilterUtil.startEditorIntent(this, intent, it)
                        }
                    },
                    onRefresh = contactsListViewModel::refresh,
                    onTurnSyncOn = contactsListViewModel::turnSyncOn,
                    onDismissSyncOff = contactsListViewModel::dismissSyncOffAlert,
                    onConfirmEnableAutoSync = contactsListViewModel::confirmEnableAutoSync,
                    onCancelEnableAutoSync = contactsListViewModel::cancelEnableAutoSync,
                    onAddAccount = {
                        ImplicitIntentsUtil.startActivityOutsideApp(
                            this, ImplicitIntentsUtil.getIntentForAddingGoogleAccount()
                        )
                    },
                    onImportContacts = { ImportDialogFragment.show(fragmentManager) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (GroupUtil.ACTION_CREATE_GROUP == intent.action) {
            val groupUri = intent.data
            if (groupUri == null) {
                Toast.makeText(this, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show()
                return
            }
            Toast.makeText(this, R.string.groupCreatedToast, Toast.LENGTH_SHORT).show()
            startGroupActivity(groupUri)
            return
        }

        setIntent(intent)
        if (!processIntent(intent, recreated = false)) {
            finish()
            return
        }
        filterController.checkFilterValidity(false)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(groupDeletedReceiver)
        ContactSaveService.unregisterListener(this)

        super.onDestroy()
    }

    override fun onServiceCompleted(callbackIntent: Intent) {
        onNewIntent(callbackIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Bring up the search when the user starts typing.
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0
            && (unicodeChar and KeyCharacterMap.COMBINING_ACCENT) == 0
            && !Character.isWhitespace(unicodeChar)
            && contactsListViewModel.searchQuery.value == null
            && contactsListViewModel.selectedContactIds.value.isEmpty()
        ) {
            contactsListViewModel.setSearchQuery(String(Character.toChars(unicodeChar)))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // SelectAccountDialogFragment.Listener
    override fun onAccountChosen(account: AccountWithDataSet, extraArgs: Bundle?) {
        if (extraArgs?.containsKey(ImportDialogFragment.KEY_RES_ID) == true) {
            AccountSelectionUtil.doImport(
                this,
                extraArgs.getInt(ImportDialogFragment.KEY_RES_ID),
                account,
                extraArgs.getInt(ImportDialogFragment.KEY_SUBSCRIPTION_ID),
            )
        } else {
            GroupNameEditDialogFragment.newInstanceForCreation(
                account, GroupUtil.ACTION_CREATE_GROUP
            ).show(fragmentManager, TAG_GROUP_NAME_EDIT_DIALOG)
        }
    }

    override fun onAccountSelectorCancelled() {}

    /**
     * Resolves [intent], and starts another activity if needed.
     *
     * @return whether this activity should keep running.
     */
    private fun processIntent(intent: Intent, recreated: Boolean): Boolean {
        val request = ContactsIntentResolver(this).resolveIntent(intent)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "processIntent: intent=$intent request=$request")
        }
        if (!request.isValid) {
            setResult(RESULT_CANCELED)
            return false
        }

        when (request.actionCode) {
            ContactsRequest.ACTION_VIEW_CONTACT -> {
                ImplicitIntentsUtil.startQuickContact(this, request.contactUri, ScreenType.UNKNOWN)
                return false
            }

            ContactsRequest.ACTION_INSERT_GROUP -> createGroup(intent.extras)

            ContactsRequest.ACTION_VIEW_GROUP, ContactsRequest.ACTION_EDIT_GROUP -> {
                if (!recreated) {
                    startGroupActivity(request.contactUri)
                }
            }

            ContactsRequest.ACTION_ALL_CONTACTS -> if (!recreated) {
                setFilter(AccountFilterUtil.createContactsFilter(this))
            }

            ContactsRequest.ACTION_CONTACTS_WITH_PHONES -> if (!recreated) {
                setFilter(
                    ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY
                    )
                )
            }

            else -> if (request.isSearchMode && request.contactUri == null && !recreated) {
                contactsListViewModel.setSearchQuery(request.queryString.orEmpty())
            }
        }
        return true
    }

    /**
     * Shows the contacts of [filter], persisted if it shows all the contacts.
     */
    private fun setFilter(filter: ContactListFilter) {
        contactsListViewModel.stopSearch()
        contactsListViewModel.clearSelection()
        AccountFilterUtil.handleAccountFilterResult(
            filterController,
            RESULT_OK,
            Intent().putExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER, filter),
        )
    }

    private fun openContact(contact: ContactListEntry, position: Int, searchMode: Boolean) {
        if (ContactsCompat.isEnterpriseContactId(contact.contactId)) {
            // No implicit intent as user may have a different contacts app in work profile.
            ContactsContract.QuickContact.showQuickContact(
                this, Rect(), contact.lookupUri, QuickContactActivity.MODE_FULLY_EXPANDED, null
            )
            return
        }

        val filter = contactsListViewModel.state.value?.filter
        val previousScreen = when {
            searchMode -> ScreenType.SEARCH
            filter?.isContactsFilterType != true -> ScreenType.LIST_ACCOUNT
            contact.favorite -> ScreenType.FAVORITES
            else -> ScreenType.ALL_CONTACTS
        }
        Logger.logListEvent(
            ListEvent.ActionType.CLICK,
            if (searchMode) {
                ListEvent.ListType.SEARCH_RESULT
            } else {
                filter?.toListType() ?: ListEvent.ListType.UNKNOWN_LIST
            },
            contactsListViewModel.state.value?.contacts?.size ?: 0,
            position,
            /* numSelected */ 0,
        )
        ImplicitIntentsUtil.startQuickContact(this, contact.lookupUri, previousScreen)
    }

    private fun openProfile(profile: DrawerProfile) {
        val contactId = profile.contactId
        if (contactId != null) {
            ImplicitIntentsUtil.startQuickContact(
                this,
                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                ScreenType.ME_CONTACT,
            )
        } else {
            ImplicitIntentsUtil.startActivityInApp(
                this,
                Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI)
                    .putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true),
            )
        }
    }

    private fun startGroupActivity(groupUri: Uri?) {
        if (groupUri == null || !GroupUtil.isGroupUri(groupUri)) {
            Toast.makeText(this, R.string.groupLoadErrorToast, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(GroupActivity.createIntent(this, groupUri))
    }

    /**
     * Creates a group in the account of [extras], or in one picked by the user.
     */
    private fun createGroup(extras: Bundle?) {
        val account = extras?.getParcelable(
            ContactsContract.Intents.Insert.EXTRA_ACCOUNT, Account::class.java
        )
        if (account != null) {
            onAccountChosen(
                AccountWithDataSet(
                    account.name,
                    account.type,
                    extras?.getString(ContactsContract.Intents.Insert.EXTRA_DATA_SET),
                ),
                null,
            )
            return
        }

        lifecycleScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                AccountTypeManager.getInstance(this@PeopleActivity)
                    .filterAccountsAsync(AccountTypeManager.AccountFilter.GROUPS_WRITABLE)
                    .get()
            }
            when (accounts.size) {
                // The drawer doesn't offer to create a group without such an account, but an
                // intent may still ask for it.
                0 -> Toast.makeText(
                    this@PeopleActivity, R.string.groupCreateFailedToast, Toast.LENGTH_SHORT
                ).show()

                1 -> onAccountChosen(accounts[0].account, null)

                else -> SelectAccountDialogFragment.show(
                    fragmentManager,
                    R.string.dialog_new_group_account,
                    AccountTypeManager.AccountFilter.GROUPS_WRITABLE,
                    /* extraArgs */ null,
                    TAG_SELECT_ACCOUNT_DIALOG,
                )
            }
        }
    }

    /**
     * Offers to undo the deletion of a group.
     */
    private fun onGroupDeleted(intent: Intent) {
        if (!ContactSaveService.canUndo(intent)) {
            return
        }
        lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(R.string.groupDeletedToast),
                actionLabel = getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                ContactSaveService.startService(
                    this@PeopleActivity,
                    ContactSaveService.createUndoIntent(this@PeopleActivity, intent),
                )
            }
        }
    }

    companion object {
        private const val TAG = "PeopleActivity"

        private const val TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog"
        private const val TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog"
    }
}
