/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Space between two groups of settings entries.
 */
internal val SettingsGroupSpacing = 16.dp

/**
 * A single entry of a settings screen.
 */
class SettingsEntry(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val summary: CharSequence? = null,
    val onClick: () -> Unit,
)

/**
 * Adds a group of entries, shaped as a single segmented block.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.settingsGroup(entries: List<SettingsEntry>) {
    if (entries.isEmpty()) {
        return
    }

    item(key = "spacer_${entries.first().key}") {
        Spacer(Modifier.height(SettingsGroupSpacing - ListItemDefaults.SegmentedGap))
    }
    itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
        SettingsItem(entry = entry, index = index, count = entries.size)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsItem(entry: SettingsEntry, index: Int, count: Int) {
    val summary = entry.summary

    SegmentedListItem(
        onClick = entry.onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        leadingContent = { Icon(entry.icon, contentDescription = null) },
        supportingContent = if (summary != null) {
            { Text(summary.toString()) }
        } else {
            null
        },
    ) {
        Text(entry.title)
    }
}
