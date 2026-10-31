/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

/**
 * Scaffold of a settings screen: a large flexible top app bar that collapses on scroll, above a
 * list of [settingsGroup].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScaffold(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { NavigateUpButton(onClick = onNavigateUp) },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    scrolledContainerColor = containerColor,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + SettingsGroupSpacing,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content,
        )
    }
}
