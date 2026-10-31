/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.telecom.PhoneAccount
import androidx.core.graphics.drawable.IconCompat
import com.android.contacts.ContactPhotoManager.DefaultImageRequest
import com.android.contacts.lettertiles.LetterTileDrawable
import com.android.contacts.util.BitmapUtil
import com.android.contacts.util.ImplicitIntentsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Constructs shortcut intents. */
class ShortcutIntentBuilder(
  private val context: Context,
  private val listener: OnShortcutIntentCreatedListener,
) {

  /** Listener interface. */
  fun interface OnShortcutIntentCreatedListener {

    /**
     * Callback for shortcut intent creation.
     *
     * @param uri the original URI for which the shortcut intent has been created.
     * @param shortcutIntent resulting shortcut intent.
     */
    fun onShortcutIntentCreated(uri: Uri, shortcutIntent: Intent)
  }

  private class LoadedData(
    val displayName: String?,
    val lookupKey: String?,
    val photo: ByteArray?,
    val phoneNumber: String?,
  )

  private val resources = context.resources
  private val shortcutManager = context.getSystemService(ShortcutManager::class.java)
  private val iconSize: Int
  private val iconDensity: Int

  init {
    val am = context.getSystemService(ActivityManager::class.java)
    iconSize =
      resources.getDimensionPixelSize(R.dimen.shortcut_icon_size).takeIf { it != 0 }
        ?: am.launcherLargeIconSize
    iconDensity = am.launcherLargeIconDensity
  }

  fun createContactShortcutIntent(contactUri: Uri) {
    launch(contactUri, CONTACT_COLUMNS) { data -> buildContactShortcutIntent(contactUri, data) }
  }

  fun createPhoneNumberShortcutIntent(dataUri: Uri, shortcutAction: String) {
    launch(dataUri, PHONE_COLUMNS) { data ->
      buildPhoneNumberShortcutIntent(data, shortcutAction)
    }
  }

  /**
   * Loads name, photo and other data from the database in the background, then builds the
   * shortcut intent and delivers it to the listener on the main thread.
   */
  private fun launch(uri: Uri, columns: Array<String>, build: (LoadedData) -> Intent) {
    // Like the AsyncTask this replaces, the work outlives the screen that started it, as some
    // callers are not lifecycle aware yet.
    val app = context.applicationContext as ContactsApplication
    app.applicationScope.launch(Dispatchers.Main) {
      val data = withContext(Dispatchers.IO) { loadData(uri, columns) }
      listener.onShortcutIntentCreated(uri, build(data))
    }
  }

  private fun loadData(uri: Uri, columns: Array<String>): LoadedData {
    var displayName: String? = null
    var lookupKey: String? = null
    var photoId = 0L
    var phoneNumber: String? = null
    context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        displayName = cursor.getString(DISPLAY_NAME_COLUMN_INDEX)
        photoId = cursor.getLong(PHOTO_ID_COLUMN_INDEX)
        lookupKey = cursor.getString(LOOKUP_KEY_COLUMN_INDEX)
        if (columns.size > PHONE_NUMBER_COLUMN_INDEX) {
          phoneNumber = cursor.getString(PHONE_NUMBER_COLUMN_INDEX)
        }
      }
    }
    return LoadedData(displayName, lookupKey, loadPhoto(photoId), phoneNumber)
  }

  private fun loadPhoto(photoId: Long): ByteArray? {
    if (photoId == 0L) {
      return null
    }
    return context.contentResolver
      .query(Data.CONTENT_URI, PHOTO_COLUMNS, PHOTO_SELECTION, arrayOf(photoId.toString()), null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }
  }

  private fun getPhotoDrawable(
    bitmapData: ByteArray?,
    displayName: String?,
    lookupKey: String?,
  ): Drawable {
    if (bitmapData != null) {
      val bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size)
      return BitmapDrawable(resources, bitmap)
    }
    val request = DefaultImageRequest(displayName, lookupKey, false)
    // Scale the image down to add the padding needed by AdaptiveIcons.
    request.scale = LetterTileDrawable.getAdaptiveIconScale()
    return ContactPhotoManager.getDefaultAvatarDrawableForContact(resources, false, request)
  }

  private fun buildContactShortcutIntent(contactUri: Uri, data: LoadedData): Intent {
    val displayName = data.displayName.orMissingName()
    val shortcutInfo =
      DynamicShortcuts(context)
        .getQuickContactShortcutInfo(ContentUris.parseId(contactUri), data.lookupKey, displayName)
    val intent = shortcutInfo?.let { shortcutManager.createShortcutResultIntent(it) } ?: Intent()

    val icon = generateQuickContactIcon(getPhotoDrawable(data.photo, displayName, data.lookupKey))
    IconCompat.createWithAdaptiveBitmap(icon).addToShortcutIntent(intent, null, context)
    intent.putExtra(
      Intent.EXTRA_SHORTCUT_INTENT,
      ImplicitIntentsUtil.getIntentForQuickContactLauncherShortcut(context, contactUri),
    )
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName)
    return intent
  }

  private fun buildPhoneNumberShortcutIntent(data: LoadedData, shortcutAction: String): Intent {
    val drawable = getPhotoDrawable(data.photo, data.displayName, data.lookupKey)
    val displayName = data.displayName.orMissingName()

    val phoneUri: Uri
    val icon: Bitmap
    val shortcutName: String
    if (Intent.ACTION_CALL == shortcutAction) {
      // Make the URI a direct tel: URI so that it will always continue to work
      phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, data.phoneNumber, null)
      icon = generatePhoneNumberIcon(drawable, R.drawable.quantum_ic_phone_vd_theme_24)
      shortcutName = resources.getString(R.string.call_by_shortcut, displayName)
    } else {
      phoneUri = Uri.fromParts(ContactsUtils.SCHEME_SMSTO, data.phoneNumber, null)
      icon = generatePhoneNumberIcon(drawable, R.drawable.quantum_ic_message_vd_theme_24)
      shortcutName = resources.getString(R.string.sms_by_shortcut, displayName)
    }

    val shortcutIntent = Intent(shortcutAction, phoneUri).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    val adaptiveIcon = IconCompat.createWithAdaptiveBitmap(icon)
    val id = shortcutAction + data.lookupKey + phoneUri.toString().hashCode()
    val shortcutInfo =
      DynamicShortcuts(context)
        .getActionShortcutInfo(id, displayName, shortcutIntent, adaptiveIcon.toIcon(context))
    val intent = shortcutInfo?.let { shortcutManager.createShortcutResultIntent(it) } ?: Intent()

    adaptiveIcon.addToShortcutIntent(intent, null, context)
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName)
    return intent
  }

  private fun String?.orMissingName(): String =
    if (isNullOrEmpty()) resources.getString(R.string.missing_name) else this

  private fun generateQuickContactIcon(photo: Drawable): Bitmap {
    val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    photo.setBounds(0, 0, iconSize, iconSize)
    photo.draw(Canvas(bitmap))
    return bitmap
  }

  /** Generates a phone number shortcut icon, with the call or message action icon on top. */
  private fun generatePhoneNumberIcon(photo: Drawable, actionResId: Int): Bitmap {
    val density = resources.displayMetrics.density

    val phoneDrawable = resources.getDrawableForDensity(actionResId, iconDensity, null)!!
    // These icons have the same height and width so either is fine for the size.
    val phoneIcon = BitmapUtil.drawableToBitmap(phoneDrawable, phoneDrawable.intrinsicHeight)

    val icon = generateQuickContactIcon(photo)
    val photoPaint =
      Paint().apply {
        isDither = true
        isFilterBitmap = true
      }

    // The whole canvas area is 108dp, a centered circle with a diameter of 66dp is the "safe
    // zone". So we start the drawing the phone icon at
    // 108dp - 21 dp (distance from right edge of safe zone to the edge of the canvas)
    // - 24 dp (size of the phone icon) on the x axis (left)
    // The y axis is simply 21dp for the distance to the safe zone (top).
    val left = (iconSize - 45 * density).toInt()
    val top = (21 * density).toInt()
    Canvas(icon).drawBitmap(phoneIcon, left.toFloat(), top.toFloat(), photoPaint)
    return icon
  }

  companion object {
    /**
     * This is a hidden API of the launcher in JellyBean that allows us to disable the animation
     * that it would usually do, because it interferes with our own animation for QuickContact.
     * This is needed since some versions of the launcher override the intent flags and therefore
     * ignore Intent.FLAG_ACTIVITY_NO_ANIMATION.
     */
    const val INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
      "com.android.launcher.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION"

    // Contacts and Phone share the same column names for these.
    private val CONTACT_COLUMNS =
      arrayOf(Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.LOOKUP_KEY)
    private val PHONE_COLUMNS = CONTACT_COLUMNS + Phone.NUMBER

    private const val DISPLAY_NAME_COLUMN_INDEX = 0
    private const val PHOTO_ID_COLUMN_INDEX = 1
    private const val LOOKUP_KEY_COLUMN_INDEX = 2
    private const val PHONE_NUMBER_COLUMN_INDEX = 3

    private val PHOTO_COLUMNS = arrayOf(Photo.PHOTO)
    private const val PHOTO_SELECTION = "${Photo._ID}=?"
  }
}
