To run the espresso tests:

# Test setup
## 1) Build the binary first
packages/apps/Contacts/androidtest/mm
## 2) Install the test apk on target device
adb install -r $OUT/target/product/[device]/data/app/ContactsAndroidTests/ContactsAndroidTests.apk
## 3) Install an InCall plugin, its dependency app and sign in. It is
 recommended to use a test version of InCall plugin that supports the
  ACTION "com.android.contacts.androidtest.AUTH_STATE" to help with
  the plugin state automation. test1 ~ test6 uses this ACTION to
  automate the plugin authentication state

# Execute InCallMetricsContactTest (all Contact UI related) tests
* test1: CONTACTS_MANUAL_MERGED
* test2: CONTACTS_AUTO_MERGED
* test3: INVITES_SENT
* test4: DIRECTORY_SEARCH (from Contacts card)
* test5: INAPP_NUDGE_CONTACTS_LOGIN
* test6: DIRECTORY_SEARCH (from Contacts plugin tab)
* test7: INAPP_NUDGE_CONTACTS_TAB_LOGIN (precondition: hard signed out)
* test8: INAPP_NUDGE_CONTACTS_INSTALL (precondition: uninstall dependency)
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsContactTest \
com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner

## Note. It's best to run test cases individually since they have
preconditions such as the auth state needs to be signed in/signed
out, or plugin state needs to be ENABLED or HIDDEN (eg. test1 in InCallMetricsContactTest)
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsContactTest#test1 \
com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner

# Execute InCallMetricsSendTest (auto generate metrics and send) tests
adb shell am instrument -w -e class com.android.contacts.androidtest.InCallMetricsSendTest \
com.android.contacts.androidtest/android.support.test.runner.AndroidJUnitRunner


