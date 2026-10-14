rootProject.name = "FreedomPlayer"

pluginManagement {
    repositories {
        // Google Maven solo para lo que publica Google; lo demás va directo a Maven Central.
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // NewPipe Extractor (y su nanojson) solo se publica en JitPack. Solo esos grupos salen de
        // ahí, y de ningún otro sitio: así nadie puede colar otra librería con el mismo nombre.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "JitPack" } }
            filter {
                includeGroupAndSubgroups("com.github.teamnewpipe")
                includeGroupAndSubgroups("com.github.TeamNewPipe")
            }
        }
    }
}

// -Pfp.android=false deja fuera Android (para equipos sin el SDK de Android).
val withAndroid = providers.gradleProperty("fp.android").getOrElse("true").toBoolean()

include(":shared", ":desktopApp")
if (withAndroid) include(":androidApp")
