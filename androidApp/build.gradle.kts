import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Firma del APK. En GitHub Actions, si existen los secretos KEYSTORE_BASE64, KEYSTORE_PASSWORD,
// KEY_ALIAS y KEY_PASSWORD, el workflow deja la clave en FP_KEYSTORE_FILE y el APK se firma con ella
// (así cada versión se instala encima de la anterior). Si no, se firma con la clave de depuración
// estándar de Android. Nunca se guarda ninguna clave ni contraseña en el repositorio.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseKeystore = env("FP_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }
val releaseStorePassword = env("KEYSTORE_PASSWORD")
val releaseKeyAlias = env("KEY_ALIAS")
val releaseKeyPassword = env("KEY_PASSWORD") ?: releaseStorePassword
val signWithReleaseKey = releaseKeystore != null && releaseStorePassword != null && releaseKeyAlias != null

if (releaseKeystore != null && !signWithReleaseKey) {
    logger.warn("FreedomPlayer: hay keystore, pero faltan KEYSTORE_PASSWORD o KEY_ALIAS. Se firma con la clave de depuración.")
}

val buildNumber = env("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// En una etiqueta vX.Y.Z el workflow pone FP_VERSION=X.Y.Z; si no, «0.1.<n.º de compilación>».
val appVersion = env("FP_VERSION") ?: "0.1.$buildNumber"

android {
    namespace = "com.freedomplayer.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.freedomplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersion
    }

    signingConfigs {
        if (signWithReleaseKey) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (signWithReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.10.1")
}
