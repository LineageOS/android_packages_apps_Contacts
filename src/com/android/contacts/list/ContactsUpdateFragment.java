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

package com.android.contacts.list;

import com.android.contacts.util.RCSUtil;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import com.android.contacts.R;

/*
 * A dialog to select whether update the contact photo.
 */
public class ContactsUpdateFragment extends DialogFragment {

    public static final String CONTACT_UPDATE_DIALOG_TAG = "contact_update_dialog_tag";

    boolean mUpdateContactPhotosItemSelected;

    public static void show(FragmentManager fragmentManager) {
        ContactsUpdateFragment dialog = new ContactsUpdateFragment();
        // dialog.show(getFragmentManager(), CALL_FILTER_DIALOG_TAG);
        dialog.show(fragmentManager, CONTACT_UPDATE_DIALOG_TAG);
    }

    public ContactsUpdateFragment() {

    }

    private class SelectItem {
        public String mName;
        public String mPref;
        public boolean mIsSelceted;

        public SelectItem(String name, String pref, boolean isSelected) {
            mName = name;
            mPref = pref;
            mIsSelceted = isSelected;
        }
    }

    private SelectItem[] selectItems;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        selectItems = new SelectItem[] { new SelectItem(
                getActivity().getResources().getString(
                        R.string.rcs_wlan_first_connection_per_week),
                RCSUtil.PREF_UPDATE_CONTACT_PHOTOS_WLAN_FIRST_CONNECTION_PER_WEEK,
                prefs.getBoolean(
                        RCSUtil.PREF_UPDATE_CONTACT_PHOTOS_WLAN_FIRST_CONNECTION_PER_WEEK,
                        false)) };
        final SharedPreferences.Editor editor = prefs.edit();
        mUpdateContactPhotosItemSelected = prefs
                .getBoolean(
                        RCSUtil.PREF_UPDATE_CONTACT_PHOTOS_WLAN_FIRST_CONNECTION_PER_WEEK,
                        false);

        final ContactsPhotoUpdateAdapter adapter = new ContactsPhotoUpdateAdapter(
                getActivity());
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                getActivity());
        builder.setAdapter(adapter, null);
        AlertDialog dialog = builder
                .setTitle(
                        getActivity().getResources().getString(
                                R.string.rcs_update_contact_photos))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                for (SelectItem selectItem : selectItems) {
                                    editor.putBoolean(selectItem.mPref,
                                            selectItem.mIsSelceted);
                                }
                                editor.apply();
                                dialog.dismiss();
                            }
                        }).create();
        dialog.getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        dialog.getListView().setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                    int position, long id) {
                boolean isChecked = selectItems[position].mIsSelceted;
                selectItems[position].mIsSelceted = !isChecked;
                adapter.notifyDataSetChanged();
            }
        });
        return dialog;
    }

    private static final class ViewHolder {
        TextView selectItemText;
        CheckedTextView checkListItem;
    }

    private class ContactsPhotoUpdateAdapter extends BaseAdapter {

        public ContactsPhotoUpdateAdapter(Context context) {
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder item = null;
            if (view == null) {
                final LayoutInflater dialogInflater = (LayoutInflater) getActivity()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = dialogInflater.inflate(
                        R.layout.contacts_photo_update_item, parent, false);
                item = new ViewHolder();
                item.selectItemText = (TextView) view
                        .findViewById(R.id.checklist_text);
                item.checkListItem = (CheckedTextView) view
                        .findViewById(R.id.checklist_check);
                view.setTag(item);
            } else {
                item = (ViewHolder) view.getTag();
            }
            item.selectItemText.setText(selectItems[position].mName);
            item.checkListItem.setChecked(selectItems[position].mIsSelceted);
            return view;
        }

        @Override
        public int getCount() {
            return selectItems.length;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }
    }
}