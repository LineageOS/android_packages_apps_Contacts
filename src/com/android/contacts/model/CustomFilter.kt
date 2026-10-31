/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

/**
 * An account, as shown when customizing the contacts filter.
 */
data class CustomFilterAccount(
    val key: String,
    /**
     * The name of the account, null if it isn't relevant (e.g. the device account).
     */
    val name: CharSequence?,
    val type: CharSequence,
    val groups: List<CustomFilterGroup>,
)

/**
 * A group of an account, as shown when customizing the contacts filter.
 */
data class CustomFilterGroup(
    val key: String,
    val title: CharSequence?,
    val visible: Boolean,
    /**
     * Whether the user can stop syncing this group.
     */
    val canRemoveSync: Boolean,
    /**
     * Whether removing this group from sync also stops syncing the contacts not in any group.
     */
    val removeSyncAffectsUngrouped: Boolean,
)

/**
 * The status of saving the contacts filter.
 */
enum class CustomFilterSaveStatus {
    IDLE,
    SAVING,
    SAVED,
}
