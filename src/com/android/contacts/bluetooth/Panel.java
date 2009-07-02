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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.LinearLayout;


public class Panel extends LinearLayout {

    /**
     * Callback invoked when the panel is opened/closed.
     */
    public static interface OnPanelListener {
        /**
         * Invoked when the panel becomes fully closed.
         */
        public void onPanelClosed(Panel panel);
        /**
         * Invoked when the panel becomes fully opened.
         */
        public void onPanelOpened(Panel panel);
    }

    private enum State {
        ABOUT_TO_ANIMATE,
        ANIMATING,
        READY,
        TRACKING,
        FLYING,
    }

    private static final String TAG = "Panel";
    private boolean mIsShrinking;
    private int mPosition;
    private int mDuration;
    private View mHandle;
    private View mContent;
    private Drawable mOpenedHandle;
    private Drawable mClosedHandle;
    private float mTrackX;
    private float mTrackY;

    private float mVelocity;

    private OnPanelListener panelListener;
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int LEFT = 2;

    public static final int RIGHT = 3;;
    private State mState;
    private Interpolator mInterpolator;
    private GestureDetector mGestureDetector;
    private int mContentHeight;
    private int mContentWidth;
    private int mOrientation;

    OnGestureListener gestureListener = new OnGestureListener() {
        private float scrollY;
        private float scrollX;

        public boolean onDown(MotionEvent e) {
            scrollX = scrollY = 0;
            if (mState != State.READY) {
                // we are animating or just about to animate
                return false;
            }
            mState = State.ABOUT_TO_ANIMATE;
            mIsShrinking = mContent.getVisibility() == VISIBLE;
            if (!mIsShrinking) {
                // this could make flicker so we test mState in dispatchDraw()
                // to see if is equal to ABOUT_TO_ANIMATE
                mContent.setVisibility(VISIBLE);
            }
            return true;
        }
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mState = State.FLYING;
            mVelocity = mOrientation == VERTICAL? velocityY : velocityX;
            post(startAnimation);
            return true;
        }
        public void onLongPress(MotionEvent e) {
            // not used
        }
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mState = State.TRACKING;
            float tmpY = 0, tmpX = 0;
            if (mOrientation == VERTICAL) {
                scrollY -= distanceY;
                if (mPosition == TOP) {
                    tmpY = ensureRange(scrollY, -mContentHeight, 0);
                } else  {
                    tmpY = ensureRange(scrollY, 0, mContentHeight);
                }
            } else {
                scrollX -= distanceX;
                if (mPosition == LEFT) {
                    tmpX = ensureRange(scrollX, -mContentWidth, 0);
                } else {
                    tmpX = ensureRange(scrollX, 0, mContentWidth);
                }
            }
            if (tmpX != mTrackX || tmpY != mTrackY) {
                mTrackX = tmpX;
                mTrackY = tmpY;
                invalidate();
            }
            return true;
        }
        public void onShowPress(MotionEvent e) {
            // not used
        }
        public boolean onSingleTapUp(MotionEvent e) {
            // simple tap: click
            post(startAnimation);
            return true;
        }
    };

    OnTouchListener touchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
//            Log.d(TAG, "state " + mState);
            if (!mGestureDetector.onTouchEvent(event)) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // tup up after scrolling
