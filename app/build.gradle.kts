plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "vn.ndang.aovcollector"
 compileSdk = 35
 defaultConfig { applicationId = "vn.ndang.aovcollector"; minSdk = 30; targetSdk = 35; versionCode = 3; versionName = "0.3.0"; testInstrumentationRunner = "vn.ndang.aovcollector.UiSmoke" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("com.google.mlkit:text-recognition:16.0.1") }
