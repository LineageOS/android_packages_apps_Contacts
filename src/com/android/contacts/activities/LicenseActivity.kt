/*
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android.contacts.ui.screens.LicensesScreen
import com.android.contacts.ui.theme.ContactsTheme

/**
 * Displays the open source licenses of the app.
 */
class LicenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                LicensesScreen(
                    url = LICENSE_FILE,
                    onNavigateUp = ::finish,
                )
            }
        }
    }

    companion object {
        private const val LICENSE_FILE = "file:///android_asset/licenses.html"
    }
}
