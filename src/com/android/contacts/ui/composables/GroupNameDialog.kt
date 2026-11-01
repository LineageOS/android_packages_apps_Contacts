/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import com.android.contacts.R

/**
 * Asks for the name of a group, initially [name]. Names are trimmed, and must not be in
 * [existingNames], the names of the other groups of the same account.
 */
@Composable
fun GroupNameDialog(
    title: String,
    name: String,
    existingNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxLength = integerResource(R.integer.group_name_max_length)
    var newName by rememberSaveable { mutableStateOf(name.take(maxLength)) }
    val focusRequester = remember { FocusRequester() }

    val trimmedName = newName.trim()
    val unchanged = trimmedName == name.trim()
    val exists = !unchanged && trimmedName in existingNames

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (unchanged) onDismiss() else onConfirm(trimmedName) },
                enabled = trimmedName.isNotEmpty() && !exists,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(maxLength) },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.group_name_dialog_hint)) },
                supportingText = if (exists) {
                    { Text(stringResource(R.string.groupExistsErrorMessage)) }
                } else {
                    null
                },
                isError = exists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
