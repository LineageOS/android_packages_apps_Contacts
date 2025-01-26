/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2025 The LineageOS Project
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

package com.android.contacts.detail;

import android.content.Context;
import android.provider.ContactsContract.DisplayNameSources;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import com.android.contacts.R;
import com.android.contacts.model.Contact;
import com.android.contacts.model.dataitem.OrganizationDataItem;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.MoreMath;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link Contact} data object to appropriate {@link View}s.
 */
public class ContactDisplayUtils {
    private static final String TAG = "ContactDisplayUtils";
    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Contact contactData) {
        ContactsPreferences prefs = new ContactsPreferences(context);
        final CharSequence displayName = contactData.getDisplayName();
        if (prefs.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            if (!TextUtils.isEmpty(displayName)) {
                if (contactData.getDisplayNameSource() == DisplayNameSources.PHONE) {
                    return sBidiFormatter.unicodeWrap(
                            displayName.toString(), TextDirectionHeuristics.LTR);
                }
                return displayName;
            }
        } else {
            final CharSequence altDisplayName = contactData.getAltDisplayName();
            if (!TextUtils.isEmpty(altDisplayName)) {
                return altDisplayName;
            }
        }
        return context.getResources().getString(R.string.missing_name);
    }

    /**
     * Returns the phonetic name of the contact or null if there isn't one.
     */
    public static String getPhoneticName(Context context, Contact contactData) {
        String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            return phoneticName;
        }
        return null;
    }

    /**
     * Return the formatted organization string from the given OrganizationDataItem
     *
     * This will combine the company, department and title in one formatted string. However, if the
     * DisplayName is already the organization (company or title) and resulted combined string
     * include either company or title only then we don't need to display the organization string,
     * as it will already be shown in the DisplayName.
     */
    public static String getFormattedCompanyString(
            Context context, OrganizationDataItem organization, boolean displayNameIsOrganization) {
        List<String> text = Lists.newArrayList();
        if (!TextUtils.isEmpty(organization.getCompany())) {
            text.add(organization.getCompany());
        }
        if (!TextUtils.isEmpty(organization.getDepartment())) {
            text.add(organization.getDepartment());
        }
        if (!TextUtils.isEmpty(organization.getTitle())) {
            text.add(organization.getTitle());
        }
        if (text.size() == 3) {
            return context.getString(
                R.string.organization_entry_all_field, text.get(0), text.get(1),
                text.get(2));
        }
        if (text.size() == 2) {
            return context.getString(
                R.string.organization_entry_two_field, text.get(0), text.get(1));
        }
        if (text.size() == 1 && !displayNameIsOrganization) {
            return text.get(0);
        }
        return null;
    }

    /**
     * Sets the starred state of this contact.
     */
    public static void configureStarredMenuItem(MenuItem starredMenuItem, boolean isDirectoryEntry,
            boolean isUserProfile, boolean isStarred) {
        // Check if the starred state should be visible
        if (!isDirectoryEntry && !isUserProfile) {
            starredMenuItem.setVisible(true);
            final int resId = isStarred
                    ? R.drawable.quantum_ic_star_vd_theme_24
                    : R.drawable.quantum_ic_star_border_vd_theme_24;
            starredMenuItem.setIcon(resId);
            starredMenuItem.setChecked(isStarred);
            starredMenuItem.setTitle(isStarred ? R.string.menu_removeStar : R.string.menu_addStar);
        } else {
            starredMenuItem.setVisible(false);
        }
    }

    /**
     * Sets an alpha value on the view.
     */
    public static void setAlphaOnViewBackground(View view, float alpha) {
        if (view != null) {
            // Convert alpha layer to a black background HEX color with an alpha value for better
            // performance (i.e. use setBackgroundColor() instead of setAlpha())
            view.setBackgroundColor((int) (MoreMath.clamp(alpha, 0.0f, 1.0f) * 255) << 24);
        }
    }
}
