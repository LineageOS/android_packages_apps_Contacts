/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import android.content.ContentResolver
import android.content.OperationApplicationException
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.ContactsApplication
import com.android.contacts.model.AccountDisplay
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.CustomFilterAccount
import com.android.contacts.model.CustomFilterGroup
import com.android.contacts.model.CustomFilterSaveStatus
import com.android.contacts.model.GroupDelta
import com.android.contacts.model.account.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View model of the screen customizing which groups of each account are displayed.
 */
class CustomFilterViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Application's [ContentResolver].
     */
    private val contentResolver = application.contentResolver

    /**
     * Account type manager, used to get the accounts.
     */
    private val accountTypeManager = AccountTypeManager.getInstance(application)

    /**
     * The accounts and their groups, edited in place until saved. Null while loading.
     */
    private var accountDisplays: List<AccountDisplay>? = null

    /**
     * The accounts and their groups, as displayed. Null while loading.
     */
    private val _accounts = MutableStateFlow<List<CustomFilterAccount>?>(null)
    val accounts = _accounts.asStateFlow()

    /**
     * Whether the groups have been changed since they were loaded.
     */
    private val _hasChanges = MutableStateFlow(false)
    val hasChanges = _hasChanges.asStateFlow()

    /**
     * The status of saving the changes.
     */
    private val _saveStatus = MutableStateFlow(CustomFilterSaveStatus.IDLE)
    val saveStatus = _saveStatus.asStateFlow()

    init {
        viewModelScope.launch {
            accountDisplays = withContext(Dispatchers.IO) {
                createAccountDisplays(accountTypeManager.accountsAsync.get())
            }
            publish()
        }
    }

    fun toggleVisible(account: CustomFilterAccount, group: CustomFilterGroup) {
        val groupDelta = findGroup(account, group) ?: return
        groupDelta.putVisible(!groupDelta.visible)
        publish()
    }

    /**
     * Stop syncing [group], and the contacts not in any group if [alsoUngrouped] is true.
     */
    fun removeSync(
        account: CustomFilterAccount,
        group: CustomFilterGroup,
        alsoUngrouped: Boolean,
    ) {
        val accountDisplay = findAccount(account) ?: return
        val groupDelta = findGroup(account, group) ?: return
        if (alsoUngrouped) {
            accountDisplay.removeSync(accountDisplay.ungrouped)
        }
        accountDisplay.removeSync(groupDelta)
        publish()
    }

    /**
     * Persist the changes, then switch [saveStatus] to [CustomFilterSaveStatus.SAVED].
     */
    fun save() {
        val diff = accountDisplays?.flatMap { it.buildDiff() } ?: return
        if (diff.isEmpty()) {
            _saveStatus.value = CustomFilterSaveStatus.SAVED
            return
        }

        _saveStatus.value = CustomFilterSaveStatus.SAVING
        // Not tied to the screen: the changes must be saved even if it is closed.
        getApplication<ContactsApplication>().applicationScope.launch {
            try {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(diff))
            } catch (e: RemoteException) {
                Log.e(TAG, "Problem saving display groups", e)
            } catch (e: OperationApplicationException) {
                Log.e(TAG, "Problem saving display groups", e)
            }
            _saveStatus.value = CustomFilterSaveStatus.SAVED
        }
    }

    private fun findAccount(account: CustomFilterAccount) =
        accountDisplays?.firstOrNull { it.key == account.key }

    private fun findGroup(account: CustomFilterAccount, group: CustomFilterGroup) =
        findAccount(account)?.groups?.firstOrNull { it.key == group.key }

    private fun publish() {
        val accountDisplays = accountDisplays ?: return
        val context = getApplication<Application>()

        _accounts.value = accountDisplays.map { accountDisplay ->
            val accountInfo = accountDisplay.accountInfo
            CustomFilterAccount(
                key = accountDisplay.key,
                name = accountInfo.nameLabel.takeIf {
                    !accountInfo.isDeviceAccount || accountInfo.hasDistinctName()
                },
                type = accountInfo.typeLabel,
                groups = accountDisplay.groups.map { group ->
                    val canRemoveSync = accountDisplay.supportsSelectiveSync && group.shouldSync
                    CustomFilterGroup(
                        key = group.key,
                        title = group.getTitle(context),
                        visible = group.visible,
                        canRemoveSync = canRemoveSync,
                        removeSyncAffectsUngrouped = canRemoveSync && !group.isUngrouped &&
                                accountDisplay.ungrouped.shouldSync,
                    )
                },
            )
        }
        _hasChanges.value = accountDisplays.any { it.buildDiff().isNotEmpty() }
    }

    private fun createAccountDisplays(accountInfos: List<AccountInfo>) = accountInfos
        // Don't include the null account because it doesn't support writing to
        // ContactsContract.Settings
        .filterNot { it.account.isNullAccount }
        // The same account can be listed more than once, e.g. the device account
        .distinctBy { it.account }
        .mapNotNull { accountInfo ->
            val account = accountInfo.account
            val groupsUri = Groups.CONTENT_URI.buildUpon()
                .appendQueryParameter(Groups.ACCOUNT_NAME, account.name)
                .appendQueryParameter(Groups.ACCOUNT_TYPE, account.type)
            if (account.dataSet != null) {
                groupsUri.appendQueryParameter(Groups.DATA_SET, account.dataSet)
            }
            val cursor = contentResolver.query(
                groupsUri.build(), null, "${Groups.DELETED}=0", null, null
            ) ?: return@mapNotNull null

            val accountDisplay = AccountDisplay(accountInfo)
            val iterator = Groups.newEntityIterator(cursor)
            try {
                var hasGroups = false

                // Create entries for each known group
                while (iterator.hasNext()) {
                    accountDisplay.addGroup(GroupDelta.fromBefore(iterator.next().entityValues))
                    hasGroups = true
                }

                // Create single entry handling ungrouped status
                accountDisplay.ungrouped = GroupDelta.fromSettings(
                    contentResolver, account.name, account.type, account.dataSet, hasGroups
                )
                accountDisplay.addGroup(accountDisplay.ungrouped)
            } finally {
                iterator.close()
            }

            accountDisplay
        }

    private val AccountDisplay.key: String
        get() = "${accountDisplays?.indexOf(this)}_${accountInfo.account.stringify()}"

    private val GroupDelta.key: String
        get() = if (isUngrouped) "ungrouped" else "group_$id"

    companion object {
        private const val TAG = "CustomFilterViewModel"
    }
}
