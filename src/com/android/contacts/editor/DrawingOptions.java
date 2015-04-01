package com.android.contacts.editor;

/**
 * Holds configuration options that dictate the look and feel of the editor fields
 */
public class DrawingOptions {

    private Integer mTextColor;
    private Boolean mIsTextColorTransient;

    public void setTextColor(int color) {
        mTextColor = color;
    }

    public Integer getTextColor() {
        return mTextColor;
    }

    public void setIsTextColorTransient(Boolean isTransient) {
        mIsTextColorTransient = isTransient;
    }

    public Boolean getIsTextColorTransient() {
        return mIsTextColorTransient;
    }

}
