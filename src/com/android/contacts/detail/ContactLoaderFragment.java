/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.detail;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.list.ShortcutIntentBuilder;
import com.android.contacts.common.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.util.PhoneCapabilityTester;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

/**
 * This is an invisible worker {@link Fragment} that loads the contact details for the contact card.
 * The data is then passed to the listener, who can then pass the data to other {@link View}s.
 */
public class ContactLoaderFragment extends Fragment implements FragmentKeyListener {

    private static final String TAG = ContactLoaderFragment.class.getSimpleName();

    /** The launch code when picking a ringtone */
    private static final int REQUEST_CODE_PICK_RINGTONE = 1;

    /** This is the Intent action to install a shortcut in the launcher. */
    private static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";

    private boolean mOptionsMenuOptions;
    private boolean mOptionsMenuEditable;
    private boolean mOptionsMenuShareable;
    private boolean mOptionsMenuCanCreateShortcut;
    private boolean mSendToVoicemailState;
    private String mCustomRingtone;

    private static final String ACTION_INSTALL_SHORTCUT_SUCCESSFUL =
            "com.android.launcher.action.INSTALL_SHORTCUT_SUCCESSFUL";
    private static final String EXTRA_RESPONSE_PACKAGENAME = "response_packagename";

    /**
     * This is a listener to the {@link ContactLoaderFragment} and will be notified when the
     * contact details have finished loading or if the user selects any menu options.
     */
    public static interface ContactLoaderFragmentListener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        public void onContactNotFound();

