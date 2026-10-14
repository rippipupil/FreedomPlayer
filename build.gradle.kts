buildscript {
    // El plugin de Android está siempre en el classpath para que shared/build.gradle.kts pueda
    // usar sus tipos, pero solo se aplica si se compila Android (ver settings.gradle.kts).
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.11.1")
    }
}

plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    kotlin("jvm") version "2.2.21" apply false
    kotlin("android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
}
