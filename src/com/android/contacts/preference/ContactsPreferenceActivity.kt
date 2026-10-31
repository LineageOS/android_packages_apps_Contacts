/*
 * SPDX-FileCopyrightText: 2009 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.preference

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.MessageFormat
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.ProviderStatus
import android.provider.ContactsContract.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.contacts.R
import com.android.contacts.SimImportService
import com.android.contacts.activities.LicenseActivity
import com.android.contacts.compat.TelecomManagerUtil
import com.android.contacts.editor.SelectAccountDialogFragment
import com.android.contacts.interactions.ExportDialogFragment
import com.android.contacts.interactions.ImportDialogFragment
import com.android.contacts.list.AccountFilterActivity
import com.android.contacts.list.ContactListFilterController
import com.android.contacts.list.ProviderStatusWatcher
import com.android.contacts.logging.ScreenEvent.ScreenType
import com.android.contacts.model.MyInfo
import com.android.contacts.model.SettingsFeatures
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.ui.SettingsApp
import com.android.contacts.ui.theme.ContactsTheme
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.AccountSelectionUtil
import com.android.contacts.util.ImplicitIntentsUtil
import com.android.contacts.viewmodels.SettingsViewModel
import com.android.contactsbind.HelpUtils
import kotlinx.coroutines.launch

class ContactsPreferenceActivity : ComponentActivity(), SelectAccountDialogFragment.Listener {
    // View models
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val snackbarHostState = SnackbarHostState()

    // Activity result launchers
    private val customContactsFilterLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        AccountFilterUtil.handleAccountFilterResult(
            ContactListFilterController.getInstance(this), result.resultCode, result.data
        )
        settingsViewModel.refreshCustomFilter()
    }

    private val defaultAccountLauncher = registerForActivityResult(StartActivityForResult()) {
        settingsViewModel.refreshDefaultAccount()
    }

    private val simImportReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onSimImportComplete(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val newLocalProfileExtra = intent.getStringExtra(EXTRA_NEW_LOCAL_PROFILE)
        val features = SettingsFeatures(
            sortOrder = resources.getBoolean(R.bool.config_sort_order_user_changeable),
            displayOrder = resources.getBoolean(R.bool.config_display_order_user_changeable),
            phoneticNameDisplay =
                resources.getBoolean(R.bool.config_phonetic_name_display_user_changeable),
            export = ProviderStatusWatcher.getInstance(this).providerStatus ==
                    ProviderStatus.STATUS_NORMAL,
            blockedNumbers = getSystemService(TelephonyManager::class.java).isVoiceCapable &&
                    BlockedNumberContract.canCurrentUserBlockNumbers(this),
            about = !HelpUtils.isHelpAndFeedbackAvailable(),
        )
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName

        LocalBroadcastManager.getInstance(this).registerReceiver(
            simImportReceiver, IntentFilter(SimImportService.BROADCAST_SIM_IMPORT_COMPLETE)
        )

        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                SettingsApp(
                    settingsViewModel = settingsViewModel,
                    features = features,
                    versionName = versionName,
                    snackbarHostState = snackbarHostState,
                    onClose = ::finish,
                    onMyInfoClick = { openMyInfo(it, newLocalProfileExtra) },
                    onAccountsClick = {
                        ImplicitIntentsUtil.startActivityOutsideApp(
                            this, ImplicitIntentsUtil.getIntentForAddingAccount()
                        )
                    },
                    onDefaultAccountClick = {
                        defaultAccountLauncher.launch(Intent(Settings.ACTION_SET_DEFAULT_ACCOUNT))
                    },
                    onCustomContactsFilterClick = {
                        customContactsFilterLauncher.launch(
                            Intent(this, AccountFilterActivity::class.java)
                        )
                    },
                    onImportClick = { ImportDialogFragment.show(fragmentManager) },
                    onExportClick = {
                        ExportDialogFragment.show(
                            fragmentManager,
                            ContactsPreferenceActivity::class.java,
                            ExportDialogFragment.EXPORT_MODE_ALL_CONTACTS,
                        )
                    },
                    onBlockedNumbersClick = {
                        startActivity(
                            TelecomManagerUtil.createManageBlockedNumbersIntent(
                                getSystemService(TelecomManager::class.java)
                            )
                        )
                    },
                    onOpenSourceLicensesClick = {
                        startActivity(Intent(this, LicenseActivity::class.java))
                    },
                    onPrivacyPolicyClick = { openUrl(PRIVACY_POLICY_URL) },
                    onTermsOfServiceClick = { openUrl(TERMS_OF_SERVICE_URL) },
                )
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(simImportReceiver)

        super.onDestroy()
    }

    override fun onAccountChosen(account: AccountWithDataSet, extraArgs: Bundle) {
        AccountSelectionUtil.doImport(
            this,
            extraArgs.getInt(ImportDialogFragment.KEY_RES_ID),
            account,
            extraArgs.getInt(ImportDialogFragment.KEY_SUBSCRIPTION_ID),
        )
    }

    override fun onAccountSelectorCancelled() {}

    private fun openMyInfo(myInfo: MyInfo, newLocalProfileExtra: String?) {
        when (myInfo) {
            MyInfo.Loading -> {}

            MyInfo.None -> ImplicitIntentsUtil.startActivityInApp(
                this,
                Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI)
                    .putExtra(newLocalProfileExtra, true),
            )

            is MyInfo.Existing -> ImplicitIntentsUtil.startQuickContact(
                this,
                ContentUris.withAppendedId(Contacts.CONTENT_URI, myInfo.contactId),
                ScreenType.ME_CONTACT,
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.url_open_error_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onSimImportComplete(intent: Intent) {
        val now = System.currentTimeMillis()
        val opStart = intent.getLongExtra(SimImportService.EXTRA_OPERATION_REQUESTED_AT_TIME, now)

        // If it's been over 30 seconds the user is likely in a different context so suppress the
        // message.
        if (now - opStart > 30 * 1000) {
            return
        }

        val code = intent.getIntExtra(
            SimImportService.EXTRA_RESULT_CODE, SimImportService.RESULT_UNKNOWN
        )
        val count = intent.getIntExtra(SimImportService.EXTRA_RESULT_COUNT, -1)
        val message = when {
            code == SimImportService.RESULT_SUCCESS && count > 0 ->
                MessageFormat(getString(R.string.sim_import_success_toast_fmt))
                    .format(mapOf("count" to count))

            code == SimImportService.RESULT_FAILURE -> getString(R.string.sim_import_failed_toast)

            else -> return
        }

        lifecycleScope.launch {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    companion object {
        const val EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile"

        private const val PRIVACY_POLICY_URL = "http://www.google.com/policies/privacy"
        private const val TERMS_OF_SERVICE_URL = "http://www.google.com/policies/terms"
    }
}
