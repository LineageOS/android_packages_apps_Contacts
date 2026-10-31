/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.model

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.Settings
import com.android.contacts.R
import com.android.contacts.util.LocalizedNameResolver

/**
 * Entry holding any changes to [Groups] or [Settings] rows, such as [Groups.SHOULD_SYNC] or
 * [Groups.GROUP_VISIBLE].
 *
 * The [Settings] row stands for the contacts of an account that aren't in any group.
 */
class GroupDelta private constructor() : ValuesDelta() {
    /**
     * Whether this entry is the [Settings] row of the account, rather than a [Groups] row.
     */
    var isUngrouped = false
        private set

    private var accountHasGroups = false

    val shouldSync: Boolean
        get() = getAsInteger(
            if (isUngrouped) Settings.SHOULD_SYNC else Groups.SHOULD_SYNC,
            DEFAULT_SHOULD_SYNC,
        ) != 0

    val visible: Boolean
        get() = getAsInteger(
            if (isUngrouped) Settings.UNGROUPED_VISIBLE else Groups.GROUP_VISIBLE,
            DEFAULT_VISIBLE,
        ) != 0

    fun putShouldSync(shouldSync: Boolean) {
        put(if (isUngrouped) Settings.SHOULD_SYNC else Groups.SHOULD_SYNC, if (shouldSync) 1 else 0)
    }

    fun putVisible(visible: Boolean) {
        put(
            if (isUngrouped) Settings.UNGROUPED_VISIBLE else Groups.GROUP_VISIBLE,
            if (visible) 1 else 0,
        )
    }

    override fun beforeExists() = mBefore != null

    fun getTitle(context: Context): CharSequence? {
        if (isUngrouped) {
            val accountType = (mBefore ?: mAfter).getAsString(Settings.ACCOUNT_TYPE)
            return LocalizedNameResolver.getAllContactsName(context, accountType)
                ?: context.getText(
                    if (accountHasGroups) {
                        R.string.display_ungrouped
                    } else {
                        R.string.display_all_contacts
                    }
                )
        }

        val titleRes = getAsInteger(Groups.TITLE_RES)
        if (titleRes != null && titleRes != 0) {
            val packageName = getAsString(Groups.RES_PACKAGE)
            if (packageName != null) {
                return context.packageManager.getText(packageName, titleRes, null)
            }
        }
        return getAsString(Groups.TITLE)
    }

    /**
     * Build a possible [ContentProviderOperation] to persist any changes to the [Groups] or
     * [Settings] row described by this [GroupDelta].
     */
    fun buildDiff(): ContentProviderOperation? = when {
        isInsert -> {
            // Only allow inserts for Settings
            check(isUngrouped) { "Unexpected diff" }
            mAfter.remove(mIdColumn)
            ContentProviderOperation.newInsert(Settings.CONTENT_URI)
                .withValues(mAfter)
                .build()
        }

        isUpdate -> if (isUngrouped) {
            val accountName = getAsString(Settings.ACCOUNT_NAME)
            val accountType = getAsString(Settings.ACCOUNT_TYPE)
            val dataSet = getAsString(Settings.DATA_SET)
            val accountSelection = "${Settings.ACCOUNT_NAME}=? AND ${Settings.ACCOUNT_TYPE}=?"
            val (selection, selectionArgs) = if (dataSet == null) {
                "$accountSelection AND ${Settings.DATA_SET} IS NULL" to
                        arrayOf(accountName, accountType)
            } else {
                "$accountSelection AND ${Settings.DATA_SET}=?" to
                        arrayOf(accountName, accountType, dataSet)
            }
            ContentProviderOperation.newUpdate(Settings.CONTENT_URI)
                .withSelection(selection, selectionArgs)
                .withValues(mAfter)
                .build()
        } else {
            ContentProviderOperation.newUpdate(Groups.CONTENT_URI.asSyncAdapter())
                .withSelection("${Groups._ID}=$id", null)
                .withValues(mAfter)
                .build()
        }

        else -> null
    }

    companion object {
        private const val DEFAULT_SHOULD_SYNC = 1
        private const val DEFAULT_VISIBLE = 0

        /**
         * Build [GroupDelta] from the [Settings] row for the given [Settings.ACCOUNT_NAME],
         * [Settings.ACCOUNT_TYPE], and [Settings.DATA_SET].
         */
        fun fromSettings(
            resolver: ContentResolver,
            accountName: String?,
            accountType: String?,
            dataSet: String?,
            accountHasGroups: Boolean,
        ): GroupDelta {
            val settingsUri = Settings.CONTENT_URI.buildUpon()
                .appendQueryParameter(Settings.ACCOUNT_NAME, accountName)
                .appendQueryParameter(Settings.ACCOUNT_TYPE, accountType)
            if (dataSet != null) {
                settingsUri.appendQueryParameter(Settings.DATA_SET, dataSet)
            }

            val values = ContentValues().apply {
                put(Settings.ACCOUNT_NAME, accountName)
                put(Settings.ACCOUNT_TYPE, accountType)
                put(Settings.DATA_SET, dataSet)
            }

            resolver.query(
                settingsUri.build(),
                arrayOf(Settings.SHOULD_SYNC, Settings.UNGROUPED_VISIBLE),
                null,
                null,
                null,
            ).use { cursor ->
                val group = if (cursor != null && cursor.moveToFirst()) {
                    // Read existing values when present
                    values.put(Settings.SHOULD_SYNC, cursor.getInt(0))
                    values.put(Settings.UNGROUPED_VISIBLE, cursor.getInt(1))
                    fromBefore(values)
                } else {
                    // Nothing found, so treat as create
                    values.put(Settings.SHOULD_SYNC, DEFAULT_SHOULD_SYNC)
                    values.put(Settings.UNGROUPED_VISIBLE, DEFAULT_VISIBLE)
                    fromAfter(values)
                }
                return group.apply {
                    isUngrouped = true
                    this.accountHasGroups = accountHasGroups
                }
            }
        }

        fun fromBefore(before: ContentValues) = GroupDelta().apply {
            mBefore = before
            mAfter = ContentValues()
        }

        private fun fromAfter(after: ContentValues) = GroupDelta().apply {
            mBefore = null
            mAfter = after
        }

        private fun Uri.asSyncAdapter() = buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
    }
}
