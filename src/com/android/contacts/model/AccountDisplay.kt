/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.content.ContentProviderOperation
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.GoogleAccountType

/**
 * [GroupDelta] details for a single account, as shown when customizing the contacts filter.
 */
class AccountDisplay(val accountInfo: AccountInfo) {
    /**
     * The entry for the contacts of the account that aren't in any group.
     */
    lateinit var ungrouped: GroupDelta

    private val syncedGroups = mutableListOf<GroupDelta>()
    private val unsyncedGroups = mutableListOf<GroupDelta>()

    /**
     * All the groups of the account, synced ones first.
     */
    val groups: List<GroupDelta>
        get() = syncedGroups + unsyncedGroups

    /**
     * Whether groups can be individually removed from sync.
     */
    // TODO: read sync mode through <sync-adapter> definition
    val supportsSelectiveSync: Boolean
        get() = accountInfo.account.type == GoogleAccountType.ACCOUNT_TYPE &&
                accountInfo.account.dataSet == null

    /**
     * Add the given [GroupDelta] internally, filing based on its [GroupDelta.shouldSync] status.
     */
    fun addGroup(group: GroupDelta) {
        if (group.shouldSync) {
            syncedGroups.add(group)
        } else {
            unsyncedGroups.add(group)
        }
    }

    /**
     * Stop syncing [group], and file it internally based on its updated state.
     */
    fun removeSync(group: GroupDelta) {
        group.putShouldSync(false)
        if (syncedGroups.remove(group)) {
            unsyncedGroups.add(group)
        }
    }

    /**
     * Build the [ContentProviderOperation]s persisting any user changes to the groups of the
     * account.
     */
    fun buildDiff() = groups.mapNotNull { it.buildDiff() }
}
