/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.android.contacts.list.AddressBookIndex
import com.android.contacts.list.ContactListFilter
import com.android.contacts.list.ContactListFilterController
import com.android.contacts.model.ContactListEntry
import com.android.contacts.model.ContactsListEmptyState
import com.android.contacts.model.ContactsListHeader
import com.android.contacts.model.ContactsListState
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.model.account.GoogleAccountType
import com.android.contacts.preference.ContactsPreferences
import com.android.contacts.util.AccountFilterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * View model of the main contact list, showing the contacts of the current
 * [ContactListFilter].
 */
class ContactsListViewModel(application: Application) : AndroidViewModel(application) {
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
                contacts.isNotEmpty() -> null
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
        private const val COLUMN_CONTACT_ID = 0
        private const val COLUMN_LOOKUP_KEY = 1
        private const val COLUMN_PHOTO_THUMBNAIL_URI = 2
        private const val COLUMN_DISPLAY_NAME = 3
    }
}
