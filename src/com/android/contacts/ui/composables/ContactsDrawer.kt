/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.contacts.R
import com.android.contacts.list.ContactListFilter
import com.android.contacts.model.DrawerAccount
import com.android.contacts.model.DrawerGroup
import com.android.contacts.model.DrawerState

/**
 * Content of the navigation drawer of the main screen: the profile of the user, all the
 * contacts, the groups, the accounts, then the emergency information and the settings.
 *
 * [currentFilter] is the filter of the contact list, to highlight its entry.
 */
@Composable
fun ContactsDrawer(
    state: DrawerState?,
    currentFilter: ContactListFilter?,
    onProfileClick: () -> Unit,
    onAllContactsClick: () -> Unit,
    onGroupClick: (DrawerGroup) -> Unit,
    onCreateGroupClick: () -> Unit,
    onAccountClick: (DrawerAccount) -> Unit,
    onEmergencyInfoClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            state?.profile?.let { profile ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(stringResource(R.string.settings_my_info_title))
                            Text(
                                text = profile.displayName
                                    ?: stringResource(R.string.set_up_profile),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    selected = false,
                    onClick = onProfileClick,
                    modifier = DrawerItemModifier,
                    icon = {
                        ContactAvatar(
                            name = profile.displayName,
                            photoUri = profile.photoUri,
                            size = 32.dp,
                        )
                    },
                )
                DrawerDivider()
            }

            DrawerItem(
                label = stringResource(R.string.contactsList),
                icon = Icons.Outlined.AccountCircle,
                selected = currentFilter?.isContactsFilterType == true,
                onClick = onAllContactsClick,
            )

            if (state != null && (state.canCreateGroup || state.groups.isNotEmpty())) {
                DrawerHeader(stringResource(R.string.menu_title_groups))
                state.groups.forEach { group ->
                    val description = stringResource(R.string.navigation_drawer_label, group.title)
                    DrawerItem(
                        label = group.title,
                        icon = Icons.AutoMirrored.Outlined.Label,
                        selected = false,
                        onClick = { onGroupClick(group) },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
                if (state.canCreateGroup) {
                    DrawerItem(
                        label = stringResource(R.string.menu_new_group_action_bar),
                        icon = Icons.Outlined.Add,
                        selected = false,
                        onClick = onCreateGroupClick,
                    )
                }
            }

            val accounts = state?.accounts.orEmpty()
            if (accounts.isNotEmpty()) {
                DrawerHeader(stringResource(R.string.menu_title_filters))
                accounts.forEach { account ->
                    val painter = rememberDrawablePainter(account.icon)
                    NavigationDrawerItem(
                        label = { Text(account.label) },
                        selected = account.filter == currentFilter,
                        onClick = { onAccountClick(account) },
                        modifier = DrawerItemModifier.semantics {
                            contentDescription = account.contentDescription
                        },
                        icon = painter?.let {
                            { Icon(it, contentDescription = null, tint = Color.Unspecified) }
                        },
                    )
                }
            }

            if (state?.emergencyInfoAvailable == true) {
                DrawerDivider()
                DrawerItem(
                    label = stringResource(R.string.menu_emergency_information_txt),
                    icon = Icons.Outlined.MedicalInformation,
                    selected = false,
                    onClick = onEmergencyInfoClick,
                )
            }

            DrawerDivider()
            DrawerItem(
                label = stringResource(R.string.menu_settings),
                icon = Icons.Outlined.Settings,
                selected = false,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = DrawerItemModifier.then(modifier),
        icon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun DrawerHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
}

private val DrawerItemModifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
