/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.util;

import android.content.res.Resources;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.contacts.R;

/**
 * Provides static functions to work with views
 */
public class ViewUtil {
    private ViewUtil() {}

    /**
     * Returns a boolean indicating whether or not the view's layout direction is RTL
     *
     * @param view - A valid view
     * @return True if the view's layout direction is RTL
     */
    public static boolean isViewLayoutRtl(View view) {
        return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER;
    static {
        OVAL_OUTLINE_PROVIDER = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        };
    }

    private static final ViewOutlineProvider RECT_OUTLINE_PROVIDER;
    static {
        RECT_OUTLINE_PROVIDER = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        };
    }

    /**
     * Adds a rectangular outline to a view. This can be useful when you want to add a shadow
     * to a transparent view. See b/16856049.
     * @param view view that the outline is added to
     * @param res The resources file.
     */
    public static void addRectangularOutlineProvider(View view, Resources res) {
        view.setOutlineProvider(RECT_OUTLINE_PROVIDER);
    }

    /**
     * Configures the floating action button, clipping it to a circle and setting its translation z.
     * @param view The float action button's view.
     * @param res The resources file.
     */
    public static void setupFloatingActionButton(View view, Resources res) {
        view.setOutlineProvider(OVAL_OUTLINE_PROVIDER);
        view.setTranslationZ(
                res.getDimensionPixelSize(R.dimen.floating_action_button_translation_z));
    }
}
