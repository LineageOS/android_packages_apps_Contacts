/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.contacts.R
import com.android.contacts.model.ContactListEntry
import com.android.contacts.model.ContactsDeletion
import com.android.contacts.model.ContactsListEmptyState
import com.android.contacts.model.ContactsListHeader
import com.android.contacts.model.ContactsListState
import com.android.contacts.model.ContactsSearchState
import com.android.contacts.model.ContactsSyncState
import com.android.contacts.model.ContactsUnavailable
import com.android.contacts.model.DirectorySearchResults
import com.android.contacts.ui.composables.NavigateUpButton
import com.android.contacts.ui.composables.SettingsGroupSpacing
import com.android.contacts.ui.composables.contactCount
import com.android.contacts.ui.composables.contactList
import com.android.contacts.util.SyncUtil
import kotlinx.coroutines.launch

/**
 * The main screen: the contacts of the current filter, with a navigation drawer.
 *
 * The screen has three modes: the contact list, the search, when [searchQuery] isn't null, and
 * the selection, when [selectedContactIds] isn't empty.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsListScreen(
    state: ContactsListState?,
    searchQuery: String?,
    searchState: ContactsSearchState?,
    selectedContactIds: Set<Long>,
    syncState: ContactsSyncState?,
    unavailable: ContactsUnavailable?,
    deletion: ContactsDeletion?,
    enableAutoSyncConfirmation: Boolean,
    snackbarHostState: SnackbarHostState,
    drawerContent: @Composable (closeDrawer: () -> Unit) -> Unit,
    onContactClick: (ContactListEntry, Int) -> Unit,
    onToggleSelected: (ContactListEntry) -> Unit,
    onClearSelection: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStopSearch: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onJoin: () -> Unit,
    onConfirmDeletion: () -> Unit,
    onCancelDeletion: () -> Unit,
    onAddContact: () -> Unit,
    onRefresh: () -> Unit,
    onTurnSyncOn: () -> Unit,
    onDismissSyncOff: () -> Unit,
    onConfirmEnableAutoSync: () -> Unit,
    onCancelEnableAutoSync: () -> Unit,
    onAddAccount: () -> Unit,
    onImportContacts: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val searchMode = searchQuery != null
    val selectionMode = selectedContactIds.isNotEmpty()

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && (searchMode || selectionMode)) {
        if (selectionMode) onClearSelection() else onStopSearch()
    }

    ModalNavigationDrawer(
        drawerContent = {
            drawerContent { coroutineScope.launch { drawerState.close() } }
        },
        drawerState = drawerState,
        gesturesEnabled = !searchMode && !selectionMode,
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val containerColor = MaterialTheme.colorScheme.surfaceContainer

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = containerColor,
            topBar = {
                when {
                    selectionMode -> SelectionTopAppBar(
                        count = selectedContactIds.size,
                        onClearSelection = onClearSelection,
                        onShare = onShare,
                        onDelete = onDelete,
                        onJoin = onJoin,
                    )

                    searchMode -> SearchTopAppBar(
                        query = searchQuery.orEmpty(),
                        onQueryChange = onSearchQueryChange,
                        onStopSearch = onStopSearch,
                    )

                    else -> TopAppBar(
                        title = { Text(state?.title.orEmpty()) },
                        navigationIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    Icons.Filled.Menu,
                                    stringResource(R.string.navigation_drawer_open),
                                )
                            }
                        },
                        actions = {
                            if (unavailable == null) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        stringResource(R.string.hint_findContacts),
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = containerColor,
                            scrolledContainerColor = containerColor,
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            floatingActionButton = {
                if (!searchMode && !selectionMode && unavailable == null) {
                    FloatingActionButton(onClick = onAddContact) {
                        Icon(
                            Icons.Filled.PersonAdd,
                            stringResource(R.string.action_menu_add_new_contact_button),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            val contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + SettingsGroupSpacing,
            )

            when {
                unavailable == ContactsUnavailable.BUSY || (state == null && !searchMode) ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }

                unavailable == ContactsUnavailable.NO_ACCOUNT -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    EmptyContacts(
                        image = R.drawable.contacts_no_account_empty,
                        message = stringResource(R.string.noContacts),
                    ) {
                        FilledTonalButton(onClick = onAddAccount) {
                            Text(stringResource(R.string.contacts_unavailable_add_account))
                        }
                        FilledTonalButton(onClick = onImportContacts) {
                            Text(stringResource(R.string.contacts_unavailable_import_contacts))
                        }
                    }
                }

                searchMode -> LazyColumn(
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    searchResults(
                        searchState = searchState,
                        selectedContactIds = selectedContactIds,
                        onContactClick = { contact, position ->
                            if (selectionMode) {
                                onToggleSelected(contact)
                            } else {
                                onContactClick(contact, position)
                            }
                        },
                        onLongClick = onToggleSelected,
                    )
                }

                state != null -> PullToRefreshBox(
                    isRefreshing = syncState?.refreshing == true,
                    onRefresh = onRefresh,
                    enabled = syncState?.canRefresh == true && !selectionMode,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        contacts(
                            state = state,
                            syncOffReason = syncState?.syncOffReason,
                            selectedContactIds = selectedContactIds,
                            onContactClick = { contact, position ->
                                if (selectionMode) {
                                    onToggleSelected(contact)
                                } else {
                                    onContactClick(contact, position)
                                }
                            },
                            onLongClick = onToggleSelected,
                            onAddContact = onAddContact,
                            onTurnSyncOn = onTurnSyncOn,
                            onDismissSyncOff = onDismissSyncOff,
                        )
                    }
                }
            }
        }
    }

    if (deletion != null) {
        AlertDialog(
            onDismissRequest = onCancelDeletion,
            confirmButton = {
                TextButton(onClick = onConfirmDeletion) {
                    Text(stringResource(deletion.confirmButton))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDeletion) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { Text(stringResource(deletion.message)) },
        )
    }

    if (enableAutoSyncConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelEnableAutoSync,
            confirmButton = {
                TextButton(onClick = onConfirmEnableAutoSync) {
                    Text(stringResource(R.string.turn_auto_sync_on_dialog_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelEnableAutoSync) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.turn_auto_sync_on_dialog_title)) },
            text = { Text(stringResource(R.string.turn_auto_sync_on_dialog_body)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    count: Int,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onJoin: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(count.toString()) },
        navigationIcon = {
            NavigateUpButton(
                onClick = onClearSelection,
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_menu_back_from_edit_select),
            )
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, stringResource(R.string.menu_share))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.menu_deleteContact))
            }
            // Contacts are linked by two at least.
            if (count > 1) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_joinAggregate)) },
                            onClick = {
                                showMenu = false
                                onJoin()
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onStopSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.hint_findContacts)) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                stringResource(R.string.description_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            )
        },
        navigationIcon = {
            NavigateUpButton(
                onClick = onStopSearch,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_menu_back_from_search),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Adds the sync off alert, the header, then the contacts of the list or why it is empty.
 */
