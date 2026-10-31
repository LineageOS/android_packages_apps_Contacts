/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.contacts.R
import com.android.contacts.ui.composables.SettingsEntry
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.settingsGroup

/**
 * About screen, showing the version and the legal information.
 */
@Composable
fun AboutScreen(
    versionName: String?,
    onNavigateUp: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
) {
    val entries = listOf(
        SettingsEntry(
            key = "buildVersion",
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.about_build_version),
            summary = versionName,
            onClick = {},
        ),
        SettingsEntry(
            key = "openSourceLicenses",
            icon = Icons.Outlined.Description,
            title = stringResource(R.string.about_open_source_licenses),
            summary = stringResource(R.string.about_open_source_licenses_summary),
            onClick = onOpenSourceLicensesClick,
        ),
        SettingsEntry(
            key = "privacyPolicy",
            icon = Icons.Outlined.PrivacyTip,
            title = stringResource(R.string.about_privacy_policy),
            onClick = onPrivacyPolicyClick,
        ),
        SettingsEntry(
            key = "termsOfService",
            icon = Icons.Outlined.Policy,
            title = stringResource(R.string.about_terms_of_service),
            onClick = onTermsOfServiceClick,
        ),
    )

    SettingsScaffold(
        title = stringResource(R.string.setting_about),
        onNavigateUp = onNavigateUp,
    ) {
        settingsGroup(entries)
    }
}
