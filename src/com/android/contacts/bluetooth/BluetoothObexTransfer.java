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

import com.android.contacts.R;
import com.android.contacts.bluetooth.Panel;
import android.bluetooth.BluetoothDevice;

import android.app.Activity;
import android.bluetooth.obex.BluetoothObexIntent;
import android.bluetooth.obex.BluetoothOpp;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.nio.channels.FileChannel;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.provider.MediaStore.Audio;

public class BluetoothObexTransfer {
   static final boolean V = true;

   private static final boolean useSdCardForVCard = false;

   private static final String TAG = "BluetoothObexTransfer";
   protected static final int FINISHEDID = 0x1337;
   protected static final int UPDATESTATS = 0x1338;

   private BluetoothDevice mBluetooth;

   private ProgressBar mProgressBar;
   private TextView mProgressText;
   private Panel mTopPanel;
   private Button mCancelButton;
   private Activity mActivity;
   private List<BluetoothObexTransferFileInfo> mSendingFileList = new ArrayList<BluetoothObexTransferFileInfo>();
   private List<BluetoothObexTransferFileInfo> mReceivingContactsList = new ArrayList<BluetoothObexTransferFileInfo>();

   private boolean mCancelTransfer=false;
   private boolean mRegisteredBroadcasts=false;
   private int mProgressPercent = 0;
   BluetoothOpp mBluetoothOPP;

   public BluetoothObexTransfer(Activity activity) {
      mActivity = activity;
      mBluetooth = (BluetoothDevice) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
      mBluetoothOPP = new BluetoothOpp();
   }

   public void onDestroy() {
      unRegisterBluetoothOBexIntentHandler();
   }

   /* Is Bluetooth Enabled or not? */
   public boolean isEnabled() {
      boolean enabled = false;
      if (mBluetooth != null) {
         enabled = mBluetooth.isEnabled();
      }
      return(enabled);
   }

   private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
         if (V) {
            Log.v(TAG, "Received " + intent.getAction());
         }

         String action = intent.getAction();

