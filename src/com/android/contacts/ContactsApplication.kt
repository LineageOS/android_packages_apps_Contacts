/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.SharedPreferences
import android.os.StrictMode
import android.provider.ContactsContract.Contacts
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import com.android.contacts.testing.InjectedServices
import com.android.contacts.util.Constants
import com.android.contactsbind.analytics.AnalyticsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContactsApplication : Application() {

    /** Scope for work that is tied to the process rather than to a single component. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getContentResolver(): ContentResolver =
        injectedServices?.contentResolver ?: super.getContentResolver()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        injectedServices?.sharedPreferences ?: super.getSharedPreferences(name, mode)

    override fun getSystemService(name: String): Any? =
        injectedServices?.getSystemService(name) ?: super.getSystemService(name)

    override fun onCreate() {
        super.onCreate()

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate start")
        }

        if (Log.isLoggable(STRICT_MODE_TAG, Log.DEBUG)) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
        }

        // Perform the initialization that doesn't have to finish immediately.
        applicationScope.launch {
            // Warm up the preferences and the contacts provider. We delay initialization of the
            // account type manager because we may not have the contacts group permission (and thus
            // not have the get accounts permission).
            PreferenceManager.getDefaultSharedPreferences(this@ContactsApplication)
            contentResolver.getType(ContentUris.withAppendedId(Contacts.CONTENT_URI, 1))
        }

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "ContactsApplication.onCreate finish")
        }

        AnalyticsUtil.initialize(this)
    }

    companion object {
        /**
         * Log tag for enabling/disabling StrictMode violation log. To enable: adb shell setprop
         * log.tag.ContactsStrictMode DEBUG
         */
        const val STRICT_MODE_TAG = "ContactsStrictMode"

        @get:JvmStatic
        var injectedServices: InjectedServices? = null
            private set

        /** Overrides the system services with mocks for testing. */
        @VisibleForTesting
        @JvmStatic
        fun injectServices(services: InjectedServices?) {
            injectedServices = services
        }
    }
}
