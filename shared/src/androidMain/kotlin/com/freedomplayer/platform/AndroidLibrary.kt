package com.freedomplayer.platform

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.Services
import com.freedomplayer.db.FpDatabase
import com.freedomplayer.jvm.AppDirs
import com.freedomplayer.jvm.SyncthingBackend
import com.freedomplayer.jvm.realServices
import java.io.File

/** Dónde guarda la app de Android su biblioteca, y los servicios de verdad para el móvil. */
object AndroidLibrary {
    /** Música, carátulas y descargas a medias, en el almacenamiento privado de la app. */
    fun dirs(context: Context): AppDirs = AppDirs(File(context.applicationContext.filesDir, "biblioteca"))

    fun services(context: Context, engine: AudioEngine): Services {
        val app = context.applicationContext
        val driver = AndroidSqliteDriver(FpDatabase.Schema, app, DATABASE)
        return realServices(driver, dirs(app), engine, AndroidLoudness, sync = sync(app))
    }

    /**
     * Syncthing del móvil: va dentro del APK como biblioteca nativa (libsyncthing.so), que Android
     * deja ejecutar desde su carpeta. En las tres ABI (arm64-v8a, armeabi-v7a y x86_64) es el código
     * oficial compilado para Android con el NDK (ver .github/scripts/syncthing.sh).
     */
    private fun sync(app: Context): SyncthingBackend {
        val dirs = dirs(app)
        return SyncthingBackend(
            executable = { File(app.applicationInfo.nativeLibraryDir, "libsyncthing.so").takeIf { it.isFile } },
            home = File(app.filesDir, "syncthing"),
            music = dirs.music,
            library = File(dirs.root, "sincronizar"),
            showsQr = false,
            extraEnvironment = mapOf("HOME" to app.filesDir.absolutePath),
        )
    }

    const val DATABASE = "freedomplayer.db"
}
