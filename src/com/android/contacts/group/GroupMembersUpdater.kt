/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.contacts.group

import android.content.Context
import android.provider.ContactsContract.RawContacts
import android.widget.Toast
import com.android.contacts.ContactSaveService
import com.android.contacts.ContactsApplication
import com.android.contacts.R
import com.android.contacts.activities.PeopleActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds or removes contacts from a group through [ContactSaveService]. */
object GroupMembersUpdater {

    @JvmStatic
    fun addMembers(context: Context, contactIds: LongArray, group: GroupMetaData) =
        update(context, contactIds, group, add = true)

    @JvmStatic
    fun removeMembers(context: Context, contactIds: LongArray, group: GroupMetaData) =
        update(context, contactIds, group, add = false)

    private fun update(
        context: Context,
        contactIds: LongArray,
        group: GroupMetaData,
        add: Boolean,
    ) {
        val appContext = context.applicationContext
        // Not tied to the lifecycle of the screen: the change must be saved even if it is closed.
        (appContext as ContactsApplication).applicationScope.launch {
            val rawContactIds = getRawContactIds(appContext, contactIds, group)
            withContext(Dispatchers.Main) {
                if (rawContactIds.isEmpty()) {
                    Toast.makeText(appContext, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT)
                        .show()
                    return@withContext
                }
                appContext.startService(
                    ContactSaveService.createGroupUpdateIntent(
                        appContext,
                        group.groupId,
                        /* newLabel */ null,
                        if (add) rawContactIds else null,
                        if (add) null else rawContactIds,
                        PeopleActivity::class.java,
                        if (add) {
                            GroupUtil.ACTION_ADD_TO_GROUP
                        } else {
                            GroupUtil.ACTION_REMOVE_FROM_GROUP
                        },
                    )
                )
            }
        }
    }

    // TODO(wjang): prune raw contacts that are already in the group; ContactSaveService will
    // log a warning if the raw contact is already a member and keep going but it is not ideal.
    private fun getRawContactIds(
        context: Context,
        contactIds: LongArray,
        group: GroupMetaData,
    ): LongArray {
        val builder = RawContacts.CONTENT_URI.buildUpon()
        // null account names are not valid, see ContactsProvider2#appendAccountFromParameter
        if (group.accountName != null) {
            builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, group.accountName)
            builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, group.accountType)
        }
        if (group.dataSet != null) {
            builder.appendQueryParameter(RawContacts.DATA_SET, group.dataSet)
        }
        val selection = contactIds.joinToString(" OR ") { "${RawContacts.CONTACT_ID}=?" }
        val selectionArgs = contactIds.map { it.toString() }.toTypedArray()

        return context.contentResolver
            .query(builder.build(), arrayOf(RawContacts._ID), selection, selectionArgs, null)
            ?.use { cursor -> LongArray(cursor.count) { cursor.moveToNext(); cursor.getLong(0) } }
            ?: LongArray(0)
    }
}
