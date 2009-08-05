/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.Contacts;
import android.syncml.pim.PropertyNode;
import android.syncml.pim.VDataBuilder;
import android.syncml.pim.VNode;
import android.syncml.pim.vcard.ContactStruct;
import android.syncml.pim.vcard.VCardComposer;
import android.syncml.pim.vcard.VCardException;
import android.syncml.pim.vcard.VCardParser;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import android.text.TextUtils;


/**
 * VCardManager to convert vCard2.1 to and from Android Contact
 * database values.
 * This supports only one contact
 *
 * @hide
 */
public class VCardManager {
   static private final String TAG = "VCardManager";
   static private final boolean V = false;
   static private final boolean ADD_CONTACT_TO_MYCONTACTSGROUP = true;

   /**
    *  Contains the Contact Methods for the contact
    *  that goes into the Contacts.People.ContactMethods
    */
   private ArrayList<ContentValues> mContactMethodList;
   /**
    *  Contains the Phones Numbers for the contact
    *  that goes into the Contacts.People.Phones
    */
   private ArrayList<ContentValues> mPhoneList;
   /**
    *  Contains the Organization information for the contact
    *  that goes into the Contacts.People.Organization
    */
   private ArrayList<ContentValues> mOrganizationList;
   /**
    *  Contains the Contact information that goes into the
    *  Contacts.People
    */
   private ContentValues mPeople;

   /**
    *  String that contains the vCard
    */
   private final String mData;

   private final ContentResolver mResolver;
   private final Context mContext;
   /* TODO: Remove this or set to false when testing is done*/

   /**
    *  VCardManager to take the vCard String and parse the
    *  information into variables
    */
   public VCardManager(Context context, String data) {
      mContext = context;
      mResolver = context.getContentResolver();
      mData = data;
      parse(mData);
   }

   /**
    *  VCardManager to take the Contact Uri and parse the
    *  information into variables
    */
   public VCardManager(Context context, Uri uri) {
      mContext = context;
      mResolver = context.getContentResolver();
      mData = loadData(uri);
      parse(mData);
   }

   /**
    *  API to get the vCard String
    *
    *  @return the value
    */
   public String getData() {
      return mData;
   }

   /**
    * Get Name of the contact.
    *
    * @return the Name
    */
   public String getName() {
       Log.v(TAG, "getName");
      return(String) mPeople.get(Contacts.People.NAME);
   }

   /**
    * Save content to content provider.
    *
    * @return Uri of the Contact created in Contact database
    */
   public Uri save() {
      try {
         /**
          *  Create the Contact in the data base
          */
         Uri uri;
         if (ADD_CONTACT_TO_MYCONTACTSGROUP)
         {
             uri = Contacts.People.createPersonInMyContactsGroup(mResolver, mPeople);
         }
         else
         {
             uri = mResolver.insert(Contacts.People.CONTENT_URI, mPeople);
         }
         /**
          *  Add the phone list
          */
         ContentValues[] phoneArray = new ContentValues[mPhoneList.size()];
         mResolver.bulkInsert(Uri.withAppendedPath(uri, "phones"), mPhoneList.toArray(phoneArray));

         /**
          *  Add the Organization list
          */
         ContentValues[] organizationArray = new ContentValues[mOrganizationList.size()];
         mResolver.bulkInsert(Uri.withAppendedPath(uri, "organizations"), mOrganizationList
                              .toArray(organizationArray));

         /**
          *  Add the Contact Methods
          */
         ContentValues[] contactMethodArray = new ContentValues[mContactMethodList.size()];
         mResolver.bulkInsert(Uri.withAppendedPath(uri, "contact_methods"), mContactMethodList
                              .toArray(contactMethodArray));
         return uri;
      } catch (SQLiteException e) {
         //checkSQLiteException(e);
      }
      return null;
   }

