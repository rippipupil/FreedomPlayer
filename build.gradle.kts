buildscript {
    // El plugin de Android solo se carga si se compila Android (ver settings.gradle.kts).
    val withAndroid = (project.findProperty("fp.android") as String?)?.toBoolean() ?: true
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        if (withAndroid) classpath("com.android.tools.build:gradle:8.11.1")
    }
}

plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    kotlin("jvm") version "2.2.21" apply false
    kotlin("android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
}
