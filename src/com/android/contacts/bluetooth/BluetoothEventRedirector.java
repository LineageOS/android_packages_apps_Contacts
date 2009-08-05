/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
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

package com.android.contacts.bluetooth;
import com.android.contacts.R;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothError;
import android.bluetooth.BluetoothIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * BluetoothEventRedirector receives broadcasts and callbacks from the Bluetooth
 * API and dispatches the event on the UI thread to the right class in the
 * Settings.
 */
public class BluetoothEventRedirector {
    private static final String TAG = "BluetoothEventRedirector";
    private static final boolean V = LocalBluetoothManager.V;

    private LocalBluetoothManager mManager;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (V) {
                Log.v(TAG, "Received " + intent.getAction());
            }

            String action = intent.getAction();
            String address = intent.getStringExtra(BluetoothIntent.ADDRESS);

            if (action.equals(BluetoothIntent.BLUETOOTH_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(BluetoothIntent.BLUETOOTH_STATE,
                                        BluetoothError.ERROR);
                mManager.setBluetoothStateInt(state);

            } else if (action.equals(BluetoothIntent.DISCOVERY_STARTED_ACTION)) {
                mManager.onScanningStateChanged(true);
            } else if (action.equals(BluetoothIntent.DISCOVERY_COMPLETED_ACTION)) {
                mManager.onScanningStateChanged(false);

            } else if (action.equals(BluetoothIntent.REMOTE_DEVICE_FOUND_ACTION)) {
                short rssi = intent.getShortExtra(BluetoothIntent.RSSI, Short.MIN_VALUE);
                mManager.getLocalDeviceManager().onDeviceAppeared(address, rssi);

            } else if (action.equals(BluetoothIntent.REMOTE_DEVICE_DISAPPEARED_ACTION)) {
                mManager.getLocalDeviceManager().onDeviceDisappeared(address);

            } else if (action.equals(BluetoothIntent.REMOTE_NAME_UPDATED_ACTION)) {
                mManager.getLocalDeviceManager().onDeviceNameUpdated(address);
            }
        }
    };

    public BluetoothEventRedirector(LocalBluetoothManager localBluetoothManager) {
        mManager = localBluetoothManager;
    }

    public void start() {
        IntentFilter filter = new IntentFilter();

        // Bluetooth on/off broadcasts
        filter.addAction(BluetoothIntent.BLUETOOTH_STATE_CHANGED_ACTION);

        // Discovery broadcasts
        filter.addAction(BluetoothIntent.DISCOVERY_STARTED_ACTION);
        filter.addAction(BluetoothIntent.DISCOVERY_COMPLETED_ACTION);
        filter.addAction(BluetoothIntent.REMOTE_DEVICE_DISAPPEARED_ACTION);
        filter.addAction(BluetoothIntent.REMOTE_DEVICE_FOUND_ACTION);
        filter.addAction(BluetoothIntent.REMOTE_NAME_UPDATED_ACTION);

        mManager.getContext().registerReceiver(mBroadcastReceiver, filter);
    }

    public void stop() {
        mManager.getContext().unregisterReceiver(mBroadcastReceiver);
    }
}