//                    Log.d(TAG, "tup up after scrolling");
                    if(mState == State.ANIMATING)

                    post(startAnimation);
                }
            }
            return false;
        }
    };

    Runnable startAnimation = new Runnable() {
        public void run() {
            // this is why we post this Runnable couple of lines above:
            // now its save to use mContent.getHeight() && mContent.getWidth()
            TranslateAnimation animation;
            int fromXDelta = 0, toXDelta = 0, fromYDelta = 0, toYDelta = 0;
            if (mState == State.FLYING) {
                mIsShrinking = (mPosition == TOP || mPosition == LEFT) ^ (mVelocity > 0);
            }
            int calculatedDuration;
            if (mOrientation == VERTICAL) {
                int height = mContentHeight;
                if (!mIsShrinking) {
                    fromYDelta = mPosition == TOP? -height : height;
                } else {
                    toYDelta = mPosition == TOP? -height : height;
                }
                if (mState == State.TRACKING) {
                    if (Math.abs(mTrackY - fromYDelta) < Math.abs(mTrackY - toYDelta)) {
                        mIsShrinking = !mIsShrinking;
                        toYDelta = fromYDelta;
                    }
                    fromYDelta = (int) mTrackY;
                } else
                if (mState == State.FLYING) {
                    fromYDelta = (int) mTrackY;
                }
                // TODO in fact for FLYING events we should calculate animation duration - thus speed
                // based on flying velocity, probably by calculating interpolator's derivative
                // in time == 0 but it's too much for now...
                calculatedDuration = mDuration * Math.abs(toYDelta - fromYDelta) / mContentHeight;
            } else {
                int width = mContentWidth;
                if (!mIsShrinking) {
                    fromXDelta = mPosition == LEFT? -width : width;
                } else {
                    toXDelta = mPosition == LEFT? -width : width;
                }
                if (mState == State.TRACKING) {
                    if (Math.abs(mTrackX - fromXDelta) < Math.abs(mTrackX - toXDelta)) {
                        mIsShrinking = !mIsShrinking;
                        toXDelta = fromXDelta;
                    }
                    fromXDelta = (int) mTrackX;
                } else
                if (mState == State.FLYING) {
                    fromXDelta = (int) mTrackX;
                }
                // TODO ditto
                calculatedDuration = mDuration * Math.abs(toXDelta - fromXDelta) / mContentWidth;
            }
            mTrackX = mTrackY = 0;
            if (calculatedDuration == 0) {
                mState = State.READY;
                mContent.setVisibility(GONE);
                return;
            }
            animation = new TranslateAnimation(fromXDelta, toXDelta, fromYDelta, toYDelta);
            animation.setDuration(calculatedDuration);
            animation.setAnimationListener(animationListener);
            if (mInterpolator != null) {
                animation.setInterpolator(mInterpolator);
            }
            startAnimation(animation);
        }
    };

    private AnimationListener animationListener = new AnimationListener() {
        public void onAnimationEnd(Animation animation) {
            mState = State.READY;
//            Log.d(TAG, "end of animation");
            if (mIsShrinking) {
                mContent.setVisibility(GONE);
            }
            if (mIsShrinking && mClosedHandle != null) {
                mHandle.setBackgroundDrawable(mClosedHandle);
            } else
            if (!mIsShrinking && mOpenedHandle != null) {
                mHandle.setBackgroundDrawable(mOpenedHandle);
            }
            // invoke listener if any
            if (panelListener != null) {
                if (mIsShrinking) {
                    panelListener.onPanelClosed(Panel.this);
                } else {
                    panelListener.onPanelOpened(Panel.this);
                }
            }
        }
        public void onAnimationRepeat(Animation animation) {
        }
        public void onAnimationStart(Animation animation) {
            mState = State.ANIMATING;
        }
    };


    public Panel(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Panel);
        mDuration = a.getInteger(R.styleable.Panel_animationDuration, 750);    // duration defaults to 750 ms
        mPosition = a.getInteger(R.styleable.Panel_position, BOTTOM);        // position defaults to BOTTOM
        mOpenedHandle = a.getDrawable(R.styleable.Panel_openedHandle);
        mClosedHandle = a.getDrawable(R.styleable.Panel_closedHandle);
        a.recycle();
        mOrientation = (mPosition == TOP || mPosition == BOTTOM)? VERTICAL : HORIZONTAL;
        setOrientation(mOrientation);
        mState = State.READY;
        mGestureDetector = new GestureDetector(gestureListener);
        mGestureDetector.setIsLongpressEnabled(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
//        String name = getResources().getResourceEntryName(getId());
//        Log.d(TAG, name + " ispatchDraw " + mState);
        // this is why 'mState' was added:
        // avoid flicker before animation start
        if (mState == State.ABOUT_TO_ANIMATE && !mIsShrinking) {
            int delta = mOrientation == VERTICAL? mContentHeight : mContentWidth;
            if (mPosition == LEFT || mPosition == TOP) {
                delta = -delta;
            }
            if (mOrientation == VERTICAL) {
                canvas.translate(0, delta);
            } else {
                canvas.translate(delta, 0);
            }
        }
        if (mState == State.TRACKING || mState == State.FLYING) {
            canvas.translate(mTrackX, mTrackY);
        }
        super.dispatchDraw(canvas);
    }

    private float ensureRange(float v, int min, int max) {
        v = Math.max(v, min);
        v = Math.min(v, max);
        return v;
    }

    /**
     * Gets Panel's mContent
     *
     * @return Panel's mContent
     */
    public View getContent() {
        return mContent;
    }

    /**
     * Gets Panel's mHandle
     *
     * @return Panel's mHandle
     */
    public View getHandle() {
        return mHandle;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHandle = findViewById(R.id.panelHandle);
        if (mHandle == null) {
            throw new RuntimeException("Your Panel must have a View whose id attribute is 'R.id.panelHandle'");
        }
        mHandle.setOnTouchListener(touchListener);

        mContent = findViewById(R.id.panelContent);
        if (mContent == null) {
            throw new RuntimeException("Your Panel must have a View whose id attribute is 'R.id.panelContent'");
        }

        // reposition children
        removeView(mHandle);
        removeView(mContent);
        if (mPosition == TOP || mPosition == LEFT) {
            addView(mContent);
            addView(mHandle);
        } else {
            addView(mHandle);
            addView(mContent);
        }

        if (mClosedHandle != null) {
            mHandle.setBackgroundDrawable(mClosedHandle);
        }
        mContent.setVisibility(GONE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mContentWidth = mContent.getWidth();
        mContentHeight = mContent.getHeight();
    }

    /**
     * Sets the acceleration curve for panel's animation.
     *
     * @param i The interpolator which defines the acceleration curve
     */
    public void setInterpolator(Interpolator i) {
        mInterpolator = i;
    }

    /**
     * Sets the listener that receives a notification when the panel becomes open/close.
     *
     * @param onPanelListener The listener to be notified when the panel is opened/closed.
     */
    public void setOnPanelListener(OnPanelListener onPanelListener) {
        panelListener = onPanelListener;
    }

    public void initialInflation() {
        mContent.setVisibility(VISIBLE);
    }
}
