package com.android.contacts;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public final class DialerSettings {	

	private static final String PREFNAME = "EclairDialer";
	private static final String LEFTBUTTONTYPE = "left_button_type"; // The configurable button	
	
	//Types
	public static final String ADDCONTACTS = "Add To Contacts";
	public static final String VOICEMAIL = "Voicemail"; //prefix for voicemail types
	public static final String SMS = "SMS / MMS";
	
	//Voicemail Apps
	public static final String TMO = "T-Mobile Visual Voicemail";
	public static final String GV = "Google Voice";
	public static final String NONE = "None (Dial Voicemail Number)";
	
	//Intents, if any
	public static final String LEFTBUTTONINTENTPKG = "left_button_intent_pkg";
	public static final String LEFTBUTTONINTENTCLS = "left_button_intent_cls";
	
	public static final String SENSORROTATION = "sensor_rotation";
	
	private static SharedPreferences sPrefs = null;
	private static SharedPreferences.Editor sEditor = null;
	
	private static SharedPreferences getSharedPreferences(Context context) {
		if (sPrefs == null) {
			sPrefs = context.getSharedPreferences(PREFNAME, Context.MODE_PRIVATE); // Use MODE_PRIVATE for this preferences
		}
		return sPrefs;
        }
        
        private static SharedPreferences.Editor getEditor(Context context) {
        	getSharedPreferences(context);
        	if (sEditor == null) {
        		sEditor = sPrefs.edit();
        	}
        	return sEditor;        	
        }
	
	public static String getLeftButtonType(Context context) {
		getSharedPreferences(context);
		return sPrefs.getString(LEFTBUTTONTYPE, ADDCONTACTS); // Return add_contacts as default if not found
	}
	
	public static void setLeftButtonType(Context context, String type) {
		getEditor(context);
		sEditor.putString(LEFTBUTTONTYPE, type);
		sEditor.commit();
	}
	
	public static String getVoicemailApp(Context context) {
		getSharedPreferences(context);
		return sPrefs.getString(VOICEMAIL, NONE); // Return add_contacts as default if not found
	}
	
	public static void setVoicemailApp(Context context, String app) {		
		getEditor(context);
		sEditor.putString(VOICEMAIL, app);
		sEditor.commit();
		setLeftButtonIntent(context, VOICEMAIL, app);
	}
	
	private static void setLeftButtonIntent(Context context, String type, String app) {
		getEditor(context);
		
		//Wysie_Soh: Only voicemail for now, might have more in future, thus the check
		if (type.equals(VOICEMAIL)) {
			if (app.equals(TMO)) {
				sEditor.putString(LEFTBUTTONINTENTPKG, "com.oz.mobile.android.voicemail.application");
				sEditor.putString(LEFTBUTTONINTENTCLS, "com.oz.mobile.android.voicemail.application.application");
			}
			else if (app.equals(GV)) {
				sEditor.putString(LEFTBUTTONINTENTPKG, "com.google.android.apps.googlevoice");
				sEditor.putString(LEFTBUTTONINTENTCLS, "com.google.android.apps.googlevoice.SplashActivity");
			}
			else if (app.equals(NONE)) {
				sEditor.putString(LEFTBUTTONINTENTPKG, "");
				sEditor.putString(LEFTBUTTONINTENTCLS, "");
			}
		}
		else {
			sEditor.putString(LEFTBUTTONINTENTPKG, "");
			sEditor.putString(LEFTBUTTONINTENTCLS, "");
		}
				
		sEditor.commit();
	}
	
	public static String getLeftButtonIntentPkg(Context context) {
		getSharedPreferences(context);
		return sPrefs.getString(LEFTBUTTONINTENTPKG, ""); //Return none if nothing found
	}
	
	public static String getLeftButtonIntentCls(Context context) {
		getSharedPreferences(context);
		return sPrefs.getString(LEFTBUTTONINTENTCLS, ""); //Return none if nothing found
	}
	
	public static void setSensorRotation(Context context, boolean val) {
		getEditor(context);	
		sEditor.putBoolean(SENSORROTATION, val);
		sEditor.commit();
	}
	
	public static boolean getSensorRotation(Context context) {
		getSharedPreferences(context);
		return sPrefs.getBoolean(SENSORROTATION, false); // Return false by default
	}

}
