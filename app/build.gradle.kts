import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.techadvantage.budgetrak"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techadvantage.budgetrak"
        minSdk = 28
        targetSdk = 35
        // versionName format: MAJOR.MINOR.PP (third segment is zero-padded
        // 00-99 to leave 100 patch slots per minor for extensive debugging
        // cycles before bumping minor).
        versionCode = 15
        versionName = "2.10.00"
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localPropsFile.inputStream().use { localProps.load(it) }
            }
            val keystorePath = localProps.getProperty("BUDGETRAK_KEYSTORE_FILE", "")
            val keystorePassword = localProps.getProperty("BUDGETRAK_KEYSTORE_PASSWORD", "")
            val keystoreAlias = localProps.getProperty("BUDGETRAK_KEY_ALIAS", "upload")
            if (keystorePath.isNotEmpty() && keystorePassword.isNotEmpty()) {
                val ksFile = file(keystorePath)
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = keystorePassword
                    keyAlias = keystoreAlias
                    keyPassword = keystorePassword
                }
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
            // Embed native (.so) debug symbols into the AAB so Play Console
            // can deobfuscate crashes/ANRs from bundled native libs (Firebase,
            // Compose runtime, etc.). Without this, Play warns "this AAB
            // contains native code, and you've not uploaded debug symbols."
            // SYMBOL_TABLE is sufficient for stack-trace symbolication; FULL
            // adds line numbers but ~triples the symbol payload size.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime UTC\"")

        // Gemini API key from local.properties (kept out of VCS).
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { localProps.load(it) }
        }
        val geminiKey = localProps.getProperty("GEMINI_API_KEY", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Google AI (Gemini) — direct SDK, uses API key from local.properties
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Encrypted storage for sync keys
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SAF directory access for backup restore (scoped storage compatibility)
    implementation("androidx.documentfile:documentfile:1.0.1")
}