        /**
         * Contact details have finished loading.
         */
        public void onDetailsLoaded(Contact result);

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri lookupUri);

        /**
         * User decided to delete the contact
         */
        public void onDeleteRequested(Uri lookupUri);

    }

    private static final int LOADER_DETAILS = 1;

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String LOADER_ARG_CONTACT_URI = "contactUri";
    private static final String CONTACTS_COMMON_PKG_NAME = "com.android.contacts.common";

    private Context mContext;
    private Uri mLookupUri;
    private ContactLoaderFragmentListener mListener;

    private Contact mContactData;

    private IntentFilter mResponseFilter;

    /** Receive broadcast, show toast only when put shortcut sucessful in laucher */
    private BroadcastReceiver mResponseReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_INSTALL_SHORTCUT_SUCCESSFUL.equals(intent.getAction())) {
                return;
            }
            String packageName = intent.getStringExtra(EXTRA_RESPONSE_PACKAGENAME);
            if (packageName != null && (packageName.equals(context.getPackageName()) ||
                    CONTACTS_COMMON_PKG_NAME.equals(packageName))) {
                // Send a toast to give feedback to the user that a shortcut to this
                // contact was added to the launcher.
                Toast.makeText(context, R.string.createContactShortcutSuccessful,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    public ContactLoaderFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
        mResponseFilter = new IntentFilter(ACTION_INSTALL_SHORTCUT_SUCCESSFUL);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);
        // This is an invisible view.  This fragment is declared in a layout, so it can't be
        // "viewless".  (i.e. can't return null here.)
        // See also the comment in the layout file.
        return inflater.inflate(R.layout.contact_detail_loader_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mLookupUri != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().initLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mResponseReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mResponseReceiver, mResponseFilter);
    }

    public void loadUri(Uri lookupUri) {
        if (Objects.equal(lookupUri, mLookupUri)) {
            // Same URI, no need to load the data again
            return;
        }

        mLookupUri = lookupUri;
        if (mLookupUri == null) {
            getLoaderManager().destroyLoader(LOADER_DETAILS);
            mContactData = null;
            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        } else if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().restartLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void setListener(ContactLoaderFragmentListener value) {
        mListener = value;
    }

    /**
     * The listener for the detail loader
     */
    private final LoaderManager.LoaderCallbacks<Contact> mDetailLoaderListener =
            new LoaderCallbacks<Contact>() {
        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            Uri lookupUri = args.getParcelable(LOADER_ARG_CONTACT_URI);
            return new ContactLoader(mContext, lookupUri, true /* loadGroupMetaData */,
                    true /* load invitable account types */, true /* postViewNotification */,
                    true /* computeFormattedPhoneNumber */);
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            if (!mLookupUri.equals(data.getRequestedUri())) {
                Log.e(TAG, "Different URI: requested=" + mLookupUri + "  actual=" + data);
                return;
            }

            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            } else if (data.isNotFound()) {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                mContactData = null;
            } else {
                mContactData = data;
            }

            if (mListener != null) {
                if (mContactData == null) {
                    mListener.onContactNotFound();
                } else {
                    mListener.onDetailsLoaded(mContactData);
                }
            }
            // Make sure the options menu is setup correctly with the loaded data.
            if (getActivity() != null) getActivity().invalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(Loader<Contact> loader) {}
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view_contact, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuOptions != isContactOptionsChangeEnabled()
                || mOptionsMenuEditable != isContactEditable()
                || mOptionsMenuShareable != isContactShareable()
                || mOptionsMenuCanCreateShortcut != isContactCanCreateShortcut();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuOptions = isContactOptionsChangeEnabled();
        mOptionsMenuEditable = isContactEditable();
        mOptionsMenuShareable = isContactShareable();
        mOptionsMenuCanCreateShortcut = isContactCanCreateShortcut();
        if (mContactData != null) {
            mSendToVoicemailState = mContactData.isSendToVoicemail();
            mCustomRingtone = mContactData.getCustomRingtone();
        }

        // Hide telephony-related settings (ringtone, send to voicemail)
        // if we don't have a telephone
        final MenuItem optionsSendToVoicemail = menu.findItem(R.id.menu_send_to_voicemail);
        if (optionsSendToVoicemail != null) {
            optionsSendToVoicemail.setChecked(mSendToVoicemailState);
            optionsSendToVoicemail.setVisible(mOptionsMenuOptions);
        }
        final MenuItem optionsRingtone = menu.findItem(R.id.menu_set_ringtone);
        if (optionsRingtone != null) {
            optionsRingtone.setVisible(mOptionsMenuOptions);
        }

        final MenuItem editMenu = menu.findItem(R.id.menu_edit);
        if (editMenu != null) {
            editMenu.setVisible(mOptionsMenuEditable);
        }

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        if (deleteMenu != null) {
            deleteMenu.setVisible(mOptionsMenuEditable);
        }

        final MenuItem shareMenu = menu.findItem(R.id.menu_share);
        if (shareMenu != null) {
            shareMenu.setVisible(mOptionsMenuShareable);
        }

        final MenuItem createContactShortcutMenu = menu.findItem(R.id.menu_create_contact_shortcut);
        if (createContactShortcutMenu != null) {
            createContactShortcutMenu.setVisible(mOptionsMenuCanCreateShortcut);
        }
        String accoutName = null;
        String accoutType = null;
        if (mContactData != null) {
            final RawContact rawContact = (RawContact) mContactData.getRawContacts().get(0);
            accoutName = rawContact.getAccountName();
            accoutType = rawContact.getAccountTypeString();
        }

        final MenuItem copyToPhoneMenu = menu.findItem(R.id.menu_copy_to_phone);
        if (copyToPhoneMenu != null) {
            copyToPhoneMenu.setVisible(false);
        }

        final MenuItem copyToSim1Menu = menu.findItem(R.id.menu_copy_to_sim1);
        if (copyToSim1Menu != null) {
            copyToSim1Menu.setVisible(false);
        }

        final MenuItem copyToSim2Menu = menu.findItem(R.id.menu_copy_to_sim2);
        if (copyToSim2Menu != null) {
            copyToSim2Menu.setVisible(false);
        }

        if (!TextUtils.isEmpty(accoutType)) {
            if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accoutType)) {
                copyToPhoneMenu.setVisible(true);
                copyToPhoneMenu.setTitle(getString(R.string.menu_copyTo,
                        getString(R.string.phoneLabelsGroup)));
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    if (SimContactsConstants.SIM_NAME_1.equals(accoutName)
                            && hasEnabledIccCard(SimContactsConstants.SUB_2)) {
                        copyToSim2Menu.setTitle(getString(R.string.menu_copyTo,
                                getString(R.string.copy_to_target_msim, 2)));
                        copyToSim2Menu.setVisible(true);
                    }
                    if (SimContactsConstants.SIM_NAME_2.equals(accoutName)
                            && hasEnabledIccCard(SimContactsConstants.SUB_1)) {
                        copyToSim1Menu.setTitle(getString(R.string.menu_copyTo,
                                getString(R.string.copy_to_target_msim, 1)));
                        copyToSim1Menu.setVisible(true);
                    }
                }
            } else if (SimContactsConstants.ACCOUNT_TYPE_PHONE.equals(accoutType)) {
                copyToPhoneMenu.setVisible(false);
                boolean hasPhoneOrEmail = hasPhoneOrEmailDate(mContactData);
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    if (hasPhoneOrEmail && hasEnabledIccCard(SimContactsConstants.SUB_1)) {
                        copyToSim1Menu.setTitle(getString(R.string.menu_copyTo,
                                getString(R.string.copy_to_target_msim, 1)));
                        copyToSim1Menu.setVisible(true);
                    }
                    if (hasPhoneOrEmail && hasEnabledIccCard(SimContactsConstants.SUB_2)) {
                        copyToSim2Menu.setTitle(getString(R.string.menu_copyTo,
                                getString(R.string.copy_to_target_msim, 2)));
                        copyToSim2Menu.setVisible(true);
                    }
                } else {
                    if (hasPhoneOrEmail && TelephonyManager.getDefault().hasIccCard()
                            && TelephonyManager.getDefault().getSimState()
                                == TelephonyManager.SIM_STATE_READY) {
                        copyToSim1Menu.setTitle(getString(R.string.menu_copyTo,
                                getString(R.string.copy_to_target_sim)));
                        copyToSim1Menu.setVisible(true);
                    }
                }
            }

        }
    }
    private boolean hasPhoneOrEmailDate(Contact contact){
        int phoneCount = 0;
        int emailCount = 0;
        ImmutableList<RawContact> rawContacts = contact.getRawContacts();
        for (RawContact rawContact : rawContacts) {
            RawContactDelta rawContactDelta = RawContactDelta.fromBefore(rawContact);
            phoneCount += rawContactDelta.getMimeEntriesCount(
                    Phone.CONTENT_ITEM_TYPE, true);
            emailCount += rawContactDelta.getMimeEntriesCount(
                    Email.CONTENT_ITEM_TYPE, true);
        }
        if (phoneCount > 0 || emailCount > 0) {
            return true;
        } else {
            return false;
        }
    }

    private boolean hasEnabledIccCard(int subscription) {
        return MSimTelephonyManager.getDefault().hasIccCard(subscription)
                && MSimTelephonyManager.getDefault().getSimState(subscription)
                        == TelephonyManager.SIM_STATE_READY;
    }

    public boolean isContactOptionsChangeEnabled() {
        return mContactData != null && !mContactData.isDirectoryEntry()
                && PhoneCapabilityTester.isPhone(mContext);
    }

    public boolean isContactEditable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactShareable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactCanCreateShortcut() {
        return mContactData != null && !mContactData.isUserProfile()
                && !mContactData.isDirectoryEntry();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                if (mListener != null) mListener.onEditRequested(mLookupUri);
                break;
            }
            case R.id.menu_delete: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
            case R.id.menu_set_ringtone: {
                if (mContactData == null) return false;
                doPickRingtone();
                return true;
            }
            case R.id.menu_send_via_sms: {
                if (mContactData == null) return false;
                sendContactViaSMS();
                return true;
            }
            case R.id.menu_copy_to_phone: {
                if (mContactData == null) return false;
                copyToPhone();
                return true;
            }
            case R.id.menu_copy_to_sim1: {
                if (mContactData == null) return false;
                copyToCard(SimContactsConstants.SUB_1);
                return true;
            }
            case R.id.menu_copy_to_sim2: {
                if (mContactData == null) return false;
                copyToCard(SimContactsConstants.SUB_2);
                return true;
            }
            case R.id.menu_share: {
                if (mContactData == null) return false;

                final String lookupKey = mContactData.getLookupKey();
                Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                if (mContactData.isUserProfile()) {
                    // User is sharing the profile.  We don't want to force the receiver to have
                    // the highly-privileged READ_PROFILE permission, so we need to request a
                    // pre-authorized URI from the provider.
                    shareUri = getPreAuthorizedUri(shareUri);
                }

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, shareUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = mContext.getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    mContext.startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case R.id.menu_send_to_voicemail: {
                // Update state and save
                mSendToVoicemailState = !mSendToVoicemailState;
                item.setChecked(mSendToVoicemailState);
                Intent intent = ContactSaveService.createSetSendToVoicemail(
                        mContext, mLookupUri, mSendToVoicemailState);
                mContext.startService(intent);
                return true;
            }
            case R.id.menu_create_contact_shortcut: {
                // Create a launcher shortcut with this contact
                createLauncherShortcutWithContact();
                return true;
            }
        }
        return false;
    }

    private void copyToPhone() {
        String name = mContactData.getDisplayName();
        if (TextUtils.isEmpty(name)) {
            name = "";
        }
        String phoneNumber = "";
        String anrNumber = "";
        String email = "";

        //get phonenumber,email,anr from SIM contacts,then insert them to phone
        for (RawContact rawContact : mContactData.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                if (dataItem.getMimeType() == null) {
                    continue;
                }
                if (dataItem instanceof PhoneDataItem) {
                    PhoneDataItem phoneNum = (PhoneDataItem) dataItem;
                    final String number = phoneNum.getNumber();
                    if (!TextUtils.isEmpty(number)) {
                        if (Phone.TYPE_MOBILE == phoneNum.getContentValues().getAsInteger(
                                Phone.TYPE)) {
                            phoneNumber = number;
                        } else {
                            if (!anrNumber.equals("")) {
                                anrNumber += ",";
                            } else {
                                anrNumber += number;
                            }
                        }
                    }
                } else if (dataItem instanceof EmailDataItem) {
                    EmailDataItem emailData = (EmailDataItem) dataItem;
                    final String address = emailData.getData();
                    if (!TextUtils.isEmpty(address)) {
                        email = address;
                    }
                }
            }
        }

        String[] value = new String[] {
                name, phoneNumber, email, anrNumber
        };
        MoreContactUtils
                .insertToPhone(value, mContext.getContentResolver(),
                        SimContactsConstants.SUB_INVALID);
    }

    private Handler mHandler = null;

    private void copyToCard(final int sub) {
        final int MSG_COPY_DONE = 0;
        final int MSG_COPY_FAILURE = 1;
        final int MSG_CARD_NO_SPACE = 2;
        final int MSG_NO_EMPTY_EMAIL = 3;
        if (mHandler == null) {
            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_COPY_DONE:
                            Toast.makeText(mContext, R.string.copy_done, Toast.LENGTH_SHORT)
                                    .show();
                            break;
                        case MSG_COPY_FAILURE:
                            Toast.makeText(mContext, R.string.copy_failure, Toast.LENGTH_SHORT)
                                    .show();
                            break;
                        case MSG_CARD_NO_SPACE:
                            Toast.makeText(mContext, R.string.card_no_space, Toast.LENGTH_SHORT)
                                    .show();
                            break;
                        case MSG_NO_EMPTY_EMAIL:
                            Toast.makeText(mContext, R.string.no_empty_email_in_usim,
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            };
        }

        new Thread(new Runnable() {
            public void run() {
                synchronized (this) {
                    int adnCountInSimContact = 1;
                    int anrCountInSimContact = 1;
                    int emailCountInSimContact = 0;
                    if (!MoreContactUtils.canSaveAnr(sub)) {
                        anrCountInSimContact = 0;
                    } else {
                        anrCountInSimContact = MoreContactUtils.getOneSimAnrCount(sub);
                    }
                    if (MoreContactUtils.canSaveEmail(sub)) {
                        emailCountInSimContact = MoreContactUtils.getOneSimEmailCount(sub);
                    }
                    int totalEmptyAdn = MoreContactUtils.getSimFreeCount(mContext, sub);
                    int totalEmptyAnr = MoreContactUtils.getSpareAnrCount(sub);
                    int totalEmptyEmail = MoreContactUtils.getSpareEmailCount(sub);

                    Message msg = Message.obtain();
                    if (totalEmptyAdn <= 0) {
                        msg.what = MSG_CARD_NO_SPACE;
                        mHandler.sendMessage(msg);
                        return;
                    }

                    //to indiacate how many number in one ADN can saved to SIM card,
                    //1 means can only save one number,2 means can save anr
                    int numEntitySize = adnCountInSimContact + anrCountInSimContact;

                    //empty number is equals to the sum of adn and anr
                    int emptyNumTotal = totalEmptyAdn + totalEmptyAnr;

                    // Get name string
                    String strName = mContactData.getDisplayName();

                    ArrayList<String> arrayNumber = new ArrayList<String>();
                    ArrayList<String> arrayEmail = new ArrayList<String>();

                    for (RawContact rawContact : mContactData.getRawContacts()) {
                        for (DataItem dataItem : rawContact.getDataItems()) {
                            if (dataItem.getMimeType() == null) {
                                continue;
                            }
                            if (dataItem instanceof PhoneDataItem) {
                                // Get phone string
                                PhoneDataItem phoneNum = (PhoneDataItem) dataItem;
                                final String number = phoneNum.getNumber();
                                if (!TextUtils.isEmpty(number) && emptyNumTotal-- > 0) {
                                    arrayNumber.add(number);
                                }
                            } else if (dataItem instanceof EmailDataItem) {
                                // Get email string
                                EmailDataItem emailData = (EmailDataItem) dataItem;
                                final String address = emailData.getData();
                                if (!TextUtils.isEmpty(address) && totalEmptyEmail-- > 0) {
                                    arrayEmail.add(address);
                                }
                            }
                        }
                    }

                    //calculate how many ADN needed according to the number name,phone,email,
                    //and uses the max of them
                    int nameCount = (strName != null && !strName.equals("")) ? 1 : 0;
                    int groupNumCount = (arrayNumber.size() % numEntitySize) != 0 ? (arrayNumber
                            .size() / numEntitySize + 1) : (arrayNumber.size() / numEntitySize);
                    int groupEmailCount = emailCountInSimContact == 0 ? 0
                            : ((arrayEmail.size() % emailCountInSimContact) != 0 ? (arrayEmail
                                    .size() / emailCountInSimContact + 1)
                                    : (arrayEmail.size() / emailCountInSimContact));

                    int groupCount = Math.max(groupEmailCount, Math.max(nameCount, groupNumCount));

                    ArrayList<UsimEntity> results = new ArrayList<UsimEntity>();
                    for (int i = 0; i < groupCount; i++) {
                        results.add(new UsimEntity());
                    }

                    UsimEntity value;
                    //get the phone number for each ADN from arrayNumber,put them in UsimEntity
                    for (int i = 0; i < groupNumCount; i++) {
                        value = results.get(i);
                        ArrayList<String> numberItem = new ArrayList<String>();
                        for (int j = 0; j < numEntitySize; j++) {
                            if ((i * numEntitySize + j) < arrayNumber.size()) {
                                numberItem.add(arrayNumber.get(i * numEntitySize + j));
                            }
                        }
                        value.putNumberList(numberItem);
                    }

                    for (int i = 0; i < groupEmailCount; i++) {
                        value = results.get(i);
                        ArrayList<String> emailItem = new ArrayList<String>();
                        for (int j = 0; j < emailCountInSimContact; j++) {
                            if ((i * emailCountInSimContact + j) < arrayEmail.size()) {
                                emailItem.add(arrayEmail.get(i * emailCountInSimContact + j));
                            }
                        }
                        value.putEmailList(emailItem);
                    }

                    ArrayList<String> emptyList = new ArrayList<String>();
                    Uri itemUri = null;
                    if (totalEmptyEmail < 0 && MoreContactUtils.canSaveEmail(sub)) {
                        Message e_msg = Message.obtain();
                        e_msg.what = MSG_NO_EMPTY_EMAIL;
                        mHandler.sendMessage(e_msg);
                    }

                    //get phone number from UsimEntity,then insert to SIM card
                    for (int i = 0; i < groupCount; i++) {
                        value = results.get(i);
                        if (value.containsNumber()) {
                            arrayNumber = (ArrayList<String>) value.getNumberList();
                        } else {
                            arrayNumber = emptyList;
                        }

                        if (value.containsEmail()) {
                            arrayEmail = (ArrayList<String>) value.getEmailList();
                        } else {
                            arrayEmail = emptyList;
                        }
                        String strNum = arrayNumber.size() > 0 ? arrayNumber.get(0) : null;
                        StringBuilder strAnrNum = new StringBuilder();
                        for (int j = 1; j < arrayNumber.size(); j++) {
                            String s = arrayNumber.get(j);
                            if (s.length() > MoreContactUtils.MAX_LENGTH_NUMBER_IN_SIM) {
                                s = s.substring(
                                        0, MoreContactUtils.MAX_LENGTH_NUMBER_IN_SIM);
                            }
                            strAnrNum.append(s);
                            strAnrNum.append(",");
                        }
                        StringBuilder strEmail = new StringBuilder();
                        for (int j = 0; j < arrayEmail.size(); j++) {
                            String s = arrayEmail.get(j);
                            if (s.length() > MoreContactUtils.MAX_LENGTH_EMAIL_IN_SIM) {
                                s = s.substring(
                                        0, MoreContactUtils.MAX_LENGTH_EMAIL_IN_SIM);
                            }
                            strEmail.append(s);
                            strEmail.append(",");
                        }
                        itemUri = MoreContactUtils.insertToCard(mContext, strName, strNum,
                                strEmail.toString(), strAnrNum.toString(), sub);
                    }
                    if (itemUri != null) {
                        msg.what = MSG_COPY_DONE;
                        mHandler.sendMessage(msg);
                    } else {
                        msg.what = MSG_COPY_FAILURE;
                        mHandler.sendMessage(msg);
                    }
                }
            }
        }).start();

    }

    /**
     * Creates a launcher shortcut with the current contact.
     */
    private void createLauncherShortcutWithContact() {
        // Hold the parent activity of this fragment in case this fragment is destroyed
        // before the callback to onShortcutIntentCreated(...)
        final Activity parentActivity = getActivity();

        ShortcutIntentBuilder builder = new ShortcutIntentBuilder(parentActivity,
                new OnShortcutIntentCreatedListener() {

            @Override
            public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
                // Broadcast the shortcutIntent to the launcher to create a
                // shortcut to this contact
                shortcutIntent.setAction(ACTION_INSTALL_SHORTCUT);
                parentActivity.sendBroadcast(shortcutIntent);
            }

        });
        builder.createContactShortcutIntent(mLookupUri);
    }

    /**
     * Calls into the contacts provider to get a pre-authorized version of the given URI.
     */
    private Uri getPreAuthorizedUri(Uri uri) {
        Bundle uriBundle = new Bundle();
        uriBundle.putParcelable(ContactsContract.Authorization.KEY_URI_TO_AUTHORIZE, uri);
        Bundle authResponse = mContext.getContentResolver().call(
                ContactsContract.AUTHORITY_URI,
                ContactsContract.Authorization.AUTHORIZATION_METHOD,
                null,
                uriBundle);
        if (authResponse != null) {
            return (Uri) authResponse.getParcelable(
                    ContactsContract.Authorization.KEY_AUTHORIZED_URI);
        } else {
            return uri;
        }
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
        }
        return false;
    }

    private void sendContactViaSMS() {
        // Get name string
        String name = mContactData.getDisplayName();
        String phone = null;
        String email = null;
        String postal = null;
        String organization = null;
        String sipAddress = null;

        Log.d(TAG, "Contact name: " + name);

        for (RawContact raw: mContactData.getRawContacts()) {
            final ContentValues entValues = raw.getValues();
            Log.d(TAG, "  entValues:" + entValues);

            for (RawContact.NamedDataItem namedDataItem : raw.getNamedDataItems()) {
                final ContentValues entryValues = namedDataItem.mContentValues;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                Log.d(TAG, "    entryValues:" + entryValues);

                if (mimeType == null) continue;

                if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) { // Get phone string
                    if (phone == null) {
                        phone = entryValues.getAsString(Phone.NUMBER);
                    } else {
                        phone = phone + ", " + entryValues.getAsString(Phone.NUMBER);
                    }
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) { // Get email string
                    if (email == null) {
                        email = entryValues.getAsString(Email.ADDRESS);
                    } else {
                        email = email + ", " + entryValues.getAsString(Email.ADDRESS);
                    }
                } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (postal == null) {
                        postal = entryValues.getAsString(StructuredPostal.FORMATTED_ADDRESS);
                    } else {
                        postal = postal + ", " + entryValues.getAsString(
                                 StructuredPostal.FORMATTED_ADDRESS);
                    }
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (organization == null) {
                        organization = entryValues.getAsString(Organization.COMPANY);
                    } else {
                        organization = organization + ", " + entryValues
                                     .getAsString(Organization.COMPANY);
                    }
                } else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (sipAddress == null) {
                        sipAddress = entryValues.getAsString(SipAddress.SIP_ADDRESS);
                    } else {
                        sipAddress = sipAddress + ", " + entryValues
                                    .getAsString(SipAddress.SIP_ADDRESS);
                    }
                }
            }
        }

        if (TextUtils.isEmpty(name)) {
            name = mContext.getResources().getString(R.string.missing_name);
        }

        name = getString(R.string.nameLabelsGroup) + ":" + name + "\r\n";
        phone = (phone == null) ? "" : getString(R.string.phoneLabelsGroup)
                + ":" + phone + "\r\n";
        email = (email == null )? "" : getString(R.string.emailLabelsGroup)
                + ":" + email + "\r\n";
        postal = (postal == null) ? "" : getString(R.string.postalLabelsGroup)
                + ":" + postal + "\r\n";
        organization = (organization == null) ? "" : getString(R.string.organizationLabelsGroup)
                + ":" + organization + "\r\n";
        sipAddress = (sipAddress == null) ? "" : getString(R.string.label_sip_address) + ":"
                + sipAddress + "\r\n";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("sms_body", name + phone + email + postal + organization + sipAddress);
        intent.setType("vnd.android-dir/mms-sms");
        mContext.startActivity(intent);
    }

    //supply phone number and email which could stored in one ADN
    class UsimEntity {
        private ArrayList<String> mNumberList = new ArrayList<String>();
        private ArrayList<String> mEmailList = new ArrayList<String>();

        public ArrayList<String> getEmailList() {
            return mEmailList;
        }

        public ArrayList<String> getNumberList() {
            return mNumberList;
        }

        public void putEmailList(ArrayList<String> list) {
            mEmailList = list;
        }

        public void putNumberList(ArrayList<String> list) {
            mNumberList = list;
        }

        public boolean containsEmail() {
            return !mEmailList.isEmpty();
        }

        public boolean containsNumber() {
            return !mNumberList.isEmpty();
        }
    }

    private void doPickRingtone() {

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Allow the user to pick a silent ringtone
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        // Set HoloLight theme dialog
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DIALOG_THEME, R.style.Theme_RingtoneDialog);

        Uri ringtoneUri;
        if (mCustomRingtone != null) {
            ringtoneUri = Uri.parse(mCustomRingtone);
        } else {
            // Otherwise pick default ringtone Uri so that something is selected.
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);

        // Launch!
        startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_PICK_RINGTONE: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                handleRingtonePicked(pickedUri);
                break;
            }
        }
    }

    private void handleRingtonePicked(Uri pickedUri) {
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            mCustomRingtone = null;
        } else {
            mCustomRingtone = pickedUri.toString();
        }
        Intent intent = ContactSaveService.createSetRingtone(
                mContext, mLookupUri, mCustomRingtone);
        mContext.startService(intent);
    }
}
