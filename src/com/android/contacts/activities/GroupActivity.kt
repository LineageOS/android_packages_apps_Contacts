/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.android.contacts.ContactSaveService
import com.android.contacts.ContactsUtils
import com.android.contacts.R
import com.android.contacts.group.GroupUtil
import com.android.contacts.list.UiIntentActions
import com.android.contacts.logging.ListEvent
import com.android.contacts.logging.Logger
import com.android.contacts.logging.ScreenEvent
import com.android.contacts.model.GroupStatus
import com.android.contacts.ui.screens.GroupScreen
import com.android.contacts.ui.theme.ContactsTheme
import com.android.contacts.util.ImplicitIntentsUtil
import com.android.contacts.viewmodels.GroupViewModel
import kotlinx.coroutines.launch

/**
 * Shows the members of the group [EXTRA_GROUP_URI], to add or remove some, send them a message,
 * rename or delete the group.
 */
class GroupActivity : ComponentActivity(), ContactSaveService.Listener {
    // View models
    private val groupViewModel: GroupViewModel by viewModels()

    private val snackbarHostState = SnackbarHostState()

    private val pickMembers = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            return@registerForActivityResult
        }
        val contactIds = data.getLongArrayExtra(UiIntentActions.TARGET_CONTACT_IDS_EXTRA_KEY)
            ?: data.getLongExtra(UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, -1)
                .takeIf { it > -1 }
                ?.let { longArrayOf(it) }
            ?: return@registerForActivityResult
        groupViewModel.addMembers(contactIds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Changes to the group are saved by ContactSaveService, which then calls us back.
        ContactSaveService.registerListener(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val state by groupViewModel.state.collectAsStateWithLifecycle()
            val status by groupViewModel.status.collectAsStateWithLifecycle()

            LaunchedEffect(status) {
                when (val current = status) {
                    GroupStatus.Idle -> return@LaunchedEffect

                    GroupStatus.LoadFailed -> {
                        Toast.makeText(
                            this@GroupActivity, R.string.groupLoadErrorToast, Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }

                    is GroupStatus.SendTo -> {
                        // The chooser may cover the screen, a toast stays visible above it.
                        current.warning?.let {
                            Toast.makeText(this@GroupActivity, it, Toast.LENGTH_LONG).show()
                        }
                        current.intent?.let(::startActivity)
                    }
                }
                groupViewModel.onStatusHandled()
            }

            ContactsTheme {
                GroupScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onNavigateUp = ::finish,
                    onMemberClick = { member, position ->
                        Logger.logListEvent(
                            ListEvent.ActionType.CLICK,
                            ListEvent.ListType.GROUP,
                            state?.members?.size ?: 0,
                            position,
                            /* numSelected */ 0,
                        )
                        ImplicitIntentsUtil.startQuickContact(
                            this, member.lookupUri, ScreenEvent.ScreenType.LIST_GROUP
                        )
                    },
                    onToggleSelected = groupViewModel::toggleSelected,
                    onClearSelection = groupViewModel::clearSelection,
                    onSetEditMode = groupViewModel::setEditMode,
                    onAddMembers = {
                        state?.let {
                            pickMembers.launch(
                                GroupUtil.createPickMemberIntent(
                                    this,
                                    it.metaData,
                                    it.members.mapTo(ArrayList()) { member ->
                                        member.contactId.toString()
                                    },
                                )
                            )
                        }
                    },
                    onRemoveMember = { groupViewModel.removeMembers(longArrayOf(it.contactId)) },
                    onRemoveSelected = groupViewModel::removeSelectedMembers,
                    onSendEmail = {
                        groupViewModel.sendTo(
                            ContactsUtils.SCHEME_MAILTO, getString(R.string.menu_sendEmailOption)
                        )
                    },
                    onSendMessage = {
                        groupViewModel.sendTo(
                            ContactsUtils.SCHEME_SMSTO, getString(R.string.menu_sendMessageOption)
                        )
                    },
                    onRename = groupViewModel::rename,
                    onDelete = {
                        // PeopleActivity shows how to undo it.
                        groupViewModel.delete()
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        ContactSaveService.unregisterListener(this)
        super.onDestroy()
    }

    override fun onServiceCompleted(callbackIntent: Intent) {
        val message = when {
            callbackIntent.data == null -> R.string.groupSavedErrorToast
            else -> when (callbackIntent.action) {
                GroupUtil.ACTION_UPDATE_GROUP -> R.string.groupUpdatedToast
                GroupUtil.ACTION_ADD_TO_GROUP -> R.string.groupMembersAddedToast
                GroupUtil.ACTION_REMOVE_FROM_GROUP -> R.string.groupMembersRemovedToast
                else -> {
                    Log.w(TAG, "Unhandled save action ${callbackIntent.action}")
                    return
                }
            }
        }
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(getString(message))
        }
    }

    companion object {
        private const val TAG = "GroupActivity"

        const val EXTRA_GROUP_URI = "groupUri"

        /**
         * Returns an intent showing the group [groupUri].
         */
        @JvmStatic
        fun createIntent(context: Context, groupUri: Uri) =
            Intent(context, GroupActivity::class.java).putExtra(EXTRA_GROUP_URI, groupUri)
    }
}
