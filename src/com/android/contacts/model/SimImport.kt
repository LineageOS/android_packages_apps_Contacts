/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.graphics.drawable.Drawable

/**
 * An account SIM contacts can be imported into.
 */
data class SimImportAccount(
    val key: String,
    val name: CharSequence,
    val type: CharSequence,
    val icon: Drawable?,
)

/**
 * A contact of the SIM card, see [SimContact].
 */
data class SimImportContact(
    val recordNumber: Int,
    val label: String,
    /**
     * Whether the contact already exists in the selected account, in which case it can't be
     * imported.
     */
    val existsInAccount: Boolean,
    val selected: Boolean,
)

/**
 * The SIM contacts and the account they would be imported into.
 */
data class SimImportState(
    val accounts: List<SimImportAccount>,
    val currentAccount: SimImportAccount?,
    val contacts: List<SimImportContact>,
) {
    /**
     * The number of selected contacts that would actually be imported.
     */
    val importableCount = contacts.count { it.selected && !it.existsInAccount }
}
