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

package com.android.contacts.quickcontact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ActionBar;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.BaseSavedState;
import android.view.View.OnClickListener;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.ContactsContract.RawContacts;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.EventDataItem;
import com.android.contacts.common.model.dataitem.OrganizationDataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.model.dataitem.StructuredNameDataItem;
import com.android.contacts.common.model.dataitem.StructuredPostalDataItem;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.text.TextUtils;
import android.content.ContentValues;
import com.android.contacts.R;
import android.util.Log;
import android.content.SharedPreferences;
import com.android.contacts.RcsApiManager;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar.IMAGE_TYPE;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Profile;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.TelephoneModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.profile.callback.ProfileListener;
import com.suntek.mway.rcs.client.api.profile.impl.ProfileApi;

public class QrcodeInfoSettingActivity extends Activity {

    private static final String TAG = "QrcodeinfosettingActivity";

    private static final int DIALOG_PRIVATE_ID = 11;
    private static final int DIALOG_BUSINESS_ID = 12;
    private static final int DIALOG_COMFIR_FOR_PROFILE_UPDATE = 13;
    private static final int PRIVATE_ID = 0;
    private static final int BUSINESS_ID = 1;
    private static final int GROUP_MAX_CONUNT = 2;
    private static final int ITME_MAX_COUNT = 20;
    private static final int REQUEST_EDIT_CONTACT = 1;

    private static final int QRCODE_HOMENUMBER = 0;
    private static final int QRCODE_PERSONAL_NUMBER = 1;
    private static final int QRCODE_WORK_NUMBER = 2;
    private static final int QRCODE_OTHER_NUMBER = 3;
    private static final int QRCODE_HOME_ADDRESS = 4;
    private static final int QRCODE_EMAIL = 5;
    private static final int QRCODE_BIRTHDAY = 6;
    private static final int QRCODE_COMPANY_NAME = 7;
    private static final int QRCODE_COMPANY_TITLE = 8;
    private static final int QRCODE_COMPANY_NUMBER = 9;
    private static final int QRCODE_COMPANY_ADDRESS = 10;
    private static final int QRCODE_COMPANY_FAX = 11;

