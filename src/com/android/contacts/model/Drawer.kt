/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.graphics.drawable.Drawable
import android.net.Uri
import com.android.contacts.list.ContactListFilter

/**
 * The profile of the user, shown at the top of the navigation drawer.
 */
data class DrawerProfile(
    /**
     * The contact of the profile, null if the user didn't set it up yet.
     */
    val contactId: Long?,
    val displayName: String?,
    val photoUri: Uri?,
)

/**
 * A group of the navigation drawer.
 */
data class DrawerGroup(val groupId: Long, val title: String)

/**
 * An account of the navigation drawer, showing its contacts with [filter].
 */
data class DrawerAccount(
    val filter: ContactListFilter,
    val label: String,
    val icon: Drawable?,
    val contentDescription: String,
)

/**
 * The content of the navigation drawer.
 */
data class DrawerState(
    val profile: DrawerProfile,
    val groups: List<DrawerGroup>,
    /**
     * Whether a group can be created, i.e. there is an account supporting groups.
     */
    val canCreateGroup: Boolean,
    /**
     * The accounts, only shown when there are at least two of them.
     */
    val accounts: List<DrawerAccount>,
    /**
     * Whether the emergency information of the user can be edited.
     */
    val emergencyInfoAvailable: Boolean,
)