private fun LazyListScope.contacts(
    state: ContactsListState,
    syncOffReason: Int?,
    selectedContactIds: Set<Long>,
    onContactClick: (ContactListEntry, Int) -> Unit,
    onLongClick: (ContactListEntry) -> Unit,
    onAddContact: () -> Unit,
    onTurnSyncOn: () -> Unit,
    onDismissSyncOff: () -> Unit,
) {
    if (syncOffReason != null) {
        item(key = "sync_off") {
            SyncOffAlert(
                reason = syncOffReason,
                onTurnSyncOn = onTurnSyncOn,
                onDismiss = onDismissSyncOff,
            )
        }
    }

    when (val header = state.header) {
        ContactsListHeader.None -> Unit
        ContactsListHeader.CustomFilter -> item(key = "custom_filter") {
            Text(
                text = stringResource(R.string.listCustomView),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is ContactsListHeader.Count -> contactCount(header.count, header.accountName)
    }

    when (state.emptyState) {
        ContactsListEmptyState.NO_CONTACTS -> item(key = "empty") {
            EmptyContacts(
                image = R.drawable.home_empty,
                message = stringResource(R.string.emptyMainList),
            ) {
                FilledTonalButton(onClick = onAddContact) {
                    Text(stringResource(R.string.menu_addToGroup))
                }
            }
        }

        ContactsListEmptyState.EMPTY_ACCOUNT -> item(key = "empty") {
            EmptyContacts(
                image = R.drawable.accounts_empty,
                message = stringResource(R.string.emptyAccount),
            ) {
                FilledTonalButton(onClick = onAddContact) {
                    Text(stringResource(R.string.menu_addToGroup))
                }
            }
        }

        null -> contactList(
            contacts = state.contacts,
            selectedContactIds = selectedContactIds,
            onClick = onContactClick,
            onLongClick = onLongClick,
        )
    }
}

/**
 * Adds the contacts found in each directory, below the name of the remote ones.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.searchResults(
    searchState: ContactsSearchState?,
    selectedContactIds: Set<Long>,
    onContactClick: (ContactListEntry, Int) -> Unit,
    onLongClick: (ContactListEntry) -> Unit,
) {
    if (searchState == null || searchState.query.isBlank()) {
        return
    }

    var position = 0
    searchState.results.forEach { results ->
        val directoryId = results.directory.id
        val contacts = results.contacts
        if (results.directory.label != null && contacts?.isEmpty() != true) {
            item(key = "directory_$directoryId") {
                DirectoryHeader(results)
            }
        }
        if (contacts != null) {
            val firstPosition = position
            contactList(
                contacts = contacts,
                selectedContactIds = selectedContactIds,
                onClick = { contact, index -> onContactClick(contact, firstPosition + index) },
                // Only the contacts of the device can be selected.
                onLongClick = onLongClick.takeIf { directoryId == 0L },
                keyPrefix = "directory_${directoryId}_",
            )
            position += contacts.size
        }
    }

    when {
        searchState.searching -> item(key = "searching") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }

        searchState.empty -> item(key = "no_results") {
            Text(
                text = stringResource(R.string.listFoundAllContactsZero),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DirectoryHeader(results: DirectorySearchResults) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = SettingsGroupSpacing),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = results.directory.label.orEmpty(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        results.directory.name?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun SyncOffAlert(reason: Int, onTurnSyncOn: () -> Unit, onDismiss: () -> Unit) {
    Card(
        onClick = onTurnSyncOn,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SyncDisabled, contentDescription = null)
            Text(
                text = stringResource(
                    if (reason == SyncUtil.SYNC_SETTING_GLOBAL_SYNC_OFF) {
                        R.string.auto_sync_off
                    } else {
                        R.string.account_sync_off
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.dismiss_sync_alert))
            }
        }
    }
}

@Composable
private fun EmptyContacts(
    @DrawableRes image: Int,
    message: String,
    buttons: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painter = painterResource(image), contentDescription = null)
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            buttons()
        }
    }
}
