/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.DisplayNameSources
import android.provider.ContactsContract.Profile
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.R
import com.android.contacts.list.ContactListFilter
import com.android.contacts.list.ContactListFilterController
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.MyInfo
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.preference.ContactsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View model of the settings screens.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Application's [ContentResolver].
     */
    private val contentResolver = application.contentResolver

    /**
     * The contacts preferences, where the choices are stored.
     */
    private val preferences = ContactsPreferences(application)

    /**
     * Controller of the filter of the contacts list.
     */
    private val contactListFilterController = ContactListFilterController.getInstance(application)

    /**
     * Account type manager, used to get the writable accounts.
     */
    private val accountTypeManager = AccountTypeManager.getInstance(application)

    /**
     * How the contacts are sorted, see [ContactsPreferences.getSortOrder].
     */
    private val _sortOrder = MutableStateFlow(preferences.sortOrder)
    val sortOrder = _sortOrder.asStateFlow()

    /**
     * How the names are displayed, see [ContactsPreferences.getDisplayOrder].
     */
    private val _displayOrder = MutableStateFlow(preferences.displayOrder)
    val displayOrder = _displayOrder.asStateFlow()

    /**
     * When the phonetic name fields are shown, see
     * [ContactsPreferences.getPhoneticNameDisplayPreference].
     */
    private val _phoneticNameDisplay = MutableStateFlow(preferences.phoneticNameDisplayPreference)
    val phoneticNameDisplay = _phoneticNameDisplay.asStateFlow()

    /**
     * The label of the account new contacts are saved to.
     */
    private val _defaultAccount = MutableStateFlow<CharSequence?>(null)
    val defaultAccount = _defaultAccount.asStateFlow()

    /**
     * The string resource describing the contacts filter.
     */
    private val _customFilter = MutableStateFlow<Int?>(null)
    val customFilter = _customFilter.asStateFlow()

    /**
     * Emits when the user profile may have changed.
     */
    private val profileChanges = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(Profile.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    /**
     * The user profile.
     */
    val myInfo: StateFlow<MyInfo> = combine(profileChanges, displayOrder) { _, displayOrder ->
        loadMyInfo(displayOrder)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyInfo.Loading,
        )

    init {
        refreshCustomFilter()
        refreshDefaultAccount()
    }

    fun setSortOrder(sortOrder: Int) {
        preferences.sortOrder = sortOrder
        _sortOrder.value = preferences.sortOrder
    }

    fun setDisplayOrder(displayOrder: Int) {
        preferences.displayOrder = displayOrder
        _displayOrder.value = preferences.displayOrder
    }

    fun setPhoneticNameDisplay(phoneticNameDisplay: Int) {
        preferences.phoneticNameDisplayPreference = phoneticNameDisplay
        _phoneticNameDisplay.value = preferences.phoneticNameDisplayPreference
    }

    fun refreshCustomFilter() {
        _customFilter.value = when (contactListFilterController.persistedFilter?.filterType) {
            ContactListFilter.FILTER_TYPE_DEFAULT,
            ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS -> R.string.list_filter_all_accounts

            ContactListFilter.FILTER_TYPE_CUSTOM -> R.string.listCustomView

            else -> null
        }
    }

    fun refreshDefaultAccount() {
        viewModelScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                accountTypeManager.filterAccountsAsync(AccountTypeManager.writableFilter()).get()
            }
            _defaultAccount.value =
                AccountInfo.getAccount(accounts, preferences.defaultAccount)?.nameLabel
        }
    }

    private fun loadMyInfo(displayOrder: Int): MyInfo {
        val displayNameColumn = when (displayOrder) {
            ContactsPreferences.DISPLAY_ORDER_PRIMARY -> Contacts.DISPLAY_NAME_PRIMARY
            else -> Contacts.DISPLAY_NAME_ALTERNATIVE
        }
        val projection = arrayOf(
            Contacts._ID,
            displayNameColumn,
            Contacts.IS_USER_PROFILE,
            Contacts.DISPLAY_NAME_SOURCE,
        )

        val cursor = try {
            contentResolver.query(Profile.CONTENT_URI, projection, null, null, null)
        } catch (e: RuntimeException) {
            null
        }

        return cursor?.use {
            if (!it.moveToFirst() || it.getInt(2) != 1) {
                return@use MyInfo.None
            }

            val displayName = it.getString(1)
            MyInfo.Existing(
                contactId = it.getLong(0),
                displayName = when {
                    displayName.isNullOrEmpty() ->
                        getApplication<Application>().getString(R.string.missing_name)

                    it.getInt(3) == DisplayNameSources.PHONE ->
                        BidiFormatter.getInstance()
                            .unicodeWrap(displayName, TextDirectionHeuristics.LTR)

                    else -> displayName
                },
            )
        } ?: MyInfo.None
    }
}
