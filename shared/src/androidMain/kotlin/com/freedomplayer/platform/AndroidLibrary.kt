package com.freedomplayer.platform

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.Services
import com.freedomplayer.db.FpDatabase
import com.freedomplayer.jvm.AppDirs
import com.freedomplayer.jvm.UpdateInstaller
import com.freedomplayer.jvm.realServices
import java.io.File

/** Dónde guarda la app de Android su biblioteca, y los servicios de verdad para el móvil. */
object AndroidLibrary {
    /** Música, carátulas y descargas a medias, en el almacenamiento privado de la app. */
    fun dirs(context: Context): AppDirs = AppDirs(File(context.applicationContext.filesDir, "biblioteca"))

    fun services(context: Context, engine: AudioEngine, updater: UpdateInstaller? = null): Services {
        val app = context.applicationContext
        val driver = AndroidSqliteDriver(FpDatabase.Schema, app, DATABASE)
        return realServices(driver, dirs(app), engine, AndroidLoudness, updater = updater)
    }

    const val DATABASE = "freedomplayer.db"
}
