/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    id("com.android.application") version "8.7.1"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("org.lineageos.generatebp") version "+"
}

android {
    compileSdk = 35
    namespace = "com.android.contacts"

    defaultConfig {
        applicationId = "com.android.contacts"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard.flags"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            res.srcDirs("res")
            java.srcDirs("src", "src-bind")
            assets.srcDirs("assets")
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    //"com.android.phone.common-lib",

    //"com.google.android.material_material",
    implementation("com.google.android.material:material:1.11.0")
    //"androidx.transition_transition",
    implementation("androidx.transition:transition:1.6.0")
    //"androidx.legacy_legacy-support-v13",
    implementation("androidx.legacy:legacy-support-v13:1.0.0")
    //"androidx.appcompat_appcompat",
    implementation("androidx.appcompat:appcompat:1.7.1")
    //"androidx.cardview_cardview",
    implementation("androidx.cardview:cardview:1.0.0")
    //"androidx.recyclerview_recyclerview",
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    //"androidx.palette_palette",
    implementation("androidx.palette:palette:1.0.0")
    //"androidx.legacy_legacy-support-v4",
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    //"android-common",
    //"com.android.vcard",
    implementation(files("libs/vcard.jar"))
    //"guava",
    implementation("com.google.guava:guava:33.5.0-android")
    //"libphonenumber",
    implementation ("com.googlecode.libphonenumber:libphonenumber:9.0.18")
    implementation("com.googlecode.libphonenumber:geocoder:3.18")

    // https://mvnrepository.com/artifact/jakarta.annotation/jakarta.annotation-api
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.defaultConfig.targetSdk!!)
    minSdk.set(android.defaultConfig.minSdk!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group.startsWith("androidx") -> true
            module.group.startsWith("org.jetbrains") -> true
            module.group == "com.google.android.material" -> true
            module.group == "com.google.errorprone" -> true
            module.group == "com.google.guava" -> true
            module.group == "junit" -> true

            module.group == "com.googlecode.libphonenumber" -> true
            else -> false
        }
    }
}
