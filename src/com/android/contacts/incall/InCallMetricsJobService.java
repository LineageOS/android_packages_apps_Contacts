/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.contacts.incall;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

public class InCallMetricsJobService extends JobService {
    private static final String TAG = InCallMetricsJobService.class.getSimpleName();
    private static final boolean DEBUG = false;
    private Runnable mScheduledTask = null;
    public interface JobDoneCallback {
        void callback(JobParameters params, boolean reschedule);
    }

    private JobDoneCallback mCallback = new JobDoneCallback() {
        @Override
        public void callback(JobParameters params, boolean reschedule) {
            if (DEBUG) Log.d(TAG, "JobDoneCallback");
            mScheduledTask = null;
            jobFinished(params, reschedule);
        }
    };

    @Override
    public boolean onStartJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStartJob");
        mScheduledTask = InCallMetricsHelper.prepareAndSend(this, mCallback, params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStopJob");
        if (mScheduledTask != null) {
            InCallMetricsHelper.stopTask(this, mScheduledTask);
            mScheduledTask = null;
        }
        return true;
    }
}
