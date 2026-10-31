/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.contacts.ui.screens.SimImportScreen
import com.android.contacts.ui.theme.ContactsTheme
import com.android.contacts.viewmodels.SimImportViewModel

/**
 * Imports the contacts of the SIM card with the [EXTRA_SUBSCRIPTION_ID] subscription.
 */
class SimImportActivity : ComponentActivity() {
    // View models
    private val simImportViewModel: SimImportViewModel by viewModels()

    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val state by simImportViewModel.state.collectAsStateWithLifecycle()

            ContactsTheme {
                SimImportScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onImport = {
                        simImportViewModel.importSelected()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onAccountSelected = simImportViewModel::selectAccount,
                    onToggleSelected = simImportViewModel::toggleSelected,
                )
            }
        }
    }

    companion object {
        const val EXTRA_SUBSCRIPTION_ID = "extraSubscriptionId"
    }
}
