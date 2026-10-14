import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
}

// Versiones de las librerías que se usan en más de un sitio.
val newPipeVersion = "v0.26.5"
val okHttpVersion = "5.4.0"
val sqlDelightVersion = "2.2.1"
val coilVersion = "3.3.0"

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
            // La biblioteca en SQLite (canciones, playlists, notas, rankings, historial y letras).
            implementation("app.cash.sqldelight:runtime:$sqlDelightVersion")
            // Carátulas (de internet o del disco) con fundido.
            implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
            implementation("com.squareup.okio:okio:3.18.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Lo que comparten Android y el PC (los dos son JVM): YouTube con NewPipe Extractor,
        // descargas con OkHttp y letras de LRCLIB. Mismo código, compilado para cada uno.
        val jvmShared = listOf(
            "com.github.teamnewpipe:NewPipeExtractor:$newPipeVersion",
            "com.squareup.okhttp3:okhttp:$okHttpVersion",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0",
            "io.coil-kt.coil3:coil-network-okhttp:$coilVersion",
        )
        if (withAndroid) {
            androidMain {
                kotlin.srcDir("src/jvmShared/kotlin")
                dependencies {
                    jvmShared.forEach { implementation(it) }
                    implementation("app.cash.sqldelight:android-driver:$sqlDelightVersion")
                }
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                jvmShared.forEach { implementation(it) }
                implementation("app.cash.sqldelight:sqlite-driver:$sqlDelightVersion")
            }
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

// Prueba de red con YouTube y LRCLIB de verdad (NetworkSmoke.kt). No forma parte de las pruebas:
// YouTube a veces bloquea los servidores de GitHub, así que el workflow la lanza aparte y solo avisa.
tasks.register<JavaExec>("networkSmoke") {
    group = "verification"
    description = "Busca, saca el audio y pide una letra de verdad (necesita internet)."
    val test = kotlin.targets.getByName("desktop").compilations.getByName("test")
    classpath = files(listOfNotNull(test.output.allOutputs, test.runtimeDependencyFiles))
    mainClass.set("com.freedomplayer.jvm.NetworkSmokeKt")
}

sqldelight {
    databases {
        create("FpDatabase") {
            packageName.set("com.freedomplayer.db")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.freedomplayer.resources"
}
