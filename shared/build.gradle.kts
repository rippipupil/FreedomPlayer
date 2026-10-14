import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// -Pfp.android=false deja fuera Android (ver settings.gradle.kts). El plugin de Android está
// siempre en el classpath (build.gradle.kts de la raíz), pero solo se aplica si se compila Android.
val withAndroid = (project.findProperty("fp.android") as String?)?.toBoolean() ?: true
if (withAndroid) {
    apply(plugin = "com.android.library")
    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.freedomplayer.shared"
        compileSdk = 36
        defaultConfig { minSdk = 26 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

kotlin {
    if (withAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            // runtime y ui son «api» porque App() y AppState los usan en su firma (Modifier,
            // @Composable): así las apps de Android y de escritorio los ven al compilar.
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Prueba de humo de la interfaz (AppSmokeTest): pinta la app sin ventana.
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Si una prueba falla, el error completo sale en el log (los informes HTML no siempre se pueden abrir).
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.freedomplayer.resources"
}
