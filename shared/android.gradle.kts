// Solo se aplica cuando se compila con Android (ver shared/build.gradle.kts).
configure<com.android.build.gradle.LibraryExtension> {
    namespace = "com.freedomplayer.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
