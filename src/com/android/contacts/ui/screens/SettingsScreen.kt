/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.android.contacts.R
import com.android.contacts.model.MyInfo
import com.android.contacts.model.SettingsFeatures
import com.android.contacts.preference.ContactsPreferences
import com.android.contacts.ui.composables.ChoiceDialog
import com.android.contacts.ui.composables.SettingsChoice
import com.android.contacts.ui.composables.SettingsEntry
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.settingsGroup

/**
 * Main settings screen.
 */
@Composable
fun SettingsScreen(
    features: SettingsFeatures,
    myInfo: MyInfo,
    defaultAccount: CharSequence?,
    customFilter: Int?,
    sortOrder: Int,
    displayOrder: Int,
    phoneticNameDisplay: Int,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onMyInfoClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onDefaultAccountClick: () -> Unit,
    onCustomContactsFilterClick: () -> Unit,
    onSortOrderChange: (Int) -> Unit,
    onDisplayOrderChange: (Int) -> Unit,
    onPhoneticNameDisplayChange: (Int) -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onBlockedNumbersClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    var openedChoice by rememberSaveable { mutableStateOf<ChoiceSetting?>(null) }

    val choiceSettings = mapOf(
        ChoiceSetting.SORT_ORDER to ChoiceSettingState(
            icon = Icons.AutoMirrored.Outlined.Sort,
            title = stringResource(R.string.display_options_sort_list_by),
            choices = listOf(
                SettingsChoice(
                    ContactsPreferences.SORT_ORDER_PRIMARY,
                    stringResource(R.string.display_options_sort_by_given_name),
                ),
                SettingsChoice(
                    ContactsPreferences.SORT_ORDER_ALTERNATIVE,
                    stringResource(R.string.display_options_sort_by_family_name),
                ),
            ),
            selected = sortOrder,
            onChange = onSortOrderChange,
        ),
        ChoiceSetting.DISPLAY_ORDER to ChoiceSettingState(
            icon = Icons.Outlined.Badge,
            title = stringResource(R.string.display_options_view_names_as),
            choices = listOf(
                SettingsChoice(
                    ContactsPreferences.DISPLAY_ORDER_PRIMARY,
                    stringResource(R.string.display_options_view_given_name_first),
                ),
                SettingsChoice(
                    ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE,
                    stringResource(R.string.display_options_view_family_name_first),
                ),
            ),
            selected = displayOrder,
            onChange = onDisplayOrderChange,
        ),
        ChoiceSetting.PHONETIC_NAME_DISPLAY to ChoiceSettingState(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.display_options_phonetic_name_fields),
            choices = listOf(
                SettingsChoice(
                    ContactsPreferences.PHONETIC_NAME_DISPLAY_SHOW_ALWAYS,
                    stringResource(R.string.editor_options_always_show_phonetic_names),
                ),
                SettingsChoice(
                    ContactsPreferences.PHONETIC_NAME_DISPLAY_HIDE_IF_EMPTY,
                    stringResource(R.string.editor_options_hide_phonetic_names_if_empty),
                ),
            ),
            selected = phoneticNameDisplay,
            onChange = onPhoneticNameDisplayChange,
        ),
    )

    val accountEntries = listOf(
        SettingsEntry(
            key = "myInfo",
            icon = Icons.Outlined.AccountCircle,
            title = stringResource(R.string.settings_my_info_title),
            summary = when (myInfo) {
                MyInfo.Loading -> null
                MyInfo.None -> stringResource(R.string.set_up_profile)
                is MyInfo.Existing -> myInfo.displayName
            },
            onClick = onMyInfoClick,
        ),
        SettingsEntry(
            key = "accounts",
            icon = Icons.Outlined.ManageAccounts,
            title = stringResource(R.string.settings_accounts),
            onClick = onAccountsClick,
        ),
        SettingsEntry(
            key = "defaultAccount",
            icon = Icons.Outlined.PersonAdd,
            title = stringResource(R.string.default_editor_account),
            summary = defaultAccount,
            onClick = onDefaultAccountClick,
        ),
    )

    val displayEntries = buildList {
        add(
            SettingsEntry(
                key = "customContactsFilter",
                icon = Icons.Outlined.FilterList,
                title = stringResource(R.string.menu_contacts_filter),
                summary = customFilter?.let { stringResource(it) },
                onClick = onCustomContactsFilterClick,
            )
        )

        choiceSettings.forEach { (setting, state) ->
            val available = when (setting) {
                ChoiceSetting.SORT_ORDER -> features.sortOrder
                ChoiceSetting.DISPLAY_ORDER -> features.displayOrder
                ChoiceSetting.PHONETIC_NAME_DISPLAY -> features.phoneticNameDisplay
            }
            if (available) {
                add(
                    SettingsEntry(
                        key = setting.name,
                        icon = state.icon,
                        title = state.title,
                        summary = state.choices.firstOrNull { it.value == state.selected }?.label,
                        onClick = { openedChoice = setting },
                    )
                )
            }
        }
    }

    val manageEntries = buildList {
        add(
            SettingsEntry(
                key = "import",
                icon = Icons.Outlined.FileDownload,
                title = stringResource(R.string.menu_import),
                onClick = onImportClick,
            )
        )
        if (features.export) {
            add(
                SettingsEntry(
                    key = "export",
                    icon = Icons.Outlined.FileUpload,
                    title = stringResource(R.string.menu_export),
                    onClick = onExportClick,
                )
            )
        }
        if (features.blockedNumbers) {
            add(
                SettingsEntry(
                    key = "blockedNumbers",
                    icon = Icons.Outlined.Block,
                    title = stringResource(R.string.menu_blocked_numbers),
                    onClick = onBlockedNumbersClick,
                )
            )
        }
    }

    val aboutEntries = buildList {
        if (features.about) {
            add(
                SettingsEntry(
                    key = "about",
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.setting_about),
                    onClick = onAboutClick,
                )
            )
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.activity_title_settings),
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
    ) {
        settingsGroup(accountEntries)
        settingsGroup(displayEntries)
        settingsGroup(manageEntries)
        settingsGroup(aboutEntries)
    }

    openedChoice?.let { setting ->
        val state = choiceSettings.getValue(setting)
        ChoiceDialog(
            title = state.title,
            choices = state.choices,
            selected = state.selected,
            onSelect = {
                state.onChange(it)
                openedChoice = null
            },
            onDismiss = { openedChoice = null },
        )
    }
}

/**
 * Settings entries that are picked from a [ChoiceDialog].
 */
private enum class ChoiceSetting {
    SORT_ORDER,
    DISPLAY_ORDER,
    PHONETIC_NAME_DISPLAY,
}

private class ChoiceSettingState(
    val icon: ImageVector,
    val title: String,
    val choices: List<SettingsChoice>,
    val selected: Int,
    val onChange: (Int) -> Unit,
)
