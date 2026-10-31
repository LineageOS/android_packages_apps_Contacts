/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * App screen.
 */
sealed interface Screen {
    /**
     * Main settings screen.
     */
    data object Settings : Screen

    /**
     * About screen.
     */
    data object About : Screen

    companion object {
        /**
         * Saves a navigation back stack across configuration changes and process death.
         */
        val BackStackSaver: Saver<SnapshotStateList<Screen>, Any> = listSaver(
            save = { backStack ->
                backStack.map {
                    when (it) {
                        Settings -> "settings"
                        About -> "about"
                    }
                }
            },
            restore = { keys ->
                mutableStateListOf(
                    *keys.map {
                        when (it) {
                            "about" -> About
                            else -> Settings
                        }
                    }.toTypedArray()
                )
            },
        )
    }
}
