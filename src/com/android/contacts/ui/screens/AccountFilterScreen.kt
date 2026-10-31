/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.contacts.R
import com.android.contacts.ui.composables.SettingsEntry
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.settingsGroup

/**
 * Screen to choose which contacts are displayed: all of them, or a customized selection.
 */
@Composable
fun AccountFilterScreen(
    isCustomFilter: Boolean,
    onNavigateUp: () -> Unit,
    onAllContactsClick: () -> Unit,
    onCustomizeClick: () -> Unit,
) {
    val entries = listOf(
        SettingsEntry(
            key = "allContacts",
            title = stringResource(R.string.list_filter_all_accounts),
            selected = !isCustomFilter,
            onClick = onAllContactsClick,
        ),
        SettingsEntry(
            key = "customize",
            title = stringResource(R.string.list_filter_customize),
            selected = isCustomFilter,
            onClick = onCustomizeClick,
        ),
    )

    SettingsScaffold(
        title = stringResource(R.string.activity_title_contacts_filter),
        onNavigateUp = onNavigateUp,
    ) {
        settingsGroup(entries)
    }
}
