/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.Profile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.contacts.GroupListLoader
import com.android.contacts.R
import com.android.contacts.group.GroupUtil
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.DrawerAccount
import com.android.contacts.model.DrawerGroup
import com.android.contacts.model.DrawerProfile
import com.android.contacts.model.DrawerState
import com.android.contacts.model.account.AccountDisplayInfoFactory
import com.android.contacts.profile.ProfileLoader
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.ImplicitIntentsUtil
import com.android.contactsbind.ObjectFactory
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
 * View model of the navigation drawer of the main screen: the profile of the user, the groups
 * and the accounts.
 */
class DrawerViewModel(application: Application) : AndroidViewModel(application) {
    private val contentResolver = application.contentResolver

    /**
     * Account type manager, used to get the accounts.
     */
    private val accountTypeManager = AccountTypeManager.getInstance(application)

    /**
     * Emits when the profile or the groups may have changed.
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
     * Emits when the accounts may have changed.
     */
    private val accountsChanges = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val broadcastManager = LocalBroadcastManager.getInstance(getApplication<Application>())
        broadcastManager.registerReceiver(
            receiver, IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED)
        )
        trySend(Unit)
        awaitClose { broadcastManager.unregisterReceiver(receiver) }
    }.conflate()

    /**
     * The content of the drawer. Null while loading.
     */
    val state: StateFlow<DrawerState?> = combine(contactsChanges, accountsChanges) { _, _ ->
        DrawerState(
            profile = loadProfile(),
            groups = loadGroups(),
            canCreateGroup = accountTypeManager
                .filterAccountsAsync(AccountTypeManager.AccountFilter.GROUPS_WRITABLE)
                .get()
                .isNotEmpty(),
            accounts = loadAccounts(),
            emergencyInfoAvailable = ImplicitIntentsUtil.getIntentForEmergencyInfo(
                getApplication<Application>()
            ) != null,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private fun loadProfile(): DrawerProfile {
        val application = getApplication<Application>()
        val profile = contentResolver.query(
            Profile.CONTENT_URI, ProfileLoader.getProjection(application), null, null, null
        ).use { cursor -> ProfileLoader.getProfileItem(application, cursor) }
        return if (profile.HasProfile()) {
            DrawerProfile(
                contactId = profile.contactId,
                displayName = profile.displayName,
                photoUri = profile.photoUri?.let(Uri::parse),
            )
        } else {
            DrawerProfile(contactId = null, displayName = null, photoUri = null)
        }
    }

    /**
     * Loads the groups, leaving out the empty system groups of Google accounts.
     */
    private fun loadGroups(): List<DrawerGroup> = contentResolver.query(
        Groups.CONTENT_SUMMARY_URI,
        GroupListLoader.COLUMNS,
        GroupUtil.DEFAULT_SELECTION,
        null,
        GroupUtil.getGroupsSortOrder(),
    )?.use { cursor ->
        (0 until cursor.count)
            .mapNotNull { GroupUtil.getGroupListItem(cursor, it) }
            .filterNot { GroupUtil.isEmptyFFCGroup(it) }
            .map { DrawerGroup(it.groupId, it.title) }
    }.orEmpty()

    /**
     * Loads the accounts, shown only when there are at least two of them.
     */
    private fun loadAccounts(): List<DrawerAccount> {
        val application = getApplication<Application>()
        val filters = AccountFilterUtil.getFiltersForAccounts(
            application,
            ObjectFactory.getDeviceLocalAccountTypeFactory(application),
            accountTypeManager.filterAccountsAsync(AccountTypeManager.drawerDisplayableFilter())
                .get(),
        )
        if (filters.size < 2) {
            return emptyList()
        }
        val displayInfoFactory = AccountDisplayInfoFactory.fromListFilters(application, filters)
        return filters.map { filter ->
            val displayInfo = displayInfoFactory.getAccountDisplayInfoFor(filter)
            DrawerAccount(
                filter = filter,
                label = if (filter.accountType == application.packageName) {
                    application.getString(R.string.account_phone)
                } else {
                    displayInfo.nameLabel.toString()
                },
                icon = displayInfo.icon,
                contentDescription = "${displayInfo.typeLabel} ${filter.accountName}",
            )
        }
    }
}
