package com.android.contacts.incall;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;

import java.lang.ref.WeakReference;

public class StartCallResultReceiver extends ResultReceiver {
    private static final String TAG = StartCallResultReceiver.class.getSimpleName();
    private static final boolean DEBUG = false;

    private WeakReference<Receiver> mReceiver;

    public StartCallResultReceiver(Handler handler) {
        super(handler);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (DEBUG) {
            Log.d(TAG, "Result received resultCode: " + resultCode + " resultData: " + resultData);
        }

        if (mReceiver != null) {
            Receiver receiver = mReceiver.get();
            if (receiver != null) {
                receiver.onReceiveResult(resultCode, resultData);
            }
        }
    }

    public interface Receiver {
        public void onReceiveResult(int resultCode, Bundle resultData);
    }

    public void setReceiver(Receiver receiver) {
        mReceiver = new WeakReference<Receiver>(receiver);
    }

}