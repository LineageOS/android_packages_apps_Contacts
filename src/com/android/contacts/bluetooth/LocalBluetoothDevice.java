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

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import android.bluetooth.IBluetoothDeviceCallback;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * LocalBluetoothDevice represents a remote Bluetooth device. It contains
 * attributes of the device (such as the address, name, RSSI, etc.) and
 * functionality that can be performed on the device (connect, pair, disconnect,
 * etc.).
 */
public class LocalBluetoothDevice implements Comparable<LocalBluetoothDevice> {
    private static final String TAG = "LocalBluetoothDevice";

    private static final int CONTEXT_ITEM_CONNECT = Menu.FIRST + 1;
    private static final int CONTEXT_ITEM_DISCONNECT = Menu.FIRST + 2;
    private static final int CONTEXT_ITEM_UNPAIR = Menu.FIRST + 3;
    private static final int CONTEXT_ITEM_CONNECT_ADVANCED = Menu.FIRST + 4;

    private final String mAddress;
    private String mName;
    private short mRssi;
    private int mBtClass = BluetoothClass.ERROR;

    private boolean mVisible;

    private int mBondState;

    private final LocalBluetoothManager mLocalManager;

    private List<Callback> mCallbacks = new ArrayList<Callback>();

    /**
     * When we connect to multiple profiles, we only want to display a single
     * error even if they all fail. This tracks that state.
     */
    private boolean mIsConnectingErrorPossible;

    LocalBluetoothDevice(Context context, String address) {
        mLocalManager = LocalBluetoothManager.getInstance(context);
        if (mLocalManager == null) {
            throw new IllegalStateException(
                    "Cannot use LocalBluetoothDevice without Bluetooth hardware");
        }

        mAddress = address;

        fillData();
    }

    public void onClicked() {
       /** Send the object */
    }

    public void showConnectingError() {
        if (!mIsConnectingErrorPossible) return;
        mIsConnectingErrorPossible = false;

        mLocalManager.showError(mAddress, R.string.bluetooth_error_title,
                R.string.bluetooth_connecting_error_message);
    }

    private void fillData() {
        BluetoothDevice manager = mLocalManager.getBluetoothManager();

        fetchName();
        fetchBtClass();

        mVisible = false;

        dispatchAttributesChanged();
    }

    public String getAddress() {
        return mAddress;
    }

    public String getName() {
        return mName;
    }

    public void refreshName() {
        fetchName();
        dispatchAttributesChanged();
    }

    private void fetchName() {
        mName = mLocalManager.getBluetoothManager().getRemoteName(mAddress);

        if (TextUtils.isEmpty(mName)) {
            mName = mAddress;
        }
    }

    public void refresh() {
        dispatchAttributesChanged();
    }

    public boolean isVisible() {
        return mVisible;
    }

    void setVisible(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            dispatchAttributesChanged();
        }
    }

    public int getBondState() {
        return mLocalManager.getBluetoothManager().getBondState(mAddress);
    }

    void setRssi(short rssi) {
        if (mRssi != rssi) {
            mRssi = rssi;
            dispatchAttributesChanged();
        }
    }

    public int getBtClassDrawable() {

        // Fallback on class
        switch (BluetoothClass.Device.Major.getDeviceMajor(mBtClass)) {
        case BluetoothClass.Device.Major.COMPUTER:
            return R.drawable.ic_bt_laptop;

        case BluetoothClass.Device.Major.PHONE:
            return R.drawable.ic_bt_cellphone;

        default:
            return 0;
        }
    }
    /**
     * Fetches a new value for the cached BT class.
     */
    private void fetchBtClass() {
        mBtClass = mLocalManager.getBluetoothManager().getRemoteClass(mAddress);
    }

    /**
     * Check class bits to check if it supports OBJECT_TRANSFER.
     * @return True if this device might support OBJECT_TRANSFER
     */
    public boolean doesClassMatchObjectTransfer() {
        if (BluetoothClass.Service.hasService(mBtClass, BluetoothClass.Service.OBJECT_TRANSFER)) {
            return true;
        }
        return false;
    }

    public void registerCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(Callback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    private void dispatchAttributesChanged() {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.onDeviceAttributesChanged(this);
            }
        }
    }

    @Override
    public String toString() {
        return mAddress;
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || !(o instanceof LocalBluetoothDevice)) {
            throw new ClassCastException();
        }

        return mAddress.equals(((LocalBluetoothDevice) o).mAddress);
    }

    @Override
    public int hashCode() {
        return mAddress.hashCode();
    }

    public int compareTo(LocalBluetoothDevice another) {
        int comparison;

        // Paired above not paired
        comparison = (another.getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0) -
            (getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0);
        if (comparison != 0) return comparison;

        // Visible above not visible
        comparison = (another.mVisible ? 1 : 0) - (mVisible ? 1 : 0);
        if (comparison != 0) return comparison;

        // Stronger signal above weaker signal
        comparison = another.mRssi - mRssi;
        if (comparison != 0) return comparison;

        // Fallback on name
        return getName().compareTo(another.getName());
    }

    public interface Callback {
        void onDeviceAttributesChanged(LocalBluetoothDevice device);
    }
}
