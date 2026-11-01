/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.accounts.Account
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.icu.text.MessageFormat
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.SearchSnippets
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.android.contacts.ContactSaveService
import com.android.contacts.R
import com.android.contacts.compat.ContactsCompat
import com.android.contacts.compat.DirectoryCompat
import com.android.contacts.compat.ProviderStatusCompat
import com.android.contacts.list.AddressBookIndex
import com.android.contacts.list.ContactListFilter
import com.android.contacts.list.ContactListFilterController
import com.android.contacts.list.ContactSnippets
import com.android.contacts.list.ProviderStatusWatcher
import com.android.contacts.logging.ListEvent
import com.android.contacts.logging.Logger
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.ContactListEntry
import com.android.contacts.model.ContactsDeletion
import com.android.contacts.model.ContactsListEmptyState
import com.android.contacts.model.ContactsListHeader
import com.android.contacts.model.ContactsListState
import com.android.contacts.model.ContactsListStatus
import com.android.contacts.model.ContactsSearchState
import com.android.contacts.model.ContactsSyncState
import com.android.contacts.model.ContactsUnavailable
import com.android.contacts.model.DirectorySearchResults
import com.android.contacts.model.SearchDirectory
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.model.account.GoogleAccountType
import com.android.contacts.preference.ContactsPreferences
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.ContactDisplayUtils
import com.android.contacts.util.SharedPreferenceUtil
import com.android.contacts.util.SyncUtil
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * View model of the main contact list, showing the contacts of the current
 * [ContactListFilter], or the contacts matching a search query in every directory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsListViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver

    /**
     * Contacts preferences, for the sort and display orders.
     */
    private val preferences = ContactsPreferences(application)

    /**
     * Holds the current filter, shared with the navigation drawer and the filter screens.
     */
    private val filterController = ContactListFilterController.getInstance(application)

    /**
     * The current filter.
     */
    private val filter = callbackFlow {
        val listener = ContactListFilterController.ContactListFilterListener {
            trySend(filterController.filter)
        }
        filterController.addListener(listener)
        trySend(filterController.filter)
        awaitClose { filterController.removeListener(listener) }
    }.conflate()

    /**
     * Emits when the contacts may have changed.
     */
    private val contactsChanges = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    /**
     * Emits when the sort or the display order may have changed.
     */
    private val preferencesChanges = callbackFlow {
        preferences.registerChangeListener { trySend(Unit) }
        trySend(Unit)
        awaitClose { preferences.unregisterChangeListener() }
    }.conflate()

    /**
     * Account type manager, used to get the accounts to sync.
     */
    private val accountTypeManager = AccountTypeManager.getInstance(application)

    /**
     * Emits when the sync status of the accounts changes.
     */
    private val syncStatusChanges = callbackFlow {
        val handle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                    or ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                    or ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS
        ) { trySend(Unit) }
        trySend(Unit)
        awaitClose { ContentResolver.removeStatusChangeListener(handle) }
    }.conflate()

    /**
     * Incremented when the sync off alert is dismissed, to update it.
     */
    private val syncAlertChanges = MutableStateFlow(0)

    /**
     * Whether to ask to confirm turning on the global auto-sync.
     */
    private val _enableAutoSyncConfirmation = MutableStateFlow(false)
    val enableAutoSyncConfirmation = _enableAutoSyncConfirmation.asStateFlow()

    /**
     * Why the contacts can't be shown, null if they can.
     */
    val unavailable: StateFlow<ContactsUnavailable?> = callbackFlow {
        val watcher = ProviderStatusWatcher.getInstance(getApplication<Application>())
        val listener = ProviderStatusWatcher.ProviderStatusListener {
            trySend(watcher.providerStatus)
        }
        watcher.addListener(listener)
        watcher.start()
        trySend(withContext(Dispatchers.IO) { watcher.providerStatus })
        awaitClose {
            watcher.removeListener(listener)
            watcher.stop()
        }
    }
        // The watcher works on the main thread.
        .flowOn(Dispatchers.Main)
        .map { providerStatus ->
            when (providerStatus) {
                ProviderStatusCompat.STATUS_NORMAL -> null
                // The provider may still be empty while there is a non local account, see
                // b/30566908.
                ProviderStatusCompat.STATUS_EMPTY -> ContactsUnavailable.NO_ACCOUNT.takeUnless {
                    accountTypeManager.hasNonLocalAccount()
                }

                else -> ContactsUnavailable.BUSY
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * The search query, null outside of search mode.
     */
    val searchQuery = savedStateHandle.getStateFlow<String?>(KEY_SEARCH_QUERY, null)

    /**
     * The selected contacts, the list is in selection mode if it isn't empty.
     */
    val selectedContactIds: StateFlow<Set<Long>> =
        savedStateHandle.getStateFlow(KEY_SELECTED, LongArray(0))
            .map { it.toSet() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet(),
            )

    /**
     * The deletion of the selected contacts to confirm, if any.
     */
    private val _deletion = MutableStateFlow<ContactsDeletion?>(null)
    val deletion = _deletion.asStateFlow()

    /**
     * What the activity has to do once, see [onStatusHandled].
     */
    private val _status = MutableStateFlow<ContactsListStatus>(ContactsListStatus.Idle)
    val status = _status.asStateFlow()

    /**
     * The contacts of the current filter. Null while loading.
     */
    val state: StateFlow<ContactsListState?> = combine(
        filter, contactsChanges, preferencesChanges
    ) { filter, _, _ ->
        load(filter)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * The sync of the contacts of the current filter.
     */
    val syncState: StateFlow<ContactsSyncState?> = combine(
        filter, syncStatusChanges, syncAlertChanges
    ) { filter, _, _ ->
        loadSyncState(filter)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * The contacts matching [searchQuery], published as each directory returns them. Null
     * outside of search mode.
     */
    val searchState: StateFlow<ContactsSearchState?> = combine(
        searchQuery, contactsChanges, preferencesChanges
    ) { query, _, _ ->
        query
    }
        .transformLatest { query ->
            when {
                query == null -> emit(null)
                query.isBlank() -> emit(ContactsSearchState(query, emptyList()))
                else -> emitAll(search(query))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Enters search mode, or updates the search query.
     */
    fun setSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    /**
     * Leaves search mode.
     */
    fun stopSearch() {
        savedStateHandle[KEY_SEARCH_QUERY] = null
    }

    fun toggleSelected(contact: ContactListEntry) {
        val selected = selectedContactIds.value.toMutableSet()
        if (!selected.remove(contact.contactId)) {
            selected.add(contact.contactId)
        }
        savedStateHandle[KEY_SELECTED] = selected.toLongArray()
    }

    fun clearSelection() {
        savedStateHandle[KEY_SELECTED] = LongArray(0)
    }

    /**
     * Shares the selected contacts as a single vCard.
     */
    fun shareSelected() {
        val contactIds = selectedContactIds.value
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) { createShareIntent(contactIds) }
                ?: return@launch
            logSelectionEvent(ListEvent.ActionType.SHARE)
            _status.value = ContactsListStatus.Share(intent)
        }
    }

    /**
     * Links the selected contacts into a single one.
     */
    fun joinSelected() {
        logSelectionEvent(ListEvent.ActionType.LINK)
        val application = getApplication<Application>()
        application.startService(
            ContactSaveService.createJoinSeveralContactsIntent(
                application, selectedContactIds.value.toLongArray()
            )
        )
        clearSelection()
    }

    /**
     * Asks to confirm the deletion of the selected contacts, see [deletion].
     */
    fun requestDeleteSelected() {
        val contactIds = selectedContactIds.value
        viewModelScope.launch {
            _deletion.value = withContext(Dispatchers.IO) { createDeletion(contactIds) }
        }
    }

    /**
     * Deletes the contacts of [deletion] once confirmed.
     */
    fun confirmDeletion() {
        val deletion = _deletion.value ?: return
        _deletion.value = null
        logSelectionEvent(ListEvent.ActionType.DELETE)
        val application = getApplication<Application>()
        application.startService(
            ContactSaveService.createDeleteMultipleContactsIntent(
                application, deletion.contactIds.toLongArray(), deletion.names.toTypedArray()
            )
        )
        clearSelection()
    }

    fun cancelDeletion() {
        _deletion.value = null
    }

    /**
     * Requests a sync of the accounts of the current filter.
     */
    fun refresh() {
        val filter = state.value?.filter ?: return
        val application = getApplication<Application>()
        if (!SyncUtil.isNetworkConnected(application)) {
            _status.value = ContactsListStatus.ConnectionError
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            }
            syncableAccounts(filter).forEach { account ->
                // Prioritize the sync of the contacts if it isn't initialized yet.
                if (!SyncUtil.isSyncStatusPendingOrActive(account)
                    || SyncUtil.isUnsyncableGoogleAccount(account)
                ) {
                    ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras)
                }
            }
        }
    }

    /**
     * Turns on the sync of the account of the current filter, or asks to confirm turning on
     * the global auto-sync, see [enableAutoSyncConfirmation].
     */
    fun turnSyncOn() {
        val filter = state.value?.filter ?: return
        if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
            && syncState.value?.syncOffReason == SyncUtil.SYNC_SETTING_ACCOUNT_SYNC_OFF
        ) {
            ContentResolver.setSyncAutomatically(
                Account(filter.accountName, filter.accountType), ContactsContract.AUTHORITY, true
            )
        } else {
            _enableAutoSyncConfirmation.value = true
        }
    }

    /**
     * Turns on the global auto-sync, and the sync of the accounts of the current filter.
     */
    fun confirmEnableAutoSync() {
        _enableAutoSyncConfirmation.value = false
        val filter = state.value?.filter ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ContentResolver.setMasterSyncAutomatically(true)
            syncableAccounts(filter).forEach { account ->
                ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
            }
        }
    }

    fun cancelEnableAutoSync() {
        _enableAutoSyncConfirmation.value = false
    }

    /**
     * Hides the sync off alert, which is shown again a limited number of times.
     */
    fun dismissSyncOffAlert() {
        val application = getApplication<Application>()
        when (syncState.value?.syncOffReason) {
            SyncUtil.SYNC_SETTING_GLOBAL_SYNC_OFF ->
                SharedPreferenceUtil.incNumOfDismissesForAutoSyncOff(application)

            SyncUtil.SYNC_SETTING_ACCOUNT_SYNC_OFF ->
                SharedPreferenceUtil.incNumOfDismissesForAccountSyncOff(
                    application, state.value?.filter?.accountName
                )
        }
        syncAlertChanges.value++
    }

    /**
     * Called once the activity handled [status].
     */
    fun onStatusHandled() {
        _status.value = ContactsListStatus.Idle
    }

    private fun load(filter: ContactListFilter): ContactsListState {
        val favorites = if (filter.isContactsFilterType) loadFavorites(filter) else emptyList()
        val contacts = loadContacts(filter)

        return ContactsListState(
            filter = filter,
            title = AccountFilterUtil.getActionBarTitleForFilter(
                getApplication<Application>(), filter
            ),
            contacts = favorites + contacts,
            header = when (filter.filterType) {
                ContactListFilter.FILTER_TYPE_DEFAULT,
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS -> ContactsListHeader.None

                ContactListFilter.FILTER_TYPE_CUSTOM -> ContactsListHeader.CustomFilter

                else -> ContactsListHeader.Count(
                    count = contacts.size,
                    accountName = filter.accountName.takeIf {
                        filter.accountType == GoogleAccountType.ACCOUNT_TYPE
                    },
                )
            },
            emptyState = when {
                contacts.isNotEmpty() || !shouldShowEmptyView(filter) -> null
                filter.isContactsFilterType -> ContactsListEmptyState.NO_CONTACTS
                else -> ContactsListEmptyState.EMPTY_ACCOUNT
            },
        )
    }

    /**
     * Loads the starred contacts, shown above the others.
     */
    private fun loadFavorites(filter: ContactListFilter): List<ContactListEntry> {
        val selection = buildString {
            append("${Contacts.STARRED}=1")
            if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                append(" AND ${Contacts.IN_VISIBLE_GROUP}=1")
            }
        }
        return contentResolver.query(
            Contacts.CONTENT_URI,
            projection(Contacts._ID),
            selection,
            null,
            sortOrder(),
        )?.use { cursor -> readContacts(cursor, favorite = true) }.orEmpty()
    }

    /**
     * Loads the contacts of [filter], with the address book index of their sections.
     */
    private fun loadContacts(filter: ContactListFilter): List<ContactListEntry> {
        // The contacts of the device and of the SIM card are found from their raw contacts.
        val fromData = filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS
                || filter.filterType == ContactListFilter.FILTER_TYPE_SIM_CONTACTS

        val uri = (if (fromData) Data.CONTENT_URI else Contacts.CONTENT_URI).buildUpon()
            .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
            .apply {
                if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                    filter.addAccountQueryParameterToUrl(this)
                }
            }
            .build()

        val selectionArgs = mutableListOf<String>()
        val selection = when (filter.filterType) {
            ContactListFilter.FILTER_TYPE_STARRED -> "${Contacts.STARRED}!=0"

            ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY ->
                "${Contacts.HAS_PHONE_NUMBER}=1"

            ContactListFilter.FILTER_TYPE_CUSTOM -> buildString {
                append("${Contacts.IN_VISIBLE_GROUP}=1")
                if (isCustomFilterForPhoneNumbersOnly()) {
                    append(" AND ${Contacts.HAS_PHONE_NUMBER}=1")
                }
            }

            ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
            ContactListFilter.FILTER_TYPE_SIM_CONTACTS -> filter.accountType?.let { accountType ->
                buildString {
                    append("${RawContacts.ACCOUNT_TYPE}=?")
                    selectionArgs.add(accountType)
                    filter.accountName?.let {
                        append(" AND ${RawContacts.ACCOUNT_NAME}=?")
                        selectionArgs.add(it)
                    }
                }
            } ?: AccountWithDataSet.LOCAL_ACCOUNT_SELECTION

            // All accounts, or an account given as query parameters.
            else -> null
        }

        return contentResolver.query(
            uri,
            projection(if (fromData) Data.CONTACT_ID else Contacts._ID),
            selection,
            selectionArgs.toTypedArray(),
            sortOrder(),
        )?.use { cursor -> readContacts(cursor, favorite = false) }.orEmpty()
    }

    /**
     * Reads the contacts of [cursor], once each: a contact has a row for each of its data rows
     * when queried from [Data].
     */
    private fun readContacts(cursor: Cursor, favorite: Boolean): List<ContactListEntry> {
        val index = AddressBookIndex(cursor)
        val contactIds = mutableSetOf<Long>()
        return buildList {
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(COLUMN_CONTACT_ID)
                if (!contactIds.add(contactId)) {
                    continue
                }
                add(
                    ContactListEntry(
                        contactId = contactId,
                        lookupUri = Contacts.getLookupUri(
                            contactId, cursor.getString(COLUMN_LOOKUP_KEY)
                        ),
                        displayName = cursor.getString(COLUMN_DISPLAY_NAME),
                        photoThumbnailUri = cursor.getString(COLUMN_PHOTO_THUMBNAIL_URI)
                            ?.let(Uri::parse),
                        section = index.sectionAt(cursor.position),
                        favorite = favorite,
                    )
                )
            }
        }
    }

    private fun loadSyncState(filter: ContactListFilter): ContactsSyncState {
        val application = getApplication<Application>()

        // Only Google accounts are synced with the list.
        val accounts = when {
            filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                    && filter.isGoogleAccountType -> listOf(
                AccountWithDataSet(filter.accountName, filter.accountType, null)
            )

            filter.shouldShowSyncState() ->
                AccountInfo.extractAccounts(accountTypeManager.writableGoogleAccounts)

            else -> emptyList()
        }

        val alertAccount = Account(filter.accountName, filter.accountType).takeIf {
            filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                    && filter.isGoogleAccountType
        }
        val syncOffReason = if (alertAccount != null || filter.isContactsFilterType) {
            SyncUtil.calculateReasonSyncOff(application, alertAccount).takeIf {
                SyncUtil.isAlertVisible(application, alertAccount, it)
            }
        } else {
            null
        }

        return ContactsSyncState(
            canRefresh = filter.isSyncable
                    || (filter.shouldShowSyncState()
                    && SyncUtil.hasSyncableAccount(accountTypeManager)),
            refreshing = SyncUtil.isAnySyncing(accounts),
            syncOffReason = syncOffReason,
        )
    }

    /**
     * Returns the writable accounts of [filter] which can be synced.
     */
    private fun syncableAccounts(filter: ContactListFilter): List<Account> =
        filter.getSyncableAccounts(
            AccountInfo.extractAccounts(
                accountTypeManager.filterAccountsAsync(AccountTypeManager.writableFilter()).get()
            )
        ).orEmpty()

    /**
     * Whether the empty view can be shown for [filter]: while the Google accounts are being
     * synced, the list is shown empty with the sync status instead.
     */
    private fun shouldShowEmptyView(filter: ContactListFilter): Boolean = when (filter.filterType) {
        ContactListFilter.FILTER_TYPE_DEFAULT,
        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS -> accountTypeManager.writableGoogleAccounts
            .mapNotNull { it.account.accountOrNull }
            .none {
                SyncUtil.isSyncStatusPendingOrActive(it) || SyncUtil.isUnsyncableGoogleAccount(it)
            }

        ContactListFilter.FILTER_TYPE_ACCOUNT -> Account(filter.accountName, filter.accountType)
            .let {
                !SyncUtil.isSyncStatusPendingOrActive(it) && !SyncUtil.isUnsyncableGoogleAccount(it)
            }

        else -> true
    }

    private fun logSelectionEvent(actionType: Int) {
        val search = searchState.value
        Logger.logListEvent(
            actionType,
            if (search != null) {
                ListEvent.ListType.SEARCH_RESULT
            } else {
                state.value?.filter?.toListType() ?: ListEvent.ListType.UNKNOWN_LIST
            },
            search?.results?.sumOf { it.contacts?.size ?: 0 } ?: state.value?.contacts?.size ?: 0,
            /* clickedIndex */ -1,
            selectedContactIds.value.size,
        )
    }

    /**
     * Returns a chooser intent sharing [contactIds] as a single vCard, or null if none of them
     * still exists.
     */
    private fun createShareIntent(contactIds: Set<Long>): Intent? {
        val lookupKeys = contactIds.mapNotNull { contactId ->
            val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
            Contacts.getLookupUri(contentResolver, contactUri)
                ?.pathSegments
                ?.takeIf { it.size >= 2 }
                ?.let { it[it.size - 2] }
        }
        if (lookupKeys.isEmpty()) {
            return null
        }
        val uri = Uri.withAppendedPath(
            Contacts.CONTENT_MULTI_VCARD_URI,
            Uri.encode(lookupKeys.joinToString(":") { Uri.encode(it) }),
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType(Contacts.CONTENT_VCARD_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
        val title = MessageFormat(
            getApplication<Application>().getString(R.string.title_share_via),
            Locale.getDefault(),
        ).format(mapOf("count" to contactIds.size))
        return Intent.createChooser(intent, title)
    }

    /**
     * Loads the raw contacts of [contactIds], to ask to confirm their deletion depending on
     * whether their accounts are writable.
     */
    private fun createDeletion(contactIds: Set<Long>): ContactsDeletion? {
        if (contactIds.isEmpty()) {
            return null
        }
        val accountTypeManager = AccountTypeManager.getInstance(getApplication<Application>())
        val readOnlyRawContacts = mutableSetOf<Long>()
        val writableRawContacts = mutableSetOf<Long>()
        val existingContactIds = mutableSetOf<Long>()
        val names = mutableSetOf<String>()

        contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(
                RawContacts._ID,
                RawContacts.ACCOUNT_TYPE,
                RawContacts.DATA_SET,
                RawContacts.CONTACT_ID,
                RawContacts.DISPLAY_NAME_PRIMARY,
                RawContacts.DISPLAY_NAME_ALTERNATIVE,
            ),
            "${RawContacts.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
            contactIds.map { it.toString() }.toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                ContactDisplayUtils.getPreferredDisplayName(
                    cursor.getString(4), cursor.getString(5), preferences
                )?.takeIf { it.isNotEmpty() }?.let(names::add)
                existingContactIds.add(cursor.getLong(3))

                val accountType = accountTypeManager.getAccountType(
                    cursor.getString(1), cursor.getString(2)
                )
                if (accountType == null || accountType.areContactsWritable()) {
                    writableRawContacts.add(cursor.getLong(0))
                } else {
                    readOnlyRawContacts.add(cursor.getLong(0))
                }
            }
        } ?: return null

        val readOnlyCount = readOnlyRawContacts.size
        val writableCount = writableRawContacts.size
        val (message, confirmButton) = when {
            readOnlyCount > 0 && writableCount > 0 ->
                R.string.batch_delete_multiple_accounts_confirmation to android.R.string.ok

            readOnlyCount > 0 ->
                R.string.batch_delete_read_only_contact_confirmation to
                        R.string.readOnlyContactWarning_positive_button

            writableCount == 1 ->
                R.string.single_delete_confirmation to
                        R.string.deleteConfirmation_positive_button

            else ->
                R.string.batch_delete_confirmation to R.string.deleteConfirmation_positive_button
        }
        return ContactsDeletion(message, confirmButton, existingContactIds.toList(), names.toList())
    }

    /**
     * Searches [query] in every directory at once, emitting the results as they come.
     */
    private fun search(query: String) = channelFlow {
        val directories = loadDirectories()
        val results = directories.associateWith<SearchDirectory, List<ContactListEntry>?> {
            null
        }.toMutableMap()
        send(ContactsSearchState(query, results.toResults()))

        // The directories are searched in parallel, their results are published in order.
        val mutex = Mutex()
        directories.forEach { directory ->
            launch {
                val contacts = searchDirectory(directory.id, query.trim())
                mutex.withLock {
                    results[directory] = contacts
                    send(ContactsSearchState(query, results.toResults()))
                }
            }
        }
    }

    private fun Map<SearchDirectory, List<ContactListEntry>?>.toResults() =
        map { (directory, contacts) -> DirectorySearchResults(directory, contacts) }

    /**
     * Loads the directories to search, the contacts of the device first.
     */
    private fun loadDirectories(): List<SearchDirectory> {
        val packageManager = getApplication<Application>().packageManager
        return try {
            contentResolver.query(
                DirectoryCompat.getContentUri(),
                arrayOf(
                    Directory._ID,
                    Directory.PACKAGE_NAME,
                    Directory.TYPE_RESOURCE_ID,
                    Directory.DISPLAY_NAME,
                ),
                null,
                null,
                Directory._ID,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        if (DirectoryCompat.isInvisibleDirectory(id)) {
                            continue
                        }
                        val type = cursor.getString(1)?.takeIf { cursor.getInt(2) != 0 }
                            ?.let { packageName ->
                                try {
                                    packageManager.getResourcesForApplication(packageName)
                                        .getString(cursor.getInt(2))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Cannot get the directory type of $packageName")
                                    null
                                }
                            }
                        add(createSearchDirectory(id, cursor.getString(3), type))
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to load the directories", e)
            null
        } ?: listOf(createSearchDirectory(Directory.DEFAULT, null, null))
    }

    private fun createSearchDirectory(id: Long, name: String?, type: String?): SearchDirectory {
        val application = getApplication<Application>()
        val remote = DirectoryCompat.isRemoteDirectoryId(id)
        val work = DirectoryCompat.isEnterpriseDirectoryId(id)
        return SearchDirectory(
            id = id,
            label = when {
                remote && work -> application.getString(R.string.directory_search_label_work)
                remote -> application.getString(R.string.directory_search_label)
                work -> application.getString(R.string.list_filter_phones_work)
                else -> null
            },
            name = if (remote) name?.takeIf { it.isNotEmpty() } ?: type else null,
        )
    }

    /**
     * Searches [query] in the directory [directoryId], limiting the results of remote ones.
     */
    private fun searchDirectory(directoryId: Long, query: String): List<ContactListEntry> {
        val uri = ContactsCompat.getContentUri().buildUpon()
            .appendPath(query)
            .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, directoryId.toString())
            .apply {
                if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                    appendQueryParameter(
                        ContactsContract.LIMIT_PARAM_KEY, DIRECTORY_RESULT_LIMIT.toString()
                    )
                }
            }
            .appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY, "1")
            .build()
        val snippetMaxLength = getApplication<Application>().resources
            .getInteger(R.integer.snippet_length_before_tokenize)

        return try {
            contentResolver.query(
                uri,
                projection(Contacts._ID) + SearchSnippets.SNIPPET,
                null,
                null,
                "${Contacts.SORT_KEY_PRIMARY}, ${sortOrder()}",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val contactId = cursor.getLong(COLUMN_CONTACT_ID)
                        val lookupUri = Contacts.getLookupUri(
                            contactId, cursor.getString(COLUMN_LOOKUP_KEY)
                        )
                        add(
                            ContactListEntry(
                                contactId = contactId,
                                lookupUri = if (directoryId == Directory.DEFAULT) {
                                    lookupUri
                                } else {
                                    lookupUri.buildUpon().appendQueryParameter(
                                        ContactsContract.DIRECTORY_PARAM_KEY,
                                        directoryId.toString(),
                                    ).build()
                                },
                                displayName = cursor.getString(COLUMN_DISPLAY_NAME),
                                photoThumbnailUri = cursor.getString(COLUMN_PHOTO_THUMBNAIL_URI)
                                    ?.let(Uri::parse),
                                section = "",
                                snippet = ContactSnippets.fromCursor(
                                    cursor, COLUMN_SNIPPET, snippetMaxLength
                                ),
                                workProfile = ContactsCompat.isEnterpriseContactId(contactId),
                            )
                        )
                    }
                }
            }.orEmpty()
        } catch (e: RuntimeException) {
            // Directories are provided by other apps, which may fail.
            Log.w(TAG, "Failed to search the directory $directoryId", e)
            emptyList()
        }
    }

    private fun projection(contactIdColumn: String) = arrayOf(
        contactIdColumn,
        Contacts.LOOKUP_KEY,
        Contacts.PHOTO_THUMBNAIL_URI,
        if (preferences.displayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            Contacts.DISPLAY_NAME_PRIMARY
        } else {
            Contacts.DISPLAY_NAME_ALTERNATIVE
        },
    )

    private fun sortOrder() =
        if (preferences.sortOrder == ContactsPreferences.SORT_ORDER_PRIMARY) {
            Contacts.SORT_KEY_PRIMARY
        } else {
            Contacts.SORT_KEY_ALTERNATIVE
        }

    // TODO: this flag should not be stored in shared prefs. It needs to be in the db.
    private fun isCustomFilterForPhoneNumbersOnly() =
        PreferenceManager.getDefaultSharedPreferences(getApplication<Application>()).getBoolean(
            ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
            ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT,
        )

    companion object {
        private const val TAG = "ContactsListViewModel"

        private const val KEY_SEARCH_QUERY = "searchQuery"
        private const val KEY_SELECTED = "selectedContactIds"

        /**
         * The maximum number of results of a remote directory.
         */
        private const val DIRECTORY_RESULT_LIMIT = 20

        private const val COLUMN_CONTACT_ID = 0
        private const val COLUMN_LOOKUP_KEY = 1
        private const val COLUMN_PHOTO_THUMBNAIL_URI = 2
        private const val COLUMN_DISPLAY_NAME = 3
        private const val COLUMN_SNIPPET = 4
    }
}
