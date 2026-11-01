/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap

/**
 * Returns a painter drawing [drawable], e.g. the icon of an account, or null if it is null.
 */
@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter? = remember(drawable) {
    drawable?.let {
        val size = it.intrinsicWidth.takeIf { width -> width > 0 } ?: DEFAULT_ICON_SIZE_PX
        BitmapPainter(it.toBitmap(width = size, height = size).asImageBitmap())
    }
}

private const val DEFAULT_ICON_SIZE_PX = 96
