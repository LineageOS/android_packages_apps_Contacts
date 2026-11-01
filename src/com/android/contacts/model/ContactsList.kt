/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

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
