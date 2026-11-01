/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.content.Intent
import androidx.annotation.StringRes
import com.android.contacts.list.ContactListFilter

/**
 * The header of the main contact list.
 */
sealed interface ContactsListHeader {
    /**
     * No header, e.g. for all the contacts.
     */
    data object None : ContactsListHeader

    /**
     * The contacts of the custom filter are shown.
     */
    data object CustomFilter : ContactsListHeader

    /**
     * The number of contacts of an account, along with its name if not null.
     */
    data class Count(val count: Int, val accountName: String?) : ContactsListHeader
}

/**
 * Why the main contact list is empty.
 */
enum class ContactsListEmptyState {
    /**
     * There are no contacts at all.
     */
    NO_CONTACTS,

    /**
     * The account of the filter has no contacts.
     */
    EMPTY_ACCOUNT,
}

/**
 * The main contact list.
 */
data class ContactsListState(
    val filter: ContactListFilter,
    /**
     * The title of the list, depending on the filter.
     */
    val title: String,
    /**
     * The contacts, starting with the favorites when the filter isn't an account.
     */
    val contacts: List<ContactListEntry>,
    val header: ContactsListHeader,
    /**
     * Why the list is empty, null if it isn't.
     */
    val emptyState: ContactsListEmptyState?,
)

/**
 * A directory searched for contacts: the contacts of the device, of the work profile, or a
 * remote directory such as the address book of a company.
 */
data class SearchDirectory(
    val id: Long,
    /**
     * The label of the directory, null for the contacts of the device.
     */
    val label: String?,
    /**
     * The name of a remote directory, or its type if it has no name.
     */
    val name: String?,
)

/**
 * The contacts of a [directory] matching the search query, null while searching.
 */
data class DirectorySearchResults(
    val directory: SearchDirectory,
    val contacts: List<ContactListEntry>?,
)

/**
 * The contacts matching a search [query], for each directory.
 */
data class ContactsSearchState(
    val query: String,
    val results: List<DirectorySearchResults>,
) {
    val searching get() = results.any { it.contacts == null }
    val empty get() = results.all { it.contacts?.isEmpty() == true }
}

/**
 * Asks to confirm the deletion of the selected contacts, with a [message] depending on the
 * accounts of their raw contacts.
 */
data class ContactsDeletion(
    @StringRes val message: Int,
    @StringRes val confirmButton: Int,
    val contactIds: List<Long>,
    /**
     * The names of the contacts, shown in the notification of the deletion.
     */
    val names: List<String>,
)

/**
 * The status of the main contact list, for what the activity has to do once.
 */
sealed interface ContactsListStatus {
    /**
     * Nothing to do.
     */
    data object Idle : ContactsListStatus

    /**
     * Shares the selected contacts through the chooser [intent].
     */
    data class Share(val intent: Intent) : ContactsListStatus

    /**
     * The contacts couldn't be synced without a network connection.
     */
    data object ConnectionError : ContactsListStatus
}

/**
 * Why the contacts can't be shown.
 */
enum class ContactsUnavailable {
    /**
     * There are no accounts and no contacts, the user can add an account or import contacts.
     */
    NO_ACCOUNT,

    /**
     * The contacts provider is being upgraded, or is changing the locale of the contacts.
     */
    BUSY,
}

/**
 * The sync of the contacts of the current filter.
 */
data class ContactsSyncState(
    /**
     * Whether the contacts can be synced by pulling the list.
     */
    val canRefresh: Boolean,
    /**
     * Whether the contacts of the filter are being synced.
     */
    val refreshing: Boolean,
    /**
     * Why the sync is off, see SyncUtil, null if no alert should be shown.
     */
    val syncOffReason: Int?,
)
