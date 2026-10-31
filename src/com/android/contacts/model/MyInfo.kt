/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

/**
 * The user profile, as shown in the "My info" settings entry.
 */
sealed interface MyInfo {
    /**
     * The profile is being loaded.
     */
    data object Loading : MyInfo

    /**
     * The user didn't set up a profile yet.
     */
    data object None : MyInfo

    /**
     * The user profile.
     */
    data class Existing(val contactId: Long, val displayName: CharSequence) : MyInfo
}
