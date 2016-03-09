package com.android.contacts.incall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CallMethodStatusReceiver extends BroadcastReceiver {
    private static final String TAG = CallMethodStatusReceiver.class.getSimpleName();
    private static final boolean DEBUG = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG) Log.d(TAG, "plugin status changed");
        InCallPluginHelper.refresh();
    }
}
