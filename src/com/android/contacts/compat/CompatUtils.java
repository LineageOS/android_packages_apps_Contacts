/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.contacts.compat;

public final class CompatUtils {
    /**
     * These 4 variables are copied from ContentProviderOperation for compatibility.
     */
    public final static int TYPE_INSERT = 1;

    public final static int TYPE_UPDATE = 2;

    public final static int TYPE_DELETE = 3;

    public final static int TYPE_ASSERT = 4;
}
