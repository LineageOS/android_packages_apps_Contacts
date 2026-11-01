/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.list

import android.database.Cursor
import android.provider.ContactsContract.Contacts

/**
 * The address book index of a [cursor] of contacts queried with
 * [Contacts.EXTRA_ADDRESS_BOOK_INDEX], giving the section, e.g. the initial, of each of its rows.
 */
class AddressBookIndex(cursor: Cursor) {
    private val sections =
        cursor.extras.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES) ?: emptyArray()

    /**
     * The position following the last row of each section.
     */
    private val sectionEnds = cursor.extras.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)
        ?.takeIf { it.size == sections.size }
        ?.runningReduce(Int::plus)
        ?.toIntArray()
        ?: IntArray(0)

    /**
     * Returns the section of the row at [position] of the cursor, or an empty string if the
     * cursor has no index.
     */
    fun sectionAt(position: Int): String {
        val index = sectionEnds.binarySearch(position).let { if (it >= 0) it + 1 else -(it + 1) }
        return sections.getOrNull(index).orEmpty()
    }
}
