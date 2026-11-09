import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional release signing. Create android/keystore.properties (git-ignored) to enable it.
//
// storeFile may be absolute or relative; a relative path is resolved against android/ first
// and then against the repository root, so both "release.jks" and "android/release.jks"
// and "C:\\keys\\release.jks" work. Missing values are reported instead of silently
// producing an unsigned APK.
// android/keystore.properties is the documented location; a file in the repository root
// is accepted as well so an existing setup keeps working.
// Search order: android/, repository root, .github/ (a file that was misplaced there).
val androidKeystorePropertiesFile = rootProject.file("keystore.properties")
val keystorePropertiesFile = listOf(
    androidKeystorePropertiesFile,
    rootProject.file("../keystore.properties").normalize(),
    rootProject.file("../.github/keystore.properties").normalize(),
).firstOrNull { it.isFile } ?: androidKeystorePropertiesFile
// Base directory for a relative storeFile: the folder holding keystore.properties, so the
// same ../name.jks entry works wherever the properties file lives.
val keystorePropertiesDir: File = keystorePropertiesFile.parentFile ?: rootProject.projectDir
val keystorePropertiesPath = keystorePropertiesFile.absolutePath
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/** Absolute keystore file, or null when it cannot be resolved. */
val releaseStoreFile: File? = if (!keystorePropertiesFile.isFile) {
    null
} else {
    keystoreProperties.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val candidate = File(raw)
        when {
            candidate.isAbsolute -> candidate
            // Relative to the properties file first, then to android/ and the repo root.
            File(keystorePropertiesDir, raw).isFile -> File(keystorePropertiesDir, raw)
            rootProject.file(raw).isFile -> rootProject.file(raw)
            else -> File(keystorePropertiesDir, raw)
        }
    }
}

val signingProblem: String? = when {
    !keystorePropertiesFile.isFile -> null // unsigned release is intentional here
    keystoreProperties.getProperty("storeFile").isNullOrBlank() -> "storeFile is missing in '" + keystorePropertiesPath + "'"
    releaseStoreFile == null || !releaseStoreFile.isFile ->
        "storeFile does not point to an existing file (resolved to " + (releaseStoreFile?.absolutePath ?: "?") + ")"
    keystoreProperties.getProperty("storePassword").isNullOrBlank() -> "storePassword is missing in '" + keystorePropertiesPath + "'"
    keystoreProperties.getProperty("keyAlias").isNullOrBlank() -> "keyAlias is missing in '" + keystorePropertiesPath + "'"
    keystoreProperties.getProperty("keyPassword").isNullOrBlank() -> "keyPassword is missing in '" + keystorePropertiesPath + "'"
    else -> null
}

// Fail loudly rather than shipping an unsigned release build, and always state which
// signing configuration the release build is using.
gradle.taskGraph.whenReady {
    val releases = allTasks.any {
        it.path == ":app:packageRelease" || it.path == ":app:assembleRelease" || it.path == ":app:bundleRelease"
    }
    if (!releases) return@whenReady
    if (signingProblem != null) {
        throw GradleException(
            "Release signing is configured but invalid: " + signingProblem +
                ". Fix android/keystore.properties or delete it to build an unsigned release on purpose."
        )
    }
    if (releaseStoreFile == null) {
        logger.lifecycle("[signing] No " + keystorePropertiesPath + " found: the release APK is UNSIGNED by design.")
    } else {
        logger.lifecycle(
            "[signing] Release APK will be signed with '" + releaseStoreFile.absolutePath +
                "' (alias " + keystoreProperties.getProperty("keyAlias") + ")."
        )
    }
}

android {
    namespace = "com.droidvna.app"
    compileSdk = 36

    signingConfigs {
        if (signingProblem == null && releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // Sign with v1+v2+v3 so the APK installs on API 26 (minSdk) as well.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.droidvna.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingProblem == null && releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Complex linear algebra for the 12-term SOLT correction. The complex 2x2 solve
    // (S = B * A^-1) is done with FieldLUDecomposition instead of hand-written code.
    implementation("org.apache.commons:commons-math3:3.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20260814")
}