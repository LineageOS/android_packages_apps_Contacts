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
 * limitations under the License.
 */
package com.android.contacts

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.OperationApplicationException
import android.os.IBinder
import android.os.RemoteException
import android.util.TimingLogger
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.contacts.activities.PeopleActivity
import com.android.contacts.database.SimContactDao
import com.android.contacts.model.SimCard
import com.android.contacts.model.SimContact
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.util.ContactsNotificationChannelsUtil
import com.android.contactsbind.FeedbackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimImportService : Service() {

  // Imports run one at a time, in the order they were requested.
  private val importDispatcher = Dispatchers.IO.limitedParallelism(1)

  // Not cancelled in onDestroy(): like the executor this replaces, requested imports are allowed
  // to complete.
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // Number of imports that haven't finished yet. Only accessed from the main thread.
  private var pendingImports = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ContactsNotificationChannelsUtil.createDefaultChannel(this)

    val dao = SimContactDao.create(this)
    val account = intent?.let {
      IntentCompat.getParcelableExtra(it, EXTRA_ACCOUNT, AccountWithDataSet::class.java)
    }
    val contacts = intent?.let {
      IntentCompat.getParcelableArrayListExtra(it, EXTRA_SIM_CONTACTS, SimContact::class.java)
    }
    val sim = intent?.let {
      dao.getSimBySubscriptionId(
        it.getIntExtra(EXTRA_SIM_SUBSCRIPTION_ID, SimCard.NO_SUBSCRIPTION_ID)
      )
    }

    if (sim == null || contacts == null) {
      // Only stop once the imports requested before this one have completed, so that the calls
      // to stopSelf() occur in the correct order.
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(importDispatcher) {}
        stopSelf(startId)
      }
      return START_NOT_STICKY
    }

    runImport(sim, contacts, account, dao, startId)
    return START_REDELIVER_INTENT
  }

  private fun runImport(
    sim: SimCard,
    contacts: List<SimContact>,
    targetAccount: AccountWithDataSet?,
    dao: SimContactDao,
    startId: Int,
  ) {
    val startTime = System.currentTimeMillis()
    pendingImports++
    startForeground(NOTIFICATION_ID, getImportingNotification())
    notifyStateChanged()

    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      val success =
        withContext(importDispatcher) { importContacts(sim, contacts, targetAccount, dao) }
      stopSelf(startId)

      val result =
        Intent(BROADCAST_SIM_IMPORT_COMPLETE)
          .putExtra(EXTRA_RESULT_CODE, if (success) RESULT_SUCCESS else RESULT_FAILURE)
          .putExtra(EXTRA_OPERATION_REQUESTED_AT_TIME, startTime)
          .putExtra(EXTRA_SIM_SUBSCRIPTION_ID, sim.subscriptionId)
      if (success) {
        result.putExtra(EXTRA_RESULT_COUNT, contacts.size)
      }
      LocalBroadcastManager.getInstance(this@SimImportService).sendBroadcast(result)

      pendingImports--
      // Only notify of completion if all the import requests have finished. We're using the same
      // notification for imports so in the rare case that a user has started multiple imports
      // the notification won't go away until all of them complete.
      if (pendingImports == 0) {
        stopForeground(STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java)
          .notify(
            NOTIFICATION_ID,
            if (success) getCompletedNotification() else getFailedNotification(),
          )
      }
      notifyStateChanged()
    }
  }

  private fun importContacts(
    sim: SimCard,
    contacts: List<SimContact>,
    targetAccount: AccountWithDataSet?,
    dao: SimContactDao,
  ): Boolean {
    val timer = TimingLogger(TAG, "import")
    try {
      // Just import them all at once.
      // Experimented with using smaller batches (e.g. 25 and 50) so that percentage progress
      // could be displayed however this slowed down the import by over a factor of 2. If the
      // batch size is over a 100 then most cases will only require a single batch so we don't
      // even worry about displaying accurate progress
      dao.importContacts(contacts, targetAccount)
      dao.persistSimState(sim.withImportedState(true))
      timer.addSplit("done")
      timer.dumpToLog()
    } catch (e: RemoteException) {
      FeedbackHelper.sendFeedback(this, TAG, "Failed to import contacts from SIM card", e)
      return false
    } catch (e: OperationApplicationException) {
      FeedbackHelper.sendFeedback(this, TAG, "Failed to import contacts from SIM card", e)
      return false
    }
    return true
  }

  private fun getCompletedNotification(): Notification =
    newNotificationBuilder()
      .setContentTitle(getString(R.string.importing_sim_finished_title))
      .setSmallIcon(R.drawable.quantum_ic_done_vd_theme_24)
      .build()

  private fun getFailedNotification(): Notification =
    newNotificationBuilder()
      .setContentTitle(getString(R.string.importing_sim_failed_title))
      .setContentText(getString(R.string.importing_sim_failed_message))
      .setSmallIcon(R.drawable.quantum_ic_error_vd_theme_24)
      .build()

  private fun newNotificationBuilder(): NotificationCompat.Builder {
    val intent = Intent(this, PeopleActivity::class.java)
    return NotificationCompat.Builder(this, ContactsNotificationChannelsUtil.DEFAULT_CHANNEL)
      .setOngoing(false)
      .setAutoCancel(true)
      .setColor(getColor(R.color.dialtacts_theme_color))
      .setContentIntent(
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
      )
  }

  private fun getImportingNotification(): Notification =
    NotificationCompat.Builder(this, ContactsNotificationChannelsUtil.DEFAULT_CHANNEL)
      .setOngoing(true)
      .setProgress(/* max */ 100, /* progress */ 0, /* indeterminate */ true)
      .setContentTitle(getString(R.string.importing_sim_in_progress_title))
      .setColor(getColor(R.color.dialtacts_theme_color))
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .build()

  private fun notifyStateChanged() {
    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_SERVICE_STATE_CHANGED))
  }

  companion object {
    private const val TAG = "SimImportService"

    const val EXTRA_ACCOUNT = "account"
    const val EXTRA_SIM_CONTACTS = "simContacts"
    const val EXTRA_SIM_SUBSCRIPTION_ID = "simSubscriptionId"
    const val EXTRA_RESULT_CODE = "resultCode"
    const val EXTRA_RESULT_COUNT = "count"
    const val EXTRA_OPERATION_REQUESTED_AT_TIME = "requestedTime"

    @JvmField
    val BROADCAST_SERVICE_STATE_CHANGED =
      SimImportService::class.java.name + "#serviceStateChanged"
    @JvmField
    val BROADCAST_SIM_IMPORT_COMPLETE = SimImportService::class.java.name + "#simImportComplete"

    const val RESULT_UNKNOWN = 0
    const val RESULT_SUCCESS = 1
    const val RESULT_FAILURE = 2

    // VCardService uses jobIds for it's notifications which count up from 0 so we just use a
    // bigger number to prevent overlap.
    private const val NOTIFICATION_ID = 100

    /**
     * Starts an import of the contacts from the sim into the target account
     *
     * @param context context to use for starting the service
     * @param subscriptionId the subscriptionId of the SIM card that is being imported. See
     *   [android.telephony.SubscriptionInfo.getSubscriptionId]. Upon completion the SIM for that
     *   subscription ID will be marked as imported
     * @param contacts the contacts to import
     * @param targetAccount the account import the contacts into
     */
    @JvmStatic
    fun startImport(
      context: Context,
      subscriptionId: Int,
      contacts: ArrayList<SimContact>,
      targetAccount: AccountWithDataSet?,
    ) {
      context.startService(
        Intent(context, SimImportService::class.java)
          .putParcelableArrayListExtra(EXTRA_SIM_CONTACTS, contacts)
          .putExtra(EXTRA_SIM_SUBSCRIPTION_ID, subscriptionId)
          .putExtra(EXTRA_ACCOUNT, targetAccount)
      )
    }
  }
}
