/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.android.contacts.R

/**
 * Navigation icon of a top app bar, going back to the previous screen by default.
 */
@Composable
fun NavigateUpButton(
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = stringResource(R.string.back_arrow_content_description),
) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
