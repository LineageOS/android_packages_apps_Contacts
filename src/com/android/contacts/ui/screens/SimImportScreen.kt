/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.contacts.R
import com.android.contacts.model.SimImportAccount
import com.android.contacts.model.SimImportContact
import com.android.contacts.model.SimImportState
import com.android.contacts.ui.composables.ChoiceDialog
import com.android.contacts.ui.composables.NavigateUpButton
import com.android.contacts.ui.composables.SettingsChoice
import com.android.contacts.ui.composables.SettingsEntry
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.settingsGroup
import kotlinx.coroutines.launch

/**
 * Full screen dialog importing the contacts of a SIM card into an account.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimImportScreen(
    state: SimImportState?,
    snackbarHostState: SnackbarHostState,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onAccountSelected: (SimImportAccount) -> Unit,
    onToggleSelected: (SimImportContact) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }

    val importableCount = state?.importableCount ?: 0
    val accountCount = state?.accounts?.size ?: 0
    val existsMessage = stringResource(R.string.sim_import_contact_exists_toast)

    val accountPainter = rememberDrawablePainter(state?.currentAccount?.icon)
    val accountEntries = listOfNotNull(
        state?.currentAccount?.let { account ->
            SettingsEntry(
                key = "account",
                title = account.name.toString(),
                iconPainter = accountPainter,
                summary = account.type,
                onClick = {
                    if (accountCount > 1) {
                        showAccountDialog = true
                    }
                },
            )
        }
    )
    val contactEntries = state?.contacts?.map { contact ->
        if (contact.existsInAccount) {
            SettingsEntry(
                key = "contact_${contact.recordNumber}",
                title = contact.label,
                summary = existsMessage,
                onClick = {
                    coroutineScope.launch { snackbarHostState.showSnackbar(existsMessage) }
                },
            )
        } else {
            SettingsEntry(
                key = "contact_${contact.recordNumber}",
                title = contact.label,
                checked = contact.selected,
                onClick = { onToggleSelected(contact) },
            )
        }
    }

    SettingsScaffold(
        title = if (importableCount == 0) {
            stringResource(R.string.sim_import_title_none_selected)
        } else {
            importableCount.toString()
        },
        onNavigateUp = onCancel,
        navigationIcon = {
            NavigateUpButton(
                onClick = onCancel,
                icon = Icons.Filled.Close,
                contentDescription =
                    stringResource(R.string.sim_import_cancel_content_description),
            )
        },
        largeTopAppBar = false,
        actions = {
            if (importableCount > 0) {
                TextButton(onClick = onImport) {
                    Text(stringResource(R.string.sim_import_button_text))
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) {
        when {
            contactEntries == null -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            contactEntries.isEmpty() -> item(key = "empty") {
                Text(
                    text = stringResource(R.string.sim_import_empty_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                settingsGroup(accountEntries)
                settingsGroup(contactEntries)
            }
        }
    }

    if (showAccountDialog && state != null) {
        ChoiceDialog(
            title = stringResource(R.string.editor_account_selector_title),
            choices = state.accounts.mapIndexed { index, account ->
                SettingsChoice(index, account.name.toString())
            },
            selected = state.accounts.indexOf(state.currentAccount),
            onSelect = { index ->
                onAccountSelected(state.accounts[index])
                showAccountDialog = false
            },
            onDismiss = { showAccountDialog = false },
        )
    }
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable?): Painter? = remember(drawable) {
    drawable?.let {
        val size = it.intrinsicWidth.takeIf { width -> width > 0 } ?: DEFAULT_ICON_SIZE_PX
        BitmapPainter(it.toBitmap(width = size, height = size).asImageBitmap())
    }
}

private const val DEFAULT_ICON_SIZE_PX = 96