    private Context mContent;
    private LayoutInflater mInflater;
    private Uri mCurrContactUri;
    private ExpandableListView expandableListView;
    private List<String> group_list = new ArrayList<String>();
    private List<List<String>> item_list = new ArrayList<List<String>>();
    private List<List<String>> item_list2 = new ArrayList<List<String>>();
    private boolean[][] isDataCheck = new boolean[GROUP_MAX_CONUNT][ITME_MAX_COUNT];
    private String company = "";
    private String title = "";
    private String mContactName = null;
    private int[][] contactType = new int[GROUP_MAX_CONUNT][ITME_MAX_COUNT];
    private RawContact mRawContact;
    private boolean isHasBusiness = false;
    private Bundle mChoiceSet;
    private MyExpandableListViewAdapter mAdapter;
    private int index1 = 0;
    private boolean isCheck = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Bundle bundle = new Bundle();
        bundle = getIntent().getExtras();
        mRawContact = (RawContact) bundle.getParcelable("raw_contact");
        mContactName = bundle.getString("contact_name");
        mCurrContactUri = Uri.withAppendedPath(RawContacts.CONTENT_URI,
                String.valueOf(mRawContact.getId()));
        setContentView(R.layout.qrinfo_editor_container);
        ActionBar actionBar = getActionBar();
        View customActionBarView = mInflater.inflate(
                R.layout.editor_custom_action_bar, null);
        View saveMenuItem = customActionBarView
                .findViewById(R.id.save_menu_item);
        saveMenuItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // mFragment.doSaveAction();
                showDialog(DIALOG_COMFIR_FOR_PROFILE_UPDATE);
            }
        });
        TextView title = (TextView) customActionBarView
                .findViewById(R.id.title);

        title.setText(getResources()
                .getString(R.string.qrcode_setting_complete));
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setCustomView(customActionBarView);
        setContentView(R.layout.qrcode_info_setting);
        mChoiceSet = new Bundle();
        mContent = getApplicationContext();
        initContactData();
        initQrinfoEditorView();

    }

    private void initContactData() {

        group_list.add(getString(R.string.qrcode_private_info));
        group_list.add(getString(R.string.qrcode_business_info));
        List<String> group1_list_key = new ArrayList<String>();
        List<String> group1_list_value = new ArrayList<String>();
        List<String> group2_list_key = new ArrayList<String>();
        List<String> group2_list_value = new ArrayList<String>();

        int group1Typeindex = 0;
        int group2Typeindex = 0;
        int otherNumberCount = 0;
        if (mRawContact == null)
            return;

        SharedPreferences myQrcodeSharedPreferences = getSharedPreferences(
                "QrcodePersonalCheckState", Activity.MODE_PRIVATE);
        String value = myQrcodeSharedPreferences.getString("value", "");
        String[] initChecked = value.split(",");
        int total = myQrcodeSharedPreferences.getInt("total", 0);
        String myAccountNumber = "+8613522631112";
        try {
            myAccountNumber = RcsApiManager.getRcsAccoutApi()
                    .getRcsUserProfileInfo().getUserName();
        } catch (ServiceDisconnectedException e1) {
            Log.w("RCS_UI", e1);
        }

        group1_list_key.add(getString(R.string.rcs_qr_name));
        group1_list_value.add(mContactName);
        group1_list_key.add(getString(R.string.rcs_qr_number));
        group1_list_value.add(myAccountNumber);
        for (DataItem dataItem : mRawContact.getDataItems()) {

            final String mimeType = dataItem.getMimeType();
            final ContentValues entryValues = dataItem.getContentValues();

            if (dataItem instanceof PhoneDataItem) {
                int phoneType = ((PhoneDataItem) dataItem).getContentValues()
                        .getAsInteger(Phone.TYPE);
                if (Phone.TYPE_WORK == phoneType
                        && !TextUtils.isEmpty(((PhoneDataItem) dataItem)
                        .getNumber())) {
                    group2_list_key.add(getString(R.string.rcs_company_number));
                    group2_list_value.add(((PhoneDataItem) dataItem).getNumber());
                    contactType[BUSINESS_ID][group2Typeindex++] = QRCODE_COMPANY_NUMBER;
                    Log.d(TAG, "rcs_company_number=" + QRCODE_COMPANY_NUMBER);

                } else if (Phone.TYPE_FAX_WORK == phoneType
                        && !TextUtils.isEmpty(((PhoneDataItem) dataItem).getNumber())) {
                    group2_list_key.add(getString(R.string.rcs_company_fax));
                    group2_list_value.add(((PhoneDataItem) dataItem)
                            .getNumber());
                    contactType[BUSINESS_ID][group2Typeindex++] = QRCODE_COMPANY_FAX;
                    Log.d(TAG, "rcs_company_fax=" + QRCODE_COMPANY_FAX);
                }
            } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)
                    && !TextUtils.isEmpty(entryValues.getAsString(Email.ADDRESS))) {
                group2_list_key.add(getString(R.string.rcs_email_address));
                group2_list_value.add(entryValues.getAsString(Email.ADDRESS));
                contactType[BUSINESS_ID][group2Typeindex++] = QRCODE_EMAIL;
                Log.d(TAG, "rcs_email_address=" + QRCODE_EMAIL);
            } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)
                    && !TextUtils.isEmpty(entryValues.getAsString(Organization.COMPANY))) {
                String company = entryValues.getAsString(Organization.COMPANY);
                group2_list_key.add(getString(R.string.rcs_company_name));
                group2_list_value.add(company);
                contactType[BUSINESS_ID][group2Typeindex++] = QRCODE_COMPANY_NAME;
                Log.d(TAG, "rcs_company_name=" + QRCODE_COMPANY_NAME);
                String title = ((OrganizationDataItem) dataItem).getTitle();
                group2_list_key.add(getString(R.string.rcs_company_tilte));
                group2_list_value.add(title);
                contactType[BUSINESS_ID][group2Typeindex++] = QRCODE_COMPANY_TITLE;
                Log.d(TAG, "rcs_company_tilte=" + QRCODE_COMPANY_TITLE);
                Log.d("RCSUtil", "company=" + company);
                Log.d("RCSUtil", "title=" + title);
            }
        }
        for (int i = 0; i < group2_list_key.size(); i++) {
            for (int j = 0; j < total; j++) {
                if (group2_list_key.get(i).equals(initChecked[j])) {
                    isDataCheck[BUSINESS_ID][i] = true;
                }
            }
        }

        item_list.add(group1_list_key);
        item_list.add(group2_list_key);
        item_list2.add(group1_list_value);
        item_list2.add(group2_list_value);
    }

    private Profile saveContactInfo() {
        int index = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GROUP_MAX_CONUNT; i++) {
            int childCount = mAdapter.getChildrenCount(i);
            Log.d(TAG, "childCount" + childCount);
            for (int j = 0; j < childCount; j++) {
                if (isDataCheck[BUSINESS_ID][j] == true) {
                    Log.d(TAG, "isDataCheck[i][j] is" + i + j
                            + isDataCheck[i][j]);
                    if (i == BUSINESS_ID) {
                        isHasBusiness = true;
                    }
                    String[] value = null;
                    value = new String[] { String.valueOf(contactType[i][j]),
                            item_list2.get(i).get(j) };
                    sb.append(item_list.get(i).get(j)).append(",");
                    mChoiceSet.putStringArray(String.valueOf(index), value);
                    Log.d(TAG, "mChoiceSet is" + item_list2.get(i).get(j));
                    index++;
                }
            }
        }

        if (mChoiceSet.size() == 0) {
            Log.d(TAG, "mChoiceSet is null");
            return null;
        } else {
            saveSharePrefence(index, sb.toString(), isHasBusiness);
            return converToProfile();
        }
    }

    private Profile converToProfile() {

        if (mRawContact == null)
            return null;

        Profile profile = new Profile();

        String myAccountNumber = "+8613522631112";
        try {
            myAccountNumber = RcsApiManager.getRcsAccoutApi()
                    .getRcsUserProfileInfo().getUserName();
        } catch (ServiceDisconnectedException e1) {
            Log.w("RCS_UI", e1);
        }

        profile.setAccount(myAccountNumber);
        profile.setOtherTels(new ArrayList<TelephoneModel>());

        for (DataItem dataItem : mRawContact.getDataItems()) {

            final String mimeType = dataItem.getMimeType();
            final ContentValues entryValues = dataItem.getContentValues();

            if (dataItem instanceof StructuredNameDataItem) {
                String firstName = ((StructuredNameDataItem) dataItem)
                        .getGivenName();
                if (TextUtils.isEmpty(firstName)) {
                    return null;
                }
                profile.setFirstName(firstName);

                profile.setLastName(((StructuredNameDataItem) dataItem)
                        .getFamilyName());
            }
        }
        Set<String> set = mChoiceSet.keySet();
        Iterator<String> iterator = set.iterator();
        while (iterator.hasNext()) {
            String[] contactInfo = mChoiceSet.getStringArray(iterator.next());
            if (null != contactInfo) {
                String value = contactInfo[1];
                int contactType = Integer.valueOf(contactInfo[0]);
                TelephoneModel tele;
                if (contactType == QRCODE_HOMENUMBER
                        && profile.getOtherTels().size() < 6) {
                    tele = new TelephoneModel();
                    tele.setType(TelephoneModel.TYPE_HOME);
                    tele.setTelephone(value);
                    profile.getOtherTels().add(tele);
                    tele = new TelephoneModel();
                    tele.setType(TelephoneModel.TYPE_FIXED);
                    tele.setTelephone(value);
                    profile.getOtherTels().add(tele);
                } else if (contactType == QRCODE_WORK_NUMBER
                        && profile.getOtherTels().size() < 6) {
                    profile.setCompanyTel(value);
                } else if (contactType == QRCODE_WORK_NUMBER) {
                    tele = new TelephoneModel();
                    tele.setType(TelephoneModel.TYPE_WORK);
                    tele.setTelephone(value);
                    profile.getOtherTels().add(tele);
                } else if (contactType == QRCODE_OTHER_NUMBER
                        && profile.getOtherTels().size() < 6) {
                    tele = new TelephoneModel();
                    tele.setType(TelephoneModel.TYPE_OTHER);
                    tele.setTelephone(value);
                    profile.getOtherTels().add(tele);
                } else if (contactType == QRCODE_HOME_ADDRESS) {
                    profile.setHomeAddress(value);
                } else if (contactType == QRCODE_PERSONAL_NUMBER
                        && profile.getOtherTels().size() < 6) {
                    tele = new TelephoneModel();
                    tele.setType(TelephoneModel.TYPE_MOBILE);
                    tele.setTelephone(value);
                    profile.getOtherTels().add(tele);
                } else if (contactType == QRCODE_EMAIL) {
                    profile.setEmail(value);
                } else if (contactType == QRCODE_BIRTHDAY) {
                    profile.setBirthday(value);
                } else if (contactType == QRCODE_COMPANY_NAME) {
                    profile.setCompanyName(value);
                } else if (contactType == QRCODE_COMPANY_TITLE) {
                    profile.setCompanyDuty(value);
                } else if (contactType == QRCODE_COMPANY_NUMBER) {
                    profile.setCompanyTel(value);
                } else if (contactType == QRCODE_COMPANY_ADDRESS) {
                    profile.setCompanyAddress(value);
                } else if (contactType == QRCODE_COMPANY_FAX
                        && profile.getCompanyFax() == null) {
                    profile.setCompanyFax(value);
                }
            }
        }
        return profile;
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        Dialog dialog = new Dialog(this);
        switch (id) {
        case DIALOG_COMFIR_FOR_PROFILE_UPDATE:
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.rcs_confirom_upload_profile)
                    .setPositiveButton(R.string.btn_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    Profile prfile = saveContactInfo();
                                    if (null != prfile) {
                                        Intent intent = new Intent();
                                        Bundle bundle = new Bundle();
                                        intent.putExtra("isHasBusiness",
                                                isHasBusiness);
                                        bundle.putParcelable("Profile", prfile);
                                        intent.putExtras(bundle);
                                        QrcodeInfoSettingActivity.this
                                                .setResult(RESULT_OK, intent);
                                    } else {
                                        QrcodeInfoSettingActivity.this
                                                .setResult(RESULT_CANCELED);
                                    }
                                    finish();
                                }
                            })
                    .setNegativeButton(R.string.btn_cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    finish();
                                }
                            }).create();
            break;
        case DIALOG_PRIVATE_ID:
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.qrcode_setting_private_message)
                    .setPositiveButton(R.string.btn_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    startActivityForResult(
                                            new Intent(Intent.ACTION_EDIT,
                                            mCurrContactUri),
                                            REQUEST_EDIT_CONTACT);
                                }
                            })
                    .setNegativeButton(R.string.btn_cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    finish();
                                }
                            }).create();
            break;

        case DIALOG_BUSINESS_ID:
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.qrcode_setting_business_message)
                    .setPositiveButton(R.string.btn_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    startActivityForResult(
                                            new Intent(Intent.ACTION_EDIT,
                                            mCurrContactUri),
                                            REQUEST_EDIT_CONTACT);
                                }

                            })
                    .setNegativeButton(R.string.btn_cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {

                                }
                            }).create();
            break;
        }
        return dialog;
    }

    public static class ViewCache {
        public int position;
        public TextView label;
        public TextView data;
        public CheckBox check;

    }

    public void initQrinfoEditorView() {
        expandableListView = (ExpandableListView) findViewById(R.id.Qrcodeinfo_expendlist);
        expandableListView
                .setOnGroupExpandListener(new OnGroupExpandListener() {

                    @Override
                    public void onGroupExpand(int position) {
                        // TODO Auto-generated method stub
                        if (BUSINESS_ID == position
                                && 0 == item_list.get(position).size()) {
                            showDialog(DIALOG_BUSINESS_ID);
                        } else if (PRIVATE_ID == position
                                && 0 == item_list.get(position).size()) {
                            showDialog(DIALOG_PRIVATE_ID);
                        }
                    }
                });
        mAdapter = new MyExpandableListViewAdapter(this);
        expandableListView.setAdapter(mAdapter);
        String myAccountNumber = null;
        try {
            myAccountNumber = RcsApiManager.getRcsAccoutApi()
                    .getRcsUserProfileInfo().getUserName();
        } catch (ServiceDisconnectedException e1) {
            Log.w("RCS_UI", e1);
        }
        if (null == myAccountNumber || null == mContactName) {
            showDialog(DIALOG_PRIVATE_ID);
        }
    }

    class MyExpandableListViewAdapter extends BaseExpandableListAdapter {

        private Context context;

        public MyExpandableListViewAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getGroupCount() {
            return group_list.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return item_list.get(groupPosition).size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return group_list.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return item_list.get(groupPosition).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                View convertView, ViewGroup parent) {
            GroupHolder groupHolder = null;
            if (convertView == null) {
                convertView = (View) getLayoutInflater().from(context).inflate(
                        R.layout.contacts_qrcodeinfo_subexpendview, null);
                groupHolder = new GroupHolder();
                groupHolder.txt = (TextView) convertView
                        .findViewById(R.id.qrcode_info_txt);
                groupHolder.img = (ImageView) convertView
                        .findViewById(R.id.qrcode_info_img);
                Log.d(TAG, "mRawContact:1" + isExpanded);
                convertView.setTag(groupHolder);
            } else {
                Log.d(TAG, "mRawContact:2" + isExpanded);
                groupHolder = (GroupHolder) convertView.getTag();
            }
            groupHolder.txt.setText(group_list.get(groupPosition));
            if (isExpanded) {
                groupHolder.img
                        .setBackgroundResource(R.drawable.ic_menu_expander_minimized_holo_light);
            } else {
                groupHolder.img
                        .setBackgroundResource(R.drawable.ic_menu_expander_maximized_holo_light);
            }
            return convertView;
        }

        @Override
        public View getChildView(final int groupPosition,
                final int childPosition, boolean isLastChild, View convertView,
                ViewGroup parent) {
            ItemHolder itemHolder = null;
            if (convertView == null) {
                convertView = (View) getLayoutInflater().from(context).inflate(
                        R.layout.item_qrcode_data, null);
                itemHolder = new ItemHolder();
                itemHolder.txt = (TextView) convertView
                        .findViewById(R.id.label);
                itemHolder.img = (TextView) convertView.findViewById(R.id.data);
                itemHolder.checkbox = (CheckBox) convertView
                        .findViewById(R.id.checkbox);

                convertView.setTag(itemHolder);

            } else {
                itemHolder = (ItemHolder) convertView.getTag();

            }
            // isCheck = true;
            itemHolder.txt.setText(item_list.get(groupPosition).get(
                    childPosition));
            itemHolder.img.setText(item_list2.get(groupPosition).get(
                    childPosition));
            if (groupPosition == PRIVATE_ID) {
                itemHolder.checkbox.setVisibility(View.GONE);
            } else {
                itemHolder.checkbox.setVisibility(View.VISIBLE);
            }
            itemHolder.checkbox
                    .setChecked(isDataCheck[groupPosition][childPosition]);
            itemHolder.checkbox
                    .setOnClickListener(new CheckBox.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            // TODO Auto-generated method stub
                            CheckBox cb = (CheckBox) v;
                            if (cb.isChecked()) {
                                isDataCheck[groupPosition][childPosition] = true;
                            } else {
                                isDataCheck[groupPosition][childPosition] = false;
                            }
                        }
                    });
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }

    class GroupHolder {
        public TextView txt;
        public ImageView img;
    }

    class ItemHolder {
        public TextView img;
        public TextView txt;
        public CheckBox checkbox;
    }

    private void saveSharePrefence(int index, String result,
            boolean isHasBusiness) {
        SharedPreferences myQrcodeSharedPreferences = getSharedPreferences(
                "QrcodePersonalCheckState", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = myQrcodeSharedPreferences.edit();

        editor.putInt("total", index);
        editor.putBoolean("isHasBusiness", isHasBusiness);
        editor.putString("value", result);
        editor.commit();
    }
}
