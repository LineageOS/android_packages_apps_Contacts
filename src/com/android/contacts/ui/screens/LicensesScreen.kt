/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.contacts.R
import com.android.contacts.ui.composables.NavigateUpButton

/**
 * Open source licenses screen, showing the HTML page at [url].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    url: String,
    onNavigateUp: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        containerColor = containerColor,
        topBar = {
            // A WebView doesn't dispatch its scroll to Compose, so the app bar can't collapse.
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title_licenses)) },
                navigationIcon = { NavigateUpButton(onClick = onNavigateUp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
            )
        },
    ) { innerPadding ->
        AndroidView(
            factory = { context -> WebView(context).apply { loadUrl(url) } },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            onRelease = WebView::destroy,
        )
    }
}
