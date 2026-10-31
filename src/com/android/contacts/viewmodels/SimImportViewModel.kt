/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.contacts.SimImportService
import com.android.contacts.activities.SimImportActivity
import com.android.contacts.database.SimContactDao
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.SimCard
import com.android.contacts.model.SimContact
import com.android.contacts.model.SimImportAccount
import com.android.contacts.model.SimImportContact
import com.android.contacts.model.SimImportState
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.preference.ContactsPreferences
import com.android.contacts.util.concurrent.ContactsExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * View model of the screen importing the contacts of a SIM card into an account.
 */
class SimImportViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    /**
     * The subscription of the SIM card to import the contacts from.
     */
    private val subscriptionId: Int =
        savedStateHandle[SimImportActivity.EXTRA_SUBSCRIPTION_ID] ?: SimCard.NO_SUBSCRIPTION_ID

    private val simContactDao = SimContactDao.create(application)
    private val accountTypeManager = AccountTypeManager.getInstance(application)
    private val preferences = ContactsPreferences(application)

    /**
     * Reads from the SIM card one at a time, see [ContactsExecutors.getSimReadExecutor].
     */
    private val simReadDispatcher = ContactsExecutors.getSimReadExecutor().asCoroutineDispatcher()

    private var accounts = emptyList<AccountInfo>()
    private var contacts = emptyList<SimContact>()
    private var existingContacts = emptyMap<AccountWithDataSet, Set<SimContact>>()

    private var currentAccount: AccountWithDataSet? = null

    /**
     * The SIM contacts and the account they would be imported into. Null while loading.
     */
    private val _state = MutableStateFlow<SimImportState?>(null)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accountsAsync = async(Dispatchers.IO) {
                accountTypeManager.filterAccountsAsync(
                    AccountTypeManager.insertableFilter(getApplication<Application>())
                ).get()
            }
            val simContactsAsync = async(simReadDispatcher) { loadFromSim() }

            accounts = accountsAsync.await()
            simContactsAsync.await().let { (contacts, existingContacts) ->
                this@SimImportViewModel.contacts = contacts
                this@SimImportViewModel.existingContacts = existingContacts
            }

            // Fall back to the default account, then to the first one.
            val savedAccount = savedStateHandle.get<String>(KEY_CURRENT_ACCOUNT)
            currentAccount = accounts.map { it.account }.let { accounts ->
                accounts.firstOrNull { it.stringify() == savedAccount }
                    ?: accounts.firstOrNull { it == preferences.defaultAccount }
                    ?: accounts.firstOrNull()
            }

            publish()
        }
    }

    fun selectAccount(account: SimImportAccount) {
        currentAccount = accounts.firstOrNull { it.account.stringify() == account.key }?.account
        savedStateHandle[KEY_CURRENT_ACCOUNT] = account.key
        publish()
    }

    fun toggleSelected(contact: SimImportContact) {
        val account = currentAccount ?: return
        val selection = selectionFor(account).toMutableSet()
        if (!selection.remove(contact.recordNumber)) {
            selection.add(contact.recordNumber)
        }
        savedStateHandle[selectionKey(account)] = selection.toIntArray()
        publish()
    }

    /**
     * Starts importing the selected contacts that don't exist yet in the current account.
     */
    fun importSelected() {
        val account = currentAccount
        val selection = selectionFor(account)
        val existing = existingContacts[account].orEmpty()
        val importableContacts = contacts.filter {
            it.recordNumber in selection && it !in existing
        }

        SimImportService.startImport(
            getApplication<Application>(), subscriptionId, ArrayList(importableContacts), account
        )
    }

    /**
     * The record numbers of the selected contacts. All of them are selected by default.
     */
    private fun selectionFor(account: AccountWithDataSet?): Set<Int> =
        account?.let { savedStateHandle.get<IntArray>(selectionKey(it))?.toSet() }
            ?: contacts.map { it.recordNumber }.toSet()

    private fun loadFromSim(): Pair<List<SimContact>, Map<AccountWithDataSet, Set<SimContact>>> {
        val sim = simContactDao.getSimBySubscriptionId(subscriptionId)
            ?: return emptyList<SimContact>() to emptyMap()
        val contacts = simContactDao.loadContactsForSim(sim)
        return contacts to simContactDao.findAccountsOfExistingSimContacts(contacts)
    }

    private fun publish() {
        val account = currentAccount
        val selection = selectionFor(account)
        val existing = existingContacts[account].orEmpty()
        val accounts = accounts.map { it.toSimImportAccount() }

        _state.value = SimImportState(
            accounts = accounts,
            currentAccount = accounts.firstOrNull { it.key == account?.stringify() },
            contacts = contacts.map {
                SimImportContact(
                    recordNumber = it.recordNumber,
                    label = it.label,
                    existsInAccount = it in existing,
                    selected = it.recordNumber in selection,
                )
            },
        )
    }

    private fun AccountInfo.toSimImportAccount(): SimImportAccount {
        val hasName = !isDeviceAccount || hasDistinctName()
        return SimImportAccount(
            key = account.stringify(),
            name = if (hasName) nameLabel else typeLabel,
            type = typeLabel.takeIf { hasName },
            icon = icon,
        )
    }

    private val SimContact.label: String
        get() = when {
            hasName() -> name
            hasPhone() -> phone
            hasEmails() -> emails[0]
            // This isn't really possible because we skip empty SIM contacts during loading
            else -> ""
        }

    companion object {
        private const val KEY_CURRENT_ACCOUNT = "currentAccount"

        private fun selectionKey(account: AccountWithDataSet) = "selection_${account.stringify()}"
    }
}
