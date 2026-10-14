import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0

// En una etiqueta vX.Y.Z el workflow pone FP_VERSION=X.Y.Z. El instalador de Windows solo admite
// números (MAJOR.MINOR.BUILD), así que se quita lo que vaya detrás de un guion («-beta»…).
val appVersion = providers.environmentVariable("FP_VERSION").orNull
    ?.substringBefore('-')
    ?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
    ?: "0.1.$buildNumber"

compose.desktop {
    application {
        mainClass = "com.freedomplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "FreedomPlayer"
            packageVersion = appVersion
            description = "Reproductor de música personal"
            vendor = "FreedomPlayer"
            // Módulos de Java que se meten en el instalador (además de java.base y java.desktop).
            modules("java.instrument", "java.management", "java.naming", "jdk.unsupported")
            windows {
                menuGroup = "FreedomPlayer"
                shortcut = true
                dirChooser = true
                // Fijo para que cada instalador nuevo actualice el anterior.
                upgradeUuid = "6f1c2a3e-8b4d-4e9a-9c51-2d7f0b8e4a16"
            }
        }
    }
}
