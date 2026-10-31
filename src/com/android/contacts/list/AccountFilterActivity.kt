/*
 * SPDX-FileCopyrightText: 2011 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.list

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.android.contacts.ui.screens.AccountFilterScreen
import com.android.contacts.ui.theme.ContactsTheme

/**
 * Lets the user choose which contacts are displayed, see [ContactListFilter]. The chosen filter
 * is returned in [EXTRA_CONTACT_LIST_FILTER].
 */
class AccountFilterActivity : ComponentActivity() {
    // Activity result launchers
    private val customizeFilterLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finishWithFilter(ContactListFilter.FILTER_TYPE_CUSTOM)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The default contact list type, it should be either FILTER_TYPE_ALL_ACCOUNTS or
        // FILTER_TYPE_CUSTOM, since those are the only two options we give the user.
        val currentFilterType =
            if (ContactListFilterController.getInstance(this).isCustomFilterPersisted) {
                ContactListFilter.FILTER_TYPE_CUSTOM
            } else {
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
            }

        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                AccountFilterScreen(
                    isCustomFilter = currentFilterType == ContactListFilter.FILTER_TYPE_CUSTOM,
                    onNavigateUp = ::finish,
                    onAllContactsClick = {
                        finishWithFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS)
                    },
                    onCustomizeClick = {
                        customizeFilterLauncher.launch(
                            Intent(this, CustomContactListFilterActivity::class.java).putExtra(
                                CustomContactListFilterActivity.EXTRA_CURRENT_LIST_FILTER_TYPE,
                                currentFilterType,
                            )
                        )
                    },
                )
            }
        }
    }

    private fun finishWithFilter(filterType: Int) {
        setResult(
            RESULT_OK,
            Intent().putExtra(
                EXTRA_CONTACT_LIST_FILTER,
                ContactListFilter.createFilterWithType(filterType),
            ),
        )
        finish()
    }

    companion object {
        const val EXTRA_CONTACT_LIST_FILTER = "contactListFilter"
    }
}
