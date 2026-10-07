plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "vn.ndang.aovcollector"
 compileSdk = 35
 defaultConfig { applicationId = "vn.ndang.aovcollector"; minSdk = 30; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