         if (action.equals(BluetoothObexIntent.PROGRESS_ACTION)) {

            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            int bytesTotal = intent.getIntExtra(BluetoothObexIntent.OBJECT_SIZE, 0);
            int bytesDone = intent.getIntExtra(BluetoothObexIntent.BYTES_TRANSFERRED, 0);
            onProgressIndication(fileName, bytesTotal, bytesDone);

         } else if (action.equals(BluetoothObexIntent.RX_COMPLETE_ACTION)) {
            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            boolean success = intent.getBooleanExtra(BluetoothObexIntent.SUCCESS, false);
            onReceiveCompleteIndication(fileName, success);

         } else if (action.equals(BluetoothObexIntent.TX_COMPLETE_ACTION)) {

            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            boolean success = intent.getBooleanExtra(BluetoothObexIntent.SUCCESS, false);
            String errorMsg = intent.getStringExtra(BluetoothObexIntent.ERROR_MESSAGE);
            onTransmitCompleteIndication(fileName, success, errorMsg);

         }
      }
   };

   public void registerBluetoothOBexIntentHandler() {
      /* Register only if not already regsitered */
      synchronized (this) {
         if (mRegisteredBroadcasts ==  false) {
            IntentFilter filter = new IntentFilter();

            // Bluetooth on/off broadcasts
            filter.addAction(BluetoothObexIntent.PROGRESS_ACTION);
            filter.addAction(BluetoothObexIntent.TX_COMPLETE_ACTION);
            filter.addAction(BluetoothObexIntent.RX_COMPLETE_ACTION);
            mActivity.registerReceiver(mBroadcastReceiver, filter);
            mRegisteredBroadcasts=true;
            if (V) {
               Log.v(TAG, "registerBluetoothOBexIntentHandler");
            }
         }
      }
   }

   public void unRegisterBluetoothOBexIntentHandler() {
      synchronized (this) {
         if (mRegisteredBroadcasts ==  true) {
            mActivity.unregisterReceiver(mBroadcastReceiver);
            mRegisteredBroadcasts = false;
            if (V) {
               Log.v(TAG, "unRegisterBluetoothOBexIntentHandler");
            }
         }
      }
   }

   /*************************************************************************
              Panel Related - Start
   **************************************************************************/
   public void setupPanel() {
      if (V) {
         Log.v(TAG, "setupPanel - Start");
      }
      mTopPanel = (Panel) mActivity.findViewById(R.id.topPanel);
      mTopPanel.setVisibility(Panel.VISIBLE);
      mTopPanel.initialInflation();
      mProgressText = (TextView) mActivity.findViewById(R.id.progressText);
      mProgressBar = (ProgressBar) mActivity.findViewById(R.id.progress);
      mCancelButton = (Button) mActivity.findViewById(R.id.cancelButton);

      updateProgress();
      updateProgressText();

      mCancelButton.setOnClickListener(new Button.OnClickListener() {
                                          public void onClick(View v) {
                                             progressCanceled();
                                          }
                                       });
   }
   public void Cleanup() {
      mProgressPercent=0;
      unRegisterBluetoothOBexIntentHandler();

      synchronized (mSendingFileList) {
         mSendingFileList.clear();
      }
      synchronized (mReceivingContactsList) {
         mReceivingContactsList.clear();
      }
      if (mTopPanel != null) {
         mProgressBar.setProgress(mProgressPercent);
         mTopPanel.setVisibility(Panel.GONE);
      }
   }

   public void setProgress() {
      if (mTopPanel != null) {
         if(isTransferinProgress()) {
            if ((mProgressPercent > 0) && (mProgressPercent <= 100)) {
               updateProgressText();
               mProgressBar.setProgress(mProgressPercent);
            }
         } else {
            mProgressPercent=0;
            progressDone();
         }
      }
   }

   public void progressCanceled() {
      boolean txCancelled = false;
      boolean rxCancelled = false;
      if (mBluetoothOPP != null) {
         synchronized (mSendingFileList) {
            Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
            while (iter.hasNext()) {
               BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
               if (fileInfo != null) {
                  mBluetoothOPP.cancelTransfer(fileInfo.getName());
                  fileInfo.setDone(fileInfo.getTotal());
                  fileInfo.deleteIfVCard(mActivity);
                  txCancelled = true;
               }//if fileInfo
            }//while iter.hasNext
         }//synchronized (mSendingFileList)
         synchronized (mReceivingContactsList) {
            Iterator<BluetoothObexTransferFileInfo> iter = mReceivingContactsList.iterator();
            while (iter.hasNext()) {
               BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
               if (fileInfo != null) {
                  /* Send a Cancel and delete the temporary file if vCard file
                   *  was created
                   */
                  mBluetoothOPP.cancelTransfer(fileInfo.getName());
                  fileInfo.setDone(fileInfo.getTotal());
                  fileInfo.deleteIfVCard(mActivity);
                  rxCancelled = true;
               }//if fileInfo
            }//while iter.hasNext
         }//synchronized (mSendingFileList)
      }
      Cleanup();
      if (V) {
         String cancelString = new String();
         if ( (rxCancelled) && (txCancelled)) {
            cancelString = mActivity.getResources().getString(R.string.cancel_tx_rx_transfer);
         } else if (txCancelled) {
            cancelString = mActivity.getResources().getString(R.string.cancel_tx_transfer);
         } else if (rxCancelled) {
            cancelString = mActivity.getResources().getString(R.string.cancel_rx_transfer);
         }
         if (TextUtils.isEmpty(cancelString) == false) {
            Toast.makeText(mActivity, cancelString, Toast.LENGTH_LONG).show();
         }
      }
   }

   public void progressDone() {
      if (V) {
         Toast.makeText(mActivity, R.string.transfer_complete, Toast.LENGTH_SHORT).show();
      }
      Cleanup();
   }

   public void onPanelClosed(Panel panel) {
      String panelName = mActivity.getResources().getResourceEntryName(panel.getId());
      if (V) {
         Log.v(TAG, "Panel [" + panelName + "] closed");
      }
   }

   public void onPanelOpened(Panel panel) {
      String panelName = mActivity.getResources().getResourceEntryName(panel.getId());
      if (V) {
         Log.v(TAG, "Panel [" + panelName + "] opened");
      }
   }


   public void updateProgressText() {
      String szTxString = "";
      int txCount = 0;
      int rxCount = 0;
      synchronized (mSendingFileList) {
         txCount = mSendingFileList.size();
         if ( txCount > 0) {
            BluetoothObexTransferFileInfo sendFile = (BluetoothObexTransferFileInfo) mSendingFileList.get(0);
            if ( txCount == 1) {
               szTxString = mActivity.getResources().getString(R.string.sending_progress_one, sendFile.getDisplayName() + " (" + mProgressPercent + "%)");
            } else {
               szTxString = mActivity.getResources().getString(R.string.sending_progress_more, sendFile.getDisplayName() + " (" + mProgressPercent + "%) of " + txCount);
            }
         }
      }
      String szRxString = "";
      synchronized (mReceivingContactsList) {
         rxCount = mReceivingContactsList.size();
         if ( rxCount > 0) {
            if (txCount > 0) {
               szTxString += ", ";
            }

            if ( rxCount == 1) {
               szRxString = mActivity.getResources().getString(R.string.get_progress_one);
            } else {
               szRxString = mActivity.getResources().getString(R.string.get_progress_more, (String)(""+rxCount));
            }
         }
      }
      updateProgressText(szTxString + szRxString);
   }

   public void updateProgressText(String szText) {
      if (mTopPanel != null) {
         //Log.v(TAG, "updateProgressText: " + szText);
         mProgressText.setText(mActivity.getResources().getString(R.string.transfer_progress, szText));
      }
   }
   /*************************************************************************
              Panel Related - End
   **************************************************************************/


   /* onProgressIndication
    *  This routine will handle the PROGRESS_ACTION broadcast and
    *  updates the progress if the progress was for one of its
    *  file.
    *
    * @param fileName: The name of the file that needs to be sent.
    * @param totalBytes:  Total number of bytes of the file
    * @param bytesDone:    The name of the Remote Bluetooth Device
    * to which the contact has to be sent
    *
    * @return None.
    */
   public void onProgressIndication(String fileName, int bytesTotal, int bytesDone) {
      synchronized (mSendingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  fileInfo.setDone((long)bytesDone);
                  //fileInfo.setTotal((long)bytesTotal);
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mSendingFileList)
      synchronized (mReceivingContactsList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mReceivingContactsList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  fileInfo.setDone(bytesDone);
                  //fileInfo.setTotal(bytesTotal);
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mReceivingContactsList)
      updateProgress();
   }

   public void onTransmitCompleteIndication(String fileName, boolean success, String errorString) {

      if( false == success ) {
            Toast.makeText(mActivity, "Object Send failed!\n", Toast.LENGTH_LONG).show();
            Cleanup();
            return;
      }
      synchronized (mSendingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  /* Set Done = total and let the updateprogress take care of remove the file from the list */
                  fileInfo.setDone(fileInfo.getTotal());
                  fileInfo.deleteIfVCard(mActivity);
                  iter.remove();
                  break;
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mSendingFileList)
      updateProgress();
   }

   public void onReceiveCompleteIndication(String fileName, boolean success) {

      if( false == success ) {
            Toast.makeText(mActivity, "Object Receive failed!", Toast.LENGTH_LONG).show();
            Cleanup();
            return;
      }
      synchronized (mReceivingContactsList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mReceivingContactsList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  /* Set Done = total and let the updateprogress take care of remove the file from the list */
                  fileInfo.setDone(fileInfo.getTotal());
                  iter.remove();
                  break;
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mReceivingContactsList)
      updateProgress();
   }

   public boolean isTransferinProgress() {
      if ( (mSendingFileList.size() > 0) || (mReceivingContactsList.size()>0)) {
         return true;
      }
      return false;
   }

   public void updateProgress() {
      long currentTxDone =0;
      long currentTxTotal =0;
      long currentRxDone =0;
      long currentRxTotal =0;
      String szTitle;
      synchronized (mSendingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               long done=fileInfo.getDone();
               long total = fileInfo.getTotal();
               /* Delete if the total = 0 or if the done >= total */
               if (total > 0){
                  if (done >= total) {
                     done = total;
                  }
                  currentTxTotal += total ;
                  currentTxDone += done;
               }
            }//if fileInfo
         }//while iter.hasNext
         if (currentTxTotal>0) {
            mProgressPercent = (int) (currentTxDone*100) / (int)currentTxTotal;
         } else {
            mProgressPercent = (100);
         }
      }//synchronized (mSendingFileList)
      synchronized (mReceivingContactsList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mReceivingContactsList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               long done=fileInfo.getDone();
               long total = fileInfo.getTotal();
               /* Update the database if total >0 and if the done >= total */
               if (total > 0){
                  if (done >= total) {
                     done = total;
                  }
                  currentRxTotal += total ;
                  currentRxDone += done;
               }
            }//if fileInfo
         }//while iter.hasNext
      }//synchronized (mReceivingContactsList)
      if ( (currentTxTotal + currentRxTotal)>0) {
         mProgressPercent = ((int)(currentRxDone+currentTxDone)*100) / (int)(currentRxTotal+currentTxTotal);
      } else {
         mProgressPercent = (100);
      }
      setProgress();
   }

   /* Invoke createVCardFile will create a vcard file.
   *
   * @param String The Name of the vCard file
   *
   * @return String: notEmpty : If the file was created
   *                EmptyString: If the vCard file was not created
   */
   public String createVCardFile(String vCardString, String vCardName) {
      String returnFileName = "";
      String vCardFileName = "";
      FileOutputStream fos = null;
      OutputStreamWriter osw = null;

      try {
         /* openFileOutput creates an Application's private file, is
          *  this fine?
          */
         /* Create a StreamWriter */
         String filename = (vCardName == "") ? "btopp_"+System.currentTimeMillis()+".vcf" : vCardName+".vcf";
         if (useSdCardForVCard == false) {

            /*
             * If not using the SD card, then  save it the application path:
             */

            /* Use openFileOuput without the path */
            fos = mActivity.openFileOutput(filename, Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);

            File file  = mActivity.getFileStreamPath(filename);
            vCardFileName =  file.getCanonicalPath();

         } else if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

            File dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp/bt");
            dir.mkdirs();
            vCardFileName =  dir.getAbsolutePath() + filename;
            File newFile = new File(vCardFileName);
            if (newFile.createNewFile() == true) {
               fos = new FileOutputStream(newFile);
            }
         } else {
            /* Use sdcard set and there is no sd card on the device, so return */
            fos = null;
            return "";
         }

         if (fos != null) {
            FileDescriptor fd = fos.getFD();
            osw = new OutputStreamWriter(fos);

            /* Write the vCard String into the file */
            if (osw != null) {
               osw.write(vCardString);
               osw.flush();
            }

            if (fd != null) {
               fd.sync();
            }

            returnFileName = vCardFileName;
            if (V) {
               Log.v(TAG, "Write vcf file : " + returnFileName);
            }
         }
      } catch (IOException ioe) {
         ioe.printStackTrace();
      } finally {
         try {
            /* Clean up after the write */
            if (osw != null) {
               osw.close();
            }
            if (fos != null) {
               fos.close();
            }
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      return returnFileName;
   }

   /* sendContact:
    *  This routine will make a vCard File and send's the file over
    *  Bluetooth OPP to the specified bluetooth Address
    *
    * @param uri: The Contact's uri
    * @param bluetoothDeviceAddress:  The Bluetooth address of the remote Device to
    * which the contact has to be sent
    *
    * @return None.
    */
   public void sendContact (Uri uri,
                            String bluetoothDeviceAddress
                           ) {
      boolean vCardCreatedOk = true;

      if (isEnabled()) {
         VCardManager vManager = new VCardManager(mActivity, uri);
         if (vManager != null) {
            String vcfFile = createVCardFile(vManager.getData(), vManager.getName());
            if (TextUtils.isEmpty(vcfFile) == false) {
               sendFile(vcfFile, bluetoothDeviceAddress);
               /* Update the Display name to the name of the contact */
               Log.v(TAG, "sendContact : Sending Contact : " + vcfFile + " vname: " + vManager.getName());
               synchronized (mSendingFileList) {
                  Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
                  while (iter.hasNext()) {
                     BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
                     if (fileInfo != null) {
                        if (vcfFile.equals(fileInfo.getName())) {
                           fileInfo.setDisplayName(vManager.getName());
                           break;
                        }
                     }//if fileInfo != null
                  }//while iter.hasNext
               }//synchronized (mSendingFileList)
            }
         }
      }//isEnabled
   }

   /* getContact: This routine will make a vCard File and send
    *  the file over Bluetooth OPP to the specified bluetooth
    *  Address
    *
    * @param bluetoothDeviceAddress:  The Bluetooth address of the remote Device to
    * which the contact has to be sent
    *
    * @return None.
    */
   public void getContact (String bluetoothDeviceAddress) {
      if (isEnabled()) {
         String vcfFile = "";
         vcfFile = createVCardFile("", "");
         if (TextUtils.isEmpty(vcfFile) == false) {
            String rxFileName = null;
            if (V) {
               Log.v(TAG, "pullBusinessCard : Receive File : " + vcfFile + " from deviceAddress : " + bluetoothDeviceAddress);
            }
            if (null != mBluetoothOPP) {
               rxFileName = mBluetoothOPP.pullBusinessCard(bluetoothDeviceAddress, vcfFile);
            }

            if (null != rxFileName){
               BluetoothObexTransferFileInfo fileinfo = new BluetoothObexTransferFileInfo(rxFileName);

               /** Dummy TEST ONLY */
               fileinfo.setTotal(1000);
               synchronized (mReceivingContactsList) {
                  mReceivingContactsList.add(fileinfo);
               }
               registerBluetoothOBexIntentHandler();
               setupPanel();
            }
         }//vcfFile non empty
      }//isEnabled
   }

   /* sendMedia: This routine will convert Uri to String for the media files to be sent.
   *
   * @param uri: The Uri of the media that needs to be sent.
   * @param bluetoothDeviceAddress: The Bluetooth address of the remote Device to
   * which the contact has to be sent
   * @param a: The activity by which this routine is called
   *
   * @return None.
   */
   public void sendMedia (Uri uri,
                          String bluetoothDeviceAddress,
                          Activity a) {

       // Obtain the file name from media content Uri
       String fileName = new String();
       Cursor c = a.managedQuery(uri, null, null, null, null);
       if (c.moveToFirst()) {
           fileName = c.getString(c.getColumnIndexOrThrow(Audio.Media.DATA));
       }
       sendFile(fileName, bluetoothDeviceAddress);
   }

   /* sendFile
   *  This routine will do the following:
   *  1. Register for the Progress and Complete notification
   *  2. Initiate the OPP-Client Push
   *  3. Launch the Panel to display the Progress and allow user to
   *  cancel.
   *
   * @param fileName: The name of the file that needs to be sent.
   * @param bluetoothDeviceAddress:  The Bluetooth address of the remote Device to
   * which the contact has to be sent
   * @param bluetoothDeviceName:    The name of the Remote Bluetooth Device to
   * which the contact has to be sent
   *
   * @return None.
   */
   public void sendFile (String fileName,
                         String bluetoothDeviceAddress) {
      boolean bStarted = false;

      if (!isEnabled()) {
         return;
      }

      if (V) {
         Log.v(TAG, "sendFile : Send File Name: " + fileName + " to deviceAddress : " + bluetoothDeviceAddress);
      }

      if (true != TextUtils.isEmpty(fileName)) {
         /* Initate the push */
         if (null != mBluetoothOPP) {
            bStarted = mBluetoothOPP.pushObject(bluetoothDeviceAddress, fileName);
         }

         if (true == bStarted){
            BluetoothObexTransferFileInfo fileinfo = new BluetoothObexTransferFileInfo(fileName);
            File file = new File(fileName);
            fileinfo.setDisplayName(file.getName());
            long fileSize = file.length();
            fileinfo.setTotal(fileSize);
            synchronized (mSendingFileList) {
               mSendingFileList.add(fileinfo);
            }
            registerBluetoothOBexIntentHandler();
            setupPanel();
         }
         else {
             Toast.makeText(mActivity, "OPP service is not available!", Toast.LENGTH_LONG).show();
             Cleanup();
         }
      }
   }

}