   /**
    * Parse the vCard String into local member variables.
    *
    * @return None
    */
   private void parse(String data) {
      VCardParser mParser = new VCardParser();
      VDataBuilder builder = new VDataBuilder();

      mContactMethodList = new ArrayList<ContentValues>();
      mPhoneList = new ArrayList<ContentValues>();
      mOrganizationList = new ArrayList<ContentValues>();
      mPeople = new ContentValues();

      if (data != null) {
         /* Try to parse with the original VCard parser implementation */
         try {
            mParser.parse(data, builder);
         } catch (VCardException e) {
             /* If the VCard parser threw an exception, try to parse all the fields that can be parsed and
              * ignore the rest, without aborting the parsing.
              */
            LocalVCardParser_V21 mParser21 = new LocalVCardParser_V21();
            try {
               mParser21.parse(new ByteArrayInputStream(data.getBytes()), "US-ASCII", builder);
             } catch (VCardException e1) {
                 Log.e(TAG, e1.getMessage(), e1);
             }
           catch (IOException e1) {
                 Log.e(TAG, e1.getMessage(), e1);
           }
         } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
         }

         for (VNode vnode : builder.vNodeList) {
            setContactsValue(vnode);
         }
      }
   }

   /**
    * Parse each node in the vCard String into local member
    * variables.
    *
    * @return None
    */
   private void setContactsValue(VNode vnode) {
      String title = null;
      String company = null;

      for (PropertyNode prop : vnode.propList) {
         if (prop.propName.equalsIgnoreCase("TITLE") && (prop.propValue != null)) {
            title = prop.propValue;
         }
         if (prop.propName.equalsIgnoreCase("ORG") && (prop.propValue != null)) {
            company = prop.propValue;
         }

         // People.
         if (prop.propName.equalsIgnoreCase("N") && (prop.propValue != null)) {
            mPeople.put(Contacts.People.NAME, prop.propValue);
         }

         // Phone
         if (prop.propName.equalsIgnoreCase("TEL")
             && (prop.propValue != null)) {
            HashSet<String> typeList = new HashSet<String>();
            ContentValues phoneContent = new ContentValues();
            int phoneContentType = -1;

            phoneContent.clear();
            typeList.clear();

            for (String typeStr : prop.paramMap_TYPE) {
               typeList.add(typeStr.toUpperCase());
            }
            if (typeList.contains("FAX")) {
               phoneContentType = -1;
               if (typeList.contains("HOME")) {
                  phoneContentType = Contacts.Phones.TYPE_FAX_HOME;
                  typeList.remove("HOME");
               } else if (typeList.contains("WORK")) {
                  phoneContentType = Contacts.Phones.TYPE_FAX_WORK;
                  typeList.remove("WORK");
               }
               if (phoneContentType != -1) {
                  phoneContent.put(Contacts.Phones.TYPE, phoneContentType);
                  phoneContent.put(Contacts.Phones.NUMBER, prop.propValue);
                  mPhoneList.add(phoneContent);
                  typeList.remove("FAX");
               }
            }
            // Remove the strings that has "VOICE"
            if (typeList.contains("VOICE")) {
               typeList.remove("VOICE");
            }

            for (String typeStr : typeList) {
               phoneContent.clear();
               // The following just to match the type that predefined in
               // contacts.db. If not match, we will save the phone number
               // with one type in phone column
               if (typeStr.equals("HOME")) {
                  phoneContentType = Contacts.Phones.TYPE_HOME;
               } else if (typeStr.equals("WORK")) {
                  phoneContentType = Contacts.Phones.TYPE_WORK;
               } else if (typeStr.equals("FAX")) {
                  phoneContentType = Contacts.Phones.TYPE_FAX_WORK;
               } else if (typeStr.equals("PAGER")) {
                  phoneContentType = Contacts.Phones.TYPE_PAGER;
               } else if (typeStr.equals("CELL")) {
                  phoneContentType = Contacts.Phones.TYPE_MOBILE;
               } else if (typeStr.equals("X-OTHER")) {
                  phoneContentType = Contacts.Phones.TYPE_OTHER;
               } else {
                  phoneContentType = Contacts.Phones.TYPE_CUSTOM;
                  phoneContent.put(Contacts.Phones.LABEL, typeStr);
               } // end if-else
               phoneContent.put(Contacts.Phones.TYPE, phoneContentType);
               phoneContent.put(Contacts.Phones.NUMBER, prop.propValue);
               mPhoneList.add(phoneContent);
            } // end for String typeStr : typeList)
         } // end if prop.propName == TEL


         //Contact method.
         if (prop.propName.equalsIgnoreCase("EMAIL")
             && (prop.propValue != null)) {
            int prefType = 0;
            for (String typeName : prop.paramMap_TYPE) {
               ContentValues mapContactM = new ContentValues();
               int iType;

               /* Usually "PREF" is supposed to be first type, so if PREF is selected,
                * then set them as primary e-mail
                */
               if (typeName.contains("PREF")) {
                  prefType = 1;
                  continue;
               }

               mapContactM.clear();
               mapContactM.put(Contacts.ContactMethods.DATA,
                               prop.propValue.replaceAll(";", " ").trim());
               mapContactM.put(Contacts.ContactMethods.KIND,
                               Contacts.KIND_EMAIL);
               iType = getEmailTypeByName(typeName);
               mapContactM.put(Contacts.ContactMethods.TYPE, iType);
               if (iType == Contacts.ContactMethods.TYPE_CUSTOM) {
                  mapContactM.put(Contacts.ContactMethods.LABEL, typeName);
               }
               mapContactM.put(Contacts.ContactMethods.ISPRIMARY, prefType);
               mContactMethodList.add(mapContactM);
            }
         }// end if prop.propName == EMAIL

         if (prop.propName.equalsIgnoreCase("ADR") && (prop.propValue != null)) {
            ContentValues mapContactM = new ContentValues();

            mapContactM.put(Contacts.ContactMethods.DATA,
                            prop.propValue.replaceAll(";", " ").trim());
            mapContactM.put(Contacts.ContactMethods.KIND,
                            Contacts.KIND_POSTAL);

                String typeName = setToString(prop.paramMap_TYPE);
            int addressType = getAddressTypeByName(typeName);
            mapContactM.put(Contacts.ContactMethods.TYPE, addressType);
            if (addressType == Contacts.ContactMethods.TYPE_CUSTOM) {
               mapContactM.put(Contacts.ContactMethods.LABEL, typeName);
            }
            mContactMethodList.add(mapContactM);
         }// end if prop.propName == ADR
      } //end of for PropertyNode prop

      // Organization
      if ((title != null) || (company != null)) {
         ContentValues organization = new ContentValues();
         organization.put(Contacts.Organizations.COMPANY, company);
         organization.put(Contacts.Organizations.TITLE, title);
         organization.put(Contacts.Organizations.TYPE,
                          Contacts.Organizations.TYPE_WORK);
         mOrganizationList.add(organization);
      } // Organization
   }

   /**
    * Private method to identify E-Mail Type
    *
    * @param ContactMethod Type in a String
    *
    * @return Contacts.ContactMethods.TYPE (int)
    */
   private int getEmailTypeByName(String typeName) {
      if ((typeName.length() == 0) || typeName.equalsIgnoreCase("INTERNET")) {
         return Contacts.ContactMethods.TYPE_HOME;
      } else if (typeName.equalsIgnoreCase("HOME")) {
         return Contacts.ContactMethods.TYPE_HOME;
      } else if (typeName.equalsIgnoreCase("WORK")){
         return Contacts.ContactMethods.TYPE_WORK;
      } else if (typeName.equalsIgnoreCase("X-OTHER")){
         return Contacts.ContactMethods.TYPE_OTHER;
      } else {
         return Contacts.ContactMethods.TYPE_CUSTOM;
      }
   }

   /**
    * Private method to identify Address Type
    *
    * @param ContactMethod Type in a String
    *
    * @return Contacts.ContactMethods.TYPE (int)
    */
   private int getAddressTypeByName(String typeName) {
      if (typeName.length() == 0) {
         return Contacts.ContactMethods.TYPE_HOME;
      } else if (typeName.equalsIgnoreCase("HOME")) {
         return Contacts.ContactMethods.TYPE_HOME;
      } else if (typeName.equalsIgnoreCase("WORK")){
         return Contacts.ContactMethods.TYPE_WORK;
      } else if (typeName.equalsIgnoreCase("X-OTHER")){
         return Contacts.ContactMethods.TYPE_OTHER;
      } else {
         return Contacts.ContactMethods.TYPE_CUSTOM;
      }
   }


   /**
    * Private method to convert String list into a single string
    *
    * @param String list
    *
    * @return String
    */
    private String setToString(Set<String> set) {
      StringBuilder typeListB = new StringBuilder("");
        for (String o : set) {
         typeListB.append(o).append(";");
      }

      String typeList = typeListB.toString();
      if (typeList.endsWith(";")) {
         return typeList.substring(0, typeList.length() - 1);
      } else {
         return typeList;
      }
   }

   /**
    * Private method to read contact database for the contact
    * represented by the uri into local member variables
    *
    * @param uri representation of the contact (in Contacts.People)
    *
    * @return VCard String
    */
   private String loadData(Uri uri) {
      Cursor contactC=null;
      ContactStruct contactStruct = new ContactStruct();
      try {
         // get the Contact
         if (V) {
            Log.v(TAG, "---> loadData - uri : " + uri.toString());
         }
         contactC = mResolver.query(uri,
                                    null,
                                    null,
                                    null,
                                    null);
      } catch (SQLiteException e) {
         // If conact could not be found return NULL
         //throw (e);
         if (V) {
            Log.e(TAG, "loadData - Contact Not found : " + uri.toString());
         }
      }
      contactStruct.name = null;
      if (contactC != null) {
         // get only one contact
         if (contactC.moveToFirst()) {
            // Get people info.
            contactStruct.name = contactC.getString(
                                                   contactC.getColumnIndexOrThrow(Contacts.People.NAME));
            contactStruct.notes.add(contactC.getString(
                                                      contactC.getColumnIndexOrThrow(Contacts.People.NOTES)));
         }
         contactC.close();
         contactC=null;
      }

      if (V) {
         Log.i(TAG, "loadData - Contact Name : " +  contactStruct.name);
      }

      // get the organizations
      Cursor orgC = null;
      try {
         orgC = mResolver.query(Uri.withAppendedPath(uri, "organizations"),
                                null, null, null, null);
      } catch (SQLiteException e) {
         //checkSQLiteException(e);
         orgC=null;
         if (V) {
            Log.i(TAG, "loadData - organizations Not found : " + uri.toString());
         }
      }

      contactStruct.company = null;
      contactStruct.title = null;
      if (orgC != null) {
         if (orgC.moveToFirst()) {
            do {
               contactStruct.company = orgC.getString(
                                                     orgC.getColumnIndexOrThrow(Contacts.Organizations.COMPANY));
               // VCardComposer's createVCard handing of "foldingString" requires a '\n' to end the string
               contactStruct.title = orgC.getString(
                                                   orgC.getColumnIndexOrThrow(Contacts.Organizations.TITLE))+ "\n";
            } while (orgC.moveToNext());
         }
         orgC.close();
         orgC=null;
      }

      // Get phone list.
      Cursor phoneC = null;
      try {
         phoneC = mResolver.query( Uri.withAppendedPath(uri, "phones"),
                                   null, null, null, null);
      } catch (SQLiteException e) {
         //checkSQLiteException(e);
         phoneC=null;
         if (V) {
            Log.i(TAG, "loadData - phones Not found : " + uri.toString());
         }
      }

      String data, label;
      int type, kind, isPrimary;
      if (phoneC != null) {
         if (phoneC.moveToFirst()) {
            do {
               data = phoneC.getString(phoneC.getColumnIndexOrThrow(
                                                                   Contacts.Phones.NUMBER));

               type = phoneC.getInt(phoneC.getColumnIndexOrThrow(
                                                                Contacts.Phones.TYPE));
               label = phoneC.getString(phoneC.getColumnIndexOrThrow(
                                                                    Contacts.Phones.LABEL));
               isPrimary = phoneC.getInt(phoneC.getColumnIndexOrThrow(
                                                                     Contacts.Phones.ISPRIMARY));

               if (isPrimary != 0) {
                  if (contactStruct.name == null || TextUtils.isEmpty(contactStruct.name)) {
                      contactStruct.name = data;
                  }
               }
               if (V) {
                  Log.i(TAG, "loadData - addPhone : " + data);
               }
               contactStruct.addPhone(type, data, label, isPrimary != 0 ? true :
                  false);
            } while (phoneC.moveToNext());
         }
         phoneC.close();
         phoneC=null;
      }

      // Get contact-method list.
      Cursor contactMethodC=null;
      try {
         contactMethodC = mResolver.query(Uri.withAppendedPath(uri, "contact_methods"),
                                          null, null, null, null);
      } catch (SQLiteException e) {
         //checkSQLiteException(e);
         contactMethodC=null;
         if (V) {
            Log.i(TAG, "loadData - contact_methods Not found : " + uri.toString());
         }
      }

      if (contactMethodC != null) {
         if (contactMethodC.moveToFirst()) {
            do {
               kind = contactMethodC.getInt(contactMethodC.getColumnIndexOrThrow(
                                                                                Contacts.ContactMethods.KIND));
               type = contactMethodC.getInt(contactMethodC.getColumnIndexOrThrow(
                                                                                Contacts.ContactMethods.TYPE));
               label = contactMethodC.getString(contactMethodC.getColumnIndexOrThrow(
                                                                                    Contacts.ContactMethods.LABEL));
               data = contactMethodC.getString(contactMethodC.getColumnIndexOrThrow(
                                                                                   Contacts.ContactMethods.DATA));

               // VCardComposer's createVCard handing of "foldingString" requires a '\n' to end the string
               if (kind == Contacts.KIND_POSTAL)
               {
                  data += "\n";
               }
               contactStruct.addContactmethod(kind, type, data, label, false);
            } while (contactMethodC.moveToNext());
         }
         contactMethodC.close();
         contactMethodC=null;
      }
      // Generate vCard data.
      try {
         VCardComposer composer = new VCardComposer();
         return composer.createVCard(contactStruct,
                                     VCardParser.VERSION_VCARD21_INT);
      } catch (VCardException e) {
         return null;
      }
   }
}
