/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import android.icu.text.MessageFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.contacts.R
import com.android.contacts.model.ContactListEntry
import java.util.Locale

/**
 * Adds the number of contacts of a list, along with the name of their account if not null.
 */
fun LazyListScope.contactCount(count: Int, accountName: String?) {
    item(key = "contact_count") {
        val pattern = stringResource(
            if (accountName != null) {
                R.string.contacts_count_with_account
            } else {
                R.string.contacts_count
            }
        )
        val text = remember(pattern, count, accountName) {
            MessageFormat(pattern, Locale.getDefault()).format(
                mapOf("count" to count, "account" to accountName.orEmpty())
            ).trim()
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Adds [contacts], in a segmented block below a header for each of their sections, starting with
 * the favorites.
 *
 * The list is in selection mode when [selectedContactIds] isn't empty: every contact then shows
 * a checkbox instead of [trailingContent]. [onClick] gets the position of the contact in
 * [contacts].
 */
fun LazyListScope.contactList(
    contacts: List<ContactListEntry>,
    selectedContactIds: Set<Long>,
    onClick: (ContactListEntry, Int) -> Unit,
    onLongClick: ((ContactListEntry) -> Unit)? = null,
    trailingContent: (@Composable (ContactListEntry) -> Unit)? = null,
) {
    var position = 0
    contacts.groupBy { if (it.favorite) FAVORITES_KEY else it.section }
        .forEach { (sectionKey, sectionContacts) ->
            contactSection(
                sectionKey = sectionKey,
                contacts = sectionContacts,
                firstPosition = position,
                selectedContactIds = selectedContactIds,
                onClick = onClick,
                onLongClick = onLongClick,
                trailingContent = trailingContent,
            )
            position += sectionContacts.size
        }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.contactSection(
    sectionKey: String,
    contacts: List<ContactListEntry>,
    firstPosition: Int,
    selectedContactIds: Set<Long>,
    onClick: (ContactListEntry, Int) -> Unit,
    onLongClick: ((ContactListEntry) -> Unit)?,
    trailingContent: (@Composable (ContactListEntry) -> Unit)?,
) {
    val selectionMode = selectedContactIds.isNotEmpty()

    item(key = "spacer_$sectionKey") {
        Spacer(Modifier.height(SettingsGroupSpacing - ListItemDefaults.SegmentedGap))
    }
    if (sectionKey.isNotEmpty()) {
        item(key = "section_$sectionKey") {
            SectionHeader(sectionKey)
        }
    }
    // Favorites are also shown in their own section, the keys include the section.
    itemsIndexed(
        contacts,
        key = { _, contact -> "${sectionKey}_${contact.contactId}" },
    ) { index, contact ->
        val name = contact.displayName?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.missing_name)

        SegmentedListItem(
            onClick = { onClick(contact, firstPosition + index) },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = contacts.size),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            ),
            leadingContent = {
                ContactAvatar(name = contact.displayName, photoUri = contact.photoThumbnailUri)
            },
            trailingContent = when {
                selectionMode -> {
                    {
                        Checkbox(
                            checked = contact.contactId in selectedContactIds,
                            onCheckedChange = null,
                        )
                    }
                }

                trailingContent != null -> {
                    { trailingContent(contact) }
                }

                else -> null
            },
            onLongClick = onLongClick?.let { longClick -> { longClick(contact) } },
        ) {
            Text(name)
        }
    }
}

@Composable
private fun SectionHeader(sectionKey: String) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sectionKey == FAVORITES_KEY) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (sectionKey == FAVORITES_KEY) {
                stringResource(R.string.contactsFavoritesLabel)
            } else {
                sectionKey
            },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * The key of the favorites section, which can't be the title of an address book index section.
 */
private const val FAVORITES_KEY = "favorites"
