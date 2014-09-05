/*
 * Copyright (C) 2013, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

package com.android.contacts.group.local;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.provider.LocalGroups;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.R;

public class AddLocalGroupDialog extends AlertDialog implements OnClickListener, TextWatcher {

    public static interface AddGroupListener {
        void onAddGroup(String name);
    }

    public static final int GROUP_NAME_MAX_LENGTH = 20;

    private EditText groupSettings;

    private AddGroupListener addGroupListener;
    private int mChangeStartPos;
    private int mChangeCount;

    public AddLocalGroupDialog(Context context, AddGroupListener addGroupListener) {
        super(context);
        this.addGroupListener = addGroupListener;
        groupSettings = new EditText(context);
        groupSettings.setHint(R.string.title_group_name);
        groupSettings.addTextChangedListener(this);
        setTitle(R.string.title_group_add);
        setView(groupSettings);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                this);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // Because the dialog will reuse, should remove the last group
                // name if we show another add group dialog.
                groupSettings.getText().clear();
                getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                        groupSettings.getText().length() > 0);
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                String name = groupSettings.getText().toString();
                if (checkGroupTitleExist(name)) {
                    String text = getContext().getString(R.string.error_group_exist, name);
                    Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
                } else {
                    addGroupListener.onAddGroup(name);
                }
                break;
        }
        groupSettings.setText(null);
    }

    private boolean checkGroupTitleExist(String name) {
        Cursor c = null;
        try {
            c = this.getContext().getContentResolver()
                    .query(LocalGroups.CONTENT_URI, null, LocalGroups.GroupColumns.TITLE + "=?",
                            new String[] { name }, null);
            if (c != null)
                return c.getCount() > 0;
            else {
                return false;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // The start position of new added characters
        mChangeStartPos = start;
        // The number of new added characters
        mChangeCount = count;
    }

    @Override
    public void afterTextChanged(Editable s) {
        limitTextSize(s);
        getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(groupSettings.getText().length() > 0);
    }

    private void limitTextSize(Editable s) {
        String name = s.toString();
        int len = 0;
        // The end position of insert string
        int insertEnd = mChangeStartPos + mChangeCount - 1;
        // The string need to keep.
        String keepStr = name.substring(insertEnd + 1);
        // Keep string len
        int keepStrCharLen = 0;
        for (int n = 0; n < keepStr.length(); n++) {
            // Get the char length of keep stringi.
            // To get the char number need insert.
            keepStrCharLen += getCharacterVisualLength(keepStr, n);
        }
        String headStr = name.substring(0, insertEnd + 1);
        for (int i = 0; i < headStr.length(); i++) {
            int ch = Character.codePointAt(name, i);
            len += getCharacterVisualLength(name, i);
            if (len > GROUP_NAME_MAX_LENGTH - keepStrCharLen || ch == 10 || ch == 32) {
                // delete the redundant text.
                s.delete(i, s.length() - keepStr.length());
                groupSettings.setTextKeepState(s);
                break;
            }
        }
    }

    /**
     * A character beyond 0xff is twice as big as a character within 0xff in
     * width when showing.
     */
    private int getCharacterVisualLength(String seq, int index) {
        int cp = Character.codePointAt(seq, index);
        if (cp >= 0x00 && cp <= 0xFF) {
            return 1;
        } else {
            return 2;
        }
    }

}
