/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.android.contacts.group.GroupMetaData

/**
 * A contact member of a group.
 */
data class GroupMember(
    val contactId: Long,
    val lookupUri: Uri,
    val displayName: String?,
    val photoThumbnailUri: Uri?,
    /**
     * The address book index section of the contact, e.g. its initial.
     */
    val section: String,
)

/**
 * A group and its members.
 */
data class GroupState(
    val metaData: GroupMetaData,
    val members: List<GroupMember>,
    /**
     * The names of the other groups of the account, trimmed, to prevent duplicates on rename.
     */
    val existingNames: Set<String>,
    /**
     * Whether the header shows the name of the account of the group next to the member count.
     */
    val showAccountName: Boolean,
    /**
     * The members selected, the group is in selection mode if it isn't empty.
     */
    val selectedContactIds: Set<Long>,
    /**
     * Whether the group is in edit mode, showing a remove button on every member.
     */
    val editMode: Boolean,
) {
    val selectionMode get() = selectedContactIds.isNotEmpty()
}

/**
 * The status of the group screen, for what the activity has to do once.
 */
sealed interface GroupStatus {
    /**
     * Nothing to do.
     */
    data object Idle : GroupStatus

    /**
     * The group couldn't be loaded, e.g. because it was deleted.
     */
    data object LoadFailed : GroupStatus

    /**
     * Sends an email or a message to some members, through [intent] if any. [warning] is shown
     * when some of the members have no email address or phone number.
     */
    data class SendTo(val intent: Intent?, @StringRes val warning: Int?) : GroupStatus
}
