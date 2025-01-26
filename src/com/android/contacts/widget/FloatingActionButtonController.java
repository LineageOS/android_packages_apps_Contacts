/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.contacts.widget;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.widget.ImageButton;

import com.android.contacts.R;
import com.android.contacts.util.ViewUtil;
import com.android.phone.common.animation.AnimUtils;

/**
 * Controls the movement and appearance of the FAB (Floating Action Button).
 */
public class FloatingActionButtonController {
    private static final int FAB_SCALE_IN_DURATION = 186;
    private static final int FAB_SCALE_IN_FADE_IN_DELAY = 70;
    private static final int FAB_ICON_FADE_OUT_DURATION = 46;

    private final int mAnimationDuration;
    private final View mFloatingActionButtonContainer;
    private final ImageButton mFloatingActionButton;

    public FloatingActionButtonController(Activity activity, View container, ImageButton button) {
        Resources resources = activity.getResources();
        mAnimationDuration = resources.getInteger(
                R.integer.floating_action_button_animation_duration);
        mFloatingActionButtonContainer = container;
        mFloatingActionButton = button;
        ViewUtil.setupFloatingActionButton(mFloatingActionButtonContainer, resources);
    }

    /**
     * Sets FAB as View.VISIBLE or View.GONE.
     *
     * @param visible Whether or not to make the container visible.
     */
    public void setVisible(boolean visible) {
        mFloatingActionButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Scales the floating action button from no height and width to its actual dimensions. This is
     * an animation for showing the floating action button.
     * @param delayMs The delay for the effect, in milliseconds.
     */
    public void scaleIn(int delayMs) {
        setVisible(true);
        AnimUtils.scaleIn(mFloatingActionButtonContainer, FAB_SCALE_IN_DURATION, delayMs);
        AnimUtils.fadeIn(mFloatingActionButton, FAB_SCALE_IN_DURATION,
                delayMs + FAB_SCALE_IN_FADE_IN_DELAY, null);
    }

    /**
     * Immediately remove the affects of the last call to {@link #scaleOut}.
     */
    public void resetIn() {
        mFloatingActionButton.setAlpha(1f);
        mFloatingActionButton.setVisibility(View.VISIBLE);
        mFloatingActionButtonContainer.setScaleX(1);
        mFloatingActionButtonContainer.setScaleY(1);
    }

    /**
     * Scales the floating action button from its actual dimensions to no height and width. This is
     * an animation for hiding the floating action button.
     */
    public void scaleOut() {
        AnimUtils.scaleOut(mFloatingActionButtonContainer, mAnimationDuration);
        // Fade out the icon faster than the scale out animation, so that the icon scaling is less
        // obvious. We don't want it to scale, but the resizing the container is not as performant.
        AnimUtils.fadeOut(mFloatingActionButton, FAB_ICON_FADE_OUT_DURATION, null);
    }
}
