/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.contacts.util

import android.content.Context
import android.telephony.PhoneNumberFormattingTextWatcher
import android.widget.TextView
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.contacts.GeoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PhoneNumberFormatter {

  /**
   * Delay-sets [PhoneNumberFormattingTextWatcher] to a [TextView] and formats the number
   * immediately if [formatAfterWatcherSet] is true. In some cases, formatting before user editing
   * might cause unwanted results (e.g. the editor thinks the user changed the content, and would
   * save when closed even when the user didn't make other changes.)
   *
   * The watcher is created off the main thread, as it loads the phone number metadata, and the
   * work is cancelled if the hosting lifecycle is destroyed first.
   */
  @JvmStatic
  @JvmOverloads
  fun setPhoneNumberFormattingTextWatcher(
    context: Context,
    textView: TextView,
    formatAfterWatcherSet: Boolean = false,
  ) {
    val countryCode = GeoUtil.getCurrentCountryIso(context)
    textView.doOnAttach { view ->
      view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
        val watcher =
          withContext(Dispatchers.IO) { PhoneNumberFormattingTextWatcher(countryCode) }

        // Setting a text changed listener is safe even after the view is detached.
        textView.addTextChangedListener(watcher)

        // Forcing formatting the existing phone number
        if (formatAfterWatcherSet) {
          watcher.afterTextChanged(textView.editableText)
        }
      }
    }
  }
}
