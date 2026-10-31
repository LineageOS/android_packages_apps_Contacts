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
import androidx.compose.material3.RadioButton
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
 *
 * If [selected] isn't null, the entry is one of the options of a single choice group, shown with
 * a radio button instead of [icon].
 */
class SettingsEntry(
    val key: String,
    val title: String,
    val icon: ImageVector? = null,
    val summary: CharSequence? = null,
    val selected: Boolean? = null,
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
    val icon = entry.icon
    val summary = entry.summary
    val selected = entry.selected

    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    )
    val supportingContent: (@Composable () -> Unit)? = if (summary != null) {
        { Text(summary.toString()) }
    } else {
        null
    }

    if (selected != null) {
        SegmentedListItem(
            selected = selected,
            onClick = entry.onClick,
            shapes = shapes,
            colors = colors,
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            supportingContent = supportingContent,
        ) {
            Text(entry.title)
        }
    } else {
        SegmentedListItem(
            onClick = entry.onClick,
            shapes = shapes,
            colors = colors,
            leadingContent = if (icon != null) {
                { Icon(icon, contentDescription = null) }
            } else {
                null
            },
            supportingContent = supportingContent,
        ) {
            Text(entry.title)
        }
    }
}
