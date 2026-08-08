plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val isFdroidBuild = gradle.startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }

if (!isFdroidBuild) {
  pluginManager.apply("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
  pluginManager.apply("com.google.gms.google-services")

  extensions.findByName("secrets")?.let { ext ->
    try {
      ext.javaClass.getMethod("setPropertiesFileName", String::class.java).invoke(ext, ".env")
      ext.javaClass.getMethod("setDefaultPropertiesFileName", String::class.java).invoke(ext, ".env.example")
    } catch (e: Exception) {
      // Ignore fallback
    }
  }

  extensions.findByName("googleServices")?.let { ext ->
    try {
      val warnEnum = Class.forName("com.google.gms.googleservices.GoogleServicesPlugin\$MissingGoogleServicesStrategy")
        .enumConstants?.firstOrNull { it.toString() == "WARN" }
      if (warnEnum != null) {
        val method = ext.javaClass.methods.firstOrNull { it.name == "setMissingGoogleServicesStrategy" || it.name == "missingGoogleServicesStrategy" }
        method?.invoke(ext, warnEnum)
      }
    } catch (e: Exception) {
      // Ignore fallback
    }
  }
}

android {
  namespace = "xyz.younglion.flexilist"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "xyz.younglion.flexilist"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "mode"

  productFlavors {
    create("full") {
      dimension = "mode"
    }
    create("fdroid") {
      dimension = "mode"
      signingConfig = null
    }
  }

  signingConfigs {
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val keystoreFile = file(keystorePath)
    if (keystoreFile.exists()) {
      create("release") {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseSigning = signingConfigs.findByName("release")
      if (releaseSigning != null) {
        signingConfig = releaseSigning
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  "fullImplementation"(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  "fullImplementation"(libs.firebase.ai)
  "fullImplementation"(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
