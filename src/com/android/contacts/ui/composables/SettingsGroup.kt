/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
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
 * a radio button instead of [icon]. If [checked] isn't null, the entry is toggled on click, shown
 * with a checkbox.
 */
class SettingsEntry(
    val key: String,
    val title: String,
    val icon: ImageVector? = null,
    val summary: CharSequence? = null,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * Adds a group of entries, shaped as a single segmented block, below an optional header.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.settingsGroup(
    entries: List<SettingsEntry>,
    title: String? = null,
    subtitle: String? = null,
) {
    if (entries.isEmpty()) {
        return
    }

    val groupKey = entries.first().key
    item(key = "spacer_$groupKey") {
        Spacer(Modifier.height(SettingsGroupSpacing - ListItemDefaults.SegmentedGap))
    }
    if (title != null) {
        item(key = "header_$groupKey") {
            SettingsGroupHeader(title = title, subtitle = subtitle)
        }
    }
    itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
        SettingsItem(entry = entry, index = index, count = entries.size)
    }
}

@Composable
private fun SettingsGroupHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsItem(entry: SettingsEntry, index: Int, count: Int) {
    val icon = entry.icon
    val summary = entry.summary
    val selected = entry.selected
    val checked = entry.checked

    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val containerColor = MaterialTheme.colorScheme.surfaceBright
    val supportingContent: (@Composable () -> Unit)? = if (summary != null) {
        { Text(summary.toString()) }
    } else {
        null
    }

    when {
        selected != null -> SegmentedListItem(
            selected = selected,
            onClick = entry.onClick,
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            supportingContent = supportingContent,
            onLongClick = entry.onLongClick,
        ) {
            Text(entry.title)
        }

        checked != null -> SegmentedListItem(
            checked = checked,
            onCheckedChange = { entry.onClick() },
            shapes = shapes,
            // Many entries can be checked, only the checkbox shows the state.
            colors = ListItemDefaults.segmentedColors(
                containerColor = containerColor,
                selectedContainerColor = containerColor,
            ),
            trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
            supportingContent = supportingContent,
            onLongClick = entry.onLongClick,
        ) {
            Text(entry.title)
        }

        else -> SegmentedListItem(
            onClick = entry.onClick,
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
            leadingContent = if (icon != null) {
                { Icon(icon, contentDescription = null) }
            } else {
                null
            },
            supportingContent = supportingContent,
            onLongClick = entry.onLongClick,
        ) {
            Text(entry.title)
        }
    }
}
