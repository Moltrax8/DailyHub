import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Read secrets from local.properties (never commit this file)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun localProp(key: String) = localProps.getProperty(key, "")

android {
    namespace = "com.moltrax.personalnoteapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moltrax.personalnoteapp"
        minSdk = 26
        targetSdk = 35
        // Version codes follow the GitHub tag scheme (Phase 9 auto-update):
        // major*10000 + minor*100 + patch, so v1.0 == 10000 and plain integer
        // comparison decides updates (see versionCodeFromTag).
        versionCode = 10105
        versionName = "1.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject secrets as BuildConfig fields
        buildConfigField("String", "DRIVE_FOLDER_NAME",       "\"${localProp("DRIVE_FOLDER_NAME").ifEmpty { "PersonalNoteAppBackup" }}\"")
        buildConfigField("String", "DRIVE_SCOPE",             "\"${localProp("DRIVE_SCOPE").ifEmpty { "https://www.googleapis.com/auth/drive.appdata" }}\"")
        // Supabase (dev project): public anon key only — never put service_role here.
        // Empty on machines without local.properties entries → Supabase stays unavailable at runtime.
        buildConfigField("String", "SUPABASE_URL",            "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",       "\"${localProp("SUPABASE_ANON_KEY")}\"")

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProp("RELEASE_STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProp("RELEASE_STORE_PASSWORD")
                keyAlias = localProp("RELEASE_KEY_ALIAS")
                keyPassword = localProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.api.ApkVariantOutput) {
                outputFileName = "DailyHub-v${versionName}.apk"
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Glance widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Media3 ExoPlayer (exercise demo videos)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Security (token storage)
    implementation(libs.security.crypto)

    // Drag-and-drop reorderable lists
    implementation(libs.reorderable)

    // Firebase Cloud Messaging (Phase 7 dev-activity push; BoM 33.x fits compileSdk 35 / AGP 8.7).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Testing
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (android.jar only ships throwing stubs).
    testImplementation("org.json:json:20180813")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
