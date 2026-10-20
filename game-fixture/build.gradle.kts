// Synthetic touch receiver installed only on the disposable CI emulator, never shipped with the collector.
plugins { id("com.android.application") }
android {
    namespace = "vn.ndang.gamefixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.garena.game.kgvn"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "synthetic-test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
