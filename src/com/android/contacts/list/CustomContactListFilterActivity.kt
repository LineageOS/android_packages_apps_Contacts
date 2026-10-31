/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.list

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.contacts.model.CustomFilterSaveStatus
import com.android.contacts.ui.screens.CustomFilterScreen
import com.android.contacts.ui.theme.ContactsTheme
import com.android.contacts.viewmodels.CustomFilterViewModel

/**
 * Shows a list of all available groups of each account, letting the user choose which ones are
 * displayed in the contacts list.
 */
class CustomContactListFilterActivity : ComponentActivity() {
    // View models
    private val customFilterViewModel: CustomFilterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentListFilterType = intent.getIntExtra(
            EXTRA_CURRENT_LIST_FILTER_TYPE, ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
        )

        enableEdgeToEdge()
        setContent {
            val accounts by customFilterViewModel.accounts.collectAsStateWithLifecycle()
            val hasChanges by customFilterViewModel.hasChanges.collectAsStateWithLifecycle()
            val saveStatus by customFilterViewModel.saveStatus.collectAsStateWithLifecycle()

            LaunchedEffect(saveStatus) {
                if (saveStatus == CustomFilterSaveStatus.SAVED) {
                    setResult(RESULT_OK)
                    finish()
                }
            }

            ContactsTheme {
                CustomFilterScreen(
                    accounts = accounts,
                    isSaving = saveStatus != CustomFilterSaveStatus.IDLE,
                    // Switching to the custom filter is a change by itself.
                    hasUnsavedChanges = accounts != null &&
                            (currentListFilterType != ContactListFilter.FILTER_TYPE_CUSTOM ||
                                    hasChanges),
                    onDiscard = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onSave = customFilterViewModel::save,
                    onToggleVisible = customFilterViewModel::toggleVisible,
                    onRemoveSync = customFilterViewModel::removeSync,
                )
            }
        }
    }

    companion object {
        const val EXTRA_CURRENT_LIST_FILTER_TYPE = "currentListFilterType"
    }
}
