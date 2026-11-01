/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.contacts.R
import com.android.contacts.model.ContactListEntry
import com.android.contacts.model.GroupState
import com.android.contacts.ui.composables.GroupNameDialog
import com.android.contacts.ui.composables.NavigateUpButton
import com.android.contacts.ui.composables.SettingsScaffold
import com.android.contacts.ui.composables.contactCount
import com.android.contacts.ui.composables.contactList

/**
 * Shows the members of a group.
 *
 * Long pressing a member of an editable group starts the selection mode, to send a message to
 * some members or to remove them. The edit mode shows a remove button on every member instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupScreen(
    state: GroupState?,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onMemberClick: (ContactListEntry, Int) -> Unit,
    onToggleSelected: (ContactListEntry) -> Unit,
    onClearSelection: () -> Unit,
    onSetEditMode: (Boolean) -> Unit,
    onAddMembers: () -> Unit,
    onRemoveMember: (ContactListEntry) -> Unit,
    onRemoveSelected: () -> Unit,
    onSendEmail: () -> Unit,
    onSendMessage: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val selectionMode = state?.selectionMode == true
    val editMode = state?.editMode == true

    BackHandler(enabled = selectionMode || editMode) {
        if (editMode) onSetEditMode(false) else onClearSelection()
    }

    SettingsScaffold(
        title = when {
            state == null -> ""
            editMode -> stringResource(R.string.title_edit_group)
            selectionMode -> state.selectedContactIds.size.toString()
            else -> state.metaData.groupName.orEmpty()
        },
        onNavigateUp = onNavigateUp,
        navigationIcon = {
            if (selectionMode || editMode) {
                NavigateUpButton(
                    onClick = { if (editMode) onSetEditMode(false) else onClearSelection() },
                    icon = Icons.Filled.Close,
                )
            } else {
                NavigateUpButton(onClick = onNavigateUp)
            }
        },
        // The title changes with the mode and the selection.
        largeTopAppBar = false,
        actions = {
            if (state != null && !editMode) {
                GroupActions(
                    state = state,
                    onAddMembers = onAddMembers,
                    onRemoveSelected = onRemoveSelected,
                    onEdit = { onSetEditMode(true) },
                    onSendEmail = onSendEmail,
                    onSendMessage = onSendMessage,
                    onRename = { showRenameDialog = true },
                    onDelete = {
                        // Deleting an empty group can't lose anything, don't ask.
                        if (state.members.isEmpty()) onDelete() else showDeleteDialog = true
                    },
                )
            }
        },
        snackbarHostState = snackbarHostState,
    ) {
        when {
            state == null -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            state.members.isEmpty() -> item(key = "empty") {
                EmptyGroup(editable = state.metaData.editable, onAddMembers = onAddMembers)
            }

            else -> {
                contactCount(
                    count = state.members.size,
                    accountName = state.metaData.accountName.takeIf { state.showAccountName },
                )
                contactList(
                    contacts = state.members,
                    selectedContactIds = state.selectedContactIds,
                    onClick = { member, position ->
                        if (selectionMode) {
                            onToggleSelected(member)
                        } else {
                            onMemberClick(member, position)
                        }
                    },
                    // Only the members of editable groups can be selected, to be removed.
                    onLongClick = onToggleSelected.takeIf {
                        state.metaData.editable && !editMode
                    },
                    trailingContent = if (editMode) {
                        { member ->
                            IconButton(onClick = { onRemoveMember(member) }) {
                                Icon(
                                    Icons.Outlined.RemoveCircleOutline,
                                    stringResource(R.string.description_delete_contact),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (showRenameDialog && state != null) {
        GroupNameDialog(
            title = stringResource(R.string.group_name_dialog_update_title),
            name = state.metaData.groupName.orEmpty(),
            existingNames = state.existingNames,
            onConfirm = {
                onRename(it)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog && state != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Text(
                    stringResource(
                        R.string.delete_group_dialog_message,
                        state.metaData.groupName.orEmpty(),
                    )
                )
            },
        )
    }
}

@Composable
private fun GroupActions(
    state: GroupState,
    onAddMembers: () -> Unit,
    onRemoveSelected: () -> Unit,
    onEdit: () -> Unit,
    onSendEmail: () -> Unit,
    onSendMessage: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val metaData = state.metaData
    val selectionMode = state.selectionMode
    val hasMembers = state.members.isNotEmpty()

    if (metaData.editable && !selectionMode) {
        IconButton(onClick = onAddMembers) {
            Icon(Icons.Filled.PersonAdd, stringResource(R.string.menu_addToGroup))
        }
    }
    if (metaData.editable && selectionMode) {
        IconButton(onClick = onRemoveSelected) {
            Icon(Icons.Filled.PersonRemove, stringResource(R.string.menu_removeFromGroup))
        }
    }

    val menuItems = buildList<Pair<Int, () -> Unit>> {
        if (hasMembers) {
            add(R.string.menu_sendEmailOption to onSendEmail)
            add(R.string.menu_sendMessageOption to onSendMessage)
        }
        if (metaData.editable && hasMembers && !selectionMode) {
            add(R.string.menu_editGroup to onEdit)
        }
        if (!metaData.readOnly && !selectionMode) {
            add(R.string.menu_renameGroup to onRename)
            add(R.string.menu_deleteGroup to onDelete)
        }
    }
    if (menuItems.isEmpty()) {
        return
    }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            menuItems.forEach { (title, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(title)) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyGroup(editable: Boolean, onAddMembers: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painter = painterResource(R.drawable.label_empty), contentDescription = null)
        Text(
            text = stringResource(R.string.emptyGroup),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (editable) {
            FilledTonalButton(onClick = onAddMembers) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.menu_addContactsToGroup))
            }
        }
    }
}
