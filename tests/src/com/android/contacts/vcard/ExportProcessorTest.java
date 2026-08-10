/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.contacts.vcard;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExportProcessorTest {

    private VCardService mMockService;
    private AtomicReference<String> mUsedMode = new AtomicReference<>();

    @Before
    public void setUp() {
        mMockService = mock(VCardService.class);
        when(mMockService.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(null);
        when(mMockService.getString(any(Integer.class))).thenReturn("");
        when(mMockService.getResources()).thenReturn(
                InstrumentationRegistry.getTargetContext().getResources());
    }

    @Test
    public void testRunInternal_usesTruncateMode() throws Exception {
        ContentResolver wrappedResolver = ContentResolver.wrap(new ContentProvider() {
            @Override
            public AssetFileDescriptor openAssetFile(
                    Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
                mUsedMode.set(mode);
                throw new FileNotFoundException("Simulated");
            }

            @Override
            public boolean onCreate() { return true; }

            @Override
            public Cursor query(
                    Uri uri,
                    String[] projection,
                    String selection,
                    String[] selectionArgs,
                    String sortOrder) {
                return null;
            }

            @Override
            public String getType(Uri uri) { return ""; }

            @Override
            public Uri insert(Uri uri, ContentValues values) { return null; }

            @Override
            public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

            @Override
            public int update(Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
                return 0;
            }
        });

        when(mMockService.getContentResolver()).thenReturn(wrappedResolver);

        Uri uri = Uri.parse("content://com.android.contacts/export");
        ExportRequest request = new ExportRequest(uri);
        ExportProcessor processor = new ExportProcessor(mMockService, request, 1, "activity");

        try {
            processor.run();
        } catch (Exception e) {
            // Expected to fail in runInternal after openOutputStream
            // due to Simulated FileNotFoundException
        }

        assertEquals("wt", mUsedMode.get());
    }
}
