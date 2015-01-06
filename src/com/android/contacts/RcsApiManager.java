/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.contacts;

import com.suntek.mway.rcs.client.api.RCSServiceListener;
import com.suntek.mway.rcs.client.api.autoconfig.RcsAccountApi;
import com.suntek.mway.rcs.client.api.blacklist.impl.BlackListApi;
import com.suntek.mway.rcs.client.api.capability.impl.CapabilityApi;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.im.impl.PaMessageApi;
import com.suntek.mway.rcs.client.api.impl.groupchat.ConfApi;
import com.suntek.mway.rcs.client.api.login.impl.LoginApi;
import com.suntek.mway.rcs.client.api.mcloud.McloudFileApi;
import com.suntek.mway.rcs.client.api.mcontact.McontactApi;
import com.suntek.mway.rcs.client.api.profile.impl.ProfileApi;
import com.suntek.mway.rcs.client.api.publicaccount.impl.PublicAccountApi;
import com.suntek.mway.rcs.client.api.support.RcsSupportApi;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.voip.impl.RichScreenApi;
import com.suntek.mway.rcs.client.api.plugincenter.PluginCenterApi;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

public class RcsApiManager {
    private static final String TAG = "RcsApiManager";
    private static Context mContext;
    private static boolean mIsRcsServiceInstalled = false;
    private static MessageApi mMessageApi = new MessageApi();
    private static RcsAccountApi mRcsAccountApi = new RcsAccountApi();
    private static ProfileApi mProfileApi = new ProfileApi();
    private static CapabilityApi mCapabilityApi = new CapabilityApi();
    private static ConfApi mConfApi = new ConfApi();
    private static RichScreenApi mRichScreenApi = new RichScreenApi(null);
    private static PluginCenterApi mPluginCenterApi = new PluginCenterApi();
    private static McontactApi mMcontactApi = new McontactApi();

    public static void init(Context context) {
        mContext = context;
        mIsRcsServiceInstalled = RcsSupportApi.isRcsServiceInstalled(context);
        if (!mIsRcsServiceInstalled) {
            Log.d(TAG, "_________mIsRcsServiceInstalled false__________");
            return;
        }

        mMessageApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "MessageApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "MessageApi connected");
            }
        });

        mRcsAccountApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "RcsAccountApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "RcsAccountApi connected");
            }
        });

        mProfileApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "ProfileApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "ProfileApi connected");
            }
        });

        mCapabilityApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "CapabilityApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "CapabilityApi connected");
            }
        });

        mConfApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "ConfApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "ConfApi connected");
            }
        });
        mRichScreenApi.init(context,new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "mRichScreenApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "mRichScreenApi connected");
            }
        });
        mPluginCenterApi.init(context,new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "mPluginCenterApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "mPluginCenterApi connected");
            }
        });
        mMcontactApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
            }

            @Override
            public void onServiceConnected() throws RemoteException {
            }
        });
     }

    public static MessageApi getMessageApi() {

        if (mMessageApi == null) {
            Log.d(TAG, "_______mMessageApi null______");
        }
        return mMessageApi;
    }

    public static RcsAccountApi getRcsAccoutApi() {

        return mRcsAccountApi;
    }

    public static ConfApi getConfApi() {
        if (mConfApi == null) {
            mConfApi = new ConfApi();
            mConfApi.init(mContext, null);
        }
        return mConfApi;
    }

    public static ProfileApi getProfileApi() {
        if (mProfileApi == null) {
            mProfileApi = new ProfileApi();
            mProfileApi.init(mContext, null);
            Log.d(TAG, "_______mProfileApi init______");
        }
        return mProfileApi;
    }

    public static RichScreenApi getRichScreenApi() {
        if (mRichScreenApi == null) {
            mRichScreenApi = new RichScreenApi(null);
            mRichScreenApi.init(mContext, null);
            Log.d(TAG, "_______mRichScreenApi init______");
        }
        return mRichScreenApi;
    }

    public static PluginCenterApi getPluginCenterApi() {
        if (mPluginCenterApi == null) {
            mPluginCenterApi = new PluginCenterApi();
            mPluginCenterApi.init(mContext, null);
            Log.d(TAG, "mPluginCenterApi init______");
        }
        return mPluginCenterApi;
    }

    public static CapabilityApi getCapabilityApi() {
        if (mCapabilityApi == null) {
            mCapabilityApi = new CapabilityApi();
            mCapabilityApi.init(mContext, null);
        }
        return mCapabilityApi;
    }

    public static McontactApi getMcontactApi() {
        if (mMcontactApi == null) {
            mMcontactApi = new McontactApi();
            mMcontactApi.init(mContext, null);
        }
        return mMcontactApi;
    }

    public static boolean isRcsServiceInstalled() {
        return mIsRcsServiceInstalled;
    }
}
