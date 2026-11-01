/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.viewmodels

import android.app.Application
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.Groups
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.contacts.ContactSaveService
import com.android.contacts.ContactsUtils
import com.android.contacts.GroupMetaDataLoader
import com.android.contacts.R
import com.android.contacts.activities.GroupActivity
import com.android.contacts.group.GroupMembersUpdater
import com.android.contacts.group.GroupMetaData
import com.android.contacts.group.GroupUtil
import com.android.contacts.logging.ListEvent
import com.android.contacts.logging.Logger
import com.android.contacts.model.GroupMember
import com.android.contacts.model.GroupState
import com.android.contacts.model.account.GoogleAccountType
import com.android.contacts.preference.ContactsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-time events of the group screen, handled by [GroupActivity].
 */
sealed interface GroupEvent {
    /**
     * The group couldn't be loaded, e.g. because it was deleted.
     */
    data object LoadFailed : GroupEvent

    /**
     * Sends an email or a message to some members, through [intent] if any. [warning] is shown
     * when some of the members have no email address or phone number.
     */
    data class SendTo(val intent: Intent?, @StringRes val warning: Int?) : GroupEvent
}

/**
 * View model of the screen showing the members of the group [GroupActivity.EXTRA_GROUP_URI].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val groupUri = checkNotNull(savedStateHandle.get<Uri>(GroupActivity.EXTRA_GROUP_URI)) {
        "Missing group URI"
    }

    private val contentResolver = application.contentResolver
    private val preferences = ContactsPreferences(application)

    private val selectedContactIds = savedStateHandle.getStateFlow(KEY_SELECTED, LongArray(0))
    private val editMode = savedStateHandle.getStateFlow(KEY_EDIT_MODE, false)

    private val _events = Channel<GroupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * The group, reloaded whenever the contacts change.
     */
    private val group = contactsChanges()
        .mapLatest { withContext(Dispatchers.IO) { load() } }
        .onEach { if (it == null) _events.send(GroupEvent.LoadFailed) }
        .filterNotNull()

    /**
     * The group and its members. Null while loading.
     */
    val state = combine(group, selectedContactIds, editMode) { group, selected, editMode ->
        val memberIds = group.members.map { it.contactId }.toSet()
        group.copy(
            // Members removed in the meantime can't stay selected.
            selectedContactIds = selected.filter { it in memberIds }.toSet(),
            // Leave the edit mode once the last member is removed.
            editMode = editMode && group.members.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val metaData get() = state.value?.metaData

    fun toggleSelected(member: GroupMember) {
        if (metaData?.editable != true) {
            return
        }
        val selected = selectedContactIds.value.toMutableSet()
        if (!selected.remove(member.contactId)) {
            selected.add(member.contactId)
        }
        savedStateHandle[KEY_SELECTED] = selected.toLongArray()
    }

    fun clearSelection() {
        savedStateHandle[KEY_SELECTED] = LongArray(0)
    }

    fun setEditMode(editMode: Boolean) {
        clearSelection()
        savedStateHandle[KEY_EDIT_MODE] = editMode
    }

    fun addMembers(contactIds: LongArray) {
        val metaData = metaData ?: return
        if (contactIds.isNotEmpty()) {
            GroupMembersUpdater.addMembers(getApplication<Application>(), contactIds, metaData)
        }
    }

    fun removeMembers(contactIds: LongArray) {
        val metaData = metaData ?: return
        GroupMembersUpdater.removeMembers(getApplication<Application>(), contactIds, metaData)
    }

    fun removeSelectedMembers() {
        val state = state.value ?: return
        val contactIds = state.selectedContactIds.toLongArray()
        Logger.logListEvent(
            ListEvent.ActionType.REMOVE_LABEL,
            ListEvent.ListType.GROUP,
            state.members.size,
            /* clickedIndex */ -1,
            contactIds.size,
        )
        removeMembers(contactIds)
        clearSelection()
    }

    /**
     * Renames the group to [name], which is trimmed like the existing names.
     */
    fun rename(name: String) {
        val metaData = metaData ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName == metaData.groupName?.trim()) {
            return
        }
        val application = getApplication<Application>()
        ContactSaveService.startService(
            application,
            ContactSaveService.createGroupRenameIntent(
                application,
                metaData.groupId,
                trimmedName,
                GroupActivity::class.java,
                GroupUtil.ACTION_UPDATE_GROUP,
            ),
        )
    }

    /**
     * Deletes the group, [ContactSaveService] then broadcasts how to undo it.
     */
    fun delete() {
        val metaData = metaData ?: return
        val application = getApplication<Application>()
        ContactSaveService.startService(
            application,
            ContactSaveService.createGroupDeletionIntent(application, metaData.groupId),
        )
    }

    /**
     * Sends an email or a message, depending on [scheme], to the selected members, or to all of
     * them outside of selection mode.
     */
    fun sendTo(scheme: String, title: String) {
        val state = state.value ?: return
        val contactIds = if (state.selectionMode) {
            state.selectedContactIds.toLongArray()
        } else {
            state.members.map { it.contactId }.toLongArray()
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { createSendToEvent(contactIds, scheme, title) }
                ?.let { _events.send(it) }
        }
    }

    private fun contactsChanges() = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
        send(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun load(): GroupState? {
        if (!GroupUtil.isGroupUri(groupUri)) {
            return null
        }
        val metaData = contentResolver.query(
            groupUri,
            GroupMetaDataLoader.COLUMNS,
            GroupUtil.DEFAULT_SELECTION,
            null,
            GroupUtil.getGroupsSortOrder(),
        )?.use { cursor ->
            if (cursor.moveToFirst()) GroupMetaData(getApplication<Application>(), cursor) else null
        } ?: return null

        return GroupState(
            metaData = metaData,
            members = loadMembers(metaData.groupId),
            existingNames = loadExistingNames(metaData),
            showAccountName = metaData.editable
                    || GoogleAccountType.ACCOUNT_TYPE == metaData.accountType,
            selectedContactIds = emptySet(),
            editMode = false,
        )
    }

    private fun loadMembers(groupId: Long): List<GroupMember> {
        val displayNameColumn =
            if (preferences.displayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
                Data.DISPLAY_NAME_PRIMARY
            } else {
                Data.DISPLAY_NAME_ALTERNATIVE
            }
        val sortOrder = if (preferences.sortOrder == ContactsPreferences.SORT_ORDER_PRIMARY) {
            Contacts.SORT_KEY_PRIMARY
        } else {
            Contacts.SORT_KEY_ALTERNATIVE
        }
        val uri = Data.CONTENT_URI.buildUpon()
            .appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY, Directory.DEFAULT.toString()
            )
            .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
            .build()

        return contentResolver.query(
            uri,
            arrayOf(Data.CONTACT_ID, Data.LOOKUP_KEY, Data.PHOTO_THUMBNAIL_URI, displayNameColumn),
            "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
            sortOrder,
        )?.use { cursor -> readMembers(cursor) }.orEmpty()
    }

    /**
     * Reads the members, once each: a contact has a row for each of its raw contacts in the group.
     */
    private fun readMembers(cursor: Cursor): List<GroupMember> {
        val sections = cursor.extras.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)
        val counts = cursor.extras.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)
        val hasIndex = sections != null && counts != null && sections.size == counts.size

        val contactIds = mutableSetOf<Long>()
        val members = mutableListOf<GroupMember>()
        var sectionIndex = 0
        var sectionEnd = if (hasIndex && counts!!.isNotEmpty()) counts[0] else Int.MAX_VALUE

        while (cursor.moveToNext()) {
            // The index counts the rows of the cursor, before removing the duplicates.
            if (hasIndex) {
                while (cursor.position >= sectionEnd && sectionIndex < counts!!.size - 1) {
                    sectionIndex++
                    sectionEnd += counts[sectionIndex]
                }
            }
            val contactId = cursor.getLong(MEMBER_CONTACT_ID)
            if (!contactIds.add(contactId)) {
                continue
            }
            members.add(
                GroupMember(
                    contactId = contactId,
                    lookupUri = Contacts.getLookupUri(
                        contactId, cursor.getString(MEMBER_LOOKUP_KEY)
                    ),
                    displayName = cursor.getString(MEMBER_DISPLAY_NAME),
                    photoThumbnailUri = cursor.getString(MEMBER_PHOTO_THUMBNAIL_URI)
                        ?.let(Uri::parse),
                    section = if (hasIndex) sections!!.getOrNull(sectionIndex).orEmpty() else "",
                )
            )
        }
        return members
    }

    /**
     * Loads the names of the groups of the account of [metaData]. Empty system groups are left
     * out, as they are hidden from the navigation drawer.
     */
    private fun loadExistingNames(metaData: GroupMetaData): Set<String> {
        val selectionArgs = mutableListOf<String>()
        fun equalTo(column: String, value: String?) = if (value == null) {
            "$column IS NULL"
        } else {
            selectionArgs.add(value)
            "$column=?"
        }
        val selection = listOf(
            equalTo(Groups.ACCOUNT_NAME, metaData.accountName),
            equalTo(Groups.ACCOUNT_TYPE, metaData.accountType),
            equalTo(Groups.DATA_SET, metaData.dataSet),
            "${Groups.DELETED}=0",
        ).joinToString(" AND ")

        return contentResolver.query(
            Groups.CONTENT_SUMMARY_URI,
            arrayOf(
                Groups.TITLE,
                Groups.SYSTEM_ID,
                Groups.ACCOUNT_TYPE,
                Groups.SUMMARY_COUNT,
                Groups.GROUP_IS_READ_ONLY,
            ),
            selection,
            selectionArgs.toTypedArray(),
            null,
        )?.use { cursor ->
            val projection = GroupUtil.GroupsProjection(cursor)
            buildSet {
                while (cursor.moveToNext()) {
                    if (!projection.isEmptyFFCGroup(cursor)) {
                        projection.getTitle(cursor)?.trim()?.let(::add)
                    }
                }
            }
        }.orEmpty()
    }

    /**
     * Looks up the email addresses or phone numbers of [contactIds]. If one of the contacts has
     * many of them but no default one, they are picked by the user first.
     */
    private fun createSendToEvent(
        contactIds: LongArray,
        scheme: String,
        title: String,
    ): GroupEvent.SendTo? {
        if (contactIds.isEmpty()) {
            return null
        }
        val isEmail = scheme == ContactsUtils.SCHEME_MAILTO

        class ContactItems {
            var firstItemId: Long? = null
            var primaryItemId: Long? = null
            var count = 0

            val hasDefaultItem get() = primaryItemId != null || count == 1
            val defaultItemId get() = primaryItemId ?: firstItemId
        }

        val contacts = mutableMapOf<Long, ContactItems>()
        val items = mutableListOf<String>()
        contentResolver.query(
            Data.CONTENT_URI,
            if (isEmail) GroupUtil.Query.EMAIL_PROJECTION else GroupUtil.Query.PHONE_PROJECTION,
            (if (isEmail) GroupUtil.Query.EMAIL_SELECTION else GroupUtil.Query.PHONE_SELECTION) +
                    " AND ${Data.CONTACT_ID} IN (${GroupUtil.convertArrayToString(contactIds)})",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val data = cursor.getString(GroupUtil.Query.DATA1)
                if (data.isNullOrEmpty()) {
                    continue
                }
                val contact = contacts.getOrPut(cursor.getLong(GroupUtil.Query.CONTACT_ID)) {
                    ContactItems()
                }
                val itemId = cursor.getLong(GroupUtil.Query.ITEM_ID)
                if (contact.firstItemId == null) {
                    contact.firstItemId = itemId
                }
                if (cursor.getInt(GroupUtil.Query.PRIMARY) != 0) {
                    contact.primaryItemId = itemId
                }
                contact.count++
                items.add(data)
            }
        } ?: return null

        if (contacts.values.any { !it.hasDefaultItem }) {
            return GroupEvent.SendTo(
                GroupUtil.createSendToSelectionPickerIntent(
                    getApplication<Application>(),
                    contactIds,
                    contacts.values.mapNotNull { it.defaultItemId }.toLongArray(),
                    scheme,
                    title,
                ),
                warning = null,
            )
        }

        val warning = if (items.isEmpty() || contacts.size < contactIds.size) {
            if (isEmail) {
                R.string.groupSomeContactsNoEmailsToast
            } else {
                R.string.groupSomeContactsNoPhonesToast
            }
        } else {
            null
        }
        val intent = items.takeIf { it.isNotEmpty() }?.let {
            Intent.createChooser(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts(scheme, it.joinToString(","), null)),
                title,
            )
        }
        return GroupEvent.SendTo(intent, warning)
    }

    companion object {
        private const val KEY_SELECTED = "selectedContactIds"
        private const val KEY_EDIT_MODE = "editMode"

        private const val MEMBER_CONTACT_ID = 0
        private const val MEMBER_LOOKUP_KEY = 1
        private const val MEMBER_PHOTO_THUMBNAIL_URI = 2
        private const val MEMBER_DISPLAY_NAME = 3
    }
}
