/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.bluetooth;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.os.Environment;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

public class BluetoothObexTransferFileInfo {
   private static final String TAG = "BluetoothObexTransferFileInfo";
   private String mName = "";
   private String mDisplayName = "";
   private long mDoneBytes = 0;
   private long mSizeBytes = 0;

   public BluetoothObexTransferFileInfo(String name) {
      mName = name;
      mDoneBytes = 0 ;
      File file = new File(name);
      mDisplayName = file.getName();
      mSizeBytes = file.length();
   }

   public String getName() {
      return mName;
   }
   public void setName(String name) {
      mName = name;
   }

   public String getDisplayName() {
      return mDisplayName;
   }
   public void setDisplayName(String name) {
      mDisplayName = name;
   }

   public String toString() {
      return getName();
   }
   public void setTotal(long total) {
      mSizeBytes = total;
   }
   public long getTotal() {
      return mSizeBytes;
   }
   public long getDone() {
      return mDoneBytes;
   }
   public void setDone(long done) {
      mDoneBytes = done;
   }

   public void deleteIfVCard(Context context) {
      /** If vcard file, delete it */
      File file = new File(mName);
      if(getExtension().equalsIgnoreCase(".vcf")) {
         try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
               if (file.exists()) {
                  file.delete();
               } else {
                  context.deleteFile(mName);
               }
            }
         } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
         }
      }
   }

    public String getExtension() {
        if (mName == null) {
            return null;
        }

        int dot = mName.lastIndexOf(".");
        if (dot >= 0) {
            return mName.substring(dot);
        } else {
            // No extension.
            return "";
        }
    }

   public int getCompletePercent() {
      int percentDone = 100;
      if (mSizeBytes > 0)
      {
         percentDone =  (int)(mDoneBytes / mSizeBytes);
      }
      return(percentDone);
   }
}
