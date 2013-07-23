/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.util;

import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.contacts.R;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class XCloudManager {

    private static boolean DBG_XCOULD = true;

    private static String BAIDU_CLOUD_APK_NAME = "com.baidu.netdisk_qti";

    private static Uri sUri = Uri.parse("content://com.baidu.xcloud.content/contact");
    private static Uri sMasterUri = Uri.parse(
            "content://com.baidu.xcloud.content/contact_master");
    private static final String CONTACTBACKUPNOW = "contactbackupnow";
    private static final String ON_KEY = "on";
    private static final String OFF_KEY = "off";
    private static final String AUTO_CONTACT = "autocontact";
    private static final String AUTO_CONTACT_MASTER = "autocontactmaster";
    private static final String TIME_STAMP = "timestamp";

    private static XCloudManager sInstance = null;
    private static Object sSyncRoot = new Object();

    public static XCloudManager getInstance() {
        if (sInstance == null) {
            synchronized (sSyncRoot) {
                if (sInstance == null) {
                    sInstance = new XCloudManager();
                }
            }
        }
        return sInstance;
    }

    private XCloudManager() {
    }

    private void loge(String message) {
        loge(message, null);
    }

    private void loge(String message, Exception e) {
        if (DBG_XCOULD) {
            Log.e("XCLOUD-GALLERY", message, e);
        }
    }

    private boolean isXCloudInstalled(Context context) {
        boolean installed = false;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    BAIDU_CLOUD_APK_NAME, PackageManager.GET_PROVIDERS);
            installed = info != null;
        } catch (NameNotFoundException e) {
            installed = false;
        }
        loge("Is xcloud installed ? " + installed);
        return installed;
    }

    public void updateMenuState(Menu menu, Context context) {
        loge("Updating menu state");
        final MenuItem autoSyncToXCloudSwitcher = menu.findItem(R.id.menu_auto_sync_to_baidu_cloud);
        final MenuItem syncToXCloud = menu.findItem(R.id.menu_sync_to_baidu_cloud);

        if (autoSyncToXCloudSwitcher != null && syncToXCloud != null) {
            if (isXCloudInstalled(context)) {
                autoSyncToXCloudSwitcher.setVisible(true);
                syncToXCloud.setVisible(true);
                autoSyncToXCloudSwitcher.setChecked(
                        isAutoSyncMasterEnabled(context) && isAutoSyncEnabled(context));
            } else {
                autoSyncToXCloudSwitcher.setVisible(false);
                syncToXCloud.setVisible(false);
            }
        }
    }

    public boolean handleXCouldRelatedMenuItem(MenuItem item, Context context) {
        switch (item.getItemId()) {
            case R.id.menu_sync_to_baidu_cloud:
                syncContact(context);
                return true;
            case R.id.menu_auto_sync_to_baidu_cloud:
                boolean requestedState = !item.isChecked();
                if (isAutoSyncMasterEnabled(context)) {
                    enableAutoSync(requestedState, context);
                } else {
                    gotoSettings(context);
                }
                return true;
            default:
                return false;
        }
    }

    private void gotoSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        context.startActivity(intent);
    }

    private void enableAutoSync(boolean enabled, Context context) {
        long timestamp = Calendar.getInstance()
                .getTimeInMillis();
        ContentValues values = new ContentValues();
        values.put(TIME_STAMP, timestamp);
        values.put(
                AESUtils.getInstance().encrypt(AUTO_CONTACT, timestamp),
                AESUtils.getInstance().encrypt(
                        enabled ? ON_KEY : OFF_KEY, timestamp));
        context.getContentResolver()
                .update(sUri, values, null, null);

    }

    private boolean isAutoSyncEnabled(Context context) {
        Cursor cursor = context.getContentResolver().query(sUri, null, null, null, null);
        int columnIndex = cursor.getColumnIndex(AESUtils.getInstance().encrypt(AUTO_CONTACT));
        try {
            if (cursor != null && cursor.moveToNext() && -1 != columnIndex) {
                String value = cursor.getString(columnIndex);
                value = AESUtils.getInstance().decrypt(value);
                return (value != null && value.equals(ON_KEY));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private boolean isAutoSyncMasterEnabled(Context context) {
        Cursor cursor = context.getContentResolver().query(sMasterUri, null, null, null, null);
        int columnIndex = cursor
                .getColumnIndex(AESUtils.getInstance().encrypt(AUTO_CONTACT_MASTER));

        try {
            if (cursor != null && cursor.moveToNext() && -1 != columnIndex) {
                String value = cursor.getString(columnIndex);
                value = AESUtils.getInstance().decrypt(value);
                return (value != null && value.equals(ON_KEY));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private void syncContact(Context context) {
        ContentValues values = new ContentValues();
        long timestamp = Calendar.getInstance().getTimeInMillis();
        values.put(TIME_STAMP, timestamp);

        values.put(
                AESUtils.getInstance().encrypt(CONTACTBACKUPNOW, timestamp),
                AESUtils.getInstance().encrypt(ON_KEY, timestamp));

        context.getContentResolver().update(sUri, values, null, null);
    }

    private static class AESUtils {

        private final static String PWD = "qrd@baidu";

        private final static long DEFAULT_TIMESTAMP = 1351077888044L;

        private static AESUtils instance;

        public static AESUtils getInstance() {
            if (instance == null) {
                instance = new AESUtils();
            }
            return instance;
        }

        private AESUtils() {
        }

        private byte[] hex2Byte(String hex) {
            if (hex.length() < 1) {
                return null;
            }
            byte[] r = new byte[hex.length() / 2];
            for (int i = 0; i < hex.length() / 2; i++) {
                int h = Integer.parseInt(hex.substring(i * 2, i * 2 + 1), 16);
                int l = Integer.parseInt(hex.substring(i * 2 + 1, i * 2 + 2),
                        16);
                r[i] = (byte) (h * 16 + l);
            }
            return r;
        }

        private String passGen(long timestamp) {
            return PWD + timestamp;
        }

        private String byte2Hex(byte buf[]) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < buf.length; i++) {
                String hex = Integer.toHexString(buf[i] & 0xFF);
                if (hex.length() == 1) {
                    hex = '0' + hex;
                }
                sb.append(hex.toUpperCase());
            }
            return sb.toString();
        }

        public String encrypt(String content) {
            return encrypt(content, DEFAULT_TIMESTAMP);
        }

        public String encrypt(String content, long timestamp) {
            if (timestamp == 0) {
                throw new RuntimeException("timestamp can't be null");
            }
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES");
                SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG", "Crypto" );
                sr.setSeed(passGen(timestamp).getBytes());
                kgen.init(128, sr);
                SecretKey secretKey = kgen.generateKey();
                byte[] enCodeFormat = secretKey.getEncoded();
                SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
                Cipher cipher = Cipher.getInstance("AES");
                byte[] byteContent = content.getBytes("utf-8");
                cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(
                        new byte[cipher.getBlockSize()]));
                byte[] bytes = cipher.doFinal(byteContent);
                String result = byte2Hex(bytes);
                return result;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
            return null;
        }

        public String decrypt(String content) {
            return decrypt(content, DEFAULT_TIMESTAMP);
        }

        public String decrypt(String content, long timestamp) {
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES");
                SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG", "Crypto" );
                sr.setSeed(passGen(timestamp).getBytes());
                kgen.init(128, sr);
                SecretKey secretKey = kgen.generateKey();
                byte[] enCodeFormat = secretKey.getEncoded();
                SecretKeySpec key = new SecretKeySpec(enCodeFormat, "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(
                        new byte[cipher.getBlockSize()]));
                byte[] bytes = cipher.doFinal(hex2Byte(content));
                String result = new String(bytes, "UTF-8");
                return result;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
