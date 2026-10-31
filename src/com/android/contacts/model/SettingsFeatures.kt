/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

/**
 * Settings entries that are only available on some devices or configurations.
 */
data class SettingsFeatures(
    val sortOrder: Boolean,
    val displayOrder: Boolean,
    val phoneticNameDisplay: Boolean,
    val export: Boolean,
    val blockedNumbers: Boolean,
    val about: Boolean,
)
