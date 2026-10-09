plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "vn.ndang.aovcollector"
 compileSdk = 35
 signingConfigs { getByName("debug") { storeFile = rootProject.file("collector-debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
 defaultConfig { applicationId = "vn.ndang.aovcollector"; minSdk = 30; targetSdk = 35; versionCode = 9; versionName = "0.4.3"; testInstrumentationRunner = "vn.ndang.aovcollector.UiSmoke" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 // ML Kit ships native code per ABI; a phone-only APK is under half the universal size.
 splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64", "x86"); isUniversalApk = true } }
}

dependencies { implementation("com.google.mlkit:text-recognition:16.0.1"); testImplementation("junit:junit:4.13.2") }
