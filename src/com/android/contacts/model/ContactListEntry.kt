/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.net.Uri

/**
 * A contact shown in a list of contacts.
 */
data class ContactListEntry(
    val contactId: Long,
    val lookupUri: Uri,
    val displayName: String?,
    val photoThumbnailUri: Uri?,
    /**
     * The address book index section of the contact, e.g. its initial.
     */
    val section: String,
    /**
     * Whether the contact is shown in the favorites section, above the other sections.
     */
    val favorite: Boolean = false,
    /**
     * The part of the contact matching the search query, if not its name.
     */
    val snippet: String? = null,
    /**
     * Whether the contact is from the work profile.
     */
    val workProfile: Boolean = false,
)
