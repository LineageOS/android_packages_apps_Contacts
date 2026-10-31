/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.contacts.R
import com.android.contacts.model.CustomFilterAccount
import com.android.contacts.model.CustomFilterGroup
import com.android.contacts.ui.composables.SettingsEntry
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.settingsGroup

/**
 * Screen customizing which groups of each account are displayed in the contacts list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomFilterScreen(
    accounts: List<CustomFilterAccount>?,
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onToggleVisible: (CustomFilterAccount, CustomFilterGroup) -> Unit,
    onRemoveSync: (CustomFilterAccount, CustomFilterGroup, Boolean) -> Unit,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var removeSyncTarget by remember {
        mutableStateOf<Pair<CustomFilterAccount, CustomFilterGroup>?>(null)
    }
    var removeSyncWarningTarget by remember {
        mutableStateOf<Pair<CustomFilterAccount, CustomFilterGroup>?>(null)
    }

    val onNavigateUp: () -> Unit = {
        if (hasUnsavedChanges) {
            // Prompt whether to discard the customizations unless nothing will be changed.
            showDiscardDialog = true
        } else {
            onDiscard()
        }
    }
    BackHandler(enabled = !isSaving, onBack = onNavigateUp)

    val groupEntries = accounts?.map { account ->
        account to account.groups.map { group ->
            SettingsEntry(
                key = "${account.key}_${group.key}",
                title = group.title?.toString().orEmpty(),
                checked = group.visible,
                onLongClick = if (group.canRemoveSync) {
                    { removeSyncTarget = account to group }
                } else {
                    null
                },
                onClick = { onToggleVisible(account, group) },
            )
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.custom_list_filter),
        onNavigateUp = { if (!isSaving) onNavigateUp() },
        actions = {
            TextButton(onClick = onSave, enabled = accounts != null && !isSaving) {
                Text(stringResource(R.string.menu_custom_filter_save))
            }
        },
    ) {
        if (groupEntries == null || isSaving) {
            item(key = "loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
                    if (isSaving) {
                        Text(
                            text = stringResource(R.string.savingDisplayGroups),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        } else {
            groupEntries.forEach { (account, entries) ->
                val name = account.name?.toString()
                val type = account.type.toString()
                settingsGroup(
                    entries = entries,
                    title = name ?: type,
                    subtitle = type.takeIf { name != null },
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            text = { Text(stringResource(R.string.leave_customize_confirmation_dialog_message)) },
            confirmButton = {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    removeSyncTarget?.let { (account, group) ->
        AlertDialog(
            onDismissRequest = { removeSyncTarget = null },
            title = { Text(group.title?.toString().orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeSyncTarget = null
                        if (group.removeSyncAffectsUngrouped) {
                            // Warn before removing this group when it would cause ungrouped
                            // contacts to stop syncing.
                            removeSyncWarningTarget = account to group
                        } else {
                            onRemoveSync(account, group, false)
                        }
                    },
                ) {
                    Text(stringResource(R.string.menu_sync_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeSyncTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    removeSyncWarningTarget?.let { (account, group) ->
        AlertDialog(
            onDismissRequest = { removeSyncWarningTarget = null },
            title = { Text(stringResource(R.string.menu_sync_remove)) },
            text = {
                Text(
                    stringResource(
                        R.string.display_warn_remove_ungrouped,
                        group.title?.toString().orEmpty(),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeSyncWarningTarget = null
                        // Mark both this group and ungrouped contacts to stop syncing
                        onRemoveSync(account, group, true)
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeSyncWarningTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
