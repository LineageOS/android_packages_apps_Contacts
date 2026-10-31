/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.android.contacts.model.MyInfo
import com.android.contacts.model.SettingsFeatures
import com.android.contacts.ui.navigation.Screen
import com.android.contacts.ui.screens.AboutScreen
import com.android.contacts.ui.screens.SettingsScreen
import com.android.contacts.viewmodels.SettingsViewModel

/**
 * Root of the settings, navigating between the settings screens.
 */
@Composable
fun SettingsApp(
    settingsViewModel: SettingsViewModel,
    features: SettingsFeatures,
    versionName: String?,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onMyInfoClick: (MyInfo) -> Unit,
    onAccountsClick: () -> Unit,
    onDefaultAccountClick: () -> Unit,
    onCustomContactsFilterClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onBlockedNumbersClick: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
) {
    val myInfo by settingsViewModel.myInfo.collectAsStateWithLifecycle()
    val defaultAccount by settingsViewModel.defaultAccount.collectAsStateWithLifecycle()
    val customFilter by settingsViewModel.customFilter.collectAsStateWithLifecycle()
    val sortOrder by settingsViewModel.sortOrder.collectAsStateWithLifecycle()
    val displayOrder by settingsViewModel.displayOrder.collectAsStateWithLifecycle()
    val phoneticNameDisplay by settingsViewModel.phoneticNameDisplay
        .collectAsStateWithLifecycle()

    val navigationBackStack = rememberSaveable(saver = Screen.BackStackSaver) {
        mutableStateListOf<Screen>(Screen.Settings)
    }

    val entryProvider = entryProvider {
        entry<Screen.Settings> {
            SettingsScreen(
                features = features,
                myInfo = myInfo,
                defaultAccount = defaultAccount,
                customFilter = customFilter,
                sortOrder = sortOrder,
                displayOrder = displayOrder,
                phoneticNameDisplay = phoneticNameDisplay,
                snackbarHostState = snackbarHostState,
                onNavigateUp = onClose,
                onMyInfoClick = { onMyInfoClick(myInfo) },
                onAccountsClick = onAccountsClick,
                onDefaultAccountClick = onDefaultAccountClick,
                onCustomContactsFilterClick = onCustomContactsFilterClick,
                onSortOrderChange = settingsViewModel::setSortOrder,
                onDisplayOrderChange = settingsViewModel::setDisplayOrder,
                onPhoneticNameDisplayChange = settingsViewModel::setPhoneticNameDisplay,
                onImportClick = onImportClick,
                onExportClick = onExportClick,
                onBlockedNumbersClick = onBlockedNumbersClick,
                onAboutClick = { navigationBackStack.add(Screen.About) },
            )
        }

        entry<Screen.About> {
            AboutScreen(
                versionName = versionName,
                onNavigateUp = navigationBackStack::removeLastOrNull,
                onOpenSourceLicensesClick = onOpenSourceLicensesClick,
                onPrivacyPolicyClick = onPrivacyPolicyClick,
                onTermsOfServiceClick = onTermsOfServiceClick,
            )
        }
    }

    NavDisplay(
        backStack = navigationBackStack,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        popTransitionSpec = { fadeIn() togetherWith fadeOut() },
        predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
        entryProvider = entryProvider,
    )
}
