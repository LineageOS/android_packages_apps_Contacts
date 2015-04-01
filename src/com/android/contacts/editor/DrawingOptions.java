package com.android.contacts.editor;

import java.util.HashMap;

/**
 * Holds configuration options that dictate the look and feel of the editor views
 */
public class DrawingOptions {


    public static final String TEXT_COLOR = "textColor";
    public static final String IS_TEXT_COLOR_TRANSIENT = "isTextColorTransient";

    // Text color for now
    public HashMap<String, Object> mOptions = new HashMap<String, Object>();

    public void setTextColor(int color) {
        //
        mOptions.put(TEXT_COLOR, color);
    }

    public Integer getTextColor() {
        return (Integer) mOptions.get(TEXT_COLOR);
    }

    public void setIsTextColorTransient(Boolean isTransient) {
        mOptions.put(IS_TEXT_COLOR_TRANSIENT, isTransient);
    }

    public Boolean getIsTextColorTransient() {
        return (Boolean) mOptions.get(IS_TEXT_COLOR_TRANSIENT);
    }

}
