// UI fixture installed only by CI, separate from the collector APK.
plugins { id("com.android.application") }
android {
    namespace = "vn.ndang.chatfixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.openai.chatgpt"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
